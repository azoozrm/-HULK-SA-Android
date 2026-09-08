package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus

class DownloadPauseDurabilityTest {
    @Test
    fun `paused checkpoint survives process recovery while active transport is requeued`() {
        val active = download(status = OfflineStatus.DOWNLOADING)
        val paused = pausedDownloadRecord(active)

        assertEquals(OfflineStatus.PAUSED, paused.status)
        assertEquals(OfflineStatus.PAUSED, recoverInterruptedDownloadState(paused).status)
        assertEquals(OfflineStatus.QUEUED, recoverInterruptedDownloadState(active).status)
    }

    @Test
    fun `transport is cancelled only after pause checkpoint commits`() {
        assertTrue(shouldCancelDownloadForPause(persisted = true))
        assertFalse(shouldCancelDownloadForPause(persisted = false))
    }

    private fun download(status: OfflineStatus) = OfflineDownload(
        downloadId = 42L,
        historyKey = "movie:42",
        title = "Movie",
        posterUrl = null,
        streamKind = "movie",
        streamId = 42,
        extension = "mp4",
        status = status,
    )
}
