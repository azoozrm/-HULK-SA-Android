package sa.hulksa.player.ui.screens

/**
 * Pure v1.6 policy for deciding whether the Player Pro parent may open the channel browser.
 * The child player marks an option-panel selection cycle so the same OK key cannot fall through
 * and open the channel browser while Picture Size (or another options panel) is being used.
 */
internal data class LiveTvPanelFocusState(
    val controlsLikelyVisible: Boolean,
    val childSelectionPending: Boolean,
)

internal fun liveTvPanelOnControlSelect(state: LiveTvPanelFocusState): LiveTvPanelFocusState =
    state.copy(controlsLikelyVisible = true, childSelectionPending = true)

internal fun liveTvPanelOnDirectionalNavigation(state: LiveTvPanelFocusState): LiveTvPanelFocusState =
    if (state.childSelectionPending) {
        state.copy(controlsLikelyVisible = true)
    } else {
        state.copy(controlsLikelyVisible = false)
    }

internal fun liveTvPanelShouldOpenBrowser(state: LiveTvPanelFocusState): Boolean =
    !state.controlsLikelyVisible && !state.childSelectionPending

internal fun liveTvPanelConsumeChildSelection(state: LiveTvPanelFocusState): LiveTvPanelFocusState =
    state.copy(controlsLikelyVisible = true, childSelectionPending = false)
