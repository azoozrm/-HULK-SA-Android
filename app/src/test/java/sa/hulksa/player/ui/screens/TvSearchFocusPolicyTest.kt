package sa.hulksa.player.ui.screens

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import org.junit.Assert.assertEquals
import org.junit.Test

class TvSearchFocusPolicyTest {
    @Test fun downMovesToResultsOnlyOnTvWithResults() {
        assertEquals(TvSearchFocusAction.MOVE_TO_RESULTS, tvSearchFocusAction(true, KeyEventType.KeyDown, Key.DirectionDown, true, false))
        assertEquals(TvSearchFocusAction.NONE, tvSearchFocusAction(true, KeyEventType.KeyDown, Key.DirectionDown, false, false))
        assertEquals(TvSearchFocusAction.NONE, tvSearchFocusAction(false, KeyEventType.KeyDown, Key.DirectionDown, true, false))
    }

    @Test fun backDismissesOnlyVisibleTvKeyboard() {
        assertEquals(TvSearchFocusAction.DISMISS_KEYBOARD, tvSearchFocusAction(true, KeyEventType.KeyDown, Key.Back, true, true))
        assertEquals(TvSearchFocusAction.NONE, tvSearchFocusAction(true, KeyEventType.KeyDown, Key.Back, true, false))
    }

    @Test fun keyUpNeverChangesFocus() {
        assertEquals(TvSearchFocusAction.NONE, tvSearchFocusAction(true, KeyEventType.KeyUp, Key.DirectionDown, true, true))
    }
}
