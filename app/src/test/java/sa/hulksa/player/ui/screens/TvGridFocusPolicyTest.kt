package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvGridFocusPolicyTest {
    @Test
    fun verticalMovement_preservesColumn() {
        assertEquals(7, nextTvGridFocusIndex(2, 20, 5, TvGridFocusMove.DOWN))
        assertEquals(2, nextTvGridFocusIndex(7, 20, 5, TvGridFocusMove.UP))
    }

    @Test
    fun finalShortRow_usesNearestCardInsteadOfFirstColumn() {
        assertEquals(12, nextTvGridFocusIndex(8, 13, 5, TvGridFocusMove.DOWN))
    }

    @Test
    fun rtlHorizontalMovement_staysInSameRow() {
        assertEquals(7, nextTvGridFocusIndex(6, 20, 5, TvGridFocusMove.LEFT))
        assertEquals(5, nextTvGridFocusIndex(6, 20, 5, TvGridFocusMove.RIGHT))
        assertNull(nextTvGridFocusIndex(9, 20, 5, TvGridFocusMove.LEFT))
        assertNull(nextTvGridFocusIndex(5, 20, 5, TvGridFocusMove.RIGHT))
    }

    @Test
    fun gridEdges_doNotInventTargets() {
        assertNull(nextTvGridFocusIndex(2, 20, 5, TvGridFocusMove.UP))
        assertNull(nextTvGridFocusIndex(17, 20, 5, TvGridFocusMove.DOWN))
    }
}
