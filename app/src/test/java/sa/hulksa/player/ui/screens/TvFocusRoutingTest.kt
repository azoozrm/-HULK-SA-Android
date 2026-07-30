package sa.hulksa.player.ui.screens

import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvFocusRoutingTest {
    @Test
    fun `live focus graph reaches channel play and favorite deterministically`() {
        val visited = linkedSetOf(LiveFocusSlot.CHANNEL)
        val queue = ArrayDeque<LiveFocusSlot>()
        queue.add(LiveFocusSlot.CHANNEL)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            TvFocusDirection.entries.forEach { direction ->
                nextLiveFocusSlot(current, direction)?.let { target ->
                    if (visited.add(target)) queue.add(target)
                }
            }
        }

        assertEquals(setOf(LiveFocusSlot.CHANNEL, LiveFocusSlot.PLAY, LiveFocusSlot.FAVORITE), visited)
        assertEquals(LiveFocusSlot.PLAY, nextLiveFocusSlot(LiveFocusSlot.CHANNEL, TvFocusDirection.LEFT))
        assertEquals(LiveFocusSlot.FAVORITE, nextLiveFocusSlot(LiveFocusSlot.PLAY, TvFocusDirection.LEFT))
        assertEquals(LiveFocusSlot.PLAY, nextLiveFocusSlot(LiveFocusSlot.FAVORITE, TvFocusDirection.RIGHT))
        assertEquals(LiveFocusSlot.CHANNEL, nextLiveFocusSlot(LiveFocusSlot.PLAY, TvFocusDirection.RIGHT))
    }

    @Test
    fun `download focus graph covers all filters and actions`() {
        val rowCount = 3
        val start = DownloadFocusNode(-1, DownloadFocusSlot.WIFI)
        val visited = linkedSetOf(start)
        val queue = ArrayDeque<DownloadFocusNode>()
        queue.add(start)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            DownloadFocusDirection.entries.forEach { direction ->
                nextDownloadFocusNodeStrict(current, rowCount, direction)?.let { target ->
                    if (visited.add(target)) queue.add(target)
                }
            }
        }

        assertEquals(3 + rowCount * 3, visited.size)
    }

    @Test
    fun `download navigation preserves action column across rows`() {
        assertEquals(
            DownloadFocusNode(1, DownloadFocusSlot.PRIMARY),
            nextDownloadFocusNodeStrict(
                DownloadFocusNode(0, DownloadFocusSlot.PRIMARY),
                rowCount = 2,
                direction = DownloadFocusDirection.DOWN,
            ),
        )
        assertEquals(
            DownloadFocusNode(1, DownloadFocusSlot.PRIORITY),
            nextDownloadFocusNodeStrict(
                DownloadFocusNode(0, DownloadFocusSlot.PRIORITY),
                rowCount = 2,
                direction = DownloadFocusDirection.DOWN,
            ),
        )
        assertEquals(
            DownloadFocusNode(1, DownloadFocusSlot.CANCEL),
            nextDownloadFocusNodeStrict(
                DownloadFocusNode(0, DownloadFocusSlot.CANCEL),
                rowCount = 2,
                direction = DownloadFocusDirection.DOWN,
            ),
        )
        assertNull(
            nextDownloadFocusNodeStrict(
                DownloadFocusNode(1, DownloadFocusSlot.CANCEL),
                rowCount = 2,
                direction = DownloadFocusDirection.DOWN,
            ),
        )
    }
}
