package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun restoreWaitsForBothScrollCompletionAndExactTargetPlacement() {
        val controller = CategoryFocusRestoreController()
        val request = controller.begin("category-47")

        assertNull(controller.readyRequestId("category-47"))
        controller.markScrollCompleted(request.requestId)
        assertNull(controller.readyRequestId("category-47"))

        controller.markTargetPlaced("category-47")
        assertEquals(request.requestId, controller.readyRequestId("category-47"))
        assertNull(controller.readyRequestId("category-46"))
    }

    @Test
    fun allCategoryNullIdIsNotMistakenForAnAbsentPendingRequest() {
        val controller = CategoryFocusRestoreController()

        assertFalse(controller.hasPendingTarget(null))
        val request = controller.begin(null)
        assertTrue(controller.hasPendingTarget(null))

        controller.markScrollCompleted(request.requestId)
        assertNull(controller.readyRequestId(null))
        controller.markTargetPlaced(null)
        assertEquals(request.requestId, controller.readyRequestId(null))
    }

    @Test
    fun pendingRestoreAllowsOnlyTheSelectedCategoryEvenAfterTheGroupEnters() {
        assertTrue(canCategoryChipReceiveFocus(true, true, true, "selected", "selected"))
        assertFalse(canCategoryChipReceiveFocus(true, true, true, "selected", "visible-neighbor"))
        assertTrue(canCategoryChipReceiveFocus(true, true, false, "selected", "visible-neighbor"))
        assertTrue(canCategoryChipReceiveFocus(false, false, true, "selected", "visible-neighbor"))
    }

    @Test
    fun restoreTargetUsesTheCurrentIdResolvedAfterReorder() {
        val reordered = listOf("d", "b", "a", "selected", "c")
        val currentIndex = selectedCategoryFocusIndex(
            selectedId = "selected",
            leadingIds = listOf(null, "favorites", "continue"),
            orderedIds = reordered,
        )

        assertEquals(6, currentIndex)
    }
}
