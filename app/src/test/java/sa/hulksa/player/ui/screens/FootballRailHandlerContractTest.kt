package sa.hulksa.player.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source contract for the wired Home football rail transition. */
class FootballRailHandlerContractTest {

    private fun mainShellSource(): String {
        val candidates = listOf(
            File("src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"),
            File("app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"),
        )
        return candidates.first { it.isFile }.readText()
    }

    @Test
    fun `fully visible refusal returns the normal key route`() {
        val source = mainShellSource()
        assertTrue(source.contains("footballRailVisibleRoute(railPlacement(targetKey)?.scrollDeltaPx == 0)"))
        assertTrue(source.contains("FootballRailVisibleRoute.NORMAL_ROUTE -> return@onPreviewKeyEvent false"))
        assertTrue(source.contains("FootballRailVisibleRoute.CONSUMED -> return@onPreviewKeyEvent true"))
    }

    @Test
    fun `collector is gated per emission, not once before collection`() {
        val source = mainShellSource()
        assertTrue(source.contains("val ownedDelta = railFocusOwner.collectorScrollDelta(delta)"))
        assertFalse(source.contains("LaunchedEffect(focusedCardKey, matches, railFocusOwner.isActive)"))
        assertFalse(source.contains("if (railFocusOwner.isActive) return@LaunchedEffect"))
    }

    @Test
    fun `owner cancels when focus leaves the row`() {
        val source = mainShellSource()
        assertTrue(source.contains("if (railFocusRevision != homeReturnRailBaseline) railFocusOwner.cancel()"))
        assertTrue(source.contains("onFocusChanged { if (it.isFocused) railFocusOwner.cancel() }"))
        assertFalse(source.contains("if (focusedCardKey == null) railFocusOwner.cancel()"))
    }

    @Test
    fun `rail margin and row padding share the endpoint token`() {
        val source = mainShellSource()
        assertTrue(source.contains("HOME_FOOTBALL_RAIL_EDGE_PADDING_DP.dp.roundToPx()"))
        assertTrue(
            source.contains(
                "PaddingValues(horizontal = HOME_FOOTBALL_RAIL_EDGE_PADDING_DP.dp, vertical = 7.dp)",
            ),
        )
    }
}
