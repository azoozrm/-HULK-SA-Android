package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
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

    @Test
    fun favoritesZappingWrapsInsideFavoritesAcrossUnderlyingCategories() {
        val channels = listOf(
            channel(1, "news"),
            channel(2, "news"),
            channel(3, "sports"),
            channel(4, "sports"),
            channel(5, "movies"),
        )
        val sequence = playerProLiveNavigationSequence(
            channels = channels,
            currentStreamId = 5,
            launchContext = LIVE_TV_PRO_CONTEXT_FAVORITES,
            favoriteIds = setOf(1, 3, 5),
            recentIds = emptyList(),
        )

        assertEquals(listOf(1, 3, 5), sequence.map(ContentItem::id))
        assertEquals(
            1,
            playerProQueuedRelativeChannel(
                sequence = sequence,
                currentStreamId = 5,
                pendingStreamId = null,
                delta = 1,
            )?.id,
        )
        assertEquals(
            5,
            playerProQueuedRelativeChannel(
                sequence = sequence,
                currentStreamId = 1,
                pendingStreamId = null,
                delta = -1,
            )?.id,
        )
    }

    @Test
    fun rapidLiveZappingUsesPendingTargetAsTheNextAnchor() {
        val sequence = listOf(
            channel(1, "news"),
            channel(2, "news"),
            channel(3, "news"),
            channel(4, "news"),
        )

        assertEquals(
            3,
            playerProQueuedRelativeChannel(
                sequence = sequence,
                currentStreamId = 1,
                pendingStreamId = 2,
                delta = 1,
            )?.id,
        )
        assertEquals(
            2,
            playerProQueuedRelativeChannel(
                sequence = sequence,
                currentStreamId = 1,
                pendingStreamId = 3,
                delta = -1,
            )?.id,
        )
    }

    @Test
    fun explicitCategoryNavigationNeverLeaksIntoAnotherCategory() {
        val channels = listOf(
            channel(1, "news"),
            channel(2, "sports"),
            channel(3, "movies"),
        )
        val sequence = playerProLiveNavigationSequence(
            channels = channels,
            currentStreamId = 1,
            launchContext = "news",
            favoriteIds = emptySet(),
            recentIds = emptyList(),
        )

        assertEquals(listOf(1), sequence.map(ContentItem::id))
        assertEquals(
            1,
            playerProQueuedRelativeChannel(
                sequence = sequence,
                currentStreamId = 1,
                pendingStreamId = null,
                delta = 1,
            )?.id,
        )
    }

    @Test
    fun tvPremiumPlayerOverlayProtectsCompactSafeAreaAndScalesOnLargeTv() {
        val compact = playerTvPremiumOverlayMetrics(screenWidthDp = 960, screenHeightDp = 540)
        val standard = playerTvPremiumOverlayMetrics(screenWidthDp = 1280, screenHeightDp = 720)
        val large = playerTvPremiumOverlayMetrics(screenWidthDp = 1920, screenHeightDp = 1080)

        assertEquals(24, compact.safeHorizontalPaddingDp)
        assertEquals(36, compact.safeBottomPaddingDp)
        assertEquals(320, compact.zapMinWidthDp)
        assertEquals(470, compact.zapMaxWidthDp)
        assertEquals(64, compact.zapLogoSizeDp)
        assertEquals(20, compact.zapTitleSizeSp)

        assertEquals(30, standard.safeHorizontalPaddingDp)
        assertEquals(44, standard.safeBottomPaddingDp)
        assertEquals(23, standard.zapTitleSizeSp)

        assertTrue(large.zapMaxWidthDp > standard.zapMaxWidthDp)
        assertTrue(large.zapLogoSizeDp > standard.zapLogoSizeDp)
        assertEquals(26, large.zapTitleSizeSp)
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

    private fun channel(id: Int, categoryId: String) = ContentItem(
        id = id,
        name = "Channel $id",
        categoryId = categoryId,
        type = ContentType.LIVE,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = "ts",
    )
}
