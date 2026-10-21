package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F01 intent boundary: the recovery command must reuse the position captured at the outage, must
 * not seek behind a pending Resume decision, and must replay the pre-outage play/pause intent.
 */
class VodNetworkRestoreIntentTest {
    @Test
    fun `recovery keeps the captured position when the player position collapsed to zero`() {
        assertEquals(
            1_234_000L,
            playerRetrySeekTarget(
                isLive = false,
                resumePromptPending = false,
                preservedPositionMs = 1_234_000L,
                playerPositionMs = 0L,
            ),
        )
    }

    @Test
    fun `recovery never moves backward from real player progress`() {
        assertEquals(
            1_240_000L,
            playerRetrySeekTarget(
                isLive = false,
                resumePromptPending = false,
                preservedPositionMs = 1_234_000L,
                playerPositionMs = 1_240_000L,
            ),
        )
    }

    @Test
    fun `pending resume decision prepares at zero and stays paused`() {
        assertEquals(
            0L,
            playerRetrySeekTarget(
                isLive = false,
                resumePromptPending = true,
                preservedPositionMs = 1_234_000L,
                playerPositionMs = 1_234_000L,
            ),
        )
        assertFalse(
            movieOfflineRestoredPlayWhenReady(
                wasPlayingBeforeOffline = true,
                resumePromptPending = true,
            ),
        )
    }

    @Test
    fun `playing and paused intents survive the outage`() {
        assertTrue(
            movieOfflineRestoredPlayWhenReady(
                wasPlayingBeforeOffline = true,
                resumePromptPending = false,
            ),
        )
        assertFalse(
            movieOfflineRestoredPlayWhenReady(
                wasPlayingBeforeOffline = false,
                resumePromptPending = false,
            ),
        )
    }

    @Test
    fun `live recovery remains at the live edge`() {
        assertEquals(
            0L,
            playerRetrySeekTarget(
                isLive = true,
                resumePromptPending = false,
                preservedPositionMs = 1_234_000L,
                playerPositionMs = 1_234_000L,
            ),
        )
    }
}
