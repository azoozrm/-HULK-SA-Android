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
        assertEquals(233, BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES.size)
        assertEquals("WS TNT Sports 1", BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES.first())
        assertEquals("PL CANAL EXTRA 07", BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES.last())
    }

    @Test
    fun `bundled list appends new broadcaster groups in the required order`() {
        val aliases = BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES
        fun indexOf(alias: String): Int =
            aliases.indexOf(alias).also { assertTrue(alias, it >= 0) }

        val starts = listOf(
            indexOf("DE: SKY SPORT AUSTRIA 7 ᴴᴰ (720P)"),
            indexOf("TR: TRT 1 ᴴᴰ"),
            indexOf("ES: MOVISTAR LA LIGA ᴴᴰ"),
            indexOf("PT: SPORT TV 1"),
            indexOf("NL: ZIGGO SPORT ᴿᴬᵂ ◉"),
            indexOf("RS: Arena Sport 1 ᴴᴰ"),
            indexOf("RO: DIGI Sport 1 ᴴᴰ"),
            indexOf("PL: CANAL+ SPORT ᴴᴰ"),
        )

        assertEquals(starts.sorted(), starts)
    }

    @Test
    fun `new broadcaster siblings stay adjacent to their group`() {
        val aliases = BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES
        val turkeyStart = aliases.indexOf("TR: TRT 1 ᴴᴰ")
        val spainStart = aliases.indexOf("ES: MOVISTAR LA LIGA ᴴᴰ")
        val portugalStart = aliases.indexOf("PT: SPORT TV 1")
        val netherlandsStart = aliases.indexOf("NL: ZIGGO SPORT ᴿᴬᵂ ◉")
        val balkansStart = aliases.indexOf("RS: Arena Sport 1 ᴴᴰ")
        val romaniaStart = aliases.indexOf("RO: DIGI Sport 1 ᴴᴰ")
        val polandStart = aliases.indexOf("PL: CANAL+ SPORT ᴴᴰ")

        for (alias in listOf(
            "TR TABii SPOR 1 FHD",
            "TR TABİİ SPOR FHD",
            "TR S SPORT HD",
            "TRT 1 HD",
        )) {
            val index = aliases.indexOf(alias)
            assertTrue(alias, index in turkeyStart until spainStart)
        }
        for (alias in listOf(
            "ES DAZN LA LIGA 1 FHD",
            "ES M LIGA DE CAMPEONES 13 HD",
        )) {
            val index = aliases.indexOf(alias)
            assertTrue(alias, index in spainStart until portugalStart)
        }
        for (alias in listOf(
            "PT| SPORT TV 7 HD",
            "PT| DAZN SPORT 6 FHD",
        )) {
            val index = aliases.indexOf(alias)
            assertTrue(alias, index in portugalStart until netherlandsStart)
        }
        for (alias in listOf(
            "Ziggo Sport Extra 2 NL",
            "Ziggo Sport Voetbal NL",
            "NL: ESPN 4 ᴿᴬᵂ ",
            "ESPN 1 FHD NL",
            "ESPN 4 HD NL",
        )) {
            val index = aliases.indexOf(alias)
            assertTrue(alias, index in netherlandsStart until balkansStart)
        }
        for (alias in listOf(
            "Arena Sport 5 SR",
            "SR ARENA PREMIUM 2",
        )) {
            val index = aliases.indexOf(alias)
            assertTrue(alias, index in balkansStart until romaniaStart)
        }
        for (alias in listOf(
            "RO| PRIMA SPORT PLUS HD",
            "RO| DIGI SPORT 4 FHD",
        )) {
            val index = aliases.indexOf(alias)
            assertTrue(alias, index in romaniaStart until polandStart)
        }
        for (alias in listOf(
            "PL CANAL SPORT 5 FHD",
            "PL: ELEVEN SPORTS 1 ᴴᴰ",
            "PL ELEVEN SPORTS 4 FHD",
            "PL CANAL EXTRA 01",
            "PL CANAL EXTRA 07",
        )) {
            assertTrue(alias, aliases.indexOf(alias) >= polandStart)
        }
    }

    @Test
    fun `unresolved event and non-football names stay out of the bundle`() {
        val normalized = BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES
            .map(::normalizeLiveForeignSportsAlias)
            .toSet()

        for (unresolved in listOf(
            "AZ: CBC AZERBAJAN",
            "ARENA SPORT",
            "WS Sport TV 1",
            "TR S SPORT PLUS 1 HD ( Yayın zamanı aktif )",
            "ES LALIGA 01 EVENT ONLY",
            "#### ES| M+ LALIGA VIP ᴴᴰ ####",
            "PT| SPORT TV NBA FHD",
            "Ziggo Sport Golf NL",
            "PL: ELEVEN SPORTS 1 4K/UHD",
            "PL ELEVEN SPORTS 1",
            "RO| PRIMA SPORT HD",
            "ESPN Play 1 FHD NL",
            "NL: ESPN UHD",
            "ES: DAZN F1 ᴴᴰ",
        )) {
            assertTrue(
                unresolved,
                normalizeLiveForeignSportsAlias(unresolved) !in normalized,
            )
        }
    }

    @Test
    fun `duplicate quality variants for the same channel stay out`() {
        val aliases = BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES
        for (duplicate in listOf(
            "TRT 1",
            "ES DAZN LA LIGA 2 FHD",
            "RO| DIGI SPORT 1 FHD",
            "RO| DIGI SPORT 2 FHD",
        )) {
            assertTrue(duplicate, duplicate !in aliases)
        }
        for (survivor in listOf(
            "TRT 1 HD",
            "ES: DAZN LA LIGA 2 ᴴᴰ",
            "RO: DIGI Sport 1 ᴴᴰ",
            "RO: DIGI Sport 2 ᴴᴰ",
        )) {
            assertTrue(survivor, survivor in aliases)
        }
    }

    @Test
    fun `verified aliases stay distinct across country and broadcaster labels`() {
        assertTrue(
            normalizeLiveForeignSportsAlias("PL: CANAL+ SPORT ᴴᴰ") !=
                normalizeLiveForeignSportsAlias("FR: CANAL+ SPORT ᴴᴰ"),
        )
        assertTrue(
            normalizeLiveForeignSportsAlias("ES M LALIGA FHD") !=
                normalizeLiveForeignSportsAlias("ES DAZN LA LIGA 1 FHD"),
        )
        assertTrue(
            normalizeLiveForeignSportsAlias("PT| SPORT TV 1 FHD") !=
                normalizeLiveForeignSportsAlias("PT: SPORT TV 1"),
        )
        assertEquals(
            "pt sport tv plus raw",
            normalizeLiveForeignSportsAlias("PT: SPORT TV + ᴿᴬᵂ"),
        )
        assertEquals(
            "pl canal plus sport hd",
            normalizeLiveForeignSportsAlias("PL: CANAL+ SPORT ᴴᴰ"),
        )
    }

    @Test
    fun `new exact names resolve in alias order`() {
        val aliases = listOf(
            "TR: TABII SPORT 1 ᴿᴬᵂ",
            "ES M LALIGA FHD",
            "PL: CANAL+ SPORT ᴴᴰ",
        )
        val catalog = Catalog(
            emptyList(),
            listOf(
                channel(50, "PL: CANAL+ SPORT ᴴᴰ"),
                channel(10, "TR: TABII SPORT 1 ᴿᴬᵂ"),
                channel(30, "ES M LALIGA FHD"),
            ),
        )

        assertEquals(
            listOf(10, 30, 50),
            resolveLiveForeignSportsCategory(aliases, catalog)?.channelIds,
        )
    }

    @Test
    fun `every bundled addition resolves from its exact catalog name`() {
        val additions = BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES.drop(92)
        val normalized = additions.map(::normalizeLiveForeignSportsAlias)

        assertEquals(additions.size, normalized.distinct().size)
        val catalog = Catalog(
            emptyList(),
            additions.mapIndexed { index, name -> channel(500_000 + index, name) },
        )
        val resolved = checkNotNull(
            resolveLiveForeignSportsCategory(BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES, catalog),
        )

        assertEquals(
            catalog.items.map(ContentItem::id),
            resolved.channelIds,
        )
    }

    @Test
    fun `different account catalogs resolve only their own verified names`() {
        val neoCatalog = Catalog(emptyList(), listOf(channel(11, "ES: DAZN LA LIGA ᴴᴰ")))
        val hulkCatalog = Catalog(emptyList(), listOf(channel(22, "ES DAZN LA LIGA 1 FHD")))

        assertEquals(
            listOf(11),
            resolveLiveForeignSportsCategory(
                BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES,
                neoCatalog,
            )?.channelIds,
        )
        assertEquals(
            listOf(22),
            resolveLiveForeignSportsCategory(
                BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES,
                hulkCatalog,
            )?.channelIds,
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
