package sa.hulksa.player.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import sa.hulksa.player.R
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.theme.LocalHulkColors

/** Tightly fitted NEO 4K mark for TV-only brand slots. */
@Composable
fun TvFittedBrandMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.hulk_sa_tv_mark_fitted),
        contentDescription = "NEO 4K",
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

/**
 * Explicit TV rail brand tile. The rail calls this directly in both collapsed and expanded states,
 * so the fitted mark never depends on a measured-size match. The dark backing and original 3dp
 * inset are preserved; the decorative gold border is removed.
 */
@Composable
fun TvRailBrandTile(modifier: Modifier) {
    val adaptiveUi = LocalAdaptiveUi.current
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(
        (adaptiveUi.tvPremiumPolicy.railLogoSizeDp * 0.18f).coerceIn(10f, 14f).dp,
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.hulk_sa_tv_mark_fitted),
            contentDescription = "NEO 4K",
            modifier = Modifier.fillMaxSize().padding(3.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * Shared NEO mark used by poster fallbacks, Details and other non-rail surfaces.
 *
 * The TV rail uses [TvRailBrandTile] explicitly; this function deliberately keeps the larger
 * shared artwork unchanged for every other caller.
 */
@Composable
fun BrandLogo(modifier: Modifier) {
    Image(
        painter = painterResource(R.drawable.hulk_sa_mark_reference),
        contentDescription = "NEO 4K",
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
