package sa.hulksa.player.data

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

class PairingInstallationIdTest {
    @Test
    fun `read-or-create returns one stable uuid v4 without creating a session`() {
        val environment = environment()

        val first = environment.store.installationIdForPairing()

        assertNotNull(first)
        assertTrue(isPairingUuidV4(checkNotNull(first)))
        assertEquals(first, environment.store.installationIdForPairing())
        assertEquals(first, environment.restart().store.installationIdForPairing())
        assertNull(environment.store.metadata())
        assertNull(environment.store.activeAccountId())
    }

    @Test
    fun `checked commit failure fails closed and persists no identifier`() {
        val environment = environment()
        environment.sessionPreferences.commitResult = false

        assertNull(environment.store.installationIdForPairing())
        assertNull(environment.sessionPreferences.durableValue("installation_id"))
    }

    @Test
    fun `undurable identifier is never returned twice and is repaired after recovery`() {
        val environment = environment()
        environment.sessionPreferences.commitResult = false

        assertNull(environment.store.installationIdForPairing())
        assertNull(environment.store.installationIdForPairing())
        assertNull(environment.sessionPreferences.durableValue("installation_id"))

        environment.sessionPreferences.commitResult = true
        val recovered = environment.store.installationIdForPairing()

        assertNotNull(recovered)
        assertTrue(isPairingUuidV4(checkNotNull(recovered)))
        assertEquals(recovered, environment.store.installationIdForPairing())
        assertEquals(recovered, environment.restart().store.installationIdForPairing())
        assertEquals(recovered, environment.sessionPreferences.durableValue("installation_id"))
    }

    @Test
    fun `a new store owner cannot trust an undurable shared process cache`() {
        val environment = environment()
        environment.sessionPreferences.commitResult = false
        assertNull(environment.store.installationIdForPairing())
        val secondOwner = AccountSessionStore(
            environment.sessionPreferences,
            environment.accountScope,
        )
        assertNull(secondOwner.installationIdForPairing())
        assertNull(environment.sessionPreferences.durableValue("installation_id"))
        environment.sessionPreferences.commitResult = true
        val recovered = secondOwner.installationIdForPairing()
        assertNotNull(recovered)
        assertEquals(recovered, environment.sessionPreferences.durableValue("installation_id"))
        assertEquals(recovered, environment.restart().store.installationIdForPairing())
        assertNull(secondOwner.metadata())
        assertNull(secondOwner.activeAccountId())
    }

    @Test
    fun `successful authentication reuses the pre-login installation identifier`() {
        val environment = environment()
        val identifier = environment.store.installationIdForPairing()

        val recorded = environment.store.recordAuthenticated(session())

        assertEquals(identifier, recorded.installationId)
        assertEquals(identifier, environment.store.metadata()?.installationId)
        assertEquals(identifier, environment.restart().store.metadata()?.installationId)
    }

    @Test
    fun `concurrent startup observes a single identifier`() {
        val environment = environment()
        val threads = 8
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val results = ConcurrentLinkedQueue<String>()
        val executor = Executors.newFixedThreadPool(threads)
        repeat(threads) {
            executor.execute {
                start.await()
                environment.store.installationIdForPairing()?.let(results::add)
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        executor.shutdown()

        assertEquals(threads, results.size)
        assertEquals(1, results.toSet().size)
        assertTrue(isPairingUuidV4(checkNotNull(results.first())))
    }

    private fun environment(): TestEnvironment =
        TestEnvironment(FakeSharedPreferences(), FakeSharedPreferences())

    private fun session(): AuthenticatedSession = AuthenticatedSession(
        portal = PortalConfig(baseUrl = PORTAL, source = PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials(
            accessCode = "CODE",
            username = "subscriber",
            password = "password",
        ),
        account = AccountInfo(
            username = "subscriber",
            status = "Active",
            expiresAtEpochSeconds = 2_000_000_000L,
            activeConnections = 1,
            maxConnections = 1,
            isTrial = false,
        ),
    )

    private class TestEnvironment(
        val scopePreferences: FakeSharedPreferences,
        val sessionPreferences: FakeSharedPreferences,
    ) {
        val accountScope: AccountScopeStore = AccountScopeStore(scopePreferences) { name ->
            throw AssertionError("Unexpected scoped preferences lookup: $name")
        }
        val store: AccountSessionStore = AccountSessionStore(sessionPreferences, accountScope)

        fun restart(): TestEnvironment = TestEnvironment(
            scopePreferences.restart(),
            sessionPreferences.restart(),
        )
    }

    private companion object {
        const val PORTAL = "https://alpha.example.test:8080"
    }
}
