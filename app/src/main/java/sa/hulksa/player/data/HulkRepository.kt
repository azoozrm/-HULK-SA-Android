package sa.hulksa.player.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
import sa.hulksa.player.security.CredentialVault

private val ACCOUNT_SESSION_COMMIT_LOCK = Any()

private data class AccountSessionOwner(
    val accountId: String,
    val sessionId: String,
)

class HulkRepository(context: Context) {
    private val appContext = context.applicationContext
    private val portalResolver = PortalResolver()
    private val client = XtreamClient()
    private val kidsCatalogClient = KidsServerCatalogClient()
    private val kidsContentFilterStore = KidsContentFilterStore(appContext)
    private val movieCardMetadataClient = MovieCardMetadataClient()
    private val seriesCardMetadataClient = SeriesCardMetadataClient()
    private val vault = CredentialVault(appContext)
    private val accountSessionStore = AccountSessionStore(appContext)
    private val diagnostics = ServerDiagnosticsEngine(appContext)

    suspend fun login(credentials: Credentials, remember: Boolean): AuthenticatedSession {
        val session = try {
            val portal = portalResolver.resolve(credentials.accessCode)
            client.authenticate(portal, credentials)
        } catch (error: Throwable) {
            ManualParentAuthProofRegistry.completeAuthenticationFailure()
            throw error
        }

        try {
            val metadata = synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                suspendExistingDownloadOwner()
                val recorded = accountSessionStore.recordAuthenticated(session)
                AuthenticatedSessionRegistry.update(session)
                if (remember) vault.save(credentials) else vault.clear()
                recorded
            }
            // The gate marks an attempt as manual only when it originated from HulkViewModel.login().
            // Startup restore reaches authenticate() directly, so this call records current ownership
            // but intentionally creates no parent-bootstrap proof for restored credentials.
            ManualParentAuthProofRegistry.completeAuthenticationSuccess(
                accountId = metadata.accountId,
                sessionId = metadata.sessionId,
            )
        } catch (error: Throwable) {
            ManualParentAuthProofRegistry.completeAuthenticationFailure()
            synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                vault.clear()
                accountSessionStore.clearActiveSession()
                AuthenticatedSessionRegistry.clear()
            }
            throw error
        }
        return session
    }

    suspend fun reauthenticate(session: AuthenticatedSession): AuthenticatedSession {
        val owner = currentSessionOwner() ?: throw staleReauthentication()
        val refreshed = try {
            val portal = portalResolver.resolve(session.credentials.accessCode)
            client.authenticate(portal, session.credentials)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            ManualParentAuthProofRegistry.completeAuthenticationFailure()
            if (!isCurrentSessionOwner(owner)) throw staleReauthentication()
            throw error
        }

        val metadata = synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
            if (!matchesCurrentSessionOwner(owner)) throw staleReauthentication()
            val recorded = accountSessionStore.recordAuthenticated(refreshed)
            AuthenticatedSessionRegistry.update(refreshed)
            recorded
        }
        ManualParentAuthProofRegistry.onSessionReplacement(
            accountId = metadata.accountId,
            sessionId = metadata.sessionId,
        )
        return refreshed
    }

    fun savedCredentials(): Credentials? {
        val credentials = vault.load()
        if (credentials == null) {
            ManualParentAuthProofRegistry.invalidateAll()
            synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                suspendExistingDownloadOwner()
                accountSessionStore.clearActiveSession()
                AuthenticatedSessionRegistry.clear()
            }
        }
        return credentials
    }

    fun activeAccountSession(): AccountSessionMetadata? = accountSessionStore.metadata()

    suspend fun currentAuthenticatedSession(): AuthenticatedSession? {
        AuthenticatedSessionRegistry.current()?.let { return it }
        val credentials = vault.load() ?: return null
        val owner = currentSessionOwner() ?: return null

        repeat(2) { attempt ->
            val restored = runCatching {
                val portal = portalResolver.resolve(credentials.accessCode)
                client.authenticate(portal, credentials)
            }.getOrNull()
            if (restored != null) {
                val metadata = synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
                    if (!matchesCurrentSessionOwner(owner)) {
                        null
                    } else {
                        val recorded = accountSessionStore.recordAuthenticated(restored)
                        AuthenticatedSessionRegistry.update(restored)
                        recorded
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

    fun logout() {
        ManualParentAuthProofRegistry.invalidateAll()
        synchronized(ACCOUNT_SESSION_COMMIT_LOCK) {
            suspendExistingDownloadOwner()
            vault.clear()
            accountSessionStore.clearActiveSession()
            AuthenticatedSessionRegistry.clear()
        }
    }

    suspend fun catalog(session: AuthenticatedSession, type: ContentType): Catalog =
        client.catalog(session, type)

    suspend fun verifiedKidsCatalog(session: AuthenticatedSession): VerifiedKidsCatalogSnapshot {
        var snapshot = kidsCatalogClient.loadVerified(session)
        var retryIndex = 0
        while (snapshot.hasOnlyTransientKidsFailures() && retryIndex < 2) {
            delay(if (retryIndex == 0) 450L else 900L)
            snapshot = kidsCatalogClient.loadVerified(session)
            retryIndex++
        }
        if (!kidsContentFilterStore.replace(snapshot)) {
            return VerifiedKidsCatalogSnapshot(
                catalogs = emptyMap(),
                blockedTypes = ContentType.entries.associateWith {
                    "تعذر تثبيت فلترة الأطفال محليا بأمان"
                },
            )
        }
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
            ?.let { DownloadRepositoryProcessOwner.suspendAccount(appContext, it) }
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
