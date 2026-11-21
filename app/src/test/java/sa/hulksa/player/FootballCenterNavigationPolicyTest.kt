package sa.hulksa.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The football Center is a real destination only while Operations advertises the feed. A withdrawn
 * feed must not leave the user on an unavailable screen, mirroring the existing Downloads gate.
 */
class FootballCenterNavigationPolicyTest {
    @Test
    fun withdrawnFootballOrDownloadsReturnsToHome() {
        assertEquals(
            MainDestination.HOME,
            safeDestinationForOperations(
                destination = MainDestination.FOOTBALL,
                downloadsEnabled = true,
                footballAvailable = false,
            ),
        )
        assertEquals(
            MainDestination.HOME,
            safeDestinationForOperations(
                destination = MainDestination.DOWNLOADS,
                downloadsEnabled = false,
                footballAvailable = true,
            ),
        )
    }

    @Test
    fun availableSurfacesAndUnrelatedDestinationsKeepTheirSelection() {
        assertEquals(
            MainDestination.FOOTBALL,
            safeDestinationForOperations(
                destination = MainDestination.FOOTBALL,
                downloadsEnabled = true,
                footballAvailable = true,
            ),
        )
        assertEquals(
            MainDestination.DOWNLOADS,
            safeDestinationForOperations(
                destination = MainDestination.DOWNLOADS,
                downloadsEnabled = true,
                footballAvailable = false,
            ),
        )
        assertEquals(
            MainDestination.HOME,
            safeDestinationForOperations(
                destination = MainDestination.HOME,
                downloadsEnabled = false,
                footballAvailable = false,
            ),
        )
        assertEquals(
            MainDestination.MOVIES,
            safeDestinationForOperations(
                destination = MainDestination.MOVIES,
                downloadsEnabled = false,
                footballAvailable = false,
            ),
        )
    }
}
