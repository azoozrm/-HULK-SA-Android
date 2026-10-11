package sa.hulksa.player.ui.screens

import sa.hulksa.player.MainDestination

internal enum class TvGridFocusMove {
    LEFT,
    RIGHT,
    UP,
    DOWN,
}

/**
 * Resolves the next catalog-card index without spatial fallback.
 *
 * HULK's UI is forced RTL, so index 0 is the right-most card in a row:
 * physical LEFT advances the index and physical RIGHT decrements it.
 * Vertical movement preserves the visual column; on a short final row it
 * chooses the nearest surviving card instead of falling back to column 1.
 */
internal fun nextTvGridFocusIndex(
    currentIndex: Int,
    itemCount: Int,
    columnCount: Int,
    move: TvGridFocusMove,
): Int? {
    if (itemCount <= 0 || columnCount <= 0 || currentIndex !in 0 until itemCount) return null

    val rowStart = (currentIndex / columnCount) * columnCount
    val rowEnd = minOf(rowStart + columnCount - 1, itemCount - 1)
    val column = currentIndex - rowStart

    return when (move) {
        TvGridFocusMove.LEFT ->
            (currentIndex + 1).takeIf { it <= rowEnd }

        TvGridFocusMove.RIGHT ->
            (currentIndex - 1).takeIf { it >= rowStart }

        TvGridFocusMove.UP -> {
            if (rowStart == 0) null else currentIndex - columnCount
        }

        TvGridFocusMove.DOWN -> {
            val nextRowStart = rowStart + columnCount
            if (nextRowStart >= itemCount) {
                null
            } else {
                val nextRowEnd = minOf(nextRowStart + columnCount - 1, itemCount - 1)
                minOf(nextRowStart + column, nextRowEnd)
            }
        }
    }
}

internal enum class FavoritesGridFocusTransition {
    REMOVAL,
    NONE,
}

/**
 * Classifies a Favorites content-key transition for the single grid focus/scroll owner. A removal
 * is claimed only when the type filter (memory row key) is unchanged and the card that owned focus
 * is gone from the new projection. Every other transition stays with the accepted entry/return
 * restoration path, so a filter switch or a surviving focused card can never masquerade as a
 * removal and steal its release guard.
 */
internal fun favoritesGridFocusTransition(
    destination: MainDestination,
    previousKeys: List<String>,
    currentKeys: List<String>,
    previousRowKey: String,
    currentRowKey: String,
    focusedKey: String?,
): FavoritesGridFocusTransition = if (
    destination == MainDestination.FAVORITES &&
    previousKeys != currentKeys &&
    previousRowKey == currentRowKey &&
    focusedKey != null &&
    focusedKey in previousKeys &&
    focusedKey !in currentKeys
) {
    FavoritesGridFocusTransition.REMOVAL
} else {
    FavoritesGridFocusTransition.NONE
}

/**
 * Removal focus target: the next surviving stable key (the item that shifted into the removed
 * slot), then the previous item when the removed one was last. Null when nothing survives; the
 * page-level empty handoff returns focus to the reachable filter control instead.
 */
internal fun favoritesRemovalFocusIndex(removedIndex: Int, remainingCount: Int): Int? =
    if (remainingCount <= 0 || removedIndex < 0) {
        null
    } else {
        removedIndex.coerceAtMost(remainingCount - 1)
    }

internal enum class FavoritesSelectGuardDecision {
    CONSUME_RELEASE,
    CLEAR_ARMED_ON_PRESS,
    PASS,
}

/**
 * Select-key arbitration for the Favorites removal release guard. The release of the long press
 * that removed the card (KeyUp) is consumed once; only a fresh press (repeatCount 0) clears an arm
 * that was never released; held repeats neither clear nor consume.
 */
internal fun favoritesSelectGuardDecision(
    armed: Boolean,
    isSelectKey: Boolean,
    isKeyUp: Boolean,
    keyDownRepeatCount: Int,
): FavoritesSelectGuardDecision = when {
    !isSelectKey || !armed -> FavoritesSelectGuardDecision.PASS
    isKeyUp -> FavoritesSelectGuardDecision.CONSUME_RELEASE
    keyDownRepeatCount == 0 -> FavoritesSelectGuardDecision.CLEAR_ARMED_ON_PRESS
    else -> FavoritesSelectGuardDecision.PASS
}
