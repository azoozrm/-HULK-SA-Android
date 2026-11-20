package sa.hulksa.player.ui.screens

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import sa.hulksa.player.data.FootballDayFailure
import sa.hulksa.player.data.FootballFeedSnapshot

internal data class FootballAdjacentDayInputs(
    val todayKey: String,
    val exactSnapshot: FootballFeedSnapshot?,
    val failure: FootballDayFailure?,
    val loading: Boolean,
    val retryAtEpochMs: Long,
    val discoveryRefreshSeconds: Int?,
)

/** One timer for the selected adjacent day, including its first load and failed requests. */
internal class FootballAdjacentDayScheduler(private val selectedDay: String) {
    private var lastAttemptAtEpochMs: Long? = null

    internal fun nextDelayMs(nowEpochMs: Long, input: FootballAdjacentDayInputs): Long? {
        val refreshSeconds = input.discoveryRefreshSeconds ?: return null
        if (selectedDay == input.todayKey || input.loading) return null

        val intervalMs = refreshSeconds.coerceAtLeast(1) * 1_000L
        val receivedAt = input.exactSnapshot?.let {
            it.fetchedAtEpochMs.takeIf { fetchedAt -> fetchedAt > 0L } ?: it.cachedAtEpochMs
        }
        val failure = input.failure
        val eligibleAt = when {
            failure != null && failure.retryAtEpochMs > 0L -> failure.retryAtEpochMs
            failure != null -> failure.attemptedAtEpochMs + maxOf(60_000L, intervalMs)
            receivedAt != null && receivedAt in 1..nowEpochMs -> receivedAt + intervalMs
            else -> nowEpochMs
        }
        val nextAttemptAt = lastAttemptAtEpochMs?.plus(intervalMs) ?: nowEpochMs
        return (maxOf(eligibleAt, nextAttemptAt, input.retryAtEpochMs) - nowEpochMs).coerceAtLeast(0L)
    }

    /**
     * Re-read the live inputs at each wake-up. The refresh owner can reject an attempt without
     * publishing new state; the attempt time still re-arms the timer and prevents an instant loop.
     * The caller ties this loop to one day, one discovery source and the RESUMED lifecycle.
     */
    suspend fun run(
        readInputs: () -> FootballAdjacentDayInputs,
        awaitChangeOrTimeout: suspend (FootballAdjacentDayInputs, Long?) -> Unit,
        requestRefresh: (force: Boolean) -> Unit,
        now: () -> Long = System::currentTimeMillis,
    ) {
        while (currentCoroutineContext().isActive) {
            val input = readInputs()
            val attemptedAt = now()
            val waitMs = nextDelayMs(attemptedAt, input)
            if (waitMs == 0L) {
                lastAttemptAtEpochMs = attemptedAt
                requestRefresh(input.failure != null)
            } else {
                awaitChangeOrTimeout(input, waitMs)
            }
        }
    }
}
