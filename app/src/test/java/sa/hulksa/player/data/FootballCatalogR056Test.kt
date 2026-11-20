package sa.hulksa.player.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R05.5 independent-review regression: two production store instances share one preferences file.
 * Their per-instance locks do not serialize the generation comparison and commit, so an older
 * write can pass the comparison, wait at `commit`, let the newer write land, then replace it.
 *
 * The old write is paused by the shared preference file inside `commit`, after the generation
 * comparison and before any mutation. The newer write is allowed to finish, then the old write is
 * released and a fresh store reloads the durable record.
 */
class FootballCatalogR056Test {
    private val urlA = "https://source-a.example/hulk-operations/api/app/v1/sports/"
    private val urlB = "https://source-b.example/hulk-operations/api/app/v1/sports/"

    private fun team(id: String) = FootballTeam(id, "Team $id", competitionIds = setOf("17"))

    private fun catalogJson(teams: List<FootballTeam>, cachedAtSeconds: Long): String = """
        {"schemaVersion":1,"generatedAt":$cachedAtSeconds,"cachedAt":$cachedAtSeconds,
         "stale":false,"complete":true,"allowlistHash":"$FOOTBALL_CATALOG_ALLOWLIST_HASH",
         "teams":[${teams.joinToString(",") { team ->
        """{"id":"${team.id}","name":"${team.name}","competitionIds":["17"]}"""
    }}]}
    """.trimIndent()

    private fun sharedPreferences(context: TestAppContext): FakeSharedPreferences =
        context.getSharedPreferences("hulk_football_team_catalog_v1", 0) as FakeSharedPreferences

    @Test
    fun `two stores on the same URL keep the newer generation after both commits`() = runBlocking {
        val context = TestAppContext()
        val preferences = sharedPreferences(context)
        val oldStore = FootballTeamCatalogStore(context)
        val newStore = FootballTeamCatalogStore(context)
        val oldCommitEntered = CountDownLatch(1)
        val releaseOld = CountDownLatch(1)
        val blockFirstCommit = AtomicBoolean(true)
        preferences.beforeCommit = {
            if (blockFirstCommit.compareAndSet(true, false)) {
                oldCommitEntered.countDown()
                releaseOld.await(10, TimeUnit.SECONDS)
            }
        }

        val oldJob = async(Dispatchers.IO) {
            oldStore.save(
                catalogJson(listOf(team("262")), 900L),
                10_000L,
                urlA,
                FootballCatalogWriteOrder.next(),
            )
        }
        assertTrue("old write reached commit", oldCommitEntered.await(10, TimeUnit.SECONDS))

        // The newer write may wait for the shared store lock; release the old commit first, then
        // join both jobs and assert the final durable record.
        val newStarted = CountDownLatch(1)
        val newJob = async(Dispatchers.IO) {
            newStarted.countDown()
            newStore.save(
                catalogJson(listOf(team("267")), 1_200L),
                10_000L,
                urlA,
                FootballCatalogWriteOrder.next(),
            )
        }
        assertTrue("new write started", newStarted.await(10, TimeUnit.SECONDS))

        releaseOld.countDown()
        oldJob.join()
        newJob.join()
        preferences.beforeCommit = null

        val reloaded = checkNotNull(FootballTeamCatalogStore(context).load(urlA))
        assertEquals(
            "the newer catalog must remain after both commits",
            listOf("267"),
            reloaded.teams.map(FootballTeam::id),
        )
        assertEquals(10_000L, reloaded.fetchedAtEpochMs)
    }

    @Test
    fun `two stores keep a valid empty B catalog after an A-to-B switch`() = runBlocking {
        val context = TestAppContext()
        val preferences = sharedPreferences(context)
        val oldStore = FootballTeamCatalogStore(context)
        val newStore = FootballTeamCatalogStore(context)
        val oldCommitEntered = CountDownLatch(1)
        val releaseOld = CountDownLatch(1)
        val blockFirstCommit = AtomicBoolean(true)
        preferences.beforeCommit = {
            if (blockFirstCommit.compareAndSet(true, false)) {
                oldCommitEntered.countDown()
                releaseOld.await(10, TimeUnit.SECONDS)
            }
        }

        val oldJob = async(Dispatchers.IO) {
            oldStore.save(
                catalogJson(listOf(team("262")), 900L),
                10_000L,
                urlA,
                FootballCatalogWriteOrder.next(),
            )
        }
        assertTrue("old write reached commit", oldCommitEntered.await(10, TimeUnit.SECONDS))

        val newStarted = CountDownLatch(1)
        val newJob = async(Dispatchers.IO) {
            newStarted.countDown()
            newStore.save(
                catalogJson(emptyList(), 1_200L),
                10_000L,
                urlB,
                FootballCatalogWriteOrder.next(),
            )
        }
        assertTrue("new write started", newStarted.await(10, TimeUnit.SECONDS))

        releaseOld.countDown()
        oldJob.join()
        newJob.join()
        preferences.beforeCommit = null

        val reloadedB = FootballTeamCatalogStore(context).load(urlB)
        assertNotNull(
            "the valid empty B catalog must remain after both commits",
            reloadedB,
        )
        assertTrue(reloadedB!!.teams.isEmpty())
        assertEquals(urlB, reloadedB.sourceKey)
        assertNull("the old source-A write must not win", FootballTeamCatalogStore(context).load(urlA))
    }
}
