package sa.hulksa.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.PlaybackRequest

class AudioTrackSelectionPolicyTest {
    @Test
    fun `supported selected audio keeps normal path`() {
        val selected = track(0, 0, AudioFormatSupport.SUPPORTED, selected = true)
        val alternate = track(0, 1, AudioFormatSupport.SUPPORTED)

        assertNull(chooseSupportedAudioTrack(listOf(selected, alternate)))
    }

    @Test
    fun `unsupported selected audio moves to supported second track`() {
        val unsupported = track(0, 0, AudioFormatSupport.UNSUPPORTED, selected = true)
        val supported = track(0, 1, AudioFormatSupport.SUPPORTED, defaultSelection = true)

        assertEquals(supported.ref, chooseSupportedAudioTrack(listOf(unsupported, supported)))
    }

    @Test
    fun `exceeds capabilities loses to fully supported track`() {
        val exceeds = track(0, 0, AudioFormatSupport.EXCEEDS_CAPABILITIES, selected = true)
        val supported = track(1, 0, AudioFormatSupport.SUPPORTED)

        assertEquals(supported.ref, chooseSupportedAudioTrack(listOf(exceeds, supported)))
    }

    @Test
    fun `multiple supported tracks prefer provider default`() {
        val first = track(0, 0, AudioFormatSupport.SUPPORTED)
        val providerDefault = track(1, 0, AudioFormatSupport.SUPPORTED, defaultSelection = true)

        assertEquals(providerDefault.ref, chooseSupportedAudioTrack(listOf(first, providerDefault)))
    }

    @Test
    fun `proven failure chooses a different supported track`() {
        val selected = track(0, 0, AudioFormatSupport.SUPPORTED, selected = true)
        val alternate = track(0, 1, AudioFormatSupport.SUPPORTED)

        assertEquals(alternate.ref, chooseAlternateSupportedAudioTrack(listOf(selected, alternate)))
    }

    @Test
    fun `no audio track has no automatic override`() {
        assertNull(chooseSupportedAudioTrack(emptyList()))
        assertNull(chooseAlternateSupportedAudioTrack(emptyList()))
        assertFalse(
            requiresCompatibilityAudioRecovery(emptyList(), PlayerAudioOutputMode.NORMAL),
        )
    }

    @Test
    fun `unsupported or exceeds capability enters compatibility mode from normal output`() {
        assertTrue(
            requiresCompatibilityAudioRecovery(
                listOf(track(0, 0, AudioFormatSupport.UNSUPPORTED)),
                PlayerAudioOutputMode.NORMAL,
            ),
        )
        assertTrue(
            requiresCompatibilityAudioRecovery(
                listOf(
                    track(
                        0,
                        0,
                        AudioFormatSupport.EXCEEDS_CAPABILITIES,
                        selected = true,
                    ),
                ),
                PlayerAudioOutputMode.NORMAL,
            ),
        )
    }

    @Test
    fun `selected exceeds track may prove itself in software recovery mode`() {
        assertFalse(
            requiresCompatibilityAudioRecovery(
                listOf(
                    track(
                        0,
                        0,
                        AudioFormatSupport.EXCEEDS_CAPABILITIES,
                        selected = true,
                    ),
                ),
                PlayerAudioOutputMode.PLATFORM_SOFTWARE_PCM,
            ),
        )
        assertTrue(
            requiresCompatibilityAudioRecovery(
                listOf(track(0, 0, AudioFormatSupport.UNSUPPORTED)),
                PlayerAudioOutputMode.PLATFORM_SOFTWARE_PCM,
            ),
        )
    }

    private fun track(
        group: Int,
        index: Int,
        support: AudioFormatSupport,
        selected: Boolean = false,
        defaultSelection: Boolean = false,
    ) = AudioTrackCandidate(
        ref = AudioTrackRef(group, index),
        support = support,
        selected = selected,
        defaultSelection = defaultSelection,
        autoSelection = false,
    )
}

class PlayerRecoveryCoordinatorTest {
    private val generation = PlaybackGeneration(7L, "live", 77, 101)
    private val plan = planMediaSources(liveRequest())

    @Test
    fun `decoder failure uses alternate track before software player`() {
        val coordinator = PlayerRecoveryCoordinator(generation, plan)
        val alternate = AudioTrackRef(0, 1)

        val first = coordinator.requestAudioRecovery(
            generation.id,
            RecoveryTrigger.AUDIO_DECODER_ERROR,
            alternate,
        )
        assertEquals(RecoveryCommandType.SELECT_ALTERNATE_AUDIO_TRACK, first?.type)
        assertEquals(0, first?.candidateIndex)
        coordinator.markCommandApplied(generation.id, checkNotNull(first).id)

        val second = coordinator.requestAudioRecovery(
            generation.id,
            RecoveryTrigger.SILENT_AUDIO,
            alternate,
        )
        assertEquals(RecoveryCommandType.RECREATE_WITH_SOFTWARE_AUDIO, second?.type)
        assertEquals(PlayerAudioOutputMode.PLATFORM_SOFTWARE_PCM, outputModeFor(checkNotNull(second).type))
    }

    @Test
    fun `sink failure without alternate uses software audio once`() {
        val coordinator = PlayerRecoveryCoordinator(generation, plan)

        val command = coordinator.requestAudioRecovery(
            generation.id,
            RecoveryTrigger.AUDIO_SINK_ERROR,
            alternateTrack = null,
        )

        assertEquals(RecoveryCommandType.RECREATE_WITH_SOFTWARE_AUDIO, command?.type)
    }

    @Test
    fun `unsupported audio capability enters the software decoder path`() {
        val coordinator = PlayerRecoveryCoordinator(generation, plan)

        val command = coordinator.requestAudioRecovery(
            generation.id,
            RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
            alternateTrack = null,
        )

        assertEquals(RecoveryCommandType.RECREATE_WITH_SOFTWARE_AUDIO, command?.type)
    }

    @Test
    fun `audio budget exhausts deterministically`() {
        val coordinator = PlayerRecoveryCoordinator(generation, plan)
        val alternate = AudioTrackRef(0, 1)
        val first = checkNotNull(
            coordinator.requestAudioRecovery(generation.id, RecoveryTrigger.SILENT_AUDIO, alternate),
        )
        coordinator.markCommandApplied(generation.id, first.id)
        val second = checkNotNull(
            coordinator.requestAudioRecovery(generation.id, RecoveryTrigger.SILENT_AUDIO, alternate),
        )
        coordinator.markCommandApplied(generation.id, second.id)

        val exhausted = coordinator.requestAudioRecovery(
            generation.id,
            RecoveryTrigger.SILENT_AUDIO,
            alternate,
        )

        assertEquals(RecoveryCommandType.SHOW_FINAL_ERROR, exhausted?.type)
        assertEquals(RecoveryFailureClass.AUDIO, exhausted?.failureClass)
        assertEquals(RecoveryPhase.EXHAUSTED, coordinator.phase)
        assertNull(
            coordinator.requestAudioRecovery(generation.id, RecoveryTrigger.SILENT_AUDIO, alternate),
        )
    }

    @Test
    fun `rejected alternate track advances to software audio`() {
        val coordinator = PlayerRecoveryCoordinator(generation, plan)
        val alternate = AudioTrackRef(0, 1)
        val rejected = checkNotNull(
            coordinator.requestAudioRecovery(
                generation.id,
                RecoveryTrigger.AUDIO_DECODER_ERROR,
                alternate,
            ),
        )

        assertTrue(coordinator.markCommandRejected(generation.id, rejected.id))
        assertEquals(
            RecoveryCommandType.RECREATE_WITH_SOFTWARE_AUDIO,
            coordinator.requestAudioRecovery(
                generation.id,
                RecoveryTrigger.AUDIO_DECODER_ERROR,
                alternateTrack = null,
            )?.type,
        )
    }

    @Test
    fun `successful audio recovery keeps source candidate`() {
        val coordinator = PlayerRecoveryCoordinator(generation, plan)
        val command = checkNotNull(
            coordinator.requestAudioRecovery(generation.id, RecoveryTrigger.AUDIO_DECODER_ERROR, null),
        )
        coordinator.markCommandApplied(generation.id, command.id)

        assertTrue(coordinator.markAudioHealthy(generation.id))
        assertEquals(0, coordinator.candidateIndex)
        assertEquals(RecoveryPhase.HEALTHY, coordinator.phase)
    }

    @Test
    fun `source first failure retries same candidate`() {
        val coordinator = PlayerRecoveryCoordinator(generation, plan)

        val command = coordinator.requestSourceRecovery(
            generation.id,
            RecoveryTrigger.TRANSIENT_SOURCE_ERROR,
        )

        assertEquals(RecoveryCommandType.RETRY_CURRENT_SOURCE, command?.type)
        assertEquals(0, command?.candidateIndex)
        assertEquals(SOURCE_RETRY_BACKOFF_MS, command?.delayMs)
    }

    @Test
    fun `source second failure advances to second provider candidate`() {
        val coordinator = PlayerRecoveryCoordinator(generation, plan)
        val retry = checkNotNull(
            coordinator.requestSourceRecovery(generation.id, RecoveryTrigger.STARTUP_TIMEOUT),
        )
        coordinator.markCommandApplied(generation.id, retry.id)
        coordinator.beginCandidate(generation.id, 0, sourceChanged = false)

        val fallback = coordinator.requestSourceRecovery(
            generation.id,
            RecoveryTrigger.STARTUP_TIMEOUT,
        )

        assertEquals(RecoveryCommandType.MOVE_TO_NEXT_SOURCE, fallback?.type)
        assertEquals(1, fallback?.candidateIndex)
        assertEquals(SOURCE_SWITCH_BACKOFF_MS, fallback?.delayMs)
    }

    @Test
    fun `rejected same-source retry advances to provider fallback`() {
        val coordinator = PlayerRecoveryCoordinator(generation, plan)
        val rejected = checkNotNull(
            coordinator.requestSourceRecovery(
                generation.id,
                RecoveryTrigger.STARTUP_TIMEOUT,
            ),
        )

        assertTrue(coordinator.markCommandRejected(generation.id, rejected.id))
        val fallback = coordinator.requestSourceRecovery(
            generation.id,
            RecoveryTrigger.STARTUP_TIMEOUT,
        )

        assertEquals(RecoveryCommandType.MOVE_TO_NEXT_SOURCE, fallback?.type)
        assertEquals(1, fallback?.candidateIndex)
    }

    @Test
    fun `VOD transient failure skips live same-source retry`() {
        val vodPlan = planMediaSources(vodRequest())
        val coordinator = PlayerRecoveryCoordinator(
            generation = generation,
            sourcePlan = vodPlan,
            maxSameSourceRetries = maxSameSourceRetriesFor(isLive = false),
        )

        assertEquals(
            RecoveryCommandType.MOVE_TO_NEXT_SOURCE,
            coordinator.requestSourceRecovery(
                generation.id,
                RecoveryTrigger.TRANSIENT_SOURCE_ERROR,
            )?.type,
        )
    }

    @Test
    fun `all candidates exhaust without an infinite retry`() {
        val coordinator = PlayerRecoveryCoordinator(generation, plan)
        val commands = mutableListOf<RecoveryCommandType>()

        repeat(4) {
            val command = checkNotNull(
                coordinator.requestSourceRecovery(
                    generation.id,
                    RecoveryTrigger.TRANSIENT_SOURCE_ERROR,
                ),
            )
            commands += command.type
            if (command.type != RecoveryCommandType.SHOW_FINAL_ERROR) {
                coordinator.markCommandApplied(generation.id, command.id)
                val changed = command.candidateIndex != coordinator.candidateIndex
                coordinator.beginCandidate(generation.id, command.candidateIndex, changed)
            }
        }

        assertEquals(
            listOf(
                RecoveryCommandType.RETRY_CURRENT_SOURCE,
                RecoveryCommandType.MOVE_TO_NEXT_SOURCE,
                RecoveryCommandType.RETRY_CURRENT_SOURCE,
                RecoveryCommandType.SHOW_FINAL_ERROR,
            ),
            commands,
        )
        assertNull(
            coordinator.requestSourceRecovery(generation.id, RecoveryTrigger.TRANSIENT_SOURCE_ERROR),
        )
    }

    @Test
    fun `malformed source skips meaningless same-candidate retry`() {
        val coordinator = PlayerRecoveryCoordinator(generation, plan)

        val command = coordinator.requestSourceRecovery(
            generation.id,
            RecoveryTrigger.NON_RETRIABLE_SOURCE_ERROR,
        )

        assertEquals(RecoveryCommandType.MOVE_TO_NEXT_SOURCE, command?.type)
        assertEquals(1, command?.candidateIndex)
    }

    @Test
    fun `non-retriable failure on only candidate reports final error immediately`() {
        val singleSourcePlan = planMediaSources(
            liveRequest(candidates = listOf("https://stream.example/live/77.ts")),
        )
        val coordinator = PlayerRecoveryCoordinator(generation, singleSourcePlan)

        val command = coordinator.requestSourceRecovery(
            generation.id,
            RecoveryTrigger.NON_RETRIABLE_SOURCE_ERROR,
        )

        assertEquals(RecoveryCommandType.SHOW_FINAL_ERROR, command?.type)
    }

    @Test
    fun `audio command blocks concurrent source recovery`() {
        val coordinator = PlayerRecoveryCoordinator(generation, plan)
        coordinator.requestAudioRecovery(generation.id, RecoveryTrigger.SILENT_AUDIO, null)

        assertTrue(coordinator.recoveryInFlight)
        assertNull(
            coordinator.requestSourceRecovery(generation.id, RecoveryTrigger.STARTUP_TIMEOUT),
        )
    }

    @Test
    fun `source command blocks concurrent audio recovery`() {
        val coordinator = PlayerRecoveryCoordinator(generation, plan)
        coordinator.requestSourceRecovery(generation.id, RecoveryTrigger.STARTUP_TIMEOUT)

        assertNull(
            coordinator.requestAudioRecovery(generation.id, RecoveryTrigger.SILENT_AUDIO, null),
        )
    }

    @Test
    fun `source stabilization defers audio recovery until source is healthy`() {
        val coordinator = PlayerRecoveryCoordinator(generation, plan)
        val source = checkNotNull(
            coordinator.requestSourceRecovery(generation.id, RecoveryTrigger.STARTUP_TIMEOUT),
        )
        coordinator.markCommandApplied(generation.id, source.id)

        assertNull(
            coordinator.requestAudioRecovery(generation.id, RecoveryTrigger.SILENT_AUDIO, null),
        )
        assertEquals(RecoveryFailureClass.SOURCE, coordinator.activeFailure)

        coordinator.markSourceHealthy(generation.id)
        assertEquals(
            RecoveryCommandType.RECREATE_WITH_SOFTWARE_AUDIO,
            coordinator.requestAudioRecovery(
                generation.id,
                RecoveryTrigger.SILENT_AUDIO,
                null,
            )?.type,
        )
    }

    @Test
    fun `source evidence serially takes ownership after applied audio recovery`() {
        val coordinator = PlayerRecoveryCoordinator(generation, plan)
        val audio = checkNotNull(
            coordinator.requestAudioRecovery(generation.id, RecoveryTrigger.SILENT_AUDIO, null),
        )
        coordinator.markCommandApplied(generation.id, audio.id)

        val source = coordinator.requestSourceRecovery(
            generation.id,
            RecoveryTrigger.REBUFFER_STALL,
        )

        assertEquals(RecoveryCommandType.RETRY_CURRENT_SOURCE, source?.type)
        assertEquals(RecoveryFailureClass.SOURCE, coordinator.activeFailure)
        assertEquals(RecoveryPhase.SOURCE_RECOVERY, coordinator.phase)
    }

    @Test
    fun `audio health cannot steal an active source recovery`() {
        val coordinator = PlayerRecoveryCoordinator(generation, plan)
        val source = checkNotNull(
            coordinator.requestSourceRecovery(generation.id, RecoveryTrigger.STARTUP_TIMEOUT),
        )
        coordinator.markCommandApplied(generation.id, source.id)

        coordinator.markAudioHealthy(generation.id)

        assertEquals(RecoveryFailureClass.SOURCE, coordinator.activeFailure)
        assertEquals(RecoveryPhase.STABILIZING, coordinator.phase)
    }

    @Test
    fun `source fallback resets audio strategy for the new candidate`() {
        val coordinator = PlayerRecoveryCoordinator(generation, plan)
        val audio = checkNotNull(
            coordinator.requestAudioRecovery(generation.id, RecoveryTrigger.SILENT_AUDIO, null),
        )
        coordinator.markCommandApplied(generation.id, audio.id)
        coordinator.markAudioHealthy(generation.id)
        val retry = checkNotNull(
            coordinator.requestSourceRecovery(generation.id, RecoveryTrigger.STARTUP_TIMEOUT),
        )
        coordinator.markCommandApplied(generation.id, retry.id)
        coordinator.beginCandidate(generation.id, 0, sourceChanged = false)
        val move = checkNotNull(
            coordinator.requestSourceRecovery(generation.id, RecoveryTrigger.STARTUP_TIMEOUT),
        )
        coordinator.markCommandApplied(generation.id, move.id)
        coordinator.beginCandidate(generation.id, 1, sourceChanged = true)
        coordinator.markSourceHealthy(generation.id)

        val reevaluated = coordinator.requestAudioRecovery(
            generation.id,
            RecoveryTrigger.SILENT_AUDIO,
            null,
        )

        assertEquals(RecoveryCommandType.RECREATE_WITH_SOFTWARE_AUDIO, reevaluated?.type)
        assertEquals(1, reevaluated?.candidateIndex)
    }

    @Test
    fun `stale generation cannot recover or report error`() {
        val coordinator = PlayerRecoveryCoordinator(generation, plan)

        assertNull(
            coordinator.requestAudioRecovery(generation.id + 1, RecoveryTrigger.SILENT_AUDIO, null),
        )
        assertNull(
            coordinator.requestSourceRecovery(generation.id + 1, RecoveryTrigger.STARTUP_TIMEOUT),
        )
        assertFalse(coordinator.markAudioHealthy(generation.id + 1))
    }

    @Test
    fun `channel switch invalidation stops old recovery`() {
        val old = PlayerRecoveryCoordinator(generation, plan)
        old.invalidate()
        val nextGeneration = generation.copy(id = generation.id + 1, streamId = 78)
        val next = PlayerRecoveryCoordinator(nextGeneration, plan)

        assertNull(old.requestSourceRecovery(generation.id, RecoveryTrigger.STARTUP_TIMEOUT))
        assertEquals(
            RecoveryCommandType.RETRY_CURRENT_SOURCE,
            next.requestSourceRecovery(nextGeneration.id, RecoveryTrigger.STARTUP_TIMEOUT)?.type,
        )
    }

    @Test
    fun `manual retry explicitly refreshes bounded budgets`() {
        val coordinator = PlayerRecoveryCoordinator(generation, plan)
        val first = checkNotNull(
            coordinator.requestSourceRecovery(generation.id, RecoveryTrigger.STARTUP_TIMEOUT),
        )
        coordinator.markCommandApplied(generation.id, first.id)
        coordinator.beginCandidate(generation.id, 0, sourceChanged = false)

        assertTrue(coordinator.resetForManualRetry(generation.id, 0))
        assertEquals(
            RecoveryCommandType.RETRY_CURRENT_SOURCE,
            coordinator.requestSourceRecovery(generation.id, RecoveryTrigger.STARTUP_TIMEOUT)?.type,
        )
    }
}

class PlaybackHealthMonitorTest {
    @Test
    fun `first frame and progress cancel startup watchdog`() {
        val monitor = PlaybackHealthMonitor(isLive = true)
        monitor.onPrepare(0L)
        monitor.evaluate(500L, healthySnapshot(positionMs = 100L))

        assertEquals(
            PlaybackHealthSignal.SOURCE_HEALTHY,
            monitor.evaluate(1_000L, healthySnapshot(positionMs = 700L)),
        )
        assertNotEquals(
            PlaybackHealthSignal.STARTUP_TIMEOUT,
            monitor.evaluate(LIVE_STARTUP_TIMEOUT_MS + 1L, healthySnapshot(positionMs = 1_500L)),
        )
    }

    @Test
    fun `ready without useful output reaches startup timeout`() {
        val monitor = PlaybackHealthMonitor(isLive = true)
        monitor.onPrepare(0L)

        assertEquals(
            PlaybackHealthSignal.STARTUP_TIMEOUT,
            monitor.evaluate(
                LIVE_STARTUP_TIMEOUT_MS,
                healthySnapshot(positionMs = 0L, firstFrame = false, isPlaying = false),
            ),
        )
    }

    @Test
    fun `short buffering does not recover`() {
        val monitor = healthyLiveMonitor()
        val buffering = healthySnapshot(
            positionMs = 700L,
            state = EnginePlaybackState.BUFFERING,
            isPlaying = false,
        )

        assertEquals(PlaybackHealthSignal.NONE, monitor.evaluate(2_000L, buffering))
        assertEquals(
            PlaybackHealthSignal.NONE,
            monitor.evaluate(2_000L + LIVE_STALL_TIMEOUT_MS - 1L, buffering),
        )
    }

    @Test
    fun `prolonged buffering without progress triggers stall recovery`() {
        val monitor = healthyLiveMonitor()
        val buffering = healthySnapshot(
            positionMs = 700L,
            state = EnginePlaybackState.BUFFERING,
            isPlaying = false,
        )
        monitor.evaluate(2_000L, buffering)

        assertEquals(
            PlaybackHealthSignal.REBUFFER_STALL,
            monitor.evaluate(2_000L + LIVE_STALL_TIMEOUT_MS, buffering),
        )
    }

    @Test
    fun `ready live output without position progress also triggers stall recovery`() {
        val monitor = healthyLiveMonitor()
        val stuckReady = healthySnapshot(positionMs = 700L)
        monitor.evaluate(2_000L, stuckReady)

        assertEquals(
            PlaybackHealthSignal.REBUFFER_STALL,
            monitor.evaluate(2_000L + LIVE_STALL_TIMEOUT_MS, stuckReady),
        )
    }

    @Test
    fun `buffering progress restarts stall deadline`() {
        val monitor = healthyLiveMonitor()
        monitor.evaluate(
            2_000L,
            healthySnapshot(positionMs = 700L, state = EnginePlaybackState.BUFFERING, isPlaying = false),
        )
        monitor.evaluate(
            6_000L,
            healthySnapshot(positionMs = 1_300L, state = EnginePlaybackState.BUFFERING, isPlaying = false),
        )

        assertEquals(
            PlaybackHealthSignal.NONE,
            monitor.evaluate(
                6_000L + LIVE_STALL_TIMEOUT_MS - 1L,
                healthySnapshot(positionMs = 1_300L, state = EnginePlaybackState.BUFFERING, isPlaying = false),
            ),
        )
    }

    @Test
    fun `silent audio requires continuous video and pipeline evidence`() {
        val monitor = healthyLiveMonitor()
        monitor.evaluate(2_000L, healthySnapshot(positionMs = 1_300L))

        assertEquals(
            PlaybackHealthSignal.SILENT_AUDIO,
            monitor.evaluate(
                2_000L + SILENT_AUDIO_TIMEOUT_MS,
                healthySnapshot(positionMs = 2_000L),
            ),
        )
    }

    @Test
    fun `mute and zero volume never trigger silent recovery`() {
        val mutedMonitor = healthyLiveMonitor()
        mutedMonitor.evaluate(2_000L, healthySnapshot(positionMs = 1_300L, muted = true))
        assertEquals(
            PlaybackHealthSignal.NONE,
            mutedMonitor.evaluate(20_000L, healthySnapshot(positionMs = 2_000L, muted = true)),
        )

        val zeroMonitor = healthyLiveMonitor()
        zeroMonitor.evaluate(2_000L, healthySnapshot(positionMs = 1_300L, volume = 0f))
        assertEquals(
            PlaybackHealthSignal.NONE,
            zeroMonitor.evaluate(20_000L, healthySnapshot(positionMs = 2_000L, volume = 0f)),
        )
    }

    @Test
    fun `no audio track never triggers silent recovery`() {
        val monitor = healthyLiveMonitor()
        monitor.evaluate(2_000L, healthySnapshot(positionMs = 1_300L, audioTrack = false))

        assertEquals(
            PlaybackHealthSignal.NONE,
            monitor.evaluate(20_000L, healthySnapshot(positionMs = 2_000L, audioTrack = false)),
        )
    }

    @Test
    fun `audio output advancement proves recovery health`() {
        val monitor = healthyLiveMonitor()

        assertEquals(
            PlaybackHealthSignal.AUDIO_HEALTHY,
            monitor.evaluate(
                2_000L,
                healthySnapshot(positionMs = 1_300L, audioAdvanced = true),
            ),
        )
    }

    @Test
    fun `background suspends watchdog deadlines`() {
        val monitor = PlaybackHealthMonitor(isLive = true)
        monitor.onPrepare(0L)
        monitor.evaluate(
            LIVE_STARTUP_TIMEOUT_MS * 2,
            healthySnapshot(positionMs = 0L, firstFrame = false, foreground = false),
        )

        assertEquals(
            PlaybackHealthSignal.NONE,
            monitor.evaluate(
                LIVE_STARTUP_TIMEOUT_MS * 2 + 1L,
                healthySnapshot(positionMs = 0L, firstFrame = false),
            ),
        )
    }

    @Test
    fun `audio focus suppression pauses watchdog deadlines`() {
        val monitor = PlaybackHealthMonitor(isLive = true)
        monitor.onPrepare(0L)
        monitor.evaluate(
            LIVE_STARTUP_TIMEOUT_MS * 2,
            healthySnapshot(
                positionMs = 0L,
                firstFrame = false,
                playbackSuppressed = true,
            ),
        )

        assertEquals(
            PlaybackHealthSignal.NONE,
            monitor.evaluate(
                LIVE_STARTUP_TIMEOUT_MS * 2 + 1L,
                healthySnapshot(positionMs = 0L, firstFrame = false),
            ),
        )
    }

    @Test
    fun `invalidated watchdog cannot affect replacement generation`() {
        val monitor = PlaybackHealthMonitor(isLive = true)
        monitor.onPrepare(0L)
        monitor.invalidate()

        assertEquals(
            PlaybackHealthSignal.NONE,
            monitor.evaluate(LIVE_STARTUP_TIMEOUT_MS * 2, healthySnapshot(positionMs = 0L)),
        )
    }

    private fun healthyLiveMonitor(): PlaybackHealthMonitor =
        PlaybackHealthMonitor(isLive = true).also { monitor ->
            monitor.onPrepare(0L)
            monitor.evaluate(500L, healthySnapshot(positionMs = 100L))
            assertEquals(
                PlaybackHealthSignal.SOURCE_HEALTHY,
                monitor.evaluate(1_000L, healthySnapshot(positionMs = 700L)),
            )
        }

    private fun healthySnapshot(
        positionMs: Long,
        state: EnginePlaybackState = EnginePlaybackState.READY,
        firstFrame: Boolean = true,
        isPlaying: Boolean = true,
        audioTrack: Boolean = true,
        audioAdvanced: Boolean = false,
        volume: Float = 1f,
        muted: Boolean = false,
        foreground: Boolean = true,
        playbackSuppressed: Boolean = false,
    ) = PlaybackHealthSnapshot(
        playbackState = state,
        playWhenReady = true,
        playbackSuppressed = playbackSuppressed,
        isPlaying = isPlaying,
        positionMs = positionMs,
        isLoading = state == EnginePlaybackState.BUFFERING,
        firstFrameRendered = firstFrame,
        audioTrackPresent = audioTrack,
        audioTrackSelected = audioTrack,
        audioFormatKnown = audioTrack,
        audioDecoderInitialized = audioTrack,
        audioPositionAdvanced = audioAdvanced,
        volume = volume,
        muted = muted,
        trackTransitionInProgress = false,
        appForeground = foreground,
    )
}

class MediaSourcePlannerTest {
    @Test
    fun `live TS and HLS retain provider order with explicit source types`() {
        val plan = planMediaSources(liveRequest())

        assertEquals(listOf(0, 1), plan.candidates.map(PlannedMediaSource::candidateIndex))
        assertEquals(PlannedSourceType.MPEG_TS, plan.candidates[0].type)
        assertEquals("video/mp2t", plan.candidates[0].containerMimeType)
        assertEquals(PlannedSourceType.HLS, plan.candidates[1].type)
        assertEquals("application/x-mpegURL", plan.candidates[1].containerMimeType)
    }

    @Test
    fun `HLS query parameters do not defeat routing hint`() {
        val request = liveRequest(
            candidates = listOf("https://stream.example/live/channel.m3u8?token=redacted"),
        )

        assertEquals(PlannedSourceType.HLS, planMediaSources(request).candidates.single().type)
    }

    @Test
    fun `malformed and unsupported schemes are rejected before playback`() {
        val request = liveRequest(
            candidates = listOf("not a uri", "ftp://stream.example/live/77.ts", "https://stream.example/live/77.ts"),
        )
        val plan = planMediaSources(request)

        assertEquals(setOf(0, 1), plan.rejectedCandidateIndices)
        assertEquals(2, plan.firstCandidateIndex)
    }

    @Test
    fun `planner never invents a TS or HLS alternate`() {
        val request = liveRequest(candidates = listOf("https://stream.example/live/77.ts"))

        assertEquals(1, planMediaSources(request).candidates.size)
    }

    @Test
    fun `automatic source plan is capped even when input is unexpectedly large`() {
        val request = liveRequest(
            candidates = (1..6).map { index ->
                "https://stream.example/live/$index.ts"
            },
        )
        val plan = planMediaSources(request)

        assertEquals(MAX_AUTOMATIC_SOURCE_CANDIDATES, plan.candidates.size)
        assertEquals(setOf(4, 5), plan.rejectedCandidateIndices)
    }

    @Test
    fun `VOD source plan preserves every provider candidate`() {
        val request = vodRequest(
            candidates = (1..6).map { index ->
                "https://stream.example/movie/$index.mp4"
            },
        )
        val plan = planMediaSources(request)

        assertEquals(6, plan.candidates.size)
        assertTrue(plan.rejectedCandidateIndices.isEmpty())
    }

    @Test
    fun `new playback request owns a new generation even with same identity`() {
        val request = liveRequest()

        assertNotEquals(newPlaybackGeneration(request).id, newPlaybackGeneration(request).id)
    }
}

class PlaybackFailureClassificationTest {
    @Test
    fun `audio sink error is audio recovery`() {
        assertEquals(
            RecoveryFailureClass.AUDIO,
            classifyPlaybackFailure(
                audioOutputFailure = true,
                decoderFailure = false,
                rendererMimeType = null,
            ),
        )
    }

    @Test
    fun `audio decoder error is audio recovery`() {
        assertEquals(
            RecoveryFailureClass.AUDIO,
            classifyPlaybackFailure(
                audioOutputFailure = false,
                decoderFailure = true,
                rendererMimeType = "audio/mp4a-latm",
            ),
        )
    }

    @Test
    fun `audio renderer type classifies decoder error when format is unavailable`() {
        assertEquals(
            RecoveryFailureClass.AUDIO,
            classifyPlaybackFailure(
                audioOutputFailure = false,
                decoderFailure = true,
                rendererMimeType = null,
                rendererIsAudio = true,
            ),
        )
    }

    @Test
    fun `video decoder and network errors stay source recovery`() {
        assertEquals(
            RecoveryFailureClass.SOURCE,
            classifyPlaybackFailure(false, true, "video/avc"),
        )
        assertEquals(
            RecoveryFailureClass.SOURCE,
            classifyPlaybackFailure(false, false, null),
        )
    }

    @Test
    fun `bounded HTTP retry statuses are classified without URL inspection`() {
        assertTrue(isTransientHttpStatus(408))
        assertTrue(isTransientHttpStatus(425))
        assertTrue(isTransientHttpStatus(429))
        assertTrue(isTransientHttpStatus(500))
        assertTrue(isTransientHttpStatus(599))
        assertFalse(isTransientHttpStatus(404))
    }
}

class AudioTrackOverrideRecoveryPolicyTest {
    @Test
    fun `decoder and sink failures reprepare after alternate track selection`() {
        assertTrue(shouldReprepareAfterAudioTrackOverride(RecoveryTrigger.AUDIO_DECODER_ERROR))
        assertTrue(shouldReprepareAfterAudioTrackOverride(RecoveryTrigger.AUDIO_SINK_ERROR))
    }

    @Test
    fun `capability correction and silent recovery stay lightweight`() {
        assertFalse(
            shouldReprepareAfterAudioTrackOverride(
                RecoveryTrigger.UNSUPPORTED_SELECTED_AUDIO_TRACK,
            ),
        )
        assertFalse(shouldReprepareAfterAudioTrackOverride(RecoveryTrigger.SILENT_AUDIO))
    }
}

class PlayerReplacementPolicyTest {
    @Test
    fun `VOD replacement preserves position without clearing same-source selections`() {
        val policy = playerReplacementPolicy(
            isLive = false,
            currentPositionMs = 42_000L,
            currentCandidateIndex = 0,
            targetCandidateIndex = 0,
        )

        assertEquals(42_000L, policy.positionMs)
        assertFalse(policy.sourceChanged)
    }

    @Test
    fun `live replacement starts at live edge and clears stale source overrides`() {
        val policy = playerReplacementPolicy(
            isLive = true,
            currentPositionMs = 42_000L,
            currentCandidateIndex = 0,
            targetCandidateIndex = 1,
        )

        assertNull(policy.positionMs)
        assertTrue(policy.sourceChanged)
    }
}

private fun liveRequest(
    candidates: List<String> = listOf(
        "https://stream.example/live/77.ts",
        "https://stream.example/live/77.m3u8",
    ),
) = PlaybackRequest(
    title = "Demo",
    posterUrl = null,
    candidates = candidates,
    isLive = true,
    historyKey = "LIVE:77",
    streamKind = "live",
    streamId = 77,
    extension = "ts",
)

private fun vodRequest(
    candidates: List<String> = listOf(
        "https://stream.example/movie/77.mp4",
        "https://backup.example/movie/77.mp4",
    ),
) = PlaybackRequest(
    title = "Demo movie",
    posterUrl = null,
    candidates = candidates,
    isLive = false,
    historyKey = "movie:77",
    streamKind = "movie",
    streamId = 77,
    extension = "mp4",
)
