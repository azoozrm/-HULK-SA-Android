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
import androidx.compose.foundation.lazy.grid.item
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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
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

private data class SmartSearchSnapshot(
    val query: String = "",
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
    val recentEntries = remember(activeProfileId, historyRevision) { historyStore.recent() }

    val normalizedQuery = state.searchQuery.trim()
    var settledQuery by remember { mutableStateOf("") }
    LaunchedEffect(normalizedQuery) {
        if (normalizedQuery.length < MIN_QUERY_LENGTH) {
            settledQuery = ""
        } else {
            delay(SEARCH_DEBOUNCE_MS)
            settledQuery = normalizedQuery
        }
    }

    val searchSnapshot by produceState(
        initialValue = SmartSearchSnapshot(),
        key1 = state.catalogs,
        key2 = settledQuery,
    ) {
        value = if (settledQuery.length < MIN_QUERY_LENGTH) {
            SmartSearchSnapshot()
        } else {
            withContext(Dispatchers.Default) {
                SmartSearchSnapshot(
                    query = settledQuery,
                    results = boundedSmartSearch(state, settledQuery),
                )
            }
        }
    }

    val results = if (searchSnapshot.query == settledQuery) searchSnapshot.results else emptyList()
    val imeVisible = !isTv && WindowInsets.isImeVisible
    val suggestionLimit = if (isTv) MAX_TV_SUGGESTIONS else MAX_PHONE_SUGGESTIONS
    val suggestions = remember(results, suggestionLimit) { results.take(suggestionLimit) }
    val searchPending = normalizedQuery.length >= MIN_QUERY_LENGTH &&
        (settledQuery != normalizedQuery || searchSnapshot.query != settledQuery)
    val loadingCatalogs = normalizedQuery.length >= MIN_QUERY_LENGTH &&
        state.loadingTypes.isNotEmpty() && results.isEmpty()
    val useRail = adaptiveUi.navigationType == HulkNavigationType.RAIL

    val searchFieldRequester = remember { FocusRequester() }
    val firstSuggestionRequester = remember { FocusRequester() }
    val firstResultRequester = remember { FocusRequester() }
    val railSearchRequester = remember { FocusRequester() }

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
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    state = state,
                    isTv = isTv,
                    recentEntries = recentEntries,
                    suggestions = suggestions,
                    results = results,
                    searchPending = searchPending,
                    loadingCatalogs = loadingCatalogs,
                    imeVisible = imeVisible,
                    searchFieldRequester = searchFieldRequester,
                    firstSuggestionRequester = firstSuggestionRequester,
                    firstResultRequester = firstResultRequester,
                    railSearchRequester = if (isTv) railSearchRequester else null,
                    isFavorite = isFavorite,
                    onSearch = onSearch,
                    onOpenSuggestion = recordAndOpen,
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
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .imePadding(),
                    state = state,
                    isTv = false,
                    recentEntries = recentEntries,
                    suggestions = suggestions,
                    results = results,
                    searchPending = searchPending,
                    loadingCatalogs = loadingCatalogs,
                    imeVisible = imeVisible,
                    searchFieldRequester = searchFieldRequester,
                    firstSuggestionRequester = firstSuggestionRequester,
                    firstResultRequester = firstResultRequester,
                    railSearchRequester = null,
                    isFavorite = isFavorite,
                    onSearch = onSearch,
                    onOpenSuggestion = recordAndOpen,
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
    suggestions: List<ContentItem>,
    results: List<ContentItem>,
    searchPending: Boolean,
    loadingCatalogs: Boolean,
    imeVisible: Boolean,
    searchFieldRequester: FocusRequester,
    firstSuggestionRequester: FocusRequester,
    firstResultRequester: FocusRequester,
    railSearchRequester: FocusRequester?,
    isFavorite: (ContentItem) -> Boolean,
    onSearch: (String) -> Unit,
    onOpenSuggestion: (ContentItem) -> Unit,
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

    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.background,
                        colors.surface.copy(alpha = .96f),
                        colors.background,
                    ),
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
                        text = "اقتراحات سريعة من القنوات والافلام والمسلسلات",
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
            hasSuggestions = suggestions.isNotEmpty(),
            hasResults = results.isNotEmpty(),
            searchFieldRequester = searchFieldRequester,
            firstSuggestionRequester = firstSuggestionRequester,
            firstResultRequester = firstResultRequester,
            railSearchRequester = railSearchRequester,
        )

        Spacer(Modifier.height(if (compactHeight || imeVisible) 8.dp else 12.dp))

        when {
            state.searchQuery.isBlank() -> {
                SmartRecentSection(
                    modifier = Modifier.weight(1f),
                    entries = recentEntries,
                    isTv = isTv,
                    onOpen = onOpenRecent,
                    onRemove = onRemoveRecent,
                    onClear = onClearRecent,
                )
            }

            state.searchQuery.trim().length < MIN_QUERY_LENGTH -> {
                SearchMessage(
                    modifier = Modifier.weight(1f),
                    isTv = isTv,
                    title = "اكتب حرفين على الاقل",
                    subtitle = "لن يبدأ فحص المكتبة من حرف واحد حتى يبقى البحث سريع ومستقر",
                )
            }

            searchPending || loadingCatalogs -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LoadingRing(label = "جاري تجهيز الاقتراحات…")
                }
            }

            results.isEmpty() -> {
                SearchMessage(
                    modifier = Modifier.weight(1f),
                    isTv = isTv,
                    title = "لا توجد نتائج مطابقة",
                    subtitle = "جرب كتابة جزء آخر من اسم الفيلم او المسلسل او القناة",
                )
            }

            else -> {
                SmartSuggestionsAndResults(
                    modifier = Modifier.weight(1f),
                    query = state.searchQuery.trim(),
                    suggestions = suggestions,
                    results = results,
                    isTv = isTv,
                    firstSuggestionRequester = firstSuggestionRequester,
                    firstResultRequester = firstResultRequester,
                    searchFieldRequester = searchFieldRequester,
                    isFavorite = isFavorite,
                    onOpenSuggestion = onOpenSuggestion,
                    onOpenResult = onOpenResult,
                    onToggleFavorite = onToggleFavorite,
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
    hasSuggestions: Boolean,
    hasResults: Boolean,
    searchFieldRequester: FocusRequester,
    firstSuggestionRequester: FocusRequester,
    firstResultRequester: FocusRequester,
    railSearchRequester: FocusRequester?,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var tvEditing by remember { mutableStateOf(false) }

    val moveDown: () -> Boolean = {
        when {
            hasSuggestions -> {
                tvEditing = false
                keyboardController?.hide()
                runCatching { firstSuggestionRequester.requestFocus() }.isSuccess
            }

            hasResults -> {
                tvEditing = false
                keyboardController?.hide()
                runCatching { firstResultRequester.requestFocus() }.isSuccess
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
        modifier = Modifier
            .fillMaxWidth()
            .then(tvModifier),
        readOnly = isTv && !tvEditing,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { moveDown() }),
    )
}

@Composable
private fun SmartRecentSection(
    modifier: Modifier,
    entries: List<ProfileContentSearchHistoryEntry>,
    isTv: Boolean,
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
                        "السجل يحفظ فقط المحتوى الحقيقي الذي تختاره"
                    } else {
                        "سجل مستقل لهذا المستخدم، بدون كلمات عشوائية او تكرار"
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
                items(
                    items = entries,
                    key = { it.stableKey },
                ) { entry ->
                    SmartRecentCard(
                        entry = entry,
                        isTv = isTv,
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
            .clickable(role = Role.Button, onClick = onOpen)
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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FocusButton(
                text = "فتح",
                onClick = onOpen,
                modifier = Modifier.weight(1f),
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
private fun SmartSuggestionsAndResults(
    modifier: Modifier,
    query: String,
    suggestions: List<ContentItem>,
    results: List<ContentItem>,
    isTv: Boolean,
    firstSuggestionRequester: FocusRequester,
    firstResultRequester: FocusRequester,
    searchFieldRequester: FocusRequester,
    isFavorite: (ContentItem) -> Boolean,
    onOpenSuggestion: (ContentItem) -> Unit,
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
                    text = "اقتراحات",
                    color = colors.text,
                    fontSize = if (isTv) 19.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "\"$query\"",
                    color = colors.goldBright,
                    fontSize = if (isTv) 11.sp else 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        suggestions.forEachIndexed { index, item ->
            item(
                key = "suggestion:${item.type}:${item.id}",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                SmartSuggestionRow(
                    item = item,
                    isTv = isTv,
                    modifier = Modifier.then(
                        if (index == 0) {
                            Modifier
                                .focusRequester(firstSuggestionRequester)
                                .focusProperties { up = searchFieldRequester }
                        } else {
                            Modifier
                        },
                    ),
                    onClick = { onOpenSuggestion(item) },
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "كل النتائج",
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
                                .focusProperties { up = firstSuggestionRequester }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

@Composable
private fun SmartSuggestionRow(
    item: ContentItem,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceRaised.copy(alpha = .78f))
            .border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(if (isTv) 58.dp else 48.dp)
                .height(if (isTv) 72.dp else 58.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = colors.text,
                fontSize = if (isTv) 15.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(item.type.smartLabel(), item.year?.takeIf(String::isNotBlank)).joinToString(" • "),
                color = colors.textMuted,
                fontSize = if (isTv) 11.sp else 10.sp,
                maxLines = 1,
            )
            item.genre?.takeIf(String::isNotBlank)?.let { genre ->
                Text(
                    text = genre,
                    color = colors.textMuted.copy(alpha = .75f),
                    fontSize = if (isTv) 10.sp else 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = "فتح",
            color = colors.goldBright,
            fontSize = if (isTv) 12.sp else 10.sp,
            fontWeight = FontWeight.Bold,
        )
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
            items(
                items = smartSearchDestinations,
                key = { it.destination.name },
            ) { entry ->
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
): List<ContentItem> {
    val query = normalizeSearchText(rawQuery)
    if (query.length < MIN_QUERY_LENGTH) return emptyList()

    val buckets = Array(SEARCH_RANK_COUNT) { mutableListOf<ContentItem>() }
    val seen = HashSet<String>()

    state.catalogs.values.forEach { catalog ->
        catalog.items.forEach { item ->
            val rank = item.smartSearchRankOrNull(query) ?: return@forEach
            val key = "${item.type}:${item.id}"
            if (!seen.add(key)) return@forEach
            val bucket = buckets[rank]
            if (bucket.size < MAX_RESULTS_PER_RANK) {
                bucket += item
            }
        }
    }

    val withinRank = compareByDescending<ContentItem> { it.rating?.toDoubleOrNull() ?: 0.0 }
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

private fun ContentItem.smartSearchRankOrNull(normalizedQuery: String): Int? {
    val cleanName = normalizeSearchText(name)
    val normalizedYear = normalizeSearchText(year.orEmpty())
    val normalizedGenre = normalizeSearchText(genre.orEmpty())
    return when {
        cleanName == normalizedQuery -> 0
        cleanName.startsWith(normalizedQuery) -> 1
        cleanName.split(' ').any { it.startsWith(normalizedQuery) } -> 2
        cleanName.contains(normalizedQuery) -> 3
        normalizedYear == normalizedQuery -> 4
        normalizedGenre.contains(normalizedQuery) -> 5
        else -> null
    }
}

private fun normalizeSearchText(raw: String): String = raw
    .trim()
    .lowercase()
    .replace('أ', 'ا')
    .replace('إ', 'ا')
    .replace('آ', 'ا')
    .replace('ى', 'ي')
    .replace("ـ", "")
    .replace(ARABIC_DIACRITICS_REGEX, "")
    .replace(WHITESPACE_REGEX, " ")

private fun ContentType.smartLabel(): String = when (this) {
    ContentType.LIVE -> "قناة"
    ContentType.MOVIE -> "فيلم"
    ContentType.SERIES -> "مسلسل"
}

private const val MIN_QUERY_LENGTH = 2
private const val SEARCH_DEBOUNCE_MS = 140L
private const val MAX_PHONE_SUGGESTIONS = 4
private const val MAX_TV_SUGGESTIONS = 5
private const val SEARCH_RANK_COUNT = 6
private const val MAX_RESULTS_PER_RANK = 48
private const val MAX_SEARCH_RESULTS = 120
private val WHITESPACE_REGEX = Regex("\\s+")
private val ARABIC_DIACRITICS_REGEX = Regex("[\\u064B-\\u065F\\u0670]")
