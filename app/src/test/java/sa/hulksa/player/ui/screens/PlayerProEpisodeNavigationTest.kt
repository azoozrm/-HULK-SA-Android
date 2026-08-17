package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sa.hulksa.player.model.Episode

class PlayerProEpisodeNavigationTest {
    @Test
    fun middleEpisodeResolvesChronologicalPreviousAndNext() {
        val episodes = listOf(
            episode(id = 20, season = 2, number = 1),
            episode(id = 11, season = 1, number = 2),
            episode(id = 10, season = 1, number = 1),
        )

        val neighbors = playerProEpisodeNeighbors(episodes, currentStreamId = 11)

        assertEquals(10, neighbors.previous?.id)
        assertEquals(20, neighbors.next?.id)
    }

    @Test
    fun firstEpisodeHasNoPreviousAndLastEpisodeHasNoNext() {
        val episodes = listOf(
            episode(id = 10, season = 1, number = 1),
            episode(id = 11, season = 1, number = 2),
            episode(id = 20, season = 2, number = 1),
        )

        assertNull(playerProEpisodeNeighbors(episodes, currentStreamId = 10).previous)
        assertNull(playerProEpisodeNeighbors(episodes, currentStreamId = 20).next)
    }

    @Test
    fun unknownStreamDoesNotGuessEpisodeNeighbors() {
        val neighbors = playerProEpisodeNeighbors(
            episodes = listOf(episode(id = 10, season = 1, number = 1)),
            currentStreamId = 999,
        )

        assertNull(neighbors.previous)
        assertNull(neighbors.next)
    }

    @Test
    fun episodeLabelKeepsSeasonEpisodeAndTitleTogether() {
        assertEquals(
            "الموسم 3 • الحلقة 7 • النهاية",
            playerProEpisodeLabel(episode(id = 70, season = 3, number = 7, title = "النهاية")),
        )
    }

    private fun episode(
        id: Int,
        season: Int,
        number: Int,
        title: String = "Episode $number",
    ) = Episode(
        id = id,
        title = title,
        season = season,
        episodeNumber = number,
        containerExtension = "mp4",
        posterUrl = null,
        duration = "00:45:00",
    )
}
