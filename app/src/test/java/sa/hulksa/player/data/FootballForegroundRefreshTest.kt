package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FootballForegroundRefreshTest {
    private val now = 1_795_040_000_000L

    @Test
    fun `background missing owner disabled feed or Kids wait for a real wake`() {
        val wait = FootballForegroundRefreshDecision.WaitForWake
        assertEquals(
            wait,
            decide(false, true, true, true, false, false, 60, now, 0L, 0L),
        )
        assertEquals(
            wait,
            decide(true, false, true, true, false, false, 60, now, 0L, 0L),
        )
        assertEquals(
            wait,
            decide(true, true, false, true, false, false, 60, now, 0L, 0L),
        )
        assertEquals(
            wait,
            decide(true, true, true, false, false, false, 60, now, 0L, 0L),
        )
        assertEquals(
            wait,
            decide(true, true, true, true, true, false, 60, now, 0L, 0L),
        )
    }

    @Test
    fun `server TTL produces the remaining foreground delay then a refresh`() {
        val lastAttempt = now - 30_000L
        assertEquals(
            FootballForegroundRefreshDecision.WaitForDelay(30_000L),
            decide(true, true, true, true, false, false, 60, now, lastAttempt, 0L),
        )
        assertEquals(
            FootballForegroundRefreshDecision.RefreshNow,
            decide(true, true, true, true, false, false, 60, now, now - 70_000L, 0L),
        )
    }

    @Test
    fun `Retry-After overrides the normal TTL`() {
        assertEquals(
            FootballForegroundRefreshDecision.WaitForDelay(120_000L),
            decide(
                true,
                true,
                true,
                true,
                false,
                false,
                60,
                now,
                now - 70_000L,
                now + 120_000L,
            ),
        )
    }

    @Test
    fun `an active request waits briefly instead of overlapping`() {
        assertEquals(
            FootballForegroundRefreshDecision.WaitForDelay(1_000L),
            decide(true, true, true, true, false, true, 60, now, now - 70_000L, 0L),
        )
    }

    @Test
    fun `refresh seconds are clamped to the bounded server contract`() {
        assertEquals(
            FootballForegroundRefreshDecision.WaitForDelay(30_000L),
            decide(true, true, true, true, false, false, 1, now, now, 0L),
        )
        assertEquals(
            FootballForegroundRefreshDecision.WaitForDelay(300_000L),
            decide(true, true, true, true, false, false, 900, now, now, 0L),
        )
    }

    private fun decide(
        foreground: Boolean,
        enabled: Boolean,
        authenticated: Boolean,
        profileReady: Boolean,
        kids: Boolean,
        active: Boolean,
        refreshSeconds: Int,
        nowMs: Long,
        lastAttemptMs: Long,
        retryAfterMs: Long,
    ): FootballForegroundRefreshDecision = decideFootballForegroundRefresh(
        foreground = foreground,
        enabled = enabled,
        authenticated = authenticated,
        profileReady = profileReady,
        kidsProfile = kids,
        requestActive = active,
        refreshSeconds = refreshSeconds,
        nowEpochMs = nowMs,
        lastAttemptEpochMs = lastAttemptMs,
        retryAfterEpochMs = retryAfterMs,
    )
}
