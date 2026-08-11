package sa.hulksa.player.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import sa.hulksa.player.HulkScreen
import sa.hulksa.player.HulkViewModel
import sa.hulksa.player.MainDestination
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.ui.adaptive.ApplyAdaptiveWindowPresentation
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.adaptive.rememberAdaptiveUiState
import sa.hulksa.player.ui.adaptive.trackAdaptiveInput
import sa.hulksa.player.ui.screens.LoginScreen
import sa.hulksa.player.ui.screens.MainShellScreen
import sa.hulksa.player.ui.screens.MovieDetailsScreen
import sa.hulksa.player.ui.screens.NavigationMemoryStore
import sa.hulksa.player.ui.screens.PlayerScreen
import sa.hulksa.player.ui.screens.SeriesDetailsScreenV2
import sa.hulksa.player.ui.theme.LocalHulkColors

@Composable
fun HulkApp(
    viewModel: HulkViewModel,
    isTelevisionDevice: Boolean,
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
    val applySafeDrawingInsets =
        !isTv && state.screen != HulkScreen.PLAYER && state.screen != HulkScreen.LOGIN
    ApplyAdaptiveWindowPresentation(
        isTelevisionDevice = isTv,
        isPlayer = state.screen == HulkScreen.PLAYER,
    )
    val navigationMemory = remember { NavigationMemoryStore() }
    // Favorite state changes must update the star immediately, but they must not rebuild
    // Home recommendation membership/order while the user is focused on those rows.
    // Refresh recommendation inputs only when catalogs/history actually change.
    val homeRecommendationFavorites = remember(state.catalogs, state.history) { state.favorites }
    val notify: (String) -> Unit = { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
                            Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
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
                                onSelectDestination = viewModel::selectDestination,
                                onSearch = viewModel::updateSearch,
                                onOpen = viewModel::open,
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
                            MainShellScreen(
                                state = if (state.destination == MainDestination.HOME) {
                                    state.copy(favorites = homeRecommendationFavorites)
                                } else {
                                    state
                                },
                                isTv = isTv,
                                navigationMemory = navigationMemory,
                                isFavorite = viewModel::isFavorite,
                                onSelectDestination = viewModel::selectDestination,
                                onSelectCategory = viewModel::selectCategory,
                                onSearch = viewModel::updateSearch,
                                onOpen = viewModel::open,
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
                                onCycleDownloadPriority = { item -> notify(viewModel.cycleDownloadPriority(item)) },
                                onRunDiagnostics = viewModel::runDiagnostics,
                                onLogout = viewModel::logout,
                            )
                        }
                    }

                    HulkScreen.MOVIE_DETAILS -> {
                        val item = state.selectedItem
                        if (item != null) {
                            val relatedMovies = state.catalogs[ContentType.MOVIE]
                                ?.items
                                .orEmpty()
                                .asSequence()
                                .filter { it.id != item.id && it.categoryId == item.categoryId }
                                .take(10)
                                .toList()
                            val movieDownload = state.downloads.firstOrNull { it.historyKey == "MOVIE:${item.id}" }
                            MovieDetailsScreen(
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
                                    notify(if (movieDownload == null) viewModel.downloadSelectedMovie() else viewModel.retryDownload(movieDownload))
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
                                    notify(if (wasFavorite) "تمت ازالة ${related.name} من المفضلة" else "تمت اضافة ${related.name} الى المفضلة")
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
                            val relatedSeries = state.catalogs[ContentType.SERIES]
                                ?.items
                                .orEmpty()
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
                                relatedItems = relatedSeries,
                                isRelatedFavorite = viewModel::isFavorite,
                                onBack = viewModel::back,
                                onPlay = viewModel::playEpisode,
                                onDownload = { episode ->
                                    val existing = state.downloads.firstOrNull { it.historyKey == "SERIES:${episode.id}" }
                                    notify(if (existing == null) viewModel.downloadEpisode(episode) else viewModel.retryDownload(existing))
                                },
                                onCancelDownload = { episode ->
                                    state.downloads.firstOrNull { it.historyKey == "SERIES:${episode.id}" }?.let {
                                        viewModel.deleteDownload(it)
                                        notify("تم الغاء التحميل.")
                                    }
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(series) },
                                onToggleRelatedFavorite = { related ->
                                    val wasFavorite = viewModel.isFavorite(related)
                                    viewModel.toggleFavorite(related)
                                    notify(if (wasFavorite) "تمت ازالة ${related.name} من المفضلة" else "تمت اضافة ${related.name} الى المفضلة")
                                },
                                onOpenRelated = viewModel::open,
                            )
                        } else {
                            LaunchedEffect(state.screen) { viewModel.back() }
                        }
                    }

                    HulkScreen.PLAYER -> {
                        val playback = state.playback
                        if (playback != null) {
                            val orderedEpisodes = state.episodes.sortedWith(compareBy(sa.hulksa.player.model.Episode::season, sa.hulksa.player.model.Episode::episodeNumber))
                            val currentEpisodeIndex = orderedEpisodes.indexOfFirst { it.id == playback.streamId }
                            val nextEpisode = if (playback.streamKind == "series") orderedEpisodes.getOrNull(currentEpisodeIndex + 1) else null
                            PlayerScreen(
                                request = playback,
                                liveCatalog = state.catalogs[ContentType.LIVE],
                                isFavorite = viewModel::isFavorite,
                                onSelectLiveChannel = viewModel::switchLiveChannel,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onBack = viewModel::back,
                                onProgress = viewModel::onPlaybackProgress,
                                nextEpisodeTitle = nextEpisode?.let { "الموسم ${it.season} • الحلقة ${it.episodeNumber} • ${it.title}" },
                                onPlayNextEpisode = nextEpisode?.let { { viewModel.playNextEpisode() } },
                            )
                        } else {
                            LaunchedEffect(state.screen) { viewModel.back() }
                        }
                    }
                }
            }
        }
    }
}
