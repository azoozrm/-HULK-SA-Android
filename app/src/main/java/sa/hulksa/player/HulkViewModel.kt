package sa.hulksa.player

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import sa.hulksa.player.data.EpisodeNotificationPopup
import sa.hulksa.player.data.EpisodeNotificationSubscription
import sa.hulksa.player.data.EpisodeNotificationStoreResult
import sa.hulksa.player.data.HulkRepository
import sa.hulksa.player.data.KidsContentFilterStore
import sa.hulksa.player.data.LocalEpisodeNotification
import sa.hulksa.player.data.LocalEpisodeNotificationStore
import sa.hulksa.player.data.LocalNotificationItem
import sa.hulksa.player.data.OPERATIONS_CACHE_TTL_MS
import sa.hulksa.player.data.OperationsApkInstaller
import sa.hulksa.player.data.OperationsClient
import sa.hulksa.player.data.OperationsConfig
import sa.hulksa.player.data.OperationsConfigSource
import sa.hulksa.player.data.OperationsDownloadStatus
import sa.hulksa.player.data.OperationsDownloadUiState
import sa.hulksa.player.data.OperationsFetchResult
import sa.hulksa.player.data.OperationsInstallResult
import sa.hulksa.player.data.OperationsServiceStatus
import sa.hulksa.player.data.OperationsStore
import sa.hulksa.player.data.OperationsUiState
import sa.hulksa.player.data.OperationsUpdateDecision
import sa.hulksa.player.data.activePersistentOperationsAnnouncement
import sa.hulksa.player.data.PortalException
import sa.hulksa.player.data.ProfileStore
import sa.hulksa.player.data.UserLibrary
import sa.hulksa.player.data.XtreamException
import sa.hulksa.player.data.buildEpisodeNotificationPopups
import sa.hulksa.player.data.canUseSeriesEpisodeNotifications
import sa.hulksa.player.data.effectiveOperationsServiceStatus
import sa.hulksa.player.data.eligibleOperationsAnnouncements
import sa.hulksa.player.data.evaluateOperationsUpdatePolicy
import sa.hulksa.player.data.mergeNotificationCenterItems
import sa.hulksa.player.data.normalizeResellerAccessCode
import sa.hulksa.player.data.reliableEpisodeKeys
import sa.hulksa.player.data.resolveLocalNotificationSeriesTarget
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
import sa.hulksa.player.model.ProfileKind
import sa.hulksa.player.tv.TvDeepLinkDispatchDecision
import sa.hulksa.player.tv.TvDeepLinkResolution
import sa.hulksa.player.tv.TvDeepLinkRouter
import sa.hulksa.player.tv.TvDeepLinkTarget
import sa.hulksa.player.tv.TvPlatformIntegration
import sa.hulksa.player.tv.TvPlatformSyncResult
import sa.hulksa.player.tv.TvProfilePublicationPhase
import sa.hulksa.player.tv.decideTvDeepLinkDispatch
import sa.hulksa.player.tv.findTvDeepLinkEpisode
import sa.hulksa.player.tv.planTvProfilePublication
import sa.hulksa.player.tv.resolveTvDeepLink
import sa.hulksa.player.tv.shouldSyncTvProgress

enum class HulkScreen {
    LOGIN,
    MAIN,
    MOVIE_DETAILS,
    SERIES,
    NOTIFICATION_CENTER,
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
    val isAccountRefreshing: Boolean = false,
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
    val seriesEpisodeTarget: SeriesEpisodeTarget? = null,
    val notificationSubscribedSeriesIds: Set<Int> = emptySet(),
    val localNotifications: List<LocalNotificationItem> = emptyList(),
    val unreadNotificationCount: Int = 0,
    val episodeNotificationsEnabled: Boolean = true,
    val notificationPopup: EpisodeNotificationPopup? = null,
    val operations: OperationsUiState = OperationsUiState(),
    val playback: PlaybackRequest? = null,
    val diagnostics: DiagnosticsState = DiagnosticsState(),
) {
    companion object {
    }
}

data class SeriesEpisodeTarget(
    val seriesId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeId: Int?,
)

private enum class NotificationScanTrigger(val minimumAgeMs: Long) {
    APP_START(5 * 60_000L),
    APP_RESUME(10 * 60_000L),
    PROFILE_SWITCH(5 * 60_000L),
    LIBRARY_REFRESH(5 * 60_000L),
    MANUAL_REFRESH(60_000L),
    NOTIFICATION_CENTER(2 * 60_000L),
    MASTER_REENABLE(0L),
}

private data class MovieCardProbeMetadata(
    val quality: String? = null,
    val durationMs: Long? = null,
)

private fun initialOperationsUiState(
    config: OperationsConfig,
    fetchedAtEpochMs: Long,
    isTv: Boolean,
): OperationsUiState {
    val nowEpochMs = System.currentTimeMillis()
    val source = OperationsConfigSource.CACHE
    val effectiveService = config.service.copy(
        status = effectiveOperationsServiceStatus(config.service, source),
    )
    return OperationsUiState(
        source = source,
        updateDecision = evaluateOperationsUpdatePolicy(
            currentVersionCode = BuildConfig.VERSION_CODE,
            update = config.update,
            source = source,
            cacheAgeMs = nowEpochMs - fetchedAtEpochMs,
        ),
        update = config.update,
        service = effectiveService,
        features = config.features,
        growth = config.growth,
        persistentAnnouncement = activePersistentOperationsAnnouncement(
            announcements = config.announcements,
            currentVersionCode = BuildConfig.VERSION_CODE,
            isTv = isTv,
            nowEpochSeconds = nowEpochMs / 1_000L,
        ),
    )
}

private fun Application.isTelevisionDevice(): Boolean {
    val uiMode = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEVISION)
}

class HulkViewModel(application: Application) : AndroidViewModel(application) {
    private var lastFavoriteToggleAtMs: Long = 0L
    private val repository = HulkRepository(application)
    private val userLibrary = UserLibrary(application)
    private val downloadRepository = DownloadRepository(application)
    private val profileStore = ProfileStore(application)
    private val kidsContentFilterStore = KidsContentFilterStore(application)
    private val localEpisodeNotificationStore = LocalEpisodeNotificationStore(application)
    private val operationsStore = OperationsStore(application)
    private val operationsClient = OperationsClient()
    private val operationsInstaller = OperationsApkInstaller(application)
    private val operationsDeviceIsTv = application.isTelevisionDevice()
    private val tvPlatformIntegration = TvPlatformIntegration(application)
    private val initialCachedOperations = operationsStore.cachedConfig()
    private val initialNotificationSnapshot = localEpisodeNotificationStore.snapshot(
        profileStore.activeProfileId(),
    )
    private val mutableState = MutableStateFlow(
        HulkUiState(
            favorites = userLibrary.favorites(),
            history = userLibrary.history(),
            downloads = downloadRepository.downloads(),
            downloadSettings = downloadRepository.settings(),
            notificationSubscribedSeriesIds = initialNotificationSnapshot.subscribedSeriesIds,
            localNotifications = mergeNotificationCenterItems(
                initialNotificationSnapshot.notifications,
                operationsStore.systemNotifications(),
            ),
            unreadNotificationCount = initialNotificationSnapshot.unreadCount +
                operationsStore.systemNotifications().count { !it.read },
            episodeNotificationsEnabled = initialNotificationSnapshot.settings.enabled,
            operations = initialCachedOperations?.let { cached ->
                initialOperationsUiState(cached.config, cached.fetchedAtEpochMs, operationsDeviceIsTv)
            } ?: OperationsUiState(),
        ),
    )
    val state: StateFlow<HulkUiState> = mutableState.asStateFlow()

    private var session: AuthenticatedSession? = null
    private var sessionRestorationComplete: Boolean = false
    private val catalogJobs = mutableMapOf<ContentType, Job>()
    private val loadedCatalogs = mutableMapOf<ContentType, Catalog>()
    private val homeCatalogs = mutableMapOf<ContentType, Catalog>()
    private val selectedCategoryByType = mutableMapOf<ContentType, String?>()
    private var detailsJob: Job? = null
    private var accountRefreshJob: Job? = null
    private var diagnosticsJob: Job? = null
    private var profileLibraryRefreshJob: Job? = null
    private var notificationScanJob: Job? = null
    private var operationsRefreshJob: Job? = null
    private var operationsDownloadJob: Job? = null
    private var tvPlatformSyncJob: Job? = null
    private var tvPlatformClearJob: Job? = null
    private var tvDeepLinkEpisodeJob: Job? = null
    private var tvPlatformProfileReady: Boolean = false
    private var tvPlatformGeneration: Long = 0L
    private var tvExpectedProfileScopeId: String? = null
    private var tvPublishedProfileScopeId: String? = null
    private val tvLastSyncedPositions = mutableMapOf<String, Long>()
    private var pendingTvDeepLink: TvDeepLinkTarget? = null
    private var activeOperationsConfig: OperationsConfig? = initialCachedOperations?.config
    private var activeOperationsFetchedAtEpochMs: Long = initialCachedOperations?.fetchedAtEpochMs ?: 0L
    private val presentedOperationsMessageIds = linkedSetOf<String>()
    private val dismissedOptionalUpdateVersionCodes = linkedSetOf<Int>()
    private var notificationUiReady: Boolean = false
    private var notificationCenterReturnScreen = HulkScreen.MAIN
    private var playerReturnScreen = HulkScreen.MAIN
    private val movieCardMetadataPrefs = application.getSharedPreferences(
        MOVIE_CARD_METADATA_PREFS,
        Context.MODE_PRIVATE,
    )
    private val movieCardProbeSemaphore = Semaphore(MOVIE_CARD_PROBE_CONCURRENCY)
    private val episodeNotificationScanSemaphore = Semaphore(EPISODE_NOTIFICATION_SCAN_CONCURRENCY)
    private val movieCardProbeCallbacks =
        mutableMapOf<Int, MutableList<(String?, Long?) -> Unit>>()
    private val movieCardProbeInFlight = mutableSetOf<Int>()
    private val movieCardProbeAttempted = mutableSetOf<Int>()

    init {
        initialCachedOperations?.let { cached ->
            applyOperationsConfig(
                config = cached.config,
                source = OperationsConfigSource.CACHE,
                fetchedAtEpochMs = cached.fetchedAtEpochMs,
            )
        }
        refreshOperations(force = true)
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

    fun login(accessCode: String, username: String, password: String, remember: Boolean = true) {
        val normalizedAccessCode = normalizeResellerAccessCode(accessCode)
        if (normalizedAccessCode == null) {
            mutableState.update { it.copy(errorMessage = "ادخل كود دخول صحيح.") }
            return
        }
        val cleanUsername = username.trim()
        if (cleanUsername.isEmpty() || password.isEmpty()) {
            mutableState.update { it.copy(errorMessage = "ادخل اسم المستخدم وكلمة المرور.") }
            return
        }
        authenticate(
            Credentials(
                accessCode = normalizedAccessCode,
                username = cleanUsername,
                password = password,
            ),
            remember,
        )
    }

    fun handleTvDeepLink(rawUri: String?) {
        if (!operationsDeviceIsTv) return
        val target = TvDeepLinkRouter.parse(rawUri)
        if (target == null) {
            pendingTvDeepLink = null
            tvDeepLinkEpisodeJob?.cancel()
            tvDeepLinkEpisodeJob = null
            if (!rawUri.isNullOrBlank()) {
                mutableState.update { it.copy(errorMessage = "تعذر فتح هذا الرابط.") }
            }
            return
        }
        tvDeepLinkEpisodeJob?.cancel()
        tvDeepLinkEpisodeJob = null
        pendingTvDeepLink = target
        resolvePendingTvDeepLink()
    }

    fun setTvPlatformProfileReady(ready: Boolean) {
        if (!operationsDeviceIsTv) return
        if (!ready) {
            beginTvPlatformProfileTransition(resetPublishedScope = false)
            return
        }

        val scope = tvPlatformIntegration.activeProfileScope()
        val phases = planTvProfilePublication(
            previouslyPublishedScopeId = tvPublishedProfileScopeId,
            activeScopeId = scope?.providerScopeId,
            hasSession = session != null,
            profileResolved = true,
            kidsVerificationRequired = scope?.profileKind == ProfileKind.KIDS,
            kidsVerified = true,
        )
        if (TvProfilePublicationPhase.CLEAR in phases) {
            clearTvPlatformPrograms(resetPublishedScope = false)
        }
        if (TvProfilePublicationPhase.PUBLISH !in phases || scope == null) {
            tvPlatformProfileReady = false
            tvExpectedProfileScopeId = null
            return
        }

        tvPlatformGeneration++
        tvPlatformProfileReady = true
        tvExpectedProfileScopeId = scope.providerScopeId
        scheduleTvPlatformSync(immediate = true)
        resolvePendingTvDeepLink()
    }

    /** Called before ProfileStore changes so stale provider work is cancelled and cleared first. */
    fun beginTvPlatformProfileSwitch() {
        if (!operationsDeviceIsTv) return
        beginTvPlatformProfileTransition(resetPublishedScope = false)
    }

    fun selectDestination(destination: MainDestination) {
        if (
            destination == MainDestination.DOWNLOADS &&
            !mutableState.value.operations.features.downloadsEnabled
        ) return
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
        refreshNotificationState(clearPopup = false)
        scanSubscribedSeries(NotificationScanTrigger.LIBRARY_REFRESH)

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
        refreshOperations(force = true)
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
        mutableState.update { it.copy(errorMessage = null) }
        types.forEach { ensureCatalog(it, force = true) }
        scanSubscribedSeries(NotificationScanTrigger.MANUAL_REFRESH)
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
            ContentType.SERIES -> openSeries(item = item, target = null, activeSession = activeSession)
        }
    }

    private fun openSeries(
        item: ContentItem,
        target: SeriesEpisodeTarget?,
        activeSession: AuthenticatedSession,
    ) {
        val notificationAccountId = localEpisodeNotificationStore.activeAccountId()
        val notificationProfile = profileStore.activeProfile()
        detailsJob?.cancel()
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
                seriesEpisodeTarget = target,
                isLoading = true,
                errorMessage = null,
            )
        }
        detailsJob = viewModelScope.launch {
            runCatching { repository.seriesBundle(activeSession, item.id) }
                .onSuccess { bundle ->
                    val targetAvailable = target == null || if (target.episodeId != null) {
                        bundle.episodes.any { it.id == target.episodeId }
                    } else {
                        bundle.episodes.any {
                            it.season == target.seasonNumber &&
                                it.episodeNumber == target.episodeNumber
                        }
                    }
                    mutableState.update {
                        it.copy(
                            selectedDetails = bundle.details,
                            episodes = bundle.episodes,
                            isLoading = false,
                            errorMessage = when {
                                bundle.episodes.isEmpty() -> "لم نجد حلقات لهذا المسلسل."
                                !targetAvailable -> "هذه الحلقة لم تعد متاحة، وتم فتح المسلسل بدلًا منها."
                                else -> null
                            },
                        )
                    }
                    processOpenedSeriesForNotifications(
                        series = item,
                        episodes = bundle.episodes,
                        expectedAccountId = notificationAccountId,
                        expectedProfileId = notificationProfile.id,
                        expectedProfileKind = notificationProfile.kind,
                    )
                }
                .onFailure { error ->
                    if (target != null) {
                        mutableState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "هذا المحتوى لم يعد متاحًا.",
                            )
                        }
                    } else {
                        showFailure(error)
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
        if (!mutableState.value.operations.features.downloadsEnabled) {
            return "التنزيلات متوقفة مؤقتًا."
        }
        val activeSession = session ?: return "سجل الدخول اولا لبدء التحميل."
        val movie = mutableState.value.selectedItem?.takeIf { it.type == ContentType.MOVIE }
            ?: return "تعذر تحديد الفيلم."
        return enqueueDownload(repository.playback(activeSession, movie))
    }

    fun downloadEpisode(episode: Episode): String {
        if (!mutableState.value.operations.features.downloadsEnabled) {
            return "التنزيلات متوقفة مؤقتًا."
        }
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
        val localUri = downloadRepository.playableLocalUri(item.downloadId, item.historyKey) ?: return
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
        if (!mutableState.value.operations.features.downloadsEnabled) {
            return "التنزيلات متوقفة مؤقتًا."
        }
        val settings = downloadRepository.setWifiOnly(!mutableState.value.downloadSettings.wifiOnly)
        mutableState.update { it.copy(downloadSettings = settings, downloads = downloadRepository.downloads()) }
        return if (settings.wifiOnly) "تم تفعيل التحميل عبر واي فاي فقط." else "تم السماح بالتحميل عبر جميع الشبكات."
    }

    fun toggleDownloadSchedule(): String {
        if (!mutableState.value.operations.features.downloadsEnabled) {
            return "التنزيلات متوقفة مؤقتًا."
        }
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
        if (!mutableState.value.operations.features.downloadsEnabled) {
            return "التنزيلات متوقفة مؤقتًا."
        }
        val current = mutableState.value.downloadSettings.concurrentDownloads
        val next = if (current >= 3) 1 else current + 1
        val settings = downloadRepository.setConcurrentDownloads(next)
        mutableState.update { it.copy(downloadSettings = settings, downloads = downloadRepository.downloads()) }
        return "عدد التحميلات المتزامنة الان ${settings.concurrentDownloads}."
    }

    fun cycleDownloadPriority(item: OfflineDownload): String {
        if (!mutableState.value.operations.features.downloadsEnabled) {
            return "التنزيلات متوقفة مؤقتًا."
        }
        val downloads = downloadRepository.cyclePriority(item.downloadId)
        val updated = downloads.firstOrNull { it.downloadId == item.downloadId }
        mutableState.update { it.copy(downloads = downloads) }
        return when (updated?.priority) {
            1 -> "تم رفع اولوية التحميل."
            -1 -> "تم خفض اولوية التحميل."
            else -> "تم ضبط اولوية التحميل على عادية."
        }
    }

    fun retryDownload(item: OfflineDownload): String {
        if (!mutableState.value.operations.features.downloadsEnabled) {
            return "التنزيلات متوقفة مؤقتًا."
        }
        return when (item.status) {
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
        val lastSyncedPositionMs = tvLastSyncedPositions[request.historyKey] ?: 0L
        if (
            operationsDeviceIsTv &&
            shouldSyncTvProgress(lastSyncedPositionMs, positionMs, durationMs)
        ) {
            tvLastSyncedPositions[request.historyKey] = positionMs
            scheduleTvPlatformSync(immediate = positionMs.toDouble() / durationMs.coerceAtLeast(1L) >= .92)
        }
    }

    fun removeHistoryEntry(key: String) {
        val updated = userLibrary.removeHistory(key)
        mutableState.update { it.copy(history = updated) }
        tvLastSyncedPositions.remove(key)
        scheduleTvPlatformSync(immediate = true)
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

    fun isSeriesNotificationsEnabled(series: ContentItem): Boolean =
        series.id in mutableState.value.notificationSubscribedSeriesIds

    fun toggleSeriesNotifications(
        series: ContentItem,
        onResult: (String) -> Unit,
    ) {
        if (!mutableState.value.operations.features.episodeNotificationsEnabled) {
            onResult("تنبيهات الحلقات متوقفة مؤقتًا.")
            return
        }
        if (series.type != ContentType.SERIES || series.id <= 0) {
            onResult("تعذر تفعيل تنبيهات هذا المسلسل.")
            return
        }
        val profile = profileStore.activeProfile()
        val accountId = localEpisodeNotificationStore.activeAccountId()
        if (accountId == null) {
            onResult("سجل الدخول أولًا.")
            return
        }
        if (!canUseSeriesNotifications(profile.kind, series.id)) {
            onResult("لا يمكن تفعيل التنبيه لأن المسلسل غير موثّق ضمن محتوى الأطفال.")
            return
        }
        val currentlyEnabled = isSeriesNotificationsEnabled(series)
        val currentState = mutableState.value
        if (
            !currentlyEnabled &&
            (
                currentState.selectedSeries?.id != series.id ||
                    currentState.isLoading ||
                    (currentState.episodes.isEmpty() && currentState.errorMessage != null)
                )
        ) {
            onResult("انتظر حتى يكتمل تحميل الحلقات ثم حاول مرة أخرى.")
            return
        }

        viewModelScope.launch {
            if (
                localEpisodeNotificationStore.activeAccountId() != accountId ||
                profileStore.activeProfileId() != profile.id
            ) {
                onResult("تغيّر الحساب أو الملف الشخصي. حاول مرة أخرى.")
                return@launch
            }
            var enabledBaselineEpisodes: List<Episode>? = null
            val result = if (currentlyEnabled) {
                withContext(Dispatchers.IO) {
                    localEpisodeNotificationStore.disableSubscription(
                        profileId = profile.id,
                        seriesId = series.id,
                        expectedAccountId = accountId,
                    )
                }
            } else {
                val activeSession = session
                    ?: return@launch onResult("سجل الدخول أولًا.")
                val episodes = try {
                    repository.seriesBundle(activeSession, series.id).episodes
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    onResult("تعذر جلب الحلقات الحالية. لم يتم تفعيل التنبيهات.")
                    return@launch
                }
                val displayedKeys = reliableEpisodeKeys(currentState.episodes)
                val freshKeys = reliableEpisodeKeys(episodes)
                if (displayedKeys.isNotEmpty() && !freshKeys.containsAll(displayedKeys)) {
                    onResult("وصلت بيانات حلقات غير مكتملة. لم يتم تفعيل التنبيهات.")
                    return@launch
                }
                if (
                    localEpisodeNotificationStore.activeAccountId() != accountId ||
                    profileStore.activeProfileId() != profile.id ||
                    !canUseSeriesNotifications(profile.kind, series.id)
                ) {
                    onResult("تغيّر الملف الشخصي أو نطاق المحتوى. حاول مرة أخرى.")
                    return@launch
                }
                enabledBaselineEpisodes = episodes
                withContext(Dispatchers.IO) {
                    localEpisodeNotificationStore.enableSubscription(
                        profileId = profile.id,
                        series = series,
                        episodes = episodes,
                        expectedAccountId = accountId,
                    )
                }
            }
            if (result == EpisodeNotificationStoreResult.SUCCESS && !currentlyEnabled) {
                val refreshedEpisodes = enabledBaselineEpisodes.orEmpty()
                mutableState.update { state ->
                    if (state.selectedSeries?.id == series.id) {
                        state.copy(episodes = refreshedEpisodes)
                    } else {
                        state
                    }
                }
            }
            refreshNotificationState(clearPopup = false)
            onResult(
                when (result) {
                    EpisodeNotificationStoreResult.SUCCESS -> if (currentlyEnabled) {
                        "تم إيقاف تنبيهات الحلقات لهذا المسلسل."
                    } else {
                        "تم تفعيل التنبيهات. ستصلك الحلقات الجديدة فقط."
                    }
                    EpisodeNotificationStoreResult.INVALID_EPISODES ->
                        "تعذر إنشاء خط أساس موثوق للحلقات. حاول لاحقًا."
                    EpisodeNotificationStoreResult.MISSING_ACCOUNT -> "سجل الدخول أولًا."
                    else -> "تعذر حفظ إعداد التنبيهات. حاول مرة أخرى."
                },
            )
        }
    }

    fun toggleEpisodeNotificationMaster(onResult: (String) -> Unit) {
        if (!mutableState.value.operations.features.episodeNotificationsEnabled) {
            onResult("تنبيهات الحلقات متوقفة مؤقتًا.")
            return
        }
        val profileId = profileStore.activeProfileId()
        val accountId = localEpisodeNotificationStore.activeAccountId()
        if (accountId == null) {
            onResult("سجل الدخول أولًا.")
            return
        }
        val enable = !mutableState.value.episodeNotificationsEnabled
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                localEpisodeNotificationStore.setMasterEnabled(
                    profileId = profileId,
                    enabled = enable,
                    expectedAccountId = accountId,
                )
            }
            if (result == EpisodeNotificationStoreResult.SUCCESS) {
                if (!enable) {
                    notificationScanJob?.cancel()
                    mutableState.update { it.copy(notificationPopup = null) }
                }
                refreshNotificationState(clearPopup = !enable)
                if (enable) scanSubscribedSeries(NotificationScanTrigger.MASTER_REENABLE)
            }
            onResult(
                if (result != EpisodeNotificationStoreResult.SUCCESS) {
                    "تعذر تحديث إعداد التنبيهات."
                } else if (enable) {
                    "تم التشغيل. سيُحدّث خط الأساس بأمان قبل استئناف التنبيهات."
                } else {
                    "تم إيقاف تنبيهات الحلقات دون حذف اشتراكاتك."
                },
            )
        }
    }

    fun openNotificationCenter() {
        val current = mutableState.value
        if (current.screen == HulkScreen.LOGIN || current.account == null) return
        if (current.screen != HulkScreen.NOTIFICATION_CENTER) {
            notificationCenterReturnScreen = current.screen.takeUnless { it == HulkScreen.PLAYER }
                ?: HulkScreen.MAIN
        }
        mutableState.update {
            it.copy(
                screen = HulkScreen.NOTIFICATION_CENTER,
                notificationPopup = null,
                errorMessage = null,
            )
        }
        refreshNotificationState(clearPopup = true)
        scanSubscribedSeries(NotificationScanTrigger.NOTIFICATION_CENTER)
    }

    fun markNotificationRead(notificationId: String) {
        val item = mutableState.value.localNotifications.firstOrNull { it.id == notificationId } ?: return
        val profileId = profileStore.activeProfileId()
        val accountId = localEpisodeNotificationStore.activeAccountId()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (item) {
                    is LocalNotificationItem.Episode -> accountId?.let {
                        localEpisodeNotificationStore.markRead(profileId, notificationId, it)
                    }
                    is LocalNotificationItem.System ->
                        operationsStore.markSystemNotificationRead(notificationId)
                }
            }
            refreshNotificationState(clearPopup = false)
        }
    }

    fun markAllNotificationsRead() {
        val profileId = profileStore.activeProfileId()
        val accountId = localEpisodeNotificationStore.activeAccountId()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                accountId?.let { localEpisodeNotificationStore.markAllRead(profileId, it) }
                operationsStore.markAllSystemNotificationsRead()
            }
            refreshNotificationState(clearPopup = false)
        }
    }

    fun deleteNotification(notificationId: String) {
        val item = mutableState.value.localNotifications.firstOrNull { it.id == notificationId } ?: return
        val profileId = profileStore.activeProfileId()
        val accountId = localEpisodeNotificationStore.activeAccountId()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (item) {
                    is LocalNotificationItem.Episode -> accountId?.let {
                        localEpisodeNotificationStore.deleteNotification(profileId, notificationId, it)
                    }
                    is LocalNotificationItem.System ->
                        operationsStore.deleteSystemNotification(notificationId)
                }
            }
            refreshNotificationState(clearPopup = false)
        }
    }

    fun clearNotifications() {
        val profileId = profileStore.activeProfileId()
        val accountId = localEpisodeNotificationStore.activeAccountId()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                accountId?.let { localEpisodeNotificationStore.clearNotifications(profileId, it) }
                operationsStore.clearSystemNotifications()
            }
            refreshNotificationState(clearPopup = true)
        }
    }

    fun openNotification(
        notificationId: String,
        onResult: (String?) -> Unit = {},
    ) {
        val item = mutableState.value.localNotifications.firstOrNull { it.id == notificationId }
        if (item is LocalNotificationItem.System) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    operationsStore.markSystemNotificationRead(notificationId)
                }
                refreshNotificationState(clearPopup = false)
                onResult(null)
            }
            return
        }
        val profileId = profileStore.activeProfileId()
        val accountId = localEpisodeNotificationStore.activeAccountId()
        if (accountId == null) {
            onResult("سجل الدخول لفتح هذا المحتوى.")
            return
        }
        viewModelScope.launch {
            val notification = (item as? LocalNotificationItem.Episode)?.notification
                ?: localEpisodeNotificationStore.snapshot(profileId)
                    .notifications
                    .firstOrNull { it.id == notificationId }
            if (notification == null) {
                onResult("هذا الإشعار لم يعد متاحًا.")
                return@launch
            }
            val targetError = openNotificationTarget(notification)
            if (targetError != null) {
                onResult(targetError)
                return@launch
            }
            withContext(Dispatchers.IO) {
                localEpisodeNotificationStore.markRead(profileId, notification.id, accountId)
            }
            refreshNotificationState(clearPopup = false)
            onResult(null)
        }
    }

    fun confirmNotificationPopupPresented() {
        val popup = mutableState.value.notificationPopup ?: return
        if (!notificationUiReady) return
        persistNotificationPopupShown(popup)
    }

    fun dismissNotificationPopup() {
        val popup = mutableState.value.notificationPopup
        val persisted = popup == null || persistNotificationPopupShown(popup)
        mutableState.update { it.copy(notificationPopup = null) }
        if (persisted) maybeShowPendingNotificationPopup()
    }

    fun activateNotificationPopup(onResult: (String?) -> Unit = {}) {
        val popup = mutableState.value.notificationPopup ?: return
        persistNotificationPopupShown(popup)
        if (popup.summary) {
            mutableState.update { it.copy(notificationPopup = null) }
            openNotificationCenter()
            return
        }
        val target = popup.notifications.maxWithOrNull(
            compareBy(LocalEpisodeNotification::seasonNumber, LocalEpisodeNotification::episodeNumber),
        ) ?: return
        viewModelScope.launch {
            val targetError = openNotificationTarget(target)
            if (targetError != null) {
                mutableState.update { it.copy(notificationPopup = null) }
                onResult(targetError)
                return@launch
            }
            withContext(Dispatchers.IO) {
                popup.eventIds.forEach { id ->
                    localEpisodeNotificationStore.markRead(
                        profileId = popup.profileId,
                        notificationId = id,
                        expectedAccountId = target.accountId,
                    )
                }
            }
            mutableState.update { it.copy(notificationPopup = null) }
            refreshNotificationState(clearPopup = false)
            onResult(null)
        }
    }

    fun onProfileChanged() {
        notificationScanJob?.cancel()
        mutableState.update { it.copy(notificationPopup = null) }
        refreshNotificationState(clearPopup = true)
        scanSubscribedSeries(NotificationScanTrigger.PROFILE_SWITCH)
        scheduleTvPlatformSync(immediate = true)
    }

    fun removeNotificationProfileData(profileId: String) {
        val accountId = localEpisodeNotificationStore.activeAccountId() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            localEpisodeNotificationStore.removeProfile(profileId, accountId)
        }
    }

    fun removeDownloadProfileData(profileId: String) {
        val accountId = downloadRepository.activeAccountIdForCleanup() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.removeProfile(accountId, profileId)
        }
    }

    fun onAppResumed() {
        refreshOperations(force = false)
        scanSubscribedSeries(NotificationScanTrigger.APP_RESUME)
        scheduleTvPlatformSync(immediate = false)
        resolvePendingTvDeepLink()
    }

    fun retryOperations() {
        refreshOperations(force = true)
    }

    fun dismissOptionalOperationsUpdate() {
        val operations = mutableState.value.operations
        if (operations.updateDecision != OperationsUpdateDecision.OPTIONAL) return
        dismissedOptionalUpdateVersionCodes += operations.update.latestVersionCode
        mutableState.update { state ->
            state.copy(
                operations = state.operations.copy(
                    updateDecision = OperationsUpdateDecision.NONE,
                    download = OperationsDownloadUiState(),
                ),
            )
        }
    }

    fun startOperationsUpdate() {
        val operations = mutableState.value.operations
        if (
            operations.updateDecision == OperationsUpdateDecision.NONE ||
            operationsDownloadJob?.isActive == true
        ) return
        mutableState.update { state ->
            state.copy(
                operations = state.operations.copy(
                    download = OperationsDownloadUiState(
                        status = OperationsDownloadStatus.DOWNLOADING,
                        progressPercent = 0,
                    ),
                ),
            )
        }
        operationsDownloadJob = viewModelScope.launch {
            val result = operationsInstaller.downloadAndOpen(operations.update) { progress ->
                mutableState.update { state ->
                    if (state.operations.update.latestVersionCode != operations.update.latestVersionCode) {
                        state
                    } else {
                        state.copy(
                            operations = state.operations.copy(
                                download = state.operations.download.copy(
                                    status = OperationsDownloadStatus.DOWNLOADING,
                                    progressPercent = progress,
                                ),
                            ),
                        )
                    }
                }
            }
            mutableState.update { state ->
                if (state.operations.update.latestVersionCode != operations.update.latestVersionCode) {
                    state
                } else {
                    state.copy(
                        operations = state.operations.copy(
                            download = when (result) {
                                OperationsInstallResult.InstallerOpened -> OperationsDownloadUiState(
                                    status = OperationsDownloadStatus.INSTALLER_OPENED,
                                    progressPercent = 100,
                                    message = "تم التحقق من التحديث. أكمل التثبيت من مثبت Android.",
                                )
                                OperationsInstallResult.UnknownSourcesBlocked -> OperationsDownloadUiState(
                                    status = OperationsDownloadStatus.UNKNOWN_SOURCES_BLOCKED,
                                    message = "اسمح لـ HULK SA بتثبيت التطبيقات من هذا المصدر ثم أعد المحاولة.",
                                )
                                is OperationsInstallResult.Failure -> OperationsDownloadUiState(
                                    status = OperationsDownloadStatus.FAILED,
                                    message = result.message,
                                )
                            },
                        ),
                    )
                }
            }
        }
    }

    fun openOperationsInstallSettings(onResult: (String) -> Unit = {}) {
        onResult(
            if (operationsInstaller.openUnknownSourcesSettings()) {
                "فعّل السماح بالتثبيت ثم ارجع واضغط تحديث التطبيق."
            } else {
                "تعذر فتح إعدادات التثبيت على هذا الجهاز. افتح إعدادات الأمان يدويًا."
            },
        )
    }

    fun confirmOperationsAnnouncement() {
        val popup = mutableState.value.operations.announcementPopup ?: return
        presentedOperationsMessageIds += popup.id
        if (popup.showOnce) {
            operationsStore.acknowledgeMessage(popup.id)
        }
        updateOperationsAnnouncementPresentation()
    }

    private fun refreshOperations(force: Boolean) {
        if (operationsRefreshJob?.isActive == true) return
        val cached = operationsStore.cachedConfig()
        val nowEpochMs = System.currentTimeMillis()
        if (
            !force &&
            cached != null &&
            nowEpochMs - cached.fetchedAtEpochMs in 0 until OPERATIONS_CACHE_TTL_MS
        ) return

        operationsRefreshJob = viewModelScope.launch {
            when (val result = operationsClient.fetch()) {
                is OperationsFetchResult.Success -> {
                    if (operationsStore.saveConfig(result.rawJson, result.fetchedAtEpochMs)) {
                        applyOperationsConfig(
                            config = result.config,
                            source = OperationsConfigSource.NETWORK,
                            fetchedAtEpochMs = result.fetchedAtEpochMs,
                        )
                    } else {
                        applyOperationsFailure()
                    }
                }
                OperationsFetchResult.Failure -> applyOperationsFailure()
            }
        }
    }

    private fun applyOperationsFailure() {
        val cached = operationsStore.cachedConfig()
        when {
            cached != null -> applyOperationsConfig(
                config = cached.config,
                source = OperationsConfigSource.CACHE,
                fetchedAtEpochMs = cached.fetchedAtEpochMs,
            )
            activeOperationsConfig != null -> applyOperationsConfig(
                config = checkNotNull(activeOperationsConfig),
                source = OperationsConfigSource.CACHE,
                fetchedAtEpochMs = activeOperationsFetchedAtEpochMs,
            )
            else -> mutableState.update { state ->
                state.copy(
                    operations = state.operations.copy(
                        source = OperationsConfigSource.DEFAULT,
                        updateDecision = OperationsUpdateDecision.NONE,
                        service = state.operations.service.copy(
                            status = OperationsServiceStatus.OPERATIONAL,
                        ),
                    ),
                )
            }
        }
    }

    private fun applyOperationsConfig(
        config: OperationsConfig,
        source: OperationsConfigSource,
        fetchedAtEpochMs: Long,
    ) {
        activeOperationsConfig = config
        activeOperationsFetchedAtEpochMs = fetchedAtEpochMs
        val nowEpochMs = System.currentTimeMillis()
        val activeAnnouncements = eligibleOperationsAnnouncements(
            announcements = config.announcements,
            currentVersionCode = BuildConfig.VERSION_CODE,
            isTv = operationsDeviceIsTv,
            nowEpochSeconds = nowEpochMs / 1_000L,
            acknowledgedMessageIds = emptySet(),
            presentedMessageIds = emptySet(),
        )
        operationsStore.recordImportantAnnouncements(
            announcements = activeAnnouncements,
            generatedAtEpochSeconds = config.generatedAtEpochSeconds,
        )
        val acknowledged = operationsStore.acknowledgedMessageIds()
        val announcementPopup = eligibleOperationsAnnouncements(
            announcements = config.announcements,
            currentVersionCode = BuildConfig.VERSION_CODE,
            isTv = operationsDeviceIsTv,
            nowEpochSeconds = nowEpochMs / 1_000L,
            acknowledgedMessageIds = acknowledged,
            presentedMessageIds = presentedOperationsMessageIds,
        ).firstOrNull()
        val persistentAnnouncement = activePersistentOperationsAnnouncement(
            announcements = config.announcements,
            currentVersionCode = BuildConfig.VERSION_CODE,
            isTv = operationsDeviceIsTv,
            nowEpochSeconds = nowEpochMs / 1_000L,
        )
        var updateDecision = evaluateOperationsUpdatePolicy(
            currentVersionCode = BuildConfig.VERSION_CODE,
            update = config.update,
            source = source,
            cacheAgeMs = nowEpochMs - fetchedAtEpochMs,
        )
        if (
            updateDecision == OperationsUpdateDecision.OPTIONAL &&
            config.update.latestVersionCode in dismissedOptionalUpdateVersionCodes
        ) {
            updateDecision = OperationsUpdateDecision.NONE
        }
        val service = config.service.copy(
            status = effectiveOperationsServiceStatus(config.service, source),
        )
        val updateChanged =
            mutableState.value.operations.update.latestVersionCode != config.update.latestVersionCode
        if (updateChanged) {
            operationsDownloadJob?.cancel()
            operationsDownloadJob = null
        }

        if (!config.features.episodeNotificationsEnabled) {
            notificationScanJob?.cancel()
        }
        mutableState.update { state ->
            val safeDestination = if (
                !config.features.downloadsEnabled && state.destination == MainDestination.DOWNLOADS
            ) {
                MainDestination.HOME
            } else {
                state.destination
            }
            state.copy(
                destination = safeDestination,
                catalogs = if (safeDestination != state.destination) {
                    catalogsForDestination(safeDestination, state.catalogs)
                } else {
                    state.catalogs
                },
                notificationPopup = if (config.features.episodeNotificationsEnabled) {
                    state.notificationPopup
                } else {
                    null
                },
                operations = OperationsUiState(
                    source = source,
                    updateDecision = updateDecision,
                    update = config.update,
                    service = service,
                    features = config.features,
                    growth = config.growth,
                    announcementPopup = announcementPopup,
                    persistentAnnouncement = persistentAnnouncement,
                    download = if (updateChanged) OperationsDownloadUiState() else state.operations.download,
                ),
            )
        }
        refreshNotificationState(clearPopup = false)
    }

    private fun updateOperationsAnnouncementPresentation() {
        val config = activeOperationsConfig ?: run {
            mutableState.update { state ->
                state.copy(operations = state.operations.copy(announcementPopup = null))
            }
            return
        }
        val next = eligibleOperationsAnnouncements(
            announcements = config.announcements,
            currentVersionCode = BuildConfig.VERSION_CODE,
            isTv = operationsDeviceIsTv,
            nowEpochSeconds = System.currentTimeMillis() / 1_000L,
            acknowledgedMessageIds = operationsStore.acknowledgedMessageIds(),
            presentedMessageIds = presentedOperationsMessageIds,
        ).firstOrNull()
        mutableState.update { state ->
            state.copy(operations = state.operations.copy(announcementPopup = next))
        }
    }

    fun setNotificationUiReady(ready: Boolean) {
        val becameReady = ready && !notificationUiReady
        notificationUiReady = ready
        if (ready) {
            maybeShowPendingNotificationPopup()
            if (becameReady && profileStore.activeProfile().kind == ProfileKind.KIDS) {
                scanSubscribedSeries(NotificationScanTrigger.PROFILE_SWITCH)
            }
        } else {
            mutableState.update { it.copy(notificationPopup = null) }
        }
    }

    private fun refreshNotificationState(clearPopup: Boolean) {
        val profile = profileStore.activeProfile()
        val snapshot = localEpisodeNotificationStore.snapshot(profile.id)
        val allowedSeriesIds = if (profile.kind == ProfileKind.KIDS) {
            snapshot.subscriptions.asSequence()
                .map(EpisodeNotificationSubscription::seriesId)
                .filter { seriesId -> canUseSeriesNotifications(profile.kind, seriesId) }
                .toSet()
        } else {
            snapshot.subscriptions.mapTo(linkedSetOf(), EpisodeNotificationSubscription::seriesId)
        }
        val safeNotifications = if (profile.kind == ProfileKind.KIDS) {
            snapshot.notifications.filter { it.seriesId in allowedSeriesIds }
        } else {
            snapshot.notifications
        }
        val notificationCenterItems = mergeNotificationCenterItems(
            episodeNotifications = safeNotifications,
            systemNotifications = operationsStore.systemNotifications(),
        )
        val safeSubscribed = snapshot.subscriptions.asSequence()
            .filter(EpisodeNotificationSubscription::enabled)
            .map(EpisodeNotificationSubscription::seriesId)
            .filter { it in allowedSeriesIds }
            .toSet()
        mutableState.update { state ->
            val retainedPopup = state.notificationPopup
                ?.takeIf { !clearPopup && it.profileId == profile.id }
            state.copy(
                notificationSubscribedSeriesIds = safeSubscribed,
                localNotifications = notificationCenterItems,
                unreadNotificationCount = notificationCenterItems.count { !it.read },
                episodeNotificationsEnabled = snapshot.settings.enabled,
                notificationPopup = retainedPopup,
            )
        }
    }

    private fun openNotificationTarget(notification: LocalEpisodeNotification): String? {
        val activeSession = session ?: return "سجل الدخول لفتح هذا المحتوى."
        val profile = profileStore.activeProfile()
        if (notification.accountId != localEpisodeNotificationStore.activeAccountId()) {
            return "هذا الإشعار يخص حسابًا آخر."
        }
        if (notification.profileId != profile.id) return "هذا الإشعار يخص ملفًا شخصيًا آخر."
        if (!canUseSeriesNotifications(profile.kind, notification.seriesId)) {
            return "هذا المحتوى غير متاح في ملف الأطفال."
        }
        val series = resolveLocalNotificationSeriesTarget(
            profileKind = profile.kind,
            notification = notification,
            generalSeries = loadedCatalogs[ContentType.SERIES]?.items.orEmpty(),
        )
        openSeries(
            item = series,
            target = SeriesEpisodeTarget(
                seriesId = notification.seriesId,
                seasonNumber = notification.seasonNumber,
                episodeNumber = notification.episodeNumber,
                episodeId = notification.episodeId,
            ),
            activeSession = activeSession,
        )
        return null
    }

    private suspend fun processOpenedSeriesForNotifications(
        series: ContentItem,
        episodes: List<Episode>,
        expectedAccountId: String?,
        expectedProfileId: String,
        expectedProfileKind: ProfileKind,
    ) {
        if (!mutableState.value.operations.features.episodeNotificationsEnabled) return
        if (
            expectedAccountId == null ||
            localEpisodeNotificationStore.activeAccountId() != expectedAccountId ||
            profileStore.activeProfileId() != expectedProfileId
        ) return
        val snapshot = localEpisodeNotificationStore.snapshot(expectedProfileId)
        val subscription = snapshot.subscriptions.firstOrNull {
            it.enabled && it.seriesId == series.id
        } ?: return
        if (!snapshot.settings.enabled) return
        if (!canUseSeriesNotifications(expectedProfileKind, series.id)) return

        val checkedAt = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            if (snapshot.settings.baselineRefreshRequired) {
                localEpisodeNotificationStore.replaceBaseline(
                    profileId = expectedProfileId,
                    seriesId = series.id,
                    episodes = episodes,
                    checkedAtEpochMs = checkedAt,
                    expectedAccountId = subscription.accountId,
                )
            } else {
                localEpisodeNotificationStore.recordSuccessfulScan(
                    profileId = expectedProfileId,
                    seriesId = series.id,
                    episodes = episodes,
                    detectedAtEpochMs = checkedAt,
                    batchId = UUID.randomUUID().toString(),
                    expectedAccountId = subscription.accountId,
                )
            }
        }
        if (
            localEpisodeNotificationStore.activeAccountId() == expectedAccountId &&
            profileStore.activeProfileId() == expectedProfileId
        ) {
            refreshNotificationState(clearPopup = false)
            maybeShowPendingNotificationPopup()
        }
    }

    private fun scanSubscribedSeries(trigger: NotificationScanTrigger) {
        if (!mutableState.value.operations.features.episodeNotificationsEnabled) return
        val activeSession = session ?: return
        val profile = profileStore.activeProfile()
        val accountId = localEpisodeNotificationStore.activeAccountId() ?: return
        val snapshot = localEpisodeNotificationStore.snapshot(profile.id)
        if (
            !snapshot.settings.enabled ||
            notificationScanJob?.isActive == true ||
            (profile.kind == ProfileKind.KIDS && !notificationUiReady)
        ) return

        notificationScanJob = viewModelScope.launch {
            if (snapshot.settings.baselineRefreshRequired) {
                refreshMasterNotificationBaselines(activeSession, accountId, profile.id, profile.kind)
                return@launch
            }
            val now = System.currentTimeMillis()
            val eligible = snapshot.subscriptions.filter { subscription ->
                val lastCheckedAt = subscription.lastCheckedAtEpochMs
                subscription.enabled && (
                    lastCheckedAt <= 0L ||
                        now < lastCheckedAt ||
                        now - lastCheckedAt >= trigger.minimumAgeMs
                    )
            }
            if (eligible.isEmpty()) {
                if (profileStore.activeProfileId() == profile.id) maybeShowPendingNotificationPopup()
                return@launch
            }

            val batchId = UUID.randomUUID().toString()
            coroutineScope {
                eligible.map { subscription ->
                    async {
                        episodeNotificationScanSemaphore.withPermit {
                            if (!canUseSeriesNotifications(profile.kind, subscription.seriesId)) {
                                return@withPermit
                            }
                            try {
                                val bundle = repository.seriesBundle(activeSession, subscription.seriesId)
                                if (!canUseSeriesNotifications(profile.kind, subscription.seriesId)) {
                                    return@withPermit
                                }
                                withContext(Dispatchers.IO) {
                                    localEpisodeNotificationStore.recordSuccessfulScan(
                                        profileId = profile.id,
                                        seriesId = subscription.seriesId,
                                        episodes = bundle.episodes,
                                        detectedAtEpochMs = System.currentTimeMillis(),
                                        batchId = batchId,
                                        expectedAccountId = subscription.accountId,
                                    )
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                // A failed request must leave both the baseline and event history unchanged.
                            }
                        }
                    }
                }.awaitAll()
            }
            if (profileStore.activeProfileId() == profile.id) {
                refreshNotificationState(clearPopup = false)
                maybeShowPendingNotificationPopup()
            }
        }
    }

    private suspend fun refreshMasterNotificationBaselines(
        activeSession: AuthenticatedSession,
        accountId: String,
        profileId: String,
        profileKind: ProfileKind,
    ) {
        val subscriptions = localEpisodeNotificationStore.snapshot(profileId)
            .subscriptions
            .filter(EpisodeNotificationSubscription::enabled)
            .filter { subscription ->
                canUseSeriesNotifications(profileKind, subscription.seriesId)
            }
        val results = coroutineScope {
            subscriptions.map { subscription ->
                async {
                    episodeNotificationScanSemaphore.withPermit {
                        try {
                            val episodes = repository.seriesBundle(activeSession, subscription.seriesId).episodes
                            if (!canUseSeriesNotifications(profileKind, subscription.seriesId)) {
                                return@withPermit false
                            }
                            withContext(Dispatchers.IO) {
                                localEpisodeNotificationStore.replaceBaseline(
                                    profileId = profileId,
                                    seriesId = subscription.seriesId,
                                    episodes = episodes,
                                    checkedAtEpochMs = System.currentTimeMillis(),
                                    expectedAccountId = subscription.accountId,
                                ) == EpisodeNotificationStoreResult.SUCCESS
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            false
                        }
                    }
                }
            }.awaitAll()
        }
        if (results.all { it }) {
            withContext(Dispatchers.IO) {
                localEpisodeNotificationStore.completeMasterBaselineRefresh(
                    profileId = profileId,
                    expectedAccountId = accountId,
                )
            }
        }
        if (profileStore.activeProfileId() == profileId) {
            refreshNotificationState(clearPopup = false)
            maybeShowPendingNotificationPopup()
        }
    }

    private fun maybeShowPendingNotificationPopup() {
        val state = mutableState.value
        if (
            !notificationUiReady ||
            state.notificationPopup != null ||
            !state.episodeNotificationsEnabled ||
            !state.operations.features.episodeNotificationsEnabled ||
            state.screen == HulkScreen.PLAYER ||
            state.screen == HulkScreen.LOGIN ||
            state.screen == HulkScreen.NOTIFICATION_CENTER
        ) return
        val profileId = profileStore.activeProfileId()
        val episodeNotifications = state.localNotifications.mapNotNull { item ->
            (item as? LocalNotificationItem.Episode)?.notification
        }
        val popup = buildEpisodeNotificationPopups(episodeNotifications).firstOrNull()
            ?.takeIf { it.profileId == profileId }
            ?: return
        mutableState.update { it.copy(notificationPopup = popup) }
    }

    private fun persistNotificationPopupShown(popup: EpisodeNotificationPopup): Boolean {
        val profileId = profileStore.activeProfileId()
        if (popup.profileId != profileId) return false
        val accountId = popup.notifications.firstOrNull()?.accountId ?: return false
        if (!localEpisodeNotificationStore.markPopupShown(profileId, popup.eventIds, accountId)) return false
        refreshNotificationState(clearPopup = false)
        return true
    }

    private fun canUseSeriesNotifications(profileKind: ProfileKind, seriesId: Int): Boolean =
        canUseSeriesEpisodeNotifications(
            profileKind = profileKind,
            seriesId = seriesId,
            verifiedKidsContentKeys = kidsContentFilterStore.allowedKeys(),
        )

    fun clearHistory() {
        mutableState.update { it.copy(history = userLibrary.clearHistory()) }
        tvLastSyncedPositions.clear()
        scheduleTvPlatformSync(immediate = true)
    }

    fun refreshAccount(onResult: (String) -> Unit) {
        val activeSession = session ?: run {
            onResult("سجل الدخول اولا لتحديث بيانات الاشتراك.")
            return
        }
        if (accountRefreshJob?.isActive == true) {
            onResult("تحديث بيانات الاشتراك يعمل حاليا.")
            return
        }
        mutableState.update { it.copy(isAccountRefreshing = true, errorMessage = null) }
        accountRefreshJob = viewModelScope.launch {
            runCatching { repository.reauthenticate(activeSession) }
                .onSuccess { refreshed ->
                    session = refreshed
                    mutableState.update {
                        it.copy(
                            account = refreshed.account,
                            isAccountRefreshing = false,
                            errorMessage = null,
                        )
                    }
                    onResult("تم تحديث بيانات الاشتراك من السيرفر.")
                }
                .onFailure { error ->
                    mutableState.update { it.copy(isAccountRefreshing = false) }
                    val invalidSession = error is XtreamException.InvalidCredentials ||
                        error is XtreamException.SubscriptionInactive ||
                        error is PortalException.InvalidAccessCode ||
                        error is PortalException.ResellerInactive
                    if (invalidSession) showFailure(error)
                    onResult(
                        if (invalidSession) {
                            "تعذر تحديث الاشتراك. اعد تسجيل الدخول."
                        } else {
                            error.message ?: "تعذر تحديث بيانات الاشتراك."
                        },
                    )
                }
        }
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
            HulkScreen.PLAYER -> {
                mutableState.update {
                    it.copy(screen = playerReturnScreen, playback = null, errorMessage = null)
                }
                scheduleTvPlatformSync(immediate = true)
                maybeShowPendingNotificationPopup()
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
                    seriesEpisodeTarget = null,
                    errorMessage = null,
                )
            }
            HulkScreen.NOTIFICATION_CENTER -> {
                mutableState.update {
                    it.copy(screen = notificationCenterReturnScreen, errorMessage = null)
                }
                maybeShowPendingNotificationPopup()
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
        val operationsState = mutableState.value.operations
        beginTvPlatformProfileTransition(resetPublishedScope = true)
        pendingTvDeepLink = null
        mutableState.update { it.copy(downloads = emptyList()) }
        downloadRepository.suspendActiveAccountForLogout()
        repository.logout()
        session = null
        sessionRestorationComplete = true
        notificationScanJob?.cancel()
        notificationScanJob = null
        notificationUiReady = false
        clearCatalogMemory()
        detailsJob?.cancel()
        accountRefreshJob?.cancel()
        diagnosticsJob?.cancel()
        catalogJobs.values.forEach(Job::cancel)
        catalogJobs.clear()
        mutableState.value = HulkUiState(
            isStarting = false,
            favorites = userLibrary.favorites(),
            history = userLibrary.history(),
            downloads = emptyList(),
            downloadSettings = downloadRepository.settings(),
            notificationSubscribedSeriesIds = emptySet(),
            localNotifications = emptyList(),
            unreadNotificationCount = 0,
            episodeNotificationsEnabled = true,
            notificationPopup = null,
            operations = operationsState,
        )
    }

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    private fun restoreSession() {
        val credentials = repository.savedCredentials()
        if (credentials == null) {
            sessionRestorationComplete = true
            mutableState.update { it.copy(isStarting = false) }
            resolvePendingTvDeepLink()
            return
        }
        authenticate(credentials, remember = true, restoringSession = true)
    }

    private fun authenticate(
        credentials: Credentials,
        remember: Boolean,
        restoringSession: Boolean = false,
    ) {
        if (restoringSession) sessionRestorationComplete = false
        mutableState.update {
            it.copy(isStarting = false, isLoading = true, errorMessage = null)
        }
        viewModelScope.launch {
            runCatching { repository.login(credentials, remember) }
                .onSuccess { authenticated ->
                    session = authenticated
                    sessionRestorationComplete = true
                    clearCatalogMemory()
                    mutableState.update {
                        it.copy(
                            screen = HulkScreen.MAIN,
                            destination = MainDestination.HOME,
                            isLoading = false,
                            account = authenticated.account,
                            isAccountRefreshing = false,
                            catalogs = emptyMap(),
                            selectedCategoryId = null,
                            searchQuery = "",
                            downloads = downloadRepository.downloads(),
                            downloadSettings = downloadRepository.settings(),
                            errorMessage = null,
                        )
                    }
                    ensureCatalog(ContentType.MOVIE)
                    ensureCatalog(ContentType.SERIES)
                    refreshOperations(force = false)
                    refreshNotificationState(clearPopup = true)
                    scanSubscribedSeries(NotificationScanTrigger.APP_START)
                    resolvePendingTvDeepLink()
                }
                .onFailure { error ->
                    sessionRestorationComplete = true
                    showFailure(error)
                    resolvePendingTvDeepLink()
                }
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
                    resolvePendingTvDeepLink()
                    scheduleTvPlatformSync(immediate = false)
                }
                .onFailure { error ->
                    mutableState.update { it.copy(loadingTypes = it.loadingTypes - type) }
                    showFailure(error)
                }
        }
    }

    private fun resolvePendingTvDeepLink() {
        val target = pendingTvDeepLink ?: return
        if (!operationsDeviceIsTv) return
        val profile = profileStore.activeProfile()
        when (
            decideTvDeepLinkDispatch(
                sessionRestorationComplete = sessionRestorationComplete,
                hasSession = session != null,
                profileResolved = tvPlatformProfileReady,
                kidsVerificationRequired = profile.kind == ProfileKind.KIDS,
                kidsVerified = tvPlatformProfileReady,
            )
        ) {
            TvDeepLinkDispatchDecision.WAIT_FOR_SESSION_RESTORATION,
            TvDeepLinkDispatchDecision.SHOW_LOGIN,
            TvDeepLinkDispatchDecision.WAIT_FOR_PROFILE,
            -> return
            TvDeepLinkDispatchDecision.DISPATCH -> Unit
        }
        val activeSession = session ?: return
        val activeProfileScopeId = tvPlatformIntegration.activeProfileScope()?.providerScopeId
            ?: return failPendingTvDeepLink("تعذر تحديد الملف الشخصي النشط.")
        when (
            val resolution = resolveTvDeepLink(
                target = target,
                movieCatalog = loadedCatalogs[ContentType.MOVIE],
                seriesCatalog = loadedCatalogs[ContentType.SERIES],
                history = mutableState.value.history,
                profileKind = profile.kind,
                verifiedKidsContentKeys = kidsContentFilterStore.allowedKeys(),
                activeProfileScopeId = activeProfileScopeId,
            )
        ) {
            TvDeepLinkResolution.OpenHome -> {
                pendingTvDeepLink = null
                tvDeepLinkEpisodeJob?.cancel()
                tvDeepLinkEpisodeJob = null
                openSafeTvHome(message = null)
            }
            is TvDeepLinkResolution.AwaitCatalog -> ensureCatalog(resolution.type)
            is TvDeepLinkResolution.OpenMovie -> {
                pendingTvDeepLink = null
                tvDeepLinkEpisodeJob?.cancel()
                tvDeepLinkEpisodeJob = null
                if (resolution.resumePlayback) {
                    playerReturnScreen = HulkScreen.MAIN
                    startPlayback(repository.playback(activeSession, resolution.item))
                } else {
                    open(resolution.item)
                }
            }
            is TvDeepLinkResolution.OpenSeries -> {
                pendingTvDeepLink = null
                tvDeepLinkEpisodeJob?.cancel()
                tvDeepLinkEpisodeJob = null
                openSeries(item = resolution.item, target = null, activeSession = activeSession)
            }
            is TvDeepLinkResolution.OpenEpisode -> {
                if (resolution.resumePlayback) {
                    openResumeEpisodeFromTvLink(
                        target = target,
                        resolution = resolution,
                        activeSession = activeSession,
                        expectedProfileId = profile.id,
                        expectedProfileScopeId = activeProfileScopeId,
                    )
                } else {
                    pendingTvDeepLink = null
                    tvDeepLinkEpisodeJob?.cancel()
                    tvDeepLinkEpisodeJob = null
                    openSeries(
                        item = resolution.series,
                        target = SeriesEpisodeTarget(
                            seriesId = resolution.series.id,
                            seasonNumber = 0,
                            episodeNumber = 0,
                            episodeId = resolution.episodeId,
                        ),
                        activeSession = activeSession,
                    )
                }
            }
            TvDeepLinkResolution.MissingContent -> failPendingTvDeepLink(
                "هذا المحتوى لم يعد متاحًا.",
            )
            TvDeepLinkResolution.BlockedForKids -> failPendingTvDeepLink(
                "هذا المحتوى غير متاح في ملف الأطفال.",
            )
            TvDeepLinkResolution.StaleProfile -> failPendingTvDeepLink(
                "هذا العنصر يخص ملفًا شخصيًا آخر.",
            )
        }
    }

    private fun openResumeEpisodeFromTvLink(
        target: TvDeepLinkTarget,
        resolution: TvDeepLinkResolution.OpenEpisode,
        activeSession: AuthenticatedSession,
        expectedProfileId: String,
        expectedProfileScopeId: String,
    ) {
        if (tvDeepLinkEpisodeJob?.isActive == true) return
        tvDeepLinkEpisodeJob = viewModelScope.launch {
            try {
                val bundle = repository.seriesBundle(activeSession, resolution.series.id)
                if (
                    pendingTvDeepLink != target ||
                    session !== activeSession ||
                    !tvPlatformProfileReady ||
                    profileStore.activeProfileId() != expectedProfileId ||
                    tvPlatformIntegration.activeProfileScope()?.providerScopeId != expectedProfileScopeId
                ) return@launch

                val rechecked = resolveTvDeepLink(
                    target = target,
                    movieCatalog = loadedCatalogs[ContentType.MOVIE],
                    seriesCatalog = loadedCatalogs[ContentType.SERIES],
                    history = mutableState.value.history,
                    profileKind = profileStore.activeProfile().kind,
                    verifiedKidsContentKeys = kidsContentFilterStore.allowedKeys(),
                    activeProfileScopeId = expectedProfileScopeId,
                )
                if (rechecked == TvDeepLinkResolution.BlockedForKids) {
                    failPendingTvDeepLink("هذا المحتوى غير متاح في ملف الأطفال.")
                    return@launch
                }
                if (rechecked == TvDeepLinkResolution.StaleProfile) {
                    failPendingTvDeepLink("هذا العنصر يخص ملفًا شخصيًا آخر.")
                    return@launch
                }
                if (rechecked !is TvDeepLinkResolution.OpenEpisode) {
                    failPendingTvDeepLink("هذا المحتوى لم يعد متاحًا.")
                    return@launch
                }
                val episode = findTvDeepLinkEpisode(bundle.episodes, resolution.episodeId)
                    ?: run {
                        failPendingTvDeepLink("هذا المحتوى لم يعد متاحًا.")
                        return@launch
                    }
                pendingTvDeepLink = null
                playerReturnScreen = HulkScreen.MAIN
                startPlayback(repository.playback(activeSession, resolution.series, episode))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (pendingTvDeepLink == target) {
                    failPendingTvDeepLink("تعذر فتح هذا المحتوى الآن.")
                }
            }
        }
    }

    private fun failPendingTvDeepLink(message: String) {
        pendingTvDeepLink = null
        openSafeTvHome(message)
    }

    private fun openSafeTvHome(message: String?) {
        detailsJob?.cancel()
        detailsJob = null
        playerReturnScreen = HulkScreen.MAIN
        mutableState.update {
            it.copy(
                screen = HulkScreen.MAIN,
                destination = MainDestination.HOME,
                selectedItem = null,
                selectedSeries = null,
                selectedDetails = null,
                episodes = emptyList(),
                seriesEpisodeTarget = null,
                playback = null,
                isLoading = false,
                errorMessage = message,
            )
        }
        ensureDestinationCatalogs(MainDestination.HOME)
    }

    private fun scheduleTvPlatformSync(immediate: Boolean) {
        if (
            !operationsDeviceIsTv ||
            !tvPlatformProfileReady ||
            session == null
        ) return
        val expectedProfileScopeId = tvExpectedProfileScopeId ?: return
        val expectedGeneration = tvPlatformGeneration
        val pendingClear = tvPlatformClearJob
        tvPlatformSyncJob?.cancel()
        tvPlatformSyncJob = viewModelScope.launch {
            pendingClear?.join()
            if (!immediate) delay(TV_PLATFORM_SYNC_DEBOUNCE_MS)
            if (
                tvPlatformProfileReady &&
                session != null &&
                tvPlatformGeneration == expectedGeneration &&
                tvExpectedProfileScopeId == expectedProfileScopeId &&
                tvPlatformIntegration.activeProfileScope()?.providerScopeId == expectedProfileScopeId
            ) {
                val result = tvPlatformIntegration.syncActiveProfile(
                    expectedProfileScopeId = expectedProfileScopeId,
                    kidsVerified = true,
                    landscapeArtworkByContentKey = tvLandscapeArtworkSnapshot(),
                )
                if (
                    result is TvPlatformSyncResult.Synced &&
                    tvPlatformGeneration == expectedGeneration &&
                    tvExpectedProfileScopeId == expectedProfileScopeId
                ) {
                    tvPublishedProfileScopeId = expectedProfileScopeId
                }
            }
        }
    }

    private fun beginTvPlatformProfileTransition(resetPublishedScope: Boolean) {
        if (!operationsDeviceIsTv) return
        tvPlatformGeneration++
        tvPlatformProfileReady = false
        tvExpectedProfileScopeId = null
        tvLastSyncedPositions.clear()
        tvDeepLinkEpisodeJob?.cancel()
        tvDeepLinkEpisodeJob = null
        clearTvPlatformPrograms(resetPublishedScope)
    }

    private fun clearTvPlatformPrograms(resetPublishedScope: Boolean) {
        if (!operationsDeviceIsTv) return
        tvPlatformSyncJob?.cancel()
        tvPlatformSyncJob = null
        if (resetPublishedScope) tvPublishedProfileScopeId = null
        if (tvPlatformClearJob?.isActive == true) return
        tvPlatformClearJob = viewModelScope.launch {
            tvPlatformIntegration.clearUserContent()
        }
    }

    private fun tvLandscapeArtworkSnapshot(): Map<String, String> = buildMap {
        loadedCatalogs[ContentType.MOVIE]?.items.orEmpty().forEach { item ->
            item.backdropUrl?.trim()?.takeIf(String::isNotEmpty)?.let { backdrop ->
                put("MOVIE:${item.id}", backdrop)
            }
        }
        loadedCatalogs[ContentType.SERIES]?.items.orEmpty().forEach { item ->
            item.backdropUrl?.trim()?.takeIf(String::isNotEmpty)?.let { backdrop ->
                put("SERIES:${item.id}", backdrop)
            }
        }
    }

    private fun startPlayback(request: PlaybackRequest) {
        if (operationsDeviceIsTv && !request.isLive) {
            tvLastSyncedPositions.remove(request.historyKey)
        }
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
            error is XtreamException.SubscriptionInactive ||
            error is PortalException.InvalidAccessCode ||
            error is PortalException.ResellerInactive
        if (invalidSession) {
            sessionRestorationComplete = true
            beginTvPlatformProfileTransition(resetPublishedScope = true)
            mutableState.update { it.copy(downloads = emptyList()) }
            downloadRepository.suspendActiveAccountForLogout()
            repository.logout()
            session = null
            notificationScanJob?.cancel()
            notificationScanJob = null
            notificationUiReady = false
            clearCatalogMemory()
        }
        mutableState.update {
            it.copy(
                screen = if (invalidSession) HulkScreen.LOGIN else it.screen,
                isStarting = false,
                isLoading = false,
                isAccountRefreshing = false,
                notificationPopup = if (invalidSession) null else it.notificationPopup,
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
        private const val EPISODE_NOTIFICATION_SCAN_CONCURRENCY = 3
        private const val TV_PLATFORM_SYNC_DEBOUNCE_MS = 350L
    }
}
