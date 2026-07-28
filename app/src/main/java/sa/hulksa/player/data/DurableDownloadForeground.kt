package sa.hulksa.player.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
