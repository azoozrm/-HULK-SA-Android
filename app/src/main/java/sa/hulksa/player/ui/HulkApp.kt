package sa.hulksa.player.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import sa.hulksa.player.HulkScreen
import sa.hulksa.player.HulkViewModel
import sa.hulksa.player.MainDestination
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.ui.adaptive.ApplyAdaptiveWindowPresentation
import sa.hulksa.player.ui.adaptive.HulkNavigationType
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.adaptive.rememberAdaptiveUiState
import sa.hulksa.player.ui.adaptive.trackAdaptiveInput
import sa.hulksa.player.ui.screens.LIVE_TV_PRO_CONTEXT_ALL
import sa.hulksa.player.ui.screens.LIVE_TV_PRO_CONTEXT_FAVORITES
import sa.hulksa.player.ui.screens.LoginScreen
import sa.hulksa.player.ui.screens.MainShellScreen
import sa.hulksa.player.ui.screens.MovieDetailsProPolishedScreen
import sa.hulksa.player.ui.screens.NavigationMemoryStore
import sa.hulksa.player.ui.screens.PlayerProScreen
import sa.hulksa.player.ui.screens.SeriesDetailsProPolishedScreen
import sa.hulksa.player.ui.screens.detailsProRelatedItems
import sa.hulksa.player.ui.screens.liveTvProDecorateMainState
import sa.hulksa.player.ui.screens.liveTvProMainCategoryToContext
import sa.hulksa.player.ui.screens.playerProEpisodeNeighbors
import sa.hulksa.player.ui.screens.saveLiveTvProLaunchContext
import sa.hulksa.player.ui.theme.LocalHulkColors

@Composable
fun HulkApp(
    viewModel: HulkViewModel,
    isTelevisionDevice: Boolean,
    navigationMemory: NavigationMemoryStore,
    catalogNavigationMemory: ProfileCatalogNavigationMemory,
) {
    val state by viewModel.state.collectAsState()
    val (adaptiveUi, adaptiveInputController) = rememberAdaptiveUiState(isTelevisionDevice)
    val isTv = adaptiveUi.isTelevision
    val context = LocalContext.current
    val colors = LocalHulkColors.current
    val requestProfileSwitch = LocalProfileSwitchRequester.current
    val windowBackground =
        if (state.screen == HulkScreen.LOGIN || state.screen == HulkScreen.PLAYER) {
            colors.background
        } else {
            colors.surface
        }
    val isPhoneHome =
        !isTv && state.screen == HulkScreen.MAIN && state.destination == MainDestination.HOME
    val applySafeDrawingInsets =
        !isTv && !isPhoneHome && state.screen != HulkScreen.PLAYER && state.screen != HulkScreen.LOGIN
    ApplyAdaptiveWindowPresentation(
        isTelevisionDevice = isTv,
        isPlayer = state.screen == HulkScreen.PLAYER,
    )
    // Favorite state changes must update the star immediately, but they must not rebuild
    // Home recommendation membership/order while the user is focused on those rows.
    // Refresh recommendation inputs only when catalogs/history actually change.
    val homeRecommendationFavorites = remember(state.catalogs, state.history) { state.favorites }
    val notify: (String) -> Unit = { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    val isTransientCatalogSearch: (MainDestination) -> Boolean = { destination ->
        destination == MainDestination.LIVE ||
            destination == MainDestination.MOVIES ||
            destination == MainDestination.SERIES
    }
    val openWithLiveContext: (ContentItem) -> Unit = { item ->
        if (item.type == ContentType.LIVE) {
            val launchContext = when (state.destination) {
                MainDestination.FAVORITES -> LIVE_TV_PRO_CONTEXT_FAVORITES
                MainDestination.LIVE -> liveTvProMainCategoryToContext(state.selectedCategoryId)
                else -> LIVE_TV_PRO_CONTEXT_ALL
            }
            context.saveLiveTvProLaunchContext(launchContext)
        }
        if (isTransientCatalogSearch(state.destination) && state.searchQuery.isNotBlank()) {
            catalogNavigationMemory.save(
                destination = state.destination,
                categoryId = state.selectedCategoryId,
                query = "",
            )
            viewModel.updateSearch("")
        }
        viewModel.open(item)
    }

    val selectDestinationWithProfileContext: (MainDestination) -> Unit = { destination ->
        val sourceUsesTransientSearch = isTransientCatalogSearch(state.destination)
        catalogNavigationMemory.save(
            destination = state.destination,
            categoryId = state.selectedCategoryId,
            query = if (sourceUsesTransientSearch) "" else state.searchQuery,
        )
        viewModel.selectDestination(destination)
        if (destination.isProfileCatalogDestination()) {
            viewModel.updateSearch(
                if (isTransientCatalogSearch(destination)) "" else catalogNavigationMemory.query(destination),
            )
            viewModel.selectCategory(catalogNavigationMemory.category(destination))
        } else if (sourceUsesTransientSearch && state.searchQuery.isNotBlank()) {
            viewModel.updateSearch("")
        }
    }
    val selectCategoryWithProfileContext: (String?) -> Unit = { categoryId ->
        val clearSearch = isTransientCatalogSearch(state.destination)
        catalogNavigationMemory.save(
            destination = state.destination,
            categoryId = categoryId,
            query = if (clearSearch) "" else state.searchQuery,
        )
        if (clearSearch && state.searchQuery.isNotBlank()) {
            viewModel.updateSearch("")
        }
        viewModel.selectCategory(categoryId)
    }
    val searchCatalogWithProfileContext: (String) -> Unit = { query ->
        catalogNavigationMemory.save(
            destination = state.destination,
            categoryId = state.selectedCategoryId,
            query = query,
        )
        viewModel.updateSearch(query)
    }

    BackHandler(
        enabled = state.screen == HulkScreen.MOVIE_DETAILS || state.screen == HulkScreen.SERIES,
        onBack = viewModel::back,
    )

    CompositionLocalProvider(LocalAdaptiveUi provides adaptiveUi) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(windowBackground)
                .trackAdaptiveInput(adaptiveInputController),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (applySafeDrawingInsets) {
                            if (state.screen == HulkScreen.MAIN) {
                                Modifier.windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical),
                                )
                            } else {
                                Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                when (state.screen) {
                    HulkScreen.LOGIN -> LoginScreen(
                        isTv = isTv,
                        isStarting = state.isStarting,
                        isLoading = state.isLoading,
                        errorMessage = state.errorMessage,
                        onLogin = viewModel::login,
                    )

                    HulkScreen.MAIN -> {
                        if (state.destination == MainDestination.SEARCH) {
                            ProfileSmartSearchLayer(
                                state = state,
                                isTv = isTv,
                                isFavorite = viewModel::isFavorite,
                                onSelectDestination = selectDestinationWithProfileContext,
                                onSearch = viewModel::updateSearch,
                                onOpen = openWithLiveContext,
                                onToggleFavorite = { item ->
                                    val wasFavorite = viewModel.isFavorite(item)
                                    viewModel.toggleFavorite(item)
                                    notify(
                                        if (wasFavorite) {
                                            "تمت ازالة ${item.name} من المفضلة"
                                        } else {
                                            "تمت اضافة ${item.name} الى المفضلة"
                                        },
                                    )
                                },
                                onSwitchProfile = requestProfileSwitch,
                            )
                        } else {
                            // Keep the shell and navigation rail alive while destinations change.
                            // Profile changes still replace catalogNavigationMemory and reset the
                            // shell-level state at the existing profile ownership boundary.
                            key(catalogNavigationMemory) {
                                MainShellScreen(
                                    state = when (state.destination) {
                                        MainDestination.HOME -> state.copy(favorites = homeRecommendationFavorites)
                                        MainDestination.LIVE -> liveTvProDecorateMainState(state, context)
                                        else -> state
                                    },
                                    isTv = isTv,
                                    navigationMemory = navigationMemory,
                                    isFavorite = viewModel::isFavorite,
                                    onSelectDestination = selectDestinationWithProfileContext,
                                    onSelectCategory = selectCategoryWithProfileContext,
                                    onSearch = searchCatalogWithProfileContext,
                                    onOpen = openWithLiveContext,
                                    onOpenHistory = { entry ->
                                        val localDownload = state.downloads.firstOrNull { download ->
                                            download.historyKey == entry.key &&
                                                download.status == OfflineStatus.COMPLETED &&
                                                !download.localUri.isNullOrBlank()
                                        }
                                        if (localDownload != null) {
                                            viewModel.playDownload(localDownload)
                                        } else {
                                            viewModel.openHistory(entry)
                                        }
                                    },
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onRefresh = viewModel::refresh,
                                    onOpenNotifications = viewModel::openNotificationCenter,
                                    onClearHistory = viewModel::clearHistory,
                                    onPlayDownload = viewModel::playDownload,
                                    onDeleteDownload = { item ->
                                        viewModel.deleteDownload(item)
                                        notify("تم حذف التحميل.")
                                    },
                                    onRetryDownload = { item -> notify(viewModel.retryDownload(item)) },
                                    onToggleWifiOnly = { notify(viewModel.toggleWifiOnly()) },
                                    onToggleDownloadSchedule = { notify(viewModel.toggleDownloadSchedule()) },
                                    onCycleConcurrentDownloads = { notify(viewModel.cycleConcurrentDownloads()) },
                                    onToggleEpisodeNotificationMaster = {
                                        viewModel.toggleEpisodeNotificationMaster(notify)
                                    },
                                    onCycleDownloadPriority = { item -> notify(viewModel.cycleDownloadPriority(item)) },
                                    onRefreshAccount = { viewModel.refreshAccount(notify) },
                                    onRunDiagnostics = viewModel::runDiagnostics,
                                    onLogout = viewModel::logout,
                                )
                            }
                        }
                    }

                    HulkScreen.MOVIE_DETAILS -> {
                        val item = state.selectedItem
                        if (item != null) {
                            val relatedMovies = detailsProRelatedItems(
                                source = item,
                                candidates = state.catalogs[ContentType.MOVIE]?.items.orEmpty(),
                            )
                            val movieDownload = state.downloads.firstOrNull { it.historyKey == "MOVIE:${item.id}" }
                            MovieDetailsProPolishedScreen(
                                item = item,
                                details = state.selectedDetails,
                                isLoading = state.isLoading,
                                errorMessage = state.errorMessage,
                                isTv = isTv,
                                isFavorite = viewModel.isFavorite(item),
                                download = movieDownload,
                                historyEntry = state.history.firstOrNull { it.key == "MOVIE:${item.id}" },
                                relatedItems = relatedMovies,
                                isRelatedFavorite = viewModel::isFavorite,
                                onBack = viewModel::back,
                                onPlay = viewModel::playSelectedMovie,
                                onDownload = {
                                    notify(
                                        if (movieDownload == null) {
                                            viewModel.downloadSelectedMovie()
                                        } else {
                                            viewModel.retryDownload(movieDownload)
                                        },
                                    )
                                },
                                onCancelDownload = {
                                    movieDownload?.let {
                                        viewModel.deleteDownload(it)
                                        notify("تم الغاء التحميل.")
                                    }
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(item) },
                                onToggleRelatedFavorite = { related ->
                                    val wasFavorite = viewModel.isFavorite(related)
                                    viewModel.toggleFavorite(related)
                                    notify(
                                        if (wasFavorite) {
                                            "تمت ازالة ${related.name} من المفضلة"
                                        } else {
                                            "تمت اضافة ${related.name} الى المفضلة"
                                        },
                                    )
                                },
                                onOpenRelated = viewModel::open,
                            )
                        } else {
                            LaunchedEffect(state.screen) { viewModel.back() }
                        }
                    }

                    HulkScreen.SERIES -> {
                        val series = state.selectedSeries
                        if (series != null) {
                            val relatedSeries = detailsProRelatedItems(
                                source = series,
                                candidates = state.catalogs[ContentType.SERIES]?.items.orEmpty(),
                            )
                            SeriesDetailsProPolishedScreen(
                                series = series,
                                details = state.selectedDetails,
                                episodes = state.episodes,
                                isLoading = state.isLoading,
                                errorMessage = state.errorMessage,
                                isTv = isTv,
                                isFavorite = viewModel.isFavorite(series),
                                notificationsEnabled = viewModel.isSeriesNotificationsEnabled(series),
                                notificationToggleAvailable =
                                    state.operations.features.episodeNotificationsEnabled &&
                                        !state.isLoading &&
                                        state.errorMessage == null,
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
                                relatedItems = relatedSeries,
                                isRelatedFavorite = viewModel::isFavorite,
                                onBack = viewModel::back,
                                onPlay = viewModel::playEpisode,
                                onDownload = { episode ->
                                    val existing = state.downloads.firstOrNull {
                                        it.historyKey == "SERIES:${episode.id}"
                                    }
                                    notify(
                                        if (existing == null) {
                                            viewModel.downloadEpisode(episode)
                                        } else {
                                            viewModel.retryDownload(existing)
                                        },
                                    )
                                },
                                onCancelDownload = { episode ->
                                    state.downloads.firstOrNull {
                                        it.historyKey == "SERIES:${episode.id}"
                                    }?.let {
                                        viewModel.deleteDownload(it)
                                        notify("تم الغاء التحميل.")
                                    }
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(series) },
                                onToggleNotifications = {
                                    viewModel.toggleSeriesNotifications(series, notify)
                                },
                                onToggleRelatedFavorite = { related ->
                                    val wasFavorite = viewModel.isFavorite(related)
                                    viewModel.toggleFavorite(related)
                                    notify(
                                        if (wasFavorite) {
                                            "تمت ازالة ${related.name} من المفضلة"
                                        } else {
                                            "تمت اضافة ${related.name} الى المفضلة"
                                        },
                                    )
                                },
                                onOpenRelated = viewModel::open,
                            )
                        } else {
                            LaunchedEffect(state.screen) { viewModel.back() }
                        }
                    }

                    HulkScreen.NOTIFICATION_CENTER -> Unit

                    HulkScreen.PLAYER -> {
                        val playback = state.playback
                        if (playback != null) {
                            val episodeNeighbors = playerProEpisodeNeighbors(
                                episodes = if (playback.streamKind == "series") state.episodes else emptyList(),
                                currentStreamId = playback.streamId,
                            )
                            PlayerProScreen(
                                request = playback,
                                liveTvProEnabled = state.operations.features.liveTvProEnabled,
                                liveCatalog = state.catalogs[ContentType.LIVE],
                                isFavorite = viewModel::isFavorite,
                                onSelectLiveChannel = viewModel::switchLiveChannel,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onBack = viewModel::back,
                                onProgress = viewModel::onPlaybackProgress,
                                previousEpisode = episodeNeighbors.previous,
                                nextEpisode = episodeNeighbors.next,
                                onPlayPreviousEpisode = episodeNeighbors.previous?.let { episode ->
                                    { viewModel.playEpisode(episode) }
                                },
                                onPlayNextEpisode = episodeNeighbors.next?.let { episode ->
                                    { viewModel.playEpisode(episode) }
                                },
                            )
                        } else {
                            LaunchedEffect(state.screen) { viewModel.back() }
                        }
                    }
                }

                if (
                    state.screen == HulkScreen.MAIN &&
                    !isTv &&
                    adaptiveUi.navigationType != HulkNavigationType.RAIL
                ) {
                    StableMobileBottomNavigation(
                        selected = state.destination,
                        downloadsEnabled = state.operations.features.downloadsEnabled,
                        onSelectDestination = selectDestinationWithProfileContext,
                        onSwitchProfile = requestProfileSwitch,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}
