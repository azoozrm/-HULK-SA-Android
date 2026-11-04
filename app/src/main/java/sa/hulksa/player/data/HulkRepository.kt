package sa.hulksa.player.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
        beginAuthentication(credentials, remember)

    /** Real provider authentication is required before automatic local-owner continuation. */
    internal suspend fun beginAuthentication(
        credentials: Credentials,
        remember: Boolean,
    ): AuthenticatedSession {
        var attempt: AuthenticationAttempt? = null
        try {
            return withContext(Dispatchers.IO) {
                val operation = currentCoroutineContext()
                val ownedAttempt = synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                    operation.ensureActive()
                    AuthenticationAttemptRegistry.begin().also { attempt = it }
                }
                val portal = portalResolver.resolve(credentials.accessCode)
                val session = client.authenticate(portal, credentials)
                synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                    fun requireCurrentAttempt() {
                        operation.ensureActive()
                        if (!AuthenticationAttemptRegistry.isCurrent(ownedAttempt)) {
                            throw CancellationException("Stale authentication result")
                        }
                    }
                    requireCurrentAttempt()
                    val resolution = accountSessionStore.resolveAuthenticationIdentity(
                        username = session.credentials.username,
                        portalBaseUrl = session.portal.baseUrl,
                    )
                    val accountId = when (resolution) {
                        is AccountIdentityResolution.Known -> resolution.accountId
                        is AccountIdentityResolution.New -> resolution.accountId
                    }
                    suspendExistingDownloadOwner()
                    val recorded = commitAuthenticatedSessionLocked(session, accountId, remember, ::requireCurrentAttempt)
                    // Proof publication is part of the same current-attempt transaction. A delayed
                    // older success/failure can never overwrite or revoke a newer owner's proof.
                    ManualParentAuthProofRegistry.completeAuthenticationSuccess(recorded.accountId, recorded.sessionId)
                    AuthenticationAttemptRegistry.invalidate(ownedAttempt)
                    session
                }
            }
        } catch (error: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) {
                synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                    attempt?.takeIf(AuthenticationAttemptRegistry::isCurrent)?.let {
                        ManualParentAuthProofRegistry.completeAuthenticationFailure()
                        AuthenticationAttemptRegistry.invalidate(it)
                    }
                }
            }
            throw error
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
        requireCurrentAttempt: () -> Unit,
    ): AccountSessionMetadata {
        requireCurrentAttempt()
        requireUnexpiredAccountSession(session.account.expiresAtEpochSeconds, System.currentTimeMillis() / 1_000L)
        val previous = accountSessionStore.rollbackSnapshot()
        val previousCredentials = vault.load()
        var credentialsTouched = false
        var metadataTouched = false
        try {
            requireCurrentAttempt()
            credentialsTouched = true
            if (remember) vault.save(session.credentials) else vault.clear()
            metadataTouched = true
            val recorded = accountSessionStore.recordAuthenticated(session, accountId)
            requireCurrentAttempt()
            AuthenticatedSessionRegistry.update(session, recorded)
            return recorded
        } catch (error: Throwable) {
            if (metadataTouched) {
                runCatching { accountSessionStore.restore(previous) }.exceptionOrNull()?.let(error::addSuppressed)
            }
            if (credentialsTouched) {
                runCatching {
                    if (previousCredentials != null) vault.save(previousCredentials) else vault.clear()
                }.exceptionOrNull()?.let(error::addSuppressed)
            }
            // Registry publication is last, so the prior authenticated owner remains coherent.
            throw error
        }
    }

    suspend fun reauthenticate(session: AuthenticatedSession): AuthenticatedSession {
        val owner = withContext(Dispatchers.IO) {
            synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                if (AuthenticationAttemptRegistry.hasActiveAttempt()) return@synchronized null
                val registered = AuthenticatedSessionRegistry.currentOwner()
                    ?.takeIf { it.session === session } ?: return@synchronized null
                currentSessionOwner()?.takeIf {
                    it.accountId == registered.accountId && it.sessionId == registered.sessionId
                }
            }
        } ?: throw staleReauthentication()
        val refreshed = try {
            val portal = portalResolver.resolve(session.credentials.accessCode)
            client.authenticate(portal, session.credentials)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val ownerStillCurrent = withContext(Dispatchers.IO) {
                synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                    (matchesCurrentSessionOwner(owner) && !AuthenticationAttemptRegistry.hasActiveAttempt()).also { current ->
                        if (current) ManualParentAuthProofRegistry.completeAuthenticationFailure()
                    }
                }
            }
            if (!ownerStillCurrent) throw staleReauthentication()
            throw error
        }

        val metadata = withContext(Dispatchers.IO) {
            val operation = currentCoroutineContext()
            synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                operation.ensureActive()
                if (!matchesCurrentSessionOwner(owner) || AuthenticationAttemptRegistry.hasActiveAttempt()) throw staleReauthentication()
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
                AuthenticationAttemptRegistry.invalidate()
                ManualParentAuthProofRegistry.onSessionReplacement(recorded.accountId, recorded.sessionId)
                recorded
            }
        }
        return refreshed
    }

    suspend fun savedCredentials(): Credentials? = withContext(Dispatchers.IO) {
        synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
            if (AuthenticationAttemptRegistry.hasActiveAttempt()) return@synchronized null
            val credentials = vault.load()
            if (credentials == null) {
                ManualParentAuthProofRegistry.invalidateAll()
                AuthenticationAttemptRegistry.invalidate()
                suspendExistingDownloadOwner()
                accountSessionStore.clearActiveSession()
                AuthenticatedSessionRegistry.clear()
            }
            credentials
        }
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
            synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                if (AuthenticationAttemptRegistry.hasActiveAttempt()) return@synchronized null
                val credentials = vault.load() ?: return@synchronized null
                val owner = currentSessionOwner() ?: return@synchronized null
                credentials to owner
            }
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
                    val operation = currentCoroutineContext()
                    synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                        operation.ensureActive()
                        if (!matchesCurrentSessionOwner(owner) || AuthenticationAttemptRegistry.hasActiveAttempt()) {
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
                                AuthenticationAttemptRegistry.invalidate()
                                ManualParentAuthProofRegistry.onSessionReplacement(recorded.accountId, recorded.sessionId)
                                recorded
                            }
                        }
                    }
                }
                if (metadata != null) {
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
        val restoration = withContext(Dispatchers.IO) {
            synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                if (AuthenticationAttemptRegistry.hasActiveAttempt()) return@synchronized null
                vault.load() to currentSessionOwner()
            }
        } ?: return DurableDownloadSessionRestoreResult.TransientFailure
        val credentials = restoration.first ?: return DurableDownloadSessionRestoreResult.NoCredentials
        val owner = restoration.second ?: return DurableDownloadSessionRestoreResult.PermanentAuthFailure
        val restored = try {
            val portal = portalResolver.resolve(credentials.accessCode)
            client.authenticate(portal, credentials)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            return durableDownloadSessionRestoreFailure(error)
        }
        val metadata = withContext(Dispatchers.IO) {
            val operation = currentCoroutineContext()
            synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                operation.ensureActive()
                if (!matchesCurrentSessionOwner(owner) || AuthenticationAttemptRegistry.hasActiveAttempt()) {
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
                        AuthenticationAttemptRegistry.invalidate()
                        ManualParentAuthProofRegistry.onSessionReplacement(recorded.accountId, recorded.sessionId)
                        recorded
                    }
                }
            }
        } ?: return DurableDownloadSessionRestoreResult.PermanentAuthFailure
        return DurableDownloadSessionRestoreResult.Authenticated(restored)
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
            ManualParentAuthProofRegistry.invalidateAll()
            AuthenticationAttemptRegistry.invalidate()
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
                AuthenticationAttemptRegistry.invalidate()
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

internal fun durableDownloadSessionRestoreFailure(
    error: Throwable,
): DurableDownloadSessionRestoreResult = when (error) {
    XtreamException.InvalidCredentials,
    XtreamException.SubscriptionInactive,
    XtreamException.SubscriptionExpired,
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
