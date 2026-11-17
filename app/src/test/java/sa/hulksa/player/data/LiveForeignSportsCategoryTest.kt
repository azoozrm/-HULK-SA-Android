package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class LiveForeignSportsCategoryTest {
    private fun channel(id: Int, name: String) = ContentItem(
        id = id,
        name = name,
        categoryId = "112",
        type = ContentType.LIVE,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = null,
    )

    @Test
    fun `normalization keeps channel number and feed distinctions`() {
        assertEquals("ws tnt sports 1", normalizeLiveForeignSportsAlias("WS TNT Sports 1"))
        assertEquals("uk tnt sports 1 hd", normalizeLiveForeignSportsAlias("UK: TNT SPORTS 1 ᴴᴰ "))
        assertEquals("uk sky sports plus hd", normalizeLiveForeignSportsAlias("UK: SKY SPORTS + ᴴᴰ"))
        assertEquals("ws sky sport 251", normalizeLiveForeignSportsAlias("WS Sky Sport 251"))
        assertTrue(
            normalizeLiveForeignSportsAlias("WS Sky Sport 251") !=
                normalizeLiveForeignSportsAlias("WS Sky Sport 252"),
        )
    }

    @Test
    fun `matching uses the active catalog name and the matched item's actual stream id`() {
        val aliases = listOf("WS TNT Sports 1")
        val catalog = Catalog(
            categories = emptyList(),
            items = listOf(
                channel(999001, "WS TNT Sports 1"),
                channel(488864, "Unrelated channel with an old reused id"),
            ),
        )

        val resolved = resolveLiveForeignSportsCategory(aliases, catalog)

        assertEquals(VIRTUAL_FOREIGN_SPORTS_CATEGORY_ID, resolved?.category?.id)
        assertEquals(VIRTUAL_FOREIGN_SPORTS_CATEGORY_NAME, resolved?.category?.name)
        assertEquals(listOf(999001), resolved?.channelIds)
    }

    @Test
    fun `a changed stream id still resolves by verified name`() {
        val catalog = Catalog(emptyList(), listOf(channel(123456, "WS TNT Sports 1")))
        val resolved = resolveLiveForeignSportsCategory(listOf("WS TNT Sports 1"), catalog)

        assertEquals(listOf(123456), resolved?.channelIds)
    }

    @Test
    fun `the same verified name resolves under a different source catalog and id`() {
        val aliases = listOf("WS TNT Sports 1")
        val first = Catalog(emptyList(), listOf(channel(100, "WS TNT Sports 1")))
        val second = Catalog(emptyList(), listOf(channel(200, "WS TNT Sports 1")))

        assertEquals(listOf(100), resolveLiveForeignSportsCategory(aliases, first)?.channelIds)
        assertEquals(listOf(200), resolveLiveForeignSportsCategory(aliases, second)?.channelIds)
    }

    @Test
    fun `a reused id with a different name stays out`() {
        val catalog = Catalog(
            emptyList(),
            listOf(channel(488864, "Some other provider football channel")),
        )

        assertNull(resolveLiveForeignSportsCategory(listOf("WS TNT Sports 1"), catalog))
    }

    @Test
    fun `no matches or empty aliases hide the category`() {
        val catalog = Catalog(emptyList(), listOf(channel(1, "Unrelated")))
        assertNull(resolveLiveForeignSportsCategory(listOf("WS TNT Sports 1"), catalog))
        assertNull(resolveLiveForeignSportsCategory(emptyList(), catalog))
        assertNull(resolveLiveForeignSportsCategory(null, catalog))
        assertNull(resolveLiveForeignSportsCategory(listOf("WS TNT Sports 1"), null))
    }

    @Test
    fun `matched items are ordered by alias priority and deduplicated by original id`() {
        val catalog = Catalog(
            emptyList(),
            listOf(
                channel(20, "UK: TNT SPORTS 2 ᴴᴰ"),
                channel(10, "WS TNT Sports 1"),
                channel(10, "WS TNT Sports 1"),
            ),
        )

        val resolved = resolveLiveForeignSportsCategory(
            listOf("WS TNT Sports 1", "UK: TNT SPORTS 2 ᴴᴰ"),
            catalog,
        )

        assertEquals(listOf(10, 20), resolved?.channelIds)
    }

    @Test
    fun `bundled list contains the full one-time verified alias set`() {
        assertEquals(92, BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES.size)
        assertEquals("WS TNT Sports 1", BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES.first())
        assertEquals(
            "DE: SKY SPORT AUSTRIA 7 ᴴᴰ (720P)",
            BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES.last(),
        )
    }

    @Test
    fun `override wins, last valid is the fallback, and the bundle is the default`() {
        val override = OperationsLiveForeignSportsConfig(listOf("Override One"))
        val lastValid = listOf("Last Valid One")

        assertEquals(
            listOf("Override One"),
            liveForeignSportsAliasesFor(override, lastValid),
        )
        assertEquals(
            listOf("Last Valid One"),
            liveForeignSportsAliasesFor(null, lastValid),
        )
        assertEquals(
            BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES,
            liveForeignSportsAliasesFor(null, null),
        )
        assertEquals(
            emptyList<String>(),
            liveForeignSportsAliasesFor(OperationsLiveForeignSportsConfig(emptyList()), lastValid),
        )
    }

    @Test
    fun `last valid alias list round trips including an explicit empty hide`() {
        val encoded = encodeLiveForeignSportsLastValid(listOf("A", "B", "A"))
        assertEquals(listOf("A", "B"), decodeLiveForeignSportsLastValid(encoded))
        assertEquals(emptyList<String>(), decodeLiveForeignSportsLastValid("[]"))
        assertNull(decodeLiveForeignSportsLastValid(null))
    }
}
