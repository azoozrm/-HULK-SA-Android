package sa.hulksa.player.ui.screens

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester

/**
 * Saves the exact focused child of one scrollable/lazy focus boundary when focus leaves it.
 *
 * Compose lazy and `verticalScroll` containers install an implicit non-focusable focus target but
 * no focus restorer. Saving each boundary's direct child while it still has focus lets the outer
 * content group's `restoreFocusedChild()` walk the complete saved path back to the originating
 * item/control on a same-section rail dismissal. This modifier only saves; ordinary D-pad entry,
 * section entry and explicit navigation restoration keep their existing owners.
 */
internal fun Modifier.saveFocusedChildOnExit(requester: FocusRequester): Modifier =
    focusRequester(requester).focusProperties {
        onExit = { requester.saveFocusedChild() }
    }
