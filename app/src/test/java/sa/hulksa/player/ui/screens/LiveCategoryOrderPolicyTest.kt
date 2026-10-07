package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentType

class LiveCategoryOrderPolicyTest {
    private fun category(id: String) = Category(id = id, name = id, type = ContentType.LIVE)

    @Test
    fun `legacy adoption keeps displayed server order and drops synthetic fixed rows`() {
        assertEquals(
            listOf("c1", "c2"),
            legacyLiveServerCategoryOrderIds(
                "$LIVE_TV_PRO_MAIN_RECENT_CATEGORY,c1,$LIVE_TV_PRO_MAIN_FAVORITES_CATEGORY,c2,c1",
            ),
        )
    }

    @Test
    fun `committed order wins and new server categories append`() {
        assertEquals(
            listOf("c3", "c1", "c2"),
            orderedLiveServerCategories(
                categories = listOf(category("c1"), category("c2"), category("c3")),
                committedOrderIds = listOf("c3", "c1"),
            ).map(Category::id),
        )
    }

    @Test
    fun `unavailable ids are ignored and duplicate ids collapse`() {
        assertEquals(
            listOf("c1", "c2"),
            orderedLiveServerCategories(
                categories = listOf(category("c1"), category("c2")),
                committedOrderIds = listOf("c1", "gone", "c1"),
            ).map(Category::id),
        )
    }

    @Test
    fun `fixed semantic rows never enter the server order`() {
        val categories = listOf(
            category(LIVE_TV_PRO_MAIN_RECENT_CATEGORY),
            category("c1"),
            category(LIVE_TV_PRO_MAIN_FAVORITES_CATEGORY),
            category(LIVE_TV_PRO_BROWSER_CONTINUE_CATEGORY),
            category(LIVE_TV_PRO_BROWSER_FAVORITES_CATEGORY),
        )

        assertEquals(
            listOf("c1"),
            orderedLiveServerCategories(categories, emptyList()).map(Category::id),
        )
    }

    @Test
    fun `hidden category keeps its committed position while excluded from the visible list`() {
        val ordered = orderedLiveServerCategories(
            categories = listOf(category("a"), category("b"), category("c")),
            committedOrderIds = listOf("b", "a", "c"),
        ).map(Category::id)
        val hidden = setOf("a")

        assertEquals(listOf("b", "a", "c"), ordered)
        assertEquals(listOf("b", "c"), ordered.filterNot { it in hidden })
    }

    @Test
    fun `move does not wrap at either boundary`() {
        assertEquals(listOf("a", "b", "c"), moveLiveCategory(listOf("a", "b", "c"), "a", -1))
        assertEquals(listOf("a", "b", "c"), moveLiveCategory(listOf("a", "b", "c"), "c", 3))
        assertEquals(listOf("b", "a", "c"), moveLiveCategory(listOf("a", "b", "c"), "a", 1))
        assertEquals(listOf("a", "c", "b"), moveLiveCategory(listOf("a", "b", "c"), "b", 2))
        assertEquals(listOf("a", "b", "c"), moveLiveCategory(listOf("a", "b", "c"), "missing", 0))
    }

    @Test
    fun `draft commits once and cancellation restores the committed order`() {
        val draft = LiveCategoryOrderDraft(listOf("a", "b", "c"))
        draft.start()
        draft.move("c", 0)
        assertEquals(listOf("c", "a", "b"), draft.displayIds)

        assertEquals(listOf("c", "a", "b"), draft.commit())
        assertFalse(draft.isMoving)
        assertEquals(listOf("c", "a", "b"), draft.committedIds)

        draft.start()
        draft.move("c", 2)
        draft.cancel()
        assertFalse(draft.isMoving)
        assertEquals(listOf("c", "a", "b"), draft.displayIds)

        assertNull(draft.commit())
        draft.cancel()
        assertEquals(listOf("c", "a", "b"), draft.displayIds)
    }

    @Test
    fun `interrupted draft never mutates the committed order`() {
        val draft = LiveCategoryOrderDraft(listOf("a", "b"))
        draft.move("a", 1)
        draft.cancel()

        assertEquals(listOf("a", "b"), draft.committedIds)
        assertTrue(legacyLiveServerCategoryOrderIds(null).isEmpty())
    }

    @Test
    fun `legacy adoption happens once for the first verified scope only`() {
        val legacy = listOf("c1", "c2")

        assertEquals(legacy, liveCategoryOrderAdoption(null, adoptionDone = false, legacy))
        assertNull(liveCategoryOrderAdoption(null, adoptionDone = true, legacy))
        assertNull(liveCategoryOrderAdoption(emptyList(), adoptionDone = false, legacy))
    }

    @Test
    fun `drag hit testing uses the current laid-out rows after scrolling`() {
        // The first row is partially scrolled above the viewport (negative offset), as Compose
        // reports for a scrolled list.
        val rows = listOf(
            LiveCategoryRowGeometry(key = "a", offset = -30, size = 100),
            LiveCategoryRowGeometry(key = "b", offset = 76, size = 100),
            LiveCategoryRowGeometry(key = "c", offset = 182, size = 100),
        )

        assertEquals("a", liveCategoryDragTargetKey(rows, pointerY = 510f, viewportTop = 530f))
        assertEquals("b", liveCategoryDragTargetKey(rows, pointerY = 650f, viewportTop = 530f))
        assertEquals("c", liveCategoryDragTargetKey(rows, pointerY = 730f, viewportTop = 530f))
    }

    @Test
    fun `drag hit testing ignores spacing gaps and points outside the rows`() {
        val rows = listOf(
            LiveCategoryRowGeometry(key = "a", offset = 0, size = 100),
            LiveCategoryRowGeometry(key = "b", offset = 106, size = 100),
        )

        assertNull(liveCategoryDragTargetKey(rows, pointerY = 103f, viewportTop = 0f))
        assertNull(liveCategoryDragTargetKey(rows, pointerY = -5f, viewportTop = 0f))
        assertNull(liveCategoryDragTargetKey(rows, pointerY = 999f, viewportTop = 0f))
    }

    @Test
    fun `drag hit testing never targets a row that is no longer laid out`() {
        // "gone" was the previous gesture's row and is no longer among the current rows; the pointer
        // now sits over the row that is actually displayed there.
        val currentRows = listOf(
            LiveCategoryRowGeometry(key = "b", offset = 0, size = 100),
            LiveCategoryRowGeometry(key = "c", offset = 106, size = 100),
        )

        val target = liveCategoryDragTargetKey(currentRows, pointerY = 150f, viewportTop = 100f)

        assertEquals("b", target)
    }
}
