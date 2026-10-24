package sa.hulksa.player.data

import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
import sa.hulksa.player.ui.screens.downloadPrimaryActionIntent

/**
 * R02: the download primary action is the user's captured intent, not a status guess made after
 * the fact. These tests drive the production dispatcher through a real DownloadRepository and the
 * same account/profile checks the production profile facade applies.
 */
class DownloadPrimaryActionDispatchTest {
    @Test
    fun `stale retry from a failed row cannot pause the retry it already started`() = runBlocking {
        val server = MockWebServer()
        server.start()
        // Hold the first transport attempt long enough for the stale row input to arrive.
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeadersDelay(1_500L, TimeUnit.MILLISECONDS),
        )
        val fixture = Fixture(server.url("/").toString().trimEnd('/'))
        fixture.seedRecord(
            status = OfflineStatus.FAILED,
            retryCount = 4,
            bytesDownloaded = 32L,
            totalBytes = 128L,
            createPartial = true,
        )
        val repository = fixture.repository()
        val store = RepositoryActionStore(repository, fixture.accountId, PROFILE_ID)
        val dispatcher = DownloadPrimaryActionDispatcher(store)
        val lease = DurableDownloadExecutionLeaseRegistry.claim(fixture.accountId, DOWNLOAD_ID)
        try {
            // Capture the exact action the FAILED row displays.
            val staleRow = store.record(DOWNLOAD_ID, fixture.accountId, PROFILE_ID)!!
            assertEquals(OfflineStatus.FAILED, staleRow.status)
            val staleIntent = downloadPrimaryActionIntent(staleRow.status)
            assertEquals(DownloadActionIntent.RETRY, staleIntent)

            val first = dispatcher.dispatch(
                downloadId = DOWNLOAD_ID,
                expectedAccountId = fixture.accountId,
                expectedProfileId = PROFILE_ID,
                intent = checkNotNull(staleIntent),
            )
            assertEquals(DownloadActionClaim.APPLIED, first.claim)
            assertTrue(
                first.status in setOf(
                    OfflineStatus.QUEUED,
                    OfflineStatus.CHECKING,
                    OfflineStatus.DOWNLOADING,
                ),
            )
            awaitCondition { server.requestCount >= 1 }

            // Deliver the same still-displayed FAILED row action while the record is running.
            val second = dispatcher.dispatch(
                downloadId = DOWNLOAD_ID,
                expectedAccountId = fixture.accountId,
                expectedProfileId = PROFILE_ID,
                intent = DownloadActionIntent.RETRY,
            )
            assertEquals(DownloadActionClaim.ALREADY_QUEUED, second.claim)
            assertEquals(1, store.resumeCalls.get())
            assertEquals(0, store.pauseCalls.get())
            assertNotEquals(OfflineStatus.PAUSED, repository.record(DOWNLOAD_ID)?.status)
            assertEquals(1, server.requestCount)
            assertEquals(1, repository.snapshot().size)

            val retried = awaitRecord(repository) {
                it.status == OfflineStatus.QUEUED && it.retryCount == 1
            }
            // The stale second input did not start a duplicate transport or reset the new budget.
            assertEquals(32L, retried.bytesDownloaded)
            assertEquals(1, retried.retryCount)
            assertEquals(1, server.requestCount)
            assertEquals(1, repository.snapshot().size)
            assertEquals(1, store.resumeCalls.get())
            assertEquals(0, store.pauseCalls.get())
        } finally {
            repository.interruptForDurableWorkerStop(DOWNLOAD_ID)
            repository.suspendForAccountBoundary()
            lease?.let(DurableDownloadExecutionLeaseRegistry::release)
            server.shutdown()
            AuthenticatedSessionRegistry.clear()
        }
    }

    @Test
    fun `explicit pause and resume keep their own intent and never cross`() = runBlocking {
        val fixture = Fixture()
        fixture.seedRecord(status = OfflineStatus.DOWNLOADING, retryCount = 2)
        val repository = fixture.repository()
        val store = RepositoryActionStore(repository, fixture.accountId, PROFILE_ID)
        val dispatcher = DownloadPrimaryActionDispatcher(store)
        try {
            val paused = dispatcher.dispatch(
                downloadId = DOWNLOAD_ID,
                expectedAccountId = fixture.accountId,
                expectedProfileId = PROFILE_ID,
                intent = DownloadActionIntent.PAUSE,
            )
            assertEquals(DownloadActionClaim.APPLIED, paused.claim)
            assertEquals(OfflineStatus.PAUSED, repository.record(DOWNLOAD_ID)?.status)
            assertEquals(2, repository.record(DOWNLOAD_ID)?.retryCount)
            assertEquals(1, store.pauseCalls.get())

            val stalePause = dispatcher.dispatch(
                downloadId = DOWNLOAD_ID,
                expectedAccountId = fixture.accountId,
                expectedProfileId = PROFILE_ID,
                intent = DownloadActionIntent.PAUSE,
            )
            assertEquals(DownloadActionClaim.NOT_APPLICABLE, stalePause.claim)
            assertEquals(OfflineStatus.PAUSED, repository.record(DOWNLOAD_ID)?.status)
            assertEquals(1, store.pauseCalls.get())
            assertEquals(0, store.resumeCalls.get())

            val resumed = dispatcher.dispatch(
                downloadId = DOWNLOAD_ID,
                expectedAccountId = fixture.accountId,
                expectedProfileId = PROFILE_ID,
                intent = DownloadActionIntent.RESUME,
            )
            assertEquals(DownloadActionClaim.APPLIED, resumed.claim)
            assertNotEquals(OfflineStatus.PAUSED, repository.record(DOWNLOAD_ID)?.status)
            assertEquals(2, repository.record(DOWNLOAD_ID)?.retryCount)
            assertEquals(1, store.pauseCalls.get())
            assertEquals(1, store.resumeCalls.get())
        } finally {
            repository.suspendForAccountBoundary()
            AuthenticatedSessionRegistry.clear()
        }
    }

    @Test
    fun `completed record stays terminal for every captured intent`() = runBlocking {
        val completed = OfflineDownload(
            downloadId = DOWNLOAD_ID,
            historyKey = "movie:1",
            title = "Movie 1",
            posterUrl = null,
            streamKind = "movie",
            streamId = 1,
            extension = "mp4",
            status = OfflineStatus.COMPLETED,
            bytesDownloaded = 128L,
            totalBytes = 128L,
        )
        val store = StaticActionStore(completed)
        val dispatcher = DownloadPrimaryActionDispatcher(store)

        DownloadActionIntent.entries.forEach { intent ->
            val outcome = dispatcher.dispatch(
                downloadId = DOWNLOAD_ID,
                expectedAccountId = "account-a",
                expectedProfileId = PROFILE_ID,
                intent = intent,
            )
            assertEquals("$intent", DownloadActionClaim.ALREADY_COMPLETED, outcome.claim)
            assertEquals(OfflineStatus.COMPLETED, store.record(DOWNLOAD_ID, "account-a", PROFILE_ID)?.status)
        }
        assertEquals(0, store.pauseCalls.get())
        assertEquals(0, store.resumeCalls.get())
    }

    @Test
    fun `stale owner cannot mutate another profiles record`() = runBlocking {
        val fixture = Fixture()
        fixture.seedRecord(status = OfflineStatus.FAILED, retryCount = 4)
        val repository = fixture.repository()
        val store = RepositoryActionStore(repository, "other-account", PROFILE_ID)
        val dispatcher = DownloadPrimaryActionDispatcher(store)
        try {
            DownloadActionIntent.entries.forEach { intent ->
                val outcome = dispatcher.dispatch(
                    downloadId = DOWNLOAD_ID,
                    expectedAccountId = fixture.accountId,
                    expectedProfileId = PROFILE_ID,
                    intent = intent,
                )
                assertEquals("$intent", DownloadActionClaim.OWNER_CHANGED, outcome.claim)
                assertTrue(outcome.downloads.isEmpty())
            }

            val authoritative = repository.record(DOWNLOAD_ID)
            assertEquals(OfflineStatus.FAILED, authoritative?.status)
            assertEquals(4, authoritative?.retryCount)
            assertEquals(0, store.pauseCalls.get())
            assertEquals(0, store.resumeCalls.get())
        } finally {
            AuthenticatedSessionRegistry.clear()
        }
    }

    private suspend fun awaitRecord(
        repository: DownloadRepository,
        predicate: (OfflineDownload) -> Boolean,
    ): OfflineDownload = withTimeout(8_000L) {
        var found: OfflineDownload? = null
        while (found == null) {
            found = repository.record(DOWNLOAD_ID)?.takeIf(predicate)
            if (found == null) delay(10L)
        }
        checkNotNull(found)
    }

    private suspend fun awaitCondition(predicate: () -> Boolean) {
        withTimeout(8_000L) {
            while (!predicate()) delay(10L)
        }
    }

    /** Deterministic store for terminal/status-mapping cases that need no transport. */
    private class StaticActionStore(
        private val record: OfflineDownload,
    ) : DownloadPrimaryActionStore {
        val pauseCalls = AtomicInteger(0)
        val resumeCalls = AtomicInteger(0)

        override fun record(
            downloadId: Long,
            expectedAccountId: String,
            expectedProfileId: String,
        ): OfflineDownload? = record

        override fun snapshot(): List<OfflineDownload> = listOf(record)

        override fun pause(
            downloadId: Long,
            expectedAccountId: String,
            expectedProfileId: String,
        ): ProfileDownloadPauseOutcome {
            pauseCalls.incrementAndGet()
            return ProfileDownloadPauseOutcome(listOf(record), applied = false, persisted = true)
        }

        override fun resume(
            downloadId: Long,
            expectedAccountId: String,
            expectedProfileId: String,
        ): ProfileDownloadResumeOutcome {
            resumeCalls.incrementAndGet()
            return ProfileDownloadResumeOutcome(listOf(record), resumed = false, status = record.status)
        }
    }

    /**
     * Mirrors the production profile facade's owner checks while delegating to a real repository.
     * This keeps the test off WorkManager; the dispatcher itself is the production object.
     */
    private class RepositoryActionStore(
        private val repository: DownloadRepository,
        private val accountId: String,
        private val profileId: String,
    ) : DownloadPrimaryActionStore {
        val pauseCalls = AtomicInteger(0)
        val resumeCalls = AtomicInteger(0)

        override fun record(
            downloadId: Long,
            expectedAccountId: String,
            expectedProfileId: String,
        ): OfflineDownload? {
            if (!owns(expectedAccountId, expectedProfileId)) return null
            return repository.record(downloadId)
        }

        override fun snapshot(): List<OfflineDownload> = repository.snapshot()

        override fun pause(
            downloadId: Long,
            expectedAccountId: String,
            expectedProfileId: String,
        ): ProfileDownloadPauseOutcome {
            if (!owns(expectedAccountId, expectedProfileId)) {
                return ProfileDownloadPauseOutcome(emptyList(), applied = false, persisted = true)
            }
            pauseCalls.incrementAndGet()
            val result = repository.pause(downloadId)
            return ProfileDownloadPauseOutcome(
                downloads = repository.snapshot(),
                applied = result.persisted,
                persisted = result.persisted,
            )
        }

        override fun resume(
            downloadId: Long,
            expectedAccountId: String,
            expectedProfileId: String,
        ): ProfileDownloadResumeOutcome {
            if (!owns(expectedAccountId, expectedProfileId)) {
                return ProfileDownloadResumeOutcome(emptyList(), resumed = false, status = null)
            }
            resumeCalls.incrementAndGet()
            val resumed = repository.resume(downloadId)
            return ProfileDownloadResumeOutcome(
                downloads = repository.snapshot(),
                resumed = resumed,
                status = repository.record(downloadId)?.status,
            )
        }

        private fun owns(expectedAccountId: String, expectedProfileId: String): Boolean =
            expectedAccountId == accountId && expectedProfileId == profileId
    }

    private class Fixture(
        providerBaseUrl: String = "https://provider.example.test",
    ) {
        val accountId = "account-${UUID.randomUUID()}"
        val preferences = FakeSharedPreferences()
        private val tempDirectory = Files.createTempDirectory("hulk-primary-action").toFile()
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
                .put("localUri", JSONObject.NULL)
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
