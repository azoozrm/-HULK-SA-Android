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
import sa.hulksa.player.data.AuthenticatedSessionOwner
import sa.hulksa.player.data.AuthenticatedSessionRegistry
import sa.hulksa.player.data.ContentMetadataRequestKey
import sa.hulksa.player.data.EpisodeNotificationPopup
import sa.hulksa.player.data.EpisodeNotificationSubscription
import sa.hulksa.player.data.EpisodeNotificationStoreResult
import sa.hulksa.player.data.HulkRepository
import sa.hulksa.player.data.HomeHeroMetadataStore
import sa.hulksa.player.data.HomeHeroTechnicalMetadata
import sa.hulksa.player.data.KidsContentFilterStore
import sa.hulksa.player.data.LocalEpisodeNotification
import sa.hulksa.player.data.LocalEpisodeNotificationStore
import sa.hulksa.player.data.LocalNotificationItem
import sa.hulksa.player.data.LocalSystemNotification
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
import sa.hulksa.player.data.ProfileDownloadPauseOutcome
import sa.hulksa.player.data.ProfileStore
import sa.hulksa.player.data.UserLibrary
import sa.hulksa.player.data.XtreamException
import sa.hulksa.player.data.buildEpisodeNotificationPopups
import sa.hulksa.player.data.canUseSeriesEpisodeNotifications
import sa.hulksa.player.data.downloadOwnerContextMatches
import sa.hulksa.player.data.downloadRemovalContextMatches
import sa.hulksa.player.data.effectiveOperationsServiceStatus
import sa.hulksa.player.data.eligibleOperationsAnnouncements
import sa.hulksa.player.data.evaluateOperationsUpdatePolicy
import sa.hulksa.player.data.mergeNotificationCenterItems
import sa.hulksa.player.data.normalizeResellerAccessCode
import sa.hulksa.player.data.reliableEpisodeKeys
import sa.hulksa.player.data.resolveLocalNotificationSeriesTarget
import sa.hulksa.player.data.contentMetadataKey
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
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.tv.TvDeepLinkDispatchDecision
import sa.hulksa.player.tv.TvDeepLinkResolution
import sa.hulksa.player.tv.TvDeepLinkRouter
import sa.hulksa.player.tv.TvDeepLinkTarget
import sa.hulksa.player.tv.TvPlatformIntegration
import sa.hulksa.player.tv.TvPlatformIntegrationProvider
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

internal data class VoiceSearchOwner(
    val accountId: String,
    val sessionId: String,
    val profileId: String,
    val contextGeneration: Long,
)

private data class SeriesNotificationToggleOwner(
    val authenticatedSession: AuthenticatedSessionOwner,
    val detailsRequest: DetailsRequestGate.Token,
    val accountId: String,
    val profileId: String,
    val profileKind: ProfileKind,
    val seriesId: Int,
)

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
    val isProfileLibraryReady: Boolean = false,
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

private fun Application.isTelevisionDevice(): Boolean {
    val uiMode = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEVISION)
}

internal suspend fun loadDownloadUiSnapshot(
    snapshotLoader: () -> List<OfflineDownload>,
): List<OfflineDownload> = withContext(Dispatchers.IO) {
    snapshotLoader()
}

internal suspend fun runDownloadRemovalOffMain(
    removal: () -> List<OfflineDownload>,
): List<OfflineDownload> = withContext(Dispatchers.IO) {
    removal()
}

internal suspend fun <T> runDownloadEnqueueOffMain(
    enqueue: () -> T,
): T = withContext(Dispatchers.IO) {
    enqueue()
}

internal suspend fun runDownloadPauseOffMain(
    pause: () -> ProfileDownloadPauseOutcome,
): ProfileDownloadPauseOutcome = withContext(Dispatchers.IO) {
    pause()
}

internal suspend fun <T> runOperationsPersistenceOffMain(
    persistence: () -> T,
): T = withContext(Dispatchers.IO) {
    persistence()
}

internal fun downloadEnqueuePublicationAllowed(
    sameSession: Boolean,
    expectedAccountId: String,
    expectedProfileId: String,
    activeAccountId: String?,
    activeProfileId: String,
): Boolean =
    sameSession &&
        downloadOwnerContextMatches(
            expectedAccountId = expectedAccountId,
            expectedProfileId = expectedProfileId,
            activeAccountId = activeAccountId,
            activeProfileId = activeProfileId,
        )

internal fun seriesNotificationPublicationAllowed(
    sameAuthenticatedSession: Boolean,
    detailsGenerationCurrent: Boolean,
    expectedAccountId: String,
    activeAccountId: String?,
    expectedProfileId: String,
    activeProfileId: String,
    screen: HulkScreen,
    expectedSeriesId: Int,
    selectedSeriesId: Int?,
): Boolean =
    sameAuthenticatedSession &&
        detailsGenerationCurrent &&
        activeAccountId == expectedAccountId &&
        activeProfileId == expectedProfileId &&
        screen == HulkScreen.SERIES &&
        selectedSeriesId == expectedSeriesId

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
    private val tvPlatformIntegration = TvPlatformIntegrationProvider {
        TvPlatformIntegration(application)
    }
    private val initialNotificationSnapshot = localEpisodeNotificationStore.snapshot(
        profileStore.activeProfileId(),
    )
    private val mutableState = MutableStateFlow(
        HulkUiState(
            downloads = emptyList(),
            downloadSettings = DownloadSettings(),
            notificationSubscribedSeriesIds = initialNotificationSnapshot.subscribedSeriesIds,
            localNotifications = mergeNotificationCenterItems(
                initialNotificationSnapshot.notifications,
                emptyList(),
            ),
            unreadNotificationCount = initialNotificationSnapshot.unreadCount,
            episodeNotificationsEnabled = initialNotificationSnapshot.settings.enabled,
            operations = OperationsUiState(),
        ),
    )
    val state: StateFlow<HulkUiState> = mutableState.asStateFlow()

    private var session: AuthenticatedSession? = null
    private var sessionRestorationComplete: Boolean = false
    private val authenticationAttemptGate = AuthenticationAttemptGate()
    private val accountRefreshCoordinator = AccountRefreshCoordinator()
    private val diagnosticsCoordinator = DiagnosticsCoordinator()
    private val profileLibraryStartupGate = ProfileLibraryStartupGate()
    private val detailsRequestGate = DetailsRequestGate()
    private var loginJob: Job? = null
    private var logoutJob: Job? = null
    private val catalogJobs = mutableMapOf<ContentType, Job>()
    private var catalogGeneration: Long = 0L
    private val loadedCatalogs = mutableMapOf<ContentType, Catalog>()
    private val homeCatalogs = mutableMapOf<ContentType, Catalog>()
    private val selectedCategoryByType = mutableMapOf<ContentType, String?>()
    private var detailsJob: Job? = null
    private var accountRefreshJob: Job? = null
    private var diagnosticsJob: Job? = null
    private var profileLibraryRefreshJob: Job? = null
    private var playerProgressPersistenceJob: Job? = null
    private var notificationScanJob: Job? = null
    private var notificationPopupActionJob: Job? = null
    private var operationsRefreshJob: Job? = null
    private var operationsDownloadJob: Job? = null
    private var operationsAnnouncementConfirmationJob: Job? = null
    private var tvPlatformSyncJob: Job? = null
    private var tvPlatformClearJob: Job? = null
    private var tvDeepLinkEpisodeJob: Job? = null
    private val pendingDownloadEnqueues = mutableSetOf<String>()
    private var tvPlatformProfileReady: Boolean = false
    private var tvPlatformGeneration: Long = 0L
    private var tvExpectedProfileScopeId: String? = null
    private var tvPublishedProfileScopeId: String? = null
    private val tvLastSyncedPositions = mutableMapOf<String, Long>()
    private var pendingTvDeepLink: TvDeepLinkTarget? = null
    private var activeOperationsConfig: OperationsConfig? = null
    private var activeOperationsFetchedAtEpochMs: Long = 0L
    private var operationsSystemNotifications: List<LocalSystemNotification> = emptyList()
    private val presentedOperationsMessageIds = linkedSetOf<String>()
    private val dismissedOptionalUpdateVersionCodes = linkedSetOf<Int>()
    private var notificationUiReady: Boolean = false
    private val mutableVoiceSearchContextGeneration = MutableStateFlow(0L)
    internal val voiceSearchContextGeneration: StateFlow<Long> =
        mutableVoiceSearchContextGeneration.asStateFlow()
    private var notificationCenterReturnScreen = HulkScreen.MAIN
    private var playerReturnScreen = HulkScreen.MAIN
    private val contentMetadataStore = HomeHeroMetadataStore.get(application)
    private val movieCardProbeSemaphore = Semaphore(MOVIE_CARD_PROBE_CONCURRENCY)
    private val episodeNotificationScanSemaphore = Semaphore(EPISODE_NOTIFICATION_SCAN_CONCURRENCY)
    private val movieCardProbeCallbacks =
        mutableMapOf<ContentMetadataRequestKey, MutableList<(String?, Long?) -> Unit>>()
    private val movieCardProbeInFlight = mutableSetOf<ContentMetadataRequestKey>()
    private val movieCardProbeAttempted = mutableSetOf<ContentMetadataRequestKey>()
    private val movieCardProbeJobs = mutableMapOf<ContentMetadataRequestKey, Job>()

    init {
        refreshOperations(force = true)
        restoreSession()
        viewModelScope.launch {
            while (isActive) {
                val downloads = loadDownloadUiSnapshot(downloadRepository::snapshot)
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
        if (logoutJob?.isActive == true || authenticationAttemptGate.isActive()) return
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

        val scope = tvPlatformIntegration.get().activeProfileScope()
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

        invalidateVoiceSearchContext()

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

    fun refreshProfileLibrary(
        expectedAccountId: String,
        profile: UserProfile,
    ) {
        val activeSession = session ?: return
        invalidatePlayerProgressPersistence()
        profileLibraryRefreshJob?.cancel()
        val attempt = profileLibraryStartupGate.start(
            session = activeSession,
            accountId = expectedAccountId,
            profileId = profile.id,
        )
        mutableState.update {
            it.copy(
                favorites = emptySet(),
                history = emptyList(),
                isProfileLibraryReady = false,
                errorMessage = null,
            )
        }
        profileLibraryRefreshJob = viewModelScope.launch {
            val snapshot = try {
                userLibrary.initializeForProfile(
                    expectedAccountId = expectedAccountId,
                    profile = profile,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (profileLibraryStartupGate.completeIfCurrent(attempt, session)) {
                    mutableState.update {
                        it.copy(
                            favorites = emptySet(),
                            history = emptyList(),
                            isProfileLibraryReady = true,
                            errorMessage = "تعذر تهيئة مكتبة هذا الملف الشخصي بأمان.",
                        )
                    }
                }
                return@launch
            } ?: return@launch

            if (!profileLibraryStartupGate.completeIfCurrent(attempt, session)) return@launch
            val favorites = snapshot.favorites
            val history = snapshot.history
            mutableState.update {
                it.copy(
                    favorites = favorites,
                    history = history,
                    isProfileLibraryReady = true,
                    errorMessage = null,
                )
            }
            refreshNotificationState(clearPopup = false)
            scanSubscribedSeries(NotificationScanTrigger.LIBRARY_REFRESH)

            val sourceCatalogs = loadedCatalogs
                .filterKeys { it == ContentType.MOVIE || it == ContentType.SERIES }
                .toMap()
            if (sourceCatalogs.isEmpty()) return@launch
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

    internal fun currentVoiceSearchOwner(): VoiceSearchOwner? {
        val currentState = mutableState.value
        if (
            currentState.screen != HulkScreen.MAIN ||
            currentState.account == null ||
            currentState.destination != MainDestination.SEARCH ||
            !notificationUiReady
        ) return null
        val authenticatedOwner = AuthenticatedSessionRegistry.currentOwner() ?: return null
        if (session !== authenticatedOwner.session) return null
        val activeAccountId = repository.activeAccountSession()?.accountId ?: return null
        if (activeAccountId != authenticatedOwner.accountId) return null
        return VoiceSearchOwner(
            accountId = activeAccountId,
            sessionId = authenticatedOwner.sessionId,
            profileId = profileStore.activeProfileId(),
            contextGeneration = mutableVoiceSearchContextGeneration.value,
        )
    }

    internal fun updateSearchFromVoice(owner: VoiceSearchOwner, query: String) {
        if (currentVoiceSearchOwner() == owner) updateSearch(query)
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

        val owner = contentMetadataStore.currentOwner()
        val cached = contentMetadataStore.cached(owner, item)
        if (
            owner != null &&
            contentMetadataStore.isCurrent(owner) &&
            cached.quality != null &&
            cached.durationMs != null
        ) {
            onResult(cached.quality, cached.durationMs)
            return
        }
        if (owner == null) {
            onResult(cached.quality, cached.durationMs)
            return
        }

        val requestKey = owner.contentMetadataKey(ContentType.MOVIE, item.id)
        movieCardProbeCallbacks.getOrPut(requestKey) { mutableListOf() }.add(onResult)
        if (requestKey in movieCardProbeInFlight) return

        if (requestKey in movieCardProbeAttempted) {
            notifyMovieCardProbeCallbacks(requestKey, cached)
            return
        }

        movieCardProbeAttempted += requestKey
        movieCardProbeInFlight += requestKey

        movieCardProbeJobs[requestKey] = viewModelScope.launch {
            try {
                val fetched = try {
                    movieCardProbeSemaphore.withPermit {
                        contentMetadataStore.metadata(owner, item)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    contentMetadataStore.cached(owner, item)
                }

                val published = AuthenticatedSessionRegistry.withCurrentOwner(owner) {
                    notifyMovieCardProbeCallbacks(requestKey, fetched)
                    true
                } ?: false
                if (!published) {
                    movieCardProbeCallbacks.remove(requestKey)
                }
            } finally {
                movieCardProbeInFlight -= requestKey
                movieCardProbeJobs.remove(requestKey)
                if (!contentMetadataStore.isCurrent(owner)) {
                    movieCardProbeCallbacks.remove(requestKey)
                }
            }
        }
    }

    private fun notifyMovieCardProbeCallbacks(
        requestKey: ContentMetadataRequestKey,
        metadata: HomeHeroTechnicalMetadata,
    ) {
        movieCardProbeCallbacks
            .remove(requestKey)
            .orEmpty()
            .forEach { callback ->
                callback(metadata.quality, metadata.durationMs)
            }
    }

    private fun beginDetailsRequest(item: ContentItem): DetailsRequestGate.Token? {
        val accountId = repository.activeAccountSession()?.accountId ?: return null
        val request = detailsRequestGate.begin(
            DetailsRequestGate.Key(
                type = item.type,
                contentId = item.id,
                accountId = accountId,
                profileId = profileStore.activeProfileId(),
            ),
        )
        invalidateVoiceSearchContext()
        detailsJob?.cancel()
        detailsJob = null
        return request
    }

    private fun invalidateDetailsRequest() {
        detailsRequestGate.invalidate()
        detailsJob?.cancel()
        detailsJob = null
    }

    private fun isCurrentDetailsRequest(
        request: DetailsRequestGate.Token,
    ): Boolean {
        if (
            !detailsRequestGate.isCurrentForContext(
                token = request,
                accountId = repository.activeAccountSession()?.accountId,
                profileId = profileStore.activeProfileId(),
            )
        ) return false
        val current = mutableState.value
        return when (request.key.type) {
            ContentType.MOVIE -> current.selectedItem?.let {
                it.type == ContentType.MOVIE && it.id == request.key.contentId
            } == true
            ContentType.SERIES -> current.selectedSeries?.let {
                it.type == ContentType.SERIES && it.id == request.key.contentId
            } == true
            ContentType.LIVE -> false
        }
    }

    fun open(item: ContentItem) {
        val activeSession = session ?: return
        when (item.type) {
            ContentType.LIVE -> {
                playerReturnScreen = HulkScreen.MAIN
                startPlayback(repository.playback(activeSession, item))
            }
            ContentType.MOVIE -> {
                val detailsRequest = beginDetailsRequest(item) ?: return
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
                    try {
                        val details = try {
                            repository.contentDetails(activeSession, item.id)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            if (isCurrentDetailsRequest(detailsRequest)) {
                                showFailure(error)
                            }
                            return@launch
                        }
                        if (!isCurrentDetailsRequest(detailsRequest)) return@launch
                        mutableState.update {
                            it.copy(selectedDetails = details, isLoading = false)
                        }
                    } finally {
                        if (detailsRequestGate.isCurrent(detailsRequest)) {
                            detailsJob = null
                        }
                    }
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
        val detailsRequest = beginDetailsRequest(item) ?: return
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
            try {
                val bundle = try {
                    repository.seriesBundle(activeSession, item.id)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    if (!isCurrentDetailsRequest(detailsRequest)) return@launch
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
                    return@launch
                }
                if (!isCurrentDetailsRequest(detailsRequest)) return@launch
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
            } finally {
                if (detailsRequestGate.isCurrent(detailsRequest)) {
                    detailsJob = null
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
        val currentIndex = ordered.indexOfFirst { it.id == curr…14708 tokens truncated…otificationState(clearPopup = false)
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

    private suspend fun persistNotificationPopupShown(popup: EpisodeNotificationPopup): Boolean {
        val profileId = popup.profileId
        val accountId = popup.notifications.firstOrNull()?.accountId ?: return false
        if (
            profileStore.activeProfileId() != profileId ||
            localEpisodeNotificationStore.activeAccountId() != accountId
        ) return false
        val persisted = withContext(Dispatchers.IO) {
            localEpisodeNotificationStore.markPopupShown(
                profileId = profileId,
                notificationIds = popup.eventIds,
                expectedAccountId = accountId,
            )
        }
        if (!persisted) return false
        if (
            profileStore.activeProfileId() != profileId ||
            localEpisodeNotificationStore.activeAccountId() != accountId
        ) return false
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
        val attemptGeneration = accountRefreshCoordinator.tryStart() ?: run {
            onResult("تحديث بيانات الاشتراك يعمل حاليا.")
            return
        }
        mutableState.update { it.copy(isAccountRefreshing = true, errorMessage = null) }
        accountRefreshJob = viewModelScope.launch {
            when (
                val outcome = accountRefreshCoordinator.runCurrent(attemptGeneration) {
                    repository.reauthenticate(activeSession)
                }
            ) {
                null -> return@launch
                is AccountRefreshOutcome.Success -> {
                    accountRefreshJob = null
                    val refreshed = outcome.value
                    invalidateDiagnosticsForSessionChange()
                    invalidatePlayerProgressPersistence()
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
                is AccountRefreshOutcome.Failure -> {
                    accountRefreshJob = null
                    val error = outcome.error
                    val invalidSession = error is XtreamException.InvalidCredentials ||
                        error is XtreamException.SubscriptionInactive ||
                        error is PortalException.InvalidAccessCode ||
                        error is PortalException.ResellerInactive
                    if (invalidSession) {
                        showFailure(error)
                    } else {
                        mutableState.update { it.copy(isAccountRefreshing = false) }
                    }
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
    }

    fun runDiagnostics() {
        val activeSession = session ?: run {
            mutableState.update {
                it.copy(diagnostics = it.diagnostics.copy(errorMessage = "سجل الدخول اولا لتشغيل الفحص."))
            }
            return
        }
        val attempt = diagnosticsCoordinator.tryStart(activeSession) ?: return
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
            when (
                val outcome = diagnosticsCoordinator.runCurrent(
                    attempt = attempt,
                    currentSession = { session },
                ) {
                    coroutineScope {
                        val progressScope = this
                        repository.diagnose(activeSession) { progress, stage ->
                            progressScope.launch {
                                if (!diagnosticsCoordinator.isCurrent(attempt, session)) {
                                    return@launch
                                }
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
                        }
                    }
                }
            ) {
                null -> return@launch
                is DiagnosticsOutcome.Success -> {
                    mutableState.update {
                        it.copy(
                            diagnostics = DiagnosticsState(
                                isRunning = false,
                                progress = 100,
                                stage = "اكتمل الفحص",
                                report = outcome.value,
                            ),
                        )
                    }
                }
                is DiagnosticsOutcome.Failure -> {
                    mutableState.update {
                        it.copy(
                            diagnostics = it.diagnostics.copy(
                                isRunning = false,
                                stage = "تعذر اكمال الفحص",
                                errorMessage = outcome.error.message
                                    ?: "حدث خطا غير متوقع اثناء الفحص.",
                            ),
                        )
                    }
                }
            }
        }
    }

    fun back() {
        val currentScreen = mutableState.value.screen
        if (currentScreen == HulkScreen.MOVIE_DETAILS || currentScreen == HulkScreen.SERIES) {
            invalidateDetailsRequest()
        }
        when (currentScreen) {
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
                    isLoading = false,
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
                    isLoading = false,
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
        profileLibraryStartupGate.invalidate()
        loadedCatalogs.clear()
        homeCatalogs.clear()
        selectedCategoryByType.clear()
        movieCardProbeJobs.values.forEach { it.cancel() }
        movieCardProbeJobs.clear()
        movieCardProbeCallbacks.clear()
        movieCardProbeInFlight.clear()
        movieCardProbeAttempted.clear()
        profileLibraryRefreshJob?.cancel()
        profileLibraryRefreshJob = null
    }

    private fun invalidateCatalogRequests() {
        catalogGeneration += 1L
        catalogJobs.values.forEach(Job::cancel)
        catalogJobs.clear()
    }

    private fun invalidateAccountRefresh() {
        accountRefreshCoordinator.invalidate()
        accountRefreshJob?.cancel()
        accountRefreshJob = null
    }

    fun logout() {
        beginLogout(errorMessage = null)
    }

    private fun beginLogout(errorMessage: String?) {
        if (logoutJob?.isActive == true) return

        authenticationAttemptGate.invalidate()
        invalidateAccountRefresh()
        val pendingLoginJob = loginJob
        pendingLoginJob?.cancel()
        loginJob = null
        invalidateDetailsRequest()
        val operationsState = mutableState.value.operations
        beginTvPlatformProfileTransition(resetPublishedScope = true)
        pendingTvDeepLink = null
        notificationScanJob?.cancel()
        notificationScanJob = null
        notificationUiReady = false
        invalidateCatalogRequests()
        clearCatalogMemory()
        invalidateDiagnosticsForSessionChange()
        invalidatePlayerProgressPersistence()
        mutableState.update {
            it.copy(
                screen = HulkScreen.LOGIN,
                isStarting = false,
                isLoading = false,
                loadingTypes = emptySet(),
                account = null,
                isAccountRefreshing = false,
                favorites = emptySet(),
                history = emptyList(),
                isProfileLibraryReady = false,
                downloads = emptyList(),
                notificationPopup = null,
                errorMessage = errorMessage,
            )
        }

        logoutJob = viewModelScope.launch {
            try {
                pendingLoginJob?.join()
                withContext(Dispatchers.IO) {
                    downloadRepository.suspendActiveAccountForLogout()
                }
                repository.logout()
                session = null
                sessionRestorationComplete = true
                mutableState.value = HulkUiState(
                    isStarting = false,
                    downloads = emptyList(),
                    downloadSettings = downloadRepository.settings(),
                    notificationSubscribedSeriesIds = emptySet(),
                    localNotifications = emptyList(),
                    unreadNotificationCount = 0,
                    episodeNotificationsEnabled = true,
                    notificationPopup = null,
                    operations = operationsState,
                    errorMessage = errorMessage,
                )
            } finally {
                logoutJob = null
            }
        }
    }

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    private fun restoreSession() {
        val restoreGeneration = authenticationAttemptGate.tryStart() ?: return
        sessionRestorationComplete = false
        loginJob = viewModelScope.launch {
            try {
                val credentials = repository.savedCredentials()
                if (!authenticationAttemptGate.isCurrent(restoreGeneration)) return@launch
                if (credentials == null) {
                    sessionRestorationComplete = true
                    mutableState.update { it.copy(isStarting = false) }
                    resolvePendingTvDeepLink()
                    return@launch
                }
                invalidateAccountRefresh()
                mutableState.update {
                    it.copy(
                        isStarting = false,
                        isLoading = true,
                        isAccountRefreshing = false,
                        errorMessage = null,
                    )
                }
                runAuthenticationAttempt(credentials, remember = true, restoreGeneration)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (!authenticationAttemptGate.isCurrent(restoreGeneration)) return@launch
                sessionRestorationComplete = true
                showFailure(error)
                resolvePendingTvDeepLink()
            } finally {
                if (authenticationAttemptGate.complete(restoreGeneration)) {
                    loginJob = null
                }
            }
        }
    }

    private fun authenticate(
        credentials: Credentials,
        remember: Boolean,
    ) {
        val attemptGeneration = authenticationAttemptGate.tryStart() ?: return
        invalidateAccountRefresh()
        mutableState.update {
            it.copy(
                isStarting = false,
                isLoading = true,
                isAccountRefreshing = false,
                errorMessage = null,
            )
        }
        loginJob = viewModelScope.launch {
            try {
                runAuthenticationAttempt(credentials, remember, attemptGeneration)
            } finally {
                if (authenticationAttemptGate.complete(attemptGeneration)) {
                    loginJob = null
                }
            }
        }
    }

    private suspend fun runAuthenticationAttempt(
        credentials: Credentials,
        remember: Boolean,
        attemptGeneration: Long,
    ) {
        try {
            val authenticated = repository.login(credentials, remember)
            if (!authenticationAttemptGate.isCurrent(attemptGeneration)) return

            invalidateCatalogRequests()
            invalidateDiagnosticsForSessionChange()
            invalidatePlayerProgressPersistence()
            session = authenticated
            sessionRestorationComplete = true
            clearCatalogMemory()
            val downloads = loadDownloadUiSnapshot(downloadRepository::snapshot)
            val downloadSettings = withContext(Dispatchers.IO) {
                downloadRepository.settings()
            }
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
                    favorites = emptySet(),
                    history = emptyList(),
                    isProfileLibraryReady = false,
                    downloads = downloads,
                    downloadSettings = downloadSettings,
                    errorMessage = null,
                )
            }
            ensureCatalog(ContentType.MOVIE)
            ensureCatalog(ContentType.SERIES)
            refreshOperations(force = false)
            refreshNotificationState(clearPopup = true)
            scanSubscribedSeries(NotificationScanTrigger.APP_START)
            resolvePendingTvDeepLink()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (!authenticationAttemptGate.isCurrent(attemptGeneration)) return
            sessionRestorationComplete = true
            showFailure(error)
            resolvePendingTvDeepLink()
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
        val requestGeneration = catalogGeneration
        mutableState.update {
            it.copy(loadingTypes = it.loadingTypes + type, errorMessage = null)
        }
        catalogJobs[type] = viewModelScope.launch {
            try {
                val full = repository.catalog(activeSession, type)
                val snapshot = mutableState.value
                val compact = if (type == ContentType.MOVIE || type == ContentType.SERIES) {
                    withContext(Dispatchers.Default) {
                        compactHomeCatalog(full, snapshot.favorites, snapshot.history)
                    }
                } else {
                    full
                }
                if (requestGeneration != catalogGeneration) return@launch

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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (requestGeneration != catalogGeneration) return@launch
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
        val activeProfileScopeId = tvPlatformIntegration.get().activeProfileScope()?.providerScopeId
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
                    tvPlatformIntegration.get().activeProfileScope()?.providerScopeId != expectedProfileScopeId
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
        invalidateDetailsRequest()
        invalidateVoiceSearchContext()
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
                tvPlatformIntegration.get().activeProfileScope()?.providerScopeId == expectedProfileScopeId
            ) {
                val result = tvPlatformIntegration.get().syncActiveProfile(
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
        val integration = tvPlatformIntegration.getIfInitialized()
            ?: if (session != null) tvPlatformIntegration.get() else return
        tvPlatformClearJob = viewModelScope.launch {
            integration.clearUserContent()
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

    private fun invalidateVoiceSearchContext() {
        mutableVoiceSearchContextGeneration.update { generation -> generation + 1L }
    }

    private fun invalidateDiagnosticsForSessionChange() {
        diagnosticsCoordinator.invalidate()
        diagnosticsJob?.cancel()
        diagnosticsJob = null
        mutableState.update { state ->
            if (!state.diagnostics.isRunning) {
                state
            } else {
                state.copy(
                    diagnostics = state.diagnostics.copy(
                        isRunning = false,
                        progress = 0,
                        stage = "",
                        errorMessage = null,
                    ),
                )
            }
        }
    }

    private fun invalidatePlayerProgressPersistence() {
        userLibrary.invalidateProgressMutations()
        playerProgressPersistenceJob?.cancel()
        playerProgressPersistenceJob = null
    }

    private fun startPlayback(request: PlaybackRequest) {
        if (playerReturnScreen == HulkScreen.MAIN) {
            invalidateDetailsRequest()
        }
        invalidateVoiceSearchContext()
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
        expectedSession: AuthenticatedSession,
        seriesTitle: String? = null,
        season: Int? = null,
        episodeNumber: Int? = null,
        replaceDownloadId: Long? = null,
        onResult: (String) -> Unit,
    ) {
        if (!pendingDownloadEnqueues.add(request.historyKey)) {
            onResult("جار بدء هذا التحميل.")
            return
        }
        val expectedAccountId = repository.activeAccountSession()?.accountId
        val expectedProfileId = profileStore.activeProfileId()
        if (expectedAccountId.isNullOrBlank() || expectedProfileId.isBlank()) {
            pendingDownloadEnqueues.remove(request.historyKey)
            onResult("تعذر تثبيت مالك التحميل.")
            return
        }

        viewModelScope.launch {
            try {
                val outcome = runDownloadEnqueueOffMain {
                    replaceDownloadId?.let { downloadId ->
                        downloadRepository.remove(
                            downloadId = downloadId,
                            expectedAccountId = expectedAccountId,
                            expectedProfileId = expectedProfileId,
                        )
                    }
                    downloadRepository.enqueue(
                        request = request,
                        expectedAccountId = expectedAccountId,
                        expectedProfileId = expectedProfileId,
                        seriesTitle = seriesTitle,
                        season = season,
                        episodeNumber = episodeNumber,
                    )
                }
                if (
                    !downloadEnqueuePublicationAllowed(
                        sameSession = session === expectedSession,
                        expectedAccountId = expectedAccountId,
                        expectedProfileId = expectedProfileId,
                        activeAccountId = downloadRepository.activeAccountIdForCleanup(),
                        activeProfileId = profileStore.activeProfileId(),
                    )
                ) {
                    return@launch
                }

                val message = when (val result = outcome.result) {
                    is DownloadRepository.EnqueueResult.Started -> {
                        mutableState.update { it.copy(downloads = outcome.downloads) }
                        "بدا فحص حجم ${result.item.title} والمساحة المتاحة."
                    }
                    is DownloadRepository.EnqueueResult.AlreadyExists -> when (result.item.status) {
                        OfflineStatus.COMPLETED -> "هذا المحتوى محمل بالفعل."
                        else -> "هذا المحتوى موجود في قائمة التحميلات."
                    }
                    is DownloadRepository.EnqueueResult.Failed -> result.message
                }
                onResult(message)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (
                    downloadEnqueuePublicationAllowed(
                        sameSession = session === expectedSession,
                        expectedAccountId = expectedAccountId,
                        expectedProfileId = expectedProfileId,
                        activeAccountId = downloadRepository.activeAccountIdForCleanup(),
                        activeProfileId = profileStore.activeProfileId(),
                    )
                ) {
                    onResult("تعذر بدء التحميل. حاول مرة اخرى.")
                }
            } finally {
                pendingDownloadEnqueues.remove(request.historyKey)
            }
        }
    }

    private fun showFailure(error: Throwable) {
        val invalidSession = error is XtreamException.InvalidCredentials ||
            error is XtreamException.SubscriptionInactive ||
            error is PortalException.InvalidAccessCode ||
            error is PortalException.ResellerInactive
        if (invalidSession) {
            beginLogout(error.message ?: "حدث خطا غير متوقع. حاول مرة اخرى.")
            return
        }
        mutableState.update {
            it.copy(
                screen = if (invalidSession) HulkScreen.LOGIN else it.screen,
                isStarting = false,
                isLoading = false,
                isAccountRefreshing = false,
                errorMessage = error.message ?: "حدث خطا غير متوقع. حاول مرة اخرى.",
            )
        }
    }

    companion object {

        private const val HOME_RECENT_LIMIT = 240
        private const val HOME_RATED_LIMIT = 120
        private const val HOME_CATALOG_LIMIT = 320
        private const val MOVIE_CARD_PROBE_CONCURRENCY = 2
        private const val EPISODE_NOTIFICATION_SCAN_CONCURRENCY = 3
        private const val TV_PLATFORM_SYNC_DEBOUNCE_MS = 350L
    }
}
