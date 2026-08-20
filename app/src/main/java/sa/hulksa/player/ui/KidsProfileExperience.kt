package sa.hulksa.player.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.ui.adaptive.ApplyAdaptiveWindowPresentation
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.adaptive.rememberAdaptiveUiState
import sa.hulksa.player.ui.adaptive.trackAdaptiveInput
import sa.hulksa.player.ui.components.HulkTextField
import sa.hulksa.player.ui.screens.KidsMobileMovieDetailsScreen
import sa.hulksa.player.ui.screens.KidsMobileSeriesDetailsScreen
import sa.hulksa.player.ui.screens.MovieDetailsScreen
import sa.hulksa.player.ui.screens.NotificationBellButton
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
    val context = LocalContext.current

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
    var selectedKidsSectionName by rememberSaveable(profile.id) {
        mutableStateOf(KidsSection.HOME.name)
    }
    val selectedKidsSection = KidsSection.entries
        .firstOrNull { it.name == selectedKidsSectionName }
        ?: KidsSection.HOME

    BackHandler(
        enabled = state.screen == HulkScreen.MOVIE_DETAILS || state.screen == HulkScreen.SERIES,
        onBack = viewModel::back,
    )

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
                    onRetry = onRetrySource,
                    onSwitchProfile = onSwitchProfile,
                )

                state.screen == HulkScreen.MAIN -> KidsMainScreen(
                    profile = profile,
                    snapshot = safeSnapshot,
                    isTv = isTv,
                    selectedSection = selectedKidsSection,
                    onSelectedSectionChange = { selectedKidsSectionName = it.name },
                    onOpen = safeOpen,
                    unreadNotificationCount = state.unreadNotificationCount,
                    onOpenNotifications = viewModel::openNotificationCenter,
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
                        if (isTv) {
                            MovieDetailsScreen(
                                item = item,
                                details = state.selectedDetails,
                                isLoading = state.isLoading,
                                errorMessage = state.errorMessage,
                                isTv = true,
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
                        } else {
                            KidsMobileMovieDetailsScreen(
                                item = item,
                                details = state.selectedDetails,
                                isLoading = state.isLoading,
                                errorMessage = state.errorMessage,
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
                        if (isTv) {
                            SeriesDetailsScreenV2(
                                series = series,
                                details = state.selectedDetails,
                                episodes = state.episodes,
                                isLoading = state.isLoading,
                                errorMessage = state.errorMessage,
                                isTv = true,
                                isFavorite = viewModel.isFavorite(series),
                                notificationsEnabled = viewModel.isSeriesNotificationsEnabled(series),
                                notificationToggleAvailable = !state.isLoading && state.errorMessage == null,
                                targetEpisodeId = state.seriesEpisodeTarget
                                    ?.takeIf { it.seriesId == series.id }
                                    ?.episodeId,
                                targetSeason = state.seriesEpisodeTarget
                                    ?.takeIf { it.seriesId == series.id }
                                    ?.seasonNumber,
                                targetEpisodeNumber = state.seriesEpisodeTarget
                                    ?.takeIf { it.seriesId == series.id }
                                    ?.episodeNumber,
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
                                onToggleNotifications = {
                                    viewModel.toggleSeriesNotifications(series) { message ->
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onToggleRelatedFavorite = viewModel::toggleFavorite,
                                onOpenRelated = safeOpen,
                            )
                        } else {
                            KidsMobileSeriesDetailsScreen(
                                series = series,
                                details = state.selectedDetails,
                                episodes = state.episodes,
                                isLoading = state.isLoading,
                                errorMessage = state.errorMessage,
                                isFavorite = viewModel.isFavorite(series),
                                notificationsEnabled = viewModel.isSeriesNotificationsEnabled(series),
                                notificationToggleAvailable = !state.isLoading && state.errorMessage == null,
                                targetEpisodeId = state.seriesEpisodeTarget
                                    ?.takeIf { it.seriesId == series.id }
                                    ?.episodeId,
                                targetSeason = state.seriesEpisodeTarget
                                    ?.takeIf { it.seriesId == series.id }
                                    ?.seasonNumber,
                                targetEpisodeNumber = state.seriesEpisodeTarget
                                    ?.takeIf { it.seriesId == series.id }
                                    ?.episodeNumber,
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
                                onToggleNotifications = {
                                    viewModel.toggleSeriesNotifications(series) { message ->
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onToggleRelatedFavorite = viewModel::toggleFavorite,
                                onOpenRelated = safeOpen,
                            )
                        }
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
    onRetry: () -> Unit,
    onSwitchProfile: () -> Unit,
) {
    val colors = LocalHulkColors.current
    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (isTv) {
                    Modifier.safeDrawingPadding()
                } else {
                    Modifier.statusBarsPadding().navigationBarsPadding()
                },
            )
            .padding(horizontal = if (isTv) 48.dp else 14.dp, vertical = if (isTv) 32.dp else 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = if (isTv) 620.dp else 480.dp)
                .clip(RoundedCornerShape(if (isTv) 24.dp else 20.dp))
                .background(colors.surface.copy(alpha = .97f))
                .border(1.dp, colors.gold.copy(alpha = .24f), RoundedCornerShape(if (isTv) 24.dp else 20.dp))
                .padding(horizontal = if (isTv) 38.dp else 22.dp, vertical = if (isTv) 30.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProfileAvatarArtwork(profile.avatarKey, profile.displayName, if (isTv) 78.dp else 64.dp, true)
            Spacer(Modifier.height(14.dp))
            Text(
                if (loading) "جار تجهيز مساحة الأطفال" else "تعذر فتح مساحة الأطفال",
                color = colors.text,
                fontSize = if (isTv) 27.sp else 22.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                if (loading) "قد يستغرق ذلك لحظات." else "حاول مرة أخرى، أو اختر ملفًا شخصيًا آخر.",
                color = colors.textMuted,
                fontSize = if (isTv) 14.sp else 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(if (isTv) 22.dp else 18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!loading) KidsTextButton("إعادة المحاولة", isTv, primary = true, onClick = onRetry)
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
    selectedSection: KidsSection,
    onSelectedSectionChange: (KidsSection) -> Unit,
    onOpen: (ContentItem) -> Unit,
    unreadNotificationCount: Int,
    onOpenNotifications: () -> Unit,
    onSwitchProfile: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val sections = remember(snapshot) { availableKidsSections(snapshot) }
    val selected = selectedSection.takeIf { it in sections } ?: KidsSection.HOME
    var query by remember(selected) { mutableStateOf("") }
    var categoryId by remember(selected) { mutableStateOf<String?>(null) }
    LaunchedEffect(sections, selectedSection) {
        if (selectedSection !in sections) onSelectedSectionChange(KidsSection.HOME)
    }

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
                    listOf(colors.goldDeep.copy(alpha = .10f), colors.background),
                    radius = if (isTv) 1260f else 780f,
                ),
            )
            .then(
                if (isTv) {
                    Modifier.safeDrawingPadding()
                } else {
                    Modifier.statusBarsPadding().navigationBarsPadding()
                },
            ),
    ) {
        val compactTv = isTv && (maxWidth <= 960.dp || maxHeight <= 540.dp)
        val largeTv = isTv && maxWidth >= 1600.dp && maxHeight >= 900.dp
        val phoneLandscape = !isTv && maxWidth > maxHeight
        val wide = isTv || maxWidth >= 840.dp
        val edgePadding = when {
            compactTv -> 8.dp
            largeTv -> 18.dp
            isTv -> 12.dp
            else -> 0.dp
        }
        val contentPadding = when {
            compactTv -> 12.dp
            largeTv -> 22.dp
            isTv -> 16.dp
            maxWidth >= 600.dp -> 16.dp
            phoneLandscape -> 10.dp
            else -> 10.dp
        }
        val railGap = when {
            compactTv -> 9.dp
            largeTv -> 16.dp
            isTv -> 12.dp
            else -> 10.dp
        }

        if (wide) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = edgePadding, vertical = if (isTv) 8.dp else 0.dp),
            ) {
                KidsSideRail(
                    profile = profile,
                    entries = entries,
                    selected = selected,
                    isTv = isTv,
                    onSelect = { onSelectedSectionChange(it); query = ""; categoryId = null },
                    onSwitchProfile = onSwitchProfile,
                )
                Spacer(Modifier.width(railGap))
                KidsContentPane(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(contentPadding),
                    profile = profile,
                    snapshot = snapshot,
                    selected = selected,
                    query = query,
                    categoryId = categoryId,
                    isTv = isTv,
                    showProfileAvatar = false,
                    onQueryChange = { query = it },
                    onCategoryChange = { categoryId = it },
                    onOpen = onOpen,
                    unreadNotificationCount = unreadNotificationCount,
                    onOpenNotifications = onOpenNotifications,
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
                    showProfileAvatar = true,
                    onQueryChange = { query = it },
                    onCategoryChange = { categoryId = it },
                    onOpen = onOpen,
                    unreadNotificationCount = unreadNotificationCount,
                    onOpenNotifications = onOpenNotifications,
                )
                KidsBottomBar(
                    entries = entries,
                    selected = selected,
                    onSelect = { onSelectedSectionChange(it); query = ""; categoryId = null },
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
    showProfileAvatar: Boolean,
    onQueryChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onOpen: (ContentItem) -> Unit,
    unreadNotificationCount: Int,
    onOpenNotifications: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val compactTv = isTv && (adaptiveUi.screenWidthDp <= 960 || adaptiveUi.screenHeightDp <= 540)
    val largeTv = isTv && adaptiveUi.screenWidthDp >= 1600 && adaptiveUi.screenHeightDp >= 900
    val phoneLandscape = !isTv && adaptiveUi.screenWidthDp > adaptiveUi.screenHeightDp
    val tablet = !isTv && adaptiveUi.screenWidthDp >= 600
    val titleSize = when {
        compactTv -> 26.sp
        largeTv -> 34.sp
        isTv -> 30.sp
        tablet -> 26.sp
        phoneLandscape -> 20.sp
        else -> 22.sp
    }
    val headerAvatarSize = when {
        tablet -> 48.dp
        phoneLandscape -> 40.dp
        else -> 44.dp
    }
    val gridMinSize = when {
        compactTv -> 132.dp
        largeTv -> 170.dp
        isTv -> 154.dp
        tablet -> 138.dp
        phoneLandscape -> 112.dp
        else -> 116.dp
    }
    val gridHorizontalGap = when {
        compactTv -> 10.dp
        largeTv -> 16.dp
        isTv -> 13.dp
        else -> 8.dp
    }
    val gridVerticalGap = when {
        compactTv -> 11.dp
        largeTv -> 17.dp
        isTv -> 14.dp
        phoneLandscape -> 9.dp
        else -> 11.dp
    }
    val sectionGap = when {
        compactTv -> 12.dp
        isTv -> 16.dp
        phoneLandscape -> 8.dp
        else -> 12.dp
    }
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
    val title = when (selected) {
        KidsSection.HOME -> "أهلًا ${profile.displayName}"
        KidsSection.LIVE -> "قنوات الأطفال"
        KidsSection.MOVIES -> "أفلام الأطفال"
        KidsSection.SERIES -> "مسلسلات الأطفال"
        KidsSection.SEARCH -> "البحث"
    }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (showProfileAvatar) Arrangement.SpaceBetween else Arrangement.Start,
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                color = colors.text,
                fontSize = titleSize,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected == KidsSection.HOME) {
                Spacer(Modifier.width(10.dp))
                NotificationBellButton(
                    unreadCount = unreadNotificationCount,
                    isTv = isTv,
                    onClick = onOpenNotifications,
                )
            }
            if (showProfileAvatar) {
                Spacer(Modifier.width(10.dp))
                ProfileAvatarArtwork(profile.avatarKey, profile.displayName, headerAvatarSize, true)
            }
        }

        Spacer(Modifier.height(sectionGap))

        if (selected == KidsSection.SEARCH) {
            HulkTextField(
                value = query,
                onValueChange = onQueryChange,
                label = "ابحث عن فيلم أو مسلسل أو قناة",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(if (compactTv) 11.dp else if (isTv) 15.dp else if (phoneLandscape) 8.dp else 11.dp))
        }

        if (categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 8.dp else 6.dp),
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
            Spacer(Modifier.height(if (compactTv) 11.dp else if (isTv) 15.dp else if (phoneLandscape) 8.dp else 11.dp))
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (selected == KidsSection.SEARCH && query.isBlank()) {
                        "اكتب اسم الفيلم أو المسلسل أو القناة"
                    } else {
                        "لا توجد نتائج"
                    },
                    color = colors.textMuted,
                    fontSize = if (isTv) 16.sp else 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = gridMinSize),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (largeTv) 28.dp else if (isTv) 22.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(gridHorizontalGap),
                verticalArrangement = Arrangement.spacedBy(gridVerticalGap),
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
    val adaptiveUi = LocalAdaptiveUi.current
    val compactTv = isTv && (adaptiveUi.screenWidthDp <= 960 || adaptiveUi.screenHeightDp <= 540)
    val largeTv = isTv && adaptiveUi.screenWidthDp >= 1600 && adaptiveUi.screenHeightDp >= 900
    val phoneLandscape = !isTv && adaptiveUi.screenWidthDp > adaptiveUi.screenHeightDp
    val tablet = !isTv && adaptiveUi.screenWidthDp >= 600
    val posterHeight = when {
        compactTv -> 150.dp
        largeTv -> 206.dp
        isTv -> 180.dp
        tablet -> 176.dp
        phoneLandscape -> 132.dp
        else -> 158.dp
    }
    val cardPadding = when {
        compactTv -> 6.dp
        largeTv -> 8.dp
        isTv -> 7.dp
        else -> 6.dp
    }
    val titleSize = when {
        compactTv -> 12.sp
        largeTv -> 15.sp
        isTv -> 14.sp
        tablet -> 13.sp
        phoneLandscape -> 11.sp
        else -> 12.sp
    }
    var focused by remember(item.id, item.type) { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (isTv) 15.dp else 13.dp)
    Column(
        Modifier
            .clip(shape)
            .background(colors.surface.copy(alpha = .96f))
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) colors.goldBright else Color.White.copy(alpha = .09f),
                shape,
            )
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
            .padding(cardPadding),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(posterHeight)
                .clip(RoundedCornerShape(if (isTv) 11.dp else 10.dp))
                .background(colors.surfaceRaised),
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = if (item.type == ContentType.LIVE) ContentScale.Fit else ContentScale.Crop,
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(if (compactTv || phoneLandscape) 5.dp else 6.dp)
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
                    fontSize = if (largeTv) 11.sp else if (isTv) 10.sp else 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(if (phoneLandscape) 5.dp else 7.dp))
        Text(
            item.name,
            color = if (focused) colors.goldBright else colors.text,
            fontSize = titleSize,
            lineHeight = (titleSize.value + 4f).sp,
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
    val adaptiveUi = LocalAdaptiveUi.current
    val compactTv = isTv && (adaptiveUi.screenWidthDp <= 960 || adaptiveUi.screenHeightDp <= 540)
    val phoneLandscape = !isTv && adaptiveUi.screenWidthDp > adaptiveUi.screenHeightDp
    var focused by remember(text) { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.gold else if (focused) colors.gold.copy(alpha = .15f) else colors.surfaceRaised)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) colors.goldBright else Color.White.copy(alpha = .08f),
                RoundedCornerShape(50),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(
                horizontal = if (compactTv) 12.dp else if (isTv) 15.dp else if (phoneLandscape) 10.dp else 13.dp,
                vertical = if (compactTv || phoneLandscape) 7.dp else 8.dp,
            ),
    ) {
        Text(
            text,
            color = if (selected) Color.Black else if (focused) colors.goldBright else colors.text,
            fontSize = if (compactTv) 11.sp else if (isTv) 12.sp else if (phoneLandscape) 10.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun KidsSideRail(
    profile: UserProfile,
    entries: List<KidsNavEntry>,
    selected: KidsSection,
    isTv: Boolean,
    onSelect: (KidsSection) -> Unit,
    onSwitchProfile: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val compactTv = isTv && (adaptiveUi.screenWidthDp <= 960 || adaptiveUi.screenHeightDp <= 540)
    val largeTv = isTv && adaptiveUi.screenWidthDp >= 1600 && adaptiveUi.screenHeightDp >= 900
    val railWidth = when {
        compactTv -> 112.dp
        largeTv -> 140.dp
        isTv -> 126.dp
        else -> 114.dp
    }
    val avatarSize = when {
        compactTv -> 40.dp
        largeTv -> 52.dp
        isTv -> 46.dp
        else -> 42.dp
    }
    val railShape = RoundedCornerShape(if (isTv) 20.dp else 17.dp)
    Column(
        Modifier
            .width(railWidth)
            .fillMaxHeight()
            .clip(railShape)
            .background(Color(0xF6090A07))
            .border(1.dp, Color.White.copy(alpha = .06f), railShape)
            .padding(
                horizontal = if (compactTv) 7.dp else if (isTv) 9.dp else 8.dp,
                vertical = if (compactTv) 11.dp else if (largeTv) 18.dp else if (isTv) 16.dp else 14.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProfileAvatarArtwork(profile.avatarKey, profile.displayName, avatarSize, true)
        Spacer(Modifier.height(5.dp))
        Text(
            "KIDS",
            color = colors.goldBright,
            fontSize = if (compactTv) 11.sp else if (largeTv) 14.sp else if (isTv) 13.sp else 12.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(if (compactTv) 8.dp else if (isTv) 13.dp else 10.dp))
        entries.forEach { entry ->
            KidsNavButton(
                entry = entry,
                selected = selected == entry.section,
                isTv = isTv,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSelect(entry.section) },
            )
            Spacer(Modifier.height(if (compactTv) 2.dp else if (isTv) 4.dp else 3.dp))
        }
        Spacer(Modifier.weight(1f))
        KidsNavButton(
            entry = KidsNavEntry(KidsSection.HOME, "تغيير المستخدم", Icons.Rounded.Person),
            selected = false,
            isTv = isTv,
            modifier = Modifier.fillMaxWidth(),
            onClick = onSwitchProfile,
        )
    }
}

@Composable
private fun KidsBottomBar(
    entries: List<KidsNavEntry>,
    selected: KidsSection,
    onSelect: (KidsSection) -> Unit,
    onSwitchProfile: () -> Unit,
) {
    LazyRow(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF090A07))
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(entries, key = { it.section.name }) { entry ->
            KidsNavButton(entry, selected == entry.section, false) { onSelect(entry.section) }
        }
        item(key = "switch-profile") {
            KidsNavButton(
                KidsNavEntry(KidsSection.HOME, "تغيير المستخدم", Icons.Rounded.Person),
                false,
                false,
                onClick = onSwitchProfile,
            )
        }
    }
}

@Composable
private fun KidsNavButton(
    entry: KidsNavEntry,
    selected: Boolean,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val compactTv = isTv && (adaptiveUi.screenWidthDp <= 960 || adaptiveUi.screenHeightDp <= 540)
    val largeTv = isTv && adaptiveUi.screenWidthDp >= 1600 && adaptiveUi.screenHeightDp >= 900
    val phoneLandscape = !isTv && adaptiveUi.screenWidthDp > adaptiveUi.screenHeightDp
    val phoneButtonWidth = when {
        adaptiveUi.screenWidthDp < 360 -> 62.dp
        phoneLandscape -> 64.dp
        else -> 68.dp
    }
    val minHeight = when {
        compactTv -> 48.dp
        largeTv -> 58.dp
        isTv -> 54.dp
        phoneLandscape -> 46.dp
        else -> 50.dp
    }
    val iconSize = when {
        compactTv -> 19.dp
        largeTv -> 24.dp
        isTv -> 22.dp
        phoneLandscape -> 18.dp
        else -> 19.dp
    }
    val labelSize = when {
        compactTv -> 9.sp
        largeTv -> 11.sp
        isTv -> 10.sp
        else -> 9.sp
    }
    var focused by remember(entry.label) { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (isTv) 13.dp else 12.dp)
    Column(
        modifier
            .then(if (isTv) Modifier else Modifier.width(phoneButtonWidth))
            .heightIn(min = minHeight)
            .clip(shape)
            .background(
                when {
                    selected -> colors.gold.copy(alpha = .18f)
                    focused -> colors.surfaceRaised
                    else -> Color.Transparent
                },
            )
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> colors.goldBright
                    selected -> colors.gold.copy(alpha = .34f)
                    else -> Color.Transparent
                },
                shape,
            )
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
            .padding(horizontal = 4.dp, vertical = if (compactTv) 5.dp else if (isTv) 7.dp else 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            entry.icon,
            contentDescription = entry.label,
            tint = if (selected || focused) colors.goldBright else colors.textMuted,
            modifier = Modifier.size(iconSize),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            entry.label,
            color = if (selected || focused) colors.text else colors.textMuted,
            fontSize = labelSize,
            lineHeight = (labelSize.value + 2f).sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
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
    val shape = RoundedCornerShape(if (isTv) 14.dp else 12.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (primary) colors.gold else if (focused) colors.gold.copy(alpha = .15f) else colors.surfaceRaised)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) colors.goldBright else Color.White.copy(alpha = .08f),
                shape,
            )
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
