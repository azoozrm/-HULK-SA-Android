package sa.hulksa.player.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.Episode
import sa.hulksa.player.model.PlaybackRequest

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
 * v1.5 Player Pro entry point.
 *
 * The qualified Media3 player remains the playback core. This layer owns series continuity so
 * previous/next media keys move between exact chronological episodes without changing the already
 * qualified VOD D-pad seek mapping, Live controls, reconnect behavior, track panels or resume path.
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (
                    event.type != KeyEventType.KeyDown ||
                    !request.streamKind.equals("series", ignoreCase = true)
                ) {
                    return@onPreviewKeyEvent false
                }

                when (event.nativeKeyEvent.keyCode) {
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
