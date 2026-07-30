package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadFocusPolicyTest {
    @Test fun `toolbar routes down into matching first card action`() {
        assertEquals(DownloadFocusLocation.card(0, DownloadFocusSlot.PRIMARY), nextDownloadFocus(3, DownloadFocusLocation.toolbar(DownloadFocusSlot.WIFI), DownloadFocusMove.DOWN))
        assertEquals(DownloadFocusLocation.card(0, DownloadFocusSlot.PRIORITY), nextDownloadFocus(3, DownloadFocusLocation.toolbar(DownloadFocusSlot.SCHEDULE), DownloadFocusMove.DOWN))
        assertEquals(DownloadFocusLocation.card(0, DownloadFocusSlot.CANCEL), nextDownloadFocus(3, DownloadFocusLocation.toolbar(DownloadFocusSlot.CONCURRENT), DownloadFocusMove.DOWN))
    }

    @Test fun `all actions traverse horizontally in physical rtl order`() {
        val primary = DownloadFocusLocation.card(1, DownloadFocusSlot.PRIMARY)
        val priority = DownloadFocusLocation.card(1, DownloadFocusSlot.PRIORITY)
        val cancel = DownloadFocusLocation.card(1, DownloadFocusSlot.CANCEL)
        assertEquals(priority, nextDownloadFocus(3, primary, DownloadFocusMove.LEFT))
        assertEquals(cancel, nextDownloadFocus(3, priority, DownloadFocusMove.LEFT))
        assertEquals(priority, nextDownloadFocus(3, cancel, DownloadFocusMove.RIGHT))
        assertEquals(primary, nextDownloadFocus(3, priority, DownloadFocusMove.RIGHT))
    }

    @Test fun `vertical traversal preserves action column between downloads`() {
        listOf(DownloadFocusSlot.PRIMARY, DownloadFocusSlot.PRIORITY, DownloadFocusSlot.CANCEL).forEach { slot ->
            assertEquals(DownloadFocusLocation.card(1, slot), nextDownloadFocus(3, DownloadFocusLocation.card(0, slot), DownloadFocusMove.DOWN))
            assertEquals(DownloadFocusLocation.card(0, slot), nextDownloadFocus(3, DownloadFocusLocation.card(1, slot), DownloadFocusMove.UP))
        }
    }

    @Test fun `first row returns to toolbar and final row does not escape`() {
        assertEquals(DownloadFocusLocation.toolbar(DownloadFocusSlot.WIFI), nextDownloadFocus(2, DownloadFocusLocation.card(0, DownloadFocusSlot.PRIMARY), DownloadFocusMove.UP))
        assertEquals(DownloadFocusLocation.toolbar(DownloadFocusSlot.SCHEDULE), nextDownloadFocus(2, DownloadFocusLocation.card(0, DownloadFocusSlot.PRIORITY), DownloadFocusMove.UP))
        assertEquals(DownloadFocusLocation.toolbar(DownloadFocusSlot.CONCURRENT), nextDownloadFocus(2, DownloadFocusLocation.card(0, DownloadFocusSlot.CANCEL), DownloadFocusMove.UP))
        assertNull(nextDownloadFocus(0, DownloadFocusLocation.toolbar(DownloadFocusSlot.WIFI), DownloadFocusMove.DOWN))
        assertNull(nextDownloadFocus(2, DownloadFocusLocation.card(1, DownloadFocusSlot.PRIMARY), DownloadFocusMove.DOWN))
    }
}
