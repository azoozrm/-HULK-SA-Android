package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.data.FootballDayFailure
import sa.hulksa.player.data.FootballFeedError
import sa.hulksa.player.data.FootballFeedSnapshot
import sa.hulksa.player.data.footballDayCandidateAccepted

class FootballCenterRetryPolicyTest {
    private val today = "2026-11-20"
    private val tomorrow = "2026-11-21"

    @Test
    fun `today respects an active shared retry deadline`() {
        assertEquals(5_000L, footballAutomaticRetryDelayMs(1_000L, 6_000L))
    }

    @Test
    fun `today backs off when a failure has no active deadline`() {
        assertEquals(60_000L, footballAutomaticRetryDelayMs(10_000L, 0L))
        assertEquals(60_000L, footballAutomaticRetryDelayMs(10_000L, 9_000L))
    }

    @Test
    fun `an adjacent plain failure backs off from when it occurred`() {
        val scheduler = FootballAdjacentDayScheduler(tomorrow)
        val input = FootballAdjacentDayInputs(
            todayKey = today,
            exactSnapshot = null,
            failure = FootballDayFailure(FootballFeedError.UNAVAILABLE, 1_000_000L),
            loading = false,
            retryAtEpochMs = 0L,
            discoveryRefreshSeconds = 30,
        )
        assertEquals(59_800L, scheduler.nextDelayMs(1_000_200L, input))
        assertEquals(1L, scheduler.nextDelayMs(1_059_999L, input))
        assertEquals(0L, scheduler.nextDelayMs(1_060_000L, input))
    }

    @Test
    fun `newer exact provider data wins at acceptance`() {
        val older = FootballFeedSnapshot(
            dayKey = tomorrow,
            generatedAtEpochMs = 900_000L,
            cachedAtEpochMs = 900_000L,
            stale = false,
            retryAfterSeconds = null,
            refreshSeconds = 60,
            matches = emptyList(),
            teams = emptyList(),
            fetchedAtEpochMs = 1_000_000L,
        )
        val newer = older.copy(
            generatedAtEpochMs = 1_050_000L,
            cachedAtEpochMs = 1_050_000L,
            fetchedAtEpochMs = 1_100_000L,
        )
        assertTrue(footballDayCandidateAccepted(older, newer, 1_100_000L))
    }
}
