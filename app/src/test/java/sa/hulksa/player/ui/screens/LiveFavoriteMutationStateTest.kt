package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sa.hulksa.player.data.FavoriteMutationStatus
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

/**
 * Controlled-result coverage for the Live Favorites intent owner. The production composable uses
 * this exact state machine before the authoritative result is allowed to clear ownership, so a
 * failed preview cannot discard a later row handoff and an old result cannot clear a newer one.
 */
class LiveFavoriteMutationStateTest {
    @Test
    fun `failed preview then successful row keeps the row handoff and focus target`() {
        var state = LiveFavoriteMutationState()
        val (previewState, previewIntent) = state.begin(
            origin = LiveFavoriteMutationOrigin.PREVIEW,
            removedKey = "LIVE:1",
        )
        state = previewState.onOutcome(previewIntent.id, FavoriteMutationStatus.PERSISTENCE_FAILED)
        assertNull(state.active)

        val before = listOf(liveChannel(1), liveChannel(2), liveChannel(3), liveChannel(4))
        val handoff = liveRemovalHandoff(before, "LIVE:2")
        assertEquals("LIVE:3", handoff?.fallbackKey)

        val (rowState, rowIntent) = state.begin(
            origin = LiveFavoriteMutationOrigin.ROW,
            removedKey = "LIVE:2",
            fallbackKey = handoff?.fallbackKey,
        )
        state = rowState.onOutcome(rowIntent.id, FavoriteMutationStatus.APPLIED_REMOVED)
        assertEquals(LiveFavoriteMutationOrigin.ROW, state.active?.origin)
        assertEquals("LIVE:3", state.active?.fallbackKey)

        val after = before.filterNot { liveChannelKey(it) == "LIVE:2" }
        val focusIndex = after.indexOfFirst { liveChannelKey(it) == state.active?.fallbackKey }
        assertEquals(1, focusIndex)
        assertEquals("LIVE:3", liveChannelKey(after[focusIndex]))
    }

    @Test
    fun `non-applied preview outcomes leave no stale removal ownership`() {
        val nonApplied = listOf(
            FavoriteMutationStatus.APPLIED_ADDED,
            FavoriteMutationStatus.UNCHANGED,
            FavoriteMutationStatus.KIDS_REJECTED,
            FavoriteMutationStatus.OWNER_CHANGED,
            FavoriteMutationStatus.PERSISTENCE_FAILED,
            FavoriteMutationStatus.THROTTLED,
        )
        nonApplied.forEach { status ->
            val (pending, intent) = LiveFavoriteMutationState().begin(
                origin = LiveFavoriteMutationOrigin.PREVIEW,
                removedKey = "LIVE:1",
            )
            val result = pending.onOutcome(intent.id, status)
            assertNull("$status must not retain intent", result.active)
        }
    }

    @Test
    fun `older outcome cannot clear a newer row handoff`() {
        val (previewState, previewIntent) = LiveFavoriteMutationState().begin(
            origin = LiveFavoriteMutationOrigin.PREVIEW,
            removedKey = "LIVE:1",
        )
        val (rowState, rowIntent) = previewState.begin(
            origin = LiveFavoriteMutationOrigin.ROW,
            removedKey = "LIVE:2",
            fallbackKey = "LIVE:3",
        )
        val afterOldFailure = rowState.onOutcome(
            previewIntent.id,
            FavoriteMutationStatus.PERSISTENCE_FAILED,
        )
        assertEquals(rowIntent, afterOldFailure.active)
        assertEquals(LiveFavoriteMutationOrigin.ROW, afterOldFailure.active?.origin)
        assertEquals("LIVE:2", afterOldFailure.active?.removedKey)
    }

    @Test
    fun `new row intent replaces a preview intent before the row mutation starts`() {
        val (previewState, _) = LiveFavoriteMutationState().begin(
            origin = LiveFavoriteMutationOrigin.PREVIEW,
            removedKey = "LIVE:1",
        )
        val (rowState, rowIntent) = previewState.begin(
            origin = LiveFavoriteMutationOrigin.ROW,
            removedKey = "LIVE:2",
            fallbackKey = "LIVE:3",
        )
        assertEquals(rowIntent, rowState.active)
        assertEquals(LiveFavoriteMutationOrigin.ROW, rowState.active?.origin)
    }

    @Test
    fun `failed row removal clears ownership while the surviving row stays present`() {
        val (pending, intent) = LiveFavoriteMutationState().begin(
            origin = LiveFavoriteMutationOrigin.ROW,
            removedKey = "LIVE:2",
            fallbackKey = "LIVE:3",
        )
        val failed = pending.onOutcome(intent.id, FavoriteMutationStatus.PERSISTENCE_FAILED)
        assertNull(failed.active)
        val visible = listOf(liveChannel(1), liveChannel(2), liveChannel(3))
        assertEquals(1, visible.indexOfFirst { liveChannelKey(it) == "LIVE:2" })
    }

    @Test
    fun `applied removal stays active until the committed membership change is observed`() {
        val (pending, intent) = LiveFavoriteMutationState().begin(
            origin = LiveFavoriteMutationOrigin.ROW,
            removedKey = "LIVE:2",
            fallbackKey = "LIVE:3",
        )
        val applied = pending.onOutcome(intent.id, FavoriteMutationStatus.APPLIED_REMOVED)
        assertEquals(intent, applied.active)
        val observed = applied.clear()
        assertNull(observed.active)
        assertEquals(intent.id, observed.nextId)
    }

    private fun liveChannel(id: Int): ContentItem = ContentItem(
        id = id,
        name = "Channel $id",
        categoryId = "news",
        type = ContentType.LIVE,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = "m3u8",
        genre = null,
        addedAtEpochSeconds = 0L,
    )
}
