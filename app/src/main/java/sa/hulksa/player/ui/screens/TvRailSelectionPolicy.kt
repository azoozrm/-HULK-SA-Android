package sa.hulksa.player.ui.screens

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.LayoutDirection
import sa.hulksa.player.MainDestination

/**
 * One TV navigation-rail activation decision.
 *
 * A genuine destination change keeps the accepted navigation + entry/remembered-content handoff
 * rules. Activating the already selected destination is only a rail dismissal: it must return to
 * the originating content context instead of running destination-entry focus.
 */
internal enum class TvRailSelectionIntent {
    CHANGE_DESTINATION,
    RETURN_TO_CONTENT,
}

/** The focus owner that completed one unchanged-destination rail dismissal. */
internal enum class TvRailReturnResult {
    /** The content focus group restored its originating (or surviving) control. */
    CONTENT,

    /** The content group could not take focus; the existing entry handoff owns the transition. */
    ENTRY_HANDOFF,
}

internal fun tvRailSelectionIntent(
    selectedDestination: MainDestination,
    requestedDestination: MainDestination,
): TvRailSelectionIntent =
    if (requestedDestination == selectedDestination) {
        TvRailSelectionIntent.RETURN_TO_CONTENT
    } else {
        TvRailSelectionIntent.CHANGE_DESTINATION
    }

/**
 * The physical key that closes the rail toward the content: the rail owns the layout start edge,
 * so the content is toward the layout end (physical LEFT in RTL, physical RIGHT in LTR).
 */
internal fun tvRailContentReturnDirection(layoutDirection: LayoutDirection): Key =
    if (layoutDirection == LayoutDirection.Rtl) Key.DirectionLeft else Key.DirectionRight

/**
 * One unchanged-destination rail dismissal.
 *
 * The content host is the single restoration owner. Its content focus group saved the exact child
 * that was focused before the rail took focus; Home's lazy boundaries save their own direct child
 * on exit, so `restoreFocusedChild()` walks the complete saved path back to the originating card
 * (a plain group `requestFocus()` resolves to the group's top-left focusable child instead, which
 * is the Home "jump upward" defect). When the saved path no longer exists, the call reports false
 * and the existing destination-entry handoff runs as the deterministic fallback; the two never
 * request focus at once.
 */
internal inline fun returnTvRailFocusToContent(
    restoreContentFocus: () -> Boolean,
    armEntryHandoff: () -> Unit,
): TvRailReturnResult {
    val restored = runCatching(restoreContentFocus).getOrDefault(false)
    if (restored) return TvRailReturnResult.CONTENT
    armEntryHandoff()
    return TvRailReturnResult.ENTRY_HANDOFF
}
