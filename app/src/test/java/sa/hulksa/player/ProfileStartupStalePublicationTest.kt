package sa.hulksa.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.data.AuthenticatedSessionOwner
import sa.hulksa.player.data.ProfileRoutingPreferences
import sa.hulksa.player.data.ProfileStoreSnapshot
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig
import sa.hulksa.player.model.ProfileKind
import sa.hulksa.player.model.UserProfile

/**
 * R03 same-session mutation freshness contracts.
 *
 * These tests drive the production startup owner and routing-repair owner through deterministic
 * interleavings in which a profile/routing mutation is published (through the production mutation
 * rules) while an earlier asynchronous publication is in flight. A current session and account are
 * necessary but not sufficient: the earlier completion must not replace the newer presentation.
 */
class ProfileStartupStalePublicationTest {
    private val accountId = "account-a"
    private val scope = ProfileStartupScope(accountId, "provider-a")

    // ---------- startup publication freshness ----------

    @Test
    fun `a continuation cannot overwrite a newer active-profile snapshot published while it loads`() =
        runBlocking {
            var presentation = readyPresentation()
            val gate = CompletableDeferred<Unit>()
            val readiness = readiness(loadSnapshot = { gate.await(); snapshot("profile-a") })

            val attempt = async {
                readiness.run(true, accountId, owner("session-2"), { presentation }) { presentation = it }
            }
            yield()
            val newerSnapshot = snapshot("profile-b")
            presentation = presentation.withProfileSnapshot(newerSnapshot)
            gate.complete(Unit)
            attempt.await()

            assertSame("a stale completion must not replace the newer profile mutation", newerSnapshot, presentation.snapshot)
            assertEquals("profile-b", presentation.snapshot?.activeProfileId)
        }

    @Test
    fun `a continuation cannot overwrite a newer routing choice published while it loads`() = runBlocking {
        var presentation = readyPresentation()
        val gate = CompletableDeferred<Unit>()
        val readiness = readiness(
            loadSnapshot = { snapshot("profile-a") },
            loadRouting = { _, _ -> gate.await(); routing(defaultProfileId = "profile-a") },
        )
        val newerRouting = routing(defaultProfileId = null)

        val attempt = async {
            readiness.run(true, accountId, owner("session-2"), { presentation }) { presentation = it }
        }
        yield()
        presentation = presentation.withRoutingPreferences(newerRouting)
        gate.complete(Unit)
        attempt.await()

        assertSame(newerRouting, presentation.routing)
    }

    @Test
    fun `a continuation cannot restore a deleted profile published while it loads`() = runBlocking {
        var presentation = readyPresentation()
        val gate = CompletableDeferred<Unit>()
        val readiness = readiness(loadSnapshot = { gate.await(); snapshot("profile-a") })

        val attempt = async {
            readiness.run(true, accountId, owner("session-2"), { presentation }) { presentation = it }
        }
        yield()
        val afterDeletion = snapshot("profile-b", profileIds = listOf("profile-b"))
        presentation = presentation.withProfileSnapshot(afterDeletion)
        gate.complete(Unit)
        attempt.await()

        assertSame(afterDeletion, presentation.snapshot)
        assertEquals(listOf("profile-b"), presentation.snapshot?.profiles?.map(UserProfile::id))
    }

    @Test
    fun `a continuation cannot restore an updated profile list published while it loads`() = runBlocking {
        var presentation = readyPresentation()
        val gate = CompletableDeferred<Unit>()
        val readiness = readiness(loadSnapshot = { gate.await(); snapshot("profile-a") })

        val attempt = async {
            readiness.run(true, accountId, owner("session-2"), { presentation }) { presentation = it }
        }
        yield()
        val afterCreate = snapshot("profile-a", profileIds = listOf("profile-a", "profile-c"))
        presentation = presentation.withProfileSnapshot(afterCreate)
        gate.complete(Unit)
        attempt.await()

        assertSame(afterCreate, presentation.snapshot)
        assertEquals(listOf("profile-a", "profile-c"), presentation.snapshot?.profiles?.map(UserProfile::id))
    }

    @Test
    fun `an obsolete continuation publishes nothing over a genuine account transition`() = runBlocking {
        var presentation = readyPresentation()
        val gate = CompletableDeferred<Unit>()
        var currentAccount = accountId
        val readiness = readiness(
            loadSnapshot = { gate.await(); snapshot("profile-a") },
            activeAccountId = { currentAccount },
        )

        val attempt = async {
            readiness.run(true, accountId, owner("session-2"), { presentation }) { presentation = it }
        }
        yield()
        val otherScope = ProfileStartupScope("account-b", "provider-a")
        val otherPresentation = ProfileStartupPresentation(otherScope, snapshot("profile-b"), routing(), false)
        presentation = otherPresentation
        currentAccount = "account-b"
        gate.complete(Unit)
        attempt.await()

        assertSame(otherPresentation, presentation)
        assertEquals(otherScope, presentation.scope)
    }

    // ---------- routing repair freshness ----------

    @Test
    fun `an obsolete routing repair cannot overwrite a newer routing choice on the same snapshot`() =
        runBlocking {
            var presentation = readyPresentation()
            val gate = CompletableDeferred<Unit>()
            var published: ProfileRoutingPreferences? = null
            val repair = ProfileRoutingRepair(
                loadRouting = { _, _ -> gate.await(); routing(defaultProfileId = "profile-a") },
                isSessionOwnerCurrent = { true },
                activeAccountId = { accountId },
            )

            val attempt = async {
                repair.repair(accountId, owner("session-1"), { presentation }) { published = it }
            }
            yield()
            val newerRouting = routing(directEntry = true, defaultProfileId = null)
            presentation = presentation.withRoutingPreferences(newerRouting)
            gate.complete(Unit)
            attempt.await()

            assertNull("a stale repair must not publish over the newer routing choice", published)
            assertSame(newerRouting, presentation.routing)
        }

    @Test
    fun `a newer valid persisted default survives because the repair reads the current profile set`() =
        runBlocking {
            // The store's routing repair removes a persisted default that is absent from the
            // supplied profile ids, so the repair must derive those ids from the live presentation
            // at operation start rather than from a stale deletion-time snapshot.
            var persistedDefault: String? = "profile-b"
            var suppliedIds: Set<String>? = null
            val loadRouting: suspend (String, Set<String>) -> ProfileRoutingPreferences? = { _, ids ->
                suppliedIds = ids
                if (persistedDefault != null && persistedDefault !in ids) {
                    persistedDefault = null
                }
                ProfileRoutingPreferences(directEntryEnabled = true, defaultProfileId = persistedDefault)
            }
            val repair = ProfileRoutingRepair(
                loadRouting = loadRouting,
                isSessionOwnerCurrent = { true },
                activeAccountId = { accountId },
            )
            val currentSnapshot = snapshot("profile-b", profileIds = listOf("profile-a", "profile-b"))
            var presentation = ProfileStartupPresentation(
                scope = scope,
                snapshot = currentSnapshot,
                routing = routing(defaultProfileId = "profile-b"),
                failed = false,
            )
            var published: ProfileRoutingPreferences? = null

            repair.repair(accountId, owner("session-1"), { presentation }) { published = it }

            assertEquals("profile-b", persistedDefault)
            assertEquals(setOf("profile-a", "profile-b"), suppliedIds)
            assertEquals("profile-b", published?.defaultProfileId)
        }

    // ---------- helpers ----------

    private fun readiness(
        loadSnapshot: suspend (String) -> ProfileStoreSnapshot? = { snapshot("profile-a") },
        loadRouting: suspend (String, Set<String>) -> ProfileRoutingPreferences? = { _, _ -> routing() },
        isSessionOwnerCurrent: (AuthenticatedSessionOwner) -> Boolean = { true },
        activeAccountId: () -> String? = { accountId },
    ): ProfileStartupReadiness = ProfileStartupReadiness(
        loadSnapshot = loadSnapshot,
        loadRouting = loadRouting,
        isSessionOwnerCurrent = isSessionOwnerCurrent,
        activeAccountId = activeAccountId,
    )

    private fun readyPresentation(): ProfileStartupPresentation = ProfileStartupPresentation(
        scope = scope,
        snapshot = snapshot("profile-a"),
        routing = routing(),
        failed = false,
    )

    private fun snapshot(
        activeProfileId: String,
        profileIds: List<String> = listOf("profile-a"),
    ): ProfileStoreSnapshot = ProfileStoreSnapshot(
        profiles = profileIds.map { id ->
            UserProfile(
                id = id,
                displayName = id,
                kind = ProfileKind.STANDARD,
                createdAtEpochMs = 1L,
                isPrimary = id == activeProfileId,
            )
        },
        activeProfileId = activeProfileId,
    )

    private fun routing(
        directEntry: Boolean = true,
        defaultProfileId: String? = "profile-a",
    ): ProfileRoutingPreferences = ProfileRoutingPreferences(
        directEntryEnabled = directEntry,
        defaultProfileId = defaultProfileId,
    )

    private fun owner(
        sessionId: String,
        providerId: String = "provider-a",
    ): AuthenticatedSessionOwner = AuthenticatedSessionOwner(
        accountId = accountId,
        providerId = providerId,
        sessionId = sessionId,
        session = AuthenticatedSession(
            portal = PortalConfig("http://provider.test", PortalConfig.Source.ACCESS_CODE),
            credentials = Credentials("HULK-ABCD-EFGH-JKMN-PQRS", "viewer", "secret"),
            account = AccountInfo("viewer", "Active", null, 0, 1, false),
        ),
    )
}
