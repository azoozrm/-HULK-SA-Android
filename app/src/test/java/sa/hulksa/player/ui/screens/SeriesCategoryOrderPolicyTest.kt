package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.ui.components.seriesHistoryIdentityText
import sa.hulksa.player.ui.components.orderedCountNumber

class SeriesCategoryOrderPolicyTest {
    private fun category(id: String) = Category(id = id, name = id, type = ContentType.SERIES)

    @Test
    fun `legacy adoption keeps displayed server order and drops synthetic fixed rows`() {
        assertEquals(
            listOf("c1", "c2"),
            legacySeriesServerCategoryOrderIds(
                "$CONTINUE_CATEGORY_ID,c1,$FAVORITES_CATEGORY_ID,c2,c1",
            ),
        )
    }

    @Test
    fun `committed order wins and new server categories append`() {
        assertEquals(
            listOf("c3", "c1", "c2"),
            orderedSeriesServerCategories(
                categories = listOf(category("c1"), category("c2"), category("c3")),
                committedOrderIds = listOf("c3", "c1"),
            ).map(Category::id),
        )
    }

    @Test
    fun `unavailable ids are ignored and duplicate ids collapse`() {
        assertEquals(
            listOf("c1", "c2"),
            orderedSeriesServerCategories(
                categories = listOf(category("c1"), category("c2")),
                committedOrderIds = listOf("c1", "gone", "c1"),
            ).map(Category::id),
        )
    }

    @Test
    fun `fixed semantic rows never enter the server order`() {
        val categories = listOf(
            category(CONTINUE_CATEGORY_ID),
            category("c1"),
            category(FAVORITES_CATEGORY_ID),
        )

        assertEquals(
            listOf("c1"),
            orderedSeriesServerCategories(categories, emptyList()).map(Category::id),
        )
    }

    @Test
    fun `hidden category keeps its committed position while excluded from the visible list`() {
        val ordered = orderedSeriesServerCategories(
            categories = listOf(category("a"), category("b"), category("c")),
            committedOrderIds = listOf("b", "a", "c"),
        ).map(Category::id)
        val hidden = setOf("a")

        assertEquals(listOf("b", "a", "c"), ordered)
        assertEquals(listOf("b", "c"), ordered.filterNot { it in hidden })
    }

    @Test
    fun `legacy adoption happens once for the first verified scope only`() {
        val legacy = listOf("c1", "c2")

        assertEquals(legacy, seriesCategoryOrderAdoption(null, adoptionDone = false, legacy))
        assertNull(seriesCategoryOrderAdoption(null, adoptionDone = true, legacy))
        assertNull(seriesCategoryOrderAdoption(emptyList(), adoptionDone = false, legacy))
    }

    @Test
    fun `order round trip keeps stable ids without blanks or duplicates`() {
        assertTrue(decodeSeriesCategoryOrderIds(null).isEmpty())
        assertTrue(decodeSeriesCategoryOrderIds("").isEmpty())
        assertEquals(listOf("b", "a"), decodeSeriesCategoryOrderIds(" b, a ,b"))
        assertEquals("b,a", encodeSeriesCategoryOrderIds(listOf("b", "", "a", "b")))
        assertEquals("", encodeSeriesCategoryOrderIds(emptyList()))
    }

    @Test
    fun `hidden ids round trip keeps stable ids without blanks or duplicates`() {
        assertTrue(decodeSeriesHiddenCategoryIds(null).isEmpty())
        assertEquals(setOf("b", "a"), decodeSeriesHiddenCategoryIds(" b, a ,b"))
        assertEquals("a,b", encodeSeriesHiddenCategoryIds(setOf("b", "", "a", "b")))
    }

    @Test
    fun `series manager draft commits once and cancellation restores the committed order`() {
        val draft = LiveCategoryOrderDraft(listOf("a", "b", "c"))
        draft.start()
        draft.move("c", 0)
        assertEquals(listOf("c", "a", "b"), draft.displayIds)

        assertEquals(listOf("c", "a", "b"), draft.commit())
        assertFalse(draft.isMoving)
        assertNull(draft.commit())

        draft.start()
        draft.move("c", 2)
        draft.cancel()
        assertFalse(draft.isMoving)
        assertEquals(listOf("c", "a", "b"), draft.displayIds)
        assertNull(draft.commit())
    }

    @Test
    fun `season count renders as an ordered number element only for truthful counts`() {
        // The rendered group is (icon, موسم, number) in physical LTR order, so Arabic reading from
        // the right is number, موسم, icon. The number is a separate stable element.
        assertEquals("1", orderedCountNumber(1))
        assertEquals("2", orderedCountNumber(2))
        assertEquals("10", orderedCountNumber(10))
        assertEquals("100", orderedCountNumber(100))
        // Truthful missing data stays absent instead of inventing a count.
        assertNull(orderedCountNumber(0))
        assertNull(orderedCountNumber(-3))
    }

    @Test
    fun `recent identity uses the real season and episode only when both exist`() {
        val entry = HistoryEntry(
            key = "SERIES:7",
            title = "ضرب نار · الحلقة 2",
            posterUrl = null,
            streamKind = "series",
            streamId = 7,
            extension = "mp4",
            isLive = false,
            positionMs = 1_000L,
            durationMs = 2_000L,
            updatedAtEpochMs = 0L,
            seriesTitle = "ضرب نار",
            season = 1,
            episodeNumber = 2,
            episodeTitle = "الحلقة 2",
            parentContentId = null,
        )
        assertEquals("الموسم 1 • الحلقة 2", seriesHistoryIdentityText(entry))
        assertNull(seriesHistoryIdentityText(entry.copy(season = null)))
        assertNull(seriesHistoryIdentityText(entry.copy(episodeNumber = null)))
    }
}
