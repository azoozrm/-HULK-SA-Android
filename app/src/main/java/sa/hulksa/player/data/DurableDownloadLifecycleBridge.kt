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
            owner = item.owner(),
            wifiOnly = wifiOnly,
            scheduledAtEpochMs = item.scheduledAtEpochMs,
        )
    }

    fun enqueue(
        downloadId: Long,
        owner: DownloadOwner,
        wifiOnly: Boolean,
        scheduledAtEpochMs: Long,
    ) {
        validateDurableDownloadId(downloadId)
        scheduler.enqueue(
            downloadId = downloadId,
            owner = owner,
            wifiOnly = wifiOnly,
            scheduledAtEpochMs = scheduledAtEpochMs,
        )
    }

    fun cancel(downloadId: Long, owner: DownloadOwner) {
        validateDurableDownloadId(downloadId)
        scheduler.cancel(downloadId, owner)
    }
}
