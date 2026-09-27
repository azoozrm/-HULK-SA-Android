package sa.hulksa.player.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.ui.theme.HulkColors

class EntryPresentationTest {
    private val colors = HulkColors()

    @Test
    fun `primary login action stays gold while focus adds only decoration`() {
        val idle = entryActionVisuals(colors = colors, focused = false, primary = true)
        val focused = entryActionVisuals(colors = colors, focused = true, primary = true)

        assertEquals(colors.gold, idle.background)
        assertEquals(colors.goldBright, focused.background)
        assertEquals(0.dp, idle.borderWidth)
        assertEquals(2.dp, focused.borderWidth)
    }

    @Test
    fun `secondary action stays visibly subordinate to the primary action`() {
        val primary = entryActionVisuals(colors = colors, focused = false, primary = true)
        val secondary = entryActionVisuals(colors = colors, focused = false)

        assertNotEquals(primary.background, secondary.background)
        assertNotEquals(primary.content, secondary.content)
    }

    @Test
    fun `selected state and focus state remain different visual meanings`() {
        val selected = entryActionVisuals(colors = colors, focused = false, selected = true)
        val focused = entryActionVisuals(colors = colors, focused = true)

        assertNotEquals(selected.background, focused.background)
        assertNotEquals(selected.content, focused.content)
        assertEquals(colors.goldBright, focused.border)
    }

    @Test
    fun `selected option keeps selection treatment when it is also focused`() {
        val selectedFocused = entryActionVisuals(colors = colors, focused = true, selected = true)

        assertEquals(colors.goldBright, selectedFocused.background)
        assertEquals(colors.goldBright, selectedFocused.border)
        assertEquals(2.dp, selectedFocused.borderWidth)
    }

    @Test
    fun `disabled action is muted without changing its footprint`() {
        val idle = entryActionVisuals(colors = colors, focused = false)
        val disabled = entryActionVisuals(colors = colors, focused = false, enabled = false)

        assertTrue(disabled.background.alpha < idle.background.alpha)
        assertNotEquals(idle.content, disabled.content)
        assertEquals(idle.borderWidth, disabled.borderWidth)
    }

    @Test
    fun `focus ring weight is the approved two dp contract`() {
        assertEquals(2.dp, entryActionVisuals(colors = colors, focused = true).borderWidth)
        assertEquals(2.dp, entryActionVisuals(colors = colors, focused = true, selected = true).borderWidth)
        assertEquals(2.dp, entryActionVisuals(colors = colors, focused = true, primary = true).borderWidth)
    }
}
