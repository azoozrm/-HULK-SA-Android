package sa.hulksa.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.PlaybackRequest

/**
 * F01 correction tests: single-source remote VOD recovers after a confirmed outage, even though
 * ordinary VOD same-source retries are zero. These tests use the production recovery configuration
 * factory, so `maxSameSourceRetriesFor(...)` and the dedicated outage policy are exercised rather
 * than a test-only default.
 */
class VodNetworkRestoreRecoveryTest {
    private val movieGeneration = PlaybackGeneration(901L, "movie", 501, 1_501)
    private val episodeGeneration = PlaybackGeneration(902L, "series", 601, 1_601)
    private val liveGeneration = PlaybackGeneration(903L, "live", 701, 1_701)

    @Test
    fun `single source movie recovers the current source after a confirmed outage`() {
        val coordinator = vodCoordinator(movieGeneration, moviePlan(sourceCount = 1))

        assertTrue(coordinator.onNetworkUnavailable(movieGeneration.id))
        val command = coordinator.requestSourceRecovery(
            movieGeneration.id,
            RecoveryTrigger.NETWORK_RESTORED,
        )

        assertEquals(RecoveryCommandType.RETRY_CURRENT_SOURCE, command?.type)
        assertEquals(RecoveryFailureClass.SOURCE, command?.failureClass)
        assertEquals(0, command?.candidateIndex)
        assertEquals(SOURCE_RETRY_BACKOFF_MS, command?.delayMs)
        assertEquals(RecoveryPhase.SOURCE_RECOVERY, coordinator.phase)
    }

    @Test
    fun `single source episode recovers exactly like a movie`() {
        val coordinator = vodCoordinator(episodeGeneration, episodePlan(sourceCount = 1))

        assertTrue(coordinator.onNetworkUnavailable(episodeGeneration.id))
        val command = coordinator.requestSourceRecovery(
            episodeGeneration.id,
            RecoveryTrigger.NETWORK_RESTORED,
        )

        assertEquals(RecoveryCommandType.RETRY_CURRENT_SOURCE, command?.type)
        assertEquals(0, command?.candidateIndex)
    }

    @Test
    fun `duplicate online callbacks dispatch at most one command per outage`() {
        val coordinator = vodCoordinator(movieGeneration, moviePlan(sourceCount = 1))
        assertTrue(coordinator.onNetworkUnavailable(movieGeneration.id))
        assertTrue(coordinator.onNetworkUnavailable(movieGeneration.id))

        val first = coordinator.requestSourceRecovery(
            movieGeneration.id,
            RecoveryTrigger.NETWORK_RESTORED,
        )
        assertNotNull(first)
        assertNull(
            coordinator.requestSourceRecovery(
                movieGeneration.id,
                RecoveryTrigger.NETWORK_RESTORED,
            ),
        )
        assertTrue(coordinator.markCommandApplied(movieGeneration.id, checkNotNull(first).id))
        assertNull(
            coordinator.requestSourceRecovery(
                movieGeneration.id,
                RecoveryTrigger.NETWORK_RESTORED,
            ),
        )
        assertTrue(coordinator.beginCandidate(movieGeneration.id, 0, sourceChanged = false))
        assertNull(
            coordinator.requestSourceRecovery(
                movieGeneration.id,
                RecoveryTrigger.NETWORK_RESTORED,
            ),
        )
    }

    @Test
    fun `a later confirmed outage after successful recovery remains recoverable`() {
        val coordinator = vodCoordinator(movieGeneration, moviePlan(sourceCount = 1))
        assertTrue(coordinator.onNetworkUnavailable(movieGeneration.id))
        val first = checkNotNull(
            coordinator.requestSourceRecovery(
                movieGeneration.id,
                RecoveryTrigger.NETWORK_RESTORED,
            ),
        )
        assertTrue(coordinator.markCommandApplied(movieGeneration.id, first.id))
        assertTrue(coordinator.beginCandidate(movieGeneration.id, 0, sourceChanged = false))

        assertTrue(coordinator.onNetworkUnavailable(movieGeneration.id))
        val second = coordinator.requestSourceRecovery(
            movieGeneration.id,
            RecoveryTrigger.NETWORK_RESTORED,
        )

        assertEquals(RecoveryCommandType.RETRY_CURRENT_SOURCE, second?.type)
        assertNotEquals(first.id, second?.id)
    }

    @Test
    fun `network return without a confirmed outage never restarts playback`() {
        val coordinator = vodCoordinator(movieGeneration, moviePlan(sourceCount = 1))

        assertNull(
            coordinator.requestSourceRecovery(
                movieGeneration.id,
                RecoveryTrigger.NETWORK_RESTORED,
            ),
        )
        assertEquals(RecoveryPhase.NORMAL, coordinator.phase)
    }

    @Test
    fun `a prepare that did not use the restore command clears a stale arm`() {
        val coordinator = vodCoordinator(movieGeneration, moviePlan(sourceCount = 1))
        assertTrue(coordinator.onNetworkUnavailable(movieGeneration.id))
        assertTrue(coordinator.beginCandidate(movieGeneration.id, 0, sourceChanged = false))

        assertNull(
            coordinator.requestSourceRecovery(
                movieGeneration.id,
                RecoveryTrigger.NETWORK_RESTORED,
            ),
        )
        assertTrue(coordinator.onNetworkUnavailable(movieGeneration.id))
        assertEquals(
            RecoveryCommandType.RETRY_CURRENT_SOURCE,
            coordinator.requestSourceRecovery(
                movieGeneration.id,
                RecoveryTrigger.NETWORK_RESTORED,
            )?.type,
        )
    }

    @Test
    fun `vod restore reopens a coordinator exhausted by the outage error`() {
        val coordinator = vodCoordinator(movieGeneration, moviePlan(sourceCount = 1))
        val terminal = coordinator.requestSourceRecovery(
            movieGeneration.id,
            RecoveryTrigger.TRANSIENT_SOURCE_ERROR,
        )
        assertEquals(RecoveryCommandType.SHOW_FINAL_ERROR, terminal?.type)
        assertEquals(RecoveryPhase.EXHAUSTED, coordinator.phase)

        assertTrue(coordinator.onNetworkUnavailable(movieGeneration.id))
        val restore = coordinator.requestSourceRecovery(
            movieGeneration.id,
            RecoveryTrigger.NETWORK_RESTORED,
        )

        assertEquals(RecoveryCommandType.RETRY_CURRENT_SOURCE, restore?.type)
        assertEquals(RecoveryPhase.SOURCE_RECOVERY, coordinator.phase)
    }

    @Test
    fun `failed network recovery is bounded and keeps manual retry available`() {
        val coordinator = vodCoordinator(movieGeneration, moviePlan(sourceCount = 1))
        assertTrue(coordinator.onNetworkUnavailable(movieGeneration.id))
        val retry = checkNotNull(
            coordinator.requestSourceRecovery(
                movieGeneration.id,
                RecoveryTrigger.NETWORK_RESTORED,
            ),
        )
        assertTrue(coordinator.markCommandApplied(movieGeneration.id, retry.id))
        assertTrue(coordinator.beginCandidate(movieGeneration.id, 0, sourceChanged = false))

        val terminal = coordinator.requestSourceRecovery(
            movieGeneration.id,
            RecoveryTrigger.TRANSIENT_SOURCE_ERROR,
        )

        assertEquals(RecoveryCommandType.SHOW_FINAL_ERROR, terminal?.type)
        assertEquals(RecoveryPhase.EXHAUSTED, coordinator.phase)
        assertNull(
            coordinator.requestSourceRecovery(
                movieGeneration.id,
                RecoveryTrigger.NETWORK_RESTORED,
            ),
        )
        assertTrue(coordinator.resetForManualRetry(movieGeneration.id, 0))
        assertEquals(RecoveryPhase.NORMAL, coordinator.phase)
    }

    @Test
    fun `background deferral still produces exactly one foreground recovery`() {
        val coordinator = vodCoordinator(movieGeneration, moviePlan(sourceCount = 1))
        assertTrue(coordinator.onNetworkUnavailable(movieGeneration.id))
        assertFalse(coordinator.recoveryInFlight)

        val foreground = coordinator.requestSourceRecovery(
            movieGeneration.id,
            RecoveryTrigger.NETWORK_RESTORED,
        )
        assertNotNull(foreground)
        assertNull(
            coordinator.requestSourceRecovery(
                movieGeneration.id,
                RecoveryTrigger.NETWORK_RESTORED,
            ),
        )
    }

    @Test
    fun `stale generation and invalidated coordinator ignore network restore`() {
        val coordinator = vodCoordinator(movieGeneration, moviePlan(sourceCount = 1))

        assertFalse(coordinator.onNetworkUnavailable(movieGeneration.id + 1))
        assertNull(
            coordinator.requestSourceRecovery(
                movieGeneration.id,
                RecoveryTrigger.NETWORK_RESTORED,
            ),
        )

        assertTrue(coordinator.onNetworkUnavailable(movieGeneration.id))
        coordinator.invalidate()

        assertFalse(coordinator.onNetworkUnavailable(movieGeneration.id))
        assertNull(
            coordinator.requestSourceRecovery(
                movieGeneration.id,
                RecoveryTrigger.NETWORK_RESTORED,
            ),
        )
    }

    @Test
    fun `network restore command is bound to its generation and player instance`() {
        val coordinator = vodCoordinator(movieGeneration, moviePlan(sourceCount = 1))
        assertTrue(coordinator.onNetworkUnavailable(movieGeneration.id))
        val command = checkNotNull(
            coordinator.requestSourceRecovery(
                movieGeneration.id,
                RecoveryTrigger.NETWORK_RESTORED,
            ),
        )
        val owner = RecoveryDispatchOwner(generationId = movieGeneration.id, playerInstanceId = 3)

        assertEquals(movieGeneration.id, command.generationId)
        assertTrue(ownsRecoveryCommand(command, owner, owner))
        assertFalse(
            ownsRecoveryCommand(
                command,
                owner,
                owner.copy(playerInstanceId = 4),
            ),
        )
        assertFalse(
            ownsRecoveryCommand(
                command,
                owner,
                owner.copy(generationId = movieGeneration.id + 1),
            ),
        )
    }

    @Test
    fun `live network restore uses the dedicated outage budget without consuming ordinary retries`() {
        val coordinator = createPlayerRecoveryCoordinator(
            generation = liveGeneration,
            sourcePlan = livePlan(),
            isLive = true,
        )

        assertNull(
            coordinator.requestSourceRecovery(
                liveGeneration.id,
                RecoveryTrigger.NETWORK_RESTORED,
            ),
        )

        assertTrue(coordinator.onNetworkUnavailable(liveGeneration.id))
        val restore = coordinator.requestSourceRecovery(
            liveGeneration.id,
            RecoveryTrigger.NETWORK_RESTORED,
        )
        assertEquals(RecoveryCommandType.RETRY_CURRENT_SOURCE, restore?.type)
        assertTrue(restore?.networkRestore == true)
        assertTrue(coordinator.markCommandApplied(liveGeneration.id, checkNotNull(restore).id))
        assertTrue(coordinator.beginCandidate(liveGeneration.id, 0, sourceChanged = false))

        val nextFailure = coordinator.requestSourceRecovery(
            liveGeneration.id,
            RecoveryTrigger.TRANSIENT_SOURCE_ERROR,
        )
        assertEquals(RecoveryCommandType.RETRY_CURRENT_SOURCE, nextFailure?.type)
        assertEquals(0, nextFailure?.candidateIndex)
    }

    @Test
    fun `ordinary VOD failure policy is unchanged without a confirmed outage`() {
        val singleSource = vodCoordinator(movieGeneration, moviePlan(sourceCount = 1))
        assertEquals(
            RecoveryCommandType.SHOW_FINAL_ERROR,
            singleSource.requestSourceRecovery(
                movieGeneration.id,
                RecoveryTrigger.TRANSIENT_SOURCE_ERROR,
            )?.type,
        )

        val fallbackGeneration = movieGeneration.copy(id = 904L)
        val multiSource = vodCoordinator(fallbackGeneration, moviePlan(sourceCount = 2))
        assertEquals(
            RecoveryCommandType.MOVE_TO_NEXT_SOURCE,
            multiSource.requestSourceRecovery(
                fallbackGeneration.id,
                RecoveryTrigger.TRANSIENT_SOURCE_ERROR,
            )?.type,
        )
    }

    private fun vodCoordinator(
        generation: PlaybackGeneration,
        plan: MediaSourcePlan,
    ): PlayerRecoveryCoordinator = createPlayerRecoveryCoordinator(
        generation = generation,
        sourcePlan = plan,
        isLive = false,
    )

    private fun moviePlan(sourceCount: Int): MediaSourcePlan = planMediaSources(
        PlaybackRequest(
            title = "F01 movie",
            posterUrl = null,
            candidates = (1..sourceCount).map { index ->
                "https://stream.example/movie/501-$index.mp4"
            },
            isLive = false,
            historyKey = "MOVIE:501",
            streamKind = "movie",
            streamId = 501,
            extension = "mp4",
        ),
    )

    private fun episodePlan(sourceCount: Int): MediaSourcePlan = planMediaSources(
        PlaybackRequest(
            title = "F01 episode",
            posterUrl = null,
            candidates = (1..sourceCount).map { index ->
                "https://stream.example/series/601-$index.mp4"
            },
            isLive = false,
            historyKey = "EPISODE:601:1:1",
            streamKind = "series",
            streamId = 601,
            extension = "mp4",
            seriesTitle = "F01 series",
            season = 1,
            episodeNumber = 1,
            episodeTitle = "F01 episode",
        ),
    )

    private fun livePlan(): MediaSourcePlan = planMediaSources(
        PlaybackRequest(
            title = "F01 live",
            posterUrl = null,
            candidates = listOf(
                "https://stream.example/live/701.ts",
                "https://stream.example/live/701.m3u8",
            ),
            isLive = true,
            historyKey = "LIVE:701",
            streamKind = "live",
            streamId = 701,
            extension = "ts",
        ),
    )
}
