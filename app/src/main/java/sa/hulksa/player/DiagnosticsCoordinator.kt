package sa.hulksa.player

import kotlinx.coroutines.CancellationException
import sa.hulksa.player.model.AuthenticatedSession

internal sealed interface DiagnosticsOutcome<out T> {
    data class Success<T>(val value: T) : DiagnosticsOutcome<T>
    data class Failure(val error: Throwable) : DiagnosticsOutcome<Nothing>
}

/**
 * Owns diagnostics single-flight and publication eligibility. Session changes
 * invalidate the active generation so stale progress and completion cannot
 * mutate UI state or release a newer attempt.
 */
internal class DiagnosticsCoordinator {
    data class Attempt internal constructor(
        val generation: Long,
        internal val session: AuthenticatedSession,
    )

    private var generation: Long = 0L
    private var activeGeneration: Long? = null

    @Synchronized
    fun tryStart(session: AuthenticatedSession): Attempt? {
        if (activeGeneration != null) return null
        generation += 1L
        activeGeneration = generation
        return Attempt(generation = generation, session = session)
    }

    @Synchronized
    fun isCurrent(
        attempt: Attempt,
        currentSession: AuthenticatedSession?,
    ): Boolean =
        currentSession === attempt.session && activeGeneration == attempt.generation

    @Synchronized
    fun invalidate() {
        generation += 1L
        activeGeneration = null
    }

    suspend fun <T> runCurrent(
        attempt: Attempt,
        currentSession: () -> AuthenticatedSession?,
        operation: suspend () -> T,
    ): DiagnosticsOutcome<T>? {
        return try {
            val value = operation()
            if (completeCurrent(attempt, currentSession())) {
                DiagnosticsOutcome.Success(value)
            } else {
                null
            }
        } catch (cancelled: CancellationException) {
            // Cancellation is control flow. Completing only the matching
            // generation prevents stale A from releasing replacement B.
            complete(attempt.generation)
            throw cancelled
        } catch (error: Throwable) {
            if (completeCurrent(attempt, currentSession())) {
                DiagnosticsOutcome.Failure(error)
            } else {
                null
            }
        }
    }

    @Synchronized
    private fun completeCurrent(
        attempt: Attempt,
        currentSession: AuthenticatedSession?,
    ): Boolean {
        val sameSession = currentSession === attempt.session
        val completed = complete(attempt.generation)
        return sameSession && completed
    }

    private fun complete(attemptGeneration: Long): Boolean {
        if (activeGeneration != attemptGeneration) return false
        activeGeneration = null
        return true
    }
}
