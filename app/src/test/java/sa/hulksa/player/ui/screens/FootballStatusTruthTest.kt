package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.data.FootballCompetition
import sa.hulksa.player.data.FootballMatch
import sa.hulksa.player.data.FootballMatchStatus
import sa.hulksa.player.data.FootballTeam

class FootballStatusTruthTest {

    private val now = 1_000_000L

    private fun match(
        status: FootballMatchStatus,
        kickoff: Long = now + 60_000L,
        home: Int? = null,
        away: Int? = null,
    ) = FootballMatch(
        providerEventId = "1",
        competition = FootballCompetition("17", "League"),
        homeTeam = FootballTeam("h", "Home"),
        awayTeam = FootballTeam("a", "Away"),
        kickoffEpochMs = kickoff,
        status = status,
        homeScore = home,
        awayScore = away,
    )

    @Test
    fun `future upcoming remains not started`() {
        assertEquals(
            "لم تبدا",
            footballStatusText(match(FootballMatchStatus.UPCOMING), false, now, stale = false),
        )
    }

    @Test
    fun `elapsed scheduled item never claims not started or confirmed`() {
        val elapsed = match(FootballMatchStatus.UPCOMING, kickoff = now - 60_000L)
        assertEquals(
            "الموعد مضى • بانتظار التحديث",
            footballStatusText(elapsed, false, now, stale = false),
        )
        assertEquals(
            "الموعد مضى • بيانات قديمة",
            footballStatusText(elapsed, false, now, stale = true),
        )
        assertEquals(
            "الموعد مضى • بانتظار التحديث",
            footballDetailStatusText(elapsed, now, stale = false),
        )
        assertTrue(
            !footballDetailStatusText(elapsed, now, stale = false).contains("مؤكدة"),
        )
    }

    @Test
    fun `finished and live rows expose missing scores explicitly`() {
        assertEquals(
            "انتهت • النتيجة غير متوفرة",
            footballStatusText(match(FootballMatchStatus.FINISHED), false, now, stale = false),
        )
        assertTrue(
            footballStatusText(
                match(FootballMatchStatus.LIVE, home = 1, away = null),
                false,
                now,
                stale = false,
            ).contains("النتيجة غير متوفرة"),
        )
        assertEquals(
            "انتهت • النتيجة مخفية",
            footballStatusText(match(FootballMatchStatus.FINISHED), true, now, stale = false),
        )
    }
}
