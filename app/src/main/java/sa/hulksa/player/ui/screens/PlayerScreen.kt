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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
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
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
private const val LIVE_CONTROL_HOLD_REPEAT_MS = 180L
private const val NEXT_EPISODE_SECONDS = 8
private const val PLAYER_OFFLINE_MESSAGE = "لا يوجد اتصال بالانترنت. سيتم استئناف التشغيل تلقائيا عند عودة الاتصال."

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

/** One authoritative VOD entry/player presentation. */
internal enum class VodPlayerPresentation { ERROR_CARD, RESUME, PLAYER }

/**
 * Single presentation decision for a Movie or Series request: a blocking failure or remote offline
 * entry wins, otherwise an unresolved saved-position decision, otherwise normal player
 * presentation. Connectivity is typed state; localized message equality is never used.
 */
internal fun vodPlayerPresentation(
    isVod: Boolean,
    localPlayback: Boolean,
    networkAvailable: Boolean,
    offlineFailure: Boolean,
    finalErrorPresent: Boolean,
    resumePromptPending: Boolean,
    offlineInitial: Boolean,
): VodPlayerPresentation = when {
    isVod && !localPlayback && (offlineInitial || (!networkAvailable && (offlineFailure || finalErrorPresent))) ->
        VodPlayerPresentation.ERROR_CARD
    isVod && finalErrorPresent -> VodPlayerPresentation.ERROR_CARD
    resumePromptPending -> VodPlayerPresentation.RESUME
    else -> VodPlayerPresentation.PLAYER
}

/**
 * First-frame offline entry: a remote Movie or Series episode opened while the validated
 * connectivity snapshot is already offline shows the offline card immediately, before any delayed
 * network effect can let the Resume dialog, player chrome or an old error flash first.
 */
internal fun vodOfflineInitialVisible(
    isVod: Boolean,
    localPlayback: Boolean,
    networkAvailable: Boolean,
    isPlaying: Boolean,
    playbackReady: Boolean,
): Boolean = isVod && !localPlayback && !networkAvailable && !isPlaying && !playbackReady

/**
 * Copy for the single shared VOD error card: offline wording only for real unusable connectivity.
 * `mediaLabel` selects the truthful media noun ("الفلم" or "الحلقة").
 */
internal fun moviePlayerErrorCopy(
    offline: Boolean,
    resumePending: Boolean,
    formattedSavedTime: String,
    failureMessage: String?,
    mediaLabel: String,
): MovieOfflineCardCopy = when {
    offline && resumePending -> movieOfflineCardCopy(
        resumePending = true,
        formattedSavedTime = formattedSavedTime,
    )
    offline -> movieOfflineCardCopy(
        resumePending = false,
        formattedSavedTime = formattedSavedTime,
    )
    else -> MovieOfflineCardCopy(
        title = "تعذر تشغيل $mediaLabel",
        body = failureMessage?.trim()?.takeIf { it.isNotEmpty() }
            ?: "حدث خطا اثناء التشغيل. حاول مرة اخرى.",
        context = if (resumePending) {
            "توقفت عند $formattedSavedTime"
        } else {
            "مكان توقفك محفوظ"
        },
    )
}

/** Copy for the single Live error card: offline wording only for real unusable connectivity. */
internal data class LivePlayerErrorPresentation(
    val title: String,
    val body: String,
    val offline: Boolean,
)

/**
 * Truthful Live player error presentation.
 *
 * `offlineFailure` is the typed unusable-connectivity state (never message-string matching). A
 * started broadcast that was playing when connectivity dropped keeps the automatic-return wording
 * because the existing network-restore owner replays the captured intent; an offline entry or a
 * paused broadcast never promises autoplay. Online Live failures keep the real server/media message
 * under the Live channel-recovery title. No Movie saved-position/Resume wording is used.
 */
internal fun livePlayerErrorPresentation(
    offlineFailure: Boolean,
    playbackStarted: Boolean,
    autoResumeIntent: Boolean,
    failureMessage: String?,
): LivePlayerErrorPresentation = when {
    offlineFailure && playbackStarted && autoResumeIntent -> LivePlayerErrorPresentation(
        title = "انقطع اتصال الانترنت",
        body = "سيعود تشغيل القناة تلقائيا عند عودة الاتصال",
        offline = true,
    )
    offlineFailure && playbackStarted -> LivePlayerErrorPresentation(
        title = "انقطع اتصال الانترنت",
        body = "القناة متوقفة مؤقتا ، اضغط تشغيل للمتابعة بعد عودة الاتصال",
        offline = true,
    )
    offlineFailure -> LivePlayerErrorPresentation(
        title = "لا يوجد اتصال بالانترنت",
        body = "تعذر تشغيل القناة ، تحقق من الاتصال وحاول مرة اخرى",
        offline = true,
    )
    else -> LivePlayerErrorPresentation(
        title = "تعذر تشغيل القناة",
        body = failureMessage?.trim()?.takeIf { it.isNotEmpty() }
            ?: "البث غير متاح حاليا ، جرب اعادة المحاولة او اختر قناة اخرى",
        offline = false,
    )
}

/**
 * Playback intent carried across a connectivity restore. A movie the user paused manually stays
 * paused, a pending Resume decision never auto-plays, and a movie that was playing resumes.
 */
internal fun movieOfflineRestoredPlayWhenReady(
    wasPlayingBeforeOffline: Boolean,
    resumePromptPending: Boolean,
): Boolean = wasPlayingBeforeOffline && !resumePromptPending

internal data class MovieOfflineCardCopy(
    val title: String,
    val body: String,
    val context: String,
)

/** Exact owner copy for the single Movie offline card; resumePending selects the state. */
internal fun movieOfflineCardCopy(resumePending: Boolean, formattedSavedTime: String): MovieOfflineCardCopy =
    if (resumePending) {
        MovieOfflineCardCopy(
            title = "لا يوجد اتصال بالانترنت",
            body = "اتصل بالانترنت لاكمال المشاهدة",
            context = "توقفت عند $formattedSavedTime",
        )
    } else {
        MovieOfflineCardCopy(
            title = "انقطع اتصال الانترنت",
            body = "سيعود التشغيل تلقائيا عند عودة الاتصال",
            context = "مكان توقفك محفوظ",
        )
    }

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

internal fun hasUsableNetwork(context: Context): Boolean {
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
    vodItem: ContentItem? = null,
    onSelectLiveChannel: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onLastChannel: (() -> Unit)? = null,
    onBack: () -> Unit,
    onProgress: (request: PlaybackRequest, positionMs: Long, durationMs: Long) -> Unit,
    previousEpisodeTitle: String? = null,
    onPlayPreviousEpisode: (() -> Unit)? = null,
    nextEpisodeTitle: String? = null,
    onPlayNextEpisode: (() -> Unit)? = null,
    liveControlsRevealRequested: Boolean = false,
    liveControlsInteractionTick: Int = 0,
    liveChannelHoldRepeat: Boolean = false,
    onPreviousLiveChannel: (() -> Int)? = null,
    onNextLiveChannel: (() -> Int)? = null,
    onLiveChannelHoldRelease: (() -> Unit)? = null,
    onLiveChannelHoldCancelled: ((Int) -> Unit)? = null,
    liveCatalogIndex: LiveChannelCatalogIndex? = null,
    onErrorModalActiveChanged: (Boolean) -> Unit = {},
    onBrowserVisibilityChanged: (Boolean) -> Unit = {},
    onPanelActiveChanged: (Boolean) -> Unit = {},
    onVodForegroundModalActiveChanged: (Boolean) -> Unit = {},
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
    // Live error-origin foreground surfaces (channel browser / source picker) suppress the
    // background error presentation, key ownership and focus acquisition together while they own
    // input. The failure/recovery state itself stays live so closing restores only a still-valid
    // error and never a stale snapshot from a recovered or replaced request.
    var liveErrorSurfaceSuppressed by remember(request) { mutableStateOf(false) }
    var offlineFailure by remember(request) { mutableStateOf(false) }
    var offlineWasPlaying by remember(request) { mutableStateOf(false) }
    var restoredPlayWhenReady by remember(request) { mutableStateOf<Boolean?>(null) }
    var networkAvailable by remember(context, request) { mutableStateOf(hasUsableNetwork(context)) }
    var buffering by remember(request) { mutableStateOf(true) }
    var controlsVisible by remember(request) {
        mutableStateOf(liveControlsVisibleOnRequestStart(request.isLive, liveControlsRevealRequested))
    }
    var browserVisible by remember(request) { mutableStateOf(false) }
    var browserOrigin by remember(request) { mutableStateOf(LiveChannelBrowserOrigin.NORMAL_LIVE) }
    var activePanel by remember(request) { mutableStateOf<PlayerPanel?>(null) }
    var liveMorePanel by remember(request) { mutableStateOf<PlayerLiveMorePanelView?>(null) }
    var liveMoreMenuFocusRow by remember(request) { mutableStateOf(PlayerLiveMoreRow.MUTE) }
    var vodMorePanel by remember(request) { mutableStateOf<VodMorePanelView?>(null) }
    var vodMoreMenuFocusRow by remember(request) { mutableStateOf(VodMoreRow.GO_TO_TIME) }
    var vodGoToTimeVisible by remember(request) { mutableStateOf(false) }
    var vodPanelFocusTick by remember(request) { mutableIntStateOf(0) }
    var vodSeekPreviewMs by remember(request) { mutableStateOf<Long?>(null) }
    var vodTimelineFocused by remember(request) { mutableStateOf(false) }
    var moreFocusRestoreTick by remember(request) { mutableIntStateOf(0) }
    var isPlaying by remember(request) { mutableStateOf(false) }
    // Distinguishes an offline entry from a connectivity drop during a started Live broadcast so
    // the Live error copy never promises an automatic return that the captured intent cannot honor.
    var livePlaybackStarted by remember(request) { mutableStateOf(false) }
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
    var focusTimelineOnReveal by remember(request) { mutableStateOf(false) }
    var resumePromptVisible by remember(request) {
        mutableStateOf(playbackSettings.resumePlayback && !request.isLive && request.resumePositionMs > 0L)
    }
    // Request-owned progress persistence: while a VOD Resume decision is pending, preparation,
    // cancellation, lifecycle/disposal and late callbacks must not overwrite the stored position
    // or duration with the provisional zero. Only an explicit Resume/Restart decision clears it.
    // This protects Series episode progress exactly like Movie progress.
    var vodProgressPersistenceBlocked by remember(request) {
        mutableStateOf(
            vodProgressPersistenceBlockedInitially(
                isVod = !request.isLive,
                resumePlaybackEnabled = playbackSettings.resumePlayback,
                resumePositionMs = request.resumePositionMs,
            ),
        )
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
    val topBarBackFocus = remember { FocusRequester() }
    val resumeFocus = remember { FocusRequester() }
    val resumeRestartFocus = remember { FocusRequester() }
    val resumeBackFocus = remember { FocusRequester() }
    val unlockFocus = remember { FocusRequester() }
    val nextEpisodePlayFocus = remember { FocusRequester() }
    val nextEpisodeCancelFocus = remember { FocusRequester() }
    val errorRetryFocus = remember { FocusRequester() }
    val offlineRetryFocus = remember { FocusRequester() }
    val offlineBackFocus = remember { FocusRequester() }
    // The Pro layer owns one catalog index per catalog instance and passes it down; the direct
    // (Kids) caller builds its own. Either way the index is remembered per catalog instance.
    val ownedLiveCatalogIndex = remember(liveCatalog, liveCatalogIndex) {
        liveCatalogIndex ?: LiveChannelCatalogIndex(liveCatalog?.items.orEmpty())
    }
    val resolvedLiveCatalogIndex = liveCatalogIndex ?: ownedLiveCatalogIndex
    val currentChannel = remember(resolvedLiveCatalogIndex, request.streamId) {
        val lookupStartMs = android.os.SystemClock.elapsedRealtime()
        val found = resolvedLiveCatalogIndex.byId[request.streamId]
        if (request.isLive) {
            android.util.Log.i(
                "HulkPlayer",
                "currentChannel lookup t=${android.os.SystemClock.elapsedRealtime()} " +
                    "self=${android.os.SystemClock.elapsedRealtime() - lookupStartMs}ms id=${request.streamId}",
            )
        }
        found
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
    val vodFavorite = remember(vodItem, favoriteKeys) {
        vodFavoriteControl(item = vodItem, favoriteKeys = favoriteKeys)
    }
    val vodDirectSeekActive = focusTimelineOnReveal && !request.isLive
    // Active-seek control visibility no longer depends on a preview window: controls stay usable
    // while the shared VOD TV timeline/direct-seek interaction is live and ordinary auto-hide
    // resumes after exit.
    val vodActiveSeekInteraction = vodActiveSeekHoldsControls(
        isVod = !request.isLive,
        remoteInput = tvRemoteInput,
        timelineFocused = vodTimelineFocused,
        directSeekActive = vodDirectSeekActive,
    )
    // Shared seek-target presentation: the explicit scrub target wins, otherwise the transient
    // direct-seek target shows while the remote interaction is active. This drives the timeline
    // thumb and progress; it is never an image-preview or floating-bubble request.
    val vodEffectiveSeekMs = vodSeekPreviewMs
        ?: manualSeekTargetMs?.takeIf { focusTimelineOnReveal && !request.isLive }
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
    val channelSequence = remember(resolvedLiveCatalogIndex, currentChannel) {
        val sequenceStartMs = android.os.SystemClock.elapsedRealtime()
        val resolved = resolvedLiveCatalogIndex
            .channelsInCategory(currentChannel?.categoryId)
            .ifEmpty { resolvedLiveCatalogIndex.items }
        if (currentChannel != null && resolved.isNotEmpty()) {
            android.util.Log.i(
                "HulkPlayer",
                "channelSequence build t=${android.os.SystemClock.elapsedRealtime()} " +
                    "self=${android.os.SystemClock.elapsedRealtime() - sequenceStartMs}ms size=${resolved.size}",
            )
        }
        resolved
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
    // The validated connectivity snapshot is authoritative from the first composition, so a
    // remote VOD entry opened offline renders the offline card immediately instead of flashing
    // the Resume dialog, player chrome or an old error while the delayed network effect catches up.
    val movieOfflineInitial = vodOfflineInitialVisible(
        isVod = !request.isLive,
        localPlayback = localPlayback,
        networkAvailable = networkAvailable,
        isPlaying = isPlaying,
        playbackReady = player.playbackState == Player.STATE_READY,
    )
    // One authoritative presentation branch for rendering, focus, semantics and input. The
    // Resume dialog and any VOD error card are therefore mutually exclusive by construction.
    val moviePresentation = vodPlayerPresentation(
        isVod = !request.isLive,
        localPlayback = localPlayback,
        networkAvailable = networkAvailable,
        offlineFailure = offlineFailure,
        finalErrorPresent = finalError != null,
        resumePromptPending = resumePromptVisible,
        offlineInitial = movieOfflineInitial,
    )
    val movieErrorActive = !request.isLive && moviePresentation == VodPlayerPresentation.ERROR_CARD
    val movieModalActive = !request.isLive && moviePresentation != VodPlayerPresentation.PLAYER
    // The card uses offline wording/icon only for genuinely unusable connectivity.
    val movieErrorCardOffline = !request.isLive && !localPlayback && (movieOfflineInitial || !networkAvailable)
    val vodErrorMediaLabel = if (request.streamKind.equals("series", ignoreCase = true)) "الحلقة" else "الفلم"
    // One foreground eligibility shared by the error path's rendering, key/pointer ownership and
    // focus acquisition. While a Live error-origin surface owns input, all three are suspended.
    val liveErrorForegroundActive = playerErrorForegroundActive(
        finalErrorPresent = finalError != null,
        liveSurfaceSuppressed = liveErrorSurfaceSuppressed,
    )
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
        when (playerErrorSurfaceRelease(liveErrorSurfaceSuppressed)) {
            PlayerErrorSurfaceRelease.CLEAR_LIVE_SUPPRESSION -> liveErrorSurfaceSuppressed = false
            PlayerErrorSurfaceRelease.RESTORE_SUSPENDED_ERROR -> restoreSuspendedFinalError()
        }
    }

    fun closeActivePanelAndRestoreError() {
        val restoreError = activePanel == PlayerPanel.SERVERS
        activePanel = null
        if (!restoreError) return
        when (playerErrorSurfaceRelease(liveErrorSurfaceSuppressed)) {
            PlayerErrorSurfaceRelease.CLEAR_LIVE_SUPPRESSION -> liveErrorSurfaceSuppressed = false
            PlayerErrorSurfaceRelease.RESTORE_SUSPENDED_ERROR -> restoreSuspendedFinalError()
        }
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
        if (!request.isLive && !vodProgressPersistenceBlocked) {
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

    fun openVodMorePanel() {
        vodMoreMenuFocusRow = VodMoreRow.GO_TO_TIME
        vodGoToTimeVisible = false
        vodMorePanel = VodMorePanelView.MENU
    }

    fun closeVodMorePanelToTrigger() {
        vodMorePanel = null
        vodGoToTimeVisible = false
        vodSeekPreviewMs = null
        moreFocusRestoreTick += 1
    }

    fun closeVodMorePanelForSelection() {
        vodMorePanel = null
        vodGoToTimeVisible = false
        vodSeekPreviewMs = null
        revealControls()
    }

    fun handleVodMoreBack() {
        val currentView = vodMorePanel ?: return
        if (vodGoToTimeVisible) {
            vodGoToTimeVisible = false
            vodMoreMenuFocusRow = VodMoreRow.GO_TO_TIME
            vodPanelFocusTick += 1
            return
        }
        val nextView = vodMorePanelBack(currentView)
        if (nextView == null) {
            closeVodMorePanelToTrigger()
        } else {
            vodMoreMenuFocusRow = vodMorePanelOriginRow(currentView) ?: vodMoreMenuFocusRow
            vodMorePanel = nextView
        }
    }

    fun applyVodPlaybackSpeed(rawSpeed: Float) {
        val speed = vodNormalizedSpeed(rawSpeed)
        playbackSpeed = speed
        player.setPlaybackSpeed(speed)
    }

    fun restartVodFromBeginning() {
        vodSeekPreviewMs = null
        manualSeekTargetMs = null
        player.seekTo(0L)
        currentPositionMs = 0L
        controlsVisible = true
        player.play()
    }

    fun previewVodSeek(targetMs: Long) {
        if (request.isLive || durationMs <= 0L) return
        vodSeekPreviewMs = targetMs.coerceIn(0L, durationMs)
        controlsVisible = true
    }

    fun commitVodSeek(targetMs: Long) {
        vodSeekPreviewMs = null
        // The committed position stays under its existing owner; the cancelled preview no longer
        // keeps a presentation hold.
        seekToPosition(targetMs)
    }

    fun cancelVodSeekPreview() {
        vodSeekPreviewMs = null
        currentPositionMs = player.currentPosition.coerceAtLeast(0L)
    }

    fun handleBackAction() {
        when {
            browserVisible -> closeBrowserAndRestoreError()
            liveErrorForegroundActive -> saveAndBack()
            vodGoToTimeVisible -> {
                vodGoToTimeVisible = false
                vodMoreMenuFocusRow = VodMoreRow.GO_TO_TIME
                vodPanelFocusTick += 1
            }
            activePanel != null -> closeActivePanelAndRestoreError()
            vodMorePanel != null -> handleVodMoreBack()
            liveMorePanel != null -> handleLiveMoreBack()
            resumePromptVisible -> {
                resumePromptVisible = false
                controlsVisible = false
                player.pause()
                onBack()
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

    // An accepted remote channel switch carries its reveal intent into this request's existing
    // controls owner: the compact Live strip appears instead of a separate switching overlay, and
    // the existing auto-hide effect owns hiding it again. Every accepted repeat is a fresh
    // interaction through the tick, so re-reveal and auto-hide refresh work while visibility is
    // already true.
    LaunchedEffect(request.historyKey, liveControlsRevealRequested, liveControlsInteractionTick) {
        if (liveControlsRevealRequested) {
            controlsVisible = true
        }
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
                    offlineWasPlaying = playerOfflinePlayIntentCapture(
                        alreadyOffline = offlineFailure,
                        previousIntent = offlineWasPlaying,
                        currentPlayingIntent = player.playWhenReady || player.isPlaying,
                    )
                    offlineFailure = true
                    finalError = PLAYER_OFFLINE_MESSAGE
                },
            ),
        )
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
                if (playbackState == Player.STATE_READY) {
                    android.util.Log.i(
                        "HulkPlayer",
                        "ready t=${android.os.SystemClock.elapsedRealtime()} streamId=${request.streamId}",
                    )
                }
                if (playbackState == Player.STATE_READY && request.isLive) {
                    livePlaybackStarted = true
                }
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
            if (!request.isLive && !vodProgressPersistenceBlocked) {
                onProgress(request, player.currentPosition.coerceAtLeast(0L), player.duration.coerceAtLeast(0L))
            }
            player.removeListener(listener)
            playerDiagnostics.detach(player)
            playerSession.detach(player)
            val releaseStartMs = android.os.SystemClock.uptimeMillis()
            player.release()
            if (request.isLive) {
                android.util.Log.i(
                    "HulkPlayer",
                    "player released t=${android.os.SystemClock.elapsedRealtime()} " +
                        "streamId=${request.streamId} in ${android.os.SystemClock.uptimeMillis() - releaseStartMs}ms",
                )
            }
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
        android.util.Log.i(
            "HulkPlayer",
            "media set t=${android.os.SystemClock.elapsedRealtime()} streamId=${request.streamId}",
        )

        val replacement = pendingPlayerReplacement?.takeIf { pending ->
            pending.candidateIndex == candidateIndex && pending.outputMode == audioOutputMode
        }
        if (replacement != null) {
            player.trackSelectionParameters = replacement.trackSelectionParameters
            replacement.positionMs?.let(player::seekTo)
            player.setPlaybackSpeed(replacement.speed)
            player.volume = replacement.volume
            player.prepare()
            android.util.Log.i(
                "HulkPlayer",
                "prepare issued t=${android.os.SystemClock.elapsedRealtime()} streamId=${request.streamId}",
            )
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
        android.util.Log.i(
            "HulkPlayer",
            "prepare issued t=${android.os.SystemClock.elapsedRealtime()} streamId=${request.streamId}",
        )
        // A connectivity restore replays the intent captured when the network dropped, so a
        // manually paused movie stays paused and a pending Resume decision never auto-plays.
        player.playWhenReady = restoredPlayWhenReady ?: !resumePromptVisible
        restoredPlayWhenReady = null
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
            offlineWasPlaying = playerOfflinePlayIntentCapture(
                alreadyOffline = offlineFailure,
                previousIntent = offlineWasPlaying,
                currentPlayingIntent = player.playWhenReady || player.isPlaying,
            )
            player.pause()
            suspendedFinalError = null
            finalFailureClass = null
            buffering = false
            controlsVisible = true
            offlineFailure = true
            finalError = PLAYER_OFFLINE_MESSAGE
            return@LaunchedEffect
        }

        if (offlineFailure) {
            val resumePositionMs = if (request.isLive) {
                0L
            } else {
                maxOf(pendingSeekMs, currentPositionMs, player.currentPosition.coerceAtLeast(0L))
            }
            delay(700L)
            if (!hasUsableNetwork(context)) return@LaunchedEffect
            pendingSeekMs = resumePositionMs
            restoredPlayWhenReady = movieOfflineRestoredPlayWhenReady(
                wasPlayingBeforeOffline = offlineWasPlaying,
                resumePromptPending = resumePromptVisible,
            )
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
            if (!vodProgressPersistenceBlocked) {
                onProgress(request, player.currentPosition.coerceAtLeast(0L), player.duration.coerceAtLeast(0L))
            }
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
        vodMorePanel,
        vodGoToTimeVisible,
        vodSeekPreviewMs,
        resumePromptVisible,
        controlsLocked,
        manualSeekTargetMs,
        vodTimelineFocused,
        focusTimelineOnReveal,
        // A fresh accepted Live switch interaction restarts the existing auto-hide delay even when
        // the controls are already visible (same timeout owner, no second timer).
        liveControlsInteractionTick,
    ) {
        if (
            playbackSettings.autoHideControls &&
            controlsVisible && !browserVisible && activePanel == null && liveMorePanel == null &&
            vodMorePanel == null && !vodGoToTimeVisible && vodSeekPreviewMs == null &&
            !resumePromptVisible && !buffering && finalError == null && isPlaying && !controlsLocked &&
            manualSeekTargetMs == null && !vodActiveSeekInteraction
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

    // One countdown owner: while a foreground decision (Resume, error card, panel, unlock, lock,
    // browser) owns input, the timer freezes instead of dispatching an episode change behind it.
    val autoplayCountdownBlocked = movieModalActive || resumePromptVisible || controlsLocked ||
        unlockVisible || activePanel != null || liveMorePanel != null || vodMorePanel != null ||
        vodGoToTimeVisible || browserVisible
    LaunchedEffect(nextCountdown, appForeground, autoplayCountdownBlocked) {
        if (autoplayCountdownBlocked) {
            return@LaunchedEffect
        }
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
        vodMorePanel,
        browserVisible,
        liveErrorForegroundActive,
        offlineFailure,
        movieModalActive,
        movieErrorActive,
        resumePromptVisible,
        unlockVisible,
        controlsLocked,
        nextCountdown,
        focusTimelineOnReveal,
        request.historyKey,
    ) {
        val target = when {
            movieErrorActive -> offlineRetryFocus
            liveErrorForegroundActive -> null
            browserVisible || activePanel != null -> null
            vodMorePanel != null -> null
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

    val errorModalInputActive = playerErrorModalOwnsInput(
        errorForegroundActive = liveErrorForegroundActive,
        suspendedErrorPresent = suspendedFinalError != null,
    )
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

    val panelInputActive = playerChildPanelInputActive(
        activePanel != null,
        liveMorePanel != null || vodMorePanel != null || vodGoToTimeVisible,
    )
    val latestOnPanelActiveChanged by rememberUpdatedState(onPanelActiveChanged)
    DisposableEffect(panelInputActive) {
        latestOnPanelActiveChanged(panelInputActive)
        onDispose { latestOnPanelActiveChanged(false) }
    }

    // Non-live foreground decision surfaces (Resume, error card, unlock, next-episode countdown)
    // and the actual locked-controls state own input exactly like the panels above. The Pro layer
    // gates the parent MEDIA_NEXT/PREVIOUS episode shortcuts on this signal so no episode can
    // change behind a pending decision or while the controls are locked, including the locked
    // window where the unlock overlay is not currently visible.
    val vodForegroundModalActive = playerVodForegroundDecisionActive(
        isLive = request.isLive,
        resumePromptVisible = resumePromptVisible,
        errorModalActive = movieErrorActive,
        unlockVisible = unlockVisible,
        nextCountdownActive = nextCountdown >= 0,
        controlsLocked = controlsLocked,
    )
    val latestOnVodForegroundModalActiveChanged by rememberUpdatedState(onVodForegroundModalActiveChanged)
    DisposableEffect(vodForegroundModalActive) {
        latestOnVodForegroundModalActiveChanged(vodForegroundModalActive)
        onDispose { latestOnVodForegroundModalActiveChanged(false) }
    }

    val interactionModifier = Modifier
        // movieModalActive is a derived plain value; it must be a key so the tap detector is
        // re-registered when a Resume/error/offline modal closes, otherwise the block created while
        // the modal was visible keeps rejecting every later surface tap in the same session. The
        // browser and a suppressed Live error surface are keys for the same reason: while they own
        // input the player surface behind them must not toggle controls or switch channels.
        .pointerInput(request, liveErrorForegroundActive, liveErrorSurfaceSuppressed, movieModalActive, browserVisible) {
            detectTapGestures(onTap = {
                if (browserVisible || liveErrorSurfaceSuppressed || resumePromptVisible || movieModalActive) {
                    return@detectTapGestures
                }
                when {
                    liveMorePanel != null -> closeLiveMorePanelToTrigger()
                    vodMorePanel != null -> closeVodMorePanelToTrigger()
                    controlsLocked -> { unlockVisible = true; controlsVisible = true }
                    else -> controlsVisible = !controlsVisible
                }
            })
        }
        .pointerInput(request.isLive, liveErrorForegroundActive, liveErrorSurfaceSuppressed, browserVisible) {
            if (request.isLive && !browserVisible && !liveErrorSurfaceSuppressed && !liveErrorForegroundActive) {
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
                if (liveErrorForegroundActive) {
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
                if (vodGoToTimeVisible) {
                    // The time window owns input: consume playback/media/channel commands so the
                    // player behind it cannot act, while directional/OK/TAB/numeric still reach the
                    // dialog nodes. Remote/TV Back is consumed here; touch-platform system Back
                    // falls through to the screen BackHandler, so the dialog is dismissed exactly
                    // once and focus returns to the originating Go-to-time row.
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (keyCode == AndroidKeyEvent.KEYCODE_BACK || keyCode == AndroidKeyEvent.KEYCODE_ESCAPE) {
                        if (keyCode == AndroidKeyEvent.KEYCODE_ESCAPE || tvRemoteInput) {
                            handleBackAction()
                            return@onPreviewKeyEvent true
                        }
                        return@onPreviewKeyEvent false
                    }
                    return@onPreviewKeyEvent when (keyCode) {
                        AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
                        AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
                        AndroidKeyEvent.KEYCODE_MEDIA_NEXT,
                        AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        AndroidKeyEvent.KEYCODE_MEDIA_REWIND,
                        AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                        AndroidKeyEvent.KEYCODE_MEDIA_STOP,
                        AndroidKeyEvent.KEYCODE_CHANNEL_UP,
                        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                        -> true
                        else -> false
                    }
                }
                if (resumePromptVisible) {
                    // A first remote/TV BACK (or ESCAPE) cancels the pending Resume through the
                    // existing handler and is consumed before the dialog focus can merely clear.
                    // Touch-platform system Back remains with the screen BackHandler for a single
                    // cancellation; every other key keeps the dialog's focus graph unchanged.
                    when (
                        movieResumeBackDisposition(
                            isBackKey = keyCode == AndroidKeyEvent.KEYCODE_BACK,
                            isEscapeKey = keyCode == AndroidKeyEvent.KEYCODE_ESCAPE,
                            keyDown = event.type == KeyEventType.KeyDown,
                            isRepeat = event.nativeKeyEvent.repeatCount > 0,
                            remoteInput = tvRemoteInput,
                        )
                    ) {
                        MovieResumeBackDisposition.HANDLE_AND_CONSUME -> {
                            handleBackAction()
                            return@onPreviewKeyEvent true
                        }
                        MovieResumeBackDisposition.PASS_TO_SYSTEM,
                        MovieResumeBackDisposition.PASS_TO_DIALOG,
                        -> return@onPreviewKeyEvent false
                    }
                }
                if (
                    event.type != KeyEventType.KeyDown || browserVisible || activePanel != null ||
                    liveMorePanel != null || vodMorePanel != null ||
                    resumePromptVisible || unlockVisible || nextCountdown >= 0
                ) {
                    return@onPreviewKeyEvent false
                }
                if (keyCode == AndroidKeyEvent.KEYCODE_BACK || keyCode == AndroidKeyEvent.KEYCODE_ESCAPE) {
                    // A held/repeated Back must not navigate a second time; the first handled press
                    // owns the transition.
                    if (event.nativeKeyEvent.repeatCount == 0) handleBackAction()
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
                        seekBy(-seekStepMs)
                        true
                    } else false
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> if (!request.isLive && surfaceFocused) {
                        focusTimelineOnReveal = true
                        controlsVisible = true
                        seekBy(seekStepMs)
                        true
                    } else false
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> if (
                        !request.isLive && tvRemoteInput && surfaceFocused && focusTimelineOnReveal &&
                        controlsVisible
                    ) {
                        // Direct-seek exit: the first DOWN from the video surface leaves the
                        // direct-seek focus mode through the existing focus-transition effect,
                        // which targets the attached primary tool. The already requested seek
                        // target is untouched.
                        focusTimelineOnReveal = false
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

        if (controlsVisible && nextCountdown < 0 && finalError == null && !movieModalActive && !browserVisible && activePanel == null && !controlsLocked) {
            PlayerTopBar(
                title = playerDisplayTitle,
                isLive = request.isLive,
                quality = qualityLabel(videoHeight),
                speed = playbackSpeed,
                channel = currentChannel.takeIf { request.isLive },
                onBack = ::saveAndBack,
                backFocusRequester = topBarBackFocus.takeIf { !request.isLive },
                backDownFocus = seekBarFocus.takeIf { !request.isLive },
            )
        }

        if (controlsVisible && nextCountdown < 0 && finalError == null && !movieModalActive && !browserVisible && activePanel == null && !controlsLocked) {
            if (request.isLive) {
                val liveDensity = LocalDensity.current
                val minimumMorePanelHeight = remember(
                    liveControlMetrics.captionSizeSp,
                    liveDensity.fontScale,
                ) {
                    livePlayerMorePanelMinimumHeightDp(
                        captionSizeSp = liveControlMetrics.captionSizeSp,
                        fontScale = liveDensity.fontScale,
                    ).dp
                }
                // The overlay measures the real strip first, then either the accepted above-strip
                // arrangement or a bounded full-height fallback when the complete strip plus a
                // usable panel body cannot coexist. The strip is never overlapped or compressed.
                LiveBottomOverlay(
                    showPanel = liveMorePanel != null,
                    gap = 10.dp,
                    minimumPanelHeight = minimumMorePanelHeight,
                    panelModifier = Modifier.padding(
                        end = liveControlsLayout.outerHorizontalPaddingDp.dp,
                    ),
                    fallbackPanelModifier = Modifier
                        .navigationBarsPadding()
                        .padding(
                            end = liveControlsLayout.outerHorizontalPaddingDp.dp,
                            bottom = liveControlsLayout.outerBottomPaddingDp.dp,
                        ),
                    strip = {
                        LivePlayerControls(
                            isPlaying = isPlaying,
                            favorite = liveFavoriteControl.favorite,
                            favoriteEnabled = liveFavoriteControl.enabled,
                            lastChannelEnabled = onLastChannel != null,
                            moreOpen = liveMorePanel != null,
                            inputMuted = liveMorePanel != null,
                            holdRepeatEnabled = liveChannelHoldRepeat,
                            onHoldRepeatRelease = onLiveChannelHoldRelease,
                            onHoldCancel = onLiveChannelHoldCancelled,
                            onMore = ::openLiveMorePanel,
                            onLastChannel = { onLastChannel?.invoke() },
                            onPrevious = onPreviousLiveChannel ?: { switchRelative(-1); 0 },
                            onNext = onNextLiveChannel ?: { switchRelative(1); 0 },
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
                        )
                    },
                    panel = { panelMaxHeight, panelModifier ->
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
                                    maxHeight = panelMaxHeight,
                                    modifier = panelModifier,
                                )
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                )
            } else {
                val vodDensity = LocalDensity.current
                val minimumVodPanelHeight = remember(
                    liveControlMetrics.captionSizeSp,
                    vodDensity.fontScale,
                ) {
                    livePlayerMorePanelMinimumHeightDp(
                        captionSizeSp = liveControlMetrics.captionSizeSp,
                        fontScale = vodDensity.fontScale,
                    ).dp
                }
                LiveBottomOverlay(
                    showPanel = vodMorePanel != null,
                    gap = 10.dp,
                    minimumPanelHeight = minimumVodPanelHeight,
                    panelModifier = Modifier.padding(
                        end = liveControlsLayout.outerHorizontalPaddingDp.dp,
                    ),
                    fallbackPanelModifier = Modifier
                        .navigationBarsPadding()
                        .padding(
                            end = liveControlsLayout.outerHorizontalPaddingDp.dp,
                            bottom = liveControlsLayout.outerBottomPaddingDp.dp,
                        ),
                    strip = {
                        VodCompactControlStrip(
                            isPlaying = isPlaying,
                            positionMs = currentPositionMs,
                            durationMs = durationMs,
                            bufferedPercent = bufferedPercent,
                            seekPreviewMs = vodEffectiveSeekMs,
                            favorite = vodFavorite.favorite,
                            favoriteEnabled = vodFavorite.enabled,
                            moreOpen = vodMorePanel != null,
                            inputMuted = vodMorePanel != null || vodGoToTimeVisible,
                            onMore = ::openVodMorePanel,
                            onRewind = { seekBy(-seekStepMs) },
                            onForward = { seekBy(seekStepMs) },
                            onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                            onFavorite = { vodItem?.let(onToggleFavorite) },
                            onPreview = ::previewVodSeek,
                            onCommit = ::commitVodSeek,
                            onPreviewCancel = ::cancelVodSeekPreview,
                            onTimelineFocusChanged = { vodTimelineFocused = it },
                            previousEpisodeCaption = "الحلقة السابقة".takeIf {
                                request.streamKind.equals("series", ignoreCase = true)
                            },
                            previousEpisodeEnabled = previousEpisodeTitle != null &&
                                onPlayPreviousEpisode != null,
                            onPreviousEpisode = onPlayPreviousEpisode,
                            nextEpisodeCaption = "الحلقة التالية".takeIf {
                                request.streamKind.equals("series", ignoreCase = true)
                            },
                            nextEpisodeEnabled = nextEpisodeTitle != null &&
                                onPlayNextEpisode != null,
                            onNextEpisode = onPlayNextEpisode,
                            primaryFocus = primaryFocus,
                            moreTriggerFocus = moreTriggerFocus,
                            seekBarFocusRequester = seekBarFocus,
                            remoteSeekActive = focusTimelineOnReveal,
                            seekStepMs = seekStepMs,
                            layoutMetrics = liveControlsLayout,
                            metrics = liveControlMetrics,
                            topBarBackFocus = topBarBackFocus,
                        )
                    },
                    panel = { panelMaxHeight, panelModifier ->
                        vodMorePanel?.let { panelView ->
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
                                VodMorePanel(
                                    view = panelView,
                                    speed = playbackSpeed,
                                    pictureSizeIndex = resizeModeIndex,
                                    menuFocusRow = vodMoreMenuFocusRow,
                                    focusTick = vodPanelFocusTick,
                                    panelWidthDp = liveControlMetrics.morePanelWidthDp,
                                    inputMuted = vodGoToTimeVisible,
                                    onBackToMenu = ::handleVodMoreBack,
                                    onOpenGoToTime = {
                                        vodMoreMenuFocusRow = VodMoreRow.GO_TO_TIME
                                        vodGoToTimeVisible = true
                                    },
                                    onOpenSpeed = {
                                        vodMoreMenuFocusRow = VodMoreRow.SPEED
                                        vodMorePanel = VodMorePanelView.SPEED
                                    },
                                    onOpenPictureSize = {
                                        vodMoreMenuFocusRow = VodMoreRow.PICTURE_SIZE
                                        vodMorePanel = VodMorePanelView.PICTURE_SIZE
                                    },
                                    onSelectSpeed = { speed ->
                                        applyVodPlaybackSpeed(speed)
                                        closeVodMorePanelForSelection()
                                    },
                                    onSelectPictureSize = { index ->
                                        resizeModeIndex = index
                                        closeVodMorePanelForSelection()
                                    },
                                    onRestart = {
                                        closeVodMorePanelForSelection()
                                        restartVodFromBeginning()
                                    },
                                    onLock = {
                                        controlsLocked = true
                                        controlsVisible = false
                                        vodMorePanel = null
                                    },
                                    maxHeight = panelMaxHeight,
                                    modifier = panelModifier,
                                )
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                )
            }
        }

        if (buffering && finalError == null && !movieModalActive && !resumePromptVisible) {
            LoadingRing(
                label = if (request.isLive) "جاري تشغيل القناة…" else "جاري تجهيز المشاهدة…",
                modifier = Modifier.align(Alignment.Center),
                // Television Live startup keeps a constant sweep; phone and VOD keep the default.
                constantRotation = adaptiveUi.isTelevision && request.isLive,
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

        // The Live error card derives truthful Live wording and the real connectivity glyph from
        // typed state; non-Live callers keep their existing finalError message unchanged.
        val liveErrorPresentation = if (request.isLive) {
            finalError?.let { message ->
                livePlayerErrorPresentation(
                    offlineFailure = offlineFailure,
                    playbackStarted = livePlaybackStarted,
                    autoResumeIntent = offlineWasPlaying,
                    failureMessage = message,
                )
            }
        } else {
            null
        }

        when {
            movieErrorActive -> MoviePlayerErrorCard(
                offline = movieErrorCardOffline,
                resumePending = resumePromptVisible,
                savedPositionMs = request.resumePositionMs,
                failureMessage = finalError,
                mediaLabel = vodErrorMediaLabel,
                onRetry = {
                    // While connectivity is unusable the same card stays; otherwise the generic
                    // failure uses the existing manual retry owner.
                    if (movieErrorCardOffline) {
                        if (hasUsableNetwork(context)) {
                            networkAvailable = true
                        }
                    } else {
                        retryManually()
                    }
                },
                onBack = ::saveAndBack,
                retryFocusRequester = offlineRetryFocus,
                backFocusRequester = offlineBackFocus,
                modifier = Modifier.align(Alignment.Center),
            )
            resumePromptVisible -> ResumePrompt(
                title = playerDisplayTitle,
                positionMs = request.resumePositionMs,
                durationMs = durationMs,
                identity = if (
                    request.streamKind.equals("series", ignoreCase = true) &&
                    request.season != null &&
                    request.episodeNumber != null
                ) {
                    "الموسم ${request.season} • الحلقة ${request.episodeNumber}"
                } else {
                    null
                },
                onResume = {
                    vodProgressPersistenceBlocked = vodProgressPersistenceBlockedAfterDecision(
                        blocked = vodProgressPersistenceBlocked,
                        decisionAccepted = true,
                    )
                    player.seekTo(request.resumePositionMs)
                    currentPositionMs = request.resumePositionMs
                    resumePromptVisible = false
                    controlsVisible = true
                    player.play()
                },
                onRestart = {
                    vodProgressPersistenceBlocked = vodProgressPersistenceBlockedAfterDecision(
                        blocked = vodProgressPersistenceBlocked,
                        decisionAccepted = true,
                    )
                    player.seekTo(0L)
                    currentPositionMs = 0L
                    resumePromptVisible = false
                    controlsVisible = true
                    player.play()
                },
                onBack = {
                    resumePromptVisible = false
                    controlsVisible = false
                    player.pause()
                    onBack()
                },
                focusRequester = resumeFocus,
                restartFocusRequester = resumeRestartFocus,
                backFocusRequester = resumeBackFocus,
                modifier = Modifier.align(Alignment.Center),
            )
            liveErrorForegroundActive -> PlayerErrorPanel(
                title = liveErrorPresentation?.title,
                message = liveErrorPresentation?.body ?: finalError!!,
                networkFailure = liveErrorPresentation?.offline == true,
                canChooseChannel = request.isLive && liveCatalog?.items?.isNotEmpty() == true,
                canChooseServer = canOfferPlayerErrorSourcePicker(
                    failureClass = finalFailureClass,
                    candidateCount = request.candidates.size,
                ),
                onRetry = { retryManually() },
                onChooseChannel = {
                    // Live keeps the failure state live and suppresses only its foreground
                    // presentation, so repeated offline notifications cannot re-open the modal
                    // behind the browser and the first Back can restore a still-valid error.
                    liveErrorSurfaceSuppressed = true
                    controlsVisible = false
                    browserOrigin = LiveChannelBrowserOrigin.ERROR_RECOVERY
                    browserVisible = true
                },
                onChooseServer = {
                    if (request.isLive) {
                        liveErrorSurfaceSuppressed = true
                        activePanel = PlayerPanel.SERVERS
                    } else {
                        suspendFinalErrorForModal()
                        activePanel = PlayerPanel.SERVERS
                    }
                },
                onBack = ::saveAndBack,
                retryFocusRequester = errorRetryFocus,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (
            nextCountdown >= 0 &&
            nextEpisodeTitle != null &&
            onPlayNextEpisode != null &&
            !autoplayCountdownBlocked
        ) {
            NextEpisodePrompt(
                title = nextEpisodeTitle,
                seconds = nextCountdown,
                playFocusRequester = nextEpisodePlayFocus,
                cancelFocusRequester = nextEpisodeCancelFocus,
                onPlayNow = {
                    // Guarded so a press racing the final tick can never advance twice.
                    if (nextCountdown >= 0) {
                        nextCountdown = -1
                        saveAndPlayNext()
                    }
                },
                onCancel = { nextCountdown = -1 },
                modifier = Modifier.align(Alignment.Center),
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
                    // The real selection dispatches once and invalidates the old error state, so
                    // the replaced request can never flash the old failure or restore a snapshot;
                    // the replacement request owns its own failure presentation if it fails.
                    clearFinalErrorState()
                    liveErrorSurfaceSuppressed = false
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

        if (vodGoToTimeVisible && !request.isLive) {
            VodGoToTimeDialog(
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                durationLabel = if (request.streamKind.equals("series", ignoreCase = true)) {
                    "مدة الحلقة"
                } else {
                    "مدة الفلم"
                },
                onConfirm = { target ->
                    vodGoToTimeVisible = false
                    closeVodMorePanelForSelection()
                    seekToPosition(target)
                },
                onDismiss = {
                    vodGoToTimeVisible = false
                    vodPanelFocusTick += 1
                },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (request.isLive && activePanel == PlayerPanel.SERVERS) {
            BoxWithConstraints(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                val liveErrorSourcePanelMaxHeight =
                    (maxHeight - liveControlsLayout.outerBottomPaddingDp.dp - 16.dp).coerceAtLeast(0.dp)
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
                            // The selected source supersedes the suspended error surface; the new
                            // prepare owns any new failure presentation on its own.
                            activePanel = null
                            liveErrorSurfaceSuppressed = false
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
    backFocusRequester: FocusRequester? = null,
    backDownFocus: FocusRequester? = null,
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
        FocusButton(
            "رجوع",
            onBack,
            primary = false,
            outlined = true,
            compact = true,
            modifier = Modifier
                .then(if (backFocusRequester != null) Modifier.focusRequester(backFocusRequester) else Modifier)
                .then(
                    if (backDownFocus != null) {
                        Modifier.focusProperties {
                            down = backDownFocus
                            up = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        }
                    } else {
                        Modifier
                    },
                ),
        )
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
                    color = if (isLive) Color(0xFFFF4E55) else colors.gold,
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

/**
 * Owner-approved Movie player tools strip.
 *
 * Compact Live-inspired organization: a small glyph above a complete caption, fine separators and
 * compact pale-gold focus hitboxes around the glyph. The five approved tools keep their physical
 * left-to-right order. One row is used only when the measured complete captions fit; otherwise the
 * same tools reflow into a bounded two-row arrangement. Captions are never ellipsized or shrunk.
 *
 * Bounded Series exception (owner direction 2026-10-08-R02): a Series episode additionally shows
 * directly visible `الحلقة السابقة` / `الحلقة التالية` episode tools at the outer ends, wired to
 * the authoritative neighbors and disabled (and skipped in navigation) when unavailable. Movies
 * always render exactly the five approved tools.
 *
 * While the More panel owns input the strip stays visible but every tool and the timeline are
 * input-muted, so no playback/seek action can fire underneath the open panel.
 */
@Composable
private fun VodCompactControlStrip(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedPercent: Int,
    seekPreviewMs: Long?,
    favorite: Boolean,
    favoriteEnabled: Boolean,
    moreOpen: Boolean,
    inputMuted: Boolean,
    onMore: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onPlayPause: () -> Unit,
    onFavorite: () -> Unit,
    onPreview: (Long) -> Unit,
    onCommit: (Long) -> Unit,
    onPreviewCancel: () -> Unit,
    onTimelineFocusChanged: (Boolean) -> Unit = {},
    previousEpisodeCaption: String? = null,
    previousEpisodeEnabled: Boolean = false,
    onPreviousEpisode: (() -> Unit)? = null,
    nextEpisodeCaption: String? = null,
    nextEpisodeEnabled: Boolean = false,
    onNextEpisode: (() -> Unit)? = null,
    primaryFocus: FocusRequester,
    moreTriggerFocus: FocusRequester,
    seekBarFocusRequester: FocusRequester,
    remoteSeekActive: Boolean,
    seekStepMs: Long,
    layoutMetrics: LiveControlsLayoutMetrics,
    metrics: LivePlayerControlsMetrics,
    topBarBackFocus: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val isTelevision = adaptiveUi.isTelevision
    val stepSeconds = (seekStepMs / 1_000L).coerceAtLeast(1L)
    val rewindFocus = remember { FocusRequester() }
    val forwardFocus = remember { FocusRequester() }
    val favoriteFocus = remember { FocusRequester() }
    val previousEpisodeFocus = remember { FocusRequester() }
    val nextEpisodeFocus = remember { FocusRequester() }
    var stripBounds by remember { mutableStateOf<Rect?>(null) }
    var timelineRowBounds by remember { mutableStateOf<Rect?>(null) }
    var toolsRowBounds by remember { mutableStateOf<Rect?>(null) }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val baseTextStyle = LocalTextStyle.current
    val captionStyle = remember(metrics.captionSizeSp, baseTextStyle) {
        baseTextStyle.copy(fontSize = metrics.captionSizeSp.sp, fontWeight = FontWeight.Bold)
    }
    val rewindCaption = remember(stepSeconds) { "رجوع $stepSeconds ث" }
    val forwardCaption = remember(stepSeconds) { "تقديم $stepSeconds ث" }
    val playPauseCaption = if (isPlaying) "ايقاف مؤقت" else "تشغيل"
    // Slight Movies-only glyph increase derived from the current adaptive metrics, with the gap
    // that the rendered row actually uses. Fit and render share both expressions.
    val movieGlyphDp = (metrics.transportIconDp * 1.2f).roundToInt().coerceIn(22, 34)
    val toolSpacing = vodToolSpacingDp(
        movieGlyphDp = movieGlyphDp,
        itemSpacingDp = metrics.itemSpacingDp,
    ).dp
    // Measured vertical gap between the timeline slot and the tool row (existing 8.dp source
    // baseline + thumb clearance). Owned by the strip Column, so the rows cannot overlap.
    val toolGap = (metrics.itemSpacingDp + 4).dp
    // Real lower boundary: the qualified TV overscan inset, without the extra Live presentation
    // buffer. Touch layouts consume the system navigation inset once via navigationBarsPadding().
    val safeBottomInsetDp = remember(
        adaptiveUi.screenWidthDp,
        adaptiveUi.screenHeightDp,
        isTelevision,
    ) {
        if (isTelevision) {
            tvPremiumWindowPolicy(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp).verticalSafeInsetDp
        } else {
            0f
        }
    }
    // The same lift moves into the top padding, so the strip keeps its Live-base measured height
    // and the gradient box grows above the timeline while the group shifts down as one group.
    val stripBottomPaddingDp = vodStripBottomPaddingDp(
        baseBottomDp = layoutMetrics.outerBottomPaddingDp,
        safeBottomInsetDp = safeBottomInsetDp,
        isTelevision = isTelevision,
    )
    val stripTopPaddingDp =
        layoutMetrics.outerTopPaddingDp + (layoutMetrics.outerBottomPaddingDp - stripBottomPaddingDp)
    // Gradient stops from the real measured row bounds; the even .50/.88 fallback applies only
    // before the first measurement pass.
    val gradientStops = remember(stripBounds, timelineRowBounds, toolsRowBounds) {
        vodStripGradientStops(
            stripTopPx = stripBounds?.top ?: -1f,
            stripHeightPx = stripBounds?.height ?: 0f,
            timelineTopPx = timelineRowBounds?.top ?: -1f,
            toolsTopPx = toolsRowBounds?.top ?: -1f,
        )
    }
    val requiredToolsWidthPx = remember(
        movieGlyphDp,
        metrics.captionSizeSp,
        metrics.itemSpacingDp,
        toolSpacing,
        rewindCaption,
        forwardCaption,
        playPauseCaption,
        previousEpisodeCaption,
        nextEpisodeCaption,
        density.fontScale,
        density.density,
        textMeasurer,
    ) {
        val glyphBoxPx = with(density) { (movieGlyphDp + 22).dp.roundToPx() }
        val spacingPx = with(density) { toolSpacing.roundToPx() }
        // Physical LEFT-to-RIGHT order: More, Previous, Rewind, Play/Pause, Forward, Next,
        // Favorite; episode tools only exist for Series.
        val captions = buildList {
            add("المزيد")
            previousEpisodeCaption?.let(::add)
            add(rewindCaption)
            add(playPauseCaption)
            add(forwardCaption)
            nextEpisodeCaption?.let(::add)
            add("المفضلة")
        }
        vodToolsRequiredWidthPx(
            iconBoxesPx = List(captions.size) { glyphBoxPx },
            captionWidthsPx = captions.map { caption ->
                textMeasurer.measure(caption, captionStyle).size.width
            },
            spacingPx = spacingPx,
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { stripBounds = it.boundsInRoot() }
            .background(
                gradientStops?.let { stops ->
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        stops.timelineTopFraction to Color.Black.copy(alpha = .50f),
                        stops.toolsTopFraction to Color.Black.copy(alpha = .88f),
                    )
                } ?: Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = .50f),
                        Color.Black.copy(alpha = .88f),
                    ),
                ),
            )
            .navigationBarsPadding()
            .padding(
                start = layoutMetrics.outerHorizontalPaddingDp.dp,
                end = layoutMetrics.outerHorizontalPaddingDp.dp,
                top = stripTopPaddingDp.dp,
                bottom = stripBottomPaddingDp.dp,
            ),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val weightedTools = vodToolsUseWeightedSlots(
                availableWidthPx = with(density) { maxWidth.roundToPx() },
                requiredWidthPx = requiredToolsWidthPx,
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { timelineRowBounds = it.boundsInRoot() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                    Text(formatTime(positionMs), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                    ) {
                        VodSeekBar(
                            positionMs = positionMs,
                            durationMs = durationMs,
                            buffered = bufferedPercent / 100f,
                            previewMs = seekPreviewMs,
                            onPreview = onPreview,
                            onCommit = onCommit,
                            onPreviewCancel = onPreviewCancel,
                            onTimelineFocusChanged = onTimelineFocusChanged,
                            focusRequester = seekBarFocusRequester,
                            remoteActive = remoteSeekActive,
                            seekStepMs = seekStepMs,
                            inputEnabled = !inputMuted,
                            upFocus = topBarBackFocus ?: FocusRequester.Cancel,
                            downFocus = primaryFocus,
                            stableLayout = true,
                            moviePresentation = true,
                        )
                    }
                    Text(
                        "-${formatTime((durationMs - positionMs).coerceAtLeast(0L))}",
                        color = colors.textMuted,
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(toolGap))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { toolsRowBounds = it.boundsInRoot() },
                    horizontalArrangement = if (weightedTools) {
                        Arrangement.Start
                    } else {
                        Arrangement.spacedBy(toolSpacing, Alignment.CenterHorizontally)
                    },
                    verticalAlignment = Alignment.Top,
                ) {
                    VodCompactControl(
                        icon = Icons.Rounded.MoreHoriz,
                        caption = "المزيد",
                        onClick = onMore,
                        enabled = true,
                        inputMuted = inputMuted,
                        iconSizeDp = movieGlyphDp,
                        captionSizeSp = metrics.captionSizeSp,
                        modifier = if (weightedTools) Modifier.weight(1f) else Modifier,
                        selected = moreOpen,
                        focusRequester = moreTriggerFocus,
                        upFocus = seekBarFocusRequester,
                        downFocus = FocusRequester.Cancel,
                    )
                    previousEpisodeCaption?.let { caption ->
                        VodCompactControl(
                            icon = Icons.Rounded.SkipPrevious,
                            caption = caption,
                            onClick = { onPreviousEpisode?.invoke() },
                            enabled = previousEpisodeEnabled && onPreviousEpisode != null,
                            inputMuted = inputMuted,
                            iconSizeDp = movieGlyphDp,
                            captionSizeSp = metrics.captionSizeSp,
                            modifier = if (weightedTools) Modifier.weight(1f) else Modifier,
                            focusRequester = previousEpisodeFocus,
                            upFocus = seekBarFocusRequester,
                            downFocus = FocusRequester.Cancel,
                        )
                    }
                    VodCompactControl(
                        icon = MovieRewind10Icon,
                        caption = rewindCaption,
                        onClick = onRewind,
                        enabled = true,
                        inputMuted = inputMuted,
                        iconSizeDp = movieGlyphDp,
                        captionSizeSp = metrics.captionSizeSp,
                        modifier = if (weightedTools) Modifier.weight(1f) else Modifier,
                        focusRequester = rewindFocus,
                        upFocus = seekBarFocusRequester,
                        downFocus = FocusRequester.Cancel,
                    )
                    VodCompactControl(
                        icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        caption = playPauseCaption,
                        onClick = onPlayPause,
                        enabled = true,
                        inputMuted = inputMuted,
                        iconSizeDp = movieGlyphDp,
                        captionSizeSp = metrics.captionSizeSp,
                        modifier = if (weightedTools) Modifier.weight(1f) else Modifier,
                        primary = true,
                        focusRequester = primaryFocus,
                        upFocus = seekBarFocusRequester,
                        downFocus = FocusRequester.Cancel,
                    )
                    VodCompactControl(
                        icon = MovieForward10Icon,
                        caption = forwardCaption,
                        onClick = onForward,
                        enabled = true,
                        inputMuted = inputMuted,
                        iconSizeDp = movieGlyphDp,
                        captionSizeSp = metrics.captionSizeSp,
                        modifier = if (weightedTools) Modifier.weight(1f) else Modifier,
                        focusRequester = forwardFocus,
                        upFocus = seekBarFocusRequester,
                        downFocus = FocusRequester.Cancel,
                    )
                    nextEpisodeCaption?.let { caption ->
                        VodCompactControl(
                            icon = Icons.Rounded.SkipNext,
                            caption = caption,
                            onClick = { onNextEpisode?.invoke() },
                            enabled = nextEpisodeEnabled && onNextEpisode != null,
                            inputMuted = inputMuted,
                            iconSizeDp = movieGlyphDp,
                            captionSizeSp = metrics.captionSizeSp,
                            modifier = if (weightedTools) Modifier.weight(1f) else Modifier,
                            focusRequester = nextEpisodeFocus,
                            upFocus = seekBarFocusRequester,
                            downFocus = FocusRequester.Cancel,
                        )
                    }
                    VodCompactControl(
                        icon = if (favorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                        caption = "المفضلة",
                        onClick = onFavorite,
                        enabled = favoriteEnabled,
                        inputMuted = inputMuted,
                        iconSizeDp = movieGlyphDp,
                        captionSizeSp = metrics.captionSizeSp,
                        modifier = if (weightedTools) Modifier.weight(1f) else Modifier,
                        selected = favorite,
                        focusRequester = favoriteFocus,
                        upFocus = seekBarFocusRequester,
                        downFocus = FocusRequester.Cancel,
                    )
                }
                }
            }
        }
    }
}

/**
 * One compact Movie tool: glyph above a complete caption. Normal tools are plain ivory glyphs with
 * a thin pale-gold focus edge and subtle backing; the Play/Pause tool keeps the reference
 * solid-gold primary disc. Focus never changes the measured size or position.
 */
@Composable
private fun VodCompactControl(
    icon: ImageVector,
    caption: String,
    onClick: () -> Unit,
    enabled: Boolean,
    inputMuted: Boolean,
    iconSizeDp: Int,
    captionSizeSp: Int,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
    upFocus: FocusRequester? = null,
    downFocus: FocusRequester? = null,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember { mutableStateOf(false) }
    val showFocused = focused && adaptiveUi.showFocusHighlights && !inputMuted
    val shape = CircleShape
    val glyphColor = when {
        !enabled -> colors.textMuted.copy(alpha = .45f)
        primary -> MaterialTheme.colorScheme.onPrimary
        showFocused -> colors.goldBright
        selected -> colors.gold
        else -> colors.text
    }
    val captionColor = when {
        !enabled -> colors.textMuted.copy(alpha = .45f)
        showFocused -> colors.goldBright
        selected -> colors.gold
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
                .background(
                    when {
                        primary -> colors.gold
                        showFocused -> colors.gold.copy(alpha = .16f)
                        else -> Color.Transparent
                    },
                )
                .border(
                    width = if (showFocused) 2.dp else 0.dp,
                    color = if (showFocused) colors.goldBright else Color.Transparent,
                    shape = shape,
                )
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .focusProperties {
                    canFocus = enabled && !inputMuted
                    upFocus?.let { up = it }
                    downFocus?.let { down = it }
                }
                .clickable(enabled = enabled && !inputMuted, role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = caption },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = glyphColor,
                modifier = Modifier.size(iconSizeDp.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = caption,
            color = captionColor,
            fontSize = captionSizeSp.sp,
            // Constant weight: focus/selection never changes intrinsic widths or the row fit.
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
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

private enum class LiveBottomOverlaySlot { STRIP, PANEL, FALLBACK }

/**
 * Same-pass Live bottom overlay.
 *
 * The real strip is measured first at its complete height. If the genuine remainder can still hold
 * the panel's minimum usable height, the accepted arrangement places the panel above the strip.
 * Otherwise the bounded fallback places the panel over the full safe height and omits the strip
 * (it returns when the panel closes); the strip is never overlapped or compressed in either mode.
 */
@Composable
private fun LiveBottomOverlay(
    showPanel: Boolean,
    gap: Dp,
    minimumPanelHeight: Dp,
    strip: @Composable () -> Unit,
    panel: @Composable (maxHeight: Dp, modifier: Modifier) -> Unit,
    modifier: Modifier = Modifier,
    panelModifier: Modifier = Modifier,
    fallbackPanelModifier: Modifier = Modifier,
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val gapPx = gap.roundToPx()
        val minimumPanelPx = minimumPanelHeight.roundToPx()
        val stripPlaceable = subcompose(LiveBottomOverlaySlot.STRIP) { strip() }
            .firstOrNull()
            ?.measure(constraints)
        val boundedHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
        if (stripPlaceable == null) {
            return@SubcomposeLayout layout(0, 0) { }
        }
        if (!showPanel) {
            return@SubcomposeLayout layout(constraints.maxWidth, stripPlaceable.height) {
                stripPlaceable.place(0, 0)
            }
        }
        val remainderPx = (boundedHeight - stripPlaceable.height - gapPx).coerceAtLeast(0)
        when (liveBottomOverlayMode(remainderPx, minimumPanelPx)) {
            LiveBottomOverlayMode.NORMAL -> {
                val panelPlaceable = subcompose(LiveBottomOverlaySlot.PANEL) {
                    panel(remainderPx.toDp(), panelModifier)
                }.firstOrNull()?.measure(
                    Constraints(maxWidth = constraints.maxWidth, maxHeight = remainderPx),
                )
                if (panelPlaceable == null) {
                    layout(constraints.maxWidth, stripPlaceable.height) {
                        stripPlaceable.place(0, 0)
                    }
                } else {
                    layout(
                        constraints.maxWidth,
                        panelPlaceable.height + gapPx + stripPlaceable.height,
                    ) {
                        panelPlaceable.place(0, 0)
                        stripPlaceable.place(0, panelPlaceable.height + gapPx)
                    }
                }
            }
            LiveBottomOverlayMode.FALLBACK -> {
                val panelPlaceable = subcompose(LiveBottomOverlaySlot.FALLBACK) {
                    panel(boundedHeight.toDp(), fallbackPanelModifier)
                }.firstOrNull()?.measure(
                    Constraints(maxWidth = constraints.maxWidth, maxHeight = boundedHeight),
                )
                if (panelPlaceable == null) {
                    layout(constraints.maxWidth, 0) { }
                } else {
                    layout(constraints.maxWidth, panelPlaceable.height) {
                        panelPlaceable.place(0, 0)
                    }
                }
            }
        }
    }
}

@Composable
private fun LivePlayerControls(
    isPlaying: Boolean,
    favorite: Boolean,
    favoriteEnabled: Boolean,
    lastChannelEnabled: Boolean,
    moreOpen: Boolean,
    inputMuted: Boolean,
    holdRepeatEnabled: Boolean = false,
    onHoldRepeatRelease: (() -> Unit)? = null,
    onHoldCancel: ((Int) -> Unit)? = null,
    onMore: () -> Unit,
    onLastChannel: () -> Unit,
    onPrevious: () -> Int,
    onNext: () -> Int,
    onPlayPause: () -> Unit,
    onFavorite: () -> Unit,
    onChannels: () -> Unit,
    primaryFocus: FocusRequester,
    moreTriggerFocus: FocusRequester,
    layoutMetrics: LiveControlsLayoutMetrics,
    metrics: LivePlayerControlsMetrics,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val baseTextStyle = LocalTextStyle.current
    val captionStyle = remember(metrics.captionSizeSp, baseTextStyle) {
        baseTextStyle.copy(fontSize = metrics.captionSizeSp.sp, fontWeight = FontWeight.Bold)
    }
    val playPauseCaption = if (isPlaying) "ايقاف مؤقت" else "تشغيل"
    val toolGlyphDp = metrics.transportIconDp
    val toolSpacing = vodToolSpacingDp(
        movieGlyphDp = toolGlyphDp,
        itemSpacingDp = metrics.itemSpacingDp,
    ).dp
    var stripBounds by remember { mutableStateOf<Rect?>(null) }
    var toolsBounds by remember { mutableStateOf<Rect?>(null) }
    // Restrained measured gradient: the transparent-to-strong ramp is anchored on the real tool-row
    // position inside the measured strip, so it is never an arbitrary fixed stop.
    val gradientStopFraction = remember(stripBounds, toolsBounds) {
        val stripTop = stripBounds?.top ?: return@remember null
        val stripHeight = stripBounds?.height ?: return@remember null
        val toolsTop = toolsBounds?.top ?: return@remember null
        liveStripGradientStopFraction(
            stripHeightPx = stripHeight,
            toolsTopPx = toolsTop - stripTop,
        )
    }
    // Physical LEFT-to-RIGHT caption order of the seven Live tools; the list membership (not the
    // width alone) is also what the measured single-row fit consumes.
    val toolCaptions = remember(playPauseCaption) {
        listOf(
            "المزيد",
            "اخر قناة",
            "القناة التالية",
            playPauseCaption,
            "القناة السابقة",
            "المفضلة",
            "القنوات",
        )
    }
    val requiredSingleRowWidthPx = remember(
        toolGlyphDp,
        metrics.captionSizeSp,
        metrics.itemSpacingDp,
        toolSpacing,
        toolCaptions,
        density.fontScale,
        density.density,
        textMeasurer,
    ) {
        val glyphBoxPx = with(density) { (toolGlyphDp + 22).dp.roundToPx() }
        vodToolsRequiredWidthPx(
            iconBoxesPx = List(toolCaptions.size) { glyphBoxPx },
            captionWidthsPx = toolCaptions.map { caption ->
                textMeasurer.measure(caption, captionStyle).size.width
            },
            spacingPx = with(density) { toolSpacing.roundToPx() },
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { stripBounds = it.boundsInRoot() }
            .background(
                gradientStopFraction?.let { fraction ->
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        fraction to Color.Black.copy(alpha = .50f),
                        1f to Color.Black.copy(alpha = .88f),
                    )
                } ?: Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = .50f),
                        Color.Black.copy(alpha = .88f),
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
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val rowMode = liveControlsRowMode(
                availableWidthPx = with(density) { maxWidth.roundToPx() },
                requiredSingleRowWidthPx = requiredSingleRowWidthPx,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { toolsBounds = it.boundsInRoot() },
            ) {
                when (rowMode) {
                    // Accepted wide arrangement. The row is composed in RTL, so the first child is
                    // physically RIGHT; composing the reverse of the required physical
                    // LEFT-to-RIGHT order keeps المزيد at physical LEFT and القنوات at physical
                    // RIGHT with the Play/Pause tool centered.
                    LiveControlsRowMode.SINGLE_ROW -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            toolSpacing,
                            Alignment.CenterHorizontally,
                        ),
                        verticalAlignment = Alignment.Top,
                    ) {
                        LiveControlUtility(
                            icon = Icons.AutoMirrored.Rounded.List,
                            caption = "القنوات",
                            onClick = onChannels,
                            enabled = true,
                            inputMuted = inputMuted,
                            iconSizeDp = toolGlyphDp,
                            captionSizeSp = metrics.captionSizeSp,
                        )
                        LiveControlUtility(
                            icon = if (favorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                            caption = "المفضلة",
                            onClick = onFavorite,
                            enabled = favoriteEnabled,
                            inputMuted = inputMuted,
                            selected = favorite,
                            iconSizeDp = toolGlyphDp,
                            captionSizeSp = metrics.captionSizeSp,
                        )
                        LiveControlUtility(
                            icon = Icons.Rounded.SkipPrevious,
                            caption = "القناة السابقة",
                            holdRepeat = holdRepeatEnabled,
                            onHoldRelease = onHoldRepeatRelease,
                            onHoldCancel = onHoldCancel,
                            onHoldRepeat = onPrevious,
                            onClick = { onPrevious() },
                            enabled = true,
                            inputMuted = inputMuted,
                            iconSizeDp = toolGlyphDp,
                            captionSizeSp = metrics.captionSizeSp,
                        )
                        LiveControlUtility(
                            icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            caption = playPauseCaption,
                            onClick = onPlayPause,
                            enabled = true,
                            inputMuted = inputMuted,
                            iconSizeDp = toolGlyphDp,
                            captionSizeSp = metrics.captionSizeSp,
                            primary = true,
                            focusRequester = primaryFocus,
                        )
                        LiveControlUtility(
                            icon = Icons.Rounded.SkipNext,
                            caption = "القناة التالية",
                            holdRepeat = holdRepeatEnabled,
                            onHoldRelease = onHoldRepeatRelease,
                            onHoldCancel = onHoldCancel,
                            onHoldRepeat = onNext,
                            onClick = { onNext() },
                            enabled = true,
                            inputMuted = inputMuted,
                            iconSizeDp = toolGlyphDp,
                            captionSizeSp = metrics.captionSizeSp,
                        )
                        LiveControlUtility(
                            icon = Icons.Rounded.History,
                            caption = "اخر قناة",
                            onClick = onLastChannel,
                            enabled = lastChannelEnabled,
                            inputMuted = inputMuted,
                            iconSizeDp = toolGlyphDp,
                            captionSizeSp = metrics.captionSizeSp,
                        )
                        LiveControlUtility(
                            icon = Icons.Rounded.MoreHoriz,
                            caption = "المزيد",
                            onClick = onMore,
                            enabled = true,
                            inputMuted = inputMuted,
                            selected = moreOpen,
                            iconSizeDp = toolGlyphDp,
                            captionSizeSp = metrics.captionSizeSp,
                            focusRequester = moreTriggerFocus,
                        )
                    }
                    // Existing narrow-window grouping and logical order, with full captions
                    // sharing their row width instead of truncating or shrinking.
                    LiveControlsRowMode.TWO_ROWS -> Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(metrics.itemSpacingDp.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            LiveControlUtility(
                                icon = Icons.Rounded.SkipPrevious,
                                caption = "القناة السابقة",
                                holdRepeat = holdRepeatEnabled,
                                onHoldRelease = onHoldRepeatRelease,
                                onHoldCancel = onHoldCancel,
                                onHoldRepeat = onPrevious,
                                onClick = { onPrevious() },
                                enabled = true,
                                inputMuted = inputMuted,
                                iconSizeDp = toolGlyphDp,
                                captionSizeSp = metrics.captionSizeSp,
                                modifier = Modifier.weight(1f),
                            )
                            LiveControlUtility(
                                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                caption = playPauseCaption,
                                onClick = onPlayPause,
                                enabled = true,
                                inputMuted = inputMuted,
                                iconSizeDp = toolGlyphDp,
                                captionSizeSp = metrics.captionSizeSp,
                                primary = true,
                                focusRequester = primaryFocus,
                                modifier = Modifier.weight(1f),
                            )
                            LiveControlUtility(
                                icon = Icons.Rounded.SkipNext,
                                caption = "القناة التالية",
                                holdRepeat = holdRepeatEnabled,
                                onHoldRelease = onHoldRepeatRelease,
                                onHoldCancel = onHoldCancel,
                                onHoldRepeat = onNext,
                                onClick = { onNext() },
                                enabled = true,
                                inputMuted = inputMuted,
                                iconSizeDp = toolGlyphDp,
                                captionSizeSp = metrics.captionSizeSp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(metrics.itemSpacingDp.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(metrics.itemSpacingDp.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            LiveControlUtility(
                                icon = Icons.AutoMirrored.Rounded.List,
                                caption = "القنوات",
                                onClick = onChannels,
                                enabled = true,
                                inputMuted = inputMuted,
                                iconSizeDp = toolGlyphDp,
                                captionSizeSp = metrics.captionSizeSp,
                                modifier = Modifier.weight(1f),
                            )
                            LiveControlUtility(
                                icon = if (favorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                                caption = "المفضلة",
                                onClick = onFavorite,
                                enabled = favoriteEnabled,
                                inputMuted = inputMuted,
                                selected = favorite,
                                iconSizeDp = toolGlyphDp,
                                captionSizeSp = metrics.captionSizeSp,
                                modifier = Modifier.weight(1f),
                            )
                            LiveControlUtility(
                                icon = Icons.Rounded.History,
                                caption = "اخر قناة",
                                onClick = onLastChannel,
                                enabled = lastChannelEnabled,
                                inputMuted = inputMuted,
                                iconSizeDp = toolGlyphDp,
                                captionSizeSp = metrics.captionSizeSp,
                                modifier = Modifier.weight(1f),
                            )
                            LiveControlUtility(
                                icon = Icons.Rounded.MoreHoriz,
                                caption = "المزيد",
                                onClick = onMore,
                                enabled = true,
                                inputMuted = inputMuted,
                                selected = moreOpen,
                                iconSizeDp = toolGlyphDp,
                                captionSizeSp = metrics.captionSizeSp,
                                focusRequester = moreTriggerFocus,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One compact Live tool: glyph above a complete caption. Adopts the accepted VOD compact control
 * appearance: plain ivory glyphs with a thin pale-gold focus edge and subtle circle backing; the
 * Play/Pause tool keeps the solid-gold primary circle with the dark glyph. The fixed caption weight
 * and the absence of an ellipsis keep focus/selection/favorite changes from moving the row.
 */
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
    primary: Boolean = false,
    inputMuted: Boolean = false,
    holdRepeat: Boolean = false,
    onHoldRelease: (() -> Unit)? = null,
    onHoldRepeat: (() -> Int)? = null,
    onHoldCancel: ((Int) -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember { mutableStateOf(false) }
    // Touch press-and-hold repeat for the Live previous/next tools. A quick press stays a tap;
    // a completed hold repeats without adding an unintended release click. The repeat stops on
    // release, cancellation, eligibility loss or leaving the screen (effect/gesture disposal).
    var controlPressed by remember { mutableStateOf(false) }
    var holdRepeatActive by remember { mutableStateOf(false) }
    var holdEngaged by remember { mutableStateOf(false) }
    var lastHoldToken by remember { mutableIntStateOf(-1) }
    // The hold loop outlives several request replacements; it must always call the latest
    // navigation callback instead of the one captured at press-down.
    val latestOnClick by rememberUpdatedState(onClick)
    val latestOnHoldRelease by rememberUpdatedState(onHoldRelease)
    val latestOnHoldCancel by rememberUpdatedState(onHoldCancel)
    // The repeat token callback must also stay current across request replacements.
    val latestOnHoldRepeat by rememberUpdatedState(onHoldRepeat)
    val holdLongPressTimeoutMs = LocalViewConfiguration.current.longPressTimeoutMillis
    if (holdRepeat) {
        LaunchedEffect(controlPressed, enabled, inputMuted, holdRepeat) {
            if (!controlPressed || !enabled || inputMuted) return@LaunchedEffect
            delay(holdLongPressTimeoutMs)
            holdRepeatActive = true
            holdEngaged = true
            while (true) {
                lastHoldToken = latestOnHoldRepeat?.invoke() ?: -1
                if (latestOnHoldRepeat == null) latestOnClick()
                delay(LIVE_CONTROL_HOLD_REPEAT_MS)
            }
        }
    }
    val showFocused = focused && adaptiveUi.showFocusHighlights && !inputMuted
    val shape = CircleShape
    val glyphColor = when {
        !enabled -> colors.textMuted.copy(alpha = .45f)
        primary -> MaterialTheme.colorScheme.onPrimary
        showFocused -> colors.goldBright
        selected -> colors.gold
        else -> colors.text
    }
    val captionColor = when {
        !enabled -> colors.textMuted.copy(alpha = .45f)
        showFocused -> colors.goldBright
        selected -> colors.gold
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
                .background(
                    when {
                        primary -> colors.gold
                        showFocused -> colors.gold.copy(alpha = .16f)
                        else -> Color.Transparent
                    },
                )
                .border(
                    width = if (showFocused) 2.dp else 0.dp,
                    color = if (showFocused) colors.goldBright else Color.Transparent,
                    shape = shape,
                )
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .focusProperties { canFocus = enabled && !inputMuted }
                .onFocusChanged { focused = it.isFocused }
                .then(
                    if (holdRepeat) {
                        Modifier.pointerInput(enabled, inputMuted) {
                            if (!enabled || inputMuted) return@pointerInput
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                holdRepeatActive = false
                                holdEngaged = false
                                controlPressed = true
                                var cancelled = true
                                try {
                                    if (waitForUpOrCancellation() != null) cancelled = false
                                } finally {
                                    // Cancellation, eligibility loss or disposal must always clean the
                                    // pressed state and must never flush pending hold work or swallow
                                    // the next tap/remote OK.
                                    controlPressed = false
                                    if (cancelled) {
                                        if (holdEngaged) latestOnHoldCancel?.invoke(lastHoldToken)
                                        holdRepeatActive = false
                                        holdEngaged = false
                                    }
                                }
                                // Only a valid release settles once; the clickable release handler then
                                // consumes holdRepeatActive so no extra click is delivered.
                                if (!cancelled && holdEngaged) latestOnHoldRelease?.invoke()
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                .clickable(enabled = enabled && !inputMuted, role = Role.Button) {
                    // Suppress the click that the pointer-up would otherwise deliver after a hold.
                    if (holdRepeatActive) holdRepeatActive = false else onClick()
                }
                .semantics(mergeDescendants = true) { contentDescription = caption },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = glyphColor,
                modifier = Modifier.size(iconSizeDp.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = caption,
            color = captionColor,
            fontSize = captionSizeSp.sp,
            // Constant weight: focus/selection/favorite never changes intrinsic widths.
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
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
    modifier: Modifier = Modifier,
    maxHeight: Dp? = null,
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
            .then(if (maxHeight != null) Modifier.heightIn(max = maxHeight) else Modifier)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when (view) {
                    PlayerLiveMorePanelView.MENU -> "المزيد"
                    PlayerLiveMorePanelView.SOURCE -> "اختيار المصدر"
                    PlayerLiveMorePanelView.RESIZE -> "حجم الصورة"
                },
                color = colors.text,
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
                        directAction = true,
                    )
                    VodPanelDivider()
                    LiveMoreRow(
                        text = "اعادة التحميل",
                        icon = Icons.Rounded.Refresh,
                        onClick = onReload,
                        focusRequester = reloadFocus,
                        directAction = true,
                    )
                    if (canOfferPlayerLiveSourcePicker(sourceCount)) {
                        VodPanelDivider()
                        LiveMoreRow(
                            text = "اختيار المصدر",
                            icon = Icons.Rounded.SettingsInputAntenna,
                            value = "المصدر ${candidateIndex + 1}",
                            showChevron = true,
                            onClick = onOpenSource,
                            focusRequester = sourceFocus,
                        )
                    }
                    VodPanelDivider()
                    LiveMoreRow(
                        text = "حجم الصورة",
                        icon = vodPictureSizeGlyph(resizeModeIndex),
                        value = livePlayerResizeLabel(resizeModeIndex),
                        showChevron = true,
                        onClick = onOpenResize,
                        focusRequester = resizeFocus,
                    )
                }
                PlayerLiveMorePanelView.SOURCE -> {
                    repeat(sourceCount) { index ->
                        if (index > 0) VodPanelDivider()
                        LiveMoreRow(
                            text = "المصدر ${index + 1}",
                            icon = Icons.Rounded.SettingsInputAntenna,
                            selected = index == candidateIndex,
                            reserveCheckSlot = true,
                            onClick = { onSelectSource(index) },
                            focusRequester = if (index == candidateIndex) selectedOptionFocus else null,
                        )
                    }
                }
                PlayerLiveMorePanelView.RESIZE -> {
                    LIVE_PLAYER_RESIZE_LABELS.forEachIndexed { index, label ->
                        if (index > 0) VodPanelDivider()
                        LiveMoreRow(
                            text = label,
                            icon = vodPictureSizeGlyph(index),
                            selected = index == resizeModeIndex,
                            reserveCheckSlot = true,
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
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    upFocus: FocusRequester? = null,
    downFocus: FocusRequester? = null,
    enabled: Boolean = true,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember { mutableStateOf(false) }
    val showFocused = focused && adaptiveUi.showFocusHighlights
    val foreground = if (showFocused) Color(0xFF14120A) else colors.text
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (showFocused) colors.gold else Color.Transparent)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(
                if (upFocus != null || downFocus != null) {
                    Modifier.focusProperties {
                        canFocus = enabled
                        up = upFocus ?: FocusRequester.Cancel
                        down = downFocus ?: FocusRequester.Cancel
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    }
                } else if (!enabled) {
                    Modifier.focusProperties { canFocus = false }
                } else {
                    Modifier
                },
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = caption }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = caption,
            color = foreground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Approved Live More row anatomy, aligned with the accepted VOD row reference.
 *
 * Child/value rows put the semantic glyph immediately beside the label at the physical RIGHT, then
 * the real value, the reserved check slot and the left-facing opening chevron at the physical LEFT.
 * Direct actions keep the label at the physical RIGHT with one action glyph at the physical LEFT.
 * Focus is a thin gold 1.5dp edge over a 14% gold backing (never a solid fill); the selected
 * unfocused option keeps a 16% backing with a persistent gold check in its reserved slot.
 */
@Composable
private fun LiveMoreRow(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    selected: Boolean = false,
    showChevron: Boolean = false,
    reserveCheckSlot: Boolean = false,
    directAction: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember { mutableStateOf(false) }
    val showFocused = focused && adaptiveUi.showFocusHighlights
    val shape = RoundedCornerShape(11.dp)
    val glyphTint = if (showFocused) colors.goldBright else colors.gold
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    showFocused -> colors.gold.copy(alpha = .14f)
                    selected -> colors.gold.copy(alpha = .16f)
                    else -> Color.Transparent
                },
            )
            .then(
                if (showFocused) {
                    Modifier.border(1.5.dp, colors.gold, shape)
                } else {
                    Modifier
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
        if (directAction) {
            Text(
                text = text,
                color = colors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = glyphTint,
                modifier = Modifier.size(19.dp),
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = glyphTint,
                modifier = Modifier.size(19.dp),
            )
            Text(
                text = text,
                color = colors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            value?.let {
                Text(
                    text = it,
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            if (selected || reserveCheckSlot) {
                Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = if (showFocused) colors.goldBright else colors.gold,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            if (showChevron) {
                Icon(
                    imageVector = Icons.Rounded.ChevronLeft,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// Movie timeline geometry. The coordinate diameter is shared with the preview overlay and is
// constant, so focus changes never move the knob center. The focused ring is reserved inside the
// constant 20.dp slot, so focus decoration never changes layout or clips at the endpoints.
private val MOVIE_SEEK_COORDINATE_DIAMETER = 19.dp
private val MOVIE_SEEK_IDLE_DIAMETER = 15.dp
private val MOVIE_SEEK_ACTIVE_DIAMETER = 17.dp
private val MOVIE_SEEK_FOCUS_RING_RADIUS = 9.25.dp
private val MOVIE_SEEK_FOCUS_RING_WIDTH = 1.5.dp

/**
 * Owner-approved VOD timeline: physical LTR axis (past on the LEFT, future on the RIGHT), gold
 * fill and thumb. Touch scrubbing and bar-focused D-pad seeking keep a distinct preview target;
 * releasing/confirming commits through the existing signed seek owner and cancelling closes the
 * preview without moving playback.
 */
@Composable
private fun VodSeekBar(
    positionMs: Long,
    durationMs: Long,
    buffered: Float,
    previewMs: Long?,
    onPreview: (Long) -> Unit,
    onCommit: (Long) -> Unit,
    onPreviewCancel: () -> Unit,
    focusRequester: FocusRequester,
    remoteActive: Boolean,
    seekStepMs: Long,
    inputEnabled: Boolean = true,
    upFocus: FocusRequester? = null,
    downFocus: FocusRequester? = null,
    stableLayout: Boolean = false,
    moviePresentation: Boolean = false,
    onTimelineFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val active = focused || remoteActive
    val displayMs = previewMs ?: positionMs
    // Direct target mapping shared with the preview pointer (vodPreviewCardFraction): no positional
    // animation, so the thumb, played fill and pointer always show the current seek target for
    // forward, reverse and endpoint movement.
    val progress = vodPreviewCardFraction(displayMs, durationMs)
    var dragPreviewMs by remember { mutableStateOf<Long?>(null) }
    val trackHeight = if (active) 9.dp else 6.dp
    // The Movie overlay keeps the timeline slot height constant across normal/focused/preview
    // states so focusing or previewing never moves the transport below it.
    val slotHeight = if (stableLayout) 20.dp else if (active) 20.dp else 16.dp

    Box(
        Modifier
            .fillMaxWidth()
            .height(slotHeight)
            .then(
                if (inputEnabled) {
                    Modifier
                        .pointerInput(durationMs) {
                            detectTapGestures { offset ->
                                if (durationMs > 0L && size.width > 0) {
                                    val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    onCommit((durationMs * fraction).toLong())
                                }
                            }
                        }
                        .pointerInput(durationMs) {
                            detectHorizontalDragGestures(
                                onDragStart = { },
                                onDragCancel = {
                                    dragPreviewMs = null
                                    onPreviewCancel()
                                },
                                onDragEnd = {
                                    dragPreviewMs?.let(onCommit)
                                    dragPreviewMs = null
                                },
                            ) { change, _ ->
                                change.consume()
                                if (durationMs > 0L && size.width > 0) {
                                    val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    val target = (durationMs * fraction).toLong()
                                    dragPreviewMs = target
                                    onPreview(target)
                                }
                            }
                        }
                } else {
                    Modifier
                },
            )
            .onFocusChanged { state ->
                focused = state.isFocused
                onTimelineFocusChanged?.invoke(state.isFocused)
                if (state.isFocused) {
                    onPreview(positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L)))
                } else if (previewMs != null) {
                    onPreviewCancel()
                }
            }
            .onPreviewKeyEvent { event ->
                if (!inputEnabled || durationMs <= 0L) return@onPreviewKeyEvent false
                val base = previewMs ?: positionMs
                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> when (event.type) {
                        KeyEventType.KeyDown -> {
                            onPreview((base - seekStepMs).coerceIn(0L, durationMs))
                            true
                        }
                        KeyEventType.KeyUp -> {
                            onCommit(previewMs ?: base)
                            true
                        }
                        else -> false
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> when (event.type) {
                        KeyEventType.KeyDown -> {
                            onPreview((base + seekStepMs).coerceIn(0L, durationMs))
                            true
                        }
                        KeyEventType.KeyUp -> {
                            onCommit(previewMs ?: base)
                            true
                        }
                        else -> false
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                    -> if (event.type == KeyEventType.KeyDown) {
                        onCommit(previewMs ?: base)
                        true
                    } else {
                        false
                    }
                    else -> false
                }
            }
            .focusRequester(focusRequester)
            .then(
                if (upFocus != null || downFocus != null) {
                    Modifier.focusProperties {
                        upFocus?.let { up = it }
                        downFocus?.let { down = it }
                    }
                } else {
                    Modifier
                },
            )
            .focusable(inputEnabled),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (moviePresentation) {
            // Movies: one Canvas keeps fill, thumb and focus decoration geometrically coherent
            // with no fixed offset, clipping or layout growth. The knob center uses a constant
            // coordinate diameter (shared with the preview overlay), so focusing never moves it.
            Canvas(Modifier.matchParentSize()) {
                val trackH = trackHeight.toPx()
                val centerY = size.height / 2f
                val trackTop = centerY - trackH / 2f
                val trackCorner = CornerRadius(trackH / 2f)
                val centerX = vodSeekThumbCenterPx(
                    fraction = progress,
                    trackWidthPx = size.width,
                    thumbDiameterPx = MOVIE_SEEK_COORDINATE_DIAMETER.toPx(),
                )
                // Focused keeps the smaller fill so the ring fits the constant slot; remote reveal
                // keeps its slightly larger fill but never draws the focus ring.
                val knobDiameter = when {
                    focused -> MOVIE_SEEK_IDLE_DIAMETER
                    active -> MOVIE_SEEK_ACTIVE_DIAMETER
                    else -> MOVIE_SEEK_IDLE_DIAMETER
                }.toPx()
                val knobRadius = knobDiameter / 2f
                // Restrained dark edge behind the neutral track for separation on any video.
                val trackEdgeH = trackH + 2.dp.toPx()
                drawRoundRect(
                    color = Color.Black.copy(alpha = .50f),
                    topLeft = Offset(0f, centerY - trackEdgeH / 2f),
                    size = Size(size.width, trackEdgeH),
                    cornerRadius = CornerRadius(trackEdgeH / 2f),
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = .30f),
                    topLeft = Offset(0f, trackTop),
                    size = Size(size.width, trackH),
                    cornerRadius = trackCorner,
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = .46f),
                    topLeft = Offset(0f, trackTop),
                    size = Size(size.width * buffered.coerceIn(0f, 1f), trackH),
                    cornerRadius = trackCorner,
                )
                drawRoundRect(
                    color = colors.gold,
                    topLeft = Offset(0f, trackTop),
                    size = Size(centerX, trackH),
                    cornerRadius = trackCorner,
                )
                if (focused) {
                    // Focused-only restrained track emphasis.
                    drawRoundRect(
                        color = colors.goldBright.copy(alpha = .50f),
                        topLeft = Offset(0f, trackTop),
                        size = Size(size.width, trackH),
                        cornerRadius = trackCorner,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                drawCircle(
                    color = colors.gold,
                    radius = knobRadius,
                    center = Offset(centerX, centerY),
                )
                drawCircle(
                    color = Color.Black.copy(alpha = .45f),
                    radius = knobRadius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 2.dp.toPx()),
                )
                if (focused) {
                    // Focused-only light-gold ring around the fill, separated by the dark edge and
                    // reserved inside the constant 20.dp slot (outer edge touches the slot bound).
                    drawCircle(
                        color = colors.goldBright,
                        radius = MOVIE_SEEK_FOCUS_RING_RADIUS.toPx(),
                        center = Offset(centerX, centerY),
                        style = Stroke(width = MOVIE_SEEK_FOCUS_RING_WIDTH.toPx()),
                    )
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = .20f)),
            )
            Box(
                Modifier
                    .fillMaxWidth(buffered.coerceIn(0f, 1f))
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = .32f)),
            )
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(colors.goldBright),
            )
            Box(
                modifier = Modifier.fillMaxWidth(progress),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .offset(x = 5.dp)
                        .size(if (active) 17.dp else 13.dp)
                        .clip(CircleShape)
                        .background(colors.goldBright)
                        .border(2.dp, Color.Black.copy(alpha = .45f), CircleShape),
                )
            }
        }
    }
}

@Composable
private fun VodMorePanel(
    view: VodMorePanelView,
    speed: Float,
    pictureSizeIndex: Int,
    menuFocusRow: VodMoreRow,
    focusTick: Int,
    panelWidthDp: Int,
    inputMuted: Boolean = false,
    onBackToMenu: () -> Unit,
    onOpenGoToTime: () -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenPictureSize: () -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectPictureSize: (Int) -> Unit,
    onRestart: () -> Unit,
    onLock: () -> Unit,
    maxHeight: Dp? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(16.dp)
    val goToTimeFocus = remember { FocusRequester() }
    val speedFocus = remember { FocusRequester() }
    val pictureFocus = remember { FocusRequester() }
    val restartFocus = remember { FocusRequester() }
    val lockFocus = remember { FocusRequester() }
    val headerFocus = remember { FocusRequester() }
    val speedOptionFocus = remember { VOD_PLAYER_SPEED_OPTIONS.map { FocusRequester() } }
    val pictureOptionFocus = remember { LIVE_PLAYER_RESIZE_LABELS.map { FocusRequester() } }

    // The shared VOD More panel keeps node 0 as the header and nodes 1..N as the body rows in
    // visual order. The vertical focus cycle is closed so boundary arrows cannot leak to the strip
    // or the player.
    val movieRowFocus = when (view) {
        VodMorePanelView.MENU -> listOf(goToTimeFocus, speedFocus, pictureFocus, restartFocus, lockFocus)
        VodMorePanelView.SPEED -> speedOptionFocus
        VodMorePanelView.PICTURE_SIZE -> pictureOptionFocus
    }
    val movieNodes = listOf(headerFocus) + movieRowFocus
    fun movieUp(nodeIndex: Int): FocusRequester =
        movieNodes[vodPanelVerticalNeighbor(nodeIndex, movieNodes.size, -1)]
    fun movieDown(nodeIndex: Int): FocusRequester =
        movieNodes[vodPanelVerticalNeighbor(nodeIndex, movieNodes.size, 1)]
    val selectedOptionIndex = when (view) {
        VodMorePanelView.SPEED -> VOD_PLAYER_SPEED_OPTIONS.indexOfFirst { kotlin.math.abs(it - speed) < 0.001f }
        VodMorePanelView.PICTURE_SIZE -> pictureSizeIndex
        VodMorePanelView.MENU -> -1
    }

    LaunchedEffect(view, menuFocusRow, focusTick) {
        val target = when (view) {
            VodMorePanelView.MENU -> when (menuFocusRow) {
                VodMoreRow.GO_TO_TIME -> goToTimeFocus
                VodMoreRow.SPEED -> speedFocus
                VodMoreRow.PICTURE_SIZE -> pictureFocus
                VodMoreRow.RESTART -> restartFocus
                VodMoreRow.LOCK -> lockFocus
            }
            VodMorePanelView.SPEED,
            VodMorePanelView.PICTURE_SIZE,
            -> movieRowFocus.getOrNull(selectedOptionIndex) ?: headerFocus
        }
        withFrameNanos { }
        runCatching { target.requestFocus() }
    }

    Column(
        modifier = modifier
            .width(panelWidthDp.dp)
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
            .then(if (maxHeight != null) Modifier.heightIn(max = maxHeight) else Modifier)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when (view) {
                    VodMorePanelView.MENU -> "المزيد"
                    VodMorePanelView.SPEED -> "سرعة التشغيل"
                    VodMorePanelView.PICTURE_SIZE -> "حجم الصورة"
                },
                color = colors.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LiveMoreHeaderAction(
                caption = if (view == VodMorePanelView.MENU) "اغلاق" else "رجوع",
                onClick = onBackToMenu,
                icon = if (view == VodMorePanelView.MENU) Icons.Rounded.Close else null,
                focusRequester = headerFocus,
                upFocus = movieUp(0),
                downFocus = movieDown(0),
                enabled = !inputMuted,
            )
        }
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            when (view) {
                VodMorePanelView.MENU -> {
                    VodMoreRow(
                        text = "الانتقال الى وقت",
                        icon = Icons.Rounded.Schedule,
                        showChevron = true,
                        onClick = onOpenGoToTime,
                        focusRequester = goToTimeFocus,
                        inputMuted = inputMuted,
                        upFocus = movieUp(1),
                        downFocus = movieDown(1),
                    )
                    VodPanelDivider()
                    VodMoreRow(
                        text = "السرعة",
                        icon = Icons.Rounded.Speed,
                        value = vodSpeedLabel(speed),
                        showChevron = true,
                        onClick = onOpenSpeed,
                        focusRequester = speedFocus,
                        inputMuted = inputMuted,
                        upFocus = movieUp(2),
                        downFocus = movieDown(2),
                    )
                    VodPanelDivider()
                    VodMoreRow(
                        text = "حجم الصورة",
                        icon = vodPictureSizeGlyph(pictureSizeIndex),
                        value = livePlayerResizeLabel(pictureSizeIndex),
                        showChevron = true,
                        onClick = onOpenPictureSize,
                        focusRequester = pictureFocus,
                        inputMuted = inputMuted,
                        upFocus = movieUp(3),
                        downFocus = movieDown(3),
                    )
                    VodPanelDivider()
                    VodDirectActionRow(
                        text = "من البداية",
                        icon = Icons.Rounded.Replay,
                        onClick = onRestart,
                        focusRequester = restartFocus,
                        inputMuted = inputMuted,
                        upFocus = movieUp(4),
                        downFocus = movieDown(4),
                    )
                    VodPanelDivider()
                    VodDirectActionRow(
                        text = "قفل التحكم",
                        icon = Icons.Rounded.Lock,
                        onClick = onLock,
                        focusRequester = lockFocus,
                        inputMuted = inputMuted,
                        upFocus = movieUp(5),
                        downFocus = movieDown(5),
                    )
                }
                VodMorePanelView.SPEED -> {
                    VOD_PLAYER_SPEED_OPTIONS.forEachIndexed { index, option ->
                        if (index > 0) VodPanelDivider()
                        VodNumericOptionRow(
                            value = vodSpeedLabel(option),
                            selected = kotlin.math.abs(option - speed) < 0.001f,
                            onClick = { onSelectSpeed(option) },
                            focusRequester = speedOptionFocus[index],
                            inputMuted = inputMuted,
                            upFocus = movieUp(index + 1),
                            downFocus = movieDown(index + 1),
                        )
                    }
                }
                VodMorePanelView.PICTURE_SIZE -> {
                    LIVE_PLAYER_RESIZE_LABELS.forEachIndexed { index, label ->
                        if (index > 0) VodPanelDivider()
                        VodMoreRow(
                            text = label,
                            icon = vodPictureSizeGlyph(index),
                            selected = index == pictureSizeIndex,
                            reserveCheckSlot = true,
                            onClick = { onSelectPictureSize(index) },
                            focusRequester = pictureOptionFocus[index],
                            inputMuted = inputMuted,
                            upFocus = movieUp(index + 1),
                            downFocus = movieDown(index + 1),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Approved Movie More-row anatomy: glyph immediately beside the label on the physical right,
 * complete label, flexible space and a reserved trailing value/check/chevron area on the physical
 * left. The explicit up/down targets keep the panel's vertical focus cycle closed.
 */
@Composable
private fun VodMoreRow(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    upFocus: FocusRequester,
    downFocus: FocusRequester,
    inputMuted: Boolean = false,
    modifier: Modifier = Modifier,
    value: String? = null,
    selected: Boolean = false,
    showChevron: Boolean = false,
    reserveCheckSlot: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember { mutableStateOf(false) }
    val showFocused = focused && adaptiveUi.showFocusHighlights
    val shape = RoundedCornerShape(11.dp)
    // Reference: a thin gold edge with a subtle backing, no fill/geometry change. Gold semantic
    // glyphs stay distinct from the ivory text and the selected check column.
    val glyphTint = if (showFocused) colors.goldBright else colors.gold
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    showFocused -> colors.gold.copy(alpha = .14f)
                    selected -> colors.gold.copy(alpha = .16f)
                    else -> Color.Transparent
                },
            )
            .then(
                if (showFocused) {
                    Modifier.border(1.5.dp, colors.gold, shape)
                } else {
                    Modifier
                },
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                canFocus = !inputMuted
                up = upFocus
                down = downFocus
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = !inputMuted, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = text }
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = glyphTint,
            modifier = Modifier.size(19.dp),
        )
        Text(
            text = text,
            color = colors.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        value?.let {
            Text(
                text = it,
                color = colors.textMuted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        if (selected || reserveCheckSlot) {
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = if (showFocused) colors.goldBright else colors.gold,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Fine separator between the compact menu rows. */
@Composable
private fun VodPanelDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalHulkColors.current.gold.copy(alpha = .18f)),
    )
}

/**
 * Reference direct action: the Arabic label sits on the physical RIGHT and one semantic action
 * glyph on the physical LEFT (restart, lock). Focus uses the same thin gold edge treatment.
 */
@Composable
private fun VodDirectActionRow(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    upFocus: FocusRequester,
    downFocus: FocusRequester,
    inputMuted: Boolean = false,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember { mutableStateOf(false) }
    val showFocused = focused && adaptiveUi.showFocusHighlights
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (showFocused) colors.gold.copy(alpha = .14f) else Color.Transparent)
            .then(
                if (showFocused) {
                    Modifier.border(1.5.dp, colors.gold, shape)
                } else {
                    Modifier
                },
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                canFocus = !inputMuted
                up = upFocus
                down = downFocus
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = !inputMuted, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = text }
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = text,
            color = colors.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (showFocused) colors.goldBright else colors.gold,
            modifier = Modifier.size(19.dp),
        )
    }
}

/**
 * Reference numeric option row: the value sits on the physical RIGHT and the reserved selected
 * check column on the physical LEFT, with no duplicate semantic glyph.
 */
@Composable
private fun VodNumericOptionRow(
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    upFocus: FocusRequester,
    downFocus: FocusRequester,
    inputMuted: Boolean = false,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember { mutableStateOf(false) }
    val showFocused = focused && adaptiveUi.showFocusHighlights
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    showFocused -> colors.gold.copy(alpha = .14f)
                    selected -> colors.gold.copy(alpha = .16f)
                    else -> Color.Transparent
                },
            )
            .then(
                if (showFocused) {
                    Modifier.border(1.5.dp, colors.gold, shape)
                } else {
                    Modifier
                },
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                canFocus = !inputMuted
                up = upFocus
                down = downFocus
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = !inputMuted, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = value }
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = value,
            color = colors.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = if (showFocused) colors.goldBright else colors.gold,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun vodPictureSizeGlyph(index: Int): ImageVector = when (index) {
    1 -> Icons.Rounded.ZoomIn
    2 -> Icons.Rounded.CropFree
    else -> Icons.Rounded.FitScreen
}

@Composable
private fun VodGoToTimeDialog(
    currentPositionMs: Long,
    durationMs: Long,
    durationLabel: String,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val initialFields = remember { vodGoToTimeFields(currentPositionMs.coerceAtLeast(0L)) }
    var fields by remember { mutableStateOf(initialFields) }
    val hoursFocus = remember { FocusRequester() }
    val minutesFocus = remember { FocusRequester() }
    val secondsFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val hourUpFocus = remember { FocusRequester() }
    val hourDownFocus = remember { FocusRequester() }
    val minuteUpFocus = remember { FocusRequester() }
    val minuteDownFocus = remember { FocusRequester() }
    val secondUpFocus = remember { FocusRequester() }
    val secondDownFocus = remember { FocusRequester() }
    val fieldsEnabled = durationMs > 0L
    val confirmEnabled = vodGoToTimeConfirmEnabled(
        hours = fields.hours,
        minutes = fields.minutes,
        seconds = fields.seconds,
        durationMs = durationMs,
    )
    val shape = RoundedCornerShape(22.dp)

    // Closed TAB / SHIFT+TAB cycle over every eligible dialog node: header fields, six step
    // buttons and the enabled actions. Disabled duration paths keep only the always-attached Back
    // node so TAB can never leave the window.
    val tabNodes = remember(fieldsEnabled, confirmEnabled) {
        buildList {
            add(backFocus)
            if (confirmEnabled) add(confirmFocus)
            if (fieldsEnabled) {
                add(hourUpFocus)
                add(hoursFocus)
                add(hourDownFocus)
                add(minuteUpFocus)
                add(minutesFocus)
                add(minuteDownFocus)
                add(secondUpFocus)
                add(secondsFocus)
                add(secondDownFocus)
            }
        }
    }
    fun tabTarget(target: FocusRequester, delta: Int): FocusRequester {
        if (tabNodes.isEmpty()) return target
        val index = tabNodes.indexOf(target)
        if (index < 0) return target
        return tabNodes[(index + delta + tabNodes.size) % tabNodes.size]
    }
    val tabNeighbor: (FocusRequester, Int) -> FocusRequester = { target, delta -> tabTarget(target, delta) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        val initialTarget = if (
            vodGoToTimeConfirmEnabled(initialFields.hours, initialFields.minutes, initialFields.seconds, durationMs)
        ) {
            hoursFocus
        } else {
            backFocus
        }
        runCatching { initialTarget.requestFocus() }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        val dialogMaxHeight = (maxHeight - 24.dp).coerceAtLeast(0.dp)
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(.86f)
                .heightIn(max = dialogMaxHeight)
                .focusGroup()
                .clip(shape)
                .background(Color(0xF2141510))
                .border(1.dp, colors.gold.copy(alpha = .60f), shape)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("الانتقال الى وقت", color = colors.goldBright, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(5.dp))
            Text(
                text = if (durationMs > 0L) "$durationLabel ${formatTime(durationMs)}" else "$durationLabel غير متاحة",
                color = colors.textMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    VodGoToTimeField(
                        label = "ساعات",
                        value = fields.hours,
                        fields = fields,
                        field = VodTimeField.HOURS,
                        durationMs = durationMs,
                        focusRequester = hoursFocus,
                        stepUpFocusRequester = hourUpFocus,
                        stepDownFocusRequester = hourDownFocus,
                        leftTarget = backFocus,
                        rightTarget = minutesFocus,
                        tabNeighbor = tabNeighbor,
                        onFieldsChange = { fields = it },
                        onConfirmRequest = {
                            vodGoToTimeTargetMs(fields.hours, fields.minutes, fields.seconds, durationMs)?.let(onConfirm)
                        },
                    )
                    Text(":", color = colors.goldBright, fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp))
                    VodGoToTimeField(
                        label = "دقائق",
                        value = fields.minutes,
                        fields = fields,
                        field = VodTimeField.MINUTES,
                        durationMs = durationMs,
                        focusRequester = minutesFocus,
                        stepUpFocusRequester = minuteUpFocus,
                        stepDownFocusRequester = minuteDownFocus,
                        leftTarget = hoursFocus,
                        rightTarget = secondsFocus,
                        tabNeighbor = tabNeighbor,
                        onFieldsChange = { fields = it },
                        onConfirmRequest = {
                            vodGoToTimeTargetMs(fields.hours, fields.minutes, fields.seconds, durationMs)?.let(onConfirm)
                        },
                    )
                    Text(":", color = colors.goldBright, fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp))
                    VodGoToTimeField(
                        label = "ثواني",
                        value = fields.seconds,
                        fields = fields,
                        field = VodTimeField.SECONDS,
                        durationMs = durationMs,
                        focusRequester = secondsFocus,
                        stepUpFocusRequester = secondUpFocus,
                        stepDownFocusRequester = secondDownFocus,
                        leftTarget = minutesFocus,
                        rightTarget = confirmFocus,
                        tabNeighbor = tabNeighbor,
                        onFieldsChange = { fields = it },
                        onConfirmRequest = {
                            vodGoToTimeTargetMs(fields.hours, fields.minutes, fields.seconds, durationMs)?.let(onConfirm)
                        },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("الوقت الحالي ${formatTime(currentPositionMs.coerceAtLeast(0L))}", color = colors.textMuted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .10f)))
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FocusButton(
                    text = "انتقال",
                    onClick = {
                        vodGoToTimeTargetMs(fields.hours, fields.minutes, fields.seconds, durationMs)?.let(onConfirm)
                    },
                    enabled = confirmEnabled,
                    trailingIcon = Icons.Rounded.Schedule,
                    scaleOnFocus = false,
                    textMaxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 46.dp)
                        .focusRequester(confirmFocus)
                        .focusProperties {
                            up = hoursFocus
                            down = FocusRequester.Cancel
                            left = backFocus
                            right = FocusRequester.Cancel
                        }
                        .then(
                            Modifier.focusProperties {
                                next = tabTarget(confirmFocus, 1)
                                previous = tabTarget(confirmFocus, -1)
                            },
                        ),
                )
                FocusButton(
                    text = "رجوع",
                    onClick = onDismiss,
                    primary = false,
                    outlined = true,
                    scaleOnFocus = false,
                    textMaxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 46.dp)
                        .focusRequester(backFocus)
                        .focusProperties {
                            up = hoursFocus
                            down = FocusRequester.Cancel
                            right = confirmFocus
                            left = FocusRequester.Cancel
                        }
                        .then(
                            Modifier.focusProperties {
                                next = tabTarget(backFocus, 1)
                                previous = tabTarget(backFocus, -1)
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun VodGoToTimeField(
    label: String,
    value: Int,
    fields: VodGoToTimeFields,
    field: VodTimeField,
    durationMs: Long,
    focusRequester: FocusRequester,
    stepUpFocusRequester: FocusRequester,
    stepDownFocusRequester: FocusRequester,
    leftTarget: FocusRequester?,
    rightTarget: FocusRequester?,
    tabNeighbor: ((FocusRequester, Int) -> FocusRequester)?,
    onFieldsChange: (VodGoToTimeFields) -> Unit,
    onConfirmRequest: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val enabled = durationMs > 0L
    val shape = RoundedCornerShape(10.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        VodGoToTimeStepButton(
            arrowUp = true,
            enabled = enabled,
            onClick = {
                onFieldsChange(vodGoToTimeFieldsStepped(fields, field, 1, durationMs))
            },
            focusRequester = stepUpFocusRequester,
            fieldFocusRequester = focusRequester,
            tabNeighbor = tabNeighbor,
        )
        Box(
            modifier = Modifier
                .width(76.dp)
                .heightIn(min = 52.dp)
                .clip(shape)
                .background(Color(0xFF10110D))
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = when {
                        focused -> colors.goldBright
                        enabled -> colors.gold.copy(alpha = .45f)
                        else -> colors.line.copy(alpha = .45f)
                    },
                    shape = shape,
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Non-editable numeric selector: a focusable display with no text-input connection, so
            // focusing or tapping it can never open an IME. TV UP/DOWN step this unit and phone
            // taps use the arrows above and below.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .focusProperties {
                        left = leftTarget ?: FocusRequester.Cancel
                        right = rightTarget ?: FocusRequester.Cancel
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                        tabNeighbor?.let { neighbor ->
                            next = neighbor(focusRequester, 1)
                            previous = neighbor(focusRequester, -1)
                        }
                    }
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.nativeKeyEvent.keyCode) {
                            AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                                onFieldsChange(vodGoToTimeFieldsStepped(fields, field, 1, durationMs))
                                true
                            }
                            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                                onFieldsChange(vodGoToTimeFieldsStepped(fields, field, -1, durationMs))
                                true
                            }
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                                leftTarget?.let { runCatching { it.requestFocus() } }
                                true
                            }
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                                rightTarget?.let { runCatching { it.requestFocus() } }
                                true
                            }
                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                            AndroidKeyEvent.KEYCODE_ENTER,
                            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                            -> {
                                onConfirmRequest()
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable(enabled = enabled),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = value.toString().padStart(2, '0'),
                    color = if (enabled) Color.White else colors.textMuted.copy(alpha = .55f),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
        VodGoToTimeStepButton(
            arrowUp = false,
            enabled = enabled,
            onClick = {
                onFieldsChange(vodGoToTimeFieldsStepped(fields, field, -1, durationMs))
            },
            focusRequester = stepDownFocusRequester,
            fieldFocusRequester = focusRequester,
            tabNeighbor = tabNeighbor,
        )
        Spacer(Modifier.height(3.dp))
        Text(label, color = colors.textMuted, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun VodGoToTimeStepButton(
    arrowUp: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    fieldFocusRequester: FocusRequester? = null,
    tabNeighbor: ((FocusRequester, Int) -> FocusRequester)? = null,
) {
    val colors = LocalHulkColors.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(
                if (focusRequester != null && tabNeighbor != null) {
                    Modifier.focusProperties {
                        canFocus = enabled
                        if (arrowUp) {
                            up = FocusRequester.Cancel
                            down = fieldFocusRequester ?: FocusRequester.Cancel
                        } else {
                            up = fieldFocusRequester ?: FocusRequester.Cancel
                            down = FocusRequester.Cancel
                        }
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                        next = tabNeighbor(focusRequester, 1)
                        previous = tabNeighbor(focusRequester, -1)
                    }
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (arrowUp) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = if (enabled) colors.goldBright else colors.textMuted.copy(alpha = .40f),
            modifier = Modifier.size(22.dp),
        )
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
    durationMs: Long,
    identity: String? = null,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
    restartFocusRequester: FocusRequester,
    backFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val dialogMaxHeight = (maxHeight - 24.dp).coerceAtLeast(0.dp)
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(.78f)
                .heightIn(max = dialogMaxHeight)
                .focusGroup()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xF2141510))
                .border(1.dp, colors.gold.copy(alpha = .60f), RoundedCornerShape(22.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = .40f))
                .border(1.dp, colors.gold.copy(alpha = .55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = colors.goldBright,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("اكمل المشاهدة", color = colors.goldBright, fontSize = 23.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(3.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        identity?.takeIf(String::isNotBlank)?.let { identityText ->
            Spacer(Modifier.height(3.dp))
            Text(
                identityText,
                color = colors.gold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "توقفت عند ${formatTime(positionMs)}",
            color = colors.textMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        if (progress != null) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = .16f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(colors.goldBright),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        // Captions are measured with the real compact typography so every label is allocated in
        // full: the accepted TV row when all three fit, otherwise an arrangement that wraps or
        // stacks the same three actions. The focus graph is closed in every arrangement.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val textMeasurer = rememberTextMeasurer()
            val captionStyle = LocalTextStyle.current.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            val actionGap = 9.dp
            val actionRequiredWidths = remember(maxWidth, density.fontScale, density.density, captionStyle) {
                val horizontalPaddingPx = with(density) { 12.dp.roundToPx() }
                val iconPx = with(density) { 17.dp.roundToPx() }
                val gapPx = with(density) { 6.dp.roundToPx() }
                val bufferPx = with(density) { 6.dp.roundToPx() }
                listOf("اكمل المشاهدة" to true, "من البداية" to true, "رجوع" to false).map { (caption, hasIcon) ->
                    movieActionRequiredWidthPx(
                        captionWidthPx = textMeasurer.measure(caption, captionStyle).size.width,
                        iconSizePx = if (hasIcon) iconPx else 0,
                        horizontalPaddingPx = horizontalPaddingPx,
                        gapPx = gapPx,
                    ) + bufferPx
                }
            }
            val layoutMode = movieActionLayoutMode(
                availableWidthPx = with(density) { maxWidth.roundToPx() },
                requiredWidthsPx = actionRequiredWidths,
                gapPx = with(density) { actionGap.roundToPx() },
            )
            val resumeAction: @Composable (Modifier, Modifier) -> Unit = { actionModifier, focusModifier ->
                FocusButton(
                    text = "اكمل المشاهدة",
                    onClick = onResume,
                    trailingIcon = Icons.Rounded.PlayArrow,
                    scaleOnFocus = false,
                    textMaxLines = 1,
                    modifier = actionModifier
                        .heightIn(min = 46.dp)
                        .focusRequester(focusRequester)
                        .then(focusModifier),
                )
            }
            val restartAction: @Composable (Modifier, Modifier) -> Unit = { actionModifier, focusModifier ->
                FocusButton(
                    text = "من البداية",
                    onClick = onRestart,
                    primary = false,
                    outlined = true,
                    trailingIcon = Icons.Rounded.Replay,
                    scaleOnFocus = false,
                    textMaxLines = 1,
                    modifier = actionModifier
                        .heightIn(min = 46.dp)
                        .focusRequester(restartFocusRequester)
                        .then(focusModifier),
                )
            }
            val backAction: @Composable (Modifier, Modifier) -> Unit = { actionModifier, focusModifier ->
                FocusButton(
                    text = "رجوع",
                    onClick = onBack,
                    primary = false,
                    outlined = true,
                    scaleOnFocus = false,
                    textMaxLines = 1,
                    modifier = actionModifier
                        .heightIn(min = 46.dp)
                        .focusRequester(backFocusRequester)
                        .then(focusModifier),
                )
            }
            when (layoutMode) {
                MovieActionLayout.SINGLE_ROW -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(actionGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    resumeAction(
                        Modifier.weight(1f),
                        Modifier.focusProperties {
                            left = restartFocusRequester
                            right = FocusRequester.Cancel
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                        },
                    )
                    restartAction(
                        Modifier.weight(1f),
                        Modifier.focusProperties {
                            left = backFocusRequester
                            right = focusRequester
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                        },
                    )
                    backAction(
                        Modifier.weight(1f),
                        Modifier.focusProperties {
                            left = FocusRequester.Cancel
                            right = restartFocusRequester
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                        },
                    )
                }
                MovieActionLayout.WATCH_THEN_PAIR -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(actionGap),
                ) {
                    resumeAction(
                        Modifier.fillMaxWidth(),
                        Modifier.focusProperties {
                            up = FocusRequester.Cancel
                            down = restartFocusRequester
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        },
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(actionGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        restartAction(
                            Modifier.weight(1f),
                            Modifier.focusProperties {
                                left = backFocusRequester
                                right = FocusRequester.Cancel
                                up = focusRequester
                                down = FocusRequester.Cancel
                            },
                        )
                        backAction(
                            Modifier.weight(1f),
                            Modifier.focusProperties {
                                left = FocusRequester.Cancel
                                right = restartFocusRequester
                                up = focusRequester
                                down = FocusRequester.Cancel
                            },
                        )
                    }
                }
                MovieActionLayout.STACKED -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(actionGap),
                ) {
                    resumeAction(
                        Modifier.fillMaxWidth(),
                        Modifier.focusProperties {
                            up = FocusRequester.Cancel
                            down = restartFocusRequester
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        },
                    )
                    restartAction(
                        Modifier.fillMaxWidth(),
                        Modifier.focusProperties {
                            up = focusRequester
                            down = backFocusRequester
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        },
                    )
                    backAction(
                        Modifier.fillMaxWidth(),
                        Modifier.focusProperties {
                            up = restartFocusRequester
                            down = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        },
                    )
                }
            }
        }
        }
    }
}

/**
 * The single Movie error surface, shared by offline entries and blocking generic failures. The
 * Resume prompt, player chrome and the legacy error panel are never composed while this card is
 * up because the presentation decision is exclusive by construction.
 */
@Composable
private fun MoviePlayerErrorCard(
    offline: Boolean,
    resumePending: Boolean,
    savedPositionMs: Long,
    failureMessage: String?,
    mediaLabel: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    retryFocusRequester: FocusRequester,
    backFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val copy = moviePlayerErrorCopy(
        offline = offline,
        resumePending = resumePending,
        formattedSavedTime = formatTime(savedPositionMs),
        failureMessage = failureMessage,
        mediaLabel = mediaLabel,
    )
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val cardMaxHeight = (maxHeight - 24.dp).coerceAtLeast(0.dp)
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(.78f)
                .heightIn(max = cardMaxHeight)
                .focusGroup()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xF2141510))
                .border(1.dp, colors.gold.copy(alpha = .60f), RoundedCornerShape(22.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = moviesErrorIcon(networkFailure = offline),
                contentDescription = null,
                tint = colors.gold,
                modifier = Modifier.size(if (adaptiveUi.isTelevision) 46.dp else 38.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = copy.title,
                color = colors.text,
                fontSize = if (adaptiveUi.isTelevision) 24.sp else 20.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = copy.body,
                color = colors.text,
                fontSize = if (adaptiveUi.isTelevision) 15.sp else 13.sp,
                lineHeight = if (adaptiveUi.isTelevision) 23.sp else 20.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = copy.context,
                    color = colors.textMuted,
                    fontSize = if (adaptiveUi.isTelevision) 14.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                if (resumePending) {
                    // The clock is the last child, so in RTL it renders physically LEFT of the
                    // saved-time wording, matching the owner reference.
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = colors.gold,
                        modifier = Modifier.size(if (adaptiveUi.isTelevision) 16.dp else 14.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = .12f)),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FocusButton(
                    text = "اعادة المحاولة",
                    onClick = onRetry,
                    trailingIcon = Icons.Rounded.Refresh,
                    compact = true,
                    scaleOnFocus = false,
                    textMaxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 46.dp)
                        .focusRequester(retryFocusRequester)
                        .focusProperties {
                            left = backFocusRequester
                            right = FocusRequester.Cancel
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                        },
                )
                FocusButton(
                    text = "رجوع",
                    onClick = onBack,
                    primary = false,
                    outlined = true,
                    compact = true,
                    scaleOnFocus = false,
                    textMaxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 46.dp)
                        .focusRequester(backFocusRequester)
                        .focusProperties {
                            left = FocusRequester.Cancel
                            right = retryFocusRequester
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                        },
                )
            }
        }
    }
}

/**
 * Centered end-of-episode decision card in the accepted dark/gold/ivory dialog family. Shows the
 * real next episode and the existing countdown with complete "تشغيل الان" / "الغاء" captions,
 * initial Play Now focus and a closed directional graph in every measured arrangement.
 */
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
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val dialogMaxHeight = (maxHeight - 24.dp).coerceAtLeast(0.dp)
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(.78f)
                .heightIn(max = dialogMaxHeight)
                .focusGroup()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xF2141510))
                .border(1.dp, colors.gold.copy(alpha = .60f), RoundedCornerShape(22.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = .40f))
                    .border(1.dp, colors.gold.copy(alpha = .55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = null,
                    tint = colors.goldBright,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "الحلقة التالية خلال $seconds",
                color = colors.goldBright,
                fontSize = 23.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val density = LocalDensity.current
                val textMeasurer = rememberTextMeasurer()
                val captionStyle = LocalTextStyle.current.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                val actionGap = 9.dp
                val requiredWidths = remember(maxWidth, density.fontScale, density.density, captionStyle) {
                    val horizontalPaddingPx = with(density) { 12.dp.roundToPx() }
                    val iconPx = with(density) { 17.dp.roundToPx() }
                    val gapPx = with(density) { 6.dp.roundToPx() }
                    val bufferPx = with(density) { 6.dp.roundToPx() }
                    listOf("تشغيل الان" to true, "الغاء" to false).map { (caption, hasIcon) ->
                        movieActionRequiredWidthPx(
                            captionWidthPx = textMeasurer.measure(caption, captionStyle).size.width,
                            iconSizePx = if (hasIcon) iconPx else 0,
                            horizontalPaddingPx = horizontalPaddingPx,
                            gapPx = gapPx,
                        ) + bufferPx
                    }
                }
                val availableWidthPx = with(density) { maxWidth.roundToPx() }
                val gapPx = with(density) { actionGap.roundToPx() }
                val fitsSingleRow = requiredWidths.maxOrNull()?.let { maxRequired ->
                    2 * maxRequired + gapPx <= availableWidthPx
                } == true
                val playAction: @Composable (Modifier, Modifier) -> Unit = { actionModifier, focusModifier ->
                    FocusButton(
                        text = "تشغيل الان",
                        onClick = onPlayNow,
                        trailingIcon = Icons.Rounded.PlayArrow,
                        scaleOnFocus = false,
                        textMaxLines = 1,
                        modifier = actionModifier
                            .heightIn(min = 46.dp)
                            .focusRequester(playFocusRequester)
                            .then(focusModifier),
                    )
                }
                val cancelAction: @Composable (Modifier, Modifier) -> Unit = { actionModifier, focusModifier ->
                    FocusButton(
                        text = "الغاء",
                        onClick = onCancel,
                        primary = false,
                        outlined = true,
                        scaleOnFocus = false,
                        textMaxLines = 1,
                        modifier = actionModifier
                            .heightIn(min = 46.dp)
                            .focusRequester(cancelFocusRequester)
                            .then(focusModifier),
                    )
                }
                if (fitsSingleRow) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(actionGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        playAction(
                            Modifier.weight(1f),
                            Modifier.focusProperties {
                                left = cancelFocusRequester
                                right = FocusRequester.Cancel
                                up = FocusRequester.Cancel
                                down = FocusRequester.Cancel
                            },
                        )
                        cancelAction(
                            Modifier.weight(1f),
                            Modifier.focusProperties {
                                left = FocusRequester.Cancel
                                right = playFocusRequester
                                up = FocusRequester.Cancel
                                down = FocusRequester.Cancel
                            },
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(actionGap),
                    ) {
                        playAction(
                            Modifier.fillMaxWidth(),
                            Modifier.focusProperties {
                                up = FocusRequester.Cancel
                                down = cancelFocusRequester
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            },
                        )
                        cancelAction(
                            Modifier.fillMaxWidth(),
                            Modifier.focusProperties {
                                up = playFocusRequester
                                down = FocusRequester.Cancel
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            },
                        )
                    }
                }
            }
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

private fun PlayerErrorModalAction.liveCaption(): String = when (this) {
    PlayerErrorModalAction.RETRY -> "اعادة المحاولة"
    PlayerErrorModalAction.CHOOSE_CHANNEL -> "اختيار قناة"
    PlayerErrorModalAction.CHOOSE_SOURCE -> "اختيار مصدر"
    PlayerErrorModalAction.BACK -> "رجوع"
}

private fun PlayerErrorModalAction.liveHasIcon(): Boolean = this != PlayerErrorModalAction.BACK

@Composable
private fun PlayerErrorPanel(
    title: String?,
    message: String,
    networkFailure: Boolean = false,
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
    val bringIntoViewScope = rememberCoroutineScope()
    val actionBringIntoView = remember(actions) {
        actions.associateWith { BringIntoViewRequester() }
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

    fun actionModifier(
        action: PlayerErrorModalAction,
        requester: FocusRequester,
        columns: Int,
    ): Modifier {
        val index = actions.indexOf(action)
        val neighbors = if (layoutDirection == LayoutDirection.Rtl) {
            playerErrorActionNeighbors(index, actions.size, columns)
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
            .bringIntoViewRequester(actionBringIntoView.getValue(action))
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
                    if (livePresentation) {
                        bringIntoViewScope.launch {
                            runCatching { actionBringIntoView.getValue(action).bringIntoView() }
                        }
                    }
                } else if (focusedAction == action) {
                    focusedAction = null
                }
            }
    }

    @Composable
    fun ErrorActionButton(action: PlayerErrorModalAction, buttonModifier: Modifier) {
        when (action) {
            PlayerErrorModalAction.RETRY -> FocusButton(
                action.liveCaption(),
                onRetry,
                modifier = buttonModifier,
                primary = livePresentation || focusedAction == PlayerErrorModalAction.RETRY,
                accent = !livePresentation && focusedAction != PlayerErrorModalAction.RETRY,
                compact = true,
                trailingIcon = Icons.Rounded.Refresh.takeIf { livePresentation },
            )
            PlayerErrorModalAction.CHOOSE_CHANNEL -> FocusButton(
                action.liveCaption(),
                onChooseChannel,
                modifier = buttonModifier,
                primary = !livePresentation && focusedAction == PlayerErrorModalAction.CHOOSE_CHANNEL,
                outlined = livePresentation,
                compact = true,
                trailingIcon = if (livePresentation) Icons.AutoMirrored.Rounded.List else null,
            )
            PlayerErrorModalAction.CHOOSE_SOURCE -> FocusButton(
                action.liveCaption(),
                onChooseServer,
                modifier = buttonModifier,
                primary = !livePresentation && focusedAction == PlayerErrorModalAction.CHOOSE_SOURCE,
                outlined = livePresentation,
                compact = true,
                trailingIcon = Icons.Rounded.Layers.takeIf { livePresentation },
            )
            PlayerErrorModalAction.BACK -> FocusButton(
                action.liveCaption(),
                onBack,
                modifier = buttonModifier,
                primary = !livePresentation && focusedAction == PlayerErrorModalAction.BACK,
                outlined = livePresentation,
                compact = true,
            )
        }
    }

    BoxWithConstraints(
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
        contentAlignment = Alignment.Center,
    ) {
        // The Live card is a bounded scroll container so the complete content (warning, title,
        // wrapped body, divider and every action row) stays reachable at any window height. VOD
        // keeps its previous non-scrolling composition.
        val cardMaxHeight = (maxHeight - 24.dp).coerceAtLeast(0.dp)
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(if (adaptiveUi.isTelevision) .82f else .92f)
                .widthIn(max = if (adaptiveUi.isTelevision) 920.dp else 620.dp)
                .then(if (livePresentation) Modifier.safeDrawingPadding() else Modifier)
                .then(if (livePresentation) Modifier.heightIn(max = cardMaxHeight) else Modifier)
                // Live adopts the accepted Movie error-card surface family (dark raised card,
                // warm-gold edge); the bounded scrolling and measured actions stay Live-specific.
                .clip(RoundedCornerShape(if (livePresentation) 22.dp else 20.dp))
                .background(if (livePresentation) Color(0xF2141510) else Color.Black.copy(alpha = .96f))
                .border(
                    1.dp,
                    colors.gold.copy(alpha = if (livePresentation) .60f else .34f),
                    RoundedCornerShape(if (livePresentation) 22.dp else 20.dp),
                )
                .then(if (livePresentation) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .focusGroup()
                .padding(if (adaptiveUi.isTelevision) 24.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (livePresentation) {
                Icon(
                    imageVector = moviesErrorIcon(networkFailure = networkFailure),
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
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val density = LocalDensity.current
                    val textMeasurer = rememberTextMeasurer()
                    val captionStyle = LocalTextStyle.current.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    // The accepted compact FocusButton geometry: 12dp/9dp padding and a 17dp icon
                    // with a 6dp gap. Measuring the real captions at the current font scale keeps
                    // the common size large enough for scaled text and no larger than necessary.
                    val actionContent = remember(
                        actions,
                        maxWidth,
                        density.fontScale,
                        density.density,
                        captionStyle,
                    ) {
                        val horizontalPadding = 24.dp
                        val verticalPadding = 18.dp
                        val iconAllowance = 23.dp
                        actions.map { action ->
                            val layout = textMeasurer.measure(action.liveCaption(), captionStyle)
                            val textWidth = with(density) { layout.size.width.toDp() }
                            val textHeight = with(density) { layout.size.height.toDp() }
                            val width = textWidth +
                                (if (action.liveHasIcon()) iconAllowance else 0.dp) +
                                horizontalPadding
                            val height = maxOf(textHeight, 17.dp) + verticalPadding
                            width to height
                        }
                    }
                    val sizing = liveErrorActionSizing(
                        availableWidthDp = maxWidth.value.roundToInt(),
                        actionCount = actions.size,
                        maxColumns = LIVE_PLAYER_ERROR_MAX_COLUMNS,
                        requiredActionWidthDp = actionContent.maxOfOrNull { it.first.value.roundToInt() } ?: 0,
                        requiredActionHeightDp = actionContent.maxOfOrNull { it.second.value.roundToInt() } ?: 0,
                    )
                    val actionGap = PLAYER_ERROR_ACTION_GAP_DP.dp
                    Column(
                        modifier = Modifier.fillMaxWidth().focusGroup(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(actionGap),
                    ) {
                        sizing.rows.forEach { rowIndices ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(actionGap),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                rowIndices.forEach { index ->
                                    val action = actions[index]
                                    ErrorActionButton(
                                        action = action,
                                        buttonModifier = actionModifier(action, requesters[index], sizing.columns)
                                            .width(sizing.actionWidthDp.dp)
                                            .height(sizing.actionHeightDp.dp),
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
                            buttonModifier = actionModifier(action, requesters[index], actions.size),
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
