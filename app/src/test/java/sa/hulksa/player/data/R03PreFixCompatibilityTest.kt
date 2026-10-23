package sa.hulksa.player.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.OfflineStatus

/**
 * R03 behavioral regressions that compile against reviewed R02 and the R03 candidate.
 */
class R03PreFixCompatibilityTest {
    private val credentialString = "http://provider.test/series/alpha/secret/99.mkv"

    @Test
    fun `partial history with credential string is not copied into recovery`() {
        val preferences = FakeSharedPreferences()
        val key = "profile:kids-1:history"
        val raw = JSONArray()
            .put(historyRecord())
            .put(credentialString)
            .toString()
        preferences.edit().putString(key, raw).apply()

        val read = readHistoryDocument(preferences, key)
        assertEquals(HistoryDocumentState.PARTIAL_DAMAGE, read.state)
        assertFalse(writeHistoryDocument(preferences, key, read.entries, read))
        assertEquals(raw, preferences.durableValue(key))
        assertTrue(
            preferences.durableKeys().none {
                it.startsWith(persistedRecoveryKeyPrefix(key))
            },
        )
    }

    @Test
    fun `partial download with credential string is not copied into recovery`() {
        val preferences = FakeSharedPreferences()
        val raw = JSONArray()
            .put(downloadRecord())
            .put(credentialString)
            .toString()
        preferences.edit()
            .putString(DurableDownloadPreferenceStore.KEY_DOWNLOADS, raw)
            .putInt(
                DurableDownloadPreferenceStore.KEY_STORAGE_VERSION,
                DurableDownloadPreferenceStore.STORAGE_VERSION,
            )
            .apply()
        val store = DownloadIndexStore(preferences)
        val read = store.read()
        assertEquals(DownloadIndexState.PARTIAL_DAMAGE, read.state)
        assertFalse(store.write(read.records, schedulingChanged = false, synchronous = true))
        assertEquals(raw, preferences.durableValue(DurableDownloadPreferenceStore.KEY_DOWNLOADS))
        assertTrue(
            preferences.durableKeys().none {
                it.startsWith(
                    persistedRecoveryKeyPrefix(DurableDownloadPreferenceStore.KEY_DOWNLOADS),
                )
            },
        )
    }

    @Test
    fun `unsafe startup download document is not reported as successfully scrubbed`() {
        val preferences = FakeSharedPreferences()
        preferences.edit()
            .putString(
                DurableDownloadPreferenceStore.KEY_DOWNLOADS,
                "not-json $credentialString",
            )
            .apply()
        assertFalse(scrubPersistedDownloadCredentialDocument(preferences))
    }

    @Test
    fun `history mutation monitor is not held across blocking write`() = runBlocking {
        val gate = UserLibraryHistoryMutationGate()
        val progress = gate.beginProgress()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writer = async(Dispatchers.Default) {
            gate.writeIfCurrent(progress) {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                true
            }
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val mutation = async(Dispatchers.Default) { gate.beginHistoryMutation() }
        val completedWhileIoBlocked = withTimeoutOrNull(1_000L) { mutation.await() } != null
        release.countDown()
        writer.await()
        assertTrue(completedWhileIoBlocked)
    }

    @Test
    fun `recovery-only download state is not treated as a new store`() {
        val preferences = FakeSharedPreferences()
        assertTrue(
            preserveDamagedPersistedDocument(
                preferences,
                DurableDownloadPreferenceStore.KEY_DOWNLOADS,
                "damaged-index",
            ),
        )
        val store = DownloadIndexStore(preferences)
        assertNotEquals(DownloadIndexState.MISSING, store.read().state)
        assertFalse(store.write(emptyList(), schedulingChanged = false, synchronous = true))
    }

    private fun historyRecord(): JSONObject = JSONObject()
        .put("key", "movie:1")
        .put("title", "Movie 1")
        .put("poster", "")
        .put("kind", "movie")
        .put("id", 1)
        .put("extension", "mp4")
        .put("live", false)
        .put("position", 4_000L)
        .put("duration", 60_000L)
        .put("updated", 4_000L)

    private fun downloadRecord(): JSONObject = JSONObject()
        .put("downloadId", 1L)
        .put("historyKey", "movie:1")
        .put("title", "Movie 1")
        .put("streamKind", "movie")
        .put("streamId", 1)
        .put("extension", "mp4")
        .put("status", OfflineStatus.QUEUED.name)
        .put("ownerProfileIds", JSONArray(listOf("profile-1")))
        .put("createdAtEpochMs", 1_000L)
}
