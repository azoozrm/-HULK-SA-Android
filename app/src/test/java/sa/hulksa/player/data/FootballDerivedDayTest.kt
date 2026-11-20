package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FootballDerivedDayTest {

    private val today = "2026-11-20"
    private val yesterday = "2026-11-19"
    private val tomorrow = "2026-11-21"

    private fun kickoff(day: String, hour: Int = 20): Long =
        (footballRiyadhDayStartEpochMs(day) ?: error("day")) + hour * 60L * 60L * 1_000L

    private fun match(id: String, day: String, hour: Int = 20) = FootballMatch(
        providerEventId = id,
        competition = FootballCompetition("17", "Saudi Pro League", priority = 10),
        homeTeam = FootballTeam("258", "Al-Ahli"),
        awayTeam = FootballTeam("267", "Al-Nassr"),
        kickoffEpochMs = kickoff(day, hour),
        status = FootballMatchStatus.UPCOMING,
    )

    private fun source(
        matches: List<FootballMatch> = emptyList(),
        history: List<FootballMatch> = emptyList(),
        reminders: List<FootballMatch> = emptyList(),
    ) = FootballFeedSnapshot(
        dayKey = today,
        generatedAtEpochMs = 1_795_195_800_000L,
        cachedAtEpochMs = 1_795_195_800_000L,
        stale = false,
        retryAfterSeconds = null,
        refreshSeconds = 60,
        matches = matches,
        teams = emptyList(),
        kickoffHistory = history,
        reminderCandidates = reminders,
        fetchedAtEpochMs = 1_795_195_800_000L,
    )

    @Test
    fun `tomorrow reminder rows project only into tomorrow`() {
        val projected = footballDerivedDaySnapshot(
            source = source(
                matches = listOf(match("today", today)),
                reminders = listOf(match("tomorrow", tomorrow)),
                history = listOf(match("yesterday", yesterday)),
            ),
            dayKey = tomorrow,
            favoriteTeamIds = emptySet(),
        )
        assertEquals(listOf("tomorrow"), projected?.matches?.map(FootballMatch::providerEventId))
        assertEquals(tomorrow, projected?.dayKey)
    }

    @Test
    fun `yesterday history rows project only into yesterday`() {
        val projected = footballDerivedDaySnapshot(
            source = source(
                matches = listOf(match("today", today)),
                reminders = listOf(match("tomorrow", tomorrow)),
                history = listOf(match("yesterday", yesterday)),
            ),
            dayKey = yesterday,
            favoriteTeamIds = emptySet(),
        )
        assertEquals(listOf("yesterday"), projected?.matches?.map(FootballMatch::providerEventId))
        assertEquals(yesterday, projected?.dayKey)
    }

    @Test
    fun `a day with no matching row is not a definitive empty day`() {
        val projected = footballDerivedDaySnapshot(
            source = source(matches = listOf(match("today", today))),
            dayKey = tomorrow,
            favoriteTeamIds = emptySet(),
        )
        assertNull(projected)
    }

    @Test
    fun `same-day source is handled by the exact record not the projection`() {
        val projected = footballDerivedDaySnapshot(
            source = source(matches = listOf(match("today", today))),
            dayKey = today,
            favoriteTeamIds = emptySet(),
        )
        assertNull(projected)
    }

    @Test
    fun `projection is provisional and preserves accepted content`() {
        val projected = footballDerivedDaySnapshot(
            source = source(history = listOf(match("yesterday", yesterday))),
            dayKey = yesterday,
            favoriteTeamIds = setOf("258"),
        )
        assertTrue(projected!!.stale)
        assertFalse(projected.matches.isEmpty())
        assertEquals("yesterday", projected.matches.single().providerEventId)
        assertEquals(1_795_195_800_000L, projected.fetchedAtEpochMs)
    }
}
