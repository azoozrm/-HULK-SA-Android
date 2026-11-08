package sa.hulksa.player.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveProjectionFocusContractTest {
    @Test
    fun `live focus and preview wait for the exact current projection input`() {
        val source = File("src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt").readText()
        val effect = source
            .substringAfter("LaunchedEffect(\n        exactProjection,")
            .substringBefore("LaunchedEffect(state.selectedCategoryId, hiddenCategoryIds)")
        val projection = source
            .substringAfter("val exactProjection by produceState")
            .substringBefore("var showCategoryManager")

        assertTrue(effect.contains("val exact = exactVisible ?: return@LaunchedEffect"))
        assertTrue(effect.contains("exact.isEmpty()"))
        assertTrue(effect.contains("visible = exact"))
        assertFalse(effect.contains("if (visible.isEmpty())"))
        assertTrue(projection.contains("exactProjection?.exactItemsFor(projectionInput)"))
        assertTrue(projection.contains("lastGoodLiveProjection(catalog)"))
    }
}
