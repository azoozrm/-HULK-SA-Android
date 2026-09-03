package sa.hulksa.player.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import sa.hulksa.player.R
import sa.hulksa.player.ui.adaptive.HulkNavigationType
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import kotlin.math.abs

/**
 * One-argument BrandLogo overload used by TV rail and fallback artwork surfaces.
 *
 * TV navigation rail owns the full square HULK SA badge. All non-rail one-argument
 * usages intentionally render the transparent shield-only mark so poster fallbacks
 * never inherit the in-app square badge or the Android TV banner lockup.
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

        Image(
            painter = painterResource(
                if (isTvRailLogo) R.drawable.hulk_sa_logo else R.drawable.ic_launcher_foreground,
            ),
            contentDescription = "HULK SA",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}
