package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
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
            items = (1..HOME_CATALOG_LIMIT + 1).map { id ->
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


    @Test
    fun `bounded top selection preserves the accepted stable catalog order`() {
        val items = (1..900).map { id ->
            movie(
                id = id,
                addedAt = (id % 41).toLong(),
                rating = when {
                    id % 17 == 0 -> null
                    id % 13 == 0 -> "invalid"
                    id % 11 == 0 -> "7.0"
                    id % 7 == 0 -> "9.0"
                    else -> (id % 5).toDouble().toString()
                },
            )
        }
        val catalog = Catalog(categories = emptyList(), items = items)
        val favorites = setOf("MOVIE:3", "MOVIE:500")
        val history = listOf(movieHistory(streamId = 17))
        val actual = compactHomeCatalogForHome(catalog, favorites, history)
        val expected = referenceCompact(catalog, favorites, history)
        assertEquals(expected.map(ContentItem::id), actual.items.map(ContentItem::id))
        assertEquals(expected.map(ContentItem::rating), actual.items.map(ContentItem::rating))
    }

    private fun referenceCompact(
        catalog: Catalog,
        favorites: Set<String>,
        history: List<HistoryEntry>,
    ): List<ContentItem> {
        if (catalog.items.size <= HOME_CATALOG_LIMIT) return catalog.items
        val pinned = favorites + history.asSequence().mapNotNull(HistoryEntry::catalogPinnedKey).toSet()
        return buildList {
            addAll(catalog.items.filter { "${it.type.name}:${it.id}" in pinned })
            addAll(
                catalog.items.asSequence()
                    .sortedByDescending { it.addedAtEpochSeconds ?: 0L }
                    .take(HOME_RECENT_LIMIT),
            )
            addAll(
                catalog.items.asSequence()
                    .filter { it.rating?.toDoubleOrNull() != null }
                    .sortedByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }
                    .take(HOME_RATED_LIMIT),
            )
        }.distinctBy { "${it.type.name}:${it.id}" }.take(HOME_CATALOG_LIMIT)
    }

    private fun movieHistory(streamId: Int): HistoryEntry = HistoryEntry(
        key = "MOVIE:$streamId",
        title = "Movie",
        posterUrl = null,
        streamKind = "movie",
        streamId = streamId,
        extension = "mp4",
        isLive = false,
        positionMs = 10L,
        durationMs = 100L,
        updatedAtEpochMs = 1L,
    )

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

    private fun movie(id: Int, addedAt: Long, rating: String? = null): ContentItem = ContentItem(
        id = id,
        name = "Movie $id",
        categoryId = "movie",
        type = ContentType.MOVIE,
        posterUrl = null,
        rating = rating,
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
