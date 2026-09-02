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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import sa.hulksa.player.R
import sa.hulksa.player.ui.adaptive.HulkNavigationType
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.theme.LocalHulkColors
import kotlin.math.abs

/**
 * Owns compact TV brand presentation without changing non-TV BrandLogo behavior.
 * The rail shows the complete HULK SA lockup inside a protected frame, while tiny
 * TV surfaces keep the shield-only mark for legibility.
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
        val isCompactTvBrand = adaptiveUi.isTelevision && minOf(maxWidth, maxHeight) <= 56.dp

        if (!isTvRailLogo && !isCompactTvBrand) {
            BrandLogo(
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            return@BoxWithConstraints
        }

        if (isCompactTvBrand && !isTvRailLogo) {
            Image(
                painter = painterResource(R.drawable.hulk_sa_mark),
                contentDescription = "HULK SA",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(1.dp),
                contentScale = ContentScale.Fit,
            )
            return@BoxWithConstraints
        }

        val colors = LocalHulkColors.current
        val cornerRadiusDp = (expectedRailLogoSizeDp * 0.18f).coerceIn(10f, 14f)
        val shape = RoundedCornerShape(cornerRadiusDp.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, colors.goldBright.copy(alpha = 0.07f), shape)
                .padding(1.dp)
                .background(Color(0xFF11120E).copy(alpha = 0.96f), shape)
                .border(1.dp, colors.goldBright.copy(alpha = 0.56f), shape)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.hulk_sa_logo),
                contentDescription = "HULK SA",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
