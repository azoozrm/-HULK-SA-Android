package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import sa.hulksa.player.CatalogFailure
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.MainDestination
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class LiveTvProDecorationStateTest {
    @Test
    fun `cached Live decoration never masks current query favorites loading or failures`() {
        val liveCatalog = Catalog(
            emptyList(),
            listOf(live(1, "One"), live(2, "Two")),
        )
        val oldState = HulkUiState(
            destination = MainDestination.LIVE,
            catalogs = mapOf(ContentType.LIVE to liveCatalog),
            selectedCategoryId = null,
            searchQuery = "old-query",
            favorites = setOf("LIVE:1"),
            loadingTypes = setOf(ContentType.LIVE),
            catalogFailures = mapOf(
                ContentType.LIVE to CatalogFailure("old failure", retryable = false),
            ),
        )
        val decorated = liveTvProDecoratedCatalog(
            liveCatalog = liveCatalog,
            selectedCategoryId = oldState.selectedCategoryId,
            recentChannelIds = emptyList(),
        )
        val currentState = oldState.copy(
            searchQuery = "new-query",
            favorites = setOf("LIVE:2"),
            loadingTypes = setOf(ContentType.MOVIE),
            catalogFailures = mapOf(
                ContentType.LIVE to CatalogFailure("new failure", retryable = true),
            ),
        )

        val presented = currentState.copy(
            catalogs = currentState.catalogs + (ContentType.LIVE to decorated),
        )

        assertEquals("new-query", presented.searchQuery)
        assertEquals(setOf("LIVE:2"), presented.favorites)
        assertEquals(setOf(ContentType.MOVIE), presented.loadingTypes)
        assertEquals("new failure", presented.catalogFailures[ContentType.LIVE]?.message)
        assertEquals(true, presented.catalogFailures[ContentType.LIVE]?.retryable)
        assertSame(decorated, presented.catalogs[ContentType.LIVE])
    }

    @Test
    fun `recent decoration keeps canonical order and replaces selected channel copies`() {
        val liveCatalog = Catalog(
            emptyList(),
            listOf(live(1, "One"), live(2, "Two"), live(3, "Three")),
        )

        val decorated = liveTvProDecoratedCatalog(
            liveCatalog = liveCatalog,
            selectedCategoryId = LIVE_TV_PRO_MAIN_RECENT_CATEGORY,
            recentChannelIds = listOf(3, 1),
        )

        assertEquals(listOf(2, 3, 1), decorated.items.map(ContentItem::id))
        assertEquals(
            LIVE_TV_PRO_MAIN_RECENT_CATEGORY,
            decorated.items.last().categoryId,
        )
    }

    private fun live(id: Int, name: String): ContentItem = ContentItem(
        id = id,
        name = name,
        categoryId = "news",
        type = ContentType.LIVE,
        posterUrl = null,
        rating = "8.0",
        year = null,
        containerExtension = "m3u8",
        addedAtEpochSeconds = id.toLong(),
    )
}
