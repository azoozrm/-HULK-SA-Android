package sa.hulksa.player

import kotlinx.coroutines.CancellationException
import sa.hulksa.player.data.AuthenticatedSessionOwner
import sa.hulksa.player.data.ProfileRoutingPreferences
import sa.hulksa.player.model.UserProfile

/**
 * Publication owner for the post-deletion routing repair.
 *
 * The repair derives its profile IDs from the live presentation at operation start, so a newer
 * profile/routing mutation published before the loader runs is never repaired away with a stale
 * deletion-time profile set. Routing preferences are re-published only while the operation's
 * captured session owner is still current, the account scope still matches and the presentation
 * the operation started from is still the mounted one. An obsolete completion can therefore never
 * overwrite newer routing after a session/profile/routing change, including logout followed by
 * login to the same account.
 */
internal class ProfileRoutingRepair(
    private val loadRouting: suspend (String, Set<String>) -> ProfileRoutingPreferences?,
    private val isSessionOwnerCurrent: (AuthenticatedSessionOwner) -> Boolean,
    private val activeAccountId: () -> String?,
) {
    suspend fun repair(
        expectedAccountId: String,
        sessionOwner: AuthenticatedSessionOwner,
        presentation: () -> ProfileStartupPresentation,
        publish: (ProfileRoutingPreferences) -> Unit,
    ) {
        val startedFrom = presentation()
        val snapshot = startedFrom.snapshot ?: return
        if (startedFrom.routing == null) return
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
            presentation() === startedFrom
        ) {
            publish(repaired ?: ProfileRoutingPreferences())
        }
    }
}
