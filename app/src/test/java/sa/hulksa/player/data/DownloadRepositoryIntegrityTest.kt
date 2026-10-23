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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.model.PlaybackRequest

class DownloadRepositoryIntegrityTest {
    @Test
    fun `repository initialization does not rewrite a missing established index`() {
        val fixture = Fixture()
        fixture.preferences.edit()
            .putInt(
                DurableDownloadPreferenceStore.KEY_STORAGE_VERSION,
                DurableDownloadPreferenceStore.STORAGE_VERSION,
            )
            .putString("download_progress:42", progressJson())
            .apply()

        val repository = fixture.repository()

        assertNull(fixture.preferences.durableValue(KEY_DOWNLOADS))
        assertEquals(progressJson(), fixture.preferences.durableValue("download_progress:42"))
        assertTrue(repository.snapshot().isEmpty())
        assertTrue(repository.pause(42L).persisted)
        assertNull(fixture.preferences.durableValue(KEY_DOWNLOADS))
        assertEquals(progressJson(), fixture.preferences.durableValue("download_progress:42"))
    }

    @Test
    fun `failed ownership addition rolls back repository and preference memory`() {
        val fixture = Fixture()
        fixture.seedRecord(ownerProfileIds = setOf("profile-1"))
        val repository = fixture.repository()
        fixture.preferences.commitResult = false

        val result = repository.enqueue(
            request = request(historyKey = "movie:1"),
            ownerProfileId = "profile-2",
        )

        assertTrue(result is DownloadRepository.EnqueueResult.Failed)
        assertEquals(setOf("profile-1"), repository.record(1L)?.ownerProfileIds)
        assertEquals(setOf("profile-1"), DownloadIndexStore(fixture.preferences).read().records.single().ownerProfileIds)

        fixture.preferences.commitResult = true
        fixture.preferences.edit().putString("unrelated_setting", "applied").apply()
        val restarted = fixture.repository()
        assertEquals(setOf("profile-1"), restarted.record(1L)?.ownerProfileIds)
        assertEquals("applied", fixture.preferences.durableValue("unrelated_setting"))
    }

    @Test
    fun `failed pause does not leave a paused record in memory or lifecycle state`() {
        val fixture = Fixture()
        fixture.seedRecord(status = OfflineStatus.DOWNLOADING)
        val repository = fixture.repository()
        fixture.preferences.commitResult = false

        val result = repository.pause(1L)

        assertFalse(result.persisted)
        assertEquals(OfflineStatus.QUEUED, repository.record(1L)?.status)
        assertEquals(OfflineStatus.QUEUED, DownloadIndexStore(fixture.preferences).read().records.single().status)

        fixture.preferences.commitResult = true
        fixture.preferences.edit().putString("unrelated_setting", "applied").apply()
        assertEquals(OfflineStatus.QUEUED, fixture.repository().record(1L)?.status)
    }

    @Test
    fun `snapshot spanning rollback is rejected even after failed commit finishes`() = runBlocking {
        val fixture = Fixture()
        fixture.seedRecord(status = OfflineStatus.QUEUED)
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

        val stable = reader.snapshot()
        assertFalse(stable.mutationInProgress)
        assertEquals(OfflineStatus.QUEUED, stable.records.single().status)
    }

    @Test
    fun `snapshot completed after generation capture but before final validation is rejected`() = runBlocking {
        val fixture = Fixture()
        fixture.seedRecord(status = OfflineStatus.QUEUED)
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

        val generationCaptured = CountDownLatch(1)
        val releaseValidation = CountDownLatch(1)
        val readerWork = async(Dispatchers.Default) {
            reader.snapshotWithValidationHook {
                generationCaptured.countDown()
                releaseValidation.await(5, TimeUnit.SECONDS)
            }
        }
        assertTrue(generationCaptured.await(5, TimeUnit.SECONDS))
        releaseWriter.countDown()
        assertFalse(pause.await().persisted)
        releaseValidation.countDown()
        val snapshot = readerWork.await()
        assertTrue(snapshot.mutationInProgress)
        assertEquals(OfflineStatus.PAUSED, snapshot.records.single().status)

        val stable = reader.snapshot()
        assertFalse(stable.mutationInProgress)
        assertEquals(OfflineStatus.QUEUED, stable.records.single().status)
    }

    @Test
    fun `publication retry is immediate when registration follows completion`() {
        DownloadPersistenceMutationGate.begin(ACCOUNT_ID)
        DownloadPersistenceMutationGate.end(ACCOUNT_ID)
        var retried = false
        DownloadPersistenceMutationGate.whenPublished(ACCOUNT_ID) { retried = true }
        assertTrue(retried)
    }

    @Test
    fun `publication retry registered while active runs after success and rollback`() {
        DownloadPersistenceMutationGate.begin(ACCOUNT_ID)
        var successRetry = false
        DownloadPersistenceMutationGate.whenPublished(ACCOUNT_ID) { successRetry = true }
        assertFalse(successRetry)
        DownloadPersistenceMutationGate.end(ACCOUNT_ID)
        assertTrue(successRetry)

        DownloadPersistenceMutationGate.begin(ACCOUNT_ID)
        var rollbackRetry = false
        DownloadPersistenceMutationGate.whenPublished(ACCOUNT_ID) { rollbackRetry = true }
        assertFalse(rollbackRetry)
        DownloadPersistenceMutationGate.end(ACCOUNT_ID)
        assertTrue(rollbackRetry)
    }

    @Test
    fun `network recovery intent survives in-flight deferral`() {
        val intent = DurableDownloadReconciliationIntent()
        intent.forceWaitingNetworkReenqueue = true
        var retried = false
        val inProgress = DurableDownloadPreferenceSnapshot(
            wifiOnly = false,
            records = emptyList(),
            mutationInProgress = true,
        )
        assertTrue(
            intent.deferIfMutationInProgress(inProgress) {
                retried = true
            },
        )
        assertTrue(retried)
        assertTrue(intent.forceWaitingNetworkReenqueue)

        val stable = inProgress.copy(mutationInProgress = false)
        assertFalse(intent.deferIfMutationInProgress(stable) { retried = false })
        assertTrue(intent.consumeForceWaitingNetwork())
        assertFalse(intent.forceWaitingNetworkReenqueue)
    }

    @Test
    fun `recovery-only download state blocks fresh index initialization through actual repository`() {
        val fixture = Fixture()
        assertTrue(
            preserveDamagedPersistedDocument(
                fixture.preferences,
                DurableDownloadPreferenceStore.KEY_DOWNLOADS,
                "damaged-download-index",
            ),
        )
        val recoveryKeys = fixture.preferences.durableKeys().filter {
            it.startsWith(persistedRecoveryKeyPrefix(DurableDownloadPreferenceStore.KEY_DOWNLOADS))
        }
        assertTrue(recoveryKeys.isNotEmpty())

        val repository = fixture.repository()
        assertNull(fixture.preferences.durableValue(KEY_DOWNLOADS))
        repository.setScheduleMode(sa.hulksa.player.model.DownloadScheduleMode.NIGHT)
        assertNull(fixture.preferences.durableValue(KEY_DOWNLOADS))
        assertTrue(recoveryKeys.all { fixture.preferences.durableValue(it) != null })

        val restarted = fixture.repository()
        assertTrue(restarted.snapshot().isEmpty())
        assertNull(fixture.preferences.durableValue(KEY_DOWNLOADS))
        assertTrue(recoveryKeys.all { fixture.preferences.durableValue(it) != null })
    }

    @Test
    fun `background reader cannot act on tentative memory before commit result`() = runBlocking {
        val fixture = Fixture()
        fixture.seedRecord(status = OfflineStatus.QUEUED)
        val repository = fixture.repository()
        val reader = DurableDownloadPreferenceStore(fixture.context, ACCOUNT_ID)
        fixture.preferences.commitResult = false
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        fixture.preferences.afterMemoryMutationBeforeCommit = {
            fixture.preferences.afterMemoryMutationBeforeCommit = null
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        var publishedSignal = false
        DownloadPersistenceMutationGate.whenPublished(ACCOUNT_ID) { publishedSignal = true }

        val pause = async(Dispatchers.Default) { repository.pause(1L) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val tentative = reader.snapshot()
        assertTrue(tentative.mutationInProgress)
        assertEquals(OfflineStatus.PAUSED, tentative.records.single().status)

        release.countDown()
        assertFalse(pause.await().persisted)
        assertTrue(publishedSignal)
        val after = reader.snapshot()
        assertFalse(after.mutationInProgress)
        assertEquals(OfflineStatus.QUEUED, after.records.single().status)
        assertEquals(OfflineStatus.QUEUED, repository.record(1L)?.status)
    }

    @Test
    fun `failed multi owner removal restores ownership and does not delete the record`() {
        val fixture = Fixture()
        fixture.seedRecord(ownerProfileIds = setOf("profile-1", "profile-2"))
        val repository = fixture.repository()
        fixture.preferences.commitResult = false

        val result = repository.removeOwner(1L, "profile-1")

        assertEquals(setOf("profile-1", "profile-2"), result.single().ownerProfileIds)
        assertEquals(
            setOf("profile-1", "profile-2"),
            DownloadIndexStore(fixture.preferences).read().records.single().ownerProfileIds,
        )

        fixture.preferences.commitResult = true
        fixture.preferences.edit().putString("unrelated_setting", "applied").apply()
        assertEquals(
            setOf("profile-1", "profile-2"),
            fixture.repository().record(1L)?.ownerProfileIds,
        )
    }

    private class Fixture {
        val preferences = FakeSharedPreferences()
        private val tempDirectory = Files.createTempDirectory("hulk-download-index").toFile()
        val context = TestAppContext(
            preferences = mutableMapOf(
                "hulk_downloads.account.$ACCOUNT_ID" to preferences,
            ),
            externalDir = tempDirectory,
        )

        fun repository(): DownloadRepository = DownloadRepository(
            context = context,
            accountId = ACCOUNT_ID,
            preferences = preferences,
            legacyOwners = { null },
        )

        fun seedRecord(
            ownerProfileIds: Set<String> = setOf("profile-1"),
            status: OfflineStatus = OfflineStatus.QUEUED,
        ) {
            val record = JSONObject()
                .put("downloadId", 1L)
                .put("historyKey", "movie:1")
                .put("title", "Movie 1")
                .put("streamKind", "movie")
                .put("streamId", 1)
                .put("extension", "mp4")
                .put("status", status.name)
                .put("ownerProfileIds", JSONArray(ownerProfileIds.sorted()))
                .put("createdAtEpochMs", 1_000L)
            preferences.edit()
                .putString(KEY_DOWNLOADS, JSONArray().put(record).toString())
                .putInt(
                    DurableDownloadPreferenceStore.KEY_STORAGE_VERSION,
                    DurableDownloadPreferenceStore.STORAGE_VERSION,
                )
                .putString("download_progress:1", progressJson())
                .apply()
        }
    }

    private fun request(historyKey: String): PlaybackRequest = PlaybackRequest(
        title = "Movie 1",
        posterUrl = null,
        candidates = listOf("https://cdn.example.test/movie/1.mp4"),
        isLive = false,
        historyKey = historyKey,
        streamKind = "movie",
        streamId = 1,
        extension = "mp4",
    )

    private companion object {
        const val ACCOUNT_ID = "account-a"
        const val KEY_DOWNLOADS = DurableDownloadPreferenceStore.KEY_DOWNLOADS
    }
}

private fun progressJson(): String = JSONObject()
    .put("bytesDownloaded", 32L)
    .put("totalBytes", 128L)
    .put("bytesPerSecond", 0L)
    .put("etaSeconds", -1L)
    .toString()
