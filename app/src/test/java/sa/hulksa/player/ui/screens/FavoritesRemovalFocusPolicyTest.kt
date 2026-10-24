package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sa.hulksa.player.MainDestination

/**
 * Focused regression coverage for the Favorites removal owner: a removal transition must be
 * claimed only for the actual removed focused card, the entry/return restoration must stay
 * eligible for every other content change, the replacement target follows next-then-previous,
 * and the TV long-press release guard survives held repeats and is consumed exactly once.
 */
class FavoritesRemovalFocusPolicyTest {
    private val favorites = MainDestination.FAVORITES
    private val movies = MainDestination.MOVIES
    private val previous = listOf("MOVIE:1", "SERIES:2", "LIVE:3")
    private val afterRemoval = listOf("MOVIE:1", "LIVE:3")
    private val filterKey = "favorites:all"

    @Test
    fun removedFocusedCard_isClaimedByTheRemovalOwner() {
        assertEquals(
            FavoritesGridFocusTransition.REMOVAL,
            favoritesGridFocusTransition(
                destination = favorites,
                previousKeys = previous,
                currentKeys = afterRemoval,
                previousRowKey = filterKey,
                currentRowKey = filterKey,
                focusedKey = "SERIES:2",
            ),
        )
    }

    @Test
    fun survivingFocusedCard_staysWithEntryRestoration() {
        assertEquals(
            FavoritesGridFocusTransition.NONE,
            favoritesGridFocusTransition(
                destination = favorites,
                previousKeys = previous,
                currentKeys = afterRemoval,
                previousRowKey = filterKey,
                currentRowKey = filterKey,
                focusedKey = "MOVIE:1",
            ),
        )
    }

    @Test
    fun filterSwitchAndOtherDestinations_neverMasqueradeAsRemoval() {
        assertEquals(
            FavoritesGridFocusTransition.NONE,
            favoritesGridFocusTransition(
                destination = favorites,
                previousKeys = previous,
                currentKeys = listOf("MOVIE:9"),
                previousRowKey = filterKey,
                currentRowKey = "favorites:movies",
                focusedKey = "SERIES:2",
            ),
        )
        assertEquals(
            FavoritesGridFocusTransition.NONE,
            favoritesGridFocusTransition(
                destination = movies,
                previousKeys = previous,
                currentKeys = afterRemoval,
                previousRowKey = filterKey,
                currentRowKey = filterKey,
                focusedKey = "SERIES:2",
            ),
        )
        assertEquals(
            FavoritesGridFocusTransition.NONE,
            favoritesGridFocusTransition(
                destination = favorites,
                previousKeys = previous,
                currentKeys = afterRemoval,
                previousRowKey = filterKey,
                currentRowKey = filterKey,
                focusedKey = null,
            ),
        )
    }

    @Test
    fun replacementTarget_isNextSurvivingThenPrevious() {
        // Middle removal: the item that shifted into the removed slot.
        assertEquals(1, favoritesRemovalFocusIndex(removedIndex = 1, remainingCount = 2))
        // Last-item removal: fall back to the previous item.
        assertEquals(1, favoritesRemovalFocusIndex(removedIndex = 2, remainingCount = 2))
        // Only item removed: the page-level empty handoff owns focus.
        assertNull(favoritesRemovalFocusIndex(removedIndex = 0, remainingCount = 0))
        assertNull(favoritesRemovalFocusIndex(removedIndex = -1, remainingCount = 3))
    }

    @Test
    fun releaseGuard_consumesHeldRepeatsAndRelease_andOnlyReleaseClearsIt() {
        // The release of the removing long press is consumed exactly once.
        assertEquals(
            FavoritesSelectGuardDecision.CONSUME_RELEASE,
            favoritesSelectGuardDecision(
                armed = true,
                isSelectKey = true,
                isKeyUp = true,
                keyDownRepeatCount = 0,
            ),
        )
        // Held repeats of that same press are consumed, not passed to a replacement card.
        assertEquals(
            FavoritesSelectGuardDecision.CONSUME_PRESS,
            favoritesSelectGuardDecision(
                armed = true,
                isSelectKey = true,
                isKeyUp = false,
                keyDownRepeatCount = 4,
            ),
        )
        // Even a repeat count of zero is consumed until the physical release clears the arm.
        assertEquals(
            FavoritesSelectGuardDecision.CONSUME_PRESS,
            favoritesSelectGuardDecision(
                armed = true,
                isSelectKey = true,
                isKeyUp = false,
                keyDownRepeatCount = 0,
            ),
        )
        // Unarmed or non-select events pass through untouched.
        assertEquals(
            FavoritesSelectGuardDecision.PASS,
            favoritesSelectGuardDecision(
                armed = false,
                isSelectKey = true,
                isKeyUp = true,
                keyDownRepeatCount = 0,
            ),
        )
        assertEquals(
            FavoritesSelectGuardDecision.PASS,
            favoritesSelectGuardDecision(
                armed = true,
                isSelectKey = false,
                isKeyUp = true,
                keyDownRepeatCount = 0,
            ),
        )
    }
}
