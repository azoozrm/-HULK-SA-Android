package sa.hulksa.player.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

class AccountExpiryAuthenticationTest {
    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `active subscription with past expiry is rejected at the authentication boundary`() = runBlocking<Unit> {
        server.start()
        server.enqueue(authResponse(expiry = "1999"))

        val error = assertThrows(XtreamException.SubscriptionInactive::class.java) {
            runBlocking {
                XtreamClient(nowEpochSeconds = { 2_000L })
                    .authenticate(portal(), credentials())
            }
        }

        assertEquals(XtreamException.SubscriptionInactive, error)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `expiry exactly now is rejected at the authentication boundary`() = runBlocking<Unit> {
        server.start()
        server.enqueue(authResponse(expiry = "2000"))

        assertThrows(XtreamException.SubscriptionInactive::class.java) {
            runBlocking {
                XtreamClient(nowEpochSeconds = { 2_000L })
                    .authenticate(portal(), credentials())
            }
        }
    }

    @Test
    fun `future expiry is accepted unchanged`() = runBlocking<Unit> {
        server.start()
        server.enqueue(authResponse(expiry = "2001"))

        val session = XtreamClient(nowEpochSeconds = { 2_000L })
            .authenticate(portal(), credentials())

        assertEquals(2_001L, session.account.expiresAtEpochSeconds)
        assertEquals("Active", session.account.status)
    }

    @Test
    fun `absent blank zero and non-positive expiry preserve unlimited semantics`() = runBlocking<Unit> {
        server.start()
        listOf(null, "null", "", "0", "-5").forEach { expiry ->
            server.enqueue(authResponse(expiry = expiry))
            val session = XtreamClient(nowEpochSeconds = { 2_000L })
                .authenticate(portal(), credentials())
            assertNull("expiry=$expiry", session.account.expiresAtEpochSeconds)
        }
    }

    @Test
    fun `expired session is rejected at the durable session commit boundary`() {
        val nowSeconds = System.currentTimeMillis() / 1_000L
        listOf(nowSeconds - 1L, nowSeconds).forEach { expiry ->
            val store = AccountSessionStore(TestAppContext())
            assertThrows(XtreamException.SubscriptionInactive::class.java) {
                store.recordAuthenticated(session(expiry), "account-expired")
            }
            assertNull("expiry=$expiry", store.metadata())
            assertNull("expiry=$expiry", store.activeAccountId())
        }
    }

    @Test
    fun `future and unlimited sessions still commit`() {
        val nowSeconds = System.currentTimeMillis() / 1_000L
        listOf(nowSeconds + 3_600L, null).forEachIndexed { index, expiry ->
            val store = AccountSessionStore(TestAppContext())
            val metadata = store.recordAuthenticated(session(expiry), "account-$index")
            assertEquals(expiry, metadata.expiresAtEpochSeconds)
            assertEquals("account-$index", store.activeAccountId())
        }
    }

    @Test
    fun `deferred decision gate rejects an expired pending session`() {
        assertThrows(XtreamException.SubscriptionInactive::class.java) {
            requireUnexpiredAccountSession(expiresAtEpochSeconds = 2_000L, nowEpochSeconds = 2_000L)
        }
        requireUnexpiredAccountSession(expiresAtEpochSeconds = 2_001L, nowEpochSeconds = 2_000L)
        requireUnexpiredAccountSession(expiresAtEpochSeconds = null, nowEpochSeconds = 2_000L)
    }

    private fun portal(): PortalConfig = PortalConfig(
        baseUrl = server.url("/").toString().trimEnd('/'),
        source = PortalConfig.Source.ACCESS_CODE,
    )

    private fun credentials(): Credentials = Credentials(
        accessCode = "HULK-TEST-CODE",
        username = "subscriber",
        password = "secret",
    )

    private fun session(expiry: Long?): AuthenticatedSession = AuthenticatedSession(
        portal = PortalConfig("https://provider.example.test", PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials("HULK-TEST-CODE", "subscriber", "secret"),
        account = AccountInfo("subscriber", "Active", expiry, 0, 1, false),
    )

    private fun authResponse(expiry: String?): MockResponse {
        val expiryField = expiry?.let { ""","exp_date":"$it"""" }.orEmpty()
        return MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """{"user_info":{"auth":1,"status":"Active","username":"subscriber"$expiryField}}""",
            )
    }
}
