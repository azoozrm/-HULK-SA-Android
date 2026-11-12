package sa.hulksa.player.data

import java.util.Collections
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

/** R32: catalog categories and items are fetched sequentially, not as concurrent siblings. */
class XtreamCatalogSequentialTest {
    private lateinit var server: MockWebServer
    private val arrivals = Collections.synchronizedList(mutableListOf<Pair<String, Long>>())

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
    fun `categories response completes before the items request starts`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val action = request.requestUrl?.queryParameter("action").orEmpty()
                arrivals += action to System.nanoTime()
                return when (action) {
                    "get_vod_categories" -> {
                        Thread.sleep(300L)
                        MockResponse()
                            .setResponseCode(200)
                            .setBody("""[{"category_id":"1","category_name":"All"}]""")
                    }
                    "get_vod_streams" -> MockResponse()
                        .setResponseCode(200)
                        .setBody("""[{"stream_id":1,"name":"one"}]""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val session = AuthenticatedSession(
            portal = PortalConfig(server.url("/").toString(), PortalConfig.Source.ACCESS_CODE),
            credentials = Credentials("CODE", "user", "pass"),
            account = AccountInfo("user", "Active", null, 1, 1, false),
        )
        val catalog = XtreamClient().catalog(session, ContentType.MOVIE)

        assertEquals(listOf("All"), catalog.categories.map { it.name })
        assertEquals(listOf("one"), catalog.items.map { it.name })
        synchronized(arrivals) {
            assertEquals(
                listOf("get_vod_categories", "get_vod_streams"),
                arrivals.map { it.first },
            )
            val categoriesAt = arrivals.first { it.first == "get_vod_categories" }.second
            val streamsAt = arrivals.first { it.first == "get_vod_streams" }.second
            assertTrue(
                "items arrived ${(streamsAt - categoriesAt) / 1_000_000} ms after categories",
                streamsAt - categoriesAt >= 200_000_000L,
            )
        }
    }
}
