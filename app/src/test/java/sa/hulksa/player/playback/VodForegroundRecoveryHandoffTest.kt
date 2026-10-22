package sa.hulksa.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.ui.screens.movieOfflineRestoredPlayWhenReady
import sa.hulksa.player.ui.screens.playerPreparationReady
import sa.hulksa.player.ui.screens.playerRetrySeekTarget

/**
 * F01 correction 01: a remote VOD opened offline has no media item until the first preparation.
 * A deferred NETWORK_RESTORED recovery must be replayable on foreground without weakening stale
 * player/source checks, background preparation or intent/position ownership.
 */
class VodForegroundRecoveryHandoffTest {
    @Test
    fun `unprepared offline first preparation can replay deferred network recovery on foreground`() {
        // Background: no preparation is allowed while the confirmed offline state is unresolved.
        assertFalse(
            playerPreparationReady(
                appForeground = false,
                localPlayback = false,
                networkAvailable = true,
                confirmedOutage = true,
            ),
        )
        // Foreground, no media item/expected source yet: the first NETWORK_RESTORED source recovery
        // may use the existing deferred replay path.
        assertTrue(
            canReplayDeferredRecovery(
                acceptsPlayer = true,
                currentSourceAccepted = false,
                firstPreparationPending = true,
                pendingFailureClass = RecoveryFailureClass.SOURCE,
                pendingTrigger = RecoveryTrigger.NETWORK_RESTORED,
            ),
        )
        // Once that recovery command clears offlineFailure, first preparation can proceed.
        assertTrue(
            playerPreparationReady(
                appForeground = true,
                localPlayback = false,
                networkAvailable = true,
                confirmedOutage = false,
            ),
        )
    }

    @Test
    fun `stale player prepared mismatch and audio-before-prepare cannot replay`() {
        assertFalse(
            canReplayDeferredRecovery(
                acceptsPlayer = false,
                currentSourceAccepted = false,
                firstPreparationPending = true,
                pendingFailureClass = RecoveryFailureClass.SOURCE,
                pendingTrigger = RecoveryTrigger.NETWORK_RESTORED,
            ),
        )
        assertFalse(
            canReplayDeferredRecovery(
                acceptsPlayer = true,
                currentSourceAccepted = false,
                firstPreparationPending = false,
                pendingFailureClass = RecoveryFailureClass.SOURCE,
                pendingTrigger = RecoveryTrigger.NETWORK_RESTORED,
            ),
        )
        assertFalse(
            canReplayDeferredRecovery(
                acceptsPlayer = true,
                currentSourceAccepted = false,
                firstPreparationPending = true,
                pendingFailureClass = RecoveryFailureClass.AUDIO,
                pendingTrigger = RecoveryTrigger.AUDIO_DECODER_ERROR,
            ),
        )
    }

    @Test
    fun `prepared current source keeps existing source and audio replay`() {
        assertTrue(
            canReplayDeferredRecovery(
                acceptsPlayer = true,
                currentSourceAccepted = true,
                firstPreparationPending = false,
                pendingFailureClass = RecoveryFailureClass.SOURCE,
                pendingTrigger = RecoveryTrigger.NETWORK_RESTORED,
            ),
        )
        assertTrue(
            canReplayDeferredRecovery(
                acceptsPlayer = true,
                currentSourceAccepted = true,
                firstPreparationPending = false,
                pendingFailureClass = RecoveryFailureClass.AUDIO,
                pendingTrigger = RecoveryTrigger.AUDIO_DECODER_ERROR,
            ),
        )
    }

    @Test
    fun `background offline and unavailable-source preparation gates remain closed`() {
        assertFalse(
            playerPreparationReady(
                appForeground = false,
                localPlayback = false,
                networkAvailable = true,
                confirmedOutage = false,
            ),
        )
        assertFalse(
            playerPreparationReady(
                appForeground = true,
                localPlayback = false,
                networkAvailable = false,
                confirmedOutage = false,
            ),
        )
        assertFalse(
            playerPreparationReady(
                appForeground = true,
                localPlayback = false,
                networkAvailable = true,
                confirmedOutage = true,
            ),
        )
        assertTrue(
            playerPreparationReady(
                appForeground = true,
                localPlayback = false,
                networkAvailable = true,
                confirmedOutage = false,
            ),
        )
        assertTrue(
            playerPreparationReady(
                appForeground = true,
                localPlayback = true,
                networkAvailable = false,
                confirmedOutage = true,
            ),
        )
    }

    @Test
    fun `pending resume paused intent and preserved position remain unchanged`() {
        assertFalse(
            movieOfflineRestoredPlayWhenReady(
                wasPlayingBeforeOffline = true,
                resumePromptPending = true,
            ),
        )
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
        assertEquals(
            1_234_000L,
            playerRetrySeekTarget(
                isLive = false,
                resumePromptPending = false,
                preservedPositionMs = 1_234_000L,
                playerPositionMs = 0L,
            ),
        )
        assertEquals(
            0L,
            playerRetrySeekTarget(
                isLive = false,
                resumePromptPending = true,
                preservedPositionMs = 1_234_000L,
                playerPositionMs = 1_234_000L,
            ),
        )
    }
}
