package sa.hulksa.player

/**
 * Owns the logical lifetime of login attempts. Only one attempt may be current;
 * invalidation makes any older completion stale without allowing it to release
 * a newer attempt.
 *
 * HulkViewModel.login() is the sole production caller of isActive(); restoreSession() enters
 * authenticate() directly. That distinction lets us carry explicit Login-submit intent into the
 * process-local parental bootstrap proof without treating startup/remembered-session restore as
 * parental authorization.
 */
internal class AuthenticationAttemptGate(
    private val manualAuthProofTracker: ManualParentAuthProofTracker? = null,
) {
    private var generation: Long = 0L
    private var activeGeneration: Long? = null

    @Synchronized
    fun isActive(): Boolean {
        val active = activeGeneration != null
        if (!active) manualAuthProof().noteManualLoginPreflight()
        return active
    }

    @Synchronized
    fun tryStart(): Long? {
        if (activeGeneration != null) return null
        generation += 1L
        activeGeneration = generation
        manualAuthProof().beginAuthenticationAttempt()
        return generation
    }

    @Synchronized
    fun isCurrent(attemptGeneration: Long): Boolean =
        activeGeneration == attemptGeneration

    @Synchronized
    fun complete(attemptGeneration: Long): Boolean {
        if (activeGeneration != attemptGeneration) return false
        activeGeneration = null
        manualAuthProof().endAuthenticationAttempt()
        return true
    }

    @Synchronized
    fun invalidate() {
        generation += 1L
        activeGeneration = null
        manualAuthProof().invalidateAll()
    }

    private fun manualAuthProof(): ManualAuthAttemptObserver =
        manualAuthProofTracker?.let(::TrackerObserver) ?: RegistryObserver

    private class TrackerObserver(
        private val tracker: ManualParentAuthProofTracker,
    ) : ManualAuthAttemptObserver {
        override fun noteManualLoginPreflight() = tracker.noteManualLoginPreflight()
        override fun beginAuthenticationAttempt() = tracker.beginAuthenticationAttempt()
        override fun endAuthenticationAttempt() = tracker.endAuthenticationAttempt()
        override fun invalidateAll() = tracker.invalidateAll()
    }

    private object RegistryObserver : ManualAuthAttemptObserver {
        override fun noteManualLoginPreflight() = ManualParentAuthProofRegistry.noteManualLoginPreflight()
        override fun beginAuthenticationAttempt() = ManualParentAuthProofRegistry.beginAuthenticationAttempt()
        override fun endAuthenticationAttempt() = ManualParentAuthProofRegistry.endAuthenticationAttempt()
        override fun invalidateAll() = ManualParentAuthProofRegistry.invalidateAll()
    }
}

private interface ManualAuthAttemptObserver {
    fun noteManualLoginPreflight()
    fun beginAuthenticationAttempt()
    fun endAuthenticationAttempt()
    fun invalidateAll()
}
