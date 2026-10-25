package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

private const val PORTAL_ALPHA = "https://alpha.example.test:8080"
private const val PORTAL_BETA = "https://beta.example.test:8080"
private const val USERNAME_ALPHA = "alpha"
private const val USERNAME_BETA = "beta"

class AccountSessionMetadataCommitAtomicityTest {
    @Test
    fun `successful metadata commit records the authenticated account`() {
        val environment = environment()
        val expectedAccountId = stableAccountId(PORTAL_ALPHA, USERNAME_ALPHA)

        val recorded = environment.store.recordAuthenticated(alphaSession())

        assertEquals(expectedAccountId, recorded.accountId)
        assertEquals(expectedAccountId, environment.store.activeAccountId())
        assertEquals(expectedAccountId, environment.store.metadata()?.accountId)
        assertEquals(USERNAME_ALPHA, environment.store.metadata()?.username)
        assertEquals("CODE-ALPHA", environment.store.lastAccessCode())

        val restarted = environment.restart()
        assertEquals(expectedAccountId, restarted.store.activeAccountId())
        assertEquals(expectedAccountId, restarted.store.metadata()?.accountId)
    }

    @Test
    fun `metadata commit failure fails the authentication persistence operation`() {
        val environment = environment()
        environment.sessionPreferences.commitResult = false

        assertThrows(IllegalStateException::class.java) {
            environment.store.recordAuthenticated(alphaSession())
        }

        assertNull(environment.sessionPreferences.durableValue("account_id"))
        assertNull(environment.store.activeAccountId())
        assertNull(environment.store.metadata())

        val restarted = environment.restart()
        assertNull(restarted.store.activeAccountId())
        assertNull(restarted.store.metadata())
    }

    @Test
    fun `metadata commit failure rolls back to the previous coherent owner`() {
        val environment = environment()
        val accountA = stableAccountId(PORTAL_ALPHA, USERNAME_ALPHA)
        environment.store.recordAuthenticated(alphaSession())
        environment.sessionPreferences.commitResult = false

        assertThrows(IllegalStateException::class.java) {
            environment.store.recordAuthenticated(betaSession())
        }

        // The failed B transaction restores both process and durable A ownership.
        assertEquals(accountA, environment.store.activeAccountId())
        assertEquals(accountA, environment.store.metadata()?.accountId)
        assertEquals(USERNAME_ALPHA, environment.store.metadata()?.username)
        assertEquals("CODE-ALPHA", environment.store.lastAccessCode())

        val restarted = environment.restart()
        assertEquals(accountA, restarted.store.activeAccountId())
        assertEquals(accountA, restarted.store.metadata()?.accountId)
        assertEquals(USERNAME_ALPHA, restarted.store.metadata()?.username)
        assertEquals("CODE-ALPHA", restarted.store.lastAccessCode())

        environment.sessionPreferences.commitResult = true
        assertEquals(
            stableAccountId(PORTAL_BETA, USERNAME_BETA),
            environment.store.recordAuthenticated(betaSession()).accountId,
        )
    }

    @Test
    fun `retained access code survives logout after a successful authentication`() {
        val environment = environment()
        environment.store.recordAuthenticated(alphaSession())

        environment.store.clearActiveSession()

        assertNull(environment.store.metadata())
        assertEquals("CODE-ALPHA", environment.store.lastAccessCode())
        assertEquals("CODE-ALPHA", environment.restart().store.lastAccessCode())
    }

    private fun environment(): TestEnvironment =
        TestEnvironment(FakeSharedPreferences(), FakeSharedPreferences())

    private fun alphaSession(): AuthenticatedSession = session(
        portal = PORTAL_ALPHA,
        username = USERNAME_ALPHA,
        accessCode = "CODE-ALPHA",
    )

    private fun betaSession(): AuthenticatedSession = session(
        portal = PORTAL_BETA,
        username = USERNAME_BETA,
        accessCode = "CODE-BETA",
    )

    private fun session(
        portal: String,
        username: String,
        accessCode: String,
    ): AuthenticatedSession = AuthenticatedSession(
        portal = PortalConfig(baseUrl = portal, source = PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials(
            accessCode = accessCode,
            username = username,
            password = "password-$username",
        ),
        account = AccountInfo(
            username = username,
            status = "Active",
            expiresAtEpochSeconds = 2_000_000_000L,
            activeConnections = 1,
            maxConnections = 1,
            isTrial = false,
        ),
    )
}

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
