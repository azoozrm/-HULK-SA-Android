package sa.hulksa.player

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.data.LocalSystemNotification
import sa.hulksa.player.data.OperationsAnnouncementSeverity

class OperationsNotificationSnapshotGateTest {
    @Test
    fun `persistence order wins when earlier delete request reaches storage last`() {
        val gate = OperationsNotificationSnapshotGate(Any())
        var persisted = listOf(notification(read = false))
        val deleteRequestStarted = CountDownLatch(1)
        val allowDeletePersistence = CountDownLatch(1)
        val deleteFinished = CountDownLatch(1)
        val deleteSnapshot = AtomicReference<OperationsNotificationSnapshot>()
        val deleteFailure = AtomicReference<Throwable?>()

        val deleteThread = Thread {
            try {
                deleteRequestStarted.countDown()
                check(allowDeletePersistence.await(5, TimeUnit.SECONDS))
                deleteSnapshot.set(
                    gate.capture {
                        persisted = emptyList()
                        persisted
                    },
                )
            } catch (error: Throwable) {
                deleteFailure.set(error)
            } finally {
                deleteFinished.countDown()
            }
        }.apply {
            isDaemon = true
            start()
        }

        assertTrue(deleteRequestStarted.await(5, TimeUnit.SECONDS))
        val markReadSnapshot = gate.capture {
            persisted = persisted.map { it.copy(read = true) }
            persisted
        }
        allowDeletePersistence.countDown()
        assertTrue(deleteFinished.await(5, TimeUnit.SECONDS))
        deleteThread.join(5_000L)
        assertFalse(deleteThread.isAlive)
        assertNull(deleteFailure.get())
        assertTrue(markReadSnapshot.generation < deleteSnapshot.get().generation)

        var published: List<LocalSystemNotification> = emptyList()
        publishIfCurrent(gate, deleteSnapshot.get()) { published = it }
        publishIfCurrent(gate, markReadSnapshot) { published = it }

        assertEquals(persisted, published)
        assertTrue(published.isEmpty())
    }

    @Test
    fun `out of order read completion cannot restore unread state after a later update`() {
        val gate = OperationsNotificationSnapshotGate(Any())
        val unreadSnapshot = listOf(notification(read = false))
        val readSnapshot = listOf(notification(read = true))
        val staleRead = gate.capture { unreadSnapshot }
        val markRead = gate.capture { readSnapshot }
        var published: List<LocalSystemNotification> = emptyList()

        publishIfCurrent(gate, markRead) { published = it }
        publishIfCurrent(gate, staleRead) { published = it }

        assertEquals(readSnapshot, published)
    }

    private fun publishIfCurrent(
        gate: OperationsNotificationSnapshotGate,
        snapshot: OperationsNotificationSnapshot,
        publish: (List<LocalSystemNotification>) -> Unit,
    ) {
        if (gate.isCurrent(snapshot)) publish(snapshot.notifications)
    }

    private fun notification(read: Boolean) = LocalSystemNotification(
        id = "operations:MSG-001",
        messageId = "MSG-001",
        title = "HULK SA",
        message = "رسالة تشغيلية",
        severity = OperationsAnnouncementSeverity.IMPORTANT,
        createdAtEpochMs = 1_000L,
        read = read,
    )
}
