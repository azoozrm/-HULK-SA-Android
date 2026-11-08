package sa.hulksa.player.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

class XtreamCatalogNetworkTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun http200CloudflareChallengeIsServiceBlocked() {
        enqueueCatalog(MockResponse().setResponseCode(200).setBody("cloudflare challenge"))

        val error = assertThrows(XtreamException.ServiceBlocked::class.java) {
            runBlocking { XtreamClient().catalog(session(), ContentType.MOVIE) }
        }

        assertEquals("cloudflare", error.category)
    }

    @Test
    fun http200LeadingWhitespaceHtmlIsServiceBlocked() {
        enqueueCatalog(
            MockResponse()
                .setResponseCode(200)
                .setBody(" ".repeat(600) + "<html>blocked</html>"),
        )

        val error = assertThrows(XtreamException.ServiceBlocked::class.java) {
            runBlocking { XtreamClient().catalog(session(), ContentType.MOVIE) }
        }

        assertEquals("html", error.category)
    }

    @Test
    fun http200HtmlAfterScanLimitIsServiceBlocked() {
        enqueueCatalog(
            MockResponse()
                .setResponseCode(200)
                .setBody(" ".repeat(65_536) + "<html>blocked</html>"),
        )

        val error = assertThrows(XtreamException.ServiceBlocked::class.java) {
            runBlocking { XtreamClient().catalog(session(), ContentType.MOVIE) }
        }

        assertEquals("html", error.category)
    }

    @Test
    fun http200ArrayAfterScanLimitStillLoads() {
        enqueueCatalog(
            MockResponse()
                .setResponseCode(200)
                .setBody(" ".repeat(65_536) + """[{"stream_id":3,"name":"after"}]"""),
        )

        val catalog = runBlocking { XtreamClient().catalog(session(), ContentType.MOVIE) }

        assertEquals(listOf(3), catalog.items.map { it.id })
    }

    @Test
    fun http200HtmlBelowScanLimitIsServiceBlocked() {
        enqueueCatalog(
            MockResponse()
                .setResponseCode(200)
                .setBody(" ".repeat(65_535) + "<html>blocked</html>"),
        )

        val error = assertThrows(XtreamException.ServiceBlocked::class.java) {
            runBlocking { XtreamClient().catalog(session(), ContentType.MOVIE) }
        }

        assertEquals("html", error.category)
    }

    @Test
    fun http200WhitespaceAtScanLimitIsInvalidResponse() {
        enqueueCatalog(MockResponse().setResponseCode(200).setBody(" ".repeat(65_536)))

        assertThrows(XtreamException.InvalidResponse::class.java) {
            runBlocking { XtreamClient().catalog(session(), ContentType.MOVIE) }
        }
    }

    @Test
    fun http200OrdinaryArrayStillLoads() {
        enqueueCatalog(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"stream_id":1,"name":"one"}]"""),
        )

        val catalog = runBlocking { XtreamClient().catalog(session(), ContentType.MOVIE) }

        assertEquals(listOf(1), catalog.items.map { it.id })
        assertEquals(listOf("one"), catalog.items.map { it.name })
    }

    @Test
    fun http200BomArrayStillLoads() {
        enqueueCatalog(
            MockResponse()
                .setResponseCode(200)
                .setBody("\uFEFF[{\"stream_id\":2,\"name\":\"bom\"}]"),
        )

        val catalog = runBlocking { XtreamClient().catalog(session(), ContentType.MOVIE) }

        assertEquals(listOf(2), catalog.items.map { it.id })
    }

    @Test
    fun non2xxChallengeIsServiceBlocked() {
        enqueueCatalog(
            MockResponse()
                .setResponseCode(403)
                .setBody("<html>blocked</html>"),
        )

        val error = assertThrows(XtreamException.ServiceBlocked::class.java) {
            runBlocking { XtreamClient().catalog(session(), ContentType.MOVIE) }
        }

        assertEquals("html", error.category)
    }

    @Test(timeout = 10_000)
    fun http200WhitespaceOnlyTerminatesAsInvalidResponse() {
        enqueueCatalog(MockResponse().setResponseCode(200).setBody(" ".repeat(600)))

        assertThrows(XtreamException.InvalidResponse::class.java) {
            runBlocking { XtreamClient().catalog(session(), ContentType.MOVIE) }
        }
    }

    @Test
    fun interruptedNestedCatalogBodyRemainsNetworkFailure() {
        val body = """[{"stream_id":1,"meta":{"x":"${"x".repeat(200_000)}"}}]"""
        enqueueCatalog(
            MockResponse()
                .setResponseCode(200)
                .setBody(body)
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )

        assertThrows(XtreamException.Network::class.java) {
            runBlocking { XtreamClient().catalog(session(), ContentType.MOVIE) }
        }
    }

    @Test
    fun interruptedKnownNestedCatalogBodyRemainsNetworkFailure() {
        val body = """[{"stream_id":1,"name":{"x":"${"x".repeat(200_000)}"}}]"""
        enqueueCatalog(
            MockResponse()
                .setResponseCode(200)
                .setBody(body)
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )

        assertThrows(XtreamException.Network::class.java) {
            runBlocking { XtreamClient().catalog(session(), ContentType.MOVIE) }
        }
    }

    @Test
    fun malformedCatalogJsonIsInvalidResponse() {
        enqueueCatalog(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"stream_id":1,""") ,
        )

        assertThrows(XtreamException.InvalidResponse::class.java) {
            runBlocking { XtreamClient().catalog(session(), ContentType.MOVIE) }
        }
    }

    @Test
    fun http200OversizedCatalogIsPayloadLimitExceeded() {
        val body = " ".repeat((XtreamJsonLimits.CATALOG.maxResponseBytes + 1L).toInt())
        enqueueCatalog(MockResponse().setResponseCode(200).setBody(body))

        assertThrows(XtreamException.PayloadLimitExceeded::class.java) {
            runBlocking { XtreamClient().catalog(session(), ContentType.MOVIE) }
        }
    }

    private fun enqueueCatalog(itemResponse: MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.requestUrl?.queryParameter("action")) {
                    "get_vod_categories" -> MockResponse()
                        .setResponseCode(200)
                        .setBody("""[{"category_id":"1","category_name":"All"}]""")
                    "get_vod_streams" -> itemResponse
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    private fun session(): AuthenticatedSession = AuthenticatedSession(
        portal = PortalConfig(server.url("/").toString(), PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials("CODE", "user", "pass"),
        account = AccountInfo("user", "Active", null, 1, 1, false),
    )
}
