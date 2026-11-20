package sa.hulksa.player.ui.screens

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.data.FootballDayFailure
import sa.hulksa.player.data.FootballFeedError
import sa.hulksa.player.data.FootballFeedSnapshot
import sa.hulksa.player.data.FootballMatch
import sa.hulksa.player.data.FootballCompetition
import sa.hulksa.player.data.FootballMatchStatus
import sa.hulksa.player.data.FootballTeam

class FootballPresentationR058Test {

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
        cachedAtEpochMs: Long,
        stale: Boolean,
        matches: List<FootballMatch> = emptyList(),
    ) = FootballFeedSnapshot(
        dayKey = day,
        generatedAtEpochMs = cachedAtEpochMs,
        cachedAtEpochMs = cachedAtEpochMs,
        stale = stale,
        retryAfterSeconds = null,
        refreshSeconds = 60,
        matches = matches,
        teams = emptyList(),
    )

    @Test
    fun `expired non-stale home snapshot cannot hide a newer stale day snapshot`() {
        val now = 10_000_000L
        val daySnapshot = snapshot(cachedAtEpochMs = now - 10_000L, stale = true)
        val homeSnapshot = snapshot(cachedAtEpochMs = now - 120_000L, stale = false)

        val chosen = footballCenterSnapshotForDay(daySnapshot, homeSnapshot, day, now)

        assertEquals(daySnapshot, chosen)
    }

    @Test
    fun `actually fresh home snapshot wins over a stale day snapshot`() {
        val now = 10_000_000L
        val daySnapshot = snapshot(cachedAtEpochMs = now - 5_000L, stale = true)
        val homeSnapshot = snapshot(cachedAtEpochMs = now - 10_000L, stale = false)

        val chosen = footballCenterSnapshotForDay(daySnapshot, homeSnapshot, day, now)

        assertEquals(homeSnapshot, chosen)
    }

    @Test
    fun `another day is never used`() {
        val now = 10_000_000L
        val other = snapshot(cachedAtEpochMs = now, stale = false).copy(dayKey = "2026-11-19")
        assertNull(footballCenterSnapshotForDay(other, other, day, now))
    }

    @Test
    fun `rate limited copy shows backoff and disables retry`() {
        val now = 2_000_000L
        val copy = footballCenterFailureCopy(
            FootballDayFailure(FootballFeedError.RATE_LIMITED, now, now + 30_000L),
            now,
        )
        assertTrue(copy.detail.contains("بعد 30 ثانية"))
        assertEquals(false, copy.retryEnabled)
    }

    @Test
    fun `unavailable copy allows retry`() {
        val copy = footballCenterFailureCopy(
            FootballDayFailure(FootballFeedError.UNAVAILABLE, 1L, 0L),
            1_000_000L,
        )
        assertTrue(copy.detail.contains("تعذر الوصول"))
        assertTrue(copy.retryEnabled)
    }

    @Test
    fun `stale filtered empty is not presented as a fresh no-results day`() {
        val stale = footballCenterEmptyCopy(
            snapshot(cachedAtEpochMs = 1L, stale = true, matches = listOf(match("e1"))),
            FootballCenterFilter.LIVE,
        )
        assertTrue(stale.title.contains("آخر بيانات محفوظة"))

        val fresh = footballCenterEmptyCopy(
            snapshot(cachedAtEpochMs = 1L, stale = false, matches = listOf(match("e1"))),
            FootballCenterFilter.LIVE,
        )
        assertTrue(fresh.title.contains("لا توجد مباريات مطابقة"))
        assertTrue(fresh.detail!!.contains("مباشر"))
    }

    @Test
    fun `stale empty day is a failed refresh not a valid empty fixture list`() {
        val copy = footballCenterEmptyCopy(
            snapshot(cachedAtEpochMs = 1L, stale = true),
            FootballCenterFilter.ALL,
        )
        assertTrue(copy.title.contains("تعذر تحديث"))
        assertTrue(copy.detail!!.contains("لا توجد بيانات محفوظة"))
    }

    @Test
    fun `focus scroll keeps a real margin only when the row crosses it`() {
        assertEquals(0, footballFocusScrollDelta(50, 80, 0, 200, 12))
        assertEquals(-12, footballFocusScrollDelta(0, 80, 0, 200, 12))
        assertEquals(12, footballFocusScrollDelta(120, 80, 0, 200, 12))
    }

    @Test
    fun `competition marks are measured TV-readable sizes`() {
        assertEquals(24.dp, footballCompetitionMarkSizeDp(FootballCompetitionMarkSurface.HOME, true))
        assertEquals(30.dp, footballCompetitionMarkSizeDp(FootballCompetitionMarkSurface.CENTER, true))
        assertEquals(32.dp, footballCompetitionMarkSizeDp(FootballCompetitionMarkSurface.DETAILS, true))
        assertTrue(
            footballCompetitionMarkSizeDp(FootballCompetitionMarkSurface.HOME, true) > 17.dp,
        )
    }

    @Test
    fun `broadcaster summary trims deduplicates and stays empty when unverified`() {
        assertEquals(
            "MBC Shahid • beIN SPORTS 1",
            footballBroadcasterSummary(listOf(" MBC Shahid ", "MBC Shahid", "beIN SPORTS 1")),
        )
        assertNull(footballBroadcasterSummary(emptyList()))
        assertNull(footballBroadcasterSummary(listOf(" ", "")))
    }
}
