package sa.hulksa.player.data

import sa.hulksa.player.model.ContentType

/**
 * R32: new local freshness policy for complete catalog snapshots.
 *
 * Only a validated complete [CatalogSnapshot] can qualify. A compact Home bootstrap is a different
 * type and never enters this policy. Missing, zero/negative, future-dated or older timestamps are
 * not fresh and require the existing restore-and-revalidate behavior.
 */
internal object CatalogSnapshotFreshness {
    /** Explicitly a new, local policy: 15 minutes. Not a provider constant. */
    const val WINDOW_MS: Long = 15L * 60L * 1_000L

    fun isFresh(snapshot: CatalogSnapshot, nowEpochMs: Long): Boolean {
        val completedAt = snapshot.completedAtEpochMs
        if (completedAt <= 0L) return false
        if (completedAt > nowEpochMs) return false
        return nowEpochMs - completedAt <= WINDOW_MS
    }
}

/**
 * Network work after a restore pass: fresh complete snapshots are excluded, stale/missing ones
 * remain. The selected foreground type keeps its existing first position. Bootstrap data is not
 * part of this input and therefore can never suppress a full-catalog load.
 */
internal fun catalogNetworkOrderAfterRestore(
    requested: List<ContentType>,
    selected: ContentType?,
    freshTypes: Set<ContentType>,
): List<ContentType> {
    val network = requested.filterNot { it in freshTypes }
    return buildList {
        if (selected != null && selected in network) add(selected)
        addAll(network.filter { it != selected })
    }
}
