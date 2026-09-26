package sa.hulksa.player.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig
import sa.hulksa.player.security.CredentialEnvelopeRemovalException
import sa.hulksa.player.security.CredentialVault

private const val OWNER_PORTAL = "https://owner.example.test:8080"
private const val OWNER_USERNAME = "owner"
private const val OWNER_ACCESS_CODE = "CODE-OWNER"
private const val OWNER_ENVELOPE_IV = "synthetic-owner-iv"
private const val OWNER_ENVELOPE_PAYLOAD = "synthetic-owner-envelope"

/**
 * Proves the fail-closed ordering required of credential-removal callers: durable Vault envelope
 * removal must complete before any session-ownership state is cleared, and a failed removal must
 * leave the transition unadvanced with restart-restorable state intact.
 */
class CredentialRemovalTransitionTest {
    @Before
    fun clearProcessOwnership() {
        AuthenticatedSessionRegistry.clear()
    }

    @After
    fun clearOwnershipAfterTest() {
        AuthenticatedSessionRegistry.clear()
    }

    @Test
    fun `failed durable removal keeps logout ownership and envelope across restart`() {
        val fixture = Fixture().also { it.recordRememberedOwner() }
        fixture.envelopePreferences.commitResult = false

        assertThrows(CredentialEnvelopeRemovalException::class.java) {
            fixture.clearOwnershipAfterDurableCredentialRemoval()
        }

        assertNotNull(AuthenticatedSessionRegistry.currentOwner())
        val restarted = fixture.restart()
        assertNotNull(restarted.sessionPreferences.durableValue("account_id"))
        assertNotNull(restarted.scopePreferences.durableValue("active_account_id"))
        assertEquals(
            OWNER_ENVELOPE_IV,
            restarted.envelopePreferences.durableValue(CredentialVault.KEY_IV),
        )
    }

    @Test
    fun `successful durable removal completes logout across restart and keeps the retained access code`() {
        val fixture = Fixture().also { it.recordRememberedOwner() }

        fixture.clearOwnershipAfterDurableCredentialRemoval()

        assertNull(AuthenticatedSessionRegistry.currentOwner())
        val restarted = fixture.restart()
        assertNull(restarted.sessionPreferences.durableValue("account_id"))
        assertNull(restarted.scopePreferences.durableValue("active_account_id"))
        assertNull(restarted.envelopePreferences.durableValue(CredentialVault.KEY_IV))
        assertEquals(OWNER_ACCESS_CODE, restarted.store.lastAccessCode())
    }

    @Test
    fun `failed durable removal prevents a no-remember session from being recorded`() {
        val fixture = Fixture()
        fixture.envelopePreferences.commitResult = false

        assertThrows(CredentialEnvelopeRemovalException::class.java) {
            fixture.recordNoRememberOwner()
        }

        assertNull(AuthenticatedSessionRegistry.currentOwner())
        val restarted = fixture.restart()
        assertNull(restarted.sessionPreferences.durableValue("account_id"))
        assertNull(restarted.scopePreferences.durableValue("active_account_id"))
    }

    @Test
    fun `successful durable removal records the no-remember session without an envelope`() {
        val fixture = Fixture()

        fixture.recordNoRememberOwner()

        assertNotNull(AuthenticatedSessionRegistry.currentOwner())
        val restarted = fixture.restart()
        assertNotNull(restarted.sessionPreferences.durableValue("account_id"))
        assertNotNull(restarted.scopePreferences.durableValue("active_account_id"))
        assertNull(restarted.envelopePreferences.durableValue(CredentialVault.KEY_IV))
    }
}

private class Fixture(
    val envelopePreferences: FakeSharedPreferences = FakeSharedPreferences(),
    val sessionPreferences: FakeSharedPreferences = FakeSharedPreferences(),
    val scopePreferences: FakeSharedPreferences = FakeSharedPreferences(),
) {
    val vault = CredentialVault(envelopePreferences)
    val store = AccountSessionStore(
        sessionPreferences,
        AccountScopeStore(scopePreferences) { FakeSharedPreferences() },
    )

    /**
     * Mirrors the logout / abandoned-pending-authentication transaction: ownership cleanup only
     * runs after the durable credential removal reported success.
     */
    fun clearOwnershipAfterDurableCredentialRemoval() {
        withDurableCredentialsRemoved(vault::clear) {
            store.clearActiveSession()
            AuthenticatedSessionRegistry.clear()
        }
    }

    /** Mirrors the remember=false authentication commit: removal before the session is recorded. */
    fun recordNoRememberOwner() {
        withDurableCredentialsRemoved(vault::clear) {
            val session = ownerSession()
            val metadata = store.recordAuthenticated(session, stableAccountId(OWNER_PORTAL, OWNER_USERNAME))
            AuthenticatedSessionRegistry.update(session, metadata)
        }
    }

    fun recordRememberedOwner() {
        val session = ownerSession()
        val metadata = store.recordAuthenticated(session, stableAccountId(OWNER_PORTAL, OWNER_USERNAME))
        AuthenticatedSessionRegistry.update(session, metadata)
        envelopePreferences.edit()
            .putString(CredentialVault.KEY_IV, OWNER_ENVELOPE_IV)
            .putString(CredentialVault.KEY_PAYLOAD, OWNER_ENVELOPE_PAYLOAD)
            .commit()
    }

    fun restart(): Fixture = Fixture(
        envelopePreferences = envelopePreferences.restart(),
        sessionPreferences = sessionPreferences.restart(),
        scopePreferences = scopePreferences.restart(),
    )
}

private fun ownerSession(): AuthenticatedSession = AuthenticatedSession(
    portal = PortalConfig(baseUrl = OWNER_PORTAL, source = PortalConfig.Source.ACCESS_CODE),
    credentials = Credentials(
        accessCode = OWNER_ACCESS_CODE,
        username = OWNER_USERNAME,
        password = "owner-password",
    ),
    account = AccountInfo(
        username = OWNER_USERNAME,
        status = "Active",
        expiresAtEpochSeconds = 2_000_000_000L,
        activeConnections = 1,
        maxConnections = 1,
        isTrial = false,
    ),
)
