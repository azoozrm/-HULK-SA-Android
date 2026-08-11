package sa.hulksa.player.data

import android.app.Application
import android.content.Context
import sa.hulksa.player.model.DownloadScheduleMode
import sa.hulksa.player.model.DownloadSettings
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.PlaybackRequest

/**
 * Process-local owner for the stateful repository. WorkManager runs in the app
 * process by default, so sharing this instance prevents parallel OkHttp writers
 * from touching the same partial download file.
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
 * UI-facing facade over the single device-level download engine.
 *
 * The physical transfer queue remains shared by the device/process, while this
 * facade scopes visibility and ownership to the active profile. A physical file
 * can be owned by more than one profile when the same content is added from
 * multiple profiles, avoiding duplicate files while keeping each profile's
 * Downloads screen isolated.
 */
internal class ProfileScopedDownloadRepository(context: Context) {
    private val appContext = context.applicationContext
    private val delegate = DownloadRepositoryProcessOwner.get(appContext)
    private val profileStore = ProfileStore(appContext)
    private val ownershipStore = ProfileDownloadOwnershipStore(appContext)

    init {
        ownershipStore.migrateLegacy(delegate.downloads())
    }

    fun downloads(): List<OfflineDownload> = ProfileDownloadSnapshotList(
        allDownloads = delegate.downloads(),
        activeProfileId = profileStore::activeProfileId,
        ownersForExistingDownload = ownershipStore::ownersForExistingDownload,
    )

    fun settings(): DownloadSettings = delegate.settings()

    fun setWifiOnly(enabled: Boolean): DownloadSettings = delegate.setWifiOnly(enabled)

    fun setScheduleMode(mode: DownloadScheduleMode): DownloadSettings = delegate.setScheduleMode(mode)

    fun setConcurrentDownloads(count: Int): DownloadSettings = delegate.setConcurrentDownloads(count)

    fun cyclePriority(downloadId: Long): List<OfflineDownload> {
        if (owns(downloadId)) delegate.cyclePriority(downloadId)
        return downloads()
    }

    fun enqueue(
        request: PlaybackRequest,
        seriesTitle: String? = null,
        season: Int? = null,
        episodeNumber: Int? = null,
    ): DownloadRepository.EnqueueResult {
        val profileId = profileStore.activeProfileId()
        val existing = delegate.downloads().firstOrNull { it.historyKey == request.historyKey }
        val ownersBefore = if (existing != null) {
            ownershipStore.ownersForExistingDownload(request.historyKey)
        } else {
            ownershipStore.explicitOwners(request.historyKey).orEmpty()
        }
        val alreadyOwned = profileId in ownersBefore

        ownershipStore.addOwner(
            historyKey = request.historyKey,
            profileId = profileId,
            includeLegacyPrimary = existing != null,
        )

        return try {
            delegate.enqueue(
                request = request,
                seriesTitle = seriesTitle,
                season = season,
                episodeNumber = episodeNumber,
            ).also { result ->
                if (result is DownloadRepository.EnqueueResult.Failed && !alreadyOwned) {
                    ownershipStore.removeExplicitOwner(request.historyKey, profileId)
                }
            }
        } catch (error: Throwable) {
            if (!alreadyOwned) ownershipStore.removeExplicitOwner(request.historyKey, profileId)
            throw error
        }
    }

    fun pause(downloadId: Long): List<OfflineDownload> {
        if (owns(downloadId)) delegate.pause(downloadId)
        return downloads()
    }

    fun resume(downloadId: Long): Boolean = owns(downloadId) && delegate.resume(downloadId)

    fun remove(downloadId: Long): List<OfflineDownload> {
        val item = delegate.downloads().firstOrNull { it.downloadId == downloadId }
            ?: return downloads()
        val profileId = profileStore.activeProfileId()
        val owners = ownershipStore.ownersForExistingDownload(item.historyKey)
        if (profileId !in owners) return downloads()

        val remainingOwners = ownershipStore.removeExistingOwner(item.historyKey, profileId)
        if (remainingOwners.isEmpty()) {
            ownershipStore.clearOwners(item.historyKey)
            delegate.remove(downloadId)
        }
        return downloads()
    }

    private fun owns(downloadId: Long): Boolean {
        val item = delegate.downloads().firstOrNull { it.downloadId == downloadId } ?: return false
        return profileStore.activeProfileId() in ownershipStore.ownersForExistingDownload(item.historyKey)
    }
}

/**
 * A snapshot of physical download state with dynamic profile filtering.
 *
 * The physical entries are snapshotted so download progress comparisons still
 * work normally. The active profile and owner mapping are resolved when the list
 * is read, so the state update already performed during profile switching can
 * immediately render the new profile's Downloads list without waiting for the
 * ViewModel polling interval.
 */
private class ProfileDownloadSnapshotList(
    private val allDownloads: List<OfflineDownload>,
    private val activeProfileId: () -> String,
    private val ownersForExistingDownload: (String) -> Set<String>,
) : AbstractList<OfflineDownload>() {
    private fun current(): List<OfflineDownload> {
        val profileId = activeProfileId()
        return allDownloads.filter { item ->
            profileId in ownersForExistingDownload(item.historyKey)
        }
    }

    override val size: Int
        get() = current().size

    override fun get(index: Int): OfflineDownload = current()[index]

    override fun iterator(): Iterator<OfflineDownload> = current().iterator()

    override fun listIterator(index: Int): ListIterator<OfflineDownload> = current().listIterator(index)
}

/**
 * Profile ownership metadata for physical device downloads.
 *
 * Ownership is stored by historyKey because DownloadRepository already enforces
 * one physical download per historyKey. This lets two profiles reference the
 * same on-device file without duplicating storage. Downloads created before
 * Multi Profile are migrated once to the primary profile.
 */
private class ProfileDownloadOwnershipStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun migrateLegacy(downloads: List<OfflineDownload>) {
        if (preferences.getBoolean(KEY_LEGACY_MIGRATION_COMPLETE, false)) return
        val editor = preferences.edit()
        downloads.forEach { item ->
            val key = ownersKey(item.historyKey)
            if (!preferences.contains(key)) {
                editor.putStringSet(key, setOf(ProfileStore.PRIMARY_PROFILE_ID))
            }
        }
        editor.putBoolean(KEY_LEGACY_MIGRATION_COMPLETE, true).commit()
    }

    @Synchronized
    fun explicitOwners(historyKey: String): Set<String>? {
        val key = ownersKey(historyKey)
        if (!preferences.contains(key)) return null
        return preferences.getStringSet(key, emptySet())
            .orEmpty()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
    }

    @Synchronized
    fun ownersForExistingDownload(historyKey: String): Set<String> =
        explicitOwners(historyKey)
            ?.takeIf(Set<String>::isNotEmpty)
            ?: setOf(ProfileStore.PRIMARY_PROFILE_ID)

    @Synchronized
    fun addOwner(
        historyKey: String,
        profileId: String,
        includeLegacyPrimary: Boolean,
    ) {
        val current = explicitOwners(historyKey)
            ?: if (includeLegacyPrimary) setOf(ProfileStore.PRIMARY_PROFILE_ID) else emptySet()
        val updated = current + profileId
        preferences.edit().putStringSet(ownersKey(historyKey), updated).commit()
    }

    @Synchronized
    fun removeExistingOwner(historyKey: String, profileId: String): Set<String> {
        val updated = ownersForExistingDownload(historyKey) - profileId
        if (updated.isEmpty()) {
            preferences.edit().remove(ownersKey(historyKey)).commit()
        } else {
            preferences.edit().putStringSet(ownersKey(historyKey), updated).commit()
        }
        return updated
    }

    @Synchronized
    fun removeExplicitOwner(historyKey: String, profileId: String) {
        val current = explicitOwners(historyKey) ?: return
        val updated = current - profileId
        if (updated.isEmpty()) {
            preferences.edit().remove(ownersKey(historyKey)).commit()
        } else {
            preferences.edit().putStringSet(ownersKey(historyKey), updated).commit()
        }
    }

    @Synchronized
    fun clearOwners(historyKey: String) {
        preferences.edit().remove(ownersKey(historyKey)).commit()
    }

    private fun ownersKey(historyKey: String): String = "$KEY_OWNER_PREFIX$historyKey"

    private companion object {
        const val PREFERENCES_NAME = "hulk_profile_download_ownership_v1"
        const val KEY_OWNER_PREFIX = "owners:"
        const val KEY_LEGACY_MIGRATION_COMPLETE = "legacy_primary_migration_complete"
    }
}

/**
 * More-specific overload used by AndroidViewModel callers. The underlying
 * DownloadRepository remains process-global for WorkManager and transport
 * safety, while the ViewModel sees only the active profile's owned downloads.
 */
@Suppress("FunctionName")
internal fun DownloadRepository(application: Application): ProfileScopedDownloadRepository =
    ProfileScopedDownloadRepository(application)
