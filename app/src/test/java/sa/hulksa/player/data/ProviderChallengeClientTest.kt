package sa.hulksa.player.data

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

/** R32 production clients: ordinary JSON with "cloudflare" parses; JSON 429 remains a 429. */
class ProviderChallengeClientTest {
    private lateinit var server: MockWebServer
    private lateinit var admission: ProviderAdmission

    private class FakeClock {
        val nanos = AtomicLong(System.currentTimeMillis() * 1_000_000L)
        fun nowNs(): Long = nanos.get()
        fun nowMs(): Long = nowNs() / 1_000_000L
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val clock = FakeClock()
        admission = ProviderAdmission(
            nowNs = clock::nowNs,
            nowWallMs = clock::nowMs,
            sleepNs = { },
            ownerIsCurrent = { true },
        )
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun session(): AuthenticatedSession = AuthenticatedSession(
        portal = PortalConfig(server.url("/").toString(), PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials("CODE", "user", "pass"),
        account = AccountInfo("user", "Active", null, 1, 1, false),
    )

    private fun gate() = ProviderDispatchGate()

    @Test
    fun `movie metadata json containing cloudflare succeeds`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"info":{"plot":"cloudflare edge","genre":"Drama"}}"""),
        )

        val result = MovieCardMetadataClient(admission, gate()).fetch(session(), 42)

        assertTrue(result.succeeded)
        assertEquals("cloudflare edge", result.plot)
        assertEquals("Drama", result.genre)
    }

    @Test
    fun `series metadata json containing cloudflare succeeds`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"info":{"name":"cloudflare series","cover":"x"}}"""),
        )

        val result = SeriesCardMetadataClient(admission, gate()).fetch(session(), 9)

        assertTrue(result.succeeded)
    }

    @Test
    fun `content details json containing cloudflare reaches parsing`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"info":{"name":"cloudflare movie","plot":"cloudflare plot"}}"""),
        )

        val details = XtreamClient(admission, gate()).contentDetails(session(), 7)

        assertEquals("cloudflare plot", details.plot)
    }

    @Test
    fun `catalog array containing cloudflare reaches parsing`() = runBlocking {
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(
                request: okhttp3.mockwebserver.RecordedRequest,
            ): MockResponse = when (request.requestUrl?.queryParameter("action")) {
                "get_vod_categories" -> MockResponse()
                    .setResponseCode(200)
                    .setBody("""[{"category_id":"1","category_name":"All"}]""")
                "get_vod_streams" -> MockResponse()
                    .setResponseCode(200)
                    .setBody("""[{"stream_id":1,"name":"cloudflare channel"}]""")
                else -> MockResponse().setResponseCode(404)
            }
        }

        val catalog = XtreamClient(admission, gate()).catalog(session(), ContentType.MOVIE)

        assertEquals(listOf("cloudflare channel"), catalog.items.map { it.name })
    }

    @Test
    fun `json 429 containing cloudflare remains a 429 with a cooldown`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "10")
                .setBody("""{"error":"cloudflare rate limit"}"""),
        )
        val session = session()

        val failure = assertThrows(XtreamException.Http::class.java) {
            runBlocking { XtreamClient(admission, gate()).contentDetails(session, 7) }
        }

        assertEquals(429, failure.statusCode)
        assertNotNull(admission.cooldownDeadlineNs(session))
    }

    @Test
    fun `metadata json 429 containing cloudflare fails and records a cooldown`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "10")
                .setBody("""{"error":"cloudflare rate limit"}"""),
        )
        val session = session()

        val result = MovieCardMetadataClient(admission, gate()).fetch(session, 42)

        assertFalse(result.succeeded)
        assertNotNull(admission.cooldownDeadlineNs(session))
    }

    @Test
    fun `successful html challenge remains rejected`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("<!doctype html><html><title>cloudflare</title></html>"),
        )

        val result = MovieCardMetadataClient(admission, gate()).fetch(session(), 42)

        assertFalse(result.succeeded)
    }

    @Test
    fun `successful malformed json remains rejected`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"info":"""),
        )

        val result = MovieCardMetadataClient(admission, gate()).fetch(session(), 42)

        assertFalse(result.succeeded)
    }

    @Test
    fun `successful authentication json containing cloudflare still authenticates`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"user_info":{"auth":1,"status":"Active","exp_date":null,""" +
                        """"username":"user","active_cons":"0","max_connections":"1",""" +
                        """"is_trial":"0"},"server_info":{"cloudflare":"edge"}}""",
                ),
        )

        val session = XtreamClient(admission, gate()).authenticate(
            PortalConfig(server.url("/").toString(), PortalConfig.Source.ACCESS_CODE),
            Credentials("CODE", "user", "pass"),
        )

        assertEquals("user", session.account.username)
    }
}
