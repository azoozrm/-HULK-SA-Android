package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FootballFeedPolicyTest {
    @Test
    fun `Riyadh day uses the fixed gulf calendar day`() {
        val beforeMidnightUtc = 1_795_038_600_000L // 2026-11-18T21:50:00Z = 2026-11-19 00:50 Riyadh
        val afterMidnightRiyadh = 1_795_036_200_000L // 2026-11-18T21:10:00Z = 2026-11-19 00:10 Riyadh

        assertEquals("2026-11-19", footballRiyadhDayKey(beforeMidnightUtc))
        assertEquals("2026-11-19", footballRiyadhDayKey(afterMidnightRiyadh))
        assertEquals(
            1_795_035_600_000L,
            footballRiyadhDayStartEpochMs("2026-11-19"),
        )
        assertEquals("00:50", footballRiyadhTimeLabel(1_795_038_600_000L))
        assertEquals("اليوم", footballRiyadhDayLabel("2026-11-19", afterMidnightRiyadh))
    }

    @Test
    fun `replaced_by keeps one canonical row and preserves the old identity as alias`() {
        val postponed = match(
            id = "101",
            kickoff = 1_795_020_000_000L,
            status = FootballMatchStatus.POSTPONED,
            replacedBy = "202",
        )
        val replacement = match(id = "202", kickoff = 1_795_100_000_000L, status = FootballMatchStatus.UPCOMING)

        val reconciled = reconcileFootballMatches(listOf(postponed, replacement))

        assertEquals(1, reconciled.size)
        assertEquals("202", reconciled.single().providerEventId)
        assertTrue("101" in reconciled.single().eventIds)
    }

    @Test
    fun `a postponed row whose replacement is outside the window is not shown`() {
        val orphan = match(
            id = "303",
            kickoff = 1_795_020_000_000L,
            status = FootballMatchStatus.POSTPONED,
            replacedBy = "404",
        )

        assertTrue(reconcileFootballMatches(listOf(orphan)).isEmpty())
    }

    @Test
    fun `live matches carry over midnight and finished results keep a bounded window`() {
        val day = "2026-11-19"
        val dayStart = checkNotNull(footballRiyadhDayStartEpochMs(day))
        val live = match(id = "1", kickoff = dayStart - 60_000L, status = FootballMatchStatus.LIVE)
        val finished = match(id = "2", kickoff = dayStart - 30_000L, status = FootballMatchStatus.FINISHED)
        val nextDay = match(id = "4", kickoff = dayStart + 60_000L, status = FootballMatchStatus.UPCOMING)

        val selected = footballMatchesForDay(listOf(live, finished, nextDay), day)

        assertEquals(setOf("1", "4"), selected.mapTo(linkedSetOf(), FootballMatch::providerEventId))
        assertTrue(selected.first { it.providerEventId == "1" }.carriedOver)
        assertFalse(selected.first { it.providerEventId == "4" }.carriedOver)
    }

    @Test
    fun `nullable scores stay null and confirmed scores stay exact`() {
        val pending = match(id = "1", kickoff = 1L, status = FootballMatchStatus.LIVE)
        val scored = match(
            id = "2",
            kickoff = 1L,
            status = FootballMatchStatus.FINISHED,
            homeScore = 2,
            awayScore = 1,
        )

        assertNull(pending.homeScore)
        assertNull(pending.awayScore)
        assertEquals(2, scored.homeScore)
        assertEquals(1, scored.awayScore)
        assertEquals(2, scored.scoreFor("home"))
        assertEquals(1, scored.scoreFor("away"))
    }

    @Test
    fun `favorite team fixtures lead the rail without hiding other matches`() {
        val favourite = match(id = "1", kickoff = 30L, status = FootballMatchStatus.UPCOMING)
        val liveOther = match(
            id = "2",
            kickoff = 20L,
            status = FootballMatchStatus.LIVE,
            homeId = "other-home",
            awayId = "other-away",
        )

        val ordered = orderFootballMatches(listOf(liveOther, favourite), "home")

        assertEquals(listOf("1", "2"), ordered.map(FootballMatch::providerEventId))
    }

    @Test
    fun `team search normalizes Arabic alef and keeps one stable candidate per id`() {
        val teams = listOf(
            FootballTeam("1", "الأهلي"),
            FootballTeam("2", "الهلال"),
            FootballTeam("1", "الأهلي"),
        ).distinctBy(FootballTeam::id)

        assertEquals(
            listOf("1"),
            filterFootballTeams(teams, "الاهلي").map(FootballTeam::id),
        )
        assertEquals(2, filterFootballTeams(teams, "").size)
        assertEquals(emptyList<FootballTeam>(), filterFootballTeams(teams, "النصر"))
    }

    @Test
    fun `schedule and live rows merge by provider id and keep fixture metadata`() {
        val schedule = match(
            id = "202",
            kickoff = 1_795_100_000_000L,
            status = FootballMatchStatus.UPCOMING,
            homeId = "266",
            awayId = "271",
        )
        val live = match(
            id = "202",
            kickoff = 1_795_200_000_000L,
            status = FootballMatchStatus.LIVE,
            homeId = "266",
            awayId = "271",
            homeScore = 1,
            awayScore = 0,
            minute = 31,
        )

        val merged = mergeFootballMatchRows(listOf(schedule, live))

        assertEquals(1, merged.size)
        assertEquals(FootballMatchStatus.LIVE, merged.single().status)
        assertEquals(31, merged.single().minute)
        assertEquals(1, merged.single().homeScore)
        assertEquals(0, merged.single().awayScore)
        assertEquals(schedule.kickoffEpochMs, merged.single().kickoffEpochMs)
        assertEquals(schedule.competition, merged.single().competition)
    }

    @Test
    fun `newer live score replaces a previous 0-0 row`() {
        val zero = match(id = "52", kickoff = 1L, status = FootballMatchStatus.LIVE, minute = 10)
        val one = match(id = "52", kickoff = 1L, status = FootballMatchStatus.LIVE, minute = 10, homeScore = 1, awayScore = 0)

        val merged = mergeFootballMatchRows(listOf(zero, one))
        assertEquals(1, merged.size)
        assertEquals(1, merged.single().homeScore)
        assertEquals(0, merged.single().awayScore)
    }

    @Test
    fun `confirmed final result survives an older live row in both orders`() {
        val final = match(id = "53", kickoff = 1L, status = FootballMatchStatus.FINISHED, homeScore = 2, awayScore = 1)
        val live = match(id = "53", kickoff = 1L, status = FootballMatchStatus.LIVE, minute = 60, homeScore = 0, awayScore = 0)

        for (rows in listOf(listOf(final, live), listOf(live, final))) {
            val merged = mergeFootballMatchRows(rows)
            assertEquals(FootballMatchStatus.FINISHED, merged.single().status)
            assertEquals(2, merged.single().homeScore)
            assertEquals(1, merged.single().awayScore)
        }
    }

    @Test
    fun `a team beyond position 500 remains searchable and survives cache reload`() {
        val many = (1..700).map { index ->
            FootballTeam("team-$index", "Team ${index.toString().padStart(3, '0')}")
        }
        val merged = mergeFootballTeamLists(many)
        assertEquals(700, merged.size)
        assertEquals("team-699", filterFootballTeams(merged, "Team 699").single().id)

        val reloaded = decodeFootballTeams(encodeFootballTeams(merged))
        assertEquals(700, reloaded.size)
        val selected = filterFootballTeams(reloaded, "Team 699").single()
        assertEquals("team-699", selected.id)
        val favorite = FootballFavoriteTeam(selected.id, selected.name, 1L)
        assertEquals(favorite, decodeFootballFavoriteTeam(encodeFootballFavoriteTeam(favorite)))
    }

    @Test
    fun `team catalog parser returns stable searchable teams`() {
        val teams = checkNotNull(
            parseFootballTeamCatalog(
                """{"schemaVersion":1,"generatedAt":1795040000,"stale":false,"complete":true,"teams":[{"id":17,"name":"Al Hilal"},{"id":20,"name":"Al Nassr"},{"id":17,"name":"Al Hilal"}]}""",
            ),
        )

        assertEquals(listOf("17", "20"), teams.map(FootballTeam::id))
        assertEquals(listOf("Al Hilal", "Al Nassr"), teams.map(FootballTeam::name))
        assertNull(
            parseFootballTeamCatalog(
                """{"schemaVersion":1,"complete":false,"teams":[{"id":17,"name":"Al Hilal"}]}""",
            ),
        )
    }

    @Test
    fun `feed parser preserves nullable scores aliases stale state and Riyadh metadata`() {
        val raw = """
            {
              "schemaVersion":1,
              "day":"2026-11-19",
              "timezone":"Asia/Riyadh",
              "generatedAt":1795030200,
              "cachedAt":1795030200,
              "stale":true,
              "empty":false,
              "retryAfterSeconds":90,
              "refreshSeconds":60,
              "provider":"bzzoiro",
              "matches":[
                {
                  "id":"evt:202",
                  "providerEventId":"202",
                  "aliases":["101"],
                  "competition":{"id":"85","name":"Saudi Pro League","country":"Saudi Arabia"},
                  "homeTeam":{"id":"10","name":"Al Hilal"},
                  "awayTeam":{"id":"20","name":"Al Nassr"},
                  "kickoffAt":1795037400,
                  "status":"live",
                  "homeScore":null,
                  "awayScore":null,
                  "minute":63,
                  "carriedOver":true
                }
              ],
              "teams":[{"id":"10","name":"Al Hilal"},{"id":"20","name":"Al Nassr"}]
            }
        """.trimIndent()

        val parsed = checkNotNull(parseFootballFeed(raw, "2026-11-19"))
        val match = parsed.matches.single()
        assertEquals("2026-11-19", parsed.dayKey)
        assertTrue(parsed.stale)
        assertEquals(90L, parsed.retryAfterSeconds)
        assertNull(match.homeScore)
        assertNull(match.awayScore)
        assertEquals(63, match.minute)
        assertTrue(match.carriedOver)
        assertTrue("101" in match.eventIds)
        assertEquals(2, parsed.teams.size)
    }

    @Test
    fun `feed parser keeps the separate kickoff history and started timestamp`() {
        val raw = """
            {
              "schemaVersion":1,
              "day":"2026-11-19",
              "generatedAt":1795122000,
              "cachedAt":1795122000,
              "stale":false,
              "refreshSeconds":60,
              "matches":[],
              "kickoffHistory":[
                {
                  "providerEventId":"61",
                  "aliases":[],
                  "competition":{"id":"17","name":"Saudi Pro League","country":"Saudi Arabia"},
                  "homeTeam":{"id":"266","name":"Al-Taawoun"},
                  "awayTeam":{"id":"271","name":"Al-Ittihad"},
                  "kickoffAt":1795119000,
                  "status":"finished",
                  "homeScore":2,
                  "awayScore":1,
                  "startedAt":1795119000
                }
              ],
              "teams":[]
            }
        """.trimIndent()

        val parsed = checkNotNull(parseFootballFeed(raw, "2026-11-19"))
        assertTrue(parsed.matches.isEmpty())
        assertEquals(1, parsed.kickoffHistory.size)
        assertEquals(1795119000_000L, parsed.kickoffHistory.single().startedAtEpochMs)
    }

    @Test
    fun `feed parser rejects a different day or malformed score object`() {
        assertNull(parseFootballFeed("""{"schemaVersion":1,"day":"2026-11-20","generatedAt":1,"matches":[]}""", "2026-11-19"))
        assertNull(parseFootballFeed("not-json", "2026-11-19"))
    }

    @Test
    fun `feed owner rejects a stale profile generation after a profile change`() {
        val expected = AccountProfileStateScope("account-1", "profile-1")
        val otherProfile = AccountProfileStateScope("account-1", "profile-2")

        assertTrue(footballOwnerStillCurrent(expected, expected, 4L, 4L))
        assertFalse(footballOwnerStillCurrent(expected, otherProfile, 4L, 4L))
        assertFalse(footballOwnerStillCurrent(expected, expected, 4L, 5L))
        assertFalse(footballOwnerStillCurrent(expected, null, 4L, 4L))
    }

    @Test
    fun `football feed fails closed for Kids profiles`() {
        assertTrue(footballFeedAllowedForProfile(sa.hulksa.player.model.ProfileKind.STANDARD))
        assertFalse(footballFeedAllowedForProfile(sa.hulksa.player.model.ProfileKind.KIDS))
    }

    @Test
    fun `retry-after accepts bounded seconds and an HTTP date`() {
        val now = 1_795_040_000_000L
        assertEquals(120L, parseFootballRetryAfterHeader("120", now))
        assertEquals(1L, parseFootballRetryAfterHeader("-4", now))
        assertEquals(86_400L, parseFootballRetryAfterHeader("999999", now))
        val date = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("GMT") }
            .format(java.util.Date(now + 90_000L))
        assertEquals(90L, parseFootballRetryAfterHeader(date, now))
        assertNull(parseFootballRetryAfterHeader("not-a-date", now))
    }

    private fun match(
        id: String,
        kickoff: Long,
        status: FootballMatchStatus,
        homeId: String = "home",
        awayId: String = "away",
        homeScore: Int? = null,
        awayScore: Int? = null,
        replacedBy: String? = null,
        minute: Int? = null,
        startedAtEpochMs: Long? = null,
    ): FootballMatch = FootballMatch(
        providerEventId = id,
        replacedBy = replacedBy,
        competition = FootballCompetition("85", "Saudi Pro League", "Saudi Arabia"),
        homeTeam = FootballTeam(homeId, homeId),
        awayTeam = FootballTeam(awayId, awayId),
        kickoffEpochMs = kickoff,
        status = status,
        homeScore = homeScore,
        awayScore = awayScore,
        minute = minute,
        startedAtEpochMs = startedAtEpochMs,
    )
}
