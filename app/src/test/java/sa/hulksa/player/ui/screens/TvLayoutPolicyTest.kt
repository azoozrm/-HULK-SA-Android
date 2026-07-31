package sa.hulksa.player.ui.screens

import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvLayoutPolicyTest {
    @Test
    fun `rail logo follows physical viewport instead of unreliable receiver density`() {
        assertEquals(30f, tvRailLogoSizeDp(screenWidthDp = 960), 0.001f)
        assertEquals(40f, tvRailLogoSizeDp(screenWidthDp = 1280), 0.001f)
        assertEquals(60f, tvRailLogoSizeDp(screenWidthDp = 1920), 0.001f)
    }

    @Test
    fun `rail logo remains bounded on unusually small or wide canvases`() {
        assertEquals(28f, tvRailLogoSizeDp(screenWidthDp = 640), 0.001f)
        assertEquals(60f, tvRailLogoSizeDp(screenWidthDp = 3840), 0.001f)
    }

    @Test
    fun `download card heights preserve the qualified TV layouts`() {
        assertEquals(164f, tvDownloadCardHeightDp(screenHeightDp = 360), 0.001f)
        assertEquals(164f, tvDownloadCardHeightDp(screenHeightDp = 540), 0.001f)
        assertEquals(188f, tvDownloadCardHeightDp(screenHeightDp = 541), 0.001f)
        assertEquals(188f, tvDownloadCardHeightDp(screenHeightDp = 1080), 0.001f)
    }

    @Test
    fun `toolbar focus descends to the physically aligned RTL action column`() {
        assertEquals(
            DownloadFocusNode(0, DownloadFocusSlot.PRIORITY),
            nextDownloadFocusNode(
                DownloadFocusNode(-1, DownloadFocusSlot.SCHEDULE),
                rowCount = 2,
                direction = DownloadFocusDirection.DOWN,
            ),
        )
        assertEquals(
            DownloadFocusNode(0, DownloadFocusSlot.PRIMARY),
            nextDownloadFocusNode(
                DownloadFocusNode(-1, DownloadFocusSlot.CONCURRENT),
                rowCount = 2,
                direction = DownloadFocusDirection.DOWN,
            ),
        )
    }

    @Test
    fun `download focus graph reaches every filter and every action in every row`() {
        val rowCount = 4
        val start = DownloadFocusNode(-1, DownloadFocusSlot.WIFI)
        val visited = linkedSetOf(start)
        val queue = ArrayDeque<DownloadFocusNode>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            DownloadFocusDirection.entries.forEach { direction ->
                nextDownloadFocusNode(current, rowCount, direction)?.let { target ->
                    if (visited.add(target)) queue.add(target)
                }
            }
        }
        assertEquals(3 + rowCount * 3, visited.size)
    }

    @Test
    fun `download focus graph has deterministic vertical and horizontal ordering`() {
        assertEquals(
            DownloadFocusNode(0, DownloadFocusSlot.CANCEL),
            nextDownloadFocusNode(
                DownloadFocusNode(-1, DownloadFocusSlot.WIFI),
                rowCount = 2,
                direction = DownloadFocusDirection.DOWN,
            ),
        )
        assertEquals(
            DownloadFocusNode(0, DownloadFocusSlot.PRIORITY),
            nextDownloadFocusNode(
                DownloadFocusNode(0, DownloadFocusSlot.PRIMARY),
                rowCount = 2,
                direction = DownloadFocusDirection.LEFT,
            ),
        )
        assertEquals(
            DownloadFocusNode(1, DownloadFocusSlot.CANCEL),
            nextDownloadFocusNode(
                DownloadFocusNode(0, DownloadFocusSlot.CANCEL),
                rowCount = 2,
                direction = DownloadFocusDirection.DOWN,
            ),
        )
        assertEquals(
            DownloadFocusNode(-1, DownloadFocusSlot.WIFI),
            nextDownloadFocusNode(
                DownloadFocusNode(0, DownloadFocusSlot.CANCEL),
                rowCount = 2,
                direction = DownloadFocusDirection.UP,
            ),
        )
        assertNull(
            nextDownloadFocusNode(
                DownloadFocusNode(1, DownloadFocusSlot.CANCEL),
                rowCount = 2,
                direction = DownloadFocusDirection.DOWN,
            ),
        )
    }
}
