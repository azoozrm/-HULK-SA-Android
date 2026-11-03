package sa.hulksa.player.data

/** Process-local auth ownership, reserved before provider IO and invalidated by newer ownership. */
internal class AuthenticationAttempt internal constructor(val id: Long)

internal object AuthenticationAttemptRegistry {
    private var nextId = 0L
    private var activeAttempt: AuthenticationAttempt? = null

    @Synchronized
    fun begin(): AuthenticationAttempt = AuthenticationAttempt(++nextId).also { activeAttempt = it }

    @Synchronized
    fun isCurrent(attempt: AuthenticationAttempt): Boolean = activeAttempt === attempt

    @Synchronized
    fun hasActiveAttempt(): Boolean = activeAttempt != null

    @Synchronized
    fun invalidate(attempt: AuthenticationAttempt? = null) {
        if (attempt == null || activeAttempt === attempt) activeAttempt = null
    }
}
