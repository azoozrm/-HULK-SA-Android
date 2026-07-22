package sa.hulksa.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sa.hulksa.player.data.DownloadRepository
import sa.hulksa.player.data.HulkRepository
import sa.hulksa.player.data.UserLibrary
import sa.hulksa.player.data.XtreamException
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentDetails
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.Episode
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.model.PlaybackRequest

enum class HulkScreen {
    LOGIN,
    MAIN,
    MOVIE_DETAILS,
    SERIES,
    PLAYER,
}

enum class MainDestination {
    HOME,
    LIVE,
    MOVIES,
    SERIES,
    FAVORITES,
    SEARCH,
    DOWNLOADS,
    SETTINGS,
}

data class HulkUiState(
    val screen: HulkScreen = HulkScreen.LOGIN,
    val isStarting: Boolean = true,
    val isLoading: Boolean = false,
    val loadingTypes: Set<ContentType> = emptySet(),
    val errorMessage: String? = null,
    val account: AccountInfo? = null,
    val destination: MainDestination = MainDestination.HOME,
    val selectedType: ContentType = ContentType.MOVIE,
    val catalogs: Map<ContentType, Catalog> = emptyMap(),
    val selectedCategoryId: String? = null,
    val searchQuery: String = "",
    val favorites: Set<String> = emptySet(),
    val history: List<HistoryEntry> = emptyList(),
    val downloads: List<OfflineDownload> = emptyList(),
    val selectedItem: ContentItem? = null,
    val selectedSeries: ContentItem? = null,
    val selectedDetails: ContentDetails? = null,
    val episodes: List<Episode> = emptyList(),
    val playback: PlaybackRequest? = null,
)

class HulkViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HulkRepository(application)
    private val userLibrary = UserLibrary(application)
    private val downloadRepository = DownloadRepository(application)
    private val mutableState = MutableStateFlow(
        HulkUiState(
            favorites = userLibrary.favorites(),
            history = userLibrary.history(),
            downloads = downloadRepository.downloads(),
        ),
    )
    val state: StateFlow<HulkUiState> = mutableState.asStateFlow()

    private var session: AuthenticatedSession? = null
    private val catalogJobs = mutableMapOf<ContentType, Job>()
    private var detailsJob: Job? = null
    private var playerReturnScreen = HulkScreen.MAIN

    init {
        restoreSession()
        viewModelScope.launch {
            while (isActive) {
                val downloads = downloadRepository.downloads()
                if (downloads != mutableState.value.downloads) {
                    mutableState.update { it.copy(downloads = downloads) }
                }
                val hasActive = downloads.any {
                    it.status == OfflineStatus.QUEUED ||
                        it.status == OfflineStatus.DOWNLOADING ||
                        it.status == OfflineStatus.PAUSED
                }
                delay(if (hasActive) 1_250L else 5_000L)
            }
        }
    }

    fun login(username: String, password: String, remember: Boolean = true) {
        val cleanUsername = username.trim()
        if (cleanUsername.isEmpty() || password.isEmpty()) {
            mutableState.update { it.copy(errorMessage = "ادخل اسم المستخدم وكلمة المرور.") }
            return
        }
        authenticate(Credentials(cleanUsername, password), remember)
    }

    fun selectDestination(destination: MainDestination) {
        val selectedType = when (destination) {
            MainDestination.LIVE -> ContentType.LIVE
            MainDestination.SERIES -> ContentType.SERIES
            else -> ContentType.MOVIE
        }
        mutableState.update {
            it.copy(
                destination = destination,
                selectedType = selectedType,
                selectedCategoryId = null,
                searchQuery = "",
                errorMessage = null,
            )
        }
        when (destination) {
            MainDestination.HOME -> {
                ensureCatalog(ContentType.MOVIE)
                ensureCatalog(ContentType.SERIES)
            }
            MainDestination.LIVE -> ensureCatalog(ContentType.LIVE)
            MainDestination.MOVIES -> ensureCatalog(ContentType.MOVIE)
            MainDestination.SERIES -> ensureCatalog(ContentType.SERIES)
            MainDestination.FAVORITES,
            MainDestination.SEARCH,
            -> ContentType.entries.forEach(::ensureCatalog)
            MainDestination.DOWNLOADS,
            MainDestination.SETTINGS -> Unit
        }
    }

    fun selectType(type: ContentType) {
        selectDestination(
            when (type) {
                ContentType.LIVE -> MainDestination.LIVE
                ContentType.MOVIE -> MainDestination.MOVIES
                ContentType.SERIES -> MainDestination.SERIES
            },
        )
    }

    fun selectCategory(categoryId: String?) {
        mutableState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun updateSearch(query: String) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun refresh() {
        val types = when (mutableState.value.destination) {
            MainDestination.HOME -> setOf(ContentType.MOVIE, ContentType.SERIES)
            MainDestination.LIVE -> setOf(ContentType.LIVE)
            MainDestination.MOVIES -> setOf(ContentType.MOVIE)
            MainDestination.SERIES -> setOf(ContentType.SERIES)
            MainDestination.FAVORITES,
            MainDestination.SEARCH,
            -> ContentType.entries.toSet()
            MainDestination.DOWNLOADS,
            MainDestination.SETTINGS -> emptySet()
        }
        if (types.isEmpty()) return
        mutableState.update { it.copy(catalogs = it.catalogs - types, errorMessage = null) }
        types.forEach { ensureCatalog(it, force = true) }
    }

    fun open(item: ContentItem) {
        val activeSession = session ?: return
        detailsJob?.cancel()
        when (item.type) {
            ContentType.LIVE -> {
                playerReturnScreen = HulkScreen.MAIN
                startPlayback(repository.playback(activeSession, item))
            }
            ContentType.MOVIE -> {
                mutableState.update {
                    it.copy(
                        screen = HulkScreen.MOVIE_DETAILS,
                        selectedItem = item,
                        selectedDetails = ContentDetails(
                            plot = item.plot,
                            genre = item.genre,
                            backdropUrl = item.backdropUrl,
                        ),
                        isLoading = true,
                        errorMessage = null,
                    )
                }
                detailsJob = viewModelScope.launch {
                    runCatching { repository.contentDetails(activeSession, item.id) }
                        .onSuccess { details ->
                            mutableState.update { it.copy(selectedDetails = details, isLoading = false) }
                        }
                        .onFailure(::showFailure)
                }
            }
            ContentType.SERIES -> {
                mutableState.update {
                    it.copy(
                        screen = HulkScreen.SERIES,
                        selectedSeries = item,
                        selectedDetails = ContentDetails(
                            plot = item.plot,
                            genre = item.genre,
                            backdropUrl = item.backdropUrl,
                        ),
                        episodes = emptyList(),
                        isLoading = true,
                        errorMessage = null,
                    )
                }
                detailsJob = viewModelScope.launch {
                    runCatching { repository.seriesBundle(activeSession, item.id) }
                        .onSuccess { bundle ->
                            mutableState.update {
                                it.copy(
                                    selectedDetails = bundle.details,
                                    episodes = bundle.episodes,
                                    isLoading = false,
                                    errorMessage = if (bundle.episodes.isEmpty()) {
                                        "لم نجد حلقات لهذا المسلسل."
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                        .onFailure(::showFailure)
                }
            }
        }
    }

    fun playSelectedMovie() {
        val activeSession = session ?: return
        val movie = mutableState.value.selectedItem?.takeIf { it.type == ContentType.MOVIE } ?: return
        playerReturnScreen = HulkScreen.MOVIE_DETAILS
        startPlayback(repository.playback(activeSession, movie))
    }

    fun playEpisode(episode: Episode) {
        val activeSession = session ?: return
        val series = mutableState.value.selectedSeries ?: return
        playerReturnScreen = HulkScreen.SERIES
        startPlayback(repository.playback(activeSession, series, episode))
    }

    fun switchLiveChannel(channel: ContentItem) {
        val activeSession = session ?: return
        if (channel.type != ContentType.LIVE) return
        playerReturnScreen = HulkScreen.MAIN
        startPlayback(repository.playback(activeSession, channel))
    }

    fun downloadSelectedMovie(): String {
        val activeSession = session ?: return "سجل الدخول أولا لبدء التحميل."
        val movie = mutableState.value.selectedItem?.takeIf { it.type == ContentType.MOVIE }
            ?: return "تعذر تحديد الفيلم."
        return enqueueDownload(repository.playback(activeSession, movie))
    }

    fun downloadEpisode(episode: Episode): String {
        val activeSession = session ?: return "سجل الدخول أولا لبدء التحميل."
        val series = mutableState.value.selectedSeries ?: return "تعذر تحديد المسلسل."
        return enqueueDownload(
            request = repository.playback(activeSession, series, episode),
            seriesTitle = series.name,
            season = episode.season,
            episodeNumber = episode.episodeNumber,
        )
    }

    fun playDownload(item: OfflineDownload) {
        val localUri = item.localUri?.takeIf(String::isNotBlank) ?: return
        if (item.status != OfflineStatus.COMPLETED) return
        playerReturnScreen = HulkScreen.MAIN
        startPlayback(
            PlaybackRequest(
                title = item.title,
                posterUrl = item.posterUrl,
                candidates = listOf(localUri),
                isLive = false,
                historyKey = item.historyKey,
                streamKind = item.streamKind,
                streamId = item.streamId,
                extension = item.extension,
            ),
        )
    }

    fun deleteDownload(item: OfflineDownload) {
        mutableState.update { it.copy(downloads = downloadRepository.remove(item.downloadId)) }
    }

    fun retryDownload(item: OfflineDownload): String {
        val activeSession = session ?: return "سجل الدخول أولا لإعادة التحميل."
        downloadRepository.remove(item.downloadId)
        val request = repository.playback(
            activeSession,
            HistoryEntry(
                key = item.historyKey,
                title = item.title,
                posterUrl = item.posterUrl,
                streamKind = item.streamKind,
                streamId = item.streamId,
                extension = item.extension,
                isLive = false,
                positionMs = 0L,
                durationMs = 0L,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
        return enqueueDownload(request, item.seriesTitle, item.season, item.episodeNumber)
    }

    fun openHistory(entry: HistoryEntry) {
        val activeSession = session ?: return
        playerReturnScreen = HulkScreen.MAIN
        startPlayback(repository.playback(activeSession, entry))
    }

    fun onPlaybackProgress(positionMs: Long, durationMs: Long) {
        val request = mutableState.value.playback ?: return
        if (request.isLive) return
        val updated = userLibrary.updateProgress(request, positionMs, durationMs)
        mutableState.update { it.copy(history = updated) }
    }

    fun toggleFavorite(item: ContentItem) {
        mutableState.update { it.copy(favorites = userLibrary.toggle(item)) }
    }

    fun isFavorite(item: ContentItem): Boolean = userLibrary.isFavorite(item, mutableState.value.favorites)

    fun clearHistory() {
        mutableState.update { it.copy(history = userLibrary.clearHistory()) }
    }

    fun back() {
        when (mutableState.value.screen) {
            HulkScreen.PLAYER -> mutableState.update {
                it.copy(screen = playerReturnScreen, playback = null, errorMessage = null)
            }
            HulkScreen.MOVIE_DETAILS -> mutableState.update {
                it.copy(
                    screen = HulkScreen.MAIN,
                    selectedItem = null,
                    selectedDetails = null,
                    errorMessage = null,
                )
            }
            HulkScreen.SERIES -> mutableState.update {
                it.copy(
                    screen = HulkScreen.MAIN,
                    selectedSeries = null,
                    selectedDetails = null,
                    episodes = emptyList(),
                    errorMessage = null,
                )
            }
            else -> Unit
        }
    }

    fun logout() {
        repository.logout()
        session = null
        detailsJob?.cancel()
        catalogJobs.values.forEach(Job::cancel)
        catalogJobs.clear()
        mutableState.value = HulkUiState(
            isStarting = false,
            favorites = userLibrary.favorites(),
            history = userLibrary.history(),
            downloads = downloadRepository.downloads(),
        )
    }

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    private fun restoreSession() {
        val credentials = repository.savedCredentials()
        if (credentials == null) {
            mutableState.update { it.copy(isStarting = false) }
            return
        }
        authenticate(credentials, remember = true)
    }

    private fun authenticate(credentials: Credentials, remember: Boolean) {
        mutableState.update {
            it.copy(isStarting = false, isLoading = true, errorMessage = null)
        }
        viewModelScope.launch {
            runCatching { repository.login(credentials, remember) }
                .onSuccess { authenticated ->
                    session = authenticated
                    mutableState.update {
                        it.copy(
                            screen = HulkScreen.MAIN,
                            destination = MainDestination.HOME,
                            isLoading = false,
                            account = authenticated.account,
                            errorMessage = null,
                        )
                    }
                    ensureCatalog(ContentType.MOVIE)
                    ensureCatalog(ContentType.SERIES)
                }
                .onFailure(::showFailure)
        }
    }

    private fun ensureCatalog(type: ContentType, force: Boolean = false) {
        val activeSession = session ?: return
        if (!force && mutableState.value.catalogs[type] != null) return
        if (catalogJobs[type]?.isActive == true) return
        mutableState.update {
            it.copy(loadingTypes = it.loadingTypes + type, errorMessage = null)
        }
        catalogJobs[type] = viewModelScope.launch {
            runCatching { repository.catalog(activeSession, type) }
                .onSuccess { catalog ->
                    mutableState.update {
                        it.copy(
                            catalogs = it.catalogs + (type to catalog),
                            loadingTypes = it.loadingTypes - type,
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update { it.copy(loadingTypes = it.loadingTypes - type) }
                    showFailure(error)
                }
        }
    }

    private fun startPlayback(request: PlaybackRequest) {
        val resumable = request.copy(resumePositionMs = userLibrary.resumePosition(request.historyKey))
        val updatedHistory = userLibrary.recordStart(resumable)
        mutableState.update {
            it.copy(
                screen = HulkScreen.PLAYER,
                playback = resumable,
                history = updatedHistory,
                errorMessage = null,
            )
        }
    }

    private fun enqueueDownload(
        request: PlaybackRequest,
        seriesTitle: String? = null,
        season: Int? = null,
        episodeNumber: Int? = null,
    ): String {
        return when (
            val result = downloadRepository.enqueue(
                request = request,
                seriesTitle = seriesTitle,
                season = season,
                episodeNumber = episodeNumber,
            )
        ) {
            is DownloadRepository.EnqueueResult.Started -> {
                mutableState.update { it.copy(downloads = downloadRepository.downloads()) }
                "بدأ تحميل ${result.item.title}."
            }
            is DownloadRepository.EnqueueResult.AlreadyExists -> when (result.item.status) {
                OfflineStatus.COMPLETED -> "هذا المحتوى محمل بالفعل."
                else -> "هذا المحتوى موجود في قائمة التحميلات."
            }
            is DownloadRepository.EnqueueResult.Failed -> result.message
        }
    }

    private fun showFailure(error: Throwable) {
        val invalidSession = error is XtreamException.InvalidCredentials ||
            error is XtreamException.SubscriptionInactive
        if (invalidSession) {
            repository.logout()
            session = null
        }
        mutableState.update {
            it.copy(
                screen = if (invalidSession) HulkScreen.LOGIN else it.screen,
                isStarting = false,
                isLoading = false,
                errorMessage = error.message ?: "حدث خطأ غير متوقع. حاول مرة أخرى.",
            )
        }
    }
}
