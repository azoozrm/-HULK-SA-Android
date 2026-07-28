package sa.hulksa.player.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

internal enum class DurableDownloadNotificationAction {
    PAUSE,
    RESUME,
}

internal fun durableDownloadNotificationAction(rawAction: String?): DurableDownloadNotificationAction? =
    when (rawAction) {
        ACTION_PAUSE_DOWNLOAD -> DurableDownloadNotificationAction.PAUSE
        ACTION_RESUME_DOWNLOAD -> DurableDownloadNotificationAction.RESUME
        else -> null
    }

internal class DurableDownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val downloadId = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: return
        if (downloadId <= 0L) return
        val repository = DownloadRepositoryProcessOwner.get(context.applicationContext)
        when (durableDownloadNotificationAction(intent.action)) {
            DurableDownloadNotificationAction.PAUSE -> repository.pause(downloadId)
            DurableDownloadNotificationAction.RESUME -> repository.resume(downloadId)
            null -> Unit
        }
    }
}

internal const val ACTION_PAUSE_DOWNLOAD = "sa.hulksa.player.action.PAUSE_DOWNLOAD"
internal const val ACTION_RESUME_DOWNLOAD = "sa.hulksa.player.action.RESUME_DOWNLOAD"
internal const val EXTRA_DOWNLOAD_ID = "download_id"
