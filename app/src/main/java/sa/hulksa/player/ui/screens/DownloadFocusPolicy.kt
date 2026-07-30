package sa.hulksa.player.ui.screens

internal enum class DownloadFocusZone { TOOLBAR, CARD }
internal enum class DownloadFocusSlot { WIFI, SCHEDULE, CONCURRENT, PRIMARY, PRIORITY, CANCEL }
internal enum class DownloadFocusMove { LEFT, RIGHT, UP, DOWN }

internal data class DownloadFocusLocation(
    val zone: DownloadFocusZone,
    val row: Int,
    val slot: DownloadFocusSlot,
) {
    companion object {
        fun toolbar(slot: DownloadFocusSlot) = DownloadFocusLocation(DownloadFocusZone.TOOLBAR, -1, slot)
        fun card(row: Int, slot: DownloadFocusSlot) = DownloadFocusLocation(DownloadFocusZone.CARD, row, slot)
    }
}

internal fun nextDownloadFocus(
    rowCount: Int,
    current: DownloadFocusLocation,
    move: DownloadFocusMove,
): DownloadFocusLocation? {
    require(rowCount >= 0)
    return when (current.zone) {
        DownloadFocusZone.TOOLBAR -> when (move) {
            DownloadFocusMove.LEFT -> when (current.slot) {
                DownloadFocusSlot.WIFI -> DownloadFocusLocation.toolbar(DownloadFocusSlot.SCHEDULE)
                DownloadFocusSlot.SCHEDULE -> DownloadFocusLocation.toolbar(DownloadFocusSlot.CONCURRENT)
                else -> null
            }
            DownloadFocusMove.RIGHT -> when (current.slot) {
                DownloadFocusSlot.CONCURRENT -> DownloadFocusLocation.toolbar(DownloadFocusSlot.SCHEDULE)
                DownloadFocusSlot.SCHEDULE -> DownloadFocusLocation.toolbar(DownloadFocusSlot.WIFI)
                else -> null
            }
            DownloadFocusMove.DOWN -> if (rowCount == 0) null else DownloadFocusLocation.card(
                0,
                when (current.slot) {
                    DownloadFocusSlot.WIFI -> DownloadFocusSlot.PRIMARY
                    DownloadFocusSlot.SCHEDULE -> DownloadFocusSlot.PRIORITY
                    DownloadFocusSlot.CONCURRENT -> DownloadFocusSlot.CANCEL
                    else -> return null
                },
            )
            DownloadFocusMove.UP -> null
        }
        DownloadFocusZone.CARD -> when (move) {
            DownloadFocusMove.LEFT -> when (current.slot) {
                DownloadFocusSlot.PRIMARY -> current.copy(slot = DownloadFocusSlot.PRIORITY)
                DownloadFocusSlot.PRIORITY -> current.copy(slot = DownloadFocusSlot.CANCEL)
                else -> null
            }
            DownloadFocusMove.RIGHT -> when (current.slot) {
                DownloadFocusSlot.CANCEL -> current.copy(slot = DownloadFocusSlot.PRIORITY)
                DownloadFocusSlot.PRIORITY -> current.copy(slot = DownloadFocusSlot.PRIMARY)
                else -> null
            }
            DownloadFocusMove.UP -> if (current.row > 0) current.copy(row = current.row - 1) else DownloadFocusLocation.toolbar(
                when (current.slot) {
                    DownloadFocusSlot.PRIMARY -> DownloadFocusSlot.WIFI
                    DownloadFocusSlot.PRIORITY -> DownloadFocusSlot.SCHEDULE
                    DownloadFocusSlot.CANCEL -> DownloadFocusSlot.CONCURRENT
                    else -> return null
                },
            )
            DownloadFocusMove.DOWN -> if (current.row + 1 < rowCount) current.copy(row = current.row + 1) else null
        }
    }
}
