package sa.hulksa.player.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import sa.hulksa.player.BuildConfig
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.MainDestination
import sa.hulksa.player.data.AccountProfileStateScope
import sa.hulksa.player.data.DownloadActionIntent
import sa.hulksa.player.data.FavoriteMutationOutcome
import sa.hulksa.player.data.FavoriteMutationStatus
import sa.hulksa.player.data.GrowthAction
import sa.hulksa.player.data.HomeHeroMetadataStore
import sa.hulksa.player.data.HomeHeroTechnicalMetadata
import sa.hulksa.player.data.OperationsAnnouncement
import sa.hulksa.player.data.OperationsAnnouncementSeverity
import sa.hulksa.player.data.OperationsDownloadStatus
import sa.hulksa.player.data.OperationsDownloadUiState
import sa.hulksa.player.data.OperationsUpdateConfig
import sa.hulksa.player.data.OperationsUpdateDecision
import sa.hulksa.player.data.GrowthDestination
import sa.hulksa.player.data.RenewalBannerContent
import sa.hulksa.player.data.evaluateRenewalBanner
import sa.hulksa.player.data.link
import sa.hulksa.player.data.resolveGrowthAction
import sa.hulksa.player.model.CapabilityFinding
import sa.hulksa.player.model.CapabilityStatus
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.DiagnosticIssue
import sa.hulksa.player.model.DiagnosticSeverity
import sa.hulksa.player.model.DiagnosticsState
import sa.hulksa.player.model.DownloadScheduleMode
import sa.hulksa.player.model.DownloadSettings
import sa.hulksa.player.model.FeatureRecommendation
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.model.ServerDiagnosticsReport
import sa.hulksa.player.ui.HomeMessagePresentationState
import sa.hulksa.player.ui.LocalProfileSwitchRequester
import sa.hulksa.player.ui.MobileInlineTopActions
import sa.hulksa.player.ui.mobileInlineTopActionsFitSingleRow
import sa.hulksa.player.ui.stableMobileNavigationReservedHeight
import sa.hulksa.player.ui.adaptive.HulkNavigationType
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.components.BrandLogo
import sa.hulksa.player.ui.components.BrandBadge
import sa.hulksa.player.ui.components.TvRailDestinationItem
import sa.hulksa.player.ui.components.TvRailSurface
import sa.hulksa.player.ui.components.ChannelLogo
import sa.hulksa.player.ui.components.ChannelListItem
import sa.hulksa.player.ui.components.UniversalPosterCard
import sa.hulksa.player.ui.components.MoviesCatalogBoxedCard
import sa.hulksa.player.ui.components.SeriesCatalogBoxedCard
import sa.hulksa.player.ui.components.BoxedHistoryCard
import sa.hulksa.player.ui.components.seriesHistoryIdentityText
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.goldFocusEdge
import sa.hulksa.player.ui.components.HulkTextField
import sa.hulksa.player.ui.components.LiveChannelHomeCard
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.theme.LocalHulkColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.navigationBarsPadding

internal const val FAVORITES_CATEGORY_ID = "__hulk_favorites__"
internal const val CONTINUE_CATEGORY_ID = "__hulk_continue__"
internal const val TV_CATEGORY_PARENT_HORIZONTAL_INSET_DP = 14f
internal val TV_PAGE_GUTTER = 8.dp
private val TV_LIVE_ACTION_INSET = 8.dp

/** Keeps the resting chip position unchanged while extending only the scroll viewport. */
internal data class CategorySidebarUnderlapPolicy(
    val viewportExtraDp: Float,
    val startContentPaddingDp: Float,
)

internal fun categorySidebarUnderlapPolicy(
    isTv: Boolean,
    railExpandedWidthDp: Float,
    baseContentPaddingDp: Float,
    parentHorizontalInsetDp: Float = TV_CATEGORY_PARENT_HORIZONTAL_INSET_DP,
): CategorySidebarUnderlapPolicy {
    val safeBasePadding = baseContentPaddingDp.coerceAtLeast(0f)
    val viewportExtra = if (isTv) {
        railExpandedWidthDp.coerceAtLeast(0f) + parentHorizontalInsetDp.coerceAtLeast(0f)
    } else {
        0f
    }
    return CategorySidebarUnderlapPolicy(
        viewportExtraDp = viewportExtra,
        startContentPaddingDp = safeBasePadding + viewportExtra,
    )
}

@Composable
internal fun rememberCategorySidebarUnderlap(
    isTv: Boolean,
    baseContentPadding: Dp,
): CategorySidebarUnderlapPolicy {
    val adaptiveUi = LocalAdaptiveUi.current
    return remember(
        isTv,
        baseContentPadding,
        adaptiveUi.screenWidthDp,
        adaptiveUi.screenHeightDp,
    ) {
        categorySidebarUnderlapPolicy(
            isTv = isTv,
            railExpandedWidthDp = tvRailMetrics(
                screenWidthDp = adaptiveUi.screenWidthDp,
                screenHeightDp = adaptiveUi.screenHeightDp,
            ).expandedWidthDp,
            baseContentPaddingDp = baseContentPadding.value,
        )
    }
}

/**
 * Measures the LazyRow through the sidebar-side inset while reporting its original width upstream.
 * The rail remains responsible for visually occluding chips that scroll into this extra viewport.
 */
internal fun Modifier.extendCategoryViewportTowardStart(extraWidth: Dp): Modifier {
    if (extraWidth <= 0.dp) return this
    return layout { measurable, constraints ->
        val extraWidthPx = extraWidth.roundToPx().coerceAtLeast(0)
        if (extraWidthPx == 0 || !constraints.hasBoundedWidth) {
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
            }
        } else {
            val placeable = measurable.measure(
                constraints.copy(
                    minWidth = constraints.minWidth + extraWidthPx,
                    maxWidth = constraints.maxWidth + extraWidthPx,
                ),
            )
            val reportedWidth = (placeable.width - extraWidthPx)
                .coerceIn(constraints.minWidth, constraints.maxWidth)
            layout(reportedWidth, placeable.height) {
                placeable.placeRelative(-extraWidthPx, 0)
            }
        }
    }
}

private data class CategoryContentFocusRequest(
    val categoryId: String?,
    val requestId: Long,
    val focusFirstItem: Boolean,
)

internal data class CategoryFocusTarget(
    val categoryId: String?,
    val index: Int,
    val requester: FocusRequester,
)

internal data class CategoryFocusRestoreRequest(
    val categoryId: String?,
    val requestId: Long,
    val scrollCompleted: Boolean = false,
    val targetPlaced: Boolean = false,
)

internal class CategoryFocusRestoreController {
    var job: Job? = null
    var resolveTarget: (() -> CategoryFocusTarget?)? = null
    var restore: ((() -> Unit) -> Boolean)? = null
    var pendingRequest by mutableStateOf<CategoryFocusRestoreRequest?>(null)
        private set

    private var nextRequestId = 0L
    private val placedTargets = mutableSetOf<String?>()
    private var suppressBringIntoViewFor: Pair<Long, String?>? = null
    private var focusDispatchActive = false
    private var focusDispatchCategoryId: String? = null

    fun requestFromSource(): Boolean = restore?.invoke({}) == true

    fun hasPendingTarget(categoryId: String?): Boolean =
        pendingRequest?.let { it.categoryId == categoryId } == true

    fun begin(categoryId: String?): CategoryFocusRestoreRequest {
        nextRequestId += 1L
        return CategoryFocusRestoreRequest(
            categoryId = categoryId,
            requestId = nextRequestId,
            targetPlaced = categoryId in placedTargets,
        ).also { pendingRequest = it }
    }

    fun markTargetPlaced(categoryId: String?) {
        placedTargets += categoryId
        val request = pendingRequest ?: return
        if (request.categoryId == categoryId && !request.targetPlaced) {
            pendingRequest = request.copy(targetPlaced = true)
        }
    }

    fun markTargetDetached(categoryId: String?) {
        placedTargets -= categoryId
        val request = pendingRequest ?: return
        if (request.categoryId == categoryId && request.targetPlaced) {
            pendingRequest = request.copy(targetPlaced = false)
        }
    }

    fun markScrollCompleted(requestId: Long) {
        val request = pendingRequest
        if (request?.requestId == requestId && !request.scrollCompleted) {
            pendingRequest = request.copy(scrollCompleted = true)
        }
    }

    fun readyRequestId(categoryId: String?): Long? = pendingRequest
        ?.takeIf { it.categoryId == categoryId && it.scrollCompleted && it.targetPlaced }
        ?.requestId

    fun isDispatchingFocusTo(categoryId: String?): Boolean =
        focusDispatchActive && focusDispatchCategoryId == categoryId

    fun beginFocusDispatch(categoryId: String?) {
        focusDispatchActive = true
        focusDispatchCategoryId = categoryId
    }

    fun endFocusDispatch() {
        focusDispatchActive = false
        focusDispatchCategoryId = null
    }

    fun armBringIntoViewSuppression(requestId: Long, categoryId: String?) {
        suppressBringIntoViewFor = requestId to categoryId
    }

    fun consumeBringIntoViewSuppression(categoryId: String?): Boolean {
        val armed = suppressBringIntoViewFor ?: return false
        if (armed.second != categoryId) return false
        suppressBringIntoViewFor = null
        return true
    }

    fun clearBringIntoViewSuppression(requestId: Long) {
        if (suppressBringIntoViewFor?.first == requestId) suppressBringIntoViewFor = null
    }

    fun complete(requestId: Long) {
        if (pendingRequest?.requestId == requestId) pendingRequest = null
    }

    fun cancel() {
        job?.cancel()
        job = null
        pendingRequest = null
        suppressBringIntoViewFor = null
        endFocusDispatch()
    }
}

internal fun canCategoryChipReceiveFocus(
    isTv: Boolean,
    categoryBarHasFocus: Boolean,
    restorePending: Boolean,
    selectedId: String?,
    chipId: String?,
): Boolean = !isTv || selectedId == chipId || (categoryBarHasFocus && !restorePending)

@Composable
internal fun Modifier.categoryFocusTarget(
    isTv: Boolean,
    categoryId: String?,
    requester: FocusRequester,
    controller: CategoryFocusRestoreController,
): Modifier {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val readyRequestId = controller.readyRequestId(categoryId)

    DisposableEffect(controller, categoryId) {
        onDispose { controller.markTargetDetached(categoryId) }
    }
    LaunchedEffect(isTv, categoryId, requester, readyRequestId) {
        if (!isTv || readyRequestId == null) return@LaunchedEffect
        val target = controller.resolveTarget?.invoke() ?: return@LaunchedEffect
        if (target.categoryId != categoryId || target.requester !== requester) return@LaunchedEffect

        controller.armBringIntoViewSuppression(readyRequestId, categoryId)
        controller.beginFocusDispatch(categoryId)
        val focused = try {
            runCatching { requester.requestFocus() }.getOrDefault(false)
        } finally {
            controller.endFocusDispatch()
        }
        if (!focused) controller.clearBringIntoViewSuppression(readyRequestId)
        controller.complete(readyRequestId)
    }

    return focusRequester(requester).then(
        if (isTv) {
            Modifier
                .bringIntoViewRequester(bringIntoViewRequester)
                .onGloballyPositioned { controller.markTargetPlaced(categoryId) }
                .onFocusChanged { focusState ->
                    if (focusState.isFocused && !controller.consumeBringIntoViewSuppression(categoryId)) {
                        scope.launch { bringIntoViewRequester.bringIntoView() }
                    }
                }
        } else {
            Modifier
        },
    )
}

@Composable
private fun Modifier.categoryChipFocus(
    isTv: Boolean,
    categoryId: String?,
    selectedId: String?,
    categoryBarHasFocus: Boolean,
    requester: FocusRequester,
    controller: CategoryFocusRestoreController,
    allowInitialEntry: Boolean = false,
): Modifier = categoryFocusTarget(isTv, categoryId, requester, controller)
    .focusProperties {
        canFocus = allowInitialEntry || canCategoryChipReceiveFocus(
            isTv = isTv,
            categoryBarHasFocus = categoryBarHasFocus,
            restorePending = controller.pendingRequest != null,
            selectedId = selectedId,
            chipId = categoryId,
        )
    }

internal fun restoreSelectedCategoryFocus(
    listState: LazyListState,
    scope: CoroutineScope,
    controller: CategoryFocusRestoreController,
    cancelDefaultEntry: () -> Unit,
): Boolean {
    fun resolveTarget(): CategoryFocusTarget? = controller.resolveTarget?.invoke()
    fun isVisible(index: Int): Boolean =
        listState.layoutInfo.visibleItemsInfo.any { it.index == index }

    val directTarget = resolveTarget() ?: return false
    if (controller.isDispatchingFocusTo(directTarget.categoryId)) return true
    if (controller.hasPendingTarget(directTarget.categoryId)) {
        cancelDefaultEntry()
        return true
    }

    controller.cancel()
    if (isVisible(directTarget.index)) {
        controller.beginFocusDispatch(directTarget.categoryId)
        val focused = try {
            runCatching { directTarget.requester.requestFocus() }.getOrDefault(false)
        } finally {
            controller.endFocusDispatch()
        }
        if (focused) return true
    }

    val request = controller.begin(directTarget.categoryId)
    cancelDefaultEntry()
    if (isVisible(directTarget.index)) {
        controller.markScrollCompleted(request.requestId)
        return true
    }

    controller.job = scope.launch {
        val target = resolveTarget()
        if (target == null || target.categoryId != request.categoryId) {
            controller.complete(request.requestId)
            return@launch
        }

        listState.scrollToItem(target.index)
        val resolvedAfterScroll = resolveTarget()
        if (resolvedAfterScroll != null && resolvedAfterScroll.categoryId == request.categoryId) {
            controller.markScrollCompleted(request.requestId)
        } else {
            controller.complete(request.requestId)
        }
    }
    return true
}

private fun launchGrowthUrl(context: android.content.Context, url: String): Boolean = runCatching {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
    true
}.getOrDefault(false)

internal data class TvPageSafeInsets(
    val horizontalDp: Float,
    val verticalDp: Float,
)

internal fun tvPageSafeInsets(
    screenWidthDp: Int,
    screenHeightDp: Int,
): TvPageSafeInsets {
    val width = screenWidthDp.coerceAtLeast(1).toFloat()
    val height = screenHeightDp.coerceAtLeast(1).toFloat()
    val widthPressure = ((1280f - width) / 320f).coerceIn(0f, 1f)
    val heightPressure = ((720f - height) / 180f).coerceIn(0f, 1f)
    val compactPressure = maxOf(widthPressure, heightPressure)

    return TvPageSafeInsets(
        horizontalDp = 8f + (10f * compactPressure),
        verticalDp = 8f + (8f * compactPressure),
    )
}

private fun AndroidKeyEvent.isRemoteSelectKeyCode(): Boolean =
    keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_SPACE

@Composable
private fun Modifier.adaptiveTvPageSafePadding(
    isTv: Boolean,
    mobileHorizontal: Dp,
    mobileVertical: Dp = mobileHorizontal,
): Modifier {
    val adaptiveUi = LocalAdaptiveUi.current
    val safeInsets = tvPageSafeInsets(
        screenWidthDp = adaptiveUi.screenWidthDp,
        screenHeightDp = adaptiveUi.screenHeightDp,
    )
    return padding(
        horizontal = if (isTv) safeInsets.horizontalDp.dp else mobileHorizontal,
        vertical = if (isTv) safeInsets.verticalDp.dp else mobileVertical,
    )
}

internal fun tvRailLogoSizeDp(screenWidthDp: Int): Float =
    (screenWidthDp.coerceAtLeast(1) / 32f).coerceIn(28f, 60f)

data class NavigationPosition(
    val rowKey: String = "",
    val rowIndex: Int = 0,
    val itemKey: String = "",
    val itemIndex: Int = 0,
)

private data class TvContentFocusHandoffRequest(
    val destination: MainDestination,
    val requestId: Long,
)

internal data class HomeContentSnapshot(
    val movies: List<ContentItem>,
    val series: List<ContentItem>,
    val live: List<ContentItem>,
    val continueWatching: List<HistoryEntry>,
    val lastLive: HistoryEntry?,
    val becauseYouWatched: List<ContentItem>,
    val suggested: List<ContentItem>,
    val personalizedLive: List<ContentItem>,
    val popularMovies: List<ContentItem>,
    val popularSeries: List<ContentItem>,
    val featuredCandidates: List<ContentItem>,
)

/** Stable LazyColumn content-row order used by Continue Watching's post-removal handoff. */
private val HOME_CONTENT_ROW_ORDER = listOf(
    "continue",
    "downloads",
    "because-watched",
    "recommended",
    "recent-movies",
    "recent-series",
    "top-movies",
    "top-series",
    "last-live",
    "popular-live",
)

internal fun homeHeroIdentity(item: ContentItem): String = "${item.type}:${item.id}"

private fun HomeContentSnapshot.hasHomeHeroSource(): Boolean =
    featuredCandidates.isNotEmpty() || movies.isNotEmpty() || series.isNotEmpty()

internal fun resolvePresentedHomeHero(
    currentHeroIdentity: String?,
    featuredCandidates: List<ContentItem>,
    movies: List<ContentItem>,
    series: List<ContentItem>,
): ContentItem? {
    if (currentHeroIdentity != null) {
        featuredCandidates.firstOrNull { homeHeroIdentity(it) == currentHeroIdentity }?.let { return it }
        movies.firstOrNull { homeHeroIdentity(it) == currentHeroIdentity }?.let { return it }
        series.firstOrNull { homeHeroIdentity(it) == currentHeroIdentity }?.let { return it }
    }
    return featuredCandidates.firstOrNull()
        ?: movies.firstOrNull()
        ?: series.firstOrNull()
}

internal fun nextHomeHeroIdentity(
    currentHeroIdentity: String?,
    featuredCandidates: List<ContentItem>,
): String? {
    if (featuredCandidates.isEmpty()) return currentHeroIdentity
    val currentIndex = featuredCandidates.indexOfFirst {
        homeHeroIdentity(it) == currentHeroIdentity
    }
    if (currentIndex < 0) return homeHeroIdentity(featuredCandidates.first())
    if (featuredCandidates.size == 1) return currentHeroIdentity
    return homeHeroIdentity(featuredCandidates[(currentIndex + 1) % featuredCandidates.size])
}

class NavigationMemoryStore {
    private val positions = mutableMapOf<MainDestination, NavigationPosition>()
    private val screenEntryModels = CatalogScreenEntryModelStore()

    fun position(destination: MainDestination): NavigationPosition =
        positions[destination] ?: NavigationPosition()

    fun save(
        destination: MainDestination,
        itemKey: String,
        itemIndex: Int,
        rowKey: String = "",
        rowIndex: Int = 0,
    ) {
        positions[destination] = NavigationPosition(rowKey, rowIndex, itemKey, itemIndex)
    }

    internal fun cachedCatalogModel(input: CatalogScreenModelInput): KeyedCatalogScreenModel? =
        screenEntryModels.cachedCatalog(input)

    internal fun lastGoodCatalogModel(destination: MainDestination): KeyedCatalogScreenModel? =
        screenEntryModels.lastGoodCatalog(destination)

    internal suspend fun catalogModel(input: CatalogScreenModelInput): KeyedCatalogScreenModel =
        screenEntryModels.catalog(input)

    internal fun cachedHomeModel(input: HomeContentModelInput): KeyedHomeContentModel? =
        screenEntryModels.cachedHome(input)

    internal fun lastGoodHomeModel(): KeyedHomeContentModel? =
        screenEntryModels.lastGoodHome()

    internal suspend fun homeModel(input: HomeContentModelInput): KeyedHomeContentModel =
        screenEntryModels.home(input)
}

@Composable
private fun rememberHomeModelForPresentation(
    navigationMemory: NavigationMemoryStore,
    input: HomeContentModelInput,
): KeyedHomeContentModel? {
    var presented by remember(navigationMemory) {
        mutableStateOf(
            navigationMemory.cachedHomeModel(input)
                ?: navigationMemory.lastGoodHomeModel(),
        )
    }
    var acceptedInput by remember(navigationMemory) { mutableStateOf(input) }
    val presentationInput = stableHomePresentationInput(acceptedInput, input)
    LaunchedEffect(input) {
        if (!homePresentationSourcesEqual(acceptedInput, input)) {
            acceptedInput = input
        }
    }
    LaunchedEffect(navigationMemory, presentationInput) {
        navigationMemory.cachedHomeModel(presentationInput)?.let { exact ->
            presented = exact
            return@LaunchedEffect
        }
        if (presented?.model?.hasHomeHeroSource() != true) {
            navigationMemory.lastGoodHomeModel()?.let { lastGood ->
                presented = lastGood
            }
        }
        val exact = navigationMemory.homeModel(presentationInput)
        if (exact.input == presentationInput) presented = exact
    }
    return presented
}

@Composable
private fun rememberCatalogModelForPresentation(
    navigationMemory: NavigationMemoryStore,
    input: CatalogScreenModelInput,
): KeyedCatalogScreenModel? {
    var presented by remember(navigationMemory, input.destination) {
        mutableStateOf(
            navigationMemory.cachedCatalogModel(input)
                ?: navigationMemory.lastGoodCatalogModel(input.destination),
        )
    }
    LaunchedEffect(navigationMemory, input) {
        navigationMemory.cachedCatalogModel(input)?.let { exact ->
            presented = exact
            return@LaunchedEffect
        }
        navigationMemory.lastGoodCatalogModel(input.destination)?.let { lastGood ->
            presented = lastGood
        }
        val exact = navigationMemory.catalogModel(input)
        if (exact.input == input) presented = exact
    }
    return presented
}

private fun Modifier.restoreFocus(enabled: Boolean, requester: FocusRequester): Modifier =
    then(if (enabled) Modifier.focusRequester(requester) else Modifier)

private class DownloadFocusHandle(val requester: FocusRequester = FocusRequester()) {
    var isPlaced: Boolean = false
        private set

    fun onPlaced() {
        isPlaced = true
    }

    fun onDisposed() {
        isPlaced = false
    }
}

private data class DownloadToolbarFocusRequesters(
    val wifi: FocusRequester = FocusRequester(),
    val schedule: FocusRequester = FocusRequester(),
    val concurrent: FocusRequester = FocusRequester(),
)

private data class DownloadToolbarFocusHandles(
    val wifi: DownloadFocusHandle,
    val schedule: DownloadFocusHandle,
    val concurrent: DownloadFocusHandle,
)

private data class DownloadCardFocusRequesters(
    val primary: DownloadFocusHandle = DownloadFocusHandle(),
    val priority: DownloadFocusHandle = DownloadFocusHandle(),
    val cancel: DownloadFocusHandle = DownloadFocusHandle(),
)

private sealed interface DownloadTvFocusTarget {
    data class Toolbar(val slot: DownloadFocusSlot) : DownloadTvFocusTarget
    data class CardAction(val downloadId: Long, val slot: DownloadFocusSlot) : DownloadTvFocusTarget
}

private data class DownloadTvFocusGraph(
    val downloadIds: List<Long>,
    val columns: Int,
) {
    init {
        require(columns > 0)
    }

    val indexById: Map<Long, Int> = downloadIds.withIndex().associate { (index, id) -> id to index }
}

private class DownloadFocusMoveTransaction {
    var job: Job? = null

    val isActive: Boolean
        get() = job?.isActive == true
}

private class DownloadFocusHistory(var graph: DownloadTvFocusGraph)

private fun keyToDownloadFocusMove(key: Key): DownloadFocusMove? = when (key) {
    Key.DirectionLeft -> DownloadFocusMove.LEFT
    Key.DirectionRight -> DownloadFocusMove.RIGHT
    Key.DirectionUp -> DownloadFocusMove.UP
    Key.DirectionDown -> DownloadFocusMove.DOWN
    else -> null
}

private fun downloadCardTargetAt(
    graph: DownloadTvFocusGraph,
    index: Int,
    slot: DownloadFocusSlot,
): DownloadTvFocusTarget? = graph.downloadIds.getOrNull(index)?.let { downloadId ->
    DownloadTvFocusTarget.CardAction(downloadId, slot)
}

private fun downloadVerticalCardTarget(
    graph: DownloadTvFocusGraph,
    currentIndex: Int,
    rowDelta: Int,
    slot: DownloadFocusSlot,
): DownloadTvFocusTarget? {
    val targetRow = currentIndex / graph.columns + rowDelta
    if (targetRow < 0) return null
    val targetRowStart = targetRow * graph.columns
    if (targetRowStart >= graph.downloadIds.size) return null
    val targetRowEnd = minOf(targetRowStart + graph.columns, graph.downloadIds.size)
    val requestedColumn = currentIndex % graph.columns
    val targetIndex = minOf(targetRowStart + requestedColumn, targetRowEnd - 1)
    return downloadCardTargetAt(graph, targetIndex, slot)
}

private fun downloadToolbarTargetBelow(
    graph: DownloadTvFocusGraph,
    slot: DownloadFocusSlot,
): DownloadTvFocusTarget? {
    val firstRowCount = minOf(graph.columns, graph.downloadIds.size)
    if (firstRowCount == 0) return null
    val column = when (slot) {
        DownloadFocusSlot.WIFI -> 0
        DownloadFocusSlot.SCHEDULE -> (firstRowCount - 1) / 2
        DownloadFocusSlot.CONCURRENT -> firstRowCount - 1
        else -> return null
    }
    val actionSlot = when (slot) {
        DownloadFocusSlot.WIFI -> DownloadFocusSlot.PRIMARY
        DownloadFocusSlot.SCHEDULE -> DownloadFocusSlot.PRIORITY
        DownloadFocusSlot.CONCURRENT -> DownloadFocusSlot.CANCEL
        else -> return null
    }
    return downloadCardTargetAt(graph, column, actionSlot)
}

private fun downloadToolbarTargetAbove(
    graph: DownloadTvFocusGraph,
    cardIndex: Int,
    cardSlot: DownloadFocusSlot,
): DownloadTvFocusTarget {
    val column = cardIndex % graph.columns
    val actionOffset = when (cardSlot) {
        DownloadFocusSlot.PRIMARY -> .17f
        DownloadFocusSlot.PRIORITY -> .50f
        DownloadFocusSlot.CANCEL -> .83f
        else -> .50f
    }
    val horizontalPositionFromRight = (column + actionOffset) / graph.columns
    val toolbarSlot = when {
        horizontalPositionFromRight < 1f / 3f -> DownloadFocusSlot.WIFI
        horizontalPositionFromRight < 2f / 3f -> DownloadFocusSlot.SCHEDULE
        else -> DownloadFocusSlot.CONCURRENT
    }
    return DownloadTvFocusTarget.Toolbar(toolbarSlot)
}

private fun nextDownloadTvFocus(
    graph: DownloadTvFocusGraph,
    current: DownloadTvFocusTarget,
    move: DownloadFocusMove,
): DownloadTvFocusTarget? {
    return when (current) {
        is DownloadTvFocusTarget.Toolbar -> when (move) {
            DownloadFocusMove.LEFT -> when (current.slot) {
                DownloadFocusSlot.WIFI -> DownloadTvFocusTarget.Toolbar(DownloadFocusSlot.SCHEDULE)
                DownloadFocusSlot.SCHEDULE -> DownloadTvFocusTarget.Toolbar(DownloadFocusSlot.CONCURRENT)
                else -> null
            }
            DownloadFocusMove.RIGHT -> when (current.slot) {
                DownloadFocusSlot.CONCURRENT -> DownloadTvFocusTarget.Toolbar(DownloadFocusSlot.SCHEDULE)
                DownloadFocusSlot.SCHEDULE -> DownloadTvFocusTarget.Toolbar(DownloadFocusSlot.WIFI)
                else -> null
            }
            DownloadFocusMove.DOWN -> downloadToolbarTargetBelow(graph, current.slot)
            DownloadFocusMove.UP -> null
        }
        is DownloadTvFocusTarget.CardAction -> {
            val index = graph.indexById[current.downloadId] ?: return null
            // Grid indices increase leftward in RTL; remote arrow meaning stays physical.
            val column = index % graph.columns
            val row = index / graph.columns
            when (move) {
                DownloadFocusMove.LEFT -> when (current.slot) {
                    DownloadFocusSlot.PRIMARY -> current.copy(slot = DownloadFocusSlot.PRIORITY)
                    DownloadFocusSlot.PRIORITY -> current.copy(slot = DownloadFocusSlot.CANCEL)
                    DownloadFocusSlot.CANCEL -> {
                        val targetIndex = index + 1
                        if (column + 1 < graph.columns && targetIndex / graph.columns == row) {
                            downloadCardTargetAt(graph, targetIndex, DownloadFocusSlot.PRIMARY)
                        } else {
                            null
                        }
                    }
                    else -> null
                }
                DownloadFocusMove.RIGHT -> when (current.slot) {
                    DownloadFocusSlot.CANCEL -> current.copy(slot = DownloadFocusSlot.PRIORITY)
                    DownloadFocusSlot.PRIORITY -> current.copy(slot = DownloadFocusSlot.PRIMARY)
                    DownloadFocusSlot.PRIMARY -> {
                        if (column > 0) {
                            downloadCardTargetAt(graph, index - 1, DownloadFocusSlot.CANCEL)
                        } else {
                            null
                        }
                    }
                    else -> null
                }
                DownloadFocusMove.UP -> {
                    if (row > 0) {
                        downloadVerticalCardTarget(graph, index, -1, current.slot)
                    } else {
                        downloadToolbarTargetAbove(graph, index, current.slot)
                    }
                }
                DownloadFocusMove.DOWN -> downloadVerticalCardTarget(
                    graph = graph,
                    currentIndex = index,
                    rowDelta = 1,
                    slot = current.slot,
                )
            }
        }
    }
}

private fun downloadFocusFallback(
    current: DownloadTvFocusTarget,
    previousGraph: DownloadTvFocusGraph,
    currentGraph: DownloadTvFocusGraph,
): DownloadTvFocusTarget = when (current) {
    is DownloadTvFocusTarget.Toolbar -> current
    is DownloadTvFocusTarget.CardAction -> {
        if (current.downloadId in currentGraph.indexById) {
            current
        } else {
            val previousIndex = previousGraph.indexById[current.downloadId] ?: 0
            val replacementId = currentGraph.downloadIds.getOrNull(
                previousIndex.coerceIn(0, currentGraph.downloadIds.lastIndex.coerceAtLeast(0)),
            )
            if (replacementId != null) {
                DownloadTvFocusTarget.CardAction(replacementId, current.slot)
            } else {
                DownloadTvFocusTarget.Toolbar(
                    when (current.slot) {
                        DownloadFocusSlot.PRIMARY -> DownloadFocusSlot.WIFI
                        DownloadFocusSlot.PRIORITY -> DownloadFocusSlot.SCHEDULE
                        DownloadFocusSlot.CANCEL -> DownloadFocusSlot.CONCURRENT
                        else -> DownloadFocusSlot.WIFI
                    },
                )
            }
        }
    }
}

@Composable
private fun TrackDownloadFocusHandle(handle: DownloadFocusHandle, isTv: Boolean) {
    if (isTv) {
        DisposableEffect(handle) {
            onDispose { handle.onDisposed() }
        }
    }
}

private fun Modifier.applyDownloadTvFocusNode(
    isTv: Boolean,
    handle: DownloadFocusHandle,
    attachRequester: Boolean = true,
    onDirection: (DownloadFocusMove) -> Boolean,
): Modifier = if (!isTv) {
    this
} else {
    then(if (attachRequester) Modifier.focusRequester(handle.requester) else Modifier)
        .focusProperties {
            up = FocusRequester.Cancel
            down = FocusRequester.Cancel
            left = FocusRequester.Cancel
            right = FocusRequester.Cancel
        }
        .onPreviewKeyEvent { event ->
            val move = keyToDownloadFocusMove(event.key) ?: return@onPreviewKeyEvent false
            event.type == KeyEventType.KeyDown && onDirection(move)
        }
        .onGloballyPositioned { handle.onPlaced() }
}

@Composable
fun MainShellScreen(
    state: HulkUiState,
    isTv: Boolean,
    navigationMemory: NavigationMemoryStore,
    isFavorite: (ContentItem) -> Boolean,
    onSelectDestination: (MainDestination) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onOpenHistory: (HistoryEntry) -> Unit,
    onRemoveHistoryEntry: (String, (Boolean) -> Unit) -> Unit,
    onToggleFavoriteWithResult: (ContentItem, (FavoriteMutationOutcome) -> Unit) -> Unit,
    onRefresh: () -> Unit,
    onOpenNotifications: () -> Unit,
    onClearHistory: () -> Unit,
    onPlayDownload: (OfflineDownload) -> Unit,
    onDeleteDownload: (OfflineDownload) -> Unit,
    onDownloadPrimaryAction: (OfflineDownload, DownloadActionIntent) -> Unit,
    onToggleWifiOnly: () -> Unit,
    onToggleDownloadSchedule: () -> Unit,
    onCycleConcurrentDownloads: () -> Unit,
    onToggleEpisodeNotificationMaster: () -> Unit,
    onCycleDownloadPriority: (OfflineDownload) -> Unit,
    onRefreshAccount: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onLogout: () -> Unit,
    homeMessagePresentation: HomeMessagePresentationState,
    onOpenAnnouncementDetail: (OperationsAnnouncement) -> Unit,
    onOpenUpdateDetail: () -> Unit,
    onActionNotice: (String, ImageVector) -> Unit,
    mobileHomeFocusRequester: FocusRequester? = null,
) {
    val colors = LocalHulkColors.current
    val context = LocalContext.current
    val adaptiveUi = LocalAdaptiveUi.current
    val requestProfileSwitch = sa.hulksa.player.ui.LocalProfileSwitchRequester.current
    val useNavigationRail = adaptiveUi.navigationType == HulkNavigationType.RAIL
    val tvContentFocusRequesters = remember {
        destinations.associate { entry -> entry.destination to FocusRequester() }
    }
    val tvCatalogAllFocusRequesters = remember {
        mapOf(
            MainDestination.LIVE to FocusRequester(),
            MainDestination.MOVIES to FocusRequester(),
            MainDestination.SERIES to FocusRequester(),
        )
    }
    // Shell-owned entry target for Settings: SettingsProScreen attaches it to the selected section
    // row only, so the first focus request never lands on an unrelated detail action.
    val tvSettingsEntryFocusRequester = remember { FocusRequester() }
    var tvContentFocusRequestId by remember { mutableLongStateOf(0L) }
    var pendingTvContentFocusHandoff by remember {
        mutableStateOf<TvContentFocusHandoffRequest?>(null)
    }
    val rememberedPosterContentOwnsTvHandoff: (MainDestination) -> Boolean = { destination ->
        val type = when (destination) {
            MainDestination.MOVIES -> ContentType.MOVIE
            MainDestination.SERIES -> ContentType.SERIES
            else -> null
        }
        val rememberedKey = navigationMemory.position(destination).itemKey
        type != null &&
            state.searchQuery.isBlank() &&
            rememberedKey.isNotBlank() &&
            state.catalogs[type]?.items?.any { "${it.type}:${it.id}" == rememberedKey } == true
    }
    val selectTvDestination: (MainDestination) -> Unit = { destination ->
        if (destination != state.destination) {
            onSelectDestination(destination)
        }
        val contentRestoreOwnsTvHandoff =
            destination != state.destination && rememberedPosterContentOwnsTvHandoff(destination)
        tvContentFocusRequestId += 1L
        pendingTvContentFocusHandoff = if (contentRestoreOwnsTvHandoff) {
            null
        } else {
            TvContentFocusHandoffRequest(
                destination = destination,
                requestId = tvContentFocusRequestId,
            )
        }
    }
    val homeModelInput = if (state.destination == MainDestination.HOME) {
        HomeContentModelInput(
            movieCatalog = state.catalogs[ContentType.MOVIE],
            seriesCatalog = state.catalogs[ContentType.SERIES],
            liveCatalog = state.catalogs[ContentType.LIVE],
            history = state.history,
            favorites = state.favorites,
        )
    } else {
        null
    }
    val homeModel = homeModelInput?.let { input ->
        rememberHomeModelForPresentation(
            navigationMemory = navigationMemory,
            input = input,
        )
    }
    if (state.destination == MainDestination.HOME && homeModel == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(colors.background),
            contentAlignment = Alignment.Center,
        ) {
            LoadingRing()
        }
        return
    }
    val homeContent = homeModel?.model
    val homeModelSettled = homeModel?.isFirstFrameFallback != true
    val downloadsEnabled = state.operations.features.downloadsEnabled
    val navigationEntries = remember(downloadsEnabled) {
        destinations.filterNot { entry ->
            !downloadsEnabled && entry.destination == MainDestination.DOWNLOADS
        }
    }
    val favoriteSnapshot = CatalogFavoriteSnapshot(persisted = state.favorites)
    val resolvedIsFavorite: (ContentItem) -> Boolean = isFavorite
    var growthQrDestination by remember { mutableStateOf<GrowthDestination?>(null) }
    // The shell-level scoped action notice is owned above this screen (single notice owner);
    // real Home action results are posted to it instead of the previous unstyled Toasts.
    val openGrowthDestination: (GrowthDestination) -> Unit = { destination ->
        when (resolveGrowthAction(state.operations.growth, destination, isTv)) {
            GrowthAction.OPEN_QR -> growthQrDestination = destination
            GrowthAction.OPEN_URL -> {
                val url = state.operations.growth.link(destination).url.orEmpty()
                if (!launchGrowthUrl(context, url)) {
                    onActionNotice("تعذر فتح الرابط على هذا الجهاز", Icons.Outlined.ErrorOutline)
                }
            }
            GrowthAction.NO_ACTION -> Unit
        }
    }
    LaunchedEffect(growthQrDestination, state.operations.growth, isTv) {
        val destination = growthQrDestination ?: return@LaunchedEffect
        if (resolveGrowthAction(state.operations.growth, destination, isTv) != GrowthAction.OPEN_QR) {
            growthQrDestination = null
        }
    }
    val toggleFavoriteWithFeedbackAndResult:
        (ContentItem, (FavoriteMutationOutcome) -> Unit) -> Unit = { pressedItem, onResult ->
            onToggleFavoriteWithResult(pressedItem) { outcome ->
                onResult(outcome)
                favoriteMutationFeedback(outcome, pressedItem.name)?.let { feedback ->
                    onActionNotice(
                        feedback.message,
                        favoriteFeedbackIcon(feedback.glyph),
                    )
                }
            }
        }
    val toggleFavoriteWithFeedback: (ContentItem) -> Unit = { pressedItem ->
        toggleFavoriteWithFeedbackAndResult(pressedItem) { }
    }
    val tvRailFocusRequesters = remember(navigationEntries) {
        navigationEntries.associate { entry -> entry.destination to FocusRequester() }
    }
    val currentTvContentFocusRequester = tvContentFocusRequesters.getValue(state.destination)
    val currentTvDestinationFocusRequester = tvCatalogAllFocusRequesters[state.destination]
        ?: if (state.destination == MainDestination.SETTINGS) {
            tvSettingsEntryFocusRequester
        } else {
            currentTvContentFocusRequester
        }
    val homeRemovalInputGuard = remember { HomeRemovalInputGuard() }
    val favoritesRemovalInputGuard = remember { FavoritesRemovalInputGuard() }
    val currentTvCatalogInitialFocusPending =
        pendingTvContentFocusHandoff?.destination == state.destination &&
            state.destination in tvCatalogAllFocusRequesters
    LaunchedEffect(
        useNavigationRail,
        state.destination,
        pendingTvContentFocusHandoff?.requestId,
    ) {
        val handoff = pendingTvContentFocusHandoff ?: return@LaunchedEffect
        if (!useNavigationRail || handoff.destination != state.destination) {
            return@LaunchedEffect
        }

        // Wait for the selected destination's focus group to join the applied focus tree.
        withFrameNanos { }
        if (pendingTvContentFocusHandoff?.requestId != handoff.requestId) {
            return@LaunchedEffect
        }
        val handedOff = runCatching {
            currentTvDestinationFocusRequester.requestFocus()
        }.getOrDefault(false)
        if (handedOff && pendingTvContentFocusHandoff?.requestId == handoff.requestId) {
            pendingTvContentFocusHandoff = null
        }
    }
    TvRemovalInputBoundary(
        enabled = isTv,
        homeGuard = homeRemovalInputGuard,
        favoritesGuard = favoritesRemovalInputGuard,
    ) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
        if (useNavigationRail) {
            Row(Modifier.fillMaxSize()) {
                CinematicNavigationRail(
                    entries = navigationEntries,
                    selected = state.destination,
                    onSelect = selectTvDestination,
                    onSwitchProfile = requestProfileSwitch,
                    destinationFocusRequesters = tvRailFocusRequesters,
                )
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .focusRequester(currentTvContentFocusRequester)
                        .focusRestorer()
                        .focusGroup(),
                ) {
                    DestinationContent(
                        state = state,
                        isTv = isTv,
                        navigationMemory = navigationMemory,
                        homeContent = homeContent,
                        homeModelSettled = homeModelSettled,
                        favoriteSnapshot = favoriteSnapshot,
                        isFavorite = resolvedIsFavorite,
                        onSelectCategory = onSelectCategory,
                        onSearch = onSearch,
                        onOpen = onOpen,
                        onOpenHistory = onOpenHistory,
                        onRemoveHistoryEntry = onRemoveHistoryEntry,
                        onToggleFavorite = toggleFavoriteWithFeedback,
                        onToggleFavoriteWithResult = toggleFavoriteWithFeedbackAndResult,
                        onFavoritesRemovalStarted = favoritesRemovalInputGuard::begin,
                        onRefresh = onRefresh,
                        onOpenNotifications = onOpenNotifications,
                        onGrowthAction = openGrowthDestination,
                        onSelectDestination = onSelectDestination,
                        onClearHistory = onClearHistory,
                        onPlayDownload = onPlayDownload,
                        onDeleteDownload = onDeleteDownload,
                        onDownloadPrimaryAction = onDownloadPrimaryAction,
                        onToggleWifiOnly = onToggleWifiOnly,
                        onToggleDownloadSchedule = onToggleDownloadSchedule,
                        onCycleConcurrentDownloads = onCycleConcurrentDownloads,
                        onToggleEpisodeNotificationMaster = onToggleEpisodeNotificationMaster,
                        onCycleDownloadPriority = onCycleDownloadPriority,
                        onRefreshAccount = onRefreshAccount,
                        onRunDiagnostics = onRunDiagnostics,
                        onLogout = onLogout,
                        homeMessagePresentation = homeMessagePresentation,
                        onOpenAnnouncementDetail = onOpenAnnouncementDetail,
                        onOpenUpdateDetail = onOpenUpdateDetail,
                        onActionNotice = onActionNotice,
                        homeEntryFocusRequester = tvRailFocusRequesters[MainDestination.HOME],
                        homeRemovalInputGuard = homeRemovalInputGuard,
                        initialAllFocusRequester = tvCatalogAllFocusRequesters[state.destination],
                        initialAllFocusPending = currentTvCatalogInitialFocusPending,
                        downloadsExitFocusRequester = tvRailFocusRequesters[MainDestination.DOWNLOADS],
                        tvSettingsEntryFocusRequester = tvSettingsEntryFocusRequester,
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .weight(1f)
                        .then(
                            if (state.destination == MainDestination.HOME) {
                                Modifier
                            } else {
                                Modifier.statusBarsPadding().padding(top = MOBILE_SECTION_TOP_GAP)
                            },
                        ),
                ) {
                    DestinationContent(
                        state = state,
                        isTv = false,
                        navigationMemory = navigationMemory,
                        homeContent = homeContent,
                        homeModelSettled = homeModelSettled,
                        favoriteSnapshot = favoriteSnapshot,
                        isFavorite = resolvedIsFavorite,
                        onSelectCategory = onSelectCategory,
                        onSearch = onSearch,
                        onOpen = onOpen,
                        onOpenHistory = onOpenHistory,
                        onRemoveHistoryEntry = onRemoveHistoryEntry,
                        onToggleFavorite = toggleFavoriteWithFeedback,
                        onToggleFavoriteWithResult = toggleFavoriteWithFeedbackAndResult,
                        onFavoritesRemovalStarted = favoritesRemovalInputGuard::begin,
                        onRefresh = onRefresh,
                        onOpenNotifications = onOpenNotifications,
                        onGrowthAction = openGrowthDestination,
                        onSelectDestination = onSelectDestination,
                        onClearHistory = onClearHistory,
                        onPlayDownload = onPlayDownload,
                        onDeleteDownload = onDeleteDownload,
                        onDownloadPrimaryAction = onDownloadPrimaryAction,
                        onToggleWifiOnly = onToggleWifiOnly,
                        onToggleDownloadSchedule = onToggleDownloadSchedule,
                        onCycleConcurrentDownloads = onCycleConcurrentDownloads,
                        onToggleEpisodeNotificationMaster = onToggleEpisodeNotificationMaster,
                        onCycleDownloadPriority = onCycleDownloadPriority,
                        onRefreshAccount = onRefreshAccount,
                        onRunDiagnostics = onRunDiagnostics,
                        onLogout = onLogout,
                        homeMessagePresentation = homeMessagePresentation,
                        onOpenAnnouncementDetail = onOpenAnnouncementDetail,
                        onOpenUpdateDetail = onOpenUpdateDetail,
                        onActionNotice = onActionNotice,
                        homeEntryFocusRequester = mobileHomeFocusRequester,
                        homeRemovalInputGuard = homeRemovalInputGuard,
                    )
                }
                Spacer(
                    Modifier
                        .navigationBarsPadding()
                        .height(
                            stableMobileNavigationReservedHeight(
                                windowWidthDp = adaptiveUi.screenWidthDp,
                                downloadsEnabled = downloadsEnabled,
                            ),
                        ),
                )
            }
        }
        growthQrDestination?.let { destination ->
            val link = state.operations.growth.link(destination)
            if (resolveGrowthAction(state.operations.growth, destination, isTv) == GrowthAction.OPEN_QR) {
                GrowthQrDialog(
                    destination = destination,
                    link = link,
                    onDismiss = { growthQrDestination = null },
                )
            }
        }
        }
    }
}

/**
 * Shell-level owner for removal select gestures. Home and Favorites each keep their own guard,
 * but one boundary consumes the physical hold before replacement rows/cards/filters can receive it.
 */
@Composable
private fun TvRemovalInputBoundary(
    enabled: Boolean,
    homeGuard: HomeRemovalInputGuard,
    favoritesGuard: FavoritesRemovalInputGuard,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (!enabled || !event.nativeKeyEvent.isRemoteSelectKeyCode()) {
                    false
                } else {
                    val homeConsumed = homeGuard.onSelectKey(event.type)
                    val favoritesConsumed = favoritesGuard.onSelectKey(event.type)
                    homeConsumed || favoritesConsumed
                }
            },
    ) {
        content()
    }
}

/**
 * Scoped action feedback with its semantic glyph, shared by Home actions and download feedback.
 * The single host lives above the destination layer so every screen renders the same notice.
 */
internal data class HomeActionNotice(
    val message: String,
    val icon: ImageVector,
)

/** Compact dark/gold notice family used for real action feedback. */
@Composable
internal fun ScopedHomeActionNotice(
    notice: HomeActionNotice,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .widthIn(max = if (isTv) 560.dp else 340.dp)
            .clip(shape)
            .background(Color(0xF211120D))
            .border(1.dp, colors.gold.copy(alpha = .45f), shape)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = notice.message,
            color = colors.text,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = notice.icon,
            contentDescription = null,
            tint = colors.gold,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun CinematicNavigationRail(
    entries: List<DestinationEntry>,
    selected: MainDestination,
    onSelect: (MainDestination) -> Unit,
    onSwitchProfile: () -> Unit,
    destinationFocusRequesters: Map<MainDestination, FocusRequester>,
) {
    val adaptiveUi = LocalAdaptiveUi.current
    var railHasFocus by remember { mutableStateOf(false) }
    val expanded = railHasFocus
    val metrics = tvRailMetrics(
        screenWidthDp = adaptiveUi.screenWidthDp,
        screenHeightDp = adaptiveUi.screenHeightDp,
    )
    val primaryEntries = entries.filterNot { it.destination == MainDestination.SETTINGS }
    val profileRequester = remember { FocusRequester() }
    val settingsRequester = destinationFocusRequesters.getValue(MainDestination.SETTINGS)
    val selectedRequester = destinationFocusRequesters.getValue(selected)

    TvRailSurface(
        metrics = metrics,
        expanded = expanded,
        overlayExpansion = adaptiveUi.isTelevision,
        onRailFocusChanged = { railHasFocus = it },
        onRailEnter = { selectedRequester.requestFocus() },
    ) {
        BrandLogo(Modifier.size(metrics.logoSizeDp.dp))
        Spacer(Modifier.height(metrics.logoItemGapDp.dp))
        primaryEntries.forEachIndexed { index, entry ->
            val requester = destinationFocusRequesters.getValue(entry.destination)
            val previousRequester = primaryEntries.getOrNull(index - 1)
                ?.let { destinationFocusRequesters.getValue(it.destination) }
            val nextRequester = primaryEntries.getOrNull(index + 1)
                ?.let { destinationFocusRequesters.getValue(it.destination) }
                ?: profileRequester
            TvRailDestinationItem(
                icon = entry.icon,
                label = entry.label,
                selected = selected == entry.destination,
                expanded = expanded,
                metrics = metrics,
                onClick = { onSelect(entry.destination) },
                modifier = Modifier
                    .focusRequester(requester)
                    .focusProperties {
                        previousRequester?.let { up = it }
                        down = nextRequester
                    },
            )
            Spacer(Modifier.height(metrics.itemGapDp.dp))
        }
        TvRailDestinationItem(
            icon = Icons.Rounded.Person,
            label = "تغيير المستخدم",
            selected = false,
            expanded = expanded,
            metrics = metrics,
            onClick = onSwitchProfile,
            modifier = Modifier
                .focusRequester(profileRequester)
                .focusProperties {
                    primaryEntries.lastOrNull()?.let {
                        up = destinationFocusRequesters.getValue(it.destination)
                    }
                    down = settingsRequester
                },
        )
        Spacer(Modifier.height(metrics.itemGapDp.dp))
        Spacer(Modifier.weight(1f))
        entries.first { it.destination == MainDestination.SETTINGS }.let { entry ->
            TvRailDestinationItem(
                icon = entry.icon,
                label = entry.label,
                selected = selected == entry.destination,
                expanded = expanded,
                metrics = metrics,
                onClick = { onSelect(entry.destination) },
                modifier = Modifier
                    .focusRequester(settingsRequester)
                    .focusProperties { up = profileRequester },
            )
        }
        Spacer(Modifier.height((metrics.itemGapDp * 2f).dp))
    }
}

@Composable
private fun DestinationContent(
    state: HulkUiState,
    isTv: Boolean,
    navigationMemory: NavigationMemoryStore,
    homeContent: HomeContentSnapshot?,
    homeModelSettled: Boolean,
    favoriteSnapshot: CatalogFavoriteSnapshot,
    isFavorite: (ContentItem) -> Boolean,
    onSelectCategory: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onOpenHistory: (HistoryEntry) -> Unit,
    onRemoveHistoryEntry: (String, (Boolean) -> Unit) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onToggleFavoriteWithResult: (ContentItem, (FavoriteMutationOutcome) -> Unit) -> Unit,
    onFavoritesRemovalStarted: () -> Unit,
    onRefresh: () -> Unit,
    onOpenNotifications: () -> Unit,
    onGrowthAction: (GrowthDestination) -> Unit,
    onSelectDestination: (MainDestination) -> Unit,
    onClearHistory: () -> Unit,
    onPlayDownload: (OfflineDownload) -> Unit,
    onDeleteDownload: (OfflineDownload) -> Unit,
    onDownloadPrimaryAction: (OfflineDownload, DownloadActionIntent) -> Unit,
    onToggleWifiOnly: () -> Unit,
    onToggleDownloadSchedule: () -> Unit,
    onCycleConcurrentDownloads: () -> Unit,
    onToggleEpisodeNotificationMaster: () -> Unit,
    onCycleDownloadPriority: (OfflineDownload) -> Unit,
    onRefreshAccount: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onLogout: () -> Unit,
    homeMessagePresentation: HomeMessagePresentationState,
    onOpenAnnouncementDetail: (OperationsAnnouncement) -> Unit,
    onOpenUpdateDetail: () -> Unit,
    onActionNotice: (String, ImageVector) -> Unit,
    homeEntryFocusRequester: FocusRequester? = null,
    homeRemovalInputGuard: HomeRemovalInputGuard? = null,
    initialAllFocusRequester: FocusRequester? = null,
    initialAllFocusPending: Boolean = false,
    downloadsExitFocusRequester: FocusRequester? = null,
    tvSettingsEntryFocusRequester: FocusRequester? = null,
) {
    val ownedSettingsEntryFocusRequester = remember { FocusRequester() }
    val settingsEntryFocusRequester =
        tvSettingsEntryFocusRequester ?: ownedSettingsEntryFocusRequester
    when (state.destination) {
        MainDestination.HOME -> CinemaHomeScreen(
            state = state,
            isTv = isTv,
            navigationMemory = navigationMemory,
            homeContent = requireNotNull(homeContent),
            homeModelSettled = homeModelSettled,
            favorites = favoriteSnapshot,
            onOpen = onOpen,
            onOpenHistory = onOpenHistory,
            onRemoveHistoryEntry = onRemoveHistoryEntry,
            onToggleFavorite = onToggleFavorite,
            onRefresh = onRefresh,
            onOpenNotifications = onOpenNotifications,
            onGrowthAction = onGrowthAction,
            onOpenDownloads = { onSelectDestination(MainDestination.DOWNLOADS) },
            onSelectDestination = onSelectDestination,
            homeMessagePresentation = homeMessagePresentation,
            onOpenAnnouncementDetail = onOpenAnnouncementDetail,
            onOpenUpdateDetail = onOpenUpdateDetail,
            onActionNotice = onActionNotice,
            homeEntryFocusRequester = homeEntryFocusRequester,
            homeRemovalInputGuard = homeRemovalInputGuard,
        )
        MainDestination.LIVE -> LiveCatalogScreen(
            state = state,
            isTv = isTv,
            navigationMemory = navigationMemory,
            favorites = favoriteSnapshot,
            onSelectCategory = onSelectCategory,
            onSearch = onSearch,
            onOpen = onOpen,
            onToggleFavorite = onToggleFavorite,
            onToggleFavoriteWithResult = onToggleFavoriteWithResult,
            onFavoritesRemovalStarted = onFavoritesRemovalStarted,
            onRefresh = onRefresh,
            initialAllFocusRequester = initialAllFocusRequester,
            initialAllFocusPending = initialAllFocusPending,
        )
        MainDestination.MOVIES -> PosterCatalogScreen(
            title = "الافلام",
            type = ContentType.MOVIE,
            destination = MainDestination.MOVIES,
            state = state,
            isTv = isTv,
            navigationMemory = navigationMemory,
            favoriteSnapshot = favoriteSnapshot,
            isFavorite = isFavorite,
            onSelectCategory = onSelectCategory,
            onSearch = onSearch,
            onOpen = onOpen,
            onOpenHistory = onOpenHistory,
            onToggleFavorite = onToggleFavorite,
            onRefresh = onRefresh,
            initialAllFocusRequester = initialAllFocusRequester,
            initialAllFocusPending = initialAllFocusPending,
        )
        MainDestination.SERIES -> PosterCatalogScreen(
            title = "المسلسلات",
            type = ContentType.SERIES,
            destination = MainDestination.SERIES,
            state = state,
            isTv = isTv,
            navigationMemory = navigationMemory,
            favoriteSnapshot = favoriteSnapshot,
            isFavorite = isFavorite,
            onSelectCategory = onSelectCategory,
            onSearch = onSearch,
            onOpen = onOpen,
            onOpenHistory = onOpenHistory,
            onToggleFavorite = onToggleFavorite,
            onRefresh = onRefresh,
            initialAllFocusRequester = initialAllFocusRequester,
            initialAllFocusPending = initialAllFocusPending,
        )
        MainDestination.FAVORITES -> FavoritesScreen(
            state = state,
            isTv = isTv,
            navigationMemory = navigationMemory,
            isFavorite = isFavorite,
            onOpen = onOpen,
            onToggleFavorite = onToggleFavorite,
            onFavoritesRemovalStarted = onFavoritesRemovalStarted,
            onRefresh = onRefresh,
        )
        MainDestination.SEARCH -> UnifiedSearchScreen(state, isTv, navigationMemory, isFavorite, onSearch, onOpen, onToggleFavorite)
        MainDestination.DOWNLOADS -> DownloadsScreen(
            downloads = state.downloads,
            settings = state.downloadSettings,
            isTv = isTv,
            navigationMemory = navigationMemory,
            onPlay = onPlayDownload,
            onDelete = onDeleteDownload,
            onDownloadPrimaryAction = onDownloadPrimaryAction,
            onToggleWifiOnly = onToggleWifiOnly,
            onToggleSchedule = onToggleDownloadSchedule,
            onCycleConcurrent = onCycleConcurrentDownloads,
            onCyclePriority = onCycleDownloadPriority,
            exitFocusRequester = downloadsExitFocusRequester,
        )
        MainDestination.SETTINGS -> SettingsProScreen(
            state = state,
            isTv = isTv,
            onRefreshAccount = onRefreshAccount,
            onRefreshLibrary = {
                onSelectDestination(MainDestination.HOME)
                onRefresh()
            },
            onClearHistory = onClearHistory,
            onOpenDownloads = { onSelectDestination(MainDestination.DOWNLOADS) },
            downloadsEnabled = state.operations.features.downloadsEnabled,
            tvEntryFocusRequester = settingsEntryFocusRequester,
            onToggleWifiOnly = onToggleWifiOnly,
            onToggleDownloadSchedule = onToggleDownloadSchedule,
            onCycleConcurrentDownloads = onCycleConcurrentDownloads,
            notificationMasterEnabled = state.episodeNotificationsEnabled,
            episodeNotificationsAvailable = state.operations.features.episodeNotificationsEnabled,
            onToggleEpisodeNotificationMaster = onToggleEpisodeNotificationMaster,
            onGrowthAction = onGrowthAction,
            onLogout = onLogout,
            onNotice = onActionNotice,
        )
    }
}

@Composable
private fun CinemaHomeScreen(
    state: HulkUiState,
    isTv: Boolean,
    navigationMemory: NavigationMemoryStore,
    homeContent: HomeContentSnapshot,
    homeModelSettled: Boolean,
    favorites: CatalogFavoriteSnapshot,
    onOpen: (ContentItem) -> Unit,
    onOpenHistory: (HistoryEntry) -> Unit,
    onRemoveHistoryEntry: (String, (Boolean) -> Unit) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onRefresh: () -> Unit,
    onOpenNotifications: () -> Unit,
    onGrowthAction: (GrowthDestination) -> Unit,
    onOpenDownloads: () -> Unit,
    onSelectDestination: (MainDestination) -> Unit,
    homeMessagePresentation: HomeMessagePresentationState,
    onOpenAnnouncementDetail: (OperationsAnnouncement) -> Unit,
    onOpenUpdateDetail: () -> Unit,
    onActionNotice: (String, ImageVector) -> Unit,
    homeEntryFocusRequester: FocusRequester?,
    homeRemovalInputGuard: HomeRemovalInputGuard?,
) {
    val movies = homeContent.movies
    val series = homeContent.series
    val live = homeContent.live
    val continueWatching = homeContent.continueWatching
    val lastLive = homeContent.lastLive
    val smartRecommendationsEnabled = state.operations.features.smartRecommendationsEnabled
    val becauseYouWatched = if (smartRecommendationsEnabled) homeContent.becauseYouWatched else emptyList()
    val suggested = if (smartRecommendationsEnabled) homeContent.suggested else emptyList()
    val personalizedLive = homeContent.personalizedLive
    val suggestedLive = remember(personalizedLive, lastLive, smartRecommendationsEnabled) {
        if (!smartRecommendationsEnabled) return@remember emptyList()
        val lastLiveId = lastLive?.streamId
        personalizedLive
            .asSequence()
            .filterNot { lastLiveId != null && it.id == lastLiveId }
            .take(20)
            .toList()
    }
    val popularMovies = homeContent.popularMovies
    val popularSeries = homeContent.popularSeries
    val featuredCandidates = homeContent.featuredCandidates
    val activeDownloads = remember(state.downloads, state.operations.features.downloadsEnabled) {
        if (!state.operations.features.downloadsEnabled) return@remember emptyList()
        state.downloads.filter {
            it.status == OfflineStatus.DOWNLOADING || it.status == OfflineStatus.QUEUED ||
                it.status == OfflineStatus.CHECKING || it.status == OfflineStatus.PAUSED ||
                it.status == OfflineStatus.WAITING_NETWORK || it.status == OfflineStatus.WAITING_SCHEDULE ||
                it.status == OfflineStatus.WAITING_STORAGE
        }.take(4)
    }
    var pendingContinueRowFocus by remember { mutableStateOf<HomeEmptyRowFocusRequest?>(null) }
    var nextContinueRowFocusRequestId by remember { mutableLongStateOf(0L) }
    val homeRemovalSessionActive = remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        onDispose { homeRemovalSessionActive.value = false }
    }
    val removeHistoryRef = rememberUpdatedState(onRemoveHistoryEntry)
    val actionNoticeRef = rememberUpdatedState(onActionNotice)
    val homeRowFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val homeRowFocusTargets = remember { mutableMapOf<String, HomeRowFocusTarget>() }
    LaunchedEffect(navigationMemory) { pendingContinueRowFocus = null }
    val removalController = remember {
        HomeHistoryRemovalController(
            removeHistory = { key, result -> removeHistoryRef.value(key, result) },
            onSuccessfulRemoval = { wasLastCard ->
                if (wasLastCard) {
                    nextContinueRowFocusRequestId += 1L
                    pendingContinueRowFocus = HomeEmptyRowFocusRequest(nextContinueRowFocusRequestId)
                }
            },
            canPublishSuccess = { homeRemovalSessionActive.value },
            onSuccessNotice = {
                actionNoticeRef.value("تمت ازالة المشاهدة", Icons.Rounded.DeleteOutline)
            },
        )
    }
    fun rowFocusRequester(rowKey: String): FocusRequester =
        homeRowFocusRequesters.getOrPut(rowKey) { FocusRequester() }
    fun rowFocusTarget(rowKey: String): HomeRowFocusTarget =
        homeRowFocusTargets.getOrPut(rowKey) { HomeRowFocusTarget() }
    var featuredIdentity by remember(navigationMemory) { mutableStateOf<String?>(null) }
    val featured = remember(featuredIdentity, featuredCandidates, movies, series) {
        resolvePresentedHomeHero(
            currentHeroIdentity = featuredIdentity,
            featuredCandidates = featuredCandidates,
            movies = movies,
            series = series,
        )
    }
    val resolvedFeaturedIdentity = featured?.let(::homeHeroIdentity)
    LaunchedEffect(resolvedFeaturedIdentity) {
        if (resolvedFeaturedIdentity != featuredIdentity) {
            featuredIdentity = resolvedFeaturedIdentity
        }
    }
    LaunchedEffect(featuredCandidates) {
        while (featuredCandidates.size > 1) {
            delay(9_000L)
            featuredIdentity = nextHomeHeroIdentity(
                currentHeroIdentity = featuredIdentity,
                featuredCandidates = featuredCandidates,
            )
        }
    }
    val homeMovies = remember(movies, featured) {
        movies.asSequence()
            .filterNot { it.type == featured?.type && it.id == featured.id }
            .take(28)
            .toList()
    }
    val homeSeries = remember(series, featured) {
        series.asSequence()
            .filterNot { it.type == featured?.type && it.id == featured.id }
            .take(28)
            .toList()
    }
    val loading = ContentType.MOVIE in state.loadingTypes || ContentType.SERIES in state.loadingTypes
    // R02: ONE compact five-action group in the original Home header slot for both the hero and
    // the placeholder/loading/empty/error paths; the existing callbacks and owners are reused.
    val requestProfileSwitch = LocalProfileSwitchRequester.current
    val homeTopActions: @Composable () -> Unit = {
        MobileInlineTopActions(
            selected = state.destination,
            unreadNotificationCount = state.unreadNotificationCount,
            refreshing = loading,
            onOpenSearch = { onSelectDestination(MainDestination.SEARCH) },
            onOpenFavorites = { onSelectDestination(MainDestination.FAVORITES) },
            onSwitchProfile = requestProfileSwitch,
            onOpenNotifications = onOpenNotifications,
            onRefresh = onRefresh,
        )
    }
    // Truthful Home error classification reuses the accepted Movies connectivity owner: only a
    // confirmed offline state gets the WifiOff wording; other failures keep the real message.
    val homeNetworkUsable by rememberUsableNetworkState()
    val homeErrorCopy = state.errorMessage?.let { message ->
        moviesCatalogErrorCopy(offline = !homeNetworkUsable, serverMessage = message)
    }
    val remembered = navigationMemory.position(MainDestination.HOME)
    // One Home message zone owns the non-blocking renewal, announcement and optional-update
    // presentation. GrowthPolicy, the eligible Operations announcement and the current OPTIONAL
    // update decision remain the only policy owners; the zone only renders their real output.
    val renewalBanner = evaluateRenewalBanner(
        growth = state.operations.growth,
        expiresAtEpochSeconds = state.account?.expiresAtEpochSeconds,
    )
    val announcementMessage = state.operations.announcementPopup
    val optionalUpdate = state.operations.update.takeIf {
        state.operations.updateDecision == OperationsUpdateDecision.OPTIONAL
    }
    val hasHomeMessages = renewalBanner != null ||
        announcementMessage != null ||
        optionalUpdate != null
    HomeMessageOwnership(
        presentation = homeMessagePresentation,
        announcement = announcementMessage,
        optionalUpdate = optionalUpdate,
        persistentAnnouncement = state.operations.persistentAnnouncement,
    )

    var rowCursor = homeRowCursorStart(isTv)
    val messageRow = if (hasHomeMessages) rowCursor++ else -1
    if (state.errorMessage != null) rowCursor++
    val continueRow = if (continueWatching.isNotEmpty()) rowCursor++ else -1
    val downloadsRow = if (activeDownloads.isNotEmpty()) rowCursor++ else -1
    val becauseRow = if (becauseYouWatched.isNotEmpty()) rowCursor++ else -1
    val recommendedRow = if (suggested.isNotEmpty()) rowCursor++ else -1
    val moviesRow = if (homeMovies.isNotEmpty()) rowCursor++ else -1
    val seriesRow = if (homeSeries.isNotEmpty()) rowCursor++ else -1
    val topMoviesRow = if (popularMovies.isNotEmpty()) rowCursor++ else -1
    val topSeriesRow = if (popularSeries.isNotEmpty()) rowCursor++ else -1
    val lastLiveRow = if (lastLive != null) rowCursor++ else -1
    val popularLiveRow = if (suggestedLive.isNotEmpty()) rowCursor++ else -1
    val rowIndexByKey = mapOf(
        "home-messages" to messageRow, "continue" to continueRow, "downloads" to downloadsRow, "because-watched" to becauseRow,
        "recommended" to recommendedRow, "recent-movies" to moviesRow, "recent-series" to seriesRow, "top-movies" to topMoviesRow,
        "top-series" to topSeriesRow, "last-live" to lastLiveRow, "popular-live" to popularLiveRow,
    )
    val initialRow = rowIndexByKey[remembered.rowKey]?.takeIf { it >= 0 } ?: 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialRow)
    val availableHomeContentRows = buildSet {
        if (activeDownloads.isNotEmpty()) add("downloads")
        if (becauseYouWatched.isNotEmpty()) add("because-watched")
        if (suggested.isNotEmpty()) add("recommended")
        if (homeMovies.isNotEmpty()) add("recent-movies")
        if (homeSeries.isNotEmpty()) add("recent-series")
        if (popularMovies.isNotEmpty()) add("top-movies")
        if (popularSeries.isNotEmpty()) add("top-series")
        if (lastLive != null) add("last-live")
        if (suggestedLive.isNotEmpty()) add("popular-live")
    }
    LaunchedEffect(pendingContinueRowFocus?.id, navigationMemory, continueWatching.isEmpty(), availableHomeContentRows) {
        val request = pendingContinueRowFocus ?: return@LaunchedEffect
        if (continueWatching.isNotEmpty()) return@LaunchedEffect
        val targetRow = removalFocusTarget(
            previousKeys = HOME_CONTENT_ROW_ORDER,
            remainingKeys = availableHomeContentRows.toList(),
            removedKey = "continue",
        )
        val handoff = HomeEmptyRowFocusHandoff(
            ensureRowAvailable = { rowKey ->
                val targetIndex = rowIndexByKey[rowKey]
                if (targetIndex == null) {
                    false
                } else {
                    if (listState.layoutInfo.visibleItemsInfo.none { it.key == rowKey }) {
                        listState.scrollToItem(targetIndex)
                    }
                    snapshotFlow {
                        listState.layoutInfo.visibleItemsInfo.any { it.key == rowKey }
                    }.first { it }
                    true
                }
            },
            focusRowFirstCard = { rowKey ->
                homeRowFocusTargets[rowKey]?.focusFirstCard() ?: false
            },
            focusHomeEntry = { homeEntryFocusRequester?.requestFocus() ?: false },
        )
        if (handoff.run(targetRow) && pendingContinueRowFocus?.id == request.id) {
            pendingContinueRowFocus = null
        }
    }
    val homeHeroFocusRequester = remember { FocusRequester() }
    // A player round-trip changes the history input, so Home first presents its cheap
    // no-history fallback and the remembered row is temporarily absent. Once the exact model
    // settles, re-apply the remembered row position (or the hero as the nearest valid target)
    // instead of anchoring the restored screen on the top of the list.
    val settleRestoreArmed = remember { !homeModelSettled }
    LaunchedEffect(homeModelSettled) {
        if (!settleRestoreArmed || !homeModelSettled || remembered.rowKey.isBlank()) {
            return@LaunchedEffect
        }
        val row = rowIndexByKey[remembered.rowKey] ?: -1
        if (row >= 0) {
            listState.scrollToItem(row)
        } else {
            withFrameNanos { }
            runCatching { homeHeroFocusRequester.requestFocus() }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (isTv) 32.dp else 48.dp),
        verticalArrangement = Arrangement.spacedBy(if (isTv) 24.dp else 17.dp),
    ) {
        item(key = "hero") {
            if (featured != null) {
                LaunchedEffect(Unit) {
                    if (remembered.rowKey == "hero") {
                        runCatching { homeHeroFocusRequester.requestFocus() }
                    }
                }
                CinemaHero(
                    featured, isTv, favorites.contains(featured), { onOpen(featured) },
                    { onToggleFavorite(featured) }, onRefresh, loading,
                    unreadNotificationCount = state.unreadNotificationCount,
                    onOpenNotifications = onOpenNotifications,
                    topActions = homeTopActions,
                    watchModifier = Modifier.focusRequester(homeHeroFocusRequester),
                    onFocused = { navigationMemory.save(MainDestination.HOME, "${featured.type}:${featured.id}", 0, "hero", 0) },
                )
            } else {
                HomePlaceholder(
                    loading = loading,
                    onRefresh = onRefresh,
                    isTv = isTv,
                    unreadNotificationCount = state.unreadNotificationCount,
                    onOpenNotifications = onOpenNotifications,
                    errorTitle = homeErrorCopy?.title,
                    errorBody = homeErrorCopy?.body,
                    networkFailure = !homeNetworkUsable,
                    refreshModifier = Modifier.focusRequester(homeHeroFocusRequester),
                    topActions = homeTopActions,
                )
            }
        }
        if (hasHomeMessages) {
            item(key = "home-messages") {
                HomeSectionPadding(isTv) {
                    HomeMessagesZone(
                        renewal = renewalBanner,
                        announcement = announcementMessage,
                        optionalUpdate = optionalUpdate,
                        download = state.operations.download,
                        isTv = isTv,
                        rowIndex = messageRow,
                        remembered = remembered,
                        navigationMemory = navigationMemory,
                        onRenewal = { onGrowthAction(GrowthDestination.RENEWAL) },
                        onOpenAnnouncementDetail = onOpenAnnouncementDetail,
                        onOpenUpdateDetail = onOpenUpdateDetail,
                    )
                }
            }
        }
        if (homeErrorCopy != null) {
            item(key = "error-notice") {
                HomeSectionPadding(isTv) {
                    MoviesErrorNotice(
                        title = homeErrorCopy.title,
                        body = homeErrorCopy.body,
                        onRetry = onRefresh,
                        isTv = isTv,
                        networkFailure = !homeNetworkUsable,
                        modifier = Modifier.padding(
                            horizontal = if (isTv) 0.dp else 14.dp,
                            vertical = if (isTv) 4.dp else 6.dp,
                        ),
                    )
                }
            }
        }
        if (continueWatching.isNotEmpty()) {
            item(key = "continue") {
                HomeSectionPadding(isTv) {
                    HomeContinueWatchingSection(
                        title = "متابعة المشاهدة",
                        rowKey = "continue",
                        rowIndex = continueRow,
                        entries = continueWatching,
                        isTv = isTv,
                        navigationMemory = navigationMemory,
                        onOpen = onOpenHistory,
                        removalController = removalController,
                        onRemoteRemovalStarted = { homeRemovalInputGuard?.begin() },
                    )
                }
            }
        }
        if (activeDownloads.isNotEmpty()) item(key = "downloads") { HomeSectionPadding(isTv) { ActiveDownloadsSection(activeDownloads, isTv, onOpenDownloads, rowFocusRequester("downloads"), rowFocusTarget("downloads")) } }
        if (becauseYouWatched.isNotEmpty()) {
            item(key = "because-watched") { HomeSectionPadding(isTv) { HomeBoxedSection("لانك شاهدت", "because-watched", becauseRow, becauseYouWatched, isTv, navigationMemory, favorites, onOpen, onToggleFavorite, rowFocusRequester("because-watched"), rowFocusTarget("because-watched")) } }
        }
        if (suggested.isNotEmpty()) {
            item(key = "recommended") { HomeSectionPadding(isTv) { HomeBoxedSection("مقترح لك", "recommended", recommendedRow, suggested, isTv, navigationMemory, favorites, onOpen, onToggleFavorite, rowFocusRequester("recommended"), rowFocusTarget("recommended")) } }
        }
        if (homeMovies.isNotEmpty()) {
            item(key = "recent-movies") { HomeSectionPadding(isTv) { HomeBoxedSection("احدث اضافات HULK — افلام", "recent-movies", moviesRow, homeMovies, isTv, navigationMemory, favorites, onOpen, onToggleFavorite, rowFocusRequester("recent-movies"), rowFocusTarget("recent-movies")) } }
        }
        if (homeSeries.isNotEmpty()) {
            item(key = "recent-series") { HomeSectionPadding(isTv) { HomeBoxedSection("احدث اضافات HULK — مسلسلات", "recent-series", seriesRow, homeSeries, isTv, navigationMemory, favorites, onOpen, onToggleFavorite, rowFocusRequester("recent-series"), rowFocusTarget("recent-series")) } }
        }
        if (popularMovies.isNotEmpty()) {
            item(key = "top-movies") { HomeSectionPadding(isTv) { HomeBoxedSection("الاعلى تقييما — افلام", "top-movies", topMoviesRow, popularMovies, isTv, navigationMemory, favorites, onOpen, onToggleFavorite, rowFocusRequester("top-movies"), rowFocusTarget("top-movies")) } }
        }
        if (popularSeries.isNotEmpty()) {
            item(key = "top-series") { HomeSectionPadding(isTv) { HomeBoxedSection("الاعلى تقييما — مسلسلات", "top-series", topSeriesRow, popularSeries, isTv, navigationMemory, favorites, onOpen, onToggleFavorite, rowFocusRequester("top-series"), rowFocusTarget("top-series")) } }
        }
        if (lastLive != null) {
            item(key = "last-live") { HomeSectionPadding(isTv) { HomeLastChannelSection("last-live", lastLiveRow, lastLive, isTv, navigationMemory, onOpenHistory, rowFocusRequester("last-live"), rowFocusTarget("last-live")) } }
        }
        if (suggestedLive.isNotEmpty()) {
            item(key = "popular-live") { HomeSectionPadding(isTv) { HomeBoxedSection("قنوات مقترحة لك", "popular-live", popularLiveRow, suggestedLive, isTv, navigationMemory, favorites, onOpen, onToggleFavorite, rowFocusRequester("popular-live"), rowFocusTarget("popular-live")) } }
        }
    }
}

/**
 * Publishes the exact non-blocking messages the active Home zone presents, so overlapping
 * automatic presentation can be suppressed for the same identity only. Runs as a post-apply side
 * effect (no composition-time state write) and clears ownership when Home leaves composition.
 */
@Composable
private fun HomeMessageOwnership(
    presentation: HomeMessagePresentationState,
    announcement: OperationsAnnouncement?,
    optionalUpdate: OperationsUpdateConfig?,
    persistentAnnouncement: OperationsAnnouncement?,
) {
    val ownedPersistentAnnouncementId = persistentAnnouncement
        ?.id
        ?.takeIf { it == announcement?.id }
    SideEffect {
        presentation.publish(
            announcementId = announcement?.id,
            optionalUpdateVersionCode = optionalUpdate?.latestVersionCode,
            persistentAnnouncementId = ownedPersistentAnnouncementId,
        )
    }
    DisposableEffect(presentation) {
        onDispose { presentation.clear() }
    }
}

private data class HomeMessageCardData(
    val itemKey: String,
    val icon: ImageVector,
    val accent: Color,
    val title: String,
    val body: String,
    val cta: String,
    val onClick: () -> Unit,
)

/**
 * One Home message zone directly under the hero. TV shows the eligible cards in one row, physical
 * right-to-left renewal / announcement / optional update; phone stacks that exact order. The zone
 * collapses to nothing when no message is eligible, so there is no empty reserved block and no
 * duplicate renewal banner. Cards reuse the accepted renewal-banner anatomy and focus treatment.
 */
@Composable
private fun HomeMessagesZone(
    renewal: RenewalBannerContent?,
    announcement: OperationsAnnouncement?,
    optionalUpdate: OperationsUpdateConfig?,
    download: OperationsDownloadUiState,
    isTv: Boolean,
    rowIndex: Int,
    remembered: NavigationPosition,
    navigationMemory: NavigationMemoryStore,
    onRenewal: () -> Unit,
    onOpenAnnouncementDetail: (OperationsAnnouncement) -> Unit,
    onOpenUpdateDetail: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val items = buildList {
        renewal?.let { content ->
            add(
                HomeMessageCardData(
                    itemKey = "renewal",
                    icon = Icons.Rounded.Language,
                    accent = colors.goldBright,
                    title = content.title,
                    body = content.subtitle,
                    cta = "تجديد الاشتراك",
                    onClick = onRenewal,
                ),
            )
        }
        announcement?.let { message ->
            add(
                HomeMessageCardData(
                    itemKey = "announcement:${message.id}",
                    icon = homeAnnouncementIcon(message.severity),
                    accent = homeAnnouncementAccent(message.severity, colors),
                    title = message.title,
                    body = message.message,
                    cta = "عرض الرسالة",
                    onClick = { onOpenAnnouncementDetail(message) },
                ),
            )
        }
        optionalUpdate?.let { update ->
            add(
                HomeMessageCardData(
                    itemKey = "update:${update.latestVersionCode}",
                    icon = Icons.Rounded.SystemUpdate,
                    accent = colors.goldBright,
                    title = "يتوفر تحديث جديد",
                    body = homeUpdateCardBody(update, download),
                    cta = "عرض التحديث",
                    onClick = onOpenUpdateDetail,
                ),
            )
        }
    }
    if (items.isEmpty()) return

    val targetRequester = remember { FocusRequester() }
    val targetItemKey = remembered.itemKey
    val targetIndex = items.indexOfFirst { it.itemKey == targetItemKey }
    // When the exact message leaves the zone (for example an acknowledged announcement), the
    // nearest remaining card takes the restore instead of leaving focus unowned.
    val restoreIndex = when {
        remembered.rowKey != "home-messages" -> -1
        targetIndex >= 0 -> targetIndex
        else -> 0
    }
    LaunchedEffect(remembered.rowKey, targetItemKey, items.size) {
        if (restoreIndex >= 0) {
            withFrameNanos { }
            runCatching { targetRequester.requestFocus() }
        }
    }
    val cardModifier: (Int) -> Modifier = { index ->
        Modifier.restoreFocus(index == restoreIndex, targetRequester)
    }
    val focusSave: (Int) -> () -> Unit = { index ->
        {
            navigationMemory.save(
                MainDestination.HOME,
                items[index].itemKey,
                index,
                "home-messages",
                rowIndex,
            )
        }
    }
    if (isTv) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items.forEachIndexed { index, item ->
                HomeMessageCard(
                    data = item,
                    isTv = true,
                    onClick = item.onClick,
                    modifier = cardModifier(index).weight(1f).fillMaxHeight(),
                    onFocused = focusSave(index),
                )
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            items.forEachIndexed { index, item ->
                HomeMessageCard(
                    data = item,
                    isTv = false,
                    onClick = item.onClick,
                    modifier = cardModifier(index).fillMaxWidth(),
                    onFocused = focusSave(index),
                )
            }
        }
    }
}

@Composable
private fun HomeMessageCard(
    data: HomeMessageCardData,
    isTv: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (isTv) 18.dp else 15.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (isTv) 88.dp else 76.dp)
            .clip(shape)
            .background(
                if (focused) colors.gold.copy(alpha = .16f) else Color(0xFF13140F),
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) colors.goldBright else colors.gold.copy(alpha = .30f),
                shape = shape,
            )
            .onFocusChanged { focusState ->
                focused = focusState.isFocused
                if (focusState.isFocused) onFocused()
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                horizontal = if (isTv) 18.dp else 15.dp,
                vertical = if (isTv) 14.dp else 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = data.title,
                    color = colors.text,
                    fontSize = if (isTv) 18.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // The icon is the last child, so in RTL it renders physically LEFT of the wording.
                Icon(
                    imageVector = data.icon,
                    contentDescription = null,
                    tint = data.accent,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = data.body,
                color = colors.textMuted,
                fontSize = if (isTv) 13.sp else 12.sp,
                lineHeight = if (isTv) 18.sp else 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = data.cta,
            color = colors.goldBright,
            fontSize = if (isTv) 12.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(colors.gold.copy(alpha = .12f))
                .padding(horizontal = 11.dp, vertical = 7.dp),
        )
    }
}

private fun homeAnnouncementIcon(severity: OperationsAnnouncementSeverity): ImageVector =
    when (severity) {
        OperationsAnnouncementSeverity.INFO -> Icons.Rounded.Info
        OperationsAnnouncementSeverity.WARNING,
        OperationsAnnouncementSeverity.IMPORTANT,
        -> Icons.Rounded.Warning
    }

private fun homeAnnouncementAccent(
    severity: OperationsAnnouncementSeverity,
    colors: sa.hulksa.player.ui.theme.HulkColors,
): Color = when (severity) {
    // Severity stays inside the shared gold family; the wording carries the severity.
    OperationsAnnouncementSeverity.INFO -> colors.goldBright
    OperationsAnnouncementSeverity.WARNING,
    OperationsAnnouncementSeverity.IMPORTANT,
    -> colors.gold
}

/** Real optional-update state for the Home card; the complete actions stay in the existing detail. */
private fun homeUpdateCardBody(
    update: OperationsUpdateConfig,
    download: OperationsDownloadUiState,
): String = when (download.status) {
    OperationsDownloadStatus.DOWNLOADING ->
        download.progressPercent?.let { "جارٍ التنزيل $it%" } ?: "جارٍ التنزيل…"

    OperationsDownloadStatus.INSTALLER_OPENED -> "اكمل التثبيت من مثبت Android"
    OperationsDownloadStatus.UNKNOWN_SOURCES_BLOCKED -> "اسمح بالتثبيت من هذا المصدر ثم اعد المحاولة"
    OperationsDownloadStatus.FAILED -> download.message ?: "تعذر تنزيل التحديث"
    OperationsDownloadStatus.IDLE -> "الإصدار ${update.latestVersionName}"
}

@Composable
private fun ActiveDownloadsSection(
    downloads: List<OfflineDownload>,
    isTv: Boolean,
    onOpenDownloads: () -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    rowFocusTarget: HomeRowFocusTarget? = null,
) {
    val colors = LocalHulkColors.current
    val rowState = rememberLazyListState()
    var firstCardFocused by remember { mutableStateOf(false) }
    SideEffect {
        val target = rowFocusTarget ?: return@SideEffect
        val firstRequester = firstItemFocusRequester ?: return@SideEffect
        target.attach {
            focusHomeRowFirstCard(
                firstCardVisible = {
                    !homeRowFirstCardNeedsReveal(rowState.layoutInfo.visibleItemsInfo.map { it.index })
                },
                revealFirstCard = { rowState.scrollToItem(0) },
                awaitFirstCard = {
                    snapshotFlow { rowState.layoutInfo.visibleItemsInfo.any { it.index == 0 } }
                        .first { it }
                },
                requestFocus = { firstRequester.requestFocus() },
                awaitFocused = { snapshotFlow { firstCardFocused }.first { it } },
            )
        }
    }
    DisposableEffect(rowFocusTarget) {
        onDispose { rowFocusTarget?.detach() }
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Caption physically RIGHT with its simple glyph physically LEFT in RTL.
            Text("التنزيلات الجارية", color = colors.text, fontSize = if (isTv) 20.sp else 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Download, contentDescription = null, tint = colors.goldBright, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(downloads, key = { _, item -> item.downloadId }) { index, item ->
                var focused by remember(item.downloadId) { mutableStateOf(false) }
                val shape = RoundedCornerShape(if (isTv) 15.dp else 14.dp)
                val identity = downloadDisplayIdentity(item)
                Column(
                    modifier = Modifier
                        .width(if (isTv) 270.dp else 300.dp)
                        .then(
                            if (index == 0 && firstItemFocusRequester != null) {
                                Modifier.focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier
                            },
                        )
                        .clip(shape)
                        .background(if (focused) colors.gold.copy(alpha = .12f) else Color(0xFF15160F))
                        .border(
                            if (focused) 2.dp else 1.dp,
                            if (focused) colors.goldBright else colors.line.copy(alpha = .45f),
                            shape,
                        )
                        .onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused) firstCardFocused = index == 0
                        }
                        .clickable(role = Role.Button, onClick = onOpenDownloads)
                        .padding(13.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Canonical name once + independent metadata at physical RIGHT.
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = identity.title,
                                color = colors.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (identity.metadata.isNotEmpty()) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = identity.metadata,
                                    color = colors.textMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(Modifier.width(9.dp))
                        Box(
                            Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(colors.line.copy(alpha = .30f)),
                        )
                        Spacer(Modifier.width(9.dp))
                        // Real state glyph at physical LEFT with its truthful caption.
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                downloadHomeStateGlyph(item.status),
                                contentDescription = null,
                                tint = colors.goldBright,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = downloadTvRowStatusLabel(item.status),
                                color = colors.textMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                            )
                        }
                    }
                    // A known real total shows the real track; unknown progress stays
                    // indeterminate with no invented percentage or empty fake track.
                    val knownProgress = downloadKnownProgress(item)
                    if (knownProgress != null) {
                        Spacer(Modifier.height(9.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color.White.copy(alpha = .12f)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(knownProgress)
                                    .fillMaxHeight()
                                    .background(colors.goldBright),
                            )
                        }
                    }
                    val telemetry = downloadStatusLine(item)
                    if (
                        telemetry.isNotBlank() &&
                        (item.status == OfflineStatus.DOWNLOADING || item.status == OfflineStatus.PAUSED)
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "\u200E$telemetry",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.line.copy(alpha = .30f)),
                    )
                    Spacer(Modifier.height(8.dp))
                    // Caption physically RIGHT, opening chevron physically LEFT; this is the
                    // affordance of the single whole-card action, not a nested control.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "فتح التنزيلات",
                            color = colors.goldBright,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.ChevronLeft,
                            contentDescription = null,
                            tint = colors.goldBright,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun downloadKnownProgress(item: OfflineDownload): Float? =
    if (item.totalBytes > 0L &&
        (item.status == OfflineStatus.DOWNLOADING || item.status == OfflineStatus.PAUSED)
    ) {
        item.progress.coerceIn(0f, 1f)
    } else {
        null
    }

/**
 * Truthful textual telemetry: the same known-total policy as the progress track. An unknown
 * total never produces an invented percentage; the real transfer rate stays when it is known.
 */
internal fun downloadStatusLine(item: OfflineDownload): String = when (item.status) {
    OfflineStatus.DOWNLOADING -> listOfNotNull(
        if (item.totalBytes > 0L) "${(item.progress * 100).toInt()}%" else null,
        if (item.bytesPerSecond > 0L) formatTransferRate(item.bytesPerSecond) else null,
    ).joinToString("  •  ")

    OfflineStatus.PAUSED -> if (item.totalBytes > 0L) "${(item.progress * 100).toInt()}%" else ""
    OfflineStatus.CHECKING -> ""
    OfflineStatus.WAITING_NETWORK -> ""
    OfflineStatus.WAITING_SCHEDULE -> ""
    OfflineStatus.WAITING_STORAGE -> ""
    else -> ""
}

private fun downloadHomeStateGlyph(status: OfflineStatus): ImageVector = when (status) {
    OfflineStatus.QUEUED,
    OfflineStatus.CHECKING,
    -> Icons.Rounded.Download

    OfflineStatus.DOWNLOADING -> Icons.Rounded.Downloading
    OfflineStatus.PAUSED -> Icons.Rounded.Pause
    OfflineStatus.WAITING_NETWORK -> Icons.Rounded.WifiOff
    OfflineStatus.WAITING_SCHEDULE -> Icons.Rounded.Schedule
    OfflineStatus.WAITING_STORAGE -> Icons.Rounded.Storage
    OfflineStatus.COMPLETED -> Icons.Rounded.Check
    OfflineStatus.FAILED -> Icons.Outlined.ErrorOutline
}

@Composable
private fun HomeSectionPadding(isTv: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = if (isTv) TV_PAGE_GUTTER else 0.dp)) { content() }
}

/** First list index that holds a Home content row: only the Hero/placeholder occupies index 0. */
internal fun homeRowCursorStart(isTv: Boolean): Int = 1

/** Original Home hero title/subtitle block, shared by every header variant. */
@Composable
private fun CinemaHeroTitle(modifier: Modifier = Modifier) {
    val colors = LocalHulkColors.current
    Column(modifier) {
        Text("الرئيسية", color = colors.text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("توصيات ومحتوى جديد", color = colors.textMuted, fontSize = 11.sp)
    }
}

@Composable
private fun CinemaHero(
    item: ContentItem,
    isTv: Boolean,
    isFavorite: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean,
    unreadNotificationCount: Int,
    onOpenNotifications: () -> Unit,
    topActions: @Composable () -> Unit,
    watchModifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    val colors = LocalHulkColors.current
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val adaptiveUi = LocalAdaptiveUi.current
    val density = LocalDensity.current
    // R02: the compact phone Home header group follows the same boundary as the compact bottom
    // navigation. TV and expanded/rail layouts keep the original full-size header controls.
    val compactHeaderActions = !isTv && adaptiveUi.navigationType != HulkNavigationType.RAIL
    // Measured header content height (status-bar inset excluded) so the phone artwork window
    // stays below the header only in the genuinely constrained single-row fallback.
    var measuredHeaderContentHeightDp by remember {
        mutableStateOf(HERO_PHONE_HEADER_OFFSET_DP.dp)
    }
    val isPortraitPhone = !isTv && configuration.screenWidthDp < 600 && configuration.screenHeightDp > configuration.screenWidthDp
    // Model 1 derives the wide hero from the actual viewport (about two thirds, bounded) instead
    // of the old fixed 410dp; phone/short windows keep their accepted adaptive heights.
    val heroHeight = when {
        isTv -> (configuration.screenHeightDp * 2f / 3f).coerceIn(350f, 420f).dp
        isPortraitPhone -> (configuration.screenHeightDp * .58f).coerceIn(420f, 520f).dp
        else -> 288.dp
    }
    // One selected-hero load: technical facts plus the bounded presentation backfill, shared with
    // the facts row so artwork and copy never start competing requests.
    val metadataStore = remember(context) { HomeHeroMetadataStore.get(context) }
    val metadataOwner = metadataStore.currentOwner()
    var heroMetadata by remember(item.type, item.id, metadataOwner) {
        mutableStateOf(metadataStore.cached(metadataOwner, item))
    }
    LaunchedEffect(item.type, item.id, metadataOwner) {
        val owner = metadataOwner ?: return@LaunchedEffect
        val loaded = metadataStore.metadata(owner, item, requirePresentation = true)
        metadataStore.publishIfCurrent(owner) { heroMetadata = loaded }
    }
    val artworkCandidates = remember(heroMetadata.artworkUrl, item.backdropUrl, item.posterUrl) {
        homeHeroArtworkCandidates(
            metadataArtwork = heroMetadata.artworkUrl,
            itemBackdrop = item.backdropUrl,
            itemPoster = item.posterUrl,
        )
    }
    val heroPlot = (heroMetadata.plot ?: item.plot)
        ?.trim()
        ?.takeIf(String::isNotBlank)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .background(Color(0xFF0A0B08)),
    ) {
        // One-row fit resolved from the real title/subtitle at the current font scale and the
        // natural-width action group, minus the header's own horizontal padding.
        val compactHeaderSingleRow = if (!compactHeaderActions) {
            true
        } else {
            val baseTextStyle = LocalTextStyle.current
            val textMeasurer = rememberTextMeasurer()
            val titleWidth = remember(textMeasurer, baseTextStyle, density) {
                with(density) {
                    textMeasurer.measure(
                        text = AnnotatedString("الرئيسية"),
                        style = baseTextStyle.copy(
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    ).size.width.toDp()
                }
            }
            val subtitleWidth = remember(textMeasurer, baseTextStyle, density) {
                with(density) {
                    textMeasurer.measure(
                        text = AnnotatedString("توصيات ومحتوى جديد"),
                        style = baseTextStyle.copy(fontSize = 11.sp),
                        maxLines = 1,
                    ).size.width.toDp()
                }
            }
            mobileInlineTopActionsFitSingleRow(
                titleWidth = titleWidth,
                subtitleWidth = subtitleWidth,
                availableWidth =
                    maxWidth - HERO_PHONE_HEADER_HORIZONTAL_PADDING_DP.dp * 2,
            )
        }
        HeroArtwork(
            candidates = artworkCandidates,
            contentDescription = item.name,
            phonePortrait = isPortraitPhone,
            phoneWindowHeightDp = (configuration.screenWidthDp * 9f / 16f).dp,
            phoneWindowTopOffsetDp = if (compactHeaderActions && !compactHeaderSingleRow) {
                maxOf(HERO_PHONE_HEADER_OFFSET_DP.dp, measuredHeaderContentHeightDp)
            } else {
                HERO_PHONE_HEADER_OFFSET_DP.dp
            },
        )
        if (isTv) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = .18f),
                        .55f to Color.Transparent,
                        1f to colors.background,
                    ),
                ),
            )
            Box(
                Modifier.fillMaxSize().background(
                    // Physical left-to-right readability mask: the artwork keeps its detail on
                    // the left while the copy envelope at x >= 0.54 is fully protected.
                    Brush.horizontalGradient(
                        0.00f to colors.background.copy(alpha = .08f),
                        0.30f to colors.background.copy(alpha = .08f),
                        0.40f to colors.background.copy(alpha = .20f),
                        0.48f to colors.background.copy(alpha = .65f),
                        0.54f to colors.background.copy(alpha = .94f),
                        0.68f to colors.background.copy(alpha = 1f),
                        1.00f to colors.background.copy(alpha = 1f),
                    ),
                ),
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    // Phone/short windows protect the lower copy with a vertical mask instead
                    // of reusing the wide horizontal split.
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = .18f),
                        .42f to Color.Transparent,
                        .60f to colors.background.copy(alpha = .55f),
                        .78f to colors.background.copy(alpha = .92f),
                        1f to colors.background,
                    ),
                ),
            )
        }

        // Header and copy are explicit siblings in one vertical flow: the measured header height
        // is reserved before the copy region receives the remaining space, so no title, fact row
        // or plot can ever grow into the header. The copy stays on the physical right for wide
        // layouts through the direction-relative Start alignment of this Column.
        Column(Modifier.fillMaxSize()) {
            if (compactHeaderActions) {
                // R02: one compact five-action group in the original header slot, natural content
                // width beside the title; the measured fit keeps the title/subtitle readable and
                // the constrained reflow stays inside the header.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .onGloballyPositioned { coordinates ->
                            measuredHeaderContentHeightDp =
                                with(density) { coordinates.size.height.toDp() }
                        }
                        .padding(
                            horizontal = HERO_PHONE_HEADER_HORIZONTAL_PADDING_DP.dp,
                            vertical = 10.dp,
                        ),
                ) {
                    if (compactHeaderSingleRow) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CinemaHeroTitle(Modifier.weight(1f))
                            topActions()
                        }
                    } else {
                        Column(Modifier.fillMaxWidth()) {
                            CinemaHeroTitle(Modifier.align(Alignment.Start))
                            Box(Modifier.align(Alignment.End)) { topActions() }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isTv) Modifier else Modifier.statusBarsPadding())
                        .padding(
                            horizontal = if (isTv) 26.dp else HERO_PHONE_HEADER_HORIZONTAL_PADDING_DP.dp,
                            vertical = if (isTv) 18.dp else 10.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CinemaHeroTitle(Modifier.weight(1f))
                    if (isLoading) LoadingRing()
                    Spacer(Modifier.width(10.dp))
                    NotificationBellButton(
                        unreadCount = unreadNotificationCount,
                        isTv = isTv,
                        onClick = onOpenNotifications,
                    )
                    Spacer(Modifier.width(8.dp))
                    RoundAction(Icons.Rounded.Refresh, "تحديث المحتوى", onRefresh)
                }
            }
            HeroCopy(
                title = item.name,
                item = item,
                isTv = isTv,
                heroMetadata = heroMetadata,
                heroPlot = heroPlot,
                isFavorite = isFavorite,
                onOpen = onOpen,
                onToggleFavorite = onToggleFavorite,
                watchModifier = watchModifier,
                onFocused = onFocused,
                modifier = Modifier
                    .fillMaxWidth(if (isTv) HERO_WIDE_COPY_WIDTH_FRACTION else 1f)
                    .weight(1f)
                    .padding(horizontal = if (isTv) 27.dp else 18.dp),
            )
        }
    }
}

private const val HERO_WIDE_COPY_WIDTH_FRACTION = .46f
private const val HERO_PHONE_HEADER_OFFSET_DP = 64
private const val HERO_PHONE_HEADER_HORIZONTAL_PADDING_DP = 18

/**
 * Measured hero-copy anchors. The header owns the space above [titleTopPx]; the title gets one
 * reserved two-line slot, the facts row gets a reserved two-row budget, and the actions are
 * anchored from the bottom. `reservedPlotLines` is derived from the actual remaining height so the
 * same viewport/font scale keeps the title, facts and action positions stable while plot lines
 * yield first.
 */
internal data class HeroCopyLayoutMetrics(
    val titleTopPx: Int,
    val factsTopPx: Int,
    val plotTopPx: Int,
    val actionsTopPx: Int,
    val titleSlotPx: Int,
    val factsSlotPx: Int,
    val actionsHeightPx: Int,
    val reservedPlotLines: Int,
    val plotLineHeightPx: Int,
    val fitsContent: Boolean,
)

internal fun heroCopyLayoutMetrics(
    availableHeightPx: Int,
    titleSlotPx: Int,
    factsSlotPx: Int,
    actionsHeightPx: Int,
    topGapPx: Int,
    bottomPaddingPx: Int,
    titleFactsGapPx: Int,
    plotGapPx: Int,
    plotLineHeightPx: Int,
    maxPlotLines: Int = 2,
): HeroCopyLayoutMetrics {
    val height = availableHeightPx.coerceAtLeast(0)
    val safeLineHeight = plotLineHeightPx.coerceAtLeast(1)
    val fixedBand = titleSlotPx + titleFactsGapPx + factsSlotPx + plotGapPx +
        bottomPaddingPx + actionsHeightPx
    val usableHeight = (height - topGapPx).coerceAtLeast(0)
    val reservedPlotLines = if (usableHeight <= fixedBand) {
        0
    } else {
        ((usableHeight - fixedBand) / safeLineHeight)
            .coerceIn(0, maxPlotLines.coerceAtLeast(0))
    }
    val bandHeight = fixedBand + reservedPlotLines * safeLineHeight
    val unclampedTitleTop = height - bandHeight
    val titleTop = unclampedTitleTop.coerceAtLeast(topGapPx)
    val factsTop = titleTop + titleSlotPx + titleFactsGapPx
    val actionsTop = (height - bottomPaddingPx - actionsHeightPx).coerceAtLeast(0)
    val plotTop = actionsTop - plotGapPx - reservedPlotLines * safeLineHeight
    return HeroCopyLayoutMetrics(
        titleTopPx = titleTop,
        factsTopPx = factsTop,
        plotTopPx = plotTop,
        actionsTopPx = actionsTop,
        titleSlotPx = titleSlotPx,
        factsSlotPx = factsSlotPx,
        actionsHeightPx = actionsHeightPx,
        reservedPlotLines = reservedPlotLines,
        plotLineHeightPx = safeLineHeight,
        fitsContent = unclampedTitleTop >= topGapPx,
    )
}

/**
 * Facts may exceed the reserved two-row budget on dense Series metadata or a large font scale.
 * The plot is lower priority, so its reserved lines are reduced first; the caller only moves the
 * title/facts block upward when there is no plot left to give.
 */
internal fun heroCopyVisiblePlotLines(
    reservedPlotLines: Int,
    measuredFactsHeightPx: Int,
    factsSlotPx: Int,
    plotLineHeightPx: Int,
): Int {
    if (reservedPlotLines <= 0) return 0
    val safeLineHeight = plotLineHeightPx.coerceAtLeast(1)
    val factsOverflow = (measuredFactsHeightPx - factsSlotPx).coerceAtLeast(0)
    val remainingPlotHeight = reservedPlotLines * safeLineHeight - factsOverflow
    return (remainingPlotHeight / safeLineHeight).coerceIn(0, reservedPlotLines)
}

/**
 * Last-resort upward shift of the title/facts block when the measured facts exceed the reserved
 * facts-plus-plot space. It is bounded by the real gap below the header, and actions never move.
 */
internal fun heroCopyFactsShiftPx(
    measuredFactsHeightPx: Int,
    factsTopPx: Int,
    actionsTopPx: Int,
    plotGapPx: Int,
    availableTopShiftPx: Int,
): Int {
    val factsRoom = (actionsTopPx - plotGapPx - factsTopPx).coerceAtLeast(0)
    val overflow = (measuredFactsHeightPx - factsRoom).coerceAtLeast(0)
    return overflow.coerceIn(0, availableTopShiftPx.coerceAtLeast(0))
}

/** Non-phone hero artwork always uses the same full-bleed background window. */
internal fun heroArtworkLayoutMode(phonePortrait: Boolean): HeroArtworkLayoutMode =
    if (phonePortrait) HeroArtworkLayoutMode.PHONE_WINDOW else HeroArtworkLayoutMode.WIDE_BACKGROUND

internal enum class HeroArtworkLayoutMode {
    PHONE_WINDOW,
    WIDE_BACKGROUND,
}

/**
 * Home hero copy region. The full header/copy split is owned by the caller's Column, so this
 * layout only receives the space that is genuinely left below the measured header.
 *
 * Title: fixed two-line slot, bottom-aligned inside the slot, so one-line and two-line titles
 * start the facts at the same y. Facts: wrapped at the real width inside a reserved two-row
 * budget; dense groups may use the plot budget next. Actions: measured and anchored from the
 * bottom. Plot: two lines when the viewport allows, one line on short TV heroes, none when the
 * facts consume the available budget. Every anchor depends on the viewport/font scale and the
 * measured action height, never on asynchronous metadata arriving at a different time.
 */
@Composable
private fun HeroCopy(
    title: String,
    item: ContentItem,
    isTv: Boolean,
    heroMetadata: HomeHeroTechnicalMetadata,
    heroPlot: String?,
    isFavorite: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    watchModifier: Modifier,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val titleFontSize = if (isTv) 36.sp else 28.sp
    val titleLineHeight = if (isTv) 42.sp else 34.sp
    val titleSlotPx = with(density) { (titleLineHeight.toDp() * 2f).roundToPx() }
    val factsRowHeightPx = with(density) { (if (isTv) 24.sp else 20.sp).toDp().roundToPx() }
    val factsRowGapPx = with(density) { (if (isTv) 8.dp else 6.dp).roundToPx() }
    val factsSlotPx = factsRowHeightPx * 2 + factsRowGapPx
    val plotLineHeightPx = with(density) { (if (isTv) 17.sp else 18.sp).toDp().roundToPx() }
    val titleFactsGapPx = with(density) { (if (isTv) 8.dp else 9.dp).roundToPx() }
    val plotGapPx = with(density) { (if (isTv) 8.dp else 10.dp).roundToPx() }
    val bottomPaddingPx = with(density) { (if (isTv) 40.dp else 24.dp).roundToPx() }
    val topGapPx = with(density) { (if (isTv) 16.dp else 12.dp).roundToPx() }

    SubcomposeLayout(modifier = modifier) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight

        val actionsPlaceable = subcompose("hero-actions") {
            HomeHeroActions(
                primaryCaption = if (item.type == ContentType.SERIES) "عرض الحلقات" else "شاهد الان",
                isFavorite = isFavorite,
                isTv = isTv,
                onOpen = onOpen,
                onToggleFavorite = onToggleFavorite,
                watchModifier = watchModifier,
                onFocused = onFocused,
            )
        }.first().measure(Constraints(maxWidth = width))

        val metrics = heroCopyLayoutMetrics(
            availableHeightPx = height,
            titleSlotPx = titleSlotPx,
            factsSlotPx = factsSlotPx,
            actionsHeightPx = actionsPlaceable.height,
            topGapPx = topGapPx,
            bottomPaddingPx = bottomPaddingPx,
            titleFactsGapPx = titleFactsGapPx,
            plotGapPx = plotGapPx,
            plotLineHeightPx = plotLineHeightPx,
        )

        val factsPlaceable = subcompose("hero-facts") {
            HomeHeroFacts(
                item = item,
                isTv = isTv,
                metadata = heroMetadata,
                modifier = Modifier.fillMaxWidth(),
            )
        }.firstOrNull()?.measure(Constraints(maxWidth = width))
        val factsHeight = factsPlaceable?.height ?: 0

        val factsShift = heroCopyFactsShiftPx(
            measuredFactsHeightPx = factsHeight,
            factsTopPx = metrics.factsTopPx,
            actionsTopPx = metrics.actionsTopPx,
            plotGapPx = plotGapPx,
            availableTopShiftPx = metrics.titleTopPx - topGapPx,
        )
        val titleTop = metrics.titleTopPx - factsShift
        val factsTop = metrics.factsTopPx - factsShift
        val visiblePlotLines = if (factsShift > 0) {
            0
        } else {
            heroCopyVisiblePlotLines(
                reservedPlotLines = metrics.reservedPlotLines,
                measuredFactsHeightPx = factsHeight,
                factsSlotPx = factsSlotPx,
                plotLineHeightPx = metrics.plotLineHeightPx,
            )
        }

        val titlePlaceable = subcompose("hero-title") {
            Text(
                text = title,
                color = Color.White,
                fontSize = titleFontSize,
                lineHeight = titleLineHeight,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }.first().measure(Constraints(maxWidth = width, maxHeight = titleSlotPx))

        val plotPlaceable = if (heroPlot != null && visiblePlotLines > 0) {
            subcompose("hero-plot") {
                Text(
                    text = heroPlot,
                    color = Color(0xFFD4D0C5),
                    fontSize = 12.sp,
                    lineHeight = if (isTv) 17.sp else 18.sp,
                    maxLines = visiblePlotLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }.first().measure(
                Constraints(
                    maxWidth = width,
                    maxHeight = visiblePlotLines * metrics.plotLineHeightPx,
                ),
            )
        } else {
            null
        }

        layout(width, height) {
            titlePlaceable.placeRelative(
                x = 0,
                y = titleTop + titleSlotPx - titlePlaceable.height,
            )
            factsPlaceable?.placeRelative(x = 0, y = factsTop)
            plotPlaceable?.placeRelative(
                x = 0,
                y = metrics.actionsTopPx - plotGapPx - plotPlaceable.height,
            )
            actionsPlaceable.placeRelative(x = 0, y = metrics.actionsTopPx)
        }
    }
}

/**
 * Physical-left alignment for hero artwork. Absolute (not start/end relative), so the app's RTL
 * layout never mirrors the artwork into the dark copy region. `AbsoluteAlignment.CenterLeft`
 * resolves to the physical left edge under both layout directions.
 */
internal val heroArtworkPhysicalLeftAlignment: Alignment = AbsoluteAlignment.CenterLeft

/**
 * Real artwork orientation rule: only a genuinely wide decoded source may fill the wide hero.
 * Provider backdrop fields or poster URLs alone never establish orientation.
 */
internal fun homeHeroArtworkIsLandscape(width: Float, height: Float): Boolean =
    width > 0f && height > 0f && width.isFinite() && height.isFinite() &&
        width / height >= 1.3f

/**
 * Ordered real artwork candidates for the hero: the owned metadata backdrop candidate first,
 * then the item backdrop/poster fields; blanks and duplicates are removed.
 */
internal fun homeHeroArtworkCandidates(
    metadataArtwork: String?,
    itemBackdrop: String?,
    itemPoster: String?,
): List<String> = listOfNotNull(metadataArtwork, itemBackdrop, itemPoster)
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()

/**
 * Pure selection state for the hero artwork candidates. A successfully decoded landscape wins
 * immediately; the first successfully decoded portrait/near-square source is retained while the
 * later candidates are tried, and it is still rendered when every later candidate fails. The
 * brand mark is used only when no usable real image was obtained.
 */
internal data class HeroArtworkSelectionState(
    val candidateIndex: Int = 0,
    val retainedPortraitIndex: Int? = null,
    val landscapeIndex: Int? = null,
    val exhausted: Boolean = false,
) {
    val settled: Boolean get() = landscapeIndex != null || exhausted
    val displayIndex: Int? get() = landscapeIndex ?: retainedPortraitIndex
}

/** A successful decode at the current candidate index; stale/superseded callbacks are ignored. */
internal fun heroArtworkOnLoaded(
    state: HeroArtworkSelectionState,
    index: Int,
    isLandscape: Boolean,
    candidateCount: Int,
): HeroArtworkSelectionState {
    if (state.settled || index != state.candidateIndex) return state
    val isLast = index >= candidateCount - 1
    return when {
        isLandscape -> state.copy(landscapeIndex = index)
        state.retainedPortraitIndex == null -> state.copy(
            retainedPortraitIndex = index,
            candidateIndex = if (isLast) index else index + 1,
            exhausted = isLast,
        )
        isLast -> state.copy(exhausted = true)
        else -> state.copy(candidateIndex = index + 1)
    }
}

/** A failed decode at the current candidate index; the retained portrait survives the failure. */
internal fun heroArtworkOnFailed(
    state: HeroArtworkSelectionState,
    index: Int,
    candidateCount: Int,
): HeroArtworkSelectionState {
    if (state.settled || index != state.candidateIndex) return state
    return if (index >= candidateCount - 1) {
        state.copy(exhausted = true)
    } else {
        state.copy(candidateIndex = index + 1)
    }
}

/**
 * Model 1 artwork stage. Candidates are classified one at a time by their decoded dimensions:
 * a landscape source wins when one exists, while the first portrait/near-square source is
 * retained across later failures. On TV/wide windows every successful source, including the
 * retained portrait fallback, fills the same background bounds as an aspect-preserving
 * physical-left Crop; a portrait is never presented as a narrow contained poster. Nothing is
 * painted wide before the dimensions are known, each candidate is attempted at most once, and
 * the successfully decoded painter is reused for display (no second request and no separate
 * render failure path). On portrait phones every successful source renders into one uniform 16:9
 * artwork window instead.
 */
@Composable
private fun BoxScope.HeroArtwork(
    candidates: List<String>,
    contentDescription: String,
    phonePortrait: Boolean,
    phoneWindowHeightDp: Dp,
    phoneWindowTopOffsetDp: Dp,
) {
    var selection by remember(candidates) { mutableStateOf(HeroArtworkSelectionState()) }
    val painters = remember(candidates) { mutableStateMapOf<Int, Painter>() }

    val currentIndex = selection.candidateIndex
    val currentUrl = if (selection.settled) null else candidates.getOrNull(currentIndex)
    if (currentUrl != null) {
        // Hidden classification pass; only the decoded size is used from this load, and the
        // loaded painter is retained for the display branch so no second request is made.
        AsyncImage(
            model = currentUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                val size = state.painter.intrinsicSize
                painters[currentIndex] = state.painter
                selection = heroArtworkOnLoaded(
                    state = selection,
                    index = currentIndex,
                    isLandscape = homeHeroArtworkIsLandscape(size.width, size.height),
                    candidateCount = candidates.size,
                )
            },
            onError = {
                selection = heroArtworkOnFailed(
                    state = selection,
                    index = currentIndex,
                    candidateCount = candidates.size,
                )
            },
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0f },
        )
    }

    val painter = selection.displayIndex?.let(painters::get)
    if (painter == null) {
        if (phonePortrait) {
            // Phone: the brand fallback stays inside the same reserved 16:9 window.
            Box(
                modifier = heroPhoneArtworkWindow(phoneWindowHeightDp, phoneWindowTopOffsetDp),
                contentAlignment = Alignment.Center,
            ) {
                BrandLogo(Modifier.size(120.dp).graphicsLayer { alpha = .40f })
            }
        } else {
            BrandLogo(
                Modifier
                    .align(heroArtworkPhysicalLeftAlignment)
                    .size(190.dp)
                    .graphicsLayer { alpha = .38f },
            )
        }
        return
    }

    if (heroArtworkLayoutMode(phonePortrait) == HeroArtworkLayoutMode.PHONE_WINDOW) {
        // One uniform phone artwork window for every successful source: full Home-content width,
        // 16:9 height derived from the window width, aspect-preserving centered Crop. Differing
        // source ratios crop inside this window; the window bounds never change.
        Image(
            painter = painter,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = heroPhoneArtworkWindow(phoneWindowHeightDp, phoneWindowTopOffsetDp),
        )
        return
    }

    // TV/wide background: landscape and retained portrait fallback share the exact same bounds,
    // an aspect-preserving Crop and the physical-left visual emphasis. Never a contained poster.
    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        alignment = heroArtworkPhysicalLeftAlignment,
        modifier = Modifier.fillMaxSize(),
    )
}

/** Reserved phone artwork window: below the measured header/safe inset, full content width. */
private fun BoxScope.heroPhoneArtworkWindow(height: Dp, topOffsetDp: Dp): Modifier = Modifier
    .align(Alignment.TopCenter)
    .statusBarsPadding()
    .padding(top = topOffsetDp)
    .fillMaxWidth()
    .height(height)

/**
 * Home hero actions on the accepted compact Details atoms: 13sp bold caption, 17dp icon, 6dp
 * caption/icon gap, 12dp horizontal / 9dp vertical inner padding, shared 12dp shape and stable
 * geometry (`scaleOnFocus = false`), with the [movieActionHeightDp] floor (46dp TV/normal,
 * 42dp accepted compact-height non-TV). The gold primary action stays physical RIGHT with its
 * PlayArrow physically LEFT of the caption; the outlined gold-heart favorite is immediately to its
 * left. Both captions are measured at the current font scale; when the pair cannot fit side by
 * side the existing Details narrow-layout fallback stacks the full-caption controls instead of
 * ellipsizing or shrinking them.
 */
@Composable
private fun HomeHeroActions(
    primaryCaption: String,
    isFavorite: Boolean,
    isTv: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    watchModifier: Modifier,
    onFocused: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val actionMinHeightDp = movieActionHeightDp(
        isTv = isTv,
        compactHeight = detailsProMetrics(
            screenWidthDp = adaptiveUi.screenWidthDp,
            screenHeightDp = adaptiveUi.screenHeightDp,
            isTv = isTv,
        ).compactHeight,
    )
    val captionStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val iconPx = with(density) { 17.dp.roundToPx() }
    val horizontalPaddingPx = with(density) { 12.dp.roundToPx() }
    val iconGapPx = with(density) { 6.dp.roundToPx() }
    val bufferPx = with(density) { 6.dp.roundToPx() }
    fun requiredWidthPx(caption: String): Int = movieActionRequiredWidthPx(
        captionWidthPx = textMeasurer.measure(
            text = AnnotatedString(caption),
            style = captionStyle,
            maxLines = 1,
        ).size.width,
        iconSizePx = iconPx,
        horizontalPaddingPx = horizontalPaddingPx,
        gapPx = iconGapPx,
    ) + bufferPx
    val primaryRequiredPx = requiredWidthPx(primaryCaption)
    // The accepted Home hero favorite caption is constant in both observed states; only the
    // Favorite/FavoriteBorder glyph changes. Keep the historical longest-wording width reservation
    // so the accepted action group never shrinks or reflows (the longer wording is no longer shown).
    val favoriteCaption = "المفضلة"
    val favoriteRequiredPx = maxOf(requiredWidthPx("المفضلة"), requiredWidthPx("في المفضلة"))
    val rowGapPx = with(density) { 9.dp.roundToPx() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidthPx = with(density) { maxWidth.roundToPx() }
        val fitsRow = primaryRequiredPx + favoriteRequiredPx + rowGapPx <= availableWidthPx
        if (fitsRow) {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeHeroPrimaryAction(
                    caption = primaryCaption,
                    minHeightDp = actionMinHeightDp,
                    onClick = onOpen,
                    onFocused = onFocused,
                    modifier = watchModifier.fillMaxHeight(),
                )
                HomeHeroFavoriteAction(
                    caption = favoriteCaption,
                    isFavorite = isFavorite,
                    minHeightDp = actionMinHeightDp,
                    minWidthDp = with(density) { favoriteRequiredPx.toDp() },
                    onClick = onToggleFavorite,
                    trailingIconTint = colors.gold,
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                HomeHeroPrimaryAction(
                    caption = primaryCaption,
                    minHeightDp = actionMinHeightDp,
                    onClick = onOpen,
                    onFocused = onFocused,
                    modifier = watchModifier.fillMaxWidth(),
                )
                HomeHeroFavoriteAction(
                    caption = favoriteCaption,
                    isFavorite = isFavorite,
                    minHeightDp = actionMinHeightDp,
                    minWidthDp = 0.dp,
                    onClick = onToggleFavorite,
                    trailingIconTint = colors.gold,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HomeHeroPrimaryAction(
    caption: String,
    minHeightDp: Int,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusButton(
        text = caption,
        onClick = onClick,
        modifier = modifier.heightIn(min = minHeightDp.dp),
        compact = true,
        scaleOnFocus = false,
        trailingIcon = Icons.Rounded.PlayArrow,
        textMaxLines = 1,
        onFocused = onFocused,
    )
}

@Composable
private fun HomeHeroFavoriteAction(
    caption: String,
    isFavorite: Boolean,
    minHeightDp: Int,
    minWidthDp: Dp,
    onClick: () -> Unit,
    trailingIconTint: Color,
    modifier: Modifier = Modifier,
) {
    FocusButton(
        text = caption,
        onClick = onClick,
        modifier = modifier.heightIn(min = minHeightDp.dp).widthIn(min = minWidthDp),
        compact = true,
        primary = false,
        outlined = true,
        scaleOnFocus = false,
        trailingIcon = if (isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
        trailingIconTint = trailingIconTint,
        textMaxLines = 1,
    )
}

@Composable
private fun HomePlaceholder(
    loading: Boolean,
    onRefresh: () -> Unit,
    isTv: Boolean,
    unreadNotificationCount: Int,
    onOpenNotifications: () -> Unit,
    errorTitle: String? = null,
    errorBody: String? = null,
    networkFailure: Boolean = true,
    refreshModifier: Modifier = Modifier,
    topActions: @Composable () -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    // Same compact boundary as the bottom navigation: TV and expanded/rail keep the original bell.
    val compactHeaderActions = !isTv && adaptiveUi.navigationType != HulkNavigationType.RAIL
    Box(
        Modifier.fillMaxWidth().height(if (isTv) 360.dp else 270.dp).background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (compactHeaderActions) {
            // R02: the same compact group at the original in-content top action position, so the
            // actions stay reachable while Home artwork/content is missing.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(
                        horizontal = HERO_PHONE_HEADER_HORIZONTAL_PADDING_DP.dp,
                        vertical = 10.dp,
                    ),
            ) {
                topActions()
            }
        } else {
            NotificationBellButton(
                unreadCount = unreadNotificationCount,
                isTv = isTv,
                onClick = onOpenNotifications,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .then(if (isTv) Modifier else Modifier.statusBarsPadding())
                    .padding(
                        horizontal = if (isTv) 26.dp else HERO_PHONE_HEADER_HORIZONTAL_PADDING_DP.dp,
                        vertical = if (isTv) 18.dp else 10.dp,
                    ),
            )
        }
        when {
            // One honest error/empty presentation without content; the artwork fallback above the
            // hero owns its own safe logo, never a fake movie or endless spinner.
            errorTitle != null && errorBody != null -> MoviesOfflineEmptyState(
                onRetry = onRefresh,
                title = errorTitle,
                body = errorBody,
                networkFailure = networkFailure,
                modifier = Modifier.padding(horizontal = if (isTv) 16.dp else 14.dp),
            )

            loading -> LoadingRing(label = "نجهز احدث الاضافات…")

            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("سيظهر احدث المحتوى هنا", color = colors.textMuted)
                Spacer(Modifier.height(12.dp))
                FocusButton("تحديث", onRefresh, modifier = refreshModifier, compact = true)
            }
        }
    }
}

/**
 * Home catalog row on the accepted boxed card family. Movies and Series keep their own truthful
 * footers; live entries use the contained-logo Home live card. Row key, item keys, order, caps,
 * favorite long-press and focus restoration semantics stay identical to the previous Home row.
 */
@Composable
private fun HomeBoxedSection(
    title: String,
    rowKey: String,
    rowIndex: Int,
    content: List<ContentItem>,
    isTv: Boolean,
    navigationMemory: NavigationMemoryStore,
    favorites: CatalogFavoriteSnapshot,
    onOpen: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    rowFocusTarget: HomeRowFocusTarget? = null,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val boxedCardWidth = remember(isTv, adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp) {
        homeBoxedCardWidth(isTv, adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp)
    }
    val liveCardWidth = remember(isTv, adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp) {
        homeLiveCardWidth(isTv, adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp)
    }
    val remembered = navigationMemory.position(MainDestination.HOME)
    val targetIndex = if (remembered.rowKey == rowKey) remembered.itemIndex.coerceIn(0, content.lastIndex.coerceAtLeast(0)) else 0
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = targetIndex)
    val targetRequester = remember { FocusRequester() }
    var firstCardFocused by remember { mutableStateOf(false) }
    SideEffect {
        val target = rowFocusTarget ?: return@SideEffect
        val firstRequester = firstItemFocusRequester ?: return@SideEffect
        target.attach {
            focusHomeRowFirstCard(
                firstCardVisible = {
                    !homeRowFirstCardNeedsReveal(rowState.layoutInfo.visibleItemsInfo.map { it.index })
                },
                revealFirstCard = { rowState.scrollToItem(0) },
                awaitFirstCard = {
                    snapshotFlow { rowState.layoutInfo.visibleItemsInfo.any { it.index == 0 } }
                        .first { it }
                },
                requestFocus = { firstRequester.requestFocus() },
                awaitFocused = { snapshotFlow { firstCardFocused }.first { it } },
            )
        }
    }
    DisposableEffect(rowFocusTarget) {
        onDispose { rowFocusTarget?.detach() }
    }
    LaunchedEffect(Unit) {
        if (remembered.rowKey == rowKey && content.isNotEmpty()) {
            rowState.scrollToItem(targetIndex)
            val restoreRequester = if (targetIndex == 0) {
                firstItemFocusRequester ?: targetRequester
            } else {
                targetRequester
            }
            runCatching { restoreRequester.requestFocus() }
        }
    }
    Column {
        Text(title, color = colors.text, fontSize = if (isTv) 20.sp else 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
        ) {
            itemsIndexed(content, key = { _, item -> "${item.type}:${item.id}" }) { index, item ->
                val itemKey = "${item.type}:${item.id}"
                val restore = remembered.rowKey == rowKey &&
                    (remembered.itemKey == itemKey || (remembered.itemKey.isBlank() && index == targetIndex))
                val itemModifier = if (index == 0 && firstItemFocusRequester != null) {
                    Modifier.focusRequester(firstItemFocusRequester)
                } else {
                    Modifier.restoreFocus(restore, targetRequester)
                }
                val saveFocus = {
                    firstCardFocused = index == 0
                    navigationMemory.save(MainDestination.HOME, itemKey, index, rowKey, rowIndex)
                }
                when (item.type) {
                    ContentType.MOVIE -> MoviesCatalogBoxedCard(
                        item = item,
                        isFavorite = favorites.contains(item),
                        onClick = { onOpen(item) },
                        modifier = itemModifier.width(boxedCardWidth),
                        onLongClick = { onToggleFavorite(item) },
                        onFocused = saveFocus,
                    )

                    ContentType.SERIES -> SeriesCatalogBoxedCard(
                        item = item,
                        isFavorite = favorites.contains(item),
                        onClick = { onOpen(item) },
                        modifier = itemModifier.width(boxedCardWidth),
                        onLongClick = { onToggleFavorite(item) },
                        onFocused = saveFocus,
                    )

                    ContentType.LIVE -> LiveChannelHomeCard(
                        name = item.name,
                        artworkUrl = item.posterUrl,
                        isFavorite = favorites.contains(item),
                        onClick = { onOpen(item) },
                        modifier = itemModifier.width(liveCardWidth),
                        onLongClick = { onToggleFavorite(item) },
                        onFocused = saveFocus,
                    )
                }
            }
        }
    }
}

/**
 * Continue Watching keeps the real HistoryEntry identity, saved time, played fraction, open
 * callback and long-press removal, presented with the accepted boxed Recent card. The Series
 * season/episode line uses the existing shared identity helper; the reserved slot keeps mixed
 * movie/series cards the same height.
 *
 * Remove/focus is one Home-owned transaction: the card only requests removal, the controller
 * survives row disposal, and focus is granted only after the updated model exposes the next
 * surviving stable key.
 */
@Composable
private fun HomeContinueWatchingSection(
    title: String,
    rowKey: String,
    rowIndex: Int,
    entries: List<HistoryEntry>,
    isTv: Boolean,
    navigationMemory: NavigationMemoryStore,
    onOpen: (HistoryEntry) -> Unit,
    removalController: HomeHistoryRemovalController,
    onRemoteRemovalStarted: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val cardWidth = remember(isTv, adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp) {
        homeBoxedCardWidth(isTv, adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp)
    }
    val remembered = navigationMemory.position(MainDestination.HOME)
    val targetIndex = if (remembered.rowKey == rowKey) remembered.itemIndex.coerceIn(0, entries.lastIndex.coerceAtLeast(0)) else 0
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = targetIndex)
    val focusRequesters = remember(entries.map { it.key }) {
        entries.associate { it.key to FocusRequester() }
    }
    DisposableEffect(removalController) {
        onDispose { removalController.finish() }
    }
    LaunchedEffect(Unit) {
        if (remembered.rowKey == rowKey && entries.isNotEmpty()) {
            rowState.scrollToItem(targetIndex)
            entries.getOrNull(targetIndex)?.let { entry ->
                runCatching { focusRequesters[entry.key]?.requestFocus() }
            }
        }
    }
    LaunchedEffect(removalController.succeeded, removalController.pendingKey, entries) {
        if (!removalController.succeeded) return@LaunchedEffect
        val removedKey = removalController.pendingKey ?: return@LaunchedEffect
        // The old list is still presented until the owner-guarded Home model update lands.
        if (entries.any { it.key == removedKey }) return@LaunchedEffect
        val remainingKeys = entries.map { it.key }
        val targetKey = removalController.focusTarget(remainingKeys)
        val targetIndex = targetKey?.let { key -> entries.indexOfFirst { it.key == key } } ?: -1
        if (targetKey == null || targetIndex < 0) {
            removalController.finish()
            return@LaunchedEffect
        }
        if (rowState.layoutInfo.visibleItemsInfo.none { it.key == targetKey }) {
            rowState.scrollToItem(targetIndex)
        }
        snapshotFlow { rowState.layoutInfo.visibleItemsInfo.any { it.key == targetKey } }
            .first { it }
        rowState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == targetKey }
            ?.let { targetInfo ->
                val correction = focusedCardScrollCorrection(
                    itemTop = targetInfo.offset,
                    itemBottom = targetInfo.offset + targetInfo.size,
                    usableStart = rowState.layoutInfo.viewportStartOffset,
                    usableEnd = rowState.layoutInfo.viewportEndOffset,
                    marginPx = 0,
                )
                if (correction != 0) rowState.scrollBy(correction.toFloat())
            }
        withFrameNanos { }
        runCatching { focusRequesters[targetKey]?.requestFocus() }
        navigationMemory.save(MainDestination.HOME, targetKey, targetIndex, rowKey, rowIndex)
        removalController.finish()
    }
    Column {
        Text(title, color = colors.text, fontSize = if (isTv) 20.sp else 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
        ) {
            itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
                BoxedHistoryCard(
                    entry = entry,
                    onClick = { onOpen(entry) },
                    modifier = Modifier
                        .width(cardWidth)
                        .focusRequester(focusRequesters.getValue(entry.key)),
                    identityText = seriesHistoryIdentityText(entry),
                    reserveIdentitySlot = true,
                    onFocused = {
                        navigationMemory.save(MainDestination.HOME, entry.key, index, rowKey, rowIndex)
                    },
                    onRemoveRequested = { _, remote ->
                        if (removalController.pendingKey == null) {
                            if (remote) onRemoteRemovalStarted()
                            removalController.begin(entry.key, entries.map { it.key })
                        }
                    },
                )
            }
        }
    }
}

/** Last watched live channel: the same contained-logo live card without VOD metadata or controls. */
@Composable
private fun HomeLastChannelSection(
    rowKey: String,
    rowIndex: Int,
    entry: HistoryEntry,
    isTv: Boolean,
    navigationMemory: NavigationMemoryStore,
    onOpen: (HistoryEntry) -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    rowFocusTarget: HomeRowFocusTarget? = null,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val cardWidth = remember(isTv, adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp) {
        homeLiveCardWidth(isTv, adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp)
    }
    val remembered = navigationMemory.position(MainDestination.HOME)
    val ownedRequester = remember { FocusRequester() }
    val rowRequester = firstItemFocusRequester ?: ownedRequester
    var firstCardFocused by remember { mutableStateOf(false) }
    SideEffect {
        val target = rowFocusTarget ?: return@SideEffect
        target.attach {
            focusHomeRowFirstCard(
                firstCardVisible = { true },
                revealFirstCard = {},
                awaitFirstCard = {},
                requestFocus = { rowRequester.requestFocus() },
                awaitFocused = { snapshotFlow { firstCardFocused }.first { it } },
            )
        }
    }
    DisposableEffect(rowFocusTarget) {
        onDispose { rowFocusTarget?.detach() }
    }
    LaunchedEffect(Unit) {
        if (remembered.rowKey == rowKey) {
            runCatching { rowRequester.requestFocus() }
        }
    }
    Column {
        Text("اخر قناة شاهدتها", color = colors.text, fontSize = if (isTv) 20.sp else 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 5.dp, vertical = 7.dp)) {
            LiveChannelHomeCard(
                name = entry.title,
                artworkUrl = entry.posterUrl,
                isFavorite = false,
                onClick = { onOpen(entry) },
                modifier = Modifier
                    .width(cardWidth)
                    .focusRequester(rowRequester),
                onFocused = {
                    firstCardFocused = true
                    navigationMemory.save(MainDestination.HOME, entry.key, 0, rowKey, rowIndex)
                },
            )
        }
    }
}

private const val HOME_MOBILE_BOXED_CARD_WIDTH_DP = 105f
private const val HOME_MOBILE_LIVE_CARD_WIDTH_DP = 160f

/** Home card width follows the accepted catalog cell so Home rows share the catalog geometry. */
internal fun homeBoxedCardWidth(isTv: Boolean, screenWidthDp: Int, screenHeightDp: Int): Dp {
    if (!isTv) return HOME_MOBILE_BOXED_CARD_WIDTH_DP.dp
    val metrics = tvCatalogMetrics(screenWidthDp, screenHeightDp, boxedCards = true)
    val available = (screenWidthDp - metrics.horizontalContentPaddingDp - metrics.endContentPaddingDp)
        .coerceAtLeast(1f)
    val columns = movieCatalogColumnCount(available, metrics.horizontalSpacingDp, metrics.minCellWidthDp)
    val width = (available - metrics.horizontalSpacingDp * (columns - 1)) / columns
    return width.coerceAtLeast(1f).dp
}

/** Live channel cards keep a wider 16:9 area: the boxed catalog cell scaled for contained logos. */
internal fun homeLiveCardWidth(isTv: Boolean, screenWidthDp: Int, screenHeightDp: Int): Dp {
    if (!isTv) return HOME_MOBILE_LIVE_CARD_WIDTH_DP.dp
    return (homeBoxedCardWidth(true, screenWidthDp, screenHeightDp).value * 1.35f)
        .coerceIn(140f, 240f)
        .dp
}

@Composable
private fun PosterCatalogScreen(
    title: String,
    type: ContentType,
    destination: MainDestination,
    state: HulkUiState,
    isTv: Boolean,
    navigationMemory: NavigationMemoryStore,
    favoriteSnapshot: CatalogFavoriteSnapshot,
    isFavorite: (ContentItem) -> Boolean,
    onSelectCategory: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onOpenHistory: (HistoryEntry) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onRefresh: () -> Unit,
    initialAllFocusRequester: FocusRequester? = null,
    initialAllFocusPending: Boolean = false,
) {
    val colors = LocalHulkColors.current
    val context = LocalContext.current
    val seriesCategoryManagement = destination == MainDestination.SERIES
    val categoryManagement = destination == MainDestination.MOVIES || seriesCategoryManagement
    val catalog = state.catalogs[type]
    // One committed order + hidden owner per account/profile for the selector and the manager,
    // namespaced per section so Series never reads or writes the Movie/Live state. The manager
    // interaction scope is captured when it opens so a later profile change cannot redirect an
    // in-flight write to a newer scope.
    val categoryStateScope = if (seriesCategoryManagement) {
        context.seriesCategoryStateScope()
    } else {
        context.movieCategoryStateScope()
    }
    var hiddenCategoryIds by remember(categoryStateScope, catalog) {
        mutableStateOf(
            when {
                !categoryManagement -> emptySet()
                seriesCategoryManagement -> context.seriesHiddenCategoryIds()
                else -> context.movieHiddenCategoryIds()
            },
        )
    }
    var committedOrderIds by remember(categoryStateScope, catalog) {
        mutableStateOf(
            when {
                !categoryManagement -> emptyList()
                seriesCategoryManagement -> context.seriesCommittedCategoryOrderIds()
                else -> context.movieCommittedCategoryOrderIds()
            },
        )
    }
    var managerScope by remember { mutableStateOf<AccountProfileStateScope?>(null) }
    var showCategoryManager by remember { mutableStateOf(false) }
    var restoreManagerEntryFocus by remember { mutableStateOf(false) }
    val manageCategoriesRequester = remember { FocusRequester() }
    val toggleCategoryVisibility: (String) -> Unit = { categoryId ->
        if (categoryManagement) {
            val updated = if (categoryId in hiddenCategoryIds) {
                hiddenCategoryIds - categoryId
            } else {
                hiddenCategoryIds + categoryId
            }
            hiddenCategoryIds = updated
            if (seriesCategoryManagement) {
                context.saveSeriesHiddenCategoryIds(updated, managerScope)
            } else {
                context.saveMovieHiddenCategoryIds(updated, managerScope)
            }
        }
    }
    val serverCategories = remember(catalog?.categories, committedOrderIds, categoryManagement) {
        when {
            !categoryManagement -> emptyList()
            seriesCategoryManagement ->
                orderedSeriesServerCategories(catalog?.categories.orEmpty(), committedOrderIds)
            else -> orderedMovieServerCategories(catalog?.categories.orEmpty(), committedOrderIds)
        }
    }
    val visibleServerCategories = remember(serverCategories, hiddenCategoryIds) {
        serverCategories.filterNot { it.id in hiddenCategoryIds }
    }
    LaunchedEffect(categoryStateScope, catalog) {
        if (categoryManagement) {
            if (seriesCategoryManagement) {
                context.adoptSeriesCommittedCategoryOrderOnce()
                committedOrderIds = context.seriesCommittedCategoryOrderIds()
            } else {
                context.adoptMovieCommittedCategoryOrderOnce()
                committedOrderIds = context.movieCommittedCategoryOrderIds()
            }
        }
    }
    // An account/profile switch invalidates an open manager session before any stale draft can act.
    LaunchedEffect(categoryStateScope) {
        if (showCategoryManager) {
            showCategoryManager = false
            managerScope = null
        }
    }
    val modelInput = CatalogScreenModelInput(
        catalog = catalog,
        history = state.history,
        favorites = favoriteSnapshot,
        type = type,
        destination = destination,
        categoryId = state.selectedCategoryId,
        query = state.searchQuery,
    )
    val keyedModel = rememberCatalogModelForPresentation(navigationMemory, modelInput)
    val model = keyedModel?.model
    val visible = model?.visible.orEmpty()
    val continueWatching = model?.continueWatching.orEmpty()
    val showingContinue = state.selectedCategoryId == CONTINUE_CATEGORY_ID
    val resultCount = if (showingContinue) continueWatching.size else visible.size
    val catalogErrorMessage = state.errorMessage
    val moviesNetworkUsable by rememberUsableNetworkState()
    val moviesError = categoryManagement && catalogErrorMessage != null
    val moviesOfflineError = moviesError && !moviesNetworkUsable
    val moviesErrorCopy = moviesCatalogErrorCopy(
        offline = moviesOfflineError,
        serverMessage = catalogErrorMessage,
    )
    val moviesOfflineEmpty = moviesError &&
        resultCount == 0 &&
        state.searchQuery.isBlank() &&
        type !in state.loadingTypes
    val noticeRetryRequester = remember { FocusRequester() }
    val refreshRequester = remember { FocusRequester() }
    var noticeRetryFocused by remember { mutableStateOf(false) }
    val noticeWasVisible = remember { mutableStateOf(false) }
    var nextCategoryContentFocusRequestId by remember(destination) { mutableLongStateOf(0L) }
    var categoryContentFocusRequest by remember(destination) { mutableStateOf<CategoryContentFocusRequest?>(null) }
    var armedCategoryContentFocusRequestId by remember(destination) { mutableLongStateOf(0L) }
    val categoryFocusRestoreController = remember(destination) { CategoryFocusRestoreController() }
    LaunchedEffect(moviesError, resultCount) {
        val visible = moviesError && resultCount > 0
        if (noticeWasVisible.value && !visible && noticeRetryFocused) {
            // Removal while Retry is focused restores an attached meaningful neighbor.
            withFrameNanos { }
            val restored = runCatching { refreshRequester.requestFocus() }.getOrDefault(false)
            if (!restored) categoryFocusRestoreController.requestFromSource()
        }
        noticeWasVisible.value = visible
    }
    val selectCategoryAndEnterContent: (String?) -> Unit = { categoryId ->
        if (isTv) {
            nextCategoryContentFocusRequestId += 1L
            val categoryChanged = state.selectedCategoryId != categoryId
            categoryContentFocusRequest = CategoryContentFocusRequest(
                categoryId = categoryId,
                requestId = nextCategoryContentFocusRequestId,
                focusFirstItem = categoryChanged,
            )
            if (categoryChanged) {
                navigationMemory.save(destination, itemKey = "", itemIndex = 0)
                onSelectCategory(categoryId)
            }
        } else {
            onSelectCategory(categoryId)
        }
    }
    val categoryContentFocusReady = categoryContentFocusRequest?.let { request ->
        request.categoryId == state.selectedCategoryId && keyedModel?.input == modelInput &&
            if (showingContinue) continueWatching.isNotEmpty() else visible.isNotEmpty()
    } == true
    LaunchedEffect(
        categoryContentFocusRequest,
        categoryContentFocusReady,
        keyedModel?.input,
        resultCount,
        state.loadingTypes,
    ) {
        val request = categoryContentFocusRequest ?: return@LaunchedEffect
        val exactCategoryApplied = request.categoryId == state.selectedCategoryId && keyedModel?.input == modelInput
        if (categoryContentFocusReady && request.requestId != armedCategoryContentFocusRequestId) {
            withFrameNanos { }
            armedCategoryContentFocusRequestId = request.requestId
        } else if (
            exactCategoryApplied &&
            resultCount == 0 &&
            type !in state.loadingTypes &&
            categoryContentFocusRequest?.requestId == request.requestId
        ) {
            categoryContentFocusRequest = null
        }
    }
    LaunchedEffect(categoryManagement, hiddenCategoryIds, state.selectedCategoryId, catalog) {
        if (categoryManagement && isHiddenServerCategorySelection(state.selectedCategoryId, hiddenCategoryIds)) {
            navigationMemory.save(destination, itemKey = "", itemIndex = 0)
            onSelectCategory(null)
        }
    }
    LaunchedEffect(showCategoryManager) {
        if (!showCategoryManager && restoreManagerEntryFocus) {
            withFrameNanos { }
            runCatching { manageCategoriesRequester.requestFocus() }
            restoreManagerEntryFocus = false
        }
    }
    val adaptiveUi = LocalAdaptiveUi.current
    val tvSafeInsets = remember(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp) {
        tvPageSafeInsets(
            screenWidthDp = adaptiveUi.screenWidthDp,
            screenHeightDp = adaptiveUi.screenHeightDp,
        )
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .then(
                if (isTv) {
                    Modifier
                } else {
                    Modifier.padding(horizontal = MOBILE_SECTION_HORIZONTAL_PADDING)
                },
            ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(
                    if (isTv) {
                        Modifier.padding(
                            start = TV_CATEGORY_PARENT_HORIZONTAL_INSET_DP.dp,
                            top = tvSafeInsets.verticalDp.dp,
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            CatalogHeader(
                title = title,
                resultCount = resultCount,
                query = state.searchQuery,
                onSearch = onSearch,
                onRefresh = onRefresh,
                isTv = isTv,
                onMoveToCategories = categoryFocusRestoreController::requestFromSource,
                countUnit = catalogCountUnit(type),
                onManageCategories = if (categoryManagement) {
                    {
                        managerScope = if (seriesCategoryManagement) {
                            context.seriesCategoryStateScope()
                        } else {
                            context.movieCategoryStateScope()
                        }
                        restoreManagerEntryFocus = true
                        showCategoryManager = true
                    }
                } else {
                    null
                },
                manageCategoriesRequester = manageCategoriesRequester.takeIf { categoryManagement },
                searchIcon = Icons.Rounded.Search.takeIf { categoryManagement },
                toolbarIconTint = colors.gold.takeIf { categoryManagement },
                refreshRequester = refreshRequester.takeIf { categoryManagement },
                downOverrideRequester = noticeRetryRequester.takeIf { moviesError && resultCount > 0 },
            )
            if (moviesError && resultCount > 0) {
                Spacer(Modifier.height(10.dp))
                MoviesErrorNotice(
                    title = moviesErrorCopy.title,
                    body = moviesErrorCopy.body,
                    onRetry = onRefresh,
                    isTv = isTv,
                    networkFailure = moviesOfflineError,
                    retryRequester = noticeRetryRequester,
                    onRetryFocusChanged = { noticeRetryFocused = it },
                    onRetryUp = { runCatching { refreshRequester.requestFocus() }.getOrDefault(false) },
                    onRetryDown = { categoryFocusRestoreController.requestFromSource() },
                )
            }
            Spacer(Modifier.height(11.dp))
            CatalogCategoryBar(
                serverCategories = visibleServerCategories,
                selectedId = state.selectedCategoryId,
                onSelect = selectCategoryAndEnterContent,
                isTv = isTv,
                focusRestoreController = categoryFocusRestoreController,
                initialAllFocusRequester = initialAllFocusRequester,
                initialAllFocusPending = initialAllFocusPending,
                noticeRetryRequester = noticeRetryRequester.takeIf { moviesError && resultCount > 0 },
            )
            Spacer(Modifier.height(9.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (model == null && moviesOfflineEmpty) {
                MoviesOfflineEmptyState(
                    onRetry = onRefresh,
                    modifier = Modifier.align(Alignment.Center),
                    title = moviesErrorCopy.title,
                    body = moviesErrorCopy.body,
                    networkFailure = moviesOfflineError,
                )
            } else if (model == null) {
                LoadingRing(label = "جاري تجهيز $title…", modifier = Modifier.align(Alignment.Center))
            } else if (showingContinue && continueWatching.isNotEmpty()) {
                BoxedHistoryGrid(
                    destination = destination,
                    entries = continueWatching,
                    isTv = isTv,
                    navigationMemory = navigationMemory,
                    onOpen = onOpenHistory,
                    focusFirstItemRequestId = categoryContentFocusRequest
                        ?.takeIf { categoryContentFocusReady && it.focusFirstItem }
                        ?.requestId
                        ?: 0L,
                    focusContentRequestId = categoryContentFocusRequest
                        ?.takeIf { categoryContentFocusReady }
                        ?.requestId
                        ?: 0L,
                    onMoveToCategories = categoryFocusRestoreController::requestFromSource,
                    seriesCards = destination == MainDestination.SERIES,
                )
            } else if (showingContinue) {
                if (moviesOfflineEmpty) {
                    MoviesOfflineEmptyState(
                    onRetry = onRefresh,
                    modifier = Modifier.align(Alignment.Center),
                    title = moviesErrorCopy.title,
                    body = moviesErrorCopy.body,
                    networkFailure = moviesOfflineError,
                )
                } else {
                    EmptyState("لا توجد مشاهدة غير مكتملة في $title")
                }
            } else if (catalog == null && type in state.loadingTypes) {
                LoadingRing(label = "جاري تحميل $title…", modifier = Modifier.align(Alignment.Center))
            } else if (visible.isEmpty()) {
                if (moviesOfflineEmpty) {
                    MoviesOfflineEmptyState(
                    onRetry = onRefresh,
                    modifier = Modifier.align(Alignment.Center),
                    title = moviesErrorCopy.title,
                    body = moviesErrorCopy.body,
                    networkFailure = moviesOfflineError,
                )
                } else {
                    EmptyState("لا توجد نتائج مطابقة")
                }
            } else if (isTv) {
                TvCatalogGrid(
                    content = visible,
                    contentKeys = model.contentKeys,
                    contentKeyIndex = model.contentKeyIndex,
                    destination = destination,
                    navigationMemory = navigationMemory,
                    isFavorite = isFavorite,
                    onOpen = onOpen,
                    onToggleFavorite = onToggleFavorite,
                    restoreFocusedCard = categoryContentFocusRequest?.let { request ->
                        armedCategoryContentFocusRequestId == request.requestId
                    } ?: state.searchQuery.isBlank(),
                    onMoveToCategories = categoryFocusRestoreController::requestFromSource,
                )
            } else {
                ContentGrid(
                    visible, false, destination, navigationMemory, isFavorite, onOpen, onToggleFavorite,
                    restoreFocusedCard = state.searchQuery.isBlank(),
                    preparedContentKeys = model.contentKeys,
                    preparedContentKeyIndex = model.contentKeyIndex,
                )
            }
        }
        if (categoryManagement && showCategoryManager) {
            CategoryManagerDialog(
                categories = serverCategories,
                hiddenIds = hiddenCategoryIds,
                onToggle = toggleCategoryVisibility,
                onDismiss = {
                    showCategoryManager = false
                    managerScope = null
                },
                title = "ادارة الفئات",
                scopeText = if (seriesCategoryManagement) {
                    "الترتيب والاخفاء يطبقان على فئات المسلسلات"
                } else {
                    "الترتيب والاخفاء يطبقان على فئات الافلام"
                },
                emptyText = "لا توجد فئات",
                liveStyle = true,
                onCommitOrder = { ids ->
                    committedOrderIds = ids
                    if (seriesCategoryManagement) {
                        context.saveSeriesCommittedCategoryOrderIds(managerScope, ids)
                    } else {
                        context.saveMovieCommittedCategoryOrderIds(managerScope, ids)
                    }
                },
            )
        }
    }
}

internal fun resolveLivePreview(
    current: ContentItem?,
    visible: List<ContentItem>,
    rememberedItemKey: String,
    rememberedIndex: Int,
): ContentItem? {
    if (current != null && current in visible) return current
    return visible.firstOrNull { "${it.type}:${it.id}" == rememberedItemKey }
        ?: visible.getOrNull(rememberedIndex)
        ?: visible.firstOrNull()
}

internal fun isLivePreviewSelected(
    preview: ContentItem?,
    channel: ContentItem,
): Boolean = preview?.id == channel.id

internal fun liveChannelKey(item: ContentItem): String = "${item.type}:${item.id}"

/** Which surface initiated the current Live favorite mutation. */
internal enum class LiveFavoriteMutationOrigin {
    ROW,
    PREVIEW,
}

/** One Live favorite mutation's ownership and the fallback row it expects after removal. */
internal data class LiveFavoriteMutationIntent(
    val id: Long,
    val origin: LiveFavoriteMutationOrigin,
    val removedKey: String,
    val fallbackKey: String?,
)

/**
 * Snapshot state for the current Live favorite mutation. A newer mutation replaces the active
 * intent, while a late result is ignored unless it owns the current intent id. A successful
 * APPLIED_REMOVED intent stays active until the committed membership change is observed.
 */
internal data class LiveFavoriteMutationState(
    val nextId: Long = 0L,
    val active: LiveFavoriteMutationIntent? = null,
) {
    fun begin(
        origin: LiveFavoriteMutationOrigin,
        removedKey: String,
        fallbackKey: String? = null,
    ): Pair<LiveFavoriteMutationState, LiveFavoriteMutationIntent> {
        val id = nextId + 1L
        val intent = LiveFavoriteMutationIntent(
            id = id,
            origin = origin,
            removedKey = removedKey,
            fallbackKey = fallbackKey,
        )
        return copy(nextId = id, active = intent) to intent
    }

    fun onOutcome(
        intentId: Long,
        status: FavoriteMutationStatus,
    ): LiveFavoriteMutationState {
        val current = active ?: return this
        if (current.id != intentId) return this
        return if (status == FavoriteMutationStatus.APPLIED_REMOVED) this else copy(active = null)
    }

    /** Clear observed or abandoned intent without ever reusing an older result id. */
    fun clear(): LiveFavoriteMutationState = copy(active = null)
}

/** A focused-row removal and the stable surviving row that must receive focus after it disappears. */
internal data class LiveRemovalHandoff(
    val removedKey: String,
    val fallbackKey: String?,
)

/**
 * Resolve the removal fallback from the pre-removal order: next surviving stable key first,
 * previous surviving key when the removed row was last, and null when no row survives.
 */
internal fun liveRemovalHandoff(
    visible: List<ContentItem>,
    removedKey: String,
): LiveRemovalHandoff? {
    val removedIndex = visible.indexOfFirst { liveChannelKey(it) == removedKey }
    if (removedIndex < 0) return null
    val remaining = visible.filterNot { liveChannelKey(it) == removedKey }
    val fallbackIndex = favoritesRemovalFocusIndex(removedIndex, remaining.size)
    return LiveRemovalHandoff(
        removedKey = removedKey,
        fallbackKey = fallbackIndex?.let { remaining.getOrNull(it)?.let(::liveChannelKey) },
    )
}

/**
 * Delta needed to reveal a removal fallback without aligning an already-visible row to the top.
 * Null means the row is not currently laid out; callers may then request one measured reveal.
 */
internal fun liveRemovalRevealDelta(
    itemStart: Int?,
    itemEnd: Int?,
    viewportStart: Int,
    viewportEnd: Int,
): Int? {
    if (itemStart == null || itemEnd == null) return null
    return when {
        itemStart < viewportStart -> itemStart - viewportStart
        itemEnd > viewportEnd -> itemEnd - viewportEnd
        else -> 0
    }
}

internal data class LiveCatalogErrorCopy(val title: String, val body: String)

/**
 * Live catalog error copy: validated connectivity selects the offline wording, otherwise a truthful
 * Live load failure keeps the real server message in the same accepted dark/gold/ivory notice.
 */
internal fun liveCatalogErrorCopy(offline: Boolean, serverMessage: String?): LiveCatalogErrorCopy =
    if (offline) {
        LiveCatalogErrorCopy(
            title = "لا يوجد اتصال بالانترنت",
            body = "تعذر تحديث القنوات ، تحقق من الاتصال وحاول مرة اخرى",
        )
    } else {
        LiveCatalogErrorCopy(
            title = "تعذر تحديث القنوات",
            body = serverMessage?.trim()?.takeIf { it.isNotEmpty() } ?: "حاول مرة اخرى",
        )
    }

@Composable
private fun LiveCatalogScreen(
    state: HulkUiState,
    isTv: Boolean,
    navigationMemory: NavigationMemoryStore,
    favorites: CatalogFavoriteSnapshot,
    onSelectCategory: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onToggleFavoriteWithResult: (ContentItem, (FavoriteMutationOutcome) -> Unit) -> Unit,
    onFavoritesRemovalStarted: () -> Unit,
    onRefresh: () -> Unit,
    initialAllFocusRequester: FocusRequester? = null,
    initialAllFocusPending: Boolean = false,
) {
    val colors = LocalHulkColors.current
    val context = LocalContext.current
    val catalog = state.catalogs[ContentType.LIVE]
    // The explicit snapshot is the observed favorite revision for both membership and hearts;
    // the opaque view-model callback is not a Compose state read in this subtree.
    val visible = remember(catalog, state.selectedCategoryId, state.searchQuery, favorites.persisted) {
        catalog?.items.orEmpty().filter { item ->
            categoryMatches(item, state.selectedCategoryId, favorites::contains) &&
                item.matchesSearch(state.searchQuery)
        }
    }
    var showCategoryManager by remember { mutableStateOf(false) }
    var restoreManagerEntryFocus by remember { mutableStateOf(false) }
    // One shared committed order + hidden owner for the strip, the manager and the browser. The
    // manager interaction scope is captured when it opens so a later profile change cannot redirect
    // an in-flight write to a newer scope.
    val liveProfileScope = context.liveCategoryStateScope()
    var hiddenCategoryIds by remember(liveProfileScope, catalog) {
        mutableStateOf(context.liveHiddenCategoryIds())
    }
    var committedOrderIds by remember(liveProfileScope, catalog) {
        mutableStateOf(context.liveCommittedCategoryOrderIds())
    }
    var managerScope by remember { mutableStateOf<AccountProfileStateScope?>(null) }
    LaunchedEffect(liveProfileScope, catalog) {
        context.adoptLiveCommittedCategoryOrderOnce()
        committedOrderIds = context.liveCommittedCategoryOrderIds()
    }
    // An account/profile switch invalidates an open manager session before any stale draft can act.
    LaunchedEffect(liveProfileScope) {
        if (showCategoryManager) {
            showCategoryManager = false
            managerScope = null
        }
    }
    val serverCategories = remember(catalog?.categories, committedOrderIds) {
        orderedLiveServerCategories(catalog?.categories.orEmpty(), committedOrderIds)
    }
    val visibleServerCategories = remember(serverCategories, hiddenCategoryIds) {
        serverCategories.filterNot { it.id in hiddenCategoryIds }
    }
    val manageCategoriesRequester = remember { FocusRequester() }
    val toggleCategoryVisibility: (String) -> Unit = { categoryId ->
        val updated = if (categoryId in hiddenCategoryIds) {
            hiddenCategoryIds - categoryId
        } else {
            hiddenCategoryIds + categoryId
        }
        hiddenCategoryIds = updated
        context.saveLiveHiddenCategoryIds(updated, managerScope)
    }
    val remembered = navigationMemory.position(MainDestination.LIVE)
    val rememberedIndex = remembered.itemIndex.coerceIn(0, visible.lastIndex.coerceAtLeast(0))
    val previewState = remember(catalog, state.selectedCategoryId) { mutableStateOf<ContentItem?>(null) }
    val channelRequester = remember { FocusRequester() }
    val playRequester = remember { FocusRequester() }
    val favoriteRequester = remember { FocusRequester() }
    var liveFavoriteMutation by remember { mutableStateOf(LiveFavoriteMutationState()) }
    LaunchedEffect(state.selectedCategoryId, catalog) {
        liveFavoriteMutation = liveFavoriteMutation.clear()
    }
    fun beginLiveFavoriteRemoval(
        origin: LiveFavoriteMutationOrigin,
        channel: ContentItem,
        fallbackKey: String?,
    ) {
        val (nextState, intent) = liveFavoriteMutation.begin(
            origin = origin,
            removedKey = liveChannelKey(channel),
            fallbackKey = fallbackKey,
        )
        liveFavoriteMutation = nextState
        onToggleFavoriteWithResult(channel) { outcome ->
            liveFavoriteMutation = liveFavoriteMutation.onOutcome(intent.id, outcome.status)
        }
    }
    val categoryFocusRestoreController = remember { CategoryFocusRestoreController() }
    // Accepted Live catalog error classification: typed validated connectivity plus the real
    // server error, rendered by the shared MoviesErrorNotice family with the actual Live refresh
    // callback. Cached channels, category, item and scroll identity stay intact.
    val liveNetworkUsable by rememberUsableNetworkState()
    val liveError = state.errorMessage != null
    val liveOfflineError = liveError && !liveNetworkUsable
    val liveErrorCopy = liveCatalogErrorCopy(offline = liveOfflineError, serverMessage = state.errorMessage)
    val noticeRetryRequester = remember { FocusRequester() }
    val refreshRequester = remember { FocusRequester() }
    var noticeRetryFocused by remember { mutableStateOf(false) }
    val noticeWasVisible = remember { mutableStateOf(false) }
    LaunchedEffect(liveError, catalog) {
        val noticeVisible = liveError && catalog != null
        if (noticeWasVisible.value && !noticeVisible && noticeRetryFocused) {
            withFrameNanos { }
            val restored = runCatching { refreshRequester.requestFocus() }.getOrDefault(false)
            if (!restored) categoryFocusRestoreController.requestFromSource()
        }
        noticeWasVisible.value = noticeVisible
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = rememberedIndex)
    val focusedChannelIndex = remember(visible) { intArrayOf(rememberedIndex) }
    var nextCategoryContentFocusRequestId by remember { mutableLongStateOf(0L) }
    var categoryContentFocusRequest by remember { mutableStateOf<CategoryContentFocusRequest?>(null) }
    val selectCategoryAndEnterContent: (String?) -> Unit = { categoryId ->
        if (isTv) {
            nextCategoryContentFocusRequestId += 1L
            val categoryChanged = state.selectedCategoryId != categoryId
            categoryContentFocusRequest = CategoryContentFocusRequest(
                categoryId = categoryId,
                requestId = nextCategoryContentFocusRequestId,
                focusFirstItem = categoryChanged,
            )
            if (categoryChanged) {
                navigationMemory.save(MainDestination.LIVE, itemKey = "", itemIndex = 0)
                onSelectCategory(categoryId)
            }
        } else {
            onSelectCategory(categoryId)
        }
    }
    val adaptiveUi = LocalAdaptiveUi.current
    val tvSafeInsets = remember(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp) {
        tvPageSafeInsets(
            screenWidthDp = adaptiveUi.screenWidthDp,
            screenHeightDp = adaptiveUi.screenHeightDp,
        )
    }
    LaunchedEffect(listState, visible, isTv) {
        if (isTv) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            visible.getOrNull(index)?.let { navigationMemory.save(MainDestination.LIVE, "${it.type}:${it.id}", index) }
        }
    }
    // Keying this effect on remembered.itemKey made an ordinary favorite/notice recomposition
    // replay entry restoration after focus had saved a newer channel. Navigation memory is an
    // input, not a trigger: only entry composition, category/search transitions, a real content
    // change, or an explicit category handoff may restart this effect.
    val activeLiveFavoriteMutation = liveFavoriteMutation.active
    val rowRemovalHandoff = activeLiveFavoriteMutation
        ?.takeIf { it.origin == LiveFavoriteMutationOrigin.ROW }
    val rowRemovalApplied = rowRemovalHandoff != null &&
        visible.none { liveChannelKey(it) == rowRemovalHandoff.removedKey }
    val removalFallbackIndex = if (rowRemovalApplied) {
        rowRemovalHandoff?.fallbackKey
            ?.let { key -> visible.indexOfFirst { liveChannelKey(it) == key } }
            ?.takeIf { it >= 0 }
    } else {
        null
    }
    val liveFocusKey = if (rowRemovalApplied) {
        removalFallbackIndex?.let { liveChannelKey(visible[it]) }.orEmpty()
    } else {
        remembered.itemKey
    }
    LaunchedEffect(
        visible,
        state.selectedCategoryId,
        state.searchQuery,
        categoryContentFocusRequest,
    ) {
        previewState.value = resolveLivePreview(
            current = previewState.value,
            visible = visible,
            rememberedItemKey = remembered.itemKey,
            rememberedIndex = rememberedIndex,
        )
        val categoryRequest = categoryContentFocusRequest
            ?.takeIf { it.categoryId == state.selectedCategoryId }
        val handoff = activeLiveFavoriteMutation
        val handoffApplied = handoff != null &&
            visible.none { liveChannelKey(it) == handoff.removedKey }
        if (handoffApplied) {
            if (visible.isEmpty()) {
                navigationMemory.save(MainDestination.LIVE, itemKey = "", itemIndex = 0)
                liveFavoriteMutation = liveFavoriteMutation.clear()
                withFrameNanos { }
                categoryFocusRestoreController.requestFromSource()
                return@LaunchedEffect
            }
            // A preview-button removal must keep focus on that button. A row-held removal has
            // already claimed the shell gesture guard and is the only path that takes list focus.
            if (handoff?.origin == LiveFavoriteMutationOrigin.PREVIEW) {
                previewState.value?.let { fallback ->
                    val fallbackIndex = visible.indexOfFirst { it.id == fallback.id }
                    if (fallbackIndex >= 0) {
                        navigationMemory.save(
                            MainDestination.LIVE,
                            liveChannelKey(fallback),
                            fallbackIndex,
                        )
                    }
                }
                liveFavoriteMutation = liveFavoriteMutation.clear()
                return@LaunchedEffect
            }
            if (handoff?.origin == LiveFavoriteMutationOrigin.ROW) {
                val targetIndex = removalFallbackIndex ?: rememberedIndex
                if (targetIndex in visible.indices) {
                    val targetKey = liveChannelKey(visible[targetIndex])
                    navigationMemory.save(MainDestination.LIVE, targetKey, targetIndex)
                    val layout = listState.layoutInfo
                    val targetInfo = layout.visibleItemsInfo.firstOrNull { it.index == targetIndex }
                    val revealDelta = liveRemovalRevealDelta(
                        itemStart = targetInfo?.offset,
                        itemEnd = targetInfo?.let { it.offset + it.size },
                        viewportStart = layout.viewportStartOffset,
                        viewportEnd = layout.viewportEndOffset,
                    )
                    if (targetInfo == null) {
                        listState.scrollToItem(targetIndex)
                        snapshotFlow {
                            listState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
                        }.first { it }
                    } else if (revealDelta != null && revealDelta != 0) {
                        listState.scrollBy(revealDelta.toFloat())
                    }
                    withFrameNanos { }
                    val focused = runCatching { channelRequester.requestFocus() }.getOrDefault(false)
                    liveFavoriteMutation = liveFavoriteMutation.clear()
                    if (!focused) categoryFocusRestoreController.requestFromSource()
                } else {
                    navigationMemory.save(MainDestination.LIVE, itemKey = "", itemIndex = 0)
                    liveFavoriteMutation = liveFavoriteMutation.clear()
                    withFrameNanos { }
                    categoryFocusRestoreController.requestFromSource()
                }
                return@LaunchedEffect
            }
        }
        // A pending preview removal keeps the button focus. A row intent that has not yet been
        // observed must not replay a list restoration from the disappearing key.
        if (handoff != null && !handoffApplied) {
            if (visible.isEmpty()) {
                navigationMemory.save(MainDestination.LIVE, itemKey = "", itemIndex = 0)
                liveFavoriteMutation = liveFavoriteMutation.clear()
                withFrameNanos { }
                categoryFocusRestoreController.requestFromSource()
            }
            return@LaunchedEffect
        }
        if (visible.isEmpty()) {
            return@LaunchedEffect
        }
        if (categoryRequest != null && visible.isEmpty()) {
            if (
                ContentType.LIVE !in state.loadingTypes &&
                categoryContentFocusRequest?.requestId == categoryRequest.requestId
            ) {
                categoryContentFocusRequest = null
            }
            return@LaunchedEffect
        }
        val targetIndex = when {
            categoryRequest?.focusFirstItem == true && visible.isNotEmpty() -> 0
            categoryRequest != null && visible.isNotEmpty() -> rememberedIndex
            state.searchQuery.isBlank() && remembered.itemKey.isNotBlank() && visible.isNotEmpty() -> rememberedIndex
            else -> null
        }
        if (targetIndex != null) {
            listState.scrollToItem(targetIndex)
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex } }
                .first { it }
            withFrameNanos { }
            val focused = runCatching { channelRequester.requestFocus() }.getOrDefault(false)
            if (focused && categoryRequest != null && categoryContentFocusRequest?.requestId == categoryRequest.requestId) {
                categoryContentFocusRequest = null
            }
        }
    }
    LaunchedEffect(state.selectedCategoryId, hiddenCategoryIds) {
        val selected = state.selectedCategoryId
        val hiddenSelected = selected != null &&
            selected != LIVE_TV_PRO_MAIN_FAVORITES_CATEGORY &&
            selected != LIVE_TV_PRO_MAIN_CONTINUE_CATEGORY &&
            selected != LIVE_TV_PRO_MAIN_RECENT_CATEGORY &&
            selected in hiddenCategoryIds
        if (hiddenSelected) {
            navigationMemory.save(MainDestination.LIVE, itemKey = "", itemIndex = 0)
            onSelectCategory(null)
        }
    }
    LaunchedEffect(showCategoryManager) {
        if (!showCategoryManager && restoreManagerEntryFocus) {
            withFrameNanos { }
            runCatching { manageCategoriesRequester.requestFocus() }
            restoreManagerEntryFocus = false
        }
    }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(
                    if (isTv) {
                        Modifier.padding(
                            start = TV_CATEGORY_PARENT_HORIZONTAL_INSET_DP.dp,
                            top = tvSafeInsets.verticalDp.dp,
                        )
                    } else {
                        Modifier.padding(horizontal = MOBILE_SECTION_HORIZONTAL_PADDING)
                    },
                ),
        ) {
            CatalogHeader(
                title = "البث المباشر",
                resultCount = visible.size,
                query = state.searchQuery,
                onSearch = onSearch,
                onRefresh = onRefresh,
                isTv = isTv,
                onMoveToCategories = categoryFocusRestoreController::requestFromSource,
                onManageCategories = {
                    managerScope = context.liveCategoryStateScope()
                    restoreManagerEntryFocus = true
                    showCategoryManager = true
                },
                manageCategoriesRequester = manageCategoriesRequester,
                searchIcon = Icons.Rounded.Search,
                toolbarIconTint = colors.gold,
                countUnit = "قناة",
                refreshRequester = refreshRequester,
                downOverrideRequester = noticeRetryRequester.takeIf { liveError && catalog != null },
            )
            if (liveError && catalog != null) {
                Spacer(Modifier.height(9.dp))
                MoviesErrorNotice(
                    title = liveErrorCopy.title,
                    body = liveErrorCopy.body,
                    onRetry = onRefresh,
                    isTv = isTv,
                    networkFailure = liveOfflineError,
                    retryRequester = noticeRetryRequester,
                    onRetryFocusChanged = { noticeRetryFocused = it },
                    onRetryUp = { runCatching { refreshRequester.requestFocus() }.getOrDefault(false) },
                    onRetryDown = { categoryFocusRestoreController.requestFromSource() },
                )
            }
            Spacer(Modifier.height(10.dp))
            LiveCategoryBar(
                categories = catalog?.categories.orEmpty(),
                serverCategories = visibleServerCategories,
                selectedId = state.selectedCategoryId,
                onSelect = selectCategoryAndEnterContent,
                isTv = isTv,
                focusRestoreController = categoryFocusRestoreController,
                initialAllFocusRequester = initialAllFocusRequester,
                initialAllFocusPending = initialAllFocusPending,
                noticeRetryRequester = noticeRetryRequester.takeIf { liveError && catalog != null },
            )
            Spacer(Modifier.height(6.dp))
        }
        if (catalog == null && ContentType.LIVE in state.loadingTypes) {
            LoadingRing(label = "جاري تحميل القنوات…", modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 90.dp))
        } else if (catalog == null && liveError) {
            MoviesOfflineEmptyState(
                onRetry = onRefresh,
                modifier = Modifier.padding(top = 40.dp),
                title = liveErrorCopy.title,
                body = liveErrorCopy.body,
                networkFailure = liveOfflineError,
            )
        } else if (visible.isEmpty()) {
            EmptyState("لا توجد قنوات مطابقة")
        } else if (isTv) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        start = TV_PAGE_GUTTER,
                        end = TV_CATEGORY_PARENT_HORIZONTAL_INSET_DP.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(20.dp - TV_PAGE_GUTTER),
            ) {
                Box(Modifier.weight(0.4f).fillMaxHeight().padding(bottom = 12.dp)) {
                    Column(
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))
                            .background(Color(0x6611120D))
                            .border(1.dp, colors.gold.copy(alpha = .18f), RoundedCornerShape(18.dp))
                            .padding(8.dp),
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .onPreviewKeyEvent { event ->
                                    event.type == KeyEventType.KeyDown &&
                                        event.key == Key.DirectionUp &&
                                        focusedChannelIndex[0] == 0 &&
                                        categoryFocusRestoreController.requestFromSource()
                                },
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            itemsIndexed(visible, key = { _, channel -> channel.id }) { index, channel ->
                                val key = liveChannelKey(channel)
                                val restore = key == liveFocusKey ||
                                    (liveFocusKey.isBlank() && index == rememberedIndex)
                                val selected by remember(previewState, channel.id) {
                                    derivedStateOf { isLivePreviewSelected(previewState.value, channel) }
                                }
                                val liveFavoriteRemoval = isTv &&
                                    state.selectedCategoryId == FAVORITES_CATEGORY_ID &&
                                    favorites.contains(channel)
                                ChannelListItem(
                                    item = channel,
                                    selected = selected,
                                    onFocused = {
                                        focusedChannelIndex[0] = index
                                        previewState.value = channel
                                        navigationMemory.save(MainDestination.LIVE, key, index)
                                    },
                                    onClick = { onOpen(channel) },
                                    modifier = Modifier.restoreFocus(restore, channelRequester).focusProperties {
                                        left = playRequester
                                    },
                                    isFavorite = favorites.contains(channel),
                                    onLongClick = {
                                        liveFavoriteMutation = liveFavoriteMutation.clear()
                                        onToggleFavorite(channel)
                                    },
                                    onRemoteLongClick = {
                                        if (liveFavoriteRemoval) {
                                            val fallbackKey = liveRemovalHandoff(visible, key)
                                                ?.fallbackKey
                                            beginFavoritesRemovalLongPress(
                                                enabled = true,
                                                beginGuard = onFavoritesRemovalStarted,
                                                toggleFavorite = {
                                                    beginLiveFavoriteRemoval(
                                                        origin = LiveFavoriteMutationOrigin.ROW,
                                                        channel = channel,
                                                        fallbackKey = fallbackKey,
                                                    )
                                                },
                                            )
                                        } else {
                                            liveFavoriteMutation = liveFavoriteMutation.clear()
                                            onToggleFavorite(channel)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                Box(
                    Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                        .padding(bottom = 12.dp),
                ) {
                    LivePreviewStage(
                        previewState = previewState,
                        favorites = favorites,
                        channelRequester = channelRequester,
                        playRequester = playRequester,
                        favoriteRequester = favoriteRequester,
                        onOpen = onOpen,
                        onToggleFavorite = { channel ->
                            if (
                                isTv &&
                                state.selectedCategoryId == FAVORITES_CATEGORY_ID &&
                                favorites.contains(channel)
                            ) {
                                beginLiveFavoriteRemoval(
                                    origin = LiveFavoriteMutationOrigin.PREVIEW,
                                    channel = channel,
                                    fallbackKey = null,
                                )
                            } else {
                                liveFavoriteMutation = liveFavoriteMutation.clear()
                                onToggleFavorite(channel)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(visible, key = { _, channel -> channel.id }) { index, channel ->
                    ChannelListItem(
                        item = channel,
                        selected = false,
                        onFocused = { navigationMemory.save(MainDestination.LIVE, "${channel.type}:${channel.id}", index) },
                        onClick = { onOpen(channel) },
                        isFavorite = favorites.contains(channel),
                        onLongClick = {
                            liveFavoriteMutation = liveFavoriteMutation.clear()
                            onToggleFavorite(channel)
                        },
                    )
                }
            }
        }
    }
    if (showCategoryManager) {
        CategoryManagerDialog(
            categories = serverCategories,
            hiddenIds = hiddenCategoryIds,
            onToggle = toggleCategoryVisibility,
            onDismiss = {
                showCategoryManager = false
                managerScope = null
            },
            title = "ادارة الفئات",
            scopeText = "الترتيب والاخفاء يطبقان على البث ومستعرض القنوات",
            emptyText = "لا توجد فئات",
            liveStyle = true,
            onCommitOrder = { ids ->
                committedOrderIds = ids
                context.saveLiveCommittedCategoryOrderIds(managerScope, ids)
            },
        )
    }
}

@Composable
private fun LivePreviewStage(
    previewState: State<ContentItem?>,
    favorites: CatalogFavoriteSnapshot,
    channelRequester: FocusRequester,
    playRequester: FocusRequester,
    favoriteRequester: FocusRequester,
    onOpen: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val preview = previewState.value
    LiveStage(
        item = preview,
        isFavorite = preview?.let(favorites::contains) == true,
        channelRequester = channelRequester,
        playRequester = playRequester,
        favoriteRequester = favoriteRequester,
        onWatch = { previewState.value?.let(onOpen) },
        onToggleFavorite = { previewState.value?.let(onToggleFavorite) },
        modifier = modifier,
    )
}

@Composable
private fun LiveStage(
    item: ContentItem?,
    isFavorite: Boolean,
    channelRequester: FocusRequester,
    playRequester: FocusRequester,
    favoriteRequester: FocusRequester,
    onWatch: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f)
                .padding(end = TV_PAGE_GUTTER)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0B0C09))
                .border(1.dp, colors.gold.copy(alpha = .28f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (item == null) {
                Text("اختر قناة", color = colors.textMuted, modifier = Modifier.align(Alignment.Center))
            } else {
                var artworkFailed by remember(item.id, item.posterUrl) { mutableStateOf(false) }
                if (!item.posterUrl.isNullOrBlank() && !artworkFailed) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        contentScale = ContentScale.Fit,
                        onError = { artworkFailed = true },
                    )
                } else {
                    ChannelLogo(item, Modifier.size(132.dp))
                }
            }
        }
        if (item != null) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    item.name,
                    color = colors.text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFD3262E)))
                    Text("بث مباشر", color = colors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = TV_LIVE_ACTION_INSET),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FocusButton(
                            "تشغيل القناة", onWatch,
                            modifier = Modifier.weight(1f).height(50.dp).focusRequester(playRequester).focusProperties {
                                left = favoriteRequester; right = channelRequester
                            }, compact = true,
                            trailingIcon = Icons.Rounded.PlayArrow,
                        )
                        FocusButton(
                            "المفضلة", onToggleFavorite,
                            modifier = Modifier.weight(1f).height(50.dp).focusRequester(favoriteRequester).focusProperties {
                                left = channelRequester; right = playRequester
                            }, primary = false, outlined = true, compact = true,
                            trailingIcon = if (isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                            trailingIconTint = if (isFavorite) colors.gold else null,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Owner-selected Favorites Model 01 type filter. The stable memory key is the only persisted
 * identity; it is stored through the existing per-profile navigation owner (never a new store).
 */
internal enum class FavoritesTypeFilter(
    val memoryKey: String,
    val label: String,
    val contentType: ContentType?,
) {
    ALL("favorites:all", "الكل", null),
    MOVIES("favorites:movies", "الافلام", ContentType.MOVIE),
    SERIES("favorites:series", "المسلسلات", ContentType.SERIES),
    CHANNELS("favorites:channels", "القنوات", ContentType.LIVE),
}

internal fun favoritesTypeFilterFromMemoryKey(key: String?): FavoritesTypeFilter =
    FavoritesTypeFilter.entries.firstOrNull { it.memoryKey == key } ?: FavoritesTypeFilter.ALL

/**
 * Current Favorites projection: real catalogs filtered by the observed favorite owner with stable
 * `type:id` identity and the existing catalog order preserved. Unresolved saved IDs are simply not
 * part of the projection; they are never deleted from storage.
 */
internal fun favoriteCatalogProjection(
    catalogs: Collection<Catalog>,
    isFavorite: (ContentItem) -> Boolean,
): List<ContentItem> = catalogs.asSequence()
    .flatMap { it.items.asSequence() }
    .filter(isFavorite)
    .distinctBy { "${it.type}:${it.id}" }
    .toList()

/** Model 01 type filter over the projection; the real order is never re-sorted. */
internal fun filterFavoritesByType(
    items: List<ContentItem>,
    filter: FavoritesTypeFilter,
): List<ContentItem> = filter.contentType
    ?.let { type -> items.filter { it.type == type } }
    ?: items

/** Mixed Favorites keeps the existing item wording; each real type keeps its own catalog unit. */
internal fun favoritesCountUnit(filter: FavoritesTypeFilter): String = when (filter) {
    FavoritesTypeFilter.ALL -> "عنصر"
    FavoritesTypeFilter.CHANNELS -> "قناة"
    FavoritesTypeFilter.MOVIES -> catalogCountUnit(ContentType.MOVIE)
    FavoritesTypeFilter.SERIES -> catalogCountUnit(ContentType.SERIES)
}

private fun favoritesTypeEmptyMessage(filter: FavoritesTypeFilter): String = when (filter) {
    FavoritesTypeFilter.ALL -> "لم تضف اي محتوى الى المفضلة بعد"
    FavoritesTypeFilter.MOVIES -> "لا توجد افلام في المفضلة"
    FavoritesTypeFilter.SERIES -> "لا توجد مسلسلات في المفضلة"
    FavoritesTypeFilter.CHANNELS -> "لا توجد قنوات في المفضلة"
}

private const val FAVORITES_TITLE = "المفضلة"
private const val FAVORITES_SUBTITLE = "كل ما حفظته في مكان واحد"

/** Compact type selector: the accepted Live fixed-category FocusButton anatomy, no resize on state. */
@Composable
private fun FavoritesTypeFilterBar(
    selected: FavoritesTypeFilter,
    onSelect: (FavoritesTypeFilter) -> Unit,
    isTv: Boolean,
    filterRequesters: Map<FavoritesTypeFilter, FocusRequester>,
) {
    val itemMetrics = rememberLiveCategoryStripMetrics()
    val sidebarUnderlap = rememberCategorySidebarUnderlap(isTv, 8.dp)
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .extendCategoryViewportTowardStart(sidebarUnderlap.viewportExtraDp.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(
            start = sidebarUnderlap.startContentPaddingDp.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp,
        ),
    ) {
        items(FavoritesTypeFilter.entries, key = FavoritesTypeFilter::memoryKey) { filter ->
            FocusButton(
                text = filter.label,
                onClick = { onSelect(filter) },
                primary = selected == filter,
                compact = true,
                scaleOnFocus = false,
                textSizeSp = itemMetrics.textSizeSp,
                textLineHeightSp = 15,
                modifier = Modifier
                    .focusRequester(filterRequesters.getValue(filter))
                    .then(itemMetrics.compactHeightModifier),
            )
        }
    }
}

@Composable
private fun FavoritesEmptyState(message: String) {
    val colors = LocalHulkColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandLogo(Modifier.size(70.dp).graphicsLayer { alpha = .65f })
        Spacer(Modifier.height(10.dp))
        Text(message, color = colors.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FavoritesFilteredEmptyState(message: String) {
    val colors = LocalHulkColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = colors.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FavoritesScreen(
    state: HulkUiState,
    isTv: Boolean,
    navigationMemory: NavigationMemoryStore,
    isFavorite: (ContentItem) -> Boolean,
    onOpen: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onFavoritesRemovalStarted: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val tvSafeInsets = remember(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp) {
        tvPageSafeInsets(
            screenWidthDp = adaptiveUi.screenWidthDp,
            screenHeightDp = adaptiveUi.screenHeightDp,
        )
    }
    // The active filter and the grid location live in the existing per-account/profile navigation
    // owner; no new global preferences/cache owner is introduced for these selectors.
    val remembered = navigationMemory.position(MainDestination.FAVORITES)
    var activeFilter by remember(navigationMemory) {
        mutableStateOf(favoritesTypeFilterFromMemoryKey(remembered.rowKey))
    }
    val allContent = remember(state.catalogs, state.favorites) {
        favoriteCatalogProjection(state.catalogs.values, isFavorite)
    }
    val content = remember(allContent, activeFilter) {
        filterFavoritesByType(allContent, activeFilter)
    }
    val contentKeys = remember(content) { content.map { "${it.type}:${it.id}" } }
    val contentKeyIndex = remember(contentKeys) { indexContentKeys(contentKeys) }
    val filterRequesters = remember(navigationMemory) {
        FavoritesTypeFilter.entries.associateWith { FocusRequester() }
    }
    val networkUsable by rememberUsableNetworkState()
    val errorMessage = state.errorMessage
    val offline = errorMessage != null && !networkUsable
    val resolving = state.loadingTypes.isNotEmpty()
    val errorCopy = if (offline) {
        MoviesErrorCopy(
            title = "لا يوجد اتصال بالانترنت",
            body = "تحقق من اتصالك وحاول مرة اخرى",
        )
    } else {
        MoviesErrorCopy(
            title = "تعذر تحميل المفضلة",
            body = "حاول مرة اخرى",
        )
    }
    // When the whole projection disappears (for example the last removal), the grid owns no
    // surviving card: focus returns to the always-attached type selector, which stays composed
    // through the content change (no readiness guess is needed for a control that never detaches).
    var previouslyHadContent by remember(navigationMemory) { mutableStateOf(content.isNotEmpty()) }
    LaunchedEffect(content.isNotEmpty(), activeFilter) {
        val hasContent = content.isNotEmpty()
        if (previouslyHadContent && !hasContent) {
            withFrameNanos { }
            runCatching { filterRequesters.getValue(activeFilter).requestFocus() }
        }
        previouslyHadContent = hasContent
    }
    Column(
        Modifier
            .fillMaxSize()
            .then(
                if (isTv) {
                    Modifier
                } else {
                    Modifier.padding(horizontal = MOBILE_SECTION_HORIZONTAL_PADDING)
                },
            ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(
                    if (isTv) {
                        Modifier.padding(
                            start = TV_CATEGORY_PARENT_HORIZONTAL_INSET_DP.dp,
                            top = tvSafeInsets.verticalDp.dp,
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            Column {
                Text(
                    FAVORITES_TITLE,
                    color = colors.text,
                    fontSize = if (isTv) 27.sp else MOBILE_SECTION_TITLE_SIZE,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    FAVORITES_SUBTITLE,
                    color = colors.textMuted,
                    fontSize = if (isTv) 11.sp else MOBILE_SECTION_COUNT_SIZE,
                )
                Text(
                    "${content.size} ${favoritesCountUnit(activeFilter)}",
                    color = colors.textMuted,
                    fontSize = if (isTv) 11.sp else MOBILE_SECTION_COUNT_SIZE,
                )
            }
            Spacer(Modifier.height(6.dp))
            FavoritesTypeFilterBar(
                selected = activeFilter,
                onSelect = { filter ->
                    if (filter != activeFilter) {
                        activeFilter = filter
                        navigationMemory.save(
                            MainDestination.FAVORITES,
                            itemKey = "",
                            itemIndex = 0,
                            rowKey = filter.memoryKey,
                        )
                    }
                },
                isTv = isTv,
                filterRequesters = filterRequesters,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                content.isNotEmpty() -> Column(Modifier.fillMaxSize()) {
                    // Cached cards stay usable on a recoverable failure; one bounded notice only.
                    if (errorMessage != null) {
                        MoviesErrorNotice(
                            title = errorCopy.title,
                            body = errorCopy.body,
                            onRetry = onRefresh,
                            isTv = isTv,
                            networkFailure = offline,
                        )
                    }
                    TvCatalogGrid(
                        content = content,
                        contentKeys = contentKeys,
                        contentKeyIndex = contentKeyIndex,
                        destination = MainDestination.FAVORITES,
                        navigationMemory = navigationMemory,
                        isFavorite = isFavorite,
                        onOpen = onOpen,
                        onToggleFavorite = onToggleFavorite,
                        restoreFocusedCard = true,
                        memoryRowKey = activeFilter.memoryKey,
                        onFavoritesRemovalStarted = onFavoritesRemovalStarted,
                    )
                }
                resolving -> LoadingRing(
                    label = "جاري تجهيز المفضلة",
                    modifier = Modifier.align(Alignment.Center),
                )
                errorMessage != null -> MoviesOfflineEmptyState(
                    onRetry = onRefresh,
                    modifier = Modifier.align(Alignment.Center),
                    title = if (offline) {
                        "لا يوجد اتصال بالانترنت ، تحقق من اتصالك وحاول مرة اخرى"
                    } else {
                        "تعذر تحميل المفضلة ، حاول مرة اخرى"
                    },
                    body = null,
                    networkFailure = offline,
                )
                allContent.isEmpty() -> FavoritesEmptyState("لم تضف اي محتوى الى المفضلة بعد")
                else -> FavoritesFilteredEmptyState(favoritesTypeEmptyMessage(activeFilter))
            }
        }
    }
}

@Composable
private fun UnifiedSearchScreen(
    state: HulkUiState,
    isTv: Boolean,
    navigationMemory: NavigationMemoryStore,
    isFavorite: (ContentItem) -> Boolean,
    onSearch: (String) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
) {
    val colors = LocalHulkColors.current
    val searchFieldRequester = remember { FocusRequester() }
    val firstResultRequester = remember { FocusRequester() }
    val results = remember(state.catalogs, state.searchQuery) {
        val query = state.searchQuery.trim()
        if (query.isBlank()) emptyList() else state.catalogs.values.flatMap { it.items }
            .filter { it.matchesSearch(query) }
            .distinctBy { "${it.type}:${it.id}" }
    }
    val adaptiveUi = LocalAdaptiveUi.current
    val tvSafeInsets = remember(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp) {
        tvPageSafeInsets(
            screenWidthDp = adaptiveUi.screenWidthDp,
            screenHeightDp = adaptiveUi.screenHeightDp,
        )
    }
    Column(
        Modifier
            .fillMaxSize()
            .then(if (isTv) Modifier else Modifier.padding(horizontal = MOBILE_SECTION_HORIZONTAL_PADDING)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(
                    if (isTv) {
                        Modifier.padding(horizontal = 14.dp, top = tvSafeInsets.verticalDp.dp)
                    } else {
                        Modifier
                    },
                ),
        ) {
            PageTitle("البحث", "القنوات والافلام والمسلسلات", results.size, Icons.Rounded.Search, isTv)
            Spacer(Modifier.height(14.dp))
            TvSearchField(
                value = state.searchQuery,
                onValueChange = onSearch,
                isTv = isTv,
                hasResults = results.isNotEmpty(),
                fieldRequester = searchFieldRequester,
                firstResultRequester = firstResultRequester,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }
        if (state.searchQuery.isBlank()) {
            EmptyState("ابدا بكتابة الاسم او السنة او النوع او وصف المحتوى")
        } else if (results.isEmpty()) {
            EmptyState("لا توجد نتائج مطابقة")
        } else {
            Text(
                "${results.size} نتيجة",
                color = colors.textMuted,
                fontSize = 11.sp,
                modifier = if (isTv) Modifier.padding(horizontal = 14.dp) else Modifier,
            )
            Spacer(Modifier.height(9.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ContentGrid(
                    content = results,
                    isTv = isTv,
                    destination = MainDestination.SEARCH,
                    navigationMemory = navigationMemory,
                    isFavorite = isFavorite,
                    onOpen = onOpen,
                    onToggleFavorite = onToggleFavorite,
                    firstItemFocusRequester = if (isTv) firstResultRequester else null,
                    firstItemUpRequester = if (isTv) searchFieldRequester else null,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TvSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    isTv: Boolean,
    hasResults: Boolean,
    fieldRequester: FocusRequester,
    firstResultRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    var tvSearchEditing by remember { mutableStateOf(false) }
    val moveToResults: () -> Boolean = {
        if (!isTv || !hasResults) {
            false
        } else {
            tvSearchEditing = false
            keyboardController?.hide()
            runCatching { firstResultRequester.requestFocus() }.isSuccess
        }
    }

    LaunchedEffect(isTv) {
        if (isTv) {
            delay(140L)
            runCatching { fieldRequester.requestFocus() }
        }
    }
    LaunchedEffect(isTv, tvSearchEditing) {
        if (isTv) {
            if (tvSearchEditing) keyboardController?.show() else keyboardController?.hide()
        }
    }

    val tvModifier = if (isTv) {
        Modifier
            .focusRequester(fieldRequester)
            .onFocusChanged { focusState ->
                if (!focusState.isFocused) {
                    tvSearchEditing = false
                    keyboardController?.hide()
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else if (!tvSearchEditing && (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                    tvSearchEditing = true
                    true
                } else {
                    when (tvSearchFocusAction(true, event.type, event.key, hasResults, imeVisible)) {
                        TvSearchFocusAction.MOVE_TO_RESULTS -> moveToResults()
                        TvSearchFocusAction.DISMISS_KEYBOARD -> {
                            tvSearchEditing = false
                            keyboardController?.hide()
                            true
                        }
                        TvSearchFocusAction.NONE -> false
                    }
                }
            }
    } else {
        Modifier
    }

    HulkTextField(
        value = value,
        onValueChange = onValueChange,
        label = "ابحث بالاسم او السنة او النوع…",
        modifier = modifier.then(tvModifier),
        readOnly = isTv && !tvSearchEditing,
        keyboardOptions = if (isTv) {
            KeyboardOptions(imeAction = ImeAction.Search)
        } else {
            KeyboardOptions.Default
        },
        keyboardActions = if (isTv) {
            KeyboardActions(onSearch = { moveToResults() })
        } else {
            KeyboardActions.Default
        },
    )
}

internal fun downloadStorageObservationKey(
    downloads: List<OfflineDownload>,
): List<Pair<Long, Long>> = downloads.map { item ->
    item.downloadId to item.bytesDownloaded
}

internal suspend fun readAvailableDownloadStorageBytes(
    storageRootProvider: () -> java.io.File,
    statFsAvailableBytes: (String) -> Long = { path -> StatFs(path).availableBytes },
    usableSpaceBytes: (java.io.File) -> Long = { root -> root.usableSpace },
): Long? = withContext(Dispatchers.IO) {
    // Both reads failing means the real available space is unknown; it must stay unknown instead
    // of collapsing into a fabricated 0 MB.
    try {
        val storageRoot = storageRootProvider()
        try {
            statFsAvailableBytes(storageRoot.absolutePath).coerceAtLeast(0L)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            try {
                usableSpaceBytes(storageRoot).coerceAtLeast(0L)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadsScreen(
    downloads: List<OfflineDownload>,
    settings: DownloadSettings,
    isTv: Boolean,
    navigationMemory: NavigationMemoryStore,
    onPlay: (OfflineDownload) -> Unit,
    onDelete: (OfflineDownload) -> Unit,
    onDownloadPrimaryAction: (OfflineDownload, DownloadActionIntent) -> Unit,
    onToggleWifiOnly: () -> Unit,
    onToggleSchedule: () -> Unit,
    onCycleConcurrent: () -> Unit,
    onCyclePriority: (OfflineDownload) -> Unit,
    exitFocusRequester: FocusRequester?,
) {
    val active = downloads.count {
        it.status == OfflineStatus.QUEUED ||
            it.status == OfflineStatus.CHECKING ||
            it.status == OfflineStatus.DOWNLOADING ||
            it.status == OfflineStatus.PAUSED ||
            it.status == OfflineStatus.WAITING_SCHEDULE ||
            it.status == OfflineStatus.WAITING_NETWORK ||
            it.status == OfflineStatus.WAITING_STORAGE
    }
    val downloadIds = remember(downloads) { downloads.map(OfflineDownload::downloadId) }
    val downloadIdSet = remember(downloadIds) { downloadIds.toSet() }
    val downloadIndexById = remember(downloadIds) {
        downloadIds.withIndex().associate { (index, id) -> id to index }
    }
    val remembered = navigationMemory.position(MainDestination.DOWNLOADS)
    val rememberedIndex = (
        remembered.itemKey.toLongOrNull()?.let(downloadIndexById::get) ?: remembered.itemIndex
    ).coerceIn(0, downloads.lastIndex.coerceAtLeast(0))
    val downloadsFocusScope = rememberCoroutineScope()
    val toolbarFocus = remember { DownloadToolbarFocusRequesters() }
    val toolbarFocusHandles = remember(toolbarFocus) {
        DownloadToolbarFocusHandles(
            wifi = DownloadFocusHandle(toolbarFocus.wifi),
            schedule = DownloadFocusHandle(toolbarFocus.schedule),
            concurrent = DownloadFocusHandle(toolbarFocus.concurrent),
        )
    }
    val cardFocusRegistry = remember { mutableMapOf<Long, DownloadCardFocusRequesters>() }
    downloads.forEach { item ->
        cardFocusRegistry.getOrPut(item.downloadId) { DownloadCardFocusRequesters() }
    }
    SideEffect {
        cardFocusRegistry.keys.retainAll(downloadIdSet)
    }

    TrackDownloadFocusHandle(toolbarFocusHandles.wifi, isTv)
    TrackDownloadFocusHandle(toolbarFocusHandles.schedule, isTv)
    TrackDownloadFocusHandle(toolbarFocusHandles.concurrent, isTv)

    val appContext = LocalContext.current.applicationContext
    val storageObservationKey = remember(downloads) {
        downloadStorageObservationKey(downloads)
    }
    var availableBytes by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(appContext, storageObservationKey) {
        availableBytes = readAvailableDownloadStorageBytes(
            storageRootProvider = {
                appContext.getExternalFilesDir(null) ?: appContext.filesDir
            },
        )
    }
    val adaptiveUi = LocalAdaptiveUi.current
    val tvSafeInsets = remember(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp) {
        tvPageSafeInsets(
            screenWidthDp = adaptiveUi.screenWidthDp,
            screenHeightDp = adaptiveUi.screenHeightDp,
        )
    }
    val initialDownloadId = downloadIds.getOrNull(rememberedIndex)
    val initialFocusHandle = initialDownloadId
        ?.let(cardFocusRegistry::get)
        ?.primary
        ?: toolbarFocusHandles.wifi

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .then(if (isTv) Modifier else Modifier.padding(horizontal = MOBILE_SECTION_HORIZONTAL_PADDING))
            .then(
                if (isTv) {
                    Modifier
                        .focusRestorer(initialFocusHandle.requester)
                        .focusGroup()
                } else {
                    Modifier
                },
            ),
    ) {
        val compactHeight = maxHeight < if (isTv) 560.dp else 520.dp
        val horizontalInset = if (isTv) tvSafeInsets.horizontalDp.dp else 0.dp
        val verticalInset = if (isTv) tvSafeInsets.verticalDp.dp else 0.dp
        val availableContentWidth = (maxWidth - horizontalInset - horizontalInset).coerceAtLeast(1.dp)
        val gridGap = if (isTv) 14.dp else 10.dp
        // One full-width row per stable downloadId on every window; the grid stays single-column.
        val columnCount = 1
        val graph = remember(downloadIds, columnCount) {
            DownloadTvFocusGraph(downloadIds, columnCount)
        }
        val initialGridIndex = rememberedIndex - (rememberedIndex % graph.columns)
        val downloadsState = rememberLazyGridState(
            initialFirstVisibleItemIndex = initialGridIndex,
        )
        val inheritedBringIntoViewSpec = LocalBringIntoViewSpec.current
        val downloadsBringIntoViewSpec = remember {
            // The Downloads D-pad transaction is the sole vertical scroll owner on TV.
            object : BringIntoViewSpec {
                override fun calculateScrollDistance(
                    offset: Float,
                    size: Float,
                    containerSize: Float,
                ): Float = 0f
            }
        }
        val gridBringIntoViewSpec = if (isTv) {
            downloadsBringIntoViewSpec
        } else {
            inheritedBringIntoViewSpec
        }
        // Measured once from the real captions at the current font scale so every TV row's
        // primary button keeps one identical width and complete captions.
        val actionCaptionStyle = LocalTextStyle.current.copy(
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val tvPrimaryActionWidth = remember(textMeasurer, density, actionCaptionStyle) {
            val widestCaptionPx = DownloadTvRowAction.entries.maxOf { action ->
                textMeasurer.measure(
                    text = AnnotatedString(downloadTvRowActionCaption(action)),
                    style = actionCaptionStyle,
                    maxLines = 1,
                ).size.width
            }
            with(density) {
                (
                    movieActionRequiredWidthPx(
                        captionWidthPx = widestCaptionPx,
                        iconSizePx = 17.dp.roundToPx(),
                        horizontalPaddingPx = 12.dp.roundToPx(),
                        gapPx = 6.dp.roundToPx(),
                    ) + 6.dp.roundToPx()
                    ).toDp()
            }
        }
        // The accepted wide row is used on TV and on a non-TV window only when its measured
        // columns actually fit; narrower windows get the portrait card.
        val useHorizontalDownloadRow = downloadListUsesHorizontalRow(
            isTv = isTv,
            availableWidthDp = availableContentWidth.value,
            primaryActionWidthDp = tvPrimaryActionWidth.value,
        )
        val focusTransaction = remember { DownloadFocusMoveTransaction() }
        val focusHistory = remember { DownloadFocusHistory(graph) }
        var focusedTarget by remember { mutableStateOf<DownloadTvFocusTarget?>(null) }

        fun focusHandleFor(target: DownloadTvFocusTarget): DownloadFocusHandle? = when (target) {
            is DownloadTvFocusTarget.Toolbar -> when (target.slot) {
                DownloadFocusSlot.WIFI -> toolbarFocusHandles.wifi
                DownloadFocusSlot.SCHEDULE -> toolbarFocusHandles.schedule
                DownloadFocusSlot.CONCURRENT -> toolbarFocusHandles.concurrent
                else -> null
            }
            is DownloadTvFocusTarget.CardAction -> {
                cardFocusRegistry[target.downloadId]?.let { requesters ->
                    when (target.slot) {
                        DownloadFocusSlot.PRIMARY -> requesters.primary
                        DownloadFocusSlot.PRIORITY -> requesters.priority
                        DownloadFocusSlot.CANCEL -> requesters.cancel
                        else -> null
                    }
                }
            }
        }

        fun requestAttachedFocus(target: DownloadTvFocusTarget): Boolean {
            val handle = focusHandleFor(target) ?: return false
            if (!handle.isPlaced) return false
            return runCatching { handle.requester.requestFocus() }.getOrDefault(false)
        }

        fun isCardFullyVisible(downloadId: Long): Boolean {
            val layoutInfo = downloadsState.layoutInfo
            val item = layoutInfo.visibleItemsInfo.firstOrNull { it.key == downloadId }
                ?: return false
            // Lazy viewport offsets may include content padding; the clipped screen bounds do not.
            val visibleTop = maxOf(0, layoutInfo.viewportStartOffset)
            val visibleBottom = minOf(
                layoutInfo.viewportSize.height,
                layoutInfo.viewportEndOffset,
            )
            return item.offset.y >= visibleTop &&
                item.offset.y + item.size.height <= visibleBottom
        }

        suspend fun requestFocusAfterTargetPlacement(
            target: DownloadTvFocusTarget,
            moveGraph: DownloadTvFocusGraph,
            composeOffscreenCard: Boolean,
        ): Boolean {
            val handle = focusHandleFor(target) ?: return false
            val cardTarget = target as? DownloadTvFocusTarget.CardAction
            if (composeOffscreenCard && cardTarget != null) {
                val cardIndex = moveGraph.indexById[cardTarget.downloadId] ?: return false
                // Snap the complete target row to the viewport instead of aligning one lane.
                val targetRowStart = cardIndex - (cardIndex % moveGraph.columns)
                downloadsState.scrollToItem(targetRowStart, scrollOffset = 0)
            }
            if (!handle.isPlaced) withFrameNanos { }
            if (!handle.isPlaced) return false
            if (cardTarget != null) {
                val targetStillExists = cardTarget.downloadId in moveGraph.indexById
                val targetIsVisible = isCardFullyVisible(cardTarget.downloadId)
                if (!targetStillExists || !targetIsVisible || !handle.isPlaced) return false
            }
            return runCatching { handle.requester.requestFocus() }.getOrDefault(false)
        }

        fun startFocusTransaction(
            target: DownloadTvFocusTarget,
            moveGraph: DownloadTvFocusGraph,
            composeOffscreenCard: Boolean,
            onSettled: ((Boolean) -> Unit)? = null,
        ): Boolean {
            if (focusTransaction.isActive || focusHandleFor(target) == null) {
                onSettled?.invoke(false)
                return false
            }
            lateinit var launchedJob: Job
            launchedJob = downloadsFocusScope.launch(start = CoroutineStart.LAZY) {
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
                    }
                    onSettled?.invoke(focusRequested)
                }
            }
            focusTransaction.job = launchedJob
            launchedJob.start()
            return true
        }

        fun requestFocusMove(
            target: DownloadTvFocusTarget,
            moveGraph: DownloadTvFocusGraph,
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
            val targetCard = target as? DownloadTvFocusTarget.CardAction
            val targetCardVisible = targetCard == null || isCardFullyVisible(targetCard.downloadId)
            if (targetCardVisible && handle.isPlaced) {
                val focusRequested = requestAttachedFocus(target)
                onSettled?.invoke(focusRequested)
                return focusRequested
            }
            return startFocusTransaction(
                target = target,
                moveGraph = moveGraph,
                composeOffscreenCard = targetCard != null && !targetCardVisible,
                onSettled = onSettled,
            )
        }

        fun handleDirection(
            current: DownloadTvFocusTarget,
            move: DownloadFocusMove,
        ): Boolean {
            if (focusTransaction.isActive) return true
            val target = nextDownloadTvFocus(graph, current, move)
            if (target == null) {
                val exitsTowardSidebar = move == DownloadFocusMove.RIGHT && when (current) {
                    is DownloadTvFocusTarget.Toolbar -> current.slot == DownloadFocusSlot.WIFI
                    is DownloadTvFocusTarget.CardAction -> current.slot == DownloadFocusSlot.PRIMARY
                }
                if (exitsTowardSidebar && exitFocusRequester != null) {
                    runCatching { exitFocusRequester.requestFocus() }
                }
                return true
            }
            requestFocusMove(target, graph)
            return true
        }

        fun recordFocusedTarget(target: DownloadTvFocusTarget) {
            if (focusedTarget == target) return
            focusedTarget = target
            if (target is DownloadTvFocusTarget.CardAction) {
                graph.indexById[target.downloadId]?.let { index ->
                    navigationMemory.save(
                        MainDestination.DOWNLOADS,
                        target.downloadId.toString(),
                        index,
                    )
                }
            }
        }

        fun deleteWithFocusTransfer(item: OfflineDownload) {
            if (!isTv) {
                onDelete(item)
                return
            }
            val current = DownloadTvFocusTarget.CardAction(
                item.downloadId,
                DownloadFocusSlot.CANCEL,
            )
            val graphAfterDelete = DownloadTvFocusGraph(
                graph.downloadIds.filterNot { it == item.downloadId },
                graph.columns,
            )
            val fallback = downloadFocusFallback(current, graph, graphAfterDelete)
            requestFocusMove(fallback, graph) { onDelete(item) }
        }

        LaunchedEffect(graph) {
            val runningTransaction = focusTransaction.job?.takeIf { it.isActive }
            runningTransaction?.cancelAndJoin()
            if (focusTransaction.job === runningTransaction) {
                focusTransaction.job = null
            }
            val previousGraph = focusHistory.graph
            focusHistory.graph = graph
            focusedTarget?.let { current ->
                val fallback = downloadFocusFallback(current, previousGraph, graph)
                if (fallback != current) requestFocusMove(fallback, graph)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = horizontalInset,
                    end = horizontalInset,
                    top = verticalInset,
                    bottom = if (isTv) verticalInset else 6.dp,
                ),
        ) {
            DownloadsHeader(
                settings = settings,
                isTv = isTv,
                compactHeight = compactHeight,
                itemCount = downloads.size,
                activeCount = active,
                availableBytes = availableBytes,
                wifiFocusModifier = Modifier.focusRequester(toolbarFocus.wifi),
                scheduleFocusModifier = Modifier.focusRequester(toolbarFocus.schedule),
                concurrentFocusModifier = Modifier.focusRequester(toolbarFocus.concurrent),
                focusHandles = toolbarFocusHandles,
                onFocused = { slot ->
                    recordFocusedTarget(DownloadTvFocusTarget.Toolbar(slot))
                },
                onDirection = { slot, move ->
                    handleDirection(DownloadTvFocusTarget.Toolbar(slot), move)
                },
                onToggleWifiOnly = onToggleWifiOnly,
                onToggleSchedule = onToggleSchedule,
                onCycleConcurrent = onCycleConcurrent,
            )
            if (downloads.isEmpty()) {
                DownloadsEmptyState(
                    isTv = isTv,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = if (compactHeight) 8.dp else 12.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = if (compactHeight) 8.dp else 12.dp)
                        // Keep lazy rows inside the grid viewport owned below the fixed header.
                        .clipToBounds(),
                ) {
                    CompositionLocalProvider(
                        LocalBringIntoViewSpec provides gridBringIntoViewSpec,
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(graph.columns),
                            state = downloadsState,
                            horizontalArrangement = Arrangement.spacedBy(gridGap),
                            verticalArrangement = Arrangement.spacedBy(gridGap),
                            contentPadding = PaddingValues(
                                start = if (isTv) 0.dp else 4.dp,
                                end = if (isTv) 0.dp else 4.dp,
                                bottom = if (isTv) 12.dp else 20.dp,
                            ),
                            modifier = Modifier.fillMaxSize().clipToBounds(),
                        ) {
                            itemsIndexed(downloads, key = { _, item -> item.downloadId }) { _, item ->
                                val requesters = checkNotNull(cardFocusRegistry[item.downloadId])
                                if (useHorizontalDownloadRow) {
                                    DownloadTvRow(
                                        item = item,
                                        isTv = isTv,
                                        primaryActionWidth = tvPrimaryActionWidth,
                                        focusRequesters = requesters,
                                        onFocused = { slot ->
                                            recordFocusedTarget(
                                                DownloadTvFocusTarget.CardAction(item.downloadId, slot),
                                            )
                                        },
                                        onDirection = { slot, move ->
                                            handleDirection(
                                                DownloadTvFocusTarget.CardAction(item.downloadId, slot),
                                                move,
                                            )
                                        },
                                        onPlay = onPlay,
                                        onDelete = ::deleteWithFocusTransfer,
                                        onDownloadPrimaryAction = onDownloadPrimaryAction,
                                        onCyclePriority = onCyclePriority,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    DownloadCard(
                                        item = item,
                                        focusRequesters = requesters,
                                        onFocused = { slot ->
                                            recordFocusedTarget(
                                                DownloadTvFocusTarget.CardAction(item.downloadId, slot),
                                            )
                                        },
                                        onDirection = { slot, move ->
                                            handleDirection(
                                                DownloadTvFocusTarget.CardAction(item.downloadId, slot),
                                                move,
                                            )
                                        },
                                        onPlay = onPlay,
                                        onDelete = ::deleteWithFocusTransfer,
                                        onDownloadPrimaryAction = onDownloadPrimaryAction,
                                        onCyclePriority = onCyclePriority,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadsHeader(
    settings: DownloadSettings,
    isTv: Boolean,
    compactHeight: Boolean,
    itemCount: Int,
    activeCount: Int,
    availableBytes: Long?,
    wifiFocusModifier: Modifier,
    scheduleFocusModifier: Modifier,
    concurrentFocusModifier: Modifier,
    focusHandles: DownloadToolbarFocusHandles,
    onFocused: (DownloadFocusSlot) -> Unit,
    onDirection: (DownloadFocusSlot, DownloadFocusMove) -> Boolean,
    onToggleWifiOnly: () -> Unit,
    onToggleSchedule: () -> Unit,
    onCycleConcurrent: () -> Unit,
) {
    val colors = LocalHulkColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isTv) TV_PAGE_GUTTER else 4.dp,
                vertical = if (compactHeight) 4.dp else 7.dp,
            ),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= if (isTv) 720.dp else 560.dp) {
                DownloadsTvHeaderTitle(
                    isTv = isTv,
                    itemCount = itemCount,
                    modifier = Modifier.align(Alignment.TopStart),
                )
                DownloadsTvSummary(
                    itemCount = itemCount,
                    activeCount = activeCount,
                    availableBytes = availableBytes,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            } else {
                Column {
                    DownloadsTvHeaderTitle(isTv = isTv, itemCount = itemCount)
                    Spacer(Modifier.height(if (compactHeight) 7.dp else 10.dp))
                    DownloadsTvSummary(
                        itemCount = itemCount,
                        activeCount = activeCount,
                        availableBytes = availableBytes,
                    )
                }
            }
        }
        Spacer(Modifier.height(if (compactHeight) 7.dp else 10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.focusGroup(),
        ) {
            val controlWidth = 112.dp
            val controlHeight = if (isTv) 38.dp else 44.dp
            FocusButton(
                if (settings.wifiOnly) "WiFi فقط" else "كل الشبكات",
                onToggleWifiOnly,
                primary = settings.wifiOnly,
                compact = true,
                outlined = !settings.wifiOnly,
                scaleOnFocus = false,
                textSizeSp = 11,
                trailingIcon = if (settings.wifiOnly) Icons.Rounded.Check else Icons.Rounded.Language,
                trailingIconTint = if (settings.wifiOnly) null else colors.gold,
                onFocused = { onFocused(DownloadFocusSlot.WIFI) },
                modifier = wifiFocusModifier
                    .widthIn(min = controlWidth)
                    .heightIn(min = controlHeight)
                    .applyDownloadTvFocusNode(
                        isTv = isTv,
                        handle = focusHandles.wifi,
                        attachRequester = false,
                    ) { move ->
                        onDirection(DownloadFocusSlot.WIFI, move)
                    },
            )
            FocusButton(
                if (settings.scheduleMode == DownloadScheduleMode.NIGHT) "الجدولة 02:00" else "الجدولة الان",
                onToggleSchedule,
                primary = settings.scheduleMode == DownloadScheduleMode.NIGHT,
                compact = true,
                outlined = settings.scheduleMode != DownloadScheduleMode.NIGHT,
                scaleOnFocus = false,
                textSizeSp = 11,
                trailingIcon = Icons.Rounded.Schedule,
                trailingIconTint = if (settings.scheduleMode == DownloadScheduleMode.NIGHT) null else colors.gold,
                onFocused = { onFocused(DownloadFocusSlot.SCHEDULE) },
                modifier = scheduleFocusModifier
                    .widthIn(min = controlWidth)
                    .heightIn(min = controlHeight)
                    .applyDownloadTvFocusNode(
                        isTv = isTv,
                        handle = focusHandles.schedule,
                        attachRequester = false,
                    ) { move ->
                        onDirection(DownloadFocusSlot.SCHEDULE, move)
                    },
            )
            FocusButton(
                "متزامنة ${settings.concurrentDownloads}",
                onCycleConcurrent,
                primary = false,
                compact = true,
                outlined = true,
                scaleOnFocus = false,
                textSizeSp = 11,
                trailingIcon = Icons.Rounded.Tune,
                trailingIconTint = colors.gold,
                onFocused = { onFocused(DownloadFocusSlot.CONCURRENT) },
                modifier = concurrentFocusModifier
                    .widthIn(min = controlWidth)
                    .heightIn(min = controlHeight)
                    .applyDownloadTvFocusNode(
                        isTv = isTv,
                        handle = focusHandles.concurrent,
                        attachRequester = false,
                    ) { move ->
                        onDirection(DownloadFocusSlot.CONCURRENT, move)
                    },
            )
        }
        Spacer(Modifier.height(if (compactHeight) 7.dp else 10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.line.copy(alpha = .38f)),
        )
    }
}

@Composable
private fun DownloadsTvHeaderTitle(
    isTv: Boolean,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    Column(modifier) {
        Text(
            "التنزيلات",
            color = colors.text,
            fontSize = if (isTv) 27.sp else MOBILE_SECTION_TITLE_SIZE,
            fontWeight = FontWeight.Bold,
        )
        Text(
            if (itemCount == 1) "1 عنصر" else "$itemCount عناصر",
            color = colors.textMuted,
            fontSize = if (isTv) 11.sp else MOBILE_SECTION_COUNT_SIZE,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadsTvSummary(
    itemCount: Int,
    activeCount: Int,
    availableBytes: Long?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        DownloadsTvSummaryEntry(
            icon = Icons.Rounded.Downloading,
            value = "$activeCount",
            label = "نشط",
            iconColor = colors.goldBright,
            valueColor = colors.goldBright,
        )
        DownloadsTvSummaryDivider()
        DownloadsTvSummaryEntry(
            icon = Icons.Rounded.VideoLibrary,
            value = "$itemCount",
            label = if (itemCount == 1) "عنصر" else "عناصر",
            iconColor = colors.gold,
            valueColor = colors.text,
        )
        DownloadsTvSummaryDivider()
        if (availableBytes != null) {
            DownloadsTvSummaryEntry(
                icon = Icons.Rounded.Storage,
                value = formatBytes(availableBytes),
                label = "متبقي",
                iconColor = colors.gold,
                valueColor = colors.text,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    "المساحة غير متاحة",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Icon(
                    Icons.Rounded.Storage,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun DownloadsTvSummaryEntry(
    icon: ImageVector,
    value: String,
    label: String,
    iconColor: Color,
    valueColor: Color,
) {
    val colors = LocalHulkColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = valueColor, fontWeight = FontWeight.Bold)) {
                    append(value)
                }
                append(" ")
                withStyle(SpanStyle(color = colors.textMuted)) {
                    append(label)
                }
            },
            fontSize = 11.sp,
            maxLines = 1,
        )
        Icon(
            icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun DownloadsTvSummaryDivider() {
    val colors = LocalHulkColors.current
    Box(
        Modifier
            .width(1.dp)
            .height(16.dp)
            .background(colors.line.copy(alpha = .42f)),
    )
}

@Composable
private fun DownloadsEmptyState(
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp).padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(if (isTv) 60.dp else 54.dp)
                    .clip(CircleShape)
                    .background(colors.gold.copy(alpha = .10f))
                    .border(1.dp, colors.gold.copy(alpha = .28f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Download,
                    contentDescription = null,
                    tint = colors.goldBright,
                    modifier = Modifier.size(if (isTv) 28.dp else 25.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "مكتبتك جاهزة للتنزيلات",
                color = colors.text,
                fontSize = if (isTv) 18.sp else 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "ستظهر هنا الافلام والحلقات المحفوظة للمشاهدة بدون انترنت",
                color = colors.textMuted,
                fontSize = if (isTv) 12.sp else 11.sp,
                lineHeight = if (isTv) 18.sp else 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DownloadCard(
    item: OfflineDownload,
    focusRequesters: DownloadCardFocusRequesters,
    onFocused: (DownloadFocusSlot) -> Unit,
    onDirection: (DownloadFocusSlot, DownloadFocusMove) -> Boolean,
    onPlay: (OfflineDownload) -> Unit,
    onDelete: (OfflineDownload) -> Unit,
    onDownloadPrimaryAction: (OfflineDownload, DownloadActionIntent) -> Unit,
    onCyclePriority: (OfflineDownload) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    TrackDownloadFocusHandle(focusRequesters.primary, false)
    TrackDownloadFocusHandle(focusRequesters.priority, false)
    TrackDownloadFocusHandle(focusRequesters.cancel, false)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface.copy(alpha = .92f))
            .border(1.dp, colors.line.copy(alpha = .34f), shape)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        // Portrait row: compact artwork physically RIGHT with the identity alongside it.
        Row(verticalAlignment = Alignment.Top) {
            DownloadTvRowArtwork(item = item, width = 112.dp)
            Spacer(Modifier.width(10.dp))
            DownloadTvRowDetails(item = item, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        DownloadTvRowStatus(item = item, isTv = false, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        DownloadTvRowActions(
            item = item,
            isTv = false,
            primaryActionWidth = 0.dp,
            focusRequesters = focusRequesters,
            onFocused = onFocused,
            onDirection = onDirection,
            onPlay = onPlay,
            onDelete = onDelete,
            onDownloadPrimaryAction = onDownloadPrimaryAction,
            onCyclePriority = onCyclePriority,
            fillPrimaryWidth = true,
        )
    }
}

@Composable
private fun DownloadTvRow(
    item: OfflineDownload,
    isTv: Boolean,
    primaryActionWidth: Dp,
    focusRequesters: DownloadCardFocusRequesters,
    onFocused: (DownloadFocusSlot) -> Unit,
    onDirection: (DownloadFocusSlot, DownloadFocusMove) -> Boolean,
    onPlay: (OfflineDownload) -> Unit,
    onDelete: (OfflineDownload) -> Unit,
    onDownloadPrimaryAction: (OfflineDownload, DownloadActionIntent) -> Unit,
    onCyclePriority: (OfflineDownload) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    TrackDownloadFocusHandle(focusRequesters.primary, isTv)
    TrackDownloadFocusHandle(focusRequesters.priority, isTv)
    TrackDownloadFocusHandle(focusRequesters.cancel, isTv)
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface.copy(alpha = .92f))
            .border(1.dp, colors.line.copy(alpha = .34f), shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Physical RIGHT-to-LEFT: artwork, then identity, then telemetry, then actions.
        DownloadTvRowArtwork(item = item, width = 160.dp)
        Spacer(Modifier.width(12.dp))
        DownloadTvRowDetails(item = item, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .width(1.dp)
                .height(56.dp)
                .background(colors.line.copy(alpha = .30f)),
        )
        Spacer(Modifier.width(12.dp))
        DownloadTvRowStatus(item = item, isTv = isTv, modifier = Modifier.width(228.dp))
        Spacer(Modifier.width(12.dp))
        DownloadTvRowActions(
            item = item,
            isTv = isTv,
            primaryActionWidth = primaryActionWidth,
            focusRequesters = focusRequesters,
            onFocused = onFocused,
            onDirection = onDirection,
            onPlay = onPlay,
            onDelete = onDelete,
            onDownloadPrimaryAction = onDownloadPrimaryAction,
            onCyclePriority = onCyclePriority,
        )
    }
}

@Composable
private fun DownloadTvRowArtwork(
    item: OfflineDownload,
    width: Dp,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .width(width)
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(colors.surfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        if (!item.posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            BrandLogo(
                Modifier
                    .fillMaxHeight(.58f)
                    .aspectRatio(1f)
                    .graphicsLayer { alpha = .56f },
            )
        }
    }
}

@Composable
private fun DownloadTvRowDetails(
    item: OfflineDownload,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val identity = downloadDisplayIdentity(item)
    Column(modifier) {
        Text(
            identity.title,
            color = colors.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 20.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(min = 40.dp),
        )
        if (identity.metadata.isNotEmpty()) {
            Text(
                identity.metadata,
                color = colors.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DownloadTvRowStatus(
    item: OfflineDownload,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val telemetry = downloadTvRowTelemetry(
        item = item,
        formatBytes = ::formatBytes,
        scheduledLabel = ::formatScheduledTime,
    )
    val statusColor = if (item.status == OfflineStatus.FAILED) colors.danger else colors.text
    val statusTextSize = if (isTv) 12.sp else 13.sp
    val detailTextSize = if (isTv) 10.sp else 12.sp
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                downloadTvRowStatusLabel(item.status),
                color = statusColor,
                fontSize = statusTextSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.status == OfflineStatus.WAITING_NETWORK) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Rounded.WifiOff,
                    contentDescription = null,
                    tint = colors.gold,
                    modifier = Modifier.size(if (isTv) 15.dp else 17.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            telemetry.percent?.let { percent ->
                Text(
                    "\u200E$percent%",
                    color = colors.goldBright,
                    fontSize = statusTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
        if (telemetry.showTrack) {
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = .13f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(item.progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(colors.gold),
                )
            }
        }
        telemetry.sizeLine?.let { sizeLine ->
            Spacer(Modifier.height(6.dp))
            Text(
                "\u200E$sizeLine",
                color = colors.textMuted,
                fontSize = detailTextSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        telemetry.detailLine?.let { detail ->
            Spacer(Modifier.height(4.dp))
            Text(
                detail,
                color = if (item.status == OfflineStatus.FAILED) colors.danger else colors.textMuted,
                fontSize = detailTextSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DownloadTvRowActions(
    item: OfflineDownload,
    isTv: Boolean,
    primaryActionWidth: Dp,
    focusRequesters: DownloadCardFocusRequesters,
    onFocused: (DownloadFocusSlot) -> Unit,
    onDirection: (DownloadFocusSlot, DownloadFocusMove) -> Boolean,
    onPlay: (OfflineDownload) -> Unit,
    onDelete: (OfflineDownload) -> Unit,
    onDownloadPrimaryAction: (OfflineDownload, DownloadActionIntent) -> Unit,
    onCyclePriority: (OfflineDownload) -> Unit,
    fillPrimaryWidth: Boolean = false,
) {
    val action = downloadTvRowAction(item.status)
    val actionHeight = if (isTv) 42.dp else 46.dp
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DownloadTvRowActionButton(
            label = downloadTvRowActionCaption(action),
            icon = when (action) {
                DownloadTvRowAction.PLAY,
                DownloadTvRowAction.RESUME,
                -> Icons.Rounded.PlayArrow
                DownloadTvRowAction.PAUSE -> Icons.Rounded.Pause
                DownloadTvRowAction.RETRY -> Icons.Rounded.Refresh
            },
            isTv = isTv,
            emphasized = true,
            onClick = {
                if (action == DownloadTvRowAction.PLAY) {
                    onPlay(item)
                } else {
                    downloadTvRowActionIntent(action)?.let { intent ->
                        onDownloadPrimaryAction(item, intent)
                    }
                }
            },
            onFocused = { onFocused(DownloadFocusSlot.PRIMARY) },
            modifier = (
                if (fillPrimaryWidth) {
                    Modifier.weight(1f)
                } else {
                    Modifier.width(primaryActionWidth)
                }
                ).height(actionHeight)
                .applyDownloadTvFocusNode(isTv = isTv, handle = focusRequesters.primary) { move ->
                    onDirection(DownloadFocusSlot.PRIMARY, move)
                },
        )
        DownloadTvRowActionButton(
            label = "الاولوية ${priorityLabel(item.priority)}",
            icon = Icons.Rounded.Tune,
            isTv = isTv,
            selected = item.priority != 0,
            onClick = { onCyclePriority(item) },
            onFocused = { onFocused(DownloadFocusSlot.PRIORITY) },
            modifier = Modifier
                .size(actionHeight)
                .applyDownloadTvFocusNode(isTv = isTv, handle = focusRequesters.priority) { move ->
                    onDirection(DownloadFocusSlot.PRIORITY, move)
                },
        )
        DownloadTvRowActionButton(
            label = if (item.status == OfflineStatus.COMPLETED) "حذف التنزيل" else "الغاء التنزيل",
            icon = Icons.Rounded.DeleteOutline,
            isTv = isTv,
            onClick = { onDelete(item) },
            onFocused = { onFocused(DownloadFocusSlot.CANCEL) },
            modifier = Modifier
                .size(actionHeight)
                .applyDownloadTvFocusNode(isTv = isTv, handle = focusRequesters.cancel) { move ->
                    onDirection(DownloadFocusSlot.CANCEL, move)
                },
        )
    }
}

@Composable
private fun DownloadTvRowActionButton(
    label: String,
    icon: ImageVector,
    isTv: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    selected: Boolean = false,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val background = when {
        emphasized -> colors.gold
        selected -> colors.gold.copy(alpha = .16f)
        else -> colors.surfaceRaised.copy(alpha = .92f)
    }
    val foreground = when {
        emphasized -> Color(0xFF100E04)
        selected -> colors.goldBright
        else -> colors.text
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .goldFocusEdge(shape = shape, visible = focused)
            .border(
                width = if (!emphasized && !focused) 1.dp else 0.dp,
                color = if (!emphasized) colors.line.copy(alpha = .48f) else Color.Transparent,
                shape = shape,
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = if (emphasized) 12.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (emphasized) {
            // Caption physically RIGHT with its single glyph immediately LEFT at one 6dp gap.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = label,
                    color = foreground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    icon,
                    contentDescription = null,
                    tint = foreground,
                    modifier = Modifier.size(if (isTv) 17.dp else 18.dp),
                )
            }
        } else {
            Icon(
                icon,
                contentDescription = label,
                tint = foreground,
                modifier = Modifier.size(if (isTv) 19.dp else 20.dp),
            )
        }
    }
}

private fun priorityLabel(priority: Int): String = when (priority) {
    1 -> "عالية"
    -1 -> "منخفضة"
    else -> "عادية"
}

private fun formatScheduledTime(epochMs: Long): String =
    SimpleDateFormat("EEE  HH:mm", Locale.forLanguageTag("ar-SA")).format(Date(epochMs))

private fun formatTransferRate(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0L) return "0 KB/ث"
    val kb = bytesPerSecond.toDouble() / 1024.0
    return if (kb >= 1024.0) {
        String.format(Locale.US, "%.1f MB/ث", kb / 1024.0)
    } else {
        String.format(Locale.US, "%.0f KB/ث", kb)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val megabytes = bytes.toDouble() / (1024.0 * 1024.0)
    return if (megabytes >= 1024.0) {
        String.format(Locale.US, "%.1f GB", megabytes / 1024.0)
    } else {
        String.format(Locale.US, "%.0f MB", megabytes)
    }
}

@Composable
private fun ContentGrid(
    content: List<ContentItem>,
    isTv: Boolean,
    destination: MainDestination,
    navigationMemory: NavigationMemoryStore,
    isFavorite: (ContentItem) -> Boolean,
    onOpen: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    restoreFocusedCard: Boolean = true,
    firstItemFocusRequester: FocusRequester? = null,
    firstItemUpRequester: FocusRequester? = null,
    preparedContentKeys: List<String>? = null,
    preparedContentKeyIndex: Map<String, Int>? = null,
) {
    val contentIdentity = preparedContentKeys ?: content
    val contentKeys = remember(contentIdentity) {
        preparedContentKeys ?: content.map { "${it.type}:${it.id}" }
    }
    require(contentKeys.size == content.size)
    val contentKeyIndex = remember(contentKeys, preparedContentKeyIndex) {
        preparedContentKeyIndex ?: indexContentKeys(contentKeys)
    }
    val remembered = navigationMemory.position(destination)
    val rememberedKeyIndex = contentKeyIndex[remembered.itemKey] ?: -1
    val targetIndex = if (destination == MainDestination.SEARCH) {
        0
    } else {
        (if (rememberedKeyIndex >= 0) rememberedKeyIndex else remembered.itemIndex)
            .coerceIn(0, content.lastIndex.coerceAtLeast(0))
    }
    val targetKey = contentKeys.getOrNull(targetIndex).orEmpty()
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = targetIndex)
    LaunchedEffect(gridState, content, destination) {
        snapshotFlow { gridState.firstVisibleItemIndex }.collect { index ->
            contentKeys.getOrNull(index)?.let { navigationMemory.save(destination, it, index) }
        }
    }
    val targetRequester = remember { FocusRequester() }
    LaunchedEffect(contentKeys, remembered.itemKey, destination, restoreFocusedCard) {
        if (destination == MainDestination.SEARCH) {
            if (content.isNotEmpty()) gridState.scrollToItem(0)
            navigationMemory.save(destination, contentKeys.firstOrNull().orEmpty(), 0)
        } else if (restoreFocusedCard && content.isNotEmpty()) {
            if (destination == MainDestination.FAVORITES && targetKey.isNotBlank() && targetKey != remembered.itemKey) {
                navigationMemory.save(destination, targetKey, targetIndex)
            }
            gridState.scrollToItem(targetIndex)
            delay(90)
            runCatching { targetRequester.requestFocus() }
        }
    }
    val horizontalGridPadding = if (
        isTv && (destination == MainDestination.FAVORITES || destination == MainDestination.SEARCH)
    ) 12.dp else 5.dp
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(if (isTv) 132.dp else 105.dp),
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 9.dp),
        verticalArrangement = Arrangement.spacedBy(if (isTv) 15.dp else 10.dp),
        contentPadding = PaddingValues(
            start = horizontalGridPadding,
            top = 5.dp,
            end = horizontalGridPadding,
            bottom = 28.dp,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(content, key = { index, _ -> contentKeys[index] }) { index, item ->
            val key = contentKeys[index]
            val restore = remembered.itemKey == key || index == targetIndex
            val cardModifier = Modifier
                .fillMaxWidth()
                .then(
                    if (index == 0 && firstItemFocusRequester != null) {
                        Modifier.focusRequester(firstItemFocusRequester)
                    } else {
                        Modifier.restoreFocus(restore, targetRequester)
                    },
                )
                .then(
                    if (index == 0 && firstItemUpRequester != null) {
                        Modifier.focusProperties { up = firstItemUpRequester }
                    } else {
                        Modifier
                    },
                )
            if (destination == MainDestination.MOVIES) {
                MoviesCatalogBoxedCard(
                    item = item,
                    isFavorite = isFavorite(item),
                    onClick = { onOpen(item) },
                    modifier = cardModifier,
                    onLongClick = { onToggleFavorite(item) },
                    onFocused = { navigationMemory.save(destination, key, index) },
                )
            } else if (destination == MainDestination.SERIES) {
                SeriesCatalogBoxedCard(
                    item = item,
                    isFavorite = isFavorite(item),
                    onClick = { onOpen(item) },
                    modifier = cardModifier,
                    onLongClick = { onToggleFavorite(item) },
                    onFocused = { navigationMemory.save(destination, key, index) },
                )
            } else {
                UniversalPosterCard(
                    item = item,
                    isFavorite = isFavorite(item),
                    onClick = { onOpen(item) },
                    modifier = cardModifier,
                    onLongClick = { onToggleFavorite(item) },
                    onFocused = { navigationMemory.save(destination, key, index) },
                )
            }
        }
    }
}


private class MovieHistoryFocusRevealState {
    var job: Job? = null
}

/**
 * Boxed Recent grid for Movies and Series. Uses the adopted catalog slot policy (the same shared
 * column calculation and TV safe physical-left end padding, or the mobile adaptive slot) together
 * with [BoxedHistoryCard] so Recent matches normal catalog card geometry. Series cards additionally
 * show the real season/episode identity. History keys/order, resume callbacks, focus restoration
 * and long-press removal semantics are preserved.
 */
@Composable
private fun BoxedHistoryGrid(
    destination: MainDestination,
    entries: List<HistoryEntry>,
    isTv: Boolean,
    navigationMemory: NavigationMemoryStore,
    onOpen: (HistoryEntry) -> Unit,
    focusFirstItemRequestId: Long = 0L,
    focusContentRequestId: Long = 0L,
    onMoveToCategories: (() -> Boolean)? = null,
    seriesCards: Boolean = false,
) {
    val adaptiveUi = LocalAdaptiveUi.current
    val metrics = remember(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp) {
        tvCatalogMetrics(
            screenWidthDp = adaptiveUi.screenWidthDp,
            screenHeightDp = adaptiveUi.screenHeightDp,
            boxedCards = true,
        )
    }
    val remembered = navigationMemory.position(destination)
    val targetIndex = if (focusFirstItemRequestId != 0L) {
        0
    } else {
        remembered.itemIndex.coerceIn(0, entries.lastIndex.coerceAtLeast(0))
    }
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = targetIndex)
    LaunchedEffect(gridState, entries) {
        snapshotFlow { gridState.firstVisibleItemIndex }.collect { index ->
            entries.getOrNull(index)?.let { navigationMemory.save(destination, it.key, index) }
        }
    }
    val targetRequester = remember { FocusRequester() }
    LaunchedEffect(entries, remembered.itemKey, focusFirstItemRequestId, focusContentRequestId) {
        val shouldRestore = focusContentRequestId != 0L || remembered.itemKey.isNotBlank()
        if (shouldRestore && entries.isNotEmpty()) {
            gridState.scrollToItem(targetIndex)
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex } }
                .first { it }
            withFrameNanos { }
            runCatching { targetRequester.requestFocus() }
        }
    }
    val focusRevealScope = rememberCoroutineScope()
    val focusRevealState = remember { MovieHistoryFocusRevealState() }
    var focusedEntryIndex by remember(entries) { mutableStateOf(-1) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val focusInsetPx = with(density) { metrics.focusViewportInsetDp.dp.roundToPx() }
        val safeBottomInsetPx = with(density) { metrics.focusSafeBottomInsetDp.dp.roundToPx() }
        val horizontalSpacing = metrics.horizontalSpacingDp.dp
        val verticalSpacing = metrics.verticalSpacingDp.dp
        val horizontalContentPadding = metrics.horizontalContentPaddingDp.dp
        val focusSafeEndPadding = metrics.endContentPaddingDp.dp
        val bottomContentPadding = metrics.bottomContentPaddingDp.dp
        val columnCount = if (isTv) {
            val availableGridWidth = (
                maxWidth - horizontalContentPadding - focusSafeEndPadding
            ).coerceAtLeast(metrics.minCellWidthDp.dp)
            movieCatalogColumnCount(
                availableWidthDp = availableGridWidth.value,
                spacingDp = horizontalSpacing.value,
                minCellWidthDp = metrics.minCellWidthDp,
            )
        } else {
            0
        }
        // Recent cards now share the catalog's measured footer/artwork contract so the complete
        // footer, progress and focus edge stay inside the real remaining viewport when the header
        // grows or shrinks.
        val cellWidth = if (isTv) {
            ((maxWidth - horizontalContentPadding - focusSafeEndPadding -
                horizontalSpacing * (columnCount - 1)) / columnCount).coerceAtLeast(1.dp)
        } else {
            0.dp
        }
        val usableGridHeightPx = with(density) {
            (maxHeight - horizontalContentPadding - safeBottomInsetPx.toDp()).coerceAtLeast(1.dp).roundToPx()
        }
        var boxedFooterHeightPx by remember(entries) { mutableStateOf(0) }
        val compactArtworkHeightPx = if (isTv && boxedFooterHeightPx > 0 && cellWidth > 0.dp) {
            movieCompactArtworkHeightPx(
                cellWidthPx = with(density) { cellWidth.roundToPx() },
                footerHeightPx = boxedFooterHeightPx,
                usableHeightPx = usableGridHeightPx,
                minArtworkHeightPx = with(density) { MOVIE_COMPACT_ARTWORK_MIN_HEIGHT_DP.dp.roundToPx() },
            )
        } else {
            null
        }
        // Re-evaluate the focused card's real bounds when the available viewport changes (header
        // notice appears/clears) without resetting identity or moving to the first item. The
        // focused index is read via rememberUpdatedState so focus moves do not duplicate the work.
        val latestFocusedEntryIndex by rememberUpdatedState(focusedEntryIndex)
        LaunchedEffect(maxHeight) {
            val index = latestFocusedEntryIndex
            if (isTv && index >= 0) {
                withFrameNanos { }
                revealFocusedGridItem(
                    gridState = gridState,
                    index = index,
                    focusInsetPx = focusInsetPx,
                    safeBottomInsetPx = safeBottomInsetPx,
                    extraMarginPx = focusInsetPx,
                )
            }
        }
        LazyVerticalGrid(
            state = gridState,
            columns = if (isTv) GridCells.Fixed(columnCount) else GridCells.Adaptive(105.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) horizontalSpacing else 9.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTv) verticalSpacing else 10.dp),
            contentPadding = if (isTv) {
                PaddingValues(
                    start = horizontalContentPadding,
                    top = horizontalContentPadding,
                    end = focusSafeEndPadding,
                    bottom = bottomContentPadding,
                )
            } else {
                PaddingValues(5.dp, 5.dp, 5.dp, 28.dp)
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
                val restore = if (focusFirstItemRequestId != 0L) {
                    index == 0
                } else {
                    remembered.itemKey == entry.key || (remembered.itemKey.isBlank() && index == targetIndex)
                }
                BoxedHistoryCard(
                    entry,
                    { onOpen(entry) },
                    Modifier
                        .fillMaxWidth()
                        .restoreFocus(restore, targetRequester)
                        .then(
                            if (isTv && onMoveToCategories != null) {
                                Modifier.onPreviewKeyEvent { event ->
                                    val row = gridState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.index == index }
                                        ?.row
                                    event.type == KeyEventType.KeyDown &&
                                        event.key == Key.DirectionUp &&
                                        row == 0 &&
                                        onMoveToCategories()
                                }
                            } else {
                                Modifier
                            },
                        ),
                    identityText = seriesHistoryIdentityText(entry).takeIf { seriesCards },
                    reserveIdentitySlot = seriesCards,
                    onFocused = {
                        focusedEntryIndex = index
                        navigationMemory.save(destination, entry.key, index)
                        if (isTv) {
                            // One bounded correction after focus settles, matching the catalog's
                            // single-owner movement instead of competing settling passes.
                            focusRevealState.job?.cancel()
                            focusRevealState.job = focusRevealScope.launch {
                                withFrameNanos { }
                                revealFocusedGridItem(
                                    gridState = gridState,
                                    index = index,
                                    focusInsetPx = focusInsetPx,
                                    safeBottomInsetPx = safeBottomInsetPx,
                                    extraMarginPx = focusInsetPx,
                                )
                            }
                        }
                    },
                    artworkHeightDp = compactArtworkHeightPx?.let { heightPx ->
                        with(density) { heightPx.toDp() }
                    },
                    onFooterHeightMeasured = { boxedFooterHeightPx = it },
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsCenter(
    state: DiagnosticsState,
    isTv: Boolean,
    onRun: () -> Unit,
    onShare: (ServerDiagnosticsReport) -> Unit,
    topRequester: FocusRequester,
) {
    val colors = LocalHulkColors.current
    val report = state.report
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF11120E))
            .border(1.dp, colors.gold.copy(alpha = .28f), RoundedCornerShape(20.dp))
            .padding(if (isTv) 20.dp else 15.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("غرفة العمليات الهندسية V3", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "فحص حقيقي لقدرات السيرفر والشبكة والجهاز وبناء خريطة مميزات قابلة للتنفيذ",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                )
            }
            FocusButton(
                text = when {
                    state.isRunning -> "الفحص يعمل ${state.progress}%"
                    report != null -> "اعادة الفحص"
                    else -> "بدء الفحص الشامل"
                },
                onClick = onRun,
                enabled = !state.isRunning,
                compact = true,
                modifier = Modifier.focusRequester(topRequester),
            )
        }

        if (state.isRunning) {
            DiagnosticsProgress(state.progress, state.stage)
        }
        state.errorMessage?.let { message ->
            Text(message, color = Color(0xFFFF8A80), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }

        if (report == null && !state.isRunning) {
            Text(
                "الفحص يختبر واجهات Xtream وEPG وCatch-up وعينات HLS وTS ودعم استكمال التحميل وجودة البيانات وقدرات فك الترميز، بدون تشغيل المكتبة كاملة او كشف بيانات الدخول.",
                color = colors.textMuted,
                fontSize = 12.sp,
                lineHeight = 19.sp,
            )
        }

        report?.let { value ->
            DiagnosticsSummary(value, isTv)
            DiagnosticsSectionTitle("مصفوفة القدرات", "تصنيف هندسي يفصل API والبث والجهاز والشبكة بدون معاقبة HTTP او الاختبارات غير الحاسمة")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                value.capabilities.forEach { CapabilityFindingRow(it) }
            }

            DiagnosticsSectionTitle(
                "المشاكل والملاحظات",
                if (value.issues.isEmpty()) "لم يسجل الفحص مشاكل مؤثرة" else "${value.issues.size} ملاحظة تحتاج مراجعة",
            )
            if (value.issues.isEmpty()) {
                Text("كل الفحوصات الاساسية سليمة في هذه الجولة.", color = Color(0xFF8ED39A), fontSize = 12.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    value.issues.forEach { DiagnosticIssueRow(it) }
                }
            }

            DiagnosticsSectionTitle("خريطة تطوير المنصة", "مرتبة حسب الجاهزية والاثر المتوقع")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                value.recommendations.forEachIndexed { index, recommendation ->
                    FeatureRecommendationRow(index + 1, recommendation)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "اخر فحص: ${SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale("ar")).format(Date(value.generatedAtEpochMs))}",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                )
                FocusButton("مشاركة التقرير الامن", { onShare(value) }, primary = false, compact = true)
            }
            Spacer(Modifier.height(if (isTv) 34.dp else 22.dp))
        }
    }
}

@Composable
private fun DiagnosticsProgress(progress: Int, stage: String) {
    val colors = LocalHulkColors.current
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stage, color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("$progress%", color = colors.goldBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = .08f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((progress.coerceIn(0, 100) / 100f).coerceAtLeast(.01f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colors.goldBright),
            )
        }
    }
}

@Composable
private fun DiagnosticsSummary(report: ServerDiagnosticsReport, isTv: Boolean) {
    val colors = LocalHulkColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            DiagnosticMetric("النتيجة", "${report.overallScore}/100", report.overallStatus, Modifier.weight(1f))
            DiagnosticMetric("متوسط API", "${report.averageApiLatencyMs} ms", report.portalHost, Modifier.weight(1f))
            DiagnosticMetric(
                "افضل عينة",
                String.format(Locale.US, "%.2f Mbps", report.bestSampleThroughputMbps),
                report.networkSummary,
                Modifier.weight(1f),
            )
            if (isTv) {
                DiagnosticMetric("المساحة", formatBytes(report.availableStorageBytes), report.deviceSummary, Modifier.weight(1f))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            DiagnosticMetric("القنوات", report.liveCount.toString(), "من السيرفر", Modifier.weight(1f))
            DiagnosticMetric("الافلام", report.movieCount.toString(), "من السيرفر", Modifier.weight(1f))
            DiagnosticMetric("المسلسلات", report.seriesCount.toString(), "من السيرفر", Modifier.weight(1f))
            DiagnosticMetric("الفئات", report.categoryCount.toString(), "اجمالي الفئات", Modifier.weight(1f))
        }
    }
}

@Composable
private fun DiagnosticMetric(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    val colors = LocalHulkColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(Color(0xFF181914))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Text(label, color = colors.textMuted, fontSize = 9.sp, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(value, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, color = colors.textMuted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DiagnosticsSectionTitle(title: String, subtitle: String) {
    val colors = LocalHulkColors.current
    Column {
        Text(title, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = colors.textMuted, fontSize = 10.sp)
    }
}

@Composable
private fun CapabilityFindingRow(finding: CapabilityFinding) {
    val colors = LocalHulkColors.current
    val accent = when (finding.status) {
        CapabilityStatus.SUPPORTED -> Color(0xFF8ED39A)
        CapabilityStatus.PARTIAL -> colors.goldBright
        CapabilityStatus.UNSUPPORTED -> Color(0xFFFF8A80)
        CapabilityStatus.UNSTABLE -> Color(0xFFFFB266)
    }
    val statusText = when (finding.status) {
        CapabilityStatus.SUPPORTED -> "مدعومة"
        CapabilityStatus.PARTIAL -> "جزئية"
        CapabilityStatus.UNSUPPORTED -> "غير مدعومة"
        CapabilityStatus.UNSTABLE -> "غير مستقرة"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Color(0xFF181914))
            .focusable()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
        Column(Modifier.weight(1f)) {
            Text(finding.title, color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(finding.details, color = colors.textMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(finding.evidence, color = accent.copy(alpha = .82f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(statusText, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DiagnosticIssueRow(issue: DiagnosticIssue) {
    val colors = LocalHulkColors.current
    val accent = when (issue.severity) {
        DiagnosticSeverity.INFO -> colors.goldBright
        DiagnosticSeverity.WARNING -> Color(0xFFFFB266)
        DiagnosticSeverity.CRITICAL -> Color(0xFFFF8A80)
    }
    val severity = when (issue.severity) {
        DiagnosticSeverity.INFO -> "معلومة"
        DiagnosticSeverity.WARNING -> "تحذير"
        DiagnosticSeverity.CRITICAL -> "مشكلة"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(accent.copy(alpha = .08f))
            .border(1.dp, accent.copy(alpha = .28f), RoundedCornerShape(13.dp))
            .focusable()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(issue.title, color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(severity, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Text(issue.details, color = colors.textMuted, fontSize = 10.sp)
        Text("الاجراء: ${issue.action}", color = accent.copy(alpha = .9f), fontSize = 10.sp)
    }
}

@Composable
private fun FeatureRecommendationRow(index: Int, recommendation: FeatureRecommendation) {
    val colors = LocalHulkColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Color(0xFF181914))
            .focusable()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape).background(colors.gold.copy(alpha = .18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(index.toString(), color = colors.goldBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(recommendation.title, color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(recommendation.reason, color = colors.textMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(recommendation.readiness, color = colors.goldBright, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 2)
    }
}

private fun shareDiagnosticsReport(context: android.content.Context, report: ServerDiagnosticsReport) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "تقرير تشخيص HULK SA")
        putExtra(Intent.EXTRA_TEXT, diagnosticsReportText(report))
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "مشاركة تقرير التشخيص")) }
}

private fun diagnosticsReportText(report: ServerDiagnosticsReport): String = buildString {
    appendLine("تقرير فحص HULK SA")
    appendLine("النتيجة: ${report.overallScore}/100 - ${report.overallStatus}")
    appendLine("السيرفر: ${report.portalScheme}://${report.portalHost}")
    appendLine("الشبكة: ${report.networkSummary}")
    appendLine("الجهاز: ${report.deviceSummary}")
    appendLine("متوسط API: ${report.averageApiLatencyMs} ms")
    appendLine("افضل عينة: ${String.format(Locale.US, "%.2f Mbps", report.bestSampleThroughputMbps)}")
    appendLine("المحتوى: ${report.liveCount} قناة، ${report.movieCount} فيلم، ${report.seriesCount} مسلسل")
    appendLine()
    appendLine("القدرات:")
    report.capabilities.forEach { finding ->
        appendLine("- ${finding.title}: ${finding.status.name} | ${finding.details} | ${finding.evidence}")
    }
    appendLine()
    appendLine("الملاحظات:")
    if (report.issues.isEmpty()) appendLine("- لا توجد مشاكل مؤثرة")
    report.issues.forEach { issue ->
        appendLine("- ${issue.title}: ${issue.details} | الاجراء: ${issue.action}")
    }
    appendLine()
    appendLine("خريطة التطوير:")
    report.recommendations.forEachIndexed { index, item ->
        appendLine("${index + 1}. ${item.title} - ${item.readiness}: ${item.reason}")
    }
    appendLine()
    appendLine("ملاحظة: التقرير لا يحتوي اسم المستخدم او كلمة المرور او روابط البث الخاصة.")
}

@Composable
private fun CatalogHeader(
    title: String,
    resultCount: Int,
    query: String,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    isTv: Boolean,
    onMoveToCategories: (() -> Boolean)? = null,
    onManageCategories: (() -> Unit)? = null,
    manageCategoriesRequester: FocusRequester? = null,
    searchIcon: ImageVector? = null,
    countUnit: String = "عنصر",
    toolbarIconTint: Color? = null,
    refreshRequester: FocusRequester? = null,
    downOverrideRequester: FocusRequester? = null,
) {
    val colors = LocalHulkColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = if (isTv) TV_PAGE_GUTTER else 0.dp)
                .then(
                    if (isTv && onMoveToCategories != null) {
                        Modifier.onPreviewKeyEvent { event ->
                            val downPressed = event.type == KeyEventType.KeyDown &&
                                event.key == Key.DirectionDown
                            if (!downPressed) {
                                false
                            } else {
                                // A visible notice Retry is the first meaningful target below the
                                // toolbar; otherwise keep the accepted category jump.
                                downOverrideRequester?.let { requester ->
                                    runCatching { requester.requestFocus() }.getOrDefault(false)
                                } ?: onMoveToCategories()
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isTv && onManageCategories != null) {
                Arrangement.Start
            } else {
                Arrangement.spacedBy(11.dp)
            },
        ) {
            Column(Modifier.width(if (isTv) 185.dp else 105.dp)) {
                Text(title, color = colors.text, fontSize = if (isTv) 27.sp else MOBILE_SECTION_TITLE_SIZE, fontWeight = FontWeight.Bold)
                Text("$resultCount $countUnit", color = colors.textMuted, fontSize = if (isTv) 10.sp else MOBILE_SECTION_COUNT_SIZE)
            }
            if (isTv && onManageCategories != null) {
                Spacer(Modifier.width(11.dp - TV_PAGE_GUTTER))
                HulkTextField(
                    query,
                    onSearch,
                    "ابحث في $title…",
                    Modifier.weight(1f).widthIn(max = 630.dp),
                    leadingIcon = searchIcon,
                    leadingIconTint = toolbarIconTint,
                )
                Spacer(Modifier.width(11.dp))
                ManageCategoriesButton(
                    onClick = onManageCategories,
                    requester = manageCategoriesRequester,
                    iconTint = toolbarIconTint,
                )
                Spacer(Modifier.width(11.dp))
                RoundAction(
                    Icons.Rounded.Refresh,
                    "تحديث",
                    onRefresh,
                    iconTint = toolbarIconTint,
                    requester = refreshRequester,
                )
                Spacer(Modifier.width(TV_PAGE_GUTTER))
            } else {
                HulkTextField(
                    query,
                    onSearch,
                    "ابحث في $title…",
                    Modifier.weight(1f).widthIn(max = 630.dp),
                    leadingIcon = searchIcon,
                    leadingIconTint = toolbarIconTint,
                )
                RoundAction(Icons.Rounded.Refresh, "تحديث", onRefresh, iconTint = toolbarIconTint)
            }
        }
        if (!isTv && onManageCategories != null) {
            Spacer(Modifier.height(8.dp))
            ManageCategoriesButton(
                onClick = onManageCategories,
                requester = manageCategoriesRequester,
                modifier = Modifier.align(Alignment.Start),
                iconTint = toolbarIconTint,
            )
        }
    }
}

@Composable
private fun ManageCategoriesButton(
    onClick: () -> Unit,
    requester: FocusRequester?,
    modifier: Modifier = Modifier,
    iconTint: Color? = null,
) {
    FocusButton(
        text = "ادارة الفئات",
        onClick = onClick,
        modifier = if (requester == null) modifier else modifier.focusRequester(requester),
        primary = false,
        compact = true,
        trailingIcon = Icons.Rounded.Tune,
        trailingIconTint = iconTint,
    )
}

@Composable
private fun CategoryBar(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    showFavorites: Boolean = false,
    showContinue: Boolean = false,
    showAll: Boolean = true,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp),
    ) {
        if (showAll) {
            item { FocusButton("الكل", { onSelect(null) }, primary = selectedId == null, compact = true) }
        }
        if (showFavorites) {
            item {
                FocusButton(
                    "★ المفضلة",
                    { onSelect(FAVORITES_CATEGORY_ID) },
                    primary = selectedId == FAVORITES_CATEGORY_ID,
                    compact = true,
                )
            }
        }
        if (showContinue) {
            item {
                FocusButton(
                    "▶ استكمال اخر مشاهدة",
                    { onSelect(CONTINUE_CATEGORY_ID) },
                    primary = selectedId == CONTINUE_CATEGORY_ID,
                    compact = true,
                )
            }
        }
        items(categories, key = Category::id) { category ->
            FocusButton(category.name, { onSelect(category.id) }, primary = selectedId == category.id, compact = true)
        }
    }
}

internal data class LiveCategoryStripMetrics(
    val itemMinHeight: Dp = 36.dp,
    val iconSize: Dp = 24.dp,
    val textSizeSp: Int = 12,
    val horizontalPadding: Dp = 9.dp,
    val verticalPadding: Dp = 6.dp,
    val iconGap: Dp = 7.dp,
    val cornerRadius: Dp = 13.dp,
) {
    val compactHeightModifier: Modifier
        get() = Modifier.heightIn(min = itemMinHeight)

    companion object {
        val Default = LiveCategoryStripMetrics()
    }
}

@Composable
internal fun rememberLiveCategoryStripMetrics(): LiveCategoryStripMetrics {
    val adaptiveUi = LocalAdaptiveUi.current
    val shortSide = minOf(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp).toFloat()
    val minHeight = (shortSide * 0.067f).coerceIn(36f, 40f)
    return LiveCategoryStripMetrics(
        itemMinHeight = minHeight.dp,
        iconSize = 24.dp,
        textSizeSp = 12,
        horizontalPadding = 9.dp,
        verticalPadding = 6.dp,
        iconGap = 7.dp,
        cornerRadius = 13.dp,
    )
}

/**
 * Movies/Series category selector, adopting the accepted Live strip appearance.
 *
 * Selection only: All, Favorites and Recent are fixed semantic rows and the real server categories
 * follow in the single committed order shared with that section's manager. Long-press reorder no
 * longer exists on this surface; a held activation can never reorder or hide.
 */
@Composable
private fun CatalogCategoryBar(
    serverCategories: List<Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    isTv: Boolean,
    focusRestoreController: CategoryFocusRestoreController,
    initialAllFocusRequester: FocusRequester? = null,
    initialAllFocusPending: Boolean = false,
    noticeRetryRequester: FocusRequester? = null,
) {
    val colors = LocalHulkColors.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ownedAllFocusRequester = remember { FocusRequester() }
    val allFocusRequester = initialAllFocusRequester ?: ownedAllFocusRequester
    val favoritesFocusRequester = remember { FocusRequester() }
    val recentFocusRequester = remember { FocusRequester() }
    val stableCategoryIds = remember(serverCategories) { serverCategories.map(Category::id) }
    val categoryFocusRequesters = remember(stableCategoryIds) {
        stableCategoryIds.associateWith { FocusRequester() }
    }
    var categoryBarHasFocus by remember { mutableStateOf(false) }
    val orderedIds = remember(serverCategories) { serverCategories.map(Category::id) }
    val itemMetrics = rememberLiveCategoryStripMetrics()
    val leadingIds = remember { listOf<String?>(null, FAVORITES_CATEGORY_ID, CONTINUE_CATEGORY_ID) }
    val baseContentPadding = 8.dp
    val sidebarUnderlap = rememberCategorySidebarUnderlap(isTv, baseContentPadding)

    fun selectedFocusTarget(): CategoryFocusTarget? {
        val targetIndex = selectedCategoryFocusIndex(
            selectedId = selectedId,
            leadingIds = leadingIds,
            orderedIds = orderedIds,
        ) ?: return null
        val requester = when (selectedId) {
            null -> allFocusRequester
            FAVORITES_CATEGORY_ID -> favoritesFocusRequester
            CONTINUE_CATEGORY_ID -> recentFocusRequester
            else -> selectedId?.let(categoryFocusRequesters::get)
        } ?: return null
        return CategoryFocusTarget(selectedId, targetIndex, requester)
    }
    focusRestoreController.resolveTarget = { selectedFocusTarget() }
    focusRestoreController.restore = { cancelDefaultEntry ->
        restoreSelectedCategoryFocus(
            listState = listState,
            scope = scope,
            controller = focusRestoreController,
            cancelDefaultEntry = cancelDefaultEntry,
        )
    }
    DisposableEffect(focusRestoreController) {
        onDispose {
            focusRestoreController.restore = null
            focusRestoreController.resolveTarget = null
            focusRestoreController.cancel()
        }
    }

    LaunchedEffect(isTv, selectedId, orderedIds) {
        if (isTv) return@LaunchedEffect
        val targetIndex = selectedCategoryFocusIndex(
            selectedId = selectedId,
            leadingIds = leadingIds,
            orderedIds = orderedIds,
        )
        if (targetIndex != null) {
            val anchorIndex = (targetIndex - 1).coerceAtLeast(0)
            listState.scrollToItem(anchorIndex)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .focusProperties {
                onEnter = {
                    if (isTv) {
                        restoreSelectedCategoryFocus(
                            listState = listState,
                            scope = scope,
                            controller = focusRestoreController,
                            cancelDefaultEntry = { cancelFocusChange() },
                        )
                    }
                }
            }
            .focusGroup()
            .onFocusChanged { focusState -> categoryBarHasFocus = focusState.hasFocus }
            .then(
                // With a visible notice, UP from any category chip returns to its Retry; without
                // one the accepted spatial route is untouched.
                if (noticeRetryRequester != null) {
                    Modifier.onPreviewKeyEvent { event ->
                        event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionUp &&
                            runCatching { noticeRetryRequester.requestFocus() }.getOrDefault(false)
                    }
                } else {
                    Modifier
                },
            )
            .extendCategoryViewportTowardStart(sidebarUnderlap.viewportExtraDp.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(
            start = sidebarUnderlap.startContentPaddingDp.dp,
            top = 8.dp,
            end = baseContentPadding,
            bottom = 8.dp,
        ),
    ) {
        item {
            FocusButton(
                "الكل",
                { onSelect(null) },
                primary = selectedId == null,
                compact = true,
                scaleOnFocus = false,
                textSizeSp = itemMetrics.textSizeSp,
                textLineHeightSp = 15,
                modifier = Modifier
                    .categoryChipFocus(
                        isTv, null, selectedId, categoryBarHasFocus,
                        allFocusRequester, focusRestoreController,
                        allowInitialEntry = initialAllFocusPending,
                    )
                    .then(itemMetrics.compactHeightModifier),
            )
        }
        item {
            FocusButton(
                "المفضلة",
                { onSelect(FAVORITES_CATEGORY_ID) },
                primary = selectedId == FAVORITES_CATEGORY_ID,
                compact = true,
                scaleOnFocus = false,
                textSizeSp = itemMetrics.textSizeSp,
                trailingIcon = Icons.Rounded.Favorite,
                trailingIconTint = if (selectedId == FAVORITES_CATEGORY_ID) Color.Black else colors.gold,
                modifier = Modifier
                    .categoryChipFocus(
                        isTv, FAVORITES_CATEGORY_ID, selectedId, categoryBarHasFocus,
                        favoritesFocusRequester, focusRestoreController,
                    )
                    .then(itemMetrics.compactHeightModifier),
            )
        }
        item {
            FocusButton(
                "اخر مشاهدة",
                { onSelect(CONTINUE_CATEGORY_ID) },
                primary = selectedId == CONTINUE_CATEGORY_ID,
                compact = true,
                scaleOnFocus = false,
                textSizeSp = itemMetrics.textSizeSp,
                trailingIcon = Icons.Outlined.Schedule,
                trailingIconTint = if (selectedId == CONTINUE_CATEGORY_ID) Color.Black else colors.gold,
                modifier = Modifier
                    .categoryChipFocus(
                        isTv, CONTINUE_CATEGORY_ID, selectedId, categoryBarHasFocus,
                        recentFocusRequester, focusRestoreController,
                    )
                    .then(itemMetrics.compactHeightModifier),
            )
        }
        items(serverCategories, key = Category::id) { category ->
            LiveCategoryChip(
                category = category,
                selected = selectedId == category.id,
                onClick = { onSelect(category.id) },
                modifier = Modifier
                    .categoryChipFocus(
                        isTv, category.id, selectedId, categoryBarHasFocus,
                        categoryFocusRequesters.getValue(category.id), focusRestoreController,
                    ),
                metrics = itemMetrics,
                framedBrandBadge = true,
            )
        }
    }
}

/**
 * Live category selector.
 *
 * Selection only: All, Favorites and Recent are fixed semantic rows and the real server categories
 * follow in the single committed order shared with the manager and the browser. Long-press reorder
 * no longer exists on this surface; a held activation can never reorder or repeatedly select.
 */
@Composable
private fun LiveCategoryBar(
    categories: List<Category>,
    serverCategories: List<Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    isTv: Boolean,
    focusRestoreController: CategoryFocusRestoreController,
    initialAllFocusRequester: FocusRequester? = null,
    initialAllFocusPending: Boolean = false,
    noticeRetryRequester: FocusRequester? = null,
) {
    val colors = LocalHulkColors.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ownedAllFocusRequester = remember { FocusRequester() }
    val allFocusRequester = initialAllFocusRequester ?: ownedAllFocusRequester
    val favoritesFocusRequester = remember { FocusRequester() }
    val recentFocusRequester = remember { FocusRequester() }
    val stableCategoryIds = remember(categories) { categories.map(Category::id) }
    val categoryFocusRequesters = remember(stableCategoryIds) {
        stableCategoryIds.associateWith { FocusRequester() }
    }
    var categoryBarHasFocus by remember { mutableStateOf(false) }
    val recentCategory = remember(categories) {
        categories.firstOrNull { it.id == LIVE_TV_PRO_MAIN_RECENT_CATEGORY }
    }
    val orderedIds = remember(serverCategories) { serverCategories.map(Category::id) }
    val itemMetrics = rememberLiveCategoryStripMetrics()
    val leadingIds = remember(recentCategory) {
        listOf<String?>(null, FAVORITES_CATEGORY_ID) + listOfNotNull(recentCategory?.id)
    }
    val baseContentPadding = 8.dp
    val sidebarUnderlap = rememberCategorySidebarUnderlap(isTv, baseContentPadding)

    fun selectedFocusTarget(): CategoryFocusTarget? {
        val targetIndex = selectedCategoryFocusIndex(
            selectedId = selectedId,
            leadingIds = leadingIds,
            orderedIds = orderedIds,
        ) ?: return null
        val requester = when (selectedId) {
            null -> allFocusRequester
            FAVORITES_CATEGORY_ID -> favoritesFocusRequester
            LIVE_TV_PRO_MAIN_RECENT_CATEGORY -> recentFocusRequester
            else -> selectedId?.let(categoryFocusRequesters::get)
        } ?: return null
        return CategoryFocusTarget(selectedId, targetIndex, requester)
    }
    focusRestoreController.resolveTarget = { selectedFocusTarget() }
    focusRestoreController.restore = { cancelDefaultEntry ->
        restoreSelectedCategoryFocus(
            listState = listState,
            scope = scope,
            controller = focusRestoreController,
            cancelDefaultEntry = cancelDefaultEntry,
        )
    }
    DisposableEffect(focusRestoreController) {
        onDispose {
            focusRestoreController.restore = null
            focusRestoreController.resolveTarget = null
            focusRestoreController.cancel()
        }
    }

    LaunchedEffect(isTv, selectedId, orderedIds) {
        if (isTv) return@LaunchedEffect
        val targetIndex = selectedCategoryFocusIndex(
            selectedId = selectedId,
            leadingIds = leadingIds,
            orderedIds = orderedIds,
        )
        if (targetIndex != null) {
            val anchorIndex = (targetIndex - 1).coerceAtLeast(0)
            listState.scrollToItem(anchorIndex)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .focusProperties {
                onEnter = {
                    if (isTv) {
                        restoreSelectedCategoryFocus(
                            listState = listState,
                            scope = scope,
                            controller = focusRestoreController,
                            cancelDefaultEntry = { cancelFocusChange() },
                        )
                    }
                }
            }
            .focusGroup()
            .onFocusChanged { focusState -> categoryBarHasFocus = focusState.hasFocus }
            .then(
                // With a visible notice, UP from any category chip returns to its Retry; without
                // one the accepted Live spatial route is untouched.
                if (noticeRetryRequester != null) {
                    Modifier.onPreviewKeyEvent { event ->
                        event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionUp &&
                            runCatching { noticeRetryRequester.requestFocus() }.getOrDefault(false)
                    }
                } else {
                    Modifier
                },
            )
            .extendCategoryViewportTowardStart(sidebarUnderlap.viewportExtraDp.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(
            start = sidebarUnderlap.startContentPaddingDp.dp,
            top = 8.dp,
            end = baseContentPadding,
            bottom = 8.dp,
        ),
    ) {
        item {
            FocusButton(
                "الكل",
                { onSelect(null) },
                primary = selectedId == null,
                compact = true,
                scaleOnFocus = false,
                textSizeSp = itemMetrics.textSizeSp,
                textLineHeightSp = 15,
                modifier = Modifier
                    .categoryChipFocus(
                        isTv, null, selectedId, categoryBarHasFocus,
                        allFocusRequester, focusRestoreController,
                        allowInitialEntry = initialAllFocusPending,
                    )
                    .then(itemMetrics.compactHeightModifier),
            )
        }
        item {
            FocusButton(
                "المفضلة",
                { onSelect(FAVORITES_CATEGORY_ID) },
                primary = selectedId == FAVORITES_CATEGORY_ID,
                compact = true,
                scaleOnFocus = false,
                textSizeSp = itemMetrics.textSizeSp,
                trailingIcon = Icons.Rounded.Favorite,
                trailingIconTint = if (selectedId == FAVORITES_CATEGORY_ID) Color.Black else colors.gold,
                modifier = Modifier
                    .categoryChipFocus(
                        isTv, FAVORITES_CATEGORY_ID, selectedId, categoryBarHasFocus,
                        favoritesFocusRequester, focusRestoreController,
                    )
                    .then(itemMetrics.compactHeightModifier),
            )
        }
        if (recentCategory != null) {
            item {
                FocusButton(
                    "اخر مشاهدة",
                    { onSelect(recentCategory.id) },
                    primary = selectedId == recentCategory.id,
                    compact = true,
                    scaleOnFocus = false,
                    textSizeSp = itemMetrics.textSizeSp,
                    trailingIcon = Icons.Outlined.Schedule,
                    trailingIconTint = if (selectedId == recentCategory.id) Color.Black else colors.gold,
                    modifier = Modifier
                        .categoryChipFocus(
                            isTv, recentCategory.id, selectedId, categoryBarHasFocus,
                            recentFocusRequester, focusRestoreController,
                        )
                        .then(itemMetrics.compactHeightModifier),
                )
            }
        }
        items(serverCategories, key = Category::id) { category ->
            LiveCategoryChip(
                category = category,
                selected = selectedId == category.id,
                onClick = { onSelect(category.id) },
                modifier = Modifier
                    .categoryChipFocus(
                        isTv, category.id, selectedId, categoryBarHasFocus,
                        categoryFocusRequesters.getValue(category.id), focusRestoreController,
                    ),
                metrics = itemMetrics,
                framedBrandBadge = true,
            )
        }
    }
}

@Composable
private fun LiveCategoryChip(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    metrics: LiveCategoryStripMetrics = LiveCategoryStripMetrics.Default,
    framedBrandBadge: Boolean = false,
    moving: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onMoveLeft: (() -> Unit)? = null,
    onMoveRight: (() -> Unit)? = null,
) {
    val colors = LocalHulkColors.current
    // Series keeps its existing long-press reorder callbacks; the Live selector passes none, so
    // the complete Live-only reorder machinery stays inactive there.
    val reorderable = onLongClick != null && onMoveLeft != null && onMoveRight != null
    var focused by remember { mutableStateOf(false) }
    var remoteLongPressHandled by remember { mutableStateOf(false) }
    var selectPressed by remember { mutableStateOf(false) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(selectPressed, reorderable) {
        if (selectPressed && reorderable) {
            delay(650L)
            if (selectPressed && !remoteLongPressHandled) {
                remoteLongPressHandled = true
                onLongClick?.invoke()
            }
        }
    }
    val shape = RoundedCornerShape(metrics.cornerRadius)
    Row(
        modifier = modifier
            .then(metrics.compactHeightModifier)
            .clip(shape)
            .background(
                when {
                    selected -> colors.gold
                    moving -> colors.gold.copy(alpha = .30f)
                    else -> Color(0xFF181914)
                },
            )
            .goldFocusEdge(shape = shape, visible = focused)
            .border(
                if (focused || moving) 2.dp else 1.dp,
                if (focused || moving) colors.goldBright else colors.line.copy(alpha = .40f),
                shape,
            )
            .pointerInput(category.id, reorderable) {
                if (reorderable) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = onLongClick?.let { longPress -> { longPress() } },
                    )
                }
            }
            .pointerInput(category.id, moving, reorderable) {
                if (reorderable && moving) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccumulator = 0f },
                        onDragCancel = { dragAccumulator = 0f },
                        onDragEnd = {
                            when {
                                dragAccumulator >= 48f -> onMoveRight?.invoke()
                                dragAccumulator <= -48f -> onMoveLeft?.invoke()
                            }
                            dragAccumulator = 0f
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount
                    }
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (!reorderable) return@onPreviewKeyEvent false
                val selectKey = event.key == Key.Enter || event.key == Key.DirectionCenter
                when {
                    selectKey && event.type == KeyEventType.KeyDown -> {
                        selectPressed = true
                        true
                    }
                    selectKey && event.type == KeyEventType.KeyUp -> {
                        selectPressed = false
                        if (!remoteLongPressHandled) onClick()
                        remoteLongPressHandled = false
                        true
                    }
                    moving && event.type == KeyEventType.KeyUp && event.key == Key.DirectionLeft -> {
                        onMoveLeft?.invoke(); true
                    }
                    moving && event.type == KeyEventType.KeyUp && event.key == Key.DirectionRight -> {
                        onMoveRight?.invoke(); true
                    }
                    moving && event.type == KeyEventType.KeyDown &&
                        (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) -> true
                    else -> false
                }
            }
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.iconGap, Alignment.CenterHorizontally),
    ) {
        val badgeShape = RoundedCornerShape(if (framedBrandBadge) 7.dp else 10.dp)
        Box(
            modifier = Modifier
                .size(metrics.iconSize)
                .clip(badgeShape)
                .background(Color(0xFF10110D))
                .border(
                    1.dp,
                    if (framedBrandBadge) colors.gold.copy(alpha = .70f) else colors.line.copy(alpha = .35f),
                    badgeShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            BrandLogo(Modifier.fillMaxSize().padding(if (framedBrandBadge) 3.dp else 2.dp))
        }
        Text(
            text = if (moving) "↔ ${category.name}" else category.name,
            color = if (selected) Color.Black else colors.text,
            fontSize = metrics.textSizeSp.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * Centered bounded dark category manager.
 *
 * Movies keeps its existing visibility-only behavior. The Live variant additionally owns the only
 * server-category reorder surface: a fixed header/footer with a scrolling list, one TV focus target
 * per row, remote long-OK move mode with explicit commit/cancel and a continuous phone
 * long-press drag whose stable-id order commits only on drop.
 */
@Composable
private fun CategoryManagerDialog(
    categories: List<Category>,
    hiddenIds: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String,
    scopeText: String,
    emptyText: String,
    liveStyle: Boolean = false,
    onCommitOrder: ((List<String>) -> Unit)? = null,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val density = LocalDensity.current
    val reorderEnabled = onCommitOrder != null
    val commitOrder by rememberUpdatedState(onCommitOrder)
    val committedIds = remember(categories) { categories.map(Category::id) }
    val orderDraft = remember { LiveCategoryOrderDraft(committedIds) }
    var movingId by remember { mutableStateOf<String?>(null) }
    var remoteMoving by remember { mutableStateOf(false) }
    var dragActive by remember { mutableStateOf(false) }
    var backSequenceConsumed by remember { mutableStateOf(false) }
    // NaN until a drag gesture binds its real pointer coordinate; never inherited across gestures.
    var dragPointerY by remember { mutableFloatStateOf(Float.NaN) }
    var listWindowBounds by remember { mutableStateOf<Rect?>(null) }
    val displayIds = orderDraft.displayIds
    val displayCategories = remember(displayIds, categories) {
        val byId = categories.associateBy(Category::id)
        (displayIds.mapNotNull(byId::get) + categories.filterNot { it.id in displayIds })
            .distinctBy(Category::id)
    }
    val categoryFocusRequesters = remember(categories) {
        categories.associate { it.id to FocusRequester() }
    }
    val listState = rememberLazyListState()
    val initialFocusRequester = remember { FocusRequester() }

    fun finishMove() {
        movingId = null
        remoteMoving = false
        dragActive = false
        dragPointerY = Float.NaN
    }
    fun cancelMove() {
        orderDraft.cancel()
        finishMove()
    }
    fun commitMove() {
        val committed = orderDraft.commit() ?: return
        finishMove()
        commitOrder?.invoke(committed)
    }
    val applyDragPointer: (Float) -> Unit = { pointerY ->
        dragPointerY = pointerY
        val currentMoving = movingId
        val listRect = listWindowBounds
        if (currentMoving != null && listRect != null) {
            // Hit-test only the currently laid-out rows so scrolling/recycling can never target a
            // disposed row's stale geometry.
            val rows = listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                val key = item.key as? String ?: return@mapNotNull null
                LiveCategoryRowGeometry(key = key, offset = item.offset, size = item.size)
            }
            val hovered = liveCategoryDragTargetKey(rows, pointerY, listRect.top)
            if (hovered != null && hovered != currentMoving) {
                val targetIndex = orderDraft.displayIds.indexOf(hovered)
                if (targetIndex >= 0) orderDraft.move(currentMoving, targetIndex)
            }
        }
    }
    // An incompatible catalog/scope change cancels the obsolete draft; disposal cancels too.
    LaunchedEffect(committedIds) {
        orderDraft.reset(committedIds)
        finishMove()
    }
    DisposableEffect(Unit) {
        onDispose {
            orderDraft.cancel()
            finishMove()
        }
    }
    // Phone drag edge auto-scroll: one bounded step per frame while the finger holds near an edge.
    LaunchedEffect(dragActive) {
        if (!dragActive) return@LaunchedEffect
        val edge = with(density) { 30.dp.toPx() }
        val step = with(density) { 8.dp.toPx() }
        while (dragActive) {
            val bounds = listWindowBounds
            val delta = when {
                bounds == null || !dragPointerY.isFinite() -> 0f
                dragPointerY < bounds.top + edge -> -step
                dragPointerY > bounds.bottom - edge -> step
                else -> 0f
            }
            if (delta != 0f) {
                listState.scrollBy(delta)
                applyDragPointer(dragPointerY)
            }
            withFrameNanos { }
        }
    }
    // A remote move keeps the moved category attached, revealed and focused after each step.
    LaunchedEffect(displayIds, movingId, remoteMoving) {
        val id = movingId ?: return@LaunchedEffect
        if (!remoteMoving) return@LaunchedEffect
        val index = displayIds.indexOf(id)
        if (index < 0) return@LaunchedEffect
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            listState.scrollToItem(index)
            withFrameNanos { }
            runCatching { categoryFocusRequesters[id]?.requestFocus() }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { initialFocusRequester.requestFocus() }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val dialogWidth = if (adaptiveUi.isTelevision) {
                (maxWidth * 0.42f).coerceIn(340.dp, 520.dp)
            } else {
                (maxWidth * 0.92f).coerceIn(280.dp, 520.dp)
            }
            val dialogHeight = (maxHeight * 0.86f).coerceAtLeast(200.dp)
            val shape = RoundedCornerShape(18.dp)
            Column(
                modifier = Modifier
                    .width(dialogWidth)
                    .heightIn(max = dialogHeight)
                    .clip(shape)
                    .background(colors.surface)
                    .border(1.dp, colors.line, shape)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .onPreviewKeyEvent { event ->
                        // While moving, consume the complete BACK sequence: the first press cancels
                        // the draft, keeps the manager open and its release can never close it.
                        val isBack =
                            event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK
                        if (reorderEnabled && isBack && (movingId != null || backSequenceConsumed)) {
                            if (event.type == KeyEventType.KeyDown) {
                                if (movingId != null) cancelMove()
                                backSequenceConsumed = true
                            } else if (event.type == KeyEventType.KeyUp) {
                                backSequenceConsumed = false
                            }
                            true
                        } else {
                            false
                        }
                    },
            ) {
                Text(title, color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(
                    scopeText,
                    color = colors.textMuted,
                    fontSize = 11.sp,
                )
                if (reorderEnabled) {
                    Spacer(Modifier.height(4.dp))
                    if (adaptiveUi.isTelevision) {
                        Text(
                            "لترتيب الفئات اضغط مطولا OK ، حرك بالسهمين لاعلى ولاسفل ، ثم اضغط OK للحفظ",
                            color = colors.textMuted,
                            fontSize = 9.sp,
                        )
                        Text(
                            "للالغاء اضغط BACK",
                            color = colors.textMuted,
                            fontSize = 9.sp,
                        )
                    } else {
                        Text(
                            "لترتيب الفئات اضغط مطولا على الفئة ، اسحبها الى مكانها ، ثم افلتها للحفظ",
                            color = colors.textMuted,
                            fontSize = 9.sp,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (categories.isEmpty()) {
                    Text(emptyText, color = colors.textMuted, fontSize = 12.sp)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .onGloballyPositioned { listWindowBounds = it.boundsInWindow() },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(displayCategories, key = Category::id) { category ->
                            LiveCategoryManagerRow(
                                category = category,
                                hidden = category.id in hiddenIds,
                                moving = movingId == category.id,
                                reorderEnabled = reorderEnabled,
                                onToggle = { if (movingId == null) onToggle(category.id) },
                                onEnterMove = {
                                    if (movingId == null) {
                                        orderDraft.start()
                                        movingId = category.id
                                        remoteMoving = true
                                    }
                                },
                                onMoveUp = {
                                    val index = orderDraft.displayIds.indexOf(category.id)
                                    if (index > 0) orderDraft.move(category.id, index - 1)
                                },
                                onMoveDown = {
                                    val index = orderDraft.displayIds.indexOf(category.id)
                                    if (index in 0 until orderDraft.displayIds.lastIndex) {
                                        orderDraft.move(category.id, index + 1)
                                    }
                                },
                                onCommitMove = { commitMove() },
                                onDragStart = { pointerY ->
                                    if (movingId == null) {
                                        // Bind this gesture's real pointer coordinate before the
                                        // auto-scroll loop can read it.
                                        dragPointerY = pointerY
                                        orderDraft.start()
                                        movingId = category.id
                                        remoteMoving = false
                                        dragActive = true
                                    }
                                },
                                onDragMove = applyDragPointer,
                                onDrop = { commitMove() },
                                onDragCancel = { cancelMove() },
                                modifier = if (category.id == displayCategories.first().id) {
                                    Modifier.focusRequester(initialFocusRequester)
                                } else {
                                    Modifier
                                },
                                focusRequester = categoryFocusRequesters[category.id],
                                liveStyle = liveStyle,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                FocusButton(
                    "تم",
                    onClick = { if (movingId == null) onDismiss() },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .then(
                            if (categories.isEmpty()) Modifier.focusRequester(initialFocusRequester) else Modifier,
                        ),
                    compact = true,
                    scaleOnFocus = !liveStyle,
                )
            }
        }
    }
}

@Composable
private fun LiveCategoryManagerRow(
    category: Category,
    hidden: Boolean,
    moving: Boolean,
    reorderEnabled: Boolean,
    onToggle: () -> Unit,
    onEnterMove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onCommitMove: () -> Unit,
    onDragStart: (Float) -> Unit,
    onDragMove: (Float) -> Unit,
    onDrop: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    liveStyle: Boolean = false,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember { mutableStateOf(false) }
    var longPressConsumed by remember(category.id) { mutableStateOf(false) }
    val rowTopWindow = remember { mutableFloatStateOf(0f) }
    val currentOnToggle by rememberUpdatedState(onToggle)
    val currentOnEnterMove by rememberUpdatedState(onEnterMove)
    val currentOnMoveUp by rememberUpdatedState(onMoveUp)
    val currentOnMoveDown by rememberUpdatedState(onMoveDown)
    val currentOnCommitMove by rememberUpdatedState(onCommitMove)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragMove by rememberUpdatedState(onDragMove)
    val currentOnDrop by rememberUpdatedState(onDrop)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    // Focus is distinct from hidden/moving state and never toggles visibility; the pale edge stays
    // inside the row bounds with no scale or size jump.
    val showFocused = focused && adaptiveUi.showFocusHighlights
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    moving -> colors.gold.copy(alpha = .20f)
                    hidden -> Color(0xFF10110D)
                    else -> Color(0xFF181914)
                },
            )
            .border(
                width = if (showFocused) 2.dp else 1.dp,
                color = when {
                    showFocused -> colors.goldBright
                    moving -> colors.gold.copy(alpha = .45f)
                    else -> Color.White.copy(alpha = .07f)
                },
                shape = shape,
            )
            .then(
                if (reorderEnabled && focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
            )
            .onGloballyPositioned { coordinates ->
                // Only this row's own anchor coordinate is kept; hit-testing uses the list's
                // current laid-out rows, never a stored per-row bounds map.
                rowTopWindow.floatValue = coordinates.boundsInWindow().top
            }
            .onFocusChanged { focused = it.isFocused }
            .then(if (reorderEnabled) Modifier.focusable() else Modifier)
            .then(
                if (reorderEnabled) {
                    Modifier.onPreviewKeyEvent { event ->
                        val selectKey = event.key == Key.Enter || event.key == Key.DirectionCenter
                        when {
                            moving -> when {
                                selectKey -> {
                                    if (event.type == KeyEventType.KeyUp) {
                                        if (longPressConsumed) {
                                            longPressConsumed = false
                                        } else {
                                            currentOnCommitMove()
                                        }
                                    }
                                    true
                                }
                                event.key == Key.DirectionUp -> {
                                    if (event.type == KeyEventType.KeyUp) currentOnMoveUp()
                                    true
                                }
                                event.key == Key.DirectionDown -> {
                                    if (event.type == KeyEventType.KeyUp) currentOnMoveDown()
                                    true
                                }
                                else -> false
                            }
                            selectKey && event.type == KeyEventType.KeyDown -> {
                                if (event.nativeKeyEvent.repeatCount > 0 || event.nativeKeyEvent.isLongPress) {
                                    if (!longPressConsumed) {
                                        longPressConsumed = true
                                        currentOnEnterMove()
                                    }
                                }
                                true
                            }
                            selectKey && event.type == KeyEventType.KeyUp -> {
                                if (!longPressConsumed) currentOnToggle()
                                longPressConsumed = false
                                true
                            }
                            else -> false
                        }
                    }
                } else {
                    Modifier
                },
            )
            .then(
                if (reorderEnabled) {
                    Modifier.pointerInput(category.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // A real unconsumed quick release toggles once; a consumed/cancelled
                            // change never toggles, starts a drag or commits. Slop movement stays
                            // with ordinary list scrolling.
                            var lastPosition = down.position
                            val resolvedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                var early = false
                                while (!early) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    when (
                                        liveCategoryPreLongPressDecision(
                                            hasChange = change != null,
                                            pressed = change?.pressed ?: false,
                                            consumed = change?.isConsumed ?: false,
                                            movedBeyondSlop = change != null &&
                                                (change.position - down.position).getDistance() >
                                                viewConfiguration.touchSlop,
                                        )
                                    ) {
                                        LiveCategoryGestureDecision.TOGGLE -> {
                                            currentOnToggle()
                                            early = true
                                        }
                                        LiveCategoryGestureDecision.CANCEL -> early = true
                                        else -> if (change != null) lastPosition = change.position
                                    }
                                }
                                true
                            }
                            if (resolvedEarly != null) return@awaitEachGesture
                            // One continuous long-press drag owns the gesture; only an unconsumed
                            // release commits, and it is consumed so no tap toggle can follow. The
                            // drag starts from the real current pointer coordinate of this gesture.
                            currentOnDragStart(rowTopWindow.floatValue + lastPosition.y)
                            var dropped = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    when (
                                        liveCategoryDragDecision(
                                            hasChange = change != null,
                                            pressed = change?.pressed ?: false,
                                            consumed = change?.isConsumed ?: false,
                                        )
                                    ) {
                                        LiveCategoryGestureDecision.DROP -> {
                                            currentOnDrop()
                                            dropped = true
                                            change?.consume()
                                            break
                                        }
                                        LiveCategoryGestureDecision.CANCEL -> break
                                        else -> {
                                            currentOnDragMove(
                                                rowTopWindow.floatValue + (change?.position?.y ?: 0f),
                                            )
                                            change?.consume()
                                        }
                                    }
                                }
                            } finally {
                                if (!dropped) currentOnDragCancel()
                            }
                        }
                    }
                } else {
                    Modifier
                },
            )
            .then(
                if (reorderEnabled) {
                    // Accessible hide/show activation and state without a second pointer handler:
                    // touch stays owned by the custom gesture, TV by the preview key handler.
                    Modifier.semantics(mergeDescendants = true) {
                        role = Role.Button
                        stateDescription = if (hidden) "مخفية" else "ظاهرة"
                        if (!moving) {
                            onClick(label = if (hidden) "اظهار الفئة" else "اخفاء الفئة") {
                                currentOnToggle()
                                true
                            }
                        }
                    }
                } else {
                    Modifier.clickable(role = Role.Button, onClick = onToggle)
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = category.name,
            color = if (hidden) colors.textMuted else colors.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = if (reorderEnabled) Int.MAX_VALUE else 1,
            overflow = if (reorderEnabled) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription = if (hidden) "مخفية" else "ظاهرة",
            tint = when {
                hidden -> colors.textMuted
                liveStyle -> colors.gold
                else -> colors.goldBright
            },
            modifier = Modifier.size(18.dp),
        )
        if (reorderEnabled) {
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = "اسحب للترتيب",
                tint = if (moving) colors.goldBright else colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun FavoriteHint(isTv: Boolean) {
    val colors = LocalHulkColors.current
    Text(
        text = if (isTv) "تلميح: اضغط مطولا زر OK لاضافة او ازالة العنصر من المفضلة" else "تلميح: اضغط مطولا على العنصر لاضافته او ازالته من المفضلة",
        color = colors.textMuted,
        fontSize = 9.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun PageTitle(
    title: String,
    subtitle: String,
    count: Int,
    icon: ImageVector,
    isTv: Boolean = false,
) {
    val colors = LocalHulkColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isTv) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(colors.gold.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(icon, title, tint = colors.goldBright, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(11.dp))
        }
        Column {
            Text(title, color = colors.text, fontSize = if (isTv) 27.sp else MOBILE_SECTION_TITLE_SIZE, fontWeight = FontWeight.Bold)
            Text(
                text = if (isTv) {
                    if (count > 0) "$subtitle  •  $count" else subtitle
                } else {
                    "$count عنصر"
                },
                color = colors.textMuted,
                fontSize = if (isTv) 11.sp else MOBILE_SECTION_COUNT_SIZE,
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    val colors = LocalHulkColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandLogo(Modifier.size(70.dp).graphicsLayer { alpha = .65f })
        Spacer(Modifier.height(10.dp))
        Text(message, color = colors.textMuted, fontSize = 13.sp)
    }
}

internal fun newest(content: List<ContentItem>): List<ContentItem> =
    content.sortedByDescending { it.addedAtEpochSeconds ?: 0L }

internal fun ContentItem.matchesSearch(rawQuery: String): Boolean {
    val query = rawQuery.trim()
    if (query.isBlank()) return true
    return sequenceOf(name, year, genre, plot, nowPlaying)
        .filterNotNull()
        .any { value -> value.contains(query, ignoreCase = true) }
}

internal fun HistoryEntry.isResumable(): Boolean =
    !isLive && positionMs > 0L &&
        (durationMs <= 0L || positionMs.toDouble() / durationMs < .92)

internal fun categoryMatches(
    item: ContentItem,
    selectedId: String?,
    isFavorite: (ContentItem) -> Boolean,
): Boolean = when (selectedId) {
    null -> true
    FAVORITES_CATEGORY_ID -> isFavorite(item)
    CONTINUE_CATEGORY_ID -> false
    else -> item.categoryId == selectedId
}

/**
 * True when the authoritative selection points at a server category that the Movie-only manager
 * has just hidden. All/Favorites/Recent remain available, so the caller falls back to "الكل".
 */
internal fun isHiddenServerCategorySelection(
    selectedId: String?,
    hiddenIds: Set<String>,
): Boolean = selectedId != null &&
    selectedId != FAVORITES_CATEGORY_ID &&
    selectedId != CONTINUE_CATEGORY_ID &&
    selectedId in hiddenIds

/** Owner-approved catalog count unit: Movies counts films, every other catalog counts items. */
internal fun catalogCountUnit(type: ContentType): String = when (type) {
    ContentType.MOVIE -> "فلم"
    ContentType.SERIES -> "مسلسل"
    else -> "عنصر"
}

private data class DestinationEntry(val destination: MainDestination, val icon: ImageVector, val label: String)

private val destinations = listOf(
    DestinationEntry(MainDestination.HOME, Icons.Rounded.Home, "الرئيسية"),
    DestinationEntry(MainDestination.LIVE, Icons.Rounded.LiveTv, "البث المباشر"),
    DestinationEntry(MainDestination.MOVIES, Icons.Rounded.Movie, "الافلام"),
    DestinationEntry(MainDestination.SERIES, Icons.Rounded.Tv, "المسلسلات"),
    DestinationEntry(MainDestination.FAVORITES, Icons.Rounded.Favorite, "المفضلة"),
    DestinationEntry(MainDestination.SEARCH, Icons.Rounded.Search, "البحث"),
    DestinationEntry(MainDestination.DOWNLOADS, Icons.Rounded.Download, "التنزيلات"),
    DestinationEntry(MainDestination.SETTINGS, Icons.Rounded.Settings, "الاعدادات"),
)
