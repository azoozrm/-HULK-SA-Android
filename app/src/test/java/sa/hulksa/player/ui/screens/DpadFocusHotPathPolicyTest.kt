package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class DpadFocusHotPathPolicyTest {
    @Test
    fun fullyVisibleTarget_usesDirectFocusPath() {
        assertEquals(
            TvCatalogFocusPath.DIRECT,
            tvCatalogFocusPath(
                targetIndex = 4,
                itemCount = 12,
                itemTop = 120,
                itemBottom = 300,
                viewportStart = 100,
                viewportEnd = 500,
            ),
        )
    }

    @Test
    fun offscreenTarget_usesScrollAssistedPath() {
        assertEquals(
            TvCatalogFocusPath.SCROLL_ASSISTED,
            tvCatalogFocusPath(
                targetIndex = 8,
                itemCount = 12,
                itemTop = null,
                itemBottom = null,
                viewportStart = 100,
                viewportEnd = 500,
            ),
        )
    }

    @Test
    fun partiallyVisibleTarget_usesScrollAssistedPath() {
        assertEquals(
            TvCatalogFocusPath.SCROLL_ASSISTED,
            tvCatalogFocusPath(4, 12, 80, 260, 100, 500),
        )
        assertEquals(
            TvCatalogFocusPath.SCROLL_ASSISTED,
            tvCatalogFocusPath(4, 12, 360, 520, 100, 500),
        )
    }

    @Test
    fun invalidOrEmptyTarget_neverRequestsFocus() {
        assertEquals(
            TvCatalogFocusPath.INVALID,
            tvCatalogFocusPath(0, 0, null, null, 100, 500),
        )
        assertEquals(
            TvCatalogFocusPath.INVALID,
            tvCatalogFocusPath(12, 12, 120, 300, 100, 500),
        )
    }

    @Test
    fun rtlHorizontalAndVerticalNavigation_remainUnchanged() {
        assertEquals(7, nextTvGridFocusIndex(6, 20, 5, TvGridFocusMove.LEFT))
        assertEquals(5, nextTvGridFocusIndex(6, 20, 5, TvGridFocusMove.RIGHT))
        assertEquals(7, nextTvGridFocusIndex(2, 20, 5, TvGridFocusMove.DOWN))
        assertEquals(2, nextTvGridFocusIndex(7, 20, 5, TvGridFocusMove.UP))
    }

    @Test
    fun focusEdgesAndShortLists_doNotInventTargets() {
        assertNull(nextTvGridFocusIndex(0, 1, 5, TvGridFocusMove.LEFT))
        assertNull(nextTvGridFocusIndex(0, 1, 5, TvGridFocusMove.RIGHT))
        assertNull(nextTvGridFocusIndex(0, 1, 5, TvGridFocusMove.UP))
        assertNull(nextTvGridFocusIndex(0, 1, 5, TvGridFocusMove.DOWN))
        assertNull(nextTvGridFocusIndex(2, 20, 5, TvGridFocusMove.UP))
        assertNull(nextTvGridFocusIndex(17, 20, 5, TvGridFocusMove.DOWN))
    }

    @Test
    fun rapidRepeatedMoves_doNotLetStaleCompletionClearNewestTarget() {
        val state = TvCatalogFocusMoveState()

        state.begin(6)
        state.begin(11)
        state.complete(6)

        assertEquals(11, state.pendingTargetIndex())
        assertEquals(11, state.baseIndex(currentIndex = 1))

        state.complete(11)
        assertNull(state.pendingTargetIndex())
        assertEquals(1, state.baseIndex(currentIndex = 1))
    }

    @Test
    fun liveFocus_selectsOnlyCurrentPreview() {
        val previous = channel(1)
        val current = channel(2)

        assertTrue(isLivePreviewSelected(previous, previous))
        assertFalse(isLivePreviewSelected(previous, current))
        assertFalse(isLivePreviewSelected(current, previous))
        assertTrue(isLivePreviewSelected(current, current))
    }

    @Test
    fun livePreview_keepsCurrentVisibleChannel() {
        val current = channel(2)
        val visible = listOf(channel(1), current, channel(3))

        assertSame(
            current,
            resolveLivePreview(current, visible, rememberedItemKey = "LIVE:1", rememberedIndex = 0),
        )
    }

    @Test
    fun staleLivePreview_resolvesRememberedChannelWithoutStaleStageItem() {
        val remembered = channel(2)
        val visible = listOf(channel(1), remembered, channel(3))

        assertSame(
            remembered,
            resolveLivePreview(channel(99), visible, rememberedItemKey = "LIVE:2", rememberedIndex = 0),
        )
    }

    @Test
    fun livePreview_fallsBackToValidIndexThenFirstItem() {
        val first = channel(1)
        val indexed = channel(2)
        val visible = listOf(first, indexed)

        assertSame(
            indexed,
            resolveLivePreview(null, visible, rememberedItemKey = "LIVE:99", rememberedIndex = 1),
        )
        assertSame(
            first,
            resolveLivePreview(null, visible, rememberedItemKey = "LIVE:99", rememberedIndex = 99),
        )
        assertNull(resolveLivePreview(null, emptyList(), "LIVE:99", 0))
    }

    private fun channel(id: Int): ContentItem = ContentItem(
        id = id,
        name = "Channel $id",
        categoryId = "live",
        type = ContentType.LIVE,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = "ts",
    )
}
