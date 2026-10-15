package sa.hulksa.player

import kotlinx.coroutines.CancellationException
import sa.hulksa.player.data.AuthenticatedSessionOwner
import sa.hulksa.player.data.ProfileRoutingPreferences
import sa.hulksa.player.data.ProfileStoreSnapshot
import sa.hulksa.player.model.UserProfile

/**
 * Identity of one profile-startup presentation scope: the account that owns the persisted profile
 * data plus the provider session that published it. A session-owner rotation inside the same scope
 * is a continuation and must not dispose an already-ready presentation.
 */
internal data class ProfileStartupScope(
    val accountId: String,
    val providerId: String,
)

/**
 * The complete startup presentation owned by the composition. One value keeps the scope, the ready
 * snapshot/routing and the recoverable-failure flag coherent for a single publication.
 */
internal data class ProfileStartupPresentation(
    val scope: ProfileStartupScope?,
    val snapshot: ProfileStoreSnapshot?,
    val routing: ProfileRoutingPreferences?,
    val failed: Boolean,
) {
    companion object {
        val Cleared = ProfileStartupPresentation(null, null, null, false)
        val Failed = ProfileStartupPresentation(null, null, null, true)
    }
}

/** Production mutation rule for profile mutations published inside one presentation owner. */
internal fun ProfileStartupPresentation.withProfileSnapshot(
    snapshot: ProfileStoreSnapshot,
): ProfileStartupPresentation = copy(snapshot = snapshot)

/** Production mutation rule for routing mutations published inside one presentation owner. */
internal fun ProfileStartupPresentation.withRoutingPreferences(
    routing: ProfileRoutingPreferences,
): ProfileStartupPresentation = copy(routing = routing)

/**
 * Ordered, cancellation-aware startup readiness for the current authenticated session owner.
 *
 * The authoritative profile snapshot is loaded first; routing preferences are then loaded from
 * that exact snapshot. Every publication is validated against the still-current session owner and
 * account scope, so an obsolete attempt can never publish. One cancellable operation owns both
 * stages, so a fast equal-content snapshot reload cannot leave routing readiness unpublished
 * behind a coalesced intermediate state.
 */
internal class ProfileStartupReadiness(
    private val loadSnapshot: suspend (String) -> ProfileStoreSnapshot?,
    private val loadRouting: suspend (String, Set<String>) -> ProfileRoutingPreferences?,
    private val isSessionOwnerCurrent: (AuthenticatedSessionOwner) -> Boolean,
    private val activeAccountId: () -> String?,
) {
    internal sealed interface Result {
        /** The owner or account scope changed while loading; nothing may be published. */
        data object Abandoned : Result

        /** The authoritative snapshot could not be loaded; the caller exposes a recoverable failure. */
        data object Failed : Result

        data class Ready(
            val snapshot: ProfileStoreSnapshot,
            val routing: ProfileRoutingPreferences,
        ) : Result
    }

    suspend fun load(
        accountId: String,
        sessionOwner: AuthenticatedSessionOwner,
    ): Result {
        val snapshot = try {
            loadSnapshot(accountId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (!isCurrent(accountId, sessionOwner)) return Result.Abandoned
        if (snapshot == null) return Result.Failed

        val routing = try {
            loadRouting(
                accountId,
                snapshot.profiles.mapTo(linkedSetOf(), UserProfile::id),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (!isCurrent(accountId, sessionOwner)) return Result.Abandoned
        // A failed routing load falls back to the safe picker path, never to reused routing data.
        return Result.Ready(
            snapshot = snapshot,
            routing = routing ?: ProfileRoutingPreferences(),
        )
    }

    /**
     * Runs one startup or session-rotation attempt for the current session owner and publishes the
     * coherent presentation through [onPublish].
     *
     * A session-owner rotation inside the already-published scope is a continuation: the mounted
     * presentation is revalidated in place and never cleared, so the Settings/HulkApp subtree,
     * its navigation memory, focus and scoped notices survive a successful account refresh. Cold
     * startup, a different account/provider scope and unresolved owners clear the presentation and
     * use the startup loading/failure surface; a failed continuation load keeps the mounted
     * presentation and leaves recovery to the existing refresh notice path.
     *
     * A current session and account are necessary but not sufficient: the load only publishes
     * while the presentation it started from is still the one mounted. A profile or routing
     * mutation published through the production mutation rules during the load supersedes this
     * attempt, so an obsolete completion can never roll the newer presentation back.
     */
    suspend fun run(
        authenticated: Boolean,
        accountId: String?,
        sessionOwner: AuthenticatedSessionOwner?,
        presentation: () -> ProfileStartupPresentation,
        onPublish: (ProfileStartupPresentation) -> Unit,
    ) {
        if (!authenticated) {
            onPublish(ProfileStartupPresentation.Cleared)
            return
        }
        if (accountId == null) {
            onPublish(ProfileStartupPresentation.Failed)
            return
        }
        if (sessionOwner == null) {
            onPublish(ProfileStartupPresentation.Cleared)
            return
        }

        val scope = ProfileStartupScope(accountId, sessionOwner.providerId)
        val startedFrom = presentation()
        val continuation = startedFrom.scope == scope &&
            startedFrom.snapshot != null &&
            startedFrom.routing != null
        if (!continuation) {
            onPublish(ProfileStartupPresentation.Cleared)
        }
        // The presentation the load must still own at completion: the mounted one for a
        // continuation, the just-published cleared one for a cold/transitional start.
        val publicationBaseline = if (continuation) startedFrom else presentation()

        when (val readiness = load(accountId, sessionOwner)) {
            Result.Abandoned -> Unit
            Result.Failed -> if (!continuation) onPublish(ProfileStartupPresentation.Failed)
            is Result.Ready -> if (presentation() === publicationBaseline) {
                onPublish(
                    ProfileStartupPresentation(
                        scope = scope,
                        snapshot = readiness.snapshot,
                        routing = readiness.routing,
                        failed = false,
                    ),
                )
            }
        }
    }

    private fun isCurrent(accountId: String, sessionOwner: AuthenticatedSessionOwner): Boolean =
        isSessionOwnerCurrent(sessionOwner) && activeAccountId() == accountId
}
