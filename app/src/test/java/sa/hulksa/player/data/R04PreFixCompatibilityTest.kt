package sa.hulksa.player.data

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.OfflineStatus

/**
 * R04 snapshot-boundary regression that compiles against reviewed R03 and the R04 candidate.
 */
class R04PreFixCompatibilityTest {
    @Test
    fun `snapshot spanning rollback must be rejected after failed commit finishes`() = runBlocking {
        val fixture = Fixture()
        val repository = fixture.repository()
        val reader = DurableDownloadPreferenceStore(fixture.context, ACCOUNT_ID)
        fixture.preferences.commitResult = false
        val tentative = CountDownLatch(1)
        val releaseWriter = CountDownLatch(1)
        fixture.preferences.afterMemoryMutationBeforeCommit = {
            fixture.preferences.afterMemoryMutationBeforeCommit = null
            tentative.countDown()
            releaseWriter.await(5, TimeUnit.SECONDS)
        }
        val pause = async(Dispatchers.Default) { repository.pause(1L) }
        assertTrue(tentative.await(5, TimeUnit.SECONDS))

        val captured = CountDownLatch(1)
        val releaseReader = CountDownLatch(1)
        fixture.preferences.afterStringRead = { key ->
            if (key == KEY_DOWNLOADS) {
                fixture.preferences.afterStringRead = null
                captured.countDown()
                releaseReader.await(5, TimeUnit.SECONDS)
            }
        }
        val readerWork = async(Dispatchers.Default) { reader.snapshot() }
        assertTrue(captured.await(5, TimeUnit.SECONDS))

        releaseWriter.countDown()
        assertFalse(pause.await().persisted)
        releaseReader.countDown()
        val straddling = readerWork.await()
        assertTrue(straddling.mutationInProgress)
        assertEquals(OfflineStatus.PAUSED, straddling.records.single().status)
    }

    private class Fixture {
        val preferences = FakeSharedPreferences()
        private val tempDirectory = Files.createTempDirectory("hulk-r04-prefix").toFile()
        val context = TestAppContext(
            preferences = mutableMapOf("hulk_downloads.account.$ACCOUNT_ID" to preferences),
            externalDir = tempDirectory,
        )

        init {
            val record = JSONObject()
                .put("downloadId", 1L)
                .put("historyKey", "movie:1")
                .put("title", "Movie 1")
                .put("streamKind", "movie")
                .put("streamId", 1)
                .put("extension", "mp4")
                .put("status", OfflineStatus.QUEUED.name)
                .put("ownerProfileIds", JSONArray(listOf("profile-1")))
                .put("createdAtEpochMs", 1_000L)
            preferences.edit()
                .putString(KEY_DOWNLOADS, JSONArray().put(record).toString())
                .putInt(
                    DurableDownloadPreferenceStore.KEY_STORAGE_VERSION,
                    DurableDownloadPreferenceStore.STORAGE_VERSION,
                )
                .apply()
        }

        fun repository(): DownloadRepository = DownloadRepository(
            context = context,
            accountId = ACCOUNT_ID,
            preferences = preferences,
            legacyOwners = { null },
        )
    }

    private companion object {
        const val ACCOUNT_ID = "account-a"
        const val KEY_DOWNLOADS = DurableDownloadPreferenceStore.KEY_DOWNLOADS
    }
}
