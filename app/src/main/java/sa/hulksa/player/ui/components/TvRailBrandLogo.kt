package sa.hulksa.player.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import sa.hulksa.player.R
import sa.hulksa.player.ui.adaptive.HulkNavigationType
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.theme.LocalHulkColors
import kotlin.math.abs

/**
 * One-argument BrandLogo overload used by TV rail and fallback artwork surfaces.
 *
 * The TV rail owns one black framed tile with a large shield-only mark. Non-rail
 * one-argument usages stay transparent so poster fallbacks never inherit the rail frame.
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
            val compactCategoryMark = maxWidth <= 24.dp && maxHeight <= 24.dp
            Image(
                painter = painterResource(R.drawable.hulk_sa_mark_reference),
                contentDescription = "HULK SA",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val compactScale = if (compactCategoryMark) 1.00f else 1f
                        scaleX = compactScale
                        scaleY = compactScale
                    },
                contentScale = ContentScale.Fit,
            )
            return@BoxWithConstraints
        }

        val colors = LocalHulkColors.current
        val shape = RoundedCornerShape(
            (expectedRailLogoSizeDp * 0.18f).coerceIn(10f, 14f).dp,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Color.Black)
                .border(1.5.dp, colors.goldBright.copy(alpha = .72f), shape)
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.hulk_sa_mark_reference),
                contentDescription = "HULK SA",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.30f
                        scaleY = 1.30f
                    },
                contentScale = ContentScale.Fit,
            )
        }
    }
}
