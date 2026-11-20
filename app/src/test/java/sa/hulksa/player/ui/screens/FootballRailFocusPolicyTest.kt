package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FootballRailFocusPolicyTest {

    @Test
    fun `physical left advances one RTL card from first and middle`() {
        assertEquals(1, footballRailAdjacentIndex(0, 4, forward = true))
        assertEquals(2, footballRailAdjacentIndex(1, 4, forward = true))
        assertEquals(3, footballRailAdjacentIndex(2, 4, forward = true))
    }

    @Test
    fun `physical right returns one card from middle and last`() {
        assertEquals(1, footballRailAdjacentIndex(2, 4, forward = false))
        assertEquals(0, footballRailAdjacentIndex(1, 4, forward = false))
    }

    @Test
    fun `both ends leave the rail instead of trapping or wrapping`() {
        assertNull(footballRailAdjacentIndex(3, 4, forward = true))
        assertNull(footballRailAdjacentIndex(0, 4, forward = false))
    }

    @Test
    fun `single card and invalid focus do not invent a neighbour`() {
        assertNull(footballRailAdjacentIndex(0, 1, forward = true))
        assertNull(footballRailAdjacentIndex(0, 1, forward = false))
        assertNull(footballRailAdjacentIndex(-1, 4, forward = true))
        assertNull(footballRailAdjacentIndex(4, 4, forward = false))
        assertNull(footballRailAdjacentIndex(0, 0, forward = true))
    }

    @Test
    fun `partially visible and offscreen cards require a reveal`() {
        assertTrue(footballFocusScrollDelta(950, 300, 0, 1_000, 6) > 0)
        assertTrue(footballFocusScrollDelta(1_001, 300, 0, 1_000, 6) > 0)
        assertTrue(footballFocusScrollDelta(-50, 300, 0, 1_000, 6) < 0)
        assertEquals(0, footballFocusScrollDelta(100, 300, 0, 1_000, 6))
    }

    @Test
    fun `reorder resolves by stable key and never by the old index`() {
        assertEquals(2, footballRailTargetIndex(listOf("a", "b", "c"), "c"))
        assertEquals(0, footballRailTargetIndex(listOf("c", "a", "b"), "c"))
        assertNull(footballRailTargetIndex(listOf("a", "b"), "c"))
    }

    @Test
    fun `one pixel margin deficit is accepted at a real scroll bound`() {
        // 5 dp row padding vs 6 dp focus margin: first card fully inside but one pixel short.
        assertTrue(
            footballRailFullyVisible(
                itemOffset = 5, itemSize = 302, viewportStart = 0, viewportEnd = 680,
                marginPx = 6, canScrollBackward = false, canScrollForward = true,
            ),
        )
        assertFalse(
            footballRailFullyVisible(
                itemOffset = 5, itemSize = 302, viewportStart = 0, viewportEnd = 680,
                marginPx = 6, canScrollBackward = true, canScrollForward = true,
            ),
        )
    }

    @Test
    fun `last card is accepted at the end bound and partial outside is not`() {
        assertTrue(
            footballRailFullyVisible(
                itemOffset = 373, itemSize = 302, viewportStart = 0, viewportEnd = 680,
                marginPx = 6, canScrollBackward = true, canScrollForward = false,
            ),
        )
        assertFalse(
            footballRailFullyVisible(
                itemOffset = 373, itemSize = 302, viewportStart = 0, viewportEnd = 680,
                marginPx = 6, canScrollBackward = true, canScrollForward = true,
            ),
        )
        assertFalse(
            footballRailFullyVisible(
                itemOffset = 379, itemSize = 302, viewportStart = 0, viewportEnd = 680,
                marginPx = 6, canScrollBackward = true, canScrollForward = false,
            ),
        )
    }
}
