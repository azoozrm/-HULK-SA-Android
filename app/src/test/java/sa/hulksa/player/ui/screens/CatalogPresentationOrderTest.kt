package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import sa.hulksa.player.data.ProfileContentSearchHistoryEntry
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class CatalogPresentationOrderTest {
    @Test
    fun `mixed catalog moves every channel after every VOD item without reordering groups`() {
        val items = listOf(
            live(1),
            movie(2),
            series(3),
            live(4),
            movie(5),
        )

        val ordered = orderMixedContentVodFirst(items) { it.type }

        assertEquals(listOf(2, 3, 5, 1, 4), ordered.map { it.id })
    }

    @Test
    fun `single-type lists stay in their existing order`() {
        val movies = listOf(movie(2), movie(1), movie(3))

        val ordered = orderMixedContentVodFirst(movies) { it.type }

        assertSame(movies, ordered)
        assertEquals(listOf(2, 1, 3), ordered.map { it.id })
    }

    @Test
    fun `saved history uses the same stable VOD-first presentation rule`() {
        val history = listOf(
            history(11, ContentType.LIVE),
            history(12, ContentType.SERIES),
            history(13, ContentType.LIVE),
            history(14, ContentType.MOVIE),
        )

        val ordered = orderMixedContentVodFirst(history) { it.contentType }

        assertEquals(listOf(12, 14, 11, 13), ordered.map { it.contentId })
        assertEquals(listOf(12, 14, 11, 13), ordered.map { it.stableKey }.map { it.substringAfter(':').toInt() })
    }

    private fun movie(id: Int) = ContentItem(
        id = id,
        name = "Movie $id",
        categoryId = "movies",
        type = ContentType.MOVIE,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = "mp4",
    )

    private fun series(id: Int) = movie(id).copy(type = ContentType.SERIES, name = "Series $id")

    private fun live(id: Int) = movie(id).copy(type = ContentType.LIVE, name = "Channel $id")

    private fun history(id: Int, type: ContentType) = ProfileContentSearchHistoryEntry(
        contentId = id,
        contentType = type,
        title = "Item $id",
        posterUrl = null,
        year = null,
        updatedAtEpochMs = 0L,
    )
}
