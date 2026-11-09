package sa.hulksa.player.ui.screens

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioral ownership tests for the Home entry/return focus intent. These drive the production
 * [HomeFocusRestoreOwner] through real event order: first entry, Details return, catalog/model
 * publication, header Refresh focus, rail focus, content movement and a held restoration across
 * a coroutine suspension.
 */
class HomeFocusRestoreOwnerTest {
    private val rememberedMovie = NavigationPosition(
        rowKey = "recent-movies",
        rowIndex = 4,
        itemKey = "MOVIE:7",
        itemIndex = 2,
    )

    private fun owner(
        remembered: NavigationPosition = rememberedMovie,
    ): HomeFocusRestoreOwner = HomeFocusRestoreOwner(remembered)

    @Test
    fun `first entry without a remembered row never arms restoration`() {
        val owner = owner(NavigationPosition())

        assertFalse(
            owner.canRestoreEntry(
                current = NavigationPosition(),
                railFocusBaseline = 0L,
                currentRailFocusRevision = 0L,
            ),
        )
        assertFalse(
            owner.canRestoreRow(
                remembered = NavigationPosition(),
                current = NavigationPosition(),
                railFocusBaseline = 0L,
                currentRailFocusRevision = 0L,
            ),
        )
    }

    @Test
    fun `details return arms one entry restoration and model publication cannot rearm it`() {
        val owner = owner()
        val baseline = 3L

        assertTrue(
            "a fresh composition with a remembered row is the only arm point",
            owner.canRestoreEntry(rememberedMovie, baseline, baseline),
        )

        owner.onRestored()

        // Intermediate and final catalog/model publications are not entry events. They keep
        // asking the same owner, which has already consumed the entry intent.
        repeat(3) {
            assertFalse(
                owner.canRestoreEntry(rememberedMovie, baseline, baseline),
            )
        }

        // If the user is on the header Refresh control, the same publication cannot move focus
        // back into the remembered row either.
        owner.onHeaderFocused()
        assertFalse(
            owner.canRestoreRow(rememberedMovie, rememberedMovie, baseline, baseline),
        )
    }

    @Test
    fun `header refresh focus cancels a held restoration after suspension`() = runBlocking {
        val owner = owner()
        val release = CompletableDeferred<Unit>()
        val heldRestore = async {
            release.await()
            owner.canRestoreEntry(rememberedMovie, 0L, 0L)
        }

        owner.onHeaderFocused()
        release.complete(Unit)

        assertFalse(
            "the suspended handoff rechecks ownership after the header takes focus",
            heldRestore.await(),
        )
        assertFalse(
            owner.canRestoreRow(rememberedMovie, rememberedMovie, 0L, 0L),
        )
    }

    @Test
    fun `refresh focus survives intermediate and final model publication without a row handoff`() {
        val owner = owner()
        owner.onHeaderFocused()

        // The Home model may publish a cheap bootstrap and then the exact catalog. Neither is an
        // entry event, and the header still owns focus for both.
        repeat(2) { publication ->
            assertFalse(
                "publication $publication must not restore the hero",
                owner.canRestoreEntry(rememberedMovie, 0L, 0L),
            )
            assertFalse(
                "publication $publication must not restore the remembered row",
                owner.canRestoreRow(rememberedMovie, rememberedMovie, 0L, 0L),
            )
        }
    }

    @Test
    fun `refused or throwing focus requests do not consume the entry intent`() {
        val hero = NavigationPosition(
            rowKey = "hero",
            rowIndex = 0,
            itemKey = "MOVIE:7",
            itemIndex = 0,
        )
        val owner = HomeFocusRestoreOwner(hero)

        assertFalse(
            requestHomeRestoreFocus(
                requestFocus = { false },
                onRestored = owner::onRestored,
            ),
        )
        assertTrue(
            "a non-throwing false is not a successful handoff",
            owner.canRestoreEntry(hero, 0L, 0L),
        )

        assertFalse(
            requestHomeRestoreFocus(
                requestFocus = { error("focus owner is not attached") },
                onRestored = owner::onRestored,
            ),
        )
        assertTrue(
            "a thrown request is not a successful handoff",
            owner.canRestoreEntry(hero, 0L, 0L),
        )

        var restoredCalls = 0
        assertTrue(
            requestHomeRestoreFocus(
                requestFocus = { true },
                onRestored = {
                    restoredCalls += 1
                    owner.onRestored()
                },
            ),
        )
        assertEquals(1, restoredCalls)
        assertFalse(
            "only the actual true handoff consumes the entry intent",
            owner.canRestoreEntry(hero, 0L, 0L),
        )
    }

    @Test
    fun `favorite focus on the same hero retires a pending primary restoration`() {
        val hero = NavigationPosition(
            rowKey = "hero",
            rowIndex = 0,
            itemKey = "MOVIE:7",
            itemIndex = 0,
        )
        val owner = HomeFocusRestoreOwner(hero)

        assertTrue(owner.canRestoreEntry(hero, 0L, 0L))

        // This is the actual focus event currently wired for the sibling Favorite control. It
        // carries the same hero NavigationPosition as the pending primary handoff.
        owner.onContentFocused(hero)

        assertFalse(owner.canRestoreEntry(hero, 0L, 0L))
        assertFalse(
            "the older primary request cannot survive Favorite focus or a later publication",
            owner.canRestoreEntry(hero, 0L, 0L),
        )
    }

    @Test
    fun `favorite focus on the same hero cancels a held primary restoration after suspension`() =
        runBlocking {
            val hero = NavigationPosition(
                rowKey = "hero",
                rowIndex = 0,
                itemKey = "MOVIE:7",
                itemIndex = 0,
            )
            val owner = HomeFocusRestoreOwner(hero)
            val release = CompletableDeferred<Unit>()
            val heldPrimary = async {
                release.await()
                owner.canRestoreEntry(hero, 0L, 0L)
            }

            owner.onContentFocused(hero)
            release.complete(Unit)

            assertFalse(
                "the held primary handoff rechecks ownership after Favorite takes focus",
                heldPrimary.await(),
            )
        }

    @Test
    fun `rail focus cancels even when the remembered position still matches`() {
        val owner = owner()
        val baseline = 9L

        assertTrue(owner.canRestoreEntry(rememberedMovie, baseline, baseline))
        owner.onRailFocused()

        assertFalse(owner.canRestoreEntry(rememberedMovie, baseline, baseline))
        assertFalse(owner.canRestoreRow(rememberedMovie, rememberedMovie, baseline, baseline))
    }

    @Test
    fun `a deliberate content move cancels the entry but keeps row ownership on the new target`() {
        val owner = owner()
        val otherCard = NavigationPosition(
            rowKey = "recommended",
            rowIndex = 5,
            itemKey = "MOVIE:9",
            itemIndex = 1,
        )

        owner.onContentFocused(otherCard)

        assertFalse(
            "moving to another card must cancel the older entry restoration",
            owner.canRestoreEntry(rememberedMovie, 0L, 0L),
        )
        assertTrue(
            "the new content target still owns its own row restoration",
            owner.canRestoreRow(otherCard, otherCard, 0L, 0L),
        )
        assertFalse(
            "the old row cannot pull focus back from the new target",
            owner.canRestoreRow(rememberedMovie, otherCard, 0L, 0L),
        )
    }

    @Test
    fun `a non-navigation content control blocks rows until a real card takes focus`() {
        val owner = owner()

        owner.onContentFocused(null)

        assertFalse(owner.canRestoreEntry(rememberedMovie, 0L, 0L))
        assertFalse(owner.canRestoreRow(rememberedMovie, rememberedMovie, 0L, 0L))

        owner.onContentFocused(rememberedMovie)

        assertFalse(
            "returning to the remembered card does not recreate the consumed entry intent",
            owner.canRestoreEntry(rememberedMovie, 0L, 0L),
        )
        assertTrue(owner.canRestoreRow(rememberedMovie, rememberedMovie, 0L, 0L))
    }

    @Test
    fun `hero action focus cancels a pending row restoration`() {
        val owner = owner()
        val hero = NavigationPosition(
            rowKey = "hero",
            rowIndex = 0,
            itemKey = "MOVIE:7",
            itemIndex = 0,
        )

        owner.onContentFocused(hero)

        assertFalse(owner.canRestoreEntry(rememberedMovie, 0L, 0L))
        assertFalse(owner.canRestoreRow(rememberedMovie, hero, 0L, 0L))
        assertTrue(owner.canRestoreRow(hero, hero, 0L, 0L))
    }

    @Test
    fun `entry ownership also rejects a changed rail revision or position`() {
        val owner = owner()
        val other = rememberedMovie.copy(itemKey = "MOVIE:8", itemIndex = 3)

        assertFalse(owner.canRestoreEntry(rememberedMovie, 4L, 5L))
        assertFalse(owner.canRestoreEntry(other, 4L, 4L))
    }
}
