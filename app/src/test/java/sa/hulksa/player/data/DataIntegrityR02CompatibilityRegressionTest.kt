package sa.hulksa.player.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioral regressions that compile against both the reviewed R01 source and the corrected R02
 * source. Used to prove the pre-fix failures, not merely a compile difference.
 */
class DataIntegrityR02CompatibilityRegressionTest {
    @Test
    fun `malformed credential download is not sanitized to valid empty`() {
        val malformed = "not-json http://provider.test/series/alpha/secret/99.mkv"
        assertEquals(malformed, sanitizePersistedDownloadJson(malformed))
    }

    @Test
    fun `malformed credential history is not sanitized to valid empty`() {
        val malformed = "not-json http://provider.test/series/alpha/secret/99.mkv"
        assertEquals(malformed, sanitizeHistoryDocumentJson(malformed))
        val preferences = FakeSharedPreferences()
        val key = "profile:kids-1:history"
        preferences.edit().putString(key, malformed).apply()
        assertEquals(
            HistoryDocumentState.UNREADABLE,
            readHistoryDocument(preferences, key).state,
        )
    }

    @Test
    fun `missing index with residual progress is not treated as a new store`() {
        val preferences = FakeSharedPreferences()
        preferences.edit()
            .putInt(
                DurableDownloadPreferenceStore.KEY_STORAGE_VERSION,
                DurableDownloadPreferenceStore.STORAGE_VERSION,
            )
            .putString("download_progress:42", "{}")
            .apply()

        val store = DownloadIndexStore(preferences)
        val read = store.read()
        assertNotEquals(DownloadIndexState.MISSING, read.state)
        assertFalse(store.write(emptyList(), schedulingChanged = false, synchronous = true))
        assertEquals(null, preferences.durableValue("downloads"))
        assertTrue(preferences.durableValue("download_progress:42") != null)
    }

    @Test
    fun `failed index commit does not leave rejected memory`() {
        val preferences = FakeSharedPreferences()
        val record = JSONObject()
            .put("downloadId", 1L)
            .put("historyKey", "movie:1")
            .put("title", "Movie 1")
            .put("streamKind", "movie")
            .put("streamId", 1)
            .put("extension", "mp4")
            .put("status", "QUEUED")
            .put("ownerProfileIds", JSONArray(listOf("profile-1")))
            .put("createdAtEpochMs", 1_000L)
        preferences.edit()
            .putString("downloads", JSONArray().put(record).toString())
            .putInt(
                DurableDownloadPreferenceStore.KEY_STORAGE_VERSION,
                DurableDownloadPreferenceStore.STORAGE_VERSION,
            )
            .apply()
        val before = preferences.getAll().toMap()
        val store = DownloadIndexStore(preferences)
        val records = store.read().records
        preferences.commitResult = false

        assertFalse(
            store.write(
                records.map { it.copy(ownerProfileIds = setOf("profile-2")) },
                schedulingChanged = true,
                synchronous = true,
            ),
        )
        assertEquals(before, preferences.getAll())
    }

    @Test
    fun `recovery ring does not accept an evicted payload as preserved`() {
        val preferences = FakeSharedPreferences()
        val key = "profile:kids-1:history"
        fun raw(position: Long): String = JSONArray()
            .put(
                JSONObject()
                    .put("key", "movie:1")
                    .put("title", "Movie 1")
                    .put("poster", "")
                    .put("kind", "movie")
                    .put("id", 1)
                    .put("extension", "mp4")
                    .put("live", false)
                    .put("position", position)
                    .put("duration", 60_000L)
                    .put("updated", position),
            )
            .put(JSONObject().put("key", "broken"))
            .toString()

        fun repair(payload: String) {
            preferences.edit().putString(key, payload).apply()
            val read = readHistoryDocument(preferences, key)
            assertEquals(HistoryDocumentState.PARTIAL_DAMAGE, read.state)
            assertTrue(writeHistoryDocument(preferences, key, read.entries, read))
        }

        val a = raw(1_000L)
        repair(a)
        repair(raw(2_000L))
        repair(raw(3_000L))
        repair(a)

        val slots = preferences.durableSnapshot().filterKeys {
            it.startsWith(persistedRecoveryKeyPrefix(key)) && !it.endsWith("__next_slot__")
        }
        assertTrue(a in slots.values)
    }
}
