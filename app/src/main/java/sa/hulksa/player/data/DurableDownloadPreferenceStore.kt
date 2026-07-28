package sa.hulksa.player.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import sa.hulksa.player.model.OfflineStatus

internal data class DurableDownloadPersistedRecord(
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

internal class DurableDownloadPreferenceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
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
