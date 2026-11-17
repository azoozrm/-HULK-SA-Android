package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.data.VIRTUAL_FOREIGN_SPORTS_CATEGORY_ID
import sa.hulksa.player.data.resolveLiveForeignSportsCategory
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class LiveForeignSportsMembershipTest {
    private fun channel(id: Int, category: String) = ContentItem(
        id = id,
        name = "channel-$id",
        categoryId = category,
        type = ContentType.LIVE,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = null,
    )

    @Test
    fun `virtual membership preserves configured order and original item identity`() {
        val items = listOf(channel(10, "uk"), channel(20, "uk"), channel(30, "it"))

        val visible = liveCategoryItems(
            items = items,
            categoryId = VIRTUAL_FOREIGN_SPORTS_CATEGORY_ID,
            virtualChannelIds = listOf(30, 10),
        )

        assertEquals(listOf(30, 10), visible.map(ContentItem::id))
        assertEquals(listOf("it", "uk"), visible.map(ContentItem::categoryId))
    }

    @Test
    fun `virtual matcher wins over the original category and leaves normal categories unchanged`() {
        val item = channel(10, "uk")
        val virtualIds = listOf(10)

        assertTrue(
            liveCategoryMatches(item, VIRTUAL_FOREIGN_SPORTS_CATEGORY_ID, virtualIds) { false },
        )
        assertTrue(liveCategoryMatches(item, "uk", emptyList()) { false })
        assertFalse(liveCategoryMatches(item, "it", emptyList()) { false })
    }

    @Test
    fun `cached catalog publishes the same membership for Live and the player browser`() {
        val cached = Catalog(
            categories = emptyList(),
            items = listOf(
                channel(101, "112").copy(name = "WS TNT Sports 1"),
                channel(102, "112").copy(name = "WS Sky Sports Main Event"),
                channel(103, "112").copy(name = "Unrelated"),
            ),
        )
        val resolved = checkNotNull(
            resolveLiveForeignSportsCategory(
                listOf("WS TNT Sports 1", "WS Sky Sports Main Event"),
                cached,
            ),
        )
        val live = deriveLiveCatalogProjection(
            LiveCatalogProjectionInput(
                catalog = cached,
                categoryId = VIRTUAL_FOREIGN_SPORTS_CATEGORY_ID,
                query = "",
                favorites = CatalogFavoriteSnapshot(emptySet()),
                virtualChannelIds = resolved.channelIds,
            ),
        )
        val browser = liveCategoryItems(
            items = cached.items,
            categoryId = VIRTUAL_FOREIGN_SPORTS_CATEGORY_ID,
            virtualChannelIds = resolved.channelIds,
        )

        assertEquals(listOf(101, 102), resolved.channelIds)
        assertEquals(resolved.channelIds, live.map(ContentItem::id))
        assertEquals(resolved.channelIds, browser.map(ContentItem::id))
    }

    @Test
    fun `new foreign aliases publish the same membership to Live and the player browser`() {
        val cached = Catalog(
            categories = emptyList(),
            items = listOf(
                channel(201, "es").copy(name = "ES M LALIGA FHD"),
                channel(202, "tr").copy(name = "TR TABii SPOR 1 FHD"),
                channel(203, "pl").copy(name = "PL CANAL+ SPORT ᴴᴰ"),
                channel(204, "xx").copy(name = "Unrelated football channel"),
            ),
        )
        val resolved = checkNotNull(
            resolveLiveForeignSportsCategory(
                listOf(
                    "TR TABii SPOR 1 FHD",
                    "ES M LALIGA FHD",
                    "PL CANAL+ SPORT ᴴᴰ",
                ),
                cached,
            ),
        )
        val live = deriveLiveCatalogProjection(
            LiveCatalogProjectionInput(
                catalog = cached,
                categoryId = VIRTUAL_FOREIGN_SPORTS_CATEGORY_ID,
                query = "",
                favorites = CatalogFavoriteSnapshot(emptySet()),
                virtualChannelIds = resolved.channelIds,
            ),
        )
        val browser = liveCategoryItems(
            items = cached.items,
            categoryId = VIRTUAL_FOREIGN_SPORTS_CATEGORY_ID,
            virtualChannelIds = resolved.channelIds,
        )

        assertEquals(listOf(202, 201, 203), resolved.channelIds)
        assertEquals(resolved.channelIds, live.map(ContentItem::id))
        assertEquals(resolved.channelIds, browser.map(ContentItem::id))
    }

    @Test
    fun `live projection uses the shared virtual membership`() {
        val catalog = Catalog(
            categories = emptyList(),
            items = listOf(channel(10, "uk"), channel(20, "uk"), channel(30, "it")),
        )
        val input = LiveCatalogProjectionInput(
            catalog = catalog,
            categoryId = VIRTUAL_FOREIGN_SPORTS_CATEGORY_ID,
            query = "",
            favorites = CatalogFavoriteSnapshot(emptySet()),
            virtualChannelIds = listOf(20, 30),
        )

        assertEquals(
            listOf(20, 30),
            deriveLiveCatalogProjection(input).map(ContentItem::id),
        )
    }
}
