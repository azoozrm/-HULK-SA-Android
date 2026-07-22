package sa.hulksa.player.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import sa.hulksa.player.HulkScreen
import sa.hulksa.player.HulkViewModel
import sa.hulksa.player.ui.screens.LoginScreen
import sa.hulksa.player.ui.screens.MainShellScreen
import sa.hulksa.player.ui.screens.MovieDetailsScreen
import sa.hulksa.player.ui.screens.PlayerScreen
import sa.hulksa.player.ui.screens.SeriesScreen

@Composable
fun HulkApp(
    viewModel: HulkViewModel,
    isTv: Boolean,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val notify: (String) -> Unit = { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    BackHandler(
        enabled = state.screen == HulkScreen.MOVIE_DETAILS || state.screen == HulkScreen.SERIES,
        onBack = viewModel::back,
    )

    when (state.screen) {
        HulkScreen.LOGIN -> LoginScreen(
            isTv = isTv,
            isStarting = state.isStarting,
            isLoading = state.isLoading,
            errorMessage = state.errorMessage,
            onLogin = viewModel::login,
        )

        HulkScreen.MAIN -> MainShellScreen(
            state = state,
            isTv = isTv,
            isFavorite = viewModel::isFavorite,
            onSelectDestination = viewModel::selectDestination,
            onSelectCategory = viewModel::selectCategory,
            onSearch = viewModel::updateSearch,
            onOpen = viewModel::open,
            onOpenHistory = viewModel::openHistory,
            onToggleFavorite = viewModel::toggleFavorite,
            onRefresh = viewModel::refresh,
            onClearHistory = viewModel::clearHistory,
            onPlayDownload = viewModel::playDownload,
            onDeleteDownload = { item ->
                viewModel.deleteDownload(item)
                notify("تم حذف التحميل.")
            },
            onRetryDownload = { item -> notify(viewModel.retryDownload(item)) },
            onLogout = viewModel::logout,
        )

        HulkScreen.MOVIE_DETAILS -> {
            val item = state.selectedItem
            if (item != null) {
                MovieDetailsScreen(
                    item = item,
                    details = state.selectedDetails,
                    isLoading = state.isLoading,
                    errorMessage = state.errorMessage,
                    isTv = isTv,
                    isFavorite = viewModel.isFavorite(item),
                    download = state.downloads.firstOrNull { it.historyKey == "MOVIE:${item.id}" },
                    onBack = viewModel::back,
                    onPlay = viewModel::playSelectedMovie,
                    onDownload = { notify(viewModel.downloadSelectedMovie()) },
                    onToggleFavorite = { viewModel.toggleFavorite(item) },
                )
            } else {
                LaunchedEffect(state.screen) { viewModel.back() }
            }
        }

        HulkScreen.SERIES -> {
            val series = state.selectedSeries
            if (series != null) {
                SeriesScreen(
                    series = series,
                    details = state.selectedDetails,
                    episodes = state.episodes,
                    isLoading = state.isLoading,
                    errorMessage = state.errorMessage,
                    isTv = isTv,
                    isFavorite = viewModel.isFavorite(series),
                    downloads = state.downloads,
                    onBack = viewModel::back,
                    onPlay = viewModel::playEpisode,
                    onDownload = { episode -> notify(viewModel.downloadEpisode(episode)) },
                    onToggleFavorite = { viewModel.toggleFavorite(series) },
                )
            } else {
                LaunchedEffect(state.screen) { viewModel.back() }
            }
        }

        HulkScreen.PLAYER -> {
            val playback = state.playback
            if (playback != null) {
                PlayerScreen(
                    request = playback,
                    liveCatalog = state.catalogs[sa.hulksa.player.model.ContentType.LIVE],
                    isFavorite = viewModel::isFavorite,
                    onSelectLiveChannel = viewModel::switchLiveChannel,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onBack = viewModel::back,
                    onProgress = viewModel::onPlaybackProgress,
                )
            } else {
                LaunchedEffect(state.screen) { viewModel.back() }
            }
        }
    }
}
