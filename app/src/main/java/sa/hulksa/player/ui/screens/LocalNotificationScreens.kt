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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sa.hulksa.player.data.EpisodeNotificationPopup
import sa.hulksa.player.data.LocalNotificationItem
import sa.hulksa.player.ui.components.BrandLogo
import sa.hulksa.player.ui.theme.LocalHulkColors
import kotlin.math.roundToInt

internal data class LocalNotificationCenterMetrics(
    val horizontalPaddingDp: Int,
    val topPaddingDp: Int,
    val maxContentWidthDp: Int,
    val posterWidthDp: Int,
    val posterHeightDp: Int,
    val actionWidthDp: Int,
)

internal fun localNotificationCenterMetrics(
    widthDp: Int,
    heightDp: Int,
    isTv: Boolean,
): LocalNotificationCenterMetrics {
    val width = widthDp.coerceAtLeast(1)
    val height = heightDp.coerceAtLeast(1)
    return if (isTv) {
        val posterWidth = (width * .055f).roundToInt().coerceIn(68, 96)
        LocalNotificationCenterMetrics(
            horizontalPaddingDp = (width * .035f).roundToInt().coerceIn(32, 96),
            topPaddingDp = (height * .025f).roundToInt().coerceIn(22, 48),
            maxContentWidthDp = (width * .90f).roundToInt().coerceIn(840, 3_200),
            posterWidthDp = posterWidth,
            posterHeightDp = (posterWidth * 1.5f).roundToInt(),
            actionWidthDp = (width * .125f).roundToInt().coerceIn(148, 178),
        )
    } else {
        val tablet = width >= 600
        LocalNotificationCenterMetrics(
            horizontalPaddingDp = if (tablet) 24 else 14,
            topPaddingDp = if (tablet) 20 else 12,
            maxContentWidthDp = if (tablet) 900 else width,
            posterWidthDp = if (tablet) 84 else 68,
            posterHeightDp = if (tablet) 126 else 102,
            actionWidthDp = 0,
        )
    }
}

internal enum class NotificationFocusDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

internal enum class NotificationTvCardAction {
    OPEN,
    MARK_READ,
    DELETE,
}

internal sealed interface NotificationTvFocusTarget {
    data object Back : NotificationTvFocusTarget
    data object ReadAll : NotificationTvFocusTarget
    data object ClearAll : NotificationTvFocusTarget

    data class CardAction(
        val notificationId: String,
        val action: NotificationTvCardAction,
    ) : NotificationTvFocusTarget
}

internal data class NotificationTvCardFocusSpec(
    val notificationId: String,
    val markReadVisible: Boolean,
)

internal data class NotificationTvFocusGraph(
    val cards: List<NotificationTvCardFocusSpec>,
    val unreadCount: Int,
) {
    val hasNotifications: Boolean get() = cards.isNotEmpty()
    val readAllEnabled: Boolean get() = hasNotifications && unreadCount > 0
}

internal fun notificationTvTargetNeedsOffscreenComposition(
    current: NotificationTvFocusTarget,
    target: NotificationTvFocusTarget,
    isTargetCardComposed: (notificationId: String) -> Boolean,
): Boolean {
    val targetCard = target as? NotificationTvFocusTarget.CardAction ?: return false
    val currentCard = current as? NotificationTvFocusTarget.CardAction
    if (currentCard?.notificationId == targetCard.notificationId) return false
    return !isTargetCardComposed(targetCard.notificationId)
}

internal fun notificationTvFocusTag(target: NotificationTvFocusTarget): String = when (target) {
    NotificationTvFocusTarget.Back -> "notification-back"
    NotificationTvFocusTarget.ReadAll -> "notification-read-all"
    NotificationTvFocusTarget.ClearAll -> "notification-clear-all"
    is NotificationTvFocusTarget.CardAction ->
        "notification-card-${target.notificationId}-${target.action.name}"
}

internal const val NOTIFICATION_CENTER_SCREEN_ROOT_TAG = "notification-center-screen-root"
internal const val NOTIFICATION_TV_BACKGROUND_TAG = "notification-tv-background"
internal const val NOTIFICATION_TV_SAFE_CONTENT_TAG = "notification-tv-safe-content"
internal const val NOTIFICATION_TV_CENTER_ROOT_TAG = "notification-tv-center-root"
internal const val NOTIFICATION_TV_CENTER_LIST_TAG = "notification-tv-center-list"
internal fun notificationTvCardContainerTag(notificationId: String): String =
    "notification-card-$notificationId-container"

internal fun notificationTvBlockedDirections(
    graph: NotificationTvFocusGraph,
    current: NotificationTvFocusTarget,
): Set<NotificationFocusDirection> = NotificationFocusDirection.entries
    .filterTo(mutableSetOf()) { direction ->
        notificationTvFocusMove(graph, current, direction) == null
    }

internal fun requestNotificationTvFocusOnce(request: () -> Boolean): Boolean =
    runCatching { request() }.getOrDefault(false)

private fun NotificationTvCardFocusSpec.actions(): List<NotificationTvCardAction> = buildList {
    add(NotificationTvCardAction.OPEN)
    if (markReadVisible) add(NotificationTvCardAction.MARK_READ)
    add(NotificationTvCardAction.DELETE)
}

private fun NotificationTvFocusGraph.bulkEntry(): NotificationTvFocusTarget? = when {
    !hasNotifications -> null
    readAllEnabled -> NotificationTvFocusTarget.ReadAll
    else -> NotificationTvFocusTarget.ClearAll
}

internal fun notificationTvFocusableTargets(
    graph: NotificationTvFocusGraph,
): Set<NotificationTvFocusTarget> = buildSet {
    add(NotificationTvFocusTarget.Back)
    if (graph.hasNotifications) {
        if (graph.readAllEnabled) add(NotificationTvFocusTarget.ReadAll)
        add(NotificationTvFocusTarget.ClearAll)
        graph.cards.forEach { card ->
            card.actions().forEach { action ->
                add(NotificationTvFocusTarget.CardAction(card.notificationId, action))
            }
        }
    }
}

internal fun notificationTvFocusMove(
    graph: NotificationTvFocusGraph,
    current: NotificationTvFocusTarget,
    direction: NotificationFocusDirection,
): NotificationTvFocusTarget? {
    return when (current) {
        NotificationTvFocusTarget.Back -> when (direction) {
            NotificationFocusDirection.DOWN -> graph.bulkEntry()
            else -> null
        }
        NotificationTvFocusTarget.ReadAll -> when (direction) {
            NotificationFocusDirection.UP -> NotificationTvFocusTarget.Back
            NotificationFocusDirection.DOWN -> graph.cards.firstOrNull()?.let { card ->
                NotificationTvFocusTarget.CardAction(card.notificationId, NotificationTvCardAction.OPEN)
            }
            NotificationFocusDirection.RIGHT -> NotificationTvFocusTarget.ClearAll
            else -> null
        }
        NotificationTvFocusTarget.ClearAll -> when (direction) {
            NotificationFocusDirection.UP -> NotificationTvFocusTarget.Back
            NotificationFocusDirection.DOWN -> graph.cards.firstOrNull()?.let { card ->
                NotificationTvFocusTarget.CardAction(card.notificationId, NotificationTvCardAction.OPEN)
            }
            NotificationFocusDirection.LEFT -> NotificationTvFocusTarget.ReadAll.takeIf {
                graph.readAllEnabled
            }
            else -> null
        }
        is NotificationTvFocusTarget.CardAction -> {
            val cardIndex = graph.cards.indexOfFirst { it.notificationId == current.notificationId }
            if (cardIndex < 0) return null
            val card = graph.cards[cardIndex]
            val actions = card.actions()
            val actionIndex = actions.indexOf(current.action)
            if (actionIndex < 0) return null
            when (direction) {
                NotificationFocusDirection.LEFT,
                NotificationFocusDirection.RIGHT -> null
                NotificationFocusDirection.UP -> when {
                    actionIndex > 0 -> NotificationTvFocusTarget.CardAction(
                        card.notificationId,
                        actions[actionIndex - 1],
                    )
                    cardIndex > 0 -> graph.cards[cardIndex - 1].let { previous ->
                        NotificationTvFocusTarget.CardAction(
                            previous.notificationId,
                            previous.actions().last(),
                        )
                    }
                    else -> graph.bulkEntry()
                }
                NotificationFocusDirection.DOWN -> when {
                    actionIndex < actions.lastIndex -> NotificationTvFocusTarget.CardAction(
                        card.notificationId,
                        actions[actionIndex + 1],
                    )
                    cardIndex < graph.cards.lastIndex -> graph.cards[cardIndex + 1].let { next ->
                        NotificationTvFocusTarget.CardAction(
                            next.notificationId,
                            NotificationTvCardAction.OPEN,
                        )
                    }
                    else -> null
                }
            }
        }
    }
}

internal fun notificationTvFocusFallback(
    current: NotificationTvFocusTarget,
    previousGraph: NotificationTvFocusGraph,
    currentGraph: NotificationTvFocusGraph,
): NotificationTvFocusTarget {
    if (current in notificationTvFocusableTargets(currentGraph)) return current
    return when (current) {
        NotificationTvFocusTarget.Back -> NotificationTvFocusTarget.Back
        NotificationTvFocusTarget.ReadAll -> if (currentGraph.hasNotifications) {
            NotificationTvFocusTarget.ClearAll
        } else {
            NotificationTvFocusTarget.Back
        }
        NotificationTvFocusTarget.ClearAll -> NotificationTvFocusTarget.Back
        is NotificationTvFocusTarget.CardAction -> {
            val sameCard = currentGraph.cards.firstOrNull {
                it.notificationId == current.notificationId
            }
            if (sameCard != null) {
                return NotificationTvFocusTarget.CardAction(
                    sameCard.notificationId,
                    NotificationTvCardAction.OPEN,
                )
            }

            val removedIndex = previousGraph.cards.indexOfFirst {
                it.notificationId == current.notificationId
            }
            val remainingIds = currentGraph.cards.mapTo(mutableSetOf()) { it.notificationId }
            val nextCard = previousGraph.cards
                .drop((removedIndex + 1).coerceAtLeast(0))
                .firstOrNull { it.notificationId in remainingIds }
            if (nextCard != null) {
                return NotificationTvFocusTarget.CardAction(
                    nextCard.notificationId,
                    NotificationTvCardAction.OPEN,
                )
            }
            val previousCard = if (removedIndex > 0) {
                previousGraph.cards
                    .take(removedIndex)
                    .lastOrNull { it.notificationId in remainingIds }
            } else {
                null
            }
            if (previousCard != null) {
                val surviving = currentGraph.cards.first {
                    it.notificationId == previousCard.notificationId
                }
                return NotificationTvFocusTarget.CardAction(
                    surviving.notificationId,
                    surviving.actions().last(),
                )
            }
            NotificationTvFocusTarget.Back
        }
    }
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
                contentDescription = "الاشعارات",
                tint = if (focused) Color.Black else colors.text,
                modifier = Modifier.size(21.dp),
            )
        }
        if (badgeMetrics != null) {
            val badgeShape = RoundedCornerShape((badgeMetrics.heightDp / 2).dp)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
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
                    modifier = Modifier.offset(y = (-3).dp),
                )
            }
        }
    }
}

private class NotificationFocusHandle {
    val requester = FocusRequester()

    private var placedSignal = CompletableDeferred<Unit>()
    var isPlaced: Boolean = false
        private set

    fun onPlaced() {
        isPlaced = true
        placedSignal.complete(Unit)
    }

    fun onDisposed() {
        val detachedSignal = placedSignal
        isPlaced = false
        placedSignal = CompletableDeferred()
        detachedSignal.complete(Unit)
    }

    suspend fun awaitPlaced(): Boolean {
        val expectedSignal = placedSignal
        expectedSignal.await()
        return isPlaced && placedSignal === expectedSignal
    }
}

private data class NotificationCardFocus(
    val open: NotificationFocusHandle = NotificationFocusHandle(),
    val read: NotificationFocusHandle = NotificationFocusHandle(),
    val delete: NotificationFocusHandle = NotificationFocusHandle(),
)

private class NotificationFocusMoveTransaction {
    var job: Job? = null
    var target: NotificationTvFocusTarget? = null

    val isActive: Boolean
        get() = job?.isActive == true
}

private class NotificationFocusHistory(var graph: NotificationTvFocusGraph)

private fun requestFocusAndConsume(target: FocusRequester?): Boolean {
    if (target != null) runCatching { target.requestFocus() }
    return true
}

private fun keyToDirection(key: Key): NotificationFocusDirection? = when (key) {
    Key.DirectionUp -> NotificationFocusDirection.UP
    Key.DirectionDown -> NotificationFocusDirection.DOWN
    Key.DirectionLeft -> NotificationFocusDirection.LEFT
    Key.DirectionRight -> NotificationFocusDirection.RIGHT
    else -> null
}

@Composable
private fun NotificationActionButton(
    text: String,
    onClick: () -> Unit,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    headerCompact: Boolean = false,
    tvCenterCompact: Boolean = false,
    tvFocusHandle: NotificationFocusHandle? = null,
    tvFocusTag: String? = null,
    tvFocusDestinations: Map<NotificationFocusDirection, FocusRequester>? = null,
    tvBlockedDirections: Set<NotificationFocusDirection>? = null,
    onTvDirection: ((NotificationFocusDirection) -> Boolean)? = null,
    onFocused: (() -> Unit)? = null,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    if (tvFocusHandle != null) {
        DisposableEffect(tvFocusHandle) {
            onDispose { tvFocusHandle.onDisposed() }
        }
    }
    val shape = RoundedCornerShape(
        when {
            headerCompact -> 9.dp
            isTv && tvCenterCompact -> 10.dp
            isTv -> 13.dp
            else -> 12.dp
        },
    )
    val background = when {
        !enabled -> colors.surfaceRaised.copy(alpha = .50f)
        focused -> colors.goldBright
        primary && isTv -> colors.gold.copy(alpha = .14f)
        primary -> colors.gold
        else -> Color(0xFF151711)
    }
    val borderColor = when {
        !enabled -> colors.line.copy(alpha = .36f)
        focused -> Color.White.copy(alpha = .94f)
        primary -> colors.goldBright.copy(alpha = .62f)
        else -> colors.gold.copy(alpha = .46f)
    }
    val textColor = when {
        !enabled -> colors.textMuted
        focused -> Color.Black
        primary && isTv -> colors.goldBright
        primary -> Color.Black
        else -> colors.text
    }
    Box(
        modifier = modifier
            .then(
                if (tvFocusHandle != null) {
                    Modifier.focusRequester(tvFocusHandle.requester)
                } else {
                    Modifier
                },
            )
            .then(if (tvFocusTag != null) Modifier.testTag(tvFocusTag) else Modifier)
            .clip(shape)
            .background(background)
            .border(if (focused) 3.dp else 1.dp, borderColor, shape)
            .semantics(mergeDescendants = true) { contentDescription = text }
            .focusProperties {
                canFocus = enabled
                if (isTv) {
                    fun destinationFor(direction: NotificationFocusDirection): FocusRequester =
                        tvFocusDestinations?.get(direction)
                            ?: if (tvBlockedDirections == null || direction in tvBlockedDirections) {
                                FocusRequester.Cancel
                            } else {
                                FocusRequester.Default
                            }

                    up = destinationFor(NotificationFocusDirection.UP)
                    down = destinationFor(NotificationFocusDirection.DOWN)
                    left = destinationFor(NotificationFocusDirection.LEFT)
                    right = destinationFor(NotificationFocusDirection.RIGHT)
                }
            }
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocused?.invoke()
            }
            .then(
                if (isTv && onTvDirection != null) {
                    Modifier.onPreviewKeyEvent { event ->
                        val direction = keyToDirection(event.key) ?: return@onPreviewKeyEvent false
                        event.type == KeyEventType.KeyDown && onTvDirection(direction)
                    }
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(
                if (tvFocusHandle != null) {
                    Modifier.onGloballyPositioned { tvFocusHandle.onPlaced() }
                } else {
                    Modifier
                },
            )
            .padding(
                horizontal = when {
                    headerCompact -> 12.dp
                    isTv && tvCenterCompact -> 14.dp
                    isTv -> 15.dp
                    else -> 14.dp
                },
                vertical = when {
                    headerCompact -> 6.dp
                    isTv && tvCenterCompact -> 8.dp
                    else -> 10.dp
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = if (headerCompact) 12.sp else if (isTv) 14.sp else 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = if (isTv && tvCenterCompact) TextOverflow.Clip else TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun LocalNotificationCenterScreen(
    notifications: List<LocalNotificationItem>,
    unreadCount: Int,
    isTv: Boolean,
    onBack: () -> Unit,
    onOpen: (LocalNotificationItem) -> Unit,
    onMarkRead: (LocalNotificationItem) -> Unit,
    onReadAll: () -> Unit,
    onDelete: (LocalNotificationItem) -> Unit,
    onClearAll: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val colors = LocalHulkColors.current

    Box(
        Modifier
            .fillMaxSize()
            .testTag(NOTIFICATION_CENTER_SCREEN_ROOT_TAG),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .then(
                    if (isTv) {
                        Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        colors.background,
                                        colors.surface.copy(alpha = .44f),
                                        colors.surface.copy(alpha = .44f),
                                    ),
                                ),
                            )
                            .testTag(NOTIFICATION_TV_BACKGROUND_TAG)
                    } else {
                        Modifier
                    },
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (isTv) Modifier.testTag(NOTIFICATION_TV_SAFE_CONTENT_TAG) else Modifier,
                    ),
            ) {
                val availableWidthDp = maxWidth.value.roundToInt().coerceAtLeast(1)
                val metrics = localNotificationCenterMetrics(
                    widthDp = availableWidthDp,
                    heightDp = maxHeight.value.roundToInt(),
                    isTv = isTv,
                ).copy(maxContentWidthDp = availableWidthDp)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    if (isTv) {
                        TvLocalNotificationCenter(
                            notifications = notifications,
                            unreadCount = unreadCount,
                            metrics = metrics,
                            onBack = onBack,
                            onOpen = onOpen,
                            onMarkRead = onMarkRead,
                            onReadAll = onReadAll,
                            onDelete = onDelete,
                            onClearAll = onClearAll,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        MobileLocalNotificationCenter(
                            notifications = notifications,
                            unreadCount = unreadCount,
                            metrics = metrics,
                            onBack = onBack,
                            onOpen = onOpen,
                            onMarkRead = onMarkRead,
                            onReadAll = onReadAll,
                            onDelete = onDelete,
                            onClearAll = onClearAll,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TvLocalNotificationCenter(
    notifications: List<LocalNotificationItem>,
    unreadCount: Int,
    metrics: LocalNotificationCenterMetrics,
    onBack: () -> Unit,
    onOpen: (LocalNotificationItem) -> Unit,
    onMarkRead: (LocalNotificationItem) -> Unit,
    onReadAll: () -> Unit,
    onDelete: (LocalNotificationItem) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onFocusedTargetChanged: (NotificationTvFocusTarget) -> Unit = {},
) {
    val colors = LocalHulkColors.current
    val backFocus = remember { NotificationFocusHandle() }
    val readAllFocus = remember { NotificationFocusHandle() }
    val clearAllFocus = remember { NotificationFocusHandle() }
    val cardFocusRegistry = remember { mutableMapOf<String, NotificationCardFocus>() }
    val notificationIds = remember(notifications) { notifications.map(LocalNotificationItem::id) }
    val notificationIdSet = remember(notificationIds) { notificationIds.toSet() }
    notifications.forEach { notification ->
        cardFocusRegistry.getOrPut(notification.id) { NotificationCardFocus() }
    }
    SideEffect {
        cardFocusRegistry.keys.retainAll(notificationIdSet)
    }

    val graph = remember(notifications, unreadCount) {
        NotificationTvFocusGraph(
            cards = notifications.map { notification ->
                NotificationTvCardFocusSpec(
                    notificationId = notification.id,
                    markReadVisible = notification is LocalNotificationItem.Episode && !notification.read,
                )
            },
            unreadCount = unreadCount,
        )
    }
    val focusScope = rememberCoroutineScope()
    var focusedTarget by remember { mutableStateOf<NotificationTvFocusTarget?>(null) }
    val focusHistory = remember { NotificationFocusHistory(graph) }
    val focusTransaction = remember { NotificationFocusMoveTransaction() }

    fun focusHandleFor(target: NotificationTvFocusTarget): NotificationFocusHandle? = when (target) {
        NotificationTvFocusTarget.Back -> backFocus
        NotificationTvFocusTarget.ReadAll -> readAllFocus
        NotificationTvFocusTarget.ClearAll -> clearAllFocus
        is NotificationTvFocusTarget.CardAction -> cardFocusRegistry[target.notificationId]?.let { focus ->
            when (target.action) {
                NotificationTvCardAction.OPEN -> focus.open
                NotificationTvCardAction.MARK_READ -> focus.read
                NotificationTvCardAction.DELETE -> focus.delete
            }
        }
    }

    fun requestAttachedFocus(target: NotificationTvFocusTarget): Boolean {
        val handle = focusHandleFor(target) ?: return false
        if (!handle.isPlaced) return false
        return requestNotificationTvFocusOnce { handle.requester.requestFocus() }
    }

    fun focusDestinationsFor(
        current: NotificationTvFocusTarget,
    ): Map<NotificationFocusDirection, FocusRequester> = buildMap {
        NotificationFocusDirection.entries.forEach { direction ->
            val target = notificationTvFocusMove(graph, current, direction)
            val requester = target?.let(::focusHandleFor)?.requester
            if (requester != null) put(direction, requester)
        }
    }

    suspend fun requestFocusAfterTargetPlacement(
        target: NotificationTvFocusTarget,
        moveGraph: NotificationTvFocusGraph,
        composeOffscreenCard: Boolean,
    ): Boolean {
        val handle = focusHandleFor(target) ?: return false
        val cardTarget = target as? NotificationTvFocusTarget.CardAction
        if (composeOffscreenCard && cardTarget != null) {
            // Manual list movement is reserved for composing the exact offscreen ID.
            val targetAlreadyComposed = listState.layoutInfo.visibleItemsInfo.any { item ->
                item.key == cardTarget.notificationId
            }
            if (!targetAlreadyComposed) {
                val cardIndex = moveGraph.cards.indexOfFirst { card ->
                    card.notificationId == cardTarget.notificationId
                }
                if (cardIndex < 0) return false
                listState.scrollToItem(cardIndex)
            }
        }
        if (!handle.isPlaced && !handle.awaitPlaced()) return false
        if (cardTarget != null) {
            val targetStillExists = moveGraph.cards.any { card ->
                card.notificationId == cardTarget.notificationId
            }
            val targetIsVisible = listState.layoutInfo.visibleItemsInfo.any { item ->
                item.key == cardTarget.notificationId
            }
            if (!targetStillExists || !targetIsVisible || !handle.isPlaced) return false
        }
        return requestNotificationTvFocusOnce { handle.requester.requestFocus() }
    }

    fun startFocusTransaction(
        target: NotificationTvFocusTarget,
        moveGraph: NotificationTvFocusGraph,
        composeOffscreenCard: Boolean,
        onSettled: ((Boolean) -> Unit)? = null,
    ): Boolean {
        if (focusTransaction.isActive) {
            onSettled?.invoke(false)
            return false
        }
        if (focusHandleFor(target) == null) {
            onSettled?.invoke(false)
            return false
        }
        lateinit var launchedJob: Job
        launchedJob = focusScope.launch(start = CoroutineStart.LAZY) {
            var focusRequested = false
            try {
                focusRequested = requestFocusAfterTargetPlacement(
                    target = target,
                    moveGraph = moveGraph,
                    composeOffscreenCard = composeOffscreenCard,
                )
            } finally {
                if (focusTransaction.job === launchedJob) {
                    focusTransaction.job = null
                    focusTransaction.target = null
                }
                onSettled?.invoke(focusRequested)
            }
        }
        focusTransaction.job = launchedJob
        focusTransaction.target = target
        launchedJob.start()
        return true
    }

    fun requestFocusMove(
        current: NotificationTvFocusTarget,
        target: NotificationTvFocusTarget,
        moveGraph: NotificationTvFocusGraph,
        onSettled: ((Boolean) -> Unit)? = null,
    ): Boolean {
        if (focusTransaction.isActive) {
            onSettled?.invoke(false)
            return false
        }
        val handle = focusHandleFor(target)
        if (handle == null) {
            onSettled?.invoke(false)
            return false
        }
        val currentCard = current as? NotificationTvFocusTarget.CardAction
        val targetCard = target as? NotificationTvFocusTarget.CardAction
        if (
            currentCard != null &&
            targetCard != null &&
            currentCard.notificationId == targetCard.notificationId
        ) {
            // Action-to-action movement within one card never consults or moves the list.
            val focusRequested = requestAttachedFocus(target)
            onSettled?.invoke(focusRequested)
            return focusRequested
        }

        val needsOffscreenComposition = notificationTvTargetNeedsOffscreenComposition(
            current = current,
            target = target,
            isTargetCardComposed = { notificationId ->
                listState.layoutInfo.visibleItemsInfo.any { item -> item.key == notificationId }
            },
        )
        if (!needsOffscreenComposition && handle.isPlaced) {
            val focusRequested = requestAttachedFocus(target)
            onSettled?.invoke(focusRequested)
            return focusRequested
        }
        return startFocusTransaction(
            target = target,
            moveGraph = moveGraph,
            composeOffscreenCard = needsOffscreenComposition,
            onSettled = onSettled,
        )
    }

    fun handleDirection(
        current: NotificationTvFocusTarget,
        direction: NotificationFocusDirection,
    ): Boolean {
        if (focusTransaction.isActive) return true
        val target = notificationTvFocusMove(graph, current, direction) ?: return false
        return requestFocusMove(current, target, graph)
    }

    fun markReadWithFocusTransfer(notification: LocalNotificationItem) {
        val current = NotificationTvFocusTarget.CardAction(
            notification.id,
            NotificationTvCardAction.MARK_READ,
        )
        val fallback = NotificationTvFocusTarget.CardAction(
            notification.id,
            NotificationTvCardAction.OPEN,
        )
        requestFocusMove(current, fallback, graph) {
            onMarkRead(notification)
        }
    }

    fun deleteWithFocusTransfer(notification: LocalNotificationItem) {
        val current = NotificationTvFocusTarget.CardAction(
            notification.id,
            NotificationTvCardAction.DELETE,
        )
        val graphAfterDelete = graph.copy(
            cards = graph.cards.filterNot { card -> card.notificationId == notification.id },
            unreadCount = (graph.unreadCount - if (notification.read) 0 else 1).coerceAtLeast(0),
        )
        val fallback = notificationTvFocusFallback(
            current = current,
            previousGraph = graph,
            currentGraph = graphAfterDelete,
        )
        requestFocusMove(current, fallback, graph) {
            onDelete(notification)
        }
    }

    fun recordFocusedTarget(target: NotificationTvFocusTarget) {
        if (focusedTarget == target) return
        focusedTarget = target
        onFocusedTargetChanged(target)
    }

    LaunchedEffect(backFocus) {
        if (backFocus.awaitPlaced()) {
            requestNotificationTvFocusOnce { backFocus.requester.requestFocus() }
        }
    }

    LaunchedEffect(graph) {
        val runningTransaction = focusTransaction.job?.takeIf { it.isActive }
        runningTransaction?.cancelAndJoin()
        if (focusTransaction.job === runningTransaction) {
            focusTransaction.job = null
            focusTransaction.target = null
        }
        val current = focusedTarget
        val previousGraph = focusHistory.graph
        focusHistory.graph = graph
        if (current != null) {
            val fallback = notificationTvFocusFallback(
                current = current,
                previousGraph = previousGraph,
                currentGraph = graph,
            )
            if (fallback != current) {
                requestFocusMove(current, fallback, graph)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(max = metrics.maxContentWidthDp.dp)
            .fillMaxWidth()
            .testTag(NOTIFICATION_TV_CENTER_ROOT_TAG)
            .padding(
                horizontal = metrics.horizontalPaddingDp.dp,
                vertical = metrics.topPaddingDp.dp,
            ),
    ) {
        AdaptiveNotificationCenterHeader(
            unreadCount = unreadCount,
            isTv = true,
            showActions = notifications.isNotEmpty(),
            wideLayout = true,
            backAction = {
                val backTarget = NotificationTvFocusTarget.Back
                NotificationActionButton(
                    text = "رجوع",
                    onClick = onBack,
                    isTv = true,
                    primary = false,
                    headerCompact = true,
                    tvCenterCompact = true,
                    tvFocusHandle = backFocus,
                    tvFocusTag = notificationTvFocusTag(backTarget),
                    tvFocusDestinations = focusDestinationsFor(backTarget),
                    tvBlockedDirections = notificationTvBlockedDirections(graph, backTarget),
                    onTvDirection = { direction -> handleDirection(backTarget, direction) },
                    onFocused = { recordFocusedTarget(backTarget) },
                    modifier = Modifier
                        .width(82.dp)
                        .height(40.dp),
                )
            },
            actions = { actionsModifier ->
                Row(
                    modifier = actionsModifier,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val clearAllTarget = NotificationTvFocusTarget.ClearAll
                    NotificationActionButton(
                        text = "مسح الكل",
                        onClick = onClearAll,
                        isTv = true,
                        primary = false,
                        headerCompact = true,
                        tvCenterCompact = true,
                        tvFocusHandle = clearAllFocus,
                        tvFocusTag = notificationTvFocusTag(clearAllTarget),
                        tvFocusDestinations = focusDestinationsFor(clearAllTarget),
                        tvBlockedDirections = notificationTvBlockedDirections(graph, clearAllTarget),
                        onTvDirection = { direction -> handleDirection(clearAllTarget, direction) },
                        onFocused = { recordFocusedTarget(clearAllTarget) },
                        modifier = Modifier
                            .width(96.dp)
                            .height(40.dp),
                    )
                    val readAllTarget = NotificationTvFocusTarget.ReadAll
                    NotificationActionButton(
                        text = "تعليم الكل كمقروء",
                        onClick = onReadAll,
                        isTv = true,
                        enabled = unreadCount > 0,
                        primary = false,
                        headerCompact = true,
                        tvCenterCompact = true,
                        tvFocusHandle = readAllFocus,
                        tvFocusTag = notificationTvFocusTag(readAllTarget),
                        tvFocusDestinations = focusDestinationsFor(readAllTarget),
                        tvBlockedDirections = notificationTvBlockedDirections(graph, readAllTarget),
                        onTvDirection = { direction -> handleDirection(readAllTarget, direction) },
                        onFocused = { recordFocusedTarget(readAllTarget) },
                        modifier = Modifier
                            .width(164.dp)
                            .height(40.dp),
                    )
                }
            },
        )

        Spacer(Modifier.height(10.dp))
        if (notifications.isEmpty()) {
            NotificationCenterEmptyState(
                isTv = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag(NOTIFICATION_TV_CENTER_LIST_TAG),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(
                    items = notifications,
                    key = { _, notification -> notification.id },
                ) { _, notification ->
                    TvLocalNotificationCard(
                        notification = notification,
                        metrics = metrics,
                        graph = graph,
                        focusDestinations = ::focusDestinationsFor,
                        focus = checkNotNull(cardFocusRegistry[notification.id]),
                        onDirection = ::handleDirection,
                        onFocused = ::recordFocusedTarget,
                        onOpen = { onOpen(notification) },
                        onMarkRead = { markReadWithFocusTransfer(notification) },
                        onDelete = { deleteWithFocusTransfer(notification) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdaptiveNotificationCenterHeader(
    unreadCount: Int,
    isTv: Boolean,
    showActions: Boolean,
    wideLayout: Boolean,
    backAction: @Composable () -> Unit,
    actions: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        if (wideLayout && showActions) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (isTv) 54.dp else 52.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 24.dp else 16.dp),
            ) {
                NotificationCenterHeaderIdentity(
                    unreadCount = unreadCount,
                    isTv = isTv,
                    backAction = backAction,
                    modifier = Modifier.weight(1f),
                )
                actions(Modifier)
            }
        } else {
            NotificationCenterHeaderIdentity(
                unreadCount = unreadCount,
                isTv = isTv,
                backAction = backAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (isTv) 54.dp else 52.dp),
            )
            if (showActions) {
                Spacer(Modifier.height(10.dp))
                actions(Modifier.fillMaxWidth())
            }
        }

        Spacer(Modifier.height(if (isTv) 12.dp else 10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.line.copy(alpha = .58f)),
        )
    }
}

@Composable
private fun NotificationCenterHeaderIdentity(
    unreadCount: Int,
    isTv: Boolean,
    backAction: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 12.dp else 9.dp),
    ) {
        backAction()
        Box(
            Modifier
                .width(3.dp)
                .height(if (isTv) 42.dp else 36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.goldBright),
        )
        NotificationCenterTitle(
            unreadCount = unreadCount,
            isTv = isTv,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MobileLocalNotificationCenter(
    notifications: List<LocalNotificationItem>,
    unreadCount: Int,
    metrics: LocalNotificationCenterMetrics,
    onBack: () -> Unit,
    onOpen: (LocalNotificationItem) -> Unit,
    onMarkRead: (LocalNotificationItem) -> Unit,
    onReadAll: () -> Unit,
    onDelete: (LocalNotificationItem) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val availableWidthDp = maxWidth.value.roundToInt().coerceAtLeast(1)
        val horizontalPadding = when {
            availableWidthDp >= 840 -> (availableWidthDp * .035f).roundToInt().coerceIn(30, 48)
            availableWidthDp >= 600 -> (availableWidthDp * .04f).roundToInt().coerceIn(24, 32)
            else -> (availableWidthDp * .045f).roundToInt().coerceIn(14, 20)
        }.dp
        val verticalPadding = when {
            availableWidthDp >= 840 -> 22.dp
            availableWidthDp >= 600 -> 18.dp
            else -> 12.dp
        }
        val wideHeader = maxWidth - horizontalPadding - horizontalPadding >= 680.dp
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal = horizontalPadding,
                    vertical = verticalPadding,
                ),
        ) {
            AdaptiveNotificationCenterHeader(
                unreadCount = unreadCount,
                isTv = false,
                showActions = notifications.isNotEmpty(),
                wideLayout = wideHeader,
                backAction = {
                    NotificationActionButton(
                        text = "رجوع",
                        onClick = onBack,
                        isTv = false,
                        primary = false,
                        headerCompact = true,
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                },
                actions = { actionsModifier ->
                    if (wideHeader) {
                        Row(
                            modifier = actionsModifier,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NotificationActionButton(
                                text = "مسح الكل",
                                onClick = onClearAll,
                                isTv = false,
                                primary = false,
                                headerCompact = true,
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                            NotificationActionButton(
                                text = "تعليم الكل كمقروء",
                                onClick = onReadAll,
                                isTv = false,
                                enabled = unreadCount > 0,
                                primary = false,
                                headerCompact = true,
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    } else {
                        FlowRow(
                            modifier = actionsModifier,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = 2,
                        ) {
                            NotificationActionButton(
                                text = "مسح الكل",
                                onClick = onClearAll,
                                isTv = false,
                                primary = false,
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                            NotificationActionButton(
                                text = "تعليم الكل كمقروء",
                                onClick = onReadAll,
                                isTv = false,
                                enabled = unreadCount > 0,
                                primary = false,
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                },
            )

            Spacer(Modifier.height(10.dp))
            if (notifications.isEmpty()) {
                NotificationCenterEmptyState(
                    isTv = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    items(notifications, key = LocalNotificationItem::id) { notification ->
                        MobileLocalNotificationCard(
                            notification = notification,
                            metrics = metrics,
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
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    Column(modifier = modifier) {
        Text(
            text = "مركز الإشعارات",
            color = colors.text,
            fontSize = if (isTv) 28.sp else 22.sp,
            lineHeight = if (isTv) 32.sp else 26.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (unreadCount > 0) "$unreadCount غير مقروء" else "كل الاشعارات مقروءة",
            color = if (unreadCount > 0) colors.goldBright else colors.textMuted,
            fontSize = if (isTv) 12.sp else 10.sp,
            lineHeight = if (isTv) 16.sp else 13.sp,
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NotificationCenterEmptyState(
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(if (isTv) 18.dp else 16.dp)
    Box(
        modifier
            .heightIn(min = if (isTv) 164.dp else 170.dp)
            .clip(shape)
            .background(colors.surface.copy(alpha = .72f))
            .border(1.dp, colors.line.copy(alpha = .34f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "لا توجد اشعارات جديدة",
            color = colors.textMuted,
            fontSize = if (isTv) 16.sp else 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TvLocalNotificationCard(
    notification: LocalNotificationItem,
    metrics: LocalNotificationCenterMetrics,
    graph: NotificationTvFocusGraph,
    focusDestinations: (
        NotificationTvFocusTarget,
    ) -> Map<NotificationFocusDirection, FocusRequester>,
    focus: NotificationCardFocus,
    onDirection: (NotificationTvFocusTarget, NotificationFocusDirection) -> Boolean,
    onFocused: (NotificationTvFocusTarget) -> Unit,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(14.dp)
    val episode = (notification as? LocalNotificationItem.Episode)?.notification
    val system = (notification as? LocalNotificationItem.System)?.notification
    val title = episode?.seriesName ?: system?.title.orEmpty()
    val detail = if (episode != null) {
        "الموسم ${episode.seasonNumber} — الحلقة ${episode.episodeNumber}"
    } else {
        system?.message.orEmpty()
    }
    val openTarget = NotificationTvFocusTarget.CardAction(
        notification.id,
        NotificationTvCardAction.OPEN,
    )
    val markReadTarget = NotificationTvFocusTarget.CardAction(
        notification.id,
        NotificationTvCardAction.MARK_READ,
    )
    val deleteTarget = NotificationTvFocusTarget.CardAction(
        notification.id,
        NotificationTvCardAction.DELETE,
    )
    val minimumHeight = maxOf(metrics.posterHeightDp + 20, 152)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minimumHeight.dp)
            .testTag(notificationTvCardContainerTag(notification.id))
            .clip(shape)
            .background(if (notification.read) colors.surface.copy(alpha = .72f) else colors.surfaceRaised.copy(alpha = .94f))
            .border(
                1.dp,
                if (notification.read) colors.line.copy(alpha = .32f) else colors.gold.copy(alpha = .52f),
                shape,
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .width(metrics.posterWidthDp.dp)
                .height(metrics.posterHeightDp.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF11120E)),
            contentAlignment = Alignment.Center,
        ) {
            if (!episode?.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = episode?.posterUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                BrandLogo(Modifier.size((metrics.posterWidthDp * .72f).dp))
            }
            if (!notification.read) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(8.dp).clip(CircleShape).background(colors.goldBright),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (episode != null) "حلقة جديدة" else "رسالة من HULK SA",
                color = colors.goldBright,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Start,
            )
            Text(
                text = title,
                color = colors.text,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = detail,
                color = colors.textMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = if (episode != null) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = localNotificationRelativeTime(notification.createdAtEpochMs),
                color = colors.textMuted.copy(alpha = .76f),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                textAlign = TextAlign.Start,
            )
        }
        Column(
            modifier = Modifier.width(metrics.actionWidthDp.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NotificationActionButton(
                text = if (episode != null) "مشاهدة الان" else "حسنا",
                onClick = onOpen,
                isTv = true,
                tvCenterCompact = true,
                tvFocusHandle = focus.open,
                tvFocusTag = notificationTvFocusTag(openTarget),
                tvFocusDestinations = focusDestinations(openTarget),
                tvBlockedDirections = notificationTvBlockedDirections(graph, openTarget),
                onTvDirection = { direction -> onDirection(openTarget, direction) },
                onFocused = { onFocused(openTarget) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
            )
            if (episode != null && !notification.read) {
                NotificationActionButton(
                    text = "تعليم كمقروء",
                    onClick = onMarkRead,
                    isTv = true,
                    primary = false,
                    tvCenterCompact = true,
                    tvFocusHandle = focus.read,
                    tvFocusTag = notificationTvFocusTag(markReadTarget),
                    tvFocusDestinations = focusDestinations(markReadTarget),
                    tvBlockedDirections = notificationTvBlockedDirections(graph, markReadTarget),
                    onTvDirection = { direction -> onDirection(markReadTarget, direction) },
                    onFocused = { onFocused(markReadTarget) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                )
            }
            NotificationActionButton(
                text = "حذف",
                onClick = onDelete,
                isTv = true,
                primary = false,
                tvCenterCompact = true,
                tvFocusHandle = focus.delete,
                tvFocusTag = notificationTvFocusTag(deleteTarget),
                tvFocusDestinations = focusDestinations(deleteTarget),
                tvBlockedDirections = notificationTvBlockedDirections(graph, deleteTarget),
                onTvDirection = { direction -> onDirection(deleteTarget, direction) },
                onFocused = { onFocused(deleteTarget) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MobileLocalNotificationCard(
    notification: LocalNotificationItem,
    metrics: LocalNotificationCenterMetrics,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(14.dp)
    val episode = (notification as? LocalNotificationItem.Episode)?.notification
    val system = (notification as? LocalNotificationItem.System)?.notification
    val title = episode?.seriesName ?: system?.title.orEmpty()
    val detail = if (episode != null) {
        "الموسم ${episode.seasonNumber} — الحلقة ${episode.episodeNumber}"
    } else {
        system?.message.orEmpty()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (notification.read) colors.surface.copy(alpha = .72f) else colors.surfaceRaised.copy(alpha = .94f))
            .border(
                1.dp,
                if (notification.read) colors.line.copy(alpha = .32f) else colors.gold.copy(alpha = .38f),
                shape,
            )
            .focusProperties { canFocus = false }
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            Modifier
                .width(metrics.posterWidthDp.dp)
                .height(metrics.posterHeightDp.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF11120E)),
            contentAlignment = Alignment.Center,
        ) {
            if (!episode?.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = episode?.posterUrl,
                    contentDescription = title,
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
                        .padding(4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.goldBright),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = if (episode != null) "حلقة جديدة" else "رسالة من HULK SA",
                color = colors.goldBright,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = title,
                color = colors.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                color = colors.textMuted,
                fontSize = 10.sp,
                maxLines = if (episode != null) 1 else 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = localNotificationRelativeTime(notification.createdAtEpochMs),
                color = colors.textMuted.copy(alpha = .76f),
                fontSize = 9.sp,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                maxItemsInEachRow = 3,
            ) {
                NotificationActionButton(
                    text = if (episode != null) "مشاهدة الان" else "حسنا",
                    onClick = onOpen,
                    isTv = false,
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                if (episode != null && !notification.read) {
                    NotificationActionButton(
                        text = "تعليم كمقروء",
                        onClick = onMarkRead,
                        isTv = false,
                        primary = false,
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
                NotificationActionButton(
                    text = "حذف",
                    onClick = onDelete,
                    isTv = false,
                    primary = false,
                    modifier = Modifier.heightIn(min = 48.dp),
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
                    AsyncImage(model = popup.posterUrl, contentDescription = popup.seriesName, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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
                    NotificationActionButton(
                        text = if (popup.summary) "عرض الاشعارات" else "مشاهدة الان",
                        onClick = {
                            userInteracted = true
                            onPrimary()
                        },
                        isTv = isTv,
                        onTvDirection = if (isTv) { direction ->
                            when (direction) {
                                NotificationFocusDirection.LEFT -> requestFocusAndConsume(laterRequester)
                                NotificationFocusDirection.RIGHT, NotificationFocusDirection.UP, NotificationFocusDirection.DOWN -> true
                            }
                        } else null,
                        modifier = Modifier
                            .then(if (compactPhone) Modifier.fillMaxWidth() else Modifier)
                            .heightIn(min = 48.dp)
                            .focusRequester(primaryRequester),
                    )
                    NotificationActionButton(
                        text = "لاحقا",
                        onClick = {
                            userInteracted = true
                            onLater()
                        },
                        isTv = isTv,
                        primary = false,
                        onTvDirection = if (isTv) { direction ->
                            when (direction) {
                                NotificationFocusDirection.RIGHT -> requestFocusAndConsume(primaryRequester)
                                NotificationFocusDirection.LEFT, NotificationFocusDirection.UP, NotificationFocusDirection.DOWN -> true
                            }
                        } else null,
                        modifier = Modifier
                            .then(if (compactPhone) Modifier.fillMaxWidth() else Modifier)
                            .heightIn(min = 48.dp)
                            .focusRequester(laterRequester),
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
