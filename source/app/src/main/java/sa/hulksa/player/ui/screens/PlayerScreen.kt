package sa.hulksa.player.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.PlaybackRequest
import sa.hulksa.player.ui.components.BrandBadge
import sa.hulksa.player.ui.components.ChannelListItem
import sa.hulksa.player.ui.components.ErrorNotice
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.theme.LocalHulkColors

private const val LIVE_CONTROLS_TIMEOUT_MS = 4_500L
private const val PLAYER_FAVORITES_CATEGORY = "__player_favorites__"

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    request: PlaybackRequest,
    liveCatalog: Catalog?,
    isFavorite: (ContentItem) -> Boolean,
    onSelectLiveChannel: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onBack: () -> Unit,
    onProgress: (positionMs: Long, durationMs: Long) -> Unit,
) {
    val context = LocalContext.current
    var candidateIndex by remember(request) { mutableIntStateOf(0) }
    var retryNonce by remember(request) { mutableIntStateOf(0) }
    var finalError by remember(request) { mutableStateOf<String?>(null) }
    var buffering by remember(request) { mutableStateOf(true) }
    var controlsVisible by remember(request) { mutableStateOf(true) }
    var browserVisible by remember(request) { mutableStateOf(false) }
    var isPlaying by remember(request) { mutableStateOf(false) }
    var isMuted by remember(request) { mutableStateOf(false) }
    var videoHeight by remember(request) { mutableIntStateOf(0) }
    var resizeModeIndex by remember(request) { mutableIntStateOf(0) }
    var surfaceFocused by remember { mutableStateOf(false) }
    val resizeModes = remember {
        listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
        )
    }
    val playerFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
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
            val currentIndex = channelSequence.indexOfFirst { it.id == request.streamId }
                .takeIf { it >= 0 }
                ?: 0
            val targetIndex = relativeChannelIndex(currentIndex, delta, channelSequence.size)
            onSelectLiveChannel(channelSequence[targetIndex])
        }
    }

    val player = remember(request) {
        val httpDataSource = DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.21 LibVLC/3.0.21")
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(25_000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("Accept" to "*/*", "Icy-MetaData" to "1"))
        val dataSource = DefaultDataSource.Factory(context, httpDataSource)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(12_000, 50_000, 2_000, 4_000)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setLoadControl(loadControl)
            .build()
    }

    BackHandler {
        if (browserVisible) browserVisible = false else onBack()
    }

    DisposableEffect(player, request) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
                if (playbackState == Player.STATE_READY) finalError = null
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoHeight = videoSize.height
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!request.isLive && candidateIndex < request.candidates.lastIndex) {
                    candidateIndex += 1
                } else {
                    buffering = false
                    controlsVisible = true
                    finalError = if (request.isLive) {
                        "السيرفر لا يرسل بث هذه القناة الآن. جرب إعادة التحميل أو افتح قناة أخرى."
                    } else {
                        "تعذر تشغيل هذا المحتوى الآن. جرب إعادة التحميل أو افتح محتوى آخر."
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            if (!request.isLive) {
                onProgress(player.currentPosition.coerceAtLeast(0L), player.duration.coerceAtLeast(0L))
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
        if (!request.isLive && request.resumePositionMs > 0L) player.seekTo(request.resumePositionMs)
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(player, request) {
        if (request.isLive) return@LaunchedEffect
        while (isActive) {
            delay(5_000)
            onProgress(player.currentPosition.coerceAtLeast(0L), player.duration.coerceAtLeast(0L))
        }
    }

    LaunchedEffect(controlsVisible, buffering, finalError, isPlaying, browserVisible) {
        if (controlsVisible && !browserVisible && !buffering && finalError == null && isPlaying) {
            delay(LIVE_CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(controlsVisible, finalError, browserVisible, request.isLive) {
        if (browserVisible) return@LaunchedEffect
        runCatching {
            if (request.isLive || (!controlsVisible && finalError == null)) {
                playerFocus.requestFocus()
            } else {
                backFocus.requestFocus()
            }
        }
    }

    val interactionModifier = if (request.isLive) {
        Modifier.pointerInput(request) {
            detectTapGestures(onTap = { controlsVisible = !controlsVisible })
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocus)
            .onFocusChanged { surfaceFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || browserVisible) return@onPreviewKeyEvent false
                val keyCode = event.nativeKeyEvent.keyCode
                if (request.isLive && surfaceFocused) {
                    when (keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        -> {
                            browserVisible = true
                            controlsVisible = true
                            return@onPreviewKeyEvent true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_UP,
                        AndroidKeyEvent.KEYCODE_CHANNEL_UP,
                        AndroidKeyEvent.KEYCODE_MEDIA_NEXT,
                        -> {
                            switchRelative(1)
                            return@onPreviewKeyEvent true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                        AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        -> {
                            switchRelative(-1)
                            return@onPreviewKeyEvent true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> if (!controlsVisible) {
                            switchRelative(1)
                            return@onPreviewKeyEvent true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> if (!controlsVisible) {
                            switchRelative(-1)
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                if (!controlsVisible) {
                    controlsVisible = true
                    true
                } else {
                    false
                }
            }
            .then(interactionModifier),
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = !request.isLive
                    controllerAutoShow = !request.isLive
                    controllerShowTimeoutMs = 5_000
                    resizeMode = resizeModes[resizeModeIndex]
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility: Int ->
                            if (!request.isLive) controlsVisible = visibility == View.VISIBLE
                        },
                    )
                    keepScreenOn = true
                    this.player = player
                }
            },
            update = { view ->
                view.player = player
                view.useController = !request.isLive
                view.resizeMode = resizeModes[resizeModeIndex]
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (controlsVisible && finalError == null && !browserVisible) {
            PlayerTopBar(request.title, request.isLive, onBack, backFocus)
        }

        if (request.isLive && controlsVisible && finalError == null && !browserVisible) {
            LivePlayerControls(
                isPlaying = isPlaying,
                isMuted = isMuted,
                quality = qualityLabel(videoHeight),
                resizeLabel = resizeLabel(resizeModeIndex),
                onPrevious = { switchRelative(-1) },
                onNext = { switchRelative(1) },
                onOpenChannels = { browserVisible = true },
                onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                onReload = { candidateIndex = 0; retryNonce += 1 },
                onMute = {
                    isMuted = !isMuted
                    player.volume = if (isMuted) 0f else 1f
                },
                onResize = { resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (buffering && finalError == null) {
            LoadingRing(
                label = if (request.isLive) "جاري تشغيل القناة…" else "جاري تجهيز المشاهدة…",
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (finalError != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(.78f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = .92f))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ErrorNotice(finalError!!)
                Spacer(Modifier.height(15.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusButton("إعادة التحميل", { candidateIndex = 0; retryNonce += 1 })
                    if (request.isLive && liveCatalog?.items?.isNotEmpty() == true) {
                        FocusButton("اختيار قناة", { browserVisible = true }, primary = false)
                    }
                    FocusButton("رجوع", onBack, modifier = Modifier.focusRequester(backFocus), primary = false)
                }
            }
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
                        if (wasFavorite) "تمت إزالة القناة من المفضلة" else "تمت إضافة القناة إلى المفضلة",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onSelectChannel = { channel ->
                    browserVisible = false
                    onSelectLiveChannel(channel)
                },
                onClose = { browserVisible = false },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
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
    val current = remember(catalog, currentStreamId) { catalog?.items?.firstOrNull { it.id == currentStreamId } }
    var selectedCategory by remember(catalog, currentStreamId) {
        mutableStateOf(current?.categoryId ?: catalog?.categories?.firstOrNull()?.id)
    }
    val visible = when (selectedCategory) {
        PLAYER_FAVORITES_CATEGORY -> catalog?.items.orEmpty().filter(isFavorite)
        null -> emptyList()
        else -> catalog?.items.orEmpty().filter { it.categoryId == selectedCategory }
    }
    val listState = rememberLazyListState()
    val channelFocus = remember { FocusRequester() }
    val focusIndex = visible.indexOfFirst { it.id == currentStreamId }.takeIf { it >= 0 } ?: 0

    LaunchedEffect(visible, selectedCategory) {
        if (visible.isNotEmpty()) {
            listState.scrollToItem(focusIndex)
            delay(70)
            runCatching { channelFocus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .54f)))
    Column(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(.72f)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF080906), Color(0xFA11130E)),
                ),
            )
            .border(1.dp, colors.gold.copy(alpha = .26f))
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("الفئات والقنوات", color = colors.text, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("اضغط مطولا زر OK لإضافة القناة إلى المفضلة", color = colors.textMuted, fontSize = 10.sp)
            }
            FocusButton("إغلاق", onClose, primary = false, compact = true)
        }
        Spacer(Modifier.height(13.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            item {
                FocusButton(
                    "★ المفضلة",
                    { selectedCategory = PLAYER_FAVORITES_CATEGORY },
                    primary = selectedCategory == PLAYER_FAVORITES_CATEGORY,
                    compact = true,
                )
            }
            items(catalog?.categories.orEmpty(), key = { it.id }) { category ->
                FocusButton(
                    category.name,
                    { selectedCategory = category.id },
                    primary = selectedCategory == category.id,
                    compact = true,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (catalog == null) {
            LoadingRing(label = "جاري تجهيز القنوات…", modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 70.dp))
        } else if (visible.isEmpty()) {
            Text(
                if (selectedCategory == PLAYER_FAVORITES_CATEGORY) "لا توجد قنوات مفضلة" else "لا توجد قنوات في هذه الفئة",
                color = colors.textMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 70.dp),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(visible, key = ContentItem::id) { channel ->
                    val index = visible.indexOf(channel)
                    ChannelListItem(
                        item = channel,
                        selected = channel.id == currentStreamId,
                        onFocused = {},
                        onClick = { onSelectChannel(channel) },
                        isFavorite = isFavorite(channel),
                        onLongClick = { onToggleFavorite(channel) },
                        modifier = if (index == focusIndex) Modifier.focusRequester(channelFocus) else Modifier,
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
    onBack: () -> Unit,
    backFocus: FocusRequester,
) {
    val colors = LocalHulkColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .9f), Color.Transparent)))
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        FocusButton("رجوع", onBack, modifier = Modifier.focusRequester(backFocus), primary = false, compact = true)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(if (isLive) "● مباشر الآن" else "تشغيل عند الطلب", color = colors.goldBright, fontSize = 11.sp)
        }
        BrandBadge(Modifier.size(46.dp))
    }
}

@Composable
private fun LivePlayerControls(
    isPlaying: Boolean,
    isMuted: Boolean,
    quality: String,
    resizeLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenChannels: () -> Unit,
    onPlayPause: () -> Unit,
    onReload: () -> Unit,
    onMute: () -> Unit,
    onResize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .95f))))
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("● مباشر الآن", color = Color(0xFFFF4E55), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(9.dp))
            Text(quality, color = colors.textMuted, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("OK القنوات  •  ↑ التالية  •  ↓ السابقة", color = colors.textMuted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(11.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            item { FocusButton("القناة السابقة", onPrevious, primary = false, compact = true) }
            item { FocusButton("القناة التالية", onNext, compact = true) }
            item { FocusButton("الفئات والقنوات", onOpenChannels, primary = false, compact = true) }
            item { FocusButton(if (isPlaying) "إيقاف مؤقت" else "تشغيل", onPlayPause, primary = false, compact = true) }
            item { FocusButton("إعادة تحميل", onReload, primary = false, compact = true) }
            item { FocusButton(if (isMuted) "تشغيل الصوت" else "كتم الصوت", onMute, primary = false, compact = true) }
            item { FocusButton("حجم الصورة: $resizeLabel", onResize, primary = false, compact = true) }
        }
    }
}

private fun qualityLabel(height: Int): String = when {
    height >= 2160 -> "4K"
    height >= 1440 -> "QHD"
    height >= 1080 -> "FHD"
    height >= 720 -> "HD"
    height > 0 -> "${height}p"
    else -> "جودة تلقائية"
}

private fun resizeLabel(index: Int): String = when (index) {
    1 -> "تكبير"
    2 -> "ملء"
    else -> "ملائم"
}

internal fun relativeChannelIndex(currentIndex: Int, delta: Int, size: Int): Int {
    require(size > 0)
    return (((currentIndex + delta) % size) + size) % size
}
