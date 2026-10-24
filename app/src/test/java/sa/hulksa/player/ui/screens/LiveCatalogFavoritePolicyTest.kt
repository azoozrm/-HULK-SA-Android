package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class LiveCatalogFavoritePolicyTest {
    @Test
    fun `favorite-only state does not change all or server category membership`() {
        val channel = liveChannel(1)
        val before = setOf("LIVE:2")
        val after = before + "LIVE:1"

        val beforeFavorite: (ContentItem) -> Boolean = { "${it.type.name}:${it.id}" in before }
        val afterFavorite: (ContentItem) -> Boolean = { "${it.type.name}:${it.id}" in after }
        assertTrue(categoryMatches(channel, null, beforeFavorite))
        assertTrue(categoryMatches(channel, null, afterFavorite))
        assertTrue(categoryMatches(channel, channel.categoryId, beforeFavorite))
        assertTrue(categoryMatches(channel, channel.categoryId, afterFavorite))
    }

    @Test
    fun `live favorites filter removal removes exactly the unfavorited channel`() {
        val removed = liveChannel(1)
        val retained = liveChannel(2)
        val before = setOf("LIVE:1", "LIVE:2")
        val after = before - "LIVE:1"

        val beforeFavorite: (ContentItem) -> Boolean = { "${it.type.name}:${it.id}" in before }
        val afterFavorite: (ContentItem) -> Boolean = { "${it.type.name}:${it.id}" in after }
        assertTrue(categoryMatches(removed, FAVORITES_CATEGORY_ID, beforeFavorite))
        assertTrue(categoryMatches(retained, FAVORITES_CATEGORY_ID, beforeFavorite))
        assertFalse(categoryMatches(removed, FAVORITES_CATEGORY_ID, afterFavorite))
        assertTrue(categoryMatches(retained, FAVORITES_CATEGORY_ID, afterFavorite))
    }

    @Test
    fun `preview fallback after removal is deterministic and never opens a replacement`() {
        val first = liveChannel(1)
        val second = liveChannel(2)
        val third = liveChannel(3)

        assertEquals(
            second,
            resolveLivePreview(
                current = first,
                visible = listOf(second, third),
                rememberedItemKey = "LIVE:1",
                rememberedIndex = 0,
            ),
        )
        assertEquals(
            third,
            resolveLivePreview(
                current = first,
                visible = listOf(second, third),
                rememberedItemKey = "LIVE:1",
                rememberedIndex = 1,
            ),
        )
        assertNull(
            resolveLivePreview(
                current = first,
                visible = emptyList(),
                rememberedItemKey = "LIVE:1",
                rememberedIndex = 0,
            ),
        )
    }

    @Test
    fun `removal handoff chooses next first then previous last and empty`() {
        val first = liveChannel(1)
        val middle = liveChannel(2)
        val last = liveChannel(3)
        val ordered = listOf(first, middle, last)

        assertEquals("LIVE:2", liveRemovalHandoff(ordered, "LIVE:1")?.fallbackKey)
        assertEquals("LIVE:3", liveRemovalHandoff(ordered, "LIVE:2")?.fallbackKey)
        assertEquals("LIVE:2", liveRemovalHandoff(ordered, "LIVE:3")?.fallbackKey)
        assertNull(liveRemovalHandoff(listOf(first), "LIVE:1")?.fallbackKey)
        assertNull(liveRemovalHandoff(ordered, "LIVE:99"))
    }

    @Test
    fun `already visible removal fallback has no forced top alignment delta`() {
        assertEquals(0, liveRemovalRevealDelta(100, 180, 0, 300))
        assertEquals(-100, liveRemovalRevealDelta(-100, -20, 0, 300))
        assertEquals(40, liveRemovalRevealDelta(260, 340, 0, 300))
        assertNull(liveRemovalRevealDelta(null, null, 0, 300))
    }

    @Test
    fun `row removal guard consumes repeats and release then admits a fresh press`() {
        val guard = FavoritesRemovalInputGuard()
        var mutations = 0
        var replacementActions = 0
        beginFavoritesRemovalLongPress(
            enabled = true,
            beginGuard = guard::begin,
            toggleFavorite = { mutations += 1 },
        )
        assertTrue(guard.active)
        repeat(3) {
            assertTrue(guard.onSelectKey(androidx.compose.ui.input.key.KeyEventType.KeyDown))
            if (!guard.active) replacementActions += 1
        }
        assertTrue(guard.onSelectKey(androidx.compose.ui.input.key.KeyEventType.KeyUp))
        assertFalse(guard.active)
        assertEquals(1, mutations)
        assertEquals(0, replacementActions)
        assertFalse(guard.onSelectKey(androidx.compose.ui.input.key.KeyEventType.KeyDown))
        beginFavoritesRemovalLongPress(
            enabled = true,
            beginGuard = guard::begin,
            toggleFavorite = { mutations += 1 },
        )
        assertTrue(guard.onSelectKey(androidx.compose.ui.input.key.KeyEventType.KeyUp))
        assertEquals(2, mutations)
    }

    @Test
    fun `normal preview-style toggle does not arm the shell guard`() {
        val guard = FavoritesRemovalInputGuard()
        var mutations = 0
        beginFavoritesRemovalLongPress(
            enabled = false,
            beginGuard = guard::begin,
            toggleFavorite = { mutations += 1 },
        )
        assertFalse(guard.active)
        assertFalse(guard.onSelectKey(androidx.compose.ui.input.key.KeyEventType.KeyDown))
        assertFalse(guard.onSelectKey(androidx.compose.ui.input.key.KeyEventType.KeyUp))
        assertEquals(1, mutations)
    }

    private fun liveChannel(id: Int): ContentItem = ContentItem(
        id = id,
        name = "Channel $id",
        categoryId = "news",
        type = ContentType.LIVE,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = "m3u8",
        genre = null,
        addedAtEpochSeconds = 0L,
    )
}
