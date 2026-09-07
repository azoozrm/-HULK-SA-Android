package sa.hulksa.player.data

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.OfflineStatus

class DurableDownloadExecutionOwnershipTest {
    @Test
    fun `stale worker cleanup cannot release replacement lease`() {
        val first = DurableDownloadExecutionLeaseRegistry.claim("account-a", 42L)
        assertNotNull(first)
        first ?: return
        assertNull(DurableDownloadExecutionLeaseRegistry.claim("account-a", 42L))

        assertTrue(DurableDownloadExecutionLeaseRegistry.release(first))
        val replacement = DurableDownloadExecutionLeaseRegistry.claim("account-a", 42L)
        assertNotNull(replacement)
        replacement ?: return

        assertFalse(DurableDownloadExecutionLeaseRegistry.release(first))
        assertTrue(DurableDownloadExecutionLeaseRegistry.owns("account-a", 42L))
        assertNull(DurableDownloadExecutionLeaseRegistry.claim("account-a", 42L))

        assertTrue(DurableDownloadExecutionLeaseRegistry.release(replacement))
        assertFalse(DurableDownloadExecutionLeaseRegistry.owns("account-a", 42L))
    }

    @Test
    fun `durable execution lease is isolated by download id`() {
        val first = DurableDownloadExecutionLeaseRegistry.claim("account-a", 42L)
        val second = DurableDownloadExecutionLeaseRegistry.claim("account-a", 43L)
        assertNotNull(first)
        assertNotNull(second)
        first?.let(DurableDownloadExecutionLeaseRegistry::release)
        second?.let(DurableDownloadExecutionLeaseRegistry::release)
    }

    @Test
    fun `worker interruption only recovers active transport states to queued`() {
        listOf(
            OfflineStatus.QUEUED,
            OfflineStatus.CHECKING,
            OfflineStatus.DOWNLOADING,
        ).forEach { status ->
            assertEquals(OfflineStatus.QUEUED, durableWorkerInterruptedStatus(status))
        }

        listOf(
            OfflineStatus.PAUSED,
            OfflineStatus.WAITING_SCHEDULE,
            OfflineStatus.WAITING_NETWORK,
            OfflineStatus.WAITING_STORAGE,
            OfflineStatus.COMPLETED,
            OfflineStatus.FAILED,
        ).forEach { status ->
            assertNull(durableWorkerInterruptedStatus(status))
        }
    }

    @Test
    fun `completed file requires readable exact nonempty bytes`() {
        val directory = createTempDirectory("hulk-download-integrity").toFile()
        try {
            val file = File(directory, "movie.mp4").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            assertTrue(completedDownloadFileIsUsable(file, expectedBytes = 4L))
            assertFalse(completedDownloadFileIsUsable(file, expectedBytes = 5L))

            file.writeBytes(byteArrayOf())
            assertFalse(completedDownloadFileIsUsable(file, expectedBytes = 0L))

            assertFalse(completedDownloadFileIsUsable(File(directory, "missing.mp4"), expectedBytes = 4L))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `delete cleanup is verified and idempotent`() {
        val directory = createTempDirectory("hulk-download-delete").toFile()
        try {
            val file = File(directory, "movie.mp4.part").apply { writeBytes(byteArrayOf(1)) }
            assertTrue(deleteDownloadFileIfPresent(file))
            assertFalse(file.exists())
            assertTrue(deleteDownloadFileIfPresent(file))
        } finally {
            directory.deleteRecursively()
        }
    }
}
