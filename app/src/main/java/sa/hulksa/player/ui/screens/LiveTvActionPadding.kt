package sa.hulksa.player.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Compose has no stock padding overload that combines horizontal and bottom named arguments.
 * Keep the Live TV action safe-area call explicit while delegating to the standard modifiers.
 */
internal fun Modifier.padding(
    horizontal: Dp,
    bottom: Dp,
    liveTvActionSafeArea: Unit = Unit,
): Modifier = padding(horizontal = horizontal).padding(bottom = bottom)
