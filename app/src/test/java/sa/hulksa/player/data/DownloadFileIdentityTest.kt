package sa.hulksa.player.data

import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.model.PlaybackRequest
import sa.hulksa.player.model.PortalConfig

class DownloadFileIdentityTest {
    @Test
    fun `movie and episode with equal title id and extension get distinct deterministic names`() {
        val movie = buildDownloadFileName("MOVIE:9001", "Same Title", 9001, "mp4")
        val episode = buildDownloadFileName("SERIES:9001", "Same Title", 9001, "mp4")

        assertNotEquals(movie, episode)
        assertEquals(movie, buildDownloadFileName("MOVIE:9001", "Same Title", 9001, "mp4"))
        assertFalse(movie.contains('/'))
        assertFalse(movie.contains(':'))
        assertTrue(movie.endsWith(".mp4"))
    }

    @Test
    fun `new movie and episode records no longer share final or partial paths`() {
        val movie = record(1L, "MOVIE:9001", "Same Title", 9001, OfflineStatus.QUEUED)
        val episode = record(2L, "SERIES:9001", "Same Title", 9001, OfflineStatus.QUEUED)

        assertNotEquals(movie.fileName, episode.fileName)
        assertTrue(downloadPhysicalPathKeys(movie).intersect(downloadPhysicalPathKeys(episode)).isEmpty())
    }

    @Test
    fun `same logical item keeps one file across profile owners`() {
        val fixture = Fixture()
        fixture.seedRecord(
            downloadId = 1L,
            historyKey = "MOVIE:9001",
            fileName = "movie_identity.mp4",
            status = OfflineStatus.QUEUED,
            ownerProfiles = setOf(PROFILE_ID),
        )
        val repository = fixture.repository()
        try {
            val result = repository.enqueue(
                request = request("MOVIE:9001"),
                ownerProfileId = "profile-2",
            )

            assertTrue(result is DownloadRepository.EnqueueResult.AlreadyExists)
            val stored = checkNotNull(repository.record(1L))
            assertEquals("movie_identity.mp4", stored.fileName)
            assertEquals(setOf(PROFILE_ID, "profile-2"), stored.ownerProfileIds)
            assertEquals(1, repository.snapshot().size)
        } finally {
            repository.suspendForAccountBoundary()
            AuthenticatedSessionRegistry.clear()
        }
    }

    @Test
    fun `independent enqueue resume completion and deletion keep colliding records separate`() = runBlocking {
        val content = ByteArray(100) { index -> (index % 100).toByte() }
        val server = MockWebServer().apply { start() }
        val fixture = Fixture(server.url("/").toString().trimEnd('/'))
        val repository = fixture.repository()
        var movieLease: DurableDownloadExecutionLeaseRegistry.Lease? = null
        var episodeLease: DurableDownloadExecutionLeaseRegistry.Lease? = null
        try {
            val movieResult = repository.enqueue(
                request = request("MOVIE:9001", server.url("/movie.mp4").toString()),
                ownerProfileId = PROFILE_ID,
            )
            assertTrue("movie=$movieResult", movieResult is DownloadRepository.EnqueueResult.Started)
            val movie = (movieResult as DownloadRepository.EnqueueResult.Started).item
            val episodeResult = repository.enqueue(
                request = request("SERIES:9001", server.url("/episode.mp4").toString()),
                ownerProfileId = PROFILE_ID,
            )
            assertTrue("episode=$episodeResult", episodeResult is DownloadRepository.EnqueueResult.Started)
            val episode = (episodeResult as DownloadRepository.EnqueueResult.Started).item
            assertNotEquals(movie.fileName, episode.fileName)
            assertTrue(movie.localUri != episode.localUri)

            val moviePart = File(movie.storagePath, "${movie.fileName}.part").apply {
                writeBytes(content.copyOfRange(0, 32))
            }
            server.enqueue(rangeResponse(0, 0, content.size, content))
            server.enqueue(rangeResponse(32, content.lastIndex, content.size, content))

            movieLease = DurableDownloadExecutionLeaseRegistry.claim(fixture.accountId, movie.downloadId)
            assertTrue(repository.resume(movie.downloadId))
            val completedMovie = awaitRecord(repository, movie.downloadId) {
                it.status == OfflineStatus.COMPLETED
            }
            val movieFile = File(checkNotNull(completedMovie.storagePath), checkNotNull(completedMovie.fileName))
            assertTrue(movieFile.isFile)
            assertTrue(content.contentEquals(movieFile.readBytes()))
            assertFalse(moviePart.exists())

            server.enqueue(rangeResponse(0, 0, content.size, content))
            server.enqueue(rangeResponse(0, content.lastIndex, content.size, content))
            episodeLease = DurableDownloadExecutionLeaseRegistry.claim(fixture.accountId, episode.downloadId)
            assertTrue(repository.resume(episode.downloadId))
            val completedEpisode = awaitRecord(repository, episode.downloadId) {
                it.status == OfflineStatus.COMPLETED
            }
            val episodeFile = File(
                checkNotNull(completedEpisode.storagePath),
                checkNotNull(completedEpisode.fileName),
            )
            assertTrue(episodeFile.isFile)
            assertTrue(content.contentEquals(episodeFile.readBytes()))
            assertNotEquals(movieFile.absolutePath, episodeFile.absolutePath)

            repository.removeOwner(movie.downloadId, PROFILE_ID)
            assertFalse(movieFile.exists())
            assertTrue(episodeFile.isFile)
            assertTrue(content.contentEquals(episodeFile.readBytes()))
        } finally {
            repository.suspendForAccountBoundary()
            movieLease?.let(DurableDownloadExecutionLeaseRegistry::release)
            episodeLease?.let(DurableDownloadExecutionLeaseRegistry::release)
            server.shutdown()
            AuthenticatedSessionRegistry.clear()
        }
    }

    @Test
    fun `legacy shared physical path is separated while the original is preserved`() {
        val fixture = Fixture()
        val original = fixture.createLegacyFile("Same_9001.mp4", "ORIGINAL")
        fixture.seedRecord(
            downloadId = 1L,
            historyKey = "MOVIE:9001",
            fileName = original.name,
            status = OfflineStatus.COMPLETED,
            ownerProfiles = setOf(PROFILE_ID),
        )
        fixture.seedRecord(
            downloadId = 2L,
            historyKey = "SERIES:9001",
            fileName = original.name,
            status = OfflineStatus.PAUSED,
            ownerProfiles = setOf(PROFILE_ID),
        )

        val repository = fixture.repository()
        try {
            val movie = checkNotNull(repository.record(1L))
            val episode = checkNotNull(repository.record(2L))
            assertNotEquals(movie.fileName, episode.fileName)
            assertNotEquals(original.name, movie.fileName)
            assertNotEquals(original.name, episode.fileName)
            assertEquals(OfflineStatus.FAILED, movie.status)
            assertEquals(OfflineStatus.FAILED, episode.status)
            assertEquals("ORIGINAL", original.readText())
            assertFalse(File(checkNotNull(movie.storagePath), checkNotNull(movie.fileName)).exists())
            assertFalse(File(checkNotNull(episode.storagePath), checkNotNull(episode.fileName)).exists())

            val persisted = DownloadIndexStore(fixture.preferences).read().records
            assertEquals(2, persisted.size)
            assertNotEquals(persisted[0].fileName, persisted[1].fileName)
            assertTrue(persisted.none { it.fileName == original.name })

            repository.removeOwner(1L, PROFILE_ID)
            assertEquals("ORIGINAL", original.readText())
            assertTrue(repository.record(2L) != null)
        } finally {
            repository.suspendForAccountBoundary()
            AuthenticatedSessionRegistry.clear()
        }
    }

    @Test
    fun `unique completed and partial records keep their names and files`() {
        val fixture = Fixture()
        val completed = fixture.createLegacyFile("completed_movie.mp4", "DONE")
        val partial = File(fixture.tempDirectory, "partial_episode.mp4.part").apply {
            writeText("PART")
        }
        fixture.seedRecord(
            downloadId = 1L,
            historyKey = "MOVIE:1",
            fileName = completed.name,
            status = OfflineStatus.COMPLETED,
            totalBytes = completed.length(),
            ownerProfiles = setOf(PROFILE_ID),
        )
        fixture.seedRecord(
            downloadId = 2L,
            historyKey = "SERIES:2",
            fileName = "partial_episode.mp4",
            status = OfflineStatus.PAUSED,
            bytesDownloaded = partial.length(),
            totalBytes = 100L,
            ownerProfiles = setOf(PROFILE_ID),
        )

        val repository = fixture.repository()
        try {
            assertEquals(completed.name, repository.record(1L)?.fileName)
            assertEquals("partial_episode.mp4", repository.record(2L)?.fileName)
            assertEquals(OfflineStatus.COMPLETED, repository.record(1L)?.status)
            assertEquals(OfflineStatus.PAUSED, repository.record(2L)?.status)
            assertEquals("DONE", completed.readText())
            assertEquals("PART", partial.readText())
            assertTrue(repository.resume(2L))
            assertEquals("partial_episode.mp4", repository.record(2L)?.fileName)
        } finally {
            repository.suspendForAccountBoundary()
            AuthenticatedSessionRegistry.clear()
        }
    }

    @Test
    fun `failed index persistence still leaves the original intact and the in-memory paths safe`() {
        val fixture = Fixture()
        val original = fixture.createLegacyFile("Same_9001.mp4", "ORIGINAL")
        fixture.seedRecord(
            downloadId = 1L,
            historyKey = "MOVIE:9001",
            fileName = original.name,
            status = OfflineStatus.COMPLETED,
            ownerProfiles = setOf(PROFILE_ID),
        )
        fixture.seedRecord(
            downloadId = 2L,
            historyKey = "SERIES:9001",
            fileName = original.name,
            status = OfflineStatus.PAUSED,
            ownerProfiles = setOf(PROFILE_ID),
        )
        fixture.preferences.commitResult = false

        val repository = fixture.repository()
        try {
            val movie = checkNotNull(repository.record(1L))
            val episode = checkNotNull(repository.record(2L))
            assertNotEquals(movie.fileName, episode.fileName)
            assertNotEquals(original.name, movie.fileName)
            assertNotEquals(original.name, episode.fileName)
            assertEquals("ORIGINAL", original.readText())
            val persistedRaw = fixture.preferences.durableValue(KEY_DOWNLOADS) as? String ?: ""
            assertTrue(persistedRaw.contains(original.name))
        } finally {
            repository.suspendForAccountBoundary()
            AuthenticatedSessionRegistry.clear()
        }
    }

    private class Fixture(
        providerBaseUrl: String = "https://provider.example.test",
    ) {
        val accountId = "account-${UUID.randomUUID()}"
        val preferences = FakeSharedPreferences()
        val tempDirectory: File = Files.createTempDirectory("hulk-download-identity").toFile()
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

        fun createLegacyFile(name: String, content: String): File =
            File(tempDirectory, name).apply { writeText(content) }

        fun seedRecord(
            downloadId: Long,
            historyKey: String,
            fileName: String,
            status: OfflineStatus,
            bytesDownloaded: Long = 0L,
            totalBytes: Long = -1L,
            ownerProfiles: Set<String> = setOf(PROFILE_ID),
        ) {
            val record = JSONObject()
                .put("downloadId", downloadId)
                .put("historyKey", historyKey)
                .put("title", "Same Title")
                .put("streamKind", if (historyKey.startsWith("MOVIE:")) "movie" else "series")
                .put("streamId", 9001)
                .put("extension", "mp4")
                .put("fileName", fileName)
                .put("storagePath", tempDirectory.absolutePath)
                .put("storageLabel", "internal")
                .put("supportsRange", true)
                .put("status", status.name)
                .put("localUri", "file://" + File(tempDirectory, fileName).absolutePath)
                .put("bytesDownloaded", bytesDownloaded)
                .put("totalBytes", totalBytes)
                .put("ownerProfileIds", JSONArray(ownerProfiles.sorted()))
                .put("createdAtEpochMs", 1_000L)
            val existing = preferences.durableValue(KEY_DOWNLOADS)
                ?.let { runCatching { JSONArray(it as String) }.getOrNull() }
                ?: JSONArray()
            existing.put(record)
            preferences.edit()
                .putString(KEY_DOWNLOADS, existing.toString())
                .putInt(
                    DurableDownloadPreferenceStore.KEY_STORAGE_VERSION,
                    DurableDownloadPreferenceStore.STORAGE_VERSION,
                )
                .apply()
        }
    }

    private fun record(
        downloadId: Long,
        historyKey: String,
        title: String,
        streamId: Int,
        status: OfflineStatus,
    ): OfflineDownload = OfflineDownload(
        downloadId = downloadId,
        historyKey = historyKey,
        title = title,
        posterUrl = null,
        streamKind = if (historyKey.startsWith("MOVIE:")) "movie" else "series",
        streamId = streamId,
        extension = "mp4",
        fileName = buildDownloadFileName(historyKey, title, streamId, "mp4"),
        storagePath = "/tmp/hulk-download-identity",
        status = status,
    )

    private fun request(
        historyKey: String,
        candidate: String = "https://cdn.example.test/content/9001.mp4",
    ): PlaybackRequest = PlaybackRequest(
        title = "Same Title",
        posterUrl = null,
        candidates = listOf(candidate),
        isLive = false,
        historyKey = historyKey,
        streamKind = if (historyKey.startsWith("MOVIE:")) "movie" else "series",
        streamId = 9001,
        extension = "mp4",
    )

    private fun rangeResponse(
        first: Int,
        last: Int,
        total: Int,
        content: ByteArray,
    ): MockResponse = MockResponse()
        .setResponseCode(206)
        .setHeader("Content-Range", "bytes $first-$last/$total")
        .setHeader("Content-Length", (last - first + 1).toString())
        .setBody(Buffer().write(content, first, last - first + 1))

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

    private companion object {
        const val PROFILE_ID = "profile-1"
        const val KEY_DOWNLOADS = DurableDownloadPreferenceStore.KEY_DOWNLOADS
    }
}
