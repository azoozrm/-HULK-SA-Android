package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Test
import sa.hulksa.player.model.OfflineStatus

class DurableDownloadPreferenceStoreTest {
    @Test
    fun `active and blocked states enqueue durable work`() {
        listOf(
            OfflineStatus.QUEUED,
            OfflineStatus.CHECKING,
            OfflineStatus.DOWNLOADING,
            OfflineStatus.WAITING_SCHEDULE,
            OfflineStatus.WAITING_NETWORK,
            OfflineStatus.WAITING_STORAGE,
        ).forEach { status ->
            assertEquals(
                DurableDownloadLifecycleAction.ENQUEUE,
                durableDownloadLifecycleAction(status),
            )
        }
    }

    @Test
    fun `paused failed and completed states cancel durable work`() {
        listOf(
            OfflineStatus.PAUSED,
            OfflineStatus.FAILED,
            OfflineStatus.COMPLETED,
        ).forEach { status ->
            assertEquals(
                DurableDownloadLifecycleAction.CANCEL,
                durableDownloadLifecycleAction(status),
            )
        }
    }
}
