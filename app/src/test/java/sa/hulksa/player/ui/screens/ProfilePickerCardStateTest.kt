package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProfilePickerCardStateTest {
    @Test
    fun `active profile and current focus are distinct visual states`() {
        val active = profileCardVisualState(active = true, focused = false)
        val focused = profileCardVisualState(active = false, focused = true)

        assertNotEquals(active, focused)
        assertEquals(ProfileCardVisualState.ACTIVE, active)
        assertEquals(ProfileCardVisualState.FOCUSED, focused)
    }

    @Test
    fun `active profile keeps its meaning while focused`() {
        assertEquals(
            ProfileCardVisualState.ACTIVE_FOCUSED,
            profileCardVisualState(active = true, focused = true),
        )
    }

    @Test
    fun `inactive and unfocused card stays idle`() {
        assertEquals(
            ProfileCardVisualState.IDLE,
            profileCardVisualState(active = false, focused = false),
        )
    }
}
