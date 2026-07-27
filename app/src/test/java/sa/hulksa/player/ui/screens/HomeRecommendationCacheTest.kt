package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class HomeRecommendationCacheTest {
    @Test
    fun unchangedInputsReuseTheCachedHomeSnapshot() {
        val state = testState()
        val store = NavigationMemoryStore()
        val first = store.homeContent(state)
        val second = store.homeContent(state)
        assertSame(first, second)
    }

    @Test
    fun changingFavoritesInvalidatesAndRebuildsRecommendations() {
        val state = testState()
        val store = NavigationMemoryStore()
        val before = store.homeContent(state)
        assertTrue(before.becauseYouWatched.isEmpty())

        val after = store.homeContent(state.copy(favorites = setOf("MOVIE:1")))

        assertNotSame(before, after)
        assertEquals(setOf(1, 2), after.becauseYouWatched.map(ContentItem::id).toSet())
    }

    private fun testState(): HulkUiState {
        val catalog = Catalog(
            categories = emptyList(),
            items = listOf(
                movie(1, "action", "7.0", 300L),
                movie(2, "action", "9.0", 200L),
                movie(3, "drama", "10.0", 100L),
            ),
        )
        return HulkUiState(
            catalogs = mapOf(ContentType.MOVIE to catalog),
            favorites = emptySet(),
            history = emptyList(),
        )
    }

    private fun movie(id: Int, categoryId: String, rating: String, added: Long): ContentItem = ContentItem(
        id = id,
        name = "Movie $id",
        categoryId = categoryId,
        type = ContentType.MOVIE,
        posterUrl = null,
        rating = rating,
        year = null,
        containerExtension = "mp4",
        addedAtEpochSeconds = added,
        genre = categoryId,
    )
}
