package sa.hulksa.player.data

import android.content.Context
import sa.hulksa.player.model.OfflineDownload

internal class DurableDownloadLifecycleBridge(
    context: Context,
    private val scheduler: DurableDownloadScheduler = DurableDownloadScheduler(context.applicationContext),
) {
    fun enqueue(item: OfflineDownload, wifiOnly: Boolean) {
        enqueue(
            downloadId = item.downloadId,
            title = item.title,
            wifiOnly = wifiOnly,
            scheduledAtEpochMs = item.scheduledAtEpochMs,
        )
    }

    fun enqueue(
        downloadId: Long,
        title: String?,
        wifiOnly: Boolean,
        scheduledAtEpochMs: Long,
    ) {
        validateDurableDownloadId(downloadId)
        scheduler.enqueue(
            downloadId = downloadId,
            wifiOnly = wifiOnly,
            scheduledAtEpochMs = scheduledAtEpochMs,
            title = title,
        )
    }

    fun cancel(downloadId: Long) {
        validateDurableDownloadId(downloadId)
        scheduler.cancel(downloadId)
    }
}
