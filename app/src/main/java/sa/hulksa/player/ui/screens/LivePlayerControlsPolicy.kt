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
    val transportIconDp: Int,
    val transportContainerDp: Int,
    val utilityIconDp: Int,
    val captionSizeSp: Int,
    val itemSpacingDp: Int,
    val morePanelWidthDp: Int,
)

/**
 * Live-local aspect labels. The owner text rule removes hamzas/madda from ALEF only, so the
 * originally approved `ملائم` and `ملء الشاشة` (hamza on ء is preserved) remain the Live labels.
 */
internal val LIVE_PLAYER_RESIZE_LABELS = listOf("ملائم", "تكبير", "ملء الشاشة")

internal fun livePlayerResizeLabel(index: Int): String = when (index) {
    1 -> "تكبير"
    2 -> "ملء الشاشة"
    else -> "ملائم"
}

/**
 * Deterministic strip-height reservation used before the strip reports its measured height.
 *
 * The value mirrors the compact strip composition (top/bottom safe padding, the shared transport
 * container, the caption gap and one caption line) so panel space is never allocated on top of the
 * strip even on the first frame.
 */
internal fun livePlayerReservedStripHeightDp(
    transportContainerDp: Int,
    captionSizeSp: Int,
    outerTopPaddingDp: Float,
    outerBottomPaddingDp: Float,
): Float = outerTopPaddingDp + outerBottomPaddingDp + transportContainerDp + 4f + captionSizeSp * 1.6f

internal const val PLAYER_ERROR_ACTION_GAP_DP = 9

/**
 * Live error action grid: up to four actions per centered row on television/wide windows, two per
 * row on compact windows so every button keeps readable captions and equal size.
 */
internal fun playerErrorActionColumns(
    television: Boolean,
    screenWidthDp: Int,
): Int = if (television || screenWidthDp >= 600) 4 else 2

internal fun playerErrorActionHeightDp(television: Boolean): Int = if (television) 44 else 40

/** Equal-width Live error actions per row; the last row may hold fewer actions and stays centered. */
internal fun playerErrorActionRows(actionCount: Int, columns: Int): List<List<Int>> {
    if (actionCount <= 0 || columns <= 0) return emptyList()
    return (0 until actionCount).chunked(columns)
}

internal data class PlayerErrorActionNeighbors(
    val left: Int?,
    val right: Int?,
    val up: Int?,
    val down: Int?,
)

/**
 * Deterministic directional neighbors for a gridded RTL action row.
 *
 * Children are laid out right-to-left, so the physically LEFT neighbor of an action is the next
 * list index and the physically RIGHT neighbor is the previous index; up/down move one row.
 */
internal fun playerErrorActionNeighbors(
    index: Int,
    count: Int,
    columns: Int,
): PlayerErrorActionNeighbors {
    if (index !in 0 until count || columns <= 0) return PlayerErrorActionNeighbors(null, null, null, null)
    val row = index / columns
    val left = (index + 1).takeIf { it < count && it / columns == row }
    val right = (index - 1).takeIf { it >= 0 && it / columns == row }
    val up = (index - columns).takeIf { it >= 0 }
    val down = (index + columns).takeIf { it < count }
    return PlayerErrorActionNeighbors(left = left, right = right, up = up, down = down)
}

/**
 * Adaptive geometry for the compact Live controls and the More panel.
 *
 * The transport controls share the accepted utility glyph and interaction container so the strip
 * keeps one small footprint. Geometry is derived from the current window and the existing Live
 * controls safe-area policy so TV overscan, compact phones and wide touch windows all stay inside
 * the viewport without hardcoded device values.
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

    return LivePlayerControlsMetrics(
        approvedSingleRow = approvedSingleRow,
        transportIconDp = utilityIcon,
        transportContainerDp = utilityIcon + 22,
        utilityIconDp = utilityIcon,
        captionSizeSp = captionSize,
        itemSpacingDp = spacing,
        morePanelWidthDp = panelWidth,
    )
}
