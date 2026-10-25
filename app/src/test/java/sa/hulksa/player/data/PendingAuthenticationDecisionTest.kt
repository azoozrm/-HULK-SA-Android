package sa.hulksa.player.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig
import sa.hulksa.player.security.CredentialVault

class PendingAuthenticationDecisionTest {
    private lateinit var fixture: Fixture

    @Before
    fun setUp() {
        PendingAuthenticationDecisionRegistry.invalidate()
        AuthenticatedSessionRegistry.clear()
        fixture = Fixture()
    }

    @After
    fun tearDown() {
        PendingAuthenticationDecisionRegistry.invalidate()
        AuthenticatedSessionRegistry.clear()
    }

    @Test
    fun `expired stale decision cannot erase a newer valid session`() {
        val pendingA = fixture.pendingA(expiry = System.currentTimeMillis() / 1_000L - 1L)
        val b = fixture.installCurrentB(accountId = "account-b")

        assertThrows(StaleAccountIdentityDecisionException::class.java) {
            runBlocking {
                fixture.repository.completeAuthenticationDecision(
                    pending = pendingA,
                    selectedAccountId = null,
                    remember = false,
                )
            }
        }

        fixture.assertCurrentB(b)
    }

    @Test
    fun `non-expired stale decision cannot overwrite a newer same-account session`() {
        val pendingA = fixture.pendingA(expiry = System.currentTimeMillis() / 1_000L + 3_600L)
        val b = fixture.installCurrentB(accountId = "account-a")

        assertThrows(StaleAccountIdentityDecisionException::class.java) {
            runBlocking {
                fixture.repository.completeAuthenticationDecision(
                    pending = pendingA,
                    selectedAccountId = null,
                    remember = false,
                )
            }
        }

        fixture.assertCurrentB(b)
    }

    @Test
    fun `logout then new login cannot be undone by late decision`() {
        val pendingA = fixture.pendingA(expiry = System.currentTimeMillis() / 1_000L + 3_600L)
        runBlocking { fixture.repository.logout() }
        val b = fixture.installCurrentB(accountId = "account-b")

        assertThrows(StaleAccountIdentityDecisionException::class.java) {
            runBlocking {
                fixture.repository.completeAuthenticationDecision(
                    pending = pendingA,
                    selectedAccountId = null,
                    remember = false,
                )
            }
        }

        fixture.assertCurrentB(b)
    }

    @Test
    fun `stale cancel cannot clear a newer valid session`() {
        val pendingA = fixture.pendingA(expiry = System.currentTimeMillis() / 1_000L + 3_600L)
        val b = fixture.installCurrentB(accountId = "account-b")

        assertFalse(runBlocking { fixture.repository.abandonPendingAuthentication(pendingA) })

        fixture.assertCurrentB(b)
    }

    @Test
    fun `failed deferred commit leaves no partial session for a new owner`() {
        val pendingA = fixture.pendingA(expiry = System.currentTimeMillis() / 1_000L + 3_600L)
        fixture.sessionPreferences.commitResult = false

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                fixture.repository.completeAuthenticationDecision(
                    pending = pendingA,
                    selectedAccountId = null,
                    remember = false,
                )
            }
        }

        assertNull(fixture.store.metadata())
        assertNull(fixture.store.activeAccountId())
        assertNull(AuthenticatedSessionRegistry.currentOwner())
        assertNull(fixture.sessionPreferences.durableValue("account_id"))
        assertNull(fixture.scopePreferences.durableValue("active_account_id"))
    }

    @Test
    fun `failed deferred commit preserves an existing prior owner`() {
        val b = fixture.installCurrentB(accountId = "account-b")
        val pendingA = fixture.pendingA(expiry = System.currentTimeMillis() / 1_000L + 3_600L)
        fixture.sessionPreferences.commitResult = false

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                fixture.repository.completeAuthenticationDecision(
                    pending = pendingA,
                    selectedAccountId = null,
                    remember = false,
                )
            }
        }

        fixture.assertCurrentB(b)
    }

    @Test
    fun `delayed expiry cleanup cannot clear a newer valid session`() = runBlocking {
        fixture.installCurrentA()
        val owner = checkNotNull(AuthenticatedSessionRegistry.currentOwner())
        val releaseCleanup = CompletableDeferred<Unit>()
        val cleanup = async(Dispatchers.Default) {
            releaseCleanup.await()
            fixture.repository.logoutIfOwnedBy(owner)
        }
        val b = fixture.installCurrentB(accountId = "account-b")
        releaseCleanup.complete(Unit)

        assertFalse(cleanup.await())
        fixture.assertCurrentB(b)
    }

    @Test
    fun `credential removal failure is surfaced by the real owner-bound cleanup`() = runBlocking {
        fixture.installCurrentA()
        val owner = checkNotNull(AuthenticatedSessionRegistry.currentOwner())
        fixture.envelopePreferences.commitResult = false
        var visibleStateReset = false
        val cleanupGate = SessionOwnershipCleanupGate(
            ownershipEpoch = { 0L },
            currentOwner = { AuthenticatedSessionRegistry.currentOwner() },
            logoutIfOwnedBy = fixture.repository::logoutIfOwnedBy,
        )

        val result = cleanupGate.clearIfOwnerStillCurrent(owner) {
            visibleStateReset = true
        }

        assertTrue(result is SessionOwnershipCleanupResult.CredentialRemovalFailed)
        assertFalse(visibleStateReset)
        assertEquals(owner.sessionId, AuthenticatedSessionRegistry.currentOwner()?.sessionId)
        assertEquals("account-a", fixture.store.metadata()?.accountId)
        assertEquals("account-a", fixture.store.activeAccountId())
        assertEquals(
            "synthetic-a-iv",
            fixture.envelopePreferences.durableValue(CredentialVault.KEY_IV),
        )
        assertEquals(
            "synthetic-a-payload",
            fixture.envelopePreferences.durableValue(CredentialVault.KEY_PAYLOAD),
        )
    }

    @Test
    fun `owner-scoped cleanup clears the current expired owner`() = runBlocking {
        fixture.installCurrentA()
        val owner = checkNotNull(AuthenticatedSessionRegistry.currentOwner())

        assertTrue(fixture.repository.logoutIfOwnedBy(owner))

        assertNull(fixture.store.metadata())
        assertNull(fixture.store.activeAccountId())
        assertNull(AuthenticatedSessionRegistry.currentOwner())
        assertNull(fixture.envelopePreferences.durableValue(CredentialVault.KEY_IV))
    }

    @Test
    fun `current pending decision that expires fails without publishing a session`() {
        val pendingA = fixture.pendingA(expiry = System.currentTimeMillis() / 1_000L - 1L)

        assertThrows(XtreamException.SubscriptionInactive::class.java) {
            runBlocking {
                fixture.repository.completeAuthenticationDecision(
                    pending = pendingA,
                    selectedAccountId = null,
                    remember = false,
                )
            }
        }

        assertNull(fixture.store.metadata())
        assertNull(fixture.store.activeAccountId())
        assertNull(AuthenticatedSessionRegistry.currentOwner())
        assertFalse(runBlocking { fixture.repository.abandonPendingAuthentication(pendingA) })
    }

    @Test
    fun `current valid decision still commits successfully`() {
        val pendingA = fixture.pendingA(expiry = System.currentTimeMillis() / 1_000L + 3_600L)

        val authenticated = runBlocking {
            fixture.repository.completeAuthenticationDecision(
                pending = pendingA,
                selectedAccountId = null,
                remember = false,
            )
        }

        assertEquals(pendingA.session, authenticated)
        val metadata = checkNotNull(fixture.store.metadata())
        assertTrue(metadata.accountId.isNotBlank())
        assertEquals(metadata.sessionId, AuthenticatedSessionRegistry.currentOwner()?.sessionId)
        assertEquals(metadata.accountId, fixture.store.activeAccountId())
        assertNull(fixture.vault.load())
    }

    private class Fixture {
        val envelopePreferences = FakeSharedPreferences()
        val sessionPreferences = FakeSharedPreferences()
        val scopePreferences = FakeSharedPreferences()
        val vault = CredentialVault(envelopePreferences)
        val store = AccountSessionStore(
            sessionPreferences,
            AccountScopeStore(scopePreferences) { FakeSharedPreferences() },
        )
        val repository = HulkRepository(
            context = TestAppContext(),
            vault = vault,
            accountSessionStore = store,
            suspendDownloadOwner = {},
        )

        fun pendingA(expiry: Long): PendingAccountIdentityDecision {
            val session = session(
                username = "alpha",
                accessCode = "CODE-ALPHA",
                expiry = expiry,
            )
            return PendingAccountIdentityDecision(
                session = session,
                username = session.credentials.username,
                portalBaseUrl = accountTrustedHostKey(session.portal.baseUrl),
                candidates = emptyList(),
                attempt = PendingAuthenticationDecisionRegistry.begin(),
                priorSessionId = store.metadata()?.sessionId,
            )
        }

        fun installCurrentA(): AccountSessionMetadata {
            val session = session(
                username = "alpha",
                accessCode = "CODE-ALPHA",
                expiry = System.currentTimeMillis() / 1_000L + 7_200L,
            )
            val metadata = store.recordAuthenticated(session, "account-a")
            AuthenticatedSessionRegistry.update(session, metadata)
            envelopePreferences.edit()
                .putString(CredentialVault.KEY_IV, "synthetic-a-iv")
                .putString(CredentialVault.KEY_PAYLOAD, "synthetic-a-payload")
                .commit()
            return metadata
        }

        fun installCurrentB(accountId: String): AccountSessionMetadata {
            val session = session(
                username = "beta",
                accessCode = "CODE-BETA",
                expiry = System.currentTimeMillis() / 1_000L + 7_200L,
            )
            val metadata = store.recordAuthenticated(session, accountId)
            AuthenticatedSessionRegistry.update(session, metadata)
            envelopePreferences.edit()
                .putString(CredentialVault.KEY_IV, "synthetic-b-iv")
                .putString(CredentialVault.KEY_PAYLOAD, "synthetic-b-payload")
                .commit()
            return metadata
        }

        fun assertCurrentB(expected: AccountSessionMetadata) {
            val metadata = checkNotNull(store.metadata())
            assertEquals(expected.accountId, metadata.accountId)
            assertEquals(expected.sessionId, metadata.sessionId)
            assertEquals(expected.accountId, store.activeAccountId())
            assertEquals(expected.sessionId, AuthenticatedSessionRegistry.currentOwner()?.sessionId)
            assertEquals("synthetic-b-iv", envelopePreferences.durableValue(CredentialVault.KEY_IV))
            assertEquals(
                "synthetic-b-payload",
                envelopePreferences.durableValue(CredentialVault.KEY_PAYLOAD),
            )
        }

        private fun session(username: String, accessCode: String, expiry: Long): AuthenticatedSession =
            AuthenticatedSession(
                portal = PortalConfig("https://provider.example.test", PortalConfig.Source.ACCESS_CODE),
                credentials = Credentials(accessCode, username, "secret-$username"),
                account = AccountInfo(username, "Active", expiry, 0, 1, false),
            )
    }
}
