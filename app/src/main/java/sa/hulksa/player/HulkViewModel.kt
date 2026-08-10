package sa.hulksa.player

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
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
import sa.hulksa.player.model.DownloadScheduleMode
import sa.hulksa.player.model.DownloadSettings
import sa.hulksa.player.model.DiagnosticsState
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
    val downloadSettings: DownloadSettings = DownloadSettings(),
    val selectedItem: ContentItem? = null,
    val selectedSeries: ContentItem? = null,
    val selectedDetails: ContentDetails? = null,
    val episodes: List<Episode> = emptyList(),
    val playback: PlaybackRequest? = null,
    val diagnostics: DiagnosticsState = DiagnosticsState(),
) {
    companion object {
    }
}

private data class MovieCardProbeMetadata(
    val quality: String? = null,
    val durationMs: Long? = null,
)

class HulkViewModel(application: Application) : AndroidViewModel(application) {
    private var lastFavoriteToggleAtMs: Long = 0L
    private val repository = HulkRepository(application)
    private val userLibrary = UserLibrary(application)
    private val downloadRepository = DownloadRepository(application)
    private val mutableState = MutableStateFlow(
        HulkUiState(
            favorites = userLibrary.favorites(),
            history = userLibrary.history(),
            downloads = downloadRepository.downloads(),
            downloadSettings = downloadRepository.settings(),
        ),
    )
    val state: StateFlow<HulkUiState> = mutableState.asStateFlow()

    private var session: AuthenticatedSession? = null
    private val catalogJobs = mutableMapOf<ContentType, Job>()
    private val loadedCatalogs = mutableMapOf<ContentType, Catalog>()
    private val homeCatalogs = mutableMapOf<ContentType, Catalog>()
    private val selectedCategoryByType = mutableMapOf<ContentType, String?>()
    private var detailsJob: Job? = null
    private var diagnosticsJob: Job? = null
    private var profileLibraryRefreshJob: Job? = null
    private var playerReturnScreen = HulkScreen.MAIN
    private val movieCardMetadataPrefs = application.getSharedPreferences(
        MOVIE_CARD_METADATA_PREFS,
        Context.MODE_PRIVATE,
    )
    private val movieCardProbeSemaphore = Semaphore(MOVIE_CARD_PROBE_CONCURRENCY)
    private val movieCardProbeCallbacks =
        mutableMapOf<Int, MutableList<(String?, Long?) -> Unit>>()
    private val movieCardProbeInFlight = mutableSetOf<Int>()
    private val movieCardProbeAttempted = mutableSetOf<Int>()

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
                        it.status == OfflineStatus.CHECKING ||
                        it.status == OfflineStatus.DOWNLOADING ||
                        it.status == OfflineStatus.PAUSED ||
                        it.status == OfflineStatus.WAITING_SCHEDULE ||
                        it.status == OfflineStatus.WAITING_NETWORK ||
                        it.status == OfflineStatus.WAITING_STORAGE
                }
                val destination = mutableState.value.destination
                delay(
                    when {
                        !hasActive -> 5_000L
                        destination == MainDestination.DOWNLOADS -> 1_000L
                        destination == MainDestination.HOME -> 2_500L
                        else -> 4_000L
                    },
                )
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
        val current = mutableState.value
        val currentCatalogType = current.destination.catalogTypeOrNull()
        if (currentCatalogType != null) {
            selectedCategoryByType[currentCatalogType] = current.selectedCategoryId
        }

        val selectedType = destination.catalogTypeOrNull() ?: current.selectedType
        val restoredCategory = destination.catalogTypeOrNull()?.let(selectedCategoryByType::get)

        if (
            current.destination == destination &&
            current.selectedType == selectedType &&
            current.selectedCategoryId == restoredCategory &&
            current.searchQuery.isBlank() &&
            current.errorMessage == null
        ) {
            ensureDestinationCatalogs(destination)
            return
        }

        mutableState.update { state ->
            state.copy(
                destination = destination,
                selectedType = selectedType,
                catalogs = catalogsForDestination(destination, state.catalogs),
                selectedCategoryId = restoredCategory,
                searchQuery = "",
                errorMessage = null,
            )
        }
        ensureDestinationCatalogs(destination)
    }

    fun refreshProfileLibrary() {
        val favorites = userLibrary.favorites()
        val history = userLibrary.history()

        mutableState.update {
            it.copy(
                favorites = favorites,
                history = history,
                errorMessage = null,
            )
        }

        profileLibraryRefreshJob?.cancel()
        val sourceCatalogs = loadedCatalogs
            .filterKeys { it == ContentType.MOVIE || it == ContentType.SERIES }
            .toMap()
        if (sourceCatalogs.isEmpty()) return

        profileLibraryRefreshJob = viewModelScope.launch {
            val rebuiltHomeCatalogs = withContext(Dispatchers.Default) {
                sourceCatalogs.mapValues { (_, catalog) ->
                    compactHomeCatalog(catalog, favorites, history)
                }
            }
            rebuiltHomeCatalogs.forEach { (type, catalog) ->
                homeCatalogs[type] = catalog
            }
            mutableState.update { state ->
                state.copy(
                    catalogs = catalogsForDestination(state.destination, state.catalogs),
                )
            }
        }
    }

    private fun MainDestination.catalogTypeOrNull(): ContentType? = when (this) {
        MainDestination.LIVE -> ContentType.LIVE
        MainDestination.MOVIES -> ContentType.MOVIE
        MainDestination.SERIES -> ContentType.SERIES
        else -> null
    }

    private fun catalogsForDestination(
        destination: MainDestination,
        current: Map<ContentType, Catalog>,
    ): Map<ContentType, Catalog> {
        if (loadedCatalogs.isEmpty()) return current
        val displayed = current.toMutableMap()
        fun showFull(type: ContentType) {
            loadedCatalogs[type]?.let { displayed[type] = it }
        }
        fun showHome(type: ContentType) {
            (homeCatalogs[type] ?: loadedCatalogs[type])?.let { displayed[type] = it }
        }
        when (destination) {
            MainDestination.HOME -> {
                showHome(ContentType.MOVIE)
                showHome(ContentType.SERIES)
            }
            MainDestination.LIVE -> showFull(ContentType.LIVE)
            MainDestination.MOVIES -> showFull(ContentType.MOVIE)
            MainDestination.SERIES -> showFull(ContentType.SERIES)
            MainDestination.FAVORITES,
            MainDestination.SEARCH,
            -> ContentType.entries.forEach(::showFull)
            MainDestination.DOWNLOADS,
            MainDestination.SETTINGS -> Unit
        }
        return displayed
    }

    private fun compactHomeCatalog(
        catalog: Catalog,
        favorites: Set<String>,
        history: List<HistoryEntry>,
    ): Catalog {
        if (catalog.items.size <= HOME_CATALOG_LIMIT) return catalog
        val pinned = favorites + history.asSequence().map(HistoryEntry::key).toSet()
        val retained = buildList {
            addAll(catalog.items.filter { "${it.type.name}:${it.id}" in pinned })
            addAll(
                catalog.items.asSequence()
                    .sortedByDescending { it.addedAtEpochSeconds ?: 0L }
                    .take(HOME_RECENT_LIMIT),
            )
            addAll(
                catalog.items.asSequence()
                    .filter { it.rating?.toDoubleOrNull() != null }
                    .sortedByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }
                    .take(HOME_RATED_LIMIT),
            )
        }.distinctBy { "${it.type.name}:${it.id}" }.take(HOME_CATALOG_LIMIT)
        return catalog.copy(items = retained)
    }

    private fun ensureDestinationCatalogs(destination: MainDestination) {
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
        val current = mutableState.value
        current.destination.catalogTypeOrNull()?.let { type ->
            selectedCategoryByType[type] = categoryId
        }
        if (current.selectedCategoryId != categoryId) {
            mutableState.update { it.copy(selectedCategoryId = categoryId) }
        }
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
        types.forEach {
            loadedCatalogs.remove(it)
            homeCatalogs.remove(it)
        }
        mutableState.update { it.copy(catalogs = it.catalogs - types, errorMessage = null) }
        types.forEach { ensureCatalog(it, force = true) }
    }

    fun prefetchMovieCardMetadata(
        item: ContentItem,
        onResult: (quality: String?, durationMs: Long?) -> Unit,
    ) {
        if (item.type != ContentType.MOVIE) return

        val cached = readMovieCardMetadata(item.id)
        if (cached.quality != null && cached.durationMs != null) {
            onResult(cached.quality, cached.durationMs)
            return
        }

        movieCardProbeCallbacks.getOrPut(item.id) { mutableListOf() }.add(onResult)
        if (item.id in movieCardProbeInFlight) return

        if (item.id in movieCardProbeAttempted) {
            notifyMovieCardProbeCallbacks(item.id, cached)
            return
        }

        val activeSession = session ?: run {
            notifyMovieCardProbeCallbacks(item.id, cached)
            return
        }

        movieCardProbeAttempted += item.id
        movieCardProbeInFlight += item.id

        viewModelScope.launch {
            val fetched = runCatching {
                movieCardProbeSemaphore.withPermit {
                    repository.movieCardMetadata(activeSession, item.id)
                }
            }.getOrNull()

            if (fetched != null) {
                cacheMovieCardMetadata(
                    movieId = item.id,
                    quality = fetched.quality,
                    durationMs = fetched.durationMs,
                )
            }

            movieCardProbeInFlight -= item.id
            notifyMovieCardProbeCallbacks(item.id, readMovieCardMetadata(item.id))
        }
    }

    private fun readMovieCardMetadata(movieId: Int): MovieCardProbeMetadata {
        val quality = movieCardMetadataPrefs
            .getString("movie:$movieId:quality", null)
            ?.trim()
            ?.takeIf(String::isNotBlank)
        val durationMs = movieCardMetadataPrefs
            .getLong("movie:$movieId:duration_ms", 0L)
            .takeIf { it > 0L }
        return MovieCardProbeMetadata(quality = quality, durationMs = durationMs)
    }

    private fun cacheMovieCardMetadata(
        movieId: Int,
        quality: String?,
        durationMs: Long?,
    ) {
        if (quality == null && durationMs == null) return
        movieCardMetadataPrefs.edit().apply {
            quality?.let { putString("movie:$movieId:quality", it) }
            durationMs
                ?.takeIf { it > 0L }
                ?.let { putLong("movie:$movieId:duration_ms", it) }
        }.apply()
    }

    private fun notifyMovieCardProbeCallbacks(
        movieId: Int,
        metadata: MovieCardProbeMetadata,
    ) {
        movieCardProbeCallbacks
            .remove(movieId)
            .orEmpty()
            .forEach { callback ->
                callback(metadata.quality, metadata.durationMs)
            }
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

    fun playNextEpisode(): Boolean {
        val activeSession = session ?: return false
        val series = mutableState.value.selectedSeries ?: return false
        val current = mutableState.value.playback?.takeIf { it.streamKind == "series" } ?: return false
        val ordered = mutableState.value.episodes.sortedWith(compareBy(Episode::season, Episode::episodeNumber))
        val currentIndex = ordered.indexOfFirst { it.id == current.streamId }
        val next = ordered.getOrNull(currentIndex + 1) ?: return false
        playerReturnScreen = HulkScreen.SERIES
        startPlayback(repository.playback(activeSession, series, next))
        return true
    }

    fun switchLiveChannel(channel: ContentItem) {
        val activeSession = session ?: return
        if (channel.type != ContentType.LIVE) return
        playerReturnScreen = HulkScreen.MAIN
        startPlayback(repository.playback(activeSession, channel))
    }

    fun downloadSelectedMovie(): String {
        val activeSession = session ?: return "سجل الدخول اولا لبدء التحميل."
        val movie = mutableState.value.selectedItem?.takeIf { it.type == ContentType.MOVIE }
            ?: return "تعذر تحديد الفيلم."
        return enqueueDownload(repository.playback(activeSession, movie))
    }

    fun downloadEpisode(episode: Episode): String {
        val activeSession = session ?: return "سجل الدخول اولا لبدء التحميل."
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

    fun toggleWifiOnly(): String {
        val settings = downloadRepository.setWifiOnly(!mutableState.value.downloadSettings.wifiOnly)
        mutableState.update { it.copy(downloadSettings = settings, downloads = downloadRepository.downloads()) }
        return if (settings.wifiOnly) "تم تفعيل التحميل عبر واي فاي فقط." else "تم السماح بالتحميل عبر جميع الشبكات."
    }

    fun toggleDownloadSchedule(): String {
        val current = mutableState.value.downloadSettings.scheduleMode
        val next = if (current == DownloadScheduleMode.NOW) DownloadScheduleMode.NIGHT else DownloadScheduleMode.NOW
        val settings = downloadRepository.setScheduleMode(next)
        mutableState.update { it.copy(downloadSettings = settings, downloads = downloadRepository.downloads()) }
        return if (next == DownloadScheduleMode.NIGHT) {
            "تمت جدولة التحميلات الجديدة والقائمة للساعة 2 ليلا."
        } else {
            "تم الغاء الجدولة وبدء التحميلات القائمة الان."
        }
    }

    fun cycleConcurrentDownloads(): String {
        val current = mutableState.value.downloadSettings.concurrentDownloads
        val next = if (current >= 3) 1 else current + 1
        val settings = downloadRepository.setConcurrentDownloads(next)
        mutableState.update { it.copy(downloadSettings = settings, downloads = downloadRepository.downloads()) }
        return "عدد التحميلات المتزامنة الان ${settings.concurrentDownloads}."
    }

    fun cycleDownloadPriority(item: OfflineDownload): String {
        val downloads = downloadRepository.cyclePriority(item.downloadId)
        val updated = downloads.firstOrNull { it.downloadId == item.downloadId }
        mutableState.update { it.copy(downloads = downloads) }
        return when (updated?.priority) {
            1 -> "تم رفع اولوية التحميل."
            -1 -> "تم خفض اولوية التحميل."
            else -> "تم ضبط اولوية التحميل على عادية."
        }
    }

    fun retryDownload(item: OfflineDownload): String = when (item.status) {
        OfflineStatus.COMPLETED -> "التحميل مكتمل وجاهز للتشغيل."
        OfflineStatus.QUEUED,
        OfflineStatus.CHECKING,
        OfflineStatus.DOWNLOADING,
        -> {
            mutableState.update { it.copy(downloads = downloadRepository.pause(item.downloadId)) }
            "تم ايقاف التحميل مؤقتا."
        }
        OfflineStatus.PAUSED,
        OfflineStatus.WAITING_SCHEDULE,
        OfflineStatus.WAITING_NETWORK,
        OfflineStatus.WAITING_STORAGE,
        -> {
            if (downloadRepository.resume(item.downloadId)) {
                mutableState.update { it.copy(downloads = downloadRepository.downloads()) }
                "جار استئناف التحميل من اخر نقطة."
            } else {
                rebuildDownload(item)
            }
        }
        OfflineStatus.FAILED -> {
            if (downloadRepository.resume(item.downloadId)) {
                mutableState.update { it.copy(downloads = downloadRepository.downloads()) }
                "جار اعادة المحاولة من اخر نقطة."
            } else {
                rebuildDownload(item)
            }
        }
    }

    private fun rebuildDownload(item: OfflineDownload): String {
        val activeSession = session ?: return "سجل الدخول اولا لاعادة التحميل."
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

    fun onPlaybackProgress(request: PlaybackRequest, positionMs: Long, durationMs: Long) {
        if (request.isLive) return
        val updated = userLibrary.updateProgress(request, positionMs, durationMs)
        mutableState.update { it.copy(history = updated) }
    }

    fun removeHistoryEntry(key: String) {
        val updated = userLibrary.removeHistory(key)
        mutableState.update { it.copy(history = updated) }
    }

    fun toggleFavorite(item: ContentItem) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastFavoriteToggleAtMs < 700L) return
        lastFavoriteToggleAtMs = now
        val key = userLibrary.keyFor(item)
        val current = mutableState.value.favorites
        val updated = current.toMutableSet().apply {
            if (!add(key)) remove(key)
        }.toSet()
        userLibrary.replaceFavorites(updated)
        mutableState.update { it.copy(favorites = updated) }
    }

    fun isFavorite(item: ContentItem): Boolean = userLibrary.isFavorite(item, mutableState.value.favorites)

    fun clearHistory() {
        mutableState.update { it.copy(history = userLibrary.clearHistory()) }
    }

    fun runDiagnostics() {
        val activeSession = session ?: run {
            mutableState.update {
                it.copy(diagnostics = it.diagnostics.copy(errorMessage = "سجل الدخول اولا لتشغيل الفحص."))
            }
            return
        }
        if (diagnosticsJob?.isActive == true) return
        mutableState.update {
            it.copy(
                diagnostics = DiagnosticsState(
                    isRunning = true,
                    progress = 1,
                    stage = "تهيئة مركز الفحص",
                    report = it.diagnostics.report,
                ),
            )
        }
        diagnosticsJob = viewModelScope.launch {
            runCatching {
                repository.diagnose(activeSession) { progress, stage ->
                    mutableState.update { state ->
                        state.copy(
                            diagnostics = state.diagnostics.copy(
                                isRunning = true,
                                progress = progress,
                                stage = stage,
                                errorMessage = null,
                            ),
                        )
                    }
                }
            }.onSuccess { report ->
                mutableState.update {
                    it.copy(
                        diagnostics = DiagnosticsState(
                            isRunning = false,
                            progress = 100,
                            stage = "اكتمل الفحص",
                            report = report,
                        ),
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        diagnostics = it.diagnostics.copy(
                            isRunning = false,
                            stage = "تعذر اكمال الفحص",
                            errorMessage = error.message ?: "حدث خطا غير متوقع اثناء الفحص.",
                        ),
                    )
                }
            }
        }
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

    private fun clearCatalogMemory() {
        loadedCatalogs.clear()
        homeCatalogs.clear()
        selectedCategoryByType.clear()
        movieCardProbeCallbacks.clear()
        movieCardProbeInFlight.clear()
        movieCardProbeAttempted.clear()
        profileLibraryRefreshJob?.cancel()
        profileLibraryRefreshJob = null
    }

    fun logout() {
        repository.logout()
        session = null
        clearCatalogMemory()
        detailsJob?.cancel()
        diagnosticsJob?.cancel()
        catalogJobs.values.forEach(Job::cancel)
        catalogJobs.clear()
        mutableState.value = HulkUiState(
            isStarting = false,
            favorites = userLibrary.favorites(),
            history = userLibrary.history(),
            downloads = downloadRepository.downloads(),
            downloadSettings = downloadRepository.settings(),
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
                    clearCatalogMemory()
                    mutableState.update {
                        it.copy(
                            screen = HulkScreen.MAIN,
                            destination = MainDestination.HOME,
                            isLoading = false,
                            account = authenticated.account,
                            catalogs = emptyMap(),
                            selectedCategoryId = null,
                            searchQuery = "",
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
        if (!force) {
            loadedCatalogs[type]?.let {
                mutableState.update { state ->
                    val display = catalogsForDestination(state.destination, state.catalogs)
                    if (display == state.catalogs) state else state.copy(catalogs = display)
                }
                return
            }
        }
        if (catalogJobs[type]?.isActive == true) return
        mutableState.update {
            it.copy(loadingTypes = it.loadingTypes + type, errorMessage = null)
        }
        catalogJobs[type] = viewModelScope.launch {
            runCatching {
                val full = repository.catalog(activeSession, type)
                val snapshot = mutableState.value
                val compact = if (type == ContentType.MOVIE || type == ContentType.SERIES) {
                    withContext(Dispatchers.Default) {
                        compactHomeCatalog(full, snapshot.favorites, snapshot.history)
                    }
                } else {
                    full
                }
                full to compact
            }
                .onSuccess { (full, compact) ->
                    loadedCatalogs[type] = full
                    homeCatalogs[type] = compact
                    mutableState.update { state ->
                        val base = state.catalogs + (type to full)
                        state.copy(
                            catalogs = catalogsForDestination(state.destination, base),
                            loadingTypes = state.loadingTypes - type,
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
                "بدا فحص حجم ${result.item.title} والمساحة المتاحة."
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
            clearCatalogMemory()
        }
        mutableState.update {
            it.copy(
                screen = if (invalidSession) HulkScreen.LOGIN else it.screen,
                isStarting = false,
                isLoading = false,
                errorMessage = error.message ?: "حدث خطا غير متوقع. حاول مرة اخرى.",
            )
        }
    }

    companion object {

        private const val HOME_RECENT_LIMIT = 240
        private const val HOME_RATED_LIMIT = 120
        private const val HOME_CATALOG_LIMIT = 320
        private const val MOVIE_CARD_METADATA_PREFS = "movie_card_verified_metadata"
        private const val MOVIE_CARD_PROBE_CONCURRENCY = 2
    }
}
