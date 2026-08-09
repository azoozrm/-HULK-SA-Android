package sa.hulksa.player.ui.screens

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
