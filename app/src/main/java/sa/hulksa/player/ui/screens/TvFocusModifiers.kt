package sa.hulksa.player.ui.screens

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

internal fun Modifier.liveFocusNavigation(
    slot: LiveFocusSlot,
    requesters: Map<LiveFocusSlot, FocusRequester>,
): Modifier {
    val requester = requesters[slot] ?: return this
    return focusRequester(requester).onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val direction = when (event.key) {
            Key.DirectionUp -> TvFocusDirection.UP
            Key.DirectionDown -> TvFocusDirection.DOWN
            Key.DirectionLeft -> TvFocusDirection.LEFT
            Key.DirectionRight -> TvFocusDirection.RIGHT
            else -> null
        } ?: return@onPreviewKeyEvent false
        val target = nextLiveFocusSlot(slot, direction) ?: return@onPreviewKeyEvent false
        runCatching { requesters[target]?.requestFocus() }.isSuccess
    }
}

internal fun Modifier.downloadFocusNavigationStrict(
    isTv: Boolean,
    node: DownloadFocusNode,
    rowCount: Int,
    requesters: Map<DownloadFocusNode, FocusRequester>,
    onBeforeRequest: ((DownloadFocusNode) -> Unit)? = null,
): Modifier {
    if (!isTv) return this
    val requester = requesters[node] ?: return this
    return focusRequester(requester).onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val direction = when (event.key) {
            Key.DirectionUp -> DownloadFocusDirection.UP
            Key.DirectionDown -> DownloadFocusDirection.DOWN
            Key.DirectionLeft -> DownloadFocusDirection.LEFT
            Key.DirectionRight -> DownloadFocusDirection.RIGHT
            else -> null
        } ?: return@onPreviewKeyEvent false
        val target = nextDownloadFocusNodeStrict(node, rowCount, direction)
            ?: return@onPreviewKeyEvent false
        onBeforeRequest?.invoke(target)
        runCatching { requesters[target]?.requestFocus() }.isSuccess
    }
}
