package sa.hulksa.player.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.OfflineStatus

class DownloadIndexIntegrityTest {
    @Test
    fun `valid records survive an incomplete record through initialize mutation and restart`() {
        val preferences = FakeSharedPreferences()
        val damagedRaw = JSONArray()
            .put(validRecord(downloadId = 1L, historyKey = "movie:1", owner = "kids-1"))
            .put(
                JSONObject()
                    .put("downloadId", 2L)
                    .put("historyKey", "series:2"),
            )
            .put(validRecord(downloadId = 3L, historyKey = "series:3", owner = "adult-1"))
            .toString()
        preferences.edit().putString(KEY_DOWNLOADS, damagedRaw).apply()
        preferences.edit()
            .putString("download_progress:1", progressJson(64L, 128L))
            .putString("download_progress:2", progressJson(32L, 256L))
            .putString("download_progress:3", progressJson(8L, 64L))
            .putString("download_progress:999", progressJson(16L, 512L))
            .apply()

        val store = DownloadIndexStore(preferences)
        val read = store.read()

        assertEquals(DownloadIndexState.PARTIAL_DAMAGE, read.state)
        assertEquals(listOf(1L, 3L), read.records.map { it.downloadId })
        assertEquals(setOf("kids-1"), read.records[0].ownerProfileIds)
        assertEquals(64L, read.records[0].bytesDownloaded)
        assertTrue(2L in read.retainedProgressDownloadIds)

        assertTrue(store.write(read.records, schedulingChanged = true, synchronous = true))
        assertNotNull(preferences.durableValue("download_progress:1"))
        assertNotNull(preferences.durableValue("download_progress:2"))
        assertNotNull(preferences.durableValue("download_progress:3"))
        assertNotNull(preferences.durableValue("download_progress:999"))
        assertTrue(
            preferences.durableKeys().any {
                it.startsWith(persistedRecoveryKeyPrefix(KEY_DOWNLOADS))
            },
        )

        val restarted = DownloadIndexStore(preferences.restart())
        val restored = restarted.read()
        assertEquals(DownloadIndexState.VALID, restored.state)
        assertEquals(listOf(1L, 3L), restored.records.map { it.downloadId })
        assertEquals(setOf("kids-1"), restored.records[0].ownerProfileIds)
        assertEquals(setOf("adult-1"), restored.records[1].ownerProfileIds)
        assertEquals(64L, restored.records[0].bytesDownloaded)
        assertEquals(128L, restored.records[0].totalBytes)
        assertNotNull(preferences.durableValue("download_progress:2"))
    }

    @Test
    fun `unreadable document is never rewritten as an empty index`() {
        val preferences = FakeSharedPreferences()
        val raw = "{\"downloads\":["
        preferences.edit().putString(KEY_DOWNLOADS, raw).apply()

        val store = DownloadIndexStore(preferences)
        assertEquals(DownloadIndexState.UNREADABLE, store.read().state)
        assertFalse(store.write(emptyList(), schedulingChanged = false, synchronous = true))
        assertEquals(raw, preferences.durableValue(KEY_DOWNLOADS))

        val restarted = DownloadIndexStore(preferences.restart())
        assertEquals(DownloadIndexState.UNREADABLE, restarted.read().state)
        assertFalse(restarted.write(emptyList(), schedulingChanged = false, synchronous = true))
        assertEquals(raw, preferences.durableValue(KEY_DOWNLOADS))
        assertTrue(
            preferences.durableKeys().none {
                it.startsWith(persistedRecoveryKeyPrefix(KEY_DOWNLOADS))
            },
        )
    }

    @Test
    fun `present but invalid records do not become authoritative empty data`() {
        val preferences = FakeSharedPreferences()
        val raw = JSONArray()
            .put(JSONObject().put("downloadId", 7L))
            .toString()
        preferences.edit().putString(KEY_DOWNLOADS, raw).apply()

        val store = DownloadIndexStore(preferences)
        val read = store.read()

        assertEquals(DownloadIndexState.UNREADABLE, read.state)
        assertTrue(read.records.isEmpty())
        assertTrue(7L in read.retainedProgressDownloadIds)
        assertFalse(store.write(emptyList(), schedulingChanged = false, synchronous = true))
        assertEquals(raw, preferences.durableValue(KEY_DOWNLOADS))
    }

    @Test
    fun `missing and valid empty collections remain normalizable`() {
        val missing = FakeSharedPreferences()
        val missingStore = DownloadIndexStore(missing)
        assertEquals(DownloadIndexState.MISSING, missingStore.read().state)
        assertTrue(missingStore.write(emptyList(), schedulingChanged = false, synchronous = true))
        assertEquals("[]", missing.durableValue(KEY_DOWNLOADS))

        val empty = FakeSharedPreferences()
        empty.edit().putString(KEY_DOWNLOADS, "[]").apply()
        val emptyStore = DownloadIndexStore(empty)
        assertEquals(DownloadIndexState.VALID, emptyStore.read().state)
        assertTrue(emptyStore.write(emptyList(), schedulingChanged = false, synchronous = true))
    }

    @Test
    fun `failed recovery preservation rejects the repair and leaves durable bytes unchanged`() {
        val preferences = FakeSharedPreferences()
        val damagedRaw = JSONArray()
            .put(validRecord(downloadId = 1L, historyKey = "movie:1", owner = "profile-1"))
            .put(JSONObject().put("downloadId", 2L))
            .toString()
        preferences.edit().putString(KEY_DOWNLOADS, damagedRaw).apply()
        val store = DownloadIndexStore(preferences)
        val read = store.read()
        assertEquals(DownloadIndexState.PARTIAL_DAMAGE, read.state)

        preferences.commitResult = false
        assertFalse(store.write(read.records, schedulingChanged = false, synchronous = true))
        assertEquals(damagedRaw, preferences.durableValue(KEY_DOWNLOADS))

        val restartedPreferences = preferences.restart()
        val restarted = DownloadIndexStore(restartedPreferences)
        assertEquals(DownloadIndexState.PARTIAL_DAMAGE, restarted.read().state)
        assertEquals(damagedRaw, restartedPreferences.durableValue(KEY_DOWNLOADS))

        restartedPreferences.commitResult = true
        assertTrue(restarted.write(restarted.read().records, schedulingChanged = false, synchronous = true))
        assertNotNull(restarted.read().safeRawDocument)
        assertTrue(
            restartedPreferences.durableKeys().any {
                it.startsWith(persistedRecoveryKeyPrefix(KEY_DOWNLOADS))
            },
        )
    }

    @Test
    fun `repair write failure leaves the original durable until a later successful repair`() {
        val preferences = FakeSharedPreferences()
        val damagedRaw = JSONArray()
            .put(validRecord(downloadId = 1L, historyKey = "movie:1", owner = "profile-1"))
            .put(JSONObject().put("downloadId", 2L))
            .toString()
        preferences.edit().putString(KEY_DOWNLOADS, damagedRaw).apply()
        val store = DownloadIndexStore(preferences)
        val read = store.read()
        assertEquals(DownloadIndexState.PARTIAL_DAMAGE, read.state)

        preferences.commitResults.addAll(listOf(true, false))
        assertFalse(store.write(read.records, schedulingChanged = false, synchronous = true))
        assertEquals(damagedRaw, preferences.durableValue(KEY_DOWNLOADS))
        assertTrue(
            preferences.durableKeys().any {
                it.startsWith(persistedRecoveryKeyPrefix(KEY_DOWNLOADS))
            },
        )

        assertTrue(store.write(read.records, schedulingChanged = false, synchronous = true))
        val restarted = DownloadIndexStore(preferences.restart()).read()
        assertEquals(listOf(1L), restarted.records.map { it.downloadId })
        assertEquals(setOf("profile-1"), restarted.records.single().ownerProfileIds)
    }

    @Test
    fun `missing canonical index with residual progress is established and never rewritten`() {
        val preferences = FakeSharedPreferences()
        preferences.edit()
            .putInt(DurableDownloadPreferenceStore.KEY_STORAGE_VERSION, DurableDownloadPreferenceStore.STORAGE_VERSION)
            .putString("download_progress:42", progressJson(128L, 512L))
            .apply()

        val store = DownloadIndexStore(preferences)
        val read = store.read()

        assertEquals(DownloadIndexState.MISSING_ESTABLISHED, read.state)
        assertFalse(read.state.allowsIndexWrite())
        assertFalse(store.write(emptyList(), schedulingChanged = false, synchronous = true))
        assertNull(preferences.durableValue(KEY_DOWNLOADS))
        assertNotNull(preferences.durableValue("download_progress:42"))
        assertEquals(
            DurableDownloadPreferenceStore.STORAGE_VERSION,
            preferences.durableValue(DurableDownloadPreferenceStore.KEY_STORAGE_VERSION),
        )

        val restarted = DownloadIndexStore(preferences.restart())
        assertEquals(DownloadIndexState.MISSING_ESTABLISHED, restarted.read().state)
        assertFalse(restarted.write(emptyList(), schedulingChanged = false, synchronous = true))
        assertNull(preferences.durableValue(KEY_DOWNLOADS))
        assertNotNull(preferences.durableValue("download_progress:42"))
    }

    @Test
    fun `failed index commit restores preference memory before a later apply and recreation`() {
        val preferences = FakeSharedPreferences()
        val first = validRecord(downloadId = 1L, historyKey = "movie:1", owner = "profile-1")
        preferences.edit()
            .putString(KEY_DOWNLOADS, JSONArray().put(first).toString())
            .putInt(DurableDownloadPreferenceStore.KEY_STORAGE_VERSION, DurableDownloadPreferenceStore.STORAGE_VERSION)
            .putString("download_progress:1", progressJson(64L, 128L))
            .apply()
        val before = preferences.getAll().toMap()
        val store = DownloadIndexStore(preferences)
        val oldRecords = store.read().records
        val changedRecords = oldRecords.map {
            it.copy(ownerProfileIds = it.ownerProfileIds + "profile-2")
        }

        preferences.commitResult = false
        assertFalse(store.write(changedRecords, schedulingChanged = true, synchronous = true))
        assertEquals(before, preferences.getAll())
        assertEquals(oldRecords, DownloadIndexStore(preferences).read().records)

        preferences.commitResult = true
        preferences.edit().putString("unrelated_setting", "applied").apply()
        val restarted = DownloadIndexStore(preferences.restart()).read()
        assertEquals(DownloadIndexState.VALID, restarted.state)
        assertEquals(oldRecords.map { it.downloadId }, restarted.records.map { it.downloadId })
        assertEquals(setOf("profile-1"), restarted.records.single().ownerProfileIds)
        assertEquals("applied", preferences.durableValue("unrelated_setting"))
    }

    @Test
    fun `current format record without owners is not assigned to the current profile`() {
        val ownerless = OfflineDownloadSeed(
            downloadId = 9L,
            historyKey = "movie:9",
            ownerProfileIds = emptySet(),
        ).toDownload()

        val restored = reconcilePersistedDownloadOwnership(
            downloads = listOf(ownerless),
            useLegacyOwnership = false,
            legacyOwners = { setOf("profile-1") },
        )

        assertTrue(restored.single().ownerProfileIds.isEmpty())
    }

    private fun validRecord(downloadId: Long, historyKey: String, owner: String): JSONObject =
        JSONObject()
            .put("downloadId", downloadId)
            .put("historyKey", historyKey)
            .put("title", "Synthetic $downloadId")
            .put("streamKind", if (historyKey.startsWith("movie")) "movie" else "series")
            .put("streamId", downloadId)
            .put("extension", "mp4")
            .put("status", OfflineStatus.DOWNLOADING.name)
            .put("bytesDownloaded", if (downloadId == 1L) 64L else 8L)
            .put("totalBytes", if (downloadId == 1L) 128L else 64L)
            .put("createdAtEpochMs", 1_000L)
            .put("ownerProfileIds", JSONArray(listOf(owner)))

    private fun progressJson(downloaded: Long, total: Long): String = JSONObject()
        .put("bytesDownloaded", downloaded)
        .put("totalBytes", total)
        .put("bytesPerSecond", 0L)
        .put("etaSeconds", -1L)
        .toString()

    private data class OfflineDownloadSeed(
        val downloadId: Long,
        val historyKey: String,
        val ownerProfileIds: Set<String>,
    ) {
        fun toDownload() = sa.hulksa.player.model.OfflineDownload(
            downloadId = downloadId,
            historyKey = historyKey,
            title = historyKey,
            posterUrl = null,
            streamKind = "movie",
            streamId = downloadId.toInt(),
            extension = "mp4",
            ownerProfileIds = ownerProfileIds,
        )
    }

    private companion object {
        const val KEY_DOWNLOADS = "downloads"
    }
}
