package sa.hulksa.player

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.data.downloadRemovalContextMatches
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus

class DownloadDeletionDispatchTest {
    @Test
    fun `download removal runs exactly once away from caller thread`() = runBlocking {
        val callerThreadId = Thread.currentThread().id
        val calls = AtomicInteger(0)
        var removalThreadId = callerThreadId
        val expected = emptyList<OfflineDownload>()

        val actual = runDownloadRemovalOffMain {
            calls.incrementAndGet()
            removalThreadId = Thread.currentThread().id
            expected
        }

        assertEquals(expected, actual)
        assertEquals(1, calls.get())
        assertNotEquals(callerThreadId, removalThreadId)
    }

    @Test
    fun `download removal preserves repository failure snapshot`() = runBlocking {
        val failed = download(
            downloadId = 8L,
            status = OfflineStatus.FAILED,
        ).copy(errorMessage = "تعذر حذف ملفات التحميل.")

        val actual = runDownloadRemovalOffMain { listOf(failed) }

        assertEquals(listOf(failed), actual)
    }

    @Test
    fun `cancellation before dispatch does not invoke removal side effect`() = runBlocking {
        val calls = AtomicInteger(0)
        val job = launch(start = CoroutineStart.LAZY) {
            runDownloadRemovalOffMain {
                calls.incrementAndGet()
                emptyList()
            }
        }

        job.cancel()
        job.start()
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(0, calls.get())
    }

    @Test
    fun `download removal context rejects another account or profile`() {
        assertTrue(
            downloadRemovalContextMatches(
                expectedAccountId = "account-a",
                expectedProfileId = "profile-1",
                activeAccountId = "account-a",
                activeProfileId = "profile-1",
            ),
        )
        assertFalse(
            downloadRemovalContextMatches(
                expectedAccountId = "account-a",
                expectedProfileId = "profile-1",
                activeAccountId = "account-b",
                activeProfileId = "profile-1",
            ),
        )
        assertFalse(
            downloadRemovalContextMatches(
                expectedAccountId = "account-a",
                expectedProfileId = "profile-1",
                activeAccountId = "account-a",
                activeProfileId = "profile-2",
            ),
        )
        assertFalse(
            downloadRemovalContextMatches(
                expectedAccountId = "account-a",
                expectedProfileId = "profile-1",
                activeAccountId = null,
                activeProfileId = "profile-1",
            ),
        )
    }

    private fun download(
        downloadId: Long,
        status: OfflineStatus,
    ): OfflineDownload = OfflineDownload(
        downloadId = downloadId,
        historyKey = "movie:$downloadId",
        title = "Movie $downloadId",
        posterUrl = null,
        streamKind = "movie",
        streamId = downloadId.toInt(),
        extension = "mp4",
        sourceCandidates = emptyList(),
        status = status,
    )
}
