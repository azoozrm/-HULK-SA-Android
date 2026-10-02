package sa.hulksa.player.ui.screens

import sa.hulksa.player.model.ContentItem
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Approved Live-player controls policy.
 *
 * Keeps the main-control geometry, the compact More panel state machine, panel input ownership
 * and the favorite/last-channel wiring pure and independently testable. Rendering stays in
 * PlayerScreen.kt; this file owns only deterministic decisions.
 */
internal enum class PlayerLiveMorePanelView {
    MENU,
    SOURCE,
    RESIZE,
}

internal enum class PlayerLiveMoreRow {
    MUTE,
    RELOAD,
    SOURCE,
    RESIZE,
}

/**
 * Back inside the More panel: a child view returns to MENU, MENU closes the panel.
 *
 * `null` nextView means the panel closes and the More trigger regains focus.
 */
internal fun playerLiveMorePanelBack(currentView: PlayerLiveMorePanelView): PlayerLiveMorePanelView? =
    when (currentView) {
        PlayerLiveMorePanelView.MENU -> null
        PlayerLiveMorePanelView.SOURCE,
        PlayerLiveMorePanelView.RESIZE,
        -> PlayerLiveMorePanelView.MENU
    }

/**
 * The MENU row that must regain focus when a child view returns to MENU. The panel is always
 * entered through a row, so the origin is deterministic.
 */
internal fun playerLiveMorePanelOriginRow(currentView: PlayerLiveMorePanelView): PlayerLiveMoreRow? =
    when (currentView) {
        PlayerLiveMorePanelView.MENU -> null
        PlayerLiveMorePanelView.SOURCE -> PlayerLiveMoreRow.SOURCE
        PlayerLiveMorePanelView.RESIZE -> PlayerLiveMoreRow.RESIZE
    }

/**
 * Whether the child player layer owns its panel navigation keys.
 *
 * The More panel is a child-owned surface exactly like the resize/source side panels, so the
 * outer Live TV Pro layer must not convert its D-pad input into zapping while it is open.
 */
internal fun playerChildPanelInputActive(
    hasActivePanel: Boolean,
    hasLiveMorePanel: Boolean,
): Boolean = hasActivePanel || hasLiveMorePanel

internal data class LivePlayerFavoriteControl(
    val enabled: Boolean,
    val favorite: Boolean,
)

/**
 * Favorite action wiring for the current Live catalog channel.
 *
 * The button is disabled when the playing stream has no authoritative catalog item, and it never
 * reports a favorite state for a missing target.
 */
internal fun livePlayerFavoriteControl(
    currentChannel: ContentItem?,
    isFavorite: Boolean,
): LivePlayerFavoriteControl = LivePlayerFavoriteControl(
    enabled = currentChannel != null,
    favorite = currentChannel != null && isFavorite,
)

/**
 * Last-channel action gating shared by the HUD button and the hardware Last Channel key.
 *
 * Must stay inside the existing Live TV Pro feature gate and only resolve when the profile-scoped
 * history still contains an available, non-current channel.
 */
internal fun playerProLiveLastChannelActionEnabled(
    isLive: Boolean,
    liveTvProEnabled: Boolean,
    hasLastChannel: Boolean,
): Boolean = isLive && liveTvProEnabled && hasLastChannel

internal data class LivePlayerControlsMetrics(
    val approvedSingleRow: Boolean,
    val centerButtonDp: Int,
    val secondaryButtonDp: Int,
    val circleIconDp: Int,
    val utilityIconDp: Int,
    val captionSizeSp: Int,
    val itemSpacingDp: Int,
    val morePanelWidthDp: Int,
    val morePanelBottomInsetDp: Int,
)

/**
 * Adaptive geometry for the approved Live controls and the More panel.
 *
 * Geometry is derived from the current window and the existing Live controls safe-area policy so
 * TV overscan, compact phones and wide touch windows all stay inside the viewport without
 * hardcoded device values.
 */
internal fun livePlayerControlsMetrics(
    screenWidthDp: Int,
    screenHeightDp: Int,
    remoteLayout: Boolean,
): LivePlayerControlsMetrics {
    val width = screenWidthDp.coerceAtLeast(1)
    val height = screenHeightDp.coerceAtLeast(1)
    val shortSide = min(width, height)
    val approvedSingleRow = remoteLayout || width >= 720
    val layout = liveControlsLayoutMetrics(width, height, remoteLayout)

    val centerButton = (shortSide * .115f).roundToInt().coerceIn(56, 92)
    val secondaryButton = (centerButton * .72f).roundToInt().coerceIn(42, 68)
    val circleIcon = (centerButton * .46f).roundToInt().coerceIn(24, 40)
    val utilityIcon = (shortSide * .03f).roundToInt().coerceIn(20, 30)
    val captionSize = when {
        shortSide >= 900 -> 15
        shortSide >= 600 -> 13
        else -> 11
    }
    val spacing = (shortSide * .008f).roundToInt().coerceIn(8, 16)
    val panelWidth = if (approvedSingleRow) {
        (width * .26f).roundToInt().coerceIn(300, 430)
    } else {
        (width * .86f).roundToInt().coerceIn(280, 430)
    }

    val singleRowHeight = centerButton + captionSize * 2 + 8
    val compactStackHeight = singleRowHeight + utilityIcon + captionSize * 2 + spacing
    val controlsStackHeight = if (approvedSingleRow) singleRowHeight else compactStackHeight
    val panelBottomInset = layout.outerBottomPaddingDp.roundToInt() + controlsStackHeight + spacing

    return LivePlayerControlsMetrics(
        approvedSingleRow = approvedSingleRow,
        centerButtonDp = centerButton,
        secondaryButtonDp = secondaryButton,
        circleIconDp = circleIcon,
        utilityIconDp = utilityIcon,
        captionSizeSp = captionSize,
        itemSpacingDp = spacing,
        morePanelWidthDp = panelWidth,
        morePanelBottomInsetDp = panelBottomInset,
    )
}
