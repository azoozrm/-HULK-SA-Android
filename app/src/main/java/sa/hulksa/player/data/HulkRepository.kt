package sa.hulksa.player.data

import android.content.Context
import kotlinx.coroutines.delay
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

class HulkRepository(context: Context) {
    private val portalResolver = PortalResolver(context)
    private val client = XtreamClient()
    private val kidsCatalogClient = KidsServerCatalogClient()
    private val movieCardMetadataClient = MovieCardMetadataClient()
    private val seriesCardMetadataClient = SeriesCardMetadataClient()
    private val vault = CredentialVault(context)
    private val accountSessionStore = AccountSessionStore(context)
    private val diagnostics = ServerDiagnosticsEngine(context)

    suspend fun login(credentials: Credentials, remember: Boolean): AuthenticatedSession {
        val portal = portalResolver.resolve()
        val session = client.authenticate(portal, credentials)
        try {
            accountSessionStore.recordAuthenticated(session)
            AuthenticatedSessionRegistry.update(session)
            if (remember) vault.save(credentials) else vault.clear()
        } catch (error: Throwable) {
            vault.clear()
            accountSessionStore.clearActiveSession()
            AuthenticatedSessionRegistry.clear()
            throw error
        }
        return session
    }

    suspend fun reauthenticate(session: AuthenticatedSession): AuthenticatedSession {
        val portal = portalResolver.resolve()
        val refreshed = client.authenticate(portal, session.credentials)
        accountSessionStore.recordAuthenticated(refreshed)
        AuthenticatedSessionRegistry.update(refreshed)
        return refreshed
    }

    fun savedCredentials(): Credentials? {
        val credentials = vault.load()
        if (credentials == null) {
            accountSessionStore.clearActiveSession()
            AuthenticatedSessionRegistry.clear()
        }
        return credentials
    }

    fun activeAccountSession(): AccountSessionMetadata? = accountSessionStore.metadata()

    suspend fun currentAuthenticatedSession(): AuthenticatedSession? {
        AuthenticatedSessionRegistry.current()?.let { return it }
        val credentials = vault.load() ?: return null

        repeat(2) { attempt ->
            val restored = runCatching {
                val portal = portalResolver.resolve()
                client.authenticate(portal, credentials)
            }.getOrNull()
            if (restored != null) {
                accountSessionStore.recordAuthenticated(restored)
                AuthenticatedSessionRegistry.update(restored)
                return restored
            }
            if (attempt == 0) delay(350L)
        }
        return null
    }

    fun logout() {
        vault.clear()
        accountSessionStore.clearActiveSession()
        AuthenticatedSessionRegistry.clear()
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
    ): PlaybackRequest = client.playback(session, series, episode)

    fun playback(session: AuthenticatedSession, entry: HistoryEntry): PlaybackRequest =
        client.playback(session, entry)

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
