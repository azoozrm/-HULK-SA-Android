package sa.hulksa.player.data

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FootballCatalogR051Test {
    private fun fixtureBytes(): ByteArray = checkNotNull(
        javaClass.classLoader?.getResourceAsStream("football/r04-production-teams.json"),
    ).use { it.readBytes() }

    private fun fixtureJson(): String = fixtureBytes().toString(Charsets.UTF_8)

    @Test
    fun `actual R04 production catalog is authoritative without policyVersion`() {
        val bytes = fixtureBytes()
        assertEquals(
            "6fa497fb6602abdb4bf79ca7ccfe65e7a1378f93a30ebaae9c93ac54b48c4eb6",
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) },
        )
        val payload = checkNotNull(parseFootballTeamCatalogPayload(fixtureJson()))
        assertEquals(1_494, payload.teams.size)
        assertNull(payload.policyVersion)
        assertEquals(FOOTBALL_CATALOG_ALLOWLIST_HASH, payload.allowlistHash)
        assertTrue(payload.teams.all { team ->
            team.competitionIds.any { it in FOOTBALL_ALLOWED_COMPETITION_IDS }
        })
    }

    @Test
    fun `catalog policy identity and membership are validated consistently`() {
        val valid = """
            {"schemaVersion":1,"complete":true,"allowlistHash":"$FOOTBALL_CATALOG_ALLOWLIST_HASH",
             "teams":[{"id":"262","name":"Al Hilal","competitionIds":["17"]}]}
        """.trimIndent()
        assertNotNull(parseFootballTeamCatalogPayload(valid))

        assertNull(parseFootballTeamCatalogPayload("""{"schemaVersion":1,"complete":true,"teams":[]}"""))
        assertNull(parseFootballTeamCatalogPayload("""{"schemaVersion":1,"complete":true,"allowlistHash":"wrong","teams":[]}"""))
        assertNull(parseFootballTeamCatalogPayload("""{"schemaVersion":1,"complete":false,"allowlistHash":"$FOOTBALL_CATALOG_ALLOWLIST_HASH","teams":[]}"""))
        assertNull(parseFootballTeamCatalogPayload("""{"schemaVersion":1,"complete":true,"allowlistHash":"$FOOTBALL_CATALOG_ALLOWLIST_HASH"}"""))
        assertNull(parseFootballTeamCatalogPayload("""
            {"schemaVersion":1,"complete":true,"allowlistHash":"$FOOTBALL_CATALOG_ALLOWLIST_HASH",
             "teams":[{"id":"999","name":"Excluded","competitionIds":["18"]}]}
        """.trimIndent()))
    }

    @Test
    fun `a valid empty catalog is accepted and removes obsolete teams`() {
        val payload = checkNotNull(parseFootballTeamCatalogPayload("""
            {"schemaVersion":1,"complete":true,"allowlistHash":"$FOOTBALL_CATALOG_ALLOWLIST_HASH","teams":[]}
        """.trimIndent()))
        assertTrue(payload.teams.isEmpty())
        val obsolete = FootballTeam("old", "Old Excluded", competitionIds = setOf("18"))
        val match = FootballMatch(
            providerEventId = "e1",
            competition = FootballCompetition("17", "Saudi Pro League"),
            homeTeam = FootballTeam("262", "Al Hilal", aliases = listOf("Hilal")),
            awayTeam = FootballTeam("267", "Al Nassr"),
            kickoffEpochMs = 1_795_040_600_000L,
            status = FootballMatchStatus.UPCOMING,
        )
        val favorite = FootballFavoriteTeam(
            teamId = "333",
            teamName = "Followed",
            selectedAtEpochMs = 1L,
            team = FootballTeam("333", "Followed", aliases = listOf("Alias")),
        )

        val result = reconcileFootballKnownTeams(
            authoritativeCatalog = payload.teams,
            currentKnownTeams = listOf(obsolete),
            publishedTeams = listOf(obsolete, FootballTeam("late", "Late Feed Team")),
            matches = listOf(match),
            favorites = listOf(favorite),
        )

        assertTrue("old" !in result.map(FootballTeam::id))
        assertTrue("late" !in result.map(FootballTeam::id))
        assertEquals(setOf("262", "267", "333"), result.map(FootballTeam::id).toSet())
        assertTrue(result.first { it.id == "333" }.aliases.contains("Alias"))
    }

    @Test
    fun `older feed publication cannot restore removed catalog teams`() {
        val authority = listOf(FootballTeam("262", "Al Hilal", competitionIds = setOf("17")))
        val obsolete = FootballTeam("old", "Old Catalog Team", competitionIds = setOf("18"))
        val publishedByOlderFeed = listOf(obsolete, FootballTeam("late", "Late Feed Team"))
        val match = FootballMatch(
            providerEventId = "e2",
            competition = FootballCompetition("17", "Saudi Pro League"),
            homeTeam = FootballTeam("262", "Al Hilal"),
            awayTeam = FootballTeam("267", "Al Nassr"),
            kickoffEpochMs = 1_795_040_600_000L,
            status = FootballMatchStatus.FINISHED,
        )

        val result = reconcileFootballKnownTeams(
            authoritativeCatalog = authority,
            currentKnownTeams = listOf(obsolete),
            publishedTeams = publishedByOlderFeed,
            matches = listOf(match),
            favorites = emptyList(),
        )

        assertEquals(setOf("262", "267"), result.map(FootballTeam::id).toSet())
    }
}
