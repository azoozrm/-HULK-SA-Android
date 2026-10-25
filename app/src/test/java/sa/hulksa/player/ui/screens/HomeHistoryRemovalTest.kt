package sa.hulksa.player.ui.screens

import androidx.compose.ui.input.key.KeyEventType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHistoryRemovalTest {
    private val keys = listOf("MOVIE:1", "SERIES:2", "MOVIE:3")

    @Test
    fun removalTarget_prefersNextThenPreviousThenNull() {
        assertEquals(
            "MOVIE:3",
            removalFocusTarget(keys, listOf("MOVIE:1", "MOVIE:3"), "SERIES:2"),
        )
        assertEquals(
            "SERIES:2",
            removalFocusTarget(keys, listOf("MOVIE:1", "SERIES:2"), "MOVIE:3"),
        )
        assertEquals(
            "SERIES:2",
            removalFocusTarget(keys, listOf("SERIES:2", "MOVIE:3"), "MOVIE:1"),
        )
        assertNull(removalFocusTarget(keys, emptyList(), "SERIES:2"))
        assertNull(removalFocusTarget(keys, listOf("MOVIE:1", "MOVIE:3"), "MISSING"))
    }

    @Test
    fun removedContinueWatchingRow_usesNextContentRowOrPreviousOrRailFallback() {
        val rows = listOf("continue", "downloads", "recommended")
        assertEquals(
            "downloads",
            removalFocusTarget(rows, listOf("downloads", "recommended"), "continue"),
        )
        assertEquals(
            "recommended",
            removalFocusTarget(
                listOf("downloads", "continue", "recommended"),
                listOf("downloads", "recommended"),
                "continue",
            ),
        )
        assertEquals(
            "downloads",
            removalFocusTarget(
                listOf("downloads", "continue"),
                listOf("downloads"),
                "continue",
            ),
        )
        assertNull(removalFocusTarget(rows, emptyList(), "continue"))
    }

    @Test
    fun removalController_deliversOneSuccessAndSurvivesDuplicatePress() {
        var removeCalls = 0
        var callback: ((Boolean) -> Unit)? = null
        var successOutcomes = 0
        var noticePosts = 0
        var wasLast = false
        val controller = HomeHistoryRemovalController(
            removeHistory = { _, result ->
                removeCalls += 1
                callback = result
            },
            onSuccessfulRemoval = { last ->
                successOutcomes += 1
                wasLast = last
            },
            onSuccessNotice = { _ -> noticePosts += 1 },
        )

        assertTrue(controller.begin("SERIES:2", keys))
        assertFalse(controller.begin("MOVIE:1", keys))
        assertEquals(1, removeCalls)
        assertEquals("SERIES:2", controller.pendingKey)
        assertNull(controller.focusTarget(listOf("MOVIE:1", "MOVIE:3")))

        callback?.invoke(true)
        callback?.invoke(true)
        assertTrue(controller.succeeded)
        assertEquals(1, successOutcomes)
        assertEquals(1, noticePosts)
        assertFalse(wasLast)
        assertEquals("MOVIE:3", controller.focusTarget(listOf("MOVIE:1", "MOVIE:3")))
        controller.finish()
        assertNull(controller.pendingKey)

        assertTrue(controller.begin("MOVIE:1", listOf("MOVIE:1")))
        callback?.invoke(true)
        assertEquals(2, successOutcomes)
        assertEquals(2, noticePosts)
        assertTrue(wasLast)
    }

    @Test
    fun removalController_publishesTheTitleCapturedBeforeMutation() {
        var callback: ((Boolean) -> Unit)? = null
        val notices = mutableListOf<String>()
        val controller = HomeHistoryRemovalController(
            removeHistory = { _, result -> callback = result },
            onSuccessfulRemoval = {},
            onSuccessNotice = { title -> notices += title },
        )

        assertTrue(controller.begin("SERIES:2", keys, "حبتين"))
        assertEquals("حبتين", controller.capturedTitle)
        callback?.invoke(true)
        callback?.invoke(true)
        assertEquals(listOf("حبتين"), notices)
    }

    @Test
    fun removalController_failureNeverPostsSuccessOrFocusTarget() {
        var callback: ((Boolean) -> Unit)? = null
        var successOutcomes = 0
        var noticePosts = 0
        val controller = HomeHistoryRemovalController(
            removeHistory = { _, result -> callback = result },
            onSuccessfulRemoval = { successOutcomes += 1 },
            onSuccessNotice = { _ -> noticePosts += 1 },
        )

        assertTrue(controller.begin("MOVIE:1", keys))
        callback?.invoke(false)
        assertFalse(controller.succeeded)
        assertEquals(0, successOutcomes)
        assertEquals(0, noticePosts)
        assertNull(controller.pendingKey)
        assertNull(controller.focusTarget(listOf("SERIES:2", "MOVIE:3")))
    }

    @Test
    fun successNotice_isSuppressedWhenTheHomeOwnerSessionIsGone() {
        var callback: ((Boolean) -> Unit)? = null
        var current = false
        var noticePosts = 0
        val controller = HomeHistoryRemovalController(
            removeHistory = { _, result -> callback = result },
            onSuccessfulRemoval = {},
            canPublishSuccess = { current },
            onSuccessNotice = { _ -> noticePosts += 1 },
        )

        assertTrue(controller.begin("MOVIE:1", keys))
        callback?.invoke(true)
        assertEquals(0, noticePosts)

        current = true
        controller.finish()
        assertTrue(controller.begin("MOVIE:1", keys))
        callback?.invoke(true)
        assertEquals(1, noticePosts)
    }

    @Test
    fun shellGestureGuard_consumesOnlyTheRemovingGestureAndThenReleases() {
        val guard = HomeRemovalInputGuard()
        assertFalse(guard.onSelectKey(KeyEventType.KeyDown))
        guard.begin()
        assertTrue(guard.onSelectKey(KeyEventType.KeyDown))
        assertTrue(guard.onSelectKey(KeyEventType.KeyUp))
        assertFalse(guard.active)
        assertFalse(guard.onSelectKey(KeyEventType.KeyDown))
    }

    @Test
    fun emptyRowHandoff_waitsForRowAndCardReadinessAndCompletesOnce() = runBlocking {
        val rowReady = CompletableDeferred<Unit>()
        val cardReady = CompletableDeferred<Unit>()
        var focusCalls = 0
        var homeEntryCalls = 0
        val target = HomeRowFocusTarget().apply {
            attach {
                cardReady.await()
                focusCalls += 1
                true
            }
        }
        val handoff = HomeEmptyRowFocusHandoff(
            ensureRowAvailable = {
                rowReady.await()
                true
            },
            focusRowFirstCard = { target.focusFirstCard() },
            focusHomeEntry = {
                homeEntryCalls += 1
                true
            },
        )

        val work = async(Dispatchers.Default) { handoff.run("recommended") }
        assertFalse(work.isCompleted)
        rowReady.complete(Unit)
        assertFalse(work.isCompleted)
        cardReady.complete(Unit)
        assertTrue(work.await())
        assertEquals(1, focusCalls)
        assertEquals(0, homeEntryCalls)
    }

    @Test
    fun emptyRowHandoff_cancellationNeverFocusesOrFallsThrough() = runBlocking {
        val rowReady = CompletableDeferred<Unit>()
        var focused = false
        val handoff = HomeEmptyRowFocusHandoff(
            ensureRowAvailable = {
                rowReady.await()
                true
            },
            focusRowFirstCard = {
                focused = true
                true
            },
            focusHomeEntry = { true },
        )

        val work = async(Dispatchers.Default) { handoff.run("recommended") }
        work.cancelAndJoin()
        rowReady.complete(Unit)
        assertFalse(focused)
    }

    @Test
    fun emptyRowHandoff_usesHomeEntryWhenNoContentRowRemains() = runBlocking {
        var homeEntryCalls = 0
        val handoff = HomeEmptyRowFocusHandoff(
            ensureRowAvailable = { false },
            focusRowFirstCard = { false },
            focusHomeEntry = {
                homeEntryCalls += 1
                true
            },
        )

        assertTrue(handoff.run(null))
        assertEquals(1, homeEntryCalls)
    }

    @Test
    fun rowFirstCardNeedsReveal_detectsHorizontallyScrolledRow() {
        assertTrue(homeRowFirstCardNeedsReveal(listOf(2, 3, 4)))
        assertFalse(homeRowFirstCardNeedsReveal(listOf(0, 1, 2)))
        assertTrue(homeRowFirstCardNeedsReveal(emptyList()))
    }

    @Test
    fun rowFirstCardHelper_revealsHorizontallyScrolledCardBeforeFocus() = runBlocking {
        var visible = false
        var revealed = false
        var requested = false
        var focused = false

        assertTrue(
            focusHomeRowFirstCard(
                firstCardVisible = { visible },
                revealFirstCard = {
                    revealed = true
                    visible = true
                },
                awaitFirstCard = {},
                requestFocus = {
                    requested = true
                    true
                },
                awaitFocused = { focused = true },
            ),
        )
        assertTrue(revealed)
        assertTrue(requested)
        assertTrue(focused)
    }

    @Test
    fun rowFirstCardHelper_doesNotCompleteWhenFocusRequestFails() = runBlocking {
        var focused = false
        assertFalse(
            focusHomeRowFirstCard(
                firstCardVisible = { true },
                revealFirstCard = { error("must not reveal") },
                awaitFirstCard = {},
                requestFocus = { false },
                awaitFocused = { focused = true },
            ),
        )
        assertFalse(focused)
    }

    @Test
    fun detachedRowTargetDoesNotClaimFocus() = runBlocking {
        val target = HomeRowFocusTarget()
        target.attach { true }
        assertTrue(target.focusFirstCard())
        target.detach()
        assertFalse(target.focusFirstCard())
    }

    @Test
    fun removalGesture_survivesCardDisposalAndFreshPressNeedsNoDelay() {
        var removalCalls = 0
        var callback: ((Boolean) -> Unit)? = null
        val controller = HomeHistoryRemovalController(
            removeHistory = { _, result ->
                removalCalls += 1
                callback = result
            },
            onSuccessfulRemoval = {},
        )
        val guard = HomeRemovalInputGuard()

        assertTrue(controller.begin("SERIES:2", keys))
        guard.begin()
        callback?.invoke(true)
        controller.finish() // the section may be disposed with its removed card

        // Held repeats and the release belong to removal, not to a surviving card/hero.
        assertTrue(guard.onSelectKey(KeyEventType.KeyDown))
        assertTrue(guard.onSelectKey(KeyEventType.KeyUp))
        assertEquals(1, removalCalls)

        // A fresh intentional press is not time-locked.
        assertFalse(guard.onSelectKey(KeyEventType.KeyDown))
        assertTrue(controller.begin("MOVIE:1", listOf("MOVIE:1", "MOVIE:3")))
        assertEquals(2, removalCalls)
    }
}
