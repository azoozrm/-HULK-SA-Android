package sa.hulksa.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.PlaybackRequest

/**
 * F12 regression: a multi-source VOD failure that has already moved to an alternate source must
 * become eligible for audio recovery only after the real health monitor proves the new source
 * healthy, and that proof must travel through the production health bridge.
 */
class VodMultiSourceRecoveryHealthTest {
    @Test
    fun `movie alternate source reaches health before audio decoder recovery`() {
        assertRecoverySequence(
            generation = PlaybackGeneration(1_101L, "movie", 511, 1_511),
            request = movieRequest(),
        )
    }

    @Test
    fun `episode alternate source reaches health before audio decoder recovery`() {
        assertRecoverySequence(
            generation = PlaybackGeneration(1_102L, "series", 601, 1_601),
            request = episodeRequest(),
        )
    }

    private fun assertRecoverySequence(
        generation: PlaybackGeneration,
        request: PlaybackRequest,
    ) {
        val plan = planMediaSources(request)
        assertEquals(2, plan.candidates.size)
        val coordinator = createPlayerRecoveryCoordinator(
            generation = generation,
            sourcePlan = plan,
            isLive = false,
        )

        assertTrue(coordinator.beginCandidate(generation.id, 0, sourceChanged = false))
        val move = checkNotNull(
            coordinator.requestSourceRecovery(
                generation.id,
                RecoveryTrigger.TRANSIENT_SOURCE_ERROR,
            ),
        )
        assertEquals(RecoveryCommandType.MOVE_TO_NEXT_SOURCE, move.type)
        assertEquals(1, move.candidateIndex)
        assertTrue(coordinator.markCommandApplied(generation.id, move.id))
        assertTrue(coordinator.beginCandidate(generation.id, 1, sourceChanged = true))
        assertEquals(RecoveryFailureClass.SOURCE, coordinator.activeFailure)
        assertEquals(RecoveryPhase.STABILIZING, coordinator.phase)
        assertFalse(
            canResumeDeferredRecovery(
                activeFailure = coordinator.activeFailure,
                monitorSuppressed = false,
                appForeground = true,
            ),
        )
        assertNull(
            coordinator.requestAudioRecovery(
                generation.id,
                RecoveryTrigger.AUDIO_DECODER_ERROR,
                AudioTrackRef(0, 1),
            ),
        )

        val monitor = PlaybackHealthMonitor(isLive = false)
        monitor.onPrepare(0L)
        assertEquals(
            PlaybackHealthSignal.NONE,
            monitor.evaluate(500L, healthyVodSnapshot(positionMs = 100L)),
        )
        val signal = monitor.evaluate(1_100L, healthyVodSnapshot(positionMs = 700L))
        assertEquals(PlaybackHealthSignal.SOURCE_HEALTHY, signal)

        // A stale generation can never mark this recovered source healthy.
        assertFalse(recordPlaybackHealthSignal(signal, coordinator, generation.id + 1L))
        assertEquals(RecoveryFailureClass.SOURCE, coordinator.activeFailure)

        assertTrue(recordPlaybackHealthSignal(signal, coordinator, generation.id))
        assertNull(coordinator.activeFailure)
        assertEquals(RecoveryPhase.HEALTHY, coordinator.phase)
        assertTrue(
            canResumeDeferredRecovery(
                activeFailure = coordinator.activeFailure,
                monitorSuppressed = false,
                appForeground = true,
            ),
        )

        val alternateTrack = coordinator.requestAudioRecovery(
            generation.id,
            RecoveryTrigger.AUDIO_DECODER_ERROR,
            AudioTrackRef(0, 1),
        )
        assertEquals(RecoveryCommandType.SELECT_ALTERNATE_AUDIO_TRACK, alternateTrack?.type)
        assertTrue(coordinator.markCommandApplied(generation.id, checkNotNull(alternateTrack).id))

        val softwareAudio = coordinator.requestAudioRecovery(
            generation.id,
            RecoveryTrigger.AUDIO_DECODER_ERROR,
            AudioTrackRef(0, 1),
        )
        assertEquals(RecoveryCommandType.RECREATE_WITH_SOFTWARE_AUDIO, softwareAudio?.type)
        assertTrue(coordinator.markCommandApplied(generation.id, checkNotNull(softwareAudio).id))

        val exhausted = coordinator.requestAudioRecovery(
            generation.id,
            RecoveryTrigger.AUDIO_DECODER_ERROR,
            AudioTrackRef(0, 1),
        )
        assertEquals(RecoveryCommandType.SHOW_FINAL_ERROR, exhausted?.type)
        assertEquals(RecoveryPhase.EXHAUSTED, coordinator.phase)
    }

    private fun healthyVodSnapshot(positionMs: Long): PlaybackHealthSnapshot = PlaybackHealthSnapshot(
        playbackState = EnginePlaybackState.READY,
        playWhenReady = true,
        playbackSuppressed = false,
        isPlaying = true,
        positionMs = positionMs,
        isLoading = false,
        firstFrameRendered = true,
        audioTrackPresent = false,
        audioTrackSelected = false,
        audioFormatKnown = false,
        audioDecoderInitialized = false,
        audioPositionAdvanced = false,
        volume = 1f,
        muted = false,
        trackTransitionInProgress = false,
        appForeground = true,
    )

    private fun movieRequest(): PlaybackRequest = PlaybackRequest(
        title = "F12 movie",
        posterUrl = null,
        candidates = listOf(
            "https://primary.example/movie/511.mp4",
            "https://backup.example/movie/511.mp4",
        ),
        isLive = false,
        historyKey = "MOVIE:511",
        streamKind = "movie",
        streamId = 511,
        extension = "mp4",
    )

    private fun episodeRequest(): PlaybackRequest = PlaybackRequest(
        title = "F12 episode",
        posterUrl = null,
        candidates = listOf(
            "https://primary.example/series/601.mp4",
            "https://backup.example/series/601.mp4",
        ),
        isLive = false,
        historyKey = "SERIES:601",
        streamKind = "series",
        streamId = 601,
        extension = "mp4",
        seriesTitle = "F12 series",
        season = 1,
        episodeNumber = 1,
        parentContentId = 77,
    )
}
