package sa.hulksa.player.data

import android.content.Context
import kotlinx.coroutines.delay
import sa.hulksa.player.model.OfflineStatus

internal enum class DurableDownloadExecutionResult {
    COMPLETED,
    RETRY,
    TERMINAL,
}

internal enum class DurableDownloadExecutionDirective {
    AWAIT,
    COMPLETED,
    RETRY,
    TERMINAL,
}

internal fun validateDurableDownloadId(downloadId: Long) {
    require(downloadId > 0L) { "downloadId must be positive" }
}

internal fun durableDownloadExecutionDirective(
    status: OfflineStatus,
): DurableDownloadExecutionDirective = when (status) {
    OfflineStatus.QUEUED,
    OfflineStatus.CHECKING,
    OfflineStatus.DOWNLOADING,
    -> DurableDownloadExecutionDirective.AWAIT

    OfflineStatus.WAITING_SCHEDULE,
    OfflineStatus.WAITING_NETWORK,
    OfflineStatus.WAITING_STORAGE,
    -> DurableDownloadExecutionDirective.RETRY

    OfflineStatus.COMPLETED -> DurableDownloadExecutionDirective.COMPLETED
    OfflineStatus.PAUSED,
    OfflineStatus.FAILED,
    -> DurableDownloadExecutionDirective.TERMINAL
}

internal class DownloadExecutionEntryPoint(
    context: Context,
    private val repository: DownloadRepository = DownloadRepositoryProcessOwner.get(context),
) {
    suspend fun execute(downloadId: Long): DurableDownloadExecutionResult {
        validateDurableDownloadId(downloadId)
        return repository.executeScheduledDownload(downloadId)
    }
}

internal suspend fun DownloadRepository.executeScheduledDownload(
    downloadId: Long,
): DurableDownloadExecutionResult {
    var waitingRechecks = 0
    while (true) {
        val item = downloads().firstOrNull { it.downloadId == downloadId }
            ?: return DurableDownloadExecutionResult.TERMINAL
        when (durableDownloadExecutionDirective(item.status)) {
            DurableDownloadExecutionDirective.COMPLETED -> {
                return DurableDownloadExecutionResult.COMPLETED
            }
            DurableDownloadExecutionDirective.TERMINAL -> {
                return DurableDownloadExecutionResult.TERMINAL
            }
            DurableDownloadExecutionDirective.AWAIT -> {
                waitingRechecks = 0
                delay(ACTIVE_POLL_INTERVAL_MS)
            }
            DurableDownloadExecutionDirective.RETRY -> {
                waitingRechecks += 1
                if (waitingRechecks >= WAITING_RECHECK_LIMIT) {
                    return DurableDownloadExecutionResult.RETRY
                }
                delay(WAITING_RECHECK_INTERVAL_MS)
            }
        }
    }
}

private const val ACTIVE_POLL_INTERVAL_MS = 750L
private const val WAITING_RECHECK_INTERVAL_MS = 1_000L
private const val WAITING_RECHECK_LIMIT = 3
