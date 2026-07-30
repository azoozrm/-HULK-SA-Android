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
        TvFocusDirection.RIGHT -> LiveFocusSlot.CHANNEL
        TvFocusDirection.LEFT -> LiveFocusSlot.FAVORITE
        else -> null
    }
    LiveFocusSlot.FAVORITE -> when (direction) {
        TvFocusDirection.RIGHT -> LiveFocusSlot.PLAY
        TvFocusDirection.LEFT -> LiveFocusSlot.CHANNEL
        else -> null
    }
}

internal fun nextDownloadFocusNodeStrict(
    current: DownloadFocusNode,
    rowCount: Int,
    direction: DownloadFocusDirection,
): DownloadFocusNode? {
    if (rowCount < 0) return null
    if (current.rowIndex < 0) {
        return when (direction) {
            DownloadFocusDirection.UP -> null
            DownloadFocusDirection.DOWN -> when (current.slot) {
                DownloadFocusSlot.WIFI -> DownloadFocusNode(0, DownloadFocusSlot.PRIMARY)
                DownloadFocusSlot.SCHEDULE -> DownloadFocusNode(0, DownloadFocusSlot.PRIORITY)
                DownloadFocusSlot.CONCURRENT -> DownloadFocusNode(0, DownloadFocusSlot.CANCEL)
                else -> null
            }.takeIf { rowCount > 0 }
            DownloadFocusDirection.LEFT -> when (current.slot) {
                DownloadFocusSlot.WIFI -> DownloadFocusNode(-1, DownloadFocusSlot.SCHEDULE)
                DownloadFocusSlot.SCHEDULE -> DownloadFocusNode(-1, DownloadFocusSlot.CONCURRENT)
                else -> null
            }
            DownloadFocusDirection.RIGHT -> when (current.slot) {
                DownloadFocusSlot.CONCURRENT -> DownloadFocusNode(-1, DownloadFocusSlot.SCHEDULE)
                DownloadFocusSlot.SCHEDULE -> DownloadFocusNode(-1, DownloadFocusSlot.WIFI)
                else -> null
            }
        }
    }

    if (
        current.rowIndex >= rowCount ||
        current.slot !in setOf(
            DownloadFocusSlot.PRIMARY,
            DownloadFocusSlot.PRIORITY,
            DownloadFocusSlot.CANCEL,
        )
    ) {
        return null
    }

    return when (direction) {
        DownloadFocusDirection.LEFT -> when (current.slot) {
            DownloadFocusSlot.PRIMARY -> current.copy(slot = DownloadFocusSlot.PRIORITY)
            DownloadFocusSlot.PRIORITY -> current.copy(slot = DownloadFocusSlot.CANCEL)
            DownloadFocusSlot.CANCEL -> null
            else -> null
        }
        DownloadFocusDirection.RIGHT -> when (current.slot) {
            DownloadFocusSlot.CANCEL -> current.copy(slot = DownloadFocusSlot.PRIORITY)
            DownloadFocusSlot.PRIORITY -> current.copy(slot = DownloadFocusSlot.PRIMARY)
            DownloadFocusSlot.PRIMARY -> null
            else -> null
        }
        DownloadFocusDirection.UP -> if (current.rowIndex > 0) {
            current.copy(rowIndex = current.rowIndex - 1)
        } else {
            DownloadFocusNode(
                rowIndex = -1,
                slot = when (current.slot) {
                    DownloadFocusSlot.PRIMARY -> DownloadFocusSlot.WIFI
                    DownloadFocusSlot.PRIORITY -> DownloadFocusSlot.SCHEDULE
                    DownloadFocusSlot.CANCEL -> DownloadFocusSlot.CONCURRENT
                    else -> return null
                },
            )
        }
        DownloadFocusDirection.DOWN -> if (current.rowIndex + 1 < rowCount) {
            current.copy(rowIndex = current.rowIndex + 1)
        } else {
            null
        }
    }
}
