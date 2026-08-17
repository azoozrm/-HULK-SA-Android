package sa.hulksa.player.ui.screens

import android.content.Context
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.Episode
import sa.hulksa.player.model.PlaybackRequest

private const val LIVE_TV_PRO_HISTORY_PREFS = "live_player_history"
private const val LIVE_TV_PRO_HISTORY_IDS = "ids"
private const val ANDROID_KEYCODE_LAST_CHANNEL = 229
private const val LIVE_TV_PRO_CONTROLS_HINT_TIMEOUT_MS = 5_200L

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
 * Channel +/- and Last Channel actions, plus the v1.6 TV browser launched by OK while the player
 * controls are hidden. PlayerScreen remains the qualified playback/control core.
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

    fun markLiveControlsInteraction() {
        liveControlsLikelyVisible = true
        liveControlsInteractionTick += 1
    }

    fun switchLiveRelative(delta: Int): Boolean {
        if (!request.isLive) return false
        val channel = liveTvProRelativeChannel(
            channels = liveChannels,
            currentStreamId = request.streamId,
            delta = delta,
        ) ?: return false
        if (channel.id == request.streamId) return false
        liveBrowserVisible = false
        liveControlsLikelyVisible = false
        onSelectLiveChannel(channel)
        return true
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
                            return@onPreviewKeyEvent switchLiveRelative(1)
                        }

                        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                        AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        -> {
                            liveControlsLikelyVisible = false
                            return@onPreviewKeyEvent switchLiveRelative(-1)
                        }

                        ANDROID_KEYCODE_LAST_CHANNEL -> {
                            val channel = lastChannel ?: return@onPreviewKeyEvent false
                            liveControlsLikelyVisible = false
                            onSelectLiveChannel(channel)
                            return@onPreviewKeyEvent true
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        -> {
                            if (!liveControlsLikelyVisible) {
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
            onSelectLiveChannel = onSelectLiveChannel,
            onToggleFavorite = onToggleFavorite,
            onBack = onBack,
            onProgress = onProgress,
            nextEpisodeTitle = nextEpisode?.let(::playerProEpisodeLabel),
            onPlayNextEpisode = onPlayNextEpisode,
        )

        if (request.isLive && liveBrowserVisible) {
            LiveTvProChannelBrowser(
                catalog = liveCatalog,
                currentStreamId = request.streamId,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onSelectChannel = { channel ->
                    liveBrowserVisible = false
                    liveControlsLikelyVisible = false
                    onSelectLiveChannel(channel)
                },
                onClose = { liveBrowserVisible = false },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}
