package sa.hulksa.player.ui.screens

/**
 * Single-flight token guard for profile PIN submissions.
 *
 * A cancelled/finished token can never become current again, so a late result from an older
 * coroutine cannot overwrite state belonging to a newer PIN operation.
 */
internal class ProfilePinOperationGuard {
    private var generation: Long = 0L
    private var activeToken: Long? = null

    fun begin(): Long? {
        if (activeToken != null) return null
        generation++
        return generation.also { activeToken = it }
    }

    fun isCurrent(token: Long): Boolean = activeToken == token

    fun finish(token: Long) {
        if (activeToken == token) activeToken = null
    }

    fun cancel() {
        generation++
        activeToken = null
    }
}
