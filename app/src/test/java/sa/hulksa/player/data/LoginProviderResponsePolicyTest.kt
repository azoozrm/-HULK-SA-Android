package sa.hulksa.player.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

class LoginProviderResponsePolicyTest {
    private val server = MockWebServer()
    private val credentials = Credentials("SYNTHETIC-CODE", "synthetic-user", "synthetic-password")
    @Before fun start() { server.start() }
    @After fun stop() { server.shutdown() }

    private fun body(auth: Any? = 1, status: String? = "Active", expiry: String? = null): String {
        val user = JSONObject().put("username", "synthetic-user")
        if (auth != null) user.put("auth", auth)
        if (status != null) user.put("status", status)
        if (expiry != null) user.put("exp_date", expiry)
        return JSONObject().put("user_info", user).toString()
    }
    private fun result(raw: String, status: Int = 200) = runBlocking {
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json")
            .apply { if (status == 408) setHeader("Retry-After", "1") }.setBody(raw))
        runCatching { XtreamClient { 2_000L }.authenticate(PortalConfig(server.url("/").toString().trimEnd('/'), PortalConfig.Source.ACCESS_CODE), credentials) }
    }
    private fun failure(raw: String, category: LoginFailureCategory, status: Int = 200) {
        val error = result(raw, status).exceptionOrNull()!!
        assertTrue(error is XtreamException)
        assertEquals(LoginFailure(category, LoginFailureStage.PROVIDER), loginFailureFor(error))
    }

    @Test fun explicitFailedAuthExpiryIsPreservedButAuthZeroAloneIsBadCredentials() {
        for (auth in listOf(0, "0")) {
            failure(body(auth, "Expired"), LoginFailureCategory.EXPIRED_SUBSCRIPTION)
            failure(body(auth, "Active", "1999"), LoginFailureCategory.EXPIRED_SUBSCRIPTION)
            failure(body(auth, "Active", "2000"), LoginFailureCategory.EXPIRED_SUBSCRIPTION)
            failure(body(auth, null), LoginFailureCategory.INVALID_CREDENTIALS)
            failure(body(auth, "Active", "2001"), LoginFailureCategory.INVALID_CREDENTIALS)
            failure(body(auth, "Disabled", "2001"), LoginFailureCategory.INACTIVE_SUBSCRIPTION)
        }
    }
    @Test fun activePastEqualFutureAndUnlimitedEntitlementsKeepTheirActualMeaning() {
        failure(body(1, "Active", "1999"), LoginFailureCategory.EXPIRED_SUBSCRIPTION)
        failure(body(1, "Active", "2000"), LoginFailureCategory.EXPIRED_SUBSCRIPTION)
        for (expiry in listOf(null, "", "null", "0", "-1", "2001")) assertTrue(result(body(1, "Active", expiry)).isSuccess)
        for (status in listOf("Disabled", "Banned", "Inactive", "Suspended")) failure(body(1, status, "2001"), LoginFailureCategory.INACTIVE_SUBSCRIPTION)
    }
    @Test fun supportedAuthFlagAndOmittedStatusCompatibilityArePreserved() {
        for (auth in listOf(1, "1", 1.0)) for (status in listOf(null, "", "Active", "active")) {
            val session = result(body(auth, status, "2001")).getOrThrow()
            assertEquals("synthetic-user", session.account.username)
            assertTrue(session.account.status.equals("Active", true))
        }
        failure(body(null), LoginFailureCategory.MALFORMED)
        failure(body("not-an-auth-flag"), LoginFailureCategory.MALFORMED)
        failure(body(1, "UnknownProviderStatus"), LoginFailureCategory.INACTIVE_SUBSCRIPTION)
    }
    @Test fun literalNullStatusCannotBecomeAnOmittedActiveEntitlement() {
        for (status in listOf("null", "NULL", " null ")) {
            val observed = result(body(1, status, "2001"))
            assertTrue("An explicit non-Active status must be denied", observed.isFailure)
            assertSame(XtreamException.SubscriptionInactive, observed.exceptionOrNull())
            assertEquals(LoginFailure(LoginFailureCategory.INACTIVE_SUBSCRIPTION, LoginFailureStage.PROVIDER),
                loginFailureFor(observed.exceptionOrNull()!!))
        }
    }
    @Test fun receivedHttpFailuresCannotAuthenticateOrImpersonateCustomerCodeOrExpiry() {
        for (status in listOf(401, 403)) {
            failure(body(0, "Expired"), LoginFailureCategory.EXPIRED_SUBSCRIPTION, status)
            failure(body(0, "Disabled"), LoginFailureCategory.INACTIVE_SUBSCRIPTION, status)
            failure(body(0, "Active"), LoginFailureCategory.INVALID_CREDENTIALS, status)
            failure(body(1, "Active", "2001"), LoginFailureCategory.SERVICE_REJECTED, status)
        }
        for (status in listOf(404, 429, 500, 503)) failure(body(0, "Expired"), LoginFailureCategory.SERVICE_REJECTED, status)
        failure("timeout", LoginFailureCategory.CONNECTION_UNCERTAIN, 408)
    }
    @Test fun malformedDataAndActualChallengeKeepDistinctTruthfulCategories() {
        failure("not-json", LoginFailureCategory.MALFORMED)
        failure("{}", LoginFailureCategory.MALFORMED)
        failure("<html>ordinary page</html>", LoginFailureCategory.MALFORMED)
        failure("<html>Cloudflare: sorry, you have been blocked</html>", LoginFailureCategory.SERVICE_REJECTED)
        val valid = JSONObject(body()).apply { getJSONObject("user_info").put("username", "cloudflare-synthetic-customer") }
        assertTrue(result(valid.toString()).isSuccess)
    }
}
