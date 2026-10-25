package sa.hulksa.player.data

import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.security.CredentialEnvelopeRemovalException

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
        attemptCurrent: () -> Boolean = { true },
        publish: () -> T,
    ): SessionPublicationResult<T> {
        if (expectedOwner == null || expectedOwner.session !== authenticated || !attemptCurrent()) {
            return SessionPublicationResult.Stale
        }
        val currentOwner = AuthenticatedSessionRegistry.currentOwner()
            ?.takeIf { owner ->
                owner.accountId == expectedOwner.accountId &&
                    owner.providerId == expectedOwner.providerId &&
                    owner.sessionId == expectedOwner.sessionId &&
                    owner.session === authenticated
            }
            ?: return SessionPublicationResult.Stale
        // Owner validation, expiry validation and publication are one serialized ownership
        // operation. A concurrent commit cannot replace the owner between the check and publish.
        return AuthenticatedSessionRegistry.withCurrentOwner(currentOwner) {
            if (
                isAccountSessionExpired(
                    expiresAtEpochSeconds = authenticated.account.expiresAtEpochSeconds,
                    nowEpochSeconds = nowEpochSeconds(),
                )
            ) {
                SessionPublicationResult.Expired
            } else {
                SessionPublicationResult.Published(publish())
            }
        } ?: SessionPublicationResult.Stale
    }
}

/**
 * Applies expired-owner visible cleanup only when the owner-bound repository mutation succeeded
 * and no newer session publication happened while cleanup was in flight.
 */
internal sealed interface SessionOwnershipCleanupResult {
    data object Cleared : SessionOwnershipCleanupResult
    data object Stale : SessionOwnershipCleanupResult
    data class CredentialRemovalFailed(
        val error: CredentialEnvelopeRemovalException,
    ) : SessionOwnershipCleanupResult
}

internal class SessionOwnershipCleanupGate(
    private val ownershipEpoch: () -> Long,
    private val currentOwner: () -> AuthenticatedSessionOwner?,
    private val logoutIfOwnedBy: suspend (AuthenticatedSessionOwner) -> Boolean,
) {
    suspend fun clearIfOwnerStillCurrent(
        expectedOwner: AuthenticatedSessionOwner,
        applyClearedState: () -> Unit,
    ): SessionOwnershipCleanupResult {
        val epochBeforeCleanup = ownershipEpoch()
        val cleared = try {
            logoutIfOwnedBy(expectedOwner)
        } catch (error: CredentialEnvelopeRemovalException) {
            return if (isStillExpectedOwner(expectedOwner, epochBeforeCleanup)) {
                SessionOwnershipCleanupResult.CredentialRemovalFailed(error)
            } else {
                SessionOwnershipCleanupResult.Stale
            }
        }
        if (!cleared) return SessionOwnershipCleanupResult.Stale
        if (
            ownershipEpoch() != epochBeforeCleanup ||
            currentOwner() != null
        ) {
            return SessionOwnershipCleanupResult.Stale
        }
        applyClearedState()
        return SessionOwnershipCleanupResult.Cleared
    }

    private fun isStillExpectedOwner(
        expectedOwner: AuthenticatedSessionOwner,
        epochBeforeCleanup: Long,
    ): Boolean = ownershipEpoch() == epochBeforeCleanup &&
        currentOwner()?.let { owner ->
            owner.accountId == expectedOwner.accountId &&
                owner.providerId == expectedOwner.providerId &&
                owner.sessionId == expectedOwner.sessionId
        } == true
}
