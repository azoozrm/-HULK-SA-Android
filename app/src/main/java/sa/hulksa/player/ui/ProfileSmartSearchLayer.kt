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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
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

    val query = state.searchQuery.trim()
    val searchSnapshot by produceState(
        initialValue = SmartSearchSnapshot(filter = selectedFilter),
        key1 = state.catalogs,
        key2 = query to selectedFilter,
    ) {
        value = if (query.length < SMART_SEARCH_MIN_QUERY_LENGTH) {
            SmartSearchSnapshot(filter = selectedFilter)
        } else {
            withContext(Dispatchers.Default) {
                SmartSearchSnapshot(
                    query = query,
                    filter = selectedFilter,
                    results = boundedSmartSearch(state, query, selectedFilter),
                )
            }
        }
    }
    val results = if (searchSnapshot.query == query && searchSnapshot.filter == selectedFilter) {
        searchSnapshot.results
    } else {
        emptyList()
    }
    val searchPending = query.length >= SMART_SEARCH_MIN_QUERY_LENGTH &&
        (searchSnapshot.query != query || searchSnapshot.filter != selectedFilter)
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
            Row(Modifier.fillMaxSize()) {
                SmartSearchRail(
                    isTv = isTv,
                    selected = MainDestination.SEARCH,
                    searchFieldRequester = searchFieldRequester,
                    searchRailRequester = railSearchRequester,
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
        )

        Spacer(Modifier.height(if (compactHeight || imeVisible) 7.dp else 10.dp))

        SmartSearchFilterBar(
            selected = selectedFilter,
            isTv = isTv,
            requesters = filterRequesters,
            searchFieldRequester = searchFieldRequester,
            downRequester = firstContentRequester,
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
                    isFavorite = isFavorite,
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
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var tvEditing by remember { mutableStateOf(false) }

    val moveDown: () -> Boolean = {
        when {
            firstFilterRequester != null -> {
                tvEditing = false
                keyboardController?.hide()
                runCatching { firstFilterRequester.requestFocus() }.isSuccess
            }
            hasResults -> {
                tvEditing = false
                keyboardController?.hide()
                runCatching { firstResultRequester.requestFocus() }.isSuccess
            }
            hasRecent -> {
                tvEditing = false
                keyboardController?.hide()
                runCatching { firstRecentRequester.requestFocus() }.isSuccess
            }
            else -> false
        }
    }

    val tvModifier = if (isTv) {
        Modifier
            .focusRequester(searchFieldRequester)
            .focusProperties {
                railSearchRequester?.let { right = it }
            }
            .onFocusChanged { state ->
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
                } else if (event.key == Key.DirectionDown) {
                    moveDown()
                } else {
                    false
                }
            }
    } else {
        Modifier
    }

    HulkTextField(
        value = value,
        onValueChange = onValueChange,
        label = "اكتب اسم فيلم او مسلسل او قناة…",
        modifier = Modifier.fillMaxWidth().then(tvModifier),
        readOnly = isTv && !tvEditing,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { moveDown() }),
    )
}

@Composable
private fun SmartSearchFilterBar(
    selected: SmartSearchFilter,
    isTv: Boolean,
    requesters: Map<SmartSearchFilter, FocusRequester>,
    searchFieldRequester: FocusRequester,
    downRequester: FocusRequester?,
    onSelect: (SmartSearchFilter) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 10.dp else 7.dp),
    ) {
        items(
            items = SmartSearchFilter.entries,
            key = SmartSearchFilter::name,
        ) { filter ->
            val active = selected == filter
            FocusButton(
                text = filter.label,
                onClick = { onSelect(filter) },
                modifier = Modifier
                    .focusRequester(requesters.getValue(filter))
                    .focusProperties {
                        up = searchFieldRequester
                        downRequester?.let { down = it }
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
    onOpen: (ProfileContentSearchHistoryEntry) -> Unit,
    onRemove: (ProfileContentSearchHistoryEntry) -> Unit,
    onClear: () -> Unit,
) {
    val colors = LocalHulkColors.current
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
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
            ) {
                items(items = entries, key = { it.stableKey }) { entry ->
                    val isFirstEntry = entries.firstOrNull()?.stableKey == entry.stableKey
                    SmartRecentCard(
                        entry = entry,
                        isTv = isTv,
                        openButtonModifier = if (isTv && isFirstEntry) {
                            Modifier
                                .focusRequester(firstRecentRequester)
                                .focusProperties { up = topRequester }
                        } else {
                            Modifier
                        },
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
    openButtonModifier: Modifier = Modifier,
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
                modifier = openButtonModifier.weight(1f),
                compact = true,
            )
            FocusButton(
                text = "حذف",
                onClick = onRemove,
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
    isFavorite: (ContentItem) -> Boolean,
    onOpenResult: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
) {
    val colors = LocalHulkColors.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = if (isTv) 146.dp else 112.dp),
        modifier = modifier.fillMaxWidth(),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (index == 0) {
                            Modifier
                                .focusRequester(firstResultRequester)
                                .focusProperties { up = topRequester }
                        } else {
                            Modifier
                        },
                    ),
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
            entry.destination to if (entry.destination == MainDestination.SEARCH) {
                searchRailRequester
            } else {
                FocusRequester()
            }
        }
    }
    val selectedRequester = destinationRequesters.getValue(selected)

    Column(
        modifier = Modifier
            .width(railWidth)
            .fillMaxHeight()
            .focusProperties { onEnter = { selectedRequester.requestFocus() } }
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
                                Modifier.focusProperties { left = searchFieldRequester }
                            } else {
                                Modifier
                            },
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

private fun boundedSmartSearch(
    state: HulkUiState,
    rawQuery: String,
    filter: SmartSearchFilter,
): List<ContentItem> {
    val query = normalizeSearchText(rawQuery)
    if (query.length < SMART_SEARCH_MIN_QUERY_LENGTH) return emptyList()
    val queryTokens = query.split(' ').filter(String::isNotBlank)

    val buckets = Array(SEARCH_RANK_COUNT) { mutableListOf<ContentItem>() }
    val seen = HashSet<String>()

    for (catalog in state.catalogs.values) {
        for (item in catalog.items) {
            if (!filter.accepts(item.type)) continue
            val rank = item.smartSearchRankOrNull(query, queryTokens) ?: continue
            val key = "${item.type}:${item.id}"
            if (!seen.add(key)) continue
            val bucket = buckets[rank]
            if (bucket.size < MAX_RESULTS_PER_RANK) {
                bucket += item
            }
        }
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

private fun ContentItem.smartSearchRankOrNull(
    normalizedQuery: String,
    queryTokens: List<String>,
): Int? {
    val cleanName = normalizeSearchText(name)
    val titleTokens = cleanName.split(' ').filter(String::isNotBlank)
    val normalizedYear = normalizeSearchText(year.orEmpty())
    val normalizedGenre = normalizeSearchText(genre.orEmpty())
    val phoneticRank = crossScriptPhoneticRank(normalizedQuery, cleanName)
    return when {
        cleanName == normalizedQuery -> 0
        cleanName.startsWith(normalizedQuery) -> 1
        titleTokens.any { it.startsWith(normalizedQuery) } -> 2
        cleanName.contains(normalizedQuery) -> 3
        queryTokens.size > 1 && queryTokens.all { token ->
            titleTokens.any { word -> word.startsWith(token) || word.contains(token) }
        } -> 4
        phoneticRank != null -> 5 + phoneticRank
        normalizedYear == normalizedQuery -> 8
        normalizedGenre.contains(normalizedQuery) -> 9
        queryTokens.size > 1 && queryTokens.all(normalizedGenre::contains) -> 10
        else -> null
    }
}

internal fun crossScriptPhoneticRank(query: String, title: String): Int? {
    val queryArabic = query.any(::isArabicSearchChar)
    val titleArabic = title.any(::isArabicSearchChar)
    if (queryArabic == titleArabic) return null

    val queryWords = phoneticSearchWords(query)
    val titleWords = phoneticSearchWords(title)
    if (queryWords.isEmpty() || titleWords.isEmpty()) return null
    val queryPhrase = queryWords.joinToString(" ")
    val titlePhrase = titleWords.joinToString(" ")
    if (queryPhrase.length < 3) return null

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

internal fun phoneticSearchWords(raw: String): List<String> = normalizeSearchText(raw)
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

internal fun normalizeSearchText(raw: String): String = normalizeSearchDigits(raw)
    .trim()
    .lowercase()
    .replace('أ', 'ا')
    .replace('إ', 'ا')
    .replace('آ', 'ا')
    .replace('ى', 'ي')
    .replace('ؤ', 'و')
    .replace('ئ', 'ي')
    .replace("ـ", "")
    .replace(ARABIC_DIACRITICS_REGEX, "")
    .replace(NON_ALPHANUMERIC_REGEX, " ")
    .replace(WHITESPACE_REGEX, " ")
    .trim()

private fun normalizeSearchDigits(raw: String): String = buildString(raw.length) {
    raw.forEach { char ->
        append(
            when (char) {
                in '٠'..'٩' -> ('0'.code + char.code - '٠'.code).toChar()
                in '۰'..'۹' -> ('0'.code + char.code - '۰'.code).toChar()
                else -> char
            },
        )
    }
}

private fun ContentType.smartLabel(): String = when (this) {
    ContentType.LIVE -> "قناة"
    ContentType.MOVIE -> "فيلم"
    ContentType.SERIES -> "مسلسل"
}

private const val SMART_SEARCH_MIN_QUERY_LENGTH = 2
private const val SEARCH_RANK_COUNT = 11
private const val MAX_RESULTS_PER_RANK = 48
private const val MAX_SEARCH_RESULTS = 120
private val WHITESPACE_REGEX = Regex("\\s+")
private val ARABIC_DIACRITICS_REGEX = Regex("[\\u064B-\\u065F\\u0670]")
private val NON_ALPHANUMERIC_REGEX = Regex("[^\\p{L}\\p{N}]+")
