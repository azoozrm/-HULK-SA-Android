package sa.hulksa.player.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

/**
 * The bounded card-metadata request already returns presentation artwork from the same
 * get_vod_info payload used for the quality/duration footer. These tests prove the provider
 * candidate is carried through the production client and normalized exactly like Details.
 */
class MovieCardMetadataClientTest {
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
    fun `movie presentation artwork rides the bounded get vod info request`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"info":{"plot":"حبكة","genre":"دراما","backdrop_path":"images/back.jpg"},"movie_data":{"duration_secs":5400}}""",
                ),
        )

        val result = MovieCardMetadataClient().fetch(session(), movieId = 42)

        assertTrue(result.succeeded)
        assertEquals("حبكة", result.plot)
        assertEquals("دراما", result.genre)
        assertEquals(5_400_000L, result.durationMs)
        assertEquals(server.url("/images/back.jpg").toString(), result.artworkUrl)

        val request = server.takeRequest()
        assertTrue(request.path.orEmpty().contains("action=get_vod_info"))
        assertTrue(request.path.orEmpty().contains("vod_id=42"))
    }

    @Test
    fun `movie image is used when backdrop path is absent`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"info":{},"movie_data":{"movie_image":"https://cdn.test/p.jpg"}}"""),
        )

        val result = MovieCardMetadataClient().fetch(session(), movieId = 7)

        assertTrue(result.succeeded)
        assertEquals("https://cdn.test/p.jpg", result.artworkUrl)
    }

    @Test
    fun `first backdrop candidate is normalized and later candidates are ignored`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"info":{"backdrop_path":["images/first.jpg","images/second.jpg"]}}""",
                ),
        )

        val result = MovieCardMetadataClient().fetch(session(), movieId = 8)

        assertTrue(result.succeeded)
        assertEquals(server.url("/images/first.jpg").toString(), result.artworkUrl)
    }

    @Test
    fun `invalid provider payload never invents artwork`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"info":{"plot":" ","backdrop_path":"null","movie_image":""}}"""),
        )

        val result = MovieCardMetadataClient().fetch(session(), movieId = 9)

        assertTrue(result.succeeded)
        assertNull(result.plot)
        assertNull(result.artworkUrl)
    }

    @Test
    fun `failed request keeps the honest no artwork result`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))

        val result = MovieCardMetadataClient().fetch(session(), movieId = 10)

        assertFalse(result.succeeded)
        assertNull(result.artworkUrl)
    }

    private fun session(): AuthenticatedSession = AuthenticatedSession(
        portal = PortalConfig(server.url("/").toString(), PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials("CODE", "user", "pass"),
        account = AccountInfo("user", "Active", null, 1, 1, false),
    )
}
