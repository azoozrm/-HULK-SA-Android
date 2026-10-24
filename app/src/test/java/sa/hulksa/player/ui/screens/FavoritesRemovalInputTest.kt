package sa.hulksa.player.ui.screens

import androidx.compose.ui.input.key.KeyEventType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.MainDestination

/**
 * R04: one physical TV long press owns its select gesture at the shell boundary. The original
 * card is mutated once; all repeats and the release belong to that gesture, survive the card/
 * grid replacement and never reach a replacement card or the empty-filter fallback.
 */
class FavoritesRemovalInputTest {
    private val favorites = MainDestination.FAVORITES
    private val filterKey = "favorites:all"

    @Test
    fun `one held press mutates only the original card and release clears the guard`() {
        val guard = FavoritesRemovalInputGuard()
        val mutations = mutableListOf<String>()
        val replacementActions = mutableListOf<String>()
        val previous = listOf("MOVIE:1", "SERIES:2", "LIVE:3")
        val current = listOf("MOVIE:1", "LIVE:3")

        beginFavoritesRemovalLongPress(
            enabled = true,
            beginGuard = guard::begin,
            toggleFavorite = { mutations += "SERIES:2" },
        )
        assertTrue(guard.active)
        assertEquals(listOf("SERIES:2"), mutations)
        assertEquals(
            FavoritesGridFocusTransition.REMOVAL,
            favoritesGridFocusTransition(
                destination = favorites,
                previousKeys = previous,
                currentKeys = current,
                previousRowKey = filterKey,
                currentRowKey = filterKey,
                focusedKey = "SERIES:2",
            ),
        )
        val replacementIndex = favoritesRemovalFocusIndex(removedIndex = 1, remainingCount = 2)
        assertEquals(1, replacementIndex)
        assertEquals("LIVE:3", current[replacementIndex!!])

        repeat(4) { repeatIndex ->
            assertTrue(
                "held repeat $repeatIndex must be consumed",
                deliver(guard, KeyEventType.KeyDown) {
                    replacementActions += "LIVE:3"
                },
            )
        }
        assertTrue(
            "the release must be consumed once",
            deliver(guard, KeyEventType.KeyUp) { replacementActions += "LIVE:3" },
        )
        assertFalse(guard.active)
        assertTrue(replacementActions.isEmpty())
        assertEquals(listOf("SERIES:2"), mutations)

        // A genuinely new press after release is admitted normally.
        assertFalse(deliver(guard, KeyEventType.KeyDown) { replacementActions += "LIVE:3" })
        beginFavoritesRemovalLongPress(
            enabled = true,
            beginGuard = guard::begin,
            toggleFavorite = { mutations += "LIVE:3" },
        )
        assertTrue(guard.active)
        assertEquals(listOf("SERIES:2", "LIVE:3"), mutations)
        assertTrue(guard.onSelectKey(KeyEventType.KeyUp))
    }

    @Test
    fun `Movie Series and Live removals each mutate exactly once in mixed and filtered grids`() {
        val mixed = listOf("MOVIE:1", "SERIES:2", "LIVE:3", "MOVIE:4")
        listOf("MOVIE:1", "SERIES:2", "LIVE:3").forEach { removed ->
            val guard = FavoritesRemovalInputGuard()
            val mutations = mutableListOf<String>()
            val remaining = mixed - removed

            beginFavoritesRemovalLongPress(
                enabled = true,
                beginGuard = guard::begin,
                toggleFavorite = { mutations += removed },
            )
            assertEquals(
                FavoritesGridFocusTransition.REMOVAL,
                favoritesGridFocusTransition(
                    destination = favorites,
                    previousKeys = mixed,
                    currentKeys = remaining,
                    previousRowKey = filterKey,
                    currentRowKey = filterKey,
                    focusedKey = removed,
                ),
            )
            repeat(3) {
                assertTrue(deliver(guard, KeyEventType.KeyDown) { mutations += "replacement" })
            }
            assertTrue(deliver(guard, KeyEventType.KeyUp) { mutations += "replacement" })
            assertEquals("$removed", listOf(removed), mutations)
        }

        val filtered = listOf("SERIES:2", "SERIES:5")
        val guard = FavoritesRemovalInputGuard()
        val mutations = mutableListOf<String>()
        beginFavoritesRemovalLongPress(
            enabled = true,
            beginGuard = guard::begin,
            toggleFavorite = { mutations += "SERIES:2" },
        )
        assertTrue(deliver(guard, KeyEventType.KeyDown) { mutations += "replacement" })
        assertTrue(deliver(guard, KeyEventType.KeyUp) { mutations += "replacement" })
        assertEquals(listOf("SERIES:2"), mutations)
        assertEquals(
            FavoritesGridFocusTransition.REMOVAL,
            favoritesGridFocusTransition(
                destination = favorites,
                previousKeys = filtered,
                currentKeys = listOf("SERIES:5"),
                previousRowKey = "favorites:series",
                currentRowKey = "favorites:series",
                focusedKey = "SERIES:2",
            ),
        )
    }

    @Test
    fun `last-item disposal keeps the release away from the filter fallback`() {
        val guard = FavoritesRemovalInputGuard()
        val mutations = mutableListOf<String>()
        var filterActions = 0
        val previous = listOf("MOVIE:1")
        val current = emptyList<String>()

        beginFavoritesRemovalLongPress(
            enabled = true,
            beginGuard = guard::begin,
            toggleFavorite = { mutations += "MOVIE:1" },
        )
        assertEquals(
            FavoritesGridFocusTransition.REMOVAL,
            favoritesGridFocusTransition(
                destination = favorites,
                previousKeys = previous,
                currentKeys = current,
                previousRowKey = filterKey,
                currentRowKey = filterKey,
                focusedKey = "MOVIE:1",
            ),
        )
        assertNull(favoritesRemovalFocusIndex(removedIndex = 0, remainingCount = 0))

        // The grid/card is gone; the shell guard remains active and consumes the held gesture.
        assertTrue(deliver(guard, KeyEventType.KeyDown) { filterActions += 1 })
        assertTrue(deliver(guard, KeyEventType.KeyUp) { filterActions += 1 })
        assertEquals(1, mutations.size)
        assertEquals(0, filterActions)

        // Only a fresh press after the release reaches the always-attached filter fallback.
        assertFalse(deliver(guard, KeyEventType.KeyDown) { filterActions += 1 })
        assertEquals(1, filterActions)
    }

    @Test
    fun `completed or failed persistence never clears the held gesture`() {
        val guard = FavoritesRemovalInputGuard()
        var mutationResult: Boolean? = null
        var mutations = 0

        beginFavoritesRemovalLongPress(
            enabled = true,
            beginGuard = guard::begin,
            toggleFavorite = { mutations += 1 },
        )
        assertTrue(guard.active)
        mutationResult = false
        assertTrue(deliver(guard, KeyEventType.KeyDown) { mutations += 1 })
        assertTrue(guard.active)
        mutationResult = true
        assertTrue(deliver(guard, KeyEventType.KeyDown) { mutations += 1 })
        assertTrue(guard.active)
        assertEquals(1, mutations)
        assertTrue(deliver(guard, KeyEventType.KeyUp) { mutations += 1 })
        assertFalse(guard.active)
        assertTrue(mutationResult!!)
    }

    @Test
    fun `shell boundary and grid route the production removal gesture`() {
        val shell = File("src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt").readText()
        assertTrue(shell.contains("TvRemovalInputBoundary("))
        assertTrue(shell.contains("favoritesGuard = favoritesRemovalInputGuard"))
        assertTrue(shell.contains("onFavoritesRemovalStarted = favoritesRemovalInputGuard::begin"))

        val grid = File("src/main/java/sa/hulksa/player/ui/screens/TvCatalogGrid.kt").readText()
        assertTrue(grid.contains("beginFavoritesRemovalLongPress("))
        assertTrue(grid.contains("enabled = destination == MainDestination.FAVORITES && isTelevision"))
        assertFalse("the card-local release guard must be gone", grid.contains("suppressSelectKeyUp"))
    }

    @Test
    fun `mobile long press toggles once without arming the TV shell guard`() {
        val guard = FavoritesRemovalInputGuard()
        var mutations = 0

        beginFavoritesRemovalLongPress(
            enabled = false,
            beginGuard = guard::begin,
            toggleFavorite = { mutations += 1 },
        )

        assertFalse(guard.active)
        assertEquals(1, mutations)
        assertFalse(guard.onSelectKey(KeyEventType.KeyDown))
        assertFalse(guard.onSelectKey(KeyEventType.KeyUp))
        assertEquals(1, mutations)
    }

    /** One shell-boundary delivery; the action runs only when the guard passes the event. */
    private fun deliver(
        guard: FavoritesRemovalInputGuard,
        type: KeyEventType,
        action: () -> Unit,
    ): Boolean {
        val consumed = guard.onSelectKey(type)
        if (!consumed) action()
        return consumed
    }
}
