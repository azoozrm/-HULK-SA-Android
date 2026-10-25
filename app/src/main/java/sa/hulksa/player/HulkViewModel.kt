package sa.hulksa.player

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import sa.hulksa.player.data.AccountIdentityCandidateLabel
import sa.hulksa.player.data.AuthenticatedSessionOwner
import sa.hulksa.player.data.AuthenticatedSessionRegistry
import sa.hulksa.player.data.AuthenticationStartResult
import sa.hulksa.player.data.ContentMetadataRequestKey
import sa.hulksa.player.data.DownloadActionClaim
import sa.hulksa.player.data.DownloadActionIntent
import sa.hulksa.player.data.DownloadActionOutcome
import sa.hulksa.player.data.DownloadPrimaryActionDispatcher
import sa.hulksa.player.data.DownloadRepository
import sa.hulksa.player.data.EpisodeNotificationPopup
import sa.hulksa.player.data.EpisodeNotificationSubscription
import sa.hulksa.player.data.SessionOwnershipCleanupGate
import sa.hulksa.player.data.SessionOwnershipCleanupResult
import sa.hulksa.player.data.SessionPublicationGate
import sa.hulksa.player.data.SessionPublicationResult
import sa.hulksa.player.data.EpisodeNotificationStoreResult
import sa.hulksa.player.data.FavoriteMutationGate
import sa.hulksa.player.data.FavoriteMutationOutcome
import sa.hulksa.player.data.FavoriteMutationStatus
import sa.hulksa.player.data.HulkRepository
import sa.hulksa.player.data.HomeHeroMetadataStore
import sa.hulksa.player.data.HomeHeroTechnicalMetadata
import sa.hulksa.player.data.historyPublicationAllowed
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
import sa.hulksa.player.data.PairingClient
import sa.hulksa.player.data.PairingDeviceInfo
import sa.hulksa.player.data.PendingAccountIdentityDecision
import sa.hulksa.player.data.accountIdentityCandidateLabels
import sa.hulksa.player.data.activePersistentOperationsAnnouncement
import sa.hulksa.player.data.PortalException
import sa.hulksa.player.data.PresenceClient
import sa.hulksa.player.data.PresenceLifecycleOwner
import sa.hulksa.player.data.currentPresenceAppMetadata
import sa.hulksa.player.data.currentPresenceDeviceMetadata
import sa.hulksa.player.data.ProfileDownloadMutationOutcome
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
import sa.hulksa.player.data.runFavoriteMutationOffMain
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
import sa.hulksa.player.security.CredentialEnvelopeRemovalException
import sa.hulksa.player.tv.TvDeepLinkDispatchDecision
import sa.hulksa.player.ui.screens.DownloadFeedback
import sa.hulksa.player.ui.screens.DownloadFeedbackGlyph
import sa.hulksa.player.ui.screens.compactHomeCatalogForHome
import sa.hulksa.player.ui.screens.downloadAlreadyCompletedFeedback
import sa.hulksa.player.ui.screens.downloadAlreadyQueuedFeedback
import sa.hulksa.player.ui.screens.downloadCancelConfirmedFeedback
import sa.hulksa.player.ui.screens.downloadConcurrencyFeedback
import sa.hulksa.player.ui.screens.downloadDeletionConfirmedFeedback
import sa.hulksa.player.ui.screens.downloadDeletionFailedFeedback
import sa.hulksa.player.ui.screens.downloadDuplicatePendingFeedback
import sa.hulksa.player.ui.screens.downloadFeatureDisabledFeedback
import sa.hulksa.player.ui.screens.downloadGenericFailureFeedback
import sa.hulksa.player.ui.screens.downloadOwnerChangedFeedback
import sa.hulksa.player.ui.screens.downloadOwnerFixFailedFeedback
import sa.hulksa.player.ui.screens.downloadPauseFailedFeedback
import sa.hulksa.player.ui.screens.downloadPausePersistedFeedback
import sa.hulksa.player.ui.screens.downloadPreparingFeedback
import sa.hulksa.player.ui.screens.downloadPriorityFeedback
import sa.hulksa.player.ui.screens.downloadResumeAcceptedFeedback
import sa.hulksa.player.ui.screens.downloadRetryAcceptedFeedback
import sa.hulksa.player.ui.screens.downloadScheduleFeedback
import sa.hulksa.player.ui.screens.downloadSessionRequiredFeedback
import sa.hulksa.player.ui.screens.downloadTargetUnresolvedFeedback
import sa.hulksa.player.ui.screens.downloadWifiFeedback
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
    val pairing: PairingUiState = PairingUiState(),
    val playback: PlaybackRequest? = null,
    val diagnostics: DiagnosticsState = DiagnosticsState(),
    val accountDecision: AccountDecisionUiState? = null,
) {
    companion object {
    }
}

internal fun HulkUiState.finishExpiredOwnerFailure(message: String?): HulkUiState = copy(
    isStarting = false,
    isLoading = false,
    isAccountRefreshing = false,
    errorMessage = message,
)

/**
 * UI-safe ambiguity options. Keys are opaque positional tokens; accountIds and credentials never
 * enter UI state.
 */
data class AccountDecisionOption(
    val key: String,
    val label: String,
)

data class AccountDecisionUiState(
    val username: String,
    val options: List<AccountDecisionOption>,
)

internal sealed interface AccountDecisionChoice {
    data class ExistingAccount(val key: String) : AccountDecisionChoice
    data object DifferentSubscription : AccountDecisionChoice
    data object Cancel : AccountDecisionChoice
}

private data class PendingAccountDecision(
    val generation: Long,
    val outcome: PendingAccountIdentityDecision,
    val remember: Boolean,
    val labeledCandidates: List<AccountIdentityCandidateLabel>,
)

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

internal suspend fun publishDownloadUiSnapshot(
    state: MutableStateFlow<HulkUiState>,
    gate: DownloadSnapshotPublicationGate,
    snapshotLoader: () -> List<OfflineDownload>,
): List<OfflineDownload> {
    val attempt = gate.begin()
    val downloads = loadDownloadUiSnapshot(snapshotLoader)
    if (!gate.isCurrent(attempt)) return state.value.downloads
    if (downloads != state.value.downloads) {
        state.update { it.copy(downloads = downloads) }
    }
    return downloads
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

internal suspend fun <T> runDownloadSettingsPersistenceOffMain(
    persistence: suspend () -> T,
): T = withContext(Dispatchers.IO) {
    persistence()
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

internal fun playerProgressProfileOwnerAllowed(
    expectedProfileId: String,
    activeProfileId: String,
): Boolean = expectedProfileId == activeProfileId

class HulkViewModel(application: Application) : AndroidViewModel(application) {
    private val favoriteMutationGate = FavoriteMutationGate()
    private val favoriteMutationMutex = Mutex()
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
    private val presenceDeviceMetadata = application.currentPresenceDeviceMetadata(operationsDeviceIsTv)
    private val presenceAppMetadata = currentPresenceAppMetadata()
    private val presenceLifecycleOwner = PresenceLifecycleOwner(viewModelScope, PresenceClient())
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
    private val pairingClient = PairingClient()
    private val pairingAckReceiptOwner = PairingAckReceiptOwner(
        scope = viewModelScope,
        transport = pairingClient,
    )
    private val pairingAuthHandoff = object : PairingAuthHandoff {
        override fun tryReserve(): Long? = authenticationAttemptGate.tryStartPairedHandoff()

        override fun launch(credentials: Credentials, remember: Boolean, reservation: Long) {
            beginReservedAuthentication(credentials, remember, reservation)
        }

        override fun release(reservation: Long) {
            authenticationAttemptGate.complete(reservation)
        }
    }
    private val pairingController = PairingController(
        transport = pairingClient,
        scope = viewModelScope,
        handoff = pairingAuthHandoff,
        installationIdProvider = repository::pairingInstallationId,
        deviceInfoProvider = {
            PairingDeviceInfo(
                manufacturer = presenceDeviceMetadata.manufacturer.take(64),
                model = presenceDeviceMetadata.model.take(64),
                appVersion = presenceAppMetadata.versionName.take(32),
            )
        },
        onConsumedAck = { baseUrl, requestId, claimToken ->
            pairingAckReceiptOwner.tryEnqueue(baseUrl, requestId, claimToken)
        },
        onState = { pairing -> mutableState.update { it.copy(pairing = pairing) } },
    )
    private val accountRefreshCoordinator = AccountRefreshCoordinator()
    private val sessionPublicationGate = SessionPublicationGate()
    private val sessionCleanupGate = SessionOwnershipCleanupGate(
        ownershipEpoch = { sessionOwnershipEpoch },
        currentOwner = { AuthenticatedSessionRegistry.currentOwner() },
        logoutIfOwnedBy = repository::logoutIfOwnedBy,
    )
    private val diagnosticsCoordinator = DiagnosticsCoordinator()
    private val profileLibraryStartupGate = ProfileLibraryStartupGate()
    private val detailsRequestGate = DetailsRequestGate()
    private val downloadSettingsMutationGate = DownloadSettingsMutationGate()
    private val downloadSnapshotPublicationGate = DownloadSnapshotPublicationGate()
    private val downloadPrimaryActionDispatcher = DownloadPrimaryActionDispatcher(downloadRepository)
    private val downloadPriorityMutationQueues = mutableMapOf<Long, DownloadPriorityMutationQueue>()
    private var loginJob: Job? = null
    private var logoutJob: Job? = null
    private var sessionOwnershipEpoch = 0L
    private var pendingAccountDecision: PendingAccountDecision? = null
    private val loadedCatalogs = mutableMapOf<ContentType, Catalog>()
    private val homeCatalogs = mutableMapOf<ContentType, Catalog>()
    private val catalogRequestOwner = CatalogRequestOwner(
        scope = viewModelScope,
        load = { activeSession, type ->
            val full = repository.catalog(activeSession, type)
            val snapshot = mutableState.value
            val home = if (type == ContentType.MOVIE || type == ContentType.SERIES) {
                withContext(Dispatchers.Default) {
                    compactHomeCatalogForHome(full, snapshot.favorites, snapshot.history)
                }
            } else {
                full
            }
            CatalogLoadResult(full = full, home = home)
        },
        hasLoaded = { type -> loadedCatalogs[type] != null },
        isSessionCurrent = { activeSession -> session === activeSession },
        onLoadingChanged = { loadingTypes ->
            mutableState.update { it.copy(loadingTypes = loadingTypes) }
        },
        onLoaded = { type, result, remaining ->
            loadedCatalogs[type] = result.full
            homeCatalogs[type] = result.home
            mutableState.update { state ->
                val base = state.catalogs + (type to result.full)
                state.copy(
                    catalogs = catalogsForDestination(state.destination, base),
                    loadingTypes = remaining,
                )
            }
            resolvePendingTvDeepLink()
            scheduleTvPlatformSync(immediate = false)
        },
        onFailure = { _, error, remaining ->
            mutableState.update { it.copy(loadingTypes = remaining) }
            showFailure(error)
        },
    )
    private val selectedCategoryByType = mutableMapOf<ContentType, String?>()
    private var detailsJob: Job? = null
    private var accountRefreshJob: Job? = null
    private var diagnosticsJob: Job? = null
    private var profileLibraryRefreshJob: Job? = null
    private var playerProgressPersistenceJob: Job? = null
    private var playerProgressProfileOwner: String? = null
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
                val downloads = publishDownloadUiSnapshot(
                    state = mutableState,
                    gate = downloadSnapshotPublicationGate,
                    snapshotLoader = downloadRepository::snapshot,
                )
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
        pairingController.cancel()
        if (logoutJob?.isActive == true || authenticationAttemptGate.isActive()) return
        val normalizedAccessCode = normalizeResellerAccessCode(accessCode)
        if (normalizedAccessCode == null) {
            mutableState.update { it.copy(errorMessage = "ادخل كود دخول صحيح") }
            return
        }
        val cleanUsername = username.trim()
        if (cleanUsername.isEmpty() || password.isEmpty()) {
            mutableState.update { it.copy(errorMessage = "ادخل اسم المستخدم وكلمة المرور") }
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

    fun startPairing(remember: Boolean) {
        val current = mutableState.value
        if (current.screen != HulkScreen.LOGIN) return
        if (current.isStarting || current.isLoading || current.accountDecision != null) return
        if (!current.pairing.available) return
        pairingController.start(remember)
    }

    fun retryPairing() = pairingController.retry()

    fun regeneratePairing() = pairingController.regenerate()

    fun cancelPairing() = pairingController.cancel()

    fun handleTvDeepLink(rawUri: String?) {
        if (!operationsDeviceIsTv) return
        val target = TvDeepLinkRouter.parse(rawUri)
        if (target == null) {
            pendingTvDeepLink = null
            tvDeepLinkEpisodeJob?.cancel()
            tvDeepLinkEpisodeJob = null
            if (!rawUri.isNullOrBlank()) {
                mutableState.update { it.copy(errorMessage = "تعذر فتح هذا الرابط") }
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
                            errorMessage = "تعذر تهيئة مكتبة هذا الملف الشخصي بأمان",
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
                    compactHomeCatalogForHome(catalog, favorites, history)
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
    ): Map<ContentType, Catalog> = projectCatalogsForDestination(
        destination = destination,
        current = current,
        loaded = loadedCatalogs,
        home = homeCatalogs,
    )

    private fun ensureDestinationCatalogs(destination: MainDestination) {
        catalogTypesForDestination(destination).forEach(::ensureCatalog)
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
            currentState.destination != MainDestination.SEARCH
        ) return null
        // Voice ownership is the authenticated account/session/profile scope; the notification UI
        // readiness latch is a notification-presentation gate and must not disable Search voice.
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
        val types = catalogTypesForDestination(mutableState.value.destination)
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
                                errorMessage = "هذا المحتوى لم يعد متاحًا",
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
                            bundle.episodes.isEmpty() -> "لم نجد حلقات لهذا المسلسل"
                            !targetAvailable -> "هذه الحلقة لم تعد متاحة، وتم فتح المسلسل بدلًا منها"
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

    fun downloadSelectedMovie(onResult: (DownloadFeedback) -> Unit) {
        if (!mutableState.value.operations.features.downloadsEnabled) {
            onResult(downloadFeatureDisabledFeedback())
            return
        }
        val activeSession = session
        if (activeSession == null) {
            onResult(downloadSessionRequiredFeedback())
            return
        }
        val movie = mutableState.value.selectedItem?.takeIf { it.type == ContentType.MOVIE }
        if (movie == null) {
            onResult(downloadTargetUnresolvedFeedback(isMovie = true))
            return
        }
        enqueueDownload(
            request = repository.playback(activeSession, movie),
            expectedSession = activeSession,
            onResult = onResult,
        )
    }

    fun downloadEpisode(episode: Episode, onResult: (DownloadFeedback) -> Unit) {
        if (!mutableState.value.operations.features.downloadsEnabled) {
            onResult(downloadFeatureDisabledFeedback())
            return
        }
        val activeSession = session
        if (activeSession == null) {
            onResult(downloadSessionRequiredFeedback())
            return
        }
        val series = mutableState.value.selectedSeries
        if (series == null) {
            onResult(downloadTargetUnresolvedFeedback(isMovie = false))
            return
        }
        enqueueDownload(
            request = repository.playback(activeSession, series, episode),
            expectedSession = activeSession,
            seriesTitle = series.name,
            season = episode.season,
            episodeNumber = episode.episodeNumber,
            onResult = onResult,
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

    fun deleteDownload(item: OfflineDownload, onResult: (DownloadFeedback) -> Unit = {}) {
        val expectedAccountId = downloadRepository.activeAccountIdForCleanup() ?: return
        val expectedProfileId = profileStore.activeProfileId()
        viewModelScope.launch {
            val downloads = runDownloadRemovalOffMain {
                downloadRepository.remove(
                    downloadId = item.downloadId,
                    expectedAccountId = expectedAccountId,
                    expectedProfileId = expectedProfileId,
                )
            }
            if (
                downloadRemovalContextMatches(
                    expectedAccountId = expectedAccountId,
                    expectedProfileId = expectedProfileId,
                    activeAccountId = downloadRepository.activeAccountIdForCleanup(),
                    activeProfileId = profileStore.activeProfileId(),
                )
            ) {
                mutableState.update { state -> state.copy(downloads = downloads) }
                // Only the owning context's actually published mutation reports a result; a
                // failed removal keeps the record (with its real failure state) and never
                // claims success from the button press.
                if (downloads.none { download -> download.downloadId == item.downloadId }) {
                    onResult(
                        if (item.status == OfflineStatus.COMPLETED) {
                            downloadDeletionConfirmedFeedback()
                        } else {
                            downloadCancelConfirmedFeedback()
                        },
                    )
                } else {
                    onResult(downloadDeletionFailedFeedback())
                }
            }
        }
    }

    fun toggleWifiOnly(): DownloadFeedback {
        if (!mutableState.value.operations.features.downloadsEnabled) {
            return downloadFeatureDisabledFeedback()
        }
        val next = mutableState.value.downloadSettings.copy(
            wifiOnly = !mutableState.value.downloadSettings.wifiOnly,
        )
        if (!submitDownloadSettingsMutation(next) { expectedAccountId, expectedProfileId ->
            downloadRepository.setSettings(next, expectedAccountId, expectedProfileId)
        }) return downloadOwnerChangedFeedback()
        return downloadWifiFeedback(next.wifiOnly)
    }

    fun toggleDownloadSchedule(): DownloadFeedback {
        if (!mutableState.value.operations.features.downloadsEnabled) {
            return downloadFeatureDisabledFeedback()
        }
        val current = mutableState.value.downloadSettings
        val next = if (current.scheduleMode == DownloadScheduleMode.NOW) DownloadScheduleMode.NIGHT else DownloadScheduleMode.NOW
        val nextSettings = current.copy(scheduleMode = next)
        if (!submitDownloadSettingsMutation(nextSettings) { expectedAccountId, expectedProfileId ->
            downloadRepository.setSettings(nextSettings, expectedAccountId, expectedProfileId)
        }) return downloadOwnerChangedFeedback()
        return downloadScheduleFeedback(next == DownloadScheduleMode.NIGHT)
    }

    fun cycleConcurrentDownloads(): DownloadFeedback {
        if (!mutableState.value.operations.features.downloadsEnabled) {
            return downloadFeatureDisabledFeedback()
        }
        val current = mutableState.value.downloadSettings
        val next = if (current.concurrentDownloads >= 3) 1 else current.concurrentDownloads + 1
        val nextSettings = current.copy(concurrentDownloads = next)
        if (!submitDownloadSettingsMutation(nextSettings) { expectedAccountId, expectedProfileId ->
            downloadRepository.setSettings(nextSettings, expectedAccountId, expectedProfileId)
        }) return downloadOwnerChangedFeedback()
        return downloadConcurrencyFeedback(next)
    }

    fun cycleDownloadPriority(item: OfflineDownload): DownloadFeedback {
        if (!mutableState.value.operations.features.downloadsEnabled) {
            return downloadFeatureDisabledFeedback()
        }
        if (!submitDownloadPriorityMutation(item.downloadId) { expectedAccountId, expectedProfileId ->
            downloadRepository.cyclePriority(item.downloadId, expectedAccountId, expectedProfileId)
        }) return downloadOwnerChangedFeedback()
        return downloadPriorityFeedback(item.priority)
    }

    private fun submitDownloadSettingsMutation(
        nextSettings: DownloadSettings,
        mutation: (expectedAccountId: String, expectedProfileId: String) -> ProfileDownloadMutationOutcome,
    ): Boolean {
        val expectedSession = session ?: return false
        val expectedAccountId = downloadRepository.activeAccountIdForCleanup() ?: return false
        val expectedProfileId = profileStore.activeProfileId()
        if (expectedProfileId.isBlank()) return false
        val attempt = downloadSettingsMutationGate.begin()
        mutableState.update { state -> state.copy(downloadSettings = nextSettings) }
        viewModelScope.launch {
            val outcome = runDownloadSettingsPersistenceOffMain {
                downloadSettingsMutationGate.writeIfCurrent(attempt) {
                    mutation(expectedAccountId, expectedProfileId)
                }
            } ?: return@launch
            if (
                !downloadSettingsMutationGate.isCurrent(attempt) ||
                session !== expectedSession ||
                !downloadOwnerContextMatches(
                    expectedAccountId = expectedAccountId,
                    expectedProfileId = expectedProfileId,
                    activeAccountId = downloadRepository.activeAccountIdForCleanup(),
                    activeProfileId = profileStore.activeProfileId(),
                )
            ) {
                return@launch
            }
            if (outcome.applied) {
                mutableState.update { state ->
                    state.copy(downloadSettings = outcome.settings, downloads = outcome.downloads)
                }
            }
        }
        return true
    }

    private fun submitDownloadPriorityMutation(
        downloadId: Long,
        mutation: (expectedAccountId: String, expectedProfileId: String) -> ProfileDownloadMutationOutcome,
    ): Boolean {
        val expectedSession = session ?: return false
        val expectedAccountId = downloadRepository.activeAccountIdForCleanup() ?: return false
        val expectedProfileId = profileStore.activeProfileId()
        if (expectedProfileId.isBlank()) return false
        val queue = downloadPriorityMutationQueues.getOrPut(downloadId, ::DownloadPriorityMutationQueue)
        val attempt = queue.begin()
        viewModelScope.launch {
            val outcome = runDownloadSettingsPersistenceOffMain {
                queue.write { mutation(expectedAccountId, expectedProfileId) }
            }
            if (
                !queue.isCurrent(attempt) ||
                session !== expectedSession ||
                !downloadOwnerContextMatches(
                    expectedAccountId = expectedAccountId,
                    expectedProfileId = expectedProfileId,
                    activeAccountId = downloadRepository.activeAccountIdForCleanup(),
                    activeProfileId = profileStore.activeProfileId(),
                )
            ) return@launch
            if (outcome.applied) {
                mutableState.update { state ->
                    state.copy(downloadSettings = outcome.settings, downloads = outcome.downloads)
                }
            }
        }
        return true
    }

    /**
     * Applies the explicit download action the user saw. The authoritative record validates the
     * intent; it never chooses a different action, so a stale Retry cannot become a Pause.
     */
    fun onDownloadPrimaryAction(
        item: OfflineDownload,
        intent: DownloadActionIntent,
        onResult: (DownloadFeedback) -> Unit = {},
    ) {
        if (!mutableState.value.operations.features.downloadsEnabled) {
            onResult(downloadFeatureDisabledFeedback())
            return
        }
        val expectedSession = session ?: run {
            onResult(downloadSessionRequiredFeedback())
            return
        }
        val expectedAccountId = downloadRepository.activeAccountIdForCleanup()
        val expectedProfileId = profileStore.activeProfileId()
        if (expectedAccountId.isNullOrBlank() || expectedProfileId.isBlank()) {
            onResult(downloadOwnerChangedFeedback())
            return
        }
        viewModelScope.launch {
            val outcome = downloadPrimaryActionDispatcher.dispatch(
                downloadId = item.downloadId,
                expectedAccountId = expectedAccountId,
                expectedProfileId = expectedProfileId,
                intent = intent,
            )
            if (
                session !== expectedSession ||
                !downloadOwnerContextMatches(
                    expectedAccountId = expectedAccountId,
                    expectedProfileId = expectedProfileId,
                    activeAccountId = downloadRepository.activeAccountIdForCleanup(),
                    activeProfileId = profileStore.activeProfileId(),
                )
            ) return@launch
            if (outcome.downloads.isNotEmpty()) {
                mutableState.update { state ->
                    state.copy(downloads = outcome.downloads)
                }
            }
            downloadActionFeedback(outcome, intent)?.let(onResult)
        }
    }

    private fun downloadActionFeedback(
        outcome: DownloadActionOutcome,
        intent: DownloadActionIntent,
    ): DownloadFeedback? = when (outcome.claim) {
        DownloadActionClaim.APPLIED -> when (intent) {
            DownloadActionIntent.RETRY -> downloadRetryAcceptedFeedback()
            DownloadActionIntent.RESUME -> downloadResumeAcceptedFeedback()
            DownloadActionIntent.PAUSE -> downloadPausePersistedFeedback()
        }
        DownloadActionClaim.ALREADY_QUEUED -> downloadAlreadyQueuedFeedback()
        DownloadActionClaim.ALREADY_COMPLETED -> downloadAlreadyCompletedFeedback()
        DownloadActionClaim.OWNER_CHANGED -> downloadOwnerChangedFeedback()
        DownloadActionClaim.PERSISTENCE_FAILED -> downloadPauseFailedFeedback()
        DownloadActionClaim.FAILED -> downloadGenericFailureFeedback()
        DownloadActionClaim.NOT_APPLICABLE,
        DownloadActionClaim.SUPERSEDED,
        -> null
    }

    fun openHistory(entry: HistoryEntry) {
        val activeSession = session ?: return
        playerReturnScreen = HulkScreen.MAIN
        startPlayback(repository.playback(activeSession, entry))
    }

    fun onPlaybackProgress(request: PlaybackRequest, positionMs: Long, durationMs: Long) {
        if (request.isLive) return
        val owner = AuthenticatedSessionRegistry.currentOwner() ?: return
        if (owner.session !== session) return
        val profileId = playerProgressProfileOwner ?: return
        playerProgressPersistenceJob?.cancel()
        val attempt = userLibrary.beginProgressMutation()
        playerProgressPersistenceJob = viewModelScope.launch {
            if (
                !userLibrary.isCurrentProgressMutation(attempt) ||
                !AuthenticatedSessionRegistry.isCurrent(owner) ||
                session !== owner.session
            ) return@launch
            val updated = userLibrary.updateProgressForOwner(
                expectedOwner = owner,
                expectedProfileId = profileId,
                request = request,
                positionMs = positionMs,
                durationMs = durationMs,
                historyAttempt = attempt,
                isCurrentAttempt = { userLibrary.isCurrentProgressMutation(attempt) },
            ) ?: return@launch
            if (
                !userLibrary.isCurrentProgressMutation(attempt) ||
                !AuthenticatedSessionRegistry.isCurrent(owner) ||
                session !== owner.session
            ) return@launch
            mutableState.update { it.copy(history = updated) }
        }
        val lastSyncedPositionMs = tvLastSyncedPositions[request.historyKey] ?: 0L
        if (
            operationsDeviceIsTv &&
            shouldSyncTvProgress(lastSyncedPositionMs, positionMs, durationMs)
        ) {
            tvLastSyncedPositions[request.historyKey] = positionMs
            scheduleTvPlatformSync(immediate = positionMs.toDouble() / durationMs.coerceAtLeast(1L) >= .92)
        }
    }

    fun removeHistoryEntry(key: String, onResult: (Boolean) -> Unit = {}) {
        val expectedOwner = AuthenticatedSessionRegistry.currentOwner()
        if (expectedOwner == null) {
            onResult(false)
            return
        }
        if (expectedOwner.session !== session) {
            onResult(false)
            return
        }
        if (mutableState.value.history.none { it.key == key }) {
            onResult(false)
            return
        }
        val expectedProfileId = profileStore.activeProfileId()
        val historyAttempt = userLibrary.beginHistoryMutation()
        viewModelScope.launch {
            val updated = userLibrary.removeHistoryForOwner(
                expectedOwner = expectedOwner,
                expectedAccountId = expectedOwner.accountId,
                expectedProfileId = expectedProfileId,
                key = key,
                historyAttempt = historyAttempt,
            )
            if (updated == null) {
                onResult(false)
                return@launch
            }
            if (!historyPublicationAllowed(
                    attemptCurrent = userLibrary.isCurrentHistoryMutation(historyAttempt),
                    sessionCurrent = AuthenticatedSessionRegistry.isCurrent(expectedOwner) &&
                        session === expectedOwner.session,
                    expectedAccountId = expectedOwner.accountId,
                    activeAccountId = repository.activeAccountSession()?.accountId,
                    expectedProfileId = expectedProfileId,
                    activeProfileId = profileStore.activeProfileId(),
                )
            ) {
                onResult(false)
                return@launch
            }
            mutableState.update { it.copy(history = updated) }
            tvLastSyncedPositions.remove(key)
            scheduleTvPlatformSync(immediate = true)
            onResult(true)
        }
    }

    fun toggleFavorite(item: ContentItem) {
        toggleFavorite(item) { }
    }

    /**
     * One production favorite path. The result callback always receives the authoritative
     * apply/reject/stale outcome; callers never derive success from a pre-click guess.
     */
    fun toggleFavorite(
        item: ContentItem,
        onResult: (FavoriteMutationOutcome) -> Unit,
    ) {
        val key = userLibrary.keyFor(item)
        val ticket = favoriteMutationGate.admit(key)
        if (ticket == null) {
            onResult(
                FavoriteMutationOutcome(
                    status = FavoriteMutationStatus.THROTTLED,
                    key = key,
                    favorites = mutableState.value.favorites,
                ),
            )
            return
        }
        val expectedOwner = AuthenticatedSessionRegistry.currentOwner()
        val expectedAccountId = repository.activeAccountSession()?.accountId
        val expectedProfileId = profileStore.activeProfileId()
        if (
            expectedOwner == null ||
            expectedAccountId.isNullOrBlank() ||
            expectedProfileId.isBlank() ||
            expectedOwner.session !== session ||
            expectedOwner.accountId != expectedAccountId
        ) {
            favoriteMutationGate.finish(ticket)
            onResult(FavoriteMutationOutcome(FavoriteMutationStatus.OWNER_CHANGED, key))
            return
        }
        viewModelScope.launch {
            try {
                favoriteMutationMutex.withLock {
                    val outcome = try {
                        runFavoriteMutationOffMain {
                            userLibrary.toggleFavoriteForOwner(
                                expectedOwner = expectedOwner,
                                expectedAccountId = expectedAccountId,
                                expectedProfileId = expectedProfileId,
                                item = item,
                            )
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        FavoriteMutationOutcome(
                            status = FavoriteMutationStatus.PERSISTENCE_FAILED,
                            key = key,
                            favorites = mutableState.value.favorites,
                        )
                    }
                    val ownerCurrent =
                        session === expectedOwner.session &&
                            AuthenticatedSessionRegistry.isCurrent(expectedOwner) &&
                            repository.activeAccountSession()?.accountId == expectedAccountId &&
                            profileStore.activeProfileId() == expectedProfileId
                    if (!ownerCurrent) {
                        onResult(FavoriteMutationOutcome(FavoriteMutationStatus.OWNER_CHANGED, key))
                        return@withLock
                    }
                    when (outcome.status) {
                        FavoriteMutationStatus.APPLIED_ADDED,
                        FavoriteMutationStatus.APPLIED_REMOVED,
                        FavoriteMutationStatus.UNCHANGED,
                        FavoriteMutationStatus.KIDS_REJECTED,
                        FavoriteMutationStatus.PERSISTENCE_FAILED,
                        -> mutableState.update { it.copy(favorites = outcome.favorites) }
                        FavoriteMutationStatus.OWNER_CHANGED,
                        FavoriteMutationStatus.THROTTLED,
                        -> Unit
                    }
                    onResult(outcome)
                }
            } finally {
                favoriteMutationGate.finish(ticket)
            }
        }
    }

    fun isFavorite(item: ContentItem): Boolean = userLibrary.isFavorite(item, mutableState.value.favorites)

    fun isSeriesNotificationsEnabled(series: ContentItem): Boolean =
        series.id in mutableState.value.notificationSubscribedSeriesIds

    fun toggleSeriesNotifications(
        series: ContentItem,
        onResult: (String) -> Unit,
    ) {
        if (!mutableState.value.operations.features.episodeNotificationsEnabled) {
            onResult("تنبيهات الحلقات متوقفة مؤقتًا")
            return
        }
        if (series.type != ContentType.SERIES || series.id <= 0) {
            onResult("تعذر تفعيل تنبيهات هذا المسلسل")
            return
        }
        val profile = profileStore.activeProfile()
        val accountId = localEpisodeNotificationStore.activeAccountId()
        if (accountId == null) {
            onResult("سجل الدخول أولًا")
            return
        }
        if (!canUseSeriesNotifications(profile.kind, series.id)) {
            onResult("لا يمكن تفعيل التنبيه لأن المسلسل غير موثّق ضمن محتوى الأطفال")
            return
        }
        val authenticatedOwner = AuthenticatedSessionRegistry.currentOwner()
        val detailsRequest = detailsRequestGate.currentToken()
        val activeSession = session
        if (
            authenticatedOwner == null ||
            authenticatedOwner.session !== activeSession ||
            authenticatedOwner.accountId != accountId ||
            detailsRequest == null ||
            detailsRequest.key.type != ContentType.SERIES ||
            detailsRequest.key.contentId != series.id ||
            detailsRequest.key.accountId != accountId ||
            detailsRequest.key.profileId != profile.id
        ) {
            onResult("تغيّر سياق المسلسل. أعد فتحه ثم حاول مرة أخرى")
            return
        }
        val toggleOwner = SeriesNotificationToggleOwner(
            authenticatedSession = authenticatedOwner,
            detailsRequest = detailsRequest,
            accountId = accountId,
            profileId = profile.id,
            profileKind = profile.kind,
            seriesId = series.id,
        )
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
            onResult("انتظر حتى يكتمل تحميل الحلقات ثم حاول مرة أخرى")
            return
        }

        viewModelScope.launch {
            if (!isCurrentSeriesNotificationToggle(toggleOwner)) return@launch
            var enabledBaselineEpisodes: List<Episode>? = null
            val result = if (currentlyEnabled) {
                withContext(Dispatchers.IO) {
                    localEpisodeNotificationStore.disableSubscription(
                        profileId = toggleOwner.profileId,
                        seriesId = toggleOwner.seriesId,
                        expectedAccountId = toggleOwner.accountId,
                    )
                }
            } else {
                val episodes = try {
                    repository.seriesBundle(
                        toggleOwner.authenticatedSession.session,
                        series.id,
                    ).episodes
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    if (isCurrentSeriesNotificationToggle(toggleOwner)) {
                        onResult("تعذر جلب الحلقات الحالية. لم يتم تفعيل التنبيهات")
                    }
                    return@launch
                }
                if (!isCurrentSeriesNotificationToggle(toggleOwner)) return@launch
                val displayedKeys = reliableEpisodeKeys(currentState.episodes)
                val freshKeys = reliableEpisodeKeys(episodes)
                if (displayedKeys.isNotEmpty() && !freshKeys.containsAll(displayedKeys)) {
                    if (isCurrentSeriesNotificationToggle(toggleOwner)) {
                        onResult("وصلت بيانات حلقات غير مكتملة. لم يتم تفعيل التنبيهات")
                    }
                    return@launch
                }
                if (
                    !isCurrentSeriesNotificationToggle(toggleOwner) ||
                    !canUseSeriesNotifications(toggleOwner.profileKind, toggleOwner.seriesId)
                ) {
                    return@launch
                }
                enabledBaselineEpisodes = episodes
                withContext(Dispatchers.IO) {
                    localEpisodeNotificationStore.enableSubscription(
                        profileId = toggleOwner.profileId,
                        series = series,
                        episodes = episodes,
                        expectedAccountId = toggleOwner.accountId,
                    )
                }
            }
            if (!isCurrentSeriesNotificationToggle(toggleOwner)) return@launch
            if (result == EpisodeNotificationStoreResult.SUCCESS && !currentlyEnabled) {
                val refreshedEpisodes = enabledBaselineEpisodes.orEmpty()
                mutableState.update { state ->
                    if (
                        isCurrentSeriesNotificationToggle(toggleOwner) &&
                        state.selectedSeries?.id == series.id
                    ) {
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
                        "تعذر إنشاء خط أساس موثوق للحلقات. حاول لاحقًا"
                    EpisodeNotificationStoreResult.MISSING_ACCOUNT -> "سجل الدخول أولًا"
                    else -> "تعذر حفظ إعداد التنبيهات. حاول مرة أخرى"
                },
            )
        }
    }

    private fun isCurrentSeriesNotificationToggle(
        owner: SeriesNotificationToggleOwner,
    ): Boolean {
        val detailsGenerationCurrent = detailsRequestGate.isCurrentForContext(
            token = owner.detailsRequest,
            accountId = owner.accountId,
            profileId = owner.profileId,
        )
        val current = mutableState.value
        val selectedSeriesId = current.selectedSeries
            ?.takeIf { it.type == ContentType.SERIES }
            ?.id
        return seriesNotificationPublicationAllowed(
            sameAuthenticatedSession =
                AuthenticatedSessionRegistry.isCurrent(owner.authenticatedSession) &&
                    session === owner.authenticatedSession.session,
            detailsGenerationCurrent = detailsGenerationCurrent,
            expectedAccountId = owner.accountId,
            activeAccountId = localEpisodeNotificationStore.activeAccountId(),
            expectedProfileId = owner.profileId,
            activeProfileId = profileStore.activeProfileId(),
            screen = current.screen,
            expectedSeriesId = owner.seriesId,
            selectedSeriesId = selectedSeriesId,
        )
    }

    fun toggleEpisodeNotificationMaster(onResult: (String) -> Unit) {
        if (!mutableState.value.operations.features.episodeNotificationsEnabled) {
            onResult("تنبيهات الحلقات متوقفة مؤقتًا")
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
                    "تعذر تحديث إعداد التنبيهات"
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
            invalidateVoiceSearchContext()
        }
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
            val operationsSnapshot = withContext(Dispatchers.IO) {
                when (item) {
                    is LocalNotificationItem.Episode -> {
                        accountId?.let {
                            localEpisodeNotificationStore.markRead(profileId, notificationId, it)
                        }
                        operationsStore.systemNotificationSnapshot()
                    }
                    is LocalNotificationItem.System ->
                        operationsStore.markSystemNotificationReadAndSnapshot(notificationId)
                }
            }
            publishOperationsSystemNotifications(operationsSnapshot, clearPopup = false)
        }
    }

    fun markAllNotificationsRead() {
        val profileId = profileStore.activeProfileId()
        val accountId = localEpisodeNotificationStore.activeAccountId()
        viewModelScope.launch {
            val operationsSnapshot = withContext(Dispatchers.IO) {
                accountId?.let { localEpisodeNotificationStore.markAllRead(profileId, it) }
                operationsStore.markAllSystemNotificationsReadAndSnapshot()
            }
            publishOperationsSystemNotifications(operationsSnapshot, clearPopup = false)
        }
    }

    fun deleteNotification(notificationId: String) {
        val item = mutableState.value.localNotifications.firstOrNull { it.id == notificationId } ?: return
        val profileId = profileStore.activeProfileId()
        val accountId = localEpisodeNotificationStore.activeAccountId()
        viewModelScope.launch {
            val operationsSnapshot = withContext(Dispatchers.IO) {
                when (item) {
                    is LocalNotificationItem.Episode -> {
                        accountId?.let {
                            localEpisodeNotificationStore.deleteNotification(profileId, notificationId, it)
                        }
                        operationsStore.systemNotificationSnapshot()
                    }
                    is LocalNotificationItem.System ->
                        operationsStore.deleteSystemNotificationAndSnapshot(notificationId)
                }
            }
            publishOperationsSystemNotifications(operationsSnapshot, clearPopup = false)
        }
    }

    fun clearNotifications() {
        val profileId = profileStore.activeProfileId()
        val accountId = localEpisodeNotificationStore.activeAccountId()
        viewModelScope.launch {
            val operationsSnapshot = withContext(Dispatchers.IO) {
                accountId?.let { localEpisodeNotificationStore.clearNotifications(profileId, it) }
                operationsStore.clearSystemNotificationsAndSnapshot()
            }
            publishOperationsSystemNotifications(operationsSnapshot, clearPopup = true)
        }
    }

    fun openNotification(
        notificationId: String,
        onResult: (String?) -> Unit = {},
    ) {
        val item = mutableState.value.localNotifications.firstOrNull { it.id == notificationId }
        if (item is LocalNotificationItem.System) {
            viewModelScope.launch {
                val operationsSnapshot = withContext(Dispatchers.IO) {
                    operationsStore.markSystemNotificationReadAndSnapshot(notificationId)
                }
                publishOperationsSystemNotifications(
                    operationsSnapshot,
                    clearPopup = false,
                )
                onResult(null)
            }
            return
        }
        val profileId = profileStore.activeProfileId()
        val accountId = localEpisodeNotificationStore.activeAccountId()
        if (accountId == null) {
            onResult("سجل الدخول لفتح هذا المحتوى")
            return
        }
        viewModelScope.launch {
            val notification = (item as? LocalNotificationItem.Episode)?.notification
                ?: localEpisodeNotificationStore.snapshot(profileId)
                    .notifications
                    .firstOrNull { it.id == notificationId }
            if (notification == null) {
                onResult("هذا الإشعار لم يعد متاحًا")
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
        viewModelScope.launch {
            persistNotificationPopupShown(popup)
        }
    }

    fun dismissNotificationPopup() {
        val popup = mutableState.value.notificationPopup
        if (popup == null) {
            maybeShowPendingNotificationPopup()
            return
        }
        if (notificationPopupActionJob?.isActive == true) return
        notificationPopupActionJob = viewModelScope.launch {
            try {
                if (!persistNotificationPopupShown(popup)) return@launch
                if (mutableState.value.notificationPopup != popup) return@launch
                mutableState.update { state ->
                    if (state.notificationPopup == popup) {
                        state.copy(notificationPopup = null)
                    } else {
                        state
                    }
                }
                maybeShowPendingNotificationPopup()
            } finally {
                notificationPopupActionJob = null
            }
        }
    }

    fun activateNotificationPopup(onResult: (String?) -> Unit = {}) {
        val popup = mutableState.value.notificationPopup ?: return
        if (notificationPopupActionJob?.isActive == true) return
        notificationPopupActionJob = viewModelScope.launch {
            try {
                if (!persistNotificationPopupShown(popup)) return@launch
                if (mutableState.value.notificationPopup != popup) return@launch
                if (popup.summary) {
                    mutableState.update { state ->
                        if (state.notificationPopup == popup) {
                            state.copy(notificationPopup = null)
                        } else {
                            state
                        }
                    }
                    openNotificationCenter()
                    return@launch
                }
                val target = popup.notifications.maxWithOrNull(
                    compareBy(
                        LocalEpisodeNotification::seasonNumber,
                        LocalEpisodeNotification::episodeNumber,
                    ),
                ) ?: return@launch
                val targetError = openNotificationTarget(target)
                if (targetError != null) {
                    mutableState.update { state ->
                        if (state.notificationPopup == popup) {
                            state.copy(notificationPopup = null)
                        } else {
                            state
                        }
                    }
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
                if (
                    profileStore.activeProfileId() != popup.profileId ||
                    localEpisodeNotificationStore.activeAccountId() != target.accountId
                ) return@launch
                mutableState.update { state ->
                    if (state.notificationPopup == popup) {
                        state.copy(notificationPopup = null)
                    } else {
                        state
                    }
                }
                refreshNotificationState(clearPopup = false)
                onResult(null)
            } finally {
                notificationPopupActionJob = null
            }
        }
    }

    fun onProfileChanged() {
        downloadSettingsMutationGate.invalidate()
        downloadSnapshotPublicationGate.invalidate()
        downloadPrimaryActionDispatcher.invalidate()
        downloadPriorityMutationQueues.values.forEach(DownloadPriorityMutationQueue::invalidate)
        downloadPriorityMutationQueues.clear()
        invalidatePlayerProgressPersistence()
        notificationScanJob?.cancel()
        invalidateDetailsRequest()
        invalidateVoiceSearchContext()
        mutableState.update { state ->
            if (state.screen == HulkScreen.MOVIE_DETAILS || state.screen == HulkScreen.SERIES) {
                state.copy(
                    screen = HulkScreen.MAIN,
                    selectedItem = null,
                    selectedSeries = null,
                    selectedDetails = null,
                    episodes = emptyList(),
                    seriesEpisodeTarget = null,
                    downloads = emptyList(),
                    isLoading = false,
                    notificationPopup = null,
                    errorMessage = null,
                )
            } else {
                state.copy(downloads = emptyList(), notificationPopup = null)
            }
        }
        val expectedProfileId = profileStore.activeProfileId()
        viewModelScope.launch {
            val downloads = loadDownloadUiSnapshot(downloadRepository::snapshot)
            val settings = withContext(Dispatchers.IO) { downloadRepository.settings() }
            if (profileStore.activeProfileId() == expectedProfileId) {
                mutableState.update { state ->
                    state.copy(downloads = downloads, downloadSettings = settings)
                }
            }
        }
        refreshNotificationState(clearPopup = true)
        scanSubscribedSeries(NotificationScanTrigger.PROFILE_SWITCH)
        scheduleTvPlatformSync(immediate = true)
    }

    fun removeNotificationProfileData(accountId: String, profileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            localEpisodeNotificationStore.removeProfile(profileId, accountId)
        }
    }

    fun removeNotificationProfileData(profileId: String) {
        val accountId = localEpisodeNotificationStore.activeAccountId() ?: return
        removeNotificationProfileData(accountId, profileId)
    }

    fun removeDownloadProfileData(accountId: String, profileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.removeProfile(accountId, profileId)
        }
    }

    fun removeDownloadProfileData(profileId: String) {
        val accountId = downloadRepository.activeAccountIdForCleanup() ?: return
        removeDownloadProfileData(accountId, profileId)
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
                "تعذر فتح إعدادات التثبيت على هذا الجهاز. افتح إعدادات الأمان يدويًا"
            },
        )
    }

    fun confirmOperationsAnnouncement(expectedId: String? = null) {
        if (operationsAnnouncementConfirmationJob?.isActive == true) return
        val popup = mutableState.value.operations.announcementPopup ?: return
        // A Home-owned detail must acknowledge exactly the message it presented; if the active
        // announcement changed underneath it, the newer message keeps its own presentation path.
        if (expectedId != null && popup.id != expectedId) return
        presentedOperationsMessageIds += popup.id
        operationsAnnouncementConfirmationJob = viewModelScope.launch {
            try {
                val acknowledged = runOperationsPersistenceOffMain {
                    if (popup.showOnce) {
                        operationsStore.acknowledgeMessage(popup.id)
                    }
                    operationsStore.acknowledgedMessageIds()
                }
                updateOperationsAnnouncementPresentation(acknowledged)
            } finally {
                operationsAnnouncementConfirmationJob = null
            }
        }
    }

    private fun refreshOperations(force: Boolean) {
        if (operationsRefreshJob?.isActive == true) return
        operationsRefreshJob = viewModelScope.launch {
            val (cached, operationsSnapshot) = runOperationsPersistenceOffMain {
                operationsStore.cachedConfig() to operationsStore.systemNotificationSnapshot()
            }
            publishOperationsSystemNotifications(
                operationsSnapshot,
                clearPopup = false,
            )
            if (activeOperationsConfig == null) {
                if (cached != null) {
                    applyOperationsConfig(
                        config = cached.config,
                        source = OperationsConfigSource.CACHE,
                        fetchedAtEpochMs = cached.fetchedAtEpochMs,
                    )
                } else {
                    refreshNotificationState(clearPopup = false)
                }
            }

            val nowEpochMs = System.currentTimeMillis()
            if (
                !force &&
                cached != null &&
                nowEpochMs - cached.fetchedAtEpochMs in 0 until OPERATIONS_CACHE_TTL_MS
            ) return@launch

            when (val result = operationsClient.fetch()) {
                is OperationsFetchResult.Success -> {
                    val saved = runOperationsPersistenceOffMain {
                        operationsStore.saveConfig(result.rawJson, result.fetchedAtEpochMs)
                    }
                    if (saved) {
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

    private suspend fun applyOperationsFailure() {
        val cached = runOperationsPersistenceOffMain(operationsStore::cachedConfig)
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

    private suspend fun applyOperationsConfig(
        config: OperationsConfig,
        source: OperationsConfigSource,
        fetchedAtEpochMs: Long,
    ) {
        activeOperationsConfig = config
        activeOperationsFetchedAtEpochMs = fetchedAtEpochMs
        presenceLifecycleOwner.updateDiscovery(config.presence)
        pairingController.updateDiscovery(if (operationsDeviceIsTv) config.pairing else null)
        val nowEpochMs = System.currentTimeMillis()
        val activeAnnouncements = eligibleOperationsAnnouncements(
            announcements = config.announcements,
            currentVersionCode = BuildConfig.VERSION_CODE,
            isTv = operationsDeviceIsTv,
            nowEpochSeconds = nowEpochMs / 1_000L,
            acknowledgedMessageIds = emptySet(),
            presentedMessageIds = emptySet(),
        )
        val (acknowledged, operationsSnapshot) = runOperationsPersistenceOffMain {
            operationsStore.recordImportantAnnouncementsAndSnapshot(
                announcements = activeAnnouncements,
                generatedAtEpochSeconds = config.generatedAtEpochSeconds,
            )
        }
        publishOperationsSystemNotifications(operationsSnapshot, clearPopup = false)
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

    private fun updateOperationsAnnouncementPresentation(acknowledgedMessageIds: Set<String>) {
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
            acknowledgedMessageIds = acknowledgedMessageIds,
            presentedMessageIds = presentedOperationsMessageIds,
        ).firstOrNull()
        mutableState.update { state ->
            state.copy(operations = state.operations.copy(announcementPopup = next))
        }
    }

    fun setNotificationUiReady(ready: Boolean) {
        val becameReady = ready && !notificationUiReady
        val readinessChanged = notificationUiReady != ready
        notificationUiReady = ready
        if (readinessChanged) invalidateVoiceSearchContext()
        if (ready) {
            maybeShowPendingNotificationPopup()
            if (becameReady && profileStore.activeProfile().kind == ProfileKind.KIDS) {
                scanSubscribedSeries(NotificationScanTrigger.PROFILE_SWITCH)
            }
        } else {
            mutableState.update { it.copy(notificationPopup = null) }
        }
    }

    private fun publishOperationsSystemNotifications(
        snapshot: OperationsNotificationSnapshot,
        clearPopup: Boolean,
    ) {
        if (operationsStore.isCurrentSystemNotificationSnapshot(snapshot)) {
            operationsSystemNotifications = snapshot.notifications
            refreshNotificationState(clearPopup)
        } else {
            // The local episode state may still have changed, but this older Operations
            // snapshot is not allowed to replace the latest persisted snapshot.
            refreshNotificationState(clearPopup = false)
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
            systemNotifications = operationsSystemNotifications,
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
        val activeSession = session ?: return "سجل الدخول لفتح هذا المحتوى"
        val profile = profileStore.activeProfile()
        if (notification.accountId != localEpisodeNotificationStore.activeAccountId()) {
            return "هذا الإشعار يخص حسابًا آخر"
        }
        if (notification.profileId != profile.id) return "هذا الإشعار يخص ملفًا شخصيًا آخر"
        if (!canUseSeriesNotifications(profile.kind, notification.seriesId)) {
            return "هذا المحتوى غير متاح في ملف الأطفال"
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
            onResult("سجل الدخول اولا لتحديث بيانات الاشتراك")
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
                    val expectedOwner = AuthenticatedSessionRegistry.currentOwner()
                    val publication = sessionPublicationGate.publishIfCurrent(
                        authenticated = refreshed,
                        expectedOwner = expectedOwner,
                    ) {
                        invalidateDiagnosticsForSessionChange()
                        invalidatePlayerProgressPersistence()
                        sessionOwnershipEpoch += 1
                        session = refreshed
                        mutableState.update {
                            it.copy(
                                account = refreshed.account,
                                isAccountRefreshing = false,
                                errorMessage = null,
                            )
                        }
                    }
                    when (publication) {
                        is SessionPublicationResult.Published -> {
                            catalogRequestOwner.replaceSession(
                                session = refreshed,
                                requiredTypes = catalogTypesForDestination(mutableState.value.destination),
                            )
                            publishPresenceOwnership(refreshed)
                            onResult("تم تحديث بيانات الاشتراك من السيرفر.")
                        }
                        SessionPublicationResult.Expired -> {
                            when (val cleanup = handleExpiredOwner(expectedOwner)) {
                                SessionOwnershipCleanupResult.Cleared -> {
                                    onResult("تعذر تحديث الاشتراك. اعد تسجيل الدخول")
                                }
                                is SessionOwnershipCleanupResult.CredentialRemovalFailed -> {
                                    onResult(
                                        cleanup.error.message
                                            ?: "تعذر إزالة بيانات الجلسة. حاول مرة اخرى.",
                                    )
                                }
                                SessionOwnershipCleanupResult.Stale -> Unit
                            }
                        }
                        SessionPublicationResult.Stale -> Unit
                    }
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
                            "تعذر تحديث الاشتراك. اعد تسجيل الدخول"
                        } else {
                            error.message ?: "تعذر تحديث بيانات الاشتراك"
                        },
                    )
                }
            }
        }
    }

    fun runDiagnostics() {
        val activeSession = session ?: run {
            mutableState.update {
                it.copy(diagnostics = it.diagnostics.copy(errorMessage = "سجل الدخول اولا لتشغيل الفحص"))
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
                                    ?: "حدث خطا غير متوقع اثناء الفحص",
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
                playerProgressProfileOwner = null
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
        catalogRequestOwner.invalidate()
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

        presenceLifecycleOwner.logout()
        authenticationAttemptGate.invalidate()
        pairingController.cancel()
        pendingAccountDecision = null
        invalidateAccountRefresh()
        val pendingLoginJob = loginJob
        pendingLoginJob?.cancel()
        loginJob = null
        invalidateDetailsRequest()
        val operationsState = mutableState.value.operations
        val pairingState = mutableState.value.pairing
        beginTvPlatformProfileTransition(resetPublishedScope = true)
        pendingTvDeepLink = null
        notificationScanJob?.cancel()
        notificationScanJob = null
        notificationUiReady = false
        invalidateCatalogRequests()
        clearCatalogMemory()
        invalidateDiagnosticsForSessionChange()
        invalidatePlayerProgressPersistence()
        downloadSettingsMutationGate.invalidate()
        downloadSnapshotPublicationGate.invalidate()
        downloadPrimaryActionDispatcher.invalidate()
        downloadPriorityMutationQueues.values.forEach(DownloadPriorityMutationQueue::invalidate)
        downloadPriorityMutationQueues.clear()
        logoutJob = viewModelScope.launch {
            try {
                pendingLoginJob?.join()
                withContext(Dispatchers.IO) {
                    downloadRepository.suspendActiveAccountForLogout()
                }
                repository.logout()
                sessionOwnershipEpoch += 1
                session = null
                sessionRestorationComplete = true
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
                        accountDecision = null,
                    )
                }
                val downloadSettings = withContext(Dispatchers.IO) { downloadRepository.settings() }
                mutableState.value = HulkUiState(
                    isStarting = false,
                    downloads = emptyList(),
                    downloadSettings = downloadSettings,
                    notificationSubscribedSeriesIds = emptySet(),
                    localNotifications = emptyList(),
                    unreadNotificationCount = 0,
                    episodeNotificationsEnabled = true,
                    notificationPopup = null,
                    operations = operationsState,
                    pairing = pairingState,
                    errorMessage = errorMessage,
                )
            } catch (error: CredentialEnvelopeRemovalException) {
                // Fail closed: durable credential removal did not complete, so the session and
                // account state stay in place and logout is not reported as completed.
                showFailure(error)
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
            var decisionPending = false
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
                        accountDecision = null,
                    )
                }
                decisionPending = runAuthenticationAttempt(
                    credentials = credentials,
                    remember = true,
                    attemptGeneration = restoreGeneration,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (!authenticationAttemptGate.isCurrent(restoreGeneration)) return@launch
                sessionRestorationComplete = true
                showFailure(error)
                resolvePendingTvDeepLink()
            } finally {
                if (!decisionPending && authenticationAttemptGate.complete(restoreGeneration)) {
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
        beginReservedAuthentication(credentials, remember, attemptGeneration)
    }

    /**
     * Runs an already reserved attempt through the existing loginJob/state/finally ownership.
     * The reservation may come from a manual submit or from the serialized paired handoff; both
     * share the one gate and stale-completion rules, and neither can reserve a second attempt.
     */
    private fun beginReservedAuthentication(
        credentials: Credentials,
        remember: Boolean,
        attemptGeneration: Long,
    ) {
        invalidateAccountRefresh()
        mutableState.update {
            it.copy(
                isStarting = false,
                isLoading = true,
                isAccountRefreshing = false,
                errorMessage = null,
                accountDecision = null,
            )
        }
        loginJob = viewModelScope.launch {
            var decisionPending = false
            try {
                decisionPending = runAuthenticationAttempt(
                    credentials = credentials,
                    remember = remember,
                    attemptGeneration = attemptGeneration,
                )
            } finally {
                if (!decisionPending && authenticationAttemptGate.complete(attemptGeneration)) {
                    loginJob = null
                }
            }
        }
    }

    /**
     * Returns true only while an explicit ownership decision is pending. The attempt generation
     * stays open in that case so no other authentication can start and a late result cannot
     * complete the decision after ownership changed.
     */
    private suspend fun runAuthenticationAttempt(
        credentials: Credentials,
        remember: Boolean,
        attemptGeneration: Long,
    ): Boolean {
        try {
            when (val outcome = repository.beginAuthentication(credentials, remember)) {
                is AuthenticationStartResult.Completed -> {
                    if (!authenticationAttemptGate.isCurrent(attemptGeneration)) return false
                    completeAuthenticatedLogin(
                        authenticated = outcome.session,
                        expectedOwner = AuthenticatedSessionRegistry.currentOwner(),
                        attemptGeneration = attemptGeneration,
                    )
                    return false
                }
                is AuthenticationStartResult.DecisionRequired -> {
                    if (!authenticationAttemptGate.isCurrent(attemptGeneration)) return false
                    val labeledCandidates = accountIdentityCandidateLabels(
                        outcome.pending.candidates,
                    )
                    pendingAccountDecision = PendingAccountDecision(
                        generation = attemptGeneration,
                        outcome = outcome.pending,
                        remember = remember,
                        labeledCandidates = labeledCandidates,
                    )
                    mutableState.update {
                        it.copy(
                            isStarting = false,
                            isLoading = false,
                            accountDecision = AccountDecisionUiState(
                                username = outcome.pending.username,
                                options = labeledCandidates.mapIndexed { index, candidate ->
                                    AccountDecisionOption(
                                        key = index.toString(),
                                        label = candidate.label,
                                    )
                                },
                            ),
                        )
                    }
                    return true
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (!authenticationAttemptGate.isCurrent(attemptGeneration)) return false
            sessionRestorationComplete = true
            showFailure(error)
            resolvePendingTvDeepLink()
            return false
        }
    }

    /**
     * Applies the user's explicit same/different/cancel decision. Cancel abandons the pending
     * identity without any binding or trusted host; same/different commit through the repository
     * only when the decision is still the current attempt.
     */
    internal fun resolveAccountDecision(choice: AccountDecisionChoice) {
        val pending = pendingAccountDecision ?: return
        pendingAccountDecision = null
        if (!authenticationAttemptGate.isCurrent(pending.generation)) {
            mutableState.update { it.copy(accountDecision = null, isLoading = false) }
            return
        }
        when (choice) {
            is AccountDecisionChoice.Cancel -> {
                mutableState.update {
                    it.copy(accountDecision = null, isLoading = false, isStarting = false)
                }
                loginJob = viewModelScope.launch {
                    try {
                        repository.abandonPendingAuthentication(pending.outcome)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: CredentialEnvelopeRemovalException) {
                        showFailure(error)
                    } finally {
                        if (authenticationAttemptGate.complete(pending.generation)) {
                            loginJob = null
                        }
                    }
                }
            }

            is AccountDecisionChoice.ExistingAccount,
            is AccountDecisionChoice.DifferentSubscription,
            -> {
                val selectedAccountId = when (choice) {
                    is AccountDecisionChoice.ExistingAccount ->
                        pending.labeledCandidates
                            .getOrNull(choice.key.toIntOrNull() ?: -1)
                            ?.accountId
                    else -> null
                }
                if (choice is AccountDecisionChoice.ExistingAccount && selectedAccountId == null) {
                    mutableState.update { it.copy(accountDecision = null, isLoading = false) }
                    if (authenticationAttemptGate.complete(pending.generation)) loginJob = null
                    return
                }
                mutableState.update {
                    it.copy(accountDecision = null, isLoading = true, errorMessage = null)
                }
                loginJob = viewModelScope.launch {
                    try {
                        val authenticated = repository.completeAuthenticationDecision(
                            pending = pending.outcome,
                            selectedAccountId = selectedAccountId,
                            remember = pending.remember,
                        )
                        if (authenticationAttemptGate.isCurrent(pending.generation)) {
                            completeAuthenticatedLogin(
                                authenticated = authenticated,
                                expectedOwner = AuthenticatedSessionRegistry.currentOwner(),
                                attemptGeneration = pending.generation,
                            )
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        if (authenticationAttemptGate.isCurrent(pending.generation)) {
                            sessionRestorationComplete = true
                            showFailure(error)
                            resolvePendingTvDeepLink()
                        }
                    } finally {
                        if (authenticationAttemptGate.complete(pending.generation)) {
                            loginJob = null
                        }
                    }
                }
            }
        }
    }

    private suspend fun completeAuthenticatedLogin(
        authenticated: AuthenticatedSession,
        expectedOwner: AuthenticatedSessionOwner?,
        attemptGeneration: Long,
    ) {
        pairingController.cancel()
        invalidateCatalogRequests()
        invalidateDiagnosticsForSessionChange()
        invalidatePlayerProgressPersistence()
        downloadSettingsMutationGate.invalidate()
        downloadSnapshotPublicationGate.invalidate()
        downloadPrimaryActionDispatcher.invalidate()
        downloadPriorityMutationQueues.values.forEach(DownloadPriorityMutationQueue::invalidate)
        downloadPriorityMutationQueues.clear()
        clearCatalogMemory()
        val downloads = loadDownloadUiSnapshot(downloadRepository::snapshot)
        val downloadSettings = withContext(Dispatchers.IO) {
            downloadRepository.settings()
        }
        val publication = sessionPublicationGate.publishIfCurrent(
            authenticated = authenticated,
            expectedOwner = expectedOwner,
            attemptCurrent = { authenticationAttemptGate.isCurrent(attemptGeneration) },
        ) {
            sessionOwnershipEpoch += 1
            session = authenticated
            sessionRestorationComplete = true
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
                    accountDecision = null,
                )
            }
        }
        when (publication) {
            is SessionPublicationResult.Published -> Unit
            SessionPublicationResult.Expired -> {
                handleExpiredOwner(
                    expectedOwner = expectedOwner,
                    attemptCurrent = { authenticationAttemptGate.isCurrent(attemptGeneration) },
                )
                return
            }
            SessionPublicationResult.Stale -> return
        }
        publishPresenceOwnership(authenticated)
        ensureDestinationCatalogs(MainDestination.HOME)
        refreshOperations(force = false)
        refreshNotificationState(clearPopup = true)
        scanSubscribedSeries(NotificationScanTrigger.APP_START)
        resolvePendingTvDeepLink()
    }

    /**
     * Clears an expired session only if the exact owner is still current at the actual
     * session/credential/scope mutation, then resets visible state only if no newer publication
     * happened while cleanup was in flight.
     */
    private suspend fun handleExpiredOwner(
        expectedOwner: AuthenticatedSessionOwner?,
        attemptCurrent: () -> Boolean = { true },
    ): SessionOwnershipCleanupResult {
        if (expectedOwner == null || !attemptCurrent()) {
            return SessionOwnershipCleanupResult.Stale
        }
        val result = sessionCleanupGate.clearIfOwnerStillCurrent(expectedOwner) {
            sessionOwnershipEpoch += 1
            session = null
            sessionRestorationComplete = true
            invalidateAccountRefresh()
            invalidateCatalogRequests()
            clearCatalogMemory()
            invalidateDetailsRequest()
            invalidateDiagnosticsForSessionChange()
            invalidatePlayerProgressPersistence()
            downloadSettingsMutationGate.invalidate()
            downloadSnapshotPublicationGate.invalidate()
            downloadPrimaryActionDispatcher.invalidate()
            downloadPriorityMutationQueues.values.forEach(DownloadPriorityMutationQueue::invalidate)
            downloadPriorityMutationQueues.clear()
            presenceLifecycleOwner.logout()
            val state = mutableState.value
            mutableState.value = HulkUiState(
                isStarting = false,
                downloads = emptyList(),
                downloadSettings = state.downloadSettings,
                operations = state.operations,
                pairing = state.pairing,
                errorMessage = XtreamException.SubscriptionInactive.message,
            )
        }
        if (
            result is SessionOwnershipCleanupResult.CredentialRemovalFailed &&
            attemptCurrent()
        ) {
            mutableState.update { state ->
                state.finishExpiredOwnerFailure(
                    result.error.message ?: "تعذر إزالة بيانات الجلسة. حاول مرة اخرى.",
                )
            }
        } else if (result is SessionOwnershipCleanupResult.CredentialRemovalFailed) {
            return SessionOwnershipCleanupResult.Stale
        }
        return result
    }

    private suspend fun publishPresenceOwnership(authenticated: AuthenticatedSession) {
        val snapshot = try {
            repository.presenceSessionSnapshot(
                expectedSession = authenticated,
                device = presenceDeviceMetadata,
                app = presenceAppMetadata,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        if (snapshot != null && session === authenticated) {
            presenceLifecycleOwner.onAuthenticated(snapshot)
        }
    }

    private fun ensureCatalog(type: ContentType, force: Boolean = false) {
        val activeSession = session ?: return
        if (!catalogRequestOwner.request(activeSession, type, force)) {
            if (!force) {
                mutableState.update { state ->
                    val display = catalogsForDestination(state.destination, state.catalogs)
                    if (display == state.catalogs) state else state.copy(catalogs = display)
                }
            }
            return
        }
        mutableState.update { it.copy(errorMessage = null) }
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
            ?: return failPendingTvDeepLink("تعذر تحديد الملف الشخصي النشط")
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
                "هذا المحتوى لم يعد متاحًا",
            )
            TvDeepLinkResolution.BlockedForKids -> failPendingTvDeepLink(
                "هذا المحتوى غير متاح في ملف الأطفال",
            )
            TvDeepLinkResolution.StaleProfile -> failPendingTvDeepLink(
                "هذا العنصر يخص ملفًا شخصيًا آخر",
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
                    failPendingTvDeepLink("هذا المحتوى غير متاح في ملف الأطفال")
                    return@launch
                }
                if (rechecked == TvDeepLinkResolution.StaleProfile) {
                    failPendingTvDeepLink("هذا العنصر يخص ملفًا شخصيًا آخر")
                    return@launch
                }
                if (rechecked !is TvDeepLinkResolution.OpenEpisode) {
                    failPendingTvDeepLink("هذا المحتوى لم يعد متاحًا")
                    return@launch
                }
                val episode = findTvDeepLinkEpisode(bundle.episodes, resolution.episodeId)
                    ?: run {
                        failPendingTvDeepLink("هذا المحتوى لم يعد متاحًا")
                        return@launch
                    }
                pendingTvDeepLink = null
                playerReturnScreen = HulkScreen.MAIN
                startPlayback(repository.playback(activeSession, resolution.series, episode))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (pendingTvDeepLink == target) {
                    failPendingTvDeepLink("تعذر فتح هذا المحتوى الآن")
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
        playerProgressProfileOwner = null
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
        val expectedProfileId = profileStore.activeProfileId()
        playerProgressProfileOwner = expectedProfileId
        val resumable = request.copy(resumePositionMs = userLibrary.resumePosition(request.historyKey))
        val expectedOwner = AuthenticatedSessionRegistry.currentOwner()
            ?.takeIf { it.session === session }
        val historyAttempt = userLibrary.currentHistoryAttempt()
        mutableState.update {
            it.copy(
                screen = HulkScreen.PLAYER,
                playback = resumable,
                errorMessage = null,
            )
        }
        if (expectedOwner == null) return
        viewModelScope.launch {
            val historyStartMs = android.os.SystemClock.uptimeMillis()
            val updatedHistory = userLibrary.recordStartForOwner(
                expectedOwner = expectedOwner,
                expectedAccountId = expectedOwner.accountId,
                expectedProfileId = expectedProfileId,
                request = resumable,
                historyAttempt = historyAttempt,
            ) ?: return@launch
            android.util.Log.i(
                "HulkPlayer",
                "history recorded t=${android.os.SystemClock.elapsedRealtime()} streamId=${request.streamId} " +
                    "in ${android.os.SystemClock.uptimeMillis() - historyStartMs}ms",
            )
            if (!historyPublicationAllowed(
                    attemptCurrent = userLibrary.isCurrentHistoryMutation(historyAttempt),
                    sessionCurrent = AuthenticatedSessionRegistry.isCurrent(expectedOwner) &&
                        session === expectedOwner.session,
                    expectedAccountId = expectedOwner.accountId,
                    activeAccountId = repository.activeAccountSession()?.accountId,
                    expectedProfileId = expectedProfileId,
                    activeProfileId = profileStore.activeProfileId(),
                )
            ) return@launch
            mutableState.update { it.copy(history = updatedHistory) }
        }
    }

    private fun enqueueDownload(
        request: PlaybackRequest,
        expectedSession: AuthenticatedSession,
        seriesTitle: String? = null,
        season: Int? = null,
        episodeNumber: Int? = null,
        replaceDownloadId: Long? = null,
        onResult: (DownloadFeedback) -> Unit,
    ) {
        if (!pendingDownloadEnqueues.add(request.historyKey)) {
            onResult(downloadDuplicatePendingFeedback())
            return
        }
        val expectedAccountId = repository.activeAccountSession()?.accountId
        val expectedProfileId = profileStore.activeProfileId()
        if (expectedAccountId.isNullOrBlank() || expectedProfileId.isBlank()) {
            pendingDownloadEnqueues.remove(request.historyKey)
            onResult(downloadOwnerFixFailedFeedback())
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

                val feedback = when (val result = outcome.result) {
                    is DownloadRepository.EnqueueResult.Started -> {
                        mutableState.update { it.copy(downloads = outcome.downloads) }
                        downloadPreparingFeedback()
                    }
                    is DownloadRepository.EnqueueResult.AlreadyExists -> when (result.item.status) {
                        OfflineStatus.COMPLETED -> downloadAlreadyCompletedFeedback()
                        else -> downloadAlreadyQueuedFeedback()
                    }
                    is DownloadRepository.EnqueueResult.Failed -> DownloadFeedback(
                        message = result.message,
                        glyph = DownloadFeedbackGlyph.ERROR,
                    )
                }
                onResult(feedback)
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
                    onResult(downloadGenericFailureFeedback())
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
            beginLogout(error.message ?: "حدث خطا غير متوقع. حاول مرة اخرى")
            return
        }
        mutableState.update {
            it.copy(
                screen = if (invalidSession) HulkScreen.LOGIN else it.screen,
                isStarting = false,
                isLoading = false,
                isAccountRefreshing = false,
                errorMessage = error.message ?: "حدث خطا غير متوقع. حاول مرة اخرى",
                accountDecision = null,
            )
        }
    }

    companion object {

        private const val MOVIE_CARD_PROBE_CONCURRENCY = 2
        private const val EPISODE_NOTIFICATION_SCAN_CONCURRENCY = 3
        private const val TV_PLATFORM_SYNC_DEBOUNCE_MS = 350L
    }
}

/**
 * One catalog request plan for authenticated startup, destination entry and manual refresh.
 * Home consumes Live for its existing recommendations/history presentation, so it must load
 * and project Live through the same per-type pipeline as Movie and Series.
 */
internal fun catalogTypesForDestination(destination: MainDestination): Set<ContentType> =
    when (destination) {
        MainDestination.HOME -> setOf(
            ContentType.MOVIE,
            ContentType.SERIES,
            ContentType.LIVE,
        )
        MainDestination.LIVE -> setOf(ContentType.LIVE)
        MainDestination.MOVIES -> setOf(ContentType.MOVIE)
        MainDestination.SERIES -> setOf(ContentType.SERIES)
        MainDestination.FAVORITES,
        MainDestination.SEARCH,
        -> ContentType.entries.toSet()
        MainDestination.DOWNLOADS,
        MainDestination.SETTINGS -> emptySet()
    }

/**
 * Destination projection over the existing caches. Home keeps compact Movie/Series catalogs but
 * publishes the full Live catalog because Live has no compact Home derivation.
 */
internal fun projectCatalogsForDestination(
    destination: MainDestination,
    current: Map<ContentType, Catalog>,
    loaded: Map<ContentType, Catalog>,
    home: Map<ContentType, Catalog>,
): Map<ContentType, Catalog> {
    if (loaded.isEmpty()) return current
    val displayed = current.toMutableMap()
    fun showFull(type: ContentType) {
        loaded[type]?.let { displayed[type] = it }
    }
    fun showHome(type: ContentType) {
        (home[type] ?: loaded[type])?.let { displayed[type] = it }
    }
    when (destination) {
        MainDestination.HOME -> {
            showHome(ContentType.MOVIE)
            showHome(ContentType.SERIES)
            showFull(ContentType.LIVE)
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

/**
 * A loaded catalog satisfies a non-forced entry; an in-flight request always satisfies both
 * entry and refresh so overlapping startup/Home/refresh triggers cannot duplicate transport.
 */
internal fun catalogRequestShouldStart(
    hasLoadedCatalog: Boolean,
    force: Boolean,
    inFlight: Boolean,
): Boolean {
    if (inFlight) return false
    return force || !hasLoadedCatalog
}

/** A catalog result may publish only for the session and generation that started it. */
internal fun catalogPublicationAllowed(
    requestGeneration: Long,
    currentGeneration: Long,
    sessionCurrent: Boolean,
): Boolean = sessionCurrent && requestGeneration == currentGeneration
