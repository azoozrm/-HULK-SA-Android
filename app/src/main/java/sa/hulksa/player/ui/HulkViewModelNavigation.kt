package sa.hulksa.player.ui

import sa.hulksa.player.HulkViewModel
import sa.hulksa.player.MainDestination

fun HulkViewModel.openMainDestination(destination: MainDestination) {
    selectDestination(destination)
    back()
}
