package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus

class DownloadPersistenceContractTest {
    @Test
    fun `crash before atomic enqueue commit cannot restore an ownership-only record`() {
        val restored = reconcilePersistedDownloadOwnership(
            downloads = emptyList(),
            useLegacyOwnership = true,
            legacyOwners = { setOf("profile-1") },
        )

        assertTrue(restored.isEmpty())
    }

    @Test
    fun `crash during remove restores either the complete owned record or no record`() {
        val beforeCommit = download(ownerProfileIds = setOf("profile-1"))
        val restoredBeforeCommit = reconcilePersistedDownloadOwnership(
            downloads = listOf(beforeCommit),
            useLegacyOwnership = false,
            legacyOwners = { null },
        )
        val restoredAfterCommit = reconcilePersistedDownloadOwnership(
            downloads = emptyList(),
            useLegacyOwnership = true,
            legacyOwners = { setOf("profile-1") },
        )

        assertEquals(listOf(beforeCommit), restoredBeforeCommit)
        assertTrue(restoredAfterCommit.isEmpty())
    }

    @Test
    fun `startup reconciliation repairs partial legacy state and ignores orphan owners`() {
        val ownedLegacyRecord = download(downloadId = 1L, historyKey = "movie:1")
        val ownerlessLegacyRecord = download(downloadId = 2L, historyKey = "movie:2")
        val legacyOwners = mapOf(
            "movie:1" to setOf(" profile-1 ", "profile-2"),
            "orphan" to setOf("profile-3"),
        )

        val restored = reconcilePersistedDownloadOwnership(
            downloads = listOf(ownedLegacyRecord, ownerlessLegacyRecord),
            useLegacyOwnership = true,
            legacyOwners = legacyOwners::get,
        )

        assertEquals(setOf("profile-1", "profile-2"), restored[0].ownerProfileIds)
        assertEquals(setOf(ProfileStore.PRIMARY_PROFILE_ID), restored[1].ownerProfileIds)
        assertTrue(restored.none { "profile-3" in it.ownerProfileIds })
    }

    @Test
    fun `progress-only update does not change durable scheduling`() {
        val before = download(
            status = OfflineStatus.DOWNLOADING,
            ownerProfileIds = setOf("profile-1"),
        )
        val progress = before.copy(
            bytesDownloaded = 4_096L,
            totalBytes = 16_384L,
            bytesPerSecond = 2_048L,
            etaSeconds = 6L,
        )

        assertTrue(isDownloadProgressOnlyChange(before, progress))
        assertFalse(durableDownloadSchedulingChanged(listOf(before), listOf(progress)))
    }

    @Test
    fun `real durable state change still changes scheduling`() {
        val downloading = download(
            status = OfflineStatus.DOWNLOADING,
            ownerProfileIds = setOf("profile-1"),
        )
        val paused = downloading.copy(status = OfflineStatus.PAUSED)

        assertFalse(isDownloadProgressOnlyChange(downloading, paused))
        assertTrue(durableDownloadSchedulingChanged(listOf(downloading), listOf(paused)))
    }

    @Test
    fun `legacy migration preserves downloads progress and exact profile ownership`() {
        val first = download(
            downloadId = 1L,
            historyKey = "movie:1",
            status = OfflineStatus.DOWNLOADING,
        ).copy(bytesDownloaded = 8_192L, totalBytes = 32_768L)
        val second = download(
            downloadId = 2L,
            historyKey = "series:2",
            status = OfflineStatus.PAUSED,
        ).copy(bytesDownloaded = 1_024L, totalBytes = 4_096L)

        val migrated = reconcilePersistedDownloadOwnership(
            downloads = listOf(first, second),
            useLegacyOwnership = true,
            legacyOwners = { historyKey ->
                when (historyKey) {
                    "movie:1" -> setOf("profile-1", "profile-2")
                    "series:2" -> setOf("profile-3")
                    else -> null
                }
            },
        )

        assertEquals(listOf(1L, 2L), migrated.map(OfflineDownload::downloadId))
        assertEquals(listOf(8_192L, 1_024L), migrated.map(OfflineDownload::bytesDownloaded))
        assertEquals(listOf(32_768L, 4_096L), migrated.map(OfflineDownload::totalBytes))
        assertEquals(setOf("profile-1", "profile-2"), migrated[0].ownerProfileIds)
        assertEquals(setOf("profile-3"), migrated[1].ownerProfileIds)
    }

    private fun download(
        downloadId: Long = 42L,
        historyKey: String = "movie:42",
        status: OfflineStatus = OfflineStatus.QUEUED,
        ownerProfileIds: Set<String> = emptySet(),
    ): OfflineDownload = OfflineDownload(
        downloadId = downloadId,
        historyKey = historyKey,
        title = historyKey,
        posterUrl = null,
        streamKind = "movie",
        streamId = downloadId.toInt(),
        extension = "mp4",
        status = status,
        ownerProfileIds = ownerProfileIds,
    )
}
