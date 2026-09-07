package sa.hulksa.player

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `download enqueue runs exactly once away from caller thread`() = runBlocking {
        val callerThreadId = Thread.currentThread().id
        val calls = AtomicInteger(0)
        var enqueueThreadId = callerThreadId

        val result = runDownloadEnqueueOffMain {
            calls.incrementAndGet()
            enqueueThreadId = Thread.currentThread().id
            "started"
        }

        assertEquals("started", result)
        assertEquals(1, calls.get())
        assertNotEquals(callerThreadId, enqueueThreadId)
    }

    @Test
    fun `download enqueue propagates disk failure to its owner`() = runBlocking {
        var failureObserved = false

        try {
            runDownloadEnqueueOffMain<Unit> {
                throw IllegalStateException("disk write failed")
            }
        } catch (_: IllegalStateException) {
            failureObserved = true
        }

        assertTrue(failureObserved)
    }

    @Test
    fun `download enqueue publication requires same session account and profile`() {
        assertTrue(
            downloadEnqueuePublicationAllowed(
                sameSession = true,
                expectedAccountId = "account-a",
                expectedProfileId = "profile-a",
                activeAccountId = "account-a",
                activeProfileId = "profile-a",
            ),
        )
        assertFalse(
            downloadEnqueuePublicationAllowed(
                sameSession = false,
                expectedAccountId = "account-a",
                expectedProfileId = "profile-a",
                activeAccountId = "account-a",
                activeProfileId = "profile-a",
            ),
        )
        assertFalse(
            downloadEnqueuePublicationAllowed(
                sameSession = true,
                expectedAccountId = "account-a",
                expectedProfileId = "profile-a",
                activeAccountId = "account-a",
                activeProfileId = "profile-b",
            ),
        )
        assertFalse(
            downloadEnqueuePublicationAllowed(
                sameSession = true,
                expectedAccountId = "account-a",
                expectedProfileId = "profile-a",
                activeAccountId = "account-b",
                activeProfileId = "profile-a",
            ),
        )
    }
}
