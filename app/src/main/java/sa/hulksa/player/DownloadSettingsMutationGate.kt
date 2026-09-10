package sa.hulksa.player

/** Keeps only the newest Downloads settings intent eligible for durable storage. */
internal class DownloadSettingsMutationGate {
    data class Attempt internal constructor(val generation: Long)

    private var generation = 0L
    private var activeGeneration: Long? = null

    @Synchronized
    fun begin(): Attempt {
        generation += 1L
        activeGeneration = generation
        return Attempt(generation)
    }

    @Synchronized
    fun isCurrent(attempt: Attempt): Boolean = activeGeneration == attempt.generation

    @Synchronized
    fun invalidate() {
        generation += 1L
        activeGeneration = null
    }

    @Synchronized
    fun <T> writeIfCurrent(attempt: Attempt, write: () -> T): T? =
        if (activeGeneration == attempt.generation) write() else null
}
