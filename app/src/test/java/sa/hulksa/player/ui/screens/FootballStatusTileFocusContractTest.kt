package sa.hulksa.player.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for the R05.9H review finding: a focus observer placed after `focusable()` in
 * the modifier chain never sees the tile's own focus change, so the Home restoration handshake
 * (`emptyPlaced` / `emptyFocused`) can wait forever.
 */
class FootballStatusTileFocusContractTest {

    private fun mainShellSource(): String {
        val candidates = listOf(
            File("src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"),
            File("app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"),
        )
        return candidates.first { it.isFile }.readText()
    }

    @Test
    fun `status tile observes focus before creating its focus target`() {
        val source = mainShellSource()
        val function = source
            .substringAfter("private fun FootballFeedStatusCard(")
            .substringBefore("\nprivate fun ")
        val requester = function.indexOf(".then(buttonRequester?.let(Modifier::focusRequester) ?: Modifier)")
        val observer = function.indexOf(".onFocusChanged")
        val focusable = function.indexOf(".focusable()")
        assertTrue("requester modifier missing", requester >= 0)
        assertTrue("focus observer missing", observer >= 0)
        assertTrue("focusable target missing", focusable >= 0)
        assertTrue("focus observer must wrap focusable()", observer < focusable)
        assertTrue("requester must be attached before the observer", requester < observer)
    }
}
