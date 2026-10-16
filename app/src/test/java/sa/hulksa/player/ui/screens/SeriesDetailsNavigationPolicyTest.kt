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
    fun selectedSeasonIndexKeepsValidSelectionAndFallsBackForInvalidOrEmpty() {
        assertEquals(1, seriesSelectedSeasonFocusIndex(selectedSeason = 3, seasons = listOf(1, 3, 7)))
        assertEquals(2, seriesSelectedSeasonFocusIndex(selectedSeason = 7, seasons = listOf(1, 3, 7)))
        assertEquals(0, seriesSelectedSeasonFocusIndex(selectedSeason = 2, seasons = listOf(1, 3, 7)))
        assertEquals(0, seriesSelectedSeasonFocusIndex(selectedSeason = 0, seasons = listOf(1, 3, 7)))
        assertNull(seriesSelectedSeasonFocusIndex(selectedSeason = 5, seasons = emptyList()))
    }

    @Test
    fun seasonDownKeepsRememberedEpisodeAndFallsBackToFirst() {
        val visibleEpisodeIds = listOf(41, 42, 43)
        assertEquals(1, seriesSeasonDownEpisodeIndex(visibleEpisodeIds, rememberedEpisodeId = 42))
        assertEquals(0, seriesSeasonDownEpisodeIndex(visibleEpisodeIds, rememberedEpisodeId = 99))
        assertEquals(0, seriesSeasonDownEpisodeIndex(visibleEpisodeIds, rememberedEpisodeId = null))
        assertNull(seriesSeasonDownEpisodeIndex(emptyList(), rememberedEpisodeId = 42))
    }

    @Test
    fun firstEpisodeRowUpIsColumnIndependentAndLaterRowsStepOneRowUp() {
        for (column in 0 until 4) {
            assertNull(seriesEpisodeUpTargetIndex(episodeIndex = column, columns = 4))
        }
        assertEquals(0, seriesEpisodeUpTargetIndex(episodeIndex = 4, columns = 4))
        assertEquals(1, seriesEpisodeUpTargetIndex(episodeIndex = 5, columns = 4))
        assertEquals(5, seriesEpisodeUpTargetIndex(episodeIndex = 9, columns = 4))
        assertNull(seriesEpisodeUpTargetIndex(episodeIndex = 0, columns = 0))
    }

    @Test
    fun newerSeasonHandoffRequestInvalidatesTheOlderToken() {
        val owner = SeriesFocusRestorationOwner()
        val pending = owner.begin()
        owner.invalidate()
        assertFalse(owner.isCurrent(pending))
        val next = owner.begin()
        assertTrue(owner.isCurrent(next))
    }

    @Test
    fun lazyTargetIndexResolvesTheCurrentStructureForEverySlot() {
        assertEquals(0, seriesLazyTargetIndex(SeriesLazyTargetSlot.HERO, tabsIndex = 1, rowIndex = 3, totalItemsCount = 9))
        assertEquals(1, seriesLazyTargetIndex(SeriesLazyTargetSlot.TABS, tabsIndex = 1, rowIndex = 0, totalItemsCount = 9))
        assertEquals(2, seriesLazyTargetIndex(SeriesLazyTargetSlot.EPISODES_HEADER, tabsIndex = 1, rowIndex = 0, totalItemsCount = 9))
        assertEquals(5, seriesLazyTargetIndex(SeriesLazyTargetSlot.EPISODE_ROW, tabsIndex = 1, rowIndex = 2, totalItemsCount = 9))
        // The same request shifts by one item when an error notice is inserted before the tabs.
        assertEquals(3, seriesLazyTargetIndex(SeriesLazyTargetSlot.EPISODES_HEADER, tabsIndex = 2, rowIndex = 0, totalItemsCount = 10))
        // A removed row or a stale index is never focused.
        assertNull(seriesLazyTargetIndex(SeriesLazyTargetSlot.EPISODE_ROW, tabsIndex = 1, rowIndex = 6, totalItemsCount = 9))
        assertNull(seriesLazyTargetIndex(SeriesLazyTargetSlot.TABS, tabsIndex = 3, rowIndex = 0, totalItemsCount = 3))
        assertNull(seriesLazyTargetIndex(SeriesLazyTargetSlot.HERO, tabsIndex = 1, rowIndex = 0, totalItemsCount = 0))
    }

    @Test
    fun newerDirectionalCommandCancelsThePendingRevealBeforeFocus() = runBlocking {
        val owner = SeriesFocusRestorationOwner()
        val downToken = owner.begin()
        val revealGate = CompletableDeferred<Unit>()
        var focusCalls = 0
        var missingCalls = 0
        var result: Boolean? = null

        val downJob = launch {
            result = seriesLazyRevealFocus(
                isCurrent = { owner.isCurrent(downToken) },
                reveal = { revealGate.await(); true },
                requestFocus = { focusCalls += 1; true },
                onTargetMissing = { missingCalls += 1 },
            )
        }

        yield()
        // UP arrives while the DOWN reveal is still suspended.
        val upToken = owner.begin()
        assertFalse(owner.isCurrent(downToken))
        assertTrue(owner.isCurrent(upToken))
        revealGate.complete(Unit)
        downJob.join()

        assertEquals(0, focusCalls)
        assertEquals(0, missingCalls)
        assertNull("a superseded transfer must not focus or report success", result)
    }

    @Test
    fun structuralChangeDuringAPendingRevealCancelsFocus() = runBlocking {
        val owner = SeriesFocusRestorationOwner()
        val token = owner.begin()
        val revealGate = CompletableDeferred<Unit>()
        var focusCalls = 0
        var result: Boolean? = null

        val job = launch {
            result = seriesLazyRevealFocus(
                isCurrent = { owner.isCurrent(token) },
                reveal = { revealGate.await(); true },
                requestFocus = { focusCalls += 1; true },
                onTargetMissing = { },
            )
        }

        yield()
        // Error notice insertion/removal, episode-list or column-count change invalidates.
        owner.invalidate()
        revealGate.complete(Unit)
        job.join()

        assertEquals(0, focusCalls)
        assertNull(result)
    }

    @Test
    fun targetThatCannotAttachCancelsInsteadOfRetryingOrReportingSuccess() = runBlocking {
        var focusCalls = 0
        var missingCalls = 0
        val result = seriesLazyRevealFocus(
            isCurrent = { true },
            reveal = { false },
            requestFocus = { focusCalls += 1; true },
            onTargetMissing = { missingCalls += 1 },
        )

        assertNull(result)
        assertEquals(0, focusCalls)
        assertEquals(1, missingCalls)
    }

    @Test
    fun failedFocusBooleanIsReportedAndNotTreatedAsSuccess() = runBlocking {
        val denied = seriesLazyRevealFocus(
            isCurrent = { true },
            reveal = { true },
            requestFocus = { false },
            onTargetMissing = { },
        )
        assertEquals(false, denied)

        val granted = seriesLazyRevealFocus(
            isCurrent = { true },
            reveal = { true },
            requestFocus = { true },
            onTargetMissing = { },
        )
        assertEquals(true, granted)
    }

    @Test
    fun cancelledLazyRevealPerformsNoFocusOrCancellation() = runBlocking {
        val owner = SeriesFocusRestorationOwner()
        val token = owner.begin()
        val revealGate = CompletableDeferred<Unit>()
        var focusCalls = 0
        var missingCalls = 0

        val job = launch {
            seriesLazyRevealFocus(
                isCurrent = { owner.isCurrent(token) },
                reveal = { revealGate.await(); true },
                requestFocus = { focusCalls += 1; true },
                onTargetMissing = { missingCalls += 1 },
            )
        }

        yield()
        job.cancel()
        revealGate.complete(Unit)
        job.join()

        assertEquals(0, focusCalls)
        assertEquals(0, missingCalls)
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
