package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus

class AccountSessionDownloadIsolationTest {
    @Test
    fun `account mismatch is rejected when immutable profile snapshot is built`() {
        val item = download(downloadId = 1L, historyKey = "movie:1")
        val snapshot = ProfileDownloadSnapshotList(
            accountId = "account-a",
            allDownloads = listOf(item),
            activeAccountId = { "account-b" },
            activeProfileId = { "primary" },
            ownersForExistingDownload = { setOf("primary") },
        )

        assertTrue(snapshot.isEmpty())
        assertFalse(
            accountDownloadAccessAllowed(
                recordAccountId = "account-a",
                activeAccountId = "account-b",
                profileOwnsRecord = true,
            ),
        )
    }

    @Test
    fun `profile filtering is resolved once and list accessors reuse the same snapshot`() {
        val visible = download(downloadId = 2L, historyKey = "movie:visible")
        val hidden = download(downloadId = 3L, historyKey = "movie:hidden")
        var accountReads = 0
        var profileReads = 0
        var ownerReads = 0
        val snapshot = ProfileDownloadSnapshotList(
            accountId = "account-a",
            allDownloads = listOf(visible, hidden),
            activeAccountId = {
                accountReads += 1
                "account-a"
            },
            activeProfileId = {
                profileReads += 1
                "profile-1"
            },
            ownersForExistingDownload = { historyKey ->
                ownerReads += 1
                if (historyKey == visible.historyKey) setOf("profile-1") else setOf("profile-2")
            },
        )

        assertEquals(1, snapshot.size)
        assertSame(visible, snapshot[0])
        assertSame(visible, snapshot.iterator().next())
        assertSame(visible, snapshot.listIterator().next())
        assertEquals(1, accountReads)
        assertEquals(1, profileReads)
        assertEquals(2, ownerReads)
    }

    @Test
    fun `new snapshot reflects profile switch without changing old immutable snapshot`() {
        var activeProfileId = "profile-1"
        val shared = download(downloadId = 4L, historyKey = "movie:shared")
        val profileOneOnly = download(downloadId = 5L, historyKey = "movie:one")
        val owners = mapOf(
            shared.historyKey to setOf("profile-1", "profile-2"),
            profileOneOnly.historyKey to setOf("profile-1"),
        )
        fun snapshot() = ProfileDownloadSnapshotList(
            accountId = "account-a",
            allDownloads = listOf(shared, profileOneOnly),
            activeAccountId = { "account-a" },
            activeProfileId = { activeProfileId },
            ownersForExistingDownload = { owners.getValue(it) },
        )

        val profileOneSnapshot = snapshot()
        assertEquals(listOf(shared, profileOneOnly), profileOneSnapshot.toList())

        activeProfileId = "profile-2"
        val profileTwoSnapshot = snapshot()
        assertEquals(listOf(shared), profileTwoSnapshot.toList())
        assertEquals(listOf(shared, profileOneOnly), profileOneSnapshot.toList())
    }

    @Test
    fun `profile snapshot preserves active progress and completed state`() {
        val downloading = download(
            downloadId = 6L,
            historyKey = "movie:progress",
            status = OfflineStatus.DOWNLOADING,
        ).copy(bytesDownloaded = 3_000L, totalBytes = 10_000L, bytesPerSecond = 500L)
        val completed = download(
            downloadId = 7L,
            historyKey = "movie:completed",
            status = OfflineStatus.COMPLETED,
        ).copy(bytesDownloaded = 10_000L, totalBytes = 10_000L, integrityVerified = true)
        val snapshot = ProfileDownloadSnapshotList(
            accountId = "account-a",
            allDownloads = listOf(downloading, completed),
            activeAccountId = { "account-a" },
            activeProfileId = { "profile-1" },
            ownersForExistingDownload = { setOf("profile-1") },
        )

        assertEquals(OfflineStatus.DOWNLOADING, snapshot[0].status)
        assertEquals(3_000L, snapshot[0].bytesDownloaded)
        assertEquals(500L, snapshot[0].bytesPerSecond)
        assertEquals(OfflineStatus.COMPLETED, snapshot[1].status)
        assertTrue(snapshot[1].integrityVerified)
    }

    @Test
    fun `removing one profile reference preserves the shared physical download`() {
        val firstRemoval = profileReferenceRemoval(
            owners = setOf("profile-1", "profile-2"),
            profileId = "profile-1",
        )
        assertEquals(setOf("profile-2"), firstRemoval.remainingOwners)
        assertFalse(firstRemoval.deletePhysicalDownload)

        val lastRemoval = profileReferenceRemoval(
            owners = firstRemoval.remainingOwners,
            profileId = "profile-2",
        )
        assertTrue(lastRemoval.remainingOwners.isEmpty())
        assertTrue(lastRemoval.deletePhysicalDownload)
    }

    @Test
    fun `download metadata physical paths and workers are account scoped not profile scoped`() {
        assertNotEquals(
            downloadAccountStorageKey("account-a"),
            downloadAccountStorageKey("account-b"),
        )
        assertNotEquals(
            downloadAccountDirectoryName("account-a"),
            downloadAccountDirectoryName("account-b"),
        )
        assertNotEquals(
            accountScopedPreferencesName("hulk_downloads", "account-a"),
            accountScopedPreferencesName("hulk_downloads", "account-b"),
        )
        assertNotEquals(
            accountScopedPreferencesName("hulk_profile_download_ownership_v1", "account-a"),
            accountScopedPreferencesName("hulk_profile_download_ownership_v1", "account-b"),
        )
        assertNotEquals(
            durableDownloadUniqueWorkName("account-a", 42L),
            durableDownloadUniqueWorkName("account-b", 42L),
        )
    }

    @Test
    fun `worker requires its account record and the matching active session`() {
        val accountA = metadata("account-a")
        val accountB = metadata("account-b")

        assertEquals(
            DownloadWorkerSessionGate.ALLOW,
            downloadWorkerSessionGate(
                workerAccountId = "account-a",
                activeAccountId = "account-a",
                metadata = accountA,
                hasAuthenticatedSession = true,
                authenticatedSessionMatches = true,
            ),
        )
        assertTrue(
            downloadWorkerOwnsRecord(
                workerAccountId = "account-a",
                repositoryAccountId = "account-a",
                recordExists = true,
            ),
        )
        assertEquals(
            DownloadWorkerSessionGate.TERMINAL,
            downloadWorkerSessionGate(
                workerAccountId = "account-a",
                activeAccountId = "account-b",
                metadata = accountB,
                hasAuthenticatedSession = true,
                authenticatedSessionMatches = false,
            ),
        )
        assertFalse(
            downloadWorkerOwnsRecord(
                workerAccountId = "account-b",
                repositoryAccountId = "account-a",
                recordExists = true,
            ),
        )
    }

    @Test
    fun `process death retries without transport and reboot under another account terminates`() {
        assertEquals(
            DownloadWorkerSessionGate.RETRY,
            downloadWorkerSessionGate(
                workerAccountId = "account-a",
                activeAccountId = "account-a",
                metadata = metadata("account-a"),
                hasAuthenticatedSession = false,
                authenticatedSessionMatches = false,
            ),
        )
        assertEquals(
            DownloadWorkerSessionGate.TERMINAL,
            downloadWorkerSessionGate(
                workerAccountId = "account-a",
                activeAccountId = "account-b",
                metadata = metadata("account-b"),
                hasAuthenticatedSession = true,
                authenticatedSessionMatches = false,
            ),
        )
    }

    @Test
    fun `worker remains fail closed during login binding and terminates after logout`() {
        assertEquals(
            DownloadWorkerSessionGate.RETRY,
            downloadWorkerSessionGate(
                workerAccountId = "account-a",
                activeAccountId = "account-a",
                metadata = null,
                hasAuthenticatedSession = false,
                authenticatedSessionMatches = false,
            ),
        )
        assertEquals(
            DownloadWorkerSessionGate.TERMINAL,
            downloadWorkerSessionGate(
                workerAccountId = "account-a",
                activeAccountId = null,
                metadata = null,
                hasAuthenticatedSession = false,
                authenticatedSessionMatches = false,
            ),
        )
    }

    @Test
    fun `logout pauses active work while preserving paused and completed records`() {
        val records = listOf(
            download(1L, "queued", OfflineStatus.QUEUED),
            download(2L, "downloading", OfflineStatus.DOWNLOADING),
            download(3L, "paused", OfflineStatus.PAUSED),
            download(4L, "completed", OfflineStatus.COMPLETED),
        )

        val suspended = suspendDownloadsForAccountBoundary(records)

        assertEquals(OfflineStatus.PAUSED, suspended[0].status)
        assertEquals(OfflineStatus.PAUSED, suspended[1].status)
        assertEquals(OfflineStatus.PAUSED, suspended[2].status)
        assertEquals(OfflineStatus.COMPLETED, suspended[3].status)
    }

    @Test
    fun `legacy metadata migrates only to the previously captured owner`() {
        assertEquals(
            LegacyDownloadMigrationPolicy.MIGRATE,
            legacyDownloadMigrationPolicy("account-a", "account-a"),
        )
        assertEquals(
            LegacyDownloadMigrationPolicy.QUARANTINE,
            legacyDownloadMigrationPolicy("account-a", "account-b"),
        )
        assertEquals(
            LegacyDownloadMigrationPolicy.QUARANTINE,
            legacyDownloadMigrationPolicy(null, "account-a"),
        )
    }

    private fun metadata(accountId: String): AccountSessionMetadata = AccountSessionMetadata(
        accountId = accountId,
        username = "user",
        portalBaseUrl = "https://portal.example",
        authenticatedAtEpochMs = 1L,
        expiresAtEpochSeconds = Long.MAX_VALUE,
        status = "Active",
        installationId = "installation",
        sessionId = "session",
    )

    private fun download(
        downloadId: Long,
        historyKey: String,
        status: OfflineStatus = OfflineStatus.COMPLETED,
    ): OfflineDownload = OfflineDownload(
        downloadId = downloadId,
        historyKey = historyKey,
        title = historyKey,
        posterUrl = null,
        streamKind = "movie",
        streamId = downloadId.toInt(),
        extension = "mp4",
        sourceCandidates = listOf("https://example.invalid/content.mp4"),
        localUri = "file:///downloads/$historyKey.mp4",
        status = status,
    )
}
