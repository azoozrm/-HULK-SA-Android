package sa.hulksa.player.ui.screens

import android.content.Context
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.Episode
import sa.hulksa.player.model.PlaybackRequest

private const val LIVE_TV_PRO_HISTORY_PREFS = "live_player_history"
private const val LIVE_TV_PRO_HISTORY_IDS = "ids"
private const val ANDROID_KEYCODE_LAST_CHANNEL = 229

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
 * Series continuity remains isolated here. For Live, this layer now owns the persistent recent
 * channel history plus hardware Channel +/- and Last Channel actions, while PlayerScreen keeps
 * the already-qualified playback, browser, D-pad focus and control behavior.
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

    fun switchLiveRelative(delta: Int): Boolean {
        if (!request.isLive) return false
        val channel = liveTvProRelativeChannel(
            channels = liveChannels,
            currentStreamId = request.streamId,
            delta = delta,
        ) ?: return false
        if (channel.id == request.streamId) return false
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
                    when (keyCode) {
                        AndroidKeyEvent.KEYCODE_CHANNEL_UP,
                        AndroidKeyEvent.KEYCODE_MEDIA_NEXT,
                        -> return@onPreviewKeyEvent switchLiveRelative(1)

                        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                        AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        -> return@onPreviewKeyEvent switchLiveRelative(-1)

                        ANDROID_KEYCODE_LAST_CHANNEL -> {
                            val channel = lastChannel ?: return@onPreviewKeyEvent false
                            onSelectLiveChannel(channel)
                            return@onPreviewKeyEvent true
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
    }
}
