package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentType

class MoviesCategoryOrderPolicyTest {
    private fun category(id: String) = Category(id = id, name = id, type = ContentType.MOVIE)

    @Test
    fun `legacy adoption keeps displayed server order and drops synthetic fixed rows`() {
        assertEquals(
            listOf("c1", "c2"),
            legacyMovieServerCategoryOrderIds(
                "$CONTINUE_CATEGORY_ID,c1,$FAVORITES_CATEGORY_ID,c2,c1",
            ),
        )
    }

    @Test
    fun `committed order wins and new server categories append`() {
        assertEquals(
            listOf("c3", "c1", "c2"),
            orderedMovieServerCategories(
                categories = listOf(category("c1"), category("c2"), category("c3")),
                committedOrderIds = listOf("c3", "c1"),
            ).map(Category::id),
        )
    }

    @Test
    fun `unavailable ids are ignored and duplicate ids collapse`() {
        assertEquals(
            listOf("c1", "c2"),
            orderedMovieServerCategories(
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
            orderedMovieServerCategories(categories, emptyList()).map(Category::id),
        )
    }

    @Test
    fun `hidden category keeps its committed position while excluded from the visible list`() {
        val ordered = orderedMovieServerCategories(
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

        assertEquals(legacy, movieCategoryOrderAdoption(null, adoptionDone = false, legacy))
        assertNull(movieCategoryOrderAdoption(null, adoptionDone = true, legacy))
        assertNull(movieCategoryOrderAdoption(emptyList(), adoptionDone = false, legacy))
    }

    @Test
    fun `order round trip keeps stable ids without blanks or duplicates`() {
        assertTrue(decodeMovieCategoryOrderIds(null).isEmpty())
        assertTrue(decodeMovieCategoryOrderIds("").isEmpty())
        assertEquals(listOf("b", "a"), decodeMovieCategoryOrderIds(" b, a ,b"))
        assertEquals("b,a", encodeMovieCategoryOrderIds(listOf("b", "", "a", "b")))
        assertEquals("", encodeMovieCategoryOrderIds(emptyList()))
    }

    @Test
    fun `movie manager draft commits once and cancellation restores the committed order`() {
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
}
