package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRefreshFocusPolicyTest {

    @Test
    fun refreshClaimsHomeFocusBeforeAndDuringLoading() {
        assertEquals(
            HomeRefreshFocusAction.HOLD_PLACEHOLDER,
            homeRefreshFocusAction(
                isTv = true,
                loading = false,
                refreshRequested = true,
                refreshFocusHeld = false,
                placeholderFocused = false,
            ),
        )
        assertEquals(
            HomeRefreshFocusAction.HOLD_PLACEHOLDER,
            homeRefreshFocusAction(
                isTv = true,
                loading = true,
                refreshRequested = true,
                refreshFocusHeld = false,
                placeholderFocused = false,
            ),
        )
        assertEquals(
            HomeRefreshFocusAction.NONE,
            homeRefreshFocusAction(
                isTv = false,
                loading = true,
                refreshRequested = true,
                refreshFocusHeld = false,
                placeholderFocused = false,
            ),
        )
    }

    @Test
    fun completionRestoresOnlyWhenHomeStillOwnsFocus() {
        assertEquals(
            HomeRefreshFocusAction.RESTORE_HOME,
            homeRefreshFocusAction(
                isTv = true,
                loading = false,
                refreshRequested = false,
                refreshFocusHeld = true,
                placeholderFocused = true,
            ),
        )
        assertEquals(
            HomeRefreshFocusAction.NONE,
            homeRefreshFocusAction(
                isTv = true,
                loading = false,
                refreshRequested = false,
                refreshFocusHeld = true,
                placeholderFocused = false,
            ),
        )
    }

    @Test
    fun ordinaryLoadingDoesNotStealFocus() {
        assertEquals(
            HomeRefreshFocusAction.NONE,
            homeRefreshFocusAction(
                isTv = true,
                loading = true,
                refreshRequested = false,
                refreshFocusHeld = false,
                placeholderFocused = false,
            ),
        )
    }
}
