package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.data.DownloadActionIntent
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus

/**
 * The TV downloads row must stay truthful: an unknown total never fabricates a percentage,
 * total size or ETA; a known total keeps its real percent/byte pair and a track only while
 * transfer is meaningful; a completed row reserves no decorative track.
 */
class DownloadTvRowPolicyTest {
    @Test
    fun `downloading with unknown total invents no percent track total or eta`() {
        val telemetry = telemetry(
            download(
                status = OfflineStatus.DOWNLOADING,
                bytesDownloaded = 12L,
                totalBytes = -1L,
                rate = 2_048L,
            ),
        )

        assertNull(telemetry.percent)
        assertFalse(telemetry.showTrack)
        assertEquals("12 B", telemetry.sizeLine)
        assertNull(telemetry.detailLine)
    }

    @Test
    fun `downloading with known total keeps the real percent and byte pair`() {
        val telemetry = telemetry(
            download(
                status = OfflineStatus.DOWNLOADING,
                bytesDownloaded = 42L,
                totalBytes = 100L,
            ),
        )

        assertEquals(42, telemetry.percent)
        assertTrue(telemetry.showTrack)
        assertEquals("42 B / 100 B", telemetry.sizeLine)
    }

    @Test
    fun `paused with known total keeps the real percent`() {
        val telemetry = telemetry(
            download(status = OfflineStatus.PAUSED, bytesDownloaded = 37L, totalBytes = 100L),
        )

        assertEquals(37, telemetry.percent)
        assertTrue(telemetry.showTrack)
    }

    @Test
    fun `completed row shows its real size without a decorative track`() {
        val telemetry = telemetry(
            download(status = OfflineStatus.COMPLETED, bytesDownloaded = 100L, totalBytes = 100L),
        )

        assertNull(telemetry.percent)
        assertFalse(telemetry.showTrack)
        assertEquals("100 B", telemetry.sizeLine)
    }

    @Test
    fun `waiting and queued states do not display a fabricated percent`() {
        listOf(
            OfflineStatus.QUEUED,
            OfflineStatus.CHECKING,
            OfflineStatus.WAITING_SCHEDULE,
            OfflineStatus.WAITING_NETWORK,
            OfflineStatus.WAITING_STORAGE,
        ).forEach { status ->
            val telemetry = telemetry(
                download(status = status, bytesDownloaded = 0L, totalBytes = 100L),
            )
            assertNull("$status", telemetry.percent)
            assertFalse("$status", telemetry.showTrack)
        }
    }

    @Test
    fun `failed row keeps its real error detail`() {
        val telemetry = telemetry(
            download(
                status = OfflineStatus.FAILED,
                bytesDownloaded = 10L,
                totalBytes = -1L,
                error = "تعذر الاتصال بالخادم",
            ),
        )

        assertEquals("تعذر الاتصال بالخادم", telemetry.detailLine)
    }

    @Test
    fun `scheduled row keeps its real schedule detail`() {
        val telemetry = telemetry(
            download(
                status = OfflineStatus.WAITING_SCHEDULE,
                bytesDownloaded = 0L,
                totalBytes = -1L,
                scheduledAtEpochMs = 5_000L,
            ),
        )

        assertEquals("سيبدا 02:00", telemetry.detailLine)
    }

    @Test
    fun `action routing and captions follow the existing owner eligibility`() {
        assertEquals(DownloadTvRowAction.PLAY, downloadTvRowAction(OfflineStatus.COMPLETED))
        assertEquals(DownloadTvRowAction.RETRY, downloadTvRowAction(OfflineStatus.FAILED))
        listOf(
            OfflineStatus.PAUSED,
            OfflineStatus.WAITING_SCHEDULE,
            OfflineStatus.WAITING_NETWORK,
            OfflineStatus.WAITING_STORAGE,
        ).forEach { status ->
            assertEquals("$status", DownloadTvRowAction.RESUME, downloadTvRowAction(status))
        }
        listOf(
            OfflineStatus.QUEUED,
            OfflineStatus.CHECKING,
            OfflineStatus.DOWNLOADING,
        ).forEach { status ->
            assertEquals("$status", DownloadTvRowAction.PAUSE, downloadTvRowAction(status))
        }

        assertEquals("تشغيل", downloadTvRowActionCaption(DownloadTvRowAction.PLAY))
        assertEquals("اعادة التحميل", downloadTvRowActionCaption(DownloadTvRowAction.RETRY))
        assertEquals("استئناف", downloadTvRowActionCaption(DownloadTvRowAction.RESUME))
        assertEquals("ايقاف", downloadTvRowActionCaption(DownloadTvRowAction.PAUSE))
    }

    @Test
    fun `captured row action maps to one explicit intent`() {
        assertNull(downloadTvRowActionIntent(DownloadTvRowAction.PLAY))
        assertEquals(
            DownloadActionIntent.RETRY,
            downloadTvRowActionIntent(DownloadTvRowAction.RETRY),
        )
        assertEquals(
            DownloadActionIntent.RESUME,
            downloadTvRowActionIntent(DownloadTvRowAction.RESUME),
        )
        assertEquals(
            DownloadActionIntent.PAUSE,
            downloadTvRowActionIntent(DownloadTvRowAction.PAUSE),
        )
        assertEquals(DownloadActionIntent.RETRY, downloadPrimaryActionIntent(OfflineStatus.FAILED))
        assertEquals(DownloadActionIntent.RESUME, downloadPrimaryActionIntent(OfflineStatus.PAUSED))
        assertEquals(DownloadActionIntent.PAUSE, downloadPrimaryActionIntent(OfflineStatus.DOWNLOADING))
        assertNull(downloadPrimaryActionIntent(OfflineStatus.COMPLETED))
    }

    @Test
    fun `status wording keeps the accepted distinctions`() {
        assertEquals("تم التحميل", downloadTvRowStatusLabel(OfflineStatus.COMPLETED))
        assertEquals("جاري التحميل", downloadTvRowStatusLabel(OfflineStatus.DOWNLOADING))
        assertEquals("متوقف", downloadTvRowStatusLabel(OfflineStatus.PAUSED))
        assertEquals("في انتظار الشبكة", downloadTvRowStatusLabel(OfflineStatus.WAITING_NETWORK))
    }

    private fun telemetry(item: OfflineDownload): DownloadTvRowTelemetry =
        downloadTvRowTelemetry(
            item = item,
            formatBytes = { bytes -> "$bytes B" },
            scheduledLabel = { "02:00" },
        )

    private fun download(
        status: OfflineStatus,
        bytesDownloaded: Long,
        totalBytes: Long,
        rate: Long = 0L,
        error: String? = null,
        scheduledAtEpochMs: Long = 0L,
    ): OfflineDownload = OfflineDownload(
        downloadId = 1L,
        historyKey = "MOVIE:1",
        title = "فلم",
        posterUrl = null,
        streamKind = "movie",
        streamId = 1,
        extension = "mp4",
        status = status,
        bytesDownloaded = bytesDownloaded,
        totalBytes = totalBytes,
        bytesPerSecond = rate,
        errorMessage = error,
        scheduledAtEpochMs = scheduledAtEpochMs,
    )
}
