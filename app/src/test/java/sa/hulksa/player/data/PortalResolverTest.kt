package sa.hulksa.player.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import sa.hulksa.player.model.PortalConfig

class PortalResolverTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        val certificate = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()

        server = MockWebServer().apply {
            useHttps(serverCertificates.sslSocketFactory(), false)
            start()
        }
        client = OkHttpClient.Builder()
            .sslSocketFactory(
                clientCertificates.sslSocketFactory(),
                clientCertificates.trustManager,
            )
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun generatedCaseSensitiveCodeIsSentExactlyAsEntered() = runBlocking {
        server.enqueue(jsonResponse(200, """{"host":"http://reseller.example:8080/"}"""))

        val portal = resolver().resolve(GENERATED_CODE)

        assertEquals("http://reseller.example:8080", portal.baseUrl)
        assertEquals(PortalConfig.Source.ACCESS_CODE, portal.source)
        val request = server.takeRequest()
        assertEquals("/api/reseller/resolve/", request.path)
        assertEquals("POST", request.method)
        assertEquals(
            GENERATED_CODE,
            JSONObject(request.body.readUtf8()).getString("code"),
        )
    }

    @Test
    fun mixedCaseCodeRemainsCaseSensitiveEndToEnd() = runBlocking {
        server.enqueue(jsonResponse(200, """{"host":"https://reseller.example"}"""))

        resolver().resolve(MIXED_CASE_CODE)

        assertEquals(
            MIXED_CASE_CODE,
            JSONObject(server.takeRequest().body.readUtf8()).getString("code"),
        )
    }

    @Test
    fun customAdminCodeOutsideLegacyFormatIsForwardedToApi() = runBlocking {
        server.enqueue(jsonResponse(200, """{"host":"https://custom.example"}"""))

        resolver().resolve(CUSTOM_ADMIN_CODE)

        assertEquals(
            CUSTOM_ADMIN_CODE,
            JSONObject(server.takeRequest().body.readUtf8()).getString("code"),
        )
    }

    @Test
    fun accessCodeCharactersAndWhitespaceAreNotModified() = runBlocking {
        server.enqueue(jsonResponse(200, """{"host":"https://custom.example"}"""))
        val exactInput = "  Ab-12_cd!  "

        resolver().resolve(exactInput)

        assertEquals(
            exactInput,
            JSONObject(server.takeRequest().body.readUtf8()).getString("code"),
        )
    }

    @Test
    fun legacyGeneratedCodeRemainsSupportedWithoutReformatting() = runBlocking {
        server.enqueue(jsonResponse(200, """{"host":"https://legacy.example"}"""))

        val portal = resolver().resolve(LEGACY_CODE)

        assertEquals("https://legacy.example", portal.baseUrl)
        assertEquals(
            LEGACY_CODE,
            JSONObject(server.takeRequest().body.readUtf8()).getString("code"),
        )
    }

    @Test
    fun invalidCodeReturnsClearFailureFromApi() {
        server.enqueue(apiError(404, "INVALID_CODE"))

        assertThrows(PortalException.InvalidAccessCode::class.java) {
            runBlocking { resolver().resolve(GENERATED_CODE) }
        }
    }

    @Test
    fun inactiveResellerReturnsClearFailure() {
        server.enqueue(apiError(403, "RESELLER_INACTIVE"))

        assertThrows(PortalException.ResellerInactive::class.java) {
            runBlocking { resolver().resolve(GENERATED_CODE) }
        }
    }

    @Test
    fun routing404WithoutSemanticCodeIsNotAnInvalidCustomerCode() {
        server.enqueue(jsonResponse(404, "<html>route unavailable</html>"))

        val failure = assertThrows(PortalException::class.java) {
            runBlocking { resolver().resolve(GENERATED_CODE) }
        }

        assertFalse(failure is PortalException.InvalidAccessCode)
    }

    @Test
    fun gateway403WithoutSemanticCodeIsNotADisabledCustomerCode() {
        server.enqueue(jsonResponse(403, "<html>gateway refused request</html>"))

        val failure = assertThrows(PortalException::class.java) {
            runBlocking { resolver().resolve(GENERATED_CODE) }
        }

        assertFalse(failure is PortalException.ResellerInactive)
    }

    @Test
    fun invalidHostIsRejectedEvenWhenApiReturnsSuccess() {
        server.enqueue(jsonResponse(200, """{"host":"javascript:alert(1)"}"""))

        assertThrows(PortalException.InvalidHost::class.java) {
            runBlocking { resolver().resolve(GENERATED_CODE) }
        }
    }

    @Test
    fun changedHostIsUsedWithoutAnAppUpdate() = runBlocking {
        server.enqueue(jsonResponse(200, """{"host":"http://first.example:8080"}"""))
        server.enqueue(jsonResponse(200, """{"host":"https://second.example"}"""))

        val resolver = resolver()
        assertEquals("http://first.example:8080", resolver.resolve(GENERATED_CODE).baseUrl)
        assertEquals("https://second.example", resolver.resolve(GENERATED_CODE).baseUrl)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun rotatedCodeInvalidatesOldCodeAndAcceptsNewCode() {
        server.enqueue(apiError(404, "INVALID_CODE"))
        server.enqueue(jsonResponse(200, """{"host":"http://reseller.example:8080"}"""))

        assertThrows(PortalException.InvalidAccessCode::class.java) {
            runBlocking { resolver().resolve(GENERATED_CODE) }
        }
        val portal = runBlocking { resolver().resolve(ROTATED_CODE) }
        assertEquals("http://reseller.example:8080", portal.baseUrl)
    }

    @Test
    fun publicApiConfigurationMustUseHttps() {
        val insecureResolver = PortalResolver(
            apiBaseUrl = "http://api.example.test",
            client = client,
        )

        assertThrows(PortalException.ConfigurationMissing::class.java) {
            runBlocking { insecureResolver.resolve(GENERATED_CODE) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun blankAccessCodeIsRejectedBeforeNetworkRequest() {
        assertThrows(PortalException.InvalidAccessCode::class.java) {
            runBlocking { resolver().resolve("   ") }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun resolverCategoriesFollowSemanticCodesAndPreserveReceivedResponseProvenance() {
        val cases = listOf(
            Triple(503, """{"error":{"code":"INVALID_CODE"}}""", LoginFailureCategory.SERVICE_REJECTED),
            Triple(404, "<html>route</html>", LoginFailureCategory.SERVICE_REJECTED),
            Triple(403, "<html>gateway</html>", LoginFailureCategory.SERVICE_REJECTED),
            Triple(408, "timeout", LoginFailureCategory.CONNECTION_UNCERTAIN),
            Triple(404, """{"error":{"code":"INVALID_CODE"}}""", LoginFailureCategory.INVALID_CODE),
            Triple(403, """{"error":{"code":"RESELLER_INACTIVE"}}""", LoginFailureCategory.DISABLED_CODE),
            Triple(422, """{"error":{"code":"INVALID_HOST"}}""", LoginFailureCategory.SERVICE_REJECTED),
            Triple(200, "not-json", LoginFailureCategory.MALFORMED),
            Triple(200, "{}", LoginFailureCategory.MALFORMED),
        )
        for ((status, body, category) in cases) {
            server.enqueue(jsonResponse(status, body))
            val failure = assertThrows(PortalException::class.java) { runBlocking { resolver().resolve(GENERATED_CODE) } }
            assertEquals(category, failure.loginFailure.category)
            assertEquals(LoginFailureStage.RESOLVER, failure.loginFailure.stage)
        }
    }

    @Test
    fun resolverIoFailureRetainsTransportCauseInsteadOfGuessingOffline() {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))
        val failure = assertThrows(PortalException.Transport::class.java) {
            runBlocking { PortalResolver(server.url("/").toString(), client.newBuilder().retryOnConnectionFailure(false).build()).resolve(GENERATED_CODE) }
        }
        assertEquals(LoginFailure(LoginFailureCategory.TRANSPORT, LoginFailureStage.RESOLVER), failure.loginFailure)
    }

    private fun resolver(): PortalResolver = PortalResolver(
        apiBaseUrl = server.url("/").toString(),
        client = client,
    )

    private fun apiError(status: Int, code: String): MockResponse = jsonResponse(
        status,
        """{"error":{"code":"$code","message":"error"}}""",
    )

    private fun jsonResponse(status: Int, body: String): MockResponse = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        // A received 408 is distinct from OkHttp retrying it and then hitting a transport error.
        .apply { if (status == 408) setHeader("Retry-After", "1") }
        .setBody(body)

    private companion object {
        const val GENERATED_CODE = "VUKqm6Z6ZZ"
        const val MIXED_CASE_CODE = "aB12Cd34Ef"
        const val CUSTOM_ADMIN_CODE = "Admin-Code_v2!"
        const val ROTATED_CODE = "zY98xW76vU"
        const val LEGACY_CODE = "HULK-ABCD-EFGH-JKMN-PQRS"
    }
}
