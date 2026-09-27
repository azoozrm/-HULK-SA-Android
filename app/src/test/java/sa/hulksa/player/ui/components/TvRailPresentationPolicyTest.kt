package sa.hulksa.player.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
}
