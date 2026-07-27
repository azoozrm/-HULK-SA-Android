package sa.hulksa.player.ui.screens

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType

internal enum class TvSearchFocusAction { NONE, MOVE_TO_RESULTS, DISMISS_KEYBOARD }

internal fun tvSearchFocusAction(
    isTv: Boolean,
    eventType: KeyEventType,
    key: Key,
    hasResults: Boolean,
    imeVisible: Boolean,
): TvSearchFocusAction {
    if (!isTv || eventType != KeyEventType.KeyDown) return TvSearchFocusAction.NONE
    return when {
        key == Key.DirectionDown && hasResults -> TvSearchFocusAction.MOVE_TO_RESULTS
        key == Key.Back && imeVisible -> TvSearchFocusAction.DISMISS_KEYBOARD
        else -> TvSearchFocusAction.NONE
    }
}
