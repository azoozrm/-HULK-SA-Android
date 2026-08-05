package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadFocusPolicyTest {
    @Test
    fun `toolbar routes down into matching first card action`() {
        val routes = mapOf(
            DownloadFocusSlot.WIFI to DownloadFocusSlot.PRIMARY,
            DownloadFocusSlot.SCHEDULE to DownloadFocusSlot.PRIORITY,
            DownloadFocusSlot.CONCURRENT to DownloadFocusSlot.CANCEL,
        )

        routes.forEach { (toolbarSlot, cardSlot) ->
            assertEquals(
                DownloadFocusLocation.card(0, cardSlot),
                nextDownloadFocus(
                    rowCount = 3,
                    current = DownloadFocusLocation.toolbar(toolbarSlot),
                    move = DownloadFocusMove.DOWN,
                ),
            )
        }
    }

    @Test
    fun `toolbar horizontal traversal stays bounded in physical rtl order`() {
        val concurrent = DownloadFocusLocation.toolbar(DownloadFocusSlot.CONCURRENT)
        val schedule = DownloadFocusLocation.toolbar(DownloadFocusSlot.SCHEDULE)
        val wifi = DownloadFocusLocation.toolbar(DownloadFocusSlot.WIFI)

        assertEquals(schedule, nextDownloadFocus(3, concurrent, DownloadFocusMove.RIGHT))
        assertEquals(wifi, nextDownloadFocus(3, schedule, DownloadFocusMove.RIGHT))
        assertNull(nextDownloadFocus(3, wifi, DownloadFocusMove.RIGHT))

        assertEquals(schedule, nextDownloadFocus(3, wifi, DownloadFocusMove.LEFT))
        assertEquals(concurrent, nextDownloadFocus(3, schedule, DownloadFocusMove.LEFT))
        assertNull(nextDownloadFocus(3, concurrent, DownloadFocusMove.LEFT))

        assertNull(nextDownloadFocus(3, concurrent, DownloadFocusMove.UP))
        assertNull(nextDownloadFocus(3, schedule, DownloadFocusMove.UP))
        assertNull(nextDownloadFocus(3, wifi, DownloadFocusMove.UP))
    }

    @Test
    fun `all card actions traverse horizontally without wrapping`() {
        val primary = DownloadFocusLocation.card(1, DownloadFocusSlot.PRIMARY)
        val priority = DownloadFocusLocation.card(1, DownloadFocusSlot.PRIORITY)
        val cancel = DownloadFocusLocation.card(1, DownloadFocusSlot.CANCEL)

        assertEquals(priority, nextDownloadFocus(3, primary, DownloadFocusMove.LEFT))
        assertEquals(cancel, nextDownloadFocus(3, priority, DownloadFocusMove.LEFT))
        assertNull(nextDownloadFocus(3, cancel, DownloadFocusMove.LEFT))

        assertEquals(priority, nextDownloadFocus(3, cancel, DownloadFocusMove.RIGHT))
        assertEquals(primary, nextDownloadFocus(3, priority, DownloadFocusMove.RIGHT))
        assertNull(nextDownloadFocus(3, primary, DownloadFocusMove.RIGHT))
    }

    @Test
    fun `vertical traversal preserves every action column across every row`() {
        val columns = mapOf(
            DownloadFocusSlot.PRIMARY to DownloadFocusSlot.WIFI,
            DownloadFocusSlot.PRIORITY to DownloadFocusSlot.SCHEDULE,
            DownloadFocusSlot.CANCEL to DownloadFocusSlot.CONCURRENT,
        )

        for (rowCount in 1..4) {
            columns.forEach { (cardSlot, toolbarSlot) ->
                for (row in 0 until rowCount) {
                    val current = DownloadFocusLocation.card(row, cardSlot)
                    val expectedUp = if (row == 0) {
                        DownloadFocusLocation.toolbar(toolbarSlot)
                    } else {
                        DownloadFocusLocation.card(row - 1, cardSlot)
                    }
                    val expectedDown = if (row + 1 < rowCount) {
                        DownloadFocusLocation.card(row + 1, cardSlot)
                    } else {
                        null
                    }

                    assertEquals(
                        "rowCount=$rowCount row=$row slot=$cardSlot up",
                        expectedUp,
                        nextDownloadFocus(rowCount, current, DownloadFocusMove.UP),
                    )
                    assertEquals(
                        "rowCount=$rowCount row=$row slot=$cardSlot down",
                        expectedDown,
                        nextDownloadFocus(rowCount, current, DownloadFocusMove.DOWN),
                    )
                }
            }
        }
    }

    @Test
    fun `empty download list blocks every toolbar route`() {
        listOf(
            DownloadFocusSlot.WIFI,
            DownloadFocusSlot.SCHEDULE,
            DownloadFocusSlot.CONCURRENT,
        ).forEach { slot ->
            assertNull(
                nextDownloadFocus(
                    rowCount = 0,
                    current = DownloadFocusLocation.toolbar(slot),
                    move = DownloadFocusMove.DOWN,
                ),
            )
        }
    }
}
