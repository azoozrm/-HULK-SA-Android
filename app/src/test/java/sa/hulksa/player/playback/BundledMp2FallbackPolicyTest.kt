package sa.hulksa.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.PlaybackRequest

class BundledMp2FallbackPolicyTest {
    private val generation = PlaybackGeneration(230L, "live", 217091, 217091)
    private val livePlan = planMediaSources(liveRequest())

    @Test
    fun `unsupported mp2 routes to software recovery with bundled fallback exposed`() {
        val coordinator = PlayerRecoveryCoordinator(generation, livePlan)
        val command = coordinator.requestAudioRecovery(
            generation.id,
            RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
            alternateTrack = null,
        )

        assertEquals(RecoveryCommandType.RECREATE_WITH_SOFTWARE_AUDIO, command?.type)
        assertEquals(PlayerAudioOutputMode.PLATFORM_SOFTWARE_PCM, outputModeFor(checkNotNull(command).type))
        assertTrue(shouldInstallBundledMp2Renderer(outputModeFor(command.type), bundledMp2Available = true))
        assertEquals(
            BundledMp2DecoderPath.BUNDLED_MP2,
            bundledMp2DecoderPath(
                sampleMimeType = BUNDLED_MP2_MIME_TYPE,
                platformSupport = AudioFormatSupport.UNSUPPORTED,
                outputMode = PlayerAudioOutputMode.PLATFORM_SOFTWARE_PCM,
                bundledMp2Available = true,
            ),
        )
    }

    @Test
    fun `platform supported mp2 stays on platform and does not use bundled decoder`() {
        assertFalse(
            requiresCompatibilityAudioRecovery(
                listOf(track(AudioFormatSupport.SUPPORTED, selected = true)),
                PlayerAudioOutputMode.NORMAL,
            ),
        )
        assertEquals(
            BundledMp2DecoderPath.PLATFORM,
            bundledMp2DecoderPath(
                BUNDLED_MP2_MIME_TYPE,
                AudioFormatSupport.SUPPORTED,
                PlayerAudioOutputMode.NORMAL,
                bundledMp2Available = true,
            ),
        )
    }

    @Test
    fun `alternate supported audio track is attempted before software and bundled path`() {
        val coordinator = PlayerRecoveryCoordinator(generation, livePlan)
        val alternate = AudioTrackRef(0, 1)

        val first = coordinator.requestAudioRecovery(
            generation.id,
            RecoveryTrigger.AUDIO_DECODER_ERROR,
            alternate,
        )

        assertEquals(RecoveryCommandType.SELECT_ALTERNATE_AUDIO_TRACK, first?.type)
        assertEquals(PlayerAudioOutputMode.NORMAL, outputModeFor(checkNotNull(first).type))
        assertFalse(shouldInstallBundledMp2Renderer(PlayerAudioOutputMode.NORMAL, true))
    }

    @Test
    fun `successful software pcm path never needs a second bundled recovery command`() {
        val coordinator = PlayerRecoveryCoordinator(generation, livePlan)
        val software = checkNotNull(
            coordinator.requestAudioRecovery(
                generation.id,
                RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
                alternateTrack = null,
            ),
        )
        assertTrue(coordinator.markCommandApplied(generation.id, software.id))
        assertTrue(coordinator.markAudioHealthy(generation.id))
        assertEquals(RecoveryPhase.HEALTHY, coordinator.phase)
        assertNull(coordinator.activeFailure)
    }

    @Test
    fun `software pcm unsupported mp2 selects bundled decoder path`() {
        assertEquals(
            BundledMp2DecoderPath.BUNDLED_MP2,
            bundledMp2DecoderPath(
                BUNDLED_MP2_MIME_TYPE,
                AudioFormatSupport.UNSUPPORTED,
                PlayerAudioOutputMode.PLATFORM_SOFTWARE_PCM,
                bundledMp2Available = true,
            ),
        )
    }

    @Test
    fun `bundled mp2 unavailable falls through to final audio error budget`() {
        assertEquals(
            BundledMp2DecoderPath.NONE,
            bundledMp2DecoderPath(
                BUNDLED_MP2_MIME_TYPE,
                AudioFormatSupport.UNSUPPORTED,
                PlayerAudioOutputMode.PLATFORM_SOFTWARE_PCM,
                bundledMp2Available = false,
            ),
        )

        val coordinator = PlayerRecoveryCoordinator(generation, livePlan)
        val software = checkNotNull(
            coordinator.requestAudioRecovery(
                generation.id,
                RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
                alternateTrack = null,
            ),
        )
        coordinator.markCommandApplied(generation.id, software.id)
        val exhausted = coordinator.requestAudioRecovery(
            generation.id,
            RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
            alternateTrack = null,
        )
        assertEquals(RecoveryCommandType.SHOW_FINAL_ERROR, exhausted?.type)
        assertEquals(RecoveryFailureClass.AUDIO, exhausted?.failureClass)
    }

    @Test
    fun `source network failure never routes through bundled mp2`() {
        assertEquals(
            RecoveryFailureClass.SOURCE,
            classifyPlaybackFailure(
                audioOutputFailure = false,
                decoderFailure = false,
                rendererMimeType = null,
            ),
        )
        assertEquals(
            BundledMp2DecoderPath.NONE,
            bundledMp2DecoderPath(
                BUNDLED_MP2_MIME_TYPE,
                AudioFormatSupport.UNSUPPORTED,
                PlayerAudioOutputMode.NORMAL,
                bundledMp2Available = true,
            ),
        )
    }

    @Test
    fun `no audio track never routes through bundled mp2`() {
        assertFalse(requiresCompatibilityAudioRecovery(emptyList(), PlayerAudioOutputMode.NORMAL))
        assertEquals(
            BundledMp2DecoderPath.NONE,
            bundledMp2DecoderPath(
                BUNDLED_MP2_MIME_TYPE,
                AudioFormatSupport.UNSUPPORTED,
                PlayerAudioOutputMode.PLATFORM_SOFTWARE_PCM,
                bundledMp2Available = true,
                audioTrackPresent = false,
            ),
        )
    }

    @Test
    fun `stale generation cannot enter software or bundled recovery`() {
        val coordinator = PlayerRecoveryCoordinator(generation, livePlan)
        val staleGenerationId = generation.id + 1L

        assertNull(
            coordinator.requestAudioRecovery(
                staleGenerationId,
                RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
                alternateTrack = null,
            ),
        )
        assertEquals(RecoveryPhase.NORMAL, coordinator.phase)
    }

    @Test
    fun `software and bundled exposure is attempted once per playback generation`() {
        val coordinator = PlayerRecoveryCoordinator(generation, livePlan)
        val first = checkNotNull(
            coordinator.requestAudioRecovery(
                generation.id,
                RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
                alternateTrack = null,
            ),
        )
        assertEquals(RecoveryCommandType.RECREATE_WITH_SOFTWARE_AUDIO, first.type)
        assertTrue(shouldInstallBundledMp2Renderer(outputModeFor(first.type), true))
        coordinator.markCommandApplied(generation.id, first.id)

        val second = coordinator.requestAudioRecovery(
            generation.id,
            RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
            alternateTrack = null,
        )
        assertEquals(RecoveryCommandType.SHOW_FINAL_ERROR, second?.type)
    }

    @Test
    fun `vod replacement state preservation remains unchanged`() {
        val policy = playerReplacementPolicy(
            isLive = false,
            currentPositionMs = 88_000L,
            currentCandidateIndex = 0,
            targetCandidateIndex = 0,
        )

        assertEquals(88_000L, policy.positionMs)
        assertFalse(policy.sourceChanged)
    }

    @Test
    fun `live replacement behavior remains unchanged`() {
        val policy = playerReplacementPolicy(
            isLive = true,
            currentPositionMs = 88_000L,
            currentCandidateIndex = 0,
            targetCandidateIndex = 1,
        )

        assertNull(policy.positionMs)
        assertTrue(policy.sourceChanged)
    }

    @Test
    fun `source fallback behavior remains independent of bundled mp2`() {
        val coordinator = PlayerRecoveryCoordinator(generation, livePlan)
        val source = coordinator.requestSourceRecovery(
            generation.id,
            RecoveryTrigger.NON_RETRIABLE_SOURCE_ERROR,
        )

        assertEquals(RecoveryFailureClass.SOURCE, source?.failureClass)
        assertEquals(RecoveryCommandType.MOVE_TO_NEXT_SOURCE, source?.type)
        assertEquals(PlayerAudioOutputMode.NORMAL, outputModeFor(checkNotNull(source).type))
    }

    @Test
    fun `bundled diagnostics are credential safe by construction`() {
        val diagnostic = bundledMp2DiagnosticFields(
            bundledMp2Available = true,
            decoderPath = BundledMp2DecoderPath.BUNDLED_MP2,
        )

        assertTrue(diagnostic.contains("sampleMimeType=audio/mpeg-L2"))
        assertTrue(diagnostic.contains("bundledMp2Available=true"))
        assertTrue(diagnostic.contains("decoderPath=BUNDLED_MP2"))
        listOf("http://", "https://", "username", "password", "token", "access code")
            .forEach { forbidden -> assertFalse(diagnostic.contains(forbidden, ignoreCase = true)) }
    }

    private fun track(
        support: AudioFormatSupport,
        selected: Boolean,
    ) = AudioTrackCandidate(
        ref = AudioTrackRef(0, 0),
        support = support,
        selected = selected,
        defaultSelection = true,
        autoSelection = true,
    )

    private fun liveRequest() = PlaybackRequest(
        title = "MP2 qualification",
        posterUrl = null,
        candidates = listOf(
            "https://stream.example/live/217091.ts",
            "https://stream.example/live/217091.m3u8",
        ),
        isLive = true,
        historyKey = "LIVE:217091",
        streamKind = "live",
        streamId = 217091,
        extension = "ts",
    )
}
