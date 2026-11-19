package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.ui.screens.FootballCenterFilter
import sa.hulksa.player.ui.screens.footballCenterGroups
import sa.hulksa.player.ui.screens.footballMatchesForFilter

class FootballR03FeaturesTest {
    private val now = 1_795_040_000_000L

    @Test
    fun `Riyadh day labels cover yesterday today and tomorrow`() {
        val today = footballRiyadhDayKey(now)
        assertEquals("اليوم", footballRiyadhDayLabel(today, now))
        assertEquals("الامس", footballRiyadhDayLabel(footballRiyadhDayKey(now - 24L * 60L * 60L * 1_000L), now))
        assertEquals("غدا", footballRiyadhDayLabel(footballRiyadhDayKey(now + 24L * 60L * 60L * 1_000L), now))
    }

    @Test
    fun `multiple favorite teams round-trip with timestamps and stable order`() {
        val favorites = listOf(
            FootballFavoriteTeam("262", "الهلال", 100L),
            FootballFavoriteTeam("267", "النصر", 200L),
            FootballFavoriteTeam("262", "الهلال", 100L),
        )

        val decoded = decodeFootballFavoriteTeams(encodeFootballFavoriteTeams(favorites))

        assertEquals(listOf("262", "267"), decoded.map(FootballFavoriteTeam::teamId))
        assertEquals(listOf(100L, 200L), decoded.map(FootballFavoriteTeam::selectedAtEpochMs))
        assertEquals(emptyList<FootballFavoriteTeam>(), decodeFootballFavoriteTeams(null))
    }

    @Test
    fun `legacy single favorite migrates once with its original timestamp`() {
        val legacy = FootballFavoriteTeam("262", "الهلال", 1_700_000_000_000L)
        val first = migratedFootballFavoriteTeams(
            pluralRaw = null,
            legacyRaw = encodeFootballFavoriteTeam(legacy),
        )
        val second = migratedFootballFavoriteTeams(
            pluralRaw = encodeFootballFavoriteTeams(first),
            legacyRaw = encodeFootballFavoriteTeam(legacy),
        )
        val newer = FootballFavoriteTeam("267", "النصر", 1_800_000_000_000L)

        assertEquals(listOf(legacy), first)
        assertEquals(first, second)
        assertEquals(
            listOf("267"),
            migratedFootballFavoriteTeams(encodeFootballFavoriteTeams(listOf(newer)), encodeFootballFavoriteTeam(legacy))
                .map(FootballFavoriteTeam::teamId),
        )
    }

    @Test
    fun `team search matches Arabic English and provider aliases`() {
        val teams = listOf(
            FootballTeam(
                id = "262",
                name = "Al Hilal",
                arabicName = "الهلال",
                aliases = listOf("Hilal", "الهلال السعودي"),
            ),
            FootballTeam(id = "267", name = "Al Nassr", arabicName = "النصر"),
        )

        assertEquals(listOf("262"), filterFootballTeams(teams, "الهلال").map(FootballTeam::id))
        assertEquals(listOf("262"), filterFootballTeams(teams, "hilal").map(FootballTeam::id))
        assertEquals(listOf("262"), filterFootballTeams(teams, "Al Hilal").map(FootballTeam::id))
        assertEquals(listOf("267"), filterFootballTeams(teams, "النصر").map(FootballTeam::id))
        assertEquals(listOf("262", "267"), filterFootballTeams(teams, "").map(FootballTeam::id))
    }

    @Test
    fun `stale complete catalog keeps provider cache time and incomplete catalog is rejected`() {
        val payload = checkNotNull(
            parseFootballTeamCatalogPayload(
                """
                {
                  "schemaVersion":1,
                  "generatedAt":1795030200,
                  "cachedAt":1795000000,
                  "stale":true,
                  "complete":true,
                  "teams":[{"id":"262","name":"Al Hilal","arabicName":"الهلال"}]
                }
                """.trimIndent(),
            ),
        )

        assertTrue(payload.stale)
        assertEquals(1_795_000_000_000L, payload.cachedAtEpochMs)
        assertEquals("الهلال", payload.teams.single().displayName)
        assertNull(
            parseFootballTeamCatalog(
                """{"schemaVersion":1,"complete":false,"teams":[{"id":"262","name":"Al Hilal"}]}""",
            ),
        )
    }

    @Test
    fun `a fresher final row without scores cannot erase verified scores`() {
        val finalWithScore = match(
            id = "1",
            kickoff = now - 3 * 60 * 60_000L,
            status = FootballMatchStatus.FINISHED,
            homeScore = 2,
            awayScore = 1,
        )
        val finalWithoutScore = finalWithScore.copy(homeScore = null, awayScore = null)
        val liveWithScore = finalWithScore.copy(
            status = FootballMatchStatus.LIVE,
            minute = 70,
        )

        val finalLast = mergeFootballMatchRows(listOf(finalWithScore, finalWithoutScore)).single()
        val finalAfterLive = mergeFootballMatchRows(listOf(liveWithScore, finalWithoutScore)).single()

        assertEquals(FootballMatchStatus.FINISHED, finalLast.status)
        assertEquals(2, finalLast.homeScore)
        assertEquals(1, finalLast.awayScore)
        assertEquals(FootballMatchStatus.FINISHED, finalAfterLive.status)
        assertEquals(2, finalAfterLive.homeScore)
        assertEquals(1, finalAfterLive.awayScore)
    }

    @Test
    fun `competition priority leads the non-favorite ordering`() {
        val saudiUpcoming = match(
            id = "saudi",
            kickoff = now + 60 * 60_000L,
            status = FootballMatchStatus.UPCOMING,
            competitionId = "17",
            priority = 10,
        )
        val europeLive = match(
            id = "europe",
            kickoff = now - 10 * 60_000L,
            status = FootballMatchStatus.LIVE,
            competitionId = "1",
            priority = 40,
        )
        val favorite = match(
            id = "favorite",
            kickoff = now + 4 * 60 * 60_000L,
            status = FootballMatchStatus.UPCOMING,
            homeId = "fav",
            competitionId = "3",
            priority = 40,
        )

        assertTrue(favorite.involvesTeam("fav"))
        val ordered = orderFootballMatches(
            listOf(europeLive, saudiUpcoming, favorite),
            setOf("fav"),
        )

        assertEquals(listOf("favorite", "saudi", "europe"), ordered.map(FootballMatch::providerEventId))
    }

    @Test
    fun `feed parser keeps Arabic names crests competition priority and national flags`() {
        val raw = """
            {
              "schemaVersion":1,
              "day":"2026-11-19",
              "generatedAt":1795030200,
              "cachedAt":1795030200,
              "stale":false,
              "refreshSeconds":60,
              "matches":[{
                "providerEventId":"1",
                "competition":{"id":"17","name":"Saudi Pro League","arabicName":"دوري روشن السعودي","logoUrl":"https://sports.bzzoiro.com/img/league/17/","priority":10},
                "homeTeam":{"id":"262","name":"Al Hilal","arabicName":"الهلال","logoUrl":"https://sports.bzzoiro.com/img/team/262/","competitionIds":["17"],"nationalTeam":false},
                "awayTeam":{"id":"267","name":"Al Nassr","arabicName":"النصر","logoUrl":"https://sports.bzzoiro.com/img/team/267/","competitionIds":["17"],"nationalTeam":false},
                "kickoffAt":1795037400,
                "status":"upcoming"
              }],
              "teams":[]
            }
        """.trimIndent()

        val parsed = checkNotNull(parseFootballFeed(raw, "2026-11-19"))
        val match = parsed.matches.single()

        assertEquals("دوري روشن السعودي", match.competition.displayName)
        assertEquals(10, match.competition.priority)
        assertEquals("الهلال", match.homeTeam.displayName)
        assertEquals("https://sports.bzzoiro.com/img/team/267/", match.awayTeam.logoUrl)
        assertEquals(setOf("17"), match.homeTeam.competitionIds)
        assertFalse(match.awayTeam.nationalTeam)
    }

    @Test
    fun `hide scores keeps teams status and time but removes the result`() {
        val notification = footballKickoffNotificationFor(
            accountId = "account-1",
            profileId = "profile-1",
            match = match(
                id = "1",
                kickoff = now - 2 * 60 * 60_000L,
                status = FootballMatchStatus.FINISHED,
                homeScore = 2,
                awayScore = 1,
            ),
            detectedAtEpochMs = now,
        )

        val visible = footballKickoffDetail(notification)
        val hidden = footballKickoffDetail(notification, hideScores = true)

        assertTrue(visible.contains("2 - 1"))
        assertFalse(hidden.contains("2 - 1"))
        assertTrue(hidden.contains("انتهت"))
        assertTrue(hidden.contains(footballRiyadhTimeLabel(notification.kickoffEpochMs)))
    }

    @Test
    fun `a fixture reminder works without a followed team and reschedules in place`() {
        val match = match(
            id = "fixture",
            kickoff = now + 10 * 60_000L,
            status = FootballMatchStatus.UPCOMING,
        )
        val preferences = mapOf(
            "fixture" to FootballMatchAlertPreferences(
                reminder15 = true,
                manuallyTracked = true,
                trackedAtEpochMs = now - 60_000L,
            ),
        )
        val first = generateFootballMatchAlerts(
            accountId = "account-1",
            profileId = "profile-1",
            favoriteTeams = emptyList(),
            preferences = preferences,
            previous = null,
            current = snapshot(match),
            inbox = FootballInboxState(),
            nowEpochMs = now,
        )

        val reminder = first.inbox.notifications.single()
        assertEquals(FootballAlertType.REMINDER_15, reminder.alertType)
        assertEquals(match.kickoffEpochMs, reminder.kickoffEpochMs)

        val rescheduledMatch = match.copy(kickoffEpochMs = now + 45 * 60_000L)
        val second = generateFootballMatchAlerts(
            accountId = "account-1",
            profileId = "profile-1",
            favoriteTeams = emptyList(),
            preferences = preferences,
            previous = null,
            current = snapshot(rescheduledMatch),
            inbox = first.inbox,
            nowEpochMs = now + 60_000L,
        )

        assertTrue(second.added.isEmpty())
        assertEquals(1, second.inbox.notifications.size)
        assertEquals(rescheduledMatch.kickoffEpochMs, second.inbox.notifications.single().kickoffEpochMs)
        assertEquals(
            rescheduledMatch.kickoffEpochMs - 15 * 60_000L,
            second.inbox.notifications.single().startedAtEpochMs,
        )
    }

    @Test
    fun `a cancelled fixture keeps the delivered reminder but changes its status`() {
        val upcoming = match(
            id = "cancelled",
            kickoff = now + 10 * 60_000L,
            status = FootballMatchStatus.UPCOMING,
        )
        val preferences = mapOf(
            "cancelled" to FootballMatchAlertPreferences(
                reminder15 = true,
                manuallyTracked = true,
                trackedAtEpochMs = now - 60_000L,
            ),
        )
        val first = generateFootballMatchAlerts(
            accountId = "account-1",
            profileId = "profile-1",
            favoriteTeams = emptyList(),
            preferences = preferences,
            previous = null,
            current = snapshot(upcoming),
            inbox = FootballInboxState(),
            nowEpochMs = now,
        )
        assertEquals(1, first.inbox.notifications.size)

        val second = generateFootballMatchAlerts(
            accountId = "account-1",
            profileId = "profile-1",
            favoriteTeams = emptyList(),
            preferences = preferences,
            previous = null,
            current = snapshot(upcoming.copy(status = FootballMatchStatus.CANCELLED)),
            inbox = first.inbox,
            nowEpochMs = now + 60_000L,
        )

        val retained = second.inbox.notifications.single()
        assertEquals(FootballMatchStatus.CANCELLED, retained.status)
        assertEquals(FootballAlertType.REMINDER_15, retained.alertType)
    }

    @Test
    fun `an explicit all-off fixture preference overrides a followed team default`() {
        val live = match(
            id = "followed",
            kickoff = now - 5 * 60_000L,
            status = FootballMatchStatus.LIVE,
            homeId = "262",
        )
        val result = generateFootballMatchAlerts(
            accountId = "account-1",
            profileId = "profile-1",
            favoriteTeams = listOf(FootballFavoriteTeam("262", "الهلال", now - 60 * 60_000L)),
            preferences = mapOf(
                "followed" to FootballMatchAlertPreferences(
                    kickoff = false,
                    reminder15 = false,
                    manuallyTracked = true,
                    trackedAtEpochMs = now - 60_000L,
                ),
            ),
            previous = null,
            current = snapshot(live),
            inbox = FootballInboxState(),
            nowEpochMs = now,
        )

        assertTrue(result.added.isEmpty())
        assertTrue(result.inbox.notifications.isEmpty())
    }

    @Test
    fun `kickoff identity is scoped per account profile and fixture`() {
        val match = match(
            id = "profile-fixture",
            kickoff = now - 5 * 60_000L,
            status = FootballMatchStatus.LIVE,
        )
        fun generateFor(account: String, profile: String) = generateFootballMatchAlerts(
            accountId = account,
            profileId = profile,
            favoriteTeams = listOf(FootballFavoriteTeam("home", "Home", now - 60 * 60_000L)),
            preferences = emptyMap(),
            previous = null,
            current = snapshot(match),
            inbox = FootballInboxState(),
            nowEpochMs = now,
        )

        val first = generateFor("account-1", "profile-1")
        val second = generateFor("account-1", "profile-2")
        val third = generateFor("account-2", "profile-1")

        assertEquals(1, first.added.size)
        assertTrue(first.added.single().id != second.added.single().id)
        assertTrue(first.added.single().id != third.added.single().id)
        assertTrue(second.added.single().id != third.added.single().id)
    }

    @Test
    fun `match center filters and groups follow favorite live and priority rules`() {
        val favoriteLive = match(
            id = "favorite-live",
            kickoff = now - 30 * 60_000L,
            status = FootballMatchStatus.LIVE,
            homeId = "262",
            competitionId = "17",
            priority = 10,
            competitionArabicName = "دوري روشن السعودي",
        )
        val upcoming = match(
            id = "upcoming",
            kickoff = now + 60 * 60_000L,
            status = FootballMatchStatus.UPCOMING,
            competitionId = "1",
            priority = 40,
            competitionArabicName = "الدوري الإنجليزي",
        )
        val matches = listOf(upcoming, favoriteLive)

        assertEquals(
            listOf("favorite-live"),
            footballMatchesForFilter(matches, FootballCenterFilter.MY_TEAMS, setOf("262"))
                .map(FootballMatch::providerEventId),
        )
        assertEquals(
            listOf("favorite-live"),
            footballMatchesForFilter(matches, FootballCenterFilter.LIVE, setOf("262"))
                .map(FootballMatch::providerEventId),
        )
        assertEquals(
            listOf("17", "1"),
            footballCenterGroups(matches).map { it.first.id },
        )
        assertEquals("دوري روشن السعودي", footballCenterGroups(matches).first().first.displayName)
    }

    private fun snapshot(vararg matches: FootballMatch): FootballFeedSnapshot = FootballFeedSnapshot(
        dayKey = footballRiyadhDayKey(now),
        generatedAtEpochMs = now,
        cachedAtEpochMs = now,
        stale = false,
        retryAfterSeconds = null,
        refreshSeconds = 60,
        matches = matches.toList(),
        teams = emptyList(),
    )

    private fun match(
        id: String,
        kickoff: Long,
        status: FootballMatchStatus,
        homeId: String = "home",
        awayId: String = "away",
        homeScore: Int? = null,
        awayScore: Int? = null,
        competitionId: String = "85",
        priority: Int = 50,
        competitionArabicName: String? = null,
    ): FootballMatch = FootballMatch(
        providerEventId = id,
        competition = FootballCompetition(
            id = competitionId,
            name = "Competition",
            arabicName = competitionArabicName,
            priority = priority,
        ),
        homeTeam = FootballTeam(homeId, homeId),
        awayTeam = FootballTeam(awayId, awayId),
        kickoffEpochMs = kickoff,
        status = status,
        homeScore = homeScore,
        awayScore = awayScore,
    )
}
