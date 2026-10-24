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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.model.PortalConfig

class DownloadManualRetryTest {
    @Test
    fun `explicit retry of failed record resets an exhausted budget and keeps partial bytes`() {
        val fixture = Fixture()
        fixture.seedRecord(
            status = OfflineStatus.FAILED,
            retryCount = 4,
            bytesDownloaded = 32L,
            totalBytes = 128L,
        )
        val repository = fixture.repository()
        assertTrue(repository.resume(DOWNLOAD_ID))
        val resumed = repository.record(DOWNLOAD_ID)!!

        assertEquals(OfflineStatus.QUEUED, resumed.status)
        assertEquals(0, resumed.retryCount)
        assertEquals(32L, resumed.bytesDownloaded)
        assertEquals(128L, resumed.totalBytes)
        assertEquals(listOf(DOWNLOAD_ID), repository.snapshot().map(OfflineDownload::downloadId))
    }

    @Test
    fun `paused resume keeps its existing retry budget`() {
        val fixture = Fixture()
        fixture.seedRecord(status = OfflineStatus.PAUSED, retryCount = 2)
        val repository = fixture.repository()

        assertTrue(repository.resume(DOWNLOAD_ID))

        assertEquals(OfflineStatus.QUEUED, repository.record(DOWNLOAD_ID)?.status)
        assertEquals(2, repository.record(DOWNLOAD_ID)?.retryCount)
    }

    @Test
    fun `first transient error after manual retry stays within the existing bounded attempts`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(500))
        val fixture = Fixture(server.url("/").toString().trimEnd('/'))
        fixture.seedRecord(
            status = OfflineStatus.FAILED,
            retryCount = 4,
            bytesDownloaded = 32L,
            totalBytes = 128L,
            createPartial = true,
        )
        val repository = fixture.repository()
        val lease = DurableDownloadExecutionLeaseRegistry.claim(fixture.accountId, DOWNLOAD_ID)
        try {
            assertTrue(repository.resume(DOWNLOAD_ID))
            val retried = awaitRecord(repository) {
                it.status == OfflineStatus.QUEUED && it.retryCount == 1
            }
            assertEquals(32L, retried.bytesDownloaded)
            assertEquals(128L, retried.totalBytes)
            assertEquals(1, server.requestCount)

            // Duplicate explicit input while the transport attempt is live must not reset the
            // new budget, duplicate the record, or start a second transport.
            assertTrue(repository.resume(DOWNLOAD_ID))
            assertEquals(1, repository.record(DOWNLOAD_ID)?.retryCount)
            assertEquals(1, repository.snapshot().size)
            assertEquals(1, server.requestCount)
        } finally {
            repository.interruptForDurableWorkerStop(DOWNLOAD_ID)
            repository.suspendForAccountBoundary()
            lease?.let(DurableDownloadExecutionLeaseRegistry::release)
            server.shutdown()
            AuthenticatedSessionRegistry.clear()
        }
    }

    @Test
    fun `automatic retry budget remains bounded without a manual reset`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(500))
        val fixture = Fixture(server.url("/").toString().trimEnd('/'))
        fixture.seedRecord(
            status = OfflineStatus.QUEUED,
            retryCount = 3,
            bytesDownloaded = 32L,
            totalBytes = 128L,
            createPartial = true,
        )
        val repository = fixture.repository()
        val lease = DurableDownloadExecutionLeaseRegistry.claim(fixture.accountId, DOWNLOAD_ID)
        try {
            repository.downloads()
            val failed = awaitRecord(repository) { it.status == OfflineStatus.FAILED }
            assertEquals(3, failed.retryCount)
            assertEquals(1, server.requestCount)
        } finally {
            repository.suspendForAccountBoundary()
            lease?.let(DurableDownloadExecutionLeaseRegistry::release)
            server.shutdown()
            AuthenticatedSessionRegistry.clear()
        }
    }

    @Test
    fun `completed record remains terminal for explicit resume`() {
        assertTrue(downloadResumeBlocked(OfflineStatus.COMPLETED))
        assertFalse(downloadResumeBlocked(OfflineStatus.FAILED))
        assertFalse(downloadResumeBlocked(OfflineStatus.PAUSED))
        assertFalse(downloadResumeBlocked(OfflineStatus.WAITING_NETWORK))
    }

    @Test
    fun `stale account or profile cannot resume another owners record`() {
        val fixture = Fixture(seedAdultProfile = true)
        fixture.seedRecord(status = OfflineStatus.FAILED, retryCount = 4)
        val facade = ProfileScopedDownloadRepository(fixture.context)

        val wrongAccount = facade.resume(DOWNLOAD_ID, "other-account", PROFILE_ID)
        assertFalse(wrongAccount.resumed)
        assertNull(wrongAccount.status)

        val wrongProfile = facade.resume(DOWNLOAD_ID, fixture.accountId, "other-profile")
        assertFalse(wrongProfile.resumed)
        assertNull(wrongProfile.status)

        val authoritative = fixture.repository().record(DOWNLOAD_ID)!!
        assertEquals(OfflineStatus.FAILED, authoritative.status)
        assertEquals(4, authoritative.retryCount)
    }

    private fun awaitRecord(
        repository: DownloadRepository,
        predicate: (OfflineDownload) -> Boolean,
    ): OfflineDownload = runBlocking {
        withTimeout(8_000L) {
            var found: OfflineDownload? = null
            while (found == null) {
                found = repository.record(DOWNLOAD_ID)?.takeIf(predicate)
                if (found == null) delay(10L)
            }
            checkNotNull(found)
        }
    }

    private class Fixture(
        providerBaseUrl: String = "https://provider.example.test",
        seedAdultProfile: Boolean = false,
    ) {
        val accountId = "account-${UUID.randomUUID()}"
        val preferences = FakeSharedPreferences()
        private val tempDirectory = Files.createTempDirectory("hulk-manual-retry").toFile()
        private val contextPreferences = mutableMapOf<String, FakeSharedPreferences>()
        val context = TestAppContext(contextPreferences, tempDirectory)
        val session = AuthenticatedSession(
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
            if (seedAdultProfile) {
                context.getSharedPreferences(
                    accountScopedPreferencesName(ProfileStore.PREFERENCES_NAME, accountId),
                    0,
                ).edit()
                    .putString(
                        "profiles",
                        JSONArray().put(
                            JSONObject()
                                .put("id", PROFILE_ID)
                                .put("displayName", "Adult")
                                .put("kind", "STANDARD")
                                .put("avatarKey", "default")
                                .put("createdAtEpochMs", 1_000L)
                                .put("isPrimary", true),
                        ).toString(),
                    )
                    .putString("active_profile_id", PROFILE_ID)
                    .putInt("schema_version", 1)
                    .apply()
            }
        }

        fun repository(): DownloadRepository = DownloadRepository(
            context = context,
            accountId = accountId,
            preferences = preferences,
            legacyOwners = { null },
            injectedNetworkConstraint = { null },
        )

        fun seedRecord(
            status: OfflineStatus,
            retryCount: Int,
            bytesDownloaded: Long = 32L,
            totalBytes: Long = 128L,
            createPartial: Boolean = false,
        ) {
            val fileName = "movie_1.mp4"
            val record = JSONObject()
                .put("downloadId", DOWNLOAD_ID)
                .put("historyKey", "movie:1")
                .put("title", "Movie 1")
                .put("streamKind", "movie")
                .put("streamId", 1)
                .put("extension", "mp4")
                .put("fileName", fileName)
                .put("storagePath", tempDirectory.absolutePath)
                .put("storageLabel", "internal")
                .put("supportsRange", true)
                .put("status", status.name)
                .put("bytesDownloaded", bytesDownloaded)
                .put("totalBytes", totalBytes)
                .put("retryCount", retryCount)
                .put("ownerProfileIds", JSONArray().put(PROFILE_ID))
                .put("createdAtEpochMs", 1_000L)
            preferences.edit()
                .putString(DurableDownloadPreferenceStore.KEY_DOWNLOADS, JSONArray().put(record).toString())
                .putInt(
                    DurableDownloadPreferenceStore.KEY_STORAGE_VERSION,
                    DurableDownloadPreferenceStore.STORAGE_VERSION,
                )
                .apply()
            if (createPartial) {
                File(tempDirectory, "$fileName.part").writeBytes(ByteArray(bytesDownloaded.toInt()))
            }
        }
    }

    private companion object {
        const val DOWNLOAD_ID = 1L
    }
}

private const val PROFILE_ID = "profile-1"
