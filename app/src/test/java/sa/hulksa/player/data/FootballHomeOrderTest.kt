package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FootballHomeOrderTest {

    private fun match(
        id: String,
        home: String,
        away: String,
        priority: Int,
        kickoff: Long,
    ) = FootballMatch(
        providerEventId = id,
        competition = FootballCompetition("c$priority", "League $priority", priority = priority),
        homeTeam = FootballTeam(home, home),
        awayTeam = FootballTeam(away, away),
        kickoffEpochMs = kickoff,
        status = FootballMatchStatus.UPCOMING,
    )

    private val teams = listOf(
        match("a", "f1", "x", priority = 80, kickoff = 30L),
        match("b", "f2", "y", priority = 70, kickoff = 20L),
        match("c", "f1", "f2", priority = 90, kickoff = 10L),
        match("d", "z", "w", priority = 1, kickoff = 5L),
    )

    @Test
    fun `no favorites keeps competition priority order`() {
        assertEquals(
            listOf("d", "b", "a", "c"),
            orderFootballMatches(teams, emptySet()).map(FootballMatch::providerEventId),
        )
    }

    @Test
    fun `one favorite leads before higher-priority non-favorite`() {
        assertEquals(
            listOf("a", "c", "d", "b"),
            orderFootballMatches(teams, setOf("f1")).map(FootballMatch::providerEventId),
        )
    }

    @Test
    fun `multiple favorites keep every fixture before other matches`() {
        assertEquals(
            listOf("b", "a", "c", "d"),
            orderFootballMatches(teams, setOf("f1", "f2")).map(FootballMatch::providerEventId),
        )
    }

    @Test
    fun `match between two favorites is not duplicated`() {
        val ordered = orderFootballMatches(teams, setOf("f1", "f2"))
        assertEquals(4, ordered.size)
        assertEquals(1, ordered.count { it.providerEventId == "c" })
    }

    private fun saudiMatch(
        id: String,
        home: String,
        away: String,
        kickoff: Long,
    ) = FootballMatch(
        providerEventId = id,
        competition = FootballCompetition(
            id = "17",
            name = "Roshn Saudi League",
            country = "Saudi Arabia",
            arabicName = "دوري روشن السعودي",
            priority = 10,
        ),
        homeTeam = FootballTeam(home, home),
        awayTeam = FootballTeam(away, away),
        kickoffEpochMs = kickoff,
        status = FootballMatchStatus.UPCOMING,
    )

    @Test
    fun `saudi competition leads even when a foreign favorite exists`() {
        val foreignFavorite = match("f", "f1", "x", priority = 40, kickoff = 5L)
        val saudi = saudiMatch("s", "hilal", "nassr", 90L)

        val ordered = orderFootballMatches(listOf(foreignFavorite, saudi), setOf("f1"))

        assertEquals(listOf("s", "f"), ordered.map(FootballMatch::providerEventId))
    }

    @Test
    fun `saudi favorite leads inside the Saudi bucket`() {
        val saudiOther = saudiMatch("s1", "a", "b", 10L)
        val saudiFavorite = saudiMatch("s2", "f1", "c", 20L)

        val ordered = orderFootballMatches(listOf(saudiOther, saudiFavorite), setOf("f1"))

        assertEquals(listOf("s2", "s1"), ordered.map(FootballMatch::providerEventId))
    }

    @Test
    fun `favorite edit reorders the same accepted rows deterministically`() {
        val before = orderFootballMatches(teams, setOf("f1"))
        val after = orderFootballMatches(teams, setOf("f2"))
        assertEquals(listOf("a", "c", "d", "b"), before.map(FootballMatch::providerEventId))
        assertEquals(listOf("b", "c", "d", "a"), after.map(FootballMatch::providerEventId))
    }

    private fun acceptedSnapshot() = FootballFeedSnapshot(
        dayKey = "2026-11-20",
        generatedAtEpochMs = 1_000L,
        cachedAtEpochMs = 2_000L,
        stale = false,
        retryAfterSeconds = null,
        refreshSeconds = 60,
        matches = teams,
        teams = emptyList(),
        fetchedAtEpochMs = 3_000L,
    )

    @Test
    fun `favorite edit reorders the accepted day projection without mutating it`() {
        val accepted = acceptedSnapshot()
        val rail = footballFavoritesFirstSnapshot(accepted, setOf("f2"))
        assertEquals(listOf("b", "c", "d", "a"), rail!!.matches.map(FootballMatch::providerEventId))
        assertEquals(listOf("a", "b", "c", "d"), accepted.matches.map(FootballMatch::providerEventId))
        assertEquals(accepted.cachedAtEpochMs, rail.cachedAtEpochMs)
        assertEquals(accepted.fetchedAtEpochMs, rail.fetchedAtEpochMs)
    }

    @Test
    fun `profile switch uses only the active profile favorites`() {
        val accepted = acceptedSnapshot()
        val firstProfile = footballFavoritesFirstSnapshot(accepted, setOf("f1"))
        val secondProfile = footballFavoritesFirstSnapshot(accepted, setOf("f2"))
        val noFavorites = footballFavoritesFirstSnapshot(accepted, emptySet())
        assertEquals(listOf("a", "c", "d", "b"), firstProfile!!.matches.map(FootballMatch::providerEventId))
        assertEquals(listOf("b", "c", "d", "a"), secondProfile!!.matches.map(FootballMatch::providerEventId))
        assertEquals(listOf("d", "b", "a", "c"), noFavorites!!.matches.map(FootballMatch::providerEventId))
        assertEquals(listOf("a", "b", "c", "d"), accepted.matches.map(FootballMatch::providerEventId))
    }

    @Test
    fun `same provider time revalidation keeps display order and renewed receipt`() {
        val accepted = acceptedSnapshot()
        val revalidated = accepted.copy(
            cachedAtEpochMs = accepted.cachedAtEpochMs,
            fetchedAtEpochMs = accepted.fetchedAtEpochMs + 1_000L,
        )
        val rail = footballFavoritesFirstSnapshot(revalidated, setOf("f2"))
        assertEquals(listOf("b", "c", "d", "a"), rail!!.matches.map(FootballMatch::providerEventId))
        assertEquals(accepted.cachedAtEpochMs, rail.cachedAtEpochMs)
        assertEquals(4_000L, rail.fetchedAtEpochMs)
    }
}
