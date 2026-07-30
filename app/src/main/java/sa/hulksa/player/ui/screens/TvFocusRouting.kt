package sa.hulksa.player.ui.screens

internal enum class LiveFocusSlot {
    CHANNEL,
    PLAY,
    FAVORITE,
}

internal enum class TvFocusDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

internal fun nextLiveFocusSlot(
    current: LiveFocusSlot,
    direction: TvFocusDirection,
): LiveFocusSlot? = when (current) {
    LiveFocusSlot.CHANNEL -> when (direction) {
        TvFocusDirection.LEFT -> LiveFocusSlot.PLAY
        else -> null
    }
    LiveFocusSlot.PLAY -> when (direction) {
        TvFocusDirection.LEFT -> LiveFocusSlot.FAVORITE
        TvFocusDirection.RIGHT -> LiveFocusSlot.CHANNEL
        else -> null
    }
    LiveFocusSlot.FAVORITE -> when (direction) {
        TvFocusDirection.RIGHT -> LiveFocusSlot.PLAY
        else -> null
    }
}

internal fun nextDownloadFocusNodeStrict(
    current: DownloadFocusNode,
    rowCount: Int,
    direction: DownloadFocusDirection,
): DownloadFocusNode? = nextDownloadFocusNode(current, rowCount, direction)
