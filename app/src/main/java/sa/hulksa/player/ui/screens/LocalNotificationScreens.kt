package sa.hulksa.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sa.hulksa.player.data.EpisodeNotificationPopup
import sa.hulksa.player.data.LocalEpisodeNotification
import sa.hulksa.player.ui.components.BrandLogo
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.theme.LocalHulkColors
import kotlin.math.roundToInt

internal data class LocalNotificationCenterMetrics(
    val horizontalPaddingDp: Int,
    val topPaddingDp: Int,
    val maxContentWidthDp: Int,
    val posterWidthDp: Int,
    val posterHeightDp: Int,
)

internal fun localNotificationCenterMetrics(
    widthDp: Int,
    heightDp: Int,
    isTv: Boolean,
): LocalNotificationCenterMetrics {
    val width = widthDp.coerceAtLeast(1)
    val height = heightDp.coerceAtLeast(1)
    return if (isTv) {
        LocalNotificationCenterMetrics(
            horizontalPaddingDp = (width * .035f).roundToInt().coerceIn(18, 64),
            topPaddingDp = (height * .04f).roundToInt().coerceIn(16, 42),
            maxContentWidthDp = (width * .78f).roundToInt().coerceIn(760, 1320),
            posterWidthDp = (width * .07f).roundToInt().coerceIn(70, 112),
            posterHeightDp = (width * .105f).roundToInt().coerceIn(105, 168),
        )
    } else {
        val tablet = width >= 600
        LocalNotificationCenterMetrics(
            horizontalPaddingDp = if (tablet) 24 else 14,
            topPaddingDp = if (tablet) 20 else 12,
            maxContentWidthDp = if (tablet) 900 else width,
            posterWidthDp = if (tablet) 84 else 68,
            posterHeightDp = if (tablet) 126 else 102,
        )
    }
}

internal fun nextLocalNotificationFocusIndex(
    currentIndex: Int,
    itemCount: Int,
    movingDown: Boolean,
): Int? {
    if (itemCount <= 0 || currentIndex !in 0 until itemCount) return null
    val candidate = currentIndex + if (movingDown) 1 else -1
    return candidate.takeIf { it in 0 until itemCount }
}

internal fun homeHeaderActionVisualSizeDp(): Int = 44

internal fun homeHeaderActionTouchSizeDp(): Int = 48

@Suppress("UNUSED_PARAMETER")
internal fun notificationBellSizeDp(isTv: Boolean): Int = homeHeaderActionVisualSizeDp()

internal data class NotificationBadgeMetrics(
    val label: String,
    val widthDp: Int,
    val heightDp: Int,
    val fontSizeSp: Int,
)

internal fun notificationBadgeMetrics(unreadCount: Int): NotificationBadgeMetrics? {
    if (unreadCount <= 0) return null
    val label = if (unreadCount > 999) "999+" else unreadCount.toString()
    return NotificationBadgeMetrics(
        label = label,
        widthDp = when (label.length) {
            1 -> 18
            2 -> 20
            3 -> 24
            else -> 30
        },
        heightDp = 18,
        fontSizeSp = if (label.length >= 4) 7 else 8,
    )
}

@Composable
fun NotificationBellButton(
    unreadCount: Int,
    isTv: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val visualSize = notificationBellSizeDp(isTv).dp
    val badgeMetrics = notificationBadgeMetrics(unreadCount)
    Box(
        modifier = modifier
            .size(homeHeaderActionTouchSizeDp().dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(visualSize)
                .clip(CircleShape)
                .background(if (focused) colors.gold else Color.Black.copy(alpha = .46f))
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) colors.goldBright else colors.line,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = "مركز الإشعارات",
                tint = if (focused) Color.Black else colors.text,
                modifier = Modifier.size(21.dp),
            )
        }
        if (badgeMetrics != null) {
            val badgeShape = RoundedCornerShape((badgeMetrics.heightDp / 2).dp)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-5).dp)
                    .width(badgeMetrics.widthDp.dp)
                    .height(badgeMetrics.heightDp.dp)
                    .clip(badgeShape)
                    .background(colors.goldBright)
                    .border(1.dp, colors.background, badgeShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badgeMetrics.label,
                    color = Color.Black,
                    fontSize = badgeMetrics.fontSizeSp.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    modifier = Modifier.offset(y = (-1).dp),
                )
            }
        }
    }
}

private data class NotificationCardFocus(
    val open: FocusRequester = FocusRequester(),
    val read: FocusRequester = FocusRequester(),
    val delete: FocusRequester = FocusRequester(),
)

@Composable
fun LocalNotificationCenterScreen(
    notifications: List<LocalEpisodeNotification>,
    unreadCount: Int,
    isTv: Boolean,
    onBack: () -> Unit,
    onOpen: (LocalEpisodeNotification) -> Unit,
    onMarkRead: (LocalEpisodeNotification) -> Unit,
    onReadAll: () -> Unit,
    onDelete: (LocalEpisodeNotification) -> Unit,
    onClearAll: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val colors = LocalHulkColors.current
    val backRequester = remember { FocusRequester() }
    val readAllRequester = remember { FocusRequester() }
    val clearRequester = remember { FocusRequester() }
    val focusScope = rememberCoroutineScope()
    val keys = notifications.map(LocalEpisodeNotification::id)
    val cardFocus = remember(keys) { notifications.associate { it.id to NotificationCardFocus() } }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding(),
    ) {
        val metrics = localNotificationCenterMetrics(
            widthDp = maxWidth.value.roundToInt(),
            heightDp = maxHeight.value.roundToInt(),
            isTv = isTv,
        )
        val firstRequester = notifications.firstOrNull()?.let { cardFocus[it.id]?.open }

        LaunchedEffect(keys, isTv) {
            if (isTv) {
                delay(90L)
                runCatching { (firstRequester ?: backRequester).requestFocus() }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = metrics.maxContentWidthDp.dp)
                .padding(
                    horizontal = metrics.horizontalPaddingDp.dp,
                    vertical = metrics.topPaddingDp.dp,
                ),
        ) {
            if (isTv) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FocusButton(
                        text = "رجوع",
                        onClick = onBack,
                        primary = false,
                        outlined = true,
                        compact = true,
                        scaleOnFocus = false,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .focusRequester(backRequester)
                            .focusProperties {
                                down = firstRequester ?: FocusRequester.Cancel
                                left = if (notifications.isNotEmpty()) readAllRequester else FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            },
                    )
                    Column(Modifier.weight(1f)) {
                        NotificationCenterTitle(unreadCount = unreadCount, isTv = true)
                    }
                    if (notifications.isNotEmpty()) {
                        FocusButton(
                            text = "تعليم الكل كمقروء",
                            onClick = {
                                onReadAll()
                                focusScope.launch {
                                    delay(80L)
                                    runCatching { (firstRequester ?: backRequester).requestFocus() }
                                }
                            },
                            enabled = unreadCount > 0,
                            primary = false,
                            outlined = true,
                            compact = true,
                            scaleOnFocus = false,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .focusRequester(readAllRequester)
                                .focusProperties {
                                    down = firstRequester ?: FocusRequester.Cancel
                                    right = backRequester
                                    left = clearRequester
                                },
                        )
                        FocusButton(
                            text = "مسح الكل",
                            onClick = onClearAll,
                            primary = false,
                            outlined = true,
                            compact = true,
                            scaleOnFocus = false,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .focusRequester(clearRequester)
                                .focusProperties {
                                    down = firstRequester ?: FocusRequester.Cancel
                                    right = readAllRequester
                                    left = FocusRequester.Cancel
                                },
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FocusButton(
                        text = "رجوع",
                        onClick = onBack,
                        primary = false,
                        outlined = true,
                        compact = true,
                        scaleOnFocus = false,
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        NotificationCenterTitle(unreadCount = unreadCount, isTv = false)
                    }
                }
                if (notifications.isNotEmpty()) {
                    Spacer(Modifier.height(9.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FocusButton(
                            text = "تعليم الكل كمقروء",
                            onClick = onReadAll,
                            enabled = unreadCount > 0,
                            primary = false,
                            outlined = true,
                            compact = true,
                            scaleOnFocus = false,
                            modifier = Modifier.weight(1.35f).heightIn(min = 48.dp),
                        )
                        FocusButton(
                            text = "مسح الكل",
                            onClick = onClearAll,
                            primary = false,
                            outlined = true,
                            compact = true,
                            scaleOnFocus = false,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(if (isTv) 20.dp else 14.dp))

            if (notifications.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (isTv) 260.dp else 190.dp)
                        .clip(RoundedCornerShape(if (isTv) 20.dp else 16.dp))
                        .background(colors.surface.copy(alpha = .78f))
                        .border(1.dp, colors.line.copy(alpha = .38f), RoundedCornerShape(if (isTv) 20.dp else 16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "لا توجد إشعارات جديدة",
                        color = colors.textMuted,
                        fontSize = if (isTv) 18.sp else 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = if (isTv) 34.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isTv) 12.dp else 9.dp),
                ) {
                    items(notifications, key = LocalEpisodeNotification::id) { notification ->
                        val index = notifications.indexOfFirst { it.id == notification.id }
                        val focus = checkNotNull(cardFocus[notification.id])
                        val previous = nextLocalNotificationFocusIndex(index, notifications.size, movingDown = false)
                            ?.let { previousIndex -> cardFocus[notifications[previousIndex].id]?.open }
                            ?: backRequester
                        val next = nextLocalNotificationFocusIndex(index, notifications.size, movingDown = true)
                            ?.let { nextIndex -> cardFocus[notifications[nextIndex].id]?.open }
                            ?: FocusRequester.Cancel
                        LocalNotificationCard(
                            notification = notification,
                            metrics = metrics,
                            isTv = isTv,
                            focus = focus,
                            previousRequester = previous,
                            nextRequester = next,
                            onOpen = { onOpen(notification) },
                            onMarkRead = { onMarkRead(notification) },
                            onDelete = { onDelete(notification) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCenterTitle(
    unreadCount: Int,
    isTv: Boolean,
) {
    val colors = LocalHulkColors.current
    Text(
        text = "مركز الإشعارات",
        color = colors.text,
        fontSize = if (isTv) 28.sp else 22.sp,
        fontWeight = FontWeight.Black,
    )
    Text(
        text = if (unreadCount > 0) "$unreadCount غير مقروء" else "كل الإشعارات مقروءة",
        color = if (unreadCount > 0) colors.goldBright else colors.textMuted,
        fontSize = if (isTv) 12.sp else 10.sp,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalNotificationCard(
    notification: LocalEpisodeNotification,
    metrics: LocalNotificationCenterMetrics,
    isTv: Boolean,
    focus: NotificationCardFocus,
    previousRequester: FocusRequester,
    nextRequester: FocusRequester,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val focusScope = rememberCoroutineScope()
    val shape = RoundedCornerShape(if (isTv) 18.dp else 14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (notification.read) colors.surface.copy(alpha = .74f)
                else colors.surfaceRaised.copy(alpha = .96f),
            )
            .border(
                1.dp,
                if (notification.read) colors.line.copy(alpha = .34f) else colors.gold.copy(alpha = .42f),
                shape,
            )
            .focusProperties { canFocus = false }
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(if (isTv) 15.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 16.dp else 11.dp),
    ) {
        Box(
            Modifier
                .width(metrics.posterWidthDp.dp)
                .height(metrics.posterHeightDp.dp)
                .clip(RoundedCornerShape(if (isTv) 12.dp else 10.dp))
                .background(Color(0xFF11120E)),
            contentAlignment = Alignment.Center,
        ) {
            if (!notification.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = notification.posterUrl,
                    contentDescription = notification.seriesName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                BrandLogo(Modifier.size((metrics.posterWidthDp * .72f).dp))
            }
            if (!notification.read) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(colors.goldBright),
                )
            }
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = "حلقة جديدة",
                color = colors.goldBright,
                fontSize = if (isTv) 12.sp else 10.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = notification.seriesName,
                color = colors.text,
                fontSize = if (isTv) 19.sp else 16.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "الموسم ${notification.seasonNumber} — الحلقة ${notification.episodeNumber}",
                color = colors.textMuted,
                fontSize = if (isTv) 12.sp else 10.sp,
            )
            Text(
                text = localNotificationRelativeTime(notification.createdAtEpochMs),
                color = colors.textMuted.copy(alpha = .78f),
                fontSize = if (isTv) 10.sp else 9.sp,
            )
            Spacer(Modifier.height(if (isTv) 10.dp else 8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                FocusButton(
                    text = "فتح",
                    onClick = onOpen,
                    compact = true,
                    scaleOnFocus = false,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .focusRequester(focus.open)
                        .focusProperties {
                            up = previousRequester
                            down = nextRequester
                            right = FocusRequester.Cancel
                            left = if (notification.read) focus.delete else focus.read
                        },
                )
                if (!notification.read) {
                    FocusButton(
                        text = "تعليم كمقروء",
                        onClick = {
                            onMarkRead()
                            if (isTv) {
                                focusScope.launch {
                                    delay(80L)
                                    runCatching { focus.open.requestFocus() }
                                }
                            }
                        },
                        primary = false,
                        outlined = true,
                        compact = true,
                        scaleOnFocus = false,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .focusRequester(focus.read)
                            .focusProperties {
                                up = previousRequester
                                down = nextRequester
                                right = focus.open
                                left = focus.delete
                            },
                    )
                }
                FocusButton(
                    text = "حذف",
                    onClick = onDelete,
                    primary = false,
                    outlined = true,
                    compact = true,
                    scaleOnFocus = false,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .focusRequester(focus.delete)
                        .focusProperties {
                            up = previousRequester
                            down = nextRequester
                            right = if (notification.read) focus.open else focus.read
                            left = FocusRequester.Cancel
                        },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewEpisodeAlertOverlay(
    popup: EpisodeNotificationPopup,
    isTv: Boolean,
    onPresented: () -> Unit,
    onPrimary: () -> Unit,
    onLater: () -> Unit,
) {
    BackHandler(onBack = onLater)
    val colors = LocalHulkColors.current
    val primaryRequester = remember(popup.eventIds) { FocusRequester() }
    val laterRequester = remember(popup.eventIds) { FocusRequester() }
    var userInteracted by remember(popup.eventIds) { mutableStateOf(false) }

    LaunchedEffect(popup.eventIds) {
        delay(120L)
        onPresented()
    }

    LaunchedEffect(popup.eventIds, isTv) {
        if (isTv) {
            delay(100L)
            runCatching { primaryRequester.requestFocus() }
            delay(11_900L)
            if (!userInteracted) onLater()
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(
                horizontal = if (isTv) 34.dp else 12.dp,
                vertical = if (isTv) 28.dp else 12.dp,
            ),
    ) {
        val compactPhone = !isTv && maxWidth < 420.dp
        val cardWidth = when {
            isTv -> (maxWidth * .40f).coerceIn(430.dp, 650.dp)
            maxWidth >= 700.dp -> 560.dp
            else -> maxWidth
        }
        val shape = RoundedCornerShape(if (isTv) 22.dp else 18.dp)
        Row(
            modifier = Modifier
                .align(if (isTv) Alignment.BottomEnd else Alignment.BottomCenter)
                .width(cardWidth)
                .clip(shape)
                .background(colors.surfaceRaised.copy(alpha = .985f))
                .border(2.dp, colors.gold.copy(alpha = .68f), shape)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) userInteracted = true
                    false
                }
                .padding(if (isTv) 18.dp else 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 16.dp else 11.dp),
        ) {
            Box(
                Modifier
                    .width(if (isTv) 82.dp else 66.dp)
                    .height(if (isTv) 123.dp else 99.dp)
                    .clip(RoundedCornerShape(if (isTv) 13.dp else 10.dp))
                    .background(Color(0xFF11120E)),
                contentAlignment = Alignment.Center,
            ) {
                if (!popup.posterUrl.isNullOrBlank() && !popup.summary) {
                    AsyncImage(
                        model = popup.posterUrl,
                        contentDescription = popup.seriesName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    BrandLogo(Modifier.size(if (isTv) 58.dp else 46.dp))
                }
            }

            Column(Modifier.weight(1f)) {
                Text(
                    text = when {
                        popup.summary -> "لديك ${popup.episodeCount} حلقات جديدة"
                        popup.episodeCount == 1 -> "حلقة جديدة نزلت"
                        else -> "${popup.episodeCount} حلقات جديدة نزلت"
                    },
                    color = colors.goldBright,
                    fontSize = if (isTv) 16.sp else 13.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                )
                if (!popup.summary) {
                    Text(
                        text = popup.seriesName.orEmpty(),
                        color = colors.text,
                        fontSize = if (isTv) 20.sp else 17.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val episode = popup.notifications.lastOrNull()
                    if (popup.episodeCount == 1 && episode != null) {
                        Text(
                            text = "الموسم ${episode.seasonNumber} — الحلقة ${episode.episodeNumber}",
                            color = colors.textMuted,
                            fontSize = if (isTv) 12.sp else 10.sp,
                        )
                    }
                }
                Spacer(Modifier.height(if (isTv) 12.dp else 9.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = if (compactPhone) 1 else 2,
                ) {
                    FocusButton(
                        text = when {
                            popup.summary -> "عرض الإشعارات"
                            popup.episodeCount > 1 -> "عرض الحلقات"
                            else -> "مشاهدة الآن"
                        },
                        onClick = {
                            userInteracted = true
                            onPrimary()
                        },
                        compact = true,
                        scaleOnFocus = false,
                        modifier = Modifier
                            .then(if (compactPhone) Modifier.fillMaxWidth() else Modifier)
                            .heightIn(min = 48.dp)
                            .focusRequester(primaryRequester)
                            .focusProperties {
                                right = FocusRequester.Cancel
                                left = laterRequester
                            },
                    )
                    FocusButton(
                        text = "لاحقًا",
                        onClick = {
                            userInteracted = true
                            onLater()
                        },
                        primary = false,
                        outlined = true,
                        compact = true,
                        scaleOnFocus = false,
                        modifier = Modifier
                            .then(if (compactPhone) Modifier.fillMaxWidth() else Modifier)
                            .heightIn(min = 48.dp)
                            .focusRequester(laterRequester)
                            .focusProperties {
                                right = primaryRequester
                                left = FocusRequester.Cancel
                            },
                    )
                }
            }
        }
    }
}

internal fun localNotificationRelativeTime(
    createdAtEpochMs: Long,
    nowEpochMs: Long = System.currentTimeMillis(),
): String {
    val elapsed = (nowEpochMs - createdAtEpochMs).coerceAtLeast(0L)
    val minutes = elapsed / 60_000L
    return when {
        minutes < 1 -> "الآن"
        minutes < 60 -> "منذ $minutes دقيقة"
        minutes < 24 * 60 -> "منذ ${minutes / 60} ساعة"
        else -> "منذ ${minutes / (24 * 60)} يوم"
    }
}
