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
            accountId = "account-a",
            profileId = "primary",
            historyKey = "movie:42",
            status = OfflineStatus.QUEUED,
            scheduledAtEpochMs = 0L,
        )
        val downloading = queued.copy(status = OfflineStatus.DOWNLOADING)

        assertEquals(
            durableDownloadSchedulingState(queued, wifiOnly = false, activeAccountId = "account-a"),
            durableDownloadSchedulingState(downloading, wifiOnly = false, activeAccountId = "account-a"),
        )
    }

    @Test
    fun `network constraint changes require rescheduling`() {
        val record = DurableDownloadPersistedRecord(
            downloadId = 42L,
            accountId = "account-a",
            profileId = "primary",
            historyKey = "movie:42",
            status = OfflineStatus.QUEUED,
            scheduledAtEpochMs = 0L,
        )

        assertNotEquals(
            durableDownloadSchedulingState(record, wifiOnly = false, activeAccountId = "account-a"),
            durableDownloadSchedulingState(record, wifiOnly = true, activeAccountId = "account-a"),
        )
    }

    @Test
    fun `inactive account records always cancel durable work`() {
        val record = DurableDownloadPersistedRecord(
            downloadId = 42L,
            accountId = "account-a",
            profileId = "primary",
            historyKey = "movie:42",
            status = OfflineStatus.QUEUED,
            scheduledAtEpochMs = 0L,
        )

        assertEquals(
            DurableDownloadLifecycleAction.CANCEL,
            durableDownloadSchedulingState(
                record = record,
                wifiOnly = false,
                activeAccountId = "account-b",
            ).action,
        )
    }

    @Test
    fun `active transport is not replaced when scheduled metadata is cleared`() {
        val previous = DurableDownloadSchedulingState(
            action = DurableDownloadLifecycleAction.ENQUEUE,
            owner = DownloadOwner("account-a", "primary"),
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
            owner = DownloadOwner("account-a", "primary"),
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
