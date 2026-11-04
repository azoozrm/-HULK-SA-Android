package sa.hulksa.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.PlaybackRequest

class PlaybackRequestSavedPreferenceTest {

    @Test
    fun `saved preferences attach only to the exact content identity`() {
        val request = movieRequest(7)
        val matching = HistoryEntry(
            key = "MOVIE:7",
            title = "Movie",
            posterUrl = null,
            streamKind = "movie",
            streamId = 7,
            extension = "mp4",
            isLive = false,
            positionMs = 10_000L,
            durationMs = 100_000L,
            updatedAtEpochMs = 1L,
            savedAudioPreference = "audio-ar",
            savedSubtitlePreference = "off",
        )
        val attached = request.withSavedTrackPreferences(listOf(matching))
        assertEquals("audio-ar", attached.savedAudioPreference)
        assertEquals("off", attached.savedSubtitlePreference)

        val wrongId = matching.copy(streamId = 8)
        assertNull(request.withSavedTrackPreferences(listOf(wrongId)).savedAudioPreference)

        val wrongKind = matching.copy(key = "SERIES:7", streamKind = "series")
        assertNull(request.withSavedTrackPreferences(listOf(wrongKind)).savedAudioPreference)
    }

    @Test
    fun `series episode choice is not shared with another episode`() {
        val request = seriesRequest(episodeId = 503)
        val otherEpisode = HistoryEntry(
            key = "SERIES:504",
            title = "Other episode",
            posterUrl = null,
            streamKind = "series",
            streamId = 504,
            extension = "mp4",
            isLive = false,
            positionMs = 1_000L,
            durationMs = 100_000L,
            updatedAtEpochMs = 1L,
            savedAudioPreference = "audio-en",
            savedSubtitlePreference = "off",
        )
        assertNull(request.withSavedTrackPreferences(listOf(otherEpisode)).savedAudioPreference)
    }

    private fun movieRequest(id: Int): PlaybackRequest = PlaybackRequest(
        title = "Movie",
        posterUrl = null,
        candidates = listOf("https://example.test/movie.mp4"),
        isLive = false,
        historyKey = "MOVIE:$id",
        streamKind = "movie",
        streamId = id,
        extension = "mp4",
    )

    private fun seriesRequest(episodeId: Int): PlaybackRequest = PlaybackRequest(
        title = "Series · Episode",
        posterUrl = null,
        candidates = listOf("https://example.test/episode.mp4"),
        isLive = false,
        historyKey = "SERIES:$episodeId",
        streamKind = "series",
        streamId = episodeId,
        extension = "mp4",
    )
}
