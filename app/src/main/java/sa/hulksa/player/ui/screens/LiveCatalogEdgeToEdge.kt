package sa.hulksa.player.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Match the proven main/home shell spacing for the Live catalog.
 * TV keeps a small internal content gutter so controls never touch the rail, bezel, or bottom edge.
 * Phones stay visually edge-to-edge and only reserve the top status-bar/notch area.
 */
@Composable
internal fun Modifier.liveCatalogEdgeToEdge(isTv: Boolean): Modifier =
    then(
        if (isTv) {
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        } else {
            Modifier.statusBarsPadding()
        },
    )
