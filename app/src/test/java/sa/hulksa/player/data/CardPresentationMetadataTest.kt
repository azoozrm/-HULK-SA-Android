package sa.hulksa.player.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardPresentationMetadataTest {
    private val portal = "http://example.test:8080"

    @Test
    fun `movie presentation keeps real plot genre and normalized backdrop`() {
        val root = JSONObject(
            """{"info":{"plot":"حبكة حقيقية","genre":"دراما","backdrop_path":"images/back.jpg"}}""",
        )
        val presentation = cardPresentationMetadataFrom(
            sources = listOf(root.optJSONObject("info"), root.optJSONObject("movie_data"), root),
            portalBaseUrl = portal,
        )
        assertEquals("حبكة حقيقية", presentation.plot)
        assertEquals("دراما", presentation.genre)
        assertEquals("http://example.test:8080/images/back.jpg", presentation.artworkUrl)
    }

    @Test
    fun `movie_data and description fall back when info omits fields`() {
        val root = JSONObject(
            """{"info":{},"movie_data":{"plot":"من وصف الفلم","movie_image":"https://cdn.test/p.jpg"}}""",
        )
        val presentation = cardPresentationMetadataFrom(
            sources = listOf(root.optJSONObject("info"), root.optJSONObject("movie_data"), root),
            portalBaseUrl = portal,
        )
        assertEquals("من وصف الفلم", presentation.plot)
        assertEquals("https://cdn.test/p.jpg", presentation.artworkUrl)
        assertNull(presentation.genre)
    }

    @Test
    fun `series presentation uses info then root and only real values`() {
        val root = JSONObject(
            """{"info":{"description":"وصف المسلسل","genre":"غموض","backdrop_path":["images/s1.jpg","images/s2.jpg"]}}""",
        )
        val presentation = cardPresentationMetadataFrom(
            sources = listOf(root.optJSONObject("info") ?: root, root),
            portalBaseUrl = portal,
        )
        assertEquals("وصف المسلسل", presentation.plot)
        assertEquals("غموض", presentation.genre)
        assertEquals("http://example.test:8080/images/s1.jpg", presentation.artworkUrl)
    }

    @Test
    fun `blank null and empty values never produce presentation data`() {
        val root = JSONObject(
            """{"info":{"plot":"  ","description":"null","genre":"","backdrop_path":"null","movie_image":" "}}""",
        )
        val presentation = cardPresentationMetadataFrom(
            sources = listOf(root.optJSONObject("info"), root),
            portalBaseUrl = portal,
        )
        assertNull(presentation.plot)
        assertNull(presentation.genre)
        assertNull(presentation.artworkUrl)
    }
}
