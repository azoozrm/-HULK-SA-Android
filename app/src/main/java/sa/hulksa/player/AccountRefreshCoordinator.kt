package sa.hulksa.player

import kotlinx.coroutines.CancellationException

internal sealed interface AccountRefreshOutcome<out T> {
    data class Success<T>(val value: T) : AccountRefreshOutcome<T>
    data class Failure(val error: Throwable) : AccountRefreshOutcome<Nothing>
}

/**
 * Owns account-refresh single-flight and completion delivery. A completion must
 * claim its generation before the ViewModel may mutate session/UI state or call
 * the user-visible result callback. Session transitions invalidate ownership so
 * an older completion cannot release or overwrite a newer refresh.
 */
internal class AccountRefreshCoordinator {
    private val attemptGate = AuthenticationAttemptGate()

    fun tryStart(): Long? = attemptGate.tryStart()

    fun invalidate() {
        attemptGate.invalidate()
    }

    suspend fun <T> runCurrent(
        attemptGeneration: Long,
        operation: suspend () -> T,
    ): AccountRefreshOutcome<T>? {
        return try {
            val value = operation()
            if (attemptGate.complete(attemptGeneration)) {
                AccountRefreshOutcome.Success(value)
            } else {
                null
            }
        } catch (cancelled: CancellationException) {
            // Cancellation is control flow, never a refresh failure. Releasing only
            // the matching generation keeps a stale A from releasing a current B.
            attemptGate.complete(attemptGeneration)
            throw cancelled
        } catch (error: Throwable) {
            if (attemptGate.complete(attemptGeneration)) {
                AccountRefreshOutcome.Failure(error)
            } else {
                null
            }
        }
    }
}
