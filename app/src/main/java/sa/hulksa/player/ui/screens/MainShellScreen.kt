package sa.hulksa.player.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sa.hulksa.player.BuildConfig
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.MainDestination
import sa.hulksa.player.data.GrowthAction
import sa.hulksa.player.data.GrowthDestination
import sa.hulksa.player.data.RenewalBannerContent
import sa.hulksa.player.data.evaluateRenewalBanner
import sa.hulksa.player.data.link
import sa.hulksa.player.data.resolveGrowthAction
import sa.hulksa.player.model.CapabilityFinding
import sa.hulksa.player.model.CapabilityStatus
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
import sa.hulksa.player.ui.adaptive.HulkNavigationType
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.components.BrandLogo
import sa.hulksa.player.ui.components.BrandBadge
import sa.hulksa.player.ui.components.ChannelLogo
import sa.hulksa.player.ui.components.ChannelListItem
import sa.hulksa.player.ui.components.UniversalPosterCard
import sa.hulksa.player.ui.components.ErrorNotice
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.HistoryCard
import sa.hulksa.player.ui.components.HulkTextField
import sa.hulksa.player.ui.components.InfoPill
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.theme.LocalHulkColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.navigationBarsPadding

private const val FAVORITES_CATEGORY_ID = "__hulk_favorites__"
private const val CONTINUE_CATEGORY_ID = "__hulk_continue__"
private const val TV_CATEGORY_PARENT_HORIZONTAL_INSET_DP = 14f
private val TV_PAGE_GUTTER = 8.dp
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
private fun rememberCategorySidebarUnderlap(
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
private fun Modifier.extendCategoryViewportTowardStart(extraWidth: Dp): Modifier {
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
    private var mobileNavigationFirstVisibleIndex: Int = 0
    private var mobileNavigationFirstVisibleOffset: Int = 0

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

    fun mobileNavigationPosition(): Pair<Int, Int> =
        mobileNavigationFirstVisibleIndex to mobileNavigationFirstVisibleOffset

    fun saveMobileNavigationPosition(firstVisibleIndex: Int, firstVisibleOffset: Int) {
        mobileNavigationFirstVisibleIndex = firstVisibleIndex.coerceAtLeast(0)
        mobileNavigationFirstVisibleOffset = firstVisibleOffset.coerceAtLeast(0)
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
    LaunchedEffect(navigationMemory, input) {
        navigationMemory.cachedHomeModel(input)?.let { exact ->
            presented = exact
            return@LaunchedEffect
        }
        if (presented?.model?.hasHomeHeroSource() != true) {
            navigationMemory.lastGoodHomeModel()?.let { lastGood ->
                presented = lastGood
            }
        }
        val exact = navigationMemory.homeModel(input)
        if (exact.input == input) presented = exact
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
    var target: DownloadTvFocusTarget? = null

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
                    val targetIndex = index - graph.columns
                    if (targetIndex >= 0) {
                        downloadCardTargetAt(graph, targetIndex, current.slot)
                    } else {
                        downloadToolbarTargetAbove(graph, index, current.slot)
                    }
                }
                DownloadFocusMove.DOWN -> downloadCardTargetAt(
                    graph,
                    index + graph.columns,
                    current.slot,
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
    onToggleFavorite: (ContentItem) -> Unit,
    onRefresh: () -> Unit,
    onOpenNotifications: () -> Unit,
    onClearHistory: () -> Unit,
    onPlayDownload: (OfflineDownload) -> Unit,
    onDeleteDownload: (OfflineDownload) -> Unit,
    onRetryDownload: (OfflineDownload) -> Unit,
    onToggleWifiOnly: () -> Unit,
    onToggleDownloadSchedule: () -> Unit,
    onCycleConcurrentDownloads: () -> Unit,
    onToggleEpisodeNotificationMaster: () -> Unit,
    onCycleDownloadPriority: (OfflineDownload) -> Unit,
    onRefreshAccount: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onLogout: () -> Unit,
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
    var tvContentFocusRequestId by remember { mutableLongStateOf(0L) }
    var pendingTvContentFocusHandoff by remember {
        mutableStateOf<TvContentFocusHandoffRequest?>(null)
    }
    val selectTvDestination: (MainDestination) -> Unit = { destination ->
        if (destination != state.destination) {
            onSelectDestination(destination)
        }
        tvContentFocusRequestId += 1L
        pendingTvContentFocusHandoff = TvContentFocusHandoffRequest(
            destination = destination,
            requestId = tvContentFocusRequestId,
        )
    }
    val homeModel = if (state.destination == MainDestination.HOME) {
        rememberHomeModelForPresentation(
            navigationMemory = navigationMemory,
            input = HomeContentModelInput(
                movieCatalog = state.catalogs[ContentType.MOVIE],
                seriesCatalog = state.catalogs[ContentType.SERIES],
                liveCatalog = state.catalogs[ContentType.LIVE],
                history = state.history,
                favorites = state.favorites,
            ),
        )
    } else {
        null
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
    val downloadsEnabled = state.operations.features.downloadsEnabled
    val navigationEntries = remember(downloadsEnabled) {
        destinations.filterNot { entry ->
            !downloadsEnabled && entry.destination == MainDestination.DOWNLOADS
        }
    }
    val favoriteOverrides = remember { mutableStateMapOf<String, Boolean>() }
    var favoriteActionLocked by remember { mutableStateOf(false) }
    val favoriteScope = rememberCoroutineScope()
    LaunchedEffect(state.favorites) {
        favoriteOverrides.entries.toList().forEach { (key, optimisticValue) ->
            if ((key in state.favorites) == optimisticValue) favoriteOverrides.remove(key)
        }
    }
    val favoriteSnapshot = CatalogFavoriteSnapshot(
        persisted = state.favorites,
        optimistic = favoriteOverrides.toMap(),
    )
    val resolvedIsFavorite: (ContentItem) -> Boolean = { item ->
        val key = "${item.type.name}:${item.id}"
        favoriteOverrides[key] ?: isFavorite(item)
    }
    var growthQrDestination by remember { mutableStateOf<GrowthDestination?>(null) }
    val openGrowthDestination: (GrowthDestination) -> Unit = { destination ->
        when (resolveGrowthAction(state.operations.growth, destination, isTv)) {
            GrowthAction.OPEN_QR -> growthQrDestination = destination
            GrowthAction.OPEN_URL -> {
                val url = state.operations.growth.link(destination).url.orEmpty()
                if (!launchGrowthUrl(context, url)) {
                    Toast.makeText(context, "تعذر فتح الرابط على هذا الجهاز", Toast.LENGTH_SHORT).show()
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
    val toggleFavoriteWithFeedback: (ContentItem) -> Unit = { pressedItem ->
        if (!favoriteActionLocked) {
            favoriteActionLocked = true
            val pressedKey = "${pressedItem.type.name}:${pressedItem.id}"
            val pressedTitle = pressedItem.name
            val wasFavorite = resolvedIsFavorite(pressedItem)
            val optimisticValue = !wasFavorite
            favoriteOverrides[pressedKey] = optimisticValue
            onToggleFavorite(pressedItem)
            Toast.makeText(
                context,
                if (wasFavorite) "تمت ازالة $pressedTitle من المفضلة" else "تمت اضافة $pressedTitle الى المفضلة",
                Toast.LENGTH_SHORT,
            ).show()
            favoriteScope.launch {
                delay(1_600L)
                favoriteActionLocked = false
                delay(3_400L)
                if (favoriteOverrides[pressedKey] == optimisticValue) favoriteOverrides.remove(pressedKey)
            }
        }
    }
    val tvRailFocusRequesters = remember(navigationEntries) {
        navigationEntries.associate { entry -> entry.destination to FocusRequester() }
    }
    val currentTvContentFocusRequester = tvContentFocusRequesters.getValue(state.destination)
    val currentTvDestinationFocusRequester =
        tvCatalogAllFocusRequesters[state.destination] ?: currentTvContentFocusRequester
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
                        favoriteSnapshot = favoriteSnapshot,
                        isFavorite = resolvedIsFavorite,
                        onSelectCategory = onSelectCategory,
                        onSearch = onSearch,
                        onOpen = onOpen,
                        onOpenHistory = onOpenHistory,
                        onToggleFavorite = toggleFavoriteWithFeedback,
                        onRefresh = onRefresh,
                        onOpenNotifications = onOpenNotifications,
                        onGrowthAction = openGrowthDestination,
                        onSelectDestination = onSelectDestination,
                        onClearHistory = onClearHistory,
                        onPlayDownload = onPlayDownload,
                        onDeleteDownload = onDeleteDownload,
                        onRetryDownload = onRetryDownload,
                        onToggleWifiOnly = onToggleWifiOnly,
                        onToggleDownloadSchedule = onToggleDownloadSchedule,
                        onCycleConcurrentDownloads = onCycleConcurrentDownloads,
                        onToggleEpisodeNotificationMaster = onToggleEpisodeNotificationMaster,
                        onCycleDownloadPriority = onCycleDownloadPriority,
                        onRefreshAccount = onRefreshAccount,
                        onRunDiagnostics = onRunDiagnostics,
                        onLogout = onLogout,
                        initialAllFocusRequester = tvCatalogAllFocusRequesters[state.destination],
                        initialAllFocusPending = currentTvCatalogInitialFocusPending,
                        downloadsExitFocusRequester = tvRailFocusRequesters[MainDestination.DOWNLOADS],
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
                        favoriteSnapshot = favoriteSnapshot,
                        isFavorite = resolvedIsFavorite,
                        onSelectCategory = onSelectCategory,
                        onSearch = onSearch,
                        onOpen = onOpen,
                        onOpenHistory = onOpenHistory,
                        onToggleFavorite = toggleFavoriteWithFeedback,
                        onRefresh = onRefresh,
                        onOpenNotifications = onOpenNotifications,
                        onGrowthAction = openGrowthDestination,
                        onSelectDestination = onSelectDestination,
                        onClearHistory = onClearHistory,
                        onPlayDownload = onPlayDownload,
                        onDeleteDownload = onDeleteDownload,
                        onRetryDownload = onRetryDownload,
                        onToggleWifiOnly = onToggleWifiOnly,
                        onToggleDownloadSchedule = onToggleDownloadSchedule,
                        onCycleConcurrentDownloads = onCycleConcurrentDownloads,
                        onToggleEpisodeNotificationMaster = onToggleEpisodeNotificationMaster,
                        onCycleDownloadPriority = onCycleDownloadPriority,
                        onRefreshAccount = onRefreshAccount,
                        onRunDiagnostics = onRunDiagnostics,
                        onLogout = onLogout,
                    )
                }
                MobileNavigation(
                    selected = state.destination,
                    onSelect = onSelectDestination,
                    navigationMemory = navigationMemory,
                    entries = navigationEntries,
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

@Composable
private fun CinematicNavigationRail(
    entries: List<DestinationEntry>,
    selected: MainDestination,
    onSelect: (MainDestination) -> Unit,
    onSwitchProfile: () -> Unit,
    destinationFocusRequesters: Map<MainDestination, FocusRequester>,
) {
    var railHasFocus by remember { mutableStateOf(false) }
    val expanded = railHasFocus
    val adaptiveUi = LocalAdaptiveUi.current
    val metrics = tvRailMetrics(
        screenWidthDp = adaptiveUi.screenWidthDp,
        screenHeightDp = adaptiveUi.screenHeightDp,
    )
    val primaryEntries = entries.filterNot { it.destination == MainDestination.SETTINGS }
    val profileRequester = remember { FocusRequester() }
    val settingsRequester = destinationFocusRequesters.getValue(MainDestination.SETTINGS)
    val selectedRequester = destinationFocusRequesters.getValue(selected)
    val railWidth = if (expanded) metrics.expandedWidthDp.dp else metrics.collapsedWidthDp.dp

    Column(
        modifier = Modifier
            .zIndex(1f)
            .width(railWidth)
            .fillMaxHeight()
            .focusProperties {
                onEnter = {
                    selectedRequester.requestFocus()
                }
            }
            .focusGroup()
            .onFocusChanged { railHasFocus = it.hasFocus }
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF090A07), Color(0xFF0A0B08)),
                ),
            )
            .padding(
                start = metrics.outerHorizontalPaddingDp.dp,
                end = metrics.outerHorizontalPaddingDp.dp,
                top = metrics.topPaddingDp.dp,
                bottom = metrics.bottomPaddingDp.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
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
            NavigationItem(
                entry = entry,
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
        NavigationItem(
            entry = DestinationEntry(
                MainDestination.SETTINGS,
                Icons.Rounded.Person,
                "تغيير المستخدم",
            ),
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
            NavigationItem(
                entry = entry,
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
private fun NavigationItem(
    entry: DestinationEntry,
    selected: Boolean,
    expanded: Boolean,
    metrics: TvRailMetrics,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember { mutableStateOf(false) }
    val showFocused = focused && adaptiveUi.showFocusHighlights
    val active = selected || showFocused
    val shape = RoundedCornerShape(metrics.cornerRadiusDp.dp)
    val focusBorderWidth = adaptiveUi.tvPremiumPolicy.focusBorderWidthDp.dp
    val background = when {
        showFocused -> colors.gold.copy(alpha = .19f)
        selected -> colors.gold.copy(alpha = .11f)
        else -> Color.Transparent
    }
    val borderWidth = when {
        showFocused -> focusBorderWidth
        selected -> 1.dp
        else -> 0.dp
    }
    val borderColor = when {
        showFocused -> colors.goldBright
        selected -> colors.goldBright.copy(alpha = .38f)
        else -> Color.Transparent
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.itemHeightDp.dp)
            .clip(shape)
            .background(background)
            .border(borderWidth, borderColor, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = metrics.itemHorizontalPaddingDp.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        Icon(
            imageVector = entry.icon,
            contentDescription = entry.label,
            tint = if (active) colors.goldBright else colors.textMuted,
            modifier = Modifier.size(metrics.iconSizeDp.dp),
        )
        if (expanded) {
            Spacer(Modifier.width(metrics.iconLabelGapDp.dp))
            Text(
                entry.label,
                color = if (active) colors.text else colors.textMuted,
                fontSize = metrics.labelSizeSp.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MobileNavigation(
    selected: MainDestination,
    onSelect: (MainDestination) -> Unit,
    navigationMemory: NavigationMemoryStore,
    entries: List<DestinationEntry>,
) {
    val colors = LocalHulkColors.current
    val rememberedPosition = remember(navigationMemory) { navigationMemory.mobileNavigationPosition() }
    val navigationState = rememberLazyListState(
        initialFirstVisibleItemIndex = rememberedPosition.first,
        initialFirstVisibleItemScrollOffset = rememberedPosition.second,
    )
    val navigationScope = rememberCoroutineScope()
    val requestProfileSwitch = sa.hulksa.player.ui.LocalProfileSwitchRequester.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val isWide = configuration.screenWidthDp >= 600
    val mobileEntries = remember(entries) {
        buildList {
            entries.forEach { entry ->
                add(entry to false)
                if (entry.destination == MainDestination.SEARCH) {
                    add(
                        DestinationEntry(
                            MainDestination.SEARCH,
                            Icons.Rounded.Person,
                            "تغيير المستخدم",
                        ) to true,
                    )
                }
            }
        }
    }
    val searchIndex = mobileEntries.indexOfFirst { (entry, profileSwitch) ->
        !profileSwitch && entry.destination == MainDestination.SEARCH
    }

    suspend fun revealNavigationContext(index: Int, includeNext: Boolean) {
        if (isLandscape || isWide || index < 0) return
        delay(50L)
        val layoutInfo = navigationState.layoutInfo
        val fullyVisible = layoutInfo.visibleItemsInfo.filter { item ->
            item.offset >= layoutInfo.viewportStartOffset &&
                item.offset + item.size <= layoutInfo.viewportEndOffset
        }
        val requiredIndex = if (includeNext) {
            (index + 1).coerceAtMost(mobileEntries.lastIndex)
        } else {
            index
        }
        val fullyVisibleIndices = fullyVisible.mapTo(hashSetOf()) { it.index }
        if (index in fullyVisibleIndices && requiredIndex in fullyVisibleIndices) return

        val visibleCapacity = fullyVisible.size.coerceAtLeast(1)
        val anchorIndex = (requiredIndex - (visibleCapacity - 1))
            .coerceIn(0, mobileEntries.lastIndex)
        navigationState.animateScrollToItem(anchorIndex)
    }

    LaunchedEffect(navigationState, isLandscape, isWide) {
        if (isLandscape || isWide) return@LaunchedEffect
        snapshotFlow {
            navigationState.firstVisibleItemIndex to navigationState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            navigationMemory.saveMobileNavigationPosition(index, offset)
        }
    }

    LaunchedEffect(selected, isLandscape, isWide) {
        if (isLandscape || isWide) return@LaunchedEffect
        val selectedIndex = mobileEntries.indexOfFirst { (entry, profileSwitch) ->
            !profileSwitch && entry.destination == selected
        }
        revealNavigationContext(
            index = selectedIndex,
            includeNext = selectedIndex >= searchIndex && selectedIndex < mobileEntries.lastIndex,
        )
    }

    fun selectEntry(index: Int, entry: DestinationEntry, profileSwitch: Boolean) {
        if (!isLandscape && !isWide) {
            navigationMemory.saveMobileNavigationPosition(
                navigationState.firstVisibleItemIndex,
                navigationState.firstVisibleItemScrollOffset,
            )
            navigationScope.launch {
                revealNavigationContext(
                    index = index,
                    includeNext = index >= searchIndex && index < mobileEntries.lastIndex,
                )
            }
        }
        if (profileSwitch) {
            requestProfileSwitch()
        } else {
            onSelect(entry.destination)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF090A07))
            .navigationBarsPadding()
            .padding(horizontal = if (isLandscape || isWide) 4.dp else 8.dp, vertical = 4.dp),
    ) {
        if (isLandscape || isWide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                mobileEntries.forEachIndexed { index, (entry, profileSwitch) ->
                    val active = !profileSwitch && selected == entry.destination
                    val iconScale by animateFloatAsState(
                        targetValue = if (active) 1.08f else 1f,
                        label = "mobileNavIconScaleWide",
                    )
                    val labelAlpha by animateFloatAsState(
                        targetValue = if (active) 1f else .72f,
                        label = "mobileNavLabelAlphaWide",
                    )
                    val indicatorWidth by animateDpAsState(
                        targetValue = if (active) 36.dp else 0.dp,
                        label = "mobileNavIndicatorWidthWide",
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) colors.gold.copy(alpha = .12f) else Color.Transparent)
                            .border(
                                if (active) 1.dp else 0.dp,
                                if (active) colors.goldBright.copy(alpha = .45f) else Color.Transparent,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable(role = Role.Button) { selectEntry(index, entry, profileSwitch) }
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .height(4.dp)
                                .width(indicatorWidth)
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (active) colors.goldBright else Color.Transparent),
                        )
                        Spacer(Modifier.height(1.dp))
                        Icon(
                            imageVector = entry.icon,
                            contentDescription = entry.label,
                            tint = if (active) colors.goldBright else colors.textMuted,
                            modifier = Modifier
                                .size(23.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = entry.label,
                            color = if (active) colors.text.copy(alpha = labelAlpha) else colors.textMuted.copy(alpha = labelAlpha),
                            fontSize = 9.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                state = navigationState,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(
                    items = mobileEntries,
                    key = { _, item ->
                        val (entry, profileSwitch) = item
                        if (profileSwitch) "switch-profile" else entry.destination.name
                    },
                ) { index, (entry, profileSwitch) ->
                    val active = !profileSwitch && selected == entry.destination
                    val iconScale by animateFloatAsState(
                        targetValue = if (active) 1.08f else 1f,
                        label = "mobileNavIconScalePortrait",
                    )
                    val labelAlpha by animateFloatAsState(
                        targetValue = if (active) 1f else .72f,
                        label = "mobileNavLabelAlphaPortrait",
                    )
                    val indicatorWidth by animateDpAsState(
                        targetValue = if (active) 36.dp else 0.dp,
                        label = "mobileNavIndicatorWidthPortrait",
                    )

                    Column(
                        modifier = Modifier
                            .widthIn(min = 52.dp)
                            .height(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) colors.gold.copy(alpha = .12f) else Color.Transparent)
                            .border(
                                if (active) 1.dp else 0.dp,
                                if (active) colors.goldBright.copy(alpha = .45f) else Color.Transparent,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable(role = Role.Button) { selectEntry(index, entry, profileSwitch) }
                            .padding(horizontal = 3.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .height(4.dp)
                                .width(indicatorWidth)
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (active) colors.goldBright else Color.Transparent),
                        )
                        Spacer(Modifier.height(1.dp))
                        Icon(
                            imageVector = entry.icon,
                            contentDescription = entry.label,
                            tint = if (active) colors.goldBright else colors.textMuted,
                            modifier = Modifier
                                .size(23.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = entry.label,
                            color = if (active) colors.text.copy(alpha = labelAlpha) else colors.textMuted.copy(alpha = labelAlpha),
                            fontSize = 9.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationContent(
    state: HulkUiState,
    isTv: Boolean,
    navigationMemory: NavigationMemoryStore,
    homeContent: HomeContentSnapshot?,
    favoriteSnapshot: CatalogFavoriteSnapshot,
    isFavorite: (ContentItem) -> Boolean,
    onSelectCategory: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onOpenHistory: (HistoryEntry) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onRefresh: () -> Unit,
    onOpenNotifications: () -> Unit,
    onGrowthAction: (GrowthDestination) -> Unit,
    onSelectDestination: (MainDestination) -> Unit,
    onClearHistory: () -> Unit,
    onPlayDownload: (OfflineDownload) -> Unit,
    onDeleteDownload: (OfflineDownload) -> Unit,
    onRetryDownload: (OfflineDownload) -> Unit,
    onToggleWifiOnly: () -> Unit,
    onToggleDownloadSchedule: () -> Unit,
    onCycleConcurrentDownloads: () -> Unit,
    onToggleEpisodeNotificationMaster: () -> Unit,
    onCycleDownloadPriority: (OfflineDownload) -> Unit,
    onRefreshAccount: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onLogout: () -> Unit,
    initialAllFocusRequester: FocusRequester? = null,
    initialAllFocusPending: Boolean = false,
    downloadsExitFocusRequester: FocusRequester? = null,
) {
    when (state.destination) {
        MainDestination.HOME -> CinemaHomeScreen(
            state = state,
            isTv = isTv,
            navigationMemory = navigationMemory,
            homeContent = requireNotNull(homeContent),
            isFavorite = isFavorite,
            onOpen = onOpen,
            onOpenHistory = onOpenHistory,
            onToggleFavorite = onToggleFavorite,
            onRefresh = onRefresh,
            onOpenNotifications = onOpenNotifications,
            onGrowthAction = onGrowthAction,
            onOpenDownloads = { onSelectDestination(MainDestination.DOWNLOADS) },
        )
        MainDestination.LIVE -> LiveCatalogScreen(
            state = state,
            isTv = isTv,
            navigationMemory = navigationMemory,
            isFavorite = isFavorite,
            onSelectCategory = onSelectCategory,
            onSearch = onSearch,
            onOpen = onOpen,
            onToggleFavorite = onToggleFavorite,
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
            onRetry = onRetryDownload,
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
            onToggleWifiOnly = onToggleWifiOnly,
            onToggleDownloadSchedule = onToggleDownloadSchedule,
            onCycleConcurrentDownloads = onCycleConcurrentDownloads,
            notificationMasterEnabled = state.episodeNotificationsEnabled,
            episodeNotificationsAvailable = state.operations.features.episodeNotificationsEnabled,
            onToggleEpisodeNotificationMaster = onToggleEpisodeNotificationMaster,
            onGrowthAction = onGrowthAction,
            onLogout = onLogout,
        )
    }
}

@Composable
private fun CinemaHomeScreen(
    state: HulkUiState,
    isTv: Boolean,
    navigationMemory: NavigationMemoryStore,
    homeContent: HomeContentSnapshot,
    isFavorite: (ContentItem) -> Boolean,
    onOpen: (ContentItem) -> Unit,
    onOpenHistory: (HistoryEntry) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onRefresh: () -> Unit,
    onOpenNotifications: () -> Unit,
    onGrowthAction: (GrowthDestination) -> Unit,
    onOpenDownloads: () -> Unit,
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
    val remembered = navigationMemory.position(MainDestination.HOME)
    val renewalBanner = evaluateRenewalBanner(
        growth = state.operations.growth,
        expiresAtEpochSeconds = state.account?.expiresAtEpochSeconds,
    )

    var rowCursor = 1
    val renewalBannerRow = if (renewalBanner != null) rowCursor++ else -1
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
        "renewal-banner" to renewalBannerRow, "continue" to continueRow, "downloads" to downloadsRow, "because-watched" to becauseRow,
        "recommended" to recommendedRow, "recent-movies" to moviesRow, "recent-series" to seriesRow, "top-movies" to topMoviesRow,
        "top-series" to topSeriesRow, "last-live" to lastLiveRow, "popular-live" to popularLiveRow,
    )
    val initialRow = rowIndexByKey[remembered.rowKey]?.takeIf { it >= 0 } ?: 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialRow)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (isTv) 32.dp else 48.dp),
        verticalArrangement = Arrangement.spacedBy(if (isTv) 24.dp else 17.dp),
    ) {
        item {
            if (featured != null) {
                val heroRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    if (remembered.rowKey == "hero") { runCatching { heroRequester.requestFocus() } }
                }
                CinemaHero(
                    featured, isTv, isFavorite(featured), { onOpen(featured) },
                    { onToggleFavorite(featured) }, onRefresh, loading,
                    unreadNotificationCount = state.unreadNotificationCount,
                    onOpenNotifications = onOpenNotifications,
                    watchModifier = Modifier.restoreFocus(remembered.rowKey == "hero", heroRequester),
                    onFocused = { navigationMemory.save(MainDestination.HOME, "${featured.type}:${featured.id}", 0, "hero", 0) },
                )
            } else {
                HomePlaceholder(
                    loading = loading,
                    onRefresh = onRefresh,
                    isTv = isTv,
                    unreadNotificationCount = state.unreadNotificationCount,
                    onOpenNotifications = onOpenNotifications,
                )
            }
        }
        if (renewalBanner != null) {
            item {
                val bannerRequester = remember { FocusRequester() }
                LaunchedEffect(remembered.rowKey, renewalBanner) {
                    if (remembered.rowKey == "renewal-banner") {
                        runCatching { bannerRequester.requestFocus() }
                    }
                }
                HomeSectionPadding(isTv) {
                    RenewalBanner(
                        content = renewalBanner,
                        isTv = isTv,
                        onClick = { onGrowthAction(GrowthDestination.RENEWAL) },
                        modifier = Modifier.restoreFocus(
                            remembered.rowKey == "renewal-banner",
                            bannerRequester,
                        ),
                        onFocused = {
                            navigationMemory.save(
                                MainDestination.HOME,
                                "renewal-banner",
                                0,
                                "renewal-banner",
                                0,
                            )
                        },
                    )
                }
            }
        }
        if (state.errorMessage != null) {
            item { ErrorNotice(state.errorMessage, Modifier.padding(horizontal = if (isTv) 25.dp else 14.dp)) }
        }
        if (continueWatching.isNotEmpty()) {
            item { HomeSectionPadding(isTv) { HistorySection("متابعة المشاهدة", "continue", continueRow, continueWatching, isTv, navigationMemory, onOpenHistory) } }
        }
        if (activeDownloads.isNotEmpty()) item { HomeSectionPadding(isTv) { ActiveDownloadsSection(activeDownloads, isTv, onOpenDownloads) } }
        if (becauseYouWatched.isNotEmpty()) {
            item { HomeSectionPadding(isTv) { PosterSection("لانك شاهدت", "because-watched", becauseRow, becauseYouWatched, isTv, navigationMemory, isFavorite, onOpen, onToggleFavorite) } }
        }
        if (suggested.isNotEmpty()) {
            item { HomeSectionPadding(isTv) { PosterSection("مقترح لك", "recommended", recommendedRow, suggested, isTv, navigationMemory, isFavorite, onOpen, onToggleFavorite) } }
        }
        if (homeMovies.isNotEmpty()) {
            item { HomeSectionPadding(isTv) { PosterSection("احدث اضافات HULK — افلام", "recent-movies", moviesRow, homeMovies, isTv, navigationMemory, isFavorite, onOpen, onToggleFavorite) } }
        }
        if (homeSeries.isNotEmpty()) {
            item { HomeSectionPadding(isTv) { PosterSection("احدث اضافات HULK — مسلسلات", "recent-series", seriesRow, homeSeries, isTv, navigationMemory, isFavorite, onOpen, onToggleFavorite) } }
        }
        if (popularMovies.isNotEmpty()) {
            item { HomeSectionPadding(isTv) { PosterSection("الاعلى تقييما — افلام", "top-movies", topMoviesRow, popularMovies, isTv, navigationMemory, isFavorite, onOpen, onToggleFavorite) } }
        }
        if (popularSeries.isNotEmpty()) {
            item { HomeSectionPadding(isTv) { PosterSection("الاعلى تقييما — مسلسلات", "top-series", topSeriesRow, popularSeries, isTv, navigationMemory, isFavorite, onOpen, onToggleFavorite) } }
        }
        if (lastLive != null) {
            item { HomeSectionPadding(isTv) { HistorySection("اخر قناة شاهدتها", "last-live", lastLiveRow, listOf(lastLive), isTv, navigationMemory, onOpenHistory) } }
        }
        if (suggestedLive.isNotEmpty()) {
            item { HomeSectionPadding(isTv) { PosterSection("قنوات مقترحة لك", "popular-live", popularLiveRow, suggestedLive, isTv, navigationMemory, isFavorite, onOpen, onToggleFavorite) } }
        }
    }
}

@Composable
private fun RenewalBanner(
    content: RenewalBannerContent,
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
            .then(if (isTv) Modifier else Modifier.padding(horizontal = 14.dp))
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
            .focusable()
            .padding(
                horizontal = if (isTv) 22.dp else 16.dp,
                vertical = if (isTv) 15.dp else 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 16.dp else 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (isTv) 48.dp else 42.dp)
                .clip(CircleShape)
                .background(colors.gold.copy(alpha = if (focused) .22f else .12f))
                .border(1.dp, colors.gold.copy(alpha = .38f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                tint = colors.goldBright,
                modifier = Modifier.size(if (isTv) 25.dp else 22.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = content.title,
                color = colors.text,
                fontSize = if (isTv) 19.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = content.subtitle,
                color = colors.textMuted,
                fontSize = if (isTv) 13.sp else 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = if (isTv) "اضغط OK" else "فتح",
            color = colors.goldBright,
            fontSize = if (isTv) 12.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(colors.gold.copy(alpha = .12f))
                .padding(horizontal = 11.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun ActiveDownloadsSection(
    downloads: List<OfflineDownload>,
    isTv: Boolean,
    onOpenDownloads: () -> Unit,
) {
    val colors = LocalHulkColors.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Download, contentDescription = null, tint = colors.goldBright, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(8.dp))
            Text("التنزيلات الجارية", color = colors.text, fontSize = if (isTv) 20.sp else 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(downloads, key = OfflineDownload::downloadId) { item ->
                var focused by remember(item.downloadId) { mutableStateOf(false) }
                val shape = RoundedCornerShape(15.dp)
                Column(
                    modifier = Modifier
                        .width(if (isTv) 270.dp else 220.dp)
                        .clip(shape)
                        .background(if (focused) colors.gold.copy(alpha = .12f) else colors.surface)
                        .border(if (focused) 2.dp else 1.dp, if (focused) colors.goldBright else colors.line.copy(alpha = .45f), shape)
                        .onFocusChanged { focused = it.isFocused }
                        .clickable(role = Role.Button, onClick = onOpenDownloads)
                        .padding(13.dp),
                ) {
                    Text(item.title, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = .12f))) {
                        Box(Modifier.fillMaxWidth(item.progress.coerceIn(0f, 1f)).fillMaxHeight().background(colors.goldBright))
                    }
                    Spacer(Modifier.height(7.dp))
                    val status = when (item.status) {
                        OfflineStatus.DOWNLOADING -> "${(item.progress * 100).toInt()}%  •  ${formatTransferRate(item.bytesPerSecond)}"
                        OfflineStatus.PAUSED -> "متوقف مؤقتا"
                        OfflineStatus.WAITING_NETWORK -> "بانتظار الشبكة"
                        OfflineStatus.WAITING_SCHEDULE -> "مجدول"
                        OfflineStatus.WAITING_STORAGE -> "بانتظار مساحة"
                        OfflineStatus.CHECKING -> "جاري الفحص"
                        else -> "في قائمة الانتظار"
                    }
                    Text(status, color = if (focused) colors.goldBright else colors.textMuted, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun HomeSectionPadding(isTv: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = if (isTv) TV_PAGE_GUTTER else 0.dp)) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
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
    watchModifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    val colors = LocalHulkColors.current
    val configuration = LocalConfiguration.current
    val isPortraitPhone = !isTv && configuration.screenWidthDp < 600 && configuration.screenHeightDp > configuration.screenWidthDp
    val heroHeight = when {
        isTv -> 410.dp
        isPortraitPhone -> (configuration.screenHeightDp * .58f).coerceIn(420f, 520f).dp
        else -> 288.dp
    }
    val image = item.backdropUrl ?: item.posterUrl
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .background(Color(0xFF0A0B08)),
    ) {
        if (!image.isNullOrBlank()) {
            AsyncImage(
                model = image,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            BrandLogo(Modifier.align(Alignment.Center).size(190.dp).graphicsLayer { alpha = .38f })
        }
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
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = .18f), colors.background.copy(alpha = .94f)),
                ),
            ),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .then(if (isTv) Modifier else Modifier.statusBarsPadding())
                .padding(
                    horizontal = if (isTv) 26.dp else 18.dp,
                    vertical = if (isTv) 18.dp else 10.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("الرئيسية", color = colors.text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("توصيات ومحتوى جديد", color = colors.textMuted, fontSize = 11.sp)
            }
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(if (isTv) .64f else 1f)
                .padding(
                    start = if (isTv) 27.dp else 18.dp,
                    end = if (isTv) 27.dp else 18.dp,
                    bottom = if (isTv) 28.dp else 24.dp,
                ),
        ) {
            Text("مختار لك", color = colors.goldBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(
                item.name,
                color = Color.White,
                fontSize = if (isTv) 36.sp else 28.sp,
                lineHeight = if (isTv) 42.sp else 34.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(if (isTv) 8.dp else 9.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                maxItemsInEachRow = if (isTv) 5 else 3,
            ) {
                item.rating?.let { InfoPill("★ $it") }
                item.genre?.takeIf(String::isNotBlank)?.let { InfoPill(it.take(27)) }
                HomeHeroTechnicalPills(item, isTv = true)
            }
            item.plot?.takeIf(String::isNotBlank)?.let {
                Spacer(Modifier.height(if (isTv) 8.dp else 10.dp))
                Text(it, color = Color(0xFFD4D0C5), fontSize = 12.sp, lineHeight = if (isTv) 17.sp else 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(if (isTv) 12.dp else 15.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                FocusButton(
                    if (item.type == ContentType.SERIES) "عرض الحلقات" else "شاهد الان",
                    onOpen,
                    modifier = watchModifier,
                    compact = true,
                    onFocused = onFocused,
                )
                FocusButton(if (isFavorite) "★ في قائمتي" else "+ قائمتي", onToggleFavorite, primary = false, compact = true)
            }
        }
    }
}

@Composable
private fun HomePlaceholder(
    loading: Boolean,
    onRefresh: () -> Unit,
    isTv: Boolean,
    unreadNotificationCount: Int,
    onOpenNotifications: () -> Unit,
) {
    val colors = LocalHulkColors.current
    Box(
        Modifier.fillMaxWidth().height(if (isTv) 360.dp else 270.dp).background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        NotificationBellButton(
            unreadCount = unreadNotificationCount,
            isTv = isTv,
            onClick = onOpenNotifications,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .then(if (isTv) Modifier else Modifier.statusBarsPadding())
                .padding(
                    horizontal = if (isTv) 26.dp else 18.dp,
                    vertical = if (isTv) 18.dp else 10.dp,
                ),
        )
        if (loading) LoadingRing(label = "نجهز احدث الاضافات…")
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("سيظهر احدث المحتوى هنا", color = colors.textMuted)
            Spacer(Modifier.height(12.dp))
            FocusButton("تحديث", onRefresh, compact = true)
        }
    }
}

@Composable
private fun PosterSection(
    title: String,
    rowKey: String,
    rowIndex: Int,
    content: List<ContentItem>,
    isTv: Boolean,
    navigationMemory: NavigationMemoryStore,
    isFavorite: (ContentItem) -> Boolean,
    onOpen: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
) {
    val colors = LocalHulkColors.current
    val remembered = navigationMemory.position(MainDestination.HOME)
    val targetIndex = if (remembered.rowKey == rowKey) remembered.itemIndex.coerceIn(0, content.lastIndex.coerceAtLeast(0)) else 0
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = targetIndex)
    val targetRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (remembered.rowKey == rowKey && content.isNotEmpty()) {
            rowState.scrollToItem(targetIndex)
            runCatching { targetRequester.requestFocus() }
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
                UniversalPosterCard(
                    item = item,
                    isFavorite = isFavorite(item),
                    onClick = { onOpen(item) },
                    modifier = Modifier.width(if (isTv) 136.dp else 111.dp).restoreFocus(restore, targetRequester),
                    onLongClick = { onToggleFavorite(item) },
                    onFocused = { navigationMemory.save(MainDestination.HOME, itemKey, index, rowKey, rowIndex) },
                )
            }
        }
    }
}

@Composable
private fun HistorySection(
    title: String,
    rowKey: String,
    rowIndex: Int,
    entries: List<HistoryEntry>,
    isTv: Boolean,
    navigationMemory: NavigationMemoryStore,
    onOpen: (HistoryEntry) -> Unit,
) {
    val colors = LocalHulkColors.current
    val remembered = navigationMemory.position(MainDestination.HOME)
    val targetIndex = if (remembered.rowKey == rowKey) remembered.itemIndex.coerceIn(0, entries.lastIndex.coerceAtLeast(0)) else 0
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = targetIndex)
    val targetRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (remembered.rowKey == rowKey && entries.isNotEmpty()) {
            rowState.scrollToItem(targetIndex)
            runCatching { targetRequester.requestFocus() }
        }
    }
    val polishContinueWatching = isTv && rowKey == "continue"
    Column {
        Text(title, color = colors.text, fontSize = if (isTv) 20.sp else 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(if (polishContinueWatching) 11.dp else 10.dp))
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(
                horizontal = if (polishContinueWatching) 8.dp else 5.dp,
                vertical = if (polishContinueWatching) 10.dp else 7.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(if (polishContinueWatching) 16.dp else 14.dp),
        ) {
            itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
                val restore = remembered.rowKey == rowKey &&
                    (remembered.itemKey == entry.key || (remembered.itemKey.isBlank() && index == targetIndex))
                HistoryCard(
                    entry,
                    { onOpen(entry) },
                    Modifier
                        .width(if (polishContinueWatching) 226.dp else if (isTv) 214.dp else 190.dp)
                        .restoreFocus(restore, targetRequester),
                    onFocused = { navigationMemory.save(MainDestination.HOME, entry.key, index, rowKey, rowIndex) },
                )
            }
        }
    }
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
    val catalog = state.catalogs[type]
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
    var nextCategoryContentFocusRequestId by remember(destination) { mutableLongStateOf(0L) }
    var categoryContentFocusRequest by remember(destination) { mutableStateOf<CategoryContentFocusRequest?>(null) }
    var armedCategoryContentFocusRequestId by remember(destination) { mutableLongStateOf(0L) }
    val categoryFocusRestoreController = remember(destination) { CategoryFocusRestoreController() }
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
            )
            if (state.errorMessage != null) { Spacer(Modifier.height(10.dp)); ErrorNotice(state.errorMessage) }
            Spacer(Modifier.height(11.dp))
            ReorderableCatalogCategoryBar(
                type = type,
                categories = catalog?.categories.orEmpty(),
                selectedId = state.selectedCategoryId,
                onSelect = selectCategoryAndEnterContent,
                isTv = isTv,
                focusRestoreController = categoryFocusRestoreController,
                initialAllFocusRequester = initialAllFocusRequester,
                initialAllFocusPending = initialAllFocusPending,
            )
            CatalogInteractionHints(isTv)
            Spacer(Modifier.height(9.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (model == null) {
                LoadingRing(label = "جاري تجهيز $title…", modifier = Modifier.align(Alignment.Center))
            } else if (showingContinue && continueWatching.isNotEmpty()) {
                HistoryGrid(
                    entries = continueWatching,
                    isTv = isTv,
                    destination = destination,
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
                )
            } else if (showingContinue) {
                EmptyState("لا توجد مشاهدة غير مكتملة في $title")
            } else if (catalog == null && type in state.loadingTypes) {
                LoadingRing(label = "جاري تحميل $title…", modifier = Modifier.align(Alignment.Center))
            } else if (visible.isEmpty()) {
                EmptyState("لا توجد نتائج مطابقة")
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

@Composable
private fun LiveCatalogScreen(
    state: HulkUiState,
    isTv: Boolean,
    navigationMemory: NavigationMemoryStore,
    isFavorite: (ContentItem) -> Boolean,
    onSelectCategory: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onRefresh: () -> Unit,
    initialAllFocusRequester: FocusRequester? = null,
    initialAllFocusPending: Boolean = false,
) {
    val colors = LocalHulkColors.current
    val catalog = state.catalogs[ContentType.LIVE]
    val visible = remember(catalog, state.selectedCategoryId, state.searchQuery, state.favorites) {
        catalog?.items.orEmpty().filter { item ->
            categoryMatches(item, state.selectedCategoryId, isFavorite) &&
                item.matchesSearch(state.searchQuery)
        }
    }
    val remembered = navigationMemory.position(MainDestination.LIVE)
    val rememberedIndex = remembered.itemIndex.coerceIn(0, visible.lastIndex.coerceAtLeast(0))
    val previewState = remember(catalog, state.selectedCategoryId) { mutableStateOf<ContentItem?>(null) }
    val channelRequester = remember { FocusRequester() }
    val playRequester = remember { FocusRequester() }
    val favoriteRequester = remember { FocusRequester() }
    val categoryFocusRestoreController = remember { CategoryFocusRestoreController() }
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
    LaunchedEffect(listState, visible) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            visible.getOrNull(index)?.let { navigationMemory.save(MainDestination.LIVE, "${it.type}:${it.id}", index) }
        }
    }
    LaunchedEffect(
        visible,
        state.selectedCategoryId,
        state.searchQuery,
        remembered.itemKey,
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
            )
            if (state.errorMessage != null) { Spacer(Modifier.height(9.dp)); ErrorNotice(state.errorMessage) }
            Spacer(Modifier.height(10.dp))
            ReorderableLiveCategoryBar(
                categories = catalog?.categories.orEmpty(),
                items = catalog?.items.orEmpty(),
                selectedId = state.selectedCategoryId,
                onSelect = selectCategoryAndEnterContent,
                isTv = isTv,
                focusRestoreController = categoryFocusRestoreController,
                initialAllFocusRequester = initialAllFocusRequester,
                initialAllFocusPending = initialAllFocusPending,
            )
            LiveInteractionHints(isTv)
            Spacer(Modifier.height(8.dp))
        }
        if (catalog == null && ContentType.LIVE in state.loadingTypes) {
            LoadingRing(label = "جاري تحميل القنوات…", modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 90.dp))
        } else if (visible.isEmpty()) {
            EmptyState("لا توجد قنوات مطابقة")
        } else if (isTv) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Box(Modifier.padding(start = 12.dp, bottom = 12.dp)) {
                    Column(
                        modifier = Modifier.width(408.dp).fillMaxHeight().clip(RoundedCornerShape(18.dp))
                            .background(Color(0xA30D0E0B)).padding(9.dp),
                    ) {
                        Text("القنوات", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp))
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
                                val key = "${channel.type}:${channel.id}"
                                val restore = key == remembered.itemKey || (remembered.itemKey.isBlank() && index == rememberedIndex)
                                val selected by remember(previewState, channel.id) {
                                    derivedStateOf { isLivePreviewSelected(previewState.value, channel) }
                                }
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
                                    isFavorite = isFavorite(channel),
                                    onLongClick = { onToggleFavorite(channel) },
                                )
                            }
                        }
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(bottom = 12.dp),
                ) {
                    LivePreviewStage(
                        previewState = previewState,
                        isFavorite = isFavorite,
                        channelRequester = channelRequester,
                        playRequester = playRequester,
                        favoriteRequester = favoriteRequester,
                        onOpen = onOpen,
                        onToggleFavorite = onToggleFavorite,
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
                        isFavorite = isFavorite(channel),
                        onLongClick = { onToggleFavorite(channel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LivePreviewStage(
    previewState: State<ContentItem?>,
    isFavorite: (ContentItem) -> Boolean,
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
        isFavorite = preview?.let(isFavorite) == true,
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
            modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp))
                .background(Brush.radialGradient(listOf(colors.gold.copy(alpha = .12f), Color(0xFF090A08)))),
        ) {
            if (item == null) Text("اختر قناة", color = colors.textMuted, modifier = Modifier.align(Alignment.Center))
            else {
                ChannelLogo(item, Modifier.align(Alignment.Center).size(145.dp))
                Box(Modifier.align(Alignment.TopStart).padding(17.dp).clip(CircleShape).background(Color(0xFFD3262E)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (item != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(
                    "اضغط تشغيل القناة لعرضها بملء الشاشة",
                    color = colors.textMuted,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Text("على الهواء الان", color = colors.goldBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(item.name, color = colors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = TV_PAGE_GUTTER,
                            bottom = TV_LIVE_ACTION_INSET,
                        ),
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
                        )
                        FocusButton(
                            if (isFavorite) "★ في المفضلة" else "+ المفضلة", onToggleFavorite,
                            modifier = Modifier.weight(1f).height(50.dp).focusRequester(favoriteRequester).focusProperties {
                                left = channelRequester; right = playRequester
                            }, primary = false, compact = true,
                        )
                    }
                }
            }
        }
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
    onRefresh: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val content = remember(state.catalogs, state.favorites) {
        state.catalogs.values.flatMap { it.items }.filter(isFavorite).distinctBy { "${it.type}:${it.id}" }
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
                        Modifier.padding(horizontal = 14.dp, top = tvSafeInsets.verticalDp.dp)
                    } else {
                        Modifier
                    },
                ),
        ) {
            PageTitle("قائمتي", "كل ما حفظته في مكان واحد", content.size, Icons.Rounded.Star, isTv)
            Spacer(Modifier.height(18.dp))
        }
        if (isTv && content.isEmpty()) {
            FavoritesFocusFallback(
                loading = state.loadingTypes.isNotEmpty(),
                onRefresh = onRefresh,
            )
        } else if (content.isEmpty() && state.loadingTypes.isEmpty()) {
            EmptyState("لم تضف اي محتوى الى قائمتك بعد")
        } else {
            ContentGrid(content, isTv, MainDestination.FAVORITES, navigationMemory, isFavorite, onOpen, onToggleFavorite)
        }
    }
}

@Composable
private fun FavoritesFocusFallback(
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    val colors = LocalHulkColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            LoadingRing(label = "جاري تجهيز قائمتك…")
        } else {
            BrandLogo(Modifier.size(70.dp).graphicsLayer { alpha = .65f })
            Spacer(Modifier.height(10.dp))
            Text("لم تضف اي محتوى الى قائمتك بعد", color = colors.textMuted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(14.dp))
        FocusButton("تحديث القائمة", onRefresh, compact = true)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadsScreen(
    downloads: List<OfflineDownload>,
    settings: DownloadSettings,
    isTv: Boolean,
    navigationMemory: NavigationMemoryStore,
    onPlay: (OfflineDownload) -> Unit,
    onDelete: (OfflineDownload) -> Unit,
    onRetry: (OfflineDownload) -> Unit,
    onToggleWifiOnly: () -> Unit,
    onToggleSchedule: () -> Unit,
    onCycleConcurrent: () -> Unit,
    onCyclePriority: (OfflineDownload) -> Unit,
    exitFocusRequester: FocusRequester?,
) {
    val completed = downloads.count { it.status == OfflineStatus.COMPLETED }
    val active = downloads.count {
        it.status == OfflineStatus.QUEUED ||
            it.status == OfflineStatus.CHECKING ||
            it.status == OfflineStatus.DOWNLOADING ||
            it.status == OfflineStatus.PAUSED ||
            it.status == OfflineStatus.WAITING_SCHEDULE ||
            it.status == OfflineStatus.WAITING_NETWORK ||
            it.status == OfflineStatus.WAITING_STORAGE
    }
    val storedBytes = downloads
        .filter { it.status == OfflineStatus.COMPLETED }
        .sumOf { it.totalBytes.coerceAtLeast(it.bytesDownloaded).coerceAtLeast(0L) }
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

    val context = LocalContext.current
    val availableBytes = remember(downloads) {
        (context.getExternalFilesDir(null) ?: context.filesDir).usableSpace.coerceAtLeast(0L)
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
        val preferredCardWidth = when {
            isTv && compactHeight -> 218.dp
            isTv -> 236.dp
            availableContentWidth < 480.dp -> 170.dp
            availableContentWidth < 600.dp -> 184.dp
            availableContentWidth < 840.dp -> 205.dp
            else -> 218.dp
        }
        val columnCount = (
            (availableContentWidth.value + gridGap.value) /
                (preferredCardWidth.value + gridGap.value)
        ).toInt().coerceAtLeast(1)
        val graph = remember(downloadIds, columnCount) {
            DownloadTvFocusGraph(downloadIds, columnCount)
        }
        val downloadsState = rememberLazyGridState(
            initialFirstVisibleItemIndex = rememberedIndex,
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
            return item.offset.y >= layoutInfo.viewportStartOffset &&
                item.offset.y + item.size.height <= layoutInfo.viewportEndOffset
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
                downloadsState.scrollToItem(cardIndex)
            }
            if (!handle.isPlaced && !handle.awaitPlaced()) return false
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
            current: DownloadTvFocusTarget,
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
            val currentCard = current as? DownloadTvFocusTarget.CardAction
            val targetCard = target as? DownloadTvFocusTarget.CardAction
            val sameCard = currentCard != null && targetCard != null &&
                currentCard.downloadId == targetCard.downloadId
            if (sameCard && handle.isPlaced) {
                val focusRequested = requestAttachedFocus(target)
                onSettled?.invoke(focusRequested)
                return focusRequested
            }
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
            requestFocusMove(current, target, graph)
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
            requestFocusMove(current, fallback, graph) { onDelete(item) }
        }

        LaunchedEffect(graph) {
            val runningTransaction = focusTransaction.job?.takeIf { it.isActive }
            runningTransaction?.cancelAndJoin()
            if (focusTransaction.job === runningTransaction) {
                focusTransaction.job = null
                focusTransaction.target = null
            }
            val previousGraph = focusHistory.graph
            focusHistory.graph = graph
            focusedTarget?.let { current ->
                val fallback = downloadFocusFallback(current, previousGraph, graph)
                if (fallback != current) requestFocusMove(current, fallback, graph)
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
                completedCount = completed,
                activeCount = active,
                storedBytes = storedBytes,
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
            Spacer(Modifier.height(if (compactHeight) 8.dp else 12.dp))
            if (downloads.isEmpty()) {
                DownloadsEmptyState(
                    isTv = isTv,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(graph.columns),
                    state = downloadsState,
                    horizontalArrangement = Arrangement.spacedBy(gridGap),
                    verticalArrangement = Arrangement.spacedBy(gridGap),
                    contentPadding = PaddingValues(bottom = if (isTv) 12.dp else 20.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    itemsIndexed(downloads, key = { _, item -> item.downloadId }) { _, item ->
                        val requesters = checkNotNull(cardFocusRegistry[item.downloadId])
                        DownloadCard(
                            item = item,
                            isTv = isTv,
                            compactHeight = compactHeight,
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
                            onRetry = onRetry,
                            onCyclePriority = onCyclePriority,
                            modifier = Modifier.fillMaxWidth(),
                        )
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
    completedCount: Int,
    activeCount: Int,
    storedBytes: Long,
    availableBytes: Long,
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
    val summary = buildList {
        add(if (itemCount == 0) "لا توجد عناصر محفوظة" else "$itemCount عنصر")
        if (completedCount > 0) add("$completedCount مكتمل")
        if (activeCount > 0) add("$activeCount نشط")
        if (storedBytes > 0L) add("${formatBytes(storedBytes)} محفوظ")
        add("${formatBytes(availableBytes)} متاح")
    }.joinToString("  •  ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isTv) 4.dp else 2.dp,
                vertical = if (compactHeight) 4.dp else 7.dp,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                Icons.Rounded.Download,
                contentDescription = null,
                tint = colors.goldBright,
                modifier = Modifier.size(if (isTv) 27.dp else 23.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "التنزيلات",
                    color = colors.text,
                    fontSize = if (isTv) 24.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    summary,
                    color = colors.textMuted,
                    fontSize = if (isTv) 11.sp else 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(if (compactHeight) 7.dp else 10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            modifier = if (isTv) Modifier.focusGroup() else Modifier,
        ) {
            val controlWidth = if (isTv) 112.dp else 98.dp
            val controlHeight = if (isTv) 38.dp else 48.dp
            FocusButton(
                if (settings.wifiOnly) "WiFi فقط  ✓" else "كل الشبكات",
                onToggleWifiOnly,
                primary = settings.wifiOnly,
                compact = true,
                outlined = !settings.wifiOnly,
                scaleOnFocus = false,
                textSizeSp = if (isTv) 11 else 10,
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
                textSizeSp = if (isTv) 11 else 10,
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
                "متزامنة  ${settings.concurrentDownloads}",
                onCycleConcurrent,
                primary = false,
                compact = true,
                outlined = true,
                scaleOnFocus = false,
                textSizeSp = if (isTv) 11 else 10,
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
    isTv: Boolean,
    compactHeight: Boolean,
    focusRequesters: DownloadCardFocusRequesters,
    onFocused: (DownloadFocusSlot) -> Unit,
    onDirection: (DownloadFocusSlot, DownloadFocusMove) -> Boolean,
    onPlay: (OfflineDownload) -> Unit,
    onDelete: (OfflineDownload) -> Unit,
    onRetry: (OfflineDownload) -> Unit,
    onCyclePriority: (OfflineDownload) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    TrackDownloadFocusHandle(focusRequesters.primary, isTv)
    TrackDownloadFocusHandle(focusRequesters.priority, isTv)
    TrackDownloadFocusHandle(focusRequesters.cancel, isTv)
    val shape = RoundedCornerShape(if (isTv) 15.dp else 14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (focused) {
                    colors.gold.copy(alpha = .10f)
                } else {
                    colors.surface.copy(alpha = .92f)
                },
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) colors.goldBright else colors.line.copy(alpha = .46f),
                shape,
            )
            .onFocusChanged { focused = it.hasFocus }
            .then(if (isTv) Modifier.focusGroup() else Modifier)
            .padding(if (isTv) 9.dp else 8.dp),
    ) {
        DownloadArtwork(
            item = item,
            isTv = isTv,
            compactHeight = compactHeight,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(if (compactHeight) 7.dp else 9.dp))
        DownloadDetails(
            item = item,
            isTv = isTv,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(if (compactHeight) 7.dp else 9.dp))
        DownloadCardActions(
            item = item,
            isTv = isTv,
            focusRequesters = focusRequesters,
            onFocused = onFocused,
            onDirection = onDirection,
            onPlay = onPlay,
            onDelete = onDelete,
            onRetry = onRetry,
            onCyclePriority = onCyclePriority,
        )
    }
}

@Composable
private fun DownloadArtwork(
    item: OfflineDownload,
    isTv: Boolean,
    compactHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(if (isTv) 12.dp else 11.dp)
    Box(
        modifier = modifier
            .aspectRatio(if (compactHeight) 2f else 16f / 9f)
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
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .clip(CircleShape)
                .background(colors.background.copy(alpha = .86f))
                .border(1.dp, colors.gold.copy(alpha = .38f), CircleShape)
                .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Text(
                downloadCompactStatusLabel(item.status),
                color = if (item.status == OfflineStatus.COMPLETED) {
                    colors.goldBright
                } else {
                    colors.text
                },
                fontSize = if (isTv) 9.sp else 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DownloadDetails(
    item: OfflineDownload,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val displayTitle = item.seriesTitle ?: item.title
    val metadata = buildList {
        if (item.streamKind == "movie") {
            add("فيلم")
        } else {
            item.season?.let { add("الموسم $it") }
            item.episodeNumber?.let { add("الحلقة $it") }
            if (item.season == null && item.episodeNumber == null && item.title != displayTitle) {
                add(item.title)
            }
        }
        add("أولوية ${priorityLabel(item.priority)}")
    }.joinToString("  •  ")
    Column(modifier) {
        Text(
            displayTitle,
            color = colors.text,
            fontSize = if (isTv) 14.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = if (isTv) 18.sp else 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(min = if (isTv) 36.dp else 34.dp),
        )
        Text(
            metadata,
            color = colors.textMuted,
            fontSize = if (isTv) 9.sp else 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        DownloadProgress(item, isTv)
    }
}

@Composable
private fun DownloadCardActions(
    item: OfflineDownload,
    isTv: Boolean,
    focusRequesters: DownloadCardFocusRequesters,
    onFocused: (DownloadFocusSlot) -> Unit,
    onDirection: (DownloadFocusSlot, DownloadFocusMove) -> Boolean,
    onPlay: (OfflineDownload) -> Unit,
    onDelete: (OfflineDownload) -> Unit,
    onRetry: (OfflineDownload) -> Unit,
    onCyclePriority: (OfflineDownload) -> Unit,
) {
    val primaryLabel = when (item.status) {
        OfflineStatus.COMPLETED -> "تشغيل"
        OfflineStatus.FAILED -> "إعادة"
        OfflineStatus.PAUSED,
        OfflineStatus.WAITING_SCHEDULE,
        OfflineStatus.WAITING_NETWORK,
        OfflineStatus.WAITING_STORAGE,
        -> "استئناف"
        OfflineStatus.QUEUED,
        OfflineStatus.CHECKING,
        OfflineStatus.DOWNLOADING,
        -> "إيقاف"
    }
    val primaryAction = when (item.status) {
        OfflineStatus.COMPLETED -> onPlay
        else -> onRetry
    }
    val actionHeight = if (isTv) 38.dp else 48.dp
    Row(
        modifier = Modifier.fillMaxWidth().height(actionHeight).focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        DownloadActionButton(
            label = primaryLabel,
            text = primaryLabel,
            emphasized = true,
            isTv = isTv,
            onClick = { primaryAction(item) },
            onFocused = { onFocused(DownloadFocusSlot.PRIMARY) },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .applyDownloadTvFocusNode(isTv, focusRequesters.primary) { move ->
                    onDirection(DownloadFocusSlot.PRIMARY, move)
                },
        )
        DownloadActionButton(
            label = "الأولوية ${priorityLabel(item.priority)}",
            icon = Icons.Rounded.Tune,
            selected = item.priority != 0,
            isTv = isTv,
            onClick = { onCyclePriority(item) },
            onFocused = { onFocused(DownloadFocusSlot.PRIORITY) },
            modifier = Modifier
                .size(actionHeight)
                .applyDownloadTvFocusNode(isTv, focusRequesters.priority) { move ->
                    onDirection(DownloadFocusSlot.PRIORITY, move)
                },
        )
        DownloadActionButton(
            label = if (item.status == OfflineStatus.COMPLETED) "حذف التنزيل" else "إلغاء التنزيل",
            icon = Icons.Rounded.DeleteOutline,
            danger = true,
            isTv = isTv,
            onClick = { onDelete(item) },
            onFocused = { onFocused(DownloadFocusSlot.CANCEL) },
            modifier = Modifier
                .size(actionHeight)
                .applyDownloadTvFocusNode(isTv, focusRequesters.cancel) { move ->
                    onDirection(DownloadFocusSlot.CANCEL, move)
                },
        )
    }
}

@Composable
private fun DownloadActionButton(
    label: String,
    isTv: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    emphasized: Boolean = false,
    selected: Boolean = false,
    danger: Boolean = false,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val background = when {
        focused -> colors.gold.copy(alpha = .20f)
        emphasized -> colors.gold.copy(alpha = .11f)
        selected -> colors.gold.copy(alpha = .08f)
        else -> colors.surfaceRaised.copy(alpha = .92f)
    }
    val foreground = when {
        danger -> Color(0xFFFF9B8E)
        emphasized || selected || focused -> colors.goldBright
        else -> colors.text
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) colors.goldBright else colors.line.copy(alpha = .48f),
                shape,
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = if (icon == null) 6.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = label,
                tint = foreground,
                modifier = Modifier.size(if (isTv) 18.dp else 20.dp),
            )
        } else {
            Text(
                text = text.orEmpty(),
                color = foreground,
                fontSize = if (isTv) 11.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DownloadProgress(item: OfflineDownload, isTv: Boolean) {
    val colors = LocalHulkColors.current
    val targetProgress = (
        if (item.status == OfflineStatus.COMPLETED) 1f else item.progress
    ).coerceIn(0f, 1f)
    val progress by animateFloatAsState(targetProgress, label = "downloadProgress")
    val percent = (targetProgress * 100).toInt()
    val sizeLine = when {
        item.status == OfflineStatus.COMPLETED ->
            "${formatBytes(item.totalBytes.coerceAtLeast(item.bytesDownloaded))}  •  ${item.storageLabel}"
        item.totalBytes > 0L ->
            "${formatBytes(item.bytesDownloaded)} / ${formatBytes(item.totalBytes)}"
        item.bytesDownloaded > 0L -> formatBytes(item.bytesDownloaded)
        else -> item.storageLabel
    }
    val detailLine = when {
        item.status == OfflineStatus.DOWNLOADING && item.bytesPerSecond > 0L ->
            "${formatTransferRate(item.bytesPerSecond)}  •  المتبقي ${formatEta(item.etaSeconds)}"
        item.status == OfflineStatus.WAITING_SCHEDULE && item.scheduledAtEpochMs > 0L ->
            "سيبدا ${formatScheduledTime(item.scheduledAtEpochMs)}"
        !item.errorMessage.isNullOrBlank() -> item.errorMessage
        else -> downloadStatusLabel(item.status)
    }
    val detailTextSize = if (isTv) 9.sp else 8.sp
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "\u200E$sizeLine",
            color = colors.textMuted,
            fontSize = detailTextSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (item.totalBytes > 0L || item.status == OfflineStatus.COMPLETED) {
            Text(
                "\u200E$percent%",
                color = colors.goldBright,
                fontSize = detailTextSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = .13f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(colors.goldBright),
        )
    }
    Spacer(Modifier.height(5.dp))
    Text(
        detailLine,
        color = if (item.status == OfflineStatus.FAILED) {
            Color(0xFFFF9B8E)
        } else {
            colors.textMuted
        },
        fontSize = detailTextSize,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun downloadCompactStatusLabel(status: OfflineStatus): String = when (status) {
    OfflineStatus.QUEUED -> "قيد الانتظار"
    OfflineStatus.CHECKING -> "فحص"
    OfflineStatus.DOWNLOADING -> "جار التحميل"
    OfflineStatus.PAUSED -> "متوقف"
    OfflineStatus.WAITING_SCHEDULE -> "مجدول"
    OfflineStatus.WAITING_NETWORK -> "بانتظار الشبكة"
    OfflineStatus.WAITING_STORAGE -> "بانتظار التخزين"
    OfflineStatus.COMPLETED -> "جاهز"
    OfflineStatus.FAILED -> "فشل"
}

private fun downloadStatusLabel(status: OfflineStatus): String = when (status) {
    OfflineStatus.QUEUED -> "في قائمة الانتظار"
    OfflineStatus.CHECKING -> "جاري فحص الحجم والمساحة"
    OfflineStatus.DOWNLOADING -> "جاري التحميل"
    OfflineStatus.PAUSED -> "متوقف مؤقتا"
    OfflineStatus.WAITING_SCHEDULE -> "مجدول للتحميل الليلي"
    OfflineStatus.WAITING_NETWORK -> "بانتظار عودة الشبكة"
    OfflineStatus.WAITING_STORAGE -> "بانتظار وحدة التخزين"
    OfflineStatus.COMPLETED -> "اكتمل وتم التحقق"
    OfflineStatus.FAILED -> "تعذر التحميل"
}

private fun priorityLabel(priority: Int): String = when (priority) {
    1 -> "عالية"
    -1 -> "منخفضة"
    else -> "عادية"
}

private fun formatScheduledTime(epochMs: Long): String =
    SimpleDateFormat("EEE  HH:mm", Locale.forLanguageTag("ar-SA")).format(Date(epochMs))

private fun formatEta(seconds: Long): String {
    if (seconds < 0L) return "يحسب..."
    val minutes = seconds / 60L
    val remainingSeconds = seconds % 60L
    return when {
        minutes >= 60L -> "${minutes / 60L} س ${minutes % 60L} د"
        minutes > 0L -> "$minutes د $remainingSeconds ث"
        else -> "$remainingSeconds ث"
    }
}

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
            UniversalPosterCard(
                item = item,
                isFavorite = isFavorite(item),
                onClick = { onOpen(item) },
                modifier = Modifier
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
                    ),
                onLongClick = { onToggleFavorite(item) },
                onFocused = { navigationMemory.save(destination, key, index) },
            )
        }
    }
}

@Composable
private fun HistoryGrid(
    entries: List<HistoryEntry>,
    isTv: Boolean,
    destination: MainDestination,
    navigationMemory: NavigationMemoryStore,
    onOpen: (HistoryEntry) -> Unit,
    focusFirstItemRequestId: Long = 0L,
    focusContentRequestId: Long = 0L,
    onMoveToCategories: (() -> Boolean)? = null,
) {
    val remembered = navigationMemory.position(destination)
    val targetIndex = if (focusFirstItemRequestId != 0L) {
        0
    } else {
        remembered.itemIndex.coerceIn(0, entries.lastIndex.coerceAtLeast(0))
    }
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = targetIndex)
    LaunchedEffect(gridState, entries, destination) {
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
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(if (isTv) 232.dp else 180.dp),
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 9.dp),
        verticalArrangement = Arrangement.spacedBy(if (isTv) 15.dp else 10.dp),
        contentPadding = PaddingValues(5.dp, 5.dp, 5.dp, 28.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
            val restore = if (focusFirstItemRequestId != 0L) {
                index == 0
            } else {
                remembered.itemKey == entry.key || (remembered.itemKey.isBlank() && index == targetIndex)
            }
            HistoryCard(
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
                onFocused = { navigationMemory.save(destination, entry.key, index) },
            )
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
) {
    val colors = LocalHulkColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = if (isTv) TV_PAGE_GUTTER else 0.dp)
            .then(
                if (isTv && onMoveToCategories != null) {
                    Modifier.onPreviewKeyEvent { event ->
                        event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionDown &&
                            onMoveToCategories()
                    }
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Column(Modifier.width(if (isTv) 185.dp else 105.dp)) {
            Text(title, color = colors.text, fontSize = if (isTv) 27.sp else MOBILE_SECTION_TITLE_SIZE, fontWeight = FontWeight.Bold)
            Text("$resultCount عنصر", color = colors.textMuted, fontSize = if (isTv) 10.sp else MOBILE_SECTION_COUNT_SIZE)
        }
        HulkTextField(query, onSearch, "ابحث في $title…", Modifier.weight(1f).widthIn(max = 630.dp))
        RoundAction(Icons.Rounded.Refresh, "تحديث", onRefresh)
    }
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

@Composable
private fun ReorderableCatalogCategoryBar(
    type: ContentType,
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    isTv: Boolean,
    focusRestoreController: CategoryFocusRestoreController,
    initialAllFocusRequester: FocusRequester? = null,
    initialAllFocusPending: Boolean = false,
) {
    val context = LocalContext.current
    val prefs = remember(type) { context.getSharedPreferences("catalog_category_order_${type.name}", android.content.Context.MODE_PRIVATE) }
    var ids by remember(categories, type) {
        mutableStateOf(prefs.getString("ids", "").orEmpty().split(',').filter { it.isNotBlank() })
    }
    var moving by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ownedAllFocusRequester = remember(type) { FocusRequester() }
    val allFocusRequester = initialAllFocusRequester ?: ownedAllFocusRequester
    val favoritesFocusRequester = remember(type) { FocusRequester() }
    val continueFocusRequester = remember(type) { FocusRequester() }
    val stableCategoryIds = remember(categories, type) { categories.map(Category::id) }
    val categoryFocusRequesters = remember(type, stableCategoryIds) {
        stableCategoryIds.associateWith { FocusRequester() }
    }
    var categoryBarHasFocus by remember(type) { mutableStateOf(false) }
    val ordered = remember(categories, ids) {
        val byId = categories.associateBy { it.id }
        (ids.mapNotNull(byId::get) + categories.filterNot { it.id in ids }).distinctBy { it.id }
    }
    val leadingIds = remember { listOf<String?>(null, FAVORITES_CATEGORY_ID, CONTINUE_CATEGORY_ID) }
    val baseContentPadding = if (isTv) 8.dp else 24.dp
    val sidebarUnderlap = rememberCategorySidebarUnderlap(isTv, baseContentPadding)

    fun selectedFocusTarget(): CategoryFocusTarget? {
        val targetIndex = selectedCategoryFocusIndex(
            selectedId = selectedId,
            leadingIds = leadingIds,
            orderedIds = ordered.map(Category::id),
        ) ?: return null
        val requester = when (selectedId) {
            null -> allFocusRequester
            FAVORITES_CATEGORY_ID -> favoritesFocusRequester
            CONTINUE_CATEGORY_ID -> continueFocusRequester
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

    LaunchedEffect(isTv, selectedId, ordered) {
        if (isTv) return@LaunchedEffect
        val targetIndex = selectedCategoryFocusIndex(
            selectedId = selectedId,
            leadingIds = leadingIds,
            orderedIds = ordered.map(Category::id),
        )
        if (targetIndex != null) {
            val anchorIndex = (targetIndex - 1).coerceAtLeast(0)
            listState.scrollToItem(anchorIndex)
        }
    }

    fun move(id: String, direction: Int) {
        val values = ordered.map { it.id }.toMutableList()
        val from = values.indexOf(id)
        val to = (from + direction).coerceIn(0, values.lastIndex)
        if (from >= 0 && from != to) {
            values.add(to, values.removeAt(from))
            ids = values
            prefs.edit().putString("ids", values.joinToString(",")).apply()
            scope.launch {
                delay(40L)
                val targetIndex = to + 3
                val anchorIndex = (targetIndex - 1).coerceAtLeast(0)
                listState.scrollToItem(anchorIndex)
            }
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
                modifier = Modifier
                    .categoryChipFocus(
                        isTv, null, selectedId, categoryBarHasFocus,
                        allFocusRequester, focusRestoreController,
                        allowInitialEntry = initialAllFocusPending,
                    ),
            )
        }
        item {
            FocusButton(
                "★ المفضلة",
                { onSelect(FAVORITES_CATEGORY_ID) },
                primary = selectedId == FAVORITES_CATEGORY_ID,
                compact = true,
                modifier = Modifier
                    .categoryChipFocus(
                        isTv, FAVORITES_CATEGORY_ID, selectedId, categoryBarHasFocus,
                        favoritesFocusRequester, focusRestoreController,
                    ),
            )
        }
        item {
            FocusButton(
                "▶ استكمال اخر مشاهدة",
                { onSelect(CONTINUE_CATEGORY_ID) },
                primary = selectedId == CONTINUE_CATEGORY_ID,
                compact = true,
                modifier = Modifier
                    .categoryChipFocus(
                        isTv, CONTINUE_CATEGORY_ID, selectedId, categoryBarHasFocus,
                        continueFocusRequester, focusRestoreController,
                    ),
            )
        }
        items(ordered, key = Category::id) { category ->
            LiveCategoryChip(
                category = category,
                representative = null,
                selected = selectedId == category.id,
                moving = moving == category.id,
                onClick = { if (moving == category.id) moving = null else onSelect(category.id) },
                onLongClick = { moving = category.id },
                onMoveLeft = { move(category.id, 1) },
                onMoveRight = { move(category.id, -1) },
                modifier = Modifier
                    .categoryChipFocus(
                        isTv, category.id, selectedId, categoryBarHasFocus,
                        categoryFocusRequesters.getValue(category.id), focusRestoreController,
                    ),
            )
        }
    }
}

@Composable
private fun CatalogInteractionHints(isTv: Boolean) {
    val colors = LocalHulkColors.current
    Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
        Text(
            if (isTv) "ترتيب الفئات: اضغط مطولا OK، حرك بالاسهم، ثم اضغط OK للحفظ" else "لترتيب الفئات: اضغط مطولا على الفئة، اسحبها يمينا او يسارا، ثم اضغط عليها للحفظ",
            color = colors.textMuted,
            fontSize = 9.sp,
        )
        Text(
            if (isTv) "المفضلة: اضغط مطولا OK فوق العنصر" else "المفضلة: اضغط مطولا على العنصر",
            color = colors.textMuted,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun ReorderableLiveCategoryBar(
    categories: List<Category>,
    items: List<ContentItem>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    isTv: Boolean,
    focusRestoreController: CategoryFocusRestoreController,
    initialAllFocusRequester: FocusRequester? = null,
    initialAllFocusPending: Boolean = false,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("live_category_order", android.content.Context.MODE_PRIVATE) }
    var ids by remember(categories) {
        mutableStateOf(prefs.getString("ids", "").orEmpty().split(',').filter { it.isNotBlank() })
    }
    var moving by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ownedAllFocusRequester = remember { FocusRequester() }
    val allFocusRequester = initialAllFocusRequester ?: ownedAllFocusRequester
    val favoritesFocusRequester = remember { FocusRequester() }
    val stableCategoryIds = remember(categories) { categories.map(Category::id) }
    val categoryFocusRequesters = remember(stableCategoryIds) {
        stableCategoryIds.associateWith { FocusRequester() }
    }
    var categoryBarHasFocus by remember { mutableStateOf(false) }
    val ordered = remember(categories, ids) {
        val byId = categories.associateBy { it.id }
        (ids.mapNotNull(byId::get) + categories.filterNot { it.id in ids }).distinctBy { it.id }
    }
    val artworkByCategory = remember(items) {
        items.filter { !it.posterUrl.isNullOrBlank() }
            .groupBy(ContentItem::categoryId)
            .mapValues { (_, channels) -> channels.first() }
    }
    val leadingIds = remember { listOf<String?>(null, FAVORITES_CATEGORY_ID) }
    val baseContentPadding = 8.dp
    val sidebarUnderlap = rememberCategorySidebarUnderlap(isTv, baseContentPadding)

    fun selectedFocusTarget(): CategoryFocusTarget? {
        val targetIndex = selectedCategoryFocusIndex(
            selectedId = selectedId,
            leadingIds = leadingIds,
            orderedIds = ordered.map(Category::id),
        ) ?: return null
        val requester = when (selectedId) {
            null -> allFocusRequester
            FAVORITES_CATEGORY_ID -> favoritesFocusRequester
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

    LaunchedEffect(isTv, selectedId, ordered) {
        if (isTv) return@LaunchedEffect
        val targetIndex = selectedCategoryFocusIndex(
            selectedId = selectedId,
            leadingIds = leadingIds,
            orderedIds = ordered.map(Category::id),
        )
        if (targetIndex != null) {
            val anchorIndex = (targetIndex - 1).coerceAtLeast(0)
            listState.scrollToItem(anchorIndex)
        }
    }

    fun move(id: String, direction: Int) {
        val values = ordered.map { it.id }.toMutableList()
        val from = values.indexOf(id)
        val to = (from + direction).coerceIn(0, values.lastIndex)
        if (from >= 0 && from != to) {
            values.add(to, values.removeAt(from))
            ids = values
            prefs.edit().putString("ids", values.joinToString(",")).apply()
            scope.launch {
                delay(40L)
                val targetIndex = to + 2
                val anchorIndex = (targetIndex - 1).coerceAtLeast(0)
                listState.scrollToItem(anchorIndex)
            }
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
                modifier = Modifier
                    .categoryChipFocus(
                        isTv, null, selectedId, categoryBarHasFocus,
                        allFocusRequester, focusRestoreController,
                        allowInitialEntry = initialAllFocusPending,
                    ),
            )
        }
        item {
            FocusButton(
                "★ المفضلة",
                { onSelect(FAVORITES_CATEGORY_ID) },
                primary = selectedId == FAVORITES_CATEGORY_ID,
                compact = true,
                modifier = Modifier
                    .categoryChipFocus(
                        isTv, FAVORITES_CATEGORY_ID, selectedId, categoryBarHasFocus,
                        favoritesFocusRequester, focusRestoreController,
                    ),
            )
        }
        items(ordered, key = Category::id) { category ->
            if (category.id == LIVE_TV_PRO_MAIN_RECENT_CATEGORY) {
                FocusButton(
                    "▶ استكمال اخر مشاهدة",
                    { onSelect(category.id) },
                    primary = selectedId == category.id,
                    compact = true,
                    modifier = Modifier
                        .categoryChipFocus(
                            isTv, category.id, selectedId, categoryBarHasFocus,
                            categoryFocusRequesters.getValue(category.id), focusRestoreController,
                        ),
                )
            } else {
                LiveCategoryChip(
                    category = category,
                    representative = artworkByCategory[category.id],
                    selected = selectedId == category.id,
                    moving = moving == category.id,
                    onClick = {
                        if (moving == category.id) moving = null else onSelect(category.id)
                    },
                    onLongClick = { moving = category.id },
                    onMoveLeft = { move(category.id, 1) },
                    onMoveRight = { move(category.id, -1) },
                    modifier = Modifier
                        .categoryChipFocus(
                            isTv, category.id, selectedId, categoryBarHasFocus,
                            categoryFocusRequesters.getValue(category.id), focusRestoreController,
                        ),
                )
            }
        }
    }
}

@Composable
private fun LiveCategoryChip(
    category: Category,
    representative: ContentItem?,
    selected: Boolean,
    moving: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    var remoteLongPressHandled by remember { mutableStateOf(false) }
    var selectPressed by remember { mutableStateOf(false) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(selectPressed) {
        if (selectPressed) {
            delay(650L)
            if (selectPressed && !remoteLongPressHandled) {
                remoteLongPressHandled = true
                onLongClick()
            }
        }
    }
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                when {
                    focused -> colors.goldBright
                    selected -> colors.gold
                    moving -> colors.gold.copy(alpha = .30f)
                    else -> Color(0xFF181914)
                },
            )
            .border(
                if (focused || moving) 2.dp else 1.dp,
                if (focused || moving) colors.goldBright else colors.line.copy(alpha = .40f),
                shape,
            )
            .pointerInput(category.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() },
                )
            }
            .pointerInput(category.id, moving) {
                if (moving) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccumulator = 0f },
                        onDragCancel = { dragAccumulator = 0f },
                        onDragEnd = {
                            when {
                                dragAccumulator >= 48f -> onMoveRight()
                                dragAccumulator <= -48f -> onMoveLeft()
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
                        onMoveLeft(); true
                    }
                    moving && event.type == KeyEventType.KeyUp && event.key == Key.DirectionRight -> {
                        onMoveRight(); true
                    }
                    moving && event.type == KeyEventType.KeyDown &&
                        (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) -> true
                    else -> false
                }
            }
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (representative != null) {
            ChannelLogo(representative, Modifier.size(28.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF10110D))
                    .border(1.dp, colors.line.copy(alpha = .35f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                BrandLogo(Modifier.fillMaxSize().padding(4.dp))
            }
        }
        Text(
            text = if (moving) "↔ ${category.name}" else category.name,
            color = if (focused || selected) Color.Black else colors.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun LiveInteractionHints(isTv: Boolean) {
    val colors = LocalHulkColors.current
    Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
        Text(
            if (isTv) "ترتيب الفئات: اضغط مطولا OK، حرك بالاسهم، ثم اضغط OK للحفظ" else "لترتيب الفئات: اضغط مطولا على الفئة، اسحبها يمينا او يسارا، ثم اضغط عليها للحفظ",
            color = colors.textMuted,
            fontSize = 9.sp,
        )
        Text(
            if (isTv) "المفضلة: اضغط مطولا OK فوق القناة" else "المفضلة: اضغط مطولا على القناة",
            color = colors.textMuted,
            fontSize = 9.sp,
        )
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
private fun RoundAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(homeHeaderActionTouchSizeDp().dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(homeHeaderActionVisualSizeDp().dp)
                .clip(CircleShape)
                .background(if (focused) colors.gold else Color.Black.copy(alpha = .46f))
                .border(
                    if (focused) 2.dp else 1.dp,
                    if (focused) colors.goldBright else colors.line,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, description, tint = if (focused) Color.Black else colors.text, modifier = Modifier.size(21.dp))
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

private data class DestinationEntry(val destination: MainDestination, val icon: ImageVector, val label: String)

private val destinations = listOf(
    DestinationEntry(MainDestination.HOME, Icons.Rounded.Home, "الرئيسية"),
    DestinationEntry(MainDestination.LIVE, Icons.Rounded.LiveTv, "البث المباشر"),
    DestinationEntry(MainDestination.MOVIES, Icons.Rounded.Movie, "الافلام"),
    DestinationEntry(MainDestination.SERIES, Icons.Rounded.Tv, "المسلسلات"),
    DestinationEntry(MainDestination.FAVORITES, Icons.Rounded.Favorite, "قائمتي"),
    DestinationEntry(MainDestination.SEARCH, Icons.Rounded.Search, "البحث"),
    DestinationEntry(MainDestination.DOWNLOADS, Icons.Rounded.Download, "التنزيلات"),
    DestinationEntry(MainDestination.SETTINGS, Icons.Rounded.Settings, "الاعدادات"),
)
