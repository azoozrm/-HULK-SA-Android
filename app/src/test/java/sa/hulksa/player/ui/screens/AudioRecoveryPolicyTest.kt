package sa.hulksa.player.ui.screens

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRecoveryPolicyTest {

    @Test
    fun `audio decoder init failure is recoverable`() {
        assertEquals(
            AudioFailureClassification.RECOVERABLE_AUDIO,
            classifyAudioFailure(
                errorCode = PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                rendererError = true,
                rendererMimeType = "audio/mp4a-latm",
            ),
        )
    }

    @Test
    fun `audio sink init and write failures are recoverable`() {
        assertEquals(
            AudioFailureClassification.RECOVERABLE_AUDIO,
            classifyAudioFailure(
                errorCode = PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                rendererError = true,
                rendererMimeType = "audio/ac3",
            ),
        )
        assertEquals(
            AudioFailureClassification.RECOVERABLE_AUDIO,
            classifyAudioFailure(
                errorCode = PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
                rendererError = true,
                rendererMimeType = "audio/eac3",
            ),
        )
    }

    @Test
    fun `video decoder failure is not audio recovery`() {
        assertEquals(
            AudioFailureClassification.NON_AUDIO,
            classifyAudioFailure(
                errorCode = PlaybackException.ERROR_CODE_DECODING_FAILED,
                rendererError = true,
                rendererMimeType = "video/avc",
            ),
        )
    }

    @Test
    fun `network failure is not audio recovery`() {
        assertEquals(
            AudioFailureClassification.NON_AUDIO,
            classifyAudioFailure(
                errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                rendererError = false,
                rendererMimeType = null,
            ),
        )
    }

    @Test
    fun `first audio failure starts level one recovery`() {
        val policy = AudioRecoveryStateMachine().apply { beginGeneration("movie:1") }

        assertEquals(
            AudioRecoveryAction.REPREPARE_CURRENT,
            policy.requestRecovery(
                key = "movie:1",
                classification = AudioFailureClassification.RECOVERABLE_AUDIO,
                hasAudioTrack = true,
            ),
        )
        assertEquals(1, policy.attemptsUsed)
        assertTrue(policy.recoveryPending)
    }

    @Test
    fun `duplicate rapid audio error cannot start parallel recovery`() {
        val policy = AudioRecoveryStateMachine().apply { beginGeneration("movie:1") }

        assertEquals(
            AudioRecoveryAction.REPREPARE_CURRENT,
            policy.requestRecovery("movie:1", AudioFailureClassification.RECOVERABLE_AUDIO, true),
        )
        assertEquals(
            AudioRecoveryAction.NONE,
            policy.requestRecovery("movie:1", AudioFailureClassification.RECOVERABLE_AUDIO, true),
        )
        assertEquals(1, policy.attemptsUsed)
    }

    @Test
    fun `playback ready finishes recovery without restoring consumed budget`() {
        val policy = AudioRecoveryStateMachine().apply { beginGeneration("movie:1") }
        policy.requestRecovery("movie:1", AudioFailureClassification.RECOVERABLE_AUDIO, true)
        policy.markRecoveryCommandIssued("movie:1")

        policy.markPlaybackReady("movie:1")

        assertFalse(policy.recoveryPending)
        assertEquals(1, policy.attemptsUsed)
    }

    @Test
    fun `same generation never exceeds recovery budget`() {
        val policy = AudioRecoveryStateMachine().apply { beginGeneration("movie:1") }

        assertEquals(
            AudioRecoveryAction.REPREPARE_CURRENT,
            policy.requestRecovery("movie:1", AudioFailureClassification.RECOVERABLE_AUDIO, true),
        )
        policy.markRecoveryCommandIssued("movie:1")
        assertEquals(
            AudioRecoveryAction.RESET_CURRENT_PIPELINE,
            policy.requestRecovery("movie:1", AudioFailureClassification.RECOVERABLE_AUDIO, true),
        )
        policy.markRecoveryCommandIssued("movie:1")
        assertEquals(
            AudioRecoveryAction.EXHAUSTED,
            policy.requestRecovery("movie:1", AudioFailureClassification.RECOVERABLE_AUDIO, true),
        )
        assertEquals(MAX_AUDIO_RECOVERY_ATTEMPTS, policy.attemptsUsed)
    }

    @Test
    fun `new content generation gets fresh recovery budget`() {
        val policy = AudioRecoveryStateMachine().apply { beginGeneration("movie:1") }
        policy.requestRecovery("movie:1", AudioFailureClassification.RECOVERABLE_AUDIO, true)
        policy.markRecoveryCommandIssued("movie:1")
        policy.requestRecovery("movie:1", AudioFailureClassification.RECOVERABLE_AUDIO, true)
        policy.markRecoveryCommandIssued("movie:1")

        policy.beginGeneration("movie:2")

        assertEquals(0, policy.attemptsUsed)
        assertEquals(
            AudioRecoveryAction.REPREPARE_CURRENT,
            policy.requestRecovery("movie:2", AudioFailureClassification.RECOVERABLE_AUDIO, true),
        )
    }

    @Test
    fun `stale generation recovery is ignored`() {
        val policy = AudioRecoveryStateMachine().apply { beginGeneration("channel:B") }

        assertEquals(
            AudioRecoveryAction.NONE,
            policy.requestRecovery("channel:A", AudioFailureClassification.RECOVERABLE_AUDIO, true),
        )
        assertEquals(0, policy.attemptsUsed)
    }

    @Test
    fun `no audio track does not consume recovery budget`() {
        val policy = AudioRecoveryStateMachine().apply { beginGeneration("movie:1") }

        assertEquals(
            AudioRecoveryAction.NONE,
            policy.requestRecovery("movie:1", AudioFailureClassification.RECOVERABLE_AUDIO, false),
        )
        assertEquals(0, policy.attemptsUsed)
    }

    @Test
    fun `vod recovery preserves current position`() {
        assertEquals(98_765L, audioRecoveryPositionMs(isLive = false, currentPositionMs = 98_765L))
        assertEquals(0L, audioRecoveryPositionMs(isLive = false, currentPositionMs = -1L))
    }

    @Test
    fun `live recovery never applies vod resume position`() {
        assertNull(audioRecoveryPositionMs(isLive = true, currentPositionMs = 98_765L))
    }

    @Test
    fun `dispose invalidates pending and future recovery`() {
        val policy = AudioRecoveryStateMachine().apply { beginGeneration("movie:1") }
        policy.requestRecovery("movie:1", AudioFailureClassification.RECOVERABLE_AUDIO, true)

        policy.invalidate()

        assertFalse(policy.recoveryPending)
        assertEquals(
            AudioRecoveryAction.NONE,
            policy.requestRecovery("movie:1", AudioFailureClassification.RECOVERABLE_AUDIO, true),
        )
    }

    @Test
    fun `exhausted recovery returns control to existing final error path`() {
        val policy = AudioRecoveryStateMachine(maxAttempts = 1).apply { beginGeneration("movie:1") }
        policy.requestRecovery("movie:1", AudioFailureClassification.RECOVERABLE_AUDIO, true)
        policy.markRecoveryCommandIssued("movie:1")

        assertEquals(
            AudioRecoveryAction.EXHAUSTED,
            policy.requestRecovery("movie:1", AudioFailureClassification.RECOVERABLE_AUDIO, true),
        )
    }
}
