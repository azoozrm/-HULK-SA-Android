package sa.hulksa.player.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R05.3 independent-review regression: a canceled catalog save that is already running on the IO
 * dispatcher must never overwrite a newer verified catalog, including an empty one and across a
 * discovery-source switch.
 *
 * The persistence path here is the production one: [FootballCatalogAuthority.publishAndPersist]
 * accepts the response, then the production persist block enters `Dispatchers.IO` and writes through
 * the real [FootballTeamCatalogStore]. The old block is held on a blocking latch, so coroutine
 * cancellation marks the job but cannot stop the already-running IO work.
 */
class FootballCatalogR053Test {
    private val urlA = "https://source-a.example/hulk-operations/api/app/v1/sports/"
    private val urlB = "https://source-b.example/hulk-operations/api/app/v1/sports/"
    private val ownerA = "account\u0000profile\u0000$urlA"
    private val ownerB = "account\u0000profile\u0000$urlB"

    private fun team(id: String) = FootballTeam(id, "Team $id", competitionIds = setOf("17"))

    private fun catalogJson(teams: List<FootballTeam>, cachedAtSeconds: Long): String = """
        {"schemaVersion":1,"generatedAt":$cachedAtSeconds,"cachedAt":$cachedAtSeconds,
         "stale":false,"complete":true,"allowlistHash":"$FOOTBALL_CATALOG_ALLOWLIST_HASH",
         "teams":[${teams.joinToString(",") { team ->
        """{"id":"${team.id}","name":"${team.name}","competitionIds":["17"]}"""
    }}]}
    """.trimIndent()

    @Test
    fun `canceled same-url save already running on IO cannot overwrite the newer saved catalog`() =
        runBlocking {
            val store = FootballTeamCatalogStore(TestAppContext())
            val authority = FootballCatalogAuthority()
            val oldEntered = CountDownLatch(1)
            val releaseOld = CountDownLatch(1)

            val oldRequest = authority.beginRequest(ownerA)
            val oldJob = async(Dispatchers.IO) {
                authority.publishAndPersist(
                    ownerKey = ownerA,
                    requestId = oldRequest,
                    teams = listOf(team("262")),
                    cachedAtEpochMs = 900_000L,
                    stale = false,
                ) { snapshot ->
                    withContext(Dispatchers.IO) {
                        oldEntered.countDown()
                        releaseOld.await(10, TimeUnit.SECONDS)
                        store.save(catalogJson(snapshot.teams, 900L), 9_000L, urlA, snapshot.writeToken)
                    }
                }
            }
            assertTrue("old save entered the IO dispatcher", oldEntered.await(10, TimeUnit.SECONDS))
            oldJob.cancel()

            val newRequest = authority.beginRequest(ownerA)
            val accepted = authority.publishAndPersist(
                ownerKey = ownerA,
                requestId = newRequest,
                teams = listOf(team("267")),
                cachedAtEpochMs = 1_000_000L,
                stale = false,
            ) { snapshot ->
                withContext(Dispatchers.IO) {
                    store.save(catalogJson(snapshot.teams, 1_000L), 10_000L, urlA, snapshot.writeToken)
                }
            }
            assertNotNull("newer catalog is accepted", accepted)

            releaseOld.countDown()
            oldJob.join()

            val saved = checkNotNull(store.load(urlA))
            assertEquals(listOf("267"), saved.teams.map(FootballTeam::id))
            assertEquals(1_000_000L, saved.cachedAtEpochMs)
            assertEquals(10_000L, saved.fetchedAtEpochMs)
            assertEquals(
                listOf("267"),
                authority.snapshot(ownerA)!!.teams.map(FootballTeam::id),
            )
        }

    @Test
    fun `canceled source-A save cannot erase the newer valid empty source-B catalog`() = runBlocking {
        val store = FootballTeamCatalogStore(TestAppContext())
        val authority = FootballCatalogAuthority()
        val oldEntered = CountDownLatch(1)
        val releaseOld = CountDownLatch(1)

        val oldRequest = authority.beginRequest(ownerA)
        val oldJob = async(Dispatchers.IO) {
            authority.publishAndPersist(
                ownerKey = ownerA,
                requestId = oldRequest,
                teams = listOf(team("262")),
                cachedAtEpochMs = 900_000L,
                stale = false,
            ) { snapshot ->
                withContext(Dispatchers.IO) {
                    oldEntered.countDown()
                    releaseOld.await(10, TimeUnit.SECONDS)
                    store.save(catalogJson(snapshot.teams, 900L), 9_000L, urlA, snapshot.writeToken)
                }
            }
        }
        assertTrue("old source-A save is running", oldEntered.await(10, TimeUnit.SECONDS))
        oldJob.cancel()

        val newRequest = authority.beginRequest(ownerB)
        val accepted = authority.publishAndPersist(
            ownerKey = ownerB,
            requestId = newRequest,
            teams = emptyList(),
            cachedAtEpochMs = 1_200_000L,
            stale = false,
        ) { snapshot ->
            withContext(Dispatchers.IO) {
                store.save(catalogJson(snapshot.teams, 1_200L), 11_000L, urlB, snapshot.writeToken)
            }
        }
        assertNotNull("newer empty source-B catalog is accepted", accepted)
        assertNotNull("source B saves its valid empty catalog", store.load(urlB))

        releaseOld.countDown()
        oldJob.join()

        val savedB = store.load(urlB)
        val savedA = store.load(urlA)
        assertNotNull(
            "the newer source-B catalog must remain durable after the canceled source-A write " +
                "(source A currently reads ${savedA?.teams?.map(FootballTeam::id)})",
            savedB,
        )
        val durableB = checkNotNull(savedB)
        assertTrue("the newer empty catalog remains durable", durableB.teams.isEmpty())
        assertEquals(urlB, durableB.sourceKey)
        assertEquals(1_200_000L, durableB.cachedAtEpochMs)
        assertEquals(11_000L, durableB.fetchedAtEpochMs)
        assertNull("the canceled source-A write must not replace source B", store.load(urlA))
        assertTrue(authority.snapshot(ownerB)!!.teams.isEmpty())

        val match = FootballMatch(
            providerEventId = "e1",
            competition = FootballCompetition("17", "Saudi Pro League"),
            homeTeam = FootballTeam("262", "Al Hilal"),
            awayTeam = FootballTeam("267", "Al Nassr"),
            kickoffEpochMs = 1_795_040_600_000L,
            status = FootballMatchStatus.UPCOMING,
        )
        val favorite = FootballFavoriteTeam("333", "Followed", 1L, team("333"))
        val afterSwitch = FootballFeedUiState(
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
            authoritativeCatalog = listOf(team("262")),
            authoritativeCatalogSourceKey = ownerA,
        ).withCatalogSource(authority.snapshot(ownerB), ownerB)

        assertNotNull("an empty catalog is still a valid authority", afterSwitch.authoritativeCatalog)
        assertTrue(afterSwitch.authoritativeCatalog!!.isEmpty())
        assertEquals(ownerB, afterSwitch.authoritativeCatalogSourceKey)
        assertEquals(
            setOf("262", "267", "333"),
            afterSwitch.knownTeams.mapTo(linkedSetOf(), FootballTeam::id),
        )
    }
}
