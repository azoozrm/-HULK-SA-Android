package sa.hulksa.player.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationsRedirectPolicyTest {
    private val certificate = HeldCertificate.Builder()
        .commonName("127.0.0.1")
        .addSubjectAlternativeName("127.0.0.1")
        .addSubjectAlternativeName("localhost")
        .build()
    private val serverCertificates = HandshakeCertificates.Builder()
        .heldCertificate(certificate)
        .build()
    private val clientCertificates = HandshakeCertificates.Builder()
        .addTrustedCertificate(certificate.certificate)
        .build()
    private val servers = mutableListOf<MockWebServer>()

    @After
    fun tearDown() {
        servers.forEach { runCatching { it.shutdown() } }
    }

    @Test
    fun `valid https 200 config is accepted`() = runBlocking {
        val server = server()
        server.enqueue(configResponse())

        val result = OperationsClient(server.url("/config").toString(), client()).fetch()

        assertTrue(result is OperationsFetchResult.Success)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `all redirect codes fail without requesting the same-host target`() = runBlocking {
        listOf(301, 302, 303, 307, 308).forEach { code ->
            val server = server()
            server.enqueue(
                MockResponse()
                    .setResponseCode(code)
                    .setHeader("Location", server.url("/target").toString()),
            )
            server.enqueue(configResponse())

            val result = OperationsClient(server.url("/config").toString(), client()).fetch()

            assertTrue("code=$code", result is OperationsFetchResult.Failure)
            assertEquals("code=$code", 1, server.requestCount)
        }
    }

    @Test
    fun `cross-host https redirect is never followed`() = runBlocking {
        val source = server()
        val target = server()
        source.enqueue(
            MockResponse()
                .setResponseCode(307)
                .setHeader("Location", target.url("/config").toString()),
        )
        target.enqueue(configResponse())

        val result = OperationsClient(source.url("/config").toString(), client()).fetch()

        assertTrue(result is OperationsFetchResult.Failure)
        assertEquals(1, source.requestCount)
        assertEquals(0, target.requestCount)
    }

    @Test
    fun `https to http redirect never contacts the plaintext target`() = runBlocking {
        val source = server()
        val plaintext = MockWebServer().apply { start() }
        servers += plaintext
        source.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader(
                    "Location",
                    "http://127.0.0.1:${plaintext.port}/config",
                ),
        )
        plaintext.enqueue(configResponse())

        val result = OperationsClient(source.url("/config").toString(), client()).fetch()

        assertTrue(result is OperationsFetchResult.Failure)
        assertEquals(1, source.requestCount)
        assertEquals(0, plaintext.requestCount)
    }

    @Test
    fun `redirect loop is rejected after exactly one request`() = runBlocking {
        val server = server()
        server.enqueue(
            MockResponse()
                .setResponseCode(301)
                .setHeader("Location", server.url("/config").toString()),
        )
        server.enqueue(configResponse())

        val result = OperationsClient(server.url("/config").toString(), client()).fetch()

        assertTrue(result is OperationsFetchResult.Failure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `injected redirect-following client still cannot bypass the policy`() = runBlocking {
        val source = server()
        val target = server()
        source.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", target.url("/config").toString()),
        )
        target.enqueue(configResponse())
        val injected = client().newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val result = OperationsClient(source.url("/config").toString(), injected).fetch()

        assertTrue(result is OperationsFetchResult.Failure)
        assertEquals(1, source.requestCount)
        assertEquals(0, target.requestCount)
    }

    private fun server(): MockWebServer = MockWebServer().apply {
        useHttps(serverCertificates.sslSocketFactory(), false)
        start()
        servers += this
    }

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private fun configResponse(): MockResponse = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(
            """
            {
              "schemaVersion":1,
              "generatedAt":1770000000,
              "service":{"status":"OPERATIONAL","message":null},
              "update":{
                "latestVersionCode":65,
                "latestVersionName":"0.9.3.21",
                "minimumSupportedVersionCode":64,
                "updateType":"OPTIONAL",
                "apkUrl":"https://hulksa.com/hulk-operations/releases/hulk-sa-65.apk",
                "apkSha256":"${"a".repeat(64)}",
                "releaseNotes":""
              },
              "announcements":[],
              "features":{"downloads_enabled":true}
            }
            """.trimIndent(),
        )
}
