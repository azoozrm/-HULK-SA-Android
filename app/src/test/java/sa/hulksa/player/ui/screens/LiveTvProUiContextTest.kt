package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sa.hulksa.player.data.providerStableIdentity
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class LiveTvProUiContextTest {
    @Test
    fun mainFavoritesAndRecentMapToPlayerContexts() {
        assertEquals(
            LIVE_TV_PRO_CONTEXT_FAVORITES,
            liveTvProMainCategoryToContext(LIVE_TV_PRO_MAIN_FAVORITES_CATEGORY),
        )
        assertEquals(
            LIVE_TV_PRO_CONTEXT_RECENT,
            liveTvProMainCategoryToContext(LIVE_TV_PRO_MAIN_CONTINUE_CATEGORY),
        )
        assertEquals(
            LIVE_TV_PRO_CONTEXT_RECENT,
            liveTvProMainCategoryToContext(LIVE_TV_PRO_MAIN_RECENT_CATEGORY),
        )
        assertEquals(LIVE_TV_PRO_CONTEXT_ALL, liveTvProMainCategoryToContext(null))
        assertEquals("news", liveTvProMainCategoryToContext("news"))
    }

    @Test
    fun browserRestoresFavoritesOnlyWhenCurrentChannelIsStillFavorite() {
        assertEquals(
            LIVE_TV_PRO_BROWSER_FAVORITES_CATEGORY,
            liveTvProInitialBrowserCategory(
                launchContext = LIVE_TV_PRO_CONTEXT_FAVORITES,
                currentCategoryId = "news",
                currentStreamId = 42,
                favoriteIds = setOf(42),
                recentIds = emptyList(),
            ),
        )
        assertEquals(
            "news",
            liveTvProInitialBrowserCategory(
                launchContext = LIVE_TV_PRO_CONTEXT_FAVORITES,
                currentCategoryId = "news",
                currentStreamId = 42,
                favoriteIds = emptySet(),
                recentIds = emptyList(),
            ),
        )
    }

    @Test
    fun browserRestoresRecentAndAllContextsDeterministically() {
        assertEquals(
            LIVE_TV_PRO_BROWSER_CONTINUE_CATEGORY,
            liveTvProInitialBrowserCategory(
                launchContext = LIVE_TV_PRO_CONTEXT_RECENT,
                currentCategoryId = "sports",
                currentStreamId = 7,
                favoriteIds = emptySet(),
                recentIds = listOf(3, 7, 1),
            ),
        )
        assertNull(
            liveTvProInitialBrowserCategory(
                launchContext = LIVE_TV_PRO_CONTEXT_ALL,
                currentCategoryId = "sports",
                currentStreamId = 7,
                favoriteIds = emptySet(),
                recentIds = emptyList(),
            ),
        )
    }

    @Test
    fun staleCategoryContextFallsBackToCurrentChannelsRealCategory() {
        assertEquals(
            "news",
            liveTvProInitialBrowserCategory(
                launchContext = "sports",
                currentCategoryId = "news",
                currentStreamId = 8,
                favoriteIds = emptySet(),
                recentIds = emptyList(),
            ),
        )
    }

    @Test
    fun recentOverlayHasOneStableIdentityAndPreservesRecentOrder() {
        val items = listOf(
            liveItem(id = 1, name = "One", categoryId = "news"),
            liveItem(id = 2, name = "Two", categoryId = "sports"),
            liveItem(id = 3, name = "Three", categoryId = "kids"),
        )

        val overlaid = liveTvProRecentOverlayItems(
            items = items,
            recentChannelIds = listOf(3, 1, 3),
        )
        val recent = overlaid.filter { it.categoryId == LIVE_TV_PRO_MAIN_RECENT_CATEGORY }

        assertEquals(overlaid.size, overlaid.distinctBy { it.providerStableIdentity() }.size)
        assertEquals(listOf(3, 1), recent.map(ContentItem::id))
        assertEquals(listOf(2, 3, 1), overlaid.map(ContentItem::id))
    }

    @Test
    fun recentOverlayKeepsFirstCanonicalProviderDuplicate() {
        val overlaid = liveTvProRecentOverlayItems(
            items = listOf(
                liveItem(id = 7, name = "First", categoryId = "news"),
                liveItem(id = 7, name = "Duplicate", categoryId = "sports"),
                liveItem(id = 8, name = "Next", categoryId = "news"),
            ),
            recentChannelIds = emptyList(),
        )

        assertEquals(listOf(7, 8), overlaid.map(ContentItem::id))
        assertEquals("First", overlaid.first().name)
    }

    private fun liveItem(
        id: Int,
        name: String,
        categoryId: String,
    ): ContentItem = ContentItem(
        id = id,
        name = name,
        categoryId = categoryId,
        type = ContentType.LIVE,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = null,
    )
}
