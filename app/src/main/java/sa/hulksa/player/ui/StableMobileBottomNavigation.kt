package sa.hulksa.player.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sa.hulksa.player.MainDestination
import sa.hulksa.player.ui.theme.LocalHulkColors

private data class StableMobileEntry(
    val destination: MainDestination,
    val icon: ImageVector,
    val label: String,
)

private val MOBILE_BOTTOM_NAVIGATION_ITEM_HEIGHT = 56.dp
private val MOBILE_BOTTOM_NAVIGATION_VERTICAL_PADDING = 6.dp

internal val MOBILE_BOTTOM_NAVIGATION_RESERVED_HEIGHT =
    MOBILE_BOTTOM_NAVIGATION_ITEM_HEIGHT + MOBILE_BOTTOM_NAVIGATION_VERTICAL_PADDING * 2

private val stableMobileEntries = listOf(
    StableMobileEntry(MainDestination.HOME, Icons.Rounded.Home, "الرئيسية"),
    StableMobileEntry(MainDestination.LIVE, Icons.Rounded.LiveTv, "البث المباشر"),
    StableMobileEntry(MainDestination.MOVIES, Icons.Rounded.Movie, "الافلام"),
    StableMobileEntry(MainDestination.SERIES, Icons.Rounded.Tv, "المسلسلات"),
    StableMobileEntry(MainDestination.FAVORITES, Icons.Rounded.Favorite, "قائمتي"),
)

/**
 * Stable phone navigation shared by the normal shell and Smart Search.
 *
 * P02 keeps the primary five destinations in one scrollable strip in a fixed order. Search lives in
 * the Home header and the profile entry exposes the gated Downloads/Settings actions. Selection
 * never auto-scrolls or reorders the strip; the active destination uses a restrained gold
 * surface/icon treatment without a top indicator line or any geometry change.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StableMobileBottomNavigation(
    selected: MainDestination,
    onSelectDestination: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (WindowInsets.isImeVisible) return

    val listState = rememberLazyListState()

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF090A07))
            .navigationBarsPadding()
            .padding(vertical = MOBILE_BOTTOM_NAVIGATION_VERTICAL_PADDING),
        state = listState,
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(
            items = stableMobileEntries,
            key = { entry -> entry.destination.name },
        ) { entry ->
            StableMobileNavItem(
                icon = entry.icon,
                label = entry.label,
                active = selected == entry.destination,
                onClick = { onSelectDestination(entry.destination) },
            )
        }
    }
}

@Composable
private fun StableMobileNavItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val scale by animateFloatAsState(
        targetValue = if (active) 1.07f else 1f,
        label = "stableMobileNavScale",
    )
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .width(58.dp)
            .height(MOBILE_BOTTOM_NAVIGATION_ITEM_HEIGHT)
            .clip(shape)
            .background(
                if (active) colors.gold.copy(alpha = .16f)
                else Color.Transparent,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) colors.goldBright else colors.textMuted,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = if (active) 5.dp.toPx() else 0f
                },
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = if (active) colors.text else colors.textMuted,
            fontSize = 8.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 2,
            lineHeight = 8.sp,
            textAlign = TextAlign.Center,
        )
    }
}
