package sa.hulksa.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.PlaybackRequest
import sa.hulksa.player.ui.screens.NetworkAvailabilityState
import sa.hulksa.player.ui.screens.NetworkCallbackKind
import sa.hulksa.player.ui.screens.NetworkCallbackSignal
import sa.hulksa.player.ui.screens.reduceNetworkAvailability
import sa.hulksa.player.ui.screens.shouldEnterOfflineForTerminalError
import sa.hulksa.player.ui.screens.playerRetrySeekTarget

/**
 * F01 correction 02: the owner-observed generic terminal error must become the offline state when
 * callback-derived connectivity is unusable, and all three modes must recover once after the
 * confirmed outage clears.
 */
class F01Correction02RecoveryTest {
    private val movieGeneration = PlaybackGeneration(921L, "movie", 521, 1_521)
    private val episodeGeneration = PlaybackGeneration(922L, "series", 621, 1_621)
    private val liveGeneration = PlaybackGeneration(923L, "live", 721, 1_721)

    @Test
    fun `transport failure before the old snapshot enters offline for a terminal source error`() {
        val initial = NetworkAvailabilityState(available = true, networkToken = 7)
        val unvalidated = reduceNetworkAvailability(
            initial,
            NetworkCallbackSignal(
                kind = NetworkCallbackKind.CAPABILITIES,
                networkToken = 7,
                hasInternet = true,
                validated = false,
            ),
        )

        assertFalse(unvalidated.available)
        assertTrue(
            shouldEnterOfflineForTerminalError(
                failureClass = RecoveryFailureClass.SOURCE,
                localPlayback = false,
                networkAvailable = unvalidated.available,
            ),
        )
        assertFalse(
            shouldEnterOfflineForTerminalError(
                failureClass = RecoveryFailureClass.AUDIO,
                localPlayback = false,
                networkAvailable = unvalidated.available,
            ),
        )
        assertFalse(
            shouldEnterOfflineForTerminalError(
                failureClass = RecoveryFailureClass.SOURCE,
                localPlayback = true,
                networkAvailable = unvalidated.available,
            ),
        )
        assertFalse(
            shouldEnterOfflineForTerminalError(
                failureClass = RecoveryFailureClass.SOURCE,
                localPlayback = false,
                networkAvailable = true,
            ),
        )

        val restored = reduceNetworkAvailability(
            unvalidated,
            NetworkCallbackSignal(
                kind = NetworkCallbackKind.CAPABILITIES,
                networkToken = 7,
                hasInternet = true,
                validated = true,
            ),
        )
        assertTrue(restored.available)
    }

    @Test
    fun `live terminal error recovers after the network returns`() {
        val coordinator = createPlayerRecoveryCoordinator(
            generation = liveGeneration,
            sourcePlan = livePlan(sourceCount = 1),
            isLive = true,
        )

        val first = checkNotNull(
            coordinator.requestSourceRecovery(
                liveGeneration.id,
                RecoveryTrigger.TRANSIENT_SOURCE_ERROR,
            ),
        )
        assertEquals(RecoveryCommandType.RETRY_CURRENT_SOURCE, first.type)
        assertTrue(coordinator.markCommandApplied(liveGeneration.id, first.id))
        assertTrue(coordinator.beginCandidate(liveGeneration.id, 0, sourceChanged = false))

        val terminal = checkNotNull(
            coordinator.requestSourceRecovery(
                liveGeneration.id,
                RecoveryTrigger.TRANSIENT_SOURCE_ERROR,
            ),
        )
        assertEquals(RecoveryCommandType.SHOW_FINAL_ERROR, terminal.type)
        assertEquals(RecoveryPhase.EXHAUSTED, coordinator.phase)

        assertTrue(coordinator.onNetworkUnavailable(liveGeneration.id))
        val restore = coordinator.requestSourceRecovery(
            liveGeneration.id,
            RecoveryTrigger.NETWORK_RESTORED,
        )
        assertEquals(RecoveryCommandType.RETRY_CURRENT_SOURCE, restore?.type)
        assertTrue(restore?.networkRestore == true)
    }

    @Test
    fun `live dedicated restore does not consume the ordinary retry budget`() {
        val coordinator = createPlayerRecoveryCoordinator(
            generation = liveGeneration,
            sourcePlan = livePlan(sourceCount = 2),
            isLive = true,
        )

        assertTrue(coordinator.onNetworkUnavailable(liveGeneration.id))
        val restore = checkNotNull(
            coordinator.requestSourceRecovery(
                liveGeneration.id,
                RecoveryTrigger.NETWORK_RESTORED,
            ),
        )
        assertEquals(RecoveryCommandType.RETRY_CURRENT_SOURCE, restore.type)
        assertTrue(restore.networkRestore)
        assertTrue(coordinator.markCommandApplied(liveGeneration.id, restore.id))
        assertTrue(coordinator.beginCandidate(liveGeneration.id, 0, sourceChanged = false))

        val ordinary = coordinator.requestSourceRecovery(
            liveGeneration.id,
            RecoveryTrigger.TRANSIENT_SOURCE_ERROR,
        )
        assertEquals(RecoveryCommandType.RETRY_CURRENT_SOURCE, ordinary?.type)
        assertEquals(0, ordinary?.candidateIndex)
    }

    @Test
    fun `movie and episode terminal errors recover after the network returns`() {
        listOf(
            movieGeneration to moviePlan(sourceCount = 1),
            episodeGeneration to episodePlan(sourceCount = 1),
        ).forEach { (generation, plan) ->
            val coordinator = createPlayerRecoveryCoordinator(
                generation = generation,
                sourcePlan = plan,
                isLive = false,
            )
            val terminal = checkNotNull(
                coordinator.requestSourceRecovery(
                    generation.id,
                    RecoveryTrigger.TRANSIENT_SOURCE_ERROR,
                ),
            )
            assertEquals(RecoveryCommandType.SHOW_FINAL_ERROR, terminal.type)
            assertEquals(RecoveryPhase.EXHAUSTED, coordinator.phase)

            assertTrue(coordinator.onNetworkUnavailable(generation.id))
            val restore = coordinator.requestSourceRecovery(
                generation.id,
                RecoveryTrigger.NETWORK_RESTORED,
            )
            assertEquals(RecoveryCommandType.RETRY_CURRENT_SOURCE, restore?.type)
            assertTrue(restore?.networkRestore == true)
        }
    }

    @Test
    fun `manual retry clears the pending automatic restore arm`() {
        val coordinator = createPlayerRecoveryCoordinator(
            generation = movieGeneration,
            sourcePlan = moviePlan(sourceCount = 1),
            isLive = false,
        )
        assertTrue(coordinator.onNetworkUnavailable(movieGeneration.id))
        assertTrue(coordinator.resetForManualRetry(movieGeneration.id, 0))
        assertNull(
            coordinator.requestSourceRecovery(
                movieGeneration.id,
                RecoveryTrigger.NETWORK_RESTORED,
            ),
        )
    }

    @Test
    fun `live uses live edge while vod keeps its preserved position`() {
        assertEquals(
            0L,
            playerRetrySeekTarget(
                isLive = true,
                resumePromptPending = false,
                preservedPositionMs = 1_234_000L,
                playerPositionMs = 1_234_000L,
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

    private fun moviePlan(sourceCount: Int): MediaSourcePlan = planMediaSources(
        PlaybackRequest(
            title = "F01 correction movie",
            posterUrl = null,
            candidates = (1..sourceCount).map { "https://stream.example/movie/521-$it.mp4" },
            isLive = false,
            historyKey = "MOVIE:521",
            streamKind = "movie",
            streamId = 521,
            extension = "mp4",
        ),
    )

    private fun episodePlan(sourceCount: Int): MediaSourcePlan = planMediaSources(
        PlaybackRequest(
            title = "F01 correction episode",
            posterUrl = null,
            candidates = (1..sourceCount).map { "https://stream.example/series/621-$it.mp4" },
            isLive = false,
            historyKey = "EPISODE:621:1:1",
            streamKind = "series",
            streamId = 621,
            extension = "mp4",
            seriesTitle = "F01 correction series",
            season = 1,
            episodeNumber = 1,
            episodeTitle = "F01 correction episode",
        ),
    )

    private fun livePlan(sourceCount: Int): MediaSourcePlan = planMediaSources(
        PlaybackRequest(
            title = "F01 correction live",
            posterUrl = null,
            candidates = (1..sourceCount).map { "https://stream.example/live/721-$it.ts" },
            isLive = true,
            historyKey = "LIVE:721",
            streamKind = "live",
            streamId = 721,
            extension = "ts",
        ),
    )
}
