package sa.hulksa.player.ui.screens

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioral model of the wired Home rail: a collector for the source card is already subscribed
 * before the key, and the transition writes at the same time. The test uses the production
 * scroll-delta/full-visibility functions and the production single-owner gate.
 */
class FootballRailScrollConflictTest {

    private val railViewport = 500
    private val railCardWidth = 302
    private val railSpacing = 12
    private val railPadding = 5
    private val railMargin = 5

    private data class Rail(
        val scroll: Int,
        val padding: Int,
        val cardWidth: Int,
        val spacing: Int,
    ) {
        fun offset(index: Int): Int = padding + index * (cardWidth + spacing) - scroll
    }

    private fun delta(rail: Rail, index: Int): Int = footballFocusScrollDelta(
        itemOffset = rail.offset(index),
        itemSize = railCardWidth,
        viewportStart = 0,
        viewportEnd = railViewport,
        marginPx = railMargin,
    )

    private fun fullyVisible(rail: Rail, index: Int): Boolean = footballRailFullyVisible(
        itemOffset = rail.offset(index),
        itemSize = railCardWidth,
        viewportStart = 0,
        viewportEnd = railViewport,
        marginPx = railMargin,
        canScrollBackward = rail.scroll > 0,
        canScrollForward = true,
    )

    private data class Result(
        val targetVisible: Boolean,
        val sourceVisible: Boolean,
        val focusRequests: List<String>,
        val scroll: Int,
    )

    private fun runFrames(
        initialScroll: Int,
        source: Int,
        target: Int,
        gated: Boolean,
        frames: Int = 6,
    ): Result = runBlocking {
        var rail = Rail(initialScroll, railPadding, railCardWidth, railSpacing)
        val requests = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val owner = FootballRailFocusOwner()
        if (gated) {
            owner.start(this) { gate.await() }
            assertTrue(owner.isActive)
        }
        repeat(frames) {
            // Transition writer: reveal the target when it is not fully visible.
            if (!fullyVisible(rail, target)) {
                val transitionDelta = delta(rail, target)
                if (transitionDelta != 0) rail = rail.copy(scroll = rail.scroll + transitionDelta)
            }
            // Already-subscribed source collector, evaluated on every layout emission.
            val sourceDelta = delta(rail, source)
            val collectorDelta = if (gated) owner.collectorScrollDelta(sourceDelta) else sourceDelta
            if (collectorDelta != 0 && !fullyVisible(rail, source)) {
                rail = rail.copy(scroll = rail.scroll + collectorDelta)
            }
        }
        if (fullyVisible(rail, target)) requests += "target" else requests += "source"
        if (gated) gate.complete(Unit)
        Result(
            targetVisible = fullyVisible(rail, target),
            sourceVisible = fullyVisible(rail, source),
            focusRequests = requests,
            scroll = rail.scroll,
        )
    }

    @Test
    fun `left move wins when the already-subscribed collector is gated`() {
        val result = runFrames(initialScroll = 0, source = 0, target = 1, gated = true)
        assertTrue(result.targetVisible)
        assertEquals(listOf("target"), result.focusRequests)
    }

    @Test
    fun `left move oscillates under the legacy ungated collector`() {
        val result = runFrames(initialScroll = 0, source = 0, target = 1, gated = false)
        assertFalse(result.targetVisible)
        assertEquals(listOf("source"), result.focusRequests)
    }

    @Test
    fun `right move wins when the already-subscribed collector is gated`() {
        val result = runFrames(initialScroll = 126, source = 1, target = 0, gated = true)
        assertTrue(result.targetVisible)
        assertEquals(listOf("target"), result.focusRequests)
    }

    @Test
    fun `right move oscillates under the legacy ungated collector`() {
        val result = runFrames(initialScroll = 126, source = 1, target = 0, gated = false)
        assertFalse(result.targetVisible)
        assertEquals(listOf("source"), result.focusRequests)
    }

    @Test
    fun `offscreen target is revealed by the gated transition`() {
        val result = runFrames(initialScroll = 0, source = 0, target = 2, gated = true)
        assertTrue(result.targetVisible)
        assertEquals(listOf("target"), result.focusRequests)
    }

    @Test
    fun `reorder during the final frame blocks a clipped request and re-reveals`() {
        var keys = listOf("b", "c", "a")
        var scroll = 440
        fun offset(index: Int) = railPadding + index * (railCardWidth + railSpacing) - scroll
        val target = "a"
        var index = footballRailTargetIndex(keys, target)!!
        assertTrue(
            footballRailFullyVisible(
                offset(index), railCardWidth, 0, railViewport, railMargin,
                canScrollBackward = scroll > 0, canScrollForward = true,
            ),
        )
        keys = listOf("a", "b", "c")
        index = footballRailTargetIndex(keys, target)!!
        assertEquals(0, index)
        assertFalse(
            footballRailFullyVisible(
                offset(index), railCardWidth, 0, railViewport, railMargin,
                canScrollBackward = scroll > 0, canScrollForward = true,
            ),
        )
        scroll = 0
        assertTrue(
            footballRailFullyVisible(
                offset(index), railCardWidth, 0, railViewport, railMargin,
                canScrollBackward = false, canScrollForward = true,
            ),
        )
        assertNull(footballRailTargetIndex(keys, "removed"))
    }

    @Test
    fun `visible target refusal returns the normal route`() {
        assertEquals(
            FootballRailVisibleRoute.CONSUMED,
            footballRailVisibleRoute(fullyVisible = true) { true },
        )
        assertEquals(
            FootballRailVisibleRoute.NORMAL_ROUTE,
            footballRailVisibleRoute(fullyVisible = true) { false },
        )
        assertEquals(
            FootballRailVisibleRoute.SLOW_PATH,
            footballRailVisibleRoute(fullyVisible = false) { true },
        )
    }

    @Test
    fun `stable identity resolves after reorder, never the inherited slot`() {
        val before = listOf("a", "b", "c")
        val after = listOf("c", "a", "b")
        assertEquals(0, footballRailTargetIndex(before, "a"))
        assertEquals(1, footballRailTargetIndex(after, "a"))
        assertEquals("a", after[footballRailTargetIndex(after, "a")!!])
        assertNull(footballRailTargetIndex(after, "removed"))
    }

    @Test
    fun `stable key survives reorder and same-card return`() {
        val keys = listOf("a", "b", "c")
        assertEquals(2, footballRailTargetIndex(keys, "c"))
        assertEquals(0, footballRailTargetIndex(listOf("c", "a", "b"), "c"))
        assertEquals(1, footballRailTargetIndex(keys, "b"))
        assertEquals(null, footballRailTargetIndex(listOf("a", "b"), "c"))
    }

    private inner class LaidOutRail {
        var keys = listOf("a", "b", "c")
        var measuredKeys = keys
        var scroll = 0
        var focused: String? = "a"
        val requests = mutableListOf<String>()

        fun placement(key: String): FootballRailPlacement? {
            val index = keys.indexOf(key)
            if (index < 0) return null
            val rail = Rail(scroll, railPadding, railCardWidth, railSpacing)
            val offset = rail.offset(index)
            val measured = measuredKeys.getOrNull(index) == key &&
                offset < railViewport && offset + railCardWidth > 0
            if (!measured) return FootballRailPlacement(index, null)
            val visible = footballRailFullyVisible(
                offset, railCardWidth, 0, railViewport, railMargin,
                canScrollBackward = scroll > 0, canScrollForward = scroll < 440,
            )
            return FootballRailPlacement(index, if (visible) 0 else delta(rail, index))
        }

        suspend fun focus(
            target: () -> String?,
            onFrame: () -> Unit = {},
            canMove: () -> Boolean = { true },
        ): Boolean = focusFootballRailCard(
            targetKey = target,
            locate = ::placement,
            reveal = { position ->
                scroll = position.scrollDeltaPx?.let { scroll + it }
                    ?.coerceIn(0, 440)
                    ?: (position.index * (railCardWidth + railSpacing)).coerceAtMost(440)
            },
            awaitFrame = { onFrame() },
            isFocused = { focused == it },
            requestFocus = { key ->
                assertEquals("focus only a fully visible card", 0, placement(key)?.scrollDeltaPx)
                requests += key
                focused = key
            },
            canMoveFocus = canMove,
        )
    }

    @Test
    fun `one press reveals each adjacent card in both directions across both edges`() = runBlocking {
        val rail = LaidOutRail()
        for (left in listOf(true, true, false, false)) {
            val index = rail.keys.indexOf(checkNotNull(rail.focused))
            val next = footballRailAdjacentIndex(index, rail.keys.size, left)!!
            val key = rail.keys[next]
            assertTrue(rail.focus({ key }))
            assertEquals(key, rail.focused)
            assertEquals(0, rail.placement(key)?.scrollDeltaPx)
        }
        assertEquals(listOf("b", "c", "b", "a"), rail.requests)
        assertEquals(0, rail.scroll)
    }

    @Test
    fun `a reorder between reveal and commit reveals the live key again`() = runBlocking {
        val rail = LaidOutRail()
        var frame = 0
        assertTrue(
            rail.focus(
                target = { "c" },
                onFrame = {
                    when (++frame) {
                        2 -> {
                            rail.keys = listOf("c", "a", "b")
                            rail.focused = null
                        }
                        3 -> rail.measuredKeys = rail.keys
                    }
                },
            ),
        )
        assertEquals(listOf("c"), rail.requests)
        assertEquals("c", rail.focused)
        assertEquals(0, rail.scroll)
    }

    @Test
    fun `details return keeps the match visible when order changes after the request`() = runBlocking {
        val rail = LaidOutRail().apply { focused = null; scroll = 440 }
        var reordered = false
        assertTrue(
            rail.focus(
                target = { "a" },
                onFrame = {
                    if (rail.requests.isNotEmpty() && !reordered) {
                        rail.keys = listOf("b", "c", "a")
                        rail.measuredKeys = rail.keys
                        rail.focused = null
                        reordered = true
                    }
                },
            ),
        )
        assertEquals(listOf("a", "a"), rail.requests)
        assertEquals("a", rail.focused)
        assertEquals(440, rail.scroll)
    }

    @Test
    fun `removed details match falls back to a visible first card and leave cancels the move`() = runBlocking {
        val rail = LaidOutRail().apply { focused = null; scroll = 440 }
        assertFalse(rail.focus({ "removed" }))
        assertFalse(rail.focus({ rail.keys.firstOrNull() }, canMove = { false }))
        assertTrue(rail.requests.isEmpty())
        assertTrue(rail.focus({ rail.keys.firstOrNull() }))
        assertEquals(listOf("a"), rail.requests)
        assertEquals(0, rail.placement("a")?.scrollDeltaPx)
    }
}
