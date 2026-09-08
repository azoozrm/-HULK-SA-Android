package sa.hulksa.player

import java.util.concurrent.atomic.AtomicLong
import sa.hulksa.player.data.LocalSystemNotification

internal data class OperationsNotificationSnapshot(
    val generation: Long,
    val notifications: List<LocalSystemNotification>,
)

/**
 * Serializes each Operations notification persistence transaction with its snapshot.
 * Ownership is assigned only after the transaction reaches the persistence lock, so
 * request start order cannot override the order in which storage actually changes.
 */
internal class OperationsNotificationSnapshotGate(
    private val persistenceLock: Any,
) {
    private val generation = AtomicLong(0L)

    fun capture(
        persistenceTransaction: () -> List<LocalSystemNotification>,
    ): OperationsNotificationSnapshot = synchronized(persistenceLock) {
        val snapshotGeneration = generation.incrementAndGet()
        OperationsNotificationSnapshot(
            generation = snapshotGeneration,
            notifications = persistenceTransaction(),
        )
    }

    fun isCurrent(snapshot: OperationsNotificationSnapshot): Boolean =
        generation.get() == snapshot.generation
}
