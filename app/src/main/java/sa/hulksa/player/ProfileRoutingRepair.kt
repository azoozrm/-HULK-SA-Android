package sa.hulksa.player

import kotlinx.coroutines.CancellationException
import sa.hulksa.player.data.AuthenticatedSessionOwner
import sa.hulksa.player.data.ProfileRoutingPreferences
import sa.hulksa.player.data.ProfileStoreSnapshot
import sa.hulksa.player.model.UserProfile

/**
 * Publication owner for the post-deletion routing repair.
 *
 * Routing preferences are only re-published while the operation's captured session owner is still
 * current, the account scope still matches and the caller's published snapshot/routing are still
 * the ones the repair was derived from. An obsolete completion can therefore never overwrite newer
 * routing after a session/profile/routing change, including logout followed by login to the same
 * account.
 */
internal class ProfileRoutingRepair(
    private val loadRouting: suspend (String, Set<String>) -> ProfileRoutingPreferences?,
    private val isSessionOwnerCurrent: (AuthenticatedSessionOwner) -> Boolean,
    private val activeAccountId: () -> String?,
) {
    suspend fun repair(
        expectedAccountId: String,
        sessionOwner: AuthenticatedSessionOwner,
        snapshot: ProfileStoreSnapshot,
        isStillCurrent: () -> Boolean,
        publish: (ProfileRoutingPreferences) -> Unit,
    ) {
        val repaired = try {
            loadRouting(
                expectedAccountId,
                snapshot.profiles.mapTo(linkedSetOf(), UserProfile::id),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (
            isSessionOwnerCurrent(sessionOwner) &&
            activeAccountId() == expectedAccountId &&
            isStillCurrent()
        ) {
            publish(repaired ?: ProfileRoutingPreferences())
        }
    }
}
