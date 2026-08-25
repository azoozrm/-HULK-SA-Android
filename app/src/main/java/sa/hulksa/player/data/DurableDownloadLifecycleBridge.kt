package sa.hulksa.player.data

import android.content.Context

internal class DurableDownloadLifecycleBridge(
    context: Context,
    private val scheduler: DurableDownloadScheduler = DurableDownloadScheduler(context.applicationContext),
) {
    fun enqueue(
        accountId: String,
        downloadId: Long,
        title: String?,
        wifiOnly: Boolean,
        scheduledAtEpochMs: Long,
    ) {
        validateDownloadAccountId(accountId)
        validateDurableDownloadId(downloadId)
        scheduler.enqueue(
            accountId = accountId,
            downloadId = downloadId,
            wifiOnly = wifiOnly,
            scheduledAtEpochMs = scheduledAtEpochMs,
            title = title,
        )
    }

    fun cancel(accountId: String, downloadId: Long) {
        validateDownloadAccountId(accountId)
        validateDurableDownloadId(downloadId)
        scheduler.cancel(accountId, downloadId)
    }

    fun cancelAccount(accountId: String) {
        validateDownloadAccountId(accountId)
        scheduler.cancelAccount(accountId)
    }
}
