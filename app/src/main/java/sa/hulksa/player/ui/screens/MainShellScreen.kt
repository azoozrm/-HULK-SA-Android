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
                MobileNavigation(state.destination, rememberingSelectDestination)
            }
        }
    }
}

@Composable
private fun CinematicNavigationRail(
    selected: MainDestination,
    onSelect: (MainDestination) -> Unit,
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
private fun MobileNavigation(selected: MainDestination, onSelect: (MainDestination) -> Unit) {
    val colors = LocalHulkColors.current
    val navigationState = rememberLazyListState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val isWide = configuration.screenWidthDp >= 600

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
                destinations.forEach { entry ->
                    val active = selected == entry.destination
                    val iconScale by animateFloatAsState(
                        targetValue = if (active) 1.08f else 1f,
                        label = "mobileNavIconScaleWide",
                    )
                    val labelAlpha by animateFloatAsState(
                        targetValue = if (active) 1f else .72f,
                        label = "mobileNavLabelAlphaWide",
                    )
                    val indicatorWidth by animateDpAsState(
                        targetValue = if (active) 26.dp else 0.dp,
                        label = "mobileNavIndicatorWidthWide",
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clickable(role = Role.Button) { onSelect(entry.destination) }
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .height(2.dp)
                                .width(indicatorWidth)
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (active) colors.goldBright else Color.Transparent),
                        )

                        Spacer(Modifier.height(3.dp))

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
                items(destinations, key = { it.destination.name }) { entry ->
                    val active = selected == entry.destination
                    val iconScale by animateFloatAsState(
                        targetValue = if (active) 1.08f else 1f,
                        label = "mobileNavIconScalePortrait",
                    )
                    val labelAlpha by animateFloatAsState(
                        targetValue = if (active) 1f else .72f,
                        label = "mobileNavLabelAlphaPortrait",
                    )
                    val indicatorWidth by animateDpAsState(
                        targetValue = if (active) 26.dp else 0.dp,
                        label = "mobileNavIndicatorWidthPortrait",
                    )

                    Column(
                        modifier = Modifier
                            .widthIn(min = 52.dp)
                            .height(54.dp)
                            .clickable(role = Role.Button) { onSelect(entry.destination) }
                            .padding(horizontal = 3.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .height(2.dp)
                                .width(indicatorWidth)
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (active) colors.goldBright else Color.Transparent),
                        )

                        Spacer(Modifier.height(3.dp))

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

@Composable
private fun DestinationContent(
    state: HulkUiState,
    isTv: Boolean,
    navigationMemory: NavigationMemoryStore,
    isFavorite: (ContentItem) -> Boolean,
    onSelectCategory: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onOpenHistory: (HistoryEntry) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onRefresh: () -> Unit,
    onSelectDestination: (MainDestination) -> Unit,
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
    when (state.destination) {
        MainDestination.HOME -> CinemaHomeScreen(state, isTv, navigationMemory, isFavorite, onOpen, onOpenHistory, onToggleFavorite, onRefresh) { onSelectDestination(MainDestination.DOWNLOADS) }
        MainDestination.LIVE -> LiveCatalogScreen(state, isTv, navigationMemory, isFavorite, onSelectCategory, onSearch, onOpen, onToggleFavorite, onRefresh)
        MainDestination.MOVIES -> PosterCatalogScreen("الافلام", ContentType.MOVIE, MainDestination.MOVIES, state, isTv, navigationMemory, isFavorite, onSelectCategory, onSearch, onOpen, onOpenHistory, onToggleFavorite, onRefresh)
        MainDestination.SERIES -> PosterCatalogScreen("المسلسلات", ContentType.SERIES, MainDestination.SERIES, state, isTv, navigationMemory, isFavorite, onSelectCategory, onSearch, onOpen, onOpenHistory, onToggleFavorite, onRefresh)
        MainDestination.FAVORITES -> FavoritesScreen(state, isTv, navigationMemory, isFavorite, onOpen, onToggleFavorite)
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
        )
        MainDestination.SETTINGS -> SettingsScreen(
            state = state,
            isTv = isTv,
            onRefreshAll = {
                onSelectDestination(MainDestination.HOME)
                onRefresh()
            },
            onClearHistory = onClearHistory,
            onRunDiagnostics = onRunDiagnostics,
            onLogout = onLogout,
        )
    }
}

@Composable
private fun CinemaHomeScreen(
    state: HulkUiState,
    isTv: Boolean,
    navigationMemory: NavigationMemoryStore,
    isFavorite: (ContentItem) -> Boolean,
    onOpen: (ContentItem) -> Unit,
    onOpenHistory: (HistoryEntry) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onRefresh: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val homeContent = navigationMemory.homeContent(state)
    val movies = homeContent.movies
    val series = homeContent.series
    val live = homeContent.live
    val continueWatching = homeContent.continueWatching
    val lastLive = homeContent.lastLive
    val becauseYouWatched = homeContent.becauseYouWatched
    val suggested = homeContent.suggested
    val personalizedLive = homeContent.personalizedLive
    val popularMovies = homeContent.popularMovies
    val popularSeries = homeContent.popularSeries
    val featuredCandidates = homeContent.featuredCandidates
    val activeDownloads = remember(state.downloads) {
        state.downloads.filter {
            it.status == OfflineStatus.DOWNLOADING || it.status == OfflineStatus.QUEUED ||
                it.status == OfflineStatus.CHECKING || it.status == OfflineStatus.PAUSED ||
                it.status == OfflineStatus.WAITING_NETWORK || it.status == OfflineStatus.WAITING_SCHEDULE ||
                it.status == OfflineStatus.WAITING_STORAGE
        }.take(4)
    }
    var featuredIndex by remember(featuredCandidates) { mutableStateOf(0) }
    LaunchedEffect(featuredCandidates) {
        while (featuredCandidates.size > 1) {
            delay(9_000L)
            featuredIndex = (featuredIndex + 1) % featuredCandidates.size
        }
    }
    val featured = featuredCandidates.getOrNull(featuredIndex) ?: movies.firstOrNull() ?: series.firstOrNull()
    val homeMovies = remember(movies, featured) { movies.filterNot { it.type == featured?.type && it.id == featured.id } }
    val homeSeries = remember(series, featured) { series.filterNot { it.type == featured?.type && it.id == featured.id } }
    val loading = ContentType.MOVIE in state.loadingTypes || ContentType.SERIES in state.loadingTypes
    val remembered = navigationMemory.position(MainDestination.HOME)

    var rowCursor = 1 + if (state.errorMessage != null) 1 else 0
    val continueRow = if (continueWatching.isNotEmpty()) rowCursor++ else -1
    val downloadsRow = if (activeDownloads.isNotEmpty()) rowCursor++ else -1
    val becauseRow = if (becauseYouWatched.isNotEmpty()) rowCursor++ else -1
    val recommendedRow = if (suggested.isNotEmpty()) rowCursor++ else -1
    val moviesRow = if (homeMovies.isNotEmpty()) rowCursor++ else -1
    val seriesRow = if (homeSeries.isNotEmpty()) rowCursor++ else -1
    val topMoviesRow = if (popularMovies.isNotEmpty()) rowCursor++ else -1
    val topSeriesRow = if (popularSeries.isNotEmpty()) rowCursor++ else -1
    val liveRow = if (lastLive != null || live.isNotEmpty()) rowCursor else -1
    val rowIndexByKey = mapOf(
        "continue" to continueRow, "downloads" to downloadsRow, "because-watched" to becauseRow,
        "recommended" to recommendedRow, "recent-movies" to moviesRow, "recent-series" to seriesRow, "top-movies" to topMoviesRow,
        "top-series" to topSeriesRow, "last-live" to liveRow, "popular-live" to liveRow,
    )
    val initialRow = rowIndexByKey[remembered.rowKey]?.takeIf { it >= 0 } ?: 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialRow)

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isTv) TV_PAGE_GUTTER else 0.dp),
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
                    watchModifier = Modifier.restoreFocus(remembered.rowKey == "hero", heroRequester),
                    onFocused = { navigationMemory.save(MainDestination.HOME, "${featured.type}:${featured.id}", 0, "hero", 0) },
                )
            } else HomePlaceholder(loading, onRefresh, isTv)
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
            item { HomeSectionPadding(isTv) { PosterSection("احدث اضافات HULK — افلام", "recent-movies", moviesRow, homeMovies.take(28), isTv, navigationMemory, isFavorite, onOpen, onToggleFavorite) } }
        }
        if (homeSeries.isNotEmpty()) {
            item { HomeSectionPadding(isTv) { PosterSection("احدث اضافات HULK — مسلسلات", "recent-series", seriesRow, homeSeries.take(28), isTv, navigationMemory, isFavorite, onOpen, onToggleFavorite) } }
        }
        if (popularMovies.isNotEmpty()) {
            item { HomeSectionPadding(isTv) { PosterSection("الاعلى تقييما — افلام", "top-movies", topMoviesRow, popularMovies, isTv, navigationMemory, isFavorite, onOpen, onToggleFavorite) } }
        }
        if (popularSeries.isNotEmpty()) {
            item { HomeSectionPadding(isTv) { PosterSection("الاعلى تقييما — مسلسلات", "top-series", topSeriesRow, popularSeries, isTv, navigationMemory, isFavorite, onOpen, onToggleFavorite) } }
        }
        if (lastLive != null) {
            item { HomeSectionPadding(isTv) { HistorySection("اخر قناة شاهدتها", "last-live", liveRow, listOf(lastLive), isTv, navigationMemory, onOpenHistory) } }
        } else if (live.isNotEmpty()) {
            item { HomeSectionPadding(isTv) { PosterSection("قنوات مقترحة لك", "popular-live", liveRow, personalizedLive.take(20), isTv, navigationMemory, isFavorite, onOpen, onToggleFavorite) } }
        }
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
    Box(Modifier.fillMaxWidth().padding(horizontal = if (isTv) TV_PAGE_GUTTER else 25.dp)) { content() }
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
    watchModifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    val colors = LocalHulkColors.current
    val configuration = LocalConfiguration.current
    val isPortraitPhone = !isTv && configuration.screenWidthDp < 600 && configuration.screenHeightDp > configuration.screenWidthDp
    val heroHeight = when {
        isTv -> 300.dp
        isPortraitPhone -> 210.dp
        else -> 250.dp
    }
    val titleSize = when {
        isTv -> 27.sp
        isPortraitPhone -> 20.sp
        else -> 23.sp
    }
    val shape = RoundedCornerShape(if (isTv) 22.dp else 18.dp)
    val backdrop = item.backdropUrl ?: item.posterUrl
    Box(
        Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clip(shape)
            .background(colors.surface),
    ) {
        AsyncImage(
            model = backdrop,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .88f), Color.Black.copy(alpha = .22f), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .75f)))))
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(if (isTv) .55f else .78f)
                .padding(if (isTv) 22.dp else 16.dp),
        ) {
            BrandBadge()
            Spacer(Modifier.height(10.dp))
            Text(item.name, color = colors.text, fontSize = titleSize, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                item.year?.takeIf(String::isNotBlank)?.let { InfoPill(it) }
                item.rating?.takeIf(String::isNotBlank)?.let { InfoPill("★ $it") }
                item.genre?.split(',', '،')?.firstOrNull()?.trim()?.takeIf(String::isNotBlank)?.let { InfoPill(it) }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusButton("مشاهدة", onOpen, compact = true, modifier = watchModifier.onFocusChanged { if (it.isFocused) onFocused() })
                FocusButton(if (isFavorite) "في المفضلة" else "اضافة للمفضلة", onToggleFavorite, primary = false, compact = true)
                FocusButton("تحديث", onRefresh, primary = false, compact = true, enabled = !isLoading)
            }
        }
    }
}

// ... REMAINDER OMITTED IN THIS REWRITE FOR BREVITY
