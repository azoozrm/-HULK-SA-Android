package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FootballHomeTodayTest {

    private val today = "2026-11-20"
    private val yesterday = "2026-11-19"
    private val tomorrow = "2026-11-21"

    private fun kickoff(day: String, hour: Int = 20): Long =
        (footballRiyadhDayStartEpochMs(day) ?: error("day")) + hour * 60L * 60L * 1_000L

    private fun match(
        id: String,
        day: String,
        hour: Int = 20,
        carried: Boolean = false,
        scores: Pair<Int?, Int?>? = null,
    ) = FootballMatch(
        providerEventId = id,
        competition = FootballCompetition("17", "Saudi Pro League", priority = 10),
        homeTeam = FootballTeam("h$id", "Home"),
        awayTeam = FootballTeam("a$id", "Away"),
        kickoffEpochMs = kickoff(day, hour),
        status = FootballMatchStatus.UPCOMING,
        homeScore = scores?.first,
        awayScore = scores?.second,
        carriedOver = carried,
    )

    private fun snapshot(day: String, matches: List<FootballMatch>) = FootballFeedSnapshot(
        dayKey = day,
        generatedAtEpochMs = 1_000L,
        cachedAtEpochMs = 1_000L,
        stale = false,
        retryAfterSeconds = null,
        refreshSeconds = 60,
        matches = matches,
        teams = emptyList(),
        fetchedAtEpochMs = 1_000L,
    )

    private fun now(day: String, hour: Int = 12): Long = kickoff(day, hour)

    @Test
    fun `previous-day-only cache produces no Home row`() {
        val days = mapOf(yesterday to snapshot(yesterday, listOf(match("y", yesterday))))
        assertTrue(footballHomeTodayMatches(days, now(today)).isEmpty())
    }

    @Test
    fun `mixed days and carryovers keep only exact today rows`() {
        val days = mapOf(
            today to snapshot(
                today,
                listOf(
                    match("today", today),
                    match("yesterday", yesterday),
                    match("carried", yesterday, carried = true),
                    match("tomorrow", tomorrow),
                ),
            ),
        )
        assertEquals(
            listOf("today"),
            footballHomeTodayMatches(days, now(today)).map(FootballMatch::providerEventId),
        )
    }

    @Test
    fun `exact empty or missing record hides the Home row`() {
        assertTrue(footballHomeTodayMatches(emptyMap(), now(today)).isEmpty())
        assertTrue(
            footballHomeTodayMatches(
                mapOf(today to snapshot(today, emptyList())),
                now(today),
            ).isEmpty(),
        )
    }

    @Test
    fun `complete same-day rows survive and favorites lead`() {
        val rows = listOf(
            match("a", today, hour = 20),
            match("b", today, hour = 18),
        )
        val ordered = footballHomeTodayMatches(
            mapOf(today to snapshot(today, rows)),
            now(today),
            favoriteTeamIds = setOf("hb"),
        )
        assertEquals(listOf("b", "a"), ordered.map(FootballMatch::providerEventId))
    }

    @Test
    fun `midnight rollover switches the exact Home day`() {
        val days = mapOf(
            today to snapshot(today, listOf(match("late", today, hour = 23))),
            tomorrow to snapshot(tomorrow, listOf(match("early", tomorrow, hour = 1))),
        )
        assertEquals(
            listOf("late"),
            footballHomeTodayMatches(days, now(today, hour = 23)).map(FootballMatch::providerEventId),
        )
        assertEquals(
            listOf("early"),
            footballHomeTodayMatches(days, now(tomorrow, hour = 1)).map(FootballMatch::providerEventId),
        )
    }

    @Test
    fun `missing scores do not remove a legitimate same-day row`() {
        val days = mapOf(today to snapshot(today, listOf(match("no-score", today))))
        assertEquals(
            listOf("no-score"),
            footballHomeTodayMatches(days, now(today)).map(FootballMatch::providerEventId),
        )
    }
}
