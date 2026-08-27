package sa.hulksa.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.PlaybackRequest

class RecoveryGenerationOwnershipTest {
    @Test
    fun `current generation recovery command is owned by current attachment`() {
        val generation = generation(1L)
        val coordinator = coordinator(generation)
        val command = checkNotNull(
            coordinator.requestAudioRecovery(
                generation.id,
                RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
                alternateTrack = null,
            ),
        )
        val owner = RecoveryDispatchOwner(generation.id, playerInstanceId = 0)

        assertTrue(ownsRecoveryCommand(command, owner, owner))
        assertTrue(coordinator.markCommandApplied(generation.id, command.id))
    }

    @Test
    fun `stale generation command is ignored by current attachment`() {
        val generationA = generation(10L)
        val commandA = checkNotNull(
            coordinator(generationA).requestAudioRecovery(
                generationA.id,
                RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
                alternateTrack = null,
            ),
        )
        val ownerA = RecoveryDispatchOwner(generationA.id, playerInstanceId = 0)
        val ownerB = RecoveryDispatchOwner(generationA.id + 1, playerInstanceId = 0)

        assertFalse(ownsRecoveryCommand(commandA, ownerA, ownerB))
    }

    @Test
    fun `stale software command does not consume software recovery budget`() {
        val generation = generation(20L)
        val coordinator = coordinator(generation)
        val stale = checkNotNull(
            coordinator.requestAudioRecovery(
                generation.id,
                RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
                alternateTrack = null,
            ),
        )

        assertEquals(RecoveryCommandType.RECREATE_WITH_SOFTWARE_AUDIO, stale.type)
        assertTrue(coordinator.markCommandStale(generation.id, stale.id))

        val retried = coordinator.requestAudioRecovery(
            generation.id,
            RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
            alternateTrack = null,
        )
        assertEquals(RecoveryCommandType.RECREATE_WITH_SOFTWARE_AUDIO, retried?.type)
    }

    @Test
    fun `delayed generation A command cannot affect generation B`() {
        val generationA = generation(30L)
        val generationB = generation(31L)
        val coordinatorA = coordinator(generationA)
        val coordinatorB = coordinator(generationB)
        val delayedA = checkNotNull(
            coordinatorA.requestAudioRecovery(
                generationA.id,
                RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
                alternateTrack = null,
            ),
        )
        val ownerA = RecoveryDispatchOwner(generationA.id, playerInstanceId = 0)
        val ownerB = RecoveryDispatchOwner(generationB.id, playerInstanceId = 0)

        assertFalse(ownsRecoveryCommand(delayedA, ownerA, ownerB))
        val commandB = checkNotNull(
            coordinatorB.requestAudioRecovery(
                generationB.id,
                RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
                alternateTrack = null,
            ),
        )
        assertTrue(ownsRecoveryCommand(commandB, ownerB, ownerB))
    }

    @Test
    fun `new MP2 generation keeps software recovery available after stale old command`() {
        val generationA = generation(40L)
        val generationB = generation(41L)
        val coordinatorA = coordinator(generationA)
        val coordinatorB = coordinator(generationB)
        val staleA = checkNotNull(
            coordinatorA.requestAudioRecovery(
                generationA.id,
                RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
                alternateTrack = null,
            ),
        )

        assertTrue(coordinatorA.markCommandStale(generationA.id, staleA.id))
        assertEquals(
            RecoveryCommandType.RECREATE_WITH_SOFTWARE_AUDIO,
            coordinatorB.requestAudioRecovery(
                generationB.id,
                RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
                alternateTrack = null,
            )?.type,
        )
    }

    @Test
    fun `sequential MP2 generations each receive one independent software attempt`() {
        listOf(50L, 51L, 52L).forEach { id ->
            val generation = generation(id)
            val coordinator = coordinator(generation)
            val command = checkNotNull(
                coordinator.requestAudioRecovery(
                    generation.id,
                    RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
                    alternateTrack = null,
                ),
            )

            assertEquals(RecoveryCommandType.RECREATE_WITH_SOFTWARE_AUDIO, command.type)
            assertTrue(coordinator.markCommandApplied(generation.id, command.id))
        }
    }

    @Test
    fun `detached old player attachment cannot own command for replacement player`() {
        val generation = generation(60L)
        val coordinator = coordinator(generation)
        val command = checkNotNull(
            coordinator.requestAudioRecovery(
                generation.id,
                RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
                alternateTrack = null,
            ),
        )
        val oldPlayerOwner = RecoveryDispatchOwner(generation.id, playerInstanceId = 0)
        val replacementOwner = RecoveryDispatchOwner(generation.id, playerInstanceId = 1)

        assertFalse(ownsRecoveryCommand(command, oldPlayerOwner, replacementOwner))
    }

    @Test
    fun `stale software dispatch cannot cascade into final error`() {
        val generation = generation(70L)
        val coordinator = coordinator(generation)
        val stale = checkNotNull(
            coordinator.requestAudioRecovery(
                generation.id,
                RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
                alternateTrack = null,
            ),
        )
        assertTrue(coordinator.markCommandStale(generation.id, stale.id))

        val next = coordinator.requestAudioRecovery(
            generation.id,
            RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
            alternateTrack = null,
        )
        assertEquals(RecoveryCommandType.RECREATE_WITH_SOFTWARE_AUDIO, next?.type)
        assertFalse(next?.type == RecoveryCommandType.SHOW_FINAL_ERROR)
    }

    @Test
    fun `legitimate current software rejection remains deterministically bounded`() {
        val generation = generation(80L)
        val coordinator = coordinator(generation)
        val rejected = checkNotNull(
            coordinator.requestAudioRecovery(
                generation.id,
                RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
                alternateTrack = null,
            ),
        )

        assertTrue(coordinator.markCommandRejected(generation.id, rejected.id))
        val terminal = coordinator.requestAudioRecovery(
            generation.id,
            RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
            alternateTrack = null,
        )
        assertEquals(RecoveryCommandType.SHOW_FINAL_ERROR, terminal?.type)
        assertEquals(RecoveryPhase.EXHAUSTED, coordinator.phase)
    }

    @Test
    fun `single flight remains enforced while current command is pending`() {
        val generation = generation(90L)
        val coordinator = coordinator(generation)
        val first = coordinator.requestAudioRecovery(
            generation.id,
            RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
            alternateTrack = null,
        )

        assertEquals(RecoveryCommandType.RECREATE_WITH_SOFTWARE_AUDIO, first?.type)
        assertTrue(coordinator.recoveryInFlight)
        assertNull(
            coordinator.requestAudioRecovery(
                generation.id,
                RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
                alternateTrack = null,
            ),
        )
        assertNull(
            coordinator.requestSourceRecovery(
                generation.id,
                RecoveryTrigger.STARTUP_TIMEOUT,
            ),
        )
    }

    @Test
    fun `source recovery remains independent after stale audio dispatch`() {
        val generation = generation(100L)
        val coordinator = coordinator(generation)
        val staleAudio = checkNotNull(
            coordinator.requestAudioRecovery(
                generation.id,
                RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
                alternateTrack = null,
            ),
        )
        assertTrue(coordinator.markCommandStale(generation.id, staleAudio.id))

        val source = coordinator.requestSourceRecovery(
            generation.id,
            RecoveryTrigger.STARTUP_TIMEOUT,
        )
        assertEquals(RecoveryCommandType.RETRY_CURRENT_SOURCE, source?.type)
        assertEquals(RecoveryFailureClass.SOURCE, source?.failureClass)
    }

    @Test
    fun `stale source retry restores P1-010 same-source retry budget`() {
        val generation = generation(110L)
        val coordinator = coordinator(generation)
        val staleRetry = checkNotNull(
            coordinator.requestSourceRecovery(
                generation.id,
                RecoveryTrigger.STARTUP_TIMEOUT,
            ),
        )

        assertEquals(RecoveryCommandType.RETRY_CURRENT_SOURCE, staleRetry.type)
        assertTrue(coordinator.markCommandStale(generation.id, staleRetry.id))
        assertEquals(
            RecoveryCommandType.RETRY_CURRENT_SOURCE,
            coordinator.requestSourceRecovery(
                generation.id,
                RecoveryTrigger.STARTUP_TIMEOUT,
            )?.type,
        )
    }

    @Test
    fun `applied P1-010 retry still advances to provider fallback`() {
        val generation = generation(120L)
        val coordinator = coordinator(generation)
        val retry = checkNotNull(
            coordinator.requestSourceRecovery(
                generation.id,
                RecoveryTrigger.STARTUP_TIMEOUT,
            ),
        )
        assertTrue(coordinator.markCommandApplied(generation.id, retry.id))
        assertTrue(coordinator.beginCandidate(generation.id, retry.candidateIndex, sourceChanged = false))

        val fallback = coordinator.requestSourceRecovery(
            generation.id,
            RecoveryTrigger.STARTUP_TIMEOUT,
        )
        assertEquals(RecoveryCommandType.MOVE_TO_NEXT_SOURCE, fallback?.type)
        assertEquals(1, fallback?.candidateIndex)
    }

    @Test
    fun `terminal command from detached player is not owned by replacement attachment`() {
        val generation = generation(130L)
        val coordinator = coordinator(generation)
        val software = checkNotNull(
            coordinator.requestAudioRecovery(
                generation.id,
                RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
                alternateTrack = null,
            ),
        )
        assertTrue(coordinator.markCommandRejected(generation.id, software.id))
        val terminal = checkNotNull(
            coordinator.requestAudioRecovery(
                generation.id,
                RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
                alternateTrack = null,
            ),
        )
        val detachedOwner = RecoveryDispatchOwner(generation.id, playerInstanceId = 0)
        val currentOwner = RecoveryDispatchOwner(generation.id, playerInstanceId = 1)

        assertEquals(RecoveryCommandType.SHOW_FINAL_ERROR, terminal.type)
        assertFalse(ownsRecoveryCommand(terminal, detachedOwner, currentOwner))
    }

    private fun generation(id: Long) = PlaybackGeneration(
        id = id,
        streamKind = "live",
        streamId = id.toInt(),
        historyIdentity = id.toInt(),
    )

    private fun coordinator(generation: PlaybackGeneration) = PlayerRecoveryCoordinator(
        generation = generation,
        sourcePlan = planMediaSources(liveRequest()),
    )

    private fun liveRequest() = PlaybackRequest(
        title = "Race test",
        posterUrl = null,
        candidates = listOf(
            "https://stream.example/live/77.ts",
            "https://stream.example/live/77.m3u8",
        ),
        isLive = true,
        historyKey = "LIVE:77",
        streamKind = "live",
        streamId = 77,
        extension = "ts",
    )
}
