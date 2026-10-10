package sa.hulksa.player.ui.screens

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesDetailsNavigationPolicyTest {
    @Test
    fun firstUpFromEachWatchHeaderTabHandsOffToThePrimaryAction() {
        assertTrue(seriesDetailsTabUpHandsOffToWatchAction(SeriesDetailsTab.EPISODES))
        assertTrue(seriesDetailsTabUpHandsOffToWatchAction(SeriesDetailsTab.INFORMATION))
        assertTrue(seriesDetailsTabUpHandsOffToWatchAction(SeriesDetailsTab.RELATED))
        assertFalse(seriesDetailsTabUpHandsOffToWatchAction(SeriesDetailsTab.STORY))
    }

    @Test
    fun selectionRevealStaysScopedToTheInformationalTabs() {
        assertFalse(seriesDetailsTabSelectionNeedsReveal(SeriesDetailsTab.EPISODES))
        assertTrue(seriesDetailsTabSelectionNeedsReveal(SeriesDetailsTab.INFORMATION))
        assertTrue(seriesDetailsTabSelectionNeedsReveal(SeriesDetailsTab.RELATED))
        assertFalse(seriesDetailsTabSelectionNeedsReveal(SeriesDetailsTab.STORY))
    }

    @Test
    fun firstGrantedFocusContinuesPastFalseAndUnavailableTargets() {
        var tabCalls = 0
        var primaryCalls = 0
        var heroCalls = 0

        val granted = seriesDetailsFirstGrantedFocus(
            listOf(
                { tabCalls += 1; false },
                { primaryCalls += 1; throw IllegalStateException("focus target not attached") },
                { heroCalls += 1; true },
            ),
        )

        assertTrue(granted)
        assertEquals(1, tabCalls)
        assertEquals(1, primaryCalls)
        assertEquals(1, heroCalls)
    }

    @Test
    fun firstGrantedFocusStopsAtTheFirstGrantedRequest() {
        var primaryCalls = 0
        var heroCalls = 0

        val granted = seriesDetailsFirstGrantedFocus(
            listOf(
                { true },
                { primaryCalls += 1; true },
                { heroCalls += 1; true },
            ),
        )

        assertTrue(granted)
        assertEquals(0, primaryCalls)
        assertEquals(0, heroCalls)
    }

    @Test
    fun firstGrantedFocusReportsFailureWhenNoRequestIsGranted() {
        var calls = 0

        val granted = seriesDetailsFirstGrantedFocus(
            listOf(
                { calls += 1; false },
                { calls += 1; throw IllegalStateException("focus target not attached") },
            ),
        )

        assertFalse(granted)
        assertEquals(2, calls)
    }

    @Test
    fun restorationFocusesOnlyAfterTheRevealCompletes() = runBlocking {
        val revealDone = CompletableDeferred<Unit>()
        var focused = false
        val restored = launch {
            seriesDetailsRestoreFocus(
                isCurrent = { true },
                reveal = { revealDone.await() },
                requests = listOf({ focused = true; true }),
            )
        }

        yield()
        assertFalse("no focus before the reveal completes", focused)
        revealDone.complete(Unit)
        restored.join()
        assertTrue(focused)
    }

    @Test
    fun newErrorDuringRevealCancelsTheStaleRestoration() = runBlocking {
        val owner = SeriesFocusRestorationOwner()
        val token = owner.begin()
        val revealDone = CompletableDeferred<Unit>()
        var focusCalls = 0
        var result: Boolean? = null

        val job = launch {
            result = seriesDetailsRestoreFocus(
                isCurrent = { owner.isCurrent(token) },
                reveal = { revealDone.await() },
                requests = listOf({ focusCalls += 1; true }),
            )
        }

        yield()
        // A new error/selection/key invalidates the request while the reveal is suspended.
        owner.invalidate()
        revealDone.complete(Unit)
        job.join()

        assertEquals(0, focusCalls)
        assertNull("invalidated request must not fall through or report success", result)
    }

    @Test
    fun newerHeroFocusTransferCancelsThePendingRestoration() = runBlocking {
        val owner = SeriesFocusRestorationOwner()
        val token = owner.begin()
        val revealDone = CompletableDeferred<Unit>()
        var focusCalls = 0
        var result: Boolean? = null

        val job = launch {
            result = seriesDetailsRestoreFocus(
                isCurrent = { owner.isCurrent(token) },
                reveal = { revealDone.await() },
                requests = listOf({ focusCalls += 1; true }, { focusCalls += 1; true }),
            )
        }

        yield()
        // The shared actions bar transfers the hero focus owner to a newer user action while the
        // restore is still suspended; the old request must not pull focus back to the tab or
        // fall through to another target.
        seriesDetailsNoteNewFocusOwner(owner)
        revealDone.complete(Unit)
        job.join()

        assertEquals(0, focusCalls)
        assertNull(result)
    }

    @Test
    fun cancelledRestorationJobPerformsNoFocusOrFallback() = runBlocking {
        val owner = SeriesFocusRestorationOwner()
        val token = owner.begin()
        val revealDone = CompletableDeferred<Unit>()
        var focusCalls = 0

        val job = launch {
            seriesDetailsRestoreFocus(
                isCurrent = { owner.isCurrent(token) },
                reveal = { revealDone.await() },
                requests = listOf({ focusCalls += 1; true }, { focusCalls += 1; true }),
            )
        }

        yield()
        job.cancel()
        revealDone.complete(Unit)
        job.join()

        assertEquals(0, focusCalls)
    }

    @Test
    fun selectionOrRouteChangeInvalidatesOlderTokens() {
        val owner = SeriesFocusRestorationOwner()
        val first = owner.begin()
        val second = owner.begin()
        assertFalse("a newer request invalidates the older one", owner.isCurrent(first))
        assertTrue(owner.isCurrent(second))
        owner.invalidate()
        assertFalse(owner.isCurrent(second))
    }

    @Test
    fun completedRevealWithoutAnyGrantIsFalseNotCancellation() = runBlocking {
        var result: Boolean? = null
        val job = launch {
            result = seriesDetailsRestoreFocus(
                isCurrent = { true },
                reveal = { },
                requests = listOf({ false }, { false }),
            )
        }
        job.join()
        assertEquals(false, result)
    }

    @Test
    fun unattachedTargetRevealsThenFallsThroughToTheValidTarget() = runBlocking {
        var secondCalls = 0
        var result: Boolean? = null
        val job = launch {
            result = seriesDetailsRestoreFocus(
                isCurrent = { true },
                reveal = { },
                requests = listOf(
                    { throw IllegalStateException("focus target not attached") },
                    { secondCalls += 1; true },
                ),
            )
        }
        job.join()
        assertEquals(true, result)
        assertEquals(1, secondCalls)
    }
}
