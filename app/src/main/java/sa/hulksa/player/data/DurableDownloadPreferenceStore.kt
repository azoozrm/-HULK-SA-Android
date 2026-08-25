package sa.hulksa.player.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import sa.hulksa.player.model.OfflineStatus

internal data class DurableDownloadPersistedRecord(
    val accountId: String,
    val downloadId: Long,
    val title: String?,
    val status: OfflineStatus,
    val scheduledAtEpochMs: Long,
)

internal data class DurableDownloadPreferenceSnapshot(
    val wifiOnly: Boolean,
    val records: List<DurableDownloadPersistedRecord>,
)

internal enum class DurableDownloadLifecycleAction {
    ENQUEUE,
    CANCEL,
}

internal data class DurableDownloadSchedulingState(
    val accountId: String,
    val action: DurableDownloadLifecycleAction,
    val title: String?,
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
): DurableDownloadSchedulingState = DurableDownloadSchedulingState(
    accountId = record.accountId,
    action = durableDownloadLifecycleAction(record.status),
    title = record.title,
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

internal class DurableDownloadPreferenceStore(
    context: Context,
    private val accountId: String,
) {
    private val preferences = DownloadAccountStorage(context.applicationContext).preferences(
        PREFERENCES_NAME,
        accountId,
    )

    fun snapshot(): DurableDownloadPreferenceSnapshot = DurableDownloadPreferenceSnapshot(
        wifiOnly = preferences.getBoolean(KEY_WIFI_ONLY, false),
        records = parseRecords(preferences.getString(KEY_DOWNLOADS, null)),
    )

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun parseRecords(raw: String?): List<DurableDownloadPersistedRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val data = array.optJSONObject(index) ?: continue
                    val downloadId = data.optLong("downloadId", -1L)
                    if (downloadId <= 0L) continue
                    val status = runCatching {
                        OfflineStatus.valueOf(
                            data.optString("status", OfflineStatus.QUEUED.name),
                        )
                    }.getOrDefault(OfflineStatus.QUEUED)
                    add(
                        DurableDownloadPersistedRecord(
                            accountId = accountId,
                            downloadId = downloadId,
                            title = data.optString("title").trim().takeIf(String::isNotEmpty),
                            status = status,
                            scheduledAtEpochMs = data.optLong("scheduledAtEpochMs", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        const val PREFERENCES_NAME = "hulk_downloads"
        const val KEY_DOWNLOADS = "downloads"
        const val KEY_WIFI_ONLY = "wifi_only"
    }
}
