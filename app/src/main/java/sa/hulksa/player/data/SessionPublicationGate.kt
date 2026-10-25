package sa.hulksa.player.data

import sa.hulksa.player.model.AuthenticatedSession

internal sealed interface SessionPublicationResult<out T> {
    data class Published<T>(val value: T) : SessionPublicationResult<T>
    data object Expired : SessionPublicationResult<Nothing>
    data object Stale : SessionPublicationResult<Nothing>
}

/**
 * Final publication gate after asynchronous authentication work. The session must still belong to
 * the exact authenticated owner, the originating attempt must still be current, and the provider
 * expiry must not have elapsed at the moment of publication.
 */
internal class SessionPublicationGate(
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) {
    fun <T> publishIfCurrent(
        authenticated: AuthenticatedSession,
        expectedOwner: AuthenticatedSessionOwner?,
        currentOwner: () -> AuthenticatedSessionOwner?,
        attemptCurrent: () -> Boolean = { true },
        publish: () -> T,
    ): SessionPublicationResult<T> {
        if (expectedOwner == null || !attemptCurrent()) return SessionPublicationResult.Stale
        val owner = currentOwner()
        if (owner !== expectedOwner || owner.session !== authenticated) {
            return SessionPublicationResult.Stale
        }
        if (
            isAccountSessionExpired(
                expiresAtEpochSeconds = authenticated.account.expiresAtEpochSeconds,
                nowEpochSeconds = nowEpochSeconds(),
            )
        ) {
            return SessionPublicationResult.Expired
        }
        return SessionPublicationResult.Published(publish())
    }
}
