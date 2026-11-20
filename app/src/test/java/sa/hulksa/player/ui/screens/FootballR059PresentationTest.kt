package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.data.FootballDayFailure
import sa.hulksa.player.data.FootballFeedError
import sa.hulksa.player.data.FootballFeedSnapshot
import sa.hulksa.player.data.FootballMatch
import sa.hulksa.player.data.FootballCompetition
import sa.hulksa.player.data.FootballMatchStatus
import sa.hulksa.player.data.FootballTeam
import sa.hulksa.player.data.footballDisplaySnapshotForDay
import sa.hulksa.player.data.footballEffectiveDayFailure

class FootballR059PresentationTest {

    private val day = "2026-11-20"

    private fun match(id: String) = FootballMatch(
        providerEventId = id,
        competition = FootballCompetition("17", "Saudi Pro League", arabicName = "دوري روشن السعودي"),
        homeTeam = FootballTeam("258", "Al-Ahli", arabicName = "الاهلي"),
        awayTeam = FootballTeam("267", "Al-Nassr", arabicName = "النصر"),
        kickoffEpochMs = 1_795_195_800_000L,
        status = FootballMatchStatus.UPCOMING,
    )

    private fun snapshot(
        cachedAt: Long,
        stale: Boolean,
        matches: List<FootballMatch> = emptyList(),
        dayKey: String = day,
    ) = FootballFeedSnapshot(
        dayKey = dayKey,
        generatedAtEpochMs = cachedAt,
        cachedAtEpochMs = cachedAt,
        stale = stale,
        retryAfterSeconds = null,
        refreshSeconds = 60,
        matches = matches,
        teams = emptyList(),
        fetchedAtEpochMs = cachedAt,
    )

    @Test
    fun `an expired sole empty day is last-good stale data not a valid empty day`() {
        val now = 10_000_000L
        val expired = snapshot(cachedAt = now - 61_000L, stale = false)
        val chosen = footballDisplaySnapshotForDay(mapOf(day to expired), emptyMap(), day, now)
        assertTrue(chosen!!.stale)
        val copy = footballCenterEmptyCopy(chosen, FootballCenterFilter.ALL)
        assertTrue(copy.title.contains("تعذر تحديث"))
    }

    @Test
    fun `an expired row set is still stale`() {
        val now = 10_000_000L
        val expiredRows = snapshot(cachedAt = now - 61_000L, stale = false, matches = listOf(match("e1")))
        val chosen = footballDisplaySnapshotForDay(mapOf(day to expiredRows), emptyMap(), day, now)
        assertTrue(chosen!!.stale)
        assertTrue(chosen.matches.isNotEmpty())
    }

    @Test
    fun `a real fresh empty response is a valid empty day`() {
        val now = 10_000_000L
        val freshEmpty = snapshot(cachedAt = now - 5_000L, stale = false)
        val chosen = footballDisplaySnapshotForDay(mapOf(day to freshEmpty), emptyMap(), day, now)
        assertFalse(chosen!!.stale)
        assertTrue(chosen.matches.isEmpty())
        assertEquals("لا توجد مباريات لهذا اليوم", footballCenterEmptyCopy(chosen, FootballCenterFilter.ALL).title)
    }

    @Test
    fun `focused key follows reorder and same-key return restarts`() {
        assertEquals(3, footballFocusedIndex("a", mapOf("a" to 3, "b" to 0)))
        assertEquals(1, footballFocusedIndex("a", mapOf("a" to 1, "b" to 0)))
        assertEquals(null, footballFocusedIndex(null, mapOf("a" to 1)))
        assertEquals(2, footballFocusedIndex("a", mapOf("a" to 2)))
    }

    @Test
    fun `a current failure owns the day over accepted fresh empty`() {
        val now = 10_000_000L
        val freshEmpty = snapshot(cachedAt = now - 5_000L, stale = false)
        val failure = FootballDayFailure(
            error = FootballFeedError.RATE_LIMITED,
            attemptedAtEpochMs = now,
            retryAtEpochMs = now + 30_000L,
            startedAtEpochMs = now - 1_000L,
        )

        val chosen = footballDisplaySnapshotForDay(
            mapOf(day to freshEmpty),
            mapOf(day to failure),
            day,
            now,
        )

        assertTrue(chosen!!.stale)
        val copy = footballCenterEmptyCopy(chosen, FootballCenterFilter.ALL)
        assertTrue(copy.title.contains("تعذر تحديث"))
    }

    @Test
    fun `accepted day rows survive a current failure as stale last-good`() {
        val now = 10_000_000L
        val dayRows = snapshot(cachedAt = now - 5_000L, stale = false, matches = listOf(match("e1")))
        val failure = FootballDayFailure(
            error = FootballFeedError.UNAVAILABLE,
            attemptedAtEpochMs = now,
            startedAtEpochMs = now - 1_000L,
        )

        val chosen = footballDisplaySnapshotForDay(
            mapOf(day to dayRows),
            mapOf(day to failure),
            day,
            now,
        )

        assertTrue(chosen!!.stale)
        assertEquals(1, chosen.matches.size)
    }


    @Test
    fun `no baseline failure yields unavailable not a definitive empty day`() {
        val now = 10_000_000L
        val failure = FootballDayFailure(
            error = FootballFeedError.RATE_LIMITED,
            attemptedAtEpochMs = now,
            retryAtEpochMs = now + 30_000L,
            startedAtEpochMs = now - 1_000L,
        )
        val failures = mapOf(day to failure)
        val snapshot = footballDisplaySnapshotForDay(
            emptyMap(),
            failures,
            day,
            now,
            allowOtherDayFallback = true,
        )
        assertEquals(null, snapshot)
        assertEquals(failure, footballEffectiveDayFailure(emptyMap(), failures, day))
    }


    @Test
    fun `Center exact-day selection never borrows today for tomorrow`() {
        val todayRows = snapshot(cachedAt = 1_000L, stale = false, matches = listOf(match("today")))
        val tomorrow = "2026-11-21"
        val exactOnly = footballDisplaySnapshotForDay(
            mapOf(day to todayRows),
            emptyMap(),
            tomorrow,
            1_000L,
        )
        assertEquals(null, exactOnly)
        val homeFallback = footballDisplaySnapshotForDay(
            mapOf(day to todayRows),
            emptyMap(),
            tomorrow,
            1_000L,
            allowOtherDayFallback = true,
        )
        assertTrue(homeFallback!!.stale)
        assertEquals(day, homeFallback.dayKey)
    }

    @Test
    fun `retry copy disables before the real deadline and re-enables after it`() {
        val deadline = 1_040_000L
        val failure = FootballDayFailure(FootballFeedError.RATE_LIMITED, 1_010_000L, deadline)

        val inside = footballCenterFailureCopy(failure, 1_010_000L)
        assertFalse(inside.retryEnabled)
        assertTrue(inside.detail.contains("بعد"))

        val expired = footballCenterFailureCopy(failure, 1_041_000L)
        assertTrue(expired.retryEnabled)
    }

    @Test
    fun `screen clock ticks every second for a deadline and every 15s otherwise`() {
        assertEquals(1_000L, footballRetryTickDelayMs(1_000L, 41_000L))
        assertEquals(500L, footballRetryTickDelayMs(40_500L, 41_000L))
        assertEquals(0L, footballRetryTickDelayMs(41_000L, 41_000L))
        assertEquals(15_000L, footballCenterClockTickMs(41_000L, 0L))
        assertEquals(1_000L, footballCenterClockTickMs(1_000L, 41_000L))
    }

    @Test
    fun `same screen clock can remain open while the deadline elapses`() {
        val deadline = 5_000L
        assertFalse(footballCenterFailureCopy(
            FootballDayFailure(FootballFeedError.RATE_LIMITED, 1_000L, deadline),
            4_999L,
        ).retryEnabled)
        assertTrue(footballCenterFailureCopy(
            FootballDayFailure(FootballFeedError.RATE_LIMITED, 1_000L, deadline),
            5_000L,
        ).retryEnabled)
    }

    @Test
    fun `another day is disclosed as a stale fallback only when the requested day is absent`() {
        val yesterday = "2026-11-19"
        val fallback = snapshot(cachedAt = 1_000L, stale = false, dayKey = yesterday)

        val onlyFallback = footballDisplaySnapshotForDay(
            mapOf(yesterday to fallback),
            emptyMap(),
            day,
            1_000L,
            allowOtherDayFallback = true,
        )
        assertTrue(onlyFallback!!.stale)

        val exactToday = snapshot(cachedAt = 1_000L, stale = false)
        val exact = footballDisplaySnapshotForDay(
            mapOf(day to exactToday, yesterday to fallback),
            emptyMap(),
            day,
            1_000L,
        )
        assertEquals(day, exact!!.dayKey)
        assertFalse(exact.stale)
    }
}
