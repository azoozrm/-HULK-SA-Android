package sa.hulksa.player.ui.screens

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class HomeContentCacheTest {
    @Test
    fun changingFavoritesInvalidatesPersonalizedHomeSnapshot() = runBlocking {
        val movieCatalog = Catalog(
            categories = emptyList(),
            items = listOf(
                movie(id = 1, name = "Action", categoryId = "action", rating = "8.0", genre = "Action"),
                movie(id = 2, name = "Drama Favorite", categoryId = "drama", rating = "7.0", genre = "Drama"),
                movie(id = 3, name = "More Drama", categoryId = "drama", rating = "6.0", genre = "Drama"),
            ),
        )
        val initialState = HulkUiState(
            catalogs = mapOf(ContentType.MOVIE to movieCatalog),
            favorites = emptySet(),
        )
        val store = NavigationMemoryStore()

        val initialInput = initialState.homeModelInput()
        val initialSnapshot = store.homeModel(initialInput).model
        val favoritedState = initialState.copy(favorites = setOf("MOVIE:2"))
        val personalizedInput = favoritedState.homeModelInput()
        val personalizedSnapshot = store.homeModel(personalizedInput).model

        assertNotSame(initialSnapshot, personalizedSnapshot)
        assertTrue(personalizedSnapshot.becauseYouWatched.any { it.categoryId == "drama" })
        assertSame(personalizedSnapshot, store.homeModel(personalizedInput).model)
    }

    private fun HulkUiState.homeModelInput(): HomeContentModelInput = HomeContentModelInput(
        movieCatalog = catalogs[ContentType.MOVIE],
        seriesCatalog = catalogs[ContentType.SERIES],
        liveCatalog = catalogs[ContentType.LIVE],
        history = history,
        favorites = favorites,
    )

    private fun movie(
        id: Int,
        name: String,
        categoryId: String,
        rating: String,
        genre: String,
    ): ContentItem = ContentItem(
        id = id,
        name = name,
        categoryId = categoryId,
        type = ContentType.MOVIE,
        posterUrl = null,
        rating = rating,
        year = null,
        containerExtension = null,
        genre = genre,
    )
}
