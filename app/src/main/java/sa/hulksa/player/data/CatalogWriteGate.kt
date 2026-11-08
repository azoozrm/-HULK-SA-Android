package sa.hulksa.player.data

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import sa.hulksa.player.model.ContentType

/**
 * Logical write admission for the disposable catalog cache.
 *
 * A write request owns exactly one revision per type (and one for Home). A newer request or a
 * session/profile invalidation advances the revision, so an older job that resumes late fails the
 * guard at the transaction publication boundary and its transaction rolls back. The guard is
 * lock-free and safe to call from the database IO thread; it never holds the authentication lock
 * across a large write.
 */
internal class CatalogWriteGate {
    private val catalogRevisions = AtomicLongArray(ContentType.entries.size)
    private val homeRevision = AtomicLong(0L)

    fun nextCatalog(type: ContentType): Long = catalogRevisions.incrementAndGet(type.ordinal)

    fun isCatalogCurrent(type: ContentType, revision: Long): Boolean =
        catalogRevisions.get(type.ordinal) == revision

    fun nextHome(): Long = homeRevision.incrementAndGet()

    fun isHomeCurrent(revision: Long): Boolean = homeRevision.get() == revision

    /** Invalidates every in-flight catalog write for a session/ownership replacement. */
    fun invalidateCatalogs() {
        for (index in 0 until catalogRevisions.length()) {
            catalogRevisions.incrementAndGet(index)
        }
    }

    /** Invalidates only profile-owned Home writes (profile switch). */
    fun invalidateHome() {
        homeRevision.incrementAndGet()
    }

    fun invalidateAll() {
        invalidateCatalogs()
        invalidateHome()
    }
}
