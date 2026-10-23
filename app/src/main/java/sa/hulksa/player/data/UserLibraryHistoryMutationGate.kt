package sa.hulksa.player.data

/**
 * Serializes the final persistence step of Player progress with every history mutation.
 * A progress attempt is invalidated by a newer progress attempt or a history mutation.
 * A history write attempt is invalidated only by a newer destructive history mutation
 * (remove/clear), so two fast playback starts can both persist in lock order.
 */
internal class UserLibraryHistoryMutationGate {
    data class ProgressAttempt internal constructor(
        val generation: Long,
    )

    data class HistoryAttempt internal constructor(
        val generation: Long,
    )

    private var historyGeneration = 0L
    private var progressGeneration = 0L
    private var activeProgressGeneration: Long? = null

    @Synchronized
    fun currentHistoryAttempt(): HistoryAttempt = HistoryAttempt(historyGeneration)

    @Synchronized
    fun beginProgress(): ProgressAttempt {
        progressGeneration += 1L
        activeProgressGeneration = progressGeneration
        return ProgressAttempt(progressGeneration)
    }

    @Synchronized
    fun beginHistoryMutation(): HistoryAttempt {
        historyGeneration += 1L
        activeProgressGeneration = null
        return HistoryAttempt(historyGeneration)
    }

    @Synchronized
    fun invalidateProgress() {
        progressGeneration += 1L
        activeProgressGeneration = null
    }

    @Synchronized
    fun isCurrentHistory(attempt: HistoryAttempt): Boolean =
        historyGeneration == attempt.generation

    @Synchronized
    fun isCurrent(attempt: ProgressAttempt): Boolean =
        activeProgressGeneration == attempt.generation

    fun <T> writeIfCurrent(attempt: ProgressAttempt, write: () -> T): T? {
        // Do not hold the generation monitor across blocking recovery/persistence IO.
        if (!isCurrent(attempt)) return null
        return write()
    }
}
