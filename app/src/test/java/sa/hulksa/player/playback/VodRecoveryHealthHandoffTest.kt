package sa.hulksa.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.PlaybackRequest

/**
 * F01 correction 01: the VOD network-restore command leaves a SOURCE recovery stabilizing. Actual
 * VOD playback health must drive the existing SOURCE_HEALTHY -> markSourceHealthy path so a later
 * audio failure can use the bounded audio-recovery path.
 */
class VodRecoveryHealthHandoffTest {
    private val generation = PlaybackGeneration(911L, "movie", 511, 1_511)

    @Test
    fun `single source vod recovery reaches source health before later audio recovery`() {
        val coordinator = createPlayerRecoveryCoordinator(
            generation = generation,
            sourcePlan = moviePlan(),
            isLive = false,
        )

        assertTrue(coordinator.onNetworkUnavailable(generation.id))
        val restore = checkNotNull(
            coordinator.requestSourceRecovery(
                generation.id,
                RecoveryTrigger.NETWORK_RESTORED,
            ),
        )
        assertEquals(RecoveryCommandType.RETRY_CURRENT_SOURCE, restore.type)
        assertTrue(coordinator.markCommandApplied(generation.id, restore.id))
        assertTrue(coordinator.beginCandidate(generation.id, 0, sourceChanged = false))
        assertEquals(RecoveryFailureClass.SOURCE, coordinator.activeFailure)
        assertEquals(RecoveryPhase.STABILIZING, coordinator.phase)
        assertFalse(
            canResumeDeferredRecovery(
                activeFailure = coordinator.activeFailure,
                monitorSuppressed = false,
                appForeground = true,
            ),
        )

        // The SOURCE/STABILIZING guard is correct before playback proves the source healthy.
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

        val audio = coordinator.requestAudioRecovery(
            generation.id,
            RecoveryTrigger.AUDIO_DECODER_ERROR,
            AudioTrackRef(0, 1),
        )
        assertEquals(RecoveryCommandType.SELECT_ALTERNATE_AUDIO_TRACK, audio?.type)
        assertEquals(RecoveryFailureClass.AUDIO, audio?.failureClass)
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

    private fun moviePlan(): MediaSourcePlan = planMediaSources(
        PlaybackRequest(
            title = "F01 correction movie",
            posterUrl = null,
            candidates = listOf("https://stream.example/movie/511.mp4"),
            isLive = false,
            historyKey = "MOVIE:511",
            streamKind = "movie",
            streamId = 511,
            extension = "mp4",
        ),
    )
}
