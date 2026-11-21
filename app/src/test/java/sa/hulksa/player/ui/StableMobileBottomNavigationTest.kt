package sa.hulksa.player.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class StableMobileBottomNavigationTest {
    @Test
    fun normalCompactPhoneWidthsKeepOneRowAndTheSmallestWidthFallsBack() {
        listOf(360, 393).forEach { width ->
            assertEquals(1, stableMobileNavigationRows(width, downloadsEnabled = true))
            assertEquals(1, stableMobileNavigationRows(width, downloadsEnabled = false))
        }
        assertEquals(2, stableMobileNavigationRows(320, downloadsEnabled = true))
    }

    @Test
    fun genuinelyConstrainedWidthFallsBackToTwoRowsInsteadOfLosingControls() {
        assertEquals(2, stableMobileNavigationRows(280, downloadsEnabled = true))
        assertEquals(2, stableMobileNavigationRows(230, downloadsEnabled = false))
    }

    @Test
    fun reservedHeightKeepsTheSingleRowBaselineAndGrowsWithTheFallback() {
        assertEquals(68.dp, stableMobileNavigationReservedHeight(360, downloadsEnabled = true))
        assertEquals(68.dp, stableMobileNavigationReservedHeight(360, downloadsEnabled = false))
        assertEquals(124.dp, stableMobileNavigationReservedHeight(280, downloadsEnabled = true))
    }

    @Test
    fun entryCountFollowsTheRealDownloadsAvailabilityGate() {
        assertEquals(7, stableMobileEntryCount(downloadsEnabled = true))
        assertEquals(6, stableMobileEntryCount(downloadsEnabled = false))
    }
}
