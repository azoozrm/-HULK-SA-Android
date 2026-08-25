package sa.hulksa.player.data

import android.app.Application
import android.content.Context
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.DownloadScheduleMode
import sa.hulksa.player.model.DownloadSettings
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.model.PlaybackRequest

/**
 * Process-local owner for the stateful transfer engine. WorkManager runs in the
 * application process by default, so one instance prevents parallel writers from
 * touching the same owner-scoped partial file.
 */
internal object DownloadRepositoryProcessOwner {
    @Volatile
    private var instance: DownloadRepository? = null

    fun get(context: Context): DownloadRepository {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: DownloadRepository(context.applicationContext).also { created ->
                instance = created
            }
        }
    }
}

/**
 * UI-facing account + profile ownership boundary. Every read and mutation is
 * checked against the currently authenticated account and active profile.
 */
internal class ProfileScopedDownloadRepository(context: Context) {
    private val appContext = context.applicationContext
    private val delegate = DownloadRepositoryProcessOwner.get(appContext)
    private val profileStore = ProfileStore(appContext)
    private val accountSessionStore = AccountSessionStore(appContext)

    suspend fun initialize() {
        delegate.initialize()
    }

    fun downloads(): List<OfflineDownload> = visibleDownloads(
        records = delegate.downloads(),
        owner = activeOwner(),
    )

    fun activeOwnerToken(): String? = activeOwner()?.let(::downloadOwnerStorageKey)

    fun settings(): DownloadSettings = delegate.settings()

    fun setWifiOnly(enabled: Boolean): DownloadSettings = delegate.setWifiOnly(enabled)

    fun setScheduleMode(mode: DownloadScheduleMode): DownloadSettings {
        val accountId = activeOwner()?.accountId ?: return delegate.settings()
        return delegate.setScheduleMode(mode, accountId)
    }

    fun setConcurrentDownloads(count: Int): DownloadSettings = delegate.setConcurrentDownloads(count)

    fun cyclePriority(downloadId: Long): List<OfflineDownload> {
        activeOwner()?.let { delegate.cyclePriority(downloadId, it) }
        return downloads()
    }

    fun enqueue(
        request: PlaybackRequest,
        seriesTitle: String? = null,
        season: Int? = null,
        episodeNumber: Int? = null,
    ): DownloadRepository.EnqueueResult {
        val owner = activeOwner()
            ?: return DownloadRepository.EnqueueResult.Failed("سجل الدخول لاختيار مالك التحميل بأمان.")
        return delegate.enqueue(
            owner = owner,
            request = request,
            seriesTitle = seriesTitle,
            season = season,
            episodeNumber = episodeNumber,
        )
    }

    fun pause(downloadId: Long): List<OfflineDownload> {
        activeOwner()?.let { delegate.pause(downloadId, it) }
        return downloads()
    }

    fun resume(downloadId: Long): Boolean {
        val owner = activeOwner() ?: return false
        return delegate.resume(downloadId, owner)
    }

    fun remove(downloadId: Long): List<OfflineDownload> {
        activeOwner()?.let { delegate.remove(downloadId, it) }
        return downloads()
    }

    fun canAccess(downloadId: Long): Boolean {
        val owner = activeOwner() ?: return false
        return delegate.owns(downloadId, owner)
    }

    fun playableLocalUri(downloadId: Long): String? {
        val owner = activeOwner() ?: return null
        return delegate.playableLocalUri(downloadId, owner)
    }

    fun canAccess(downloadId: Long, expectedOwner: DownloadOwner): Boolean {
        val active = activeOwner() ?: return false
        val expected = expectedOwner.normalizedOrNull() ?: return false
        if (active != expected) return false
        return delegate.owns(downloadId, expected)
    }

    fun suspendActiveAccountForLogout() {
        val accountId = accountSessionStore.metadata()?.accountId
            ?: accountSessionStore.activeAccountId()
            ?: return
        delegate.suspendAccount(accountId)
    }

    suspend fun onAccountAuthenticated(): List<OfflineDownload> {
        delegate.initialize()
        val session = AuthenticatedSessionRegistry.current() ?: return emptyList()
        val metadata = accountSessionStore.metadata() ?: return emptyList()
        if (!authenticatedSessionMatchesMetadata(session, metadata)) return emptyList()
        delegate.suspendAccountsExcept(metadata.accountId)
        delegate.suspendAccount(metadata.accountId)
        val existingProfileIds = profileStore.profiles().mapTo(mutableSetOf()) { it.id }
        delegate.recordsForAccount(metadata.accountId).forEach { item ->
            if (item.profileId !in existingProfileIds) return@forEach
            val sources = if (item.status == OfflineStatus.COMPLETED) {
                emptyList()
            } else {
                runCatching { resolveDownloadSources(session, item) }.getOrDefault(emptyList())
            }
            delegate.prepareForAuthenticatedOwner(
                downloadId = item.downloadId,
                owner = item.owner(),
                refreshedSources = sources,
            )
        }
        return downloads()
    }

    fun ownerForProfileCleanup(profileId: String): DownloadOwner? {
        val session = AuthenticatedSessionRegistry.current() ?: return null
        val metadata = accountSessionStore.metadata() ?: return null
        if (!authenticatedSessionMatchesMetadata(session, metadata)) return null
        return DownloadOwner(metadata.accountId, profileId).normalizedOrNull()
    }

    fun removeProfile(owner: DownloadOwner): List<OfflineDownload> {
        delegate.removeProfile(owner)
        return downloads()
    }

    private fun activeOwner(): DownloadOwner? {
        val session = AuthenticatedSessionRegistry.current() ?: return null
        val metadata = accountSessionStore.metadata() ?: return null
        if (!authenticatedSessionMatchesMetadata(session, metadata)) return null
        return DownloadOwner(metadata.accountId, profileStore.activeProfileId()).normalizedOrNull()
    }
}

internal fun authenticatedSessionMatchesMetadata(
    session: AuthenticatedSession,
    metadata: AccountSessionMetadata,
): Boolean =
    normalizedDownloadAccountId(metadata.accountId) != null &&
        !metadata.isExpired() &&
        metadata.username == session.credentials.username.trim() &&
        metadata.portalBaseUrl.trim().trimEnd('/') == session.portal.baseUrl.trim().trimEnd('/')

internal fun resolveDownloadSources(
    session: AuthenticatedSession,
    item: OfflineDownload,
): List<String> = XtreamClient().playback(
    session,
    HistoryEntry(
        key = item.historyKey,
        title = item.title,
        posterUrl = item.posterUrl,
        streamKind = item.streamKind,
        streamId = item.streamId,
        extension = item.extension,
        isLive = false,
        positionMs = 0L,
        durationMs = 0L,
        updatedAtEpochMs = System.currentTimeMillis(),
        seriesTitle = item.seriesTitle,
        season = item.season,
        episodeNumber = item.episodeNumber,
    ),
).candidates

/** More-specific overload used by AndroidViewModel callers. */
@Suppress("FunctionName")
internal fun DownloadRepository(application: Application): ProfileScopedDownloadRepository =
    ProfileScopedDownloadRepository(application)
