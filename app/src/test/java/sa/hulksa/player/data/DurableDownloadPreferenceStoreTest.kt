package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `progress-only status changes keep the same scheduling state`() {
        val queued = DurableDownloadPersistedRecord(
            downloadId = 42L,
            title = "Movie",
            status = OfflineStatus.QUEUED,
            scheduledAtEpochMs = 0L,
        )
        val downloading = queued.copy(status = OfflineStatus.DOWNLOADING)

        assertEquals(
            durableDownloadSchedulingState(queued, wifiOnly = false),
            durableDownloadSchedulingState(downloading, wifiOnly = false),
        )
    }

    @Test
    fun `network constraint changes require rescheduling`() {
        val record = DurableDownloadPersistedRecord(
            downloadId = 42L,
            title = "Movie",
            status = OfflineStatus.QUEUED,
            scheduledAtEpochMs = 0L,
        )

        assertNotEquals(
            durableDownloadSchedulingState(record, wifiOnly = false),
            durableDownloadSchedulingState(record, wifiOnly = true),
        )
    }

    @Test
    fun `active transport is not replaced when scheduled metadata is cleared`() {
        val previous = DurableDownloadSchedulingState(
            action = DurableDownloadLifecycleAction.ENQUEUE,
            title = "Movie",
            wifiOnly = false,
            scheduledAtEpochMs = 25_000L,
        )
        val current = previous.copy(scheduledAtEpochMs = 0L)

        assertFalse(
            shouldApplyDurableDownloadSchedulingState(
                previous = previous,
                current = current,
                currentStatus = OfflineStatus.CHECKING,
            ),
        )
    }

    @Test
    fun `queued constraint changes still replace durable work`() {
        val previous = DurableDownloadSchedulingState(
            action = DurableDownloadLifecycleAction.ENQUEUE,
            title = "Movie",
            wifiOnly = false,
            scheduledAtEpochMs = 0L,
        )
        val current = previous.copy(wifiOnly = true)

        assertTrue(
            shouldApplyDurableDownloadSchedulingState(
                previous = previous,
                current = current,
                currentStatus = OfflineStatus.QUEUED,
            ),
        )
    }
}
