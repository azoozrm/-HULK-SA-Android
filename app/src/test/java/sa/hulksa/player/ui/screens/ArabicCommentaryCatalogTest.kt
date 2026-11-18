package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class ArabicCommentaryCatalogTest {
    @Test
    fun `eligible variants span all Live categories and preserve provider labels`() {
        val original = item(1, "◉: beIN Sp⚽rts 1 ᴴᴰ", "mena")
        val hevc = item(2, "BEIN SPORTS1 HEVC", "codec")
        val sd = item(3, "SPORTS ١ SD", "sport")
        val fourK = item(4, "be-in Sport 1 4K", "ultra")
        val explicitArabic = item(5, "AR: beIN 1 FHD", "mixed")
        val catalog = Catalog(
            categories = listOf(
                category("mena", "AR| BEIN SPORTS ◉⚽"),
                category("codec", "Sports | HEVC"),
                category("sport", "beIN SPORTS SD"),
                category("ultra", "Sports 4K"),
                category("mixed", "English + Arabic Sports"),
            ),
            items = listOf(original, hevc, sd, fourK, explicitArabic),
        )

        val feed = arabicCommentaryFeeds(catalog, currentStreamId = 77).single()
        assertEquals("beIN SPORTS 1", feed.key.label)
        assertEquals(listOf(3, 1, 2, 5, 4), feed.sources.map { it.item.id })
        assertSame(original, feed.sources[1].item)
        assertEquals("AR| BEIN SPORTS ◉⚽", feed.sources[1].categoryName)
        assertEquals(listOf(original, hevc, sd, fourK, explicitArabic), catalog.items)
        assertTrue(feed.matches(arabicCommentarySearchWords("Sp⚽rts ١ HEVC")))
        assertFalse(feed.matches(arabicCommentarySearchWords("Al Jazeera")))
    }

    @Test
    fun `foreign regional sports news movies and unrelated channels are excluded`() {
        val catalog = Catalog(
            categories = listOf(
                category("fr", "FRANCE"), category("sports", "AR | beIN Sports"),
                category("other", "Live"), category("us", "USA - SPORT"),
            ),
            items = listOf(
                item(1, "beIN SPORTS 2 FR HD", "sports"),
                item(2, "beIN SPORT 2", "fr"),
                item(3, "beIN SPORTS 2", "us"),
                item(4, "US BEIN SPORTS 2 FHD", "other"),
                item(5, "TR: beIN SPORTS MAX 1", "sports"),
                item(6, "beIN SPORTS NEWS HD", "sports"),
                item(7, "beIN NEWS الإخبارية", "other"),
                item(8, "BEIN GLOBAL", "sports"),
                item(9, "beIN Movies 2", "sports"),
                item(10, "Al Jazeera HD", "sports"),
                item(11, "TNT SPORTS 2", "sports"),
                item(12, "beIN SPORTS 1", "sports").copy(type = ContentType.MOVIE),
                item(13, "beIN SPORTS 3", "sports"),
                item(14, "بي إن سبورت ٢", "sports"),
                item(15, "AR: beIN SPORTS 2", "us"),
            ),
        )

        val feed = arabicCommentaryFeeds(catalog, currentStreamId = 13).single()
        assertEquals(listOf(14, 15), feed.sources.map { it.item.id })
        assertEquals("beIN SPORTS 2", feed.key.label)
    }

    @Test
    fun `natural ordering keeps channel numbers feed families and regional tags distinct`() {
        val catalog = Catalog(
            categories = listOf(category("hd", "BEIN Sports HD"), category("uhd", "BEIN Sports 4K")),
            items = listOf(
                item(11, "beIN SPORTS 11", "hd"),
                item(2, "beIN SPORTS MAX1 HD", "hd"),
                item(3, "beIN SPORTS 1 XTRA HD", "hd"),
                item(4, "beIN 1 (NM) - FHD", "hd"),
                item(5, "beIN 1 (NM) - 4K", "uhd"),
                item(6, "beIN 1 (D) - HD", "hd"),
                item(1, "beIN SPORTS 1", "hd"),
                item(7, "beIN SPORTS 4K", "uhd"),
                item(8, "beIN SPORTS 2", "hd"),
                item(12, "beIN SPORTS 2+ HD", "hd"),
                item(10, "beIN SPORTS 10", "hd"),
                item(9, "beIN SPORTS XTRA1 HEVC", "uhd"),
            ),
        )

        val feeds = arabicCommentaryFeeds(catalog, currentStreamId = 77)
        assertEquals(
            listOf("beIN SPORTS 1", "beIN SPORTS 1 D", "beIN SPORTS 1 NM", "beIN SPORTS 2",
                "beIN SPORTS 2 PLUS", "beIN SPORTS 10", "beIN SPORTS 11", "beIN SPORTS 4K",
                "beIN SPORTS MAX 1", "beIN SPORTS XTRA 1"),
            feeds.map { it.key.label },
        )
        assertEquals(listOf(4, 5), feeds[2].sources.map { it.item.id })
        assertEquals(listOf(3, 9), feeds.last().sources.map { it.item.id })
    }

    @Test
    fun `fallback is bounded within the chosen feed and old callbacks cannot affect replacement`() {
        val catalog = Catalog(
            categories = listOf(category("a", "AR | beIN SPORTS"), category("b", "beIN HD")),
            items = listOf(
                item(1, "beIN SPORTS 1 SD", "a"), item(1, "beIN SPORTS 1 SD", "b"),
                item(2, "beIN SPORTS 1 HD", "b"), item(3, "beIN SPORTS 1 FHD", "a"),
                item(4, "beIN SPORTS 1 4K", "a"), item(5, "beIN SPORTS 11 HD", "b"),
            ),
        )
        val (feed, differentFeed) = arabicCommentaryFeeds(catalog, currentStreamId = 77)
        val manual = feed.sources.last()
        val recovery = ArabicCommentaryFallback(feed, manual)

        assertEquals(5, feed.sources.size)
        assertEquals(manual, recovery.nextSource())
        val first = recovery.beginAttempt()
        assertTrue(recovery.accepts(first))
        recovery.endAttempt()
        assertFalse(recovery.accepts(first))
        assertEquals(1, recovery.nextSource()?.item?.id)
        val replacement = recovery.beginAttempt()
        assertTrue(recovery.accepts(replacement))
        assertFalse(recovery.accepts(first))
        assertEquals(2, recovery.nextSource()?.item?.id)
        assertNull(recovery.nextSource())
        assertNull(recovery.nextSource())
        assertEquals(setOf(1, 2, 3, 4), feed.sources.map { it.item.id }.toSet())

        val otherRecovery = ArabicCommentaryFallback(differentFeed, differentFeed.sources.single())
        assertFalse(otherRecovery.accepts(replacement))
        assertEquals(5, otherRecovery.nextSource()?.item?.id)
    }

    @Test
    fun snapshotReopensReuseIndexAndOnlyRemoveCurrentPictureSource() {
        val items = (1..12000).map { item(it, "TNT $it", "other") } + listOf(
            item(20001, "beIN 1 HD", "ar"), item(20002, "BE: beIN 1 RAW", "ar"),
            item(20003, "F: beIN 1 AFC RAW", "ar"), item(20004, "beIN 1 AFC", "ar"),
            item(20005, "beIN 1 XTRA", "ar"),
        )
        val catalog = Catalog(listOf(category("ar", "Arabic beIN Sports")), items)
        val profile = sa.hulksa.player.data.AccountProfileStateScope("account", "profile")
        val snapshot = CommentarySnapshot(null, profile, catalog)
        val started = System.nanoTime()
        val first = snapshot.index.forPicture(1)
        val firstMs = (System.nanoTime() - started) / 1_000_000
        val reopened = System.nanoTime()
        val again = snapshot.index.forPicture(2)
        println("synthetic 12005 rows; cold index + first lookup ms=$firstMs; warm lookup ns=" +
            (System.nanoTime() - reopened))
        assertSame(first, again)
        assertEquals(3, first.size)
        assertEquals(listOf(20001, 20002), first.shortcut(1)?.sources?.map { it.item.id })
        assertEquals(2, first.single { it.key.qualifier == "afc" }.sources.size)
        assertEquals(20002, snapshot.index.forPicture(20001).shortcut(1)?.sources?.single()?.item?.id)
        assertEquals(snapshot, CommentarySnapshot(null, profile, catalog))
        assertFalse(snapshot == CommentarySnapshot(null, profile.copy(profileId = "other"), catalog))
        assertFalse(snapshot == CommentarySnapshot(null, profile.copy(accountId = "other"), catalog))
        assertFalse(snapshot == CommentarySnapshot(null, profile, catalog.copy()))
    }

    @Test
    fun replacementKeepsRetrySelectionButDropsRouteAndOffsets() {
        val feed = arabicCommentaryFeeds(Catalog(listOf(category("a", "Sports")),
            listOf(item(1, "beIN 1 HD", "a")))).single()
        val before = ArabicCommentaryState(
            feed = feed, source = feed.sources.single(), phase = ArabicCommentaryPhase.ACTIVE,
            commentaryAudible = true, adjusting = true, buffering = true,
            alignment = ArabicCommentaryAlignment(ArabicCommentaryDelays(100, 1000)),
        )
        val after = requireNotNull(before.afterPictureReplacement())
        assertTrue(after.canRetry)
        assertSame(feed, after.feed)
        assertEquals(ArabicCommentaryPhase.INTERRUPTED, after.phase)
        assertTrue(after.alignment.needsReadjustment)
        assertFalse(after.alignment.delays.hasDelay || after.adjusting || after.buffering || after.commentaryAudible)
        assertNull(before.copy(sessionAvailable = false).afterPictureReplacement())
    }

    private fun category(id: String, name: String) = Category(id, name, ContentType.LIVE)

    private fun item(id: Int, name: String, category: String) = ContentItem(
        id = id,
        name = name,
        categoryId = category,
        type = ContentType.LIVE,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = null,
    )
}
