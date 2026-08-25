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
    private val accountSessionStore: AccountSessionStore = AccountSessionStore(context.applicationContext),
    private val sessionProvider: suspend () -> sa.hulksa.player.model.AuthenticatedSession? = {
        AuthenticatedSessionRegistry.current()
    },
    private val repositoryProvider: (String) -> DownloadRepository = { accountId ->
        DownloadRepositoryProcessOwner.activate(context.applicationContext, accountId)
    },
) {
    suspend fun execute(accountId: String, downloadId: Long): DurableDownloadExecutionResult {
        validateDownloadAccountId(accountId)
        validateDurableDownloadId(downloadId)
        val metadata = accountSessionStore.metadata()
        val session = sessionProvider()
        return when (
            downloadWorkerSessionGate(
                workerAccountId = accountId,
                activeAccountId = accountSessionStore.activeAccountId(),
                metadata = metadata,
                hasAuthenticatedSession = session != null,
                authenticatedSessionMatches =
                    authenticatedDownloadAccountId(session, metadata) == accountId,
            )
        ) {
            DownloadWorkerSessionGate.TERMINAL -> DurableDownloadExecutionResult.TERMINAL
            DownloadWorkerSessionGate.RETRY -> DurableDownloadExecutionResult.RETRY
            DownloadWorkerSessionGate.ALLOW -> {
                val repository = repositoryProvider(accountId)
                if (!downloadWorkerOwnsRecord(accountId, repository, downloadId)) {
                    DurableDownloadExecutionResult.TERMINAL
                } else {
                    repository.executeScheduledDownload(accountId, downloadId)
                }
            }
        }
    }
}

internal fun downloadWorkerOwnsRecord(
    workerAccountId: String,
    repository: DownloadRepository,
    downloadId: Long,
): Boolean = downloadWorkerOwnsRecord(
    workerAccountId = workerAccountId,
    repositoryAccountId = repository.accountId,
    recordExists = repository.record(downloadId) != null,
)

internal fun downloadWorkerOwnsRecord(
    workerAccountId: String,
    repositoryAccountId: String,
    recordExists: Boolean,
): Boolean =
    workerAccountId.trim().isNotEmpty() &&
        repositoryAccountId == workerAccountId.trim() &&
        recordExists

internal suspend fun DownloadRepository.executeScheduledDownload(
    accountId: String,
    downloadId: Long,
): DurableDownloadExecutionResult {
    if (this.accountId != accountId) return DurableDownloadExecutionResult.TERMINAL
    var waitingRechecks = 0
    while (true) {
        val item = record(downloadId)
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
