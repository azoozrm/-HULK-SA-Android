package sa.hulksa.player.ui.screens

import kotlinx.coroutines.runBlocking
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
    fun unchangedInputsReuseTheCachedHomeSnapshot() = runBlocking {
        val state = testState()
        val store = NavigationMemoryStore()
        val input = state.homeModelInput()
        val first = store.homeModel(input).model
        val second = store.homeModel(input).model
        assertSame(first, second)
    }

    @Test
    fun changingFavoritesInvalidatesAndRebuildsRecommendations() = runBlocking {
        val state = testState()
        val store = NavigationMemoryStore()
        val before = store.homeModel(state.homeModelInput()).model
        assertTrue(before.becauseYouWatched.isEmpty())

        // Catalog and history identities stay unchanged; Favorites alone must invalidate the snapshot.
        val after = store.homeModel(
            state.copy(favorites = setOf("MOVIE:1")).homeModelInput(),
        ).model

        assertNotSame(before, after)
        assertEquals(setOf(1, 2), after.becauseYouWatched.map(ContentItem::id).toSet())
    }

    private fun HulkUiState.homeModelInput(): HomeContentModelInput = HomeContentModelInput(
        movieCatalog = catalogs[ContentType.MOVIE],
        seriesCatalog = catalogs[ContentType.SERIES],
        liveCatalog = catalogs[ContentType.LIVE],
        history = history,
        favorites = favorites,
    )

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
