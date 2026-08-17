package sa.hulksa.player.ui.screens

import android.content.Context
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.Episode
import sa.hulksa.player.model.PlaybackRequest
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.components.ChannelLogo
import sa.hulksa.player.ui.theme.LocalHulkColors

private const val LIVE_TV_PRO_HISTORY_PREFS = "live_player_history"
private const val LIVE_TV_PRO_HISTORY_IDS = "ids"
private const val ANDROID_KEYCODE_LAST_CHANNEL = 229
private const val LIVE_TV_PRO_CONTROLS_HINT_TIMEOUT_MS = 5_200L
private const val LIVE_TV_PRO_ZAP_COMMIT_DELAY_MS = 220L
private const val LIVE_TV_PRO_ZAP_INDICATOR_TIMEOUT_MS = 2_400L

internal data class PlayerProEpisodeNeighbors(
    val previous: Episode?,
    val next: Episode?,
)

internal fun playerProEpisodeNeighbors(
    episodes: List<Episode>,
    currentStreamId: Int,
): PlayerProEpisodeNeighbors {
    val ordered = episodes.sortedWith(
        compareBy(Episode::season, Episode::episodeNumber, Episode::id),
    )
    val currentIndex = ordered.indexOfFirst { it.id == currentStreamId }
    if (currentIndex < 0) return PlayerProEpisodeNeighbors(previous = null, next = null)

    return PlayerProEpisodeNeighbors(
        previous = ordered.getOrNull(currentIndex - 1),
        next = ordered.getOrNull(currentIndex + 1),
    )
}

internal fun playerProEpisodeLabel(episode: Episode): String =
    "الموسم ${episode.season} • الحلقة ${episode.episodeNumber} • ${episode.title}"

internal fun playerProLiveNavigationSequence(
    channels: List<ContentItem>,
    currentStreamId: Int,
    launchContext: String,
    favoriteIds: Set<Int>,
    recentIds: List<Int>,
): List<ContentItem> {
    if (channels.isEmpty()) return emptyList()
    val byId = channels.associateBy(ContentItem::id)
    val contextual = when (launchContext) {
        LIVE_TV_PRO_CONTEXT_FAVORITES -> channels.filter { it.id in favoriteIds }
        LIVE_TV_PRO_CONTEXT_RECENT -> recentIds.mapNotNull(byId::get).distinctBy(ContentItem::id)
        LIVE_TV_PRO_CONTEXT_ALL -> channels
        else -> channels.filter { it.categoryId == launchContext }
    }
    return contextual.takeIf { sequence -> sequence.any { it.id == currentStreamId } }
        ?: liveTvProChannelSequence(channels, currentStreamId)
}

internal fun playerProQueuedRelativeChannel(
    sequence: List<ContentItem>,
    currentStreamId: Int,
    pendingStreamId: Int?,
    delta: Int,
): ContentItem? {
    if (sequence.isEmpty()) return null
    val anchorStreamId = pendingStreamId
        ?.takeIf { pendingId -> sequence.any { it.id == pendingId } }
        ?: currentStreamId
    val anchorIndex = sequence.indexOfFirst { it.id == anchorStreamId }.takeIf { it >= 0 } ?: 0
    val targetIndex = (((anchorIndex + delta) % sequence.size) + sequence.size) % sequence.size
    return sequence[targetIndex]
}

/**
 * Player Pro entry point shared by VOD/series and Live TV Pro.
 *
 * Series continuity remains isolated here. For Live, this layer owns recent history, hardware
 * Channel +/- and Last Channel actions, the v1.6 TV browser, and Channel Zapping Pro coalescing.
 * PlayerScreen remains the qualified playback/control core.
 */
@Composable
fun PlayerProScreen(
    request: PlaybackRequest,
    liveCatalog: Catalog?,
    isFavorite: (ContentItem) -> Boolean,
    onSelectLiveChannel: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onBack: () -> Unit,
    onProgress: (request: PlaybackRequest, positionMs: Long, durationMs: Long) -> Unit,
    previousEpisode: Episode?,
    nextEpisode: Episode?,
    onPlayPreviousEpisode: (() -> Unit)?,
    onPlayNextEpisode: (() -> Unit)?,
) {
    val context = LocalContext.current
    val adaptiveUi = LocalAdaptiveUi.current
    val liveChannels = liveCatalog?.items.orEmpty()
    val liveHistoryPrefs = remember(context) {
        context.getSharedPreferences(LIVE_TV_PRO_HISTORY_PREFS, Context.MODE_PRIVATE)
    }
    var recentChannelIds by remember(liveCatalog) {
        mutableStateOf(
            liveHistoryPrefs.getString(LIVE_TV_PRO_HISTORY_IDS, "")
                .orEmpty()
                .split(',')
                .mapNotNull(String::toIntOrNull),
        )
    }
    var liveBrowserVisible by remember(request.historyKey) { mutableStateOf(false) }
    var liveControlsLikelyVisible by remember(request.historyKey) { mutableStateOf(false) }
    var liveControlsInteractionTick by remember(request.historyKey) { mutableIntStateOf(0) }
    var pendingLiveChannelId by remember(request.streamId, liveCatalog) { mutableStateOf<Int?>(null) }
    var liveZapInteractionTick by remember(request.streamId) { mutableIntStateOf(0) }
    var liveZapIndicatorChannelId by remember(liveCatalog) { mutableStateOf<Int?>(null) }
    var liveZapIndicatorTick by remember(liveCatalog) { mutableIntStateOf(0) }

    LaunchedEffect(liveControlsLikelyVisible, liveControlsInteractionTick) {
        if (liveControlsLikelyVisible) {
            delay(LIVE_TV_PRO_CONTROLS_HINT_TIMEOUT_MS)
            liveControlsLikelyVisible = false
        }
    }

    LaunchedEffect(request.isLive, request.streamId, liveCatalog) {
        if (request.isLive && liveChannels.any { it.id == request.streamId }) {
            val updated = liveTvProUpdateRecentChannelIds(
                existingIds = recentChannelIds,
                currentStreamId = request.streamId,
            )
            if (updated != recentChannelIds) {
                recentChannelIds = updated
                liveHistoryPrefs.edit()
                    .putString(LIVE_TV_PRO_HISTORY_IDS, updated.joinToString(","))
                    .apply()
            }
        }
    }

    val lastChannel = remember(request.isLive, request.streamId, liveCatalog, recentChannelIds) {
        if (!request.isLive) {
            null
        } else {
            liveTvProLastChannel(
                channels = liveChannels,
                recentIds = recentChannelIds,
                currentStreamId = request.streamId,
            )
        }
    }
    val liveZapIndicatorChannel = remember(liveCatalog, liveZapIndicatorChannelId) {
        val indicatorId = liveZapIndicatorChannelId
        if (indicatorId == null) null else liveChannels.firstOrNull { it.id == indicatorId }
    }

    LaunchedEffect(
        request.isLive,
        request.streamId,
        pendingLiveChannelId,
        liveZapInteractionTick,
        liveCatalog,
    ) {
        if (!request.isLive) {
            pendingLiveChannelId = null
            return@LaunchedEffect
        }
        val targetId = pendingLiveChannelId ?: return@LaunchedEffect
        val interaction = liveZapInteractionTick
        delay(LIVE_TV_PRO_ZAP_COMMIT_DELAY_MS)
        if (pendingLiveChannelId != targetId || liveZapInteractionTick != interaction) {
            return@LaunchedEffect
        }
        val target = liveChannels.firstOrNull { it.id == targetId }
        pendingLiveChannelId = null
        if (target != null && target.id != request.streamId) {
            onSelectLiveChannel(target)
        }
    }

    LaunchedEffect(request.isLive, liveZapIndicatorChannelId, liveZapIndicatorTick) {
        if (!request.isLive) {
            liveZapIndicatorChannelId = null
            return@LaunchedEffect
        }
        val indicatorId = liveZapIndicatorChannelId ?: return@LaunchedEffect
        val interaction = liveZapIndicatorTick
        delay(LIVE_TV_PRO_ZAP_INDICATOR_TIMEOUT_MS)
        if (liveZapIndicatorChannelId == indicatorId && liveZapIndicatorTick == interaction) {
            liveZapIndicatorChannelId = null
        }
    }

    fun dismissLiveZapIndicator() {
        liveZapIndicatorChannelId = null
        liveZapIndicatorTick += 1
    }

    fun showLiveZapIndicator(channel: ContentItem) {
        liveZapIndicatorChannelId = channel.id
        liveZapIndicatorTick += 1
    }

    fun cancelPendingLiveZap() {
        pendingLiveChannelId = null
        liveZapInteractionTick += 1
    }

    fun markLiveControlsInteraction() {
        cancelPendingLiveZap()
        dismissLiveZapIndicator()
        liveControlsLikelyVisible = true
        liveControlsInteractionTick += 1
    }

    fun liveNavigationSequence(): List<ContentItem> = playerProLiveNavigationSequence(
        channels = liveChannels,
        currentStreamId = request.streamId,
        launchContext = context.liveTvProLaunchContext(),
        favoriteIds = liveChannels.asSequence().filter(isFavorite).map(ContentItem::id).toSet(),
        recentIds = recentChannelIds,
    )

    fun queueLiveRelative(delta: Int): Boolean {
        if (!request.isLive) return false
        val channel = playerProQueuedRelativeChannel(
            sequence = liveNavigationSequence(),
            currentStreamId = request.streamId,
            pendingStreamId = pendingLiveChannelId,
            delta = delta,
        ) ?: return false
        if (channel.id == request.streamId && pendingLiveChannelId == null) return false
        liveBrowserVisible = false
        liveControlsLikelyVisible = false
        pendingLiveChannelId = channel.id
        liveZapInteractionTick += 1
        showLiveZapIndicator(channel)
        return true
    }

    fun queuePlayerRequestedLiveChannel(channel: ContentItem) {
        if (!request.isLive) {
            onSelectLiveChannel(channel)
            return
        }
        if (channel.id == request.streamId && pendingLiveChannelId == null) return

        val sequence = liveTvProChannelSequence(
            channels = liveChannels,
            currentStreamId = request.streamId,
        )
        val currentIndex = sequence.indexOfFirst { it.id == request.streamId }
        val requestedIndex = sequence.indexOfFirst { it.id == channel.id }
        val relativeDelta = if (currentIndex >= 0 && requestedIndex >= 0 && sequence.size > 1) {
            val nextIndex = (currentIndex + 1) % sequence.size
            val previousIndex = (currentIndex - 1 + sequence.size) % sequence.size
            when (requestedIndex) {
                nextIndex -> 1
                previousIndex -> -1
                else -> null
            }
        } else {
            null
        }

        if (relativeDelta != null) {
            queueLiveRelative(relativeDelta)
        } else {
            cancelPendingLiveZap()
            dismissLiveZapIndicator()
            liveBrowserVisible = false
            liveControlsLikelyVisible = false
            onSelectLiveChannel(channel)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                val keyCode = event.nativeKeyEvent.keyCode
                if (request.isLive) {
                    if (liveBrowserVisible) return@onPreviewKeyEvent false

                    when (keyCode) {
                        AndroidKeyEvent.KEYCODE_CHANNEL_UP,
                        AndroidKeyEvent.KEYCODE_MEDIA_NEXT,
                        -> {
                            liveControlsLikelyVisible = false
                            return@onPreviewKeyEvent queueLiveRelative(1)
                        }

                        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                        AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        -> {
                            liveControlsLikelyVisible = false
                            return@onPreviewKeyEvent queueLiveRelative(-1)
                        }

                        ANDROID_KEYCODE_LAST_CHANNEL -> {
                            val channel = lastChannel ?: return@onPreviewKeyEvent false
                            cancelPendingLiveZap()
                            showLiveZapIndicator(channel)
                            liveControlsLikelyVisible = false
                            onSelectLiveChannel(channel)
                            return@onPreviewKeyEvent true
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        -> {
                            if (!liveControlsLikelyVisible) {
                                cancelPendingLiveZap()
                                dismissLiveZapIndicator()
                                liveBrowserVisible = true
                                return@onPreviewKeyEvent true
                            }
                            markLiveControlsInteraction()
                            return@onPreviewKeyEvent false
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                        AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        -> {
                            markLiveControlsInteraction()
                            return@onPreviewKeyEvent false
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_UP,
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                        -> {
                            liveControlsLikelyVisible = false
                            return@onPreviewKeyEvent false
                        }

                        AndroidKeyEvent.KEYCODE_BACK,
                        AndroidKeyEvent.KEYCODE_ESCAPE,
                        -> {
                            cancelPendingLiveZap()
                            dismissLiveZapIndicator()
                            liveControlsLikelyVisible = false
                            return@onPreviewKeyEvent false
                        }
                    }
                    return@onPreviewKeyEvent false
                }

                if (!request.streamKind.equals("series", ignoreCase = true)) {
                    return@onPreviewKeyEvent false
                }

                when (keyCode) {
                    AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        if (previousEpisode != null && onPlayPreviousEpisode != null) {
                            onPlayPreviousEpisode()
                            true
                        } else {
                            false
                        }
                    }

                    AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> {
                        if (nextEpisode != null && onPlayNextEpisode != null) {
                            onPlayNextEpisode()
                            true
                        } else {
                            false
                        }
                    }

                    else -> false
                }
            },
    ) {
        PlayerScreen(
            request = request,
            liveCatalog = liveCatalog,
            isFavorite = isFavorite,
            onSelectLiveChannel = ::queuePlayerRequestedLiveChannel,
            onToggleFavorite = onToggleFavorite,
            onBack = onBack,
            onProgress = onProgress,
            nextEpisodeTitle = nextEpisode?.let(::playerProEpisodeLabel),
            onPlayNextEpisode = onPlayNextEpisode,
        )

        if (request.isLive && liveZapIndicatorChannel != null && !liveBrowserVisible) {
            LiveZapIndicator(
                channel = liveZapIndicatorChannel,
                modifier = Modifier
                    .align(
                        if (adaptiveUi.isTelevision) {
                            Alignment.BottomStart
                        } else {
                            Alignment.BottomCenter
                        },
                    )
                    .padding(
                        start = if (adaptiveUi.isTelevision) 42.dp else 16.dp,
                        end = if (adaptiveUi.isTelevision) 42.dp else 16.dp,
                        bottom = if (adaptiveUi.isTelevision) 48.dp else 94.dp,
                    ),
            )
        }

        if (request.isLive && liveBrowserVisible) {
            LiveTvProChannelBrowser(
                catalog = liveCatalog,
                currentStreamId = request.streamId,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onSelectChannel = { channel ->
                    cancelPendingLiveZap()
                    dismissLiveZapIndicator()
                    liveBrowserVisible = false
                    liveControlsLikelyVisible = false
                    onSelectLiveChannel(channel)
                },
                onClose = {
                    cancelPendingLiveZap()
                    dismissLiveZapIndicator()
                    liveBrowserVisible = false
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun LiveZapIndicator(
    channel: ContentItem,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val isTv = adaptiveUi.isTelevision
    val shape = RoundedCornerShape(if (isTv) 26.dp else 22.dp)
    val logoShape = RoundedCornerShape(if (isTv) 18.dp else 15.dp)

    Row(
        modifier = modifier
            .widthIn(
                min = if (isTv) 430.dp else 300.dp,
                max = if (isTv) 620.dp else 370.dp,
            )
            .shadow(if (isTv) 20.dp else 14.dp, shape, clip = false)
            .clip(shape)
            .background(Color(0xF2141612))
            .border(1.dp, colors.gold.copy(alpha = .72f), shape)
            .padding(
                horizontal = if (isTv) 22.dp else 16.dp,
                vertical = if (isTv) 18.dp else 14.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 18.dp else 14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (isTv) 88.dp else 70.dp)
                .clip(logoShape)
                .background(Color.White.copy(alpha = .96f))
                .padding(if (isTv) 7.dp else 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            ChannelLogo(channel, Modifier.fillMaxSize())
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "● LIVE",
                    color = Color(0xFFFF4E55),
                    fontSize = if (isTv) 13.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "تبديل القناة",
                    color = colors.goldBright,
                    fontSize = if (isTv) 13.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = if (isTv) 24.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "جاري فتح البث المباشر",
                color = colors.textMuted,
                fontSize = if (isTv) 12.sp else 11.sp,
                maxLines = 1,
            )
        }

        Box(
            modifier = Modifier
                .width(4.dp)
                .height(if (isTv) 58.dp else 50.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.goldBright),
        )
    }
}
