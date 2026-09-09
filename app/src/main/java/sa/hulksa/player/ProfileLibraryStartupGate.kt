package sa.hulksa.player

import sa.hulksa.player.model.AuthenticatedSession

/**
 * Owns the one in-flight initial library load. A new profile selection or
 * session transition invalidates the prior generation before it can publish.
 */
internal class ProfileLibraryStartupGate {
    data class Attempt internal constructor(
        val generation: Long,
        val accountId: String,
        val profileId: String,
        internal val session: AuthenticatedSession,
    )

    private var generation: Long = 0L
    private var activeGeneration: Long? = null

    @Synchronized
    fun start(
        session: AuthenticatedSession,
        accountId: String,
        profileId: String,
    ): Attempt {
        generation += 1L
        activeGeneration = generation
        return Attempt(
            generation = generation,
            accountId = accountId,
            profileId = profileId,
            session = session,
        )
    }

    @Synchronized
    fun isCurrent(attempt: Attempt, currentSession: AuthenticatedSession?): Boolean =
        activeGeneration == attempt.generation && currentSession === attempt.session

    @Synchronized
    fun completeIfCurrent(attempt: Attempt, currentSession: AuthenticatedSession?): Boolean {
        if (!isCurrent(attempt, currentSession)) return false
        activeGeneration = null
        return true
    }

    @Synchronized
    fun invalidate() {
        generation += 1L
        activeGeneration = null
    }
}
