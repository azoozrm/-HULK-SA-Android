package sa.hulksa.player.ui.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryProgressAlignmentTest {
    @Test
    fun `played fill starts at the physical left under ltr and rtl`() {
        val fill = IntSize(66, 3)
        val track = IntSize(100, 3)

        val ltr = historyProgressPhysicalLeftAlignment.align(fill, track, LayoutDirection.Ltr)
        val rtl = historyProgressPhysicalLeftAlignment.align(fill, track, LayoutDirection.Rtl)

        assertEquals(IntOffset(0, 0), ltr)
        assertEquals(ltr, rtl)
    }

    @Test
    fun `truthful played fractions keep zero small two thirds and full values`() {
        assertEquals(0f, movieRecentPlayedFraction(0L, 100_000L), 0.0001f)
        assertEquals(0.0001f, movieRecentPlayedFraction(10L, 100_000L), 0.0001f)
        assertEquals(0.6666f, movieRecentPlayedFraction(20_000L, 30_000L), 0.0001f)
        assertEquals(1f, movieRecentPlayedFraction(100_000L, 100_000L), 0.0001f)
    }

    @Test
    fun `unknown and invalid durations keep the empty fill behavior`() {
        assertEquals(0f, movieRecentPlayedFraction(20_000L, 0L), 0.0001f)
        assertEquals(0f, movieRecentPlayedFraction(20_000L, -1L), 0.0001f)
    }
}
