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
