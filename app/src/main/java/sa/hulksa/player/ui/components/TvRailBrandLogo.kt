package sa.hulksa.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import sa.hulksa.player.ui.adaptive.HulkNavigationType
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.theme.LocalHulkColors
import kotlin.math.abs

/**
 * One-argument BrandLogo overload used to give only the Android TV navigation-rail logo a
 * premium static frame. All other BrandLogo usages delegate to the canonical two-argument
 * implementation unchanged.
 */
@Composable
fun BrandLogo(modifier: Modifier) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val adaptiveUi = LocalAdaptiveUi.current
        val expectedRailLogoSizeDp = adaptiveUi.tvPremiumPolicy.railLogoSizeDp
        val isTvRailLogo = adaptiveUi.isTelevision &&
            adaptiveUi.navigationType == HulkNavigationType.RAIL &&
            abs(maxWidth.value - expectedRailLogoSizeDp) <= 0.5f &&
            abs(maxHeight.value - expectedRailLogoSizeDp) <= 0.5f

        if (!isTvRailLogo) {
            BrandLogo(
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            return@BoxWithConstraints
        }

        val colors = LocalHulkColors.current
        val cornerRadiusDp = (expectedRailLogoSizeDp * 0.18f).coerceIn(10f, 14f)
        val logoPaddingDp = (expectedRailLogoSizeDp * 0.055f).coerceIn(3f, 4.5f)
        val shape = RoundedCornerShape(cornerRadiusDp.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                // Extremely restrained gold halo: visible as polish, never as a focus state.
                .border(2.dp, colors.goldBright.copy(alpha = 0.07f), shape)
                .padding(1.dp)
                .background(Color(0xFF11120E).copy(alpha = 0.96f), shape)
                .border(1.dp, colors.goldBright.copy(alpha = 0.56f), shape)
                .padding(logoPaddingDp.dp),
            contentAlignment = Alignment.Center,
        ) {
            BrandLogo(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.18f
                        scaleY = 1.18f
                    },
                contentScale = ContentScale.Fit,
            )
        }
    }
}
