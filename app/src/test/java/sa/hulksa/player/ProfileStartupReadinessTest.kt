package sa.hulksa.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
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

class ProfileStartupReadinessTest {
    private val accountId = "account-a"

    @Test
    fun `baseline two-effect coordination loses routing readiness when the equal-content reload coalesces the null`() =
        runBlocking {
            // Faithful transcription of the recorded baseline coordination in
            // ProfileAwareHulkApp.kt at fcad7c4c (effects A lines 251-283 and B lines 285-319).
            // Effect A clears the published snapshot, loads off-main and republishes a
            // structurally equal snapshot. Effect B is restarted by a composition key change,
            // clears routing, re-reads the mutable snapshot at coroutine run time and exits when
            // it observes null; Compose only restarts B again when a key is not structurally
            // equal. This deterministic schedule proves the reported permanent preparation state.
            val oldSnapshot = snapshot()
            val equalReload = snapshot()
            assertNotSame(oldSnapshot, equalReload)
            assertEquals(oldSnapshot, equalReload)

            var publishedSnapshot: ProfileStoreSnapshot? = oldSnapshot
            var publishedRouting: ProfileRoutingPreferences? = routing()
            var routingLoads = 0
            val snapshotLoadGate = CompletableDeferred<Unit>()

            // Keys remembered by effect B after the owner change: (session-2, snapshot S).
            val rememberedOwnerKey = "session-2"
            val rememberedSnapshotKey = oldSnapshot

            val effectA = launch {
                publishedSnapshot = null // A: clear before loading
                snapshotLoadGate.await() // A: off-main profileStore.load(accountId)
                // A: owner/account still current -> publish the structurally equal snapshot.
                publishedSnapshot = equalReload
            }
            val effectB = launch {
                publishedRouting = null // B: clear routing on restart
                val snapshot = publishedSnapshot // B: reads the live mutable state
                if (snapshot == null) return@launch // B: early exit on the coalesced null
                routingLoads += 1
                publishedRouting = routing()
            }

            yield() // A runs first and suspends on the load gate.
            yield() // B runs, observes the cleared snapshot and exits.
            snapshotLoadGate.complete(Unit)
            effectA.join()
            effectB.join()

            // The applied keys still equal the remembered keys, so Compose never restarts B.
            assertEquals(rememberedOwnerKey, "session-2")
            assertEquals(rememberedSnapshotKey, publishedSnapshot)
            assertEquals(0, routingLoads)
            assertNull(publishedRouting)
        }

    @Test
    fun `ordered startup readiness publishes routing after an equal-content reload with a coalesced null`() =
        runBlocking {
            val equalReload = snapshot()
            val owner = owner("session-2")
            val routing = routing()
            var routingLoads = 0
            val snapshotLoadGate = CompletableDeferred<Unit>()
            val readiness = readiness(
                loadSnapshot = {
                    snapshotLoadGate.await()
                    equalReload
                },
                loadRouting = { expectedAccountId, profileIds ->
                    routingLoads += 1
                    assertEquals(accountId, expectedAccountId)
                    assertEquals(setOf("profile-a"), profileIds)
                    routing
                },
                isSessionOwnerCurrent = { it == owner },
            )

            val attempt = async { readiness.load(accountId, owner) }
            yield()
            snapshotLoadGate.complete(Unit)
            val result = attempt.await()

            assertTrue(result is ProfileStartupReadiness.Result.Ready)
            result as ProfileStartupReadiness.Result.Ready
            assertEquals(equalReload, result.snapshot)
            assertEquals(routing, result.routing)
            assertEquals(1, routingLoads)
        }

    @Test
    fun `the authoritative snapshot is always loaded before routing for that snapshot`() = runBlocking {
        val events = mutableListOf<String>()
        val readiness = readiness(
            loadSnapshot = { events += "snapshot"; snapshot() },
            loadRouting = { _, _ -> events += "routing"; routing() },
        )

        val result = readiness.load(accountId, owner("session-1"))

        assertTrue(result is ProfileStartupReadiness.Result.Ready)
        assertEquals(listOf("snapshot", "routing"), events)
    }

    @Test
    fun `a slow snapshot load cannot publish after the session owner changed`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var currentOwner = owner("session-1")
        val readiness = readiness(
            loadSnapshot = { gate.await(); snapshot() },
            isSessionOwnerCurrent = { it == currentOwner },
        )

        val attempt = async { readiness.load(accountId, owner("session-1")) }
        yield()
        currentOwner = owner("session-2")
        gate.complete(Unit)

        assertEquals(ProfileStartupReadiness.Result.Abandoned, attempt.await())
    }

    @Test
    fun `repeated same-account session replacement publishes only the current owner readiness`() = runBlocking {
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()
        val gates = ArrayDeque(listOf(firstGate, secondGate))
        var currentOwner = owner("session-1")
        val readiness = readiness(
            loadSnapshot = { gates.removeFirst().await(); snapshot() },
            isSessionOwnerCurrent = { it == currentOwner },
        )

        val first = async { readiness.load(accountId, owner("session-1")) }
        yield()
        currentOwner = owner("session-2")
        val second = async { readiness.load(accountId, owner("session-2")) }
        yield()

        firstGate.complete(Unit)
        assertEquals(ProfileStartupReadiness.Result.Abandoned, first.await())

        secondGate.complete(Unit)
        val secondResult = second.await()
        assertTrue(secondResult is ProfileStartupReadiness.Result.Ready)
        assertEquals(snapshot(), (secondResult as ProfileStartupReadiness.Result.Ready).snapshot)
    }

    @Test
    fun `an account scope change during routing load abandons the obsolete attempt`() = runBlocking {
        var currentAccountId = accountId
        val readiness = readiness(
            loadSnapshot = { snapshot() },
            loadRouting = { _, _ ->
                currentAccountId = "account-b"
                routing()
            },
            activeAccountId = { currentAccountId },
        )

        assertEquals(
            ProfileStartupReadiness.Result.Abandoned,
            readiness.load(accountId, owner("session-1")),
        )
    }

    @Test
    fun `cancellation is not converted into a ready or failed result`() = runBlocking {
        val readiness = readiness(loadSnapshot = { throw CancellationException("stale startup") })

        try {
            readiness.load(accountId, owner("session-1"))
            fail("CancellationException must be rethrown")
        } catch (_: CancellationException) {
            // Expected control flow: the caller coroutine keeps cancellation semantics.
        }
    }

    @Test
    fun `a failed snapshot load keeps the recoverable failure path and a retry loads normally`() = runBlocking {
        var attempts = 0
        var routingLoads = 0
        val readiness = readiness(
            loadSnapshot = {
                attempts += 1
                if (attempts == 1) null else snapshot()
            },
            loadRouting = { _, _ -> routingLoads += 1; routing() },
        )

        assertEquals(ProfileStartupReadiness.Result.Failed, readiness.load(accountId, owner("session-1")))
        assertEquals(0, routingLoads)

        val retry = readiness.load(accountId, owner("session-1"))
        assertTrue(retry is ProfileStartupReadiness.Result.Ready)
        assertEquals(1, routingLoads)
    }

    @Test
    fun `a failed routing load falls back to the safe picker defaults`() = runBlocking {
        val readiness = readiness(
            loadSnapshot = { snapshot() },
            loadRouting = { _, _ -> throw IllegalStateException("routing unavailable") },
        )

        val result = readiness.load(accountId, owner("session-1"))

        assertTrue(result is ProfileStartupReadiness.Result.Ready)
        assertEquals(
            ProfileRoutingPreferences(),
            (result as ProfileStartupReadiness.Result.Ready).routing,
        )
    }

    @Test
    fun `routing is never loaded when the snapshot cannot be loaded`() = runBlocking {
        var routingLoads = 0
        val readiness = readiness(
            loadSnapshot = { null },
            loadRouting = { _, _ -> routingLoads += 1; routing() },
        )

        assertEquals(ProfileStartupReadiness.Result.Failed, readiness.load(accountId, owner("session-1")))
        assertEquals(0, routingLoads)
    }

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

    private fun routing(): ProfileRoutingPreferences = ProfileRoutingPreferences(
        directEntryEnabled = true,
        defaultProfileId = "profile-a",
    )

    private fun owner(sessionId: String): AuthenticatedSessionOwner = AuthenticatedSessionOwner(
        accountId = accountId,
        providerId = "provider-a",
        sessionId = sessionId,
        session = AuthenticatedSession(
            portal = PortalConfig("http://provider.test", PortalConfig.Source.ACCESS_CODE),
            credentials = Credentials("HULK-ABCD-EFGH-JKMN-PQRS", "viewer", "secret"),
            account = AccountInfo("viewer", "Active", null, 0, 1, false),
        ),
    )
}
