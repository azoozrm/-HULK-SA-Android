package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
