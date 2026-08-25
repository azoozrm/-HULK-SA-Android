package sa.hulksa.player

/**
 * Owns the logical lifetime of login attempts. Only one attempt may be current;
 * invalidation makes any older completion stale without allowing it to release
 * a newer attempt.
 */
internal class AuthenticationAttemptGate {
    private var generation: Long = 0L
    private var activeGeneration: Long? = null

    @Synchronized
    fun isActive(): Boolean = activeGeneration != null

    @Synchronized
    fun tryStart(): Long? {
        if (activeGeneration != null) return null
        generation += 1L
        activeGeneration = generation
        return generation
    }

    @Synchronized
    fun isCurrent(attemptGeneration: Long): Boolean =
        activeGeneration == attemptGeneration

    @Synchronized
    fun complete(attemptGeneration: Long): Boolean {
        if (activeGeneration != attemptGeneration) return false
        activeGeneration = null
        return true
    }

    @Synchronized
    fun invalidate() {
        generation += 1L
        activeGeneration = null
    }
}
