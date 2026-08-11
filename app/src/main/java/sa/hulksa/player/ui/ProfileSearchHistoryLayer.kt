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
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.MainDestination
import sa.hulksa.player.data.ProfileSearchHistoryStore
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

private data class SearchDestinationEntry(
    val destination: MainDestination,
    val icon: ImageVector,
    val label: String,
)

private val searchDestinations = listOf(
    SearchDestinationEntry(MainDestination.HOME, Icons.Rounded.Home, "الرئيسية"),
    SearchDestinationEntry(MainDestination.LIVE, Icons.Rounded.LiveTv, "البث المباشر"),
    SearchDestinationEntry(MainDestination.MOVIES, Icons.Rounded.Movie, "الافلام"),
    SearchDestinationEntry(MainDestination.SERIES, Icons.Rounded.Tv, "المسلسلات"),
    SearchDestinationEntry(MainDestination.FAVORITES, Icons.Rounded.Favorite, "قائمتي"),
    SearchDestinationEntry(MainDestination.SEARCH, Icons.Rounded.Search, "البحث"),
    SearchDestinationEntry(MainDestination.DOWNLOADS, Icons.Rounded.Download, "التنزيلات"),
    SearchDestinationEntry(MainDestination.SETTINGS, Icons.Rounded.Settings, "الاعدادات"),
)

@Composable
internal fun ProfileSearchHistoryLayer(
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
    val searchHistoryStore = remember(context) { ProfileSearchHistoryStore(context) }
    val profileStore = remember(context) { ProfileStore(context) }
    val activeProfileId = profileStore.activeProfileId()
    var revision by remember(activeProfileId) { mutableIntStateOf(0) }
    val recentQueries = remember(activeProfileId, revision) {
        searchHistoryStore.recentQueries()
    }
    val normalizedQuery = state.searchQuery.trim()
    val results = remember(state.catalogs, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            emptyList()
        } else {
            state.catalogs.values
                .asSequence()
                .flatMap { it.items.asSequence() }
                .filter { it.matchesProfessionalSearch(normalizedQuery) }
                .distinctBy { "${it.type}:${it.id}" }
                .sortedWith(
                    compareByDescending<ContentItem> {
                        it.name.startsWith(normalizedQuery, ignoreCase = true)
                    }.thenByDescending {
                        it.name.contains(normalizedQuery, ignoreCase = true)
                    }.thenByDescending {
                        it.rating?.toDoubleOrNull() ?: 0.0
                    },
                )
                .toList()
        }
    }
    val loadingSearchCatalogs =
        normalizedQuery.isNotBlank() && state.loadingTypes.isNotEmpty() && results.isEmpty()

    LaunchedEffect(activeProfileId, normalizedQuery) {
        if (normalizedQuery.length < MIN_RECORDED_QUERY_LENGTH) return@LaunchedEffect
        delay(SEARCH_SETTLE_DELAY_MS)
        searchHistoryStore.record(normalizedQuery)
        revision++
    }

    val useNavigationRail = adaptiveUi.navigationType == HulkNavigationType.RAIL
    val searchFieldRequester = remember { FocusRequester() }
    val firstRecentRequester = remember { FocusRequester() }
    val firstResultRequester = remember { FocusRequester() }
    val railSearchRequester = remember { FocusRequester() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        if (useNavigationRail) {
            Row(Modifier.fillMaxSize()) {
                SearchNavigationRail(
                    isTv = isTv,
                    selected = MainDestination.SEARCH,
                    searchFieldRequester = searchFieldRequester,
                    searchRailRequester = railSearchRequester,
                    onSelectDestination = onSelectDestination,
                    onSwitchProfile = onSwitchProfile,
                )
                SearchExperienceContent(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    state = state,
                    isTv = isTv,
                    recentQueries = recentQueries,
                    results = results,
                    loadingSearchCatalogs = loadingSearchCatalogs,
                    searchFieldRequester = searchFieldRequester,
                    firstRecentRequester = firstRecentRequester,
                    firstResultRequester = firstResultRequester,
                    railSearchRequester = railSearchRequester,
                    isFavorite = isFavorite,
                    onSearch = onSearch,
                    onOpen = onOpen,
                    onToggleFavorite = onToggleFavorite,
                    onUseRecent = { query ->
                        searchHistoryStore.record(query)
                        revision++
                        onSearch(query)
                    },
                    onRemoveRecent = { query ->
                        searchHistoryStore.remove(query)
                        revision++
                    },
                    onClearRecent = {
                        searchHistoryStore.clear()
                        revision++
                    },
                    onSwitchProfile = onSwitchProfile,
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                SearchExperienceContent(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = state,
                    isTv = false,
                    recentQueries = recentQueries,
                    results = results,
                    loadingSearchCatalogs = loadingSearchCatalogs,
                    searchFieldRequester = searchFieldRequester,
                    firstRecentRequester = firstRecentRequester,
                    firstResultRequester = firstResultRequester,
                    railSearchRequester = null,
                    isFavorite = isFavorite,
                    onSearch = onSearch,
                    onOpen = onOpen,
                    onToggleFavorite = onToggleFavorite,
                    onUseRecent = { query ->
                        searchHistoryStore.record(query)
                        revision++
                        onSearch(query)
                    },
                    onRemoveRecent = { query ->
                        searchHistoryStore.remove(query)
                        revision++
                    },
                    onClearRecent = {
                        searchHistoryStore.clear()
                        revision++
                    },
                    onSwitchProfile = onSwitchProfile,
                )
                SearchMobileNavigation(
                    selected = MainDestination.SEARCH,
                    onSelectDestination = onSelectDestination,
                )
            }
        }
    }
}

@Composable
private fun SearchExperienceContent(
    modifier: Modifier,
    state: HulkUiState,
    isTv: Boolean,
    recentQueries: List<String>,
    results: List<ContentItem>,
    loadingSearchCatalogs: Boolean,
    searchFieldRequester: FocusRequester,
    firstRecentRequester: FocusRequester,
    firstResultRequester: FocusRequester,
    railSearchRequester: FocusRequester?,
    isFavorite: (ContentItem) -> Boolean,
    onSearch: (String) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onUseRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onSwitchProfile: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val screenWidth = adaptiveUi.screenWidthDp
    val screenHeight = adaptiveUi.screenHeightDp
    val horizontalPadding = when {
        isTv -> (screenWidth / 46f).coerceIn(18f, 34f).dp
        screenWidth >= 840 -> 28.dp
        screenWidth >= 600 -> 22.dp
        else -> 14.dp
    }
    val verticalPadding = when {
        isTv -> (screenHeight / 42f).coerceIn(14f, 24f).dp
        screenHeight < 520 -> 10.dp
        else -> 14.dp
    }
    val titleSize = when {
        isTv -> 30.sp
        screenWidth >= 600 -> 27.sp
        else -> 23.sp
    }
    val subtitleSize = when {
        isTv -> 13.sp
        screenWidth >= 600 -> 12.sp
        else -> 11.sp
    }
    val queryBlank = state.searchQuery.isBlank()

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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(if (isTv) 52.dp else 44.dp)
                    .clip(CircleShape)
                    .background(colors.gold.copy(alpha = .14f))
                    .border(1.dp, colors.gold.copy(alpha = .34f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = colors.goldBright,
                    modifier = Modifier.size(if (isTv) 27.dp else 23.dp),
                )
            }

            Column(Modifier.weight(1f)) {
                Text(
                    text = "البحث",
                    color = colors.text,
                    fontSize = titleSize,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "ابحث في القنوات والافلام والمسلسلات من مكان واحد",
                    color = colors.textMuted,
                    fontSize = subtitleSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!isTv && screenWidth >= 360) {
                FocusButton(
                    text = "تغيير المستخدم",
                    onClick = onSwitchProfile,
                    primary = false,
                    compact = true,
                    outlined = true,
                )
            }
        }

        Spacer(Modifier.height(if (isTv) 18.dp else 14.dp))

        SearchInput(
            value = state.searchQuery,
            onValueChange = onSearch,
            isTv = isTv,
            hasRecent = recentQueries.isNotEmpty(),
            hasResults = results.isNotEmpty(),
            searchFieldRequester = searchFieldRequester,
            firstRecentRequester = firstRecentRequester,
            firstResultRequester = firstResultRequester,
            railSearchRequester = railSearchRequester,
        )

        Spacer(Modifier.height(if (isTv) 16.dp else 12.dp))

        if (queryBlank) {
            if (recentQueries.isNotEmpty()) {
                RecentSearchesSection(
                    recentQueries = recentQueries,
                    isTv = isTv,
                    screenWidthDp = screenWidth,
                    firstRecentRequester = firstRecentRequester,
                    searchFieldRequester = searchFieldRequester,
                    onUseRecent = onUseRecent,
                    onRemoveRecent = onRemoveRecent,
                    onClearRecent = onClearRecent,
                )
            } else {
                SearchWelcomeState(isTv = isTv)
            }
        } else {
            SearchResultsSection(
                modifier = Modifier.weight(1f),
                query = state.searchQuery.trim(),
                results = results,
                isTv = isTv,
                loading = loadingSearchCatalogs,
                firstResultRequester = firstResultRequester,
                searchFieldRequester = searchFieldRequester,
                isFavorite = isFavorite,
                onOpen = onOpen,
                onToggleFavorite = onToggleFavorite,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    isTv: Boolean,
    hasRecent: Boolean,
    hasResults: Boolean,
    searchFieldRequester: FocusRequester,
    firstRecentRequester: FocusRequester,
    firstResultRequester: FocusRequester,
    railSearchRequester: FocusRequester?,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    var tvEditing by remember { mutableStateOf(false) }

    val moveDown: () -> Boolean = {
        when {
            value.isBlank() && hasRecent -> {
                tvEditing = false
                keyboardController?.hide()
                runCatching { firstRecentRequester.requestFocus() }.isSuccess
            }
            value.isNotBlank() && hasResults -> {
                tvEditing = false
                keyboardController?.hide()
                runCatching { firstResultRequester.requestFocus() }.isSuccess
            }
            else -> false
        }
    }

    LaunchedEffect(isTv) {
        if (isTv) {
            delay(140L)
            runCatching { searchFieldRequester.requestFocus() }
        }
    }

    LaunchedEffect(isTv, tvEditing) {
        if (isTv) {
            if (tvEditing) keyboardController?.show() else keyboardController?.hide()
        }
    }

    val tvModifier = if (isTv) {
        Modifier
            .focusRequester(searchFieldRequester)
            .focusProperties {
                railSearchRequester?.let { right = it }
            }
            .onFocusChanged { focusState ->
                if (!focusState.isFocused) {
                    tvEditing = false
                    keyboardController?.hide()
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else if (!tvEditing && (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                    tvEditing = true
                    true
                } else {
                    when {
                        event.key == Key.DirectionDown -> moveDown()
                        event.key == Key.Back && imeVisible -> {
                            tvEditing = false
                            keyboardController?.hide()
                            true
                        }
                        else -> false
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
        modifier = Modifier
            .fillMaxWidth()
            .then(tvModifier),
        readOnly = isTv && !tvEditing,
        keyboardOptions = if (isTv) {
            KeyboardOptions(imeAction = ImeAction.Search)
        } else {
            KeyboardOptions.Default
        },
        keyboardActions = if (isTv) {
            KeyboardActions(onSearch = { moveDown() })
        } else {
            KeyboardActions.Default
        },
    )
}

@Composable
private fun RecentSearchesSection(
    recentQueries: List<String>,
    isTv: Boolean,
    screenWidthDp: Int,
    firstRecentRequester: FocusRequester,
    searchFieldRequester: FocusRequester,
    onUseRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val tileWidth = when {
        isTv -> 270.dp
        screenWidthDp >= 600 -> 238.dp
        else -> 190.dp
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (isTv) 20.dp else 16.dp))
            .background(colors.surfaceRaised.copy(alpha = .74f))
            .border(
                1.dp,
                colors.gold.copy(alpha = .22f),
                RoundedCornerShape(if (isTv) 20.dp else 16.dp),
            )
            .padding(if (isTv) 18.dp else 13.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "عمليات البحث الاخيرة",
                    color = colors.text,
                    fontSize = if (isTv) 19.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "سجل مستقل لهذا المستخدم فقط",
                    color = colors.textMuted,
                    fontSize = if (isTv) 11.sp else 10.sp,
                )
            }
            FocusButton(
                text = "مسح الكل",
                onClick = onClearRecent,
                primary = false,
                compact = true,
                outlined = true,
            )
        }

        Spacer(Modifier.height(if (isTv) 14.dp else 10.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 12.dp else 9.dp),
        ) {
            items(
                items = recentQueries,
                key = { it.lowercase() },
            ) { recentQuery ->
                val index = recentQueries.indexOf(recentQuery)
                Column(
                    modifier = Modifier
                        .width(tileWidth)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF11130F))
                        .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(14.dp))
                        .padding(11.dp),
                ) {
                    Text(
                        text = recentQuery,
                        color = colors.text,
                        fontSize = if (isTv) 15.sp else 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(9.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        FocusButton(
                            text = "بحث",
                            onClick = { onUseRecent(recentQuery) },
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (index == 0) {
                                        Modifier
                                            .focusRequester(firstRecentRequester)
                                            .focusProperties { up = searchFieldRequester }
                                    } else {
                                        Modifier
                                    },
                                ),
                            compact = true,
                        )
                        FocusButton(
                            text = "حذف",
                            onClick = { onRemoveRecent(recentQuery) },
                            primary = false,
                            compact = true,
                            outlined = true,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(if (isTv) 12.dp else 9.dp))
        Text(
            text = if (isTv) {
                "اضغط OK على بحث سابق لاستخدامه، أو ارجع الى حقل البحث لكتابة كلمة جديدة"
            } else {
                "اختر بحثا سابقا او اكتب كلمة جديدة"
            },
            color = colors.textMuted.copy(alpha = .86f),
            fontSize = if (isTv) 10.sp else 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    Spacer(Modifier.height(if (isTv) 18.dp else 13.dp))
    SearchDiscoveryHint(isTv = isTv)
}

@Composable
private fun SearchWelcomeState(isTv: Boolean) {
    val colors = LocalHulkColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (isTv) 20.dp else 16.dp))
            .background(colors.surfaceRaised.copy(alpha = .52f))
            .border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(if (isTv) 20.dp else 16.dp))
            .padding(vertical = if (isTv) 34.dp else 26.dp, horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = colors.goldBright,
                modifier = Modifier.size(if (isTv) 42.dp else 34.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "ابحث عن المحتوى الذي تريده",
                color = colors.text,
                fontSize = if (isTv) 20.sp else 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "اكتب اسم قناة او فيلم او مسلسل، ويمكنك ايضا البحث بالسنة او النوع",
                color = colors.textMuted,
                fontSize = if (isTv) 12.sp else 10.sp,
                maxLines = 2,
            )
        }
    }

    Spacer(Modifier.height(if (isTv) 18.dp else 13.dp))
    SearchDiscoveryHint(isTv = isTv)
}

@Composable
private fun SearchDiscoveryHint(isTv: Boolean) {
    val colors = LocalHulkColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchTypePill("قنوات", Icons.Rounded.LiveTv, Modifier.weight(1f), isTv)
        SearchTypePill("افلام", Icons.Rounded.Movie, Modifier.weight(1f), isTv)
        SearchTypePill("مسلسلات", Icons.Rounded.Tv, Modifier.weight(1f), isTv)
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = "نتائج البحث تعرض كل المصادر المتاحة في حسابك",
        color = colors.textMuted.copy(alpha = .7f),
        fontSize = if (isTv) 9.sp else 8.sp,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SearchTypePill(
    text: String,
    icon: ImageVector,
    modifier: Modifier,
    isTv: Boolean,
) {
    val colors = LocalHulkColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF10120E))
            .border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(12.dp))
            .padding(horizontal = if (isTv) 14.dp else 10.dp, vertical = if (isTv) 11.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.goldBright,
            modifier = Modifier.size(if (isTv) 18.dp else 16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = colors.text,
            fontSize = if (isTv) 12.sp else 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SearchResultsSection(
    modifier: Modifier,
    query: String,
    results: List<ContentItem>,
    isTv: Boolean,
    loading: Boolean,
    firstResultRequester: FocusRequester,
    searchFieldRequester: FocusRequester,
    isFavorite: (ContentItem) -> Boolean,
    onOpen: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
) {
    val colors = LocalHulkColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "نتائج البحث",
                color = colors.text,
                fontSize = if (isTv) 18.sp else 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            if (!loading) {
                Text(
                    text = "${results.size} نتيجة",
                    color = colors.textMuted,
                    fontSize = if (isTv) 11.sp else 10.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "\"$query\"",
                color = colors.goldBright,
                fontSize = if (isTv) 11.sp else 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = if (isTv) 360.dp else 180.dp),
            )
        }

        Spacer(Modifier.height(if (isTv) 12.dp else 9.dp))

        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingRing(label = "جاري تجهيز نتائج البحث…")
                }
            }
            results.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceRaised.copy(alpha = .56f))
                        .padding(vertical = if (isTv) 34.dp else 28.dp, horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "لا توجد نتائج مطابقة",
                            color = colors.text,
                            fontSize = if (isTv) 19.sp else 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = "جرب اسما اقصر او كلمة مختلفة",
                            color = colors.textMuted,
                            fontSize = if (isTv) 11.sp else 10.sp,
                        )
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = if (isTv) 146.dp else 112.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = if (isTv) 24.dp else 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isTv) 16.dp else 12.dp),
                ) {
                    itemsIndexed(
                        items = results,
                        key = { _, item -> "${item.type}:${item.id}" },
                    ) { index, item ->
                        UniversalPosterCard(
                            item = item,
                            isFavorite = isFavorite(item),
                            onClick = { onOpen(item) },
                            onLongClick = { onToggleFavorite(item) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (index == 0) {
                                        Modifier
                                            .focusRequester(firstResultRequester)
                                            .focusProperties { up = searchFieldRequester }
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchNavigationRail(
    isTv: Boolean,
    selected: MainDestination,
    searchFieldRequester: FocusRequester,
    searchRailRequester: FocusRequester,
    onSelectDestination: (MainDestination) -> Unit,
    onSwitchProfile: () -> Unit,
) {
    val adaptiveUi = LocalAdaptiveUi.current
    val colors = LocalHulkColors.current
    val metrics = tvRailMetrics(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp)
    var railHasFocus by remember { mutableStateOf(false) }
    val expanded = railHasFocus || !isTv
    val railWidth by animateDpAsState(
        targetValue = if (expanded) metrics.expandedWidthDp.dp else metrics.collapsedWidthDp.dp,
        label = "searchRailWidth",
    )
    val destinationRequesters = remember {
        searchDestinations.associate { entry ->
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
            .focusProperties {
                onEnter = { selectedRequester.requestFocus() }
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

        searchDestinations
            .filterNot { it.destination == MainDestination.SETTINGS }
            .forEach { entry ->
                SearchRailItem(
                    entry = entry,
                    selected = selected == entry.destination,
                    expanded = expanded,
                    isTv = isTv,
                    modifier = Modifier
                        .focusRequester(destinationRequesters.getValue(entry.destination))
                        .then(
                            if (entry.destination == MainDestination.SEARCH) {
                                Modifier.focusProperties { left = searchFieldRequester }
                            } else {
                                Modifier
                            },
                        ),
                    onClick = { onSelectDestination(entry.destination) },
                )
                Spacer(Modifier.height(metrics.itemGapDp.dp))
            }

        SearchRailItem(
            entry = SearchDestinationEntry(
                MainDestination.SEARCH,
                Icons.Rounded.Person,
                "تغيير المستخدم",
            ),
            selected = false,
            expanded = expanded,
            isTv = isTv,
            onClick = onSwitchProfile,
        )

        Spacer(Modifier.weight(1f))

        searchDestinations.first { it.destination == MainDestination.SETTINGS }.let { entry ->
            SearchRailItem(
                entry = entry,
                selected = false,
                expanded = expanded,
                isTv = isTv,
                modifier = Modifier.focusRequester(destinationRequesters.getValue(entry.destination)),
                onClick = { onSelectDestination(entry.destination) },
            )
        }
    }
}

@Composable
private fun SearchRailItem(
    entry: SearchDestinationEntry,
    selected: Boolean,
    expanded: Boolean,
    isTv: Boolean,
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
private fun SearchMobileNavigation(
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
                items = searchDestinations,
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

private fun ContentItem.matchesProfessionalSearch(rawQuery: String): Boolean {
    val query = rawQuery.trim()
    if (query.isBlank()) return false
    val typeLabel = when (type) {
        ContentType.LIVE -> "قناة بث مباشر live"
        ContentType.MOVIE -> "فيلم افلام movie"
        ContentType.SERIES -> "مسلسل مسلسلات series"
    }
    return sequenceOf(
        name,
        year.orEmpty(),
        genre.orEmpty(),
        plot.orEmpty(),
        nowPlaying.orEmpty(),
        typeLabel,
    ).any { value -> value.contains(query, ignoreCase = true) }
}

private const val SEARCH_SETTLE_DELAY_MS = 1_100L
private const val MIN_RECORDED_QUERY_LENGTH = 2
