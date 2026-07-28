package sa.hulksa.player.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

internal enum class DurableDownloadNotificationAction {
    PAUSE,
}

internal fun durableDownloadNotificationAction(rawAction: String?): DurableDownloadNotificationAction? =
    when (rawAction) {
        ACTION_PAUSE_DOWNLOAD -> DurableDownloadNotificationAction.PAUSE
        else -> null
    }

internal class DurableDownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val downloadId = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: return
        if (downloadId <= 0L) return
        when (durableDownloadNotificationAction(intent.action)) {
            DurableDownloadNotificationAction.PAUSE -> {
                DownloadRepositoryProcessOwner.get(context.applicationContext).pause(downloadId)
            }
            null -> Unit
        }
    }
}

internal const val ACTION_PAUSE_DOWNLOAD = "sa.hulksa.player.action.PAUSE_DOWNLOAD"
internal const val EXTRA_DOWNLOAD_ID = "download_id"
