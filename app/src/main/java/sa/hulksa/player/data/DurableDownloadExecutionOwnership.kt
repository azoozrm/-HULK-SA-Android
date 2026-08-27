package sa.hulksa.player.data

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import sa.hulksa.player.model.OfflineStatus

/**
 * Process-local execution lease that makes persisted WorkManager work the gate for starting
 * repository transport. The file transfer still uses the existing DownloadRepository engine,
 * but it cannot start or restart in the background without an active durable worker for the
 * same account/download record.
 */
internal object DurableDownloadExecutionLeaseRegistry {
    private val activeLeases = ConcurrentHashMap<String, Unit>()

    fun claim(accountId: String, downloadId: Long): Boolean =
        activeLeases.putIfAbsent(key(accountId, downloadId), Unit) == null

    fun release(accountId: String, downloadId: Long) {
        activeLeases.remove(key(accountId, downloadId))
    }

    fun owns(accountId: String, downloadId: Long): Boolean =
        activeLeases.containsKey(key(accountId, downloadId))

    private fun key(accountId: String, downloadId: Long): String {
        validateDownloadAccountId(accountId)
        validateDurableDownloadId(downloadId)
        return "${downloadAccountStorageKey(accountId)}:$downloadId"
    }
}

internal fun durableWorkerInterruptedStatus(status: OfflineStatus): OfflineStatus? = when (status) {
    OfflineStatus.QUEUED,
    OfflineStatus.CHECKING,
    OfflineStatus.DOWNLOADING,
    -> OfflineStatus.QUEUED

    OfflineStatus.PAUSED,
    OfflineStatus.WAITING_SCHEDULE,
    OfflineStatus.WAITING_NETWORK,
    OfflineStatus.WAITING_STORAGE,
    OfflineStatus.COMPLETED,
    OfflineStatus.FAILED,
    -> null
}

internal fun completedDownloadFileIsUsable(
    file: File?,
    expectedBytes: Long,
): Boolean {
    if (file == null || !file.isFile || !file.canRead()) return false
    val actualBytes = runCatching { file.length() }.getOrDefault(-1L)
    if (actualBytes <= 0L) return false
    return expectedBytes <= 0L || actualBytes == expectedBytes
}

internal fun deleteDownloadFileIfPresent(file: File): Boolean =
    !file.exists() || (runCatching { file.delete() }.getOrDefault(false) && !file.exists())
