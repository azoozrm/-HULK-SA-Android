package sa.hulksa.player.data

/**
 * Process-local ownership for a deferred account-identity decision. A decision may only commit
 * while this exact attempt is still the authoritative pending authentication. Logout, a newer
 * authentication, or any session ownership mutation invalidates the token.
 */
internal class AuthenticationDecisionAttempt internal constructor(
    val id: Long,
)

internal object PendingAuthenticationDecisionRegistry {
    private var nextId: Long = 0L
    private var activeAttempt: AuthenticationDecisionAttempt? = null

    @Synchronized
    fun begin(): AuthenticationDecisionAttempt {
        nextId += 1L
        return AuthenticationDecisionAttempt(nextId).also { activeAttempt = it }
    }

    @Synchronized
    fun isCurrent(attempt: AuthenticationDecisionAttempt): Boolean =
        activeAttempt === attempt

    @Synchronized
    fun invalidate(attempt: AuthenticationDecisionAttempt? = null) {
        if (attempt == null || activeAttempt === attempt) activeAttempt = null
    }
}
