package sa.hulksa.player.data

/**
 * Serializes the final persistence step of Player progress with every synchronous history
 * mutation. A mutation invalidates any progress snapshot that was built before it, while a
 * progress write that wins first is included in the mutation's subsequent read.
 */
internal class UserLibraryHistoryMutationGate {
    data class ProgressAttempt internal constructor(
        val generation: Long,
    )

    private var generation = 0L
    private var activeProgressGeneration: Long? = null

    @Synchronized
    fun beginProgress(): ProgressAttempt {
        generation += 1L
        activeProgressGeneration = generation
        return ProgressAttempt(generation)
    }

    @Synchronized
    fun beginHistoryMutation() {
        generation += 1L
        activeProgressGeneration = null
    }

    @Synchronized
    fun invalidateProgress() {
        generation += 1L
        activeProgressGeneration = null
    }

    @Synchronized
    fun isCurrent(attempt: ProgressAttempt): Boolean = activeProgressGeneration == attempt.generation

    @Synchronized
    fun <T> writeIfCurrent(attempt: ProgressAttempt, write: () -> T): T? =
        if (isCurrent(attempt)) write() else null
}
