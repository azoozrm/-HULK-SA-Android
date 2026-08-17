package sa.hulksa.player.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Match the proven main/home shell behavior for the Live catalog.
 * TV keeps a small internal gutter so controls stay inside the usable screen.
 * Phones use the full edge-to-edge viewport, including the display-cutout/notch area,
 * instead of reserving a status-bar protection inset for the whole page.
 */
@Composable
internal fun Modifier.liveCatalogEdgeToEdge(isTv: Boolean): Modifier =
    then(
        if (isTv) {
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        } else {
            Modifier
        },
    )
