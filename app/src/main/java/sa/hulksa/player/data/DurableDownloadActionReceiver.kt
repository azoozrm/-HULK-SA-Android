package sa.hulksa.player.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    ASYNC_RESUME,
}

internal fun durableDownloadReceiverExecution(
    rawAction: String?,
): DurableDownloadReceiverExecution? =
    when (durableDownloadNotificationAction(rawAction)) {
        DurableDownloadNotificationAction.PAUSE -> DurableDownloadReceiverExecution.ASYNC_PAUSE
        DurableDownloadNotificationAction.RESUME -> DurableDownloadReceiverExecution.ASYNC_RESUME
        null -> null
    }

internal fun durableDownloadReceiverDownloadId(rawDownloadId: Long): Long? =
    rawDownloadId.takeIf { it > 0L }

internal fun <T : Any> CoroutineScope.launchDurableDownloadReceiverMutation(
    resolveTarget: () -> T?,
    finish: () -> Unit,
    mutation: (T) -> Unit,
): Job = launch {
    try {
        resolveTarget()?.let(mutation)
    } finally {
        finish()
    }
}

internal class DurableDownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val rawDownloadId = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: return
        val downloadId = durableDownloadReceiverDownloadId(rawDownloadId) ?: return
        val execution = durableDownloadReceiverExecution(intent.action) ?: return
        dispatchAsync(context.applicationContext, execution, downloadId)
    }

    private fun dispatchAsync(
        context: Context,
        execution: DurableDownloadReceiverExecution,
        downloadId: Long,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launchDurableDownloadReceiverMutation(
            resolveTarget = { DownloadRepositoryProcessOwner.getActive(context) },
            finish = pendingResult::finish,
        ) { repository ->
            when (execution) {
                DurableDownloadReceiverExecution.ASYNC_PAUSE -> repository.pause(downloadId)
                DurableDownloadReceiverExecution.ASYNC_RESUME -> repository.resume(downloadId)
            }
        }
    }
}

internal const val ACTION_PAUSE_DOWNLOAD = "sa.hulksa.player.action.PAUSE_DOWNLOAD"
internal const val ACTION_RESUME_DOWNLOAD = "sa.hulksa.player.action.RESUME_DOWNLOAD"
internal const val EXTRA_DOWNLOAD_ID = "download_id"
