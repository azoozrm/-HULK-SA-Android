package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus

/**
 * The textual download telemetry in the Home active-download card follows the same known-total
 * policy as the progress track: an unknown total never invents a percentage, while a known total
 * keeps the real value and the real transfer rate stays available.
 */
class DownloadStatusLinePolicyTest {
    @Test
    fun `downloading with unknown total shows no invented percentage`() {
        val line = downloadStatusLine(
            download(status = OfflineStatus.DOWNLOADING, bytesDownloaded = 0L, totalBytes = -1L, rate = 2_048L),
        )
        assertFalse(line.contains("%"))
        assertTrue(line.isNotBlank())
    }

    @Test
    fun `downloading with a known total keeps the real percentage and rate`() {
        val line = downloadStatusLine(
            download(
                status = OfflineStatus.DOWNLOADING,
                bytesDownloaded = 42L,
                totalBytes = 100L,
                rate = 1_800_000L,
            ),
        )
        assertTrue(line.startsWith("42%"))
        assertTrue(line.contains("MB"))
    }

    @Test
    fun `paused with unknown total shows no fabricated percentage`() {
        val line = downloadStatusLine(
            download(status = OfflineStatus.PAUSED, bytesDownloaded = 0L, totalBytes = -1L),
        )
        assertFalse(line.contains("%"))
    }

    @Test
    fun `paused with a known total keeps the real percentage`() {
        assertEquals(
            "37%",
            downloadStatusLine(
                download(status = OfflineStatus.PAUSED, bytesDownloaded = 37L, totalBytes = 100L),
            ),
        )
    }

    private fun download(
        status: OfflineStatus,
        bytesDownloaded: Long,
        totalBytes: Long,
        rate: Long = 0L,
    ): OfflineDownload = OfflineDownload(
        downloadId = 1L,
        historyKey = "MOVIE:1",
        title = "فيلم",
        posterUrl = null,
        streamKind = "movie",
        streamId = 1,
        extension = "mp4",
        status = status,
        bytesDownloaded = bytesDownloaded,
        totalBytes = totalBytes,
        bytesPerSecond = rate,
    )
}
