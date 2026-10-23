package sa.hulksa.player.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import sa.hulksa.player.AccountDecisionChoice
import sa.hulksa.player.HulkScreen
import sa.hulksa.player.HulkViewModel
import sa.hulksa.player.MainDestination
import sa.hulksa.player.data.OperationsAnnouncement
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.ui.adaptive.ApplyAdaptiveWindowPresentation
import sa.hulksa.player.ui.adaptive.HulkNavigationType
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.adaptive.rememberAdaptiveUiState
import sa.hulksa.player.ui.adaptive.trackAdaptiveInput
import sa.hulksa.player.ui.screens.AccountIdentityDecisionDialog
import sa.hulksa.player.ui.screens.DownloadFeedback
import sa.hulksa.player.ui.screens.DownloadFeedbackGlyph
import sa.hulksa.player.ui.screens.HomeActionNotice
import sa.hulksa.player.ui.screens.LIVE_TV_PRO_CONTEXT_ALL
import sa.hulksa.player.ui.screens.LIVE_TV_PRO_CONTEXT_FAVORITES
import sa.hulksa.player.ui.screens.LocalNotificationCenterScreen
import sa.hulksa.player.ui.screens.LoginScreen
import sa.hulksa.player.ui.screens.MainShellScreen
import sa.hulksa.player.ui.screens.MovieDetailsProPolishedScreen
import sa.hulksa.player.ui.screens.NavigationMemoryStore
import sa.hulksa.player.ui.screens.PlayerProScreen
import sa.hulksa.player.ui.screens.ScopedHomeActionNotice
import sa.hulksa.player.ui.screens.SeriesDetailsProPolishedScreen
import sa.hulksa.player.ui.screens.detailsProRelatedItems
import sa.hulksa.player.ui.screens.downloadPrimaryActionIntent
import sa.hulksa.player.ui.screens.favoriteFeedbackIcon
import sa.hulksa.player.ui.screens.favoriteMutationFeedback
import sa.hulksa.player.ui.screens.downloadNoticeDisplayText
import sa.hulksa.player.ui.screens.liveTvProDecorateMainState
import sa.hulksa.player.ui.screens.liveTvProMainCategoryToContext
import sa.hulksa.player.ui.screens.playerProEpisodeNeighbors
import sa.hulksa.player.ui.screens.scopedNoticeDisplayText
import sa.hulksa.player.ui.screens.saveLiveTvProLaunchContext
import sa.hulksa.player.ui.theme.LocalHulkColors

internal fun detailsEntryShouldBlock(
    remoteLoading: Boolean,
    immediateShellAvailable: Boolean,
): Boolean = remoteLoading && !immediateShellAvailable

/**
 * The pairing entry exists only on real television hardware with an enabled validated discovery:
 * window width, D-pad presence or an adaptive wide layout never turns a phone/tablet into a TV.
 */
internal fun pairingEntryOffered(
    hardwareTelevision: Boolean,
    pairingAvailable: Boolean,
): Boolean = hardwareTelevision && pairingAvailable

@Composable
fun HulkApp(
    viewModel: HulkViewModel,
    isTelevisionDevice: Boolean,
    navigationMemory: NavigationMemoryStore,
    catalogNavigationMemory: ProfileCatalogNavigationMemory,
    homeMessagePresentation: HomeMessagePresentationState,
    onOpenHomeAnnouncementDetail: (OperationsAnnouncement) -> Unit,
    onOpenHomeUpdateDetail: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    // Preserves the Series details composition state (selected tab, season and scroll position)
    // across the playback round-trip for the exact series the owner opened.
    val seriesDetailsStateHolder = rememberSaveableStateHolder()
    // One-time TV Search entry focus per profile; hoisted above the destination layer so a
    // Details round-trip cannot re-grab the field over the restored card.
    var searchInitialFocusAcquired by remember(navigationMemory) { mutableStateOf(false) }
    // Scoped ephemeral presentation for a real notification-opening failure result.
    var notificationOpenError by remember { mutableStateOf<String?>(null) }
    // ONE scoped in-app action notice for Home/Settings/voice actions and download feedback; it
    // replaces the previous unstyled Toasts without creating a competing notice owner. The
    // sequence keeps consecutive identical real outcomes distinct events, so an obsolete timeout
    // can never clear a newer notice.
    var actionNotice by remember { mutableStateOf<ScopedNoticeEvent?>(null) }
    var actionNoticeSequence by remember { mutableLongStateOf(0L) }
    val mobileHomeFocusRequester = remember { FocusRequester() }
    LaunchedEffect(actionNotice) {
        val event = actionNotice ?: return@LaunchedEffect
        delay(5_000L)
        if (actionNotice == event) actionNotice = null
    }
    val (adaptiveUi, adaptiveInputController) = rememberAdaptiveUiState(isTelevisionDevice)
    val isTv = adaptiveUi.isTelevision
    val context = LocalContext.current
    val colors = LocalHulkColors.current
    // A transient notice never outlives its owning screen/destination/profile/disposal.
    LaunchedEffect(state.screen, state.destination) { actionNotice = null }
    LaunchedEffect(catalogNavigationMemory) { actionNotice = null }
    val postActionNotice: (String, ImageVector) -> Unit = { message, icon ->
        actionNoticeSequence += 1L
        actionNotice = ScopedNoticeEvent(
            id = actionNoticeSequence,
            notice = HomeActionNotice(message = message, icon = icon),
        )
    }
    val voiceSearchFeedbackState = LocalVoiceSearchFeedback.current
    val voiceSearchFeedback = voiceSearchFeedbackState?.value
    LaunchedEffect(voiceSearchFeedback) {
        val feedback = voiceSearchFeedback ?: return@LaunchedEffect
        voiceSearchFeedbackState.value = null
        // A completed outcome is only valid for the owner that still owns the Search request;
        // a late result from a previous screen/account/session/profile/generation is dropped.
        if (viewModel.currentVoiceSearchOwner() == feedback.owner) {
            postActionNotice(
                feedback.message,
                when (feedback.glyph) {
                    VoiceSearchFeedbackGlyph.VOICE -> Icons.Rounded.Mic
                    VoiceSearchFeedbackGlyph.ERROR -> Icons.Outlined.ErrorOutline
                },
            )
        }
    }
    val postDownloadFeedback: (DownloadFeedback) -> Unit = { feedback ->
        // Download feedback belongs to the download-owning screens; a late result must not
        // paint over the player or the entry/notification surfaces.
        if (
            state.screen == HulkScreen.MAIN ||
            state.screen == HulkScreen.MOVIE_DETAILS ||
            state.screen == HulkScreen.SERIES
        ) {
            postActionNotice(
                downloadNoticeDisplayText(feedback.message),
                downloadFeedbackIcon(feedback.glyph),
            )
        }
    }
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
        !isTv &&
            !isPhoneHome &&
            state.screen != HulkScreen.PLAYER &&
            state.screen != HulkScreen.LOGIN &&
            state.screen != HulkScreen.NOTIFICATION_CENTER
    // The compact phone action bars share the same normal MAIN bottom-navigation boundary.
    val showMobileMainChrome =
        state.screen == HulkScreen.MAIN &&
            !isTv &&
            adaptiveUi.navigationType != HulkNavigationType.RAIL
    ApplyAdaptiveWindowPresentation(
        isTelevisionDevice = isTv,
        isPlayer = state.screen == HulkScreen.PLAYER,
    )
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
    // ONE Notifications opener for every header host: records the logical Home hero anchor so
    // returning from the center restores one deterministic target.
    val openNotificationCenter: () -> Unit = {
        navigationMemory.save(MainDestination.HOME, "", 0, "hero", 0)
        viewModel.openNotificationCenter()
    }

    BackHandler(
        enabled = state.screen == HulkScreen.MOVIE_DETAILS || state.screen == HulkScreen.SERIES,
        onBack = viewModel::back,
    )

    // Pairing polling belongs to the foreground LOGIN entry state: a backgrounded app or a screen
    // change stops pending work; returning requires a fresh eligible user action.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.cancelPairing()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.screen, viewModel) {
        if (state.screen != HulkScreen.LOGIN) viewModel.cancelPairing()
    }

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
                    HulkScreen.LOGIN -> {
                        LoginScreen(
                            isTv = isTv,
                            pairingOffered = pairingEntryOffered(
                                hardwareTelevision = isTelevisionDevice,
                                pairingAvailable = state.pairing.available,
                            ),
                            pairing = state.pairing,
                            pairingReturnFocusSuspended = state.accountDecision != null,
                            isStarting = state.isStarting,
                            isLoading = state.isLoading,
                            errorMessage = state.errorMessage,
                            onLogin = viewModel::login,
                            onStartPairing = viewModel::startPairing,
                            onRetryPairing = viewModel::retryPairing,
                            onRegeneratePairing = viewModel::regeneratePairing,
                            onCancelPairing = viewModel::cancelPairing,
                        )
                        state.accountDecision?.let { decision ->
                            AccountIdentityDecisionDialog(
                                username = decision.username,
                                options = decision.options,
                                onSelectExisting = { key ->
                                    viewModel.resolveAccountDecision(
                                        AccountDecisionChoice.ExistingAccount(key),
                                    )
                                },
                                onDifferentSubscription = {
                                    viewModel.resolveAccountDecision(
                                        AccountDecisionChoice.DifferentSubscription,
                                    )
                                },
                                onCancel = {
                                    viewModel.resolveAccountDecision(AccountDecisionChoice.Cancel)
                                },
                            )
                        }
                    }

                    HulkScreen.MAIN -> {
                        if (state.destination == MainDestination.SEARCH) {
                            // The one-time TV entry focus belongs above the destination layer so a
                            // details round-trip cannot re-grab the field over the restored card.
                            ProfileSmartSearchLayer(
                                state = state,
                                isTv = isTv,
                                isFavorite = viewModel::isFavorite,
                                navigationMemory = navigationMemory,
                                initialFocusAcquired = searchInitialFocusAcquired,
                                onInitialFocusAcquired = {
                                    searchInitialFocusAcquired = true
                                },
                                onSelectDestination = selectDestinationWithProfileContext,
                                onSearch = viewModel::updateSearch,
                                onRefresh = viewModel::refresh,
                                onOpen = openWithLiveContext,
                                onToggleFavorite = { item ->
                                    viewModel.toggleFavorite(item) { outcome ->
                                        favoriteMutationFeedback(outcome, item.name)?.let { feedback ->
                                            postActionNotice(
                                                feedback.message,
                                                favoriteFeedbackIcon(feedback.glyph),
                                            )
                                        }
                                    }
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
                                        MainDestination.HOME -> state
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
                                    onRemoveHistoryEntry = { key, result ->
                                        viewModel.removeHistoryEntry(key, result)
                                    },
                                    onToggleFavoriteWithResult = { item, result ->
                                        viewModel.toggleFavorite(item, result)
                                    },
                                    onRefresh = viewModel::refresh,
                                    onOpenNotifications = openNotificationCenter,
                                    onClearHistory = viewModel::clearHistory,
                                    onPlayDownload = viewModel::playDownload,
                                    onDeleteDownload = { item ->
                                        viewModel.deleteDownload(item, postDownloadFeedback)
                                    },
                                    onDownloadPrimaryAction = { item, intent ->
                                        viewModel.onDownloadPrimaryAction(
                                            item = item,
                                            intent = intent,
                                            onResult = postDownloadFeedback,
                                        )
                                    },
                                    onToggleWifiOnly = { postDownloadFeedback(viewModel.toggleWifiOnly()) },
                                    onToggleDownloadSchedule = {
                                        postDownloadFeedback(viewModel.toggleDownloadSchedule())
                                    },
                                    onCycleConcurrentDownloads = {
                                        postDownloadFeedback(viewModel.cycleConcurrentDownloads())
                                    },
                                    onToggleEpisodeNotificationMaster = {
                                        viewModel.toggleEpisodeNotificationMaster { message ->
                                            postActionNotice(
                                                scopedNoticeDisplayText(message),
                                                Icons.Rounded.Notifications,
                                            )
                                        }
                                    },
                                    onCycleDownloadPriority = { item ->
                                        postDownloadFeedback(viewModel.cycleDownloadPriority(item))
                                    },
                                    onActionNotice = postActionNotice,
                                    onRefreshAccount = { viewModel.refreshAccount { message ->
                                        postActionNotice(
                                            scopedNoticeDisplayText(message),
                                            Icons.Rounded.Refresh,
                                        )
                                    } },
                                    onRunDiagnostics = viewModel::runDiagnostics,
                                    onLogout = viewModel::logout,
                                    homeMessagePresentation = homeMessagePresentation,
                                    onOpenAnnouncementDetail = onOpenHomeAnnouncementDetail,
                                    onOpenUpdateDetail = onOpenHomeUpdateDetail,
                                    mobileHomeFocusRequester = mobileHomeFocusRequester,
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
                                isLoading = detailsEntryShouldBlock(
                                    remoteLoading = state.isLoading,
                                    immediateShellAvailable = state.selectedDetails != null,
                                ),
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
                                    if (movieDownload == null) {
                                        viewModel.downloadSelectedMovie(postDownloadFeedback)
                                    } else {
                                        downloadPrimaryActionIntent(movieDownload.status)?.let { intent ->
                                            viewModel.onDownloadPrimaryAction(
                                                item = movieDownload,
                                                intent = intent,
                                                onResult = postDownloadFeedback,
                                            )
                                        }
                                    }
                                },
                                onCancelDownload = {
                                    movieDownload?.let {
                                        viewModel.deleteDownload(it, postDownloadFeedback)
                                    }
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(item) },
                                onToggleRelatedFavorite = { related ->
                                    viewModel.toggleFavorite(related) { outcome ->
                                        favoriteMutationFeedback(outcome, related.name)?.let { feedback ->
                                            notify(feedback.message)
                                        }
                                    }
                                },
                                onOpenRelated = viewModel::open,
                                onRetryDetails = { viewModel.open(item) },
                            )
                        } else {
                            LaunchedEffect(state.screen) { viewModel.back() }
                        }
                    }

                    HulkScreen.SERIES -> {
                        val series = state.selectedSeries
                        if (series != null) {
                            seriesDetailsStateHolder.SaveableStateProvider("series_details_${series.id}") {
                            val relatedSeries = detailsProRelatedItems(
                                source = series,
                                candidates = state.catalogs[ContentType.SERIES]?.items.orEmpty(),
                            )
                            SeriesDetailsProPolishedScreen(
                                series = series,
                                details = state.selectedDetails,
                                episodes = state.episodes,
                                isLoading = detailsEntryShouldBlock(
                                    remoteLoading = state.isLoading,
                                    immediateShellAvailable = state.selectedDetails != null,
                                ),
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
                                    if (existing == null) {
                                        viewModel.downloadEpisode(episode, postDownloadFeedback)
                                    } else {
                                        downloadPrimaryActionIntent(existing.status)?.let { intent ->
                                            viewModel.onDownloadPrimaryAction(
                                                item = existing,
                                                intent = intent,
                                                onResult = postDownloadFeedback,
                                            )
                                        }
                                    }
                                },
                                onCancelDownload = { episode ->
                                    state.downloads.firstOrNull {
                                        it.historyKey == "SERIES:${episode.id}"
                                    }?.let {
                                        viewModel.deleteDownload(it, postDownloadFeedback)
                                    }
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(series) },
                                onToggleNotifications = {
                                    viewModel.toggleSeriesNotifications(series) { message ->
                                        postActionNotice(
                                            scopedNoticeDisplayText(message),
                                            Icons.Rounded.Notifications,
                                        )
                                    }
                                },
                                onToggleRelatedFavorite = { related ->
                                    viewModel.toggleFavorite(related) { outcome ->
                                        favoriteMutationFeedback(outcome, related.name)?.let { feedback ->
                                            notify(feedback.message)
                                        }
                                    }
                                },
                                onOpenRelated = viewModel::open,
                                onRetryDetails = { viewModel.open(series) },
                            )
                            }
                        } else {
                            LaunchedEffect(state.screen) { viewModel.back() }
                        }
                    }

                    HulkScreen.NOTIFICATION_CENTER -> LocalNotificationCenterScreen(
                        notifications = state.localNotifications,
                        unreadCount = state.unreadNotificationCount,
                        isTv = isTv,
                        onBack = {
                            notificationOpenError = null
                            viewModel.back()
                        },
                        onOpen = { notification ->
                            notificationOpenError = null
                            viewModel.openNotification(notification.id) { message ->
                                notificationOpenError = message
                            }
                        },
                        onMarkRead = { notification -> viewModel.markNotificationRead(notification.id) },
                        onReadAll = viewModel::markAllNotificationsRead,
                        onDelete = { notification -> viewModel.deleteNotification(notification.id) },
                        onClearAll = viewModel::clearNotifications,
                    )

                    HulkScreen.PLAYER -> {
                        val playback = state.playback
                        if (playback != null) {
                            val episodeNeighbors = playerProEpisodeNeighbors(
                                episodes = if (playback.streamKind == "series") state.episodes else emptyList(),
                                currentStreamId = playback.streamId,
                            )
                            val vodFavoriteItem = if (playback.isLive) {
                                null
                            } else if (playback.streamKind == "series") {
                                val seriesId = playback.parentContentId ?: playback.streamId
                                state.catalogs[ContentType.SERIES]?.items?.firstOrNull { it.id == seriesId }
                            } else {
                                state.catalogs[ContentType.MOVIE]?.items?.firstOrNull { it.id == playback.streamId }
                            }
                            PlayerProScreen(
                                request = playback,
                                liveTvProEnabled = state.operations.features.liveTvProEnabled,
                                liveCatalog = state.catalogs[ContentType.LIVE],
                                isFavorite = viewModel::isFavorite,
                                favoriteKeys = state.favorites,
                                vodItem = vodFavoriteItem,
                                onSelectLiveChannel = viewModel::switchLiveChannel,
                                onToggleFavorite = { item, result ->
                                    viewModel.toggleFavorite(item, result)
                                },
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

                if (showMobileMainChrome) {
                    StableMobileBottomNavigation(
                        selected = state.destination,
                        downloadsEnabled = state.operations.features.downloadsEnabled,
                        onSelectDestination = selectDestinationWithProfileContext,
                        homeFocusRequester = mobileHomeFocusRequester,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                actionNotice?.let { event ->
                    ScopedHomeActionNotice(
                        notice = event.notice,
                        isTv = isTv,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                bottom = if (isTv) {
                                    28.dp
                                } else {
                                    stableMobileNavigationReservedHeight(
                                        windowWidthDp = adaptiveUi.screenWidthDp,
                                        downloadsEnabled = state.operations.features.downloadsEnabled,
                                    ) + 12.dp
                                },
                            ),
                    )
                }
            }
        }
    }
}

/** A notice event with its own identity so consecutive identical messages stay distinct. */
private data class ScopedNoticeEvent(
    val id: Long,
    val notice: HomeActionNotice,
)

/** Semantic glyph mapping for the scoped download feedback notice. */
private fun downloadFeedbackIcon(glyph: DownloadFeedbackGlyph): ImageVector = when (glyph) {
    DownloadFeedbackGlyph.PREPARING -> Icons.Rounded.Downloading
    DownloadFeedbackGlyph.QUEUED -> Icons.Rounded.Download
    DownloadFeedbackGlyph.COMPLETED -> Icons.Rounded.Check
    DownloadFeedbackGlyph.PAUSE -> Icons.Rounded.Pause
    DownloadFeedbackGlyph.RESUME -> Icons.Rounded.PlayArrow
    DownloadFeedbackGlyph.RETRY -> Icons.Rounded.Refresh
    DownloadFeedbackGlyph.CANCEL -> Icons.Rounded.DeleteOutline
    DownloadFeedbackGlyph.DELETE -> Icons.Rounded.DeleteOutline
    DownloadFeedbackGlyph.WIFI -> Icons.Rounded.Language
    DownloadFeedbackGlyph.SCHEDULE -> Icons.Rounded.Schedule
    DownloadFeedbackGlyph.CONCURRENCY -> Icons.Rounded.Tune
    DownloadFeedbackGlyph.PRIORITY -> Icons.Rounded.Tune
    DownloadFeedbackGlyph.ERROR -> Icons.Outlined.ErrorOutline
}
