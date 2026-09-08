package sa.hulksa.player

import org.junit.Assert.assertEquals
import org.junit.Test
import sa.hulksa.player.data.LocalSystemNotification
import sa.hulksa.player.data.OperationsAnnouncementSeverity

class OperationsNotificationSnapshotGateTest {
    @Test
    fun `out of order read completion cannot restore a notification deleted later`() {
        val gate = OperationsNotificationSnapshotGate()
        val staleRead = gate.begin()
        val delete = gate.begin()
        val beforeDelete = listOf(notification(read = false))
        var published: List<LocalSystemNotification> = emptyList()

        publishIfCurrent(gate, delete, emptyList()) { published = it }
        publishIfCurrent(gate, staleRead, beforeDelete) { published = it }

        assertEquals(emptyList<LocalSystemNotification>(), published)
    }

    @Test
    fun `out of order read completion cannot restore unread state after a later update`() {
        val gate = OperationsNotificationSnapshotGate()
        val staleRead = gate.begin()
        val markRead = gate.begin()
        val unreadSnapshot = listOf(notification(read = false))
        val readSnapshot = listOf(notification(read = true))
        var published: List<LocalSystemNotification> = emptyList()

        publishIfCurrent(gate, markRead, readSnapshot) { published = it }
        publishIfCurrent(gate, staleRead, unreadSnapshot) { published = it }

        assertEquals(readSnapshot, published)
    }

    private fun publishIfCurrent(
        gate: OperationsNotificationSnapshotGate,
        generation: Long,
        snapshot: List<LocalSystemNotification>,
        publish: (List<LocalSystemNotification>) -> Unit,
    ) {
        if (gate.isCurrent(generation)) publish(snapshot)
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
