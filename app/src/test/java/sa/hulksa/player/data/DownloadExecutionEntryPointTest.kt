package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import sa.hulksa.player.model.OfflineStatus

class DownloadExecutionEntryPointTest {
    @Test
    fun `download id must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            validateDurableDownloadId(0L)
        }
    }

    @Test
    fun `active transport states keep worker alive`() {
        listOf(
            OfflineStatus.QUEUED,
            OfflineStatus.CHECKING,
            OfflineStatus.DOWNLOADING,
        ).forEach { status ->
            assertEquals(
                DurableDownloadExecutionDirective.AWAIT,
                durableDownloadExecutionDirective(status),
            )
        }
    }

    @Test
    fun `blocked states request durable retry`() {
        listOf(
            OfflineStatus.WAITING_SCHEDULE,
            OfflineStatus.WAITING_NETWORK,
            OfflineStatus.WAITING_STORAGE,
        ).forEach { status ->
            assertEquals(
                DurableDownloadExecutionDirective.RETRY,
                durableDownloadExecutionDirective(status),
            )
        }
    }

    @Test
    fun `terminal states finish worker deterministically`() {
        assertEquals(
            DurableDownloadExecutionDirective.COMPLETED,
            durableDownloadExecutionDirective(OfflineStatus.COMPLETED),
        )
        listOf(OfflineStatus.PAUSED, OfflineStatus.FAILED).forEach { status ->
            assertEquals(
                DurableDownloadExecutionDirective.TERMINAL,
                durableDownloadExecutionDirective(status),
            )
        }
    }
}
