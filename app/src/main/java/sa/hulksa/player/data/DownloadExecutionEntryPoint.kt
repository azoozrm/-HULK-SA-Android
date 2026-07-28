package sa.hulksa.player.data

import android.content.Context
import sa.hulksa.player.model.OfflineStatus

internal enum class DurableDownloadExecutionResult {
    COMPLETED,
    RETRY,
    TERMINAL,
}

internal fun validateDurableDownloadId(downloadId: Long) {
    require(downloadId > 0L) { "downloadId must be positive" }
}

internal class DownloadExecutionEntryPoint(
    context: Context,
    private val repository: DownloadRepository = DownloadRepository(context.applicationContext),
) {
    suspend fun execute(downloadId: Long): DurableDownloadExecutionResult {
        validateDurableDownloadId(downloadId)
        return repository.executeScheduledDownload(downloadId)
    }
}

internal suspend fun DownloadRepository.executeScheduledDownload(
    downloadId: Long,
): DurableDownloadExecutionResult {
    val before = downloads().firstOrNull { it.downloadId == downloadId }
        ?: return DurableDownloadExecutionResult.TERMINAL
    if (before.status == OfflineStatus.PAUSED || before.status == OfflineStatus.COMPLETED) {
        return DurableDownloadExecutionResult.TERMINAL
    }
    resume(downloadId)
    val after = downloads().firstOrNull { it.downloadId == downloadId }
        ?: return DurableDownloadExecutionResult.TERMINAL
    return when (after.status) {
        OfflineStatus.COMPLETED -> DurableDownloadExecutionResult.COMPLETED
        OfflineStatus.QUEUED,
        OfflineStatus.CHECKING,
        OfflineStatus.DOWNLOADING,
        OfflineStatus.WAITING_SCHEDULE,
        OfflineStatus.WAITING_NETWORK,
        OfflineStatus.WAITING_STORAGE,
        -> DurableDownloadExecutionResult.RETRY
        OfflineStatus.PAUSED,
        OfflineStatus.FAILED,
        -> DurableDownloadExecutionResult.TERMINAL
    }
}
