package sa.hulksa.player

/**
 * Owns the latest Player progress persistence attempt. A newer callback or an owner transition
 * invalidates every prior attempt before it can write or publish a stale history snapshot.
 */
internal class PlayerProgressPersistenceGate {
    data class Attempt internal constructor(
        val generation: Long,
    )

    private var generation: Long = 0L
    private var activeGeneration: Long? = null

    @Synchronized
    fun start(): Attempt {
        generation += 1L
        activeGeneration = generation
        return Attempt(generation = generation)
    }

    @Synchronized
    fun isCurrent(attempt: Attempt): Boolean = activeGeneration == attempt.generation

    @Synchronized
    fun invalidate() {
        generation += 1L
        activeGeneration = null
    }
}
