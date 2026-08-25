package sa.hulksa.player.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.ForegroundInfo
import sa.hulksa.player.MainActivity
import sa.hulksa.player.R

internal fun durableDownloadNotificationId(downloadId: Long): Int {
    validateDurableDownloadId(downloadId)
    val folded = (downloadId xor (downloadId ushr 32)).toInt() and Int.MAX_VALUE
    return folded.coerceAtLeast(1)
}

internal fun durableDownloadNotificationRequestCode(
    downloadId: Long,
    owner: DownloadOwner,
    action: String,
): Int {
    validateDurableDownloadId(downloadId)
    val normalizedOwner = requireNotNull(owner.normalizedOrNull()) { "A valid download owner is required" }
    return durableDownloadNotificationId(downloadId) xor
        action.hashCode() xor
        downloadOwnerStorageKey(normalizedOwner).hashCode()
}

internal class DurableDownloadForeground(
    private val context: Context,
) {
    fun createInfo(downloadId: Long, owner: DownloadOwner): ForegroundInfo {
        ensureNotificationChannel()
        val notificationTitle = "تحميل محتوى HULK SA"
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(R.drawable.ic_stat_hulk)
            .setContentTitle(notificationTitle)
            .setContentText("جار التحميل وسيستمر في الخلفية.")
            .setContentIntent(openAppIntent(downloadId, owner))
            .setAutoCancel(false)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .addAction(downloadAction(downloadId, owner, ACTION_PAUSE_DOWNLOAD, "ايقاف مؤقت", android.R.drawable.ic_media_pause))
            .addAction(downloadAction(downloadId, owner, ACTION_RESUME_DOWNLOAD, "استئناف", android.R.drawable.ic_media_play))
            .build()
        val notificationId = durableDownloadNotificationId(downloadId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun openAppIntent(downloadId: Long, owner: DownloadOwner): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            .putExtra(EXTRA_DOWNLOAD_ACCOUNT_ID, owner.accountId)
            .putExtra(EXTRA_DOWNLOAD_PROFILE_ID, owner.profileId)
        return PendingIntent.getActivity(
            context,
            durableDownloadNotificationRequestCode(downloadId, owner, ACTION_OPEN_DOWNLOADS),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun downloadAction(
        downloadId: Long,
        owner: DownloadOwner,
        action: String,
        label: String,
        icon: Int,
    ): Notification.Action {
        val intent = Intent(context, DurableDownloadActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            .putExtra(EXTRA_DOWNLOAD_ACCOUNT_ID, owner.accountId)
            .putExtra(EXTRA_DOWNLOAD_PROFILE_ID, owner.profileId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            durableDownloadNotificationRequestCode(downloadId, owner, action),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(icon, label, pendingIntent).build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "تحميلات HULK SA",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "حالة تحميل الافلام والحلقات في الخلفية"
                setShowBadge(false)
            },
        )
    }

    private companion object {
        const val CHANNEL_ID = "hulk_durable_downloads"
        const val ACTION_OPEN_DOWNLOADS = "sa.hulksa.player.action.OPEN_DOWNLOADS"
    }
}
