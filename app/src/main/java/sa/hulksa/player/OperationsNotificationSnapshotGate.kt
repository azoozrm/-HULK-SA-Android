package sa.hulksa.player

/**
 * Owns publication order for Operations notification snapshots. A newer read or
 * mutation makes every older IO completion stale before it can replace the UI snapshot.
 */
internal class OperationsNotificationSnapshotGate {
    private var generation: Long = 0L

    @Synchronized
    fun begin(): Long = ++generation

    @Synchronized
    fun isCurrent(snapshotGeneration: Long): Boolean =
        generation == snapshotGeneration
}
