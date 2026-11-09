package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.MainDestination
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.projectCatalogsForDestination

class CatalogBootstrapProjectionTest {
    private fun item(id: Int, type: ContentType) = ContentItem(
        id = id,
        name = "item-$id",
        categoryId = "a",
        type = type,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = null,
    )

    private fun catalog(type: ContentType) = Catalog(
        categories = listOf(Category("a", "A", type)),
        items = listOf(item(1, type)),
    )

    @Test
    fun `home bootstrap is visible on home and never substitutes a catalog section`() {
        val bootstrap = mapOf(
            ContentType.MOVIE to catalog(ContentType.MOVIE),
            ContentType.SERIES to catalog(ContentType.SERIES),
            ContentType.LIVE to catalog(ContentType.LIVE),
        )

        val home = projectCatalogsForDestination(
            destination = MainDestination.HOME,
            current = emptyMap(),
            loaded = emptyMap(),
            home = emptyMap(),
            bootstrap = bootstrap,
        )
        assertEquals(bootstrap.keys, home.keys)
        assertTrue(home.values.all { it.items.size == 1 })

        val movies = projectCatalogsForDestination(
            destination = MainDestination.MOVIES,
            current = emptyMap(),
            loaded = emptyMap(),
            home = emptyMap(),
            bootstrap = bootstrap,
        )
        assertTrue(movies.isEmpty())
    }

    @Test
    fun `complete empty home section is preserved with zero count`() {
        val empty = Catalog(emptyList(), emptyList())

        val projected = projectCatalogsForDestination(
            destination = MainDestination.HOME,
            current = emptyMap(),
            loaded = emptyMap(),
            home = mapOf(ContentType.MOVIE to empty),
            bootstrap = emptyMap(),
        )

        assertNotNull(projected[ContentType.MOVIE])
        assertEquals(0, projected.getValue(ContentType.MOVIE).items.size)
    }

    @Test
    fun `full home catalog wins over bootstrap for the same type`() {
        val bootstrap = mapOf(ContentType.MOVIE to catalog(ContentType.MOVIE))
        val fullHome = Catalog(
            categories = emptyList(),
            items = listOf(item(1, ContentType.MOVIE), item(2, ContentType.MOVIE)),
        )
        val projected = projectCatalogsForDestination(
            destination = MainDestination.HOME,
            current = emptyMap(),
            loaded = emptyMap(),
            home = mapOf(ContentType.MOVIE to fullHome),
            bootstrap = bootstrap,
        )
        assertEquals(fullHome, projected[ContentType.MOVIE])
    }
}
