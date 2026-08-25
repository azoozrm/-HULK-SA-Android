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
    fun `account A snapshot becomes empty for account B with the same primary profile`() {
        var activeAccountId: String? = "account-a"
        var activeProfileId = "primary"
        val item = download(downloadId = 1L, historyKey = "movie:1")
        val snapshot = ProfileDownloadSnapshotList(
            accountId = "account-a",
            allDownloads = listOf(item),
            activeAccountId = { activeAccountId },
            activeProfileId = { activeProfileId },
            ownersForExistingDownload = { setOf("primary") },
        )

        assertEquals(listOf(item), snapshot.toList())
        assertFalse(
            accountDownloadAccessAllowed(
                recordAccountId = "account-a",
                activeAccountId = "account-b",
                profileOwnsRecord = true,
            ),
        )
        activeAccountId = null
        assertTrue(snapshot.isEmpty())
        activeAccountId = "account-b"
        assertTrue(snapshot.isEmpty())
        activeAccountId = "account-a"
        assertEquals(listOf(item), snapshot.toList())
    }

    @Test
    fun `profile visibility stays isolated while a physical record can have two owners`() {
        var activeProfileId = "profile-1"
        val shared = download(downloadId = 2L, historyKey = "movie:shared")
        val profileOwners = setOf("profile-1", "profile-2")
        val snapshot = ProfileDownloadSnapshotList(
            accountId = "account-a",
            allDownloads = listOf(shared),
            activeAccountId = { "account-a" },
            activeProfileId = { activeProfileId },
            ownersForExistingDownload = { profileOwners },
        )

        assertSame(shared, snapshot.single())
        activeProfileId = "profile-2"
        assertSame(shared, snapshot.single())
        activeProfileId = "profile-3"
        assertTrue(snapshot.isEmpty())
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
