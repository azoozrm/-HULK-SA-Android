package sa.hulksa.player.data

import sa.hulksa.player.model.AuthenticatedSession

/**
 * Process-only authenticated session handoff for UI-owned capabilities that must reuse the
 * already authenticated Xtream session. Credentials remain persisted only by CredentialVault;
 * this registry is cleared on logout and disappears with the process.
 */
internal object AuthenticatedSessionRegistry {
    @Volatile
    private var activeSession: AuthenticatedSession? = null

    fun update(session: AuthenticatedSession) {
        activeSession = session
    }

    fun current(): AuthenticatedSession? = activeSession

    fun clear() {
        activeSession = null
    }
}
