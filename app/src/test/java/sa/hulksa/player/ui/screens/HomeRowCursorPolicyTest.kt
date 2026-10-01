package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRowCursorPolicyTest {
    @Test
    fun `home row cursor starts content rows after the hero block`() {
        assertEquals(1, homeRowCursorStart(isTv = true))
        assertEquals(1, homeRowCursorStart(isTv = false))
    }
}
