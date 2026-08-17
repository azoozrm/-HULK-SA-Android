package sa.hulksa.player.ui.screens

import android.content.Context
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import sa.hulksa.player.ui.components.ChannelLogo
import sa.hulksa.player.ui.theme.LocalHulkColors

private const val LIVE_TV_PRO_HISTORY_PREFS = "live_player_history"
private const val LIVE_TV_PRO_HISTORY_IDS = "ids"
private const val ANDROID_KEYCODE_LAST_CHANNEL = 229
private const val LIVE_TV_PRO_CONTROLS_HINT_TIMEOUT_MS = 5_200L
private const val LIVE_TV_PRO_ZAP_COMMIT_DELAY_MS = 220L

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
    val pendingLiveChannel = remember(liveCatalog, pendingLiveChannelId) {
        val pendingId = pendingLiveChannelId
        if (pendingId == null) null else liveChannels.firstOrNull { it.id == pendingId }
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

    fun cancelPendingLiveZap() {
        pendingLiveChannelId = null
        liveZapInteractionTick += 1
    }

    fun markLiveControlsInteraction() {
        cancelPendingLiveZap()
        liveControlsLikelyVisible = true
        liveControlsInteractionTick += 1
    }

    fun queueLiveRelative(delta: Int): Boolean {
        if (!request.isLive) return false
        val channel = liveTvProQueuedRelativeChannel(
            channels = liveChannels,
            currentStreamId = request.streamId,
            pendingStreamId = pendingLiveChannelId,
            delta = delta,
        ) ?: return false
        if (channel.id == request.streamId && pendingLiveChannelId == null) return false
        liveBrowserVisible = false
        liveControlsLikelyVisible = false
        pendingLiveChannelId = channel.id
        liveZapInteractionTick += 1
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

        if (request.isLive && pendingLiveChannel != null && !liveBrowserVisible) {
            LiveZapIndicator(
                channel = pendingLiveChannel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 82.dp),
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
                    liveBrowserVisible = false
                    liveControlsLikelyVisible = false
                    onSelectLiveChannel(channel)
                },
                onClose = {
                    cancelPendingLiveZap()
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
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .widthIn(max = 440.dp)
            .clip(shape)
            .background(Color.Black.copy(alpha = .86f))
            .border(1.dp, colors.gold.copy(alpha = .60f), shape)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChannelLogo(channel, Modifier.size(52.dp))
        Column {
            Text(
                text = "تبديل القناة",
                color = colors.goldBright,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
