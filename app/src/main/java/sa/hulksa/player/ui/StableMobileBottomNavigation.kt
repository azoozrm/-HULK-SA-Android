package sa.hulksa.player.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

private data class StableMobileDestination(
    val destination: MainDestination,
    val icon: ImageVector,
    val label: String,
)

private val stableMobileDestinations = listOf(
    StableMobileDestination(MainDestination.HOME, Icons.Rounded.Home, "الرئيسية"),
    StableMobileDestination(MainDestination.LIVE, Icons.Rounded.LiveTv, "البث المباشر"),
    StableMobileDestination(MainDestination.MOVIES, Icons.Rounded.Movie, "الافلام"),
    StableMobileDestination(MainDestination.SERIES, Icons.Rounded.Tv, "المسلسلات"),
    StableMobileDestination(MainDestination.FAVORITES, Icons.Rounded.Favorite, "قائمتي"),
    StableMobileDestination(MainDestination.SEARCH, Icons.Rounded.Search, "البحث"),
    StableMobileDestination(MainDestination.DOWNLOADS, Icons.Rounded.Download, "التنزيلات"),
    StableMobileDestination(MainDestination.SETTINGS, Icons.Rounded.Settings, "الاعدادات"),
)

/**
 * One stable phone navigation surface shared by the normal shell and Smart Search.
 *
 * The destination strip never auto-scrolls when a section is selected. The user can swipe it
 * manually and its LazyListState survives destination recomposition. Profile switching is pinned
 * outside the scrollable strip so it never disappears. Active state uses a soft gold surface only
 * (no top indicator line), keeping selection clear without moving any item.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StableMobileBottomNavigation(
    selected: MainDestination,
    onSelectDestination: (MainDestination) -> Unit,
    onSwitchProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (WindowInsets.isImeVisible) return

    val colors = LocalHulkColors.current
    val listState = rememberLazyListState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF090A07))
            .navigationBarsPadding()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(
                items = stableMobileDestinations,
                key = { it.destination.name },
            ) { entry ->
                StableMobileNavItem(
                    icon = entry.icon,
                    label = entry.label,
                    active = selected == entry.destination,
                    onClick = { onSelectDestination(entry.destination) },
                )
            }
        }

        Spacer(Modifier.width(5.dp))

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(38.dp)
                .background(Color.White.copy(alpha = .07f)),
        )

        Spacer(Modifier.width(5.dp))

        StableMobileNavItem(
            icon = Icons.Rounded.Person,
            label = "تغيير المستخدم",
            active = false,
            pinned = true,
            onClick = onSwitchProfile,
        )
    }
}

@Composable
private fun StableMobileNavItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    pinned: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val scale by animateFloatAsState(
        targetValue = if (active) 1.08f else 1f,
        label = "stableMobileNavScale",
    )
    val shape = RoundedCornerShape(11.dp)

    Column(
        modifier = Modifier
            .width(if (pinned) 58.dp else 54.dp)
            .height(54.dp)
            .clip(shape)
            .background(
                when {
                    active -> colors.gold.copy(alpha = .18f)
                    pinned -> colors.surfaceRaised.copy(alpha = .42f)
                    else -> Color.Transparent
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = when {
                active -> colors.goldBright
                pinned -> colors.goldBright.copy(alpha = .90f)
                else -> colors.textMuted
            },
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = when {
                active -> colors.text
                pinned -> colors.text.copy(alpha = .92f)
                else -> colors.textMuted
            },
            fontSize = if (pinned) 7.sp else 8.sp,
            fontWeight = if (active || pinned) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = if (pinned) 2 else 1,
            lineHeight = 8.sp,
            textAlign = TextAlign.Center,
        )
    }
}
