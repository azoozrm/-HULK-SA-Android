package sa.hulksa.player.data

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

class ProviderRequestTraceNetworkTest {
    private lateinit var server: MockWebServer
    private val lines = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        ProviderRequestTrace.resetForTest()
        lines.clear()
        ProviderRequestTrace.sink = { line -> lines += line }
    }

    @After
    fun tearDown() {
        ProviderRequestTrace.sink = null
        ProviderRequestTrace.resetForTest()
        runCatching { server.shutdown() }
    }

    private fun dispatch(byAction: Map<String, MockResponse>) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                byAction[request.requestUrl?.queryParameter("action")]
                    ?: MockResponse().setResponseCode(404)
        }
    }

    private fun catalogResponses(categories: MockResponse, items: MockResponse) = dispatch(
        mapOf("get_vod_categories" to categories, "get_vod_streams" to items),
    )

    private fun session(): AuthenticatedSession = AuthenticatedSession(
        portal = PortalConfig(server.url("/").toString(), PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials("CODE", "trace-sentinel-user", "trace-sentinel-pass"),
        account = AccountInfo("user", "Active", null, 1, 1, false),
    )

    private fun terminals(): List<String> = lines.filter { it.contains("ev=terminal") }

    @Test
    fun `successful catalog records both actions with one terminal each`() {
        catalogResponses(
            MockResponse().setResponseCode(200)
                .setBody("""[{"category_id":"1","category_name":"All"}]"""),
            MockResponse().setResponseCode(200)
                .setBody("""[{"stream_id":3,"name":"A","category_id":"1"}]"""),
        )

        runBlocking { XtreamClient().catalog(session(), ContentType.MOVIE) }

        assertEquals(2, lines.count { it.contains("ev=queued") })
        assertEquals(2, lines.count { it.contains("ev=call") })
        assertEquals(2, terminals().size)
        assertTrue(terminals().all { it.contains("outcome=SUCCESS") && it.contains("code=200") })
        assertTrue(terminals().all { it.contains("shape=ARRAY_START") })
        assertTrue(terminals().any { it.contains("action=get_vod_categories") })
        assertTrue(terminals().any { it.contains("action=get_vod_streams") })
        assertTrue(ProviderRequestTrace.idle())
    }

    @Test
    fun `http 200 html keeps the existing classification and records an html shape`() {
        catalogResponses(
            MockResponse().setResponseCode(200).setBody("<!doctype html><html>blocked</html>"),
            MockResponse().setResponseCode(200)
                .setBody("""[{"stream_id":3,"name":"A","category_id":"1"}]"""),
        )

        val error = assertThrows(XtreamException.ServiceBlocked::class.java) {
            runBlocking { XtreamClient().catalog(session(), ContentType.MOVIE) }
        }

        assertEquals("html-doctype", error.category)
        val blocked = terminals().first { it.contains("action=get_vod_categories") }
        assertTrue(blocked.contains("outcome=BLOCKED"))
        assertTrue(blocked.contains("code=200"))
        assertTrue(blocked.contains("shape=HTML_START"))
        assertTrue(blocked.contains("category=html-doctype"))
    }

    @Test
    fun `json carrying the marker keeps the marker category but records an array shape`() {
        catalogResponses(
            MockResponse().setResponseCode(200)
                .setBody("""[{"category_id":"1","category_name":"Cloudflare"}]"""),
            MockResponse().setResponseCode(200)
                .setBody("""[{"stream_id":3,"name":"A","category_id":"1"}]"""),
        )

        val error = assertThrows(XtreamException.ServiceBlocked::class.java) {
            runBlocking { XtreamClient().catalog(session(), ContentType.MOVIE) }
        }

        assertEquals("cloudflare", error.category)
        val blocked = terminals().first { it.contains("action=get_vod_categories") }
        assertTrue(blocked.contains("category=cloudflare"))
        assertTrue(blocked.contains("shape=ARRAY_START"))
    }

    @Test
    fun `non-2xx cloudflare records the status and a non-html shape`() {
        catalogResponses(
            MockResponse().setResponseCode(503).setBody("cloudflare challenge"),
            MockResponse().setResponseCode(200)
                .setBody("""[{"stream_id":3,"name":"A","category_id":"1"}]"""),
        )

        val error = assertThrows(XtreamException.ServiceBlocked::class.java) {
            runBlocking { XtreamClient().catalog(session(), ContentType.MOVIE) }
        }

        assertEquals(503, error.statusCode)
        val blocked = terminals().first { it.contains("action=get_vod_categories") }
        assertTrue(blocked.contains("outcome=BLOCKED"))
        assertTrue(blocked.contains("code=503"))
        assertTrue(blocked.contains("category=cloudflare"))
        assertTrue(blocked.contains("shape=OTHER"))
    }

    @Test
    fun `movie metadata client records its own success and challenge outcomes`() {
        dispatch(
            mapOf(
                "get_vod_info" to MockResponse().setResponseCode(200)
                    .setBody("""{"info":{"duration":"2h"},"movie_data":{}}"""),
            ),
        )
        val ok = runBlocking {
            MovieCardMetadataClient().fetch(session(), 7, ProviderSurface.HERO)
        }
        assertTrue(ok.succeeded)
        val success = terminals().first { it.contains("action=get_vod_info") }
        assertTrue(success.contains("outcome=SUCCESS"))
        assertTrue(success.contains("surface=HERO"))
        assertTrue(success.contains("shape=OBJECT_START"))

        lines.clear()
        dispatch(
            mapOf(
                "get_vod_info" to MockResponse().setResponseCode(200)
                    .setBody("<!doctype html><html>blocked</html>"),
            ),
        )
        val blocked = runBlocking {
            MovieCardMetadataClient().fetch(session(), 8, ProviderSurface.SERIES_CARD)
        }
        assertFalse(blocked.succeeded)
        val terminal = terminals().single()
        assertTrue(terminal.contains("outcome=BLOCKED"))
        assertTrue(terminal.contains("category=html-doctype"))
        assertTrue(terminal.contains("shape=HTML_START"))
        assertTrue(terminal.contains("surface=SERIES_CARD"))
        assertTrue(ProviderRequestTrace.idle())
    }

    @Test
    fun `cancellation settles outstanding counters and reports one terminal`() {
        dispatch(
            mapOf(
                "get_vod_categories" to MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE),
                "get_vod_streams" to MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE),
            ),
        )

        runBlocking {
            val job = launch {
                runCatching { XtreamClient().catalog(session(), ContentType.MOVIE) }
            }
            delay(300)
            job.cancel()
            job.join()
        }

        val cancelTerminals = terminals().filter { it.contains("outcome=CANCELED") }
        assertTrue(cancelTerminals.isNotEmpty())
        assertTrue(ProviderRequestTrace.idle())
    }

    @Test
    fun `diagnostic lines never contain credentials or urls`() {
        catalogResponses(
            MockResponse().setResponseCode(200)
                .setBody("""[{"category_id":"1","category_name":"All"}]"""),
            MockResponse().setResponseCode(200)
                .setBody("""[{"stream_id":3,"name":"A","category_id":"1"}]"""),
        )

        runBlocking { XtreamClient().catalog(session(), ContentType.MOVIE) }

        val joined = lines.joinToString("\n")
        assertTrue(lines.isNotEmpty())
        assertFalse(joined.contains("trace-sentinel-user"))
        assertFalse(joined.contains("trace-sentinel-pass"))
        assertFalse(joined.contains("player_api"))
        assertFalse(joined.contains("http://"))
        assertFalse(joined.contains("https://"))
        assertFalse(joined.contains("username="))
        assertFalse(joined.contains("password="))
    }
}
