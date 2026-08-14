package sa.hulksa.player.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.MainDestination
import sa.hulksa.player.data.ProfileContentSearchHistoryEntry
import sa.hulksa.player.data.ProfileContentSearchHistoryStore
import sa.hulksa.player.data.ProfileStore
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.ui.adaptive.HulkNavigationType
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.components.BrandLogo
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.HulkTextField
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.components.UniversalPosterCard
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
    LIVE(ContentType.LIVE, "القنوات"),
    MOVIES(ContentType.MOVIE, "الأفلام"),
    SERIES(ContentType.SERIES, "المسلسلات"),
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
    val titleArabic: Boolean,
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
    SmartSearchDestination(MainDestination.FAVORITES, Icons.Rounded.Favorite, "قائمتي"),
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
    onSelectDestination: (MainDestination) -> Unit,
    onSearch: (String) -> Unit,
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
    var selectedFilter by remember(activeProfileId) { mutableStateOf(SmartSearchFilter.ALL) }
    val recentEntries = remember(activeProfileId, historyRevision) { historyStore.recent() }
    val visibleRecentEntries = remember(recentEntries, selectedFilter) {
        recentEntries.filter { selectedFilter.accepts(it.contentType) }
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
    var initialSearchFocusAcquired by remember(activeProfileId) { mutableStateOf(false) }
    var railReturnRequester by remember(activeProfileId) { mutableStateOf<FocusRequester?>(null) }

    LaunchedEffect(isTv, activeProfileId) {
        if (isTv) {
            repeat(TV_INITIAL_SEARCH_FOCUS_ATTEMPTS) { attempt ->
                delay(if (attempt == 0) TV_INITIAL_SEARCH_FOCUS_DELAY_MS else TV_INITIAL_SEARCH_FOCUS_RETRY_MS)
                if (!initialSearchFocusAcquired) {
                    runCatching { searchFieldRequester.requestFocus() }
                }
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
                    preferSearchFieldOnEnter = isTv && !initialSearchFocusAcquired,
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
                    searchFieldRequester = searchFieldRequester,
                    firstResultRequester = firstResultRequester,
                    firstRecentRequester = firstRecentRequester,
                    railSearchRequester = if (isTv) railSearchRequester else null,
                    isFavorite = isFavorite,
                    onSearch = onSearch,
                    onSearchFieldFocusChanged = { focused ->
                        if (focused) {
                            initialSearchFocusAcquired = true
                            railReturnRequester = searchFieldRequester
                        }
                    },
                    onContentFocusTargetChanged = { requester ->
                        railReturnRequester = requester
                    },
                    onFilterSelected = { selectedFilter = it },
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
                    onSwitchProfile = onSwitchProfile,
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
                    searchFieldRequester = searchFieldRequester,
                    firstResultRequester = firstResultRequester,
                    firstRecentRequester = firstRecentRequester,
                    railSearchRequester = null,
                    isFavorite = isFavorite,
                    onSearch = onSearch,
                    onSearchFieldFocusChanged = { focused ->
                        if (focused) initialSearchFocusAcquired = true
                    },
                    onContentFocusTargetChanged = {},
                    onFilterSelected = { selectedFilter = it },
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
                    onSwitchProfile = onSwitchProfile,
                )
                if (!imeVisible) {
                    SmartSearchBottomNavigation(
                        selected = MainDestination.SEARCH,
                        onSelectDestination = onSelectDestination,
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
    onSwitchProfile: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val screenWidth = adaptiveUi.screenWidthDp
    val screenHeight = adaptiveUi.screenHeightDp
    val compactHeight = !isTv && screenHeight < 600
    val horizontalPadding = when {
        isTv -> (screenWidth / 46f).coerceIn(18f, 34f).dp
        screenWidth >= 840 -> 28.dp
        screenWidth >= 600 -> 22.dp
        else -> 14.dp
    }
    val verticalPadding = when {
        isTv -> (screenHeight / 42f).coerceIn(14f, 24f).dp
        compactHeight -> 8.dp
        else -> 14.dp
    }
    val selectedFilterRequester = filterRequesters.getValue(selectedFilter)
    val firstContentRequester = when {
        results.isNotEmpty() -> firstResultRequester
        state.searchQuery.isBlank() && recentEntries.isNotEmpty() -> firstRecentRequester
        else -> null
    }

    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(colors.background, colors.surface.copy(alpha = .96f), colors.background),
                ),
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(if (isTv) 52.dp else 42.dp)
                    .clip(CircleShape)
                    .background(colors.gold.copy(alpha = .14f))
                    .border(1.dp, colors.gold.copy(alpha = .34f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = colors.goldBright,
                    modifier = Modifier.size(if (isTv) 27.dp else 22.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "البحث",
                    color = colors.text,
                    fontSize = if (isTv) 30.sp else 23.sp,
                    fontWeight = FontWeight.Black,
                )
                if (!compactHeight && !imeVisible) {
                    Text(
                        text = "بحث سريع في القنوات والأفلام والمسلسلات",
                        color = colors.textMuted,
                        fontSize = if (isTv) 13.sp else 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!isTv && screenWidth >= 600 && !imeVisible) {
                FocusButton(
                    text = "تغيير المستخدم",
                    onClick = onSwitchProfile,
                    primary = false,
                    compact = true,
                    outlined = true,
                )
            }
        }

        Spacer(Modifier.height(if (compactHeight || imeVisible) 8.dp else 14.dp))

        SmartSearchInput(
            value = state.searchQuery,
            onValueChange = onSearch,
            isTv = isTv,
            hasResults = results.isNotEmpty(),
            hasRecent = state.searchQuery.isBlank() && recentEntries.isNotEmpty(),
            searchFieldRequester = searchFieldRequester,
            firstFilterRequester = if (isTv) selectedFilterRequester else null,
            firstResultRequester = firstResultRequester,
            firstRecentRequester = firstRecentRequester,
            railSearchRequester = railSearchRequester,
            onFocusStateChanged = onSearchFieldFocusChanged,
        )

        Spacer(Modifier.height(if (compactHeight || imeVisible) 7.dp else 10.dp))

        SmartSearchFilterBar(
            selected = selectedFilter,
            isTv = isTv,
            requesters = filterRequesters,
            searchFieldRequester = searchFieldRequester,
            downRequester = firstContentRequester,
            railSearchRequester = railSearchRequester,
            onContentFocusTargetChanged = onContentFocusTargetChanged,
            onSelect = onFilterSelected,
        )

        Spacer(Modifier.height(if (compactHeight || imeVisible) 7.dp else 10.dp))

        when {
            state.searchQuery.isBlank() -> {
                SmartRecentSection(
                    modifier = Modifier.weight(1f),
                    entries = recentEntries,
                    isTv = isTv,
                    firstRecentRequester = firstRecentRequester,
                    topRequester = selectedFilterRequester,
                    onContentFocusTargetChanged = onContentFocusTargetChanged,
                    onOpen = onOpenRecent,
                    onRemove = onRemoveRecent,
                    onClear = onClearRecent,
                )
            }

            state.searchQuery.trim().length < SMART_SEARCH_MIN_QUERY_LENGTH -> {
                SearchMessage(
                    modifier = Modifier.weight(1f),
                    isTv = isTv,
                    title = "اكتب حرفين على الاقل",
                    subtitle = "تظهر النتائج مباشرة بعد كتابة حرفين",
                )
            }

            results.isNotEmpty() -> {
                SmartResultsGrid(
                    modifier = Modifier.weight(1f),
                    results = results,
                    isTv = isTv,
                    firstResultRequester = firstResultRequester,
                    topRequester = selectedFilterRequester,
                    railSearchRequester = railSearchRequester,
                    onContentFocusTargetChanged = onContentFocusTargetChanged,
                    isFavorite = { item -> "${item.type.name}:${item.id}" in state.favorites },
                    onOpenResult = onOpenResult,
                    onToggleFavorite = onToggleFavorite,
                )
            }

            searchPending -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LoadingRing(label = "جاري البحث…")
                }
            }

            loadingCatalogs -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LoadingRing(label = "جاري تحميل بقية المحتوى…")
                }
            }

            else -> {
                SearchMessage(
                    modifier = Modifier.weight(1f),
                    isTv = isTv,
                    title = if (selectedFilter == SmartSearchFilter.ALL) {
                        "لا توجد نتائج مطابقة"
                    } else {
                        "لا توجد نتائج في ${selectedFilter.label}"
                    },
                    subtitle = "جرب جزءا آخر من الاسم أو اختر نوع محتوى مختلف",
                )
            }
        }
    }
}

@Composable
private fun SearchMessage(
    modifier: Modifier,
    isTv: Boolean,
    title: String,
    subtitle: String,
) {
    val colors = LocalHulkColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceRaised.copy(alpha = .55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = colors.goldBright,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                color = colors.text,
                fontWeight = FontWeight.Bold,
                fontSize = if (isTv) 18.sp else 15.sp,
            )
            Text(
                text = subtitle,
                color = colors.textMuted,
                fontSize = if (isTv) 11.sp else 10.sp,
            )
        }
    }
}

@Composable
private fun SmartSearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    isTv: Boolean,
    hasResults: Boolean,
    hasRecent: Boolean,
    searchFieldRequester: FocusRequester,
    firstFilterRequester: FocusRequester?,
    firstResultRequester: FocusRequester,
    firstRecentRequester: FocusRequester,
    railSearchRequester: FocusRequester?,
    onFocusStateChanged: (Boolean) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var tvEditing by remember { mutableStateOf(false) }

    val moveDown: () -> Boolean = {
        when {
            firstFilterRequester != null -> {
                tvEditing = false
                keyboardController?.hide()
                runCatching { firstFilterRequester.requestFocus() }.getOrDefault(false)
            }
            hasResults -> {
                tvEditing = false
                keyboardController?.hide()
                runCatching { firstResultRequester.requestFocus() }.getOrDefault(false)
            }
            hasRecent -> {
                tvEditing = false
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
                left = FocusRequester.Cancel
                railSearchRequester?.let { right = it }
            }
            .onFocusChanged { state ->
                onFocusStateChanged(state.isFocused)
                if (!state.isFocused) {
                    tvEditing = false
                    keyboardController?.hide()
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else if (!tvEditing && (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                    tvEditing = true
                    keyboardController?.show()
                    true
                } else if (!tvEditing && event.key == Key.DirectionDown) {
                    moveDown()
                    true
                } else if (!tvEditing && event.key == Key.DirectionUp) {
                    true
                } else if (!tvEditing && event.key == Key.DirectionLeft) {
                    true
                } else if (!tvEditing && event.key == Key.DirectionRight) {
                    railSearchRequester?.let { runCatching { it.requestFocus() } }
                    true
                } else {
                    false
                }
            }
    } else {
        Modifier.onFocusChanged { onFocusStateChanged(it.isFocused) }
    }

    HulkTextField(
        value = value,
        onValueChange = onValueChange,
        label = "اكتب اسم فيلم او مسلسل او قناة…",
        modifier = Modifier.fillMaxWidth().then(tvModifier),
        readOnly = isTv && !tvEditing,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                keyboardController?.hide()
                if (isTv) {
                    moveDown()
                } else {
                    focusManager.clearFocus(force = true)
                }
            },
        ),
    )
}

@Composable
private fun SmartSearchFilterBar(
    selected: SmartSearchFilter,
    isTv: Boolean,
    requesters: Map<SmartSearchFilter, FocusRequester>,
    searchFieldRequester: FocusRequester,
    downRequester: FocusRequester?,
    railSearchRequester: FocusRequester?,
    onContentFocusTargetChanged: (FocusRequester) -> Unit,
    onSelect: (SmartSearchFilter) -> Unit,
) {
    val focusScope = rememberCoroutineScope()

    fun requestDownFocus(): Boolean {
        val target = downRequester ?: return true
        val focused = runCatching { target.requestFocus() }.getOrDefault(false)
        if (!focused) {
            focusScope.launch {
                delay(TV_GRID_FOCUS_SETTLE_MS)
                runCatching { target.requestFocus() }
            }
        }
        return true
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup(),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 10.dp else 7.dp),
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
                            left = leftNeighbor?.let(requesters::getValue) ?: FocusRequester.Cancel
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
                                    runCatching { searchFieldRequester.requestFocus() }
                                    true
                                }
                                Key.DirectionDown -> requestDownFocus()
                                Key.DirectionLeft -> {
                                    leftNeighbor?.let { runCatching { requesters.getValue(it).requestFocus() } }
                                    true
                                }
                                Key.DirectionRight -> {
                                    if (rightNeighbor != null) {
                                        runCatching { requesters.getValue(rightNeighbor).requestFocus() }
                                    } else {
                                        onContentFocusTargetChanged(requester)
                                        railSearchRequester?.let { runCatching { it.requestFocus() } }
                                    }
                                    true
                                }
                                else -> false
                            }
                        }
                    },
                primary = active,
                compact = true,
                outlined = !active,
            )
        }
    }
}

@Composable
private fun SmartRecentSection(
    modifier: Modifier,
    entries: List<ProfileContentSearchHistoryEntry>,
    isTv: Boolean,
    firstRecentRequester: FocusRequester,
    topRequester: FocusRequester,
    onContentFocusTargetChanged: (FocusRequester) -> Unit,
    onOpen: (ProfileContentSearchHistoryEntry) -> Unit,
    onRemove: (ProfileContentSearchHistoryEntry) -> Unit,
    onClear: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val recentKeys = entries.map { it.stableKey }
    val openRequesters = remember(recentKeys, firstRecentRequester) {
        List(entries.size) { index -> if (index == 0) firstRecentRequester else FocusRequester() }
    }
    val deleteRequesters = remember(recentKeys) {
        List(entries.size) { FocusRequester() }
    }
    val clearAllRequester = remember { FocusRequester() }
    val nearestRecentRequester = deleteRequesters.lastOrNull() ?: firstRecentRequester

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (entries.isEmpty()) "ابدأ البحث" else "المحتوى الذي بحثت عنه مؤخرا",
                    color = colors.text,
                    fontSize = if (isTv) 20.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (entries.isEmpty()) {
                        "يتم حفظ المحتوى الذي تختاره فقط"
                    } else {
                        "المحتوى الذي تختاره يظهر هنا بدون تكرار"
                    },
                    color = colors.textMuted,
                    fontSize = if (isTv) 11.sp else 10.sp,
                    maxLines = 2,
                )
            }
            if (entries.isNotEmpty()) {
                FocusButton(
                    text = "مسح الكل",
                    onClick = onClear,
                    modifier = Modifier
                        .focusRequester(clearAllRequester)
                        .onFocusChanged { state ->
                            if (isTv && state.isFocused) onContentFocusTargetChanged(clearAllRequester)
                        }
                        .focusProperties {
                            if (isTv) {
                                up = topRequester
                                down = nearestRecentRequester
                                right = nearestRecentRequester
                                left = FocusRequester.Cancel
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            if (!isTv || event.type != KeyEventType.KeyDown) {
                                false
                            } else {
                                when (event.key) {
                                    Key.DirectionUp -> {
                                        runCatching { topRequester.requestFocus() }
                                        true
                                    }
                                    Key.DirectionDown, Key.DirectionRight -> {
                                        runCatching { nearestRecentRequester.requestFocus() }
                                        true
                                    }
                                    Key.DirectionLeft -> true
                                    else -> false
                                }
                            }
                        },
                    primary = false,
                    compact = true,
                    outlined = true,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surfaceRaised.copy(alpha = .52f))
                    .border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(18.dp))
                    .padding(vertical = if (isTv) 36.dp else 26.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = colors.goldBright,
                    modifier = Modifier.size(if (isTv) 42.dp else 34.dp),
                )
            }
        } else {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup(),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
            ) {
                itemsIndexed(
                    items = entries,
                    key = { _, entry -> entry.stableKey },
                ) { index, entry ->
                    SmartRecentCard(
                        entry = entry,
                        isTv = isTv,
                        openRequester = openRequesters[index],
                        deleteRequester = deleteRequesters[index],
                        rightCardRequester = deleteRequesters.getOrNull(index - 1),
                        leftCardRequester = openRequesters.getOrNull(index + 1) ?: clearAllRequester,
                        topRequester = topRequester,
                        onContentFocusTargetChanged = onContentFocusTargetChanged,
                        onOpen = { onOpen(entry) },
                        onRemove = { onRemove(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartRecentCard(
    entry: ProfileContentSearchHistoryEntry,
    isTv: Boolean,
    openRequester: FocusRequester,
    deleteRequester: FocusRequester,
    rightCardRequester: FocusRequester?,
    leftCardRequester: FocusRequester,
    topRequester: FocusRequester,
    onContentFocusTargetChanged: (FocusRequester) -> Unit,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalHulkColors.current
    Column(
        modifier = Modifier
            .width(if (isTv) 220.dp else 170.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF11130F))
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(16.dp))
            .then(if (isTv) Modifier else Modifier.clickable(role = Role.Button, onClick = onOpen))
            .padding(10.dp),
    ) {
        AsyncImage(
            model = entry.posterUrl,
            contentDescription = entry.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isTv) 116.dp else 92.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceRaised),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = entry.title,
            color = colors.text,
            fontSize = if (isTv) 14.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOfNotNull(entry.contentType.smartLabel(), entry.year).joinToString(" • "),
            color = colors.textMuted,
            fontSize = if (isTv) 10.sp else 9.sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FocusButton(
                text = "فتح",
                onClick = onOpen,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(openRequester)
                    .onFocusChanged { state ->
                        if (isTv && state.isFocused) onContentFocusTargetChanged(openRequester)
                    }
                    .focusProperties {
                        if (isTv) {
                            up = topRequester
                            down = FocusRequester.Cancel
                            left = deleteRequester
                            right = rightCardRequester ?: FocusRequester.Cancel
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        if (!isTv || event.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (event.key) {
                                Key.DirectionUp -> {
                                    runCatching { topRequester.requestFocus() }
                                    true
                                }
                                Key.DirectionDown -> true
                                Key.DirectionLeft -> {
                                    runCatching { deleteRequester.requestFocus() }
                                    true
                                }
                                Key.DirectionRight -> {
                                    rightCardRequester?.let { runCatching { it.requestFocus() } }
                                    true
                                }
                                else -> false
                            }
                        }
                    },
                compact = true,
            )
            FocusButton(
                text = "حذف",
                onClick = onRemove,
                modifier = Modifier
                    .focusRequester(deleteRequester)
                    .onFocusChanged { state ->
                        if (isTv && state.isFocused) onContentFocusTargetChanged(deleteRequester)
                    }
                    .focusProperties {
                        if (isTv) {
                            up = topRequester
                            down = FocusRequester.Cancel
                            right = openRequester
                            left = leftCardRequester
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        if (!isTv || event.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (event.key) {
                                Key.DirectionUp -> {
                                    runCatching { topRequester.requestFocus() }
                                    true
                                }
                                Key.DirectionDown -> true
                                Key.DirectionRight -> {
                                    runCatching { openRequester.requestFocus() }
                                    true
                                }
                                Key.DirectionLeft -> {
                                    runCatching { leftCardRequester.requestFocus() }
                                    true
                                }
                                else -> false
                            }
                        }
                    },
                primary = false,
                compact = true,
                outlined = true,
            )
        }
    }
}

@Composable
private fun SmartResultsGrid(
    modifier: Modifier,
    results: List<ContentItem>,
    isTv: Boolean,
    firstResultRequester: FocusRequester,
    topRequester: FocusRequester,
    railSearchRequester: FocusRequester?,
    onContentFocusTargetChanged: (FocusRequester) -> Unit,
    isFavorite: (ContentItem) -> Boolean,
    onOpenResult: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
) {
    val colors = LocalHulkColors.current
    val gridState = rememberLazyGridState()
    val navigationScope = rememberCoroutineScope()
    val resultKeys = results.map { "${it.type}:${it.id}" }
    val resultRequesters = remember(resultKeys, firstResultRequester) {
        List(results.size) { index -> if (index == 0) firstResultRequester else FocusRequester() }
    }
    var navigationJob by remember(results) { mutableStateOf<Job?>(null) }

    fun requestResultFocus(targetIndex: Int): Boolean {
        val requester = resultRequesters.getOrNull(targetIndex) ?: return false
        val targetGridIndex = targetIndex + 1
        navigationJob?.cancel()

        val targetVisible = gridState.layoutInfo.visibleItemsInfo.any { it.index == targetGridIndex }
        if (targetVisible) {
            val focused = runCatching { requester.requestFocus() }.getOrDefault(false)
            if (focused) return true
        }

        navigationJob = navigationScope.launch {
            runCatching { gridState.scrollToItem(targetGridIndex) }
            delay(TV_GRID_FOCUS_SETTLE_MS)
            runCatching { requester.requestFocus() }
        }
        return true
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = if (isTv) 146.dp else 112.dp),
        state = gridState,
        modifier = modifier
            .fillMaxWidth()
            .focusGroup(),
        contentPadding = PaddingValues(bottom = if (isTv) 24.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "النتائج",
                    color = colors.text,
                    fontSize = if (isTv) 18.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "${results.size} نتيجة",
                    color = colors.textMuted,
                    fontSize = if (isTv) 11.sp else 10.sp,
                )
            }
        }

        itemsIndexed(
            items = results,
            key = { _, item -> "result:${item.type}:${item.id}" },
        ) { index, item ->
            UniversalPosterCard(
                item = item,
                isFavorite = isFavorite(item),
                onClick = { onOpenResult(item) },
                onLongClick = { onToggleFavorite(item) },
                onFocused = {
                    navigationJob?.cancel()
                    navigationJob = null
                    if (isTv) onContentFocusTargetChanged(resultRequesters[index])
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(resultRequesters[index])
                    .onPreviewKeyEvent { event ->
                        if (!isTv || event.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            val columns = gridState.layoutInfo.maxSpan.coerceAtLeast(1)
                            val rowStart = (index / columns) * columns
                            val rowEnd = minOf(rowStart + columns - 1, results.lastIndex)
                            when (event.key) {
                                Key.DirectionUp -> {
                                    val target = index - columns
                                    if (target >= 0) {
                                        requestResultFocus(target)
                                    } else {
                                        runCatching { topRequester.requestFocus() }
                                    }
                                    true
                                }
                                Key.DirectionDown -> {
                                    val target = index + columns
                                    if (target < results.size) {
                                        requestResultFocus(target)
                                    }
                                    true
                                }
                                Key.DirectionLeft -> {
                                    if (index < rowEnd) {
                                        requestResultFocus(index + 1)
                                    }
                                    true
                                }
                                Key.DirectionRight -> {
                                    if (index > rowStart) {
                                        requestResultFocus(index - 1)
                                    } else {
                                        navigationJob?.cancel()
                                        navigationJob = null
                                        onContentFocusTargetChanged(resultRequesters[index])
                                        railSearchRequester?.let { runCatching { it.requestFocus() } }
                                    }
                                    true
                                }
                                else -> false
                            }
                        }
                    },
            )
        }
    }
}

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
    val railWidth by animateDpAsState(
        targetValue = if (expanded) metrics.expandedWidthDp.dp else metrics.collapsedWidthDp.dp,
        label = "smartSearchRailWidth",
    )
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

    Column(
        modifier = Modifier
            .width(railWidth)
            .fillMaxHeight()
            .focusProperties {
                onEnter = {
                    if (isTv && preferSearchFieldOnEnter) {
                        searchFieldRequester.requestFocus()
                    } else {
                        selectedRequester.requestFocus()
                    }
                }
            }
            .focusGroup()
            .onFocusChanged { railHasFocus = it.hasFocus }
            .background(Brush.horizontalGradient(listOf(Color(0xFF090A07), Color(0xF70A0B08))))
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

        smartSearchDestinations
            .filterNot { it.destination == MainDestination.SETTINGS }
            .forEach { entry ->
                SmartSearchRailItem(
                    entry = entry,
                    selected = selected == entry.destination,
                    expanded = expanded,
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

        SmartSearchRailItem(
            entry = SmartSearchDestination(MainDestination.SEARCH, Icons.Rounded.Person, "تغيير المستخدم"),
            selected = false,
            expanded = expanded,
            onClick = onSwitchProfile,
        )

        Spacer(Modifier.weight(1f))

        smartSearchDestinations.first { it.destination == MainDestination.SETTINGS }.let { entry ->
            SmartSearchRailItem(
                entry = entry,
                selected = false,
                expanded = expanded,
                modifier = Modifier.focusRequester(destinationRequesters.getValue(entry.destination)),
                onClick = { onSelectDestination(entry.destination) },
            )
        }
    }
}

@Composable
private fun SmartSearchRailItem(
    entry: SmartSearchDestination,
    selected: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val metrics = tvRailMetrics(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp)
    var focused by remember(entry.label) { mutableStateOf(false) }
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
                text = entry.label,
                color = if (showFocused) Color.Black else if (active) colors.text else colors.textMuted,
                fontSize = metrics.labelSizeSp.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SmartSearchBottomNavigation(
    selected: MainDestination,
    onSelectDestination: (MainDestination) -> Unit,
) {
    val colors = LocalHulkColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF090A07))
            .navigationBarsPadding()
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(items = smartSearchDestinations, key = { it.destination.name }) { entry ->
                val active = selected == entry.destination
                Column(
                    modifier = Modifier
                        .widthIn(min = 54.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) colors.gold.copy(alpha = .10f) else Color.Transparent)
                        .clickable(role = Role.Button) { onSelectDestination(entry.destination) }
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = entry.label,
                        tint = if (active) colors.goldBright else colors.textMuted,
                        modifier = Modifier.size(21.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = entry.label,
                        color = if (active) colors.text else colors.textMuted,
                        fontSize = 8.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
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
            val titleArabic = normalizedName.any(::isArabicSearchChar)
            val phoneticWords = phoneticSearchWordsNormalized(normalizedName)
            entries += SmartSearchIndexEntry(
                item = item,
                normalizedName = normalizedName,
                titleTokens = titleTokens,
                normalizedYear = normalizeSearchText(item.year.orEmpty()),
                normalizedGenre = normalizeSearchText(item.genre.orEmpty()),
                titleArabic = titleArabic,
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
    val queryArabic = query.any(::isArabicSearchChar)
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
            queryArabic = queryArabic,
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
    queryArabic: Boolean,
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

    if (queryArabic != titleArabic && queryPhoneticPhrase.length >= 3) {
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
    val queryArabic = normalizedQuery.any(::isArabicSearchChar)
    val titleArabic = normalizedTitle.any(::isArabicSearchChar)
    if (queryArabic == titleArabic) return null

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
            queryWord.length >= 2 && titleWords.any { titleWord ->
                titleWord.startsWith(queryWord) || titleWord.contains(queryWord)
            }
        } -> 2
        else -> null
    }
}

internal fun phoneticSearchWords(raw: String): List<String> =
    phoneticSearchWordsNormalized(normalizeSearchText(raw))

private fun phoneticSearchWordsNormalized(normalized: String): List<String> = normalized
    .split(' ')
    .map(::phoneticSearchWord)
    .filter { it.length >= 2 }

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
    ContentType.MOVIE -> "فيلم"
    ContentType.SERIES -> "مسلسل"
}

private const val SMART_SEARCH_MIN_QUERY_LENGTH = 2
private const val SEARCH_DEBOUNCE_MS = 90L
private const val TV_INITIAL_SEARCH_FOCUS_DELAY_MS = 40L
private const val TV_INITIAL_SEARCH_FOCUS_RETRY_MS = 120L
private const val TV_INITIAL_SEARCH_FOCUS_ATTEMPTS = 6
private const val TV_RAIL_FOCUS_REDIRECT_DELAY_MS = 20L
private const val TV_GRID_FOCUS_SETTLE_MS = 24L
private const val SEARCH_CANCELLATION_MASK = 63
private const val SEARCH_RANK_COUNT = 11
private const val MAX_RESULTS_PER_RANK = 48
private const val MAX_SEARCH_RESULTS = 120
