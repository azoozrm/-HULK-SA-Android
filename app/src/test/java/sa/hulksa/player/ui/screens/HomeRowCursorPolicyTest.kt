package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRowCursorPolicyTest {
    @Test
    fun `home row cursor accounts for the hero block and the phone services row`() {
        assertEquals(1, homeRowCursorStart(isTv = true))
        assertEquals(2, homeRowCursorStart(isTv = false))
    }
}
