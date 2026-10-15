package sa.hulksa.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
 * R02 refresh-continuity contracts.
 *
 * These tests drive the production startup owner and routing-repair owner with controlled
 * coroutine scheduling. Each `run` call mirrors one composition effect start/restart: the
 * presentation published by the previous call is the `current` value of the next one. The
 * critical regression is that a session-owner rotation inside an already-ready account/provider
 * scope never emits a cleared presentation, so the caller cannot dispose the mounted
 * Settings/HulkApp subtree or lose its scoped notice.
 */
class ProfileStartupContinuityTest {
    private val accountId = "account-a"
    private val scope = ProfileStartupScope(accountId, "provider-a")

    // ---------- startup presentation continuity ----------

    @Test
    fun `same-scope session rotation keeps the ready presentation mounted and revalidates in place`() =
        runBlocking {
            val ready = readyPresentation()
            val emissions = mutableListOf<ProfileStartupPresentation>()
            var published = ready
            val readiness = readiness()

            // Composition restart caused by the rotated session id (same account + provider).
            readiness.run(
                authenticated = true,
                accountId = accountId,
                sessionOwner = owner("session-2"),
                current = published,
            ) { presentation ->
                emissions += presentation
                published = presentation
            }

            assertEquals(1, emissions.size)
            assertTrue("continuation must never clear the mounted presentation", emissions.none { it.snapshot == null })
            assertEquals(scope, published.scope)
            assertFalse(published.failed)
            assertTrue(published.snapshot != null && published.routing != null)
        }

    @Test
    fun `repeated same-scope rotations never emit a cleared presentation`() = runBlocking {
        var published = readyPresentation()
        val emissions = mutableListOf<ProfileStartupPresentation>()
        val readiness = readiness()

        for (session in 2..6) {
            readiness.run(
                authenticated = true,
                accountId = accountId,
                sessionOwner = owner("session-$session"),
                current = published,
            ) { presentation ->
                emissions += presentation
                published = presentation
            }
        }

        assertEquals(5, emissions.size)
        assertTrue(emissions.none { it.snapshot == null || it.routing == null })
    }

    @Test
    fun `equal-content reload publishes the reloaded values without clearing`() = runBlocking {
        val ready = readyPresentation()
        val equalReloadSnapshot = snapshot()
        val equalReloadRouting = routing()
        assertEquals(ready.snapshot, equalReloadSnapshot)
        assertEquals(ready.routing, equalReloadRouting)
        val emissions = mutableListOf<ProfileStartupPresentation>()
        var published = ready
        val readiness = readiness(
            loadSnapshot = { equalReloadSnapshot },
            loadRouting = { _, _ -> equalReloadRouting },
        )

        readiness.run(true, accountId, owner("session-2"), published) { presentation ->
            emissions += presentation
            published = presentation
        }

        assertEquals(1, emissions.size)
        assertSame(equalReloadSnapshot, published.snapshot)
        assertSame(equalReloadRouting, published.routing)
        assertEquals(scope, published.scope)
    }

    @Test
    fun `a slow same-scope load keeps the mounted presentation until it completes`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var published = readyPresentation()
        val emissions = mutableListOf<ProfileStartupPresentation>()
        val readiness = readiness(
            loadSnapshot = { gate.await(); snapshot() },
        )

        val attempt = async {
            readiness.run(true, accountId, owner("session-2"), published) { presentation ->
                emissions += presentation
                published = presentation
            }
        }
        yield()
        assertTrue("no publication may happen while a continuation load is in flight", emissions.isEmpty())
        assertEquals(readyPresentation().snapshot, published.snapshot)

        gate.complete(Unit)
        attempt.await()
        assertEquals(1, emissions.size)
        assertFalse(published.failed)
    }

    @Test
    fun `cold startup clears before loading and then publishes readiness`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val emissions = mutableListOf<ProfileStartupPresentation>()
        var published = ProfileStartupPresentation.Cleared
        val readiness = readiness(
            loadSnapshot = { gate.await(); snapshot() },
        )

        val attempt = async {
            readiness.run(true, accountId, owner("session-1"), published) { presentation ->
                emissions += presentation
                published = presentation
            }
        }
        yield()
        assertEquals(listOf(ProfileStartupPresentation.Cleared), emissions)

        gate.complete(Unit)
        attempt.await()
        assertEquals(2, emissions.size)
        assertEquals(scope, emissions[1].scope)
        assertTrue(emissions[1].snapshot != null && emissions[1].routing != null)
    }

    @Test
    fun `cold startup failure stays a recoverable failure`() = runBlocking {
        val emissions = mutableListOf<ProfileStartupPresentation>()
        val readiness = readiness(loadSnapshot = { null })

        readiness.run(true, accountId, owner("session-1"), ProfileStartupPresentation.Cleared) {
            emissions += it
        }

        assertEquals(
            listOf(ProfileStartupPresentation.Cleared, ProfileStartupPresentation.Failed),
            emissions,
        )
    }

    @Test
    fun `a failed same-scope revalidation keeps the mounted presentation`() = runBlocking {
        var published = readyPresentation()
        val emissions = mutableListOf<ProfileStartupPresentation>()
        val readiness = readiness(loadSnapshot = { null })

        readiness.run(true, accountId, owner("session-2"), published) { presentation ->
            emissions += presentation
            published = presentation
        }

        assertTrue("a failed continuation must not surface the startup failure surface", emissions.isEmpty())
        assertFalse(published.failed)
        assertTrue(published.snapshot != null && published.routing != null)
    }

    @Test
    fun `a true account transition clears and never reuses the previous ready content`() = runBlocking {
        val emissions = mutableListOf<ProfileStartupPresentation>()
        var published = readyPresentation()
        val otherAccountId = "account-b"
        val readiness = readiness(activeAccountId = { otherAccountId })

        readiness.run(true, otherAccountId, owner("session-2"), published) { presentation ->
            emissions += presentation
            published = presentation
        }

        assertEquals(2, emissions.size)
        assertEquals(ProfileStartupPresentation.Cleared, emissions[0])
        assertEquals(ProfileStartupScope(otherAccountId, "provider-a"), published.scope)
    }

    @Test
    fun `a provider change within the same account clears and never reuses the ready content`() = runBlocking {
        val emissions = mutableListOf<ProfileStartupPresentation>()
        var published = readyPresentation()
        val readiness = readiness()

        readiness.run(true, accountId, owner("session-2", providerId = "provider-b"), published) { presentation ->
            emissions += presentation
            published = presentation
        }

        assertEquals(ProfileStartupPresentation.Cleared, emissions.first())
        assertEquals(ProfileStartupScope(accountId, "provider-b"), published.scope)
    }

    @Test
    fun `an obsolete owner completion publishes nothing over the newer owner`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var currentOwner = owner("session-1")
        var published = readyPresentation()
        val emissions = mutableListOf<ProfileStartupPresentation>()
        val readiness = readiness(
            loadSnapshot = { gate.await(); snapshot() },
            isSessionOwnerCurrent = { it == currentOwner },
        )

        val obsolete = async {
            readiness.run(true, accountId, owner("session-1"), published) { presentation ->
                emissions += presentation
                published = presentation
            }
        }
        yield()
        currentOwner = owner("session-2")
        gate.complete(Unit)
        obsolete.await()

        assertTrue("an abandoned attempt must publish nothing", emissions.isEmpty())
        assertFalse(published.failed)
    }

    @Test
    fun `logout clears the mounted presentation`() = runBlocking {
        val emissions = mutableListOf<ProfileStartupPresentation>()
        val readiness = readiness()

        readiness.run(false, accountId, null, readyPresentation()) { emissions += it }

        assertEquals(listOf(ProfileStartupPresentation.Cleared), emissions)
    }

    @Test
    fun `cancellation thrown by the loader is rethrown without a publication`() = runBlocking {
        val emissions = mutableListOf<ProfileStartupPresentation>()
        val readiness = readiness(
            loadSnapshot = { throw CancellationException("stale startup") },
        )

        try {
            readiness.run(true, accountId, owner("session-1"), ProfileStartupPresentation.Cleared) {
                emissions += it
            }
            fail("CancellationException must be rethrown")
        } catch (_: CancellationException) {
            // Expected control flow.
        }

        assertEquals(listOf(ProfileStartupPresentation.Cleared), emissions)
    }

    @Test
    fun `cancelling an in-flight same-scope load emits nothing`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val emissions = mutableListOf<ProfileStartupPresentation>()
        val readiness = readiness(
            loadSnapshot = { gate.await(); snapshot() },
        )

        val attempt = async {
            readiness.run(true, accountId, owner("session-2"), readyPresentation()) { emissions += it }
        }
        yield()
        attempt.cancel()
        val result = runCatching { attempt.await() }

        assertTrue(result.exceptionOrNull() is CancellationException)
        assertTrue(emissions.isEmpty())
    }

    // ---------- post-deletion routing repair ----------

    @Test
    fun `routing repair publishes while the owner account and snapshot remain current`() = runBlocking {
        val deleted = snapshot()
        val repaired = routing(directEntry = false, defaultProfileId = null)
        var published: ProfileRoutingPreferences? = null
        val repair = ProfileRoutingRepair(
            loadRouting = { _, _ -> repaired },
            isSessionOwnerCurrent = { true },
            activeAccountId = { accountId },
        )

        repair.repair(
            expectedAccountId = accountId,
            sessionOwner = owner("session-1"),
            snapshot = deleted,
            isStillCurrent = { true },
        ) { published = it }

        assertEquals(repaired, published)
    }

    @Test
    fun `routing repair cannot overwrite newer routing after a same-account relogin`() = runBlocking {
        val deleted = snapshot()
        val capturedOwner = owner("session-1")
        var currentOwner = capturedOwner
        var published: ProfileRoutingPreferences? = null
        val repair = ProfileRoutingRepair(
            loadRouting = { _, _ -> routing() },
            isSessionOwnerCurrent = { it == currentOwner },
            activeAccountId = { accountId },
        )
        currentOwner = owner("session-2") // logout/login to the same account

        repair.repair(
            expectedAccountId = accountId,
            sessionOwner = capturedOwner,
            snapshot = deleted,
            isStillCurrent = { true },
        ) { published = it }

        assertNull(published)
    }

    @Test
    fun `routing repair is suppressed when a newer snapshot is published while loading`() = runBlocking {
        val deleted = snapshot()
        var currentSnapshot: ProfileStoreSnapshot? = deleted
        val gate = CompletableDeferred<Unit>()
        var published: ProfileRoutingPreferences? = null
        val repair = ProfileRoutingRepair(
            loadRouting = { _, _ -> gate.await(); routing() },
            isSessionOwnerCurrent = { true },
            activeAccountId = { accountId },
        )

        val attempt = async {
            repair.repair(
                expectedAccountId = accountId,
                sessionOwner = owner("session-1"),
                snapshot = deleted,
                isStillCurrent = { currentSnapshot === deleted },
            ) { published = it }
        }
        yield()
        currentSnapshot = snapshot()
        gate.complete(Unit)
        attempt.await()

        assertNull(published)
    }

    @Test
    fun `routing repair is suppressed after the account scope changed`() = runBlocking {
        var scopeAccount: String? = accountId
        var published: ProfileRoutingPreferences? = null
        val repair = ProfileRoutingRepair(
            loadRouting = { _, _ -> routing() },
            isSessionOwnerCurrent = { true },
            activeAccountId = { scopeAccount },
        )

        scopeAccount = "account-b"

        repair.repair(
            expectedAccountId = accountId,
            sessionOwner = owner("session-1"),
            snapshot = snapshot(),
            isStillCurrent = { true },
        ) { published = it }

        assertNull(published)
    }

    @Test
    fun `routing repair failure falls back to safe defaults only while still current`() = runBlocking {
        var published: ProfileRoutingPreferences? = null
        val repair = ProfileRoutingRepair(
            loadRouting = { _, _ -> throw IllegalStateException("routing unavailable") },
            isSessionOwnerCurrent = { true },
            activeAccountId = { accountId },
        )

        repair.repair(
            expectedAccountId = accountId,
            sessionOwner = owner("session-1"),
            snapshot = snapshot(),
            isStillCurrent = { true },
        ) { published = it }

        assertEquals(ProfileRoutingPreferences(), published)
    }

    @Test
    fun `routing repair cancellation is never converted into a publication`() = runBlocking {
        var published: ProfileRoutingPreferences? = null
        val repair = ProfileRoutingRepair(
            loadRouting = { _, _ -> throw CancellationException("stale repair") },
            isSessionOwnerCurrent = { true },
            activeAccountId = { accountId },
        )

        try {
            repair.repair(
                expectedAccountId = accountId,
                sessionOwner = owner("session-1"),
                snapshot = snapshot(),
                isStillCurrent = { true },
            ) { published = it }
            fail("CancellationException must be rethrown")
        } catch (_: CancellationException) {
            // Expected control flow.
        }

        assertNull(published)
    }

    // ---------- helpers ----------

    private fun readiness(
        loadSnapshot: suspend (String) -> ProfileStoreSnapshot? = { snapshot() },
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
        snapshot = snapshot(),
        routing = routing(),
        failed = false,
    )

    private fun snapshot(): ProfileStoreSnapshot = ProfileStoreSnapshot(
        profiles = listOf(
            UserProfile(
                id = "profile-a",
                displayName = "Adult",
                kind = ProfileKind.STANDARD,
                createdAtEpochMs = 1L,
                isPrimary = true,
            ),
        ),
        activeProfileId = "profile-a",
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
