package sa.hulksa.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sa.hulksa.player.DownloadSettingsMutationGate
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus

/**
 * The action the user actually saw and pressed. It is captured with the rendered download row
 * and passed through unchanged into the ViewModel; the authoritative record only validates it.
 */
enum class DownloadActionIntent {
    RETRY,
    RESUME,
    PAUSE,
}

internal enum class DownloadActionResolution {
    RESUME,
    PAUSE,
    ALREADY_QUEUED,
    ALREADY_COMPLETED,
    NOT_APPLICABLE,
}

/**
 * Resolves the explicit user intent against the authoritative status. A stale RETRY never
 * becomes PAUSE; only the exact status the intent owns may be mutated.
 */
internal fun resolveDownloadAction(
    intent: DownloadActionIntent,
    status: OfflineStatus,
): DownloadActionResolution = when (intent) {
    DownloadActionIntent.RETRY -> when (status) {
        OfflineStatus.FAILED -> DownloadActionResolution.RESUME
        OfflineStatus.QUEUED,
        OfflineStatus.CHECKING,
        OfflineStatus.DOWNLOADING,
        -> DownloadActionResolution.ALREADY_QUEUED
        OfflineStatus.COMPLETED -> DownloadActionResolution.ALREADY_COMPLETED
        OfflineStatus.PAUSED,
        OfflineStatus.WAITING_SCHEDULE,
        OfflineStatus.WAITING_NETWORK,
        OfflineStatus.WAITING_STORAGE,
        -> DownloadActionResolution.NOT_APPLICABLE
    }
    DownloadActionIntent.RESUME -> when (status) {
        OfflineStatus.PAUSED,
        OfflineStatus.WAITING_SCHEDULE,
        OfflineStatus.WAITING_NETWORK,
        OfflineStatus.WAITING_STORAGE,
        OfflineStatus.FAILED,
        -> DownloadActionResolution.RESUME
        OfflineStatus.QUEUED,
        OfflineStatus.CHECKING,
        OfflineStatus.DOWNLOADING,
        -> DownloadActionResolution.ALREADY_QUEUED
        OfflineStatus.COMPLETED -> DownloadActionResolution.ALREADY_COMPLETED
    }
    DownloadActionIntent.PAUSE -> when (status) {
        OfflineStatus.QUEUED,
        OfflineStatus.CHECKING,
        OfflineStatus.DOWNLOADING,
        -> DownloadActionResolution.PAUSE
        OfflineStatus.PAUSED,
        OfflineStatus.WAITING_SCHEDULE,
        OfflineStatus.WAITING_NETWORK,
        OfflineStatus.WAITING_STORAGE,
        OfflineStatus.FAILED,
        -> DownloadActionResolution.NOT_APPLICABLE
        OfflineStatus.COMPLETED -> DownloadActionResolution.ALREADY_COMPLETED
    }
}

internal enum class DownloadActionClaim {
    APPLIED,
    ALREADY_QUEUED,
    ALREADY_COMPLETED,
    NOT_APPLICABLE,
    OWNER_CHANGED,
    PERSISTENCE_FAILED,
    FAILED,
    SUPERSEDED,
}

internal data class DownloadActionOutcome(
    val claim: DownloadActionClaim,
    val downloads: List<OfflineDownload> = emptyList(),
    val status: OfflineStatus? = null,
)

/** The narrow authoritative surface the production dispatcher is allowed to mutate. */
internal interface DownloadPrimaryActionStore {
    fun record(
        downloadId: Long,
        expectedAccountId: String,
        expectedProfileId: String,
    ): OfflineDownload?

    fun snapshot(): List<OfflineDownload>

    fun pause(
        downloadId: Long,
        expectedAccountId: String,
        expectedProfileId: String,
    ): ProfileDownloadPauseOutcome

    fun resume(
        downloadId: Long,
        expectedAccountId: String,
        expectedProfileId: String,
    ): ProfileDownloadResumeOutcome
}

/**
 * Serializes the complete authoritative-record read and intent mutation for one download.
 *
 * [DownloadSettingsMutationGate] already owns owner-transition invalidation. Each input starts a
 * new generation and the persistence mutex wraps the read plus the mutation, so overlapping input
 * for the same download cannot interleave. An invalidated older input returns [DownloadActionClaim.SUPERSEDED]
 * without touching the record.
 */
internal class DownloadPrimaryActionDispatcher(
    private val downloads: DownloadPrimaryActionStore,
) {
    private val gates = mutableMapOf<Long, DownloadSettingsMutationGate>()

    fun invalidate() {
        val invalidated = synchronized(gates) {
            gates.values.toList().also { gates.clear() }
        }
        invalidated.forEach(DownloadSettingsMutationGate::invalidate)
    }

    suspend fun dispatch(
        downloadId: Long,
        expectedAccountId: String,
        expectedProfileId: String,
        intent: DownloadActionIntent,
    ): DownloadActionOutcome {
        val gate = synchronized(gates) {
            gates.getOrPut(downloadId, ::DownloadSettingsMutationGate)
        }
        val attempt = gate.begin()
        val result = withContext(Dispatchers.IO) {
            gate.writeIfCurrent(attempt) {
                dispatchCurrent(
                    downloadId = downloadId,
                    expectedAccountId = expectedAccountId,
                    expectedProfileId = expectedProfileId,
                    intent = intent,
                )
            }
        }
        // Ownership may have been superseded during IO or while this continuation was queued
        // back to the caller. The post-IO check must run here, not only inside the IO block.
        if (result == null || !gate.isCurrent(attempt)) {
            return DownloadActionOutcome(DownloadActionClaim.SUPERSEDED)
        }
        return result
    }

    private fun dispatchCurrent(
        downloadId: Long,
        expectedAccountId: String,
        expectedProfileId: String,
        intent: DownloadActionIntent,
    ): DownloadActionOutcome {
        val record = downloads.record(
            downloadId = downloadId,
            expectedAccountId = expectedAccountId,
            expectedProfileId = expectedProfileId,
        ) ?: return DownloadActionOutcome(DownloadActionClaim.OWNER_CHANGED)

        return when (resolveDownloadAction(intent, record.status)) {
            DownloadActionResolution.ALREADY_QUEUED -> DownloadActionOutcome(
                claim = DownloadActionClaim.ALREADY_QUEUED,
                downloads = downloads.snapshot(),
                status = record.status,
            )
            DownloadActionResolution.ALREADY_COMPLETED -> DownloadActionOutcome(
                claim = DownloadActionClaim.ALREADY_COMPLETED,
                downloads = downloads.snapshot(),
                status = record.status,
            )
            DownloadActionResolution.NOT_APPLICABLE -> DownloadActionOutcome(
                claim = DownloadActionClaim.NOT_APPLICABLE,
                downloads = downloads.snapshot(),
                status = record.status,
            )
            DownloadActionResolution.PAUSE -> {
                val pause = downloads.pause(
                    downloadId = downloadId,
                    expectedAccountId = expectedAccountId,
                    expectedProfileId = expectedProfileId,
                )
                val currentStatus = downloads.record(
                    downloadId = downloadId,
                    expectedAccountId = expectedAccountId,
                    expectedProfileId = expectedProfileId,
                )?.status
                when {
                    pause.persisted && currentStatus == OfflineStatus.PAUSED -> {
                        DownloadActionOutcome(
                            claim = DownloadActionClaim.APPLIED,
                            downloads = pause.downloads,
                            status = currentStatus,
                        )
                    }
                    currentStatus == OfflineStatus.COMPLETED -> DownloadActionOutcome(
                        claim = DownloadActionClaim.ALREADY_COMPLETED,
                        downloads = pause.downloads,
                        status = currentStatus,
                    )
                    else -> DownloadActionOutcome(
                        claim = DownloadActionClaim.PERSISTENCE_FAILED,
                        downloads = pause.downloads,
                        status = currentStatus,
                    )
                }
            }
            DownloadActionResolution.RESUME -> {
                val resume = downloads.resume(
                    downloadId = downloadId,
                    expectedAccountId = expectedAccountId,
                    expectedProfileId = expectedProfileId,
                )
                when {
                    resume.resumed -> DownloadActionOutcome(
                        claim = DownloadActionClaim.APPLIED,
                        downloads = resume.downloads,
                        status = resume.status,
                    )
                    resume.status == OfflineStatus.COMPLETED -> DownloadActionOutcome(
                        claim = DownloadActionClaim.ALREADY_COMPLETED,
                        downloads = resume.downloads,
                        status = resume.status,
                    )
                    else -> DownloadActionOutcome(
                        claim = DownloadActionClaim.FAILED,
                        downloads = resume.downloads,
                        status = resume.status,
                    )
                }
            }
        }
    }
}
