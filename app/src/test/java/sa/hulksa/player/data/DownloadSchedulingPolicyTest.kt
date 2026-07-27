package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus

class DownloadSchedulingPolicyTest {
    @Test
    fun futureNightScheduleBlocksUntilItsStartTime() {
        val item = download(
            status = OfflineStatus.WAITING_SCHEDULE,
            scheduledAtEpochMs = 10_000L,
        )

        val blocked = decideDownloadAttempt(
            item = item,
            nowEpochMs = 9_999L,
            networkAvailable = true,
            storageAvailable = true,
        )
        val allowed = decideDownloadAttempt(
            item = item,
            nowEpochMs = 10_000L,
            networkAvailable = true,
            storageAvailable = true,
        )

        assertFalse(blocked.canRun)
        assertEquals(DownloadBlockReason.SCHEDULE, blocked.blockReason)
        assertTrue(allowed.canRun)
        assertNull(allowed.blockReason)
    }

    @Test
    fun waitingNetworkNeedsConnectivity() {
        val item = download(status = OfflineStatus.WAITING_NETWORK)

        val blocked = decideDownloadAttempt(item, 0L, networkAvailable = false, storageAvailable = true)
        val allowed = decideDownloadAttempt(item, 0L, networkAvailable = true, storageAvailable = true)

        assertEquals(DownloadBlockReason.NETWORK, blocked.blockReason)
        assertTrue(allowed.canRun)
    }

    @Test
    fun waitingStorageNeedsTheOriginalStorageTarget() {
        val item = download(status = OfflineStatus.WAITING_STORAGE)

        val blocked = decideDownloadAttempt(item, 0L, networkAvailable = true, storageAvailable = false)
        val allowed = decideDownloadAttempt(item, 0L, networkAvailable = true, storageAvailable = true)

        assertEquals(DownloadBlockReason.STORAGE, blocked.blockReason)
        assertTrue(allowed.canRun)
    }

    @Test
    fun queuedItemIsRunnableWhenNoExplicitWaitStateExists() {
        val decision = decideDownloadAttempt(
            item = download(status = OfflineStatus.QUEUED),
            nowEpochMs = 0L,
            networkAvailable = true,
            storageAvailable = true,
        )

        assertTrue(decision.canRun)
        assertNull(decision.blockReason)
    }

    private fun download(
        status: OfflineStatus,
        scheduledAtEpochMs: Long = 0L,
    ): OfflineDownload = OfflineDownload(
        downloadId = 1L,
        historyKey = "movie:1",
        title = "Movie",
        posterUrl = null,
        streamKind = "movie",
        streamId = 1,
        extension = "mp4",
        sourceCandidates = listOf("https://example.invalid/movie.mp4"),
        status = status,
        scheduledAtEpochMs = scheduledAtEpochMs,
    )
}
