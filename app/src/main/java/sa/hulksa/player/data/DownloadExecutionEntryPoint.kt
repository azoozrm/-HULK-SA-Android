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
    private val accountSessionStore: AccountSessionStore = AccountSessionStore(context.applicationContext),
    private val profileStore: ProfileStore = ProfileStore(context.applicationContext),
    private val sessionProvider: suspend () -> sa.hulksa.player.model.AuthenticatedSession? = {
        AuthenticatedSessionRegistry.current()
    },
) {
    suspend fun execute(
        downloadId: Long,
        requestedOwner: DownloadOwner,
    ): DurableDownloadExecutionResult {
        validateDurableDownloadId(downloadId)
        val owner = requestedOwner.normalizedOrNull()
            ?: return DurableDownloadExecutionResult.TERMINAL
        repository.initialize()
        val record = repository.record(downloadId)
        if (record == null || !record.isOwnedBy(owner)) {
            return DurableDownloadExecutionResult.TERMINAL
        }
        val initialMetadata = accountSessionStore.metadata()
        if (
            initialMetadata == null ||
            initialMetadata.isExpired() ||
            initialMetadata.accountId != owner.accountId
        ) {
            repository.suspendForInactiveOwner(downloadId, owner)
            return DurableDownloadExecutionResult.TERMINAL
        }
        val session = sessionProvider()
        val metadata = accountSessionStore.metadata()
        val activeAccountId = if (
            session != null && metadata != null && authenticatedSessionMatchesMetadata(session, metadata)
        ) {
            metadata.accountId
        } else {
            null
        }
        return when (decideDownloadWorkerOwnership(record, owner, activeAccountId)) {
            DownloadWorkerOwnershipDecision.RECORD_OWNER_MISMATCH ->
                DurableDownloadExecutionResult.TERMINAL
            DownloadWorkerOwnershipDecision.SESSION_OWNER_MISMATCH -> {
                repository.suspendForInactiveOwner(downloadId, owner)
                DurableDownloadExecutionResult.TERMINAL
            }
            DownloadWorkerOwnershipDecision.ALLOW -> {
                val authenticatedSession = session ?: return DurableDownloadExecutionResult.TERMINAL
                val ownedRecord = record ?: return DurableDownloadExecutionResult.TERMINAL
                if (profileStore.profiles().none { it.id == owner.profileId }) {
                    repository.suspendForInactiveOwner(downloadId, owner)
                    return DurableDownloadExecutionResult.TERMINAL
                }
                val sources = runCatching { resolveDownloadSources(authenticatedSession, ownedRecord) }
                    .getOrDefault(emptyList())
                if (!repository.prepareForAuthenticatedOwner(downloadId, owner, sources)) {
                    repository.suspendForInactiveOwner(downloadId, owner)
                    DurableDownloadExecutionResult.TERMINAL
                } else {
                    repository.executeScheduledDownload(downloadId, owner)
                }
            }
        }
    }
}

internal suspend fun DownloadRepository.executeScheduledDownload(
    downloadId: Long,
    owner: DownloadOwner,
): DurableDownloadExecutionResult {
    var waitingRechecks = 0
    while (true) {
        val item = record(downloadId)?.takeIf { it.isOwnedBy(owner) }
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
