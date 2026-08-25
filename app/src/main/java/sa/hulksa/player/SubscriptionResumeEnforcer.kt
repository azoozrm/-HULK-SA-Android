package sa.hulksa.player

import android.content.Context
import android.os.SystemClock
import sa.hulksa.player.data.AccountSessionStore
import sa.hulksa.player.data.isAccountSessionExpired
import sa.hulksa.player.data.shouldRevalidateAccountOnResume

/**
 * Foreground-only subscription enforcement. Known local expiry is fail-safe and
 * immediate; authoritative server validation is throttled using the persisted
 * last authentication time plus a process-local failed-attempt guard.
 */
internal class SubscriptionResumeEnforcer(
    context: Context,
    private val viewModel: HulkViewModel,
) {
    private val accountSessionStore = AccountSessionStore(context.applicationContext)

    /**
     * @return true when a known expired session was invalidated and authenticated
     * resume work should be skipped for this foreground transition.
     */
    fun onResume(): Boolean {
        if (invalidateIfKnownExpired()) return true
        val account = viewModel.state.value.account ?: return false

        val metadata = accountSessionStore.metadata()
        val attemptOwner = metadata?.sessionId ?: "account:${account.username.trim()}"
        val previousAttemptElapsedMs = if (lastResumeRevalidationOwner == attemptOwner) {
            lastResumeRevalidationAttemptElapsedMs
        } else {
            0L
        }
        val nowEpochMs = System.currentTimeMillis()
        val nowElapsedMs = SystemClock.elapsedRealtime()
        if (
            !shouldRevalidateAccountOnResume(
                authenticatedAtEpochMs = metadata?.authenticatedAtEpochMs ?: 0L,
                lastAttemptElapsedMs = previousAttemptElapsedMs,
                nowEpochMs = nowEpochMs,
                nowElapsedMs = nowElapsedMs,
            )
        ) return false

        // refreshAccount already owns the single-flight gate shared with manual refresh.
        // Record the attempt before invoking it so rapid activity resumes do not retry a
        // transient failure or race a manual refresh into duplicate network validation.
        lastResumeRevalidationOwner = attemptOwner
        lastResumeRevalidationAttemptElapsedMs = nowElapsedMs
        viewModel.refreshAccount {
            // The Xtream status can still say Active while returning an expiry timestamp
            // that has just elapsed. Re-check after both success and transient failure so
            // a network error never extends a known local entitlement past its deadline.
            invalidateIfKnownExpired()
        }
        return false
    }

    private fun invalidateIfKnownExpired(): Boolean {
        val metadata = accountSessionStore.metadata()
        val expiresAtEpochSeconds = viewModel.state.value.account?.expiresAtEpochSeconds
            ?: metadata?.expiresAtEpochSeconds
        if (
            !isAccountSessionExpired(
                expiresAtEpochSeconds = expiresAtEpochSeconds,
                nowEpochSeconds = System.currentTimeMillis() / 1_000L,
            )
        ) return false

        viewModel.logout()
        return true
    }

    private companion object {
        @Volatile
        var lastResumeRevalidationOwner: String? = null

        @Volatile
        var lastResumeRevalidationAttemptElapsedMs: Long = 0L
    }
}
