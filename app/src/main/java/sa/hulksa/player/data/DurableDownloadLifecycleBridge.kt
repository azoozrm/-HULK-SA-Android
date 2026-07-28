package sa.hulksa.player.data

import android.content.Context
import sa.hulksa.player.model.OfflineDownload

internal class DurableDownloadLifecycleBridge(
    context: Context,
    private val scheduler: DurableDownloadScheduler = DurableDownloadScheduler(context.applicationContext),
) {
    fun enqueue(item: OfflineDownload, wifiOnly: Boolean) {
        scheduler.enqueue(
            downloadId = item.downloadId,
            wifiOnly = wifiOnly,
            scheduledAtEpochMs = item.scheduledAtEpochMs,
        )
    }

    fun cancel(downloadId: Long) {
        validateDurableDownloadId(downloadId)
        scheduler.cancel(downloadId)
    }
}
