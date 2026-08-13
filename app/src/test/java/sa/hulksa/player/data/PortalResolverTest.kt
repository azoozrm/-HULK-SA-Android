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
    fun validCustomCodeResolvesCurrentResellerHostOverHttps() = runBlocking {
        server.enqueue(jsonResponse(200, """{"host":"http://reseller.example:8080/"}"""))

        val portal = resolver().resolve(" hulkab12-cd34 ")

        assertEquals("http://reseller.example:8080", portal.baseUrl)
        assertEquals(PortalConfig.Source.ACCESS_CODE, portal.source)
        val request = server.takeRequest()
        assertEquals("/api/reseller/resolve", request.path)
        assertEquals("POST", request.method)
        assertEquals(
            VALID_CODE,
            JSONObject(request.body.readUtf8()).getString("code"),
        )
    }

    @Test
    fun legacyGeneratedCodeRemainsSupported() = runBlocking {
        server.enqueue(jsonResponse(200, """{"host":"https://legacy.example"}"""))

        val portal = resolver().resolve(LEGACY_CODE)

        assertEquals("https://legacy.example", portal.baseUrl)
        assertEquals(
            LEGACY_CODE,
            JSONObject(server.takeRequest().body.readUtf8()).getString("code"),
        )
    }

    @Test
    fun invalidCodeReturnsClearFailure() {
        server.enqueue(apiError(404, "INVALID_CODE"))

        assertThrows(PortalException.InvalidAccessCode::class.java) {
            runBlocking { resolver().resolve(VALID_CODE) }
        }
    }

    @Test
    fun inactiveResellerReturnsClearFailure() {
        server.enqueue(apiError(403, "RESELLER_INACTIVE"))

        assertThrows(PortalException.ResellerInactive::class.java) {
            runBlocking { resolver().resolve(VALID_CODE) }
        }
    }

    @Test
    fun invalidHostIsRejectedEvenWhenApiReturnsSuccess() {
        server.enqueue(jsonResponse(200, """{"host":"javascript:alert(1)"}"""))

        assertThrows(PortalException.InvalidHost::class.java) {
            runBlocking { resolver().resolve(VALID_CODE) }
        }
    }

    @Test
    fun changedHostIsUsedWithoutAnAppUpdate() = runBlocking {
        server.enqueue(jsonResponse(200, """{"host":"http://first.example:8080"}"""))
        server.enqueue(jsonResponse(200, """{"host":"https://second.example"}"""))

        val resolver = resolver()
        assertEquals("http://first.example:8080", resolver.resolve(VALID_CODE).baseUrl)
        assertEquals("https://second.example", resolver.resolve(VALID_CODE).baseUrl)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun rotatedCodeInvalidatesOldCodeAndAcceptsNewCode() {
        server.enqueue(apiError(404, "INVALID_CODE"))
        server.enqueue(jsonResponse(200, """{"host":"http://reseller.example:8080"}"""))

        assertThrows(PortalException.InvalidAccessCode::class.java) {
            runBlocking { resolver().resolve(VALID_CODE) }
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
            runBlocking { insecureResolver.resolve(VALID_CODE) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun malformedAccessCodeIsRejectedBeforeNetworkRequest() {
        assertThrows(PortalException.InvalidAccessCode::class.java) {
            runBlocking { resolver().resolve("HULK-8K4P") }
        }
        assertEquals(0, server.requestCount)
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
        .setBody(body)

    private companion object {
        const val VALID_CODE = "HULK-AB12-CD34"
        const val ROTATED_CODE = "HULK-TUVW-XYZ2-3456"
        const val LEGACY_CODE = "HULK-ABCD-EFGH-JKMN-PQRS"
    }
}
