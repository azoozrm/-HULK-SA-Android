package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentType

/** R32 new 15-minute full-snapshot freshness policy and startup network plan. */
class CatalogSnapshotFreshnessTest {
    private val now = 1_700_000_000_000L

    private fun snapshot(completedAt: Long): CatalogSnapshot = CatalogSnapshot(
        catalog = Catalog(categories = emptyList(), items = emptyList()),
        generation = 1L,
        completedAtEpochMs = completedAt,
    )

    @Test
    fun `recent complete snapshot is fresh at the inclusive boundary`() {
        assertTrue(CatalogSnapshotFreshness.isFresh(snapshot(now - 60_000L), now))
        assertTrue(
            CatalogSnapshotFreshness.isFresh(
                snapshot(now - CatalogSnapshotFreshness.WINDOW_MS),
                now,
            ),
        )
    }

    @Test
    fun `older than the window is not fresh`() {
        assertFalse(
            CatalogSnapshotFreshness.isFresh(
                snapshot(now - CatalogSnapshotFreshness.WINDOW_MS - 1L),
                now,
            ),
        )
        assertFalse(CatalogSnapshotFreshness.isFresh(snapshot(now - 86_400_000L), now))
    }

    @Test
    fun `missing invalid and future timestamps are not fresh`() {
        assertFalse(CatalogSnapshotFreshness.isFresh(snapshot(0L), now))
        assertFalse(CatalogSnapshotFreshness.isFresh(snapshot(-1L), now))
        assertFalse(CatalogSnapshotFreshness.isFresh(snapshot(now + 1L), now))
    }

    @Test
    fun `fresh full snapshots are excluded from automatic network work`() {
        val requested = listOf(ContentType.MOVIE, ContentType.SERIES, ContentType.LIVE)
        assertEquals(
            emptyList<ContentType>(),
            catalogNetworkOrderAfterRestore(
                requested = requested,
                selected = ContentType.SERIES,
                freshTypes = requested.toSet(),
            ),
        )
    }

    @Test
    fun `stale or missing snapshots keep revalidation and preserve foreground order`() {
        val requested = listOf(ContentType.MOVIE, ContentType.SERIES, ContentType.LIVE)
        assertEquals(
            listOf(ContentType.SERIES, ContentType.LIVE),
            catalogNetworkOrderAfterRestore(
                requested = requested,
                selected = ContentType.SERIES,
                freshTypes = setOf(ContentType.MOVIE),
            ),
        )
    }

    @Test
    fun `a compact bootstrap cannot suppress full catalog loading`() {
        // The planner accepts only complete-snapshot freshness; bootstrap data is not an input.
        assertEquals(
            listOf(ContentType.MOVIE, ContentType.SERIES, ContentType.LIVE),
            catalogNetworkOrderAfterRestore(
                requested = listOf(ContentType.MOVIE, ContentType.SERIES, ContentType.LIVE),
                selected = null,
                freshTypes = emptySet(),
            ),
        )
    }
}
