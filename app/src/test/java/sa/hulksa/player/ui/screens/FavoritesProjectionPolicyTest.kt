package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class FavoritesProjectionPolicyTest {
    private fun item(
        id: Int,
        type: ContentType,
        name: String = "item-$id",
        addedAt: Long? = null,
    ) = ContentItem(
        id = id,
        name = name,
        categoryId = "c$id",
        type = type,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = null,
        addedAtEpochSeconds = addedAt,
    )

    private val movie1 = item(1, ContentType.MOVIE, addedAt = 10L)
    private val movie2 = item(2, ContentType.MOVIE, addedAt = 30L)
    private val series1 = item(3, ContentType.SERIES, addedAt = 20L)
    private val channel1 = item(4, ContentType.LIVE, addedAt = 40L)

    private val catalogs = listOf(
        Catalog(categories = emptyList(), items = listOf(movie1, series1)),
        Catalog(categories = emptyList(), items = listOf(channel1, movie2, movie1)),
    )

    private val favorites = setOf(
        "${ContentType.MOVIE}:1",
        "${ContentType.MOVIE}:2",
        "${ContentType.SERIES}:3",
        "${ContentType.LIVE}:4",
    )
    private val isFavorite: (ContentItem) -> Boolean = { "${it.type}:${it.id}" in favorites }

    @Test
    fun projection_keepsCatalogOrderAndCollapsesDuplicateTypeIdIdentity() {
        assertEquals(listOf(movie1, series1, channel1, movie2), favoriteCatalogProjection(catalogs, isFavorite))
    }

    @Test
    fun projection_isNotAnAddedDateSort() {
        // Catalog order is authoritative; addedAt is only metadata.
        assertEquals(
            listOf(1, 3, 4, 2),
            favoriteCatalogProjection(catalogs, isFavorite).map(ContentItem::id),
        )
    }

    @Test
    fun unresolvedSavedId_staysUnresolvedAndNeverAppearsInTheProjection() {
        val withUnknownFavorite = favorites + "${ContentType.MOVIE}:999"
        val projection = favoriteCatalogProjection(catalogs) { "${it.type}:${it.id}" in withUnknownFavorite }
        assertEquals(listOf(1, 3, 4, 2), projection.map(ContentItem::id))
        // The caller-owned saved identity is not rewritten or partially deleted by the projection.
        assertEquals(favorites + "${ContentType.MOVIE}:999", withUnknownFavorite)
    }

    @Test
    fun typeFilters_keepProjectionOrderAndExactTypes() {
        val projection = favoriteCatalogProjection(catalogs, isFavorite)
        assertEquals(listOf(movie1, movie2), filterFavoritesByType(projection, FavoritesTypeFilter.MOVIES))
        assertEquals(listOf(series1), filterFavoritesByType(projection, FavoritesTypeFilter.SERIES))
        assertEquals(listOf(channel1), filterFavoritesByType(projection, FavoritesTypeFilter.CHANNELS))
        val all = filterFavoritesByType(projection, FavoritesTypeFilter.ALL)
        assertEquals(projection, all)
        assertSame(projection, all)
    }

    @Test
    fun memoryKeysRoundTrip_andUnknownKeysDefaultToAll() {
        FavoritesTypeFilter.entries.forEach { filter ->
            assertEquals(filter, favoritesTypeFilterFromMemoryKey(filter.memoryKey))
        }
        assertEquals(FavoritesTypeFilter.ALL, favoritesTypeFilterFromMemoryKey(null))
        assertEquals(FavoritesTypeFilter.ALL, favoritesTypeFilterFromMemoryKey(""))
        assertEquals(FavoritesTypeFilter.ALL, favoritesTypeFilterFromMemoryKey("legacy"))
    }

    @Test
    fun countUnits_matchRealTypes() {
        assertEquals("عنصر", favoritesCountUnit(FavoritesTypeFilter.ALL))
        assertEquals("فلم", favoritesCountUnit(FavoritesTypeFilter.MOVIES))
        assertEquals("مسلسل", favoritesCountUnit(FavoritesTypeFilter.SERIES))
        assertEquals("قناة", favoritesCountUnit(FavoritesTypeFilter.CHANNELS))
    }
}
