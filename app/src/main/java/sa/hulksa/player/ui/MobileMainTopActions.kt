package sa.hulksa.player.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sa.hulksa.player.MainDestination
import sa.hulksa.player.ui.screens.NotificationBellButton
import sa.hulksa.player.ui.screens.RoundAction
import sa.hulksa.player.ui.screens.homeHeaderActionTouchSizeDp

/**
 * Natural width of the compact phone Home header group: five unchanged 48dp touch containers with
 * no extra gap (the smaller circles already leave their own visual gaps inside the containers).
 */
internal val MOBILE_INLINE_TOP_ACTIONS_WIDTH = homeHeaderActionTouchSizeDp().dp * 5

/**
 * Single-row fit decision for the phone Home header. The widest real title/subtitle line plus the
 * natural-width action group must fit the measured header content width; otherwise the caller
 * reflows inside the header instead of letting the group cover the title or the hero artwork.
 */
internal fun mobileInlineTopActionsFitSingleRow(
    titleWidth: Dp,
    subtitleWidth: Dp,
    availableWidth: Dp,
): Boolean =
    maxOf(titleWidth, subtitleWidth) + MOBILE_INLINE_TOP_ACTIONS_WIDTH <= availableWidth

/**
 * Natural-width compact five-action group for the original Home header slot: the group sits
 * directly beside the title area with the same physical right-to-left order
 * البحث، المفضلة، تغيير المستخدم، الإشعارات، تحديث المحتوى; Notifications and Refresh remain the
 * physical-left end. Uses the existing action atoms with the opt-in compact presentation. The
 * group is Home-only and scrolls with the original Home header/hero; no shared non-Home toolbar.
 */
@Composable
internal fun MobileInlineTopActions(
    selected: MainDestination,
    unreadNotificationCount: Int,
    refreshing: Boolean,
    onOpenSearch: () -> Unit,
    onOpenFavorites: () -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundAction(
            icon = Icons.Rounded.Search,
            description = "البحث",
            onClick = onOpenSearch,
            selected = selected == MainDestination.SEARCH,
            compact = true,
        )
        RoundAction(
            icon = Icons.Rounded.Favorite,
            description = "المفضلة",
            onClick = onOpenFavorites,
            selected = selected == MainDestination.FAVORITES,
            compact = true,
        )
        RoundAction(
            icon = Icons.Rounded.Person,
            description = "تغيير المستخدم",
            onClick = onSwitchProfile,
            compact = true,
        )
        NotificationBellButton(
            unreadCount = unreadNotificationCount,
            isTv = false,
            onClick = onOpenNotifications,
            compact = true,
        )
        RoundAction(
            icon = Icons.Rounded.Refresh,
            description = "تحديث المحتوى",
            onClick = onRefresh,
            loading = refreshing,
            compact = true,
        )
    }
}
