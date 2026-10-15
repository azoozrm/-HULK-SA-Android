package sa.hulksa.player.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PairingClientTest {
    private lateinit var server: MockWebServer
    private lateinit var httpClient: OkHttpClient
    private lateinit var client: PairingClient

    @Before
    fun setUp() {
        val certificate = HeldCertificate.Builder()
            .commonName("127.0.0.1")
            .addSubjectAlternativeName("127.0.0.1")
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
        httpClient = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                // Injected local mock transport only: production validation still sees the pinned
                // hulksa.com URL; the byte transport is redirected to the local TLS test server.
                val rewritten = chain.request().url.newBuilder()
                    .scheme("https")
                    .host("127.0.0.1")
                    .port(server.port)
                    .build()
                chain.proceed(chain.request().newBuilder().url(rewritten).build())
            }
            .build()
        client = PairingClient(httpClient)
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun `create sends the exact wire shape and parses 201`() = runBlocking {
        server.enqueue(jsonResponse(201, createdBody()))

        val result = client.create(PAIRING_BASE, input())

        val created = (result as PairingResult.Success).value
        assertEquals(REQUEST_ID, created.requestId)
        assertEquals(USER_CODE, created.userCode)
        assertEquals(CLAIM_TOKEN, created.claimToken)
        assertEquals(1_770_000_600_000L, created.expiresAtEpochMs)
        assertEquals(CANONICAL_VERIFICATION_URL, created.verificationUrl)
        assertEquals(3, created.pollIntervalSeconds)
        assertEquals(20, created.maxDeliveries)

        val request = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertEquals("/control-center/api/app/v1/pairing/create/", request.path)
        assertEquals("POST", request.method)
        assertNull(request.getHeader("Authorization"))
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
        assertEquals("no-store", request.getHeader("Cache-Control"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals(
            setOf(
                "contractVersion",
                "clientRequestId",
                "clientSecret",
                "installationId",
                "device",
                "requestedAtEpochMs",
            ),
            body.keys().asSequence().toSet(),
        )
        assertEquals(1, body.getInt("contractVersion"))
        assertEquals(REQUEST_ID, body.getString("clientRequestId"))
        assertEquals(INSTALLATION_ID, body.getString("installationId"))
        assertEquals(CLIENT_SECRET, body.getString("clientSecret"))
        assertTrue(isPairingCanonicalToken(body.getString("clientSecret")))
        val device = body.getJSONObject("device")
        assertEquals("TV", device.getString("platform"))
        assertEquals("Example", device.getString("manufacturer"))
        assertEquals("ExampleTV", device.getString("model"))
        assertEquals("0.9.3.23", device.getString("appVersion"))
        assertEquals(1_770_000_000_000L, body.getLong("requestedAtEpochMs"))
    }

    @Test
    fun `create accepts the idempotent 200 replay`() = runBlocking {
        server.enqueue(jsonResponse(200, createdBody()))

        val result = client.create(PAIRING_BASE, input())

        assertTrue(result is PairingResult.Success)
    }

    @Test
    fun `create rejects unsafe discovery and invalid input before any request`() = runBlocking {
        val unsafe = client.create("http://hulksa.com/control-center/api/app/v1/pairing/", input())
        val invalidSecret = client.create(PAIRING_BASE, input().copy(clientSecret = "short"))
        val invalidUuid = client.create(PAIRING_BASE, input().copy(clientRequestId = "not-a-uuid"))

        assertEquals(PairingResult.DiscoveryInvalid, unsafe)
        assertEquals(PairingResult.InvalidResponse, invalidSecret)
        assertEquals(PairingResult.InvalidResponse, invalidUuid)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `create accepts legacy and canonical verification urls and exposes the canonical url`() = runBlocking {
        server.enqueue(jsonResponse(201, createdBody()))
        server.enqueue(
            jsonResponse(
                201,
                createdBody(verificationBase = "https://hulksa.com/pair/#C="),
            ),
        )

        val legacy = (client.create(PAIRING_BASE, input()) as PairingResult.Success).value
        val canonical = (client.create(PAIRING_BASE, input()) as PairingResult.Success).value

        assertEquals(CANONICAL_VERIFICATION_URL, legacy.verificationUrl)
        assertEquals(CANONICAL_VERIFICATION_URL, canonical.verificationUrl)
        assertEquals(USER_CODE, legacy.userCode)
    }

    @Test
    fun `create rejects every nonmatching verification url variant`() = runBlocking {
        val malformed = listOf(
            "mismatched code (legacy)" to createdBody(verificationCode = "HULKTV-ZZZZZZ"),
            "mismatched code (canonical)" to
                createdBody(
                    verificationBase = "https://hulksa.com/pair/#C=",
                    verificationCode = "HULKTV-ZZZZZZ",
                ),
            "different host" to
                createdBody(verificationBase = "https://hulksa.com.evil.test/pair/#C="),
            "http scheme" to createdBody(verificationBase = "http://hulksa.com/pair/#C="),
            "explicit port" to createdBody(verificationBase = "https://hulksa.com:443/pair/#C="),
            "credentials" to createdBody(verificationBase = "https://user@hulksa.com/pair/#C="),
            "query" to createdBody(verificationBase = "https://hulksa.com/pair/?x=1#C="),
            "encoded path" to
                createdBody(verificationBase = "https://hulksa.com/pair%2F#C="),
            "double slash path" to
                createdBody(verificationBase = "https://hulksa.com//pair/#C="),
            "extra fragment" to
                createdBody(verificationBase = "https://hulksa.com/pair/#C=x;y#C="),
            "other path" to
                createdBody(verificationBase = "https://hulksa.com/control-center/pairing/#C="),
        )

        malformed.forEach { (name, body) ->
            server.enqueue(jsonResponse(201, body))
            assertEquals(name, PairingResult.InvalidResponse, client.create(PAIRING_BASE, input()))
        }
    }

    @Test
    fun `create rejects an invalid user code and invalid bounds`() = runBlocking {
        server.enqueue(jsonResponse(201, createdBody(userCode = "HULKTV-IIIIII")))
        server.enqueue(jsonResponse(201, createdBody(pollIntervalSeconds = "0")))

        assertEquals(PairingResult.InvalidResponse, client.create(PAIRING_BASE, input()))
        assertEquals(PairingResult.InvalidResponse, client.create(PAIRING_BASE, input()))
    }

    @Test
    fun `create maps the error envelope and retry-after`() = runBlocking {
        server.enqueue(
            jsonResponse(
                429,
                """{"error":{"code":"RATE_LIMITED","message":"Too many pairing requests.","retryAfterSeconds":7}}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "5").setBody("{}"))

        val throttled = client.create(PAIRING_BASE, input()) as PairingResult.HttpFailure
        assertEquals("RATE_LIMITED", throttled.code)
        assertEquals(429, throttled.httpStatus)
        assertEquals(7, throttled.retryAfterSeconds)
        val unavailable = client.create(PAIRING_BASE, input()) as PairingResult.HttpFailure
        assertEquals("HTTP_503", unavailable.code)
        assertEquals(5, unavailable.retryAfterSeconds)
    }

    @Test
    fun `retry after uses the larger of body and header conservatively`() = runBlocking {
        server.enqueue(
            jsonResponse(
                429,
                """{"error":{"code":"RATE_LIMITED","message":"x","retryAfterSeconds":3}}""",
            ).setHeader("Retry-After", "9"),
        )

        val failure = client.create(PAIRING_BASE, input()) as PairingResult.HttpFailure

        assertEquals(9, failure.retryAfterSeconds)
    }

    @Test
    fun `claim parses pending and approved with credentials`() = runBlocking {
        server.enqueue(
            jsonResponse(
                200,
                """{"contractVersion":1,"state":"PENDING","expiresAtEpochMs":1770000600000,"retryAfterMs":3000}""",
            ),
        )
        server.enqueue(
            jsonResponse(
                200,
                """{"contractVersion":1,"state":"APPROVED","expiresAtEpochMs":1770000600000,"retryAfterMs":3000,"deliveryCount":1,"credentials":{"accessCode":"CODE","username":"user","password":"secret"}}""",
            ),
        )

        val pending = client.claim(PAIRING_BASE, REQUEST_ID, CLAIM_TOKEN)
        assertEquals(
            PairingClaimOutcome.Pending(expiresAtEpochMs = 1_770_000_600_000L, retryAfterMs = 3_000L),
            (pending as PairingResult.Success).value,
        )
        val approved = client.claim(PAIRING_BASE, REQUEST_ID, CLAIM_TOKEN)
        assertEquals(
            PairingClaimOutcome.Approved(
                expiresAtEpochMs = 1_770_000_600_000L,
                retryAfterMs = 3_000L,
                deliveryCount = 1,
                credentials = sa.hulksa.player.model.Credentials("CODE", "user", "secret"),
            ),
            (approved as PairingResult.Success).value,
        )

        val request = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertEquals("/control-center/api/app/v1/pairing/claim/", request.path)
        assertEquals("Bearer $CLAIM_TOKEN", request.getHeader("Authorization"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals(setOf("contractVersion", "requestId"), body.keys().asSequence().toSet())
        assertEquals(1, body.getInt("contractVersion"))
        assertEquals(REQUEST_ID, body.getString("requestId"))
    }

    @Test
    fun `claim parses terminal states and rejects missing fields`() = runBlocking {
        server.enqueue(jsonResponse(200, """{"contractVersion":1,"state":"EXPIRED"}"""))
        server.enqueue(jsonResponse(200, """{"contractVersion":1,"state":"DENIED"}"""))
        server.enqueue(jsonResponse(200, """{"contractVersion":1,"state":"APPROVED","expiresAtEpochMs":1}"""))

        assertEquals(
            PairingClaimOutcome.Terminal("EXPIRED"),
            (client.claim(PAIRING_BASE, REQUEST_ID, CLAIM_TOKEN) as PairingResult.Success).value,
        )
        assertEquals(
            PairingClaimOutcome.Terminal("DENIED"),
            (client.claim(PAIRING_BASE, REQUEST_ID, CLAIM_TOKEN) as PairingResult.Success).value,
        )
        assertEquals(
            PairingResult.InvalidResponse,
            client.claim(PAIRING_BASE, REQUEST_ID, CLAIM_TOKEN),
        )
    }

    @Test
    fun `ack and cancel enforce their accepted states`() = runBlocking {
        server.enqueue(jsonResponse(200, """{"contractVersion":1,"state":"ACKED"}"""))
        server.enqueue(jsonResponse(200, """{"contractVersion":1,"state":"CANCELLED"}"""))
        server.enqueue(
            jsonResponse(409, """{"error":{"code":"PAIRING_STATE_CONFLICT","message":"conflict"}}"""),
        )

        assertEquals(
            "ACKED",
            (client.ack(PAIRING_BASE, REQUEST_ID, CLAIM_TOKEN) as PairingResult.Success).value,
        )
        assertEquals(
            "CANCELLED",
            (client.cancel(PAIRING_BASE, REQUEST_ID, CLAIM_TOKEN) as PairingResult.Success).value,
        )
        val conflict = client.ack(PAIRING_BASE, REQUEST_ID, CLAIM_TOKEN) as PairingResult.HttpFailure
        assertEquals("PAIRING_STATE_CONFLICT", conflict.code)
    }

    @Test
    fun `redirects are refused`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(301)
                .setHeader("Location", "https://hulksa.com/control-center/api/app/v1/pairing/create/"),
        )

        val result = client.create(PAIRING_BASE, input())

        assertEquals(PairingResult.HttpFailure("HTTP_301", 301, null), result)
        val request = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertEquals("/control-center/api/app/v1/pairing/create/", request.path)
    }

    @Test
    fun `oversized response bodies are rejected without unbounded reading`() = runBlocking {
        server.enqueue(jsonResponse(201, "x".repeat(70 * 1_024)))

        assertEquals(PairingResult.InvalidResponse, client.create(PAIRING_BASE, input()))
    }

    @Test
    fun `transport failure stays a bounded transient result`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val timedClient = PairingClient(
            httpClient.newBuilder()
                .callTimeout(100, TimeUnit.MILLISECONDS)
                .build(),
        )

        assertEquals(PairingResult.TransportFailure, timedClient.create(PAIRING_BASE, input()))
    }

    @Test
    fun `cancelling the coroutine cancels the in-flight request`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val cancelled = CountDownLatch(1)
        val observed = CompletableDeferred<Throwable>()
        val cancellableClient = PairingClient(
            httpClient.newBuilder()
                .eventListener(
                    object : EventListener() {
                        override fun canceled(call: Call) {
                            cancelled.countDown()
                        }
                    },
                )
                .build(),
        )

        val job = launch(Dispatchers.Default) {
            try {
                cancellableClient.create(PAIRING_BASE, input())
            } catch (error: Throwable) {
                observed.complete(error)
                throw error
            }
        }

        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        job.cancel()
        assertTrue(cancelled.await(5, TimeUnit.SECONDS))
        assertTrue(withTimeout(5_000L) { observed.await() } is CancellationException)
        withTimeout(5_000L) { job.join() }
    }

    @Test
    fun `canonical secret format matches the backend contract`() {
        val secret = ByteArray(32) { index -> (index * 7 + 3).toByte() }
        val encoded = canonicalBase64Url(secret)

        assertEquals(43, encoded.length)
        assertTrue(isPairingCanonicalToken(encoded))
        val decoded = java.util.Base64.getUrlDecoder().decode(encoded)
        assertEquals(32, decoded.size)
        assertTrue(secret.contentEquals(decoded))
        assertFalse(encoded.contains('='))

        assertEquals(USER_CODE, "HULKTV-ABCDEF")
        assertTrue(isPairingUserCode(USER_CODE))
        assertFalse(isPairingUserCode("HULKTV-IIIIII"))
        assertFalse(isPairingUserCode("ABCDEF-HULKTV"))
        assertTrue(isPairingUuidV4(REQUEST_ID))
        assertFalse(isPairingUuidV4(REQUEST_ID.replace('4', '3', ignoreCase = false)))
        assertNull(canonicalPairingBaseUrl("https://hulksa.com/control-center/api/app/v1/pairing"))
        assertFalse(isPairingCanonicalToken("A".repeat(42) + "B"))
        assertTrue(isPairingCanonicalToken(encoded))
    }

    @Test
    fun `create rejects coercible numeric fields and noncanonical tokens`() = runBlocking {
        val malformed = listOf(
            "fractional contractVersion" to createdBody(contractVersion = "1.5"),
            "numeric-string contractVersion" to createdBody(contractVersion = "\"1\""),
            "numeric-string expiry" to createdBody(expiresAt = "\"1770000600000\""),
            "fractional expiry" to createdBody(expiresAt = "1770000600000.5"),
            "numeric-string poll interval" to createdBody(pollIntervalSeconds = "\"3\""),
            "fractional max deliveries" to createdBody(maxDeliveries = "20.5"),
            "noncanonical claim token" to createdBody(claimToken = "A".repeat(42) + "B"),
            "null user code" to createdBody(userCode = "null"),
        )

        malformed.forEach { (name, body) ->
            server.enqueue(jsonResponse(201, body))
            assertEquals(name, PairingResult.InvalidResponse, client.create(PAIRING_BASE, input()))
        }
    }

    @Test
    fun `claim rejects coercible fields and non-string credential slots`() = runBlocking {
        val malformed = listOf(
            "fractional version" to
                """{"contractVersion":1.5,"state":"PENDING","expiresAtEpochMs":1,"retryAfterMs":1}""",
            "numeric-string version" to
                """{"contractVersion":"1","state":"PENDING","expiresAtEpochMs":1,"retryAfterMs":1}""",
            "numeric state" to
                """{"contractVersion":1,"state":7}""",
            "null state" to
                """{"contractVersion":1,"state":null}""",
            "numeric-string expiry" to
                """{"contractVersion":1,"state":"PENDING","expiresAtEpochMs":"1770000600000","retryAfterMs":3000}""",
            "fractional retry after" to
                """{"contractVersion":1,"state":"PENDING","expiresAtEpochMs":1770000600000,"retryAfterMs":3000.5}""",
            "numeric-string delivery count" to
                """{"contractVersion":1,"state":"APPROVED","expiresAtEpochMs":1770000600000,"retryAfterMs":3000,"deliveryCount":"1","credentials":{"accessCode":"CODE","username":"user","password":"secret"}}""",
            "numeric access code" to
                """{"contractVersion":1,"state":"APPROVED","expiresAtEpochMs":1770000600000,"retryAfterMs":3000,"deliveryCount":1,"credentials":{"accessCode":123,"username":"user","password":"secret"}}""",
            "null password" to
                """{"contractVersion":1,"state":"APPROVED","expiresAtEpochMs":1770000600000,"retryAfterMs":3000,"deliveryCount":1,"credentials":{"accessCode":"CODE","username":"user","password":null}}""",
        )

        malformed.forEach { (name, body) ->
            server.enqueue(jsonResponse(200, body))
            assertEquals(
                name,
                PairingResult.InvalidResponse,
                client.claim(PAIRING_BASE, REQUEST_ID, CLAIM_TOKEN),
            )
        }
    }

    @Test
    fun `noncanonical client secret fails closed before any request`() = runBlocking {
        val result = client.create(PAIRING_BASE, input(secret = "A".repeat(42) + "B"))

        assertEquals(PairingResult.InvalidResponse, result)
        assertEquals(0, server.requestCount)
    }

    private fun input(secret: String = CLIENT_SECRET) = PairingCreateInput(
        clientRequestId = REQUEST_ID,
        clientSecret = secret,
        installationId = INSTALLATION_ID,
        requestedAtEpochMs = 1_770_000_000_000L,
        device = PairingDeviceInfo(
            manufacturer = "Example",
            model = "ExampleTV",
            appVersion = "0.9.3.23",
        ),
    )

    private fun createdBody(
        userCode: String = USER_CODE,
        verificationCode: String = USER_CODE,
        verificationBase: String = "https://hulksa.com/control-center/pair/#C=",
        contractVersion: String = "1",
        claimToken: String = CLAIM_TOKEN,
        expiresAt: String = "1770000600000",
        pollIntervalSeconds: String = "3",
        maxDeliveries: String = "20",
    ): String = """
        {
          "contractVersion":$contractVersion,
          "requestId":"$REQUEST_ID",
          "userCode":"$userCode",
          "claimToken":"$claimToken",
          "expiresAtEpochMs":$expiresAt,
          "verificationUrl":"$verificationBase$verificationCode",
          "pollIntervalSeconds":$pollIntervalSeconds,
          "maxDeliveries":$maxDeliveries
        }
    """.trimIndent()

    private fun jsonResponse(status: Int, body: String): MockResponse = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private companion object {
        const val PAIRING_BASE = "https://hulksa.com/control-center/api/app/v1/pairing/"
        const val REQUEST_ID = "11111111-2222-4333-8444-555555555555"
        const val INSTALLATION_ID = "99999999-8888-4777-8666-555555555555"
        const val USER_CODE = "HULKTV-ABCDEF"
        const val CANONICAL_VERIFICATION_URL = "https://hulksa.com/pair/#C=HULKTV-ABCDEF"
        const val CLIENT_SECRET = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        val CLAIM_TOKEN: String = canonicalBase64Url(ByteArray(32) { 2 })
    }
}
