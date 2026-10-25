package sa.hulksa.player.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.HistoryEntry

class HomeCatalogCompactionTest {
    @Test
    fun `watched episode pins its parent series rather than an unrelated episode-id series`() {
        val catalog = seriesCatalog()

        val compacted = compactHomeCatalogForHome(
            catalog = catalog,
            favorites = emptySet(),
            history = listOf(seriesHistory(episodeId = 9001, parentContentId = 42)),
        )

        val ids = compacted.items.map(ContentItem::id).toSet()
        assertTrue(42 in ids)
        assertFalse(9001 in ids)
    }

    @Test
    fun `missing parent metadata never treats the episode id as a series id`() {
        val compacted = compactHomeCatalogForHome(
            catalog = seriesCatalog(),
            favorites = emptySet(),
            history = listOf(seriesHistory(episodeId = 9001, parentContentId = null)),
        )

        val ids = compacted.items.map(ContentItem::id).toSet()
        assertFalse(42 in ids)
        assertFalse(9001 in ids)
    }

    @Test
    fun `small catalog keeps the exact existing instance`() {
        val catalog = Catalog(
            categories = emptyList(),
            items = listOf(series(1, addedAt = 3L), series(42, addedAt = 2L), series(9001, addedAt = 1L)),
        )

        val compacted = compactHomeCatalogForHome(
            catalog = catalog,
            favorites = emptySet(),
            history = listOf(seriesHistory(episodeId = 9001, parentContentId = 42)),
        )

        assertSame(catalog, compacted)
    }

    @Test
    fun `movie history continues to pin its own catalog item`() {
        val catalog = Catalog(
            categories = emptyList(),
            items = (1..HOME_CATALOG_LIMIT).map { id ->
                movie(id, addedAt = if (id == 17) 0L else 1_000L + id)
            },
        )
        val history = HistoryEntry(
            key = "MOVIE:17",
            title = "Movie",
            posterUrl = null,
            streamKind = "movie",
            streamId = 17,
            extension = "mp4",
            isLive = false,
            positionMs = 10L,
            durationMs = 100L,
            updatedAtEpochMs = 1L,
        )

        val compacted = compactHomeCatalogForHome(catalog, favorites = emptySet(), history = listOf(history))

        assertTrue(compacted.items.any { it.id == 17 })
    }

    private fun seriesCatalog(): Catalog {
        val regular = (1..HOME_CATALOG_LIMIT).map { id ->
            series(id, addedAt = if (id == 42) 0L else 1_000L + id)
        }
        return Catalog(
            categories = emptyList(),
            items = regular + series(9001, addedAt = 0L),
        )
    }

    private fun series(id: Int, addedAt: Long): ContentItem = ContentItem(
        id = id,
        name = "Series $id",
        categoryId = "series",
        type = ContentType.SERIES,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = "mp4",
        addedAtEpochSeconds = addedAt,
    )

    private fun movie(id: Int, addedAt: Long): ContentItem = ContentItem(
        id = id,
        name = "Movie $id",
        categoryId = "movie",
        type = ContentType.MOVIE,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = "mp4",
        addedAtEpochSeconds = addedAt,
    )

    private fun seriesHistory(episodeId: Int, parentContentId: Int?): HistoryEntry = HistoryEntry(
        key = "SERIES:$episodeId",
        title = "Episode",
        posterUrl = null,
        streamKind = "series",
        streamId = episodeId,
        extension = "mp4",
        isLive = false,
        positionMs = 10L,
        durationMs = 100L,
        updatedAtEpochMs = 1L,
        parentContentId = parentContentId,
    )
}
