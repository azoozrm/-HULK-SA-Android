package sa.hulksa.player.ui.screens

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import sa.hulksa.player.model.OfflineDownload

class DownloadsStorageSpaceQueryTest {
    @Test
    fun observationKeyTracksStoredBytesAndMembershipOnly() {
        val original = download(downloadId = 1L, bytesDownloaded = 10L)
        val unrelatedUpdate = original.copy(bytesPerSecond = 2_048L, priority = 1)
        val storedBytesUpdate = original.copy(bytesDownloaded = 20L)
        val added = download(downloadId = 2L, bytesDownloaded = 0L)

        val originalKey = downloadStorageObservationKey(listOf(original))

        assertEquals(originalKey, downloadStorageObservationKey(listOf(unrelatedUpdate)))
        assertNotEquals(originalKey, downloadStorageObservationKey(listOf(storedBytesUpdate)))
        assertNotEquals(originalKey, downloadStorageObservationKey(listOf(original, added)))
        assertNotEquals(originalKey, downloadStorageObservationKey(emptyList()))
    }

    @Test
    fun storageReadRunsOffCallerAndUsesStatFsValue() {
        val callerThread = Thread.currentThread()
        var observedThread: Thread? = null

        val result = runBlocking {
            readAvailableDownloadStorageBytes(
                storageRootProvider = { File(".") },
                statFsAvailableBytes = {
                    observedThread = Thread.currentThread()
                    4_096L
                },
                usableSpaceBytes = { error("fallback should not run") },
            )
        }

        assertEquals(4_096L, result)
        assertNotNull(observedThread)
        assertNotEquals(callerThread, observedThread)
    }

    @Test
    fun storageReadFallsBackAndReturnsZeroWhenBothReadsFail() {
        val fallback = runBlocking {
            readAvailableDownloadStorageBytes(
                storageRootProvider = { File(".") },
                statFsAvailableBytes = { error("statfs failed") },
                usableSpaceBytes = { 2_048L },
            )
        }
        val unavailable = runBlocking {
            readAvailableDownloadStorageBytes(
                storageRootProvider = { File(".") },
                statFsAvailableBytes = { error("statfs failed") },
                usableSpaceBytes = { error("usableSpace failed") },
            )
        }

        assertEquals(2_048L, fallback)
        assertEquals(0L, unavailable)
    }

    @Test
    fun storageReadDoesNotSwallowCancellation() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                readAvailableDownloadStorageBytes(
                    storageRootProvider = { File(".") },
                    statFsAvailableBytes = { throw CancellationException("cancelled") },
                    usableSpaceBytes = { 1L },
                )
            }
        }
    }

    private fun download(
        downloadId: Long,
        bytesDownloaded: Long,
    ): OfflineDownload = OfflineDownload(
        downloadId = downloadId,
        historyKey = "history-$downloadId",
        title = "Download $downloadId",
        posterUrl = null,
        streamKind = "movie",
        streamId = downloadId.toInt(),
        extension = "mp4",
        bytesDownloaded = bytesDownloaded,
    )
}
