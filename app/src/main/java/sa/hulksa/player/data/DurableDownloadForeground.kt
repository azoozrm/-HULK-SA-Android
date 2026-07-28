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

internal fun durableDownloadNotificationId(downloadId: Long): Int {
    validateDurableDownloadId(downloadId)
    val folded = (downloadId xor (downloadId ushr 32)).toInt() and Int.MAX_VALUE
    return folded.coerceAtLeast(1)
}

internal class DurableDownloadForeground(
    private val context: Context,
) {
    fun createInfo(downloadId: Long, title: String?): ForegroundInfo {
        ensureNotificationChannel()
        val notificationTitle = title?.trim()?.takeIf { it.isNotEmpty() }
            ?: "تحميل محتوى HULK SA"
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(notificationTitle)
            .setContentText("جار التحميل وسيستمر في الخلفية.")
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .addAction(downloadAction(downloadId, ACTION_PAUSE_DOWNLOAD, "ايقاف مؤقت", android.R.drawable.ic_media_pause))
            .addAction(downloadAction(downloadId, ACTION_RESUME_DOWNLOAD, "استئناف", android.R.drawable.ic_media_play))
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

    private fun downloadAction(
        downloadId: Long,
        action: String,
        label: String,
        icon: Int,
    ): Notification.Action {
        val intent = Intent(context, DurableDownloadActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        val requestCode = durableDownloadNotificationId(downloadId) xor action.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
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
    }
}
