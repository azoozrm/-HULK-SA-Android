package sa.hulksa.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class TvArtworkSnapshotTest {
    @Test
    fun `snapshot contains only history keys with real backdrops`() {
        val movie = item(1, ContentType.MOVIE, "https://cdn.example/movie.jpg")
        val series = item(2, ContentType.SERIES, "https://cdn.example/series.jpg")
        val unrelated = item(3, ContentType.MOVIE, "https://cdn.example/unrelated.jpg")

        val snapshot = tvLandscapeArtworkForHistory(
            historyKeys = listOf("MOVIE:1", "SERIES:2", "MOVIE:99"),
            movieCatalog = Catalog(emptyList(), listOf(movie, unrelated)),
            seriesCatalog = Catalog(emptyList(), listOf(series)),
        )

        assertEquals(
            mapOf(
                "MOVIE:1" to "https://cdn.example/movie.jpg",
                "SERIES:2" to "https://cdn.example/series.jpg",
            ),
            snapshot,
        )
    }

    @Test
    fun `empty history skips the full catalog scan`() {
        val snapshot = tvLandscapeArtworkForHistory(
            historyKeys = emptyList(),
            movieCatalog = Catalog(emptyList(), listOf(item(1, ContentType.MOVIE, "x"))),
            seriesCatalog = Catalog(emptyList(), listOf(item(2, ContentType.SERIES, "y"))),
        )

        assertTrue(snapshot.isEmpty())
    }

    private fun item(id: Int, type: ContentType, backdrop: String): ContentItem = ContentItem(
        id = id,
        name = "Item $id",
        categoryId = "all",
        type = type,
        posterUrl = null,
        rating = "8.0",
        year = "2026",
        containerExtension = "mp4",
        addedAtEpochSeconds = id.toLong(),
        backdropUrl = backdrop,
    )
}
