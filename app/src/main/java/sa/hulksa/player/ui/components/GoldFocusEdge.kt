package sa.hulksa.player.ui.components

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import sa.hulksa.player.ui.theme.LocalHulkColors

/**
 * Owner-approved focused treatment for gold-filled interactive controls.
 *
 * Draws the focus edge fully inside the control's existing bounds:
 * a 2dp light-gold outer stroke plus an immediately adjacent 1dp dark
 * separator. The normal gold surface stays visible in the center.
 * Uses the control's own shape so pills and circles keep their radius.
 */
@Composable
fun Modifier.goldFocusEdge(shape: Shape, visible: Boolean): Modifier {
    if (!visible) return this
    val colors = LocalHulkColors.current
    // Compose draws each border over its inner content, so the 2dp light-gold
    // stroke must wrap the 3dp separator band to stay on the outside.
    return border(2.dp, colors.goldBright, shape)
        .border(3.dp, colors.surface, shape)
}
