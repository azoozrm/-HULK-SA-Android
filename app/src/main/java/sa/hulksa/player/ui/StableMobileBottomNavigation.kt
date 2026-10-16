package sa.hulksa.player.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.unit.Dp
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
private val MOBILE_BOTTOM_NAVIGATION_HORIZONTAL_PADDING = 4.dp
private const val MOBILE_BOTTOM_NAVIGATION_MIN_SLOT_WIDTH_DP = 48

private val stableMobileEntries = listOf(
    StableMobileEntry(MainDestination.HOME, Icons.Rounded.Home, "الرئيسية"),
    StableMobileEntry(MainDestination.LIVE, Icons.Rounded.LiveTv, "البث المباشر"),
    StableMobileEntry(MainDestination.MOVIES, Icons.Rounded.Movie, "الافلام"),
    StableMobileEntry(MainDestination.SERIES, Icons.Rounded.Tv, "المسلسلات"),
    StableMobileEntry(MainDestination.DOWNLOADS, Icons.Rounded.Download, "التنزيلات"),
    StableMobileEntry(MainDestination.SETTINGS, Icons.Rounded.Settings, "الاعدادات"),
)

internal fun stableMobileEntryCount(downloadsEnabled: Boolean): Int =
    if (downloadsEnabled) stableMobileEntries.size else stableMobileEntries.size - 1

/**
 * One full-width row while every destination slot keeps an accessible width; otherwise the
 * explicit two-row fallback keeps all destinations visible instead of clipping or scrolling.
 */
internal fun stableMobileNavigationRows(windowWidthDp: Int, downloadsEnabled: Boolean): Int {
    val count = stableMobileEntryCount(downloadsEnabled)
    if (count <= 0) return 1
    val usableWidthDp = windowWidthDp - MOBILE_BOTTOM_NAVIGATION_HORIZONTAL_PADDING.value * 2
    return if (usableWidthDp / count >= MOBILE_BOTTOM_NAVIGATION_MIN_SLOT_WIDTH_DP) 1 else 2
}

internal fun stableMobileNavigationReservedHeight(windowWidthDp: Int, downloadsEnabled: Boolean): Dp =
    MOBILE_BOTTOM_NAVIGATION_ITEM_HEIGHT * stableMobileNavigationRows(windowWidthDp, downloadsEnabled) +
        MOBILE_BOTTOM_NAVIGATION_VERTICAL_PADDING * 2

/**
 * Stable phone navigation shared by the normal shell and Smart Search.
 *
 * The remaining destinations share the available width as equally distributed slots. Favorites,
 * Search and Switch User moved to the compact top action area, so this bar owns only real
 * destinations and Download eligibility. Selection uses the restrained gold surface/icon
 * treatment without a top indicator line or any geometry change.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StableMobileBottomNavigation(
    selected: MainDestination,
    downloadsEnabled: Boolean,
    onSelectDestination: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (WindowInsets.isImeVisible) return

    val visibleEntries = if (downloadsEnabled) {
        stableMobileEntries
    } else {
        stableMobileEntries.filterNot { it.destination == MainDestination.DOWNLOADS }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF090A07))
            .navigationBarsPadding()
            .padding(
                horizontal = MOBILE_BOTTOM_NAVIGATION_HORIZONTAL_PADDING,
                vertical = MOBILE_BOTTOM_NAVIGATION_VERTICAL_PADDING,
            ),
    ) {
        val rows = if (
            maxWidth.value / visibleEntries.size >= MOBILE_BOTTOM_NAVIGATION_MIN_SLOT_WIDTH_DP
        ) {
            1
        } else {
            2
        }
        if (rows == 1) {
            StableMobileNavigationRow(
                entries = visibleEntries,
                selected = selected,
                onSelectDestination = onSelectDestination,
            )
        } else {
            val split = (visibleEntries.size + 1) / 2
            Column {
                StableMobileNavigationRow(
                    entries = visibleEntries.take(split),
                    selected = selected,
                    onSelectDestination = onSelectDestination,
                )
                StableMobileNavigationRow(
                    entries = visibleEntries.drop(split),
                    selected = selected,
                    onSelectDestination = onSelectDestination,
                )
            }
        }
    }
}

@Composable
private fun StableMobileNavigationRow(
    entries: List<StableMobileEntry>,
    selected: MainDestination,
    onSelectDestination: (MainDestination) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEach { entry ->
            StableMobileNavItem(
                icon = entry.icon,
                label = entry.label,
                active = selected == entry.destination,
                onClick = { onSelectDestination(entry.destination) },
                modifier = Modifier.weight(1f),
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
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val scale by animateFloatAsState(
        targetValue = if (active) 1.07f else 1f,
        label = "stableMobileNavScale",
    )
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .height(MOBILE_BOTTOM_NAVIGATION_ITEM_HEIGHT)
            .clip(shape)
            .background(
                if (active) colors.gold.copy(alpha = .16f)
                else Color.Transparent,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
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
