package sa.hulksa.player.data

import android.content.Context
import android.content.SharedPreferences
import sa.hulksa.player.model.OfflineStatus

internal data class DurableDownloadPersistedRecord(
    val downloadId: Long,
    val accountId: String,
    val profileId: String,
    val historyKey: String,
    val status: OfflineStatus,
    val scheduledAtEpochMs: Long,
)

internal data class DurableDownloadPreferenceSnapshot(
    val wifiOnly: Boolean,
    val activeAccountId: String?,
    val records: List<DurableDownloadPersistedRecord>,
)

internal enum class DurableDownloadLifecycleAction {
    ENQUEUE,
    CANCEL,
}

internal data class DurableDownloadSchedulingState(
    val action: DurableDownloadLifecycleAction,
    val owner: DownloadOwner,
    val wifiOnly: Boolean,
    val scheduledAtEpochMs: Long,
)

internal fun durableDownloadLifecycleAction(
    status: OfflineStatus,
): DurableDownloadLifecycleAction = when (status) {
    OfflineStatus.QUEUED,
    OfflineStatus.CHECKING,
    OfflineStatus.DOWNLOADING,
    OfflineStatus.WAITING_SCHEDULE,
    OfflineStatus.WAITING_NETWORK,
    OfflineStatus.WAITING_STORAGE,
    -> DurableDownloadLifecycleAction.ENQUEUE

    OfflineStatus.PAUSED,
    OfflineStatus.FAILED,
    OfflineStatus.COMPLETED,
    -> DurableDownloadLifecycleAction.CANCEL
}

internal fun durableDownloadSchedulingState(
    record: DurableDownloadPersistedRecord,
    wifiOnly: Boolean,
    activeAccountId: String?,
): DurableDownloadSchedulingState = DurableDownloadSchedulingState(
    action = if (record.accountId == activeAccountId) {
        durableDownloadLifecycleAction(record.status)
    } else {
        DurableDownloadLifecycleAction.CANCEL
    },
    owner = DownloadOwner(record.accountId, record.profileId),
    wifiOnly = wifiOnly,
    scheduledAtEpochMs = record.scheduledAtEpochMs,
)

internal fun shouldApplyDurableDownloadSchedulingState(
    previous: DurableDownloadSchedulingState?,
    current: DurableDownloadSchedulingState,
    currentStatus: OfflineStatus,
): Boolean {
    if (previous == current) return false
    val transportAlreadyActive =
        currentStatus == OfflineStatus.CHECKING || currentStatus == OfflineStatus.DOWNLOADING
    if (
        transportAlreadyActive &&
        previous?.action == DurableDownloadLifecycleAction.ENQUEUE &&
        current.action == DurableDownloadLifecycleAction.ENQUEUE
    ) {
        return false
    }
    return true
}

internal class DurableDownloadPreferenceStore(context: Context) {
    private val accountScope = AccountScopeStore(context.applicationContext)
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun snapshot(): DurableDownloadPreferenceSnapshot = DurableDownloadPreferenceSnapshot(
        wifiOnly = runCatching { preferences.getBoolean(KEY_WIFI_ONLY, false) }.getOrDefault(false),
        activeAccountId = accountScope.activeAccountId(),
        records = parseRecords(runCatching { preferences.getString(KEY_DOWNLOADS, null) }.getOrNull()),
    )

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun parseRecords(raw: String?): List<DurableDownloadPersistedRecord> {
        return parseDurableDownloadRecords(raw)
    }

    companion object {
        const val PREFERENCES_NAME = "hulk_downloads"
        const val KEY_DOWNLOADS = "downloads"
        const val KEY_WIFI_ONLY = "wifi_only"
    }
}

internal fun parseDurableDownloadRecords(raw: String?): List<DurableDownloadPersistedRecord> {
    // Legacy arrays can be large and are never runnable because they have no
    // provable account owner. Avoid parsing them in this startup ContentProvider;
    // DownloadRepository performs the quarantine migration on Dispatchers.IO.
    if (raw.isNullOrBlank() || raw.trimStart().firstOrNull() != '{') return emptyList()
    return DownloadSchemaCodec.decode(raw).records.map { item ->
        DurableDownloadPersistedRecord(
            downloadId = item.downloadId,
            accountId = item.accountId,
            profileId = item.profileId,
            historyKey = item.historyKey,
            status = item.status,
            scheduledAtEpochMs = item.scheduledAtEpochMs,
        )
    }
}
