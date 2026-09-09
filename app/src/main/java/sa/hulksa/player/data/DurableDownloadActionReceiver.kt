package sa.hulksa.player.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

internal enum class DurableDownloadReceiverExecution {
    ASYNC_PAUSE,
    DIRECT_RESUME,
}

internal fun durableDownloadReceiverExecution(
    rawAction: String?,
): DurableDownloadReceiverExecution? =
    when (durableDownloadNotificationAction(rawAction)) {
        DurableDownloadNotificationAction.PAUSE -> DurableDownloadReceiverExecution.ASYNC_PAUSE
        DurableDownloadNotificationAction.RESUME -> DurableDownloadReceiverExecution.DIRECT_RESUME
        null -> null
    }

internal class DurableDownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val downloadId = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: return
        if (downloadId <= 0L) return
        val applicationContext = context.applicationContext
        when (durableDownloadReceiverExecution(intent.action)) {
            DurableDownloadReceiverExecution.ASYNC_PAUSE -> pauseAsync(applicationContext, downloadId)
            DurableDownloadReceiverExecution.DIRECT_RESUME -> {
                DownloadRepositoryProcessOwner.getActive(applicationContext)?.resume(downloadId)
            }
            null -> Unit
        }
    }

    private fun pauseAsync(context: Context, downloadId: Long) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                DownloadRepositoryProcessOwner.getActive(context)?.pause(downloadId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

internal const val ACTION_PAUSE_DOWNLOAD = "sa.hulksa.player.action.PAUSE_DOWNLOAD"
internal const val ACTION_RESUME_DOWNLOAD = "sa.hulksa.player.action.RESUME_DOWNLOAD"
internal const val EXTRA_DOWNLOAD_ID = "download_id"
