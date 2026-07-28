package sa.hulksa.player.data

import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus

internal enum class DownloadBlockReason {
    SCHEDULE,
    NETWORK,
    STORAGE,
}

internal data class DownloadAttemptDecision(
    val canRun: Boolean,
    val blockReason: DownloadBlockReason? = null,
)

internal fun decideDownloadAttempt(
    item: OfflineDownload,
    nowEpochMs: Long,
    networkAvailable: Boolean,
    storageAvailable: Boolean,
): DownloadAttemptDecision = when {
    item.status == OfflineStatus.WAITING_SCHEDULE && item.scheduledAtEpochMs > nowEpochMs ->
        DownloadAttemptDecision(canRun = false, blockReason = DownloadBlockReason.SCHEDULE)

    item.status == OfflineStatus.WAITING_NETWORK && !networkAvailable ->
        DownloadAttemptDecision(canRun = false, blockReason = DownloadBlockReason.NETWORK)

    item.status == OfflineStatus.WAITING_STORAGE && !storageAvailable ->
        DownloadAttemptDecision(canRun = false, blockReason = DownloadBlockReason.STORAGE)

    else -> DownloadAttemptDecision(canRun = true)
}
