@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package sa.hulksa.player.ui.screens

import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import sa.hulksa.player.data.SettingsProStore
import sa.hulksa.player.data.AuthenticatedSessionOwner
import sa.hulksa.player.data.HomeHeroMetadataStore
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.PlaybackRequest
import sa.hulksa.player.playback.HulkPlayerFactory
import sa.hulksa.player.playback.PlayerAudioDiagnostics
import sa.hulksa.player.playback.PlayerAudioOutputMode
import sa.hulksa.player.playback.PlayerSessionCallbacks
import sa.hulksa.player.playback.PlayerSessionController
import sa.hulksa.player.playback.RecoveryCommand
import sa.hulksa.player.playback.RecoveryCommandType
import sa.hulksa.player.playback.RecoveryDispatchOwner
import sa.hulksa.player.playback.RecoveryFailureClass
import sa.hulksa.player.playback.forPlayerReplacement
import sa.hulksa.player.playback.ownsRecoveryCommand
import sa.hulksa.player.playback.playerReplacementPolicy
import sa.hulksa.player.playback.shouldReprepareAfterAudioTrackOverride
import sa.hulksa.player.ui.adaptive.HulkInputMode
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.adaptive.tvPremiumWindowPolicy
import sa.hulksa.player.ui.components.BrandBadge
import sa.hulksa.player.ui.components.ChannelLogo
import sa.hulksa.player.ui.components.ErrorNotice
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.theme.LocalHulkColors
import java.util.Locale

private const val CONTROLS_TIMEOUT_MS = 5_000L
private const val NEXT_EPISODE_SECONDS = 8
private const val PLAYER_OFFLINE_MESSAGE = "لا يوجد اتصال بالإنترنت. سيتم استئناف التشغيل تلقائيا عند عودة الاتصال."

internal enum class PlayerLifecyclePlaybackAction { NONE, STOP, PREPARE }

internal fun playerBackgroundLifecycleAction(playbackState: Int): PlayerLifecyclePlaybackAction =
    when (playbackState) {
        Player.STATE_BUFFERING, Player.STATE_READY -> PlayerLifecyclePlaybackAction.STOP
        else -> PlayerLifecyclePlaybackAction.NONE
    }

internal fun playerForegroundLifecycleAction(
    playbackState: Int,
    hasMediaItem: Boolean,
    hasPlaybackError: Boolean,
    sourceAvailable: Boolean,
): PlayerLifecyclePlaybackAction =
    if (
        playbackState == Player.STATE_IDLE &&
        hasMediaItem &&
        !hasPlaybackError &&
        sourceAvailable
    ) {
        PlayerLifecyclePlaybackAction.PREPARE
    } else {
        PlayerLifecyclePlaybackAction.NONE
    }

internal fun shouldAdvancePlayerAutoplayCountdown(
    appForeground: Boolean,
    countdown: Int,
): Boolean = appForeground && countdown >= 0

private enum class PlayerPanel { AUDIO, SUBTITLES, SPEED, RESIZE, QUALITY, SERVERS }

private data class PlayerTrackOption(
    val key: String,
    val label: String,
    val secondary: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val selected: Boolean,
)

private data class PlayerReplacementState(
    val candidateIndex: Int,
    val outputMode: PlayerAudioOutputMode,
    val positionMs: Long?,
    val playWhenReady: Boolean,
    val speed: Float,
    val volume: Float,
    val trackSelectionParameters: TrackSelectionParameters,
)

private data class SuspendedPlayerError(
    val message: String,
    val failureClass: RecoveryFailureClass?,
)

private fun hasUsableNetwork(context: Context): Boolean {
    val manager = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

private fun PlaybackRequest.usesOnlyLocalMedia(): Boolean =
    candidates.isNotEmpty() && candidates.all { candidate ->
        val source = candidate.trim()
        source.startsWith("file:", ignoreCase = true) ||
            source.startsWith("content:", ignoreCase = true)
    }

private fun movieCardQualityLabel(height: Int): String? = when {
    height >= 2160 -> "4K"
    height >= 1440 -> "QHD"
    height >= 1080 -> "FHD"
    height >= 720 -> "HD"
    height > 0 -> "SD"
    else -> null
}

private fun cacheVerifiedMovieCardMetadata(
    metadataStore: HomeHeroMetadataStore,
    metadataOwner: AuthenticatedSessionOwner?,
    request: PlaybackRequest,
    videoHeight: Int? = null,
    durationMs: Long? = null,
) {
    if (!request.streamKind.equals("movie", ignoreCase = true)) return
    val owner = metadataOwner ?: return
    metadataStore.cacheMovieMetadata(
        owner = owner,
        movieId = request.streamId,
        quality = videoHeight?.let(::movieCardQualityLabel),
        durationMs = durationMs,
    )
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    request: PlaybackRequest,
    liveCatalog: Catalog?,
    isFavorite: (ContentItem) -> Boolean,
    favoriteKeys: Set<String> = emptySet(),
    onSelectLiveChannel: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onLastChannel: (() -> Unit)? = null,
    onBack: () -> Unit,
    onProgress: (request: PlaybackRequest, positionMs: Long, durationMs: Long) -> Unit,
    nextEpisodeTitle: String? = null,
    onPlayNextEpisode: (() -> Unit)? = null,
    onErrorModalActiveChanged: (Boolean) -> Unit = {},
    onBrowserVisibilityChanged: (Boolean) -> Unit = {},
    onPanelActiveChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsProStore = remember(context) { SettingsProStore(context) }
    val contentMetadataStore = remember(context) { HomeHeroMetadataStore.get(context) }
    val contentMetadataOwner = remember(request, contentMetadataStore) {
        contentMetadataStore.currentOwner()
    }
    val playbackSettings = remember(context, request.historyKey) { settingsProStore.playbackSettings() }
    val seekStepMs = playbackSettings.seekStepSeconds * 1_000L
    val adaptiveUi = LocalAdaptiveUi.current
    val tvRemoteInput = adaptiveUi.isTelevision || adaptiveUi.inputMode == HulkInputMode.REMOTE
    val localPlayback = remember(request) { request.usesOnlyLocalMedia() }
    val playerSession = remember(request) { PlayerSessionController(request) }
    val playerFactory = remember(context) { HulkPlayerFactory(context) }
    val playerDiagnostics = remember(context, playerSession) {
        PlayerAudioDiagnostics(
            context = context,
            request = request,
            generation = playerSession.generation,
            sourcePlan = playerSession.sourcePlan,
        )
    }
    val playerDisplayTitle = remember(request) {
        val seriesTitle = request.seriesTitle?.trim().orEmpty()
        val episodeTitle = request.episodeTitle?.trim().orEmpty()
        if (
            request.streamKind.equals("series", ignoreCase = true) &&
            seriesTitle.isNotBlank() &&
            episodeTitle.startsWith(seriesTitle, ignoreCase = true)
        ) {
            episodeTitle
        } else {
            request.title
        }
    }
    var candidateIndex by remember(playerSession) {
        mutableIntStateOf(playerSession.firstCandidateIndex ?: 0)
    }
    var retryNonce by remember(request) { mutableIntStateOf(0) }
    var playerInstanceGeneration by remember(request) { mutableIntStateOf(0) }
    var audioOutputMode by remember(request) { mutableStateOf(PlayerAudioOutputMode.NORMAL) }
    var pendingPlayerReplacement by remember(request) { mutableStateOf<PlayerReplacementState?>(null) }
    var recoveryDelayMs by remember(request) { mutableLongStateOf(0L) }
    var pendingSeekMs by remember(request) { mutableLongStateOf(0L) }
    var finalError by remember(request) { mutableStateOf<String?>(null) }
    var finalFailureClass by remember(request) { mutableStateOf<RecoveryFailureClass?>(null) }
    var suspendedFinalError by remember(request) { mutableStateOf<SuspendedPlayerError?>(null) }
    var offlineFailure by remember(request) { mutableStateOf(false) }
    var networkAvailable by remember(context, request) { mutableStateOf(hasUsableNetwork(context)) }
    var buffering by remember(request) { mutableStateOf(true) }
    var controlsVisible by remember(request) { mutableStateOf(!request.isLive) }
    var browserVisible by remember(request) { mutableStateOf(false) }
    var browserOrigin by remember(request) { mutableStateOf(LiveChannelBrowserOrigin.NORMAL_LIVE) }
    var activePanel by remember(request) { mutableStateOf<PlayerPanel?>(null) }
    var liveMorePanel by remember(request) { mutableStateOf<PlayerLiveMorePanelView?>(null) }
    var liveMoreMenuFocusRow by remember(request) { mutableStateOf(PlayerLiveMoreRow.MUTE) }
    var moreFocusRestoreTick by remember(request) { mutableIntStateOf(0) }
    var liveControlsStripHeightPx by remember(request) { mutableIntStateOf(0) }
    var isPlaying by remember(request) { mutableStateOf(false) }
    var isMuted by remember(request) { mutableStateOf(false) }
    var videoHeight by remember(request) { mutableIntStateOf(0) }
    var resizeModeIndex by remember(request) { mutableIntStateOf(0) }
    var playbackSpeed by remember(request) { mutableFloatStateOf(1f) }
    var currentPositionMs by remember(request) { mutableLongStateOf(0L) }
    var manualSeekTargetMs by remember(request) { mutableStateOf<Long?>(null) }
    var durationMs by remember(request) { mutableLongStateOf(0L) }
    var cachedMovieDurationMs by remember(request) { mutableLongStateOf(0L) }
    var bufferedPercent by remember(request) { mutableIntStateOf(0) }
    var surfaceFocused by remember { mutableStateOf(false) }
    var controlsLocked by remember(request) { mutableStateOf(false) }
    var unlockVisible by remember(request) { mutableStateOf(false) }
    var seekFeedback by remember(request) { mutableStateOf<String?>(null) }
    var seekBarFocused by remember(request) { mutableStateOf(false) }
    var focusTimelineOnReveal by remember(request) { mutableStateOf(false) }
    var resumePromptVisible by remember(request) {
        mutableStateOf(playbackSettings.resumePlayback && !request.isLive && request.resumePositionMs > 0L)
    }
    var nextCountdown by remember(request) { mutableIntStateOf(-1) }
    var audioTracks by remember(request) { mutableStateOf(emptyList<PlayerTrackOption>()) }
    var subtitleTracks by remember(request) { mutableStateOf(emptyList<PlayerTrackOption>()) }
    var videoTracks by remember(request) { mutableStateOf(emptyList<PlayerTrackOption>()) }
    var hasActiveAudio by remember(request) { mutableStateOf(false) }
    var hasActiveSubtitles by remember(request) { mutableStateOf(false) }
    var audioLanguageLabel by remember(request) { mutableStateOf("") }
    var subtitleLanguageLabel by remember(request) { mutableStateOf("") }
    var subtitleSizeIndex by remember(request) { mutableIntStateOf(1) }
    var subtitleRaised by remember(request) { mutableStateOf(false) }
    var appForeground by remember(lifecycleOwner, request) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    val resizeModes = remember {
        listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
        )
    }
    val playerFocus = remember { FocusRequester() }
    val primaryFocus = remember { FocusRequester() }
    val moreTriggerFocus = remember { FocusRequester() }
    val seekBarFocus = remember { FocusRequester() }
    val resumeFocus = remember { FocusRequester() }
    val unlockFocus = remember { FocusRequester() }
    val nextEpisodePlayFocus = remember { FocusRequester() }
    val nextEpisodeCancelFocus = remember { FocusRequester() }
    val errorRetryFocus = remember { FocusRequester() }
    val currentChannel = remember(liveCatalog, request.streamId) {
        liveCatalog?.items?.firstOrNull { it.id == request.streamId }
    }
    val liveFavoriteChannel = currentChannel.takeIf { request.isLive }
    // The observed favorites snapshot is an explicit reactive input. The authoritative predicate
    // reads the view model's StateFlow, so keying the derived control on the snapshot guarantees
    // the HUD repaints on the same state publication instead of waiting for the player-health tick.
    val liveFavoriteControl = remember(liveFavoriteChannel, favoriteKeys) {
        livePlayerFavoriteControl(
            currentChannel = liveFavoriteChannel,
            isFavorite = liveFavoriteChannel?.let(isFavorite) == true,
        )
    }
    val liveControlsLayout = remember(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp, tvRemoteInput) {
        liveControlsLayoutMetrics(
            screenWidthDp = adaptiveUi.screenWidthDp,
            screenHeightDp = adaptiveUi.screenHeightDp,
            remoteLayout = tvRemoteInput,
        )
    }
    val liveControlMetrics = remember(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp, tvRemoteInput) {
        livePlayerControlsMetrics(
            screenWidthDp = adaptiveUi.screenWidthDp,
            screenHeightDp = adaptiveUi.screenHeightDp,
            remoteLayout = tvRemoteInput,
        )
    }
    val channelSequence = remember(liveCatalog, currentChannel) {
        val inCategory = currentChannel?.let { current ->
            liveCatalog?.items.orEmpty().filter { it.categoryId == current.categoryId }
        }.orEmpty()
        inCategory.ifEmpty { liveCatalog?.items.orEmpty() }
    }
    val switchRelative: (Int) -> Unit = { delta ->
        if (channelSequence.isNotEmpty()) {
            val currentIndex = channelSequence.indexOfFirst { it.id == request.streamId }.takeIf { it >= 0 } ?: 0
            onSelectLiveChannel(channelSequence[relativeChannelIndex(currentIndex, delta, channelSequence.size)])
        }
    }
    val latestSwitchRelative by rememberUpdatedState(switchRelative)

    val player = remember(request, playerInstanceGeneration, audioOutputMode) {
        playerFactory.create(audioOutputMode)
    }
    val recoveryDispatchOwner = RecoveryDispatchOwner(
        generationId = playerSession.generation.id,
        playerInstanceId = playerInstanceGeneration,
    )
    val latestRecoveryDispatchOwner by rememberUpdatedState(recoveryDispatchOwner)

    fun clearFinalErrorState() {
        finalError = null
        finalFailureClass = null
        suspendedFinalError = null
    }

    fun suspendFinalErrorForModal() {
        val message = finalError ?: return
        suspendedFinalError = SuspendedPlayerError(message, finalFailureClass)
        finalError = null
        finalFailureClass = null
    }

    fun restoreSuspendedFinalError() {
        val suspended = suspendedFinalError ?: return
        if (finalError == null) {
            finalError = suspended.message
            finalFailureClass = suspended.failureClass
        }
        suspendedFinalError = null
    }

    fun closeBrowserAndRestoreError() {
        browserVisible = false
        restoreSuspendedFinalError()
    }

    fun closeActivePanelAndRestoreError() {
        val restoreError = activePanel == PlayerPanel.SERVERS
        activePanel = null
        if (restoreError) restoreSuspendedFinalError()
    }

    fun captureReplacementState(
        targetCandidateIndex: Int,
        targetOutputMode: PlayerAudioOutputMode,
    ): PlayerReplacementState {
        val replacementPolicy = playerReplacementPolicy(
            isLive = request.isLive,
            currentPositionMs = player.currentPosition,
            currentCandidateIndex = candidateIndex,
            targetCandidateIndex = targetCandidateIndex,
        )
        return PlayerReplacementState(
            candidateIndex = targetCandidateIndex,
            outputMode = targetOutputMode,
            positionMs = replacementPolicy.positionMs,
            playWhenReady = player.playWhenReady,
            speed = player.playbackParameters.speed,
            volume = player.volume,
            trackSelectionParameters = player.trackSelectionParameters.forPlayerReplacement(
                outputMode = targetOutputMode,
                sourceChanged = replacementPolicy.sourceChanged,
            ),
        )
    }

    fun applyRecoveryCommand(command: RecoveryCommand): Boolean {
        if (command.generationId != playerSession.generation.id) return false
        clearFinalErrorState()
        offlineFailure = false

        return when (command.type) {
            RecoveryCommandType.SELECT_ALTERNATE_AUDIO_TRACK -> {
                val track = command.audioTrack ?: return false
                val group = player.currentTracks.groups.getOrNull(track.groupIndex) ?: return false
                if (group.type != C.TRACK_TYPE_AUDIO || track.trackIndex !in 0 until group.length) return false
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
                    .build()
                if (shouldReprepareAfterAudioTrackOverride(command.trigger)) {
                    recoveryDelayMs = command.delayMs
                    buffering = true
                    retryNonce += 1
                }
                true
            }
            RecoveryCommandType.RECREATE_WITH_SOFTWARE_AUDIO -> {
                val targetMode = PlayerAudioOutputMode.PLATFORM_SOFTWARE_PCM
                pendingPlayerReplacement = captureReplacementState(command.candidateIndex, targetMode)
                recoveryDelayMs = command.delayMs
                buffering = true
                audioOutputMode = targetMode
                playerInstanceGeneration += 1
                true
            }
            RecoveryCommandType.RETRY_CURRENT_SOURCE -> {
                pendingSeekMs = if (request.isLive) 0L else player.currentPosition.coerceAtLeast(0L)
                recoveryDelayMs = command.delayMs
                buffering = true
                retryNonce += 1
                true
            }
            RecoveryCommandType.MOVE_TO_NEXT_SOURCE -> {
                pendingSeekMs = if (request.isLive) 0L else player.currentPosition.coerceAtLeast(0L)
                recoveryDelayMs = command.delayMs
                buffering = true
                if (audioOutputMode == PlayerAudioOutputMode.PLATFORM_SOFTWARE_PCM) {
                    pendingPlayerReplacement = captureReplacementState(
                        targetCandidateIndex = command.candidateIndex,
                        targetOutputMode = PlayerAudioOutputMode.NORMAL,
                    )
                    audioOutputMode = PlayerAudioOutputMode.NORMAL
                    playerInstanceGeneration += 1
                } else {
                    player.trackSelectionParameters = player.trackSelectionParameters.forPlayerReplacement(
                        outputMode = PlayerAudioOutputMode.NORMAL,
                        sourceChanged = true,
                    )
                }
                candidateIndex = command.candidateIndex
                true
            }
            RecoveryCommandType.SHOW_FINAL_ERROR -> false
        }
    }

    fun retryManually(targetCandidateIndex: Int = playerSession.firstCandidateIndex ?: 0) {
        if (!playerSession.resetForManualRetry(targetCandidateIndex)) {
            suspendedFinalError = null
            finalFailureClass = RecoveryFailureClass.SOURCE
            finalError = "لا يوجد رابط تشغيل صالح لهذا المحتوى."
            buffering = false
            return
        }
        offlineFailure = false
        clearFinalErrorState()
        pendingSeekMs = if (request.isLive) 0L else player.currentPosition.coerceAtLeast(0L)
        recoveryDelayMs = 0L
        if (audioOutputMode != PlayerAudioOutputMode.NORMAL) {
            pendingPlayerReplacement = captureReplacementState(
                targetCandidateIndex = targetCandidateIndex,
                targetOutputMode = PlayerAudioOutputMode.NORMAL,
            )
            audioOutputMode = PlayerAudioOutputMode.NORMAL
            playerInstanceGeneration += 1
        } else if (targetCandidateIndex != candidateIndex) {
            player.trackSelectionParameters = player.trackSelectionParameters.forPlayerReplacement(
                outputMode = PlayerAudioOutputMode.NORMAL,
                sourceChanged = true,
            )
        }
        candidateIndex = targetCandidateIndex
        retryNonce += 1
    }

    fun revealControls() {
        focusTimelineOnReveal = false
        controlsVisible = true
        runCatching { primaryFocus.requestFocus() }
    }

    fun seekBy(deltaMs: Long) {
        if (request.isLive || durationMs <= 0L) return
        val base = manualSeekTargetMs ?: currentPositionMs.takeIf { it > 0L } ?: player.currentPosition.coerceAtLeast(0L)
        val target = (base + deltaMs).coerceIn(0L, durationMs)
        manualSeekTargetMs = target
        currentPositionMs = target
        if (tvRemoteInput) player.seekTo(target)
        val seconds = kotlin.math.abs(deltaMs) / 1_000L
        seekFeedback = if (deltaMs > 0) "+$seconds ث" else "-$seconds ث"
        controlsVisible = true
    }

    fun seekToPosition(targetMs: Long) {
        if (request.isLive || durationMs <= 0L) return
        val target = targetMs.coerceIn(0L, durationMs)
        player.seekTo(target)
        manualSeekTargetMs = target
        currentPositionMs = target
        seekFeedback = "انتقال الى ${formatTime(target)}"
        controlsVisible = true
    }

    fun saveCurrentProgress() {
        if (!request.isLive) {
            onProgress(request, player.currentPosition.coerceAtLeast(0L), player.duration.coerceAtLeast(0L))
        }
    }

    fun saveAndBack() {
        saveCurrentProgress()
        onBack()
    }

    fun saveAndPlayNext() {
        saveCurrentProgress()
        onPlayNextEpisode?.invoke()
    }

    fun applyTrack(type: Int, option: PlayerTrackOption?) {
        val builder = player.trackSelectionParameters.buildUpon()
        if (option == null) {
            builder.clearOverridesOfType(type)
            builder.setTrackTypeDisabled(type, type == C.TRACK_TYPE_TEXT)
        } else {
            val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
            builder.setTrackTypeDisabled(type, false)
            builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        }
        player.trackSelectionParameters = builder.build()
        activePanel = null
        revealControls()
    }

    fun openLiveMorePanel() {
        liveMoreMenuFocusRow = PlayerLiveMoreRow.MUTE
        liveMorePanel = PlayerLiveMorePanelView.MENU
    }

    fun closeLiveMorePanelToTrigger() {
        liveMorePanel = null
        moreFocusRestoreTick += 1
    }

    fun closeLiveMorePanelForSelection() {
        liveMorePanel = null
        revealControls()
    }

    fun handleLiveMoreBack() {
        val currentView = liveMorePanel ?: return
        val nextView = playerLiveMorePanelBack(currentView)
        if (nextView == null) {
            closeLiveMorePanelToTrigger()
        } else {
            liveMoreMenuFocusRow = playerLiveMorePanelOriginRow(currentView) ?: liveMoreMenuFocusRow
            liveMorePanel = nextView
        }
    }

    fun handleBackAction() {
        when {
            browserVisible -> browserVisible = false
            finalError != null -> saveAndBack()
            activePanel != null -> activePanel = null
            liveMorePanel != null -> handleLiveMoreBack()
            resumePromptVisible -> {
                resumePromptVisible = false
                player.seekTo(0L)
                player.play()
            }
            nextCountdown >= 0 -> nextCountdown = -1
            controlsLocked -> {
                unlockVisible = true
                controlsVisible = true
            }
            controlsVisible -> {
                controlsVisible = false
                focusTimelineOnReveal = false
                activePanel = null
                browserVisible = false
            }
            else -> saveAndBack()
        }
        restoreSuspendedFinalError()
    }

    BackHandler(enabled = browserVisible) {
        closeBrowserAndRestoreError()
    }
    BackHandler(enabled = !browserVisible) {
        handleBackAction()
    }

    DisposableEffect(lifecycleOwner, playerSession, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    appForeground = true
                    playerSession.onAppForegroundChanged(player, foreground = true)
                }
                Lifecycle.Event.ON_STOP -> {
                    appForeground = false
                    playerSession.onAppForegroundChanged(player, foreground = false)
                    if (
                        playerBackgroundLifecycleAction(player.playbackState) ==
                        PlayerLifecyclePlaybackAction.STOP
                    ) {
                        saveCurrentProgress()
                        player.stop()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        appForeground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        playerSession.onAppForegroundChanged(player, appForeground)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(playerSession) {
        onDispose { playerSession.invalidate() }
    }

    LaunchedEffect(appForeground, localPlayback, networkAvailable, player) {
        if (
            appForeground &&
            playerForegroundLifecycleAction(
                playbackState = player.playbackState,
                hasMediaItem = player.mediaItemCount > 0,
                hasPlaybackError = player.playerError != null,
                sourceAvailable = localPlayback || networkAvailable,
            ) == PlayerLifecyclePlaybackAction.PREPARE
        ) {
            player.prepare()
        }
    }

    DisposableEffect(context, request) {
        val manager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val mainHandler = Handler(Looper.getMainLooper())
        val callback = object : ConnectivityManager.NetworkCallback() {
            private fun publishNetworkState() {
                val available = hasUsableNetwork(context)
                mainHandler.post { networkAvailable = available }
            }

            override fun onAvailable(network: Network) = publishNetworkState()

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = publishNetworkState()

            override fun onLost(network: Network) = publishNetworkState()
        }

        if (manager != null) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    manager.registerDefaultNetworkCallback(callback)
                } else {
                    manager.registerNetworkCallback(
                        NetworkRequest.Builder()
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            .build(),
                        callback,
                    )
                }
            }
            networkAvailable = hasUsableNetwork(context)
        }

        onDispose {
            mainHandler.removeCallbacksAndMessages(null)
            if (manager != null) runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }

    DisposableEffect(player, request, playerSession, playerDiagnostics) {
        val attachedRecoveryDispatchOwner = recoveryDispatchOwner
        val attachedRecoveryCommandHandler = ::applyRecoveryCommand
        playerDiagnostics.attach(player)
        playerSession.attach(
            player = player,
            candidateIndex = candidateIndex,
            outputMode = audioOutputMode,
            callbacks = PlayerSessionCallbacks(
                isNetworkAvailable = { localPlayback || hasUsableNetwork(context) },
                isRecoveryCommandCurrent = { command ->
                    ownsRecoveryCommand(
                        command = command,
                        attachedOwner = attachedRecoveryDispatchOwner,
                        currentOwner = latestRecoveryDispatchOwner,
                    )
                },
                onRecoveryCommand = attachedRecoveryCommandHandler,
                onFinalError = { failureClass ->
                    player.stop()
                    suspendedFinalError = null
                    finalFailureClass = failureClass
                    offlineFailure = false
                    buffering = false
                    controlsVisible = true
                    finalError = when {
                        failureClass == RecoveryFailureClass.AUDIO && request.isLive -> {
                            "تعذر تشغيل صوت هذه القناة. اعد المحاولة او افتح قناة اخرى"
                        }
                        failureClass == RecoveryFailureClass.AUDIO -> {
                            "تعذر تشغيل الصوت لهذا المحتوى. اعد المحاولة."
                        }
                        request.isLive -> {
                            "البث غير متاح حاليا ، جرب اعادة المحاولة او اختر قناة اخرى"
                        }
                        else -> {
                            "تعذر تشغيل المحتوى. اعد المحاولة او اختر مصدرا اخر عند توفره."
                        }
                    }
                },
                onOffline = { positionMs ->
                    pendingSeekMs = positionMs
                    suspendedFinalError = null
                    finalFailureClass = null
                    buffering = false
                    controlsVisible = true
                    offlineFailure = true
                    finalError = PLAYER_OFFLINE_MESSAGE
                },
            ),
        )
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
                if (playbackState == Player.STATE_READY && suspendedFinalError == null) {
                    finalError = null
                    finalFailureClass = null
                    offlineFailure = false
                }
                if (
                    playbackState == Player.STATE_ENDED &&
                    !request.isLive &&
                    playbackSettings.autoplayNextEpisode &&
                    onPlayNextEpisode != null &&
                    nextEpisodeTitle != null
                ) {
                    nextCountdown = NEXT_EPISODE_SECONDS
                    controlsVisible = false
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoHeight = videoSize.height
                cacheVerifiedMovieCardMetadata(
                    metadataStore = contentMetadataStore,
                    metadataOwner = contentMetadataOwner,
                    request = request,
                    videoHeight = videoSize.height,
                )
            }

            override fun onTracksChanged(tracks: Tracks) {
                audioTracks = extractTrackOptions(tracks, C.TRACK_TYPE_AUDIO)
                subtitleTracks = extractTrackOptions(tracks, C.TRACK_TYPE_TEXT)
                videoTracks = extractTrackOptions(tracks, C.TRACK_TYPE_VIDEO)
                hasActiveAudio = player.audioFormat != null || audioTracks.any { it.selected }
                hasActiveSubtitles = subtitleTracks.isNotEmpty()
                audioLanguageLabel = if (hasActiveAudio) {
                    player.audioFormat?.let(::mediaTrackLanguage)
                        ?: selectedTrackLabel(audioTracks, "")
                } else {
                    ""
                }
                subtitleLanguageLabel = if (hasActiveSubtitles) {
                    selectedTrackLabel(subtitleTracks, "")
                } else {
                    ""
                }
            }

            override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                if (cueGroup.cues.isNotEmpty()) hasActiveSubtitles = true
                if (subtitleLanguageLabel.isBlank()) {
                    subtitleLanguageLabel = inferSubtitleLanguageFromText(
                        cueGroup.cues.joinToString(" ") { cue -> cue.text?.toString().orEmpty() },
                    ).orEmpty()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            if (!request.isLive) {
                onProgress(request, player.currentPosition.coerceAtLeast(0L), player.duration.coerceAtLeast(0L))
            }
            player.removeListener(listener)
            playerDiagnostics.detach(player)
            playerSession.detach(player)
            player.release()
        }
    }

    LaunchedEffect(request, playerSession, candidateIndex, retryNonce, player, audioOutputMode) {
        val delayBeforePrepareMs = recoveryDelayMs
        recoveryDelayMs = 0L
        if (delayBeforePrepareMs > 0L) delay(delayBeforePrepareMs)
        snapshotFlow { appForeground && (localPlayback || networkAvailable) }
            .first { readyToPrepare -> readyToPrepare }

        playerDiagnostics.onPrepare(candidateIndex, audioOutputMode)
        val mediaItem = playerSession.prepareMediaItem(player, candidateIndex)
        if (mediaItem == null) {
            suspendedFinalError = null
            finalFailureClass = RecoveryFailureClass.SOURCE
            finalError = "لا يوجد رابط تشغيل صالح لهذا المحتوى."
            buffering = false
            return@LaunchedEffect
        }
        finalError = null
        finalFailureClass = null
        buffering = true
        audioTracks = emptyList()
        subtitleTracks = emptyList()
        hasActiveAudio = false
        hasActiveSubtitles = false
        audioLanguageLabel = ""
        subtitleLanguageLabel = ""
        player.setMediaItem(mediaItem)

        val replacement = pendingPlayerReplacement?.takeIf { pending ->
            pending.candidateIndex == candidateIndex && pending.outputMode == audioOutputMode
        }
        if (replacement != null) {
            player.trackSelectionParameters = replacement.trackSelectionParameters
            replacement.positionMs?.let(player::seekTo)
            player.setPlaybackSpeed(replacement.speed)
            player.volume = replacement.volume
            player.prepare()
            player.playWhenReady = replacement.playWhenReady
            pendingPlayerReplacement = null
            pendingSeekMs = 0L
            manualSeekTargetMs = null
            return@LaunchedEffect
        }

        val seekTarget = when {
            pendingSeekMs > 0L -> pendingSeekMs
            !resumePromptVisible && playbackSettings.resumePlayback -> request.resumePositionMs
            else -> 0L
        }
        if (seekTarget > 0L) player.seekTo(seekTarget)
        player.prepare()
        player.playWhenReady = !resumePromptVisible
        pendingSeekMs = 0L
        manualSeekTargetMs = null
    }

    LaunchedEffect(
        networkAvailable,
        offlineFailure,
        finalError,
        request.historyKey,
        localPlayback,
        player,
        playerSession,
    ) {
        if (localPlayback) return@LaunchedEffect

        if (!networkAvailable) {
            delay(350L)
            if (hasUsableNetwork(context)) return@LaunchedEffect
            pendingSeekMs = if (request.isLive) {
                0L
            } else {
                maxOf(pendingSeekMs, currentPositionMs, player.currentPosition.coerceAtLeast(0L))
            }
            playerSession.onNetworkUnavailable()
            player.pause()
            suspendedFinalError = null
            finalFailureClass = null
            buffering = false
            controlsVisible = true
            offlineFailure = true
            finalError = PLAYER_OFFLINE_MESSAGE
            return@LaunchedEffect
        }

        if (offlineFailure && finalError == PLAYER_OFFLINE_MESSAGE) {
            val resumePositionMs = if (request.isLive) {
                0L
            } else {
                maxOf(pendingSeekMs, currentPositionMs, player.currentPosition.coerceAtLeast(0L))
            }
            delay(700L)
            if (!hasUsableNetwork(context)) return@LaunchedEffect
            pendingSeekMs = resumePositionMs
            playerSession.onNetworkRestored(player)
        }
    }

    LaunchedEffect(manualSeekTargetMs, request) {
        val target = manualSeekTargetMs ?: return@LaunchedEffect
        if (request.isLive) return@LaunchedEffect
        delay(260L)
        if (manualSeekTargetMs != target) return@LaunchedEffect
        player.seekTo(target)
        repeat(80) {
            delay(100L)
            if (manualSeekTargetMs != target) return@LaunchedEffect
            val actual = player.currentPosition.coerceAtLeast(0L)
            if (kotlin.math.abs(actual - target) <= 1_500L) {
                manualSeekTargetMs = null
                currentPositionMs = actual
                return@LaunchedEffect
            }
        }
    }

    LaunchedEffect(player, request) {
        while (isActive) {
            delay(500L)
            playerSession.observeHealth(
                player = player,
                appForeground = appForeground,
                muted = isMuted,
            )
            if (manualSeekTargetMs == null) {
                currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            }
            val tracks = player.currentTracks
            val detectedAudioTracks = extractTrackOptions(tracks, C.TRACK_TYPE_AUDIO)
            val detectedSubtitleTracks = extractTrackOptions(tracks, C.TRACK_TYPE_TEXT)
            if (detectedAudioTracks != audioTracks) audioTracks = detectedAudioTracks
            if (detectedSubtitleTracks != subtitleTracks) subtitleTracks = detectedSubtitleTracks

            val audioActive = player.audioFormat != null || detectedAudioTracks.any { it.selected }
            hasActiveAudio = audioActive
            if (audioActive) {
                val resolvedAudioLanguage = player.audioFormat?.let(::mediaTrackLanguage)
                    ?: selectedTrackLabel(detectedAudioTracks, "")
                if (resolvedAudioLanguage.isNotBlank()) audioLanguageLabel = resolvedAudioLanguage
            } else {
                audioLanguageLabel = ""
            }

            if (detectedSubtitleTracks.isNotEmpty()) hasActiveSubtitles = true
            if (hasActiveSubtitles && subtitleLanguageLabel.isBlank()) {
                subtitleLanguageLabel = selectedTrackLabel(detectedSubtitleTracks, "")
            }

            durationMs = player.duration.takeIf { it > 0L } ?: 0L
            if (durationMs > 0L && durationMs != cachedMovieDurationMs) {
                cacheVerifiedMovieCardMetadata(
                    metadataStore = contentMetadataStore,
                    metadataOwner = contentMetadataOwner,
                    request = request,
                    durationMs = durationMs,
                )
                cachedMovieDurationMs = durationMs
            }
            bufferedPercent = player.bufferedPercentage.coerceIn(0, 100)
        }
    }

    LaunchedEffect(player, request) {
        if (request.isLive) return@LaunchedEffect
        while (isActive) {
            delay(5_000L)
            onProgress(request, player.currentPosition.coerceAtLeast(0L), player.duration.coerceAtLeast(0L))
        }
    }

    LaunchedEffect(
        controlsVisible,
        buffering,
        finalError,
        isPlaying,
        browserVisible,
        activePanel,
        liveMorePanel,
        resumePromptVisible,
        controlsLocked,
        manualSeekTargetMs,
    ) {
        if (
            playbackSettings.autoHideControls &&
            controlsVisible && !browserVisible && activePanel == null && liveMorePanel == null &&
            !resumePromptVisible && !buffering && finalError == null && isPlaying && !controlsLocked &&
            manualSeekTargetMs == null
        ) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
            focusTimelineOnReveal = false
        }
    }

    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(1_000L)
            seekFeedback = null
        }
    }

    LaunchedEffect(nextCountdown, appForeground) {
        if (!shouldAdvancePlayerAutoplayCountdown(appForeground, nextCountdown)) {
            return@LaunchedEffect
        }
        if (nextCountdown > 0) {
            delay(1_000L)
            nextCountdown -= 1
        } else if (nextCountdown == 0) {
            nextCountdown = -1
            saveAndPlayNext()
        }
    }

    LaunchedEffect(
        controlsVisible,
        activePanel,
        browserVisible,
        finalError,
        resumePromptVisible,
        unlockVisible,
        controlsLocked,
        nextCountdown,
        focusTimelineOnReveal,
        request.historyKey,
    ) {
        val target = when {
            finalError != null -> null
            browserVisible || activePanel != null -> null
            resumePromptVisible -> resumeFocus
            nextCountdown >= 0 -> nextEpisodePlayFocus
            unlockVisible -> unlockFocus
            controlsLocked || !controlsVisible -> playerFocus
            focusTimelineOnReveal && !request.isLive -> playerFocus
            else -> primaryFocus
        }
        if (target != null) {
            withFrameNanos { }
            runCatching { target.requestFocus() }
        }
    }

    LaunchedEffect(moreFocusRestoreTick) {
        if (moreFocusRestoreTick == 0) return@LaunchedEffect
        withFrameNanos { }
        runCatching { moreTriggerFocus.requestFocus() }
    }

    val errorModalInputActive = finalError != null || suspendedFinalError != null
    val latestOnErrorModalActiveChanged by rememberUpdatedState(onErrorModalActiveChanged)
    DisposableEffect(errorModalInputActive) {
        latestOnErrorModalActiveChanged(errorModalInputActive)
        onDispose { latestOnErrorModalActiveChanged(false) }
    }

    val latestOnBrowserVisibilityChanged by rememberUpdatedState(onBrowserVisibilityChanged)
    DisposableEffect(browserVisible) {
        latestOnBrowserVisibilityChanged(browserVisible)
        onDispose { latestOnBrowserVisibilityChanged(false) }
    }

    val panelInputActive = playerChildPanelInputActive(activePanel != null, liveMorePanel != null)
    val latestOnPanelActiveChanged by rememberUpdatedState(onPanelActiveChanged)
    DisposableEffect(panelInputActive) {
        latestOnPanelActiveChanged(panelInputActive)
        onDispose { latestOnPanelActiveChanged(false) }
    }

    val interactionModifier = Modifier
        .pointerInput(request, finalError) {
            detectTapGestures(onTap = {
                if (finalError != null) return@detectTapGestures
                when {
                    liveMorePanel != null -> closeLiveMorePanelToTrigger()
                    controlsLocked -> { unlockVisible = true; controlsVisible = true }
                    else -> controlsVisible = !controlsVisible
                }
            })
        }
        .pointerInput(request.isLive, finalError) {
            if (request.isLive && finalError == null) {
                var verticalDrag = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { change, amount -> change.consume(); verticalDrag += amount },
                    onDragEnd = {
                        when {
                            verticalDrag <= -55f -> latestSwitchRelative(1)
                            verticalDrag >= 55f -> latestSwitchRelative(-1)
                        }
                        verticalDrag = 0f
                    },
                    onDragCancel = { verticalDrag = 0f },
                )
            }
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocus)
            .onFocusChanged { surfaceFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                if (finalError != null) {
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    return@onPreviewKeyEvent when (
                        playerErrorModalInputDisposition(keyCode.toPlayerErrorModalInput())
                    ) {
                        PlayerErrorModalInputDisposition.HANDLE_BACK -> {
                            handleBackAction()
                            true
                        }
                        PlayerErrorModalInputDisposition.CONSUME -> true
                        PlayerErrorModalInputDisposition.PASS_TO_MODAL_ACTION,
                        PlayerErrorModalInputDisposition.PASS_TO_SYSTEM,
                        -> false
                    }
                }
                if (
                    event.type != KeyEventType.KeyDown || browserVisible || activePanel != null ||
                    liveMorePanel != null || resumePromptVisible || unlockVisible || nextCountdown >= 0
                ) {
                    return@onPreviewKeyEvent false
                }
                if (keyCode == AndroidKeyEvent.KEYCODE_BACK || keyCode == AndroidKeyEvent.KEYCODE_ESCAPE) {
                    handleBackAction()
                    return@onPreviewKeyEvent true
                }
                if (controlsLocked) {
                    when (keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        AndroidKeyEvent.KEYCODE_DPAD_UP,
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                        -> {
                            unlockVisible = true
                            controlsVisible = true
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (request.isLive) {
                    if (tvRemoteInput && !controlsVisible && surfaceFocused) {
                        when (keyCode) {
                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                            AndroidKeyEvent.KEYCODE_ENTER,
                            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                            -> {
                                controlsVisible = false
                                activePanel = null
                                browserOrigin = LiveChannelBrowserOrigin.NORMAL_LIVE
                                browserVisible = true
                                return@onPreviewKeyEvent true
                            }
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                            -> {
                                activePanel = null
                                revealControls()
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                    when (keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_UP,
                        AndroidKeyEvent.KEYCODE_CHANNEL_UP,
                        AndroidKeyEvent.KEYCODE_MEDIA_NEXT,
                        -> {
                            controlsVisible = false
                            activePanel = null
                            switchRelative(1)
                            return@onPreviewKeyEvent true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                        AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        -> {
                            controlsVisible = false
                            activePanel = null
                            switchRelative(-1)
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                when (keyCode) {
                    AndroidKeyEvent.KEYCODE_BACK,
                    AndroidKeyEvent.KEYCODE_ESCAPE,
                    -> false
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (player.isPlaying) player.pause() else player.play()
                        revealControls()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> if (!request.isLive && surfaceFocused) {
                        focusTimelineOnReveal = true
                        controlsVisible = true
                        seekBy(if (tvRemoteInput) seekStepMs else -seekStepMs)
                        true
                    } else false
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> if (!request.isLive && surfaceFocused) {
                        focusTimelineOnReveal = true
                        controlsVisible = true
                        seekBy(if (tvRemoteInput) -seekStepMs else seekStepMs)
                        true
                    } else false
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> if (!request.isLive && surfaceFocused) {
                        seekBy(-seekStepMs); true
                    } else false
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> if (!request.isLive && surfaceFocused) {
                        seekBy(seekStepMs); true
                    } else false
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                    -> if (!controlsVisible) { revealControls(); true } else false
                    else -> if (!controlsVisible) { revealControls(); true } else false
                }
            }
            .then(interactionModifier),
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    controllerAutoShow = false
                    layoutDirection = View.LAYOUT_DIRECTION_LTR
                    resizeMode = resizeModes[resizeModeIndex]
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    keepScreenOn = playbackSettings.keepScreenOn
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    this.player = player
                }
            },
            update = { view ->
                view.player = player
                view.useController = false
                view.resizeMode = resizeModes[resizeModeIndex]
                view.keepScreenOn = playbackSettings.keepScreenOn
                view.subtitleView?.apply {
                    val sizes = floatArrayOf(16f, 21f, 27f)
                    setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sizes[subtitleSizeIndex])
                    setBottomPaddingFraction(if (subtitleRaised) .20f else .08f)
                    setStyle(
                        CaptionStyleCompat(
                            AndroidColor.WHITE,
                            AndroidColor.TRANSPARENT,
                            AndroidColor.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            AndroidColor.BLACK,
                            null,
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (controlsVisible && nextCountdown < 0 && finalError == null && !browserVisible && activePanel == null && !controlsLocked) {
            PlayerTopBar(
                title = playerDisplayTitle,
                isLive = request.isLive,
                quality = qualityLabel(videoHeight),
                speed = playbackSpeed,
                channel = currentChannel.takeIf { request.isLive },
                onBack = ::saveAndBack,
            )
        }

        if (controlsVisible && nextCountdown < 0 && finalError == null && !browserVisible && activePanel == null && !controlsLocked) {
            if (request.isLive) {
                BoxWithConstraints(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val density = LocalDensity.current
                    val measuredStripDp = with(density) { liveControlsStripHeightPx.toDp() }
                    val fallbackStripDp = livePlayerReservedStripHeightDp(
                        transportContainerDp = liveControlMetrics.transportContainerDp,
                        captionSizeSp = liveControlMetrics.captionSizeSp,
                        outerTopPaddingDp = liveControlsLayout.outerTopPaddingDp,
                        outerBottomPaddingDp = liveControlsLayout.outerBottomPaddingDp,
                    ).dp
                    val reservedStripDp = if (measuredStripDp > 0.dp) measuredStripDp else fallbackStripDp
                    val liveMorePanelMaxHeight = (maxHeight - reservedStripDp - 18.dp).coerceAtLeast(160.dp)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                    liveMorePanel?.let { panelView ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
                            LiveMorePanel(
                                view = panelView,
                                isMuted = isMuted,
                                sourceCount = request.candidates.size,
                                candidateIndex = candidateIndex,
                                resizeModeIndex = resizeModeIndex,
                                menuFocusRow = liveMoreMenuFocusRow,
                                onClose = ::closeLiveMorePanelToTrigger,
                                onBackToMenu = ::handleLiveMoreBack,
                                onToggleMute = {
                                    val muted = !isMuted
                                    isMuted = muted
                                    player.volume = if (muted) 0f else 1f
                                },
                                onReload = {
                                    closeLiveMorePanelForSelection()
                                    retryManually(candidateIndex)
                                },
                                onOpenSource = {
                                    liveMoreMenuFocusRow = PlayerLiveMoreRow.SOURCE
                                    liveMorePanel = PlayerLiveMorePanelView.SOURCE
                                },
                                onOpenResize = {
                                    liveMoreMenuFocusRow = PlayerLiveMoreRow.RESIZE
                                    liveMorePanel = PlayerLiveMorePanelView.RESIZE
                                },
                                onSelectSource = { index ->
                                    closeLiveMorePanelForSelection()
                                    suspendedFinalError = null
                                    retryManually(index)
                                },
                                onSelectResize = { index ->
                                    resizeModeIndex = index
                                    closeLiveMorePanelForSelection()
                                },
                                metrics = liveControlMetrics,
                                maxHeight = liveMorePanelMaxHeight,
                                modifier = Modifier.padding(
                                    end = liveControlsLayout.outerHorizontalPaddingDp.dp,
                                    bottom = 10.dp,
                                ),
                            )
                        }
                    }
                    LivePlayerControls(
                        isPlaying = isPlaying,
                        favorite = liveFavoriteControl.favorite,
                        favoriteEnabled = liveFavoriteControl.enabled,
                        lastChannelEnabled = onLastChannel != null,
                        moreOpen = liveMorePanel != null,
                        onMore = ::openLiveMorePanel,
                        onLastChannel = { onLastChannel?.invoke() },
                        onPrevious = { switchRelative(-1) },
                        onNext = { switchRelative(1) },
                        onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                        onFavorite = { liveFavoriteChannel?.let(onToggleFavorite) },
                        onChannels = {
                            controlsVisible = false
                            activePanel = null
                            liveMorePanel = null
                            browserOrigin = LiveChannelBrowserOrigin.NORMAL_LIVE
                            browserVisible = true
                        },
                        primaryFocus = primaryFocus,
                        moreTriggerFocus = moreTriggerFocus,
                        layoutMetrics = liveControlsLayout,
                        metrics = liveControlMetrics,
                        onMeasuredHeightPx = { measured ->
                            if (measured != liveControlsStripHeightPx) liveControlsStripHeightPx = measured
                        },
                    )
                    }
                }
            } else {
                ModernVodControls(
                    isPlaying = isPlaying,
                    positionMs = currentPositionMs,
                    durationMs = durationMs,
                    bufferedPercent = bufferedPercent,
                    quality = qualityLabel(videoHeight),
                    speed = playbackSpeed,
                    hasMultipleQualities = videoTracks.distinctBy { it.label }.size > 1,
                    hasMultipleServers = request.candidates.size > 1,
                    onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                    onRewind = { seekBy(-seekStepMs) },
                    onForward = { seekBy(seekStepMs) },
                    onSeekTo = ::seekToPosition,
                    onSeekingChanged = { focused -> seekBarFocused = focused },
                    onSpeed = { activePanel = PlayerPanel.SPEED },
                    onResize = { activePanel = PlayerPanel.RESIZE },
                    onQuality = { activePanel = PlayerPanel.QUALITY },
                    onServers = { activePanel = PlayerPanel.SERVERS },
                    onLock = { controlsLocked = true; controlsVisible = false },
                    primaryFocus = primaryFocus,
                    seekBarFocusRequester = seekBarFocus,
                    remoteSeekActive = focusTimelineOnReveal,
                    seekStepMs = seekStepMs,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        if (buffering && finalError == null && !resumePromptVisible) {
            LoadingRing(
                label = if (request.isLive) "جاري تشغيل القناة…" else "جاري تجهيز المشاهدة…",
                modifier = Modifier.align(Alignment.Center),
            )
        }

        seekFeedback?.let { feedback ->
            Text(
                feedback,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = .78f))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }

        if (resumePromptVisible) {
            ResumePrompt(
                title = playerDisplayTitle,
                positionMs = request.resumePositionMs,
                onResume = {
                    player.seekTo(request.resumePositionMs)
                    currentPositionMs = request.resumePositionMs
                    resumePromptVisible = false
                    controlsVisible = true
                    player.play()
                },
                onRestart = {
                    player.seekTo(0L)
                    currentPositionMs = 0L
                    resumePromptVisible = false
                    controlsVisible = true
                    player.play()
                },
                focusRequester = resumeFocus,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (nextCountdown >= 0 && nextEpisodeTitle != null && onPlayNextEpisode != null) {
            NextEpisodePrompt(
                title = nextEpisodeTitle,
                seconds = nextCountdown,
                playFocusRequester = nextEpisodePlayFocus,
                cancelFocusRequester = nextEpisodeCancelFocus,
                onPlayNow = { nextCountdown = -1; saveAndPlayNext() },
                onCancel = { nextCountdown = -1 },
                modifier = Modifier.align(Alignment.BottomEnd).padding(32.dp),
            )
        }

        if (unlockVisible) {
            UnlockPrompt(
                onUnlock = {
                    controlsLocked = false
                    unlockVisible = false
                    controlsVisible = true
                },
                onKeepLocked = {
                    unlockVisible = false
                    controlsVisible = false
                },
                focusRequester = unlockFocus,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (finalError != null) {
            PlayerErrorPanel(
                title = if (request.isLive) "تعذر تشغيل القناة" else null,
                message = finalError!!,
                canChooseChannel = request.isLive && liveCatalog?.items?.isNotEmpty() == true,
                canChooseServer = canOfferPlayerErrorSourcePicker(
                    failureClass = finalFailureClass,
                    candidateCount = request.candidates.size,
                ),
                onRetry = { retryManually() },
                onChooseChannel = {
                    suspendFinalErrorForModal()
                    controlsVisible = false
                    browserOrigin = LiveChannelBrowserOrigin.ERROR_RECOVERY
                    browserVisible = true
                },
                onChooseServer = {
                    suspendFinalErrorForModal()
                    activePanel = PlayerPanel.SERVERS
                },
                onBack = ::saveAndBack,
                retryFocusRequester = errorRetryFocus,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (browserVisible && request.isLive) {
            LiveChannelBrowser(
                catalog = liveCatalog,
                currentStreamId = request.streamId,
                origin = browserOrigin,
                isFavorite = isFavorite,
                favoriteKeys = favoriteKeys,
                onToggleFavorite = { channel ->
                    val wasFavorite = isFavorite(channel)
                    onToggleFavorite(channel)
                    val isNowFavorite = isFavorite(channel)
                    if (isNowFavorite != wasFavorite) {
                        Toast.makeText(
                            context,
                            if (isNowFavorite) "تمت اضافة القناة الى المفضلة" else "تمت ازالة القناة من المفضلة",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onSelectChannel = { channel ->
                    suspendedFinalError = null
                    browserVisible = false
                    onSelectLiveChannel(channel)
                },
                onClose = ::closeBrowserAndRestoreError,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        activePanel?.let { panel ->
            when (panel) {
                PlayerPanel.AUDIO -> TrackSelectionPanel(
                    title = "مسارات الصوت",
                    emptyMessage = "لا توجد مسارات صوت اضافية في هذا المحتوى",
                    options = audioTracks,
                    showOff = false,
                    onSelect = { applyTrack(C.TRACK_TYPE_AUDIO, it) },
                    onClose = { activePanel = null },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                PlayerPanel.SUBTITLES -> SubtitleSelectionPanel(
                    options = subtitleTracks,
                    subtitleSizeIndex = subtitleSizeIndex,
                    raised = subtitleRaised,
                    onSelect = { applyTrack(C.TRACK_TYPE_TEXT, it) },
                    onDisable = { applyTrack(C.TRACK_TYPE_TEXT, null) },
                    onCycleSize = { subtitleSizeIndex = (subtitleSizeIndex + 1) % 3 },
                    onTogglePosition = { subtitleRaised = !subtitleRaised },
                    onClose = { activePanel = null },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                PlayerPanel.SPEED -> SimpleOptionsPanel(
                    title = "سرعة التشغيل",
                    options = listOf(.75f, 1f, 1.25f, 1.5f, 2f).map { speed ->
                        speedLabel(speed) to { playbackSpeed = speed; player.setPlaybackSpeed(speed); activePanel = null }
                    },
                    selectedLabel = speedLabel(playbackSpeed),
                    onClose = { activePanel = null },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                PlayerPanel.RESIZE -> SimpleOptionsPanel(
                    title = "حجم الصورة",
                    options = listOf("ملائم", "تكبير", "ملء الشاشة").mapIndexed { index, label ->
                        label to { resizeModeIndex = index; activePanel = null }
                    },
                    selectedLabel = resizeLabel(resizeModeIndex),
                    onClose = { activePanel = null },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                PlayerPanel.QUALITY -> QualitySelectionPanel(
                    options = videoTracks,
                    onAuto = {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                            .build()
                        activePanel = null
                    },
                    onSelect = { applyTrack(C.TRACK_TYPE_VIDEO, it) },
                    onClose = { activePanel = null },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                PlayerPanel.SERVERS -> if (!request.isLive) {
                    SimpleOptionsPanel(
                        title = "اختيار المصدر",
                        options = request.candidates.mapIndexed { index, _ ->
                            "المصدر ${index + 1}" to {
                                activePanel = null
                                suspendedFinalError = null
                                retryManually(index)
                            }
                        },
                        selectedLabel = "المصدر ${candidateIndex + 1}",
                        onClose = ::closeActivePanelAndRestoreError,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                }
            }
        }

        if (request.isLive && activePanel == PlayerPanel.SERVERS) {
            BoxWithConstraints(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                val liveErrorSourcePanelMaxHeight =
                    (maxHeight - liveControlsLayout.outerBottomPaddingDp.dp - 16.dp).coerceAtLeast(160.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(
                            end = liveControlsLayout.outerHorizontalPaddingDp.dp,
                            bottom = liveControlsLayout.outerBottomPaddingDp.dp,
                        ),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    LiveMorePanel(
                        view = PlayerLiveMorePanelView.SOURCE,
                        isMuted = isMuted,
                        sourceCount = request.candidates.size,
                        candidateIndex = candidateIndex,
                        resizeModeIndex = resizeModeIndex,
                        menuFocusRow = PlayerLiveMoreRow.SOURCE,
                        onClose = ::closeActivePanelAndRestoreError,
                        onBackToMenu = ::closeActivePanelAndRestoreError,
                        onToggleMute = {},
                        onReload = {},
                        onOpenSource = {},
                        onOpenResize = {},
                        onSelectSource = { index ->
                            activePanel = null
                            suspendedFinalError = null
                            retryManually(index)
                        },
                        onSelectResize = {},
                        metrics = liveControlMetrics,
                        maxHeight = liveErrorSourcePanelMaxHeight,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerTopBar(
    title: String,
    isLive: Boolean,
    quality: String,
    speed: Float,
    channel: ContentItem?,
    onBack: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .92f), Color.Transparent)))
            .statusBarsPadding()
            .padding(
                start = if (adaptiveUi.isTelevision) 36.dp else 24.dp,
                end = if (adaptiveUi.isTelevision) 36.dp else 24.dp,
                top = if (adaptiveUi.isTelevision) 24.dp else 10.dp,
                bottom = 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FocusButton("رجوع", onBack, primary = false, outlined = true, compact = true)
        if (isLive && channel != null) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF7F5EF))
                    .border(1.dp, colors.gold.copy(alpha = .45f), RoundedCornerShape(10.dp))
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                ChannelLogo(channel, Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isLive) "● بث مباشر" else "HULK SA",
                    color = if (isLive) Color(0xFFFF4E55) else colors.goldBright,
                    fontSize = 11.sp,
                    fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal,
                )
                Text(quality, color = colors.textMuted, fontSize = 11.sp)
                if (!isLive && speed != 1f) Text(speedLabel(speed), color = colors.textMuted, fontSize = 11.sp)
            }
        }
        BrandBadge(Modifier.size(48.dp))
    }
}

@Composable
private fun ModernVodControls(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedPercent: Int,
    quality: String,
    speed: Float,
    hasMultipleQualities: Boolean,
    hasMultipleServers: Boolean,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekingChanged: (Boolean) -> Unit,
    onSpeed: () -> Unit,
    onResize: () -> Unit,
    onQuality: () -> Unit,
    onServers: () -> Unit,
    onLock: () -> Unit,
    primaryFocus: FocusRequester,
    seekBarFocusRequester: FocusRequester,
    remoteSeekActive: Boolean,
    seekStepMs: Long,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(progress, label = "playerProgress")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .97f))))
            .navigationBarsPadding()
            .padding(
                start = if (adaptiveUi.isTelevision) 34.dp else 24.dp,
                end = if (adaptiveUi.isTelevision) 34.dp else 24.dp,
                top = 12.dp,
                bottom = if (adaptiveUi.isTelevision) 30.dp else 20.dp,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(positionMs), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${quality}  •  ${speedLabel(speed)}", color = colors.textMuted, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("-${formatTime((durationMs - positionMs).coerceAtLeast(0L))}", color = colors.textMuted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        SeekableProgressBar(
            positionMs = positionMs,
            durationMs = durationMs,
            buffered = bufferedPercent / 100f,
            onSeekTo = onSeekTo,
            onSeekingChanged = onSeekingChanged,
            focusRequester = seekBarFocusRequester,
            remoteActive = remoteSeekActive,
            seekStepMs = seekStepMs,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            item { FocusButton("-${seekStepMs / 1_000L} ث", onRewind, primary = false, compact = true) }
            item { FocusButton(if (isPlaying) "ايقاف مؤقت" else "تشغيل", onPlayPause, modifier = Modifier.focusRequester(primaryFocus), compact = true) }
            item { FocusButton("+${seekStepMs / 1_000L} ث", onForward, primary = false, compact = true) }
            item { FocusButton("السرعة ${speedLabel(speed)}", onSpeed, primary = false, compact = true) }
            item { FocusButton("حجم الصورة", onResize, primary = false, compact = true) }
            if (hasMultipleQualities) item { FocusButton("الجودة", onQuality, primary = false, compact = true) }
            if (hasMultipleServers) item { FocusButton("المصدر", onServers, primary = false, compact = true) }
            item { FocusButton("قفل التحكم", onLock, primary = false, compact = true) }
        }
    }
}

internal data class LiveControlsLayoutMetrics(
    val outerHorizontalPaddingDp: Float,
    val outerTopPaddingDp: Float,
    val outerBottomPaddingDp: Float,
    val rowHorizontalContentPaddingDp: Float,
    val rowVerticalContentPaddingDp: Float,
)

/**
 * Adaptive layout policy for the Live controls overlay.
 *
 * On television/remote layouts the overlay adopts the same premium safe-window metrics as the
 * other Player TV overlays and reserves focus-edge room so the scaled, bordered FocusButton is not
 * clipped by the LazyRow viewport at its first or last item. Touch layouts keep the existing
 * compact phone geometry.
 */
internal fun liveControlsLayoutMetrics(
    screenWidthDp: Int,
    screenHeightDp: Int,
    remoteLayout: Boolean,
): LiveControlsLayoutMetrics {
    if (!remoteLayout) {
        return LiveControlsLayoutMetrics(
            outerHorizontalPaddingDp = 18f,
            outerTopPaddingDp = 13f,
            outerBottomPaddingDp = 18f,
            rowHorizontalContentPaddingDp = 0f,
            rowVerticalContentPaddingDp = 2f,
        )
    }

    val width = screenWidthDp.coerceAtLeast(1)
    val height = screenHeightDp.coerceAtLeast(1)
    val overlay = playerTvPremiumOverlayMetrics(width, height)
    val premium = tvPremiumWindowPolicy(width, height)
    val focusGrowth = (premium.focusScale - 1f) / 2f
    val horizontalFocusRoom =
        premium.focusBorderWidthDp + focusGrowth * LIVE_CONTROL_FOCUS_REFERENCE_WIDTH_DP
    val verticalFocusRoom =
        premium.focusBorderWidthDp + focusGrowth * LIVE_CONTROL_FOCUS_REFERENCE_HEIGHT_DP

    return LiveControlsLayoutMetrics(
        outerHorizontalPaddingDp = overlay.safeHorizontalPaddingDp.toFloat(),
        outerTopPaddingDp = 18f,
        outerBottomPaddingDp = overlay.safeBottomPaddingDp.toFloat(),
        rowHorizontalContentPaddingDp = horizontalFocusRoom,
        rowVerticalContentPaddingDp = maxOf(verticalFocusRoom, 2f),
    )
}

// Compact Live control footprint used only to size focus-edge room for the row's scaled focus ring.
private const val LIVE_CONTROL_FOCUS_REFERENCE_WIDTH_DP = 150f
private const val LIVE_CONTROL_FOCUS_REFERENCE_HEIGHT_DP = 40f

@Composable
private fun LivePlayerControls(
    isPlaying: Boolean,
    favorite: Boolean,
    favoriteEnabled: Boolean,
    lastChannelEnabled: Boolean,
    moreOpen: Boolean,
    onMore: () -> Unit,
    onLastChannel: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    onFavorite: () -> Unit,
    onChannels: () -> Unit,
    primaryFocus: FocusRequester,
    moreTriggerFocus: FocusRequester,
    layoutMetrics: LiveControlsLayoutMetrics,
    metrics: LivePlayerControlsMetrics,
    modifier: Modifier = Modifier,
    onMeasuredHeightPx: (Int) -> Unit = {},
) {
    val colors = LocalHulkColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { onMeasuredHeightPx(it.height) }
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = .42f),
                        Color.Black.copy(alpha = .78f),
                    ),
                ),
            )
            .navigationBarsPadding()
            .padding(
                start = layoutMetrics.outerHorizontalPaddingDp.dp,
                end = layoutMetrics.outerHorizontalPaddingDp.dp,
                top = layoutMetrics.outerTopPaddingDp.dp,
                bottom = layoutMetrics.outerBottomPaddingDp.dp,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF00C0D0A))
                .border(1.dp, colors.gold.copy(alpha = .55f), RoundedCornerShape(18.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            if (metrics.approvedSingleRow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        LiveControlUtility(
                            icon = Icons.AutoMirrored.Rounded.List,
                            caption = "القنوات",
                            onClick = onChannels,
                            enabled = true,
                            iconSizeDp = metrics.utilityIconDp,
                            captionSizeSp = metrics.captionSizeSp,
                        )
                        LiveControlSeparator(heightDp = metrics.transportContainerDp)
                        LiveControlUtility(
                            icon = if (favorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                            caption = "المفضلة",
                            onClick = onFavorite,
                            enabled = favoriteEnabled,
                            selected = favorite,
                            iconSizeDp = metrics.utilityIconDp,
                            captionSizeSp = metrics.captionSizeSp,
                        )
                        LiveControlSeparator(heightDp = metrics.transportContainerDp)
                        LiveControlUtility(
                            icon = Icons.Rounded.SkipPrevious,
                            caption = "القناة السابقة",
                            onClick = onPrevious,
                            enabled = true,
                            iconSizeDp = metrics.transportIconDp,
                            captionSizeSp = metrics.captionSizeSp,
                        )
                        LiveControlSeparator(heightDp = metrics.transportContainerDp)
                    }
                    LiveControlUtility(
                        icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        caption = if (isPlaying) "ايقاف مؤقت" else "تشغيل",
                        onClick = onPlayPause,
                        enabled = true,
                        iconSizeDp = metrics.transportIconDp,
                        captionSizeSp = metrics.captionSizeSp,
                        emphasis = true,
                        focusRequester = primaryFocus,
                    )
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        LiveControlSeparator(heightDp = metrics.transportContainerDp)
                        LiveControlUtility(
                            icon = Icons.Rounded.SkipNext,
                            caption = "القناة التالية",
                            onClick = onNext,
                            enabled = true,
                            iconSizeDp = metrics.transportIconDp,
                            captionSizeSp = metrics.captionSizeSp,
                        )
                        LiveControlSeparator(heightDp = metrics.transportContainerDp)
                        LiveControlUtility(
                            icon = Icons.Rounded.History,
                            caption = "اخر قناة",
                            onClick = onLastChannel,
                            enabled = lastChannelEnabled,
                            iconSizeDp = metrics.utilityIconDp,
                            captionSizeSp = metrics.captionSizeSp,
                        )
                        LiveControlSeparator(heightDp = metrics.transportContainerDp)
                        LiveControlUtility(
                            icon = Icons.Rounded.MoreHoriz,
                            caption = "المزيد",
                            onClick = onMore,
                            enabled = true,
                            selected = moreOpen,
                            iconSizeDp = metrics.utilityIconDp,
                            captionSizeSp = metrics.captionSizeSp,
                            focusRequester = moreTriggerFocus,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(metrics.itemSpacingDp.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        LiveControlUtility(
                            icon = Icons.Rounded.SkipPrevious,
                            caption = "القناة السابقة",
                            onClick = onPrevious,
                            enabled = true,
                            iconSizeDp = metrics.transportIconDp,
                            captionSizeSp = metrics.captionSizeSp,
                        )
                        LiveControlUtility(
                            icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            caption = if (isPlaying) "ايقاف مؤقت" else "تشغيل",
                            onClick = onPlayPause,
                            enabled = true,
                            iconSizeDp = metrics.transportIconDp,
                            captionSizeSp = metrics.captionSizeSp,
                            emphasis = true,
                            focusRequester = primaryFocus,
                        )
                        LiveControlUtility(
                            icon = Icons.Rounded.SkipNext,
                            caption = "القناة التالية",
                            onClick = onNext,
                            enabled = true,
                            iconSizeDp = metrics.transportIconDp,
                            captionSizeSp = metrics.captionSizeSp,
                        )
                    }
                    Spacer(Modifier.height(metrics.itemSpacingDp.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = .08f)),
                    )
                    Spacer(Modifier.height(metrics.itemSpacingDp.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        LiveControlUtility(
                            icon = Icons.AutoMirrored.Rounded.List,
                            caption = "القنوات",
                            onClick = onChannels,
                            enabled = true,
                            iconSizeDp = metrics.utilityIconDp,
                            captionSizeSp = metrics.captionSizeSp,
                        )
                        LiveControlUtility(
                            icon = if (favorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                            caption = "المفضلة",
                            onClick = onFavorite,
                            enabled = favoriteEnabled,
                            selected = favorite,
                            iconSizeDp = metrics.utilityIconDp,
                            captionSizeSp = metrics.captionSizeSp,
                        )
                        LiveControlUtility(
                            icon = Icons.Rounded.History,
                            caption = "اخر قناة",
                            onClick = onLastChannel,
                            enabled = lastChannelEnabled,
                            iconSizeDp = metrics.utilityIconDp,
                            captionSizeSp = metrics.captionSizeSp,
                        )
                        LiveControlUtility(
                            icon = Icons.Rounded.MoreHoriz,
                            caption = "المزيد",
                            onClick = onMore,
                            enabled = true,
                            selected = moreOpen,
                            iconSizeDp = metrics.utilityIconDp,
                            captionSizeSp = metrics.captionSizeSp,
                            focusRequester = moreTriggerFocus,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveControlSeparator(heightDp: Int) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(heightDp.dp)
            .background(Color.White.copy(alpha = .08f)),
    )
}

@Composable
private fun LiveControlUtility(
    icon: ImageVector,
    caption: String,
    onClick: () -> Unit,
    enabled: Boolean,
    iconSizeDp: Int,
    captionSizeSp: Int,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    emphasis: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember { mutableStateOf(false) }
    val showFocused = focused && adaptiveUi.showFocusHighlights
    val shape = RoundedCornerShape(14.dp)
    val tint = when {
        !enabled -> colors.textMuted.copy(alpha = .45f)
        showFocused -> colors.goldBright
        emphasis || selected -> colors.gold
        else -> colors.text
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size((iconSizeDp + 22).dp)
                .clip(shape)
                .background(if (showFocused) colors.gold.copy(alpha = .22f) else Color.Transparent)
                .border(
                    width = if (showFocused) 2.dp else 0.dp,
                    color = if (showFocused) colors.goldBright else Color.Transparent,
                    shape = shape,
                )
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = caption },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSizeDp.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = caption,
            color = tint,
            fontSize = captionSizeSp.sp,
            fontWeight = if (selected || emphasis || showFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LiveMorePanel(
    view: PlayerLiveMorePanelView,
    isMuted: Boolean,
    sourceCount: Int,
    candidateIndex: Int,
    resizeModeIndex: Int,
    menuFocusRow: PlayerLiveMoreRow,
    onClose: () -> Unit,
    onBackToMenu: () -> Unit,
    onToggleMute: () -> Unit,
    onReload: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenResize: () -> Unit,
    onSelectSource: (Int) -> Unit,
    onSelectResize: (Int) -> Unit,
    metrics: LivePlayerControlsMetrics,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(16.dp)
    val muteFocus = remember { FocusRequester() }
    val reloadFocus = remember { FocusRequester() }
    val sourceFocus = remember { FocusRequester() }
    val resizeFocus = remember { FocusRequester() }
    val selectedOptionFocus = remember { FocusRequester() }

    LaunchedEffect(view, menuFocusRow, candidateIndex, resizeModeIndex, sourceCount) {
        val target = when (view) {
            PlayerLiveMorePanelView.MENU -> when (menuFocusRow) {
                PlayerLiveMoreRow.MUTE -> muteFocus
                PlayerLiveMoreRow.RELOAD -> reloadFocus
                PlayerLiveMoreRow.SOURCE -> sourceFocus
                PlayerLiveMoreRow.RESIZE -> resizeFocus
            }
            PlayerLiveMorePanelView.SOURCE,
            PlayerLiveMorePanelView.RESIZE,
            -> selectedOptionFocus
        }
        withFrameNanos { }
        runCatching { target.requestFocus() }
    }

    Column(
        modifier = modifier
            .width(metrics.morePanelWidthDp.dp)
            .clip(shape)
            .background(Color(0xF20A0B08))
            .border(1.dp, colors.gold.copy(alpha = .45f), shape)
            .pointerInput(Unit) { detectTapGestures { } }
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                val isBack = code == AndroidKeyEvent.KEYCODE_BACK || code == AndroidKeyEvent.KEYCODE_ESCAPE
                if (isBack) {
                    if (event.type == KeyEventType.KeyDown) onBackToMenu()
                    true
                } else {
                    false
                }
            }
            .heightIn(max = maxHeight)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when (view) {
                    PlayerLiveMorePanelView.MENU -> "المزيد"
                    PlayerLiveMorePanelView.SOURCE -> "اختيار المصدر"
                    PlayerLiveMorePanelView.RESIZE -> "حجم الصورة"
                },
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LiveMoreHeaderAction(
                caption = if (view == PlayerLiveMorePanelView.MENU) "اغلاق" else "رجوع",
                onClick = if (view == PlayerLiveMorePanelView.MENU) onClose else onBackToMenu,
            )
        }
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            when (view) {
                PlayerLiveMorePanelView.MENU -> {
                    LiveMoreRow(
                        text = if (isMuted) "تشغيل الصوت" else "كتم الصوت",
                        icon = if (isMuted) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                        onClick = onToggleMute,
                        focusRequester = muteFocus,
                    )
                    Spacer(Modifier.height(6.dp))
                    LiveMoreRow(
                        text = "اعادة التحميل",
                        icon = Icons.Rounded.Refresh,
                        onClick = onReload,
                        focusRequester = reloadFocus,
                    )
                    if (canOfferPlayerLiveSourcePicker(sourceCount)) {
                        Spacer(Modifier.height(6.dp))
                        LiveMoreRow(
                            text = "اختيار المصدر",
                            icon = Icons.Rounded.SettingsInputAntenna,
                            value = "المصدر ${candidateIndex + 1}",
                            showChevron = true,
                            onClick = onOpenSource,
                            focusRequester = sourceFocus,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LiveMoreRow(
                        text = "حجم الصورة",
                        icon = Icons.Rounded.AspectRatio,
                        value = livePlayerResizeLabel(resizeModeIndex),
                        showChevron = true,
                        onClick = onOpenResize,
                        focusRequester = resizeFocus,
                    )
                }
                PlayerLiveMorePanelView.SOURCE -> {
                    repeat(sourceCount) { index ->
                        if (index > 0) Spacer(Modifier.height(6.dp))
                        LiveMoreRow(
                            text = "المصدر ${index + 1}",
                            icon = Icons.Rounded.SettingsInputAntenna,
                            selected = index == candidateIndex,
                            onClick = { onSelectSource(index) },
                            focusRequester = if (index == candidateIndex) selectedOptionFocus else null,
                        )
                    }
                }
                PlayerLiveMorePanelView.RESIZE -> {
                    LIVE_PLAYER_RESIZE_LABELS.forEachIndexed { index, label ->
                        if (index > 0) Spacer(Modifier.height(6.dp))
                        LiveMoreRow(
                            text = label,
                            icon = Icons.Rounded.AspectRatio,
                            selected = index == resizeModeIndex,
                            onClick = { onSelectResize(index) },
                            focusRequester = if (index == resizeModeIndex) selectedOptionFocus else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveMoreHeaderAction(
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember { mutableStateOf(false) }
    val showFocused = focused && adaptiveUi.showFocusHighlights
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (showFocused) colors.gold else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = caption }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = caption,
            color = if (showFocused) Color(0xFF14120A) else colors.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun LiveMoreRow(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    selected: Boolean = false,
    showChevron: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember { mutableStateOf(false) }
    val showFocused = focused && adaptiveUi.showFocusHighlights
    val shape = RoundedCornerShape(11.dp)
    val foreground = if (showFocused) Color(0xFF14120A) else colors.text
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    showFocused -> colors.gold
                    selected -> colors.gold.copy(alpha = .16f)
                    else -> Color.Transparent
                },
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = text }
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = text,
            color = foreground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (showFocused) Color(0xFF14120A) else colors.goldBright,
            modifier = Modifier.size(19.dp),
        )
        value?.let {
            Text(
                text = it,
                color = if (showFocused) Color(0xFF14120A) else colors.textMuted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = if (showFocused) Color(0xFF14120A) else colors.goldBright,
                modifier = Modifier.size(18.dp),
            )
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = null,
                tint = if (showFocused) Color(0xFF14120A) else colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SeekableProgressBar(
    positionMs: Long,
    durationMs: Long,
    buffered: Float,
    onSeekTo: (Long) -> Unit,
    onSeekingChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    remoteActive: Boolean,
    seekStepMs: Long,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val tvRemoteInput = adaptiveUi.isTelevision || adaptiveUi.inputMode == HulkInputMode.REMOTE
    var focused by remember { mutableStateOf(false) }
    var previewMs by remember { mutableLongStateOf(positionMs) }
    val active = focused || remoteActive

    DisposableEffect(Unit) {
        onDispose { onSeekingChanged(false) }
    }

    LaunchedEffect(positionMs, durationMs, focused, remoteActive) {
        if (!focused) previewMs = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
    }

    val activePosition = if (focused) previewMs else positionMs
    val progress = if (durationMs > 0L) {
        (activePosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val shape = RoundedCornerShape(20.dp)

    Column(Modifier.fillMaxWidth()) {
        if (active) {
            Text(
                if (tvRemoteInput) {
                    "يسار +${seekStepMs / 1_000L} ث  •  يمين -${seekStepMs / 1_000L} ث"
                } else {
                    "حرك يمين ويسار للتقديم والترجيع"
                },
                color = colors.goldBright,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(6.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (active) 13.dp else 8.dp)
                .clip(shape)
                .background(Color.White.copy(alpha = .18f))
                .border(if (active) 2.dp else 0.dp, if (active) colors.goldBright else Color.Transparent, shape)
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs > 0L && size.width > 0) {
                            val fraction = (1f - offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onSeekTo((durationMs * fraction).toLong())
                        }
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { onSeekingChanged(true) },
                        onDragCancel = { onSeekingChanged(false) },
                        onDragEnd = { onSeekingChanged(false) },
                    ) { change, _ ->
                        change.consume()
                        if (durationMs > 0L && size.width > 0) {
                            val fraction = (1f - change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            val target = (durationMs * fraction).toLong()
                            previewMs = target
                            onSeekTo(target)
                        }
                    }
                }
                .onFocusChanged { state ->
                    focused = state.isFocused
                    onSeekingChanged(state.isFocused)
                    if (state.isFocused) previewMs = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || durationMs <= 0L) return@onPreviewKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                            previewMs = if (tvRemoteInput) {
                                (previewMs + seekStepMs).coerceAtMost(durationMs)
                            } else {
                                (previewMs - seekStepMs).coerceAtLeast(0L)
                            }
                            onSeekTo(previewMs)
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            previewMs = if (tvRemoteInput) {
                                (previewMs - seekStepMs).coerceAtLeast(0L)
                            } else {
                                (previewMs + seekStepMs).coerceAtMost(durationMs)
                            }
                            onSeekTo(previewMs)
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        -> {
                            onSeekTo(previewMs)
                            true
                        }
                        else -> false
                    }
                }
                .focusRequester(focusRequester)
                .focusable(),
        ) {
            Box(Modifier.fillMaxWidth(buffered.coerceIn(0f, 1f)).fillMaxHeight().background(Color.White.copy(alpha = .28f)))
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(colors.goldBright))
        }
    }
}

@Composable
private fun BufferedProgressBar(progress: Float, buffered: Float) {
    val colors = LocalHulkColors.current
    Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f))) {
        Box(Modifier.fillMaxWidth(buffered.coerceIn(0f, 1f)).fillMaxHeight().background(Color.White.copy(alpha = .28f)))
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(colors.goldBright))
    }
}

@Composable
private fun ResumePrompt(
    title: String,
    positionMs: Long,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    Column(
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth(.72f)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xF2141510))
            .border(1.dp, colors.gold.copy(alpha = .45f), RoundedCornerShape(24.dp))
            .padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandBadge(Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Text("متابعة المشاهدة؟", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(title, color = colors.textMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Text("توقفت عند ${formatTime(positionMs)}", color = colors.goldBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FocusButton(
                "متابعة من ${formatTime(positionMs)}",
                onResume,
                modifier = Modifier.focusRequester(focusRequester),
            )
            FocusButton("من البداية", onRestart, primary = false)
        }
    }
}

@Composable
private fun NextEpisodePrompt(
    title: String,
    seconds: Int,
    playFocusRequester: FocusRequester,
    cancelFocusRequester: FocusRequester,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    Column(
        modifier = modifier
            .width(430.dp)
            .focusGroup()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xF2141510))
            .border(1.dp, colors.gold.copy(alpha = .45f), RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        Text("الحلقة التالية خلال $seconds", color = colors.goldBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusButton(
                "تشغيل الان",
                onPlayNow,
                modifier = Modifier.focusRequester(playFocusRequester).focusProperties {
                    left = cancelFocusRequester
                    right = cancelFocusRequester
                    up = FocusRequester.Cancel
                    down = FocusRequester.Cancel
                },
                compact = true,
            )
            FocusButton(
                "الغاء",
                onCancel,
                modifier = Modifier.focusRequester(cancelFocusRequester).focusProperties {
                    left = playFocusRequester
                    right = playFocusRequester
                    up = FocusRequester.Cancel
                    down = FocusRequester.Cancel
                },
                primary = false,
                compact = true,
            )
        }
    }
}

@Composable
private fun UnlockPrompt(
    onUnlock: () -> Unit,
    onKeepLocked: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = .9f))
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ادوات التحكم مقفلة", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FocusButton("فك القفل", onUnlock, modifier = Modifier.focusRequester(focusRequester))
            FocusButton("ابقاء القفل", onKeepLocked, primary = false)
        }
    }
}

@Composable
private fun PlayerErrorPanel(
    title: String?,
    message: String,
    canChooseChannel: Boolean,
    canChooseServer: Boolean,
    onRetry: () -> Unit,
    onChooseChannel: () -> Unit,
    onChooseServer: () -> Unit,
    onBack: () -> Unit,
    retryFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val livePresentation = title != null
    val layoutDirection = LocalLayoutDirection.current
    val channelFocusRequester = remember { FocusRequester() }
    val serverFocusRequester = remember { FocusRequester() }
    val backFocusRequester = remember { FocusRequester() }
    val actions = remember(canChooseChannel, canChooseServer) {
        playerErrorModalActions(canChooseChannel, canChooseServer)
    }
    val requesters = actions.map { action ->
        when (action) {
            PlayerErrorModalAction.RETRY -> retryFocusRequester
            PlayerErrorModalAction.CHOOSE_CHANNEL -> channelFocusRequester
            PlayerErrorModalAction.CHOOSE_SOURCE -> serverFocusRequester
            PlayerErrorModalAction.BACK -> backFocusRequester
        }
    }
    var focusedAction by remember { mutableStateOf<PlayerErrorModalAction?>(null) }
    var placedActions by remember { mutableStateOf(emptySet<PlayerErrorModalAction>()) }
    var focusAcquired by remember { mutableStateOf(false) }
    val attemptedActions = remember { mutableSetOf<PlayerErrorModalAction>() }
    val errorActionColumns = if (livePresentation) {
        playerErrorActionColumns(adaptiveUi.isTelevision, adaptiveUi.screenWidthDp)
    } else {
        actions.size.coerceAtLeast(1)
    }

    // Deterministic initial focus: an action only becomes eligible after it signals layout
    // placement, so the request never depends on a single frame guess. The preferred RETRY action
    // wins when it is placed and focusable, and each action is attempted at most once, which makes
    // the fallback bounded and terminates as soon as one request reports success.
    LaunchedEffect(actions, placedActions, focusAcquired) {
        if (focusAcquired) return@LaunchedEffect
        val candidates = playerErrorFocusCandidates(
            actions = actions,
            placedActions = placedActions,
            attemptedActions = attemptedActions,
        )
        for (candidate in candidates) {
            attemptedActions += candidate
            val acquired = runCatching {
                requesters[actions.indexOf(candidate)].requestFocus()
            }.getOrDefault(false)
            if (acquired) {
                focusAcquired = true
                return@LaunchedEffect
            }
        }
    }

    fun actionModifier(action: PlayerErrorModalAction, requester: FocusRequester): Modifier {
        val index = actions.indexOf(action)
        val neighbors = if (layoutDirection == LayoutDirection.Rtl) {
            playerErrorActionNeighbors(index, actions.size, errorActionColumns)
        } else {
            PlayerErrorActionNeighbors(
                left = (index - 1).takeIf { it >= 0 },
                right = (index + 1).takeIf { it < actions.size },
                up = null,
                down = null,
            )
        }
        return Modifier
            .focusRequester(requester)
            .focusProperties {
                left = neighbors.left?.let { requesters.getOrNull(it) } ?: FocusRequester.Cancel
                right = neighbors.right?.let { requesters.getOrNull(it) } ?: FocusRequester.Cancel
                up = neighbors.up?.let { requesters.getOrNull(it) } ?: FocusRequester.Cancel
                down = neighbors.down?.let { requesters.getOrNull(it) } ?: FocusRequester.Cancel
            }
            .onGloballyPositioned { placedActions = placedActions + action }
            .onFocusChanged { state ->
                if (state.isFocused) {
                    focusedAction = action
                } else if (focusedAction == action) {
                    focusedAction = null
                }
            }
    }

    @Composable
    fun ErrorActionButton(action: PlayerErrorModalAction, buttonModifier: Modifier) {
        when (action) {
            PlayerErrorModalAction.RETRY -> FocusButton(
                "اعادة المحاولة",
                onRetry,
                modifier = buttonModifier,
                primary = livePresentation || focusedAction == PlayerErrorModalAction.RETRY,
                accent = !livePresentation && focusedAction != PlayerErrorModalAction.RETRY,
                compact = true,
                trailingIcon = Icons.Rounded.Refresh.takeIf { livePresentation },
            )
            PlayerErrorModalAction.CHOOSE_CHANNEL -> FocusButton(
                "اختيار قناة",
                onChooseChannel,
                modifier = buttonModifier,
                primary = !livePresentation && focusedAction == PlayerErrorModalAction.CHOOSE_CHANNEL,
                outlined = livePresentation,
                compact = true,
                trailingIcon = if (livePresentation) Icons.AutoMirrored.Rounded.List else null,
            )
            PlayerErrorModalAction.CHOOSE_SOURCE -> FocusButton(
                "اختيار مصدر",
                onChooseServer,
                modifier = buttonModifier,
                primary = !livePresentation && focusedAction == PlayerErrorModalAction.CHOOSE_SOURCE,
                outlined = livePresentation,
                compact = true,
                trailingIcon = Icons.Rounded.Layers.takeIf { livePresentation },
            )
            PlayerErrorModalAction.BACK -> FocusButton(
                "رجوع",
                onBack,
                modifier = buttonModifier,
                primary = !livePresentation && focusedAction == PlayerErrorModalAction.BACK,
                outlined = livePresentation,
                compact = true,
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .70f))
            .pointerInput(Unit) { detectTapGestures { } }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (playerErrorModalInputDisposition(event.nativeKeyEvent.keyCode.toPlayerErrorModalInput())) {
                    PlayerErrorModalInputDisposition.HANDLE_BACK -> {
                        onBack()
                        true
                    }
                    PlayerErrorModalInputDisposition.CONSUME -> true
                    PlayerErrorModalInputDisposition.PASS_TO_MODAL_ACTION,
                    PlayerErrorModalInputDisposition.PASS_TO_SYSTEM,
                    -> false
                }
            },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(if (adaptiveUi.isTelevision) .82f else .92f)
                .widthIn(max = if (adaptiveUi.isTelevision) 920.dp else 620.dp)
                .focusGroup()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = .96f))
                .border(1.dp, colors.gold.copy(alpha = if (livePresentation) .55f else .34f), RoundedCornerShape(20.dp))
                .padding(if (adaptiveUi.isTelevision) 24.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (livePresentation) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = colors.gold,
                    modifier = Modifier.size(if (adaptiveUi.isTelevision) 38.dp else 32.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = title.orEmpty(),
                    color = colors.text,
                    fontSize = if (adaptiveUi.isTelevision) 26.sp else 21.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = message,
                    color = colors.text,
                    fontSize = if (adaptiveUi.isTelevision) 15.sp else 13.sp,
                    lineHeight = if (adaptiveUi.isTelevision) 24.sp else 21.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = .12f)),
                )
                Spacer(Modifier.height(14.dp))
            } else {
                ErrorNotice(message)
                Spacer(Modifier.height(15.dp))
            }
            if (livePresentation) {
                val actionGap = PLAYER_ERROR_ACTION_GAP_DP.dp
                val actionHeight = playerErrorActionHeightDp(adaptiveUi.isTelevision).dp
                val actionRows = playerErrorActionRows(actions.size, errorActionColumns)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val rowMaxWidth = if (errorActionColumns >= 4) 760.dp else 420.dp
                    val rowWidth = minOf(maxWidth, rowMaxWidth)
                    val actionWidth = ((rowWidth - actionGap * (errorActionColumns - 1)) / errorActionColumns)
                        .coerceAtLeast(96.dp)
                    Column(
                        modifier = Modifier.fillMaxWidth().focusGroup(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(actionGap),
                    ) {
                        actionRows.forEach { rowIndices ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(actionGap),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                rowIndices.forEach { index ->
                                    val action = actions[index]
                                    ErrorActionButton(
                                        action = action,
                                        buttonModifier = actionModifier(action, requesters[index])
                                            .width(actionWidth)
                                            .height(actionHeight),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(PLAYER_ERROR_ACTION_GAP_DP.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(actions.size) { index ->
                        val action = actions[index]
                        ErrorActionButton(
                            action = action,
                            buttonModifier = actionModifier(action, requesters[index]),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackSelectionPanel(
    title: String,
    emptyMessage: String,
    options: List<PlayerTrackOption>,
    showOff: Boolean,
    onSelect: (PlayerTrackOption) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerSidePanel(title, onClose, modifier) {
        if (showOff) FocusButton("ايقاف", {}, primary = false, compact = true)
        if (options.isEmpty()) {
            Text(emptyMessage, color = LocalHulkColors.current.textMuted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 30.dp))
        } else {
            options.forEach { option ->
                FocusButton(
                    text = if (option.secondary.isBlank()) option.label else "${option.label}  •  ${option.secondary}",
                    onClick = { onSelect(option) },
                    primary = option.selected,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(7.dp))
            }
        }
    }
}

@Composable
private fun SubtitleSelectionPanel(
    options: List<PlayerTrackOption>,
    subtitleSizeIndex: Int,
    raised: Boolean,
    onSelect: (PlayerTrackOption) -> Unit,
    onDisable: () -> Unit,
    onCycleSize: () -> Unit,
    onTogglePosition: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerSidePanel("الترجمة", onClose, modifier) {
        FocusButton("ايقاف الترجمة", onDisable, primary = options.none { it.selected }, compact = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        options.forEach { option ->
            FocusButton(
                if (option.secondary.isBlank()) option.label else "${option.label}  •  ${option.secondary}",
                { onSelect(option) },
                primary = option.selected,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(7.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text("مظهر الترجمة", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        FocusButton("الحجم: ${listOf("صغير", "متوسط", "كبير")[subtitleSizeIndex]}", onCycleSize, primary = false, compact = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(7.dp))
        FocusButton("المكان: ${if (raised) "مرتفع" else "اسفل"}", onTogglePosition, primary = false, compact = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun QualitySelectionPanel(
    options: List<PlayerTrackOption>,
    onAuto: () -> Unit,
    onSelect: (PlayerTrackOption) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerSidePanel("جودة التشغيل", onClose, modifier) {
        FocusButton("تلقائي", onAuto, primary = options.count { it.selected } != 1, compact = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        options.distinctBy { it.label }.sortedByDescending { qualitySortValue(it.label) }.forEach { option ->
            FocusButton(option.label, { onSelect(option) }, primary = option.selected, compact = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(7.dp))
        }
    }
}

@Composable
private fun SimpleOptionsPanel(
    title: String,
    options: List<Pair<String, () -> Unit>>,
    selectedLabel: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val optionFocus = remember { FocusRequester() }
    val initialIndex = options.indexOfFirst { it.first == selectedLabel }.takeIf { it >= 0 } ?: 0

    LaunchedEffect(selectedLabel, options.size) {
        withFrameNanos { }
        runCatching { optionFocus.requestFocus() }
    }

    PlayerSidePanel(title, onClose, modifier) {
        options.forEachIndexed { index, (label, action) ->
            FocusButton(
                label,
                action,
                primary = label == selectedLabel,
                compact = true,
                modifier = if (index == initialIndex) {
                    Modifier.fillMaxWidth().focusRequester(optionFocus)
                } else {
                    Modifier.fillMaxWidth()
                },
            )
            Spacer(Modifier.height(7.dp))
        }
    }
}

@Composable
private fun PlayerSidePanel(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val panelShape = RoundedCornerShape(24.dp)
    val closeOnBackModifier = Modifier.onPreviewKeyEvent { event ->
        val code = event.nativeKeyEvent.keyCode
        val isBack = code == AndroidKeyEvent.KEYCODE_BACK || code == AndroidKeyEvent.KEYCODE_ESCAPE
        if (isBack) {
            if (event.type == KeyEventType.KeyDown) onClose()
            true
        } else {
            false
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .62f)))
    Column(
        modifier = modifier
            .padding(horizontal = if (adaptiveUi.isTelevision) 30.dp else 12.dp, vertical = if (adaptiveUi.isTelevision) 24.dp else 10.dp)
            .fillMaxHeight(if (adaptiveUi.isTelevision) .90f else .94f)
            .width(if (adaptiveUi.isTelevision) 500.dp else 340.dp)
            .clip(panelShape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF080906), Color(0xFA15170F))))
            .border(1.dp, colors.gold.copy(alpha = .42f), panelShape)
            .padding(horizontal = if (adaptiveUi.isTelevision) 26.dp else 18.dp, vertical = if (adaptiveUi.isTelevision) 22.dp else 16.dp)
            .then(closeOnBackModifier),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = Color.White,
                fontSize = if (adaptiveUi.isTelevision) 23.sp else 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FocusButton("اغلاق", onClose, primary = false, compact = true)
        }
        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            content = content,
        )
    }
}

private fun Int?.orZero(): Int = this ?: 0

private fun extractTrackOptions(tracks: Tracks, type: Int): List<PlayerTrackOption> = buildList {
    tracks.groups.forEachIndexed { groupIndex, group ->
        if (group.type != type) return@forEachIndexed
        for (trackIndex in 0 until group.length) {
            if (!group.isTrackSupported(trackIndex, true)) continue
            val format = group.getTrackFormat(trackIndex)
            add(
                PlayerTrackOption(
                    key = "$groupIndex:$trackIndex",
                    label = trackLabel(format, type, trackIndex),
                    secondary = trackSecondary(format, type),
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                    selected = group.isTrackSelected(trackIndex),
                ),
            )
        }
    }
}.distinctBy { it.key }

private fun trackLabel(format: Format, type: Int, index: Int): String {
    val language = mediaTrackLanguage(format)
    val explicit = format.label?.trim()?.takeIf(String::isNotBlank)
    return when (type) {
        C.TRACK_TYPE_VIDEO -> qualityLabel(format.height)
        C.TRACK_TYPE_AUDIO -> language ?: explicit?.let(::languageFromExplicitLabel) ?: ""
        C.TRACK_TYPE_TEXT -> language ?: explicit?.let(::languageFromExplicitLabel) ?: ""
        else -> explicit ?: "مسار ${index + 1}"
    }
}

private fun detectedTrackLanguage(tracks: Tracks, type: Int): String? {
    val candidates = buildList {
        tracks.groups.forEach { group ->
            if (group.type != type) return@forEach
            for (trackIndex in 0 until group.length) {
                if (!group.isTrackSupported(trackIndex, true) && !group.isTrackSelected(trackIndex)) continue
                val format = group.getTrackFormat(trackIndex)
                val label = mediaTrackLanguage(format) ?: continue
                add(group.isTrackSelected(trackIndex) to label)
            }
        }
    }
    return candidates.firstOrNull { it.first }?.second
        ?: candidates.singleOrNull()?.second
}

private fun mediaTrackLanguage(format: Format): String? {
    format.language
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.takeUnless(::isUndeterminedLanguage)
        ?.let(::languageLabel)
        ?.let { return it }

    languageFromExplicitLabel(format.label)?.let { return it }
    languageFromExplicitLabel(format.id)?.let { return it }
    languageFromExplicitLabel(format.metadata?.toString())?.let { return it }
    return null
}

private fun trackSecondary(format: Format, type: Int): String = when (type) {
    C.TRACK_TYPE_AUDIO -> listOfNotNull(
        format.channelCount.takeIf { it > 0 }?.let { if (it >= 6) "5.1" else "$it قناة" },
        format.sampleMimeType?.substringAfterLast('/'),
    ).joinToString(" • ")
    C.TRACK_TYPE_VIDEO -> format.bitrate.takeIf { it > 0 }?.let { "${it / 1_000_000f} Mbps" }.orEmpty()
    else -> format.sampleMimeType?.substringAfterLast('/').orEmpty()
}

private fun selectedTrackLabel(options: List<PlayerTrackOption>, fallback: String): String =
    options.firstOrNull { it.selected && it.label.isNotBlank() }?.label
        ?: options.firstOrNull { it.label.isNotBlank() }?.label
        ?: fallback

private fun isUndeterminedLanguage(value: String): Boolean =
    value.lowercase(Locale.ROOT).replace('_', '-').substringBefore('-') in setOf("und", "zxx", "mul")

private fun languageFromExplicitLabel(raw: String?): String? {
    val value = raw?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank) ?: return null
    when {
        value.contains("arabic") || value.contains("العربية") || value.contains("العربيه") || value.contains("عربي") -> return "Arabic"
        value.contains("english") -> return "English"
        value.contains("french") || value.contains("français") -> return "French"
        value.contains("spanish") || value.contains("español") -> return "Spanish"
        value.contains("turkish") || value.contains("türk") -> return "Turkish"
        value.contains("german") || value.contains("deutsch") -> return "German"
        value.contains("italian") || value.contains("italiano") -> return "Italian"
        value.contains("portuguese") || value.contains("português") -> return "Portuguese"
        value.contains("russian") || value.contains("рус") -> return "Russian"
        value.contains("persian") || value.contains("farsi") || value.contains("فارسی") -> return "Persian"
        value.contains("urdu") || value.contains("اردو") -> return "Urdu"
        value.contains("hindi") || value.contains("हिन्दी") || value.contains("हिंदी") -> return "Hindi"
        value.contains("japanese") || value.contains("日本") -> return "Japanese"
        value.contains("korean") || value.contains("한국") -> return "Korean"
        value.contains("chinese") || value.contains("中文") -> return "Chinese"
    }
    return value
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .asSequence()
        .mapNotNull(::languageLabel)
        .firstOrNull()
}

private fun inferSubtitleLanguageFromText(text: String): String? {
    val letters = text.filter(Char::isLetter)
    if (letters.length < 3) return null
    fun countIn(range: CharRange): Int = letters.count { it in range }

    val arabic = letters.count { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }
    if (arabic >= 3 && arabic * 2 >= letters.length) return "Arabic"

    val hebrew = countIn('\u0590'..'\u05FF')
    if (hebrew >= 3 && hebrew * 2 >= letters.length) return "Hebrew"

    val cyrillic = countIn('\u0400'..'\u04FF')
    if (cyrillic >= 3 && cyrillic * 2 >= letters.length) return "Russian"

    val greek = countIn('\u0370'..'\u03FF')
    if (greek >= 3 && greek * 2 >= letters.length) return "Greek"

    val devanagari = countIn('\u0900'..'\u097F')
    if (devanagari >= 3 && devanagari * 2 >= letters.length) return "Hindi"

    val hangul = letters.count { it in '\uAC00'..'\uD7AF' || it in '\u1100'..'\u11FF' }
    if (hangul >= 3 && hangul * 2 >= letters.length) return "Korean"

    val kana = letters.count { it in '\u3040'..'\u30FF' }
    if (kana >= 2) return "Japanese"

    val han = letters.count { it in '\u4E00'..'\u9FFF' }
    if (han >= 2) return "Chinese"

    return null
}

private fun languageLabel(code: String): String? {
    val normalized = code.trim().lowercase(Locale.ROOT).replace('_', '-').substringBefore('-')
    val known = when (normalized) {
        "ar", "ara" -> "Arabic"
        "en", "eng" -> "English"
        "fr", "fra", "fre" -> "French"
        "es", "spa" -> "Spanish"
        "tr", "tur" -> "Turkish"
        "de", "deu", "ger" -> "German"
        "it", "ita" -> "Italian"
        "pt", "por" -> "Portuguese"
        "ru", "rus" -> "Russian"
        "fa", "fas", "per" -> "Persian"
        "ur", "urd" -> "Urdu"
        "hi", "hin" -> "Hindi"
        "ja", "jpn" -> "Japanese"
        "ko", "kor" -> "Korean"
        "zh", "zho", "chi" -> "Chinese"
        "id", "ind" -> "Indonesian"
        "ms", "msa", "may" -> "Malay"
        "th", "tha" -> "Thai"
        "nl", "nld", "dut" -> "Dutch"
        "sv", "swe" -> "Swedish"
        "pl", "pol" -> "Polish"
        "uk", "ukr" -> "Ukrainian"
        "he", "heb" -> "Hebrew"
        "el", "ell", "gre" -> "Greek"
        "cs", "ces", "cze" -> "Czech"
        "ro", "ron", "rum" -> "Romanian"
        "hu", "hun" -> "Hungarian"
        "bn", "ben" -> "Bengali"
        "vi", "vie" -> "Vietnamese"
        else -> null
    }
    if (known != null) return known
    if (normalized.isBlank() || isUndeterminedLanguage(normalized)) return null

    return runCatching {
        val locale = Locale.forLanguageTag(normalized)
        locale.getDisplayLanguage(Locale.ENGLISH)
            .trim()
            .takeIf { display ->
                display.isNotBlank() &&
                    !display.equals(normalized, ignoreCase = true) &&
                    !display.equals("Unknown language", ignoreCase = true)
            }
    }.getOrNull()
}

private fun qualityLabel(height: Int): String = when {
    height >= 2160 -> "4K"
    height >= 1440 -> "QHD"
    height >= 1080 -> "FHD"
    height >= 720 -> "HD"
    height > 0 -> "SD"
    else -> "تلقائي"
}

private fun qualitySortValue(label: String): Int = when (label) {
    "4K" -> 2160
    "QHD" -> 1440
    "FHD" -> 1080
    "HD" -> 720
    "SD" -> 480
    else -> 0
}

private fun resizeLabel(index: Int): String = when (index) {
    1 -> "تكبير"
    2 -> "ملء الشاشة"
    else -> "ملائم"
}

private fun speedLabel(speed: Float): String = if (speed == 1f) "1x" else "${speed}x"

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun Int.toPlayerErrorModalInput(): PlayerErrorModalInput = when (this) {
    AndroidKeyEvent.KEYCODE_BACK,
    AndroidKeyEvent.KEYCODE_ESCAPE,
    -> PlayerErrorModalInput.BACK
    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> PlayerErrorModalInput.LEFT
    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> PlayerErrorModalInput.RIGHT
    AndroidKeyEvent.KEYCODE_DPAD_UP -> PlayerErrorModalInput.UP
    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> PlayerErrorModalInput.DOWN
    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
    AndroidKeyEvent.KEYCODE_ENTER,
    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
    -> PlayerErrorModalInput.SELECT
    AndroidKeyEvent.KEYCODE_CHANNEL_UP -> PlayerErrorModalInput.CHANNEL_UP
    AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> PlayerErrorModalInput.CHANNEL_DOWN
    AndroidKeyEvent.KEYCODE_MEDIA_NEXT,
    AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    AndroidKeyEvent.KEYCODE_MEDIA_REWIND,
    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
    -> PlayerErrorModalInput.PLAYER_COMMAND
    else -> PlayerErrorModalInput.OTHER
}

internal fun relativeChannelIndex(currentIndex: Int, delta: Int, size: Int): Int {
    require(size > 0)
    return (((currentIndex + delta) % size) + size) % size
}
