package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FootballR04Test {
    @Test
    fun `feed parser keeps confirmed Saudi broadcaster names`() {
        val raw = """
            {
              "schemaVersion":1,
              "day":"2026-11-20",
              "generatedAt":1795030200,
              "cachedAt":1795030200,
              "stale":false,
              "refreshSeconds":60,
              "matches":[{
                "providerEventId":"213754",
                "competition":{"id":"5","name":"Bundesliga","arabicName":"الدوري الألماني","priority":40},
                "homeTeam":{"id":"79","name":"FC Bayern München","arabicName":"بايرن ميونخ"},
                "awayTeam":{"id":"94","name":"1. FC Köln","arabicName":"كولن"},
                "kickoffAt":1795040600,
                "status":"upcoming",
                "broadcasters":["MBC Shahid","MBC Shahid","beIN SPORTS 1"]
              }],
              "teams":[]
            }
        """.trimIndent()

        val match = checkNotNull(parseFootballFeed(raw, "2026-11-20")).matches.single()

        assertEquals(listOf("MBC Shahid", "beIN SPORTS 1"), match.broadcasters)
        assertTrue(match.competition.displayName == "الدوري الألماني")
        assertTrue(match.homeTeam.displayName == "بايرن ميونخ")
    }

    @Test
    fun `client allowlist and catalog policy version match the verified default`() {
        assertEquals(27, FOOTBALL_ALLOWED_COMPETITION_IDS.size)
        assertTrue("17" in FOOTBALL_ALLOWED_COMPETITION_IDS)
        assertTrue("90" in FOOTBALL_ALLOWED_COMPETITION_IDS)
        assertTrue("53" !in FOOTBALL_ALLOWED_COMPETITION_IDS)
        assertTrue("47" !in FOOTBALL_ALLOWED_COMPETITION_IDS)
        assertEquals("r04-allowlist-27", FOOTBALL_CATALOG_POLICY_VERSION)
    }

    @Test
    fun `cached and network snapshots cannot reintroduce excluded competitions`() {
        fun match(id: String, competitionId: String, teamId: String) = FootballMatch(
            providerEventId = id,
            competition = FootballCompetition(competitionId, "League $competitionId"),
            homeTeam = FootballTeam(teamId, "Team $teamId", competitionIds = setOf(competitionId)),
            awayTeam = FootballTeam("away-$id", "Away $id", competitionIds = setOf(competitionId)),
            kickoffEpochMs = 1_795_040_600_000L,
            status = FootballMatchStatus.UPCOMING,
        )
        val snapshot = FootballFeedSnapshot(
            dayKey = "2026-11-20",
            generatedAtEpochMs = 1_795_040_000_000L,
            cachedAtEpochMs = 1_795_040_000_000L,
            stale = false,
            retryAfterSeconds = null,
            refreshSeconds = 60,
            matches = listOf(
                match("allowed", "17", "262"),
                match("excluded", "18", "900"),
                match("optional", "53", "901"),
            ),
            teams = listOf(
                FootballTeam("262", "Al Hilal", competitionIds = setOf("17")),
                FootballTeam("900", "MLS", competitionIds = setOf("18")),
            ),
            reminderCandidates = listOf(match("future-optional", "53", "901")),
        )

        val filtered = filterFootballSnapshotForAllowlist(snapshot)
        assertEquals(listOf("allowed"), filtered.matches.map(FootballMatch::providerEventId))
        assertTrue(filtered.reminderCandidates.isEmpty())
        assertEquals(listOf("262"), filtered.teams.map(FootballTeam::id))
    }

    @Test
    fun `fresh filtered catalog replaces obsolete catalog-derived teams`() {
        val obsolete = FootballTeam("old", "Old Excluded", competitionIds = setOf("18"))
        val catalog = listOf(FootballTeam("262", "Al Hilal", competitionIds = setOf("17")))
        val liveMatch = FootballMatch(
            providerEventId = "e1",
            competition = FootballCompetition("17", "Saudi Pro League"),
            homeTeam = FootballTeam("267", "Al Nassr", competitionIds = setOf("17")),
            awayTeam = FootballTeam("262", "Al Hilal", competitionIds = setOf("17")),
            kickoffEpochMs = 1_795_040_600_000L,
            status = FootballMatchStatus.UPCOMING,
        )
        val reconciled = reconcileFootballCatalogTeams(
            catalog,
            listOf(liveMatch),
            emptyList(),
        )

        assertTrue("old" !in reconciled.map(FootballTeam::id))
        assertTrue("262" in reconciled.map(FootballTeam::id))
        assertTrue("267" in reconciled.map(FootballTeam::id))
    }

    @Test
    fun `broadcaster names survive merge and latest detail resolution`() {
        val base = FootballMatch(
            providerEventId = "e1",
            competition = FootballCompetition("5", "Bundesliga"),
            homeTeam = FootballTeam("79", "Bayern"),
            awayTeam = FootballTeam("94", "Koln"),
            kickoffEpochMs = 1_795_040_600_000L,
            status = FootballMatchStatus.UPCOMING,
            broadcasters = listOf("MBC Shahid"),
        )
        val fresher = base.copy(
            providerUpdatedAtEpochMs = 1_795_040_000_000L,
            broadcasters = listOf("beIN SPORTS 1"),
        )
        val merged = mergeFootballMatchRows(listOf(base, fresher)).single()
        val resolved = resolveFootballMatch(base, listOf(fresher))

        assertEquals(listOf("MBC Shahid", "beIN SPORTS 1"), merged.broadcasters)
        assertTrue("MBC Shahid" in resolved.broadcasters)
        assertTrue("beIN SPORTS 1" in resolved.broadcasters)
    }

    @Test
    fun `same provider id rescheduled to a new kickoff does not carry channels`() {
        val previous = FootballMatch(
            providerEventId = "e7",
            competition = FootballCompetition("5", "Bundesliga"),
            homeTeam = FootballTeam("79", "Bayern"),
            awayTeam = FootballTeam("94", "Koln"),
            kickoffEpochMs = 1_795_040_600_000L,
            status = FootballMatchStatus.UPCOMING,
            broadcasters = listOf("Old Channel"),
        )
        val rescheduled = previous.copy(
            kickoffEpochMs = 1_795_127_000_000L,
            status = FootballMatchStatus.LIVE,
            minute = 12,
            providerUpdatedAtEpochMs = 1_795_127_120_000L,
            broadcasters = listOf("New Channel"),
        )

        val merged = mergeFootballMatchRows(listOf(previous, rescheduled)).single()
        val resolved = resolveFootballMatch(rescheduled, listOf(previous, rescheduled))

        // The old observation still owns the displayed kickoff, so the new assignment is not
        // valid for it; the old assignment is not valid for the rescheduled instant either.
        assertFalse("New Channel" in merged.broadcasters)
        assertEquals(listOf("New Channel"), resolved.broadcasters)
        assertFalse("Old Channel" in resolved.broadcasters)
    }

    @Test
    fun `replacement alias with an equal kickoff keeps only its own assignment`() {
        val original = FootballMatch(
            providerEventId = "old",
            competition = FootballCompetition("5", "Bundesliga"),
            homeTeam = FootballTeam("79", "Bayern"),
            awayTeam = FootballTeam("94", "Koln"),
            kickoffEpochMs = 1_795_040_600_000L,
            status = FootballMatchStatus.POSTPONED,
            broadcasters = listOf("Old Channel"),
        )
        val replacement = original.copy(
            providerEventId = "new",
            aliases = listOf("old"),
            status = FootballMatchStatus.UPCOMING,
            providerUpdatedAtEpochMs = 1_795_050_000_000L,
            broadcasters = listOf("New Channel"),
        )

        val resolved = resolveFootballMatch(original, listOf(original, replacement))

        assertEquals("new", resolved.providerEventId)
        assertEquals(listOf("New Channel"), resolved.broadcasters)
    }

    @Test
    fun `replacement alias does not inherit obsolete broadcaster assignments`() {
        val original = FootballMatch(
            providerEventId = "old",
            competition = FootballCompetition("5", "Bundesliga"),
            homeTeam = FootballTeam("79", "Bayern"),
            awayTeam = FootballTeam("94", "Koln"),
            kickoffEpochMs = 1_795_040_600_000L,
            status = FootballMatchStatus.POSTPONED,
            broadcasters = listOf("Old Channel"),
        )
        val replacement = original.copy(
            providerEventId = "new",
            aliases = listOf("old"),
            kickoffEpochMs = 1_795_100_000_000L,
            status = FootballMatchStatus.UPCOMING,
            providerUpdatedAtEpochMs = 1_795_050_000_000L,
            broadcasters = listOf("New Channel"),
        )

        val resolved = resolveFootballMatch(original, listOf(original, replacement))

        assertEquals("new", resolved.providerEventId)
        assertEquals(listOf("New Channel"), resolved.broadcasters)
    }

    @Test
    fun `existing favorite and notification identities remain stable`() {
        val favorite = FootballFavoriteTeam("262", "الهلال", 1_700_000_000_000L)
        assertEquals(
            listOf(favorite),
            migratedFootballFavoriteTeams(null, encodeFootballFavoriteTeam(favorite)),
        )
        val notification = footballKickoffNotificationFor(
            accountId = "account-1",
            profileId = "profile-1",
            match = FootballMatch(
                providerEventId = "213754",
                competition = FootballCompetition("5", "Bundesliga", arabicName = "الدوري الألماني"),
                homeTeam = FootballTeam("79", "FC Bayern München", arabicName = "بايرن ميونخ"),
                awayTeam = FootballTeam("94", "1. FC Köln", arabicName = "كولن"),
                kickoffEpochMs = 1_795_040_600_000L,
                status = FootballMatchStatus.UPCOMING,
            ),
            detectedAtEpochMs = 1_795_040_000_000L,
            alertType = FootballAlertType.REMINDER_15,
        )
        assertEquals("kickoff|account-1|profile-1|213754", footballKickoffNotificationId("account-1", "profile-1", "213754"))
        assertEquals(FootballAlertType.REMINDER_15, notification.alertType)
    }
}
