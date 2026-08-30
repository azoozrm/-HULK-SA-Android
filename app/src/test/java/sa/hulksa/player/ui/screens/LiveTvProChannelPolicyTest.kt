package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class LiveTvProChannelPolicyTest {
    @Test
    fun zappingStaysInsideCurrentCategoryWhenThereAreAlternatives() {
        val channels = listOf(
            channel(1, "news"),
            channel(2, "news"),
            channel(3, "sports"),
        )

        assertEquals(2, liveTvProRelativeChannel(channels, currentStreamId = 1, delta = 1)?.id)
        assertEquals(1, liveTvProRelativeChannel(channels, currentStreamId = 2, delta = 1)?.id)
    }

    @Test
    fun singletonCategoryFallsBackToWholeCatalogInsteadOfZappingToItself() {
        val channels = listOf(
            channel(1, "news"),
            channel(2, "sports"),
            channel(3, "movies"),
        )

        assertEquals(listOf(1, 2, 3), liveTvProChannelSequence(channels, 1).map(ContentItem::id))
        assertEquals(2, liveTvProRelativeChannel(channels, currentStreamId = 1, delta = 1)?.id)
    }

    @Test
    fun relativeChannelWrapsInBothDirections() {
        val channels = listOf(channel(1, "news"), channel(2, "news"), channel(3, "news"))

        assertEquals(1, liveTvProRelativeChannel(channels, currentStreamId = 3, delta = 1)?.id)
        assertEquals(3, liveTvProRelativeChannel(channels, currentStreamId = 1, delta = -1)?.id)
    }

    @Test
    fun queuedZappingAdvancesFromPendingTargetBeforePlaybackRecreates() {
        val channels = listOf(channel(1, "news"), channel(2, "news"), channel(3, "news"))

        val first = liveTvProQueuedRelativeChannel(
            channels = channels,
            currentStreamId = 1,
            pendingStreamId = null,
            delta = 1,
        )
        val second = liveTvProQueuedRelativeChannel(
            channels = channels,
            currentStreamId = 1,
            pendingStreamId = first?.id,
            delta = 1,
        )

        assertEquals(2, first?.id)
        assertEquals(3, second?.id)
    }

    @Test
    fun queuedZappingWrapsAndIgnoresPendingTargetOutsideCurrentSequence() {
        val channels = listOf(
            channel(1, "news"),
            channel(2, "news"),
            channel(3, "news"),
            channel(9, "sports"),
        )

        assertEquals(
            1,
            liveTvProQueuedRelativeChannel(
                channels = channels,
                currentStreamId = 1,
                pendingStreamId = 3,
                delta = 1,
            )?.id,
        )
        assertEquals(
            2,
            liveTvProQueuedRelativeChannel(
                channels = channels,
                currentStreamId = 1,
                pendingStreamId = 9,
                delta = 1,
            )?.id,
        )
    }

    @Test
    fun recentHistoryIsDeduplicatedAndKeepsNewestFirst() {
        assertEquals(
            listOf(3, 2, 1),
            liveTvProUpdateRecentChannelIds(listOf(2, 3, 1, 2), currentStreamId = 3, limit = 3),
        )
    }

    @Test
    fun lastChannelSkipsCurrentAndUnavailableHistoryEntries() {
        val channels = listOf(channel(1, "news"), channel(2, "sports"))

        assertEquals(2, liveTvProLastChannel(channels, listOf(99, 1, 2), currentStreamId = 1)?.id)
        assertNull(liveTvProLastChannel(channels, listOf(99, 1), currentStreamId = 1))
    }

    @Test
    fun visibleCategoryReturnDoesNotRequireRevealScroll() {
        assertFalse(
            liveTvProCategoryReturnNeedsReveal(
                targetIndex = 4,
                visibleIndices = setOf(3, 4, 5),
            ),
        )
    }

    @Test
    fun offscreenCategoryReturnRequiresExactlyOneRevealPath() {
        assertTrue(
            liveTvProCategoryReturnNeedsReveal(
                targetIndex = 7,
                visibleIndices = setOf(3, 4, 5),
            ),
        )
    }

    @Test
    fun categoryReturnGateRejectsKeyRepeatUntilCurrentTransitionFinishes() {
        val gate = LiveTvProCategoryReturnGate()

        assertTrue(gate.tryStart())
        assertFalse(gate.tryStart())
        gate.finish()
        assertTrue(gate.tryStart())
    }

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
