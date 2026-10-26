package sa.hulksa.player.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import sa.hulksa.player.ManualParentAuthProofRegistry
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentDetails
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.Episode
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.ServerDiagnosticsReport
import sa.hulksa.player.model.PlaybackRequest
import sa.hulksa.player.model.SeriesBundle
import sa.hulksa.player.security.CredentialEnvelopeRemovalException
import sa.hulksa.player.security.CredentialVault

private val ACCOUNT_SESSION_COMMIT_LOCK = Any()

/**
 * Runs a credential-removal transition fail-closed: durable credential removal must succeed
 * before the session-ownership mutation [advance] may run. A failed removal propagates and leaves
 * ownership untouched, so no caller can report logout or a no-remember authentication as completed
 * while the old credential envelope remains restart-restorable.
 */
internal fun <T> withDurableCredentialsRemoved(
    clearDurableCredentials: () -> Unit,
    advance: () -> T,
): T {
    clearDurableCredentials()
    return advance()
}

internal suspend fun <T> runKidsSnapshotPersistenceOffMain(operation: () -> T): T =
    withContext(Dispatchers.IO) { operation() }

private data class AccountSessionOwner(
    val accountId: String,
    val sessionId: String,
)

class HulkRepository internal constructor(
    context: Context,
    private val vault: CredentialVault,
    private val accountSessionStore: AccountSessionStore,
    private val suspendDownloadOwner: ((String) -> Unit)? = null,
    private val portalResolver: PortalResolver = PortalResolver(),
    private val client: XtreamClient = XtreamClient(),
) {
    constructor(context: Context) : this(
        context = context,
        vault = CredentialVault(context.applicationContext),
        accountSessionStore = AccountSessionStore(context.applicationContext),
    )

    private val appContext = context.applicationContext
    private val kidsCatalogClient = KidsServerCatalogClient()
    private val kidsContentFilterStore = KidsContentFilterStore(appContext)
    private val movieCardMetadataClient = MovieCardMetadataClient()
    private val seriesCardMetadataClient = SeriesCardMetadataClient()
    private val diagnostics = ServerDiagnosticsEngine(appContext)

    suspend fun login(credentials: Credentials, remember: Boolean): AuthenticatedSession =
        when (val result = beginAuthentication(credentials, remember)) {
            is AuthenticationStartResult.Completed -> result.session
            is AuthenticationStartResult.DecisionRequired ->
                throw AccountIdentityDecisionRequiredException(result.pending.candidates)
        }

    /**
     * Authenticates against the provider and resolves local ownership. An unknown host for a
     * username that already has local accounts returns [AuthenticationStartResult.DecisionRequired]
     * without binding a scope, updating the session registry, trusting the host or persisting
     * credentials. Nothing account-owned becomes reachable before the explicit decision.
     */
    internal suspend fun beginAuthentication(
        credentials: Credentials,
        remember: Boolean,
    ): AuthenticationStartResult {
        // Any new authentication attempt supersedes an older deferred decision. Serialize the
        // invalidation with the session commit lock so a stale decision cannot validate and
        // commit across a newer attempt's start; keep the wait off the caller thread.
        withContext(Dispatchers.IO) {
            synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                PendingAuthenticationDecisionRegistry.invalidate()
            }
        }
        val session = try {
            val portal = portalResolver.resolve(credentials.accessCode)
            client.authenticate(portal, credentials)
        } catch (error: Throwable) {
            ManualParentAuthProofRegistry.completeAuthenticationFailure()
            throw error
        }

        var pendingDecision: PendingAccountIdentityDecision? = null
        try {
            // Resolution and commit share one ownership transaction so a concurrent ownership
            // change cannot slip between the resolution that authorizes the write and the write.
            val metadata = withContext(Dispatchers.IO) {
                synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                    val resolution = accountSessionStore.resolveAuthenticationIdentity(
                        username = session.credentials.username,
                        portalBaseUrl = session.portal.baseUrl,
                    )
                    if (resolution is AccountIdentityResolution.Ambiguous) {
                        pendingDecision = PendingAccountIdentityDecision(
                            session = session,
                            username = session.credentials.username.trim(),
                            portalBaseUrl = accountTrustedHostKey(session.portal.baseUrl),
                            candidates = resolution.candidates,
                            attempt = PendingAuthenticationDecisionRegistry.begin(),
                            priorSessionId = accountSessionStore.metadata()?.sessionId,
                        )
                        null
                    } else {
                        suspendExistingDownloadOwner()
                        val accountId = when (resolution) {
                            is AccountIdentityResolution.Known -> resolution.accountId
                            is AccountIdentityResolution.New -> resolution.accountId
                            is AccountIdentityResolution.Ambiguous -> error("Ambiguous resolution handled above")
                        }
                        commitAuthenticatedSessionLocked(session, accountId, remember)
                    }
                }
            }
            if (pendingDecision != null) {
                return AuthenticationStartResult.DecisionRequired(pendingDecision)
            }
            val committed = requireNotNull(metadata) { "Committed authentication metadata missing" }
            // The gate marks an attempt as manual only when it originated from HulkViewModel.login().
            // Startup restore reaches authenticate() directly, so this call records current ownership
            // but intentionally creates no parent-bootstrap proof for restored credentials.
            ManualParentAuthProofRegistry.completeAuthenticationSuccess(
                accountId = committed.accountId,
                sessionId = committed.sessionId,
            )
        } catch (error: Throwable) {
            ManualParentAuthProofRegistry.completeAuthenticationFailure()
            withContext(NonCancellable + Dispatchers.IO) {
                synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                    try {
                        vault.clear()
                    } catch (removalFailure: CredentialEnvelopeRemovalException) {
                        // The operation is already failing; keep the original cause primary and
                        // record that the durable credential envelope could not be removed.
                        error.addSuppressed(removalFailure)
                    }
                    accountSessionStore.clearActiveSession()
                    AuthenticatedSessionRegistry.clear()
                }
            }
            throw error
        }
        return AuthenticationStartResult.Completed(session)
    }

    /**
     * Applies the user's explicit ambiguity decision. [selectedAccountId] null means the user
     * confirmed a different subscription; otherwise it must still be a current candidate. Fresh
     * ownership resolution, decision validation, the final accountId selection and the
     * trusted-host/session commit all execute inside the same [ACCOUNT_SESSION_COMMIT_LOCK]
     * ownership transaction, so a stale or late result can never rebind ownership.
     */
    internal suspend fun completeAuthenticationDecision(
        pending: PendingAccountIdentityDecision,
        selectedAccountId: String?,
        remember: Boolean,
    ): AuthenticatedSession {
        var recordedMetadata: AccountSessionMetadata? = null
        val metadata = try {
            withContext(Dispatchers.IO) {
                synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                    requireCurrentPendingDecisionLocked(pending)
                    val recorded = accountSessionStore.commitAccountIdentityDecision(
                        username = pending.username,
                        portalBaseUrl = pending.portalBaseUrl,
                        selectedAccountId = selectedAccountId,
                        session = pending.session,
                    )
                    recordedMetadata = recorded
                    AuthenticatedSessionRegistry.update(pending.session, recorded)
                    if (remember) vault.save(pending.session.credentials) else vault.clear()
                    PendingAuthenticationDecisionRegistry.invalidate(pending.attempt)
                    recorded
                }
            }
        } catch (stale: StaleAccountIdentityDecisionException) {
            // The decision no longer applies to current ownership. Fail closed without touching
            // any session or scope state that may belong to a newer valid owner.
            PendingAuthenticationDecisionRegistry.invalidate(pending.attempt)
            ManualParentAuthProofRegistry.completeAuthenticationFailure()
            throw stale
        } catch (cancelled: CancellationException) {
            ManualParentAuthProofRegistry.completeAuthenticationFailure()
            cleanupFailedDecision(
                pending = pending,
                recorded = recordedMetadata,
                error = cancelled,
            )
            throw cancelled
        } catch (error: Throwable) {
            ManualParentAuthProofRegistry.completeAuthenticationFailure()
            cleanupFailedDecision(
                pending = pending,
                recorded = recordedMetadata,
                error = error,
            )
            throw error
        }
        ManualParentAuthProofRegistry.completeAuthenticationSuccess(
            accountId = metadata.accountId,
            sessionId = metadata.sessionId,
        )
        return pending.session
    }

    /**
     * Cancel/Back leaves no completed association: no binding, no trusted host and no persisted
     * credentials for the undecided identity. The retained access code is preserved separately.
     * A stale decision returns false without touching the newer session it no longer owns.
     */
    internal suspend fun abandonPendingAuthentication(
        pending: PendingAccountIdentityDecision,
    ): Boolean = withContext(Dispatchers.IO) {
        val owned = synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
            runCatching { requireCurrentPendingDecisionLocked(pending) }.isSuccess
        }
        if (!owned) {
            PendingAuthenticationDecisionRegistry.invalidate(pending.attempt)
            return@withContext false
        }
        ManualParentAuthProofRegistry.completeAuthenticationFailure()
        synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
            if (runCatching { requireCurrentPendingDecisionLocked(pending) }.isFailure) {
                PendingAuthenticationDecisionRegistry.invalidate(pending.attempt)
                return@synchronized false
            }
            PendingAuthenticationDecisionRegistry.invalidate(pending.attempt)
            suspendExistingDownloadOwner()
            withDurableCredentialsRemoved(vault::clear) {
                accountSessionStore.clearActiveSession()
                AuthenticatedSessionRegistry.clear()
            }
            true
        }
    }

    private fun requireCurrentPendingDecisionLocked(pending: PendingAccountIdentityDecision) {
        if (!PendingAuthenticationDecisionRegistry.isCurrent(pending.attempt)) {
            throw StaleAccountIdentityDecisionException()
        }
        if (accountSessionStore.metadata()?.sessionId != pending.priorSessionId) {
            throw StaleAccountIdentityDecisionException()
        }
    }

    private suspend fun cleanupFailedDecision(
        pending: PendingAccountIdentityDecision,
        recorded: AccountSessionMetadata?,
        error: Throwable,
    ) {
        withContext(NonCancellable + Dispatchers.IO) {
            synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                val currentSessionId = accountSessionStore.metadata()?.sessionId
                val ownsCurrentSession = if (recorded != null) {
                    currentSessionId == recorded.sessionId
                } else {
                    currentSessionId == pending.priorSessionId && pending.priorSessionId == null
                }
                val attemptStillCurrent = PendingAuthenticationDecisionRegistry.isCurrent(pending.attempt)
                if (attemptStillCurrent && ownsCurrentSession) {
                    try {
                        vault.clear()
                    } catch (removalFailure: CredentialEnvelopeRemovalException) {
                        // The operation is already failing; keep the original cause primary and
                        // record that the durable credential envelope could not be removed.
                        error.addSuppressed(removalFailure)
                    }
                    accountSessionStore.clearActiveSession()
                    AuthenticatedSessionRegistry.clear()
                }
                PendingAuthenticationDecisionRegistry.invalidate(pending.attempt)
            }
        }
    }

    /**
     * Commits a resolved identity and publishes its process-local ownership. Must be called while
     * holding [ACCOUNT_SESSION_COMMIT_LOCK] so the metadata/trust write, the session registry and
     * the credential vault all change as one serialized ownership transaction.
     */
    private fun commitAuthenticatedSessionLocked(
        session: AuthenticatedSession,
        accountId: String,
        remember: Boolean,
    ): AccountSessionMetadata {
        PendingAuthenticationDecisionRegistry.invalidate()
        if (remember) {
            val recorded = accountSessionStore.recordAuthenticated(session, accountId)
            AuthenticatedSessionRegistry.update(session, recorded)
            vault.save(session.credentials)
            return recorded
        }
        // A no-remember authentication must confirm durable credential removal before any
        // durable session metadata is committed, so a failed removal can never leave a session
        // that reports remembered credentials as erased.
        return withDurableCredentialsRemoved(vault::clear) {
            val recorded = accountSessionStore.recordAuthenticated(session, accountId)
            AuthenticatedSessionRegistry.update(session, recorded)
            recorded
        }
    }

    suspend fun reauthenticate(session: AuthenticatedSession): AuthenticatedSession {
        val owner = withContext(Dispatchers.IO) { currentSessionOwner() }
            ?: throw staleReauthentication()
        val refreshed = try {
            val portal = portalResolver.resolve(session.credentials.accessCode)
            client.authenticate(portal, session.credentials)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            ManualParentAuthProofRegistry.completeAuthenticationFailure()
            val ownerStillCurrent = withContext(Dispatchers.IO) { isCurrentSessionOwner(owner) }
            if (!ownerStillCurrent) throw staleReauthentication()
            throw error
        }

        val metadata = withContext(Dispatchers.IO) {
            synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                if (!matchesCurrentSessionOwner(owner)) throw staleReauthentication()
                val continuationAccountId = accountSessionStore.continuationAccountId(
                    username = refreshed.credentials.username,
                    portalBaseUrl = refreshed.portal.baseUrl,
                    currentAccountId = owner.accountId,
                ) ?: throw staleReauthentication()
                val recorded = accountSessionStore.recordAuthenticated(
                    refreshed,
                    continuationAccountId,
                )
                AuthenticatedSessionRegistry.update(refreshed, recorded)
                PendingAuthenticationDecisionRegistry.invalidate()
                recorded
            }
        }
        ManualParentAuthProofRegistry.onSessionReplacement(
            accountId = metadata.accountId,
            sessionId = metadata.sessionId,
        )
        return refreshed
    }

    suspend fun savedCredentials(): Credentials? = withContext(Dispatchers.IO) {
        val credentials = vault.load()
        if (credentials == null) {
            ManualParentAuthProofRegistry.invalidateAll()
            synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                PendingAuthenticationDecisionRegistry.invalidate()
                suspendExistingDownloadOwner()
                accountSessionStore.clearActiveSession()
                AuthenticatedSessionRegistry.clear()
            }
        }
        credentials
    }

    fun activeAccountSession(): AccountSessionMetadata? = accountSessionStore.metadata()

    /**
     * Reuses the existing non-secret installation identifier (already shared with successful
     * authentication) for pairing startup. Read-or-create runs under the same ownership lock so a
     * concurrent authentication commit cannot create a second identifier authority.
     */
    internal suspend fun pairingInstallationId(): String? = withContext(Dispatchers.IO) {
        synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
            accountSessionStore.installationIdForPairing()
        }
    }

    internal suspend fun presenceSessionSnapshot(
        expectedSession: AuthenticatedSession,
        device: PresenceDeviceMetadata,
        app: PresenceAppMetadata,
    ): PresenceSessionSnapshot? = withContext(Dispatchers.IO) {
        synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
            val owner = AuthenticatedSessionRegistry.currentOwner()
                ?.takeIf { it.session === expectedSession }
                ?: return@synchronized null
            val metadata = accountSessionStore.metadata()
                ?.takeIf { it.sessionId == owner.sessionId && it.accountId == owner.accountId }
                ?: return@synchronized null
            PresenceSessionSnapshot(
                sessionId = metadata.sessionId,
                installationId = metadata.installationId,
                accessCode = expectedSession.credentials.accessCode,
                iptvUsername = expectedSession.credentials.username,
                iptvPassword = expectedSession.credentials.password,
                host = expectedSession.portal.baseUrl,
                authenticatedAtEpochMs = metadata.authenticatedAtEpochMs,
                device = device,
                app = app,
            )
        }
    }

    suspend fun currentAuthenticatedSession(): AuthenticatedSession? {
        AuthenticatedSessionRegistry.current()?.let { return it }
        val restoration = withContext(Dispatchers.IO) {
            val credentials = vault.load() ?: return@withContext null
            val owner = currentSessionOwner() ?: return@withContext null
            credentials to owner
        } ?: return null
        val (credentials, owner) = restoration

        repeat(2) { attempt ->
            val restored = try {
                val portal = portalResolver.resolve(credentials.accessCode)
                client.authenticate(portal, credentials)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
            if (restored != null) {
                val metadata = withContext(Dispatchers.IO) {
                    synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                        if (!matchesCurrentSessionOwner(owner)) {
                            null
                        } else {
                            val continuationAccountId = accountSessionStore.continuationAccountId(
                                username = restored.credentials.username,
                                portalBaseUrl = restored.portal.baseUrl,
                                currentAccountId = owner.accountId,
                            )
                            if (continuationAccountId == null) {
                                null
                            } else {
                                val recorded = accountSessionStore.recordAuthenticated(
                                    restored,
                                    continuationAccountId,
                                )
                                AuthenticatedSessionRegistry.update(restored, recorded)
                                PendingAuthenticationDecisionRegistry.invalidate()
                                recorded
                            }
                        }
                    }
                }
                if (metadata != null) {
                    ManualParentAuthProofRegistry.onSessionReplacement(
                        accountId = metadata.accountId,
                        sessionId = metadata.sessionId,
                    )
                    return restored
                }
                return null
            }
            if (attempt == 0) delay(350L)
        }
        return null
    }

    /**
     * Restores the session needed by a durable download worker without collapsing a
     * missing credential or an invalid subscription into a transient retry.
     */
    internal suspend fun restoreDurableDownloadSession(): DurableDownloadSessionRestoreResult {
        AuthenticatedSessionRegistry.current()?.let {
            return DurableDownloadSessionRestoreResult.Authenticated(it)
        }
        val credentials = withContext(Dispatchers.IO) { vault.load() }
            ?: return DurableDownloadSessionRestoreResult.NoCredentials
        val owner = withContext(Dispatchers.IO) { currentSessionOwner() }
            ?: return DurableDownloadSessionRestoreResult.PermanentAuthFailure
        val restored = try {
            val portal = portalResolver.resolve(credentials.accessCode)
            client.authenticate(portal, credentials)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            return durableDownloadSessionRestoreFailure(error)
        }
        val metadata = withContext(Dispatchers.IO) {
            synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                if (!matchesCurrentSessionOwner(owner)) {
                    null
                } else {
                    val continuationAccountId = accountSessionStore.continuationAccountId(
                        username = restored.credentials.username,
                        portalBaseUrl = restored.portal.baseUrl,
                        currentAccountId = owner.accountId,
                    )
                    if (continuationAccountId == null) {
                        null
                    } else {
                        val recorded = accountSessionStore.recordAuthenticated(
                            restored,
                            continuationAccountId,
                        )
                        AuthenticatedSessionRegistry.update(restored, recorded)
                        PendingAuthenticationDecisionRegistry.invalidate()
                        recorded
                    }
                }
            }
        } ?: return DurableDownloadSessionRestoreResult.PermanentAuthFailure
        ManualParentAuthProofRegistry.onSessionReplacement(
            accountId = metadata.accountId,
            sessionId = metadata.sessionId,
        )
        return DurableDownloadSessionRestoreResult.Authenticated(restored)
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        ManualParentAuthProofRegistry.invalidateAll()
        synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
            PendingAuthenticationDecisionRegistry.invalidate()
            suspendExistingDownloadOwner()
            withDurableCredentialsRemoved(vault::clear) {
                accountSessionStore.clearActiveSession()
                AuthenticatedSessionRegistry.clear()
            }
        }
    }

    /**
     * Owner-scoped expiry cleanup. The registry owner is checked under the same commit lock that
     * serializes every production session commit, so a newer owner cannot be erased by a delayed
     * expired-session result.
     */
    internal suspend fun logoutIfOwnedBy(expectedOwner: AuthenticatedSessionOwner): Boolean =
        withContext(Dispatchers.IO) {
            synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                if (AuthenticatedSessionRegistry.currentOwner() !== expectedOwner) {
                    return@synchronized false
                }
                ManualParentAuthProofRegistry.invalidateAll()
                PendingAuthenticationDecisionRegistry.invalidate()
                suspendExistingDownloadOwner()
                withDurableCredentialsRemoved(vault::clear) {
                    accountSessionStore.clearActiveSession()
                    AuthenticatedSessionRegistry.clear()
                }
                true
            }
        }

    suspend fun catalog(session: AuthenticatedSession, type: ContentType): Catalog =
        client.catalog(session, type)

    suspend fun verifiedKidsCatalog(session: AuthenticatedSession): VerifiedKidsCatalogSnapshot {
        val owner = withContext(Dispatchers.IO) {
            synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                AuthenticatedSessionRegistry.currentOwner()?.takeIf { it.session === session }
            }
        } ?: throw CancellationException("Stale Kids catalog session")
        var snapshot = kidsCatalogClient.loadVerified(session)
        var retryIndex = 0
        while (snapshot.hasOnlyTransientKidsFailures() && retryIndex < 2) {
            delay(if (retryIndex == 0) 450L else 900L)
            snapshot = kidsCatalogClient.loadVerified(session)
            retryIndex++
        }
        val persisted = runKidsSnapshotPersistenceOffMain {
            synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                if (!AuthenticatedSessionRegistry.isCurrent(owner)) {
                    throw CancellationException("Stale Kids catalog owner")
                }
                kidsContentFilterStore.replace(owner, snapshot)
            }
        }
        if (!persisted) {
            return VerifiedKidsCatalogSnapshot(
                catalogs = emptyMap(),
                blockedTypes = ContentType.entries.associateWith {
                    "تعذر تثبيت فلترة الأطفال محليا بأمان"
                },
            )
        }
        val ownerStillCurrent = withContext(Dispatchers.IO) {
            synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                AuthenticatedSessionRegistry.isCurrent(owner)
            }
        }
        if (!ownerStillCurrent) throw CancellationException("Stale Kids catalog owner")
        return snapshot
    }

    suspend fun episodes(session: AuthenticatedSession, seriesId: Int): List<Episode> =
        client.episodes(session, seriesId)

    suspend fun contentDetails(session: AuthenticatedSession, movieId: Int): ContentDetails =
        client.contentDetails(session, movieId)

    suspend fun movieCardMetadata(
        session: AuthenticatedSession,
        movieId: Int,
    ): MovieCardTechnicalMetadata =
        movieCardMetadataClient.fetch(session, movieId)

    suspend fun seriesCardMetadata(
        session: AuthenticatedSession,
        seriesId: Int,
    ): SeriesCardTechnicalMetadata =
        seriesCardMetadataClient.fetch(session, seriesId)

    suspend fun seriesBundle(session: AuthenticatedSession, seriesId: Int): SeriesBundle =
        client.seriesBundle(session, seriesId)

    fun playback(session: AuthenticatedSession, item: ContentItem): PlaybackRequest =
        client.playback(session, item)

    fun playback(
        session: AuthenticatedSession,
        series: ContentItem,
        episode: Episode,
    ): PlaybackRequest = client.playback(session, series, episode).copy(
        parentContentId = series.id,
    )

    fun playback(session: AuthenticatedSession, entry: HistoryEntry): PlaybackRequest =
        client.playback(session, entry).copy(parentContentId = entry.parentContentId)

    suspend fun diagnose(
        session: AuthenticatedSession,
        onProgress: (progress: Int, stage: String) -> Unit,
    ): ServerDiagnosticsReport {
        val baseReport = diagnostics.run(session, onProgress)
        val kidsSnapshot = kidsCatalogClient.loadVerified(session)
        val capabilities = baseReport.capabilities
            .filterNot { it.id == KIDS_SERVER_CATALOG_CAPABILITY_ID } +
            kidsSnapshot.capabilityFinding()
        val recommendations = (
            baseReport.recommendations
                .filterNot { it.title == LEGACY_PARENTAL_RECOMMENDATION_TITLE } +
                kidsSnapshot.recommendation()
            ).sortedWith(compareBy({ it.priority }, { it.title }))
        return baseReport.copy(
            capabilities = capabilities,
            recommendations = recommendations,
        )
    }

    private fun suspendExistingDownloadOwner() {
        accountSessionStore.metadata()?.accountId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { accountId ->
                suspendDownloadOwner?.invoke(accountId)
                    ?: DownloadRepositoryProcessOwner.suspendAccount(appContext, accountId)
            }
    }

    private fun currentSessionOwner(): AccountSessionOwner? =
        synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
            accountSessionStore.metadata()?.let {
                AccountSessionOwner(accountId = it.accountId, sessionId = it.sessionId)
            }
        }

    private fun isCurrentSessionOwner(owner: AccountSessionOwner): Boolean =
        synchronized(ACCOUNT_SESSION_COMMIT_LOCK) { matchesCurrentSessionOwner(owner) }

    private fun matchesCurrentSessionOwner(owner: AccountSessionOwner): Boolean {
        val current = accountSessionStore.metadata() ?: return false
        return current.accountId == owner.accountId && current.sessionId == owner.sessionId
    }

    private fun staleReauthentication(): CancellationException =
        CancellationException("Stale account reauthentication result")
}

internal sealed interface DurableDownloadSessionRestoreResult {
    data class Authenticated(val session: AuthenticatedSession) : DurableDownloadSessionRestoreResult
    data object NoCredentials : DurableDownloadSessionRestoreResult
    data object PermanentAuthFailure : DurableDownloadSessionRestoreResult
    data object TransientFailure : DurableDownloadSessionRestoreResult
}

internal sealed interface AuthenticationStartResult {
    data class Completed(val session: AuthenticatedSession) : AuthenticationStartResult
    data class DecisionRequired(
        val pending: PendingAccountIdentityDecision,
    ) : AuthenticationStartResult
}

internal data class PendingAccountIdentityDecision(
    val session: AuthenticatedSession,
    val username: String,
    val portalBaseUrl: String,
    val candidates: List<TrustedAccountIdentity>,
    val attempt: AuthenticationDecisionAttempt,
    val priorSessionId: String?,
)

internal fun durableDownloadSessionRestoreFailure(
    error: Throwable,
): DurableDownloadSessionRestoreResult = when (error) {
    XtreamException.InvalidCredentials,
    XtreamException.SubscriptionInactive,
    PortalException.ConfigurationMissing,
    PortalException.InvalidAccessCode,
    PortalException.ResellerInactive,
    PortalException.InvalidHost,
    -> DurableDownloadSessionRestoreResult.PermanentAuthFailure

    else -> DurableDownloadSessionRestoreResult.TransientFailure
}

private fun VerifiedKidsCatalogSnapshot.hasOnlyTransientKidsFailures(): Boolean {
    if (isAvailable || blockedTypes.isEmpty()) return false
    return blockedTypes.values.all(::isTransientKidsFailure)
}

private fun isTransientKidsFailure(reason: String): Boolean {
    if (reason == "تعذر الاتصال بالسيرفر" || reason == "استجابة الفئات غير صالحة") return true
    val httpCode = reason.removePrefix("فشل API بكود ").takeIf { it != reason }?.toIntOrNull()
        ?: return false
    return httpCode == 408 || httpCode == 425 || httpCode == 429 || httpCode in 500..599
}
