package sa.hulksa.player.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class StableMobileBottomNavigationTest {
    @Test
    fun everyNormalCompactPhoneWidthKeepsOneRowWithAllVisibleDestinations() {
        listOf(320, 360, 393).forEach { width ->
            assertEquals(1, stableMobileNavigationRows(width, downloadsEnabled = true))
            assertEquals(1, stableMobileNavigationRows(width, downloadsEnabled = false))
        }
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
        assertEquals(6, stableMobileEntryCount(downloadsEnabled = true))
        assertEquals(5, stableMobileEntryCount(downloadsEnabled = false))
    }
}
