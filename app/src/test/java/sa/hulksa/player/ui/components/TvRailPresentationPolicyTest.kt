package sa.hulksa.player.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvRailPresentationPolicyTest {
    @Test
    fun `selection and focus remain visually distinct meanings`() {
        val idle = tvRailVisualState(selected = false, highlighted = false)
        val selected = tvRailVisualState(selected = true, highlighted = false)
        val focused = tvRailVisualState(selected = false, highlighted = true)
        val selectedFocused = tvRailVisualState(selected = true, highlighted = true)

        assertEquals(TvRailVisualState.IDLE, idle)
        assertEquals(TvRailVisualState.SELECTED, selected)
        assertEquals(TvRailVisualState.FOCUSED, focused)
        assertEquals(TvRailVisualState.SELECTED_FOCUSED, selectedFocused)
        assertNotEquals(selected, focused)
        assertNotEquals(selectedFocused, selected)
        assertNotEquals(selectedFocused, focused)
    }

    @Test
    fun `selected item that is also focused combines instead of inventing another state`() {
        assertEquals(
            TvRailVisualState.SELECTED_FOCUSED,
            tvRailVisualState(selected = true, highlighted = true),
        )
    }

    @Test
    fun `expansion duration matches the approved bounded transition`() {
        assertEquals(160, TV_RAIL_EXPANSION_DURATION_MILLIS)
    }

    @Test
    fun `rtl overlay keeps its start edge on screen and expands only leftward`() {
        val reported = 136
        val expanded = 314

        val offset = tvRailOverlayAnchorOffsetPx(
            reportedWidthPx = reported,
            surfaceWidthPx = expanded,
            isRtl = true,
        )

        assertEquals(reported - expanded, offset)
        assertTrue(offset < 0)
        assertEquals(reported, offset + expanded)
    }

    @Test
    fun `overlay start edge stays anchored at every expansion frame`() {
        val reported = 136
        listOf(136, 180, 220, 270, 314).forEach { surface ->
            val rtl = tvRailOverlayAnchorOffsetPx(reported, surface, isRtl = true)
            val ltr = tvRailOverlayAnchorOffsetPx(reported, surface, isRtl = false)

            assertEquals(reported, rtl + surface)
            assertEquals(0, ltr)
        }
    }

    @Test
    fun `ltr overlay keeps its layout start edge without offset`() {
        assertEquals(
            0,
            tvRailOverlayAnchorOffsetPx(
                reportedWidthPx = 136,
                surfaceWidthPx = 314,
                isRtl = false,
            ),
        )
    }

    @Test
    fun `content edge scrim leaves no residual mask when the rail is collapsed`() {
        assertEquals(0f, tvRailEdgeScrimAlpha(0f), 0.001f)
    }

    @Test
    fun `content edge scrim tracks expansion with a bounded alpha`() {
        assertEquals(0.5f, tvRailEdgeScrimAlpha(0.5f), 0.001f)
        assertEquals(0f, tvRailEdgeScrimAlpha(-0.5f), 0.001f)
        assertEquals(1f, tvRailEdgeScrimAlpha(1.4f), 0.001f)
    }
}
