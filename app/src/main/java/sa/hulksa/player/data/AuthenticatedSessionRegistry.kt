package sa.hulksa.player.data

import sa.hulksa.player.model.AuthenticatedSession

internal data class AuthenticatedSessionOwner(
    val accountId: String,
    val providerId: String,
    val sessionId: String,
    val session: AuthenticatedSession,
)

internal fun authenticatedOwnerPreferencesName(
    baseName: String,
    owner: AuthenticatedSessionOwner,
): String = accountScopedPreferencesName(
    baseName = "$baseName.provider.${owner.providerId}",
    accountId = owner.accountId,
)

/**
 * Process-only authenticated session handoff for UI-owned capabilities that must reuse the
 * already authenticated Xtream session. Credentials remain persisted only by CredentialVault;
 * this registry is cleared on logout and disappears with the process.
 */
internal object AuthenticatedSessionRegistry {
    @Volatile
    private var activeOwner: AuthenticatedSessionOwner? = null

    @Synchronized
    fun update(session: AuthenticatedSession, metadata: AccountSessionMetadata) {
        activeOwner = AuthenticatedSessionOwner(
            accountId = metadata.accountId,
            providerId = stableAccountId(
                portalBaseUrl = session.portal.baseUrl,
                username = session.credentials.username,
            ),
            sessionId = metadata.sessionId,
            session = session,
        )
    }

    @Synchronized
    fun current(): AuthenticatedSession? = activeOwner?.session

    @Synchronized
    fun currentOwner(): AuthenticatedSessionOwner? = activeOwner

    @Synchronized
    fun isCurrent(owner: AuthenticatedSessionOwner): Boolean = activeOwner === owner

    @Synchronized
    fun <T> withCurrentOwner(owner: AuthenticatedSessionOwner, block: () -> T): T? =
        if (activeOwner === owner) block() else null

    @Synchronized
    fun clear() {
        activeOwner = null
    }
}
