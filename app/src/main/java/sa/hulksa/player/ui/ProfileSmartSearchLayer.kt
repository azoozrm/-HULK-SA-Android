package sa.hulksa.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.MainDestination
import sa.hulksa.player.data.ProfileContentSearchHistoryEntry
import sa.hulksa.player.data.ProfileContentSearchHistoryStore
import sa.hulksa.player.data.ProfileStore
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.ui.adaptive.HulkNavigationType
import sa.hulksa.player.ui.adaptive.HulkWindowWidthClass
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.components.BrandLogo
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.HulkTextField
import sa.hulksa.player.ui.components.LiveChannelHomeCard
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.components.MoviesCatalogBoxedCard
import sa.hulksa.player.ui.components.SeriesCatalogBoxedCard
import sa.hulksa.player.ui.components.TvRailDestinationItem
import sa.hulksa.player.ui.components.TvRailSurface
import sa.hulksa.player.ui.screens.FAVORITES_MOBILE_BOTTOM_PADDING
import sa.hulksa.player.ui.screens.FAVORITES_MOBILE_HORIZONTAL_PADDING
import sa.hulksa.player.ui.screens.FAVORITES_MOBILE_HORIZONTAL_SPACING
import sa.hulksa.player.ui.screens.FAVORITES_MOBILE_MIN_CELL_WIDTH
import sa.hulksa.player.ui.screens.FAVORITES_MOBILE_VERTICAL_SPACING
import sa.hulksa.player.ui.screens.FavoritesSelectGuardDecision
import sa.hulksa.player.ui.screens.MOBILE_SECTION_COUNT_SIZE
import sa.hulksa.player.ui.screens.MOBILE_SECTION_HORIZONTAL_PADDING
import sa.hulksa.player.ui.screens.MOBILE_SECTION_TITLE_SIZE
import sa.hulksa.player.ui.screens.MOVIE_COMPACT_ARTWORK_MIN_HEIGHT_DP
import sa.hulksa.player.ui.screens.MoviesErrorNotice
import sa.hulksa.player.ui.screens.MoviesOfflineEmptyState
import sa.hulksa.player.ui.screens.NavigationMemoryStore
import sa.hulksa.player.ui.screens.TV_CATEGORY_PARENT_HORIZONTAL_INSET_DP
import sa.hulksa.player.ui.screens.TV_PAGE_GUTTER
import sa.hulksa.player.ui.screens.TvCatalogFocusMoveState
import sa.hulksa.player.ui.screens.TvCatalogFocusPath
import sa.hulksa.player.ui.screens.TvGridFocusMove
import sa.hulksa.player.ui.screens.extendCategoryViewportTowardStart
import sa.hulksa.player.ui.screens.favoritesRemovalFocusIndex
import sa.hulksa.player.ui.screens.favoritesSelectGuardDecision
import sa.hulksa.player.ui.screens.focusedCardScrollCorrection
import sa.hulksa.player.ui.screens.homeHeaderActionTouchSizeDp
import sa.hulksa.player.ui.screens.movieCatalogColumnCount
import sa.hulksa.player.ui.screens.movieCatalogMinCellWidthDp
import sa.hulksa.player.ui.screens.movieCompactArtworkHeightPx
import sa.hulksa.player.ui.screens.moviesNoBringIntoViewSpec
import sa.hulksa.player.ui.screens.nextTvGridFocusIndex
import sa.hulksa.player.ui.screens.rememberCategorySidebarUnderlap
import sa.hulksa.player.ui.screens.rememberLiveCategoryStripMetrics
import sa.hulksa.player.ui.screens.rememberUsableNetworkState
import sa.hulksa.player.ui.screens.revealFocusedGridItem
import sa.hulksa.player.ui.screens.tvCatalogFocusPath
import sa.hulksa.player.ui.screens.tvCatalogMetrics
import sa.hulksa.player.ui.screens.tvPageSafeInsets
import sa.hulksa.player.ui.screens.tvRailMetrics
import sa.hulksa.player.ui.theme.LocalHulkColors

private data class SmartSearchDestination(
    val destination: MainDestination,
    val icon: ImageVector,
    val label: String,
)

private enum class SmartSearchFilter(
    val contentType: ContentType?,
    val label: String,
) {
    ALL(null, "الكل"),
    MOVIES(ContentType.MOVIE, "الافلام"),
    SERIES(ContentType.SERIES, "المسلسلات"),
    LIVE(ContentType.LIVE, "القنوات"),
    ;

    fun accepts(type: ContentType): Boolean = contentType == null || contentType == type
}

private data class SmartSearchSnapshot(
    val query: String = "",
    val filter: SmartSearchFilter = SmartSearchFilter.ALL,
    val results: List<ContentItem> = emptyList(),
)

private data class SmartSearchIndexEntry(
    val item: ContentItem,
    val normalizedName: String,
    val titleTokens: List<String>,
    val normalizedYear: String,
    val normalizedGenre: String,
    val titleHasArabic: Boolean,
    val titleHasLatin: Boolean,
    val phoneticWords: List<String>,
    val phoneticPhrase: String,
)

private data class SmartSearchIndex(
    val entries: List<SmartSearchIndexEntry> = emptyList(),
)

private val smartSearchDestinations = listOf(
    SmartSearchDestination(MainDestination.HOME, Icons.Rounded.Home, "الرئيسية"),
    SmartSearchDestination(MainDestination.LIVE, Icons.Rounded.LiveTv, "البث المباشر"),
    SmartSearchDestination(MainDestination.MOVIES, Icons.Rounded.Movie, "الافلام"),
    SmartSearchDestination(MainDestination.SERIES, Icons.Rounded.Tv, "المسلسلات"),
    SmartSearchDestination(MainDestination.FAVORITES, Icons.Rounded.Favorite, "المفضلة"),
    SmartSearchDestination(MainDestination.SEARCH, Icons.Rounded.Search, "البحث"),
    SmartSearchDestination(MainDestination.DOWNLOADS, Icons.Rounded.Download, "التنزيلات"),
    SmartSearchDestination(MainDestination.SETTINGS, Icons.Rounded.Settings, "الاعدادات"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileSmartSearchLayer(
    state: HulkUiState,
    isTv: Boolean,
    isFavorite: (ContentItem) -> Boolean,
    navigationMemory: NavigationMemoryStore,
    initialFocusAcquired: Boolean,
    onInitialFocusAcquired: () -> Unit,
    onSelectDestination: (MainDestination) -> Unit,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onSwitchProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val historyStore = remember(context) { ProfileContentSearchHistoryStore(context) }
    val profileStore = remember(context) { ProfileStore(context) }
    val activeProfileId = profileStore.activeProfileId()
    var historyRevision by remember(activeProfileId) { mutableIntStateOf(0) }
    // The existing per-profile navigation owner remembers the selected type filter, the focused
    // card and its location so details return restores the same surviving context.
    var selectedFilter by remember(activeProfileId, navigationMemory) {
        mutableStateOf(
            smartSearchFilterFromMemoryKey(navigationMemory.position(MainDestination.SEARCH).rowKey),
        )
    }
    val recentEntries = remember(activeProfileId, historyRevision) { historyStore.recent() }
    val visibleRecentEntries = remember(recentEntries, selectedFilter) {
        recentEntries.filter { selectedFilter.accepts(it.contentType) }
    }
    var lastSearchQuery by remember(activeProfileId, navigationMemory) {
        mutableStateOf(state.searchQuery)
    }
    LaunchedEffect(state.searchQuery, activeProfileId, navigationMemory) {
        if (state.searchQuery != lastSearchQuery) {
            lastSearchQuery = state.searchQuery
            navigationMemory.save(
                destination = MainDestination.SEARCH,
                itemKey = "",
                itemIndex = 0,
                rowKey = selectedFilter.name,
            )
        }
    }

    val searchIndex by produceState<SmartSearchIndex?>(
        initialValue = null,
        key1 = state.catalogs,
    ) {
        value = withContext(Dispatchers.Default) {
            buildSmartSearchIndex(state)
        }
    }

    val query = state.searchQuery.trim()
    var settledQuery by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        if (query.length < SMART_SEARCH_MIN_QUERY_LENGTH) {
            settledQuery = ""
        } else {
            delay(SEARCH_DEBOUNCE_MS)
            settledQuery = query
        }
    }
    val searchSnapshot by produceState(
        initialValue = SmartSearchSnapshot(filter = selectedFilter),
        key1 = searchIndex,
        key2 = settledQuery to selectedFilter,
    ) {
        val preparedIndex = searchIndex
        value = if (
            settledQuery.length < SMART_SEARCH_MIN_QUERY_LENGTH ||
            preparedIndex == null
        ) {
            SmartSearchSnapshot(filter = selectedFilter)
        } else {
            withContext(Dispatchers.Default) {
                SmartSearchSnapshot(
                    query = settledQuery,
                    filter = selectedFilter,
                    results = boundedSmartSearch(preparedIndex, settledQuery, selectedFilter),
                )
            }
        }
    }
    val results = if (
        query == settledQuery &&
        searchSnapshot.query == settledQuery &&
        searchSnapshot.filter == selectedFilter
    ) {
        searchSnapshot.results
    } else {
        emptyList()
    }
    val searchPending = query.length >= SMART_SEARCH_MIN_QUERY_LENGTH &&
        (searchIndex == null ||
            settledQuery != query ||
            searchSnapshot.query != settledQuery ||
            searchSnapshot.filter != selectedFilter)
    val relevantCatalogLoading = selectedFilter.contentType
        ?.let { it in state.loadingTypes }
        ?: state.loadingTypes.isNotEmpty()
    val loadingCatalogs = query.length >= SMART_SEARCH_MIN_QUERY_LENGTH &&
        relevantCatalogLoading && results.isEmpty()
    val imeVisible = !isTv && WindowInsets.isImeVisible
    val useRail = adaptiveUi.navigationType == HulkNavigationType.RAIL

    val searchFieldRequester = remember { FocusRequester() }
    val firstResultRequester = remember { FocusRequester() }
    val firstRecentRequester = remember { FocusRequester() }
    val railSearchRequester = remember { FocusRequester() }
    val filterRequesters = remember {
        SmartSearchFilter.entries.associateWith { FocusRequester() }
    }
    var railReturnRequester by remember(activeProfileId) { mutableStateOf<FocusRequester?>(null) }

    LaunchedEffect(isTv, activeProfileId, initialFocusAcquired) {
        if (!isTv || initialFocusAcquired) return@LaunchedEffect
        repeat(TV_INITIAL_SEARCH_FOCUS_ATTEMPTS) { attempt ->
            delay(if (attempt == 0) TV_INITIAL_SEARCH_FOCUS_DELAY_MS else TV_INITIAL_SEARCH_FOCUS_RETRY_MS)
            if (!initialFocusAcquired) {
                runCatching { searchFieldRequester.requestFocus() }
            }
        }
    }

    val recordAndOpen: (ContentItem) -> Unit = { item ->
        historyStore.record(item)
        historyRevision++
        onOpen(item)
    }
    val openRecent: (ProfileContentSearchHistoryEntry) -> Unit = { entry ->
        val resolved = state.catalogs[entry.contentType]
            ?.items
            ?.firstOrNull { it.id == entry.contentId }
        if (resolved != null) {
            historyStore.record(resolved)
            historyRevision++
            onOpen(resolved)
        } else {
            onSearch(entry.title)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        if (useRail) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties {
                        if (isTv) onEnter = { searchFieldRequester.requestFocus() }
                    }
                    .focusGroup(),
            ) {
                SmartSearchRail(
                    isTv = isTv,
                    selected = MainDestination.SEARCH,
                    searchFieldRequester = searchFieldRequester,
                    searchRailRequester = railSearchRequester,
                    contentReturnRequester = railReturnRequester ?: searchFieldRequester,
                    preferSearchFieldOnEnter = isTv && !initialFocusAcquired,
                    onSelectDestination = onSelectDestination,
                    onSwitchProfile = onSwitchProfile,
                )
                SmartSearchContent(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    state = state,
                    isTv = isTv,
                    recentEntries = visibleRecentEntries,
                    results = results,
                    selectedFilter = selectedFilter,
                    filterRequesters = filterRequesters,
                    searchPending = searchPending,
                    loadingCatalogs = loadingCatalogs,
                    imeVisible = imeVisible,
                    onRefresh = onRefresh,
                    navigationMemory = navigationMemory,
                    searchFieldRequester = searchFieldRequester,
                    firstResultRequester = firstResultRequester,
                    firstRecentRequester = firstRecentRequester,
                    railSearchRequester = if (isTv) railSearchRequester else null,
                    isFavorite = isFavorite,
                    onSearch = onSearch,
                    onSearchFieldFocusChanged = { focused ->
                        if (focused) {
                            onInitialFocusAcquired()
                            railReturnRequester = searchFieldRequester
                        }
                    },
                    onContentFocusTargetChanged = { requester ->
                        railReturnRequester = requester
                    },
                    onFilterSelected = { filter ->
                        selectedFilter = filter
                        navigationMemory.save(
                            destination = MainDestination.SEARCH,
                            itemKey = "",
                            itemIndex = 0,
                            rowKey = filter.name,
                        )
                    },
                    onOpenResult = recordAndOpen,
                    onOpenRecent = openRecent,
                    onRemoveRecent = { entry ->
                        historyStore.remove(entry.contentType, entry.contentId)
                        historyRevision++
                    },
                    onClearRecent = {
                        historyStore.clear()
                        historyRevision++
                    },
                    onToggleFavorite = onToggleFavorite,
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                SmartSearchContent(
                    modifier = Modifier.weight(1f).fillMaxWidth().imePadding(),
                    state = state,
                    isTv = false,
                    recentEntries = visibleRecentEntries,
                    results = results,
                    selectedFilter = selectedFilter,
                    filterRequesters = filterRequesters,
                    searchPending = searchPending,
                    loadingCatalogs = loadingCatalogs,
                    imeVisible = imeVisible,
                    onRefresh = onRefresh,
                    navigationMemory = navigationMemory,
                    searchFieldRequester = searchFieldRequester,
                    firstResultRequester = firstResultRequester,
                    firstRecentRequester = firstRecentRequester,
                    railSearchRequester = null,
                    isFavorite = isFavorite,
                    onSearch = onSearch,
                    onSearchFieldFocusChanged = { focused ->
                        if (focused) onInitialFocusAcquired()
                    },
                    onContentFocusTargetChanged = {},
                    onFilterSelected = { filter ->
                        selectedFilter = filter
                        navigationMemory.save(
                            destination = MainDestination.SEARCH,
                            itemKey = "",
                            itemIndex = 0,
                            rowKey = filter.name,
                        )
                    },
                    onOpenResult = recordAndOpen,
                    onOpenRecent = openRecent,
                    onRemoveRecent = { entry ->
                        historyStore.remove(entry.contentType, entry.contentId)
                        historyRevision++
                    },
                    onClearRecent = {
                        historyStore.clear()
                        historyRevision++
                    },
                    onToggleFavorite = onToggleFavorite,
                )
                if (!imeVisible) {
                    Spacer(
                        Modifier
                            .navigationBarsPadding()
                            .height(
                                stableMobileNavigationReservedHeight(
                                    windowWidthDp = adaptiveUi.screenWidthDp,
                                    downloadsEnabled = state.operations.features.downloadsEnabled,
                                ),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartSearchContent(
    modifier: Modifier,
    state: HulkUiState,
    isTv: Boolean,
    recentEntries: List<ProfileContentSearchHistoryEntry>,
    results: List<ContentItem>,
    selectedFilter: SmartSearchFilter,
    filterRequesters: Map<SmartSearchFilter, FocusRequester>,
    searchPending: Boolean,
    loadingCatalogs: Boolean,
    imeVisible: Boolean,
    onRefresh: () -> Unit,
    navigationMemory: NavigationMemoryStore,
    searchFieldRequester: FocusRequester,
    firstResultRequester: FocusRequester,
    firstRecentRequester: FocusRequester,
    railSearchRequester: FocusRequester?,
    isFavorite: (ContentItem) -> Boolean,
    onSearch: (String) -> Unit,
    onSearchFieldFocusChanged: (Boolean) -> Unit,
    onContentFocusTargetChanged: (FocusRequester) -> Unit,
    onFilterSelected: (SmartSearchFilter) -> Unit,
    onOpenResult: (ContentItem) -> Unit,
    onOpenRecent: (ProfileContentSearchHistoryEntry) -> Unit,
    onRemoveRecent: (ProfileContentSearchHistoryEntry) -> Unit,
    onClearRecent: () -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val screenWidth = adaptiveUi.screenWidthDp
    val screenHeight = adaptiveUi.screenHeightDp
    val compactHeight = !isTv && screenHeight < 600
    val compactContent = compactHeight || imeVisible
    val tvSafeInsets = remember(screenWidth, screenHeight) {
        tvPageSafeInsets(
            screenWidthDp = screenWidth,
            screenHeightDp = screenHeight,
        )
    }
    val selectedFilterRequester = filterRequesters.getValue(selectedFilter)
    val networkUsable by rememberUsableNetworkState()
    val errorMessage = state.errorMessage
    val offline = errorMessage != null && !networkUsable
    val retryRequester = remember { FocusRequester() }
    val query = state.searchQuery
    val queryEligible = query.trim().length >= SMART_SEARCH_MIN_QUERY_LENGTH
    val gridRows = remember(queryEligible, results, recentEntries, state.catalogs) {
        if (queryEligible) {
            results.map { SmartSearchGridRow.Result(it) }
        } else {
            recentEntries.map { entry ->
                SmartSearchGridRow.History(
                    entry = entry,
                    resolved = state.catalogs[entry.contentType]
                        ?.items
                        ?.firstOrNull { it.id == entry.contentId },
                )
            }
        }
    }
    val count = if (queryEligible) results.size else recentEntries.size
    val countUnit = if (queryEligible) "نتيجة" else "عنصر"
    val firstGridRequester = if (queryEligible) firstResultRequester else firstRecentRequester
    val firstContentRequester = when {
        gridRows.isNotEmpty() -> firstGridRequester
        errorMessage != null -> retryRequester
        else -> null
    }
    val hasRemovableHistory = recentEntries.isNotEmpty()
    // Restore the remembered card only while it still belongs to the current projection.
    val rememberedItemKey = navigationMemory.position(MainDestination.SEARCH).itemKey
    val restoreKey = remember(rememberedItemKey, gridRows) {
        rememberedItemKey.takeIf { key -> key.isNotBlank() && gridRows.any { it.key == key } }
    }
    // The one TV editing owner for the query field. While it is active, projection changes are
    // editing input, not content navigation: they must not move focus to filters, cards or a
    // restored row. Only the explicit IME Search/Done action, Back, intentional navigation or
    // the existing lifecycle exit ends editing.
    var tvEditing by remember { mutableStateOf(false) }
    // When the whole projection disappears (for example the last history deletion), focus returns
    // to the always-attached selected filter, which stays composed through the content change.
    var previouslyHadRows by remember { mutableStateOf(gridRows.isNotEmpty()) }
    val tvEditingNow = rememberUpdatedState(tvEditing)
    LaunchedEffect(gridRows.isNotEmpty(), selectedFilter, errorMessage) {
        val hasRows = gridRows.isNotEmpty()
        if (previouslyHadRows && !hasRows && isTv) {
            withFrameNanos { }
            // Recheck input ownership after the frame suspension: an active editing session owns
            // the focus and keeps it even while the projection is temporarily empty.
            if (!tvEditingNow.value) moveFocusTo(selectedFilterRequester)
        }
        previouslyHadRows = hasRows
    }

    Column(
        modifier = modifier
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
            modifier = Modifier
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
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stackToolbar = !isTv && maxWidth < SEARCH_TOOLBAR_STACK_WIDTH_DP.dp
                if (isTv) {
                    // Exact accepted Series field allocation: the field keeps the width its
                    // CatalogHeader slot receives beside the title, management action, refresh
                    // action and their accepted spacers. The action space toward physical LEFT
                    // stays unused and no weight/fill may expand the field into it.
                    val textMeasurer = rememberTextMeasurer()
                    val baseTextStyle = LocalTextStyle.current
                    val density = LocalDensity.current
                    val manageActionWidth = remember(textMeasurer, baseTextStyle, density) {
                        val captionWidth = textMeasurer.measure(
                            text = AnnotatedString("ادارة الفئات"),
                            style = baseTextStyle.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                        ).size.width
                        with(density) { captionWidth.toDp() } + 6.dp + 17.dp + 24.dp
                    }
                    val rowContentWidth = (maxWidth - TV_PAGE_GUTTER).coerceAtLeast(1.dp)
                    val fieldWidth = (
                        rowContentWidth - SEARCH_TITLE_SLOT_WIDTH_DP.dp -
                            (SEARCH_HEADER_GAP_DP.dp - TV_PAGE_GUTTER) - manageActionWidth -
                            SEARCH_HEADER_GAP_DP.dp - homeHeaderActionTouchSizeDp().dp -
                            SEARCH_HEADER_GAP_DP.dp - TV_PAGE_GUTTER
                        ).coerceAtLeast(120.dp).coerceAtMost(630.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = TV_PAGE_GUTTER),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        SmartSearchToolbarTitle(
                            title = "البحث",
                            count = count,
                            countUnit = countUnit,
                            isTv = isTv,
                        )
                        Spacer(Modifier.width(SEARCH_HEADER_GAP_DP.dp - TV_PAGE_GUTTER))
                        SmartSearchInput(
                            value = query,
                            onValueChange = onSearch,
                            isTv = isTv,
                            editing = isTv && tvEditing,
                            onEditingChange = { tvEditing = it },
                            hasResults = results.isNotEmpty(),
                            hasRecent = query.isBlank() && recentEntries.isNotEmpty(),
                            searchFieldRequester = searchFieldRequester,
                            firstFilterRequester = selectedFilterRequester,
                            firstResultRequester = firstResultRequester,
                            firstRecentRequester = firstRecentRequester,
                            railSearchRequester = railSearchRequester,
                            onFocusStateChanged = onSearchFieldFocusChanged,
                            modifier = Modifier.width(fieldWidth),
                        )
                    }
                } else if (stackToolbar) {
                    Column(Modifier.fillMaxWidth()) {
                        SmartSearchToolbarTitle(
                            title = "البحث",
                            count = count,
                            countUnit = countUnit,
                            isTv = isTv,
                        )
                        Spacer(Modifier.height(if (compactContent) 6.dp else 8.dp))
                        SmartSearchInput(
                            value = query,
                            onValueChange = onSearch,
                            isTv = isTv,
                            editing = false,
                            onEditingChange = { tvEditing = it },
                            hasResults = results.isNotEmpty(),
                            hasRecent = query.isBlank() && recentEntries.isNotEmpty(),
                            searchFieldRequester = searchFieldRequester,
                            firstFilterRequester = null,
                            firstResultRequester = firstResultRequester,
                            firstRecentRequester = firstRecentRequester,
                            railSearchRequester = railSearchRequester,
                            onFocusStateChanged = onSearchFieldFocusChanged,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        SmartSearchToolbarTitle(
                            title = "البحث",
                            count = count,
                            countUnit = countUnit,
                            isTv = isTv,
                        )
                        SmartSearchInput(
                            value = query,
                            onValueChange = onSearch,
                            isTv = isTv,
                            editing = false,
                            onEditingChange = { tvEditing = it },
                            hasResults = results.isNotEmpty(),
                            hasRecent = query.isBlank() && recentEntries.isNotEmpty(),
                            searchFieldRequester = searchFieldRequester,
                            firstFilterRequester = null,
                            firstResultRequester = firstResultRequester,
                            firstRecentRequester = firstRecentRequester,
                            railSearchRequester = railSearchRequester,
                            onFocusStateChanged = onSearchFieldFocusChanged,
                            modifier = Modifier.weight(1f).widthIn(max = 630.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(if (compactContent) 6.dp else 8.dp))
            val clearAllRequester = remember { FocusRequester() }
            val lastFilterRequester = filterRequesters.getValue(SmartSearchFilter.entries.last())
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isTv) {
                            Modifier.padding(end = tvSafeInsets.horizontalDp.dp)
                        } else {
                            Modifier
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    SmartSearchFilterBar(
                        selected = selectedFilter,
                        isTv = isTv,
                        requesters = filterRequesters,
                        searchFieldRequester = searchFieldRequester,
                        downRequester = firstContentRequester,
                        railSearchRequester = railSearchRequester,
                        leftEdgeRequester = if (hasRemovableHistory) clearAllRequester else null,
                        onContentFocusTargetChanged = onContentFocusTargetChanged,
                        onSelect = onFilterSelected,
                    )
                }
                if (hasRemovableHistory) {
                    Spacer(Modifier.width(8.dp))
                    FocusButton(
                        text = "مسح الكل",
                        onClick = onClearRecent,
                        modifier = Modifier
                            .focusRequester(clearAllRequester)
                            .onFocusChanged { focusState ->
                                if (isTv && focusState.isFocused) {
                                    onContentFocusTargetChanged(clearAllRequester)
                                }
                            }
                            .focusProperties {
                                if (isTv) {
                                    up = selectedFilterRequester
                                    down = firstContentRequester ?: FocusRequester.Cancel
                                    right = lastFilterRequester
                                    left = FocusRequester.Cancel
                                }
                            }
                            .onPreviewKeyEvent { event ->
                                if (!isTv || event.type != KeyEventType.KeyDown) {
                                    false
                                } else {
                                    when (event.key) {
                                        Key.DirectionUp -> moveFocusTo(selectedFilterRequester)
                                        Key.DirectionDown -> {
                                            firstContentRequester?.let { moveFocusTo(it) } ?: true
                                        }
                                        Key.DirectionRight -> moveFocusTo(lastFilterRequester)
                                        Key.DirectionLeft -> true
                                        else -> false
                                    }
                                }
                            },
                        primary = false,
                        compact = true,
                        outlined = true,
                        scaleOnFocus = false,
                    )
                }
            }
        }

        when {
            state.searchQuery.isBlank() && gridRows.isNotEmpty() -> {
                SmartSearchCatalogGrid(
                    modifier = Modifier.weight(1f),
                    rows = gridRows,
                    isTv = isTv,
                    editing = isTv && tvEditing,
                    restoreKey = restoreKey,
                    firstItemRequester = firstGridRequester,
                    railSearchRequester = railSearchRequester,
                    onFocusedRow = { index, key ->
                        navigationMemory.save(
                            destination = MainDestination.SEARCH,
                            itemKey = key,
                            itemIndex = index,
                            rowKey = selectedFilter.name,
                        )
                    },
                    onMoveUpFromFirstRow = { moveFocusTo(selectedFilterRequester) },
                    onContentFocusTargetChanged = onContentFocusTargetChanged,
                    onOpenRow = { row ->
                        when (row) {
                            is SmartSearchGridRow.Result -> onOpenResult(row.item)
                            is SmartSearchGridRow.History -> onOpenRecent(row.entry)
                        }
                    },
                    onDeleteRow = { row -> onRemoveRecent(row.entry) },
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                )
            }

            state.searchQuery.isBlank() -> {
                SmartSearchStateNotice(
                    modifier = Modifier.weight(1f),
                    isTv = isTv,
                    title = "ابدا البحث",
                    subtitle = "اكتب اسم فلم او مسلسل او قناة",
                )
            }

            !queryEligible -> {
                SmartSearchStateNotice(
                    modifier = Modifier.weight(1f),
                    isTv = isTv,
                    title = "اكتب حرفين على الاقل",
                    subtitle = "تظهر النتائج مباشرة بعد كتابة حرفين",
                )
            }

            results.isNotEmpty() -> {
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    if (errorMessage != null) {
                        MoviesErrorNotice(
                            title = if (offline) "لا يوجد اتصال بالانترنت" else "تعذر تحميل المحتوى",
                            body = if (offline) "تحقق من اتصالك وحاول مرة اخرى" else "حاول مرة اخرى",
                            onRetry = onRefresh,
                            isTv = isTv,
                            networkFailure = offline,
                            retryRequester = retryRequester,
                            onRetryFocusChanged = { focused ->
                                if (isTv && focused) onContentFocusTargetChanged(retryRequester)
                            },
                            onRetryUp = if (isTv) {
                                { moveFocusTo(selectedFilterRequester) }
                            } else {
                                null
                            },
                            onRetryDown = if (isTv) {
                                { moveFocusTo(firstGridRequester) }
                            } else {
                                null
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    SmartSearchCatalogGrid(
                        modifier = Modifier.weight(1f),
                        rows = gridRows,
                        isTv = isTv,
                        editing = isTv && tvEditing,
                        restoreKey = restoreKey,
                        firstItemRequester = firstGridRequester,
                        railSearchRequester = railSearchRequester,
                        onFocusedRow = { index, key ->
                            navigationMemory.save(
                                destination = MainDestination.SEARCH,
                                itemKey = key,
                                itemIndex = index,
                                rowKey = selectedFilter.name,
                            )
                        },
                        onMoveUpFromFirstRow = { moveFocusTo(selectedFilterRequester) },
                        onContentFocusTargetChanged = onContentFocusTargetChanged,
                        onOpenRow = { row ->
                            (row as? SmartSearchGridRow.Result)?.let { onOpenResult(it.item) }
                        },
                        onDeleteRow = null,
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite,
                    )
                }
            }

            searchPending -> {
                SmartSearchStateNotice(
                    modifier = Modifier.weight(1f),
                    isTv = isTv,
                    title = "جاري البحث",
                    subtitle = null,
                    loading = true,
                )
            }

            loadingCatalogs -> {
                SmartSearchStateNotice(
                    modifier = Modifier.weight(1f),
                    isTv = isTv,
                    title = "جاري تحميل المحتوى",
                    subtitle = null,
                    loading = true,
                )
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    MoviesOfflineEmptyState(
                        onRetry = onRefresh,
                        title = if (offline) {
                            "لا يوجد اتصال بالانترنت ، تحقق من اتصالك وحاول مرة اخرى"
                        } else {
                            "تعذر تحميل المحتوى ، حاول مرة اخرى"
                        },
                        body = null,
                        networkFailure = offline,
                        retryRequester = retryRequester,
                    )
                }
            }

            else -> {
                SmartSearchStateNotice(
                    modifier = Modifier.weight(1f),
                    isTv = isTv,
                    title = if (selectedFilter == SmartSearchFilter.ALL) {
                        "لا توجد نتائج مطابقة"
                    } else {
                        "لا توجد نتائج في ${selectedFilter.label}"
                    },
                    subtitle = "جرب اسما اخر او غير نوع المحتوى",
                )
            }
        }
    }
}

@Composable
private fun SmartSearchToolbarTitle(
    title: String,
    count: Int,
    countUnit: String,
    isTv: Boolean,
) {
    val colors = LocalHulkColors.current
    Column(Modifier.width(if (isTv) 185.dp else 105.dp)) {
        Text(
            text = title,
            color = colors.text,
            fontSize = if (isTv) 27.sp else MOBILE_SECTION_TITLE_SIZE,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "$count $countUnit",
            color = colors.textMuted,
            fontSize = if (isTv) 10.sp else MOBILE_SECTION_COUNT_SIZE,
        )
    }
}

@Composable
private fun SmartSearchStateNotice(
    modifier: Modifier,
    isTv: Boolean,
    title: String,
    subtitle: String?,
    loading: Boolean = false,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(12.dp)
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = if (isTv) 520.dp else 380.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(Color(0xFF11120D))
                .border(1.dp, colors.gold.copy(alpha = .35f), shape)
                .padding(horizontal = 16.dp, vertical = if (isTv) 20.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (loading) {
                LoadingRing()
            } else {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = colors.gold,
                    modifier = Modifier.size(if (isTv) 26.dp else 24.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                color = colors.text,
                fontWeight = FontWeight.Bold,
                fontSize = if (isTv) 15.sp else 14.sp,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = colors.textMuted,
                    fontSize = if (isTv) 11.sp else 10.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmartSearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    isTv: Boolean,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    hasResults: Boolean,
    hasRecent: Boolean,
    searchFieldRequester: FocusRequester,
    firstFilterRequester: FocusRequester?,
    firstResultRequester: FocusRequester,
    firstRecentRequester: FocusRequester,
    railSearchRequester: FocusRequester?,
    onFocusStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val windowInfo = LocalWindowInfo.current
    var backSequenceConsumed by remember { mutableStateOf(false) }
    val voiceSearchRequester = remember { FocusRequester() }
    val imeVisibleOnTv = isTv && WindowInsets.isImeVisible
    var hadImeVisible by remember { mutableStateOf(false) }
    // The system keyboard can close itself (Back reaches the IME window first) without any app
    // focus change; that still ends the TV editing session while query and page stay untouched.
    LaunchedEffect(isTv, editing, imeVisibleOnTv) {
        if (!isTv) return@LaunchedEffect
        if (imeVisibleOnTv) hadImeVisible = true
        if (editing && hadImeVisible && !imeVisibleOnTv) {
            onEditingChange(false)
        }
        if (!editing) hadImeVisible = imeVisibleOnTv
    }

    val moveDown: () -> Boolean = {
        when {
            firstFilterRequester != null -> {
                keyboardController?.hide()
                runCatching { firstFilterRequester.requestFocus() }.getOrDefault(false)
            }
            hasResults -> {
                keyboardController?.hide()
                runCatching { firstResultRequester.requestFocus() }.getOrDefault(false)
            }
            hasRecent -> {
                keyboardController?.hide()
                runCatching { firstRecentRequester.requestFocus() }.getOrDefault(false)
            }
            else -> false
        }
    }

    val tvModifier = if (isTv) {
        Modifier
            .focusRequester(searchFieldRequester)
            .focusProperties {
                firstFilterRequester?.let { down = it }
                up = FocusRequester.Cancel
                left = voiceSearchRequester
                railSearchRequester?.let { right = it }
            }
            .onFocusChanged { state ->
                onFocusStateChanged(state.isFocused)
                // A transient platform/IME window blur is not intentional app navigation; only a
                // real focus transfer inside the focused app window ends the editing session.
                if (!state.isFocused && windowInfo.isWindowFocused) {
                    onEditingChange(false)
                    keyboardController?.hide()
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Back && editing) {
                    // One Back owner: the first Back closes editing and keeps the query and the
                    // Search page. While the system keyboard is still visible its Back is
                    // consumed with its release; when the keyboard already closed itself, this
                    // Back is the later one and must follow the existing navigation.
                    when {
                        event.type == KeyEventType.KeyDown && imeVisibleOnTv -> {
                            backSequenceConsumed = true
                            onEditingChange(false)
                            keyboardController?.hide()
                            true
                        }
                        event.type == KeyEventType.KeyDown -> {
                            onEditingChange(false)
                            false
                        }
                        event.type == KeyEventType.KeyUp && backSequenceConsumed -> {
                            backSequenceConsumed = false
                            true
                        }
                        else -> false
                    }
                } else if (event.type != KeyEventType.KeyDown) {
                    false
                } else if (!editing && (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                    onEditingChange(true)
                    keyboardController?.show()
                    true
                } else if (!editing && event.key == Key.DirectionDown) {
                    moveDown()
                } else if (!editing && event.key == Key.DirectionUp) {
                    true
                } else if (!editing && event.key == Key.DirectionLeft) {
                    runCatching { voiceSearchRequester.requestFocus() }.getOrDefault(false)
                } else if (!editing && event.key == Key.DirectionRight) {
                    railSearchRequester?.let {
                        runCatching { it.requestFocus() }.getOrDefault(false)
                    } ?: true
                } else {
                    false
                }
            }
    } else {
        Modifier.onFocusChanged { onFocusStateChanged(it.isFocused) }
    }

    Box(modifier) {
        HulkTextField(
            value = value,
            onValueChange = onValueChange,
            label = "اكتب اسم فلم او مسلسل او قناة",
            modifier = Modifier.fillMaxWidth().then(tvModifier),
            readOnly = isTv && !editing,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboardController?.hide()
                    if (isTv) {
                        // The explicit IME Search/Done transition ends editing once and hands the
                        // focus to the first toolbar target without activating it.
                        onEditingChange(false)
                        moveDown()
                    } else {
                        focusManager.clearFocus(force = true)
                    }
                },
            ),
            endActionSpace = if (isTv) 38.dp else 40.dp,
        )
        InlineVoiceSearchAction(
            query = value,
            isTv = isTv,
            requester = voiceSearchRequester,
            searchFieldRequester = searchFieldRequester,
            downRequester = firstFilterRequester,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = if (isTv) 10.dp else 8.dp),
        )
    }
}

@Composable
private fun SmartSearchFilterBar(
    selected: SmartSearchFilter,
    isTv: Boolean,
    requesters: Map<SmartSearchFilter, FocusRequester>,
    searchFieldRequester: FocusRequester,
    downRequester: FocusRequester?,
    railSearchRequester: FocusRequester?,
    leftEdgeRequester: FocusRequester?,
    onContentFocusTargetChanged: (FocusRequester) -> Unit,
    onSelect: (SmartSearchFilter) -> Unit,
) {
    val itemMetrics = rememberLiveCategoryStripMetrics()
    val sidebarUnderlap = rememberCategorySidebarUnderlap(isTv, 8.dp)
    val focusScope = rememberCoroutineScope()
    var downFocusJob by remember { mutableStateOf<Job?>(null) }

    fun cancelDownFocusJob() {
        downFocusJob?.cancel()
        downFocusJob = null
    }

    DisposableEffect(Unit) {
        onDispose { downFocusJob?.cancel() }
    }

    fun moveFocus(target: FocusRequester?): Boolean =
        target != null && runCatching { target.requestFocus() }.getOrDefault(false)

    fun requestDownFocus(): Boolean {
        val target = downRequester ?: return false
        val focused = runCatching { target.requestFocus() }.getOrDefault(false)
        cancelDownFocusJob()
        if (focused) return true
        // The content may still be attaching; keep exactly one bounded retry and cancel it as soon
        // as this selector loses focus so an obsolete job cannot pull focus back to the content.
        downFocusJob = focusScope.launch {
            delay(TV_GRID_FOCUS_SETTLE_MS)
            runCatching { target.requestFocus() }
        }
        return false
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .extendCategoryViewportTowardStart(sidebarUnderlap.viewportExtraDp.dp)
            .focusGroup(),
        contentPadding = PaddingValues(
            start = sidebarUnderlap.startContentPaddingDp.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items(
            items = SmartSearchFilter.entries,
            key = SmartSearchFilter::name,
        ) { filter ->
            val active = selected == filter
            val index = SmartSearchFilter.entries.indexOf(filter)
            val requester = requesters.getValue(filter)
            val leftNeighbor = SmartSearchFilter.entries.getOrNull(index + 1)
            val rightNeighbor = SmartSearchFilter.entries.getOrNull(index - 1)
            FocusButton(
                text = filter.label,
                onClick = { onSelect(filter) },
                modifier = Modifier
                    .focusRequester(requester)
                    .onFocusChanged { state ->
                        if (!state.isFocused) cancelDownFocusJob()
                        if (isTv && state.isFocused) onContentFocusTargetChanged(requester)
                    }
                    .focusProperties {
                        up = searchFieldRequester
                        if (downRequester != null) {
                            down = downRequester
                        } else if (isTv) {
                            down = FocusRequester.Cancel
                        }
                        if (isTv) {
                            left = leftNeighbor?.let(requesters::getValue)
                                ?: leftEdgeRequester
                                ?: FocusRequester.Cancel
                            right = rightNeighbor?.let(requesters::getValue)
                                ?: railSearchRequester
                                ?: FocusRequester.Cancel
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        if (!isTv || event.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (event.key) {
                                Key.DirectionUp -> {
                                    cancelDownFocusJob()
                                    moveFocus(searchFieldRequester)
                                }
                                Key.DirectionDown -> requestDownFocus()
                                Key.DirectionLeft -> {
                                    cancelDownFocusJob()
                                    when {
                                        leftNeighbor != null -> moveFocus(requesters.getValue(leftNeighbor))
                                        leftEdgeRequester != null -> moveFocus(leftEdgeRequester)
                                        else -> true
                                    }
                                }
                                Key.DirectionRight -> {
                                    cancelDownFocusJob()
                                    if (rightNeighbor != null) {
                                        moveFocus(requesters.getValue(rightNeighbor))
                                    } else {
                                        onContentFocusTargetChanged(requester)
                                        if (railSearchRequester != null) {
                                            moveFocus(railSearchRequester)
                                        } else {
                                            true
                                        }
                                    }
                                }
                                else -> false
                            }
                        }
                    }
                    .then(itemMetrics.compactHeightModifier),
                primary = active,
                compact = true,
                scaleOnFocus = false,
                textSizeSp = itemMetrics.textSizeSp,
                textLineHeightSp = 15,
            )
        }
    }
}


/** One unified Search catalog row: a ranked result or a saved-history entry. */
private sealed interface SmartSearchGridRow {
    val key: String

    data class Result(val item: ContentItem) : SmartSearchGridRow {
        override val key: String get() = "result:${item.type}:${item.id}"
    }

    data class History(
        val entry: ProfileContentSearchHistoryEntry,
        val resolved: ContentItem?,
    ) : SmartSearchGridRow {
        override val key: String get() = "history:${entry.stableKey}"

        val cardItem: ContentItem
            get() = resolved ?: ContentItem(
                id = entry.contentId,
                name = entry.title,
                categoryId = "",
                type = entry.contentType,
                posterUrl = entry.posterUrl,
                rating = null,
                year = entry.year,
                containerExtension = null,
            )
    }
}

/**
 * One vertical Search catalog grid for both projections, on the accepted R10 single-scroll-owner
 * policy: accepted catalog metrics and column policy shared by layout and D-pad arithmetic, no
 * competing framework BringIntoView on TV, and a measured full-card reveal that includes the
 * footer/actions and the TV safe bottom inset. Search keeps its own memory row and never shares
 * another destination's navigation memory.
 */
@Composable
private fun SmartSearchCatalogGrid(
    modifier: Modifier,
    rows: List<SmartSearchGridRow>,
    isTv: Boolean,
    editing: Boolean,
    restoreKey: String?,
    firstItemRequester: FocusRequester,
    railSearchRequester: FocusRequester?,
    onFocusedRow: (Int, String) -> Unit,
    onMoveUpFromFirstRow: () -> Boolean,
    onContentFocusTargetChanged: (FocusRequester) -> Unit,
    onOpenRow: (SmartSearchGridRow) -> Unit,
    onDeleteRow: ((SmartSearchGridRow.History) -> Unit)?,
    isFavorite: (ContentItem) -> Boolean,
    onToggleFavorite: (ContentItem) -> Unit,
) {
    val adaptiveUi = LocalAdaptiveUi.current
    val isTelevision = adaptiveUi.isTelevision
    val density = LocalDensity.current
    val metrics = remember(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp) {
        tvCatalogMetrics(
            screenWidthDp = adaptiveUi.screenWidthDp,
            screenHeightDp = adaptiveUi.screenHeightDp,
            boxedCards = true,
        )
    }
    val keys = remember(rows) { rows.map { it.key } }
    val requesters = remember(keys, firstItemRequester) {
        List(keys.size) { index -> if (index == 0) firstItemRequester else FocusRequester() }
    }
    val deleteRequesters = remember(keys, onDeleteRow != null) {
        List(keys.size) { FocusRequester() }
    }
    val focusMoveState = remember(keys) { TvCatalogFocusMoveState() }
    val scope = rememberCoroutineScope()
    var focusedIndex by remember { mutableIntStateOf(-1) }
    var focusedKey by remember { mutableStateOf<String?>(null) }
    var deleteFocusedIndex by remember { mutableIntStateOf(-1) }
    var suppressSelectKeyUp by remember { mutableStateOf(false) }
    val previousKeys = remember { mutableStateOf(keys) }
    val editingNow = rememberUpdatedState(editing)
    val initialIndex = remember(keys, restoreKey) {
        restoreKey?.let(keys::indexOf)?.takeIf { it >= 0 } ?: 0
    }
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = initialIndex)
    val focusViewportInsetPx = with(density) { metrics.focusViewportInsetDp.dp.roundToPx() }
    val focusSafeBottomInsetPx = with(density) { metrics.focusSafeBottomInsetDp.dp.roundToPx() }
    val inheritedBringIntoViewSpec = LocalBringIntoViewSpec.current
    val searchBringIntoViewSpec = remember { moviesNoBringIntoViewSpec() }
    val gridBringIntoViewSpec = if (isTelevision) searchBringIntoViewSpec else inheritedBringIntoViewSpec

    DisposableEffect(focusMoveState) {
        onDispose { focusMoveState.job?.cancel() }
    }

    // Editing owns the input while it is active: obsolete grid focus jobs are cancelled the
    // moment editing begins so a queued relocation can never pull focus out of the query field.
    LaunchedEffect(editing) {
        if (editing) {
            focusMoveState.job?.cancel()
            focusMoveState.job = null
        }
    }

    fun currentIndexCorrection(index: Int): Int? {
        val layoutInfo = gridState.layoutInfo
        val targetInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return null
        return focusedCardScrollCorrection(
            itemTop = targetInfo.offset.y,
            itemBottom = targetInfo.offset.y + targetInfo.size.height,
            usableStart = layoutInfo.viewportStartOffset + focusViewportInsetPx,
            usableEnd = layoutInfo.viewportEndOffset - focusSafeBottomInsetPx - focusViewportInsetPx,
            marginPx = focusViewportInsetPx,
        )
    }

    suspend fun ensureIndexFullyVisible(index: Int) {
        revealFocusedGridItem(
            gridState = gridState,
            index = index,
            focusInsetPx = focusViewportInsetPx,
            safeBottomInsetPx = focusSafeBottomInsetPx,
            extraMarginPx = focusViewportInsetPx,
        )
    }

    suspend fun focusIndex(index: Int, ensureFullyVisible: Boolean) {
        val requester = requesters.getOrNull(index) ?: return
        // An offscreen target first gets a bounded one-row reveal; the attachment below comes from
        // real layout, never from a fixed settle delay. Once attached, one measured correction is
        // applied and focus is granted immediately.
        if (currentIndexCorrection(index) == null) {
            repeat(2) {
                val visible = gridState.layoutInfo.visibleItemsInfo
                if (visible.any { it.index == index }) return@repeat
                val rowHeight = visible.firstOrNull()?.size?.height ?: return@repeat
                val step = rowHeight + with(density) { metrics.verticalSpacingDp.dp.roundToPx() }
                val firstVisible = visible.minOfOrNull { it.index } ?: return@repeat
                val lastVisible = visible.maxOfOrNull { it.index } ?: return@repeat
                when {
                    index > lastVisible -> gridState.animateScrollBy(step.toFloat())
                    index < firstVisible -> gridState.animateScrollBy(-step.toFloat())
                    else -> return@repeat
                }
            }
        }
        if (ensureFullyVisible) {
            currentIndexCorrection(index)?.let { correction ->
                if (correction != 0) gridState.scrollBy(correction.toFloat())
            }
        }
        runCatching { requester.requestFocus() }
    }

    fun saveFocusedRow(index: Int) {
        val key = keys.getOrNull(index) ?: return
        focusedIndex = index
        focusedKey = key
        onFocusedRow(index, key)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val columnCount: Int
        val contentPadding: PaddingValues
        val horizontalSpacing: Dp
        val verticalSpacing: Dp
        val availableGridWidth: Dp
        if (isTelevision) {
            horizontalSpacing = metrics.horizontalSpacingDp.dp
            verticalSpacing = metrics.verticalSpacingDp.dp
            contentPadding = PaddingValues(
                start = metrics.horizontalContentPaddingDp.dp,
                top = metrics.horizontalContentPaddingDp.dp,
                end = metrics.endContentPaddingDp.dp,
                bottom = metrics.bottomContentPaddingDp.dp,
            )
            availableGridWidth = (
                maxWidth - metrics.horizontalContentPaddingDp.dp - metrics.endContentPaddingDp.dp
                ).coerceAtLeast(metrics.minCellWidthDp.dp)
            columnCount = movieCatalogColumnCount(
                availableWidthDp = availableGridWidth.value,
                spacingDp = metrics.horizontalSpacingDp,
                minCellWidthDp = metrics.minCellWidthDp,
            )
        } else {
            horizontalSpacing = FAVORITES_MOBILE_HORIZONTAL_SPACING
            verticalSpacing = FAVORITES_MOBILE_VERTICAL_SPACING
            contentPadding = PaddingValues(
                start = FAVORITES_MOBILE_HORIZONTAL_PADDING,
                top = 5.dp,
                end = FAVORITES_MOBILE_HORIZONTAL_PADDING,
                bottom = FAVORITES_MOBILE_BOTTOM_PADDING,
            )
            val compactWidth = adaptiveUi.windowWidthClass == HulkWindowWidthClass.COMPACT
            availableGridWidth = (
                maxWidth - FAVORITES_MOBILE_HORIZONTAL_PADDING * 2
                ).coerceAtLeast(1.dp)
            val minCellWidthDp = if (compactWidth) {
                FAVORITES_MOBILE_MIN_CELL_WIDTH.value
            } else {
                movieCatalogMinCellWidthDp(adaptiveUi.screenWidthDp)
            }
            val rawColumns = movieCatalogColumnCount(
                availableWidthDp = availableGridWidth.value,
                spacingDp = FAVORITES_MOBILE_HORIZONTAL_SPACING.value,
                minCellWidthDp = minCellWidthDp,
            )
            columnCount = if (compactWidth) rawColumns.coerceAtMost(2) else rawColumns
        }
        val cellWidth = (
            (availableGridWidth - horizontalSpacing * (columnCount - 1)) / columnCount
            ).coerceAtLeast(1.dp)
        val usableGridHeightPx = with(density) {
            (maxHeight - metrics.horizontalContentPaddingDp.dp -
                (if (isTelevision) metrics.focusSafeBottomInsetDp.dp else 0.dp))
                .coerceAtLeast(1.dp)
                .roundToPx()
        }
        var boxedFooterHeightPx by remember(keys) { mutableIntStateOf(0) }
        val compactArtworkHeightDp = if (isTelevision && boxedFooterHeightPx > 0) {
            movieCompactArtworkHeightPx(
                cellWidthPx = with(density) { cellWidth.roundToPx() },
                footerHeightPx = boxedFooterHeightPx,
                usableHeightPx = usableGridHeightPx,
                minArtworkHeightPx = with(density) {
                    MOVIE_COMPACT_ARTWORK_MIN_HEIGHT_DP.dp.roundToPx()
                },
            )?.let { heightPx -> with(density) { heightPx.toDp() } }
        } else {
            null
        }

        fun moveGridTo(nextIndex: Int): Boolean {
            val requester = requesters.getOrNull(nextIndex) ?: return false
            val layoutInfo = gridState.layoutInfo
            val targetInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == nextIndex }
            val focusPath = tvCatalogFocusPath(
                targetIndex = nextIndex,
                itemCount = rows.size,
                itemTop = targetInfo?.offset?.y,
                itemBottom = targetInfo?.let { it.offset.y + it.size.height },
                viewportStart = layoutInfo.viewportStartOffset + focusViewportInsetPx,
                viewportEnd = layoutInfo.viewportEndOffset -
                    focusSafeBottomInsetPx - focusViewportInsetPx,
                extraMargin = focusViewportInsetPx,
            )
            if (focusPath == TvCatalogFocusPath.INVALID) return false
            focusMoveState.begin(nextIndex)
            saveFocusedRow(nextIndex)
            focusMoveState.job?.cancel()
            focusMoveState.job = null
            val focusedDirectly = if (focusPath == TvCatalogFocusPath.DIRECT) {
                runCatching { requester.requestFocus() }.getOrDefault(false)
            } else {
                false
            }
            if (focusedDirectly) {
                focusMoveState.complete(nextIndex)
            } else {
                focusMoveState.job = scope.launch {
                    focusIndex(nextIndex, ensureFullyVisible = true)
                    focusMoveState.complete(nextIndex)
                }
            }
            return true
        }

        // One content transition owner: a removal moves focus to the next surviving stable key,
        // then the previous item; any other key transition resets the grid location. The release of
        // the deleting press is consumed once so it can never activate the replacement card.
        // While editing, a key change is a query/ranking change, never a deletion: it must not
        // relocate focus to a card.
        LaunchedEffect(keys) {
            val previous = previousKeys.value
            previousKeys.value = keys
            val removedKey = focusedKey
            val isRemoval = !editingNow.value &&
                removedKey != null && removedKey in previous && removedKey !in keys
            focusMoveState.job?.cancel()
            focusMoveState.job = null
            if (isRemoval) {
                val removedIndex = previous.indexOf(removedKey)
                val nextIndex = favoritesRemovalFocusIndex(removedIndex, keys.size)
                if (nextIndex == null) {
                    focusedIndex = -1
                    focusedKey = null
                    return@LaunchedEffect
                }
                val nextKey = keys[nextIndex]
                snapshotFlow {
                    gridState.layoutInfo.visibleItemsInfo.any { it.key == nextKey }
                }.first { it }
                if (editingNow.value) return@LaunchedEffect
                ensureIndexFullyVisible(nextIndex)
                runCatching { requesters[nextIndex].requestFocus() }
                focusedIndex = nextIndex
                focusedKey = nextKey
                onFocusedRow(nextIndex, nextKey)
            } else {
                focusedIndex = -1
                focusedKey = null
                if (!editingNow.value && restoreKey == null && gridState.firstVisibleItemIndex != 0) {
                    gridState.scrollToItem(0)
                }
            }
        }

        LaunchedEffect(keys, restoreKey) {
            val index = restoreKey?.let(keys::indexOf) ?: -1
            if (index < 0 || focusedIndex == index) return@LaunchedEffect
            // Restoration is an entry/navigation effect; an active editing session owns the input
            // and an ordinary typing change must not restore a card over the query field.
            if (editingNow.value) return@LaunchedEffect
            gridState.scrollToItem(index)
            snapshotFlow {
                gridState.layoutInfo.visibleItemsInfo.any { it.index == index }
            }.first { it }
            if (editingNow.value) return@LaunchedEffect
            ensureIndexFullyVisible(index)
            if (editingNow.value) return@LaunchedEffect
            runCatching { requesters[index].requestFocus() }
            // The framework focus restorer may re-apply its own target a frame later; re-assert
            // the restored card for a bounded number of frames, never with a sleep, and recheck
            // the input ownership after each suspension.
            for (attempt in 0 until 3) {
                withFrameNanos { }
                if (editingNow.value) break
                if (focusedIndex == index) break
                runCatching { requesters[index].requestFocus() }
            }
        }

        CompositionLocalProvider(LocalBringIntoViewSpec provides gridBringIntoViewSpec) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isTelevision) {
                            Modifier.onPreviewKeyEvent { event ->
                                val selectKey = event.key == Key.Enter ||
                                    event.key == Key.DirectionCenter ||
                                    event.key == Key.NumPadEnter ||
                                    event.key == Key.Spacebar
                                when (
                                    favoritesSelectGuardDecision(
                                        armed = suppressSelectKeyUp,
                                        isSelectKey = selectKey,
                                        isKeyUp = event.type == KeyEventType.KeyUp,
                                        keyDownRepeatCount = event.nativeKeyEvent.repeatCount,
                                    )
                                ) {
                                    FavoritesSelectGuardDecision.CONSUME_RELEASE -> {
                                        suppressSelectKeyUp = false
                                        true
                                    }
                                    FavoritesSelectGuardDecision.CLEAR_ARMED_ON_PRESS -> {
                                        suppressSelectKeyUp = false
                                        false
                                    }
                                    FavoritesSelectGuardDecision.PASS -> false
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            ) {
                itemsIndexed(
                    items = rows,
                    key = { index, _ -> keys[index] },
                ) { index, row ->
                    val rowKey = keys[index]
                    val cardModifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(requesters[index])
                        .onFocusChanged { state ->
                            if (!state.isFocused && focusedIndex == index) {
                                focusedIndex = -1
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            if (!isTelevision) return@onPreviewKeyEvent false
                            // While this card's Delete owns focus the ancestor body handler must
                            // not intercept the action's directional keys.
                            if (deleteFocusedIndex == index) return@onPreviewKeyEvent false
                            val move = when (event.key) {
                                Key.DirectionLeft -> TvGridFocusMove.LEFT
                                Key.DirectionRight -> TvGridFocusMove.RIGHT
                                Key.DirectionUp -> TvGridFocusMove.UP
                                Key.DirectionDown -> TvGridFocusMove.DOWN
                                else -> null
                            } ?: return@onPreviewKeyEvent false
                            if (event.type == KeyEventType.KeyUp) return@onPreviewKeyEvent true
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            if (
                                move == TvGridFocusMove.DOWN &&
                                onDeleteRow != null &&
                                row is SmartSearchGridRow.History
                            ) {
                                // The saved-content card body's DOWN entry point to its own
                                // Delete; only a real focus transfer is consumed.
                                return@onPreviewKeyEvent moveFocusTo(
                                    deleteRequesters.getOrNull(index),
                                )
                            }
                            val baseIndex = focusMoveState.baseIndex(index)
                            val nextIndex = nextTvGridFocusIndex(
                                currentIndex = baseIndex,
                                itemCount = rows.size,
                                columnCount = columnCount,
                                move = move,
                            )
                            if (nextIndex == null) {
                                return@onPreviewKeyEvent when (move) {
                                    TvGridFocusMove.UP -> {
                                        val onFirstRow = (baseIndex / columnCount) == 0
                                        if (onFirstRow) onMoveUpFromFirstRow() else false
                                    }
                                    TvGridFocusMove.RIGHT -> {
                                        if (railSearchRequester != null) {
                                            onContentFocusTargetChanged(requesters[index])
                                            moveFocusTo(railSearchRequester)
                                        } else {
                                            true
                                        }
                                    }
                                    TvGridFocusMove.DOWN, TvGridFocusMove.LEFT -> true
                                }
                            }
                            moveGridTo(nextIndex)
                        }
                    val onFocusedCard = {
                        saveFocusedRow(index)
                        onContentFocusTargetChanged(requesters[index])
                        // A card that received focus from outside the grid transaction (for
                        // example DOWN from the toolbar) still gets one measured reveal when its
                        // complete bounds are clipped.
                        val clipped = currentIndexCorrection(index)?.let { it != 0 } == true
                        if (isTelevision && clipped) {
                            scope.launch { ensureIndexFullyVisible(index) }
                        }
                    }
                    when (row) {
                        is SmartSearchGridRow.Result -> when (row.item.type) {
                            ContentType.LIVE -> LiveChannelHomeCard(
                                name = row.item.name,
                                artworkUrl = row.item.posterUrl,
                                isFavorite = isFavorite(row.item),
                                onClick = { onOpenRow(row) },
                                modifier = cardModifier,
                                onLongClick = { onToggleFavorite(row.item) },
                                onFocused = onFocusedCard,
                            )

                            ContentType.SERIES -> SeriesCatalogBoxedCard(
                                item = row.item,
                                isFavorite = isFavorite(row.item),
                                onClick = { onOpenRow(row) },
                                modifier = cardModifier,
                                onLongClick = { onToggleFavorite(row.item) },
                                onFocused = onFocusedCard,
                                artworkHeightDp = compactArtworkHeightDp,
                                onFooterHeightMeasured = { boxedFooterHeightPx = it },
                            )

                            else -> MoviesCatalogBoxedCard(
                                item = row.item,
                                isFavorite = isFavorite(row.item),
                                onClick = { onOpenRow(row) },
                                modifier = cardModifier,
                                onLongClick = { onToggleFavorite(row.item) },
                                onFocused = onFocusedCard,
                                artworkHeightDp = compactArtworkHeightDp,
                                onFooterHeightMeasured = { boxedFooterHeightPx = it },
                            )
                        }

                        is SmartSearchGridRow.History -> {
                            val item = row.cardItem
                            val deleteAction: (@Composable () -> Unit)? = onDeleteRow?.let { deleteRow ->
                                {
                                    SmartSearchHistoryDeleteAction(
                                        requester = deleteRequesters[index],
                                        cardRequester = requesters[index],
                                        isTv = isTelevision,
                                        onDelete = {
                                            suppressSelectKeyUp = true
                                            deleteRow(row)
                                        },
                                        onFocused = {
                                            deleteFocusedIndex = index
                                            onFocusedCard()
                                        },
                                        onFocusLost = {
                                            if (deleteFocusedIndex == index) {
                                                deleteFocusedIndex = -1
                                            }
                                        },
                                        onMoveDown = {
                                            val nextIndex = nextTvGridFocusIndex(
                                                currentIndex = index,
                                                itemCount = rows.size,
                                                columnCount = columnCount,
                                                move = TvGridFocusMove.DOWN,
                                            )
                                            if (nextIndex != null) moveGridTo(nextIndex) else true
                                        },
                                        onMoveLeft = {
                                            val nextIndex = nextTvGridFocusIndex(
                                                currentIndex = index,
                                                itemCount = rows.size,
                                                columnCount = columnCount,
                                                move = TvGridFocusMove.LEFT,
                                            )
                                            if (nextIndex != null) {
                                                moveFocusTo(deleteRequesters.getOrNull(nextIndex))
                                            } else {
                                                true
                                            }
                                        },
                                        onMoveRight = {
                                            val nextIndex = nextTvGridFocusIndex(
                                                currentIndex = index,
                                                itemCount = rows.size,
                                                columnCount = columnCount,
                                                move = TvGridFocusMove.RIGHT,
                                            )
                                            if (nextIndex != null) {
                                                moveFocusTo(deleteRequesters.getOrNull(nextIndex))
                                            } else {
                                                true
                                            }
                                        },
                                    )
                                }
                            }
                            when (item.type) {
                                ContentType.LIVE -> LiveChannelHomeCard(
                                    name = item.name,
                                    artworkUrl = item.posterUrl,
                                    isFavorite = false,
                                    onClick = { onOpenRow(row) },
                                    modifier = cardModifier,
                                    onFocused = onFocusedCard,
                                    footerAction = deleteAction,
                                )

                                ContentType.SERIES -> SeriesCatalogBoxedCard(
                                    item = item,
                                    isFavorite = false,
                                    onClick = { onOpenRow(row) },
                                    modifier = cardModifier,
                                    onFocused = onFocusedCard,
                                    artworkHeightDp = compactArtworkHeightDp,
                                    onFooterHeightMeasured = { boxedFooterHeightPx = it },
                                    footerAction = deleteAction,
                                )

                                else -> MoviesCatalogBoxedCard(
                                    item = item,
                                    isFavorite = false,
                                    onClick = { onOpenRow(row) },
                                    modifier = cardModifier,
                                    onFocused = onFocusedCard,
                                    artworkHeightDp = compactArtworkHeightDp,
                                    onFooterHeightMeasured = { boxedFooterHeightPx = it },
                                    footerAction = deleteAction,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartSearchHistoryDeleteAction(
    requester: FocusRequester,
    cardRequester: FocusRequester,
    isTv: Boolean,
    onDelete: () -> Unit,
    onFocused: () -> Unit,
    onFocusLost: () -> Unit,
    onMoveDown: () -> Boolean,
    onMoveLeft: () -> Boolean,
    onMoveRight: () -> Boolean,
) {
    FocusButton(
        text = "حذف",
        onClick = onDelete,
        modifier = Modifier
            .focusRequester(requester)
            .onFocusChanged { state ->
                if (state.isFocused) onFocused() else onFocusLost()
            }
            .focusProperties {
                if (isTv) {
                    up = cardRequester
                    down = FocusRequester.Cancel
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                }
            }
            .onPreviewKeyEvent { event ->
                if (!isTv || event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionUp -> moveFocusTo(cardRequester)
                        Key.DirectionDown -> onMoveDown()
                        Key.DirectionLeft -> onMoveLeft()
                        Key.DirectionRight -> onMoveRight()
                        else -> false
                    }
                }
            },
        primary = false,
        compact = true,
        outlined = true,
        scaleOnFocus = false,
    )
}

private fun smartSearchFilterFromMemoryKey(key: String?): SmartSearchFilter =
    SmartSearchFilter.entries.firstOrNull { it.name == key } ?: SmartSearchFilter.ALL


@Composable
private fun SmartSearchRail(
    isTv: Boolean,
    selected: MainDestination,
    searchFieldRequester: FocusRequester,
    searchRailRequester: FocusRequester,
    contentReturnRequester: FocusRequester,
    preferSearchFieldOnEnter: Boolean,
    onSelectDestination: (MainDestination) -> Unit,
    onSwitchProfile: () -> Unit,
) {
    val adaptiveUi = LocalAdaptiveUi.current
    val metrics = tvRailMetrics(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp)
    var railHasFocus by remember { mutableStateOf(false) }
    val expanded = railHasFocus || !isTv
    val destinationRequesters = remember {
        smartSearchDestinations.associate { entry ->
            entry.destination to if (entry.destination == MainDestination.SEARCH) searchRailRequester else FocusRequester()
        }
    }
    val selectedRequester = destinationRequesters.getValue(selected)

    fun returnToContent(): Boolean {
        val restored = runCatching { contentReturnRequester.requestFocus() }.getOrDefault(false)
        if (!restored) runCatching { searchFieldRequester.requestFocus() }
        return true
    }

    LaunchedEffect(isTv, preferSearchFieldOnEnter, railHasFocus) {
        if (isTv && preferSearchFieldOnEnter && railHasFocus) {
            delay(TV_RAIL_FOCUS_REDIRECT_DELAY_MS)
            runCatching { searchFieldRequester.requestFocus() }
        }
    }

    TvRailSurface(
        metrics = metrics,
        expanded = expanded,
        overlayExpansion = isTv,
        onRailFocusChanged = { railHasFocus = it },
        onRailEnter = {
            if (isTv && preferSearchFieldOnEnter) {
                searchFieldRequester.requestFocus()
            } else {
                selectedRequester.requestFocus()
            }
        },
    ) {
        BrandLogo(Modifier.size(metrics.logoSizeDp.dp))
        Spacer(Modifier.height(metrics.logoItemGapDp.dp))

        smartSearchDestinations
            .filterNot { it.destination == MainDestination.SETTINGS }
            .forEach { entry ->
                TvRailDestinationItem(
                    icon = entry.icon,
                    label = entry.label,
                    selected = selected == entry.destination,
                    expanded = expanded,
                    metrics = metrics,
                    modifier = Modifier
                        .focusRequester(destinationRequesters.getValue(entry.destination))
                        .then(
                            if (isTv && entry.destination == MainDestination.SEARCH) {
                                Modifier
                                    .focusProperties { left = contentReturnRequester }
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                                            returnToContent()
                                        } else {
                                            false
                                        }
                                    }
                            } else Modifier,
                        ),
                    onClick = { onSelectDestination(entry.destination) },
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
        )

        Spacer(Modifier.weight(1f))

        smartSearchDestinations.first { it.destination == MainDestination.SETTINGS }.let { entry ->
            TvRailDestinationItem(
                icon = entry.icon,
                label = entry.label,
                selected = false,
                expanded = expanded,
                metrics = metrics,
                modifier = Modifier.focusRequester(destinationRequesters.getValue(entry.destination)),
                onClick = { onSelectDestination(entry.destination) },
            )
        }
    }
}

private suspend fun buildSmartSearchIndex(state: HulkUiState): SmartSearchIndex {
    val expectedSize = state.catalogs.values.sumOf { it.items.size }
    val entries = ArrayList<SmartSearchIndexEntry>(expectedSize)
    val seen = HashSet<String>(expectedSize.coerceAtLeast(16))
    var scanned = 0

    val orderedTypes = listOf(ContentType.SERIES, ContentType.MOVIE, ContentType.LIVE)
    for (type in orderedTypes) {
        val catalog = state.catalogs[type] ?: continue
        for (item in catalog.items) {
            if ((scanned++ and SEARCH_CANCELLATION_MASK) == 0) currentCoroutineContext().ensureActive()
            val stableKey = "${item.type}:${item.id}"
            if (!seen.add(stableKey)) continue

            val normalizedName = normalizeSearchText(item.name)
            val titleTokens = normalizedName.split(' ').filter(String::isNotBlank)
            val titleHasArabic = normalizedName.any(::isArabicSearchChar)
            val titleHasLatin = normalizedName.any(::isLatinSearchChar)
            val phoneticWords = phoneticSearchWordsNormalized(normalizedName)
            entries += SmartSearchIndexEntry(
                item = item,
                normalizedName = normalizedName,
                titleTokens = titleTokens,
                normalizedYear = normalizeSearchText(item.year.orEmpty()),
                normalizedGenre = normalizeSearchText(item.genre.orEmpty()),
                titleHasArabic = titleHasArabic,
                titleHasLatin = titleHasLatin,
                phoneticWords = phoneticWords,
                phoneticPhrase = phoneticWords.joinToString(" "),
            )
        }
    }
    return SmartSearchIndex(entries)
}

private suspend fun boundedSmartSearch(
    index: SmartSearchIndex,
    rawQuery: String,
    filter: SmartSearchFilter,
): List<ContentItem> {
    val query = normalizeSearchText(rawQuery)
    if (query.length < SMART_SEARCH_MIN_QUERY_LENGTH) return emptyList()
    val queryTokens = query.split(' ').filter(String::isNotBlank)
    val queryHasArabic = query.any(::isArabicSearchChar)
    val queryHasLatin = query.any(::isLatinSearchChar)
    val queryPhoneticWords = phoneticSearchWordsNormalized(query)
    val queryPhoneticPhrase = queryPhoneticWords.joinToString(" ")

    val buckets = Array(SEARCH_RANK_COUNT) { mutableListOf<ContentItem>() }
    val seen = HashSet<String>()
    var scanned = 0

    for (entry in index.entries) {
        if ((scanned++ and SEARCH_CANCELLATION_MASK) == 0) currentCoroutineContext().ensureActive()
        val item = entry.item
        if (!filter.accepts(item.type)) continue
        val rank = entry.smartSearchRankOrNull(
            normalizedQuery = query,
            queryTokens = queryTokens,
            queryHasArabic = queryHasArabic,
            queryHasLatin = queryHasLatin,
            queryPhoneticWords = queryPhoneticWords,
            queryPhoneticPhrase = queryPhoneticPhrase,
        ) ?: continue
        val key = "${item.type}:${item.id}"
        if (!seen.add(key)) continue
        val bucket = buckets[rank]
        if (bucket.size < MAX_RESULTS_PER_RANK) bucket += item
    }

    val withinRank = compareByDescending<ContentItem> { it.rating?.toDoubleOrNull() ?: 0.0 }
        .thenByDescending { it.addedAtEpochSeconds ?: 0L }
        .thenBy { it.name.length }
        .thenBy { it.name }

    return buildList {
        buckets.forEach { bucket ->
            bucket.sortWith(withinRank)
            bucket.forEach { item ->
                if (size >= MAX_SEARCH_RESULTS) return@buildList
                add(item)
            }
        }
    }
}

private fun SmartSearchIndexEntry.smartSearchRankOrNull(
    normalizedQuery: String,
    queryTokens: List<String>,
    queryHasArabic: Boolean,
    queryHasLatin: Boolean,
    queryPhoneticWords: List<String>,
    queryPhoneticPhrase: String,
): Int? {
    when {
        normalizedName == normalizedQuery -> return 0
        normalizedName.startsWith(normalizedQuery) -> return 1
        titleTokens.any { it.startsWith(normalizedQuery) } -> return 2
        normalizedName.contains(normalizedQuery) -> return 3
        queryTokens.size > 1 && queryTokens.all { token ->
            titleTokens.any { word -> word.startsWith(token) || word.contains(token) }
        } -> return 4
    }

    val crossScriptCandidate =
        (queryHasArabic && titleHasLatin) || (queryHasLatin && titleHasArabic)
    if (crossScriptCandidate && queryPhoneticPhrase.length >= 3) {
        val phoneticRank = crossScriptPhoneticRankPrepared(
            queryWords = queryPhoneticWords,
            queryPhrase = queryPhoneticPhrase,
            titleWords = phoneticWords,
            titlePhrase = phoneticPhrase,
        )
        if (phoneticRank != null) return 5 + phoneticRank
    }

    if (normalizedYear == normalizedQuery) return 8

    return when {
        normalizedGenre.contains(normalizedQuery) -> 9
        queryTokens.size > 1 && queryTokens.all(normalizedGenre::contains) -> 10
        else -> null
    }
}

internal fun crossScriptPhoneticRank(query: String, title: String): Int? {
    val normalizedQuery = normalizeSearchText(query)
    val normalizedTitle = normalizeSearchText(title)
    val queryHasArabic = normalizedQuery.any(::isArabicSearchChar)
    val queryHasLatin = normalizedQuery.any(::isLatinSearchChar)
    val titleHasArabic = normalizedTitle.any(::isArabicSearchChar)
    val titleHasLatin = normalizedTitle.any(::isLatinSearchChar)
    val crossScriptCandidate =
        (queryHasArabic && titleHasLatin) || (queryHasLatin && titleHasArabic)
    if (!crossScriptCandidate) return null

    val queryWords = phoneticSearchWordsNormalized(normalizedQuery)
    val titleWords = phoneticSearchWordsNormalized(normalizedTitle)
    val queryPhrase = queryWords.joinToString(" ")
    val titlePhrase = titleWords.joinToString(" ")
    if (queryPhrase.length < 3) return null
    return crossScriptPhoneticRankPrepared(queryWords, queryPhrase, titleWords, titlePhrase)
}

private fun crossScriptPhoneticRankPrepared(
    queryWords: List<String>,
    queryPhrase: String,
    titleWords: List<String>,
    titlePhrase: String,
): Int? {
    if (queryWords.isEmpty() || titleWords.isEmpty()) return null

    return when {
        queryPhrase == titlePhrase -> 0
        titlePhrase.startsWith(queryPhrase) -> 1
        queryWords.all { queryWord ->
            titleWords.any { titleWord -> phoneticWordMatches(queryWord, titleWord) }
        } -> 2
        else -> null
    }
}

private fun phoneticWordMatches(queryWord: String, titleWord: String): Boolean {
    if (queryWord == titleWord) return true
    if (queryWord.length == 1 || titleWord.length == 1) return false
    if (titleWord.startsWith(queryWord) || titleWord.contains(queryWord)) return true
    return phoneticEditDistanceAtMostOne(queryWord, titleWord)
}

private fun phoneticEditDistanceAtMostOne(first: String, second: String): Boolean {
    if (kotlin.math.abs(first.length - second.length) > 1) return false
    if (first == second) return true

    var firstIndex = 0
    var secondIndex = 0
    var edits = 0

    while (firstIndex < first.length && secondIndex < second.length) {
        if (first[firstIndex] == second[secondIndex]) {
            firstIndex++
            secondIndex++
            continue
        }

        edits++
        if (edits > 1) return false
        when {
            first.length > second.length -> firstIndex++
            second.length > first.length -> secondIndex++
            else -> {
                firstIndex++
                secondIndex++
            }
        }
    }

    if (firstIndex < first.length || secondIndex < second.length) edits++
    return edits <= 1
}

internal fun phoneticSearchWords(raw: String): List<String> =
    phoneticSearchWordsNormalized(normalizeSearchText(raw))

private fun phoneticSearchWordsNormalized(normalized: String): List<String> = normalized
    .split(' ')
    .map(::phoneticSearchWord)
    .map(::collapseRepeatedPhoneticChars)
    .filter(String::isNotEmpty)

private fun collapseRepeatedPhoneticChars(value: String): String {
    if (value.length < 2) return value
    return buildString(value.length) {
        var previous: Char? = null
        value.forEach { char ->
            if (char != previous) {
                append(char)
                previous = char
            }
        }
    }
}

private fun phoneticSearchWord(word: String): String = buildString(word.length) {
    word.forEach { char ->
        when (char.lowercaseChar()) {
            'a', 'e', 'i', 'o', 'u', 'y', 'w',
            'ا', 'أ', 'إ', 'آ', 'و', 'ي', 'ى', 'ء', 'ع' -> Unit
            'b', 'p', 'ب', 'پ' -> append('b')
            'r', 'ر' -> append('r')
            's', 'z', 'ص', 'س', 'ز', 'ذ', 'ض', 'ظ' -> append('s')
            'n', 'ن' -> append('n')
            'k', 'q', 'c', 'g', 'ك', 'ق', 'غ' -> append('k')
            'f', 'v', 'ف', 'ڤ' -> append('f')
            'm', 'م' -> append('m')
            'l', 'ل' -> append('l')
            't', 'd', 'ت', 'ط', 'د' -> append('t')
            'h', 'ه', 'ح' -> append('h')
            'j', 'ج' -> append('j')
            'خ' -> append("kh")
            'ش' -> append("sh")
            'ث' -> append("th")
            'x' -> append("ks")
            else -> if (char.isLetterOrDigit()) append(char.lowercaseChar())
        }
    }
}

private fun isArabicSearchChar(char: Char): Boolean = char in '\u0600'..'\u06FF'
private fun isLatinSearchChar(char: Char): Boolean = char in 'a'..'z'

internal fun normalizeSearchText(raw: String): String {
    val normalized = StringBuilder(raw.length)
    var pendingSpace = false

    raw.forEach { original ->
        val lower = original.lowercaseChar()
        val mapped: Char? = when {
            lower in '\u064B'..'\u065F' || lower == '\u0670' || lower == 'ـ' -> null
            lower in '٠'..'٩' -> ('0'.code + lower.code - '٠'.code).toChar()
            lower in '۰'..'۹' -> ('0'.code + lower.code - '۰'.code).toChar()
            else -> when (lower) {
                'أ', 'إ', 'آ' -> 'ا'
                'ى' -> 'ي'
                'ؤ' -> 'و'
                'ئ' -> 'ي'
                'ة' -> 'ه'
                else -> lower
            }
        }

        if (mapped == null) return@forEach
        if (mapped.isLetterOrDigit()) {
            if (pendingSpace && normalized.isNotEmpty()) normalized.append(' ')
            normalized.append(mapped)
            pendingSpace = false
        } else if (normalized.isNotEmpty()) {
            pendingSpace = true
        }
    }

    return normalized.toString()
}

private fun ContentType.smartLabel(): String = when (this) {
    ContentType.LIVE -> "قناة"
    ContentType.MOVIE -> "فلم"
    ContentType.SERIES -> "مسلسل"
}

private const val SMART_SEARCH_MIN_QUERY_LENGTH = 2
private const val SEARCH_DEBOUNCE_MS = 90L
private const val TV_INITIAL_SEARCH_FOCUS_DELAY_MS = 40L
private const val TV_INITIAL_SEARCH_FOCUS_RETRY_MS = 120L
private const val TV_INITIAL_SEARCH_FOCUS_ATTEMPTS = 6
private const val TV_RAIL_FOCUS_REDIRECT_DELAY_MS = 20L
private const val TV_GRID_FOCUS_SETTLE_MS = 24L
private const val RECENT_FOCUS_REVEAL_MARGIN_DP = 6
private const val SEARCH_TOOLBAR_STACK_WIDTH_DP = 320
private const val SEARCH_TITLE_SLOT_WIDTH_DP = 185
private const val SEARCH_HEADER_GAP_DP = 11
private const val SEARCH_CANCELLATION_MASK = 63
private const val SEARCH_RANK_COUNT = 11
private const val MAX_RESULTS_PER_RANK = 48
private const val MAX_SEARCH_RESULTS = 120

/**
 * Focus transfer with real success reporting: an event is consumed only when the requested target
 * actually took focus, so a failed transfer is never swallowed and can fall back safely.
 */
private fun moveFocusTo(target: FocusRequester?): Boolean {
    if (target == null) return false
    return try {
        target.requestFocus()
    } catch (error: RuntimeException) {
        false
    }
}