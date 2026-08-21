package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectedCategoryFocusPolicyTest {
    @Test
    fun catalogSpecialCategoriesResolveToTheirExactSlots() {
        val leading = listOf<String?>(null, "favorites", "continue")
        assertEquals(0, selectedCategoryFocusIndex(null, leading, listOf("a", "b")))
        assertEquals(1, selectedCategoryFocusIndex("favorites", leading, listOf("a", "b")))
        assertEquals(2, selectedCategoryFocusIndex("continue", leading, listOf("a", "b")))
    }

    @Test
    fun farCatalogCategoryResolvesAfterLeadingSlots() {
        val ordered = (0..60).map { "category-$it" }
        assertEquals(50, selectedCategoryFocusIndex("category-47", listOf(null, "favorites", "continue"), ordered))
    }

    @Test
    fun liveCategoryUsesItsOwnLeadingSlots() {
        val ordered = (0..60).map { "live-$it" }
        assertEquals(49, selectedCategoryFocusIndex("live-47", listOf(null, "favorites"), ordered))
    }

    @Test
    fun missingCategoryDoesNotInventAFocusTarget() {
        assertNull(selectedCategoryFocusIndex("missing", listOf(null, "favorites"), listOf("a", "b")))
    }
}
