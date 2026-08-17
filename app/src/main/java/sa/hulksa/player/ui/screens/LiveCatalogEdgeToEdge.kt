package sa.hulksa.player.ui.screens

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Live catalog only: keep the page visually edge-to-edge while protecting phone content from
 * status bars and display cutouts. TV already runs immersive, so no artificial outer gutter is
 * added there.
 */
@Composable
internal fun Modifier.liveCatalogEdgeToEdge(isTv: Boolean): Modifier =
    then(
        if (isTv) {
            Modifier
        } else {
            Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            )
        },
    )
