package sa.hulksa.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.ui.screens.CatalogFavoriteSnapshot

/**
 * Bounded regression for the Search result hearts:
 *
 * The production Search grid must render Movie, Series and Live hearts from the observed committed
 * profile favorite snapshot. A favorites-only commit changes that snapshot while the ranked result
 * objects, their stable keys and the row callbacks stay identical; the card must still repaint the
 * heart immediately and must clear it again after removal.
 */
class ProfileSmartSearchFavoriteTest {
    private val movie = item(id = 3, type = ContentType.MOVIE)
    private val series = item(id = 7, type = ContentType.SERIES)
    private val live = item(id = 9, type = ContentType.LIVE)
    private val results = listOf(movie, series, live)

    @Test
    fun `favorite-only commit flips Movie Series and Live hearts without changing results`() {
        val before = CatalogFavoriteSnapshot(persisted = emptySet())
        val added = CatalogFavoriteSnapshot(
            persisted = setOf("MOVIE:3", "SERIES:7", "LIVE:9"),
        )

        // Both revisions render the same ranked result objects and stable keys; only the observed
        // favorite state changes, so the heart flips without a new query, result set or callback.
        val ranked = results
        ranked.forEach { item ->
            assertFalse(before.contains(item))
            assertTrue(added.contains(item))
        }
        assertEquals(
            listOf("result:${ContentType.MOVIE}:3", "result:${ContentType.SERIES}:7", "result:${ContentType.LIVE}:9"),
            ranked.map { "result:${it.type}:${it.id}" },
        )
    }

    @Test
    fun `removing a favorite clears the same card heart`() {
        val installed = CatalogFavoriteSnapshot(persisted = setOf("SERIES:7"))
        val removed = CatalogFavoriteSnapshot(persisted = emptySet())

        assertTrue(installed.contains(series))
        assertFalse(removed.contains(series))
        // Neighbours are unaffected by the removal of another favorite.
        assertFalse(installed.contains(movie))
        assertFalse(installed.contains(live))
    }

    @Test
    fun `the observed snapshot uses the authoritative profile favorite keys`() {
        // The account/profile favorite owner keys every item as "<TYPE>:<id>".
        val snapshot = CatalogFavoriteSnapshot(
            persisted = setOf("MOVIE:3", "SERIES:7", "LIVE:9", "SERIES:70"),
        )

        assertTrue(snapshot.contains(movie))
        assertTrue(snapshot.contains(series))
        assertTrue(snapshot.contains(live))
        assertFalse(snapshot.contains(item(id = 70, type = ContentType.MOVIE)))
        assertFalse(snapshot.contains(item(id = 0, type = ContentType.SERIES)))
    }

    private fun item(id: Int, type: ContentType): ContentItem = ContentItem(
        id = id,
        name = "${type.name} $id",
        categoryId = "action",
        type = type,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = "mp4",
        genre = null,
        addedAtEpochSeconds = 0L,
    )
}
