package sa.hulksa.player

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus

class DownloadUiSnapshotDispatchTest {
    @Test
    fun `ui snapshot loader runs exactly once away from caller thread`() = runBlocking {
        val callerThreadId = Thread.currentThread().id
        val calls = AtomicInteger(0)
        var loaderThreadId = callerThreadId
        val expected = listOf(
            OfflineDownload(
                downloadId = 1L,
                historyKey = "movie:1",
                title = "Movie",
                posterUrl = null,
                streamKind = "movie",
                streamId = 1,
                extension = "mp4",
                sourceCandidates = emptyList(),
                status = OfflineStatus.DOWNLOADING,
                bytesDownloaded = 4_096L,
                totalBytes = 8_192L,
            ),
        )

        val actual = loadDownloadUiSnapshot {
            calls.incrementAndGet()
            loaderThreadId = Thread.currentThread().id
            expected
        }

        assertEquals(expected, actual)
        assertEquals(1, calls.get())
        assertNotEquals(callerThreadId, loaderThreadId)
    }
}
