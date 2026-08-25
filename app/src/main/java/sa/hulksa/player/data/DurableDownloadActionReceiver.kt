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

internal class DurableDownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val downloadId = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: return
        if (downloadId <= 0L) return
        val owner = DownloadOwner(
            accountId = intent.getStringExtra(EXTRA_DOWNLOAD_ACCOUNT_ID).orEmpty(),
            profileId = intent.getStringExtra(EXTRA_DOWNLOAD_PROFILE_ID).orEmpty(),
        ).normalizedOrNull() ?: return
        val action = durableDownloadNotificationAction(intent.action) ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = ProfileScopedDownloadRepository(context.applicationContext)
                repository.initialize()
                if (!repository.canAccess(downloadId, owner)) return@launch
                when (action) {
                    DurableDownloadNotificationAction.PAUSE -> repository.pause(downloadId)
                    DurableDownloadNotificationAction.RESUME -> repository.resume(downloadId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

internal const val ACTION_PAUSE_DOWNLOAD = "sa.hulksa.player.action.PAUSE_DOWNLOAD"
internal const val ACTION_RESUME_DOWNLOAD = "sa.hulksa.player.action.RESUME_DOWNLOAD"
internal const val EXTRA_DOWNLOAD_ID = "download_id"
internal const val EXTRA_DOWNLOAD_ACCOUNT_ID = "download_account_id"
internal const val EXTRA_DOWNLOAD_PROFILE_ID = "download_profile_id"
