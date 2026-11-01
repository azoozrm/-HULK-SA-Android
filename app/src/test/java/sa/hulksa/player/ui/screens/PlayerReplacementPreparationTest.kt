package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.playback.RecoveryCommand
import sa.hulksa.player.playback.RecoveryCommandType
import sa.hulksa.player.playback.RecoveryDispatchOwner
import sa.hulksa.player.playback.RecoveryFailureClass
import sa.hulksa.player.playback.RecoveryTrigger
import sa.hulksa.player.playback.ownsRecoveryCommand

class PlayerReplacementPreparationTest {

    @Test
    fun pendingResumeOnlineRetryStaysPausedAtZeroEvenWhenRetryCapturedPlayback() {
        val preparation = playerReplacementPreparation(
            isLive = false,
            resumePromptPending = true,
            pendingSeekAuthoritative = false,
            pendingSeekMs = 0L,
            replacementPositionMs = 1_200_000L,
            restoredPlayWhenReady = true,
            replacementPlayWhenReady = true,
        )

        assertEquals(0L, preparation.seekTargetMs)
        assertFalse(preparation.playWhenReady)
    }

    @Test
    fun pendingResumeKeepsRestoredIntentFromStartingBehindTheDecision() {
        val preparation = playerReplacementPreparation(
            isLive = false,
            resumePromptPending = true,
            pendingSeekAuthoritative = true,
            pendingSeekMs = 0L,
            replacementPositionMs = 1_200_000L,
            restoredPlayWhenReady = null,
            replacementPlayWhenReady = true,
        )

        assertEquals(0L, preparation.seekTargetMs)
        assertFalse(preparation.playWhenReady)
    }

    @Test
    fun acceptedResumeUsesSavedPositionAndRestartUsesZero() {
        assertEquals(
            1_200_000L,
            playerResumeDecisionSeekTarget(resumeFromSaved = true, savedPositionMs = 1_200_000L),
        )
        assertEquals(
            0L,
            playerResumeDecisionSeekTarget(resumeFromSaved = false, savedPositionMs = 1_200_000L),
        )
        assertEquals(
            0L,
            playerResumeDecisionSeekTarget(resumeFromSaved = true, savedPositionMs = -1L),
        )
    }

    @Test
    fun noPendingResumeAllowsExplicitRetryToStartAtThePreservedPosition() {
        val preparation = playerReplacementPreparation(
            isLive = false,
            resumePromptPending = false,
            pendingSeekAuthoritative = true,
            pendingSeekMs = 3_044L,
            replacementPositionMs = 0L,
            restoredPlayWhenReady = null,
            replacementPlayWhenReady = true,
        )

        assertEquals(3_044L, preparation.seekTargetMs)
        assertTrue(preparation.playWhenReady)
    }

    @Test
    fun noPendingResumePreservesAnExplicitPausedReplacement() {
        val preparation = playerReplacementPreparation(
            isLive = false,
            resumePromptPending = false,
            pendingSeekAuthoritative = false,
            pendingSeekMs = 0L,
            replacementPositionMs = 3_044L,
            restoredPlayWhenReady = false,
            replacementPlayWhenReady = true,
        )

        assertEquals(3_044L, preparation.seekTargetMs)
        assertFalse(preparation.playWhenReady)
    }

    @Test
    fun pendingResumeCannotOverwriteSavedProgressDuringReplacementOrDisposal() {
        val blocked = vodProgressPersistenceBlockedInitially(
            isVod = true,
            resumePlaybackEnabled = true,
            resumePositionMs = 1_200_000L,
        )
        assertTrue(blocked)
        // Retry/replacement does not accept the pending decision, so disposal and late callbacks
        // still cannot persist a provisional zero.
        assertTrue(vodProgressPersistenceBlockedAfterDecision(blocked = blocked, decisionAccepted = false))
        // Only the explicit Resume/Restart decision releases the block.
        assertFalse(vodProgressPersistenceBlockedAfterDecision(blocked = blocked, decisionAccepted = true))
    }

    @Test
    fun replacedPlayerCallbacksCannotControlTheNewPlayerInstance() {
        val generationId = 42L
        val command = RecoveryCommand(
            id = 1L,
            generationId = generationId,
            type = RecoveryCommandType.RETRY_CURRENT_SOURCE,
            trigger = RecoveryTrigger.TRANSIENT_SOURCE_ERROR,
            failureClass = RecoveryFailureClass.SOURCE,
            candidateIndex = 0,
        )
        val replacedOwner = RecoveryDispatchOwner(generationId, playerInstanceId = 0)
        val currentOwner = RecoveryDispatchOwner(generationId, playerInstanceId = 1)

        assertFalse(ownsRecoveryCommand(command, replacedOwner, currentOwner))
        assertTrue(ownsRecoveryCommand(command, currentOwner, currentOwner))
        assertFalse(
            ownsRecoveryCommand(
                command,
                currentOwner,
                currentOwner.copy(playerInstanceId = 2),
            ),
        )
    }
}
