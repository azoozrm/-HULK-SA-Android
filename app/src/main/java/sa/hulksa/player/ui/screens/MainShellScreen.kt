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
import sa.hulksa.player.ui.components.CompactPosterCard
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
    Box(Modifier.fillMaxSize().background(colors.background)) {
        if (useNavigationRail) {
            Row(Modifier.fillMaxSize()) {
                CinematicNavigationRail(
                    selected = state.destination,
                    onSelect = rememberingSelectDestination,
                )
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    DestinationContent(
                        state = state,
                        // A rail is a layout choice; only an actual TV uses TV interaction semantics.
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
) {
    var railHasFocus by remember { mutableStateOf(false) }
    val expanded = railHasFocus
    val railWidth by animateDpAsState(if (expanded) 202.dp else 90.dp, label = "railWidth")
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val railLogoSize = tvRailLogoSizeDp(screenWidthDp).dp

    Column(
        modifier = Modifier
            .width(railWidth)
            .fillMaxHeight()
            .focusGroup()
            .onFocusChanged { railHasFocus = it.hasFocus }
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF090A07), Color(0xF70A0B08)),
                ),
            )
            .padding(start = 10.dp, end = 10.dp, top = 24.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandLogo(Modifier.size(railLogoSize))
        Spacer(Modifier.height(10.dp))
        destinations.filterNot { it.destination == MainDestination.SETTINGS }.forEach { entry ->
            NavigationItem(
                entry = entry,
                selected = selected == entry.destination,
                expanded = expanded,
                onClick = { onSelect(entry.destination) },
            )
            Spacer(Modifier.height(2.dp))
        }
        Spacer(Modifier.weight(1f))
        destinations.first { it.destination == MainDestination.SETTINGS }.let { entry ->
            NavigationItem(
                entry = entry,
                selected = selected == entry.destination,
                expanded = expanded,
                onClick = { onSelect(entry.destination) },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun NavigationItem(
    entry: DestinationEntry,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember { mutableStateOf(false) }
    val showFocused = focused && adaptiveUi.showFocusHighlights
    val active = selected || showFocused
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    showFocused -> colors.gold
                    selected -> colors.gold.copy(alpha = .13f)
                    else -> Color.Transparent
                },
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        Icon(
            imageVector = entry.icon,
            contentDescription = entry.label,
            tint = if (showFocused) Color.Black else if (active) colors.goldBright else colors.textMuted,
            modifier = Modifier.size(23.dp),
        )
        if (expanded) {
            Spacer(Modifier.width(11.dp))
            Text(
                entry.label,
                color = if (showFocused) Color.Black else if (active) colors.text else colors.textMuted,
                fontSize = 14.sp,
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

    LaunchedEffect(selected) {
        val selectedIndex = destinations.indexOfFirst { it.destination == selected }.coerceAtLeast(0)
        navigationState.animateScrollToItem(selectedIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF090A07))
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            state = navigationState,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(destinations, key = { it.destination.name }) { entry ->
                val active = selected == entry.destination
                val iconScale by animateFloatAsState(
                    targetValue = if (active) 1.08f else 1f,
                    label = "mobileNavIconScale",
                )
                val labelAlpha by animateFloatAsState(
                    targetValue = if (active) 1f else .72f,
                    label = "mobileNavLabelAlpha",
                )
                val indicatorWidth by animateDpAsState(
                    targetValue = if (active) 28.dp else 0.dp,
                    label = "mobileNavIndicatorWidth",
                )

                Column(
                    modifier = Modifier
                        .widthIn(min = 52.dp)
                        .height(58.dp)
                        .clickable(role = Role.Button) { onSelect(entry.destination) }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .height(2.dp)
                            .width(indicatorWidth)
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                if (active) colors.goldBright else Color.Transparent,
                            ),
                    )

                    Spacer(Modifier.height(5.dp))

                    Icon(
                        imageVector = entry.icon,
                        contentDescription = entry.label,
                        tint = if (active) colors.goldBright else colors.textMuted,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            },
                    )

                    Spacer(Modifier.height(3.dp))

                    Text(
                        text = entry.label,
                        color = if (active) {
                            colors.text.copy(alpha = labelAlpha)
                        } else {
                            colors.textMuted.copy(alpha = labelAlpha)
                        },
                        fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = .06f)),
        )
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
    val image = item.backdropUrl ?: item.posterUrl
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isTv) 374.dp else 288.dp)
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
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 26.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("الرئيسية", color = colors.text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("احدث اضافات HULK", color = colors.textMuted, fontSize = 11.sp)
            }
            if (isLoading) LoadingRing()
            Spacer(Modifier.width(10.dp))
            RoundAction(Icons.Rounded.Refresh, "تحديث المحتوى", onRefresh)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(if (isTv) .58f else .86f)
                .padding(start = 27.dp, end = 27.dp, bottom = if (isTv) 38.dp else 24.dp),
        ) {
            Text("وصل حديثا", color = colors.goldBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(
                item.name,
                color = Color.White,
                fontSize = if (isTv) 39.sp else 28.sp,
                lineHeight = if (isTv) 47.sp else 34.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                item.year?.let { InfoPill(it) }
                item.rating?.let { InfoPill("★ $it") }
                item.genre?.takeIf(String::isNotBlank)?.let { InfoPill(it.take(27)) }
            }
            item.plot?.takeIf(String::isNotBlank)?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Color(0xFFD4D0C5), fontSize = 12.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(15.dp))
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
private fun HomePlaceholder(loading: Boolean, onRefresh: () -> Unit, isTv: Boolean) {
    val colors = LocalHulkColors.current
    Box(
        Modifier.fillMaxWidth().height(if (isTv) 360.dp else 270.dp).background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
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
                CompactPosterCard(
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
    Column {
        Text(title, color = colors.text, fontSize = if (isTv) 20.sp else 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
                val restore = remembered.rowKey == rowKey &&
                    (remembered.itemKey == entry.key || (remembered.itemKey.isBlank() && index == targetIndex))
                HistoryCard(
                    entry,
                    { onOpen(entry) },
                    Modifier.width(if (isTv) 214.dp else 190.dp).restoreFocus(restore, targetRequester),
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
    isFavorite: (ContentItem) -> Boolean,
    onSelectCategory: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onOpenHistory: (HistoryEntry) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onRefresh: () -> Unit,
) {
    val catalog = state.catalogs[type]
    val ordered = remember(catalog) { newest(catalog?.items.orEmpty()) }
    val visible = remember(ordered, state.selectedCategoryId, state.searchQuery, state.favorites) {
        ordered.filter { item ->
            categoryMatches(item, state.selectedCategoryId, isFavorite) &&
                item.matchesSearch(state.searchQuery)
        }
    }
    val continueWatching = remember(state.history, state.searchQuery, type) {
        val kind = if (type == ContentType.MOVIE) "movie" else "series"
        state.history.filter { entry ->
            entry.streamKind == kind && entry.isResumable() &&
                (state.searchQuery.isBlank() || entry.title.contains(state.searchQuery.trim(), ignoreCase = true))
        }
    }
    val showingContinue = state.selectedCategoryId == CONTINUE_CATEGORY_ID
    val resultCount = if (showingContinue) continueWatching.size else visible.size
    Column(
        Modifier
            .fillMaxSize()
            .padding(
                horizontal = if (isTv) TV_PAGE_GUTTER else 13.dp,
                vertical = if (isTv) TV_PAGE_GUTTER else 12.dp,
            ),
    ) {
        CatalogHeader(title, resultCount, state.searchQuery, onSearch, onRefresh, isTv)
        if (state.errorMessage != null) { Spacer(Modifier.height(10.dp)); ErrorNotice(state.errorMessage) }
        Spacer(Modifier.height(11.dp))
        ReorderableCatalogCategoryBar(type, catalog?.categories.orEmpty(), ordered, state.selectedCategoryId, onSelectCategory, isTv)
        CatalogInteractionHints(isTv)
        Spacer(Modifier.height(9.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (showingContinue && continueWatching.isNotEmpty()) {
                HistoryGrid(continueWatching, isTv, destination, navigationMemory, onOpenHistory)
            } else if (showingContinue) {
                EmptyState("لا توجد مشاهدة غير مكتملة في $title")
            } else if (catalog == null && type in state.loadingTypes) {
                LoadingRing(label = "جاري تحميل $title…", modifier = Modifier.align(Alignment.Center))
            } else if (visible.isEmpty()) {
                EmptyState("لا توجد نتائج مطابقة")
            } else {
                ContentGrid(
                    visible, isTv, destination, navigationMemory, isFavorite, onOpen, onToggleFavorite,
                    restoreFocusedCard = state.searchQuery.isBlank(),
                )
            }
        }
    }
}

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
) {
    val catalog = state.catalogs[ContentType.LIVE]
    val visible = remember(catalog, state.selectedCategoryId, state.searchQuery, state.favorites) {
        catalog?.items.orEmpty().filter { item ->
            categoryMatches(item, state.selectedCategoryId, isFavorite) &&
                item.matchesSearch(state.searchQuery)
        }
    }
    val remembered = navigationMemory.position(MainDestination.LIVE)
    val rememberedIndex = remembered.itemIndex.coerceIn(0, visible.lastIndex.coerceAtLeast(0))
    var preview by remember(catalog, state.selectedCategoryId) { mutableStateOf<ContentItem?>(null) }
    val channelRequester = remember { FocusRequester() }
    val playRequester = remember { FocusRequester() }
    val favoriteRequester = remember { FocusRequester() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = rememberedIndex)
    LaunchedEffect(listState, visible) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            visible.getOrNull(index)?.let { navigationMemory.save(MainDestination.LIVE, "${it.type}:${it.id}", index) }
        }
    }
    LaunchedEffect(visible) {
        if (preview == null || preview !in visible) {
            preview = visible.firstOrNull { "${it.type}:${it.id}" == remembered.itemKey } ?: visible.getOrNull(rememberedIndex) ?: visible.firstOrNull()
        }
        if (state.searchQuery.isBlank() && remembered.itemKey.isNotBlank() && visible.isNotEmpty()) {
            listState.scrollToItem(rememberedIndex)
            delay(180)
            runCatching { channelRequester.requestFocus() }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(
                horizontal = if (isTv) TV_PAGE_GUTTER else 12.dp,
                vertical = if (isTv) TV_PAGE_GUTTER else 11.dp,
            ),
    ) {
        CatalogHeader("البث المباشر", visible.size, state.searchQuery, onSearch, onRefresh, isTv)
        if (state.errorMessage != null) { Spacer(Modifier.height(9.dp)); ErrorNotice(state.errorMessage) }
        Spacer(Modifier.height(10.dp))
        ReorderableLiveCategoryBar(catalog?.categories.orEmpty(), catalog?.items.orEmpty(), state.selectedCategoryId, onSelectCategory)
        LiveInteractionHints(isTv)
        Spacer(Modifier.height(8.dp))
        if (catalog == null && ContentType.LIVE in state.loadingTypes) {
            LoadingRing(label = "جاري تحميل القنوات…", modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 90.dp))
        } else if (visible.isEmpty()) {
            EmptyState("لا توجد قنوات مطابقة")
        } else if (isTv) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(
                    modifier = Modifier.width(408.dp).fillMaxHeight().clip(RoundedCornerShape(18.dp))
                        .background(Color(0xA30D0E0B)).padding(9.dp),
                ) {
                    Text("القنوات", color = LocalHulkColors.current.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp))
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        itemsIndexed(visible, key = { _, channel -> channel.id }) { index, channel ->
                            val key = "${channel.type}:${channel.id}"
                            val restore = key == remembered.itemKey || (remembered.itemKey.isBlank() && index == rememberedIndex)
                            ChannelListItem(
                                item = channel,
                                selected = preview?.id == channel.id,
                                onFocused = {
                                    preview = channel
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
                LiveStage(
                    item = preview,
                    isFavorite = preview?.let(isFavorite) == true,
                    channelRequester = channelRequester,
                    playRequester = playRequester,
                    favoriteRequester = favoriteRequester,
                    onWatch = { preview?.let(onOpen) },
                    onToggleFavorite = { preview?.let(onToggleFavorite) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
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
                Text("اضغط تشغيل للانتقال الى ملء الشاشة", color = colors.textMuted, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
        if (item != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text("على الهواء الان", color = colors.goldBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(item.name, color = colors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().padding(bottom = TV_LIVE_ACTION_INSET)) {
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
) {
    val colors = LocalHulkColors.current
    val content = remember(state.catalogs, state.favorites) {
        state.catalogs.values.flatMap { it.items }.filter(isFavorite).distinctBy { "${it.type}:${it.id}" }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(if (isTv) TV_PAGE_GUTTER else 13.dp),
    ) {
        PageTitle("قائمتي", "كل ما حفظته في مكان واحد", content.size, Icons.Rounded.Star)
        Spacer(Modifier.height(18.dp))
        if (content.isEmpty() && state.loadingTypes.isEmpty()) {
            EmptyState("لم تضف اي محتوى الى قائمتك بعد")
        } else {
            ContentGrid(content, isTv, MainDestination.FAVORITES, navigationMemory, isFavorite, onOpen, onToggleFavorite)
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
    Column(
        Modifier
            .fillMaxSize()
            .padding(if (isTv) TV_PAGE_GUTTER else 13.dp),
    ) {
        PageTitle("البحث", "القنوات والافلام والمسلسلات", results.size, Icons.Rounded.Search)
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
        if (state.searchQuery.isBlank()) {
            EmptyState("ابدا بكتابة الاسم او السنة او النوع او وصف المحتوى")
        } else if (results.isEmpty()) {
            EmptyState("لا توجد نتائج مطابقة")
        } else {
            Text("${results.size} نتيجة", color = colors.textMuted, fontSize = 11.sp)
            Spacer(Modifier.height(9.dp))
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
    val remembered = navigationMemory.position(MainDestination.DOWNLOADS)
    val rememberedIndex = remembered.itemIndex.coerceIn(0, downloads.lastIndex.coerceAtLeast(0))
    val downloadsState = rememberLazyListState(initialFirstVisibleItemIndex = rememberedIndex)
    val downloadsFocusScope = rememberCoroutineScope()
    var downloadsFocusJob by remember { mutableStateOf<Job?>(null) }
    val toolbarFocus = remember { DownloadToolbarFocusRequesters() }
    val cardFocus = remember(downloads.map { it.downloadId }) {
        downloads.associate { item -> item.downloadId to DownloadCardFocusRequesters() }
    }
    val resolveDownloadFocus: (DownloadFocusLocation) -> FocusRequester? = { location ->
        when (location.zone) {
            DownloadFocusZone.TOOLBAR -> when (location.slot) {
                DownloadFocusSlot.WIFI -> toolbarFocus.wifi
                DownloadFocusSlot.SCHEDULE -> toolbarFocus.schedule
                DownloadFocusSlot.CONCURRENT -> toolbarFocus.concurrent
                else -> null
            }
            DownloadFocusZone.CARD -> downloads.getOrNull(location.row)
                ?.let { cardFocus[it.downloadId] }
                ?.let { requesters ->
                    when (location.slot) {
                        DownloadFocusSlot.PRIMARY -> requesters.primary
                        DownloadFocusSlot.PRIORITY -> requesters.priority
                        DownloadFocusSlot.CANCEL -> requesters.cancel
                        else -> null
                    }
                }
        }
    }
    val context = LocalContext.current
    val availableBytes = remember(downloads) {
        (context.getExternalFilesDir(null) ?: context.filesDir).usableSpace.coerceAtLeast(0L)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(if (isTv) TV_PAGE_GUTTER else 13.dp),
    ) {
        PageTitle("التنزيلات", "ادارة كاملة للمشاهدة بدون انترنت", downloads.size, Icons.Rounded.Download)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill("مكتمل  $completed")
            if (active > 0) InfoPill("نشط ومجدول  $active")
            if (storedBytes > 0L) InfoPill("المحفوظ  ${formatBytes(storedBytes)}")
            InfoPill("المساحة المتاحة بالجهاز  ${formatBytes(availableBytes)}")
        }
        Spacer(Modifier.height(11.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
            modifier = Modifier.focusGroup(),
        ) {
            item {
                FocusButton(
                    if (settings.wifiOnly) "WiFi فقط  ✓" else "كل الشبكات",
                    onToggleWifiOnly,
                    primary = settings.wifiOnly,
                    compact = true,
                    outlined = !settings.wifiOnly,
                    modifier = Modifier
                        .focusRequester(toolbarFocus.wifi)
                        .applyDownloadFocusPolicy(
                            DownloadFocusLocation.toolbar(DownloadFocusSlot.WIFI),
                            downloads.size,
                            resolveDownloadFocus,
                        ),
                )
            }
            item {
                FocusButton(
                    if (settings.scheduleMode == DownloadScheduleMode.NIGHT) "الجدولة 02:00" else "الجدولة الان",
                    onToggleSchedule,
                    primary = settings.scheduleMode == DownloadScheduleMode.NIGHT,
                    compact = true,
                    outlined = settings.scheduleMode != DownloadScheduleMode.NIGHT,
                    modifier = Modifier
                        .focusRequester(toolbarFocus.schedule)
                        .applyDownloadFocusPolicy(
                            DownloadFocusLocation.toolbar(DownloadFocusSlot.SCHEDULE),
                            downloads.size,
                            resolveDownloadFocus,
                        ),
                )
            }
            item {
                FocusButton(
                    "متزامنة  ${settings.concurrentDownloads}",
                    onCycleConcurrent,
                    primary = false,
                    compact = true,
                    outlined = true,
                    modifier = Modifier
                        .focusRequester(toolbarFocus.concurrent)
                        .applyDownloadFocusPolicy(
                            DownloadFocusLocation.toolbar(DownloadFocusSlot.CONCURRENT),
                            downloads.size,
                            resolveDownloadFocus,
                        ),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (downloads.isEmpty()) {
            EmptyState("ستظهر هنا الافلام والحلقات التي تختار تحميلها")
        } else {
            LazyColumn(
                state = downloadsState,
                verticalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
                horizontalAlignment = Alignment.Start,
                contentPadding = PaddingValues(bottom = 28.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(downloads, key = { _, item -> item.downloadId }) { index, item ->
                    val requesters = checkNotNull(cardFocus[item.downloadId])
                    DownloadCard(
                        item = item,
                        isTv = isTv,
                        rowIndex = index,
                        rowCount = downloads.size,
                        focusRequesters = requesters,
                        resolveFocus = resolveDownloadFocus,
                        restoreFocus = remembered.itemKey == item.downloadId.toString() || (remembered.itemKey.isBlank() && index == rememberedIndex),
                        onFocused = {
                            navigationMemory.save(MainDestination.DOWNLOADS, item.downloadId.toString(), index)
                            if (isTv) {
                                downloadsFocusJob?.cancel()
                                downloadsFocusJob = downloadsFocusScope.launch {
                                    // Compose's TV focus relocation can pivot a focused action
                                    // above the LazyColumn viewport. Re-anchor the owning card
                                    // after that relocation so its top edge is never clipped.
                                    delay(120)
                                    runCatching {
                                        downloadsState.scrollToItem(index, scrollOffset = 0)
                                    }
                                }
                            }
                        },
                        onPlay = onPlay,
                        onDelete = onDelete,
                        onRetry = onRetry,
                        onCyclePriority = onCyclePriority,
                        modifier = Modifier
                            .widthIn(max = if (isTv) 720.dp else 520.dp)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(
    item: OfflineDownload,
    isTv: Boolean,
    rowIndex: Int,
    rowCount: Int,
    focusRequesters: DownloadCardFocusRequesters,
    resolveFocus: (DownloadFocusLocation) -> FocusRequester?,
    restoreFocus: Boolean,
    onFocused: () -> Unit,
    onPlay: (OfflineDownload) -> Unit,
    onDelete: (OfflineDownload) -> Unit,
    onRetry: (OfflineDownload) -> Unit,
    onCyclePriority: (OfflineDownload) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(restoreFocus, item.downloadId) {
        if (restoreFocus) { delay(220); runCatching { focusRequesters.primary.requestFocus() } }
    }
    val shape = RoundedCornerShape(17.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isTv) 164.dp else 220.dp)
            .clip(shape)
            .background(if (focused) colors.gold.copy(alpha = .10f) else Color(0xFF11120E))
            .border(if (focused) 2.dp else 1.dp, if (focused) colors.goldBright else colors.line.copy(alpha = .45f), shape)
            .onFocusChanged {
                focused = it.hasFocus
                if (it.hasFocus) onFocused()
            }
            .padding(11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(if (isTv) 82.dp else 72.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1B1C15)),
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
                BrandLogo(Modifier.size(58.dp).graphicsLayer { alpha = .55f })
            }
            if (item.status == OfflineStatus.COMPLETED) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(colors.gold)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text("جاهز", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.weight(1f).fillMaxWidth()) {
                Text(
                    item.seriesTitle ?: if (item.streamKind == "movie") "فيلم" else "حلقة",
                    color = colors.goldBright,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                val cleanDownloadTitle = if (item.seriesTitle != null && item.episodeNumber != null) {
                    "الحلقة ${item.episodeNumber}"
                } else {
                    item.title
                }
                Text(
                    cleanDownloadTitle,
                    color = colors.text,
                    fontSize = if (isTv) 14.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = if (isTv) 17.sp else 15.sp,
                )
                val downloadMeta = buildList {
                    item.season?.let { add("الموسم $it") }
                    add("الاولوية ${priorityLabel(item.priority)}")
                }.joinToString("  •  ")
                Text(
                    downloadMeta,
                    color = if (item.priority == 1) colors.goldBright else colors.textMuted,
                    fontSize = 8.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                DownloadProgress(item)
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp).focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                when (item.status) {
                    OfflineStatus.COMPLETED -> FocusButton(
                        "تشغيل",
                        { onPlay(item) },
                        compact = true,
                        onFocused = onFocused,
                        modifier = Modifier
                            .weight(1.35f)
                            .fillMaxHeight()
                            .focusRequester(focusRequesters.primary)
                            .applyDownloadFocusPolicy(
                                DownloadFocusLocation.card(rowIndex, DownloadFocusSlot.PRIMARY),
                                rowCount,
                                resolveFocus,
                            ),
                    )
                    OfflineStatus.FAILED -> FocusButton(
                        "اعادة المحاولة",
                        { onRetry(item) },
                        compact = true,
                        onFocused = onFocused,
                        modifier = Modifier
                            .weight(1.35f)
                            .fillMaxHeight()
                            .focusRequester(focusRequesters.primary)
                            .applyDownloadFocusPolicy(
                                DownloadFocusLocation.card(rowIndex, DownloadFocusSlot.PRIMARY),
                                rowCount,
                                resolveFocus,
                            ),
                    )
                    OfflineStatus.PAUSED,
                    OfflineStatus.WAITING_SCHEDULE,
                    OfflineStatus.WAITING_NETWORK,
                    OfflineStatus.WAITING_STORAGE,
                    -> FocusButton(
                        "استئناف",
                        { onRetry(item) },
                        compact = true,
                        onFocused = onFocused,
                        modifier = Modifier
                            .weight(1.35f)
                            .fillMaxHeight()
                            .focusRequester(focusRequesters.primary)
                            .applyDownloadFocusPolicy(
                                DownloadFocusLocation.card(rowIndex, DownloadFocusSlot.PRIMARY),
                                rowCount,
                                resolveFocus,
                            ),
                    )
                    OfflineStatus.QUEUED,
                    OfflineStatus.CHECKING,
                    OfflineStatus.DOWNLOADING,
                    -> FocusButton(
                        "ايقاف مؤقت",
                        { onRetry(item) },
                        primary = true,
                        compact = true,
                        onFocused = onFocused,
                        modifier = Modifier
                            .weight(1.35f)
                            .fillMaxHeight()
                            .focusRequester(focusRequesters.primary)
                            .applyDownloadFocusPolicy(
                                DownloadFocusLocation.card(rowIndex, DownloadFocusSlot.PRIMARY),
                                rowCount,
                                resolveFocus,
                            ),
                    )
                }
                FocusButton(
                    priorityShortLabel(item.priority),
                    { onCyclePriority(item) },
                    primary = item.priority == 1,
                    compact = true,
                    outlined = item.priority != 1,
                    onFocused = onFocused,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .focusRequester(focusRequesters.priority)
                        .applyDownloadFocusPolicy(
                            DownloadFocusLocation.card(rowIndex, DownloadFocusSlot.PRIORITY),
                            rowCount,
                            resolveFocus,
                        ),
                )
                FocusButton(
                    if (item.status == OfflineStatus.COMPLETED) "حذف" else "الغاء",
                    { onDelete(item) },
                    primary = false,
                    compact = true,
                    outlined = true,
                    onFocused = onFocused,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .focusRequester(focusRequesters.cancel)
                        .applyDownloadFocusPolicy(
                            DownloadFocusLocation.card(rowIndex, DownloadFocusSlot.CANCEL),
                            rowCount,
                            resolveFocus,
                        ),
                )
            }
        }
    }
}

@Composable
private fun DownloadProgress(item: OfflineDownload) {
    val colors = LocalHulkColors.current
    val targetProgress = if (item.status == OfflineStatus.COMPLETED) 1f else item.progress
    val progress by animateFloatAsState(targetProgress, label = "downloadProgress")
    val percent = (targetProgress * 100).toInt()
    val sizeLine = when {
        item.status == OfflineStatus.COMPLETED ->
            "${formatBytes(item.totalBytes.coerceAtLeast(item.bytesDownloaded))}  •  ${item.storageLabel}"
        item.totalBytes > 0L ->
            "$percent%  •  ${formatBytes(item.bytesDownloaded)} / ${formatBytes(item.totalBytes)}"
        item.bytesDownloaded > 0L -> formatBytes(item.bytesDownloaded)
        else -> downloadStatusLabel(item.status)
    }
    Text("\u200E$sizeLine", color = colors.textMuted, fontSize = 9.sp, maxLines = 1)
    Spacer(Modifier.height(3.dp))
    when {
        item.status == OfflineStatus.DOWNLOADING && item.bytesPerSecond > 0L -> Text(
            "\u200E${formatTransferRate(item.bytesPerSecond)}  •  المتبقي ${formatEta(item.etaSeconds)}",
            color = colors.goldBright,
            fontSize = 9.sp,
            maxLines = 1,
        )
        item.status == OfflineStatus.WAITING_SCHEDULE && item.scheduledAtEpochMs > 0L -> Text(
            "سيبدا ${formatScheduledTime(item.scheduledAtEpochMs)}",
            color = colors.goldBright,
            fontSize = 9.sp,
            maxLines = 1,
        )
        !item.errorMessage.isNullOrBlank() -> Text(
            item.errorMessage,
            color = if (item.status == OfflineStatus.FAILED) Color(0xFFFF9B8E) else colors.textMuted,
            fontSize = 8.sp,
            lineHeight = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        else -> Text(
            "${downloadStatusLabel(item.status)}  •  ${item.storageLabel}",
            color = colors.textMuted,
            fontSize = 8.sp,
            maxLines = 1,
        )
    }
    Spacer(Modifier.height(5.dp))
    Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f))) {
        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(colors.goldBright))
    }
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

private fun priorityShortLabel(priority: Int): String = when (priority) {
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
) {
    val remembered = navigationMemory.position(destination)
    val rememberedKeyIndex = content.indexOfFirst { "${it.type}:${it.id}" == remembered.itemKey }
    val targetIndex = if (destination == MainDestination.SEARCH) {
        0
    } else {
        (if (rememberedKeyIndex >= 0) rememberedKeyIndex else remembered.itemIndex)
            .coerceIn(0, content.lastIndex.coerceAtLeast(0))
    }
    val targetKey = content.getOrNull(targetIndex)?.let { "${it.type}:${it.id}" }.orEmpty()
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = targetIndex)
    LaunchedEffect(gridState, content, destination) {
        snapshotFlow { gridState.firstVisibleItemIndex }.collect { index ->
            content.getOrNull(index)?.let { navigationMemory.save(destination, "${it.type}:${it.id}", index) }
        }
    }
    val targetRequester = remember { FocusRequester() }
    LaunchedEffect(content.map { "${it.type}:${it.id}" }, remembered.itemKey, destination, restoreFocusedCard) {
        if (destination == MainDestination.SEARCH) {
            if (content.isNotEmpty()) gridState.scrollToItem(0)
            navigationMemory.save(destination, content.firstOrNull()?.let { "${it.type}:${it.id}" }.orEmpty(), 0)
        } else if (restoreFocusedCard && content.isNotEmpty()) {
            if (destination == MainDestination.FAVORITES && targetKey.isNotBlank() && targetKey != remembered.itemKey) {
                navigationMemory.save(destination, targetKey, targetIndex)
            }
            gridState.scrollToItem(targetIndex)
            delay(90)
            runCatching { targetRequester.requestFocus() }
        }
    }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(if (isTv) 132.dp else 105.dp),
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 9.dp),
        verticalArrangement = Arrangement.spacedBy(if (isTv) 15.dp else 10.dp),
        contentPadding = PaddingValues(5.dp, 5.dp, 5.dp, 28.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(content, key = { _, item -> "${item.type}:${item.id}" }) { index, item ->
            val key = "${item.type}:${item.id}"
            val restore = remembered.itemKey == key || index == targetIndex
            CompactPosterCard(
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
) {
    val remembered = navigationMemory.position(destination)
    val targetIndex = remembered.itemIndex.coerceIn(0, entries.lastIndex.coerceAtLeast(0))
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = targetIndex)
    LaunchedEffect(gridState, entries, destination) {
        snapshotFlow { gridState.firstVisibleItemIndex }.collect { index ->
            entries.getOrNull(index)?.let { navigationMemory.save(destination, it.key, index) }
        }
    }
    val targetRequester = remember { FocusRequester() }
    LaunchedEffect(entries, remembered.itemKey) {
        if (remembered.itemKey.isNotBlank() && entries.isNotEmpty()) {
            gridState.scrollToItem(targetIndex)
            delay(180)
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
            val restore = remembered.itemKey == entry.key || (remembered.itemKey.isBlank() && index == targetIndex)
            HistoryCard(
                entry,
                { onOpen(entry) },
                Modifier.fillMaxWidth().restoreFocus(restore, targetRequester),
                onFocused = { navigationMemory.save(destination, entry.key, index) },
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    state: HulkUiState,
    isTv: Boolean,
    onRefreshAll: () -> Unit,
    onClearHistory: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onLogout: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val open: (String) -> Unit = { url -> runCatching { uriHandler.openUri(url) }; Unit }
    val account = state.account
    val settingsListState = rememberLazyListState()
    val diagnosticsTopRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        settingsListState.scrollToItem(0)
    }
    LaunchedEffect(state.diagnostics.report?.generatedAtEpochMs) {
        if (state.diagnostics.report != null) {
            settingsListState.scrollToItem(2)
            delay(120L)
            diagnosticsTopRequester.requestFocus()
        }
    }
    LazyColumn(
        state = settingsListState,
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isTv) TV_PAGE_GUTTER else 0.dp),
        contentPadding = if (isTv) {
            PaddingValues(bottom = 24.dp)
        } else {
            PaddingValues(15.dp)
        },
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { PageTitle("الحساب والاعدادات", "ادارة اشتراكك وتجربة المشاهدة", 0, Icons.Rounded.Settings) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                AccountMetric("الحساب", account?.username?.let { "••••${it.takeLast(4)}" } ?: "—", Modifier.weight(1f))
                AccountMetric("حالة الاشتراك", account?.status ?: "—", Modifier.weight(1f))
                AccountMetric("الصلاحية", account?.let(::accountExpiry) ?: "—", Modifier.weight(1f))
                AccountMetric("الاتصالات", account?.let { "${it.activeConnections} / ${it.maxConnections}" } ?: "—", Modifier.weight(1f))
            }
        }
        item {
            DiagnosticsCenter(
                state = state.diagnostics,
                isTv = isTv,
                onRun = onRunDiagnostics,
                onShare = { report -> shareDiagnosticsReport(context, report) },
                topRequester = diagnosticsTopRequester,
            )
        }
        item {
            Column {
                Text("خدمات HULK SA", color = colors.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("الموقع اصبح امتدادا للتطبيق", color = colors.textMuted, fontSize = 11.sp)
                Spacer(Modifier.height(11.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    WebsiteCard(Icons.Rounded.Language, "اشتراك او تجديد", "hulksa.com", { open(WEBSITE_URL) }, Modifier.weight(1f))
                    WebsiteCard(Icons.Rounded.Person, "حساب العميل", "دخول برمز البريد", { open(ACCOUNT_URL) }, Modifier.weight(1f))
                    WebsiteCard(Icons.Rounded.SupportAgent, "الدعم الفني", "واتساب الرسمي", { open(SUPPORT_URL) }, Modifier.weight(1f))
                    WebsiteCard(Icons.Rounded.Apps, "مركز التطبيقات", "كل اجهزتك", { open(APPS_URL) }, Modifier.weight(1f))
                }
            }
        }
        item {
            SettingsStrip("المحتوى والبيانات") {
                FocusButton("تحديث المكتبة", onRefreshAll, compact = true)
                FocusButton("مسح سجل المشاهدة", onClearHistory, primary = false, compact = true, enabled = state.history.isNotEmpty())
            }
        }
        item {
            SettingsStrip("التطبيق") {
                Text("HULK SA  •  الاصدار ${BuildConfig.VERSION_NAME}", color = colors.textMuted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                FocusButton("تسجيل الخروج", onLogout, primary = false, compact = true)
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
private fun AccountMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalHulkColors.current
    Column(
        modifier = modifier.clip(RoundedCornerShape(15.dp)).background(Color(0xFF11120E)).padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        Text(label, color = colors.textMuted, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WebsiteCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(if (focused) 1.035f else 1f, label = "websiteCardScale")
    Column(
        modifier = modifier
            .graphicsLayer { scaleX = lift; scaleY = lift }
            .height(115.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (focused) colors.gold else Color(0xFF11120E))
            .border(if (focused) 2.dp else 0.dp, if (focused) colors.goldBright else Color.Transparent, RoundedCornerShape(16.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(15.dp),
    ) {
        Icon(icon, title, tint = if (focused) Color.Black else colors.goldBright, modifier = Modifier.size(24.dp))
        Spacer(Modifier.weight(1f))
        Text(title, color = if (focused) Color.Black else colors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(subtitle, color = if (focused) Color.Black.copy(alpha = .65f) else colors.textMuted, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun SettingsStrip(title: String, content: @Composable RowScope.() -> Unit) {
    val colors = LocalHulkColors.current
    Column {
        Text(title, color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(9.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color(0xFF11120E)).padding(15.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun CatalogHeader(
    title: String,
    resultCount: Int,
    query: String,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    isTv: Boolean,
) {
    val colors = LocalHulkColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Column(Modifier.width(if (isTv) 185.dp else 105.dp)) {
            Text(title, color = colors.text, fontSize = if (isTv) 27.sp else 20.sp, fontWeight = FontWeight.Bold)
            Text("$resultCount عنصر", color = colors.textMuted, fontSize = 10.sp)
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
    items: List<ContentItem>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    isTv: Boolean,
) {
    val context = LocalContext.current
    val prefs = remember(type) { context.getSharedPreferences("catalog_category_order_${type.name}", android.content.Context.MODE_PRIVATE) }
    var ids by remember(categories, type) {
        mutableStateOf(prefs.getString("ids", "").orEmpty().split(',').filter { it.isNotBlank() })
    }
    var moving by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ordered = remember(categories, ids) {
        val byId = categories.associateBy { it.id }
        (ids.mapNotNull(byId::get) + categories.filterNot { it.id in ids }).distinctBy { it.id }
    }
    val artworkByCategory = remember(items) {
        items.filter { !it.posterUrl.isNullOrBlank() }
            .groupBy(ContentItem::categoryId)
            .mapValues { (_, content) -> content.first() }
    }
    LaunchedEffect(selectedId) {
        val targetIndex = when (selectedId) {
            null -> 0
            FAVORITES_CATEGORY_ID -> 1
            CONTINUE_CATEGORY_ID -> 2
            else -> ordered.indexOfFirst { it.id == selectedId }
                .takeIf { it >= 0 }
                ?.plus(3)
        }
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
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(horizontal = if (isTv) 8.dp else 24.dp, vertical = 8.dp),
    ) {
        item { FocusButton("الكل", { onSelect(null) }, primary = selectedId == null, compact = true) }
        item { FocusButton("★ المفضلة", { onSelect(FAVORITES_CATEGORY_ID) }, primary = selectedId == FAVORITES_CATEGORY_ID, compact = true) }
        item { FocusButton("▶ استكمال اخر مشاهدة", { onSelect(CONTINUE_CATEGORY_ID) }, primary = selectedId == CONTINUE_CATEGORY_ID, compact = true) }
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
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("live_category_order", android.content.Context.MODE_PRIVATE) }
    var ids by remember(categories) {
        mutableStateOf(prefs.getString("ids", "").orEmpty().split(',').filter { it.isNotBlank() })
    }
    var moving by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ordered = remember(categories, ids) {
        val byId = categories.associateBy { it.id }
        (ids.mapNotNull(byId::get) + categories.filterNot { it.id in ids }).distinctBy { it.id }
    }
    val artworkByCategory = remember(items) {
        items.filter { !it.posterUrl.isNullOrBlank() }
            .groupBy(ContentItem::categoryId)
            .mapValues { (_, channels) -> channels.first() }
    }
    LaunchedEffect(selectedId) {
        val targetIndex = when (selectedId) {
            null -> 0
            FAVORITES_CATEGORY_ID -> 1
            else -> ordered.indexOfFirst { it.id == selectedId }
                .takeIf { it >= 0 }
                ?.plus(2)
        }
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
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        item {
            FocusButton(
                "الكل",
                { onSelect(null) },
                primary = selectedId == null,
                compact = true,
            )
        }
        item {
            FocusButton(
                "★ المفضلة",
                { onSelect(FAVORITES_CATEGORY_ID) },
                primary = selectedId == FAVORITES_CATEGORY_ID,
                compact = true,
            )
        }
        items(ordered, key = Category::id) { category ->
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
            )
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
        modifier = Modifier
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
            BrandBadge(Modifier.size(28.dp))
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
private fun PageTitle(title: String, subtitle: String, count: Int, icon: ImageVector) {
    val colors = LocalHulkColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(colors.gold.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Icon(icon, title, tint = colors.goldBright, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(title, color = colors.text, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(if (count > 0) "$subtitle  •  $count" else subtitle, color = colors.textMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (focused) colors.gold else Color.Black.copy(alpha = .46f))
            .border(if (focused) 2.dp else 1.dp, if (focused) colors.goldBright else colors.line, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (focused) Color.Black else colors.text, modifier = Modifier.size(21.dp))
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

private fun newest(content: List<ContentItem>): List<ContentItem> =
    content.sortedByDescending { it.addedAtEpochSeconds ?: 0L }

private fun ContentItem.matchesSearch(rawQuery: String): Boolean {
    val query = rawQuery.trim()
    if (query.isBlank()) return true
    return sequenceOf(name, year, genre, plot, nowPlaying)
        .filterNotNull()
        .any { value -> value.contains(query, ignoreCase = true) }
}

private fun HistoryEntry.isResumable(): Boolean =
    !isLive && positionMs > 0L &&
        (durationMs <= 0L || positionMs.toDouble() / durationMs < .92)

private fun categoryMatches(
    item: ContentItem,
    selectedId: String?,
    isFavorite: (ContentItem) -> Boolean,
): Boolean = when (selectedId) {
    null -> true
    FAVORITES_CATEGORY_ID -> isFavorite(item)
    CONTINUE_CATEGORY_ID -> false
    else -> item.categoryId == selectedId
}

private fun accountExpiry(account: AccountInfo): String {
    val epoch = account.expiresAtEpochSeconds ?: return "اشتراك فعال"
    val millis = epoch * 1000L
    val days = TimeUnit.MILLISECONDS.toDays(millis - System.currentTimeMillis())
    return if (days >= 0) {
        "متبقي $days يوم · ${SimpleDateFormat("yyyy/MM/dd", Locale.forLanguageTag("ar-SA")).format(Date(millis))}"
    } else {
        "منتهي"
    }
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
