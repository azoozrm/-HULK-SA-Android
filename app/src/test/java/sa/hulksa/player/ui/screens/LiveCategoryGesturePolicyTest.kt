package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveCategoryGesturePolicyTest {
    @Test
    fun `consumed release before long press never toggles`() {
        // The R8 regression: changedToUpIgnoreConsumed() ignored consumption, so this consumed
        // (synthetic/cancelled) release invoked the visibility toggle.
        assertEquals(
            LiveCategoryGestureDecision.CANCEL,
            liveCategoryPreLongPressDecision(
                hasChange = true,
                pressed = false,
                consumed = true,
                movedBeyondSlop = false,
            ),
        )
    }

    @Test
    fun `unconsumed release before long press toggles once`() {
        assertEquals(
            LiveCategoryGestureDecision.TOGGLE,
            liveCategoryPreLongPressDecision(
                hasChange = true,
                pressed = false,
                consumed = false,
                movedBeyondSlop = false,
            ),
        )
    }

    @Test
    fun `consumed change while waiting for long press cancels without a drag`() {
        assertEquals(
            LiveCategoryGestureDecision.CANCEL,
            liveCategoryPreLongPressDecision(
                hasChange = true,
                pressed = true,
                consumed = true,
                movedBeyondSlop = false,
            ),
        )
    }

    @Test
    fun `slop movement before long press keeps ordinary scrolling`() {
        assertEquals(
            LiveCategoryGestureDecision.CANCEL,
            liveCategoryPreLongPressDecision(
                hasChange = true,
                pressed = true,
                consumed = false,
                movedBeyondSlop = true,
            ),
        )
    }

    @Test
    fun `missing pointer before long press cancels`() {
        assertEquals(
            LiveCategoryGestureDecision.CANCEL,
            liveCategoryPreLongPressDecision(
                hasChange = false,
                pressed = false,
                consumed = false,
                movedBeyondSlop = false,
            ),
        )
    }

    @Test
    fun `pressed unconsumed change keeps waiting for long press`() {
        assertEquals(
            LiveCategoryGestureDecision.CONTINUE,
            liveCategoryPreLongPressDecision(
                hasChange = true,
                pressed = true,
                consumed = false,
                movedBeyondSlop = false,
            ),
        )
    }

    @Test
    fun `consumed release during drag never drops`() {
        // The R8 regression: a consumed/cancelled release called onDrop/commitMove, and dropped=true
        // suppressed the finally/onDragCancel restore.
        assertEquals(
            LiveCategoryGestureDecision.CANCEL,
            liveCategoryDragDecision(
                hasChange = true,
                pressed = false,
                consumed = true,
            ),
        )
    }

    @Test
    fun `unconsumed release during drag drops once`() {
        assertEquals(
            LiveCategoryGestureDecision.DROP,
            liveCategoryDragDecision(
                hasChange = true,
                pressed = false,
                consumed = false,
            ),
        )
    }

    @Test
    fun `consumed movement during drag cancels`() {
        assertEquals(
            LiveCategoryGestureDecision.CANCEL,
            liveCategoryDragDecision(
                hasChange = true,
                pressed = true,
                consumed = true,
            ),
        )
    }

    @Test
    fun `unconsumed movement during drag continues`() {
        assertEquals(
            LiveCategoryGestureDecision.CONTINUE,
            liveCategoryDragDecision(
                hasChange = true,
                pressed = true,
                consumed = false,
            ),
        )
    }

    @Test
    fun `missing pointer during drag cancels`() {
        assertEquals(
            LiveCategoryGestureDecision.CANCEL,
            liveCategoryDragDecision(
                hasChange = false,
                pressed = false,
                consumed = false,
            ),
        )
    }

    @Test
    fun `cancel after a crossing restores the committed order with no commit callback`() {
        val draft = LiveCategoryOrderDraft(listOf("a", "b", "c"))
        draft.start()
        draft.move("c", 0)
        assertEquals(listOf("c", "a", "b"), draft.displayIds)

        // Consumed release -> CANCEL -> the existing draft cancel owner restores and never commits.
        assertEquals(
            LiveCategoryGestureDecision.CANCEL,
            liveCategoryDragDecision(hasChange = true, pressed = false, consumed = true),
        )
        draft.cancel()
        assertEquals(listOf("a", "b", "c"), draft.committedIds)
        assertEquals(listOf("a", "b", "c"), draft.displayIds)
        assertNull(draft.commit())
    }

    @Test
    fun `unconsumed release after a crossing commits the draft exactly once`() {
        val draft = LiveCategoryOrderDraft(listOf("a", "b", "c"))
        draft.start()
        draft.move("c", 0)

        assertEquals(
            LiveCategoryGestureDecision.DROP,
            liveCategoryDragDecision(hasChange = true, pressed = false, consumed = false),
        )
        assertEquals(listOf("c", "a", "b"), draft.commit())
        assertNull(draft.commit())
    }
}
