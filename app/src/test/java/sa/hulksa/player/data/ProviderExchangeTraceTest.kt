package sa.hulksa.player.data

import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

class ProviderExchangeTraceTest {
    private lateinit var server: MockWebServer
    private val lines = Collections.synchronizedList(mutableListOf<String>())
    private var previousSink: ((String) -> Unit)? = null

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        previousSink = ProviderRequestTrace.sink
        ProviderRequestTrace.resetForTest()
        lines.clear()
        ProviderRequestTrace.sink = { lines += it }
    }

    @After
    fun tearDown() {
        ProviderRequestTrace.sink = previousSink
        ProviderRequestTrace.resetForTest()
        runCatching { server.shutdown() }
    }

    @Test
    fun `successful exchange records identity without changing request`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"info":{"plot":"ok"}}"""),
        )
        val session = session()
        val result = MovieCardMetadataClient().fetch(session, 42, ProviderSurface.MOVIE_CARD)

        assertTrue(result.succeeded)
        assertEquals(1, server.requestCount)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertTrue(request.path.orEmpty().contains("action=get_vod_info"))
        assertEquals("application/json", request.getHeader("Accept"))
        assertTrue(request.getHeader("User-Agent").orEmpty().startsWith("HULK-SA/"))
        assertTrue(lines.any {
            "ev=exchange_request" in it &&
                "id=" in it &&
                "surface=MOVIE_CARD" in it &&
                "action=get_vod_info" in it &&
                "owner=" in it &&
                "x=0" in it &&
                "endpoint_equal=true" in it &&
                "scheme_equal=true" in it &&
                "host_equal=true" in it &&
                "port_equal=true" in it &&
                "path_equal=true" in it &&
                "username_equal=true" in it &&
                "password_equal=true" in it &&
                "credentials_present=true" in it
        })
        assertTrue(lines.any { "ev=exchange_response" in it && "code=200" in it })
    }

    @Test
    fun `429 responses record allowlisted headers with and without retry-after`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "7")
                .setHeader("Date", "Thu, 12 Nov 2026 02:52:49 GMT")
                .setHeader("Server", "cloudflare")
                .setHeader("CF-Ray", "a5923e279fd9e28c-MRS")
                .setHeader("CF-Mitigated", "challenge")
                .setBody("""{"error":"limited"}"""),
        )
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"limited"}"""))
        val client = MovieCardMetadataClient()

        assertFalse(client.fetch(session(), 42).succeeded)
        assertTrue(lines.any {
            "ev=exchange_response" in it &&
                "code=429" in it &&
                "retry_after=7" in it &&
                "date=Thu,_12_Nov_2026_02:52:49_GMT" in it &&
                "cf_ray=a5923e279fd9e28c-MRS" in it &&
                "cf_mitigated=challenge" in it
        })

        lines.clear()
        assertFalse(client.fetch(session(), 42).succeeded)
        assertTrue(lines.any {
            "ev=exchange_response" in it &&
                "code=429" in it &&
                "retry_after=none" in it &&
                "cf_ray=none" in it &&
                "cf_mitigated=none" in it
        })
    }

    @Test
    fun `redirect records every exchange and changed destination and credentials`() = runBlocking {
        val redirected = server.url(
            "/r28-secret-redirect/player_api.php" +
                "?username=changed-user&password=changed-pass&action=get_vod_info",
        )
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", redirected))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"info":{"plot":"ok"}}"""))

        assertTrue(MovieCardMetadataClient().fetch(session(), 42).succeeded)
        assertEquals(2, server.requestCount)
        assertTrue(lines.any {
            "ev=exchange_request" in it &&
                "x=0" in it &&
                "endpoint_equal=true" in it &&
                "endpoint_changed=false" in it
        })
        assertTrue(lines.any {
            "ev=exchange_request" in it &&
                "x=1" in it &&
                "endpoint_equal=false" in it &&
                "endpoint_changed=true" in it &&
                "scheme_equal=true" in it &&
                "host_equal=true" in it &&
                "port_equal=true" in it &&
                "path_equal=false" in it &&
                "path_changed=true" in it &&
                "username_equal=false" in it &&
                "password_equal=false" in it &&
                "username_changed=true" in it &&
                "password_changed=true" in it
        })
        assertTrue(lines.any { "ev=exchange_response" in it && "x=0" in it && "code=302" in it })
        assertTrue(lines.any { "ev=exchange_response" in it && "x=1" in it && "code=200" in it })
        assertFalse(lines.any { "r28-secret-redirect" in it || "changed-user" in it || "changed-pass" in it })
    }


    @Test
    fun `redirect to another port records only the port change`() = runBlocking {
        val other = MockWebServer()
        other.start()
        try {
            other.enqueue(MockResponse().setResponseCode(200).setBody("""{"info":{}}"""))
            val redirect = other.url(
                "/player_api.php?username=user&password=pass&action=get_vod_info",
            )
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", redirect))

            assertTrue(MovieCardMetadataClient().fetch(session(), 42).succeeded)
            assertTrue(lines.any {
                "ev=exchange_request" in it &&
                    "x=1" in it &&
                    "scheme_equal=true" in it &&
                    "host_equal=true" in it &&
                    "port_equal=false" in it &&
                    "path_equal=true" in it &&
                    "port_changed=true" in it
            })
        } finally {
            other.shutdown()
        }
    }


    @Test
    fun `observed header URLs and credential material are rejected without echo`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "https://example.test/player_api.php?username=demo-user&password=demo-pass")
                .setHeader("Date", "Thu, 12 Nov 2026 02:52:49 GMT")
                .setHeader("Server", "https://example.test/player_api.php?username=demo-user&password=demo-pass")
                .setHeader("CF-Ray", "a5923e279fd9e28c-MRS")
                .setHeader("CF-Mitigated", "https://example.test/player_api.php?username=demo-user&password=demo-pass")
                .setBody("""{"error":"limited"}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Server", "S".repeat(200))
                .setBody("""{"error":"limited"}"""),
        )
        val client = MovieCardMetadataClient()

        assertFalse(client.fetch(session(), 42).succeeded)
        assertTrue(lines.any {
            "ev=exchange_response" in it &&
                "code=429" in it &&
                "retry_after=rejected" in it &&
                "server=other" in it &&
                "cf_mitigated=other" in it &&
                "date=Thu,_12_Nov_2026_02:52:49_GMT" in it &&
                "cf_ray=a5923e279fd9e28c-MRS" in it
        })
        assertFalse(lines.any {
            "example.test" in it || "demo-user" in it || "demo-pass" in it ||
                "player_api.php" in it || "https" in it
        })

        lines.clear()
        assertFalse(client.fetch(session(), 42).succeeded)
        assertTrue(lines.any { "ev=exchange_response" in it && "server=other" in it })
        assertFalse(lines.any { "S".repeat(32) in it })
    }



    @Test
    fun `server and mitigation headers are closed classifications without bare host echo`() = runBlocking {
        val cases = listOf(
            "127.0.0.1" to "//example.test/private-path",
            "example.test" to "127.0.0.1",
            "//example.test/private-path" to "example.test",
        )
        val client = MovieCardMetadataClient()
        for ((serverValue, mitigationValue) in cases) {
            lines.clear()
            server.enqueue(
                MockResponse()
                    .setResponseCode(429)
                    .setHeader("Server", serverValue)
                    .setHeader("CF-Mitigated", mitigationValue)
                    .setBody("""{"error":"limited"}"""),
            )
            assertFalse(client.fetch(session(), 42).succeeded)
            assertTrue(lines.any {
                "ev=exchange_response" in it &&
                    "code=429" in it &&
                    "server=other" in it &&
                    "cf_mitigated=other" in it
            })
            assertFalse(lines.any { serverValue in it || mitigationValue in it })
        }
    }

    @Test
    fun `short known credential is closed and never echoed`() = runBlocking {
        val shortSecret = "q7"
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Server", shortSecret)
                .setHeader("CF-Mitigated", shortSecret)
                .setBody("""{"error":"limited"}"""),
        )

        MovieCardMetadataClient().fetch(
            session = session(credentials = Credentials("code", "u", shortSecret)),
            movieId = 42,
        )

        assertTrue(lines.any {
            "ev=exchange_response" in it &&
                "server=other" in it &&
                "cf_mitigated=other" in it
        })
        assertFalse(lines.any { shortSecret in it })
    }

    @Test
    fun `known credential values in allowlisted headers are rejected`() = runBlocking {
        val secretUser = "s3cr3t-user-xyz"
        val secretPass = "s3cr3t-pass-xyz"
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Server", secretUser)
                .setHeader("CF-Mitigated", secretPass)
                .setBody("""{"error":"limited"}"""),
        )

        MovieCardMetadataClient().fetch(
            session = session(credentials = Credentials("code", secretUser, secretPass)),
            movieId = 42,
        )

        assertTrue(lines.any {
            "ev=exchange_response" in it &&
                "server=other" in it &&
                "cf_mitigated=other" in it
        })
        assertFalse(lines.any { secretUser in it || secretPass in it })
    }

    @Test
    fun `multi hop redirect reports present absent restored credentials`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(302).setHeader(
                "Location",
                "/step2/player_api.php?action=get_vod_info",
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(302).setHeader(
                "Location",
                "/player_api.php?username=user&password=pass&action=get_vod_info",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"info":{}}"""))

        assertTrue(MovieCardMetadataClient().fetch(session(), 42).succeeded)
        assertEquals(3, server.requestCount)
        assertTrue(lines.any {
            "ev=exchange_request" in it && "x=0" in it &&
                "credentials_present=true" in it &&
                "username_changed=false" in it &&
                "password_changed=false" in it
        })
        assertTrue(lines.any {
            "ev=exchange_request" in it && "x=1" in it &&
                "credentials_present=false" in it &&
                "username_missing=true" in it &&
                "password_missing=true" in it &&
                "username_changed=true" in it &&
                "password_changed=true" in it
        })
        assertTrue(lines.any {
            "ev=exchange_request" in it && "x=2" in it &&
                "credentials_present=true" in it &&
                "username_equal=true" in it &&
                "password_equal=true" in it &&
                "username_changed=true" in it &&
                "password_changed=true" in it
        })
    }

    @Test
    fun `missing credentials are reported without inventing values`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"info":{}}"""))
        val empty = Credentials("", "", "")
        MovieCardMetadataClient().fetch(
            session = session(credentials = empty),
            movieId = 7,
        )

        assertTrue(lines.any {
            "ev=exchange_request" in it &&
                "credentials_present=false" in it &&
                "username_missing=true" in it &&
                "password_missing=true" in it
        })
    }

    @Test
    fun `cancellation is observed and does not change the request path`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"info":{}}""")
                .setBodyDelay(3, TimeUnit.SECONDS),
        )
        val job = launch(Dispatchers.IO) {
            runCatching { MovieCardMetadataClient().fetch(session(), 42) }
        }
        withTimeout(5_000) {
            while (server.requestCount == 0) delay(20)
        }
        job.cancel()
        job.join()

        assertTrue(lines.any { "ev=exchange_terminal" in it && "outcome=CANCELED" in it })
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `throwing diagnostic sink stays inert for a successful request`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"info":{}}"""))
        ProviderRequestTrace.sink = { throw IllegalStateException("sink failure") }

        val result = MovieCardMetadataClient().fetch(session(), 42)

        assertTrue(result.succeeded)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `secret endpoint and credentials never appear in trace`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"info":{}}"""))
        val session = session(
            portal = server.url("/r28-secret-path").toString().trimEnd('/'),
            credentials = Credentials("secret-code", "secret-user", "secret-pass"),
        )
        MovieCardMetadataClient().fetch(session, 42)

        assertTrue(lines.isNotEmpty())
        assertFalse(lines.any { "r28-secret-path" in it })
        assertFalse(lines.any { "secret-code" in it || "secret-user" in it || "secret-pass" in it })
        assertFalse(lines.any { "127.0.0.1" in it || "localhost" in it })
    }

    @Test
    fun `catalog pair keeps two requests and unchanged parsing`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val action = request.requestUrl?.queryParameter("action")
                return when (action) {
                    "get_series_categories" -> MockResponse().setResponseCode(200).setBody(
                        """[{"category_id":"1","category_name":"Drama"}]""",
                    )
                    "get_series" -> MockResponse().setResponseCode(200).setBody(
                        """[{"series_id":7,"name":"Series","category_id":"1","cover":"cover.jpg"}]""",
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val catalog = XtreamClient().catalog(session(), ContentType.SERIES)

        assertEquals(2, server.requestCount)
        assertEquals(1, catalog.categories.size)
        assertEquals(1, catalog.items.size)
        assertEquals(2, lines.count { "ev=exchange_request" in it })
    }

    private fun session(
        portal: String = server.url("/").toString().trimEnd('/'),
        credentials: Credentials = Credentials("code", "user", "pass"),
    ) = AuthenticatedSession(
        portal = PortalConfig(portal, PortalConfig.Source.ACCESS_CODE),
        credentials = credentials,
        account = AccountInfo(
            username = credentials.username,
            status = "Active",
            expiresAtEpochSeconds = null,
            activeConnections = 0,
            maxConnections = 1,
            isTrial = false,
        ),
    )
}
