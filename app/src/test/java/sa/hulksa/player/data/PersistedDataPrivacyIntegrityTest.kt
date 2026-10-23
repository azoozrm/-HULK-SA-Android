package sa.hulksa.player.data

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.security.containsCredentialBearingIptvMaterial
import sa.hulksa.player.model.OfflineStatus

class PersistedDataPrivacyIntegrityTest {
    private val malformedWithCredentials =
        "not-json http://provider.test/series/alpha/secret/99.mkv"

    private companion object {
        const val ACCOUNT_ID = "account-a"
    }

    @Test
    fun `malformed credential bearing download document is never replaced by startup scrub`() = runBlocking {
        val preferences = FakeSharedPreferences()
        preferences.edit()
            .putString(DurableDownloadPreferenceStore.KEY_DOWNLOADS, malformedWithCredentials)
            .apply()

        assertTrue(containsCredentialBearingIptvMaterial(malformedWithCredentials))
        val result = runDurableDownloadStartupMaintenance(
            scrubPersistedCredentials = {
                scrubPersistedDownloadCredentialDocument(preferences)
            },
            captureLegacyOwner = { true },
        )

        assertEquals(DurableDownloadStartupMaintenanceResult.CREDENTIAL_SCRUB_FAILED, result)
        assertEquals(
            malformedWithCredentials,
            preferences.durableValue(DurableDownloadPreferenceStore.KEY_DOWNLOADS),
        )

        val index = DownloadIndexStore(preferences).read()
        assertEquals(DownloadIndexState.UNREADABLE, index.state)
        assertNull(index.safeRawDocument)
        assertFalse(DownloadIndexStore(preferences).write(emptyList(), false, true))
        assertEquals(
            malformedWithCredentials,
            preferences.durableValue(DurableDownloadPreferenceStore.KEY_DOWNLOADS),
        )
        val restarted = preferences.restart()
        assertEquals(DownloadIndexState.UNREADABLE, DownloadIndexStore(restarted).read().state)
        assertEquals(
            malformedWithCredentials,
            restarted.durableValue(DurableDownloadPreferenceStore.KEY_DOWNLOADS),
        )
    }

    @Test
    fun `malformed credential bearing history is unreadable and never becomes valid empty`() {
        val preferences = FakeSharedPreferences()
        val historyKey = "profile:kids-1:history"
        preferences.edit().putString(historyKey, malformedWithCredentials).apply()

        assertEquals(malformedWithCredentials, sanitizeHistoryDocumentJson(malformedWithCredentials))
        val read = readHistoryDocument(preferences, historyKey)
        assertEquals(HistoryDocumentState.UNREADABLE, read.state)
        assertNull(read.safeRawDocument)
        assertFalse(read.requiresPrivacyRewrite)
        assertFalse(writeHistoryDocument(preferences, historyKey, emptyList(), read))
        assertEquals(malformedWithCredentials, preferences.durableValue(historyKey))
        assertTrue(
            preferences.durableKeys().none {
                it.startsWith(persistedRecoveryKeyPrefix(historyKey))
            },
        )
        val restarted = preferences.restart()
        assertEquals(HistoryDocumentState.UNREADABLE, readHistoryDocument(restarted, historyKey).state)
        assertEquals(malformedWithCredentials, restarted.durableValue(historyKey))
    }

    @Test
    fun `startup metadata scrub leaves malformed history unreadable`() = runBlocking {
        val preferences = FakeSharedPreferences()
        val historyKey = "profile:kids-1:history"
        preferences.edit().putString(historyKey, malformedWithCredentials).apply()

        val result = runDurableDownloadStartupMaintenance(
            scrubPersistedCredentials = {
                scrubCredentialBearingMetadataPreferences(preferences)
            },
            captureLegacyOwner = { true },
        )

        assertEquals(DurableDownloadStartupMaintenanceResult.CREDENTIAL_SCRUB_FAILED, result)
        assertEquals(malformedWithCredentials, preferences.durableValue(historyKey))
        assertEquals(
            HistoryDocumentState.UNREADABLE,
            readHistoryDocument(preferences, historyKey).state,
        )
    }

    @Test
    fun `partial history with credential string is not copied into recovery`() {
        val preferences = FakeSharedPreferences()
        val key = "profile:kids-1:history"
        val healthy = JSONObject()
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
        val raw = JSONArray()
            .put(healthy)
            .put("http://provider.test/series/alpha/secret/99.mkv")
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
        val healthy = JSONObject()
            .put("downloadId", 1L)
            .put("historyKey", "movie:1")
            .put("title", "Movie 1")
            .put("streamKind", "movie")
            .put("streamId", 1)
            .put("extension", "mp4")
            .put("status", OfflineStatus.QUEUED.name)
            .put("ownerProfileIds", JSONArray(listOf("profile-1")))
            .put("createdAtEpochMs", 1_000L)
        val raw = JSONArray()
            .put(healthy)
            .put("http://provider.test/series/alpha/secret/99.mkv")
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
                it.startsWith(persistedRecoveryKeyPrefix(DurableDownloadPreferenceStore.KEY_DOWNLOADS))
            },
        )
    }

    @Test
    fun `actual download account migration skips unsafe legacy payload and marks it blocked`() {
        val context = TestAppContext()
        val scopeState = context.getSharedPreferences("hulk_account_scope_v1", 0)
        AccountScopeStore(scopeState) { name -> context.getSharedPreferences(name, 0) }
            .bind(ACCOUNT_ID)
        val legacy = context.getSharedPreferences("hulk_downloads", 0)
        legacy.edit()
            .putString(
                DurableDownloadPreferenceStore.KEY_DOWNLOADS,
                malformedWithCredentials,
            )
            .apply()
        val storage = DownloadAccountStorage(context)
        val scoped = storage.preferences(
            DurableDownloadPreferenceStore.PREFERENCES_NAME,
            ACCOUNT_ID,
        )

        assertEquals(null, scoped.getString(DurableDownloadPreferenceStore.KEY_DOWNLOADS, null))
        assertTrue(
            hasUnsafeLegacyMigrationMarker(
                scoped,
                DurableDownloadPreferenceStore.PREFERENCES_NAME,
            ),
        )
        assertTrue(
            scoped.all.values.filterIsInstance<String>().none {
                containsCredentialBearingIptvMaterial(it)
            },
        )
        assertEquals(malformedWithCredentials, legacy.getString(DurableDownloadPreferenceStore.KEY_DOWNLOADS, null))
        assertEquals(
            DownloadIndexState.MISSING_ESTABLISHED,
            DownloadIndexStore(scoped).read().state,
        )
    }

    @Test
    fun `actual history account migration skips unsafe legacy payload and marks it blocked`() {
        val context = TestAppContext()
        val scopeState = context.getSharedPreferences("hulk_account_scope_v1", 0)
        val accountScope = AccountScopeStore(scopeState) { name -> context.getSharedPreferences(name, 0) }
        accountScope.bind(ACCOUNT_ID)
        val legacy = context.getSharedPreferences(USER_LIBRARY_PREFERENCES_BASE, 0)
        legacy.edit().putString("history", malformedWithCredentials).apply()
        val scoped = accountScope.preferences(USER_LIBRARY_PREFERENCES_BASE, ACCOUNT_ID)

        assertEquals(null, scoped.getString("history", null))
        assertTrue(
            hasUnsafeLegacyMigrationMarker(scoped, USER_LIBRARY_PREFERENCES_BASE),
        )
        assertEquals(
            HistoryDocumentState.UNREADABLE,
            readHistoryDocument(scoped, "profile:kids-1:history").state,
        )
        assertEquals(
            malformedWithCredentials,
            legacy.getString("history", null),
        )
    }

    @Test
    fun `valid credential bearing fields are still sanitized`() {
        val validDownload = """
            [{
              "downloadId": 42,
              "historyKey": "movie:55",
              "title": "Movie",
              "posterUrl": "http://provider.test/movie/alpha/secret/55.jpg",
              "streamKind": "movie",
              "streamId": 55,
              "extension": "mp4",
              "sourceCandidates": ["http://provider.test/movie/alpha/secret/55.mp4"],
              "errorMessage": "failed http://provider.test/movie/alpha/secret/55.mp4"
            }]
        """.trimIndent()
        val preferences = FakeSharedPreferences()
        preferences.edit()
            .putString(DurableDownloadPreferenceStore.KEY_DOWNLOADS, validDownload)
            .apply()

        assertTrue(scrubPersistedDownloadCredentialDocument(preferences))
        val sanitized = preferences.durableValue(DurableDownloadPreferenceStore.KEY_DOWNLOADS) as String
        assertFalse("sourceCandidates" in sanitized)
        assertFalse("/movie/alpha/secret/" in sanitized)
        assertEquals(DownloadIndexState.VALID, DownloadIndexStore(preferences).read().state)
    }
}
