package sa.hulksa.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.HulkScreen
import sa.hulksa.player.MainDestination

class SidebarNavigationFocusHandoffPolicyTest {
    @Test
    fun `home movies series and live transitions request TV shell handoff`() {
        val transitions = listOf(
            MainDestination.HOME to MainDestination.MOVIES,
            MainDestination.MOVIES to MainDestination.SERIES,
            MainDestination.SERIES to MainDestination.LIVE,
            MainDestination.LIVE to MainDestination.HOME,
        )

        transitions.forEach { (previous, current) ->
            assertTrue(
                shouldAttemptTvSidebarFocusHandoff(
                    isTv = true,
                    screen = HulkScreen.MAIN,
                    previousDestination = previous,
                    currentDestination = current,
                ),
            )
        }
    }

    @Test
    fun `other persistent rail destinations use the same handoff policy`() {
        val destinations = listOf(
            MainDestination.FAVORITES,
            MainDestination.DOWNLOADS,
            MainDestination.SETTINGS,
        )

        destinations.forEach { destination ->
            assertTrue(
                shouldAttemptTvSidebarFocusHandoff(
                    isTv = true,
                    screen = HulkScreen.MAIN,
                    previousDestination = MainDestination.HOME,
                    currentDestination = destination,
                ),
            )
        }
    }

    @Test
    fun `same destination keeps current behavior without navigation handoff churn`() {
        assertFalse(
            shouldAttemptTvSidebarFocusHandoff(
                isTv = true,
                screen = HulkScreen.MAIN,
                previousDestination = MainDestination.MOVIES,
                currentDestination = MainDestination.MOVIES,
            ),
        )
    }

    @Test
    fun `search relies on its existing shell disposal and search focus ownership`() {
        assertFalse(
            shouldAttemptTvSidebarFocusHandoff(
                isTv = true,
                screen = HulkScreen.MAIN,
                previousDestination = MainDestination.HOME,
                currentDestination = MainDestination.SEARCH,
            ),
        )
        assertFalse(
            shouldAttemptTvSidebarFocusHandoff(
                isTv = true,
                screen = HulkScreen.MAIN,
                previousDestination = MainDestination.SEARCH,
                currentDestination = MainDestination.LIVE,
            ),
        )
    }

    @Test
    fun `handoff is TV main shell only`() {
        assertFalse(
            shouldAttemptTvSidebarFocusHandoff(
                isTv = false,
                screen = HulkScreen.MAIN,
                previousDestination = MainDestination.HOME,
                currentDestination = MainDestination.LIVE,
            ),
        )
        assertFalse(
            shouldAttemptTvSidebarFocusHandoff(
                isTv = true,
                screen = HulkScreen.PLAYER,
                previousDestination = MainDestination.HOME,
                currentDestination = MainDestination.LIVE,
            ),
        )
    }

    @Test
    fun `tracker advances monotonically across rapid destination changes`() {
        val tracker = SidebarNavigationFocusHandoffTracker(MainDestination.HOME)

        assertEquals(MainDestination.HOME, tracker.takePrevious(MainDestination.MOVIES))
        assertEquals(MainDestination.MOVIES, tracker.takePrevious(MainDestination.SERIES))
        assertEquals(MainDestination.SERIES, tracker.takePrevious(MainDestination.LIVE))
    }
}
