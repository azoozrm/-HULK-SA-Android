package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FootballFreshnessR058Test {

    private fun snapshot(
        cachedAtEpochMs: Long,
        stale: Boolean,
        refreshSeconds: Int = 60,
    ) = FootballFeedSnapshot(
        dayKey = "2026-11-20",
        generatedAtEpochMs = cachedAtEpochMs,
        cachedAtEpochMs = cachedAtEpochMs,
        stale = stale,
        retryAfterSeconds = null,
        refreshSeconds = refreshSeconds,
        matches = emptyList(),
        teams = emptyList(),
    )

    @Test
    fun `expired cache is displayed as last-good stale data`() {
        val now = 10_000_000L
        val expired = snapshot(cachedAtEpochMs = now - 61_000L, stale = false)
        val display = footballSnapshotForDisplay(expired, now, 60)
        assertTrue(display.stale)
        assertEquals(expired.cachedAtEpochMs, display.cachedAtEpochMs)
    }

    @Test
    fun `fresh and already stale caches keep their identity`() {
        val now = 10_000_000L
        val fresh = snapshot(cachedAtEpochMs = now - 30_000L, stale = false)
        val stale = snapshot(cachedAtEpochMs = now - 30_000L, stale = true)
        assertFalse(footballSnapshotForDisplay(fresh, now, 60).stale)
        assertTrue(footballSnapshotForDisplay(stale, now, 60).stale)
    }

    @Test
    fun `stale 200 Retry-After starts the shared cooldown`() {
        val attemptedAt = 1_000_000L
        assertEquals(
            attemptedAt + 90_000L,
            footballRetryAtAfterSuccess(attemptedAt, 90L, 0L),
        )
    }

    @Test
    fun `a later fresh 200 cannot erase an active cooldown`() {
        assertEquals(
            1_090_000L,
            footballRetryAtAfterSuccess(1_030_000L, null, 1_090_000L),
        )
        assertEquals(
            1_090_000L,
            footballRetryAtAfterSuccess(1_030_000L, 10L, 1_090_000L),
        )
    }

    @Test
    fun `an elapsed cooldown is cleared by the next success`() {
        assertEquals(0L, footballRetryAtAfterSuccess(1_100_000L, null, 1_090_000L))
    }

    @Test
    fun `a longer Retry-After extends the active deadline`() {
        assertEquals(
            1_030_000L + 120_000L,
            footballRetryAtAfterSuccess(1_030_000L, 120L, 1_090_000L),
        )
    }

    @Test
    fun `failure cannot shorten the active deadline and can extend it`() {
        assertEquals(
            1_090_000L,
            footballRetryAtAfterFailure(1_030_000L, 10L, 1_090_000L),
        )
        assertEquals(
            1_150_000L,
            footballRetryAtAfterFailure(1_030_000L, 120L, 1_090_000L),
        )
    }

    @Test
    fun `Home publication preserves another day failure`() {
        val tomorrowFailure = FootballDayFailure(
            error = FootballFeedError.UNAVAILABLE,
            attemptedAtEpochMs = 1_000L,
            retryAtEpochMs = 0L,
        )
        val previous = FootballFeedUiState(
            dayFailures = mapOf("2026-11-21" to tomorrowFailure),
        )
        val published = FootballFeedUiState(
            available = true,
            snapshot = snapshot(cachedAtEpochMs = 2_000L, stale = false),
        )

        val merged = footballPublishedFeedState(previous, published)

        assertEquals(previous.dayFailures, merged.dayFailures)
        assertEquals(published.snapshot, merged.snapshot)
    }
}
