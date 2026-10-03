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

internal const val PLAYER_ERROR_ACTION_GAP_DP = 9
internal const val LIVE_PLAYER_ERROR_MAX_COLUMNS = 4

/**
 * Measured Live error action sizing.
 *
 * The caller measures the visible captions with the actual typography, font scale, icon allowance
 * and accepted compact FocusButton padding, then this policy picks the largest equal-width column
 * count whose row fits the real card content width. The common height grows only as much as the
 * measured content requires, so scaled captions are never clipped.
 */
internal data class LiveErrorActionSizing(
    val columns: Int,
    val actionWidthDp: Int,
    val actionHeightDp: Int,
    val rows: List<List<Int>>,
)

internal fun liveErrorActionSizing(
    availableWidthDp: Int,
    actionCount: Int,
    maxColumns: Int,
    requiredActionWidthDp: Int,
    requiredActionHeightDp: Int,
    gapDp: Int = PLAYER_ERROR_ACTION_GAP_DP,
): LiveErrorActionSizing {
    if (actionCount <= 0 || requiredActionWidthDp <= 0 || requiredActionHeightDp <= 0) {
        return LiveErrorActionSizing(columns = 0, actionWidthDp = 0, actionHeightDp = 0, rows = emptyList())
    }
    var columns = maxColumns.coerceIn(1, actionCount)
    while (columns > 1 && columns * requiredActionWidthDp + (columns - 1) * gapDp > availableWidthDp) {
        columns--
    }
    return LiveErrorActionSizing(
        columns = columns,
        actionWidthDp = requiredActionWidthDp,
        actionHeightDp = requiredActionHeightDp,
        rows = playerErrorActionRows(actionCount, columns),
    )
}

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
