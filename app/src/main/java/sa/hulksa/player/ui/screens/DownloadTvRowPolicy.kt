package sa.hulksa.player.ui.screens

import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus

internal enum class DownloadTvRowAction {
    PLAY,
    RETRY,
    RESUME,
    PAUSE,
}

internal data class DownloadTvRowTelemetry(
    val percent: Int?,
    val showTrack: Boolean,
    val sizeLine: String?,
    val detailLine: String?,
)

internal fun downloadTvRowAction(status: OfflineStatus): DownloadTvRowAction = when (status) {
    OfflineStatus.COMPLETED -> DownloadTvRowAction.PLAY
    OfflineStatus.FAILED -> DownloadTvRowAction.RETRY
    OfflineStatus.PAUSED,
    OfflineStatus.WAITING_SCHEDULE,
    OfflineStatus.WAITING_NETWORK,
    OfflineStatus.WAITING_STORAGE,
    -> DownloadTvRowAction.RESUME
    OfflineStatus.QUEUED,
    OfflineStatus.CHECKING,
    OfflineStatus.DOWNLOADING,
    -> DownloadTvRowAction.PAUSE
}

internal fun downloadTvRowActionCaption(action: DownloadTvRowAction): String = when (action) {
    DownloadTvRowAction.PLAY -> "تشغيل"
    DownloadTvRowAction.RETRY -> "اعادة التحميل"
    DownloadTvRowAction.RESUME -> "استئناف"
    DownloadTvRowAction.PAUSE -> "ايقاف"
}

internal fun downloadTvRowStatusLabel(status: OfflineStatus): String = when (status) {
    OfflineStatus.QUEUED -> "في انتظار التحميل"
    OfflineStatus.CHECKING -> "جاري تجهيز التحميل"
    OfflineStatus.DOWNLOADING -> "جاري التحميل"
    OfflineStatus.PAUSED -> "متوقف"
    OfflineStatus.WAITING_SCHEDULE -> "في انتظار الموعد"
    OfflineStatus.WAITING_NETWORK -> "في انتظار الشبكة"
    OfflineStatus.WAITING_STORAGE -> "في انتظار المساحة"
    OfflineStatus.COMPLETED -> "تم التحميل"
    OfflineStatus.FAILED -> "تعذر التحميل"
}

/**
 * Truthful TV-row telemetry. A percent and a progress track are shown only for a real known
 * total on an actively transferable status; an unknown total never fabricates a percentage,
 * total size or ETA, and a completed row reserves no decorative track.
 */
internal fun downloadTvRowTelemetry(
    item: OfflineDownload,
    formatBytes: (Long) -> String,
    scheduledLabel: (Long) -> String,
): DownloadTvRowTelemetry {
    val knownTotal = item.totalBytes > 0L
    val completed = item.status == OfflineStatus.COMPLETED
    val trackEligible = item.status == OfflineStatus.DOWNLOADING || item.status == OfflineStatus.PAUSED
    val percent = if (knownTotal && trackEligible) {
        (item.progress * 100f).toInt().coerceIn(0, 100)
    } else {
        null
    }
    val sizeLine = when {
        completed && knownTotal -> formatBytes(item.totalBytes)
        completed -> item.storageLabel
        knownTotal -> "${formatBytes(item.bytesDownloaded)} / ${formatBytes(item.totalBytes)}"
        item.bytesDownloaded > 0L -> formatBytes(item.bytesDownloaded)
        else -> null
    }
    val detailLine = when {
        !item.errorMessage.isNullOrBlank() -> item.errorMessage
        item.status == OfflineStatus.WAITING_SCHEDULE && item.scheduledAtEpochMs > 0L ->
            "سيبدا ${scheduledLabel(item.scheduledAtEpochMs)}"
        else -> null
    }
    return DownloadTvRowTelemetry(
        percent = percent,
        showTrack = percent != null,
        sizeLine = sizeLine,
        detailLine = detailLine,
    )
}
