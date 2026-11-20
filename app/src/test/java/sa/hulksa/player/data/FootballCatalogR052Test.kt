package sa.hulksa.player.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R05.2 catalog authority contracts.
 *
 * These exercise the production authority owner used by both the feed and picker paths. The disk
 * cache is bootstrap-only, an authoritative empty catalog survives every older publication, and a
 * catalog response that completes after a newer request started cannot be persisted or published.
 */
class FootballCatalogR052Test {
    private val owner = "account\u0000profile\u0000https://neo4ksa.com/hulk-operations/api/app/v1/sports/"

    private fun team(id: String) = FootballTeam(id, "Team $id", competitionIds = setOf("17"))

    @Test
    fun `disk cache cannot replace an established empty authority`() {
        val authority = FootballCatalogAuthority()
        val empty = authority.bootstrap(owner, emptyList(), 500L)
        assertNotNull(empty)
        assertTrue("empty authority is established", authority.snapshot(owner)!!.teams.isEmpty())

        assertNull("older disk cache must not bootstrap over authority", authority.bootstrap(owner, listOf(team("old")), 100L))
        assertTrue(authority.snapshot(owner)!!.teams.isEmpty())
    }

    @Test
    fun `authoritative empty catalog survives feed publication and keeps real fixture teams`() {
        val authority = FootballCatalogAuthority()
        checkNotNull(authority.bootstrap(owner, emptyList(), 500L))
        val match = FootballMatch(
            providerEventId = "e1",
            competition = FootballCompetition("17", "Saudi Pro League"),
            homeTeam = FootballTeam("262", "Al Hilal"),
            awayTeam = FootballTeam("267", "Al Nassr"),
            kickoffEpochMs = 1_795_040_600_000L,
            status = FootballMatchStatus.UPCOMING,
        )
        val state = FootballFeedUiState(
            available = true,
            snapshot = FootballFeedSnapshot(
                dayKey = "2026-11-19",
                generatedAtEpochMs = 500L,
                cachedAtEpochMs = 500L,
                stale = false,
                retryAfterSeconds = null,
                refreshSeconds = 60,
                matches = listOf(match),
                teams = listOf(FootballTeam("old", "Obsolete Catalog Team")),
            ),
            knownTeams = listOf(FootballTeam("old", "Obsolete Catalog Team")),
            authoritativeCatalog = emptyList(),
        )

        val published = state.withCatalogAuthority(authority.snapshot(owner)!!, owner)

        assertTrue(published.authoritativeCatalog!!.isEmpty())
        assertEquals(setOf("262", "267"), published.knownTeams.mapTo(linkedSetOf(), FootballTeam::id))
    }

    @Test
    fun `suspended old catalog read cannot replace a newer empty catalog`() = runBlocking {
        val authority = FootballCatalogAuthority()
        val gate = CompletableDeferred<Unit>()
        val oldRequest = authority.beginRequest(owner)
        val oldCompletion = async {
            gate.await()
            authority.publish(owner, oldRequest, listOf(team("old")), 300L, stale = false)
        }
        yield()

        val emptyRequest = authority.beginRequest(owner)
        assertNotNull(authority.publish(owner, emptyRequest, emptyList(), 200L, stale = false))
        assertTrue(authority.snapshot(owner)!!.teams.isEmpty())

        gate.complete(Unit)
        assertNull("late old read must be dropped", oldCompletion.await())
        assertTrue(authority.snapshot(owner)!!.teams.isEmpty())
    }

    @Test
    fun `suspended old catalog read cannot replace newer empty and nonempty catalogs`() = runBlocking {
        val authority = FootballCatalogAuthority()
        val gate = CompletableDeferred<Unit>()
        val oldRequest = authority.beginRequest(owner)
        val oldCompletion = async {
            gate.await()
            authority.publish(owner, oldRequest, listOf(team("old")), 900L, stale = false)
        }
        yield()

        val emptyRequest = authority.beginRequest(owner)
        assertNotNull(authority.publish(owner, emptyRequest, emptyList(), 100L, stale = false))
        assertTrue(authority.snapshot(owner)!!.teams.isEmpty())

        val nonEmptyRequest = authority.beginRequest(owner)
        assertNotNull(authority.publish(owner, nonEmptyRequest, listOf(team("new")), 200L, stale = false))
        assertEquals(listOf("new"), authority.snapshot(owner)!!.teams.map(FootballTeam::id))

        gate.complete(Unit)
        assertNull("older request completion is obsolete", oldCompletion.await())
        assertEquals(listOf("new"), authority.snapshot(owner)!!.teams.map(FootballTeam::id))
    }

    @Test
    fun `stale response never replaces an established authority`() {
        val authority = FootballCatalogAuthority()
        checkNotNull(authority.bootstrap(owner, listOf(team("kept")), 100L))
        val request = authority.beginRequest(owner)

        assertNull(authority.publish(owner, request, emptyList(), 200L, stale = true))
        assertEquals(listOf("kept"), authority.snapshot(owner)!!.teams.map(FootballTeam::id))
    }

    @Test
    fun `newer request with an older provider cache time cannot replace authority`() {
        val authority = FootballCatalogAuthority()
        checkNotNull(authority.bootstrap(owner, listOf(team("kept")), 300L))
        val request = authority.beginRequest(owner)

        assertNull(authority.publish(owner, request, listOf(team("older")), 200L, stale = false))
        assertEquals(listOf("kept"), authority.snapshot(owner)!!.teams.map(FootballTeam::id))
    }

    @Test
    fun `another owner cannot publish or read the established authority`() {
        val authority = FootballCatalogAuthority()
        checkNotNull(authority.bootstrap(owner, listOf(team("kept")), 100L))
        val request = authority.beginRequest(owner)

        assertNull(authority.publish("another-owner", request, emptyList(), 200L, stale = false))
        assertNull(authority.snapshot("another-owner"))
        assertEquals(listOf("kept"), authority.snapshot(owner)!!.teams.map(FootballTeam::id))
    }

    @Test
    fun `unknown provider time uses request order instead of the device receipt clock`() {
        val authority = FootballCatalogAuthority()
        checkNotNull(authority.bootstrap(owner, listOf(team("kept")), 0L))
        val request = authority.beginRequest(owner)

        val accepted = authority.publish(owner, request, listOf(team("new")), 0L, stale = false)

        assertNotNull(accepted)
        assertEquals(listOf("new"), authority.snapshot(owner)!!.teams.map(FootballTeam::id))
    }

    @Test
    fun `reset clears authority for a profile or discovery change`() {
        val authority = FootballCatalogAuthority()
        checkNotNull(authority.bootstrap(owner, emptyList(), 100L))
        authority.reset()

        assertNull(authority.snapshot(owner))
        assertNotNull(authority.bootstrap(owner, listOf(team("new")), 50L))
    }

    @Test
    fun `catalog cache is bound to its source and keeps receipt time separate`() {
        val context = TestAppContext()
        val store = FootballTeamCatalogStore(context)
        val sourceA = "https://source-a.example/hulk-operations/api/app/v1/sports/"
        val sourceB = "https://source-b.example/hulk-operations/api/app/v1/sports/"
        val sourceC = "https://source-c.example/hulk-operations/api/app/v1/sports/"

        store.save(
            catalogJson(cachedAtSeconds = 1_000L),
            5_000L,
            sourceA,
            FootballCatalogWriteOrder.next(),
        )
        val cachedA = checkNotNull(store.load(sourceA))
        assertEquals(1_000_000L, cachedA.cachedAtEpochMs)
        assertEquals(5_000L, cachedA.fetchedAtEpochMs)
        assertEquals(sourceA, cachedA.sourceKey)
        assertNull("a cache from another URL must not suppress the current fetch", store.load(sourceB))

        val preferences = context.getSharedPreferences("hulk_football_team_catalog_v1", 0) as FakeSharedPreferences
        preferences.edit().remove("source").commit()
        assertNull("a cache without a source cannot be adopted", store.load(sourceA))

        store.save(
            catalogJson(cachedAtSeconds = 0L, includeTimestamps = false),
            7_000L,
            sourceC,
            FootballCatalogWriteOrder.next(),
        )
        val unknownProviderTime = checkNotNull(store.load(sourceC))
        assertEquals(0L, unknownProviderTime.cachedAtEpochMs)
        assertEquals(7_000L, unknownProviderTime.fetchedAtEpochMs)
    }

    @Test
    fun `production publication and persistence drop delayed old work across empty nonempty and source switch`() =
        runBlocking {
            val authority = FootballCatalogAuthority()
            val persisted = mutableListOf<String>()
            val gate = CompletableDeferred<Unit>()
            val oldRequest = authority.beginRequest(owner)
            val oldCompletion = async {
                gate.await()
                authority.publishAndPersist(
                    ownerKey = owner,
                    requestId = oldRequest,
                    teams = listOf(team("old")),
                    cachedAtEpochMs = 900L,
                    stale = false,
                ) { persisted += it.teams.map(FootballTeam::id).joinToString(",") }
            }
            yield()

            val emptyRequest = authority.beginRequest(owner)
            val empty = authority.publishAndPersist(
                ownerKey = owner,
                requestId = emptyRequest,
                teams = emptyList(),
                cachedAtEpochMs = 100L,
                stale = false,
            ) { persisted += "empty" }
            assertNotNull(empty)

            val match = FootballMatch(
                providerEventId = "e1",
                competition = FootballCompetition("17", "Saudi Pro League"),
                homeTeam = FootballTeam("262", "Al Hilal"),
                awayTeam = FootballTeam("267", "Al Nassr"),
                kickoffEpochMs = 1_795_040_600_000L,
                status = FootballMatchStatus.UPCOMING,
            )
            val favorite = FootballFavoriteTeam("333", "Followed", 1L, team("333"))
            var state = FootballFeedUiState(
                available = true,
                snapshot = FootballFeedSnapshot(
                    dayKey = "2026-11-19",
                    generatedAtEpochMs = 500L,
                    cachedAtEpochMs = 500L,
                    stale = false,
                    retryAfterSeconds = null,
                    refreshSeconds = 60,
                    matches = listOf(match),
                    teams = listOf(team("old")),
                ),
                favoriteTeams = listOf(favorite),
                knownTeams = listOf(team("old")),
            )
            state = state.withCatalogSource(empty, owner)
            assertTrue(state.authoritativeCatalog!!.isEmpty())
            assertEquals(
                setOf("262", "267", "333"),
                state.knownTeams.mapTo(linkedSetOf(), FootballTeam::id),
            )

            val nonEmptyRequest = authority.beginRequest(owner)
            val nonEmpty = authority.publishAndPersist(
                ownerKey = owner,
                requestId = nonEmptyRequest,
                teams = listOf(team("new")),
                cachedAtEpochMs = 200L,
                stale = false,
            ) { persisted += "new" }
            state = state.withCatalogSource(nonEmpty, owner)
            assertEquals(listOf("new"), state.authoritativeCatalog!!.map(FootballTeam::id))
            assertTrue("old" !in state.knownTeams.map(FootballTeam::id))

            gate.complete(Unit)
            assertNull("delayed old work must not be published", oldCompletion.await())
            assertEquals(listOf("new"), authority.snapshot(owner)!!.teams.map(FootballTeam::id))
            assertEquals(listOf("empty", "new"), persisted)

            val sourceB = "account\u0000profile\u0000https://other.example/hulk-operations/api/app/v1/sports/"
            val switched = state.withCatalogSource(authority.snapshot(sourceB), sourceB)
            assertNull("a source switch must not retain the previous authority", switched.authoritativeCatalog)
            assertNull(switched.authoritativeCatalogSourceKey)
            assertEquals(
                setOf("262", "267", "333"),
                switched.knownTeams.mapTo(linkedSetOf(), FootballTeam::id),
            )
        }

    private fun catalogJson(cachedAtSeconds: Long, includeTimestamps: Boolean = true): String {
        val timestamps = if (includeTimestamps) {
            """"generatedAt":$cachedAtSeconds,"cachedAt":$cachedAtSeconds,"""
        } else {
            ""
        }
        return """
            {"schemaVersion":1,$timestamps"stale":false,"complete":true,
             "allowlistHash":"$FOOTBALL_CATALOG_ALLOWLIST_HASH",
             "teams":[{"id":"262","name":"Al Hilal","competitionIds":["17"]}]}
        """.trimIndent()
    }
}
