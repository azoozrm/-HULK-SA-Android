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
 * R05.4 independent-review regression: write ordering must come from the accepted catalog
 * request/owner generation, not from the device receipt clock. The old save is already blocked on
 * the IO dispatcher when its owner is canceled; the newer catalog is saved before the old write is
 * released.
 *
 * Receipt timestamps are deliberately equal or rolled back between the two requests. The provider
 * timestamps and request order remain distinct.
 */
class FootballCatalogR055Test {
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

    private fun runScenario(
        switchSource: Boolean,
        oldFetchedAt: Long,
        newFetchedAt: Long,
        resetAuthority: Boolean = false,
    ) = runBlocking {
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
                    store.save(catalogJson(snapshot.teams, 900L), oldFetchedAt, urlA, snapshot.writeToken)
                }
            }
        }
        assertTrue("old save entered the IO dispatcher", oldEntered.await(10, TimeUnit.SECONDS))
        oldJob.cancel()
        if (resetAuthority) authority.reset()

        val newOwner = if (switchSource) ownerB else ownerA
        val newUrl = if (switchSource) urlB else urlA
        val newTeams = if (switchSource) emptyList() else listOf(team("267"))
        val newRequest = authority.beginRequest(newOwner)
        val accepted = authority.publishAndPersist(
            ownerKey = newOwner,
            requestId = newRequest,
            teams = newTeams,
            cachedAtEpochMs = 1_200_000L,
            stale = false,
        ) { snapshot ->
            withContext(Dispatchers.IO) {
                store.save(catalogJson(snapshot.teams, 1_200L), newFetchedAt, newUrl, snapshot.writeToken)
            }
        }
        assertNotNull("newer catalog is accepted", accepted)

        releaseOld.countDown()
        oldJob.join()

        if (switchSource) {
            val savedB = store.load(urlB)
            val savedA = store.load(urlA)
            assertNotNull(
                "the newer source-B catalog must survive the canceled source-A write " +
                    "(source A currently reads ${savedA?.teams?.map(FootballTeam::id)})",
                savedB,
            )
            assertTrue("source B remains a valid empty catalog", savedB!!.teams.isEmpty())
            assertEquals(urlB, savedB.sourceKey)
            assertNull("source A must not be readable after the switch", savedA)
        } else {
            val savedA = store.load(urlA)
            assertNotNull("the newer same-source catalog must survive", savedA)
            assertEquals(listOf("267"), savedA!!.teams.map(FootballTeam::id))
        }
        assertEquals(
            newTeams.map(FootballTeam::id),
            authority.snapshot(newOwner)!!.teams.map(FootballTeam::id),
        )
    }

    @Test
    fun `same URL with an equal receipt clock keeps the newer catalog`() =
        runScenario(switchSource = false, oldFetchedAt = 10_000L, newFetchedAt = 10_000L)

    @Test
    fun `same URL with a rolled-back receipt clock keeps the newer catalog`() =
        runScenario(switchSource = false, oldFetchedAt = 10_000L, newFetchedAt = 9_000L)

    @Test
    fun `A-to-B empty catalog with an equal receipt clock survives the old write`() =
        runScenario(switchSource = true, oldFetchedAt = 10_000L, newFetchedAt = 10_000L)

    @Test
    fun `A-to-B empty catalog with a rolled-back receipt clock survives the old write`() =
        runScenario(switchSource = true, oldFetchedAt = 10_000L, newFetchedAt = 9_000L)

    @Test
    fun `authority reset cannot reuse an older write generation`() =
        runScenario(
            switchSource = true,
            oldFetchedAt = 10_000L,
            newFetchedAt = 10_000L,
            resetAuthority = true,
        )
}
