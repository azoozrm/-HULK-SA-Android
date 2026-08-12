package sa.hulksa.player.data

import android.content.Context
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
            if (remember) vault.save(credentials) else vault.clear()
        } catch (error: Throwable) {
            vault.clear()
            accountSessionStore.clearActiveSession()
            throw error
        }
        return session
    }

    suspend fun reauthenticate(session: AuthenticatedSession): AuthenticatedSession {
        val portal = portalResolver.resolve()
        val refreshed = client.authenticate(portal, session.credentials)
        accountSessionStore.recordAuthenticated(refreshed)
        return refreshed
    }

    fun savedCredentials(): Credentials? {
        val credentials = vault.load()
        if (credentials == null) accountSessionStore.clearActiveSession()
        return credentials
    }

    fun activeAccountSession(): AccountSessionMetadata? = accountSessionStore.metadata()

    fun logout() {
        vault.clear()
        accountSessionStore.clearActiveSession()
    }

    suspend fun catalog(session: AuthenticatedSession, type: ContentType): Catalog =
        client.catalog(session, type)

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
    ): ServerDiagnosticsReport = diagnostics.run(session, onProgress)
}
