package sa.hulksa.player.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
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
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sa.hulksa.player.BuildConfig
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.MainDestination
import sa.hulksa.player.model.AccountInfo
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
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.layout.navigationBarsPadding

private const val WEBSITE_URL = "https://hulksa.com/"
private const val ACCOUNT_URL = "https://hulksa.com/account/login.php"
private const val APPS_URL = "https://hulksa.com/hulk-app/"
private const val SUPPORT_URL = "https://wa.me/966506349935"
private const val FAVORITES_CATEGORY_ID = "__hulk_favorites__"
private const val CONTINUE_CATEGORY_ID = "__hulk_continue__"
private val TV_PAGE_GUTTER = 8.dp
private val TV_LIVE_ACTION_INSET = 8.dp

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

class NavigationMemoryStore {
    private val positions = mutableMapOf<MainDestination, NavigationPosition>()
    private var homeMoviesCatalog: Any? = null
    private var homeSeriesCatalog: Any? = null
    private var homeLiveCatalog: Any? = null
    private var homeHistory: Any? = null
    private var homeFavorites: Any? = null
    private var cachedHome: HomeContentSnapshot? = null
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

    internal fun homeContent(state: HulkUiState): HomeContentSnapshot {
        val movieCatalog = state.catalogs[ContentType.MOVIE]
        val seriesCatalog = state.catalogs[ContentType.SERIES]
        val liveCatalog = state.catalogs[ContentType.LIVE]
        val cached = cachedHome
        if (
            cached != null &&
            homeMoviesCatalog === movieCatalog &&
            homeSeriesCatalog === seriesCatalog &&
            homeLiveCatalog === liveCatalog &&
            homeHistory === state.history &&
            homeFavorites === state.favorites
        ) {
            return cached
        }

        val movies = newest(movieCatalog?.items.orEmpty())
        val series = newest(seriesCatalog?.items.orEmpty())
        val live = liveCatalog?.items.orEmpty()
        val continueWatching = state.history.filter(HistoryEntry::isResumable).take(18)
        val lastLive = state.history.firstOrNull { it.isLive }
        val movieById = movies.associateBy(ContentItem::id)
        val seriesByName = series.associateBy { it.name.trim().lowercase(Locale.ROOT) }
        val historySeedItems = state.history.asSequence()
            .filterNot { it.isLive }
            .mapNotNull { entry ->
                when (entry.streamKind) {
                    "movie" -> movieById[entry.streamId]
                    "series" -> seriesByName[entry.title.substringBefore("·").trim().lowercase(Locale.ROOT)]
                    else -> null
                }
            }
            .distinctBy { "${it.type}:${it.id}" }
            .take(24)
            .toList()
        fun isFavorite(item: ContentItem): Boolean = "${item.type.name}:${item.id}" in state.favorites
        val favoriteSeedItems = (movies + series).filter(::isFavorite)
        val categoryWeights = mutableMapOf<String, Int>()
        historySeedItems.forEachIndexed { index, item ->
            val weight = (24 - index).coerceAtLeast(2)
            categoryWeights[item.categoryId] = (categoryWeights[item.categoryId] ?: 0) + weight
        }
        favoriteSeedItems.forEach { item ->
            categoryWeights[item.categoryId] = (categoryWeights[item.categoryId] ?: 0) + 30
        }
        val genreWeights = mutableMapOf<String, Int>()
        fun addGenres(item: ContentItem, weight: Int) {
            item.genre.orEmpty().split(',', '،', '/', '|')
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter(String::isNotBlank)
                .forEach { genre -> genreWeights[genre] = (genreWeights[genre] ?: 0) + weight }
        }
        historySeedItems.forEachIndexed { index, item -> addGenres(item, (24 - index).coerceAtLeast(2)) }
        favoriteSeedItems.forEach { addGenres(it, 30) }
        val watchedKeys = historySeedItems.map { "${it.type}:${it.id}" }.toSet()
        val contentScores = (movies + series).associate { item ->
            val categoryScore = (categoryWeights[item.categoryId] ?: 0) * 100
            val genreScore = item.genre.orEmpty().split(',', '،', '/', '|')
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter(String::isNotBlank)
                .sumOf { genre -> (genreWeights[genre] ?: 0) * 28 }
            "${item.type}:${item.id}" to (categoryScore + genreScore)
        }
        val pool = (movies + series).asSequence()
            .filterNot { "${it.type}:${it.id}" in watchedKeys }
            .sortedWith(
                compareByDescending<ContentItem> { contentScores["${it.type}:${it.id}"] ?: 0 }
                    .thenByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }
                    .thenByDescending { it.addedAtEpochSeconds ?: 0L },
            )
            .toList()
        val because = pool.filter { (contentScores["${it.type}:${it.id}"] ?: 0) > 0 }.take(14)
        val becauseKeys = because.map { "${it.type}:${it.id}" }.toSet()
        val suggested = pool.asSequence().filterNot { "${it.type}:${it.id}" in becauseKeys }.take(24).toList()
        val liveById = live.associateBy(ContentItem::id)
        val viewedLive = state.history.asSequence().filter { it.isLive }.mapNotNull { liveById[it.streamId] }.take(30).toList()
        val liveCategoryWeights = mutableMapOf<String, Int>()
        viewedLive.forEachIndexed { index, item ->
            val weight = (30 - index).coerceAtLeast(1)
            liveCategoryWeights[item.categoryId] = (liveCategoryWeights[item.categoryId] ?: 0) + weight
        }
        live.filter(::isFavorite).forEach { item ->
            liveCategoryWeights[item.categoryId] = (liveCategoryWeights[item.categoryId] ?: 0) + 35
        }
        val viewedLiveIds = viewedLive.map(ContentItem::id).toSet()
        val personalizedLive = live.sortedWith(
            compareByDescending<ContentItem> { item ->
                (if (isFavorite(item)) 10_000 else 0) +
                    (liveCategoryWeights[item.categoryId] ?: 0) * 100 +
                    (if (item.id in viewedLiveIds) 25 else 0)
            }
                .thenByDescending { !it.nowPlaying.isNullOrBlank() }
                .thenBy { it.name.lowercase(Locale.ROOT) },
        )
        val popularMovies = movies.sortedWith(
            compareByDescending<ContentItem> { it.rating?.toDoubleOrNull() ?: 0.0 }
                .thenByDescending { it.addedAtEpochSeconds ?: 0L },
        ).take(22)
        val popularSeries = series.sortedWith(
            compareByDescending<ContentItem> { it.rating?.toDoubleOrNull() ?: 0.0 }
                .thenByDescending { it.addedAtEpochSeconds ?: 0L },
        ).take(22)
        val featured = (movies + series)
            .filter { !it.backdropUrl.isNullOrBlank() || !it.posterUrl.isNullOrBlank() }
            .distinctBy { "${it.type}:${it.id}" }
            .take(8)
        return HomeContentSnapshot(
            movies = movies,
            series = series,
            live = live,
            continueWatching = continueWatching,
            lastLive = lastLive,
            becauseYouWatched = because,
            suggested = suggested,
            personalizedLive = personalizedLive,
            popularMovies = popularMovies,
            popularSeries = popularSeries,
            featuredCandidates = featured,
        ).also { snapshot ->
            homeMoviesCatalog = movieCatalog
            homeSeriesCatalog = seriesCatalog
            homeLiveCatalog = liveCatalog
            homeHistory = state.history
            homeFavorites = state.favorites
            cachedHome = snapshot
        }
    }
}

private fun Modifier.restoreFocus(enabled: Boolean, requester: FocusRequester): Modifier =
    then(if (enabled) Modifier.focusRequester(requester) else Modifier)

private data class DownloadToolbarFocusRequesters(
    val wifi: FocusRequester = FocusRequester(),
    val schedule: FocusRequester = FocusRequester(),
    val concurrent: FocusRequester = FocusRequester(),
)

private data class DownloadCardFocusRequesters(
    val primary: FocusRequester = FocusRequester(),
    val priority: FocusRequester = FocusRequester(),
    val cancel: FocusRequester = FocusRequester(),
)

private fun Modifier.applyDownloadFocusPolicy(
    current: DownloadFocusLocation,
    rowCount: Int,
    resolve: (DownloadFocusLocation) -> FocusRequester?,
): Modifier = focusProperties {
    nextDownloadFocus(rowCount, current, DownloadFocusMove.LEFT)?.let(resolve)?.let { left = it }
    nextDownloadFocus(rowCount, current, DownloadFocusMove.RIGHT)?.let(resolve)?.let { right = it }
    nextDownloadFocus(rowCount, current, DownloadFocusMove.UP)?.let(resolve)?.let { up = it }
    nextDownloadFocus(rowCount, current, DownloadFocusMove.DOWN)?.let(resolve)?.let { down = it }
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
    onClearHistory: () -> Unit,
    onPlayDownload: (OfflineDownload) -> Unit,
    onDeleteDownload: (OfflineDownload) -> Unit,
    onRetryDownload: (OfflineDownload) -> Unit,
    onToggleWifiOnly: () -> Unit,
    onToggleDownloadSchedule: () -> Unit,
    onCycleConcurrentDownloads: () -> Unit,
    onCycleDownloadPriority: (OfflineDownload) -> Unit,
    onRunDiagnostics: () -> Unit,
    onLogout: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val context = LocalContext.current
    val adaptiveUi = LocalAdaptiveUi.current
    val requestProfileSwitch = sa.hulksa.player.ui.LocalProfileSwitchRequester.current
    val useNavigationRail = adaptiveUi.navigationType == HulkNavigationType.RAIL
    val queryMemory = remember { mutableStateMapOf<MainDestination, String>() }
    val categoryMemory = remember { mutableStateMapOf<MainDestination, String?>() }
    var previousDestination by remember { mutableStateOf(state.destination) }
    val rememberingSelectDestination: (MainDestination) -> Unit = { destination ->
        queryMemory[state.destination] = state.searchQuery
        categoryMemory[state.destination] = state.selectedCategoryId
        onSelectDestination(destination)
    }
    LaunchedEffect(state.destination) {
        if (previousDestination != state.destination) {
            val restoredQuery = queryMemory[state.destination].orEmpty()
            val restoredCategory = categoryMemory[state.destination]
            if (state.searchQuery != restoredQuery) onSearch(restoredQuery)
            if (state.selectedCategoryId != restoredCategory) onSelectCategory(restoredCategory)
            previousDestination = state.destination
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
    val resolvedIsFavorite: (ContentItem) -> Boolean = { item ->
        val key = "${item.type.name}:${item.id}"
        favoriteOverrides[key] ?: isFavorite(item)
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
    val tvRailFocusRequesters = remember {
        destinations.associate { entry -> entry.destination to FocusRequester() }
    }
    Box(Modifier.fillMaxSize().background(colors.background)) {
        if (useNavigationRail) {
            Row(Modifier.fillMaxSize()) {
                CinematicNavigationRail(
                    selected = state.destination,
                    onSelect = rememberingSelectDestination,
                    onSwitchProfile = requestProfileSwitch,
                    destinationFocusRequesters = tvRailFocusRequesters,
                )
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    DestinationContent(
                        state = state,
                        isTv = isTv,
                        navigationMemory = navigationMemory,
                        isFavorite = resolvedIsFavorite,
                        onSelectCategory = onSelectCategory,
                        onSearch = onSearch,
                        onOpen = onOpen,
                        onOpenHistory = onOpenHistory,
                        onToggleFavorite = toggleFavoriteWithFeedback,
                        onRefresh = onRefresh,
                        onSelectDestination = rememberingSelectDestination,
                        onClearHistory = onClearHistory,
                        onPlayDownload = onPlayDownload,
                        onDeleteDownload = onDeleteDownload,
                        onRetryDownload = onRetryDownload,
                        onToggleWifiOnly = onToggleWifiOnly,
                        onToggleDownloadSchedule = onToggleDownloadSchedule,
                        onCycleConcurrentDownloads = onCycleConcurrentDownloads,
                        onCycleDownloadPriority = onCycleDownloadPriority,
                        onRunDiagnostics = onRunDiagnostics,
                        onLogout = onLogout,
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    DestinationContent(
                        state = state,
                        isTv = false,
                        navigationMemory = navigationMemory,
                        isFavorite = resolvedIsFavorite,
                        onSelectCategory = onSelectCategory,
                        onSearch = onSearch,
                        onOpen = onOpen,
                        onOpenHistory = onOpenHistory,
                        onToggleFavorite = toggleFavoriteWithFeedback,
                        onRefresh = onRefresh,
                        onSelectDestination = onSelectDestination,
                        onClearHistory = onClearHistory,
                        onPlayDownload = onPlayDownload,
                        onDeleteDownload = onDeleteDownload,
                        onRetryDownload = onRetryDownload,
                        onToggleWifiOnly = onToggleWifiOnly,
                        onToggleDownloadSchedule = onToggleDownloadSchedule,
                        onCycleConcurrentDownloads = onCycleConcurrentDownloads,
                        onCycleDownloadPriority = onCycleDownloadPriority,
                        onRunDiagnostics = onRunDiagnostics,
                        onLogout = onLogout,
                    )
                }
                MobileNavigation(state.destination, rememberingSelectDestination, navigationMemory)
            }
        }
    }
}

@Composable
private fun CinematicNavigationRail(
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
    val selectedRequester = destinationFocusRequesters.getValue(selected)
    val railWidth by animateDpAsState(
        targetValue = if (expanded) metrics.expandedWidthDp.dp else metrics.collapsedWidthDp.dp,
        label = "railWidth",
    )

    Column(
        modifier = Modifier
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
                    listOf(Color(0xFF090A07), Color(0xF70A0B08)),
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
        destinations.filterNot { it.destination == MainDestination.SETTINGS }.forEach { entry ->
            NavigationItem(
                entry = entry,
                selected = selected == entry.destination,
                expanded = expanded,
                metrics = metrics,
                onClick = { onSelect(entry.destination) },
                modifier = Modifier.focusRequester(destinationFocusRequesters.getValue(entry.destination)),
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
        )
        Spacer(Modifier.height(metrics.itemGapDp.dp))
        Spacer(Modifier.weight(1f))
        destinations.first { it.destination == MainDestination.SETTINGS }.let { entry ->
            NavigationItem(
                entry = entry,
                selected = selected == entry.destination,
                expanded = expanded,
                metrics = metrics,
                onClick = { onSelect(entry.destination) },
                modifier = Modifier.focusRequester(destinationFocusRequesters.getValue(entry.destination)),
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.itemHeightDp.dp)
            .clip(RoundedCornerShape(metrics.cornerRadiusDp.dp))
            .background(
                when {
                    showFocused -> colors.gold
                    selected -> colors.gold.copy(alpha = .13f)
                    else -> Color.Transparent
                },
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = metrics.itemHorizontalPaddingDp.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        Icon(
            imageVector = entry.icon,
            contentDescription = entry.label,
            tint = if (showFocused) Color.Black else if (active) colors.goldBright else colors.textMuted,
            modifier = Modifier.size(metrics.iconSizeDp.dp),
        )
        if (expanded) {
            Spacer(Modifier.width(metrics.iconLabelGapDp.dp))
            Text(
                entry.label,
                color = if (showFocused) Color.Black else if (active) colors.text else colors.textMuted,
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
    val mobileEntries = remember {
        buildList {
            destinations.forEach { entry ->
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
        delay(40L)
        val visibleCount = navigationState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
        val contextIndex = if (includeNext) {
            (index + 1).coerceAtMost(mobileEntries.lastIndex)
        } else {
            index
        }
        val anchorIndex = (contextIndex - (visibleCount - 1)).coerceAtLeast(0)
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
                            color = if (active) {
                                colors.text.copy(alpha = labelAlpha)
                            } else {
                                colors.textMuted.copy(alpha = labelAlpha)
                            },
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
                            color = if (active) {
                                colors.text.copy(alpha = labelAlpha)
                            } else {
                                colors.textMuted.copy(alpha = labelAlpha)
                            },
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

// Remaining screen implementations are unchanged from the previous head.
