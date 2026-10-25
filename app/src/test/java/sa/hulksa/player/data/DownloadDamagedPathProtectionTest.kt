package sa.hulksa.player.data

import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.model.PortalConfig

class DownloadDamagedPathProtectionTest {
    @Test
    fun `partially damaged record protects shared final and partial bytes through deletion and restart`() {
        val fixture = Fixture()
        val original = fixture.createFile(LEGACY_NAME, "ORIGINAL")
        val part = fixture.createFile("$LEGACY_NAME.part", "PART")
        fixture.seed(
            fixture.recordJson(id = 1L, historyKey = "MOVIE:9001", status = OfflineStatus.COMPLETED),
            fixture.recordJson(
                id = 2L,
                historyKey = "SERIES:9001",
                status = OfflineStatus.PAUSED,
                includeTitle = false,
            ),
            fixture.recordJson(
                id = 3L,
                historyKey = "MOVIE:3",
                status = OfflineStatus.COMPLETED,
                fileName = "unique_completed.mp4",
            ),
            fixture.recordJson(
                id = 4L,
                historyKey = "SERIES:4",
                status = OfflineStatus.PAUSED,
                fileName = "unique_partial.mp4",
            ),
        )
        fixture.createFile("unique_completed.mp4", "UNIQUE!!")
        fixture.createFile("unique_partial.mp4.part", "UNIQUE-PART")

        val repository = fixture.repository()
        try {
            val quarantined = checkNotNull(repository.record(1L))
            assertEquals(OfflineStatus.FAILED, quarantined.status)
            assertNotEquals(LEGACY_NAME, quarantined.fileName)
            assertEquals("ORIGINAL", original.readText())
            assertEquals("PART", part.readText())

            val completed = checkNotNull(repository.record(3L))
            assertEquals("unique_completed.mp4", completed.fileName)
            assertEquals("UNIQUE!!", File(fixture.tempDirectory, completed.fileName!!).readText())
            val partial = checkNotNull(repository.record(4L))
            assertEquals("unique_partial.mp4", partial.fileName)
            assertEquals(
                "UNIQUE-PART",
                File(fixture.tempDirectory, "${partial.fileName}.part").readText(),
            )

            repository.removeOwner(1L, PROFILE_ID)
            assertEquals("ORIGINAL", original.readText())
            assertEquals("PART", part.readText())
        } finally {
            repository.suspendForAccountBoundary()
            AuthenticatedSessionRegistry.clear()
        }

        val restarted = fixture.repository()
        try {
            assertEquals("ORIGINAL", original.readText())
            assertEquals("PART", part.readText())
            assertTrue(restarted.snapshot().none { it.downloadId == 1L })
            assertTrue(restarted.snapshot().any { it.downloadId == 3L })
            assertTrue(restarted.snapshot().any { it.downloadId == 4L })
        } finally {
            restarted.suspendForAccountBoundary()
            AuthenticatedSessionRegistry.clear()
        }
    }

    @Test
    fun `retry writes a new file and leaves protected legacy bytes untouched`() = runBlocking {
        val content = ByteArray(100) { index -> (index % 100).toByte() }
        val server = MockWebServer().apply { start() }
        val fixture = Fixture(providerBaseUrl = server.url("/").toString().trimEnd('/'))
        val original = fixture.createFile(LEGACY_NAME, "ORIGINAL")
        val part = fixture.createFile("$LEGACY_NAME.part", "PART")
        fixture.seed(
            fixture.recordJson(id = 1L, historyKey = "MOVIE:9001", status = OfflineStatus.COMPLETED),
            fixture.recordJson(
                id = 2L,
                historyKey = "SERIES:9001",
                status = OfflineStatus.PAUSED,
                includeTitle = false,
            ),
        )
        val repository = fixture.repository()
        var lease: DurableDownloadExecutionLeaseRegistry.Lease? = null
        try {
            val quarantined = checkNotNull(repository.record(1L))
            assertEquals(OfflineStatus.FAILED, quarantined.status)
            server.enqueue(MockResponse().setBody(okio.Buffer().write(content)))
            server.enqueue(MockResponse().setBody(okio.Buffer().write(content)))
            lease = DurableDownloadExecutionLeaseRegistry.claim(fixture.accountId, 1L)
            assertTrue(repository.resume(1L))

            val completed = awaitRecord(repository, 1L) { it.status == OfflineStatus.COMPLETED }
            val downloaded = File(checkNotNull(completed.storagePath), checkNotNull(completed.fileName))
            assertTrue(downloaded.isFile)
            assertTrue(content.contentEquals(downloaded.readBytes()))
            assertNotEquals(original.absolutePath, downloaded.absolutePath)
            assertEquals("ORIGINAL", original.readText())
            assertEquals("PART", part.readText())
        } finally {
            repository.suspendForAccountBoundary()
            lease?.let(DurableDownloadExecutionLeaseRegistry::release)
            server.shutdown()
            AuthenticatedSessionRegistry.clear()
        }
    }

    @Test
    fun `failed index write keeps originals and protection survives the next restart`() {
        val fixture = Fixture()
        val original = fixture.createFile(LEGACY_NAME, "ORIGINAL")
        val part = fixture.createFile("$LEGACY_NAME.part", "PART")
        fixture.seed(
            fixture.recordJson(id = 1L, historyKey = "MOVIE:9001", status = OfflineStatus.COMPLETED),
            fixture.recordJson(
                id = 2L,
                historyKey = "SERIES:9001",
                status = OfflineStatus.PAUSED,
                includeTitle = false,
            ),
        )
        fixture.preferences.commitResult = false

        val repository = fixture.repository()
        try {
            val quarantined = checkNotNull(repository.record(1L))
            assertNotEquals(LEGACY_NAME, quarantined.fileName)
            assertEquals("ORIGINAL", original.readText())
            assertEquals("PART", part.readText())
            val durableRaw = fixture.preferences.durableValue(KEY_DOWNLOADS) as? String ?: ""
            assertTrue(durableRaw.contains(LEGACY_NAME))
        } finally {
            repository.suspendForAccountBoundary()
            AuthenticatedSessionRegistry.clear()
        }

        fixture.preferences.commitResult = true
        val restarted = fixture.repository()
        try {
            val protected = checkNotNull(restarted.record(1L))
            assertNotEquals(LEGACY_NAME, protected.fileName)
            assertEquals("ORIGINAL", original.readText())
            assertEquals("PART", part.readText())
        } finally {
            restarted.suspendForAccountBoundary()
            AuthenticatedSessionRegistry.clear()
        }
    }

    private suspend fun awaitRecord(
        repository: DownloadRepository,
        downloadId: Long,
        predicate: (OfflineDownload) -> Boolean,
    ): OfflineDownload = withTimeout(10_000L) {
        var found: OfflineDownload? = null
        while (found == null) {
            found = repository.record(downloadId)?.takeIf(predicate)
            if (found == null) delay(10L)
        }
        checkNotNull(found)
    }

    private class Fixture(
        providerBaseUrl: String = "https://provider.example.test",
    ) {
        val accountId = "account-${UUID.randomUUID()}"
        val preferences = FakeSharedPreferences()
        val tempDirectory: File = Files.createTempDirectory("hulk-damaged-paths").toFile()
        private val contextPreferences = mutableMapOf<String, FakeSharedPreferences>()
        private val context = TestAppContext(contextPreferences, tempDirectory)
        private val session = AuthenticatedSession(
            portal = PortalConfig(providerBaseUrl, PortalConfig.Source.ACCESS_CODE),
            credentials = Credentials("HULK-TEST-CODE", "user", "pass"),
            account = AccountInfo("user", "Active", Long.MAX_VALUE, 0, 1, false),
        )

        init {
            contextPreferences[
                accountScopedPreferencesName(DurableDownloadPreferenceStore.PREFERENCES_NAME, accountId)
            ] = preferences
            val metadata = AccountSessionStore(context).recordAuthenticated(session, accountId)
            AuthenticatedSessionRegistry.update(session, metadata)
        }

        fun repository(): DownloadRepository = DownloadRepository(
            context = context,
            accountId = accountId,
            preferences = preferences,
            legacyOwners = { null },
            injectedNetworkConstraint = { null },
            fileUri = { file -> "file://" + file.absolutePath },
        )

        fun createFile(name: String, content: String): File =
            File(tempDirectory, name).apply { writeText(content) }

        fun recordJson(
            id: Long,
            historyKey: String,
            status: OfflineStatus,
            fileName: String = LEGACY_NAME,
            includeTitle: Boolean = true,
        ): JSONObject = JSONObject()
            .put("downloadId", id)
            .put("historyKey", historyKey)
            .apply { if (includeTitle) put("title", "Same Title") }
            .put("streamKind", if (historyKey.startsWith("MOVIE:")) "movie" else "series")
            .put("streamId", 9001)
            .put("extension", "mp4")
            .put("fileName", fileName)
            .put("storagePath", tempDirectory.absolutePath)
            .put("storageLabel", "internal")
            .put("supportsRange", true)
            .put("status", status.name)
            .put("localUri", "file://" + File(tempDirectory, fileName).absolutePath)
            .put("bytesDownloaded", 0L)
            .put("totalBytes", if (status == OfflineStatus.COMPLETED) 8L else -1L)
            .put("ownerProfileIds", JSONArray().put(PROFILE_ID))
            .put("createdAtEpochMs", 1_000L + id)

        fun seed(vararg records: JSONObject) {
            preferences.edit()
                .putString(KEY_DOWNLOADS, JSONArray(records.toList()).toString())
                .putInt(
                    DurableDownloadPreferenceStore.KEY_STORAGE_VERSION,
                    DurableDownloadPreferenceStore.STORAGE_VERSION,
                )
                .apply()
        }
    }

    private companion object {
        const val PROFILE_ID = "profile-1"
        const val LEGACY_NAME = "Same_9001.mp4"
        const val KEY_DOWNLOADS = DurableDownloadPreferenceStore.KEY_DOWNLOADS
    }
}
