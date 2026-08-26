@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package sa.hulksa.player.ui.screens

import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRecoveryPolicyTest {

    @Test
    fun `explicit audio decoder failure recovers before tracks populate`() {
        val policy = AudioRecoveryStateMachine().apply { beginGeneration("live:1") }
        val classification = classifyAudioFailure(
            errorCode = PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            rendererError = true,
            rendererMimeType = "audio/mp4a-latm",
        )
        val effectiveTrackEvidence = explicitAudioRecoveryGate(
            classification = classification,
            hasAudioTrack = false,
        )

        assertEquals(AudioFailureClassification.RECOVERABLE_AUDIO, classification)
        assertTrue(effectiveTrackEvidence)
        assertEquals(
            AudioRecoveryAction.REPREPARE_CURRENT,
            policy.requestRecovery(
                key = "live:1",
                classification = classification,
                hasAudioTrack = false,
                explicitAudioEvidence = true,
            ),
        )
        assertEquals(1, policy.attemptsUsed)
    }

    @Test
    fun `video decoder failure is not audio recovery`() {
        val classification = classifyAudioFailure(
            errorCode = PlaybackException.ERROR_CODE_DECODING_FAILED,
            rendererError = true,
            rendererMimeType = "video/avc",
        )
        assertEquals(AudioFailureClassification.NON_AUDIO, classification)
        assertFalse(explicitAudioRecoveryGate(classification, hasAudioTrack = false))
    }

    @Test
    fun `network failure is not audio recovery`() {
        val classification = classifyAudioFailure(
            errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            rendererError = false,
            rendererMimeType = null,
        )
        assertEquals(AudioFailureClassification.NON_AUDIO, classification)
        assertFalse(explicitAudioRecoveryGate(classification, hasAudioTrack = false))
    }

    @Test
    fun `audio sink init and write failures are recoverable`() {
        assertEquals(
            AudioFailureClassification.RECOVERABLE_AUDIO,
            classifyAudioFailure(
                errorCode = PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                rendererError = false,
                rendererMimeType = null,
            ),
        )
        assertEquals(
            AudioFailureClassification.RECOVERABLE_AUDIO,
            classifyAudioFailure(
                errorCode = PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
                rendererError = false,
                rendererMimeType = null,
            ),
        )
    }

    @Test
    fun `attempt one remains lightweight same player reprepare`() {
        val plan = audioRecoveryExecutionPlan(AudioRecoveryAction.REPREPARE_CURRENT)

        assertEquals(AudioRecoveryExecutionKind.SAME_PLAYER_REPREPARE, plan.kind)
        assertEquals(AudioOutputCompatibilityMode.NORMAL, plan.outputMode)
    }

    @Test
    fun `attempt two requires full player recreation in pcm compatibility mode`() {
        val plan = audioRecoveryExecutionPlan(AudioRecoveryAction.RECREATE_PLAYER_COMPATIBILITY)

        assertEquals(AudioRecoveryExecutionKind.RECREATE_PLAYER_PCM_COMPATIBILITY, plan.kind)
        assertEquals(AudioOutputCompatibilityMode.PCM_COMPATIBILITY, plan.outputMode)
    }

    @Test
    fun `first confirmed silent failure starts only attempt one`() {
        val policy = AudioRecoveryStateMachine().apply { beginGeneration("live:1") }

        assertEquals(
            AudioRecoveryAction.REPREPARE_CURRENT,
            policy.requestRecovery(
                "live:1",
                AudioFailureClassification.RECOVERABLE_AUDIO,
                hasAudioTrack = true,
            ),
        )
        assertEquals(1, policy.attemptsUsed)
    }

    @Test
    fun `repeated callback cannot start parallel recovery`() {
        val policy = AudioRecoveryStateMachine().apply { beginGeneration("live:1") }
        assertEquals(
            AudioRecoveryAction.REPREPARE_CURRENT,
            policy.requestRecovery("live:1", AudioFailureClassification.RECOVERABLE_AUDIO, true),
        )
        assertEquals(
            AudioRecoveryAction.NONE,
            policy.requestRecovery("live:1", AudioFailureClassification.RECOVERABLE_AUDIO, true),
        )
        assertEquals(1, policy.attemptsUsed)
    }

    @Test
    fun `aborted execution releases single flight without restoring attempt budget`() {
        val policy = AudioRecoveryStateMachine().apply { beginGeneration("live:1") }
        policy.requestRecovery("live:1", AudioFailureClassification.RECOVERABLE_AUDIO, true)

        policy.markRecoveryCommandAborted("live:1")

        assertFalse(policy.recoveryPending)
        assertEquals(1, policy.attemptsUsed)
        assertEquals(
            AudioRecoveryAction.RECREATE_PLAYER_COMPATIBILITY,
            policy.requestRecovery("live:1", AudioFailureClassification.RECOVERABLE_AUDIO, true),
        )
    }

    @Test
    fun `second confirmed silent failure starts attempt two`() {
        val policy = AudioRecoveryStateMachine().apply { beginGeneration("live:1") }
        policy.requestRecovery("live:1", AudioFailureClassification.RECOVERABLE_AUDIO, true)
        policy.markRecoveryCommandIssued("live:1")

        assertEquals(
            AudioRecoveryAction.RECREATE_PLAYER_COMPATIBILITY,
            policy.requestRecovery("live:1", AudioFailureClassification.RECOVERABLE_AUDIO, true),
        )
        assertEquals(2, policy.attemptsUsed)
    }

    @Test
    fun `after two attempts recovery is exhausted`() {
        val policy = AudioRecoveryStateMachine().apply { beginGeneration("live:1") }
        policy.requestRecovery("live:1", AudioFailureClassification.RECOVERABLE_AUDIO, true)
        policy.markRecoveryCommandIssued("live:1")
        policy.requestRecovery("live:1", AudioFailureClassification.RECOVERABLE_AUDIO, true)
        policy.markRecoveryCommandIssued("live:1")

        assertEquals(
            AudioRecoveryAction.EXHAUSTED,
            policy.requestRecovery("live:1", AudioFailureClassification.RECOVERABLE_AUDIO, true),
        )
        assertEquals(MAX_AUDIO_RECOVERY_ATTEMPTS, policy.attemptsUsed)
    }

    @Test
    fun `ready alone does not complete pending recovery`() {
        val policy = AudioRecoveryStateMachine().apply { beginGeneration("live:1") }
        policy.requestRecovery("live:1", AudioFailureClassification.RECOVERABLE_AUDIO, true)
        policy.markRecoveryCommandIssued("live:1")

        policy.markPlaybackReady("live:1")

        assertTrue(policy.recoveryPending)
        assertEquals(1, policy.attemptsUsed)
    }

    @Test
    fun `valid audio progression completes pending recovery`() {
        val policy = AudioRecoveryStateMachine().apply { beginGeneration("live:1") }
        policy.requestRecovery("live:1", AudioFailureClassification.RECOVERABLE_AUDIO, true)
        policy.markRecoveryCommandIssued("live:1")

        policy.markAudioHealthy("live:1")

        assertFalse(policy.recoveryPending)
        assertEquals(1, policy.attemptsUsed)
    }

    @Test
    fun `new generation gets fresh detector recovery budget`() {
        val policy = AudioRecoveryStateMachine().apply { beginGeneration("live:1") }
        policy.requestRecovery("live:1", AudioFailureClassification.RECOVERABLE_AUDIO, true)
        policy.markRecoveryCommandIssued("live:1")
        policy.requestRecovery("live:1", AudioFailureClassification.RECOVERABLE_AUDIO, true)
        policy.markRecoveryCommandIssued("live:1")

        policy.beginGeneration("live:2")

        assertEquals(0, policy.attemptsUsed)
        assertEquals(
            AudioRecoveryAction.REPREPARE_CURRENT,
            policy.requestRecovery("live:2", AudioFailureClassification.RECOVERABLE_AUDIO, true),
        )
    }

    @Test
    fun `stale generation is ignored`() {
        val policy = AudioRecoveryStateMachine().apply { beginGeneration("live:B") }

        assertEquals(
            AudioRecoveryAction.NONE,
            policy.requestRecovery("live:A", AudioFailureClassification.RECOVERABLE_AUDIO, true),
        )
        assertEquals(0, policy.attemptsUsed)
    }

    @Test
    fun `silent heuristic still requires audio track evidence`() {
        val policy = AudioRecoveryStateMachine().apply { beginGeneration("live:1") }

        assertEquals(
            AudioRecoveryAction.NONE,
            policy.requestRecovery(
                "live:1",
                AudioFailureClassification.RECOVERABLE_AUDIO,
                hasAudioTrack = false,
                explicitAudioEvidence = false,
            ),
        )
        assertEquals(0, policy.attemptsUsed)
    }

    @Test
    fun `dispose invalidates detector recovery budget`() {
        val policy = AudioRecoveryStateMachine().apply { beginGeneration("live:1") }
        policy.requestRecovery("live:1", AudioFailureClassification.RECOVERABLE_AUDIO, true)

        policy.invalidate()

        assertFalse(policy.recoveryPending)
        assertEquals(
            AudioRecoveryAction.NONE,
            policy.requestRecovery("live:1", AudioFailureClassification.RECOVERABLE_AUDIO, true),
        )
    }

    @Test
    fun `live recreation has no vod seek restoration`() {
        val state = preservedAudioRecoveryState(
            isLive = true,
            currentPositionMs = 98_765L,
            playWhenReady = true,
            speed = 1f,
            volume = .8f,
            trackSelectionParameters = "tracks",
        )

        assertNull(state.positionMs)
    }

    @Test
    fun `vod recreation preserves position speed volume and track parameters`() {
        val trackSelection = "selected-audio-and-subtitle-overrides"
        val state = preservedAudioRecoveryState(
            isLive = false,
            currentPositionMs = 45_000L,
            playWhenReady = true,
            speed = 1.25f,
            volume = .65f,
            trackSelectionParameters = trackSelection,
        )

        assertEquals(45_000L, state.positionMs)
        assertTrue(state.playWhenReady)
        assertEquals(1.25f, state.speed)
        assertEquals(.65f, state.volume)
        assertEquals(trackSelection, state.trackSelectionParameters)
    }

    @Test
    fun `compatibility track parameters preserve selections and disable offload`() {
        val original = TrackSelectionParameters.DEFAULT.buildUpon()
            .setPreferredAudioLanguage("ar")
            .setPreferredTextLanguage("en")
            .build()

        val compatibility = original.withCompatibilityAudioOutput()

        assertEquals(original.preferredAudioLanguages, compatibility.preferredAudioLanguages)
        assertEquals(original.preferredTextLanguages, compatibility.preferredTextLanguages)
        assertEquals(
            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED,
            compatibility.audioOffloadPreferences.audioOffloadMode,
        )
    }

    @Test
    fun `recreation request retains same source and candidate`() {
        val request = AudioPlayerRecreationRequest(
            generationKey = "live:99:history",
            expectedSource = "https://redacted.example/stream",
            candidateIndex = 0,
            preservedState = preservedAudioRecoveryState(
                isLive = true,
                currentPositionMs = 123L,
                playWhenReady = true,
                speed = 1f,
                volume = 1f,
                trackSelectionParameters = "tracks",
            ),
            outputMode = AudioOutputCompatibilityMode.PCM_COMPATIBILITY,
        )

        assertEquals("https://redacted.example/stream", request.expectedSource)
        assertEquals(0, request.candidateIndex)
        assertEquals(AudioOutputCompatibilityMode.PCM_COMPATIBILITY, request.outputMode)
        assertNull(request.preservedState.positionMs)
    }

    @Test
    fun `next content always starts in normal output mode`() {
        assertEquals(AudioOutputCompatibilityMode.NORMAL, initialAudioOutputCompatibilityMode())
        assertEquals(AudioOutputCompatibilityMode.NORMAL, initialAudioOutputCompatibilityMode())
    }
}

class AudioPlaybackHealthPolicyTest {
    private val healthyBase = AudioPlaybackHealthSnapshot(
        playbackReady = true,
        isPlaying = true,
        playbackProgressing = true,
        audioTrackPresent = true,
        audioTrackSelected = true,
        audioFormatKnown = true,
        audioPositionAdvanced = false,
        volume = 1f,
        muted = false,
        buffering = false,
        seekInProgress = false,
        trackTransitionInProgress = false,
        appForeground = true,
    )

    @Test
    fun `healthy audio progression prevents silent recovery`() {
        val policy = AudioPlaybackHealthPolicy().apply { beginGeneration("live:1") }

        assertEquals(
            AudioPlaybackHealthDecision.HEALTHY,
            policy.evaluate("live:1", 20_000L, healthyBase.copy(audioPositionAdvanced = true)),
        )
    }

    @Test
    fun `before four second grace is waiting`() {
        val policy = AudioPlaybackHealthPolicy().apply { beginGeneration("live:1") }
        assertEquals(AudioPlaybackHealthDecision.WAITING, policy.evaluate("live:1", 1_000L, healthyBase))
        assertEquals(AudioPlaybackHealthDecision.WAITING, policy.evaluate("live:1", 4_999L, healthyBase))
    }

    @Test
    fun `after grace before confirmation is suspected`() {
        val policy = AudioPlaybackHealthPolicy().apply { beginGeneration("live:1") }
        policy.evaluate("live:1", 1_000L, healthyBase)

        assertEquals(
            AudioPlaybackHealthDecision.SUSPECTED_SILENT_AUDIO,
            policy.evaluate("live:1", 5_000L, healthyBase),
        )
        assertEquals(
            AudioPlaybackHealthDecision.SUSPECTED_SILENT_AUDIO,
            policy.evaluate("live:1", 6_499L, healthyBase),
        )
    }

    @Test
    fun `after grace and confirmation recovers audio`() {
        val policy = AudioPlaybackHealthPolicy().apply { beginGeneration("live:1") }
        policy.evaluate("live:1", 1_000L, healthyBase)

        assertEquals(
            AudioPlaybackHealthDecision.RECOVER_AUDIO,
            policy.evaluate("live:1", 6_500L, healthyBase),
        )
    }

    @Test
    fun `buffering is no action`() {
        assertNoAction(healthyBase.copy(buffering = true))
    }

    @Test
    fun `paused is no action`() {
        assertNoAction(healthyBase.copy(isPlaying = false))
    }

    @Test
    fun `volume zero is no action`() {
        assertNoAction(healthyBase.copy(volume = 0f))
    }

    @Test
    fun `muted is no action`() {
        assertNoAction(healthyBase.copy(muted = true))
    }

    @Test
    fun `no audio track is no action`() {
        assertNoAction(healthyBase.copy(audioTrackPresent = false))
    }

    @Test
    fun `unselected audio track is no action`() {
        assertNoAction(healthyBase.copy(audioTrackSelected = false))
    }

    @Test
    fun `unknown audio format is no action`() {
        assertNoAction(healthyBase.copy(audioFormatKnown = false))
    }

    @Test
    fun `seek is no action and resets grace`() {
        val policy = AudioPlaybackHealthPolicy().apply { beginGeneration("live:1") }
        policy.evaluate("live:1", 1_000L, healthyBase)
        policy.evaluate("live:1", 4_000L, healthyBase.copy(seekInProgress = true))

        assertEquals(AudioPlaybackHealthDecision.WAITING, policy.evaluate("live:1", 7_000L, healthyBase))
    }

    @Test
    fun `track transition is no action and resets grace`() {
        val policy = AudioPlaybackHealthPolicy().apply { beginGeneration("live:1") }
        policy.evaluate("live:1", 1_000L, healthyBase)
        policy.evaluate("live:1", 4_000L, healthyBase.copy(trackTransitionInProgress = true))

        assertEquals(AudioPlaybackHealthDecision.WAITING, policy.evaluate("live:1", 7_000L, healthyBase))
    }

    @Test
    fun `format change reset uses explicit reset observation`() {
        val policy = AudioPlaybackHealthPolicy().apply { beginGeneration("live:1") }
        policy.evaluate("live:1", 1_000L, healthyBase)
        policy.resetObservation("live:1")

        assertEquals(AudioPlaybackHealthDecision.WAITING, policy.evaluate("live:1", 8_000L, healthyBase))
    }

    @Test
    fun `background is no action`() {
        assertNoAction(healthyBase.copy(appForeground = false))
    }

    @Test
    fun `playback must actually progress`() {
        assertNoAction(healthyBase.copy(playbackProgressing = false))
    }

    @Test
    fun `ready alone is not success`() {
        val policy = AudioPlaybackHealthPolicy().apply { beginGeneration("live:1") }
        assertEquals(
            AudioPlaybackHealthDecision.NO_ACTION,
            policy.evaluate(
                "live:1",
                1_000L,
                healthyBase.copy(isPlaying = false, playbackProgressing = false),
            ),
        )
    }

    @Test
    fun `new generation has fresh detector`() {
        val policy = AudioPlaybackHealthPolicy().apply { beginGeneration("live:1") }
        policy.evaluate("live:1", 1_000L, healthyBase)
        assertEquals(AudioPlaybackHealthDecision.RECOVER_AUDIO, policy.evaluate("live:1", 6_500L, healthyBase))

        policy.beginGeneration("live:2")

        assertEquals(AudioPlaybackHealthDecision.WAITING, policy.evaluate("live:2", 6_500L, healthyBase))
    }

    @Test
    fun `stale generation detector is ignored`() {
        val policy = AudioPlaybackHealthPolicy().apply { beginGeneration("live:B") }
        assertEquals(
            AudioPlaybackHealthDecision.NO_ACTION,
            policy.evaluate("live:A", 10_000L, healthyBase),
        )
    }

    @Test
    fun `dispose invalidates detector`() {
        val policy = AudioPlaybackHealthPolicy().apply { beginGeneration("live:1") }
        policy.invalidate()

        assertEquals(
            AudioPlaybackHealthDecision.NO_ACTION,
            policy.evaluate("live:1", 10_000L, healthyBase),
        )
    }

    private fun assertNoAction(snapshot: AudioPlaybackHealthSnapshot) {
        val policy = AudioPlaybackHealthPolicy().apply { beginGeneration("live:1") }
        assertEquals(AudioPlaybackHealthDecision.NO_ACTION, policy.evaluate("live:1", 10_000L, snapshot))
    }
}
