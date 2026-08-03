@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package sa.hulksa.player.ui.screens

import android.graphics.Color as AndroidColor
import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.PlaybackRequest
import sa.hulksa.player.ui.adaptive.HulkInputMode
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.components.BrandBadge
import sa.hulksa.player.ui.components.ChannelLogo
import sa.hulksa.player.ui.components.ChannelListItem
import sa.hulksa.player.ui.components.ErrorNotice
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.HulkTextField
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.theme.LocalHulkColors
import java.util.Locale

private const val CONTROLS_TIMEOUT_MS = 5_500L
private const val PLAYER_FAVORITES_CATEGORY = "__player_favorites__"
private const val RESUME_PROMPT_THRESHOLD_MS = 30_000L
private const val SEEK_STEP_MS = 10_000L
private const val NEXT_EPISODE_SECONDS = 8

private enum class PlayerPanel { AUDIO, SUBTITLES, SPEED, RESIZE, QUALITY, SERVERS }

private data class PlayerTrackOption(
    val key: String,
    val label: String,
    val secondary: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val selected: Boolean,
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    request: PlaybackRequest,
    liveCatalog: Catalog?,
    isFavorite: (ContentItem) -> Boolean,
    onSelectLiveChannel: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onBack: () -> Unit,
    onProgress: (request: PlaybackRequest, positionMs: Long, durationMs: Long) -> Unit,
    nextEpisodeTitle: String? = null,
    onPlayNextEpisode: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var candidateIndex by remember(request) { mutableIntStateOf(0) }
    var retryNonce by remember(request) { mutableIntStateOf(0) }
    var pendingSeekMs by remember(request) { mutableLongStateOf(0L) }
    var finalError by remember(request) { mutableStateOf<String?>(null) }
    var buffering by remember(request) { mutableStateOf(true) }
    var controlsVisible by remember(request) { mutableStateOf(!request.isLive) }
    var browserVisible by remember(request) { mutableStateOf(false) }
    var activePanel by remember(request) { mutableStateOf<PlayerPanel?>(null) }
    var isPlaying by remember(request) { mutableStateOf(false) }
    var isMuted by remember(request) { mutableStateOf(false) }
    var videoHeight by remember(request) { mutableIntStateOf(0) }
    var resizeModeIndex by remember(request) { mutableIntStateOf(0) }
    var playbackSpeed by remember(request) { mutableFloatStateOf(1f) }
    var currentPositionMs by remember(request) { mutableLongStateOf(0L) }
    var manualSeekTargetMs by remember(request) { mutableStateOf<Long?>(null) }
    var durationMs by remember(request) { mutableLongStateOf(0L) }
    var bufferedPercent by remember(request) { mutableIntStateOf(0) }
    var surfaceFocused by remember { mutableStateOf(false) }
    var controlsLocked by remember(request) { mutableStateOf(false) }
    var unlockVisible by remember(request) { mutableStateOf(false) }
    var seekFeedback by remember(request) { mutableStateOf<String?>(null) }
    var seekBarFocused by remember(request) { mutableStateOf(false) }
    var resumePromptVisible by remember(request) {
        mutableStateOf(!request.isLive && request.resumePositionMs >= RESUME_PROMPT_THRESHOLD_MS)
    }
    var nextCountdown by remember(request) { mutableIntStateOf(-1) }
    var audioTracks by remember(request) { mutableStateOf(emptyList<PlayerTrackOption>()) }
    var subtitleTracks by remember(request) { mutableStateOf(emptyList<PlayerTrackOption>()) }
    var videoTracks by remember(request) { mutableStateOf(emptyList<PlayerTrackOption>()) }
    var subtitleSizeIndex by remember(request) { mutableIntStateOf(1) }
    var subtitleRaised by remember(request) { mutableStateOf(false) }

    val resizeModes = remember {
        listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
        )
    }
    val playerFocus = remember { FocusRequester() }
    val primaryFocus = remember { FocusRequester() }
    val resumeFocus = remember { FocusRequester() }
    val unlockFocus = remember { FocusRequester() }
    val nextEpisodePlayFocus = remember { FocusRequester() }
    val nextEpisodeCancelFocus = remember { FocusRequester() }
    val currentChannel = remember(liveCatalog, request.streamId) {
        liveCatalog?.items?.firstOrNull { it.id == request.streamId }
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

    val player = remember(request) {
        val httpDataSource = DefaultHttpDataSource.Factory()
            .setUserAgent("HULK-SA-Player/0.7.2")
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("Accept" to "*/*", "Icy-MetaData" to "1"))
        val dataSource = DefaultDataSource.Factory(context, httpDataSource)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 60_000, 2_500, 5_000)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setLoadControl(loadControl)
            .build()
    }

    fun revealControls() {
        controlsVisible = true
        runCatching { primaryFocus.requestFocus() }
    }

    fun seekBy(deltaMs: Long) {
        if (request.isLive || durationMs <= 0L) return
        val base = manualSeekTargetMs ?: currentPositionMs.takeIf { it > 0L } ?: player.currentPosition.coerceAtLeast(0L)
        val target = (base + deltaMs).coerceIn(0L, durationMs)
        manualSeekTargetMs = target
        currentPositionMs = target
        seekFeedback = if (deltaMs > 0) "+10 ث" else "-10 ث"
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

    fun handleBackAction() {
        when {
            browserVisible -> browserVisible = false
            activePanel != null -> activePanel = null
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
                activePanel = null
                browserVisible = false
            }
            else -> saveAndBack()
        }
    }

    BackHandler { handleBackAction() }

    DisposableEffect(player, request) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
                if (playbackState == Player.STATE_READY) finalError = null
                if (
                    playbackState == Player.STATE_ENDED &&
                    !request.isLive &&
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
            }

            override fun onTracksChanged(tracks: Tracks) {
                audioTracks = extractTrackOptions(tracks, C.TRACK_TYPE_AUDIO)
                subtitleTracks = extractTrackOptions(tracks, C.TRACK_TYPE_TEXT)
                videoTracks = extractTrackOptions(tracks, C.TRACK_TYPE_VIDEO)
            }

            override fun onPlayerError(error: PlaybackException) {
                if (candidateIndex < request.candidates.lastIndex) {
                    pendingSeekMs = player.currentPosition.coerceAtLeast(0L)
                    candidateIndex += 1
                } else {
                    buffering = false
                    controlsVisible = true
                    finalError = if (request.isLive) {
                        "السيرفر لا يرسل بث هذه القناة الان. اعد التحميل او افتح قناة اخرى."
                    } else {
                        "تعذر تشغيل المحتوى. اعد المحاولة او اختر مصدرا اخر عند توفره."
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            if (!request.isLive) {
                onProgress(request, player.currentPosition.coerceAtLeast(0L), player.duration.coerceAtLeast(0L))
            }
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(request, candidateIndex, retryNonce) {
        val url = request.candidates.getOrNull(candidateIndex)
        if (url == null) {
            finalError = "لا يوجد رابط تشغيل صالح لهذا المحتوى."
            buffering = false
            return@LaunchedEffect
        }
        finalError = null
        buffering = true
        player.setMediaItem(MediaItem.fromUri(url))
        val seekTarget = when {
            pendingSeekMs > 0L -> pendingSeekMs
            !resumePromptVisible -> request.resumePositionMs
            else -> 0L
        }
        if (seekTarget > 0L) player.seekTo(seekTarget)
        player.prepare()
        player.playWhenReady = !resumePromptVisible
        pendingSeekMs = 0L
        manualSeekTargetMs = null
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
            if (manualSeekTargetMs == null) {
                currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            }
            durationMs = player.duration.takeIf { it > 0L } ?: 0L
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
        resumePromptVisible,
        controlsLocked,
        seekBarFocused,
        manualSeekTargetMs,
    ) {
        if (
            controlsVisible && !browserVisible && activePanel == null && !resumePromptVisible &&
            !buffering && finalError == null && isPlaying && !controlsLocked &&
            !seekBarFocused && manualSeekTargetMs == null
        ) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(1_000L)
            seekFeedback = null
        }
    }

    LaunchedEffect(nextCountdown) {
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
        resumePromptVisible,
        unlockVisible,
        controlsLocked,
        nextCountdown,
        request.historyKey,
    ) {
        val target = when {
            browserVisible || activePanel != null -> null
            resumePromptVisible -> resumeFocus
            nextCountdown >= 0 -> nextEpisodePlayFocus
            unlockVisible -> unlockFocus
            controlsLocked || !controlsVisible -> playerFocus
            else -> primaryFocus
        }
        if (target != null) {
            withFrameNanos { }
            runCatching { target.requestFocus() }
        }
    }

    val interactionModifier = Modifier
        .pointerInput(request) {
            detectTapGestures(onTap = {
                if (controlsLocked) { unlockVisible = true; controlsVisible = true } else controlsVisible = !controlsVisible
            })
        }
        .pointerInput(request.isLive) {
            if (request.isLive) {
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
                if (
                    event.type != KeyEventType.KeyDown || browserVisible || activePanel != null ||
                    resumePromptVisible || unlockVisible || nextCountdown >= 0
                ) {
                    return@onPreviewKeyEvent false
                }
                val keyCode = event.nativeKeyEvent.keyCode
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
                        seekBy(-SEEK_STEP_MS); true
                    } else false
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> if (!request.isLive && surfaceFocused) {
                        seekBy(SEEK_STEP_MS); true
                    } else false
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> if (!request.isLive && surfaceFocused) {
                        seekBy(-SEEK_STEP_MS); true
                    } else false
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> if (!request.isLive && surfaceFocused) {
                        seekBy(SEEK_STEP_MS); true
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
                    keepScreenOn = true
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
                title = request.title,
                isLive = request.isLive,
                quality = qualityLabel(videoHeight),
                speed = playbackSpeed,
                onBack = ::saveAndBack,
            )
        }

        if (controlsVisible && nextCountdown < 0 && finalError == null && !browserVisible && activePanel == null && !controlsLocked) {
            if (request.isLive) {
                ModernLiveControls(
                    isPlaying = isPlaying,
                    isMuted = isMuted,
                    quality = qualityLabel(videoHeight),
                    audioLabel = selectedTrackLabel(audioTracks, "الصوت"),
                    subtitleLabel = selectedTrackLabel(subtitleTracks, "الترجمة"),
                    resizeLabel = resizeLabel(resizeModeIndex),
                    hasAudio = audioTracks.isNotEmpty(),
                    hasSubtitles = subtitleTracks.isNotEmpty(),
                    onPrevious = { switchRelative(-1) },
                    onNext = { switchRelative(1) },
                    onOpenChannels = { browserVisible = true },
                    onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                    onReload = { pendingSeekMs = 0L; candidateIndex = 0; retryNonce += 1 },
                    onMute = { isMuted = !isMuted; player.volume = if (isMuted) 0f else 1f },
                    onAudio = { activePanel = PlayerPanel.AUDIO },
                    onSubtitles = { activePanel = PlayerPanel.SUBTITLES },
                    onResize = { activePanel = PlayerPanel.RESIZE },
                    onLock = { controlsLocked = true; controlsVisible = false },
                    primaryFocus = primaryFocus,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            } else {
                ModernVodControls(
                    isPlaying = isPlaying,
                    positionMs = currentPositionMs,
                    durationMs = durationMs,
                    bufferedPercent = bufferedPercent,
                    quality = qualityLabel(videoHeight),
                    speed = playbackSpeed,
                    audioLabel = selectedTrackLabel(audioTracks, "الصوت"),
                    subtitleLabel = selectedTrackLabel(subtitleTracks, "الترجمة"),
                    hasAudio = audioTracks.isNotEmpty(),
                    hasSubtitles = subtitleTracks.isNotEmpty(),
                    hasMultipleQualities = videoTracks.distinctBy { it.label }.size > 1,
                    hasMultipleServers = request.candidates.size > 1,
                    onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                    onRewind = { seekBy(-SEEK_STEP_MS) },
                    onForward = { seekBy(SEEK_STEP_MS) },
                    onSeekTo = ::seekToPosition,
                    onSeekingChanged = { seekBarFocused = it },
                    onAudio = { activePanel = PlayerPanel.AUDIO },
                    onSubtitles = { activePanel = PlayerPanel.SUBTITLES },
                    onSpeed = { activePanel = PlayerPanel.SPEED },
                    onResize = { activePanel = PlayerPanel.RESIZE },
                    onQuality = { activePanel = PlayerPanel.QUALITY },
                    onServers = { activePanel = PlayerPanel.SERVERS },
                    onLock = { controlsLocked = true; controlsVisible = false },
                    primaryFocus = primaryFocus,
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
                title = request.title,
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
                message = finalError!!,
                canChooseChannel = request.isLive && liveCatalog?.items?.isNotEmpty() == true,
                canChooseServer = request.candidates.size > 1,
                onRetry = { pendingSeekMs = currentPositionMs; candidateIndex = 0; retryNonce += 1 },
                onChooseChannel = { browserVisible = true },
                onChooseServer = { activePanel = PlayerPanel.SERVERS },
                onBack = onBack,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (browserVisible && request.isLive) {
            LiveChannelBrowser(
                catalog = liveCatalog,
                currentStreamId = request.streamId,
                isFavorite = isFavorite,
                onToggleFavorite = { channel ->
                    val wasFavorite = isFavorite(channel)
                    onToggleFavorite(channel)
                    Toast.makeText(
                        context,
                        if (wasFavorite) "تمت ازالة القناة من المفضلة" else "تمت اضافة القناة الى المفضلة",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onSelectChannel = { channel -> browserVisible = false; onSelectLiveChannel(channel) },
                onClose = { browserVisible = false },
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
                PlayerPanel.SERVERS -> SimpleOptionsPanel(
                    title = "اختيار المصدر",
                    options = request.candidates.mapIndexed { index, _ ->
                        "المصدر ${index + 1}" to {
                            if (index != candidateIndex) {
                                pendingSeekMs = player.currentPosition.coerceAtLeast(0L)
                                candidateIndex = index
                            }
                            activePanel = null
                        }
                    },
                    selectedLabel = "المصدر ${candidateIndex + 1}",
                    onClose = { activePanel = null },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
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
        FocusButton("رجوع", onBack, primary = false, compact = true)
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (isLive) "● مباشر الان" else "HULK Player", color = if (isLive) Color(0xFFFF5A61) else colors.goldBright, fontSize = 11.sp)
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
    audioLabel: String,
    subtitleLabel: String,
    hasAudio: Boolean,
    hasSubtitles: Boolean,
    hasMultipleQualities: Boolean,
    hasMultipleServers: Boolean,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekingChanged: (Boolean) -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onSpeed: () -> Unit,
    onResize: () -> Unit,
    onQuality: () -> Unit,
    onServers: () -> Unit,
    onLock: () -> Unit,
    primaryFocus: FocusRequester,
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
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            item { FocusButton("-10 ث", onRewind, primary = false, compact = true) }
            item { FocusButton(if (isPlaying) "ايقاف مؤقت" else "تشغيل", onPlayPause, modifier = Modifier.focusRequester(primaryFocus), compact = true) }
            item { FocusButton("+10 ث", onForward, primary = false, compact = true) }
            if (hasAudio) item { FocusButton(audioLabel, onAudio, primary = false, compact = true) }
            item { FocusButton(if (hasSubtitles) subtitleLabel else "الترجمة", onSubtitles, primary = false, compact = true) }
            item { FocusButton("السرعة ${speedLabel(speed)}", onSpeed, primary = false, compact = true) }
            item { FocusButton("حجم الصورة", onResize, primary = false, compact = true) }
            if (hasMultipleQualities) item { FocusButton("الجودة", onQuality, primary = false, compact = true) }
            if (hasMultipleServers) item { FocusButton("المصدر", onServers, primary = false, compact = true) }
            item { FocusButton("قفل التحكم", onLock, primary = false, compact = true) }
        }
    }
}

@Composable
private fun ModernLiveControls(
    isPlaying: Boolean,
    isMuted: Boolean,
    quality: String,
    audioLabel: String,
    subtitleLabel: String,
    resizeLabel: String,
    hasAudio: Boolean,
    hasSubtitles: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenChannels: () -> Unit,
    onPlayPause: () -> Unit,
    onReload: () -> Unit,
    onMute: () -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onResize: () -> Unit,
    onLock: () -> Unit,
    primaryFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .97f))))
            .navigationBarsPadding()
            .padding(
                start = if (adaptiveUi.isTelevision) 34.dp else 24.dp,
                end = if (adaptiveUi.isTelevision) 34.dp else 24.dp,
                top = 12.dp,
                bottom = if (adaptiveUi.isTelevision) 32.dp else 24.dp,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("● مباشر الان", color = Color(0xFFFF4E55), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text(quality, color = colors.textMuted, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(
                if (adaptiveUi.isTelevision || adaptiveUi.inputMode == HulkInputMode.REMOTE) {
                    "السهم لاعلى: القناة التالية  •  السهم لاسفل: القناة السابقة"
                } else {
                    "اسحب لاعلى للقناة التالية  •  اسحب لاسفل للقناة السابقة"
                },
                color = colors.textMuted,
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            item { FocusButton("القناة السابقة", onPrevious, primary = false, compact = true) }
            item { FocusButton("القناة التالية", onNext, compact = true) }
            item { FocusButton("القنوات", onOpenChannels, modifier = Modifier.focusRequester(primaryFocus), primary = false, compact = true) }
            item { FocusButton(if (isPlaying) "ايقاف مؤقت" else "تشغيل", onPlayPause, primary = false, compact = true) }
            item { FocusButton("اعادة تحميل", onReload, primary = false, compact = true) }
            item { FocusButton(if (isMuted) "تشغيل الصوت" else "كتم الصوت", onMute, primary = false, compact = true) }
            if (hasAudio) item { FocusButton(audioLabel, onAudio, primary = false, compact = true) }
            if (hasSubtitles) item { FocusButton(subtitleLabel, onSubtitles, primary = false, compact = true) }
            item { FocusButton("الصورة: $resizeLabel", onResize, primary = false, compact = true) }
            item { FocusButton("قفل التحكم", onLock, primary = false, compact = true) }
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
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    var previewMs by remember { mutableLongStateOf(positionMs) }

    DisposableEffect(Unit) {
        onDispose { onSeekingChanged(false) }
    }

    LaunchedEffect(positionMs, durationMs, focused) {
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
        if (focused) {
            Text(
                "${formatTime(previewMs)}  •  حرك يمين ويسار ثم اضغط OK",
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
                .height(if (focused) 13.dp else 8.dp)
                .clip(shape)
                .background(Color.White.copy(alpha = .18f))
                .border(if (focused) 2.dp else 0.dp, if (focused) colors.goldBright else Color.Transparent, shape)
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
                            previewMs = (previewMs - SEEK_STEP_MS).coerceAtLeast(0L)
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            previewMs = (previewMs + SEEK_STEP_MS).coerceAtMost(durationMs)
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
    message: String,
    canChooseChannel: Boolean,
    canChooseServer: Boolean,
    onRetry: () -> Unit,
    onChooseChannel: () -> Unit,
    onChooseServer: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth(.78f)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = .93f))
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ErrorNotice(message)
        Spacer(Modifier.height(15.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FocusButton("اعادة المحاولة", onRetry)
            if (canChooseChannel) FocusButton("اختيار قناة", onChooseChannel, primary = false)
            if (canChooseServer) FocusButton("اختيار مصدر", onChooseServer, primary = false)
            FocusButton("رجوع", onBack, primary = false)
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
    PlayerSidePanel(title, onClose, modifier) {
        options.forEach { (label, action) ->
            FocusButton(label, action, primary = label == selectedLabel, compact = true, modifier = Modifier.fillMaxWidth())
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
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .62f)))
    Column(
        modifier = modifier
            .padding(horizontal = if (adaptiveUi.isTelevision) 30.dp else 12.dp, vertical = if (adaptiveUi.isTelevision) 24.dp else 10.dp)
            .fillMaxHeight(if (adaptiveUi.isTelevision) .90f else .94f)
            .width(if (adaptiveUi.isTelevision) 500.dp else 340.dp)
            .clip(panelShape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF080906), Color(0xFA15170F))))
            .border(1.dp, colors.gold.copy(alpha = .42f), panelShape)
            .padding(horizontal = if (adaptiveUi.isTelevision) 26.dp else 18.dp, vertical = if (adaptiveUi.isTelevision) 22.dp else 16.dp),
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

@Composable
private fun LiveChannelBrowser(
    catalog: Catalog?,
    currentStreamId: Int,
    isFavorite: (ContentItem) -> Boolean,
    onToggleFavorite: (ContentItem) -> Unit,
    onSelectChannel: (ContentItem) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val current = remember(catalog, currentStreamId) {
        catalog?.items?.firstOrNull { it.id == currentStreamId }
    }
    var selectedCategory by remember(catalog, currentStreamId) {
        mutableStateOf(current?.categoryId ?: catalog?.categories?.firstOrNull()?.id)
    }
    var searchQuery by remember { mutableStateOf("") }
    var favoriteIds by remember(catalog) {
        mutableStateOf(catalog?.items.orEmpty().filter(isFavorite).map(ContentItem::id).toSet())
    }
    val categoryArtwork = remember(catalog) {
        catalog?.items.orEmpty()
            .filter { !it.posterUrl.isNullOrBlank() }
            .groupBy(ContentItem::categoryId)
            .mapValues { (_, channels) -> channels.first() }
    }

    val categoryChannels = when (selectedCategory) {
        PLAYER_FAVORITES_CATEGORY -> catalog?.items.orEmpty().filter { it.id in favoriteIds }
        null -> catalog?.items.orEmpty()
        else -> catalog?.items.orEmpty().filter { it.categoryId == selectedCategory }
    }
    val normalizedQuery = searchQuery.trim()
    val visible = if (normalizedQuery.isBlank()) {
        categoryChannels
    } else {
        catalog?.items.orEmpty().filter { channel ->
            channel.name.contains(normalizedQuery, ignoreCase = true) ||
                channel.id.toString().contains(normalizedQuery)
        }
    }

    val listState = rememberLazyListState()
    val channelFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val focusIndex = visible.indexOfFirst { it.id == currentStreamId }.takeIf { it >= 0 } ?: 0

    LaunchedEffect(visible, selectedCategory, normalizedQuery) {
        if (visible.isNotEmpty() && normalizedQuery.isBlank()) {
            listState.scrollToItem(focusIndex)
            withFrameNanos { }
            runCatching { channelFocus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .72f)))
    Column(
        modifier = modifier
            .fillMaxHeight(.80f)
            .fillMaxWidth(.76f)
            .widthIn(max = 920.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF15170F), Color(0xFF080906))))
            .border(1.dp, colors.gold.copy(alpha = .46f), RoundedCornerShape(24.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandBadge(Modifier.size(50.dp))
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text("القنوات المباشرة", color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${visible.size} قناة  •  اضغط مطولا OK لاضافة او ازالة المفضلة",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                )
            }
            FocusButton("اغلاق", onClose, primary = false, compact = true)
        }

        Spacer(Modifier.height(11.dp))
        HulkTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = "بحث سريع عن قناة",
            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
        )
        Spacer(Modifier.height(11.dp))

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = .045f))
                    .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(18.dp))
                    .padding(10.dp),
            ) {
                Text("الفئات", color = colors.goldBright, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    item {
                        FocusButton(
                            text = "★ المفضلة (${favoriteIds.size})",
                            onClick = { selectedCategory = PLAYER_FAVORITES_CATEGORY; searchQuery = "" },
                            modifier = Modifier.fillMaxWidth(),
                            primary = selectedCategory == PLAYER_FAVORITES_CATEGORY && searchQuery.isBlank(),
                            compact = true,
                        )
                    }
                    items(catalog?.categories.orEmpty(), key = { it.id }) { category ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            categoryArtwork[category.id]?.let { channel ->
                                ChannelLogo(channel, Modifier.size(32.dp))
                            }
                            FocusButton(
                                text = category.name,
                                onClick = { selectedCategory = category.id; searchQuery = "" },
                                modifier = Modifier.weight(1f),
                                primary = selectedCategory == category.id && searchQuery.isBlank(),
                                compact = true,
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = .18f))
                    .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(18.dp))
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                normalizedQuery.isNotBlank() -> "نتائج البحث"
                                selectedCategory == PLAYER_FAVORITES_CATEGORY -> "القنوات المفضلة"
                                else -> catalog?.categories?.firstOrNull { it.id == selectedCategory }?.name ?: "كل القنوات"
                            },
                            color = colors.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        current?.let {
                            Text("القناة الحالية  •  ${it.name}", color = colors.goldBright, fontSize = 10.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                when {
                    catalog == null -> LoadingRing(
                        label = "جاري تجهيز القنوات…",
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 90.dp),
                    )
                    visible.isEmpty() -> Text(
                        when {
                            normalizedQuery.isNotBlank() -> "لا توجد قناة مطابقة للبحث"
                            selectedCategory == PLAYER_FAVORITES_CATEGORY -> "لا توجد قنوات مفضلة"
                            else -> "لا توجد قنوات في هذه الفئة"
                        },
                        color = colors.textMuted,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 90.dp),
                    )
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 18.dp),
                    ) {
                        items(visible, key = ContentItem::id) { channel ->
                            val index = visible.indexOf(channel)
                            val favorite = channel.id in favoriteIds
                            ChannelListItem(
                                item = channel,
                                selected = channel.id == currentStreamId,
                                onFocused = {},
                                onClick = { onSelectChannel(channel) },
                                isFavorite = favorite,
                                onLongClick = {
                                    favoriteIds = if (favorite) favoriteIds - channel.id else favoriteIds + channel.id
                                    onToggleFavorite(channel)
                                },
                                modifier = if (index == focusIndex && normalizedQuery.isBlank()) {
                                    Modifier.focusRequester(channelFocus)
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

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
    val language = format.language?.let(::languageLabel)
    val explicit = format.label?.takeIf(String::isNotBlank)
    return when (type) {
        C.TRACK_TYPE_VIDEO -> qualityLabel(format.height)
        C.TRACK_TYPE_AUDIO -> explicit ?: language ?: "مسار صوت ${index + 1}"
        C.TRACK_TYPE_TEXT -> explicit ?: language ?: "ترجمة ${index + 1}"
        else -> explicit ?: "مسار ${index + 1}"
    }
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
    options.firstOrNull { it.selected }?.label ?: fallback

private fun languageLabel(code: String): String = when (code.lowercase(Locale.ROOT).substringBefore('-')) {
    "ar", "ara" -> "العربية"
    "en", "eng" -> "English"
    "fr", "fra", "fre" -> "Français"
    "es", "spa" -> "Español"
    "tr", "tur" -> "Türkçe"
    "de", "deu", "ger" -> "Deutsch"
    else -> code.uppercase(Locale.ROOT)
}

private fun qualityLabel(height: Int): String = when {
    height >= 2160 -> "4K"
    height >= 1440 -> "1440p"
    height >= 1080 -> "1080p"
    height >= 720 -> "720p"
    height >= 480 -> "480p"
    height > 0 -> "${height}p"
    else -> "تلقائي"
}

private fun qualitySortValue(label: String): Int = when (label) {
    "4K" -> 2160
    else -> label.removeSuffix("p").toIntOrNull() ?: 0
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

internal fun relativeChannelIndex(currentIndex: Int, delta: Int, size: Int): Int {
    require(size > 0)
    return (((currentIndex + delta) % size) + size) % size
}
