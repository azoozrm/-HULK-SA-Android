package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.data.FavoriteMutationOutcome
import sa.hulksa.player.data.FavoriteMutationStatus

class FavoriteFeedbackPolicyTest {
    @Test
    fun `applied add and remove emit exactly one matching success notice`() {
        assertEquals(
            FavoriteFeedback(
                message = "تمت اضافة Movie 1 الى المفضلة",
                glyph = FavoriteFeedbackGlyph.ADDED,
            ),
            favoriteMutationFeedback(
                outcome("MOVIE:1", FavoriteMutationStatus.APPLIED_ADDED),
                "Movie 1",
            ),
        )
        assertEquals(
            FavoriteFeedback(
                message = "تمت ازالة Movie 1 من المفضلة",
                glyph = FavoriteFeedbackGlyph.REMOVED,
            ),
            favoriteMutationFeedback(
                outcome("MOVIE:1", FavoriteMutationStatus.APPLIED_REMOVED),
                "Movie 1",
            ),
        )
    }

    @Test
    fun `throttled duplicate unchanged kids and stale owner stay silent`() {
        listOf(
            FavoriteMutationStatus.THROTTLED,
            FavoriteMutationStatus.UNCHANGED,
            FavoriteMutationStatus.KIDS_REJECTED,
            FavoriteMutationStatus.OWNER_CHANGED,
        ).forEach { status ->
            assertNull(
                "no success notice for $status",
                favoriteMutationFeedback(outcome("MOVIE:1", status), "Movie 1"),
            )
        }
    }

    @Test
    fun `browser feedback is gated by the current visible browser and playback owner`() {
        assertTrue(
            liveBrowserFavoriteFeedbackIsCurrent(
                browserVisible = true,
                samePlaybackRequest = true,
                expectedSessionId = 4L,
                currentSessionId = 4L,
            ),
        )
        assertFalse(
            liveBrowserFavoriteFeedbackIsCurrent(
                browserVisible = false,
                samePlaybackRequest = true,
                expectedSessionId = 4L,
                currentSessionId = 4L,
            ),
        )
        assertFalse(
            liveBrowserFavoriteFeedbackIsCurrent(
                browserVisible = true,
                samePlaybackRequest = false,
                expectedSessionId = 4L,
                currentSessionId = 4L,
            ),
        )
        assertFalse(
            liveBrowserFavoriteFeedbackIsCurrent(
                browserVisible = true,
                samePlaybackRequest = true,
                expectedSessionId = 3L,
                currentSessionId = 4L,
            ),
        )
    }

    @Test
    fun `actual persistence failure uses one truthful error notice`() {
        assertEquals(
            FavoriteFeedback(
                message = "تعذر تحديث المفضلة",
                glyph = FavoriteFeedbackGlyph.ERROR,
            ),
            favoriteMutationFeedback(
                outcome("MOVIE:1", FavoriteMutationStatus.PERSISTENCE_FAILED),
                "Movie 1",
            ),
        )
    }

    private fun outcome(key: String, status: FavoriteMutationStatus): FavoriteMutationOutcome =
        FavoriteMutationOutcome(status = status, key = key)
}
