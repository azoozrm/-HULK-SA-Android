package sa.hulksa.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import sa.hulksa.player.HulkScreen
import sa.hulksa.player.HulkViewModel
import sa.hulksa.player.data.VerifiedKidsCatalogSnapshot
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.ui.adaptive.ApplyAdaptiveWindowPresentation
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.adaptive.rememberAdaptiveUiState
import sa.hulksa.player.ui.adaptive.trackAdaptiveInput
import sa.hulksa.player.ui.components.HulkTextField
import sa.hulksa.player.ui.screens.MovieDetailsScreen
import sa.hulksa.player.ui.screens.PlayerScreen
import sa.hulksa.player.ui.screens.ProfileAvatarArtwork
import sa.hulksa.player.ui.screens.SeriesDetailsScreenV2
import sa.hulksa.player.ui.theme.LocalHulkColors

@Composable
fun KidsProfileExperience(
    viewModel: HulkViewModel,
    isTelevisionDevice: Boolean,
    profile: UserProfile,
    snapshot: VerifiedKidsCatalogSnapshot?,
    sourceLoading: Boolean,
    sourceError: String?,
    onRetrySource: () -> Unit,
    onSwitchProfile: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val (adaptiveUi, inputController) = rememberAdaptiveUiState(isTelevisionDevice)
    val isTv = adaptiveUi.isTelevision
    val colors = LocalHulkColors.current

    ApplyAdaptiveWindowPresentation(
        isTelevisionDevice = isTv,
        isPlayer = state.screen == HulkScreen.PLAYER,
    )

    val safeSnapshot = snapshot?.takeIf(VerifiedKidsCatalogSnapshot::isAvailable)
    val safeOpen: (ContentItem) -> Unit = { item ->
        if (safeSnapshot != null && isVerifiedKidsItem(safeSnapshot, item)) {
            viewModel.open(item)
        }
    }

    CompositionLocalProvider(LocalAdaptiveUi provides adaptiveUi) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .trackAdaptiveInput(inputController),
        ) {
            when {
                safeSnapshot == null -> KidsSourceGate(
                    profile = profile,
                    isTv = isTv,
                    loading = sourceLoading,
                    message = sourceError,
                    onRetry = onRetrySource,
                    onSwitchProfile = onSwitchProfile,
                )

                state.screen == HulkScreen.MAIN -> KidsMainScreen(
                    profile = profile,
                    snapshot = safeSnapshot,
                    isTv = isTv,
                    onOpen = safeOpen,
                    onSwitchProfile = onSwitchProfile,
                )

                state.screen == HulkScreen.MOVIE_DETAILS -> {
                    val item = state.selectedItem?.takeIf { isVerifiedKidsItem(safeSnapshot, it) }
                    if (item == null) {
                        LaunchedEffect(state.screen) { viewModel.back() }
                    } else {
                        val related = safeSnapshot.catalog(ContentType.MOVIE).items
                            .asSequence()
                            .filter { it.id != item.id && it.categoryId == item.categoryId }
                            .take(10)
                            .toList()
                        val download = state.downloads.firstOrNull { it.historyKey == "MOVIE:${item.id}" }
                        MovieDetailsScreen(
                            item = item,
                            details = state.selectedDetails,
                            isLoading = state.isLoading,
                            errorMessage = state.errorMessage,
                            isTv = isTv,
                            isFavorite = viewModel.isFavorite(item),
                            download = download,
                            historyEntry = state.history.firstOrNull { it.key == "MOVIE:${item.id}" },
                            relatedItems = related,
                            isRelatedFavorite = viewModel::isFavorite,
                            onBack = viewModel::back,
                            onPlay = viewModel::playSelectedMovie,
                            onDownload = {
                                if (download == null) viewModel.downloadSelectedMovie() else viewModel.retryDownload(download)
                            },
                            onCancelDownload = { download?.let(viewModel::deleteDownload) },
                            onToggleFavorite = { viewModel.toggleFavorite(item) },
                            onToggleRelatedFavorite = viewModel::toggleFavorite,
                            onOpenRelated = safeOpen,
                        )
                    }
                }

                state.screen == HulkScreen.SERIES -> {
                    val series = state.selectedSeries?.takeIf { isVerifiedKidsItem(safeSnapshot, it) }
                    if (series == null) {
                        LaunchedEffect(state.screen) { viewModel.back() }
                    } else {
                        val related = safeSnapshot.catalog(ContentType.SERIES).items
                            .asSequence()
                            .filter { it.id != series.id && it.categoryId == series.categoryId }
                            .take(10)
                            .toList()
                        SeriesDetailsScreenV2(
                            series = series,
                            details = state.selectedDetails,
                            episodes = state.episodes,
                            isLoading = state.isLoading,
                            errorMessage = state.errorMessage,
                            isTv = isTv,
                            isFavorite = viewModel.isFavorite(series),
                            downloads = state.downloads,
                            history = state.history,
                            relatedItems = related,
                            isRelatedFavorite = viewModel::isFavorite,
                            onBack = viewModel::back,
                            onPlay = viewModel::playEpisode,
                            onDownload = { episode ->
                                val existing = state.downloads.firstOrNull { it.historyKey == "SERIES:${episode.id}" }
                                if (existing == null) viewModel.downloadEpisode(episode) else viewModel.retryDownload(existing)
                            },
                            onCancelDownload = { episode ->
                                state.downloads.firstOrNull { it.historyKey == "SERIES:${episode.id}" }
                                    ?.let(viewModel::deleteDownload)
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(series) },
                            onToggleRelatedFavorite = viewModel::toggleFavorite,
                            onOpenRelated = safeOpen,
                        )
                    }
                }

                state.screen == HulkScreen.PLAYER -> {
                    val playback = state.playback
                    if (playback == null) {
                        LaunchedEffect(state.screen) { viewModel.back() }
                    } else {
                        val orderedEpisodes = state.episodes.sortedWith(
                            compareBy(sa.hulksa.player.model.Episode::season, sa.hulksa.player.model.Episode::episodeNumber),
                        )
                        val currentEpisodeIndex = orderedEpisodes.indexOfFirst { it.id == playback.streamId }
                        val nextEpisode = if (playback.streamKind == "series") {
                            orderedEpisodes.getOrNull(currentEpisodeIndex + 1)
                        } else {
                            null
                        }
                        PlayerScreen(
                            request = playback,
                            liveCatalog = safeSnapshot.catalog(ContentType.LIVE),
                            isFavorite = viewModel::isFavorite,
                            onSelectLiveChannel = { channel ->
                                if (isVerifiedKidsItem(safeSnapshot, channel)) viewModel.switchLiveChannel(channel)
                            },
                            onToggleFavorite = { item ->
                                if (isVerifiedKidsItem(safeSnapshot, item)) viewModel.toggleFavorite(item)
                            },
                            onBack = viewModel::back,
                            onProgress = viewModel::onPlaybackProgress,
                            nextEpisodeTitle = nextEpisode?.let {
                                "الموسم ${it.season} • الحلقة ${it.episodeNumber} • ${it.title}"
                            },
                            onPlayNextEpisode = nextEpisode?.let { { viewModel.playNextEpisode() } },
                        )
                    }
                }

                else -> LaunchedEffect(state.screen) { viewModel.back() }
            }
        }
    }
}

@Composable
private fun KidsSourceGate(
    profile: UserProfile,
    isTv: Boolean,
    loading: Boolean,
    message: String?,
    onRetry: () -> Unit,
    onSwitchProfile: () -> Unit,
) {
    val colors = LocalHulkColors.current
    Box(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = if (isTv) 64.dp else 20.dp, vertical = if (isTv) 42.dp else 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(.92f)
                .clip(RoundedCornerShape(if (isTv) 26.dp else 20.dp))
                .background(colors.surface.copy(alpha = .97f))
                .border(1.dp, colors.gold.copy(alpha = .28f), RoundedCornerShape(if (isTv) 26.dp else 20.dp))
                .padding(horizontal = if (isTv) 42.dp else 24.dp, vertical = if (isTv) 34.dp else 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProfileAvatarArtwork(profile.avatarKey, profile.displayName, if (isTv) 82.dp else 68.dp, true)
            Spacer(Modifier.height(14.dp))
            Text(
                if (loading) "جار تجهيز مساحة الأطفال" else "مساحة الأطفال مقفلة بأمان",
                color = colors.text,
                fontSize = if (isTv) 28.sp else 22.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (loading) {
                    "نتحقق الآن من فئات الأطفال الموثّقة على السيرفر. لن نعرض أي محتوى عام أثناء التحقق."
                } else {
                    message ?: "لم نتمكن من اعتماد مصدر الأطفال. لن يتم عرض محتوى غير موثّق."
                },
                color = colors.textMuted,
                fontSize = if (isTv) 14.sp else 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(if (isTv) 24.dp else 18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!loading) KidsTextButton("إعادة التحقق", isTv, primary = true, onClick = onRetry)
                KidsTextButton("تغيير المستخدم", isTv, primary = false, onClick = onSwitchProfile)
            }
        }
    }
}

private data class KidsNavEntry(
    val section: KidsSection,
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun KidsMainScreen(
    profile: UserProfile,
    snapshot: VerifiedKidsCatalogSnapshot,
    isTv: Boolean,
    onOpen: (ContentItem) -> Unit,
    onSwitchProfile: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val sections = remember(snapshot) { availableKidsSections(snapshot) }
    var selected by remember(snapshot) { mutableStateOf(KidsSection.HOME) }
    var query by remember { mutableStateOf("") }
    var categoryId by remember(selected) { mutableStateOf<String?>(null) }
    if (selected !in sections) selected = KidsSection.HOME

    val entries = remember(sections) {
        sections.map { section ->
            when (section) {
                KidsSection.HOME -> KidsNavEntry(section, "الرئيسية", Icons.Rounded.Home)
                KidsSection.LIVE -> KidsNavEntry(section, "البث", Icons.Rounded.LiveTv)
                KidsSection.MOVIES -> KidsNavEntry(section, "الأفلام", Icons.Rounded.Movie)
                KidsSection.SERIES -> KidsNavEntry(section, "المسلسلات", Icons.Rounded.Tv)
                KidsSection.SEARCH -> KidsNavEntry(section, "البحث", Icons.Rounded.Search)
            }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(colors.goldDeep.copy(alpha = .11f), colors.background),
                    radius = if (isTv) 1250f else 780f,
                ),
            )
            .safeDrawingPadding(),
    ) {
        val wide = isTv || maxWidth >= 840.dp
        val contentPadding = when {
            isTv -> 26.dp
            maxWidth >= 600.dp -> 22.dp
            else -> 14.dp
        }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                KidsSideRail(
                    entries = entries,
                    selected = selected,
                    isTv = isTv,
                    onSelect = { selected = it; query = ""; categoryId = null },
                    onSwitchProfile = onSwitchProfile,
                )
                KidsContentPane(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(contentPadding),
                    profile = profile,
                    snapshot = snapshot,
                    selected = selected,
                    query = query,
                    categoryId = categoryId,
                    isTv = isTv,
                    onQueryChange = { query = it },
                    onCategoryChange = { categoryId = it },
                    onOpen = onOpen,
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                KidsContentPane(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(contentPadding),
                    profile = profile,
                    snapshot = snapshot,
                    selected = selected,
                    query = query,
                    categoryId = categoryId,
                    isTv = false,
                    onQueryChange = { query = it },
                    onCategoryChange = { categoryId = it },
                    onOpen = onOpen,
                )
                KidsBottomBar(
                    entries = entries,
                    selected = selected,
                    onSelect = { selected = it; query = ""; categoryId = null },
                    onSwitchProfile = onSwitchProfile,
                )
            }
        }
    }
}

@Composable
private fun KidsContentPane(
    modifier: Modifier,
    profile: UserProfile,
    snapshot: VerifiedKidsCatalogSnapshot,
    selected: KidsSection,
    query: String,
    categoryId: String?,
    isTv: Boolean,
    onQueryChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onOpen: (ContentItem) -> Unit,
) {
    val colors = LocalHulkColors.current
    val selectedType = when (selected) {
        KidsSection.LIVE -> ContentType.LIVE
        KidsSection.MOVIES -> ContentType.MOVIE
        KidsSection.SERIES -> ContentType.SERIES
        else -> null
    }
    val categories = selectedType?.let { snapshot.catalog(it).categories }.orEmpty()
    val items = remember(snapshot, selected, categoryId, query) {
        kidsItemsForSection(snapshot, selected, categoryId, query)
    }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    when (selected) {
                        KidsSection.HOME -> "أهلًا ${profile.displayName}"
                        KidsSection.LIVE -> "قنوات الأطفال"
                        KidsSection.MOVIES -> "أفلام الأطفال"
                        KidsSection.SERIES -> "مسلسلات الأطفال"
                        KidsSection.SEARCH -> "ابحث في مساحة الأطفال"
                    },
                    color = colors.text,
                    fontSize = if (isTv) 29.sp else 22.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "محتوى موثّق من فئات الأطفال على السيرفر فقط",
                    color = colors.goldBright,
                    fontSize = if (isTv) 12.sp else 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            ProfileAvatarArtwork(profile.avatarKey, profile.displayName, if (isTv) 54.dp else 44.dp, true)
        }

        Spacer(Modifier.height(if (isTv) 18.dp else 13.dp))

        if (selected == KidsSection.SEARCH) {
            HulkTextField(
                value = query,
                onValueChange = onQueryChange,
                label = "ابحث عن فيلم أو مسلسل أو قناة أطفال",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(if (isTv) 16.dp else 12.dp))
        }

        if (categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    KidsCategoryChip("الكل", categoryId == null, isTv) { onCategoryChange(null) }
                }
                items(categories, key = { it.id }) { category ->
                    KidsCategoryChip(category.name, categoryId == category.id, isTv) {
                        onCategoryChange(category.id)
                    }
                }
            }
            Spacer(Modifier.height(if (isTv) 16.dp else 12.dp))
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (selected == KidsSection.SEARCH && query.isBlank()) "اكتب كلمة للبحث داخل محتوى الأطفال فقط" else "لا توجد عناصر مطابقة",
                    color = colors.textMuted,
                    fontSize = if (isTv) 16.sp else 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if (isTv) 150.dp else 128.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (isTv) 20.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
                verticalArrangement = Arrangement.spacedBy(if (isTv) 16.dp else 12.dp),
            ) {
                items(items, key = { "${it.type.name}:${it.id}" }) { item ->
                    KidsContentCard(item = item, isTv = isTv, onClick = { onOpen(item) })
                }
            }
        }
    }
}

@Composable
private fun KidsContentCard(
    item: ContentItem,
    isTv: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember(item.id, item.type) { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused && isTv) 1.045f else 1f, label = "kidsCardScale")
    val shape = RoundedCornerShape(if (isTv) 16.dp else 13.dp)
    Column(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (focused && isTv) 18.dp.toPx() else 0f
            }
            .clip(shape)
            .background(colors.surface.copy(alpha = .96f))
            .border(if (focused) 2.dp else 1.dp, if (focused) colors.goldBright else Color.White.copy(alpha = .09f), shape)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val select = event.key == Key.Enter || event.key == Key.DirectionCenter
                if (!isTv || !select) false else when (event.type) {
                    KeyEventType.KeyDown -> true
                    KeyEventType.KeyUp -> { onClick(); true }
                    else -> false
                }
            }
            .clickable(onClick = onClick)
            .focusable()
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (isTv) 188.dp else 164.dp)
                .clip(RoundedCornerShape(if (isTv) 12.dp else 10.dp))
                .background(colors.surfaceRaised),
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = .70f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text(
                    when (item.type) {
                        ContentType.LIVE -> "قناة"
                        ContentType.MOVIE -> "فيلم"
                        ContentType.SERIES -> "مسلسل"
                    },
                    color = colors.goldBright,
                    fontSize = if (isTv) 10.sp else 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.name,
            color = if (focused) colors.goldBright else colors.text,
            fontSize = if (isTv) 14.sp else 12.sp,
            lineHeight = if (isTv) 18.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun KidsCategoryChip(
    text: String,
    selected: Boolean,
    isTv: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember(text) { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.gold else if (focused) colors.gold.copy(alpha = .16f) else colors.surfaceRaised)
            .border(if (focused) 2.dp else 1.dp, if (focused) colors.goldBright else Color.White.copy(alpha = .08f), RoundedCornerShape(50))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = if (isTv) 16.dp else 13.dp, vertical = if (isTv) 9.dp else 8.dp),
    ) {
        Text(
            text,
            color = if (selected) Color.Black else if (focused) colors.goldBright else colors.text,
            fontSize = if (isTv) 12.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun KidsSideRail(
    entries: List<KidsNavEntry>,
    selected: KidsSection,
    isTv: Boolean,
    onSelect: (KidsSection) -> Unit,
    onSwitchProfile: () -> Unit,
) {
    val colors = LocalHulkColors.current
    Column(
        Modifier
            .width(if (isTv) 112.dp else 104.dp)
            .fillMaxHeight()
            .background(Color(0xF6090A07))
            .padding(horizontal = 10.dp, vertical = if (isTv) 28.dp else 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("KIDS", color = colors.goldBright, fontSize = if (isTv) 15.sp else 13.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(if (isTv) 24.dp else 16.dp))
        entries.forEach { entry ->
            KidsNavButton(entry, selected == entry.section, isTv) { onSelect(entry.section) }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.weight(1f))
        KidsNavButton(KidsNavEntry(KidsSection.HOME, "تغيير", Icons.Rounded.Person), false, isTv, onSwitchProfile)
    }
}

@Composable
private fun KidsBottomBar(
    entries: List<KidsNavEntry>,
    selected: KidsSection,
    onSelect: (KidsSection) -> Unit,
    onSwitchProfile: () -> Unit,
) {
    val colors = LocalHulkColors.current
    LazyRow(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF090A07))
            .navigationBarsPadding()
            .padding(vertical = 5.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(entries, key = { it.section.name }) { entry ->
            KidsNavButton(entry, selected == entry.section, false) { onSelect(entry.section) }
        }
        item(key = "switch-profile") {
            KidsNavButton(KidsNavEntry(KidsSection.HOME, "تغيير", Icons.Rounded.Person), false, false, onSwitchProfile)
        }
    }
}

@Composable
private fun KidsNavButton(
    entry: KidsNavEntry,
    selected: Boolean,
    isTv: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember(entry.label) { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused && isTv) 1.06f else 1f, label = "kidsNavScale")
    val shape = RoundedCornerShape(if (isTv) 14.dp else 12.dp)
    Column(
        Modifier
            .width(if (isTv) 88.dp else 78.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (selected) colors.gold.copy(alpha = .16f) else if (focused) colors.surfaceRaised else Color.Transparent)
            .border(if (focused) 2.dp else 1.dp, if (focused) colors.goldBright else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(vertical = if (isTv) 10.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            entry.icon,
            contentDescription = entry.label,
            tint = if (selected || focused) colors.goldBright else colors.textMuted,
            modifier = Modifier.size(if (isTv) 24.dp else 21.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            entry.label,
            color = if (selected || focused) colors.text else colors.textMuted,
            fontSize = if (isTv) 11.sp else 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun KidsTextButton(
    text: String,
    isTv: Boolean,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember(text) { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(if (isTv) 14.dp else 12.dp))
            .background(if (primary) colors.gold else if (focused) colors.gold.copy(alpha = .15f) else colors.surfaceRaised)
            .border(if (focused) 2.dp else 1.dp, if (focused) colors.goldBright else Color.White.copy(alpha = .08f), RoundedCornerShape(if (isTv) 14.dp else 12.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = if (isTv) 19.dp else 15.dp, vertical = if (isTv) 11.dp else 9.dp),
    ) {
        Text(
            text,
            color = if (primary) Color.Black else colors.text,
            fontSize = if (isTv) 13.sp else 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
