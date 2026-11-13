package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.LayoutDirection
import sa.hulksa.player.MainDestination

/**
 * Bounded regression for the TV navigation-rail dismissal invariant:
 *
 * - activating the already selected destination is a dismissal, not navigation;
 * - the content focus group is the single restoration owner and keeps the exact originating
 *   control/row/scroll context;
 * - a genuinely failed content request arms the existing destination-entry handoff exactly once as
 *   the deterministic fallback (surviving/empty content), so two focus owners never compete;
 * - a genuine destination change keeps the accepted change-destination path.
 */
class TvRailSelectionPolicyTest {
    private val home = MainDestination.HOME
    private val movies = MainDestination.MOVIES

    @Test
    fun `activating the selected destination only dismisses the rail`() {
        for (destination in MainDestination.entries) {
            assertEquals(
                TvRailSelectionIntent.RETURN_TO_CONTENT,
                tvRailSelectionIntent(
                    selectedDestination = destination,
                    requestedDestination = destination,
                ),
            )
        }
    }

    @Test
    fun `a genuine destination change keeps the accepted change-destination path`() {
        assertEquals(
            TvRailSelectionIntent.CHANGE_DESTINATION,
            tvRailSelectionIntent(selectedDestination = home, requestedDestination = movies),
        )
        assertEquals(
            TvRailSelectionIntent.CHANGE_DESTINATION,
            tvRailSelectionIntent(selectedDestination = movies, requestedDestination = home),
        )
    }

    @Test
    fun `a surviving origin is restored by the content owner without an entry handoff`() {
        var entryHandoffs = 0
        val result = returnTvRailFocusToContent(
            restoreContentFocus = { true },
            armEntryHandoff = { entryHandoffs += 1 },
        )

        assertEquals(TvRailReturnResult.CONTENT, result)
        assertEquals(0, entryHandoffs)
    }

    @Test
    fun `a vanished origin falls back to the existing entry handoff exactly once`() {
        var entryHandoffs = 0
        val result = returnTvRailFocusToContent(
            restoreContentFocus = { false },
            armEntryHandoff = { entryHandoffs += 1 },
        )

        assertEquals(TvRailReturnResult.ENTRY_HANDOFF, result)
        assertEquals(1, entryHandoffs)
    }

    @Test
    fun `a failed content request cannot throw or double-arm the entry handoff`() {
        var entryHandoffs = 0
        val result = returnTvRailFocusToContent(
            restoreContentFocus = { error("content group is not attached") },
            armEntryHandoff = { entryHandoffs += 1 },
        )

        assertEquals(TvRailReturnResult.ENTRY_HANDOFF, result)
        assertEquals(1, entryHandoffs)
    }

    @Test
    fun `a successful content return never reports the fallback`() {
        val result = returnTvRailFocusToContent(
            restoreContentFocus = { true },
            armEntryHandoff = { error("entry handoff must not run after a real content return") },
        )

        assertEquals(TvRailReturnResult.CONTENT, result)
    }

    @Test
    fun `the content return direction follows the layout start edge`() {
        assertEquals(Key.DirectionLeft, tvRailContentReturnDirection(LayoutDirection.Rtl))
        assertEquals(Key.DirectionRight, tvRailContentReturnDirection(LayoutDirection.Ltr))
    }
}
