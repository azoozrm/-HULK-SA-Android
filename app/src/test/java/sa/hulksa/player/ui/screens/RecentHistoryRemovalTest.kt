package sa.hulksa.player.ui.screens

import androidx.compose.ui.input.key.KeyEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentHistoryRemovalTest {
    private val keys = listOf("MOVIE:1", "SERIES:2", "MOVIE:3")

    @Test
    fun recentRemoval_armsGuardBeforeCapturedKeyAndAllowsOnlyOneMutation() {
        val events = mutableListOf<String>()
        var callback: ((Boolean) -> Unit)? = null
        var removalCalls = 0
        val controller = HomeHistoryRemovalController(
            removeHistory = { key, result ->
                removalCalls += 1
                events += "remove:$key"
                callback = result
            },
            onSuccessfulRemoval = {},
        )

        val accepted = beginRecentHistoryRemoval(
            remote = true,
            controller = controller,
            capturedKey = "SERIES:2",
            capturedKeys = keys,
            capturedTitle = "حبتين",
            onRemoteRemovalStarted = { events += "guard" },
        )
        assertEquals(listOf("guard", "remove:SERIES:2"), events)
        assertTrue(accepted)
        assertEquals("SERIES:2", controller.pendingKey)

        // Repeated KeyDown/neighbour callbacks during the same physical hold are rejected before
        // another mutation can be started.
        assertFalse(
            beginRecentHistoryRemoval(
                remote = true,
                controller = controller,
                capturedKey = "MOVIE:3",
                capturedKeys = keys,
                capturedTitle = "عنصر آخر",
                onRemoteRemovalStarted = { events += "guard-again" },
            ),
        )
        assertEquals(1, removalCalls)
        assertEquals(listOf("guard", "remove:SERIES:2"), events)

        callback?.invoke(true)
        callback?.invoke(true)
        assertTrue(controller.succeeded)
        assertEquals("MOVIE:3", controller.focusTarget(listOf("MOVIE:1", "MOVIE:3")))
        controller.finish()
    }

    @Test
    fun recentRemoval_capturesTitleBeforeMutationAndPublishesItOnceOnSuccess() {
        var callback: ((Boolean) -> Unit)? = null
        val notices = mutableListOf<String>()
        val controller = HomeHistoryRemovalController(
            removeHistory = { _, result -> callback = result },
            onSuccessfulRemoval = {},
            onSuccessNotice = { title -> notices += title },
        )

        assertTrue(
            beginRecentHistoryRemoval(
                remote = true,
                controller = controller,
                capturedKey = "SERIES:2",
                capturedKeys = keys,
                capturedTitle = "حبتين",
                onRemoteRemovalStarted = {},
            ),
        )
        assertEquals("حبتين", controller.capturedTitle)
        callback?.invoke(true)
        callback?.invoke(true)
        assertEquals(listOf("حبتين"), notices)
        controller.finish()
        assertEquals("", controller.capturedTitle)
    }

    @Test
    fun removalNoticeCopyNamesTheCapturedItemForBothDestinations() {
        assertEquals(
            "تمت إزالة «حبتين» من آخر مشاهدة",
            recentRemovalNotice("حبتين"),
        )
        assertEquals(
            "تمت إزالة «حبتين» من متابعة المشاهدة",
            homeRemovalNotice("حبتين"),
        )
    }

    @Test
    fun recentRemoval_failurePublishesNoNamedNotice() {
        var callback: ((Boolean) -> Unit)? = null
        val notices = mutableListOf<String>()
        val controller = HomeHistoryRemovalController(
            removeHistory = { _, result -> callback = result },
            onSuccessfulRemoval = {},
            onSuccessNotice = { title -> notices += title },
        )

        assertTrue(
            beginRecentHistoryRemoval(
                remote = false,
                controller = controller,
                capturedKey = "SERIES:2",
                capturedKeys = keys,
                capturedTitle = "حبتين",
                onRemoteRemovalStarted = {},
            ),
        )
        callback?.invoke(false)
        assertTrue(notices.isEmpty())
        assertNull(controller.pendingKey)
    }

    @Test
    fun recentRemoval_lastCardRequestsCategoryHandoffOnlyAfterAuthoritativeSuccess() {
        var callback: ((Boolean) -> Unit)? = null
        var successCount = 0
        var noticeCount = 0
        var wasLastCard = false
        val controller = HomeHistoryRemovalController(
            removeHistory = { _, result -> callback = result },
            onSuccessfulRemoval = { last ->
                successCount += 1
                wasLastCard = last
            },
            onSuccessNotice = { _ -> noticeCount += 1 },
        )

        assertTrue(controller.begin("SERIES:2", listOf("SERIES:2")))
        callback?.invoke(false)
        assertEquals(0, successCount)
        assertEquals(0, noticeCount)
        assertNull(controller.pendingKey)

        assertTrue(controller.begin("SERIES:2", listOf("SERIES:2")))
        callback?.invoke(true)
        assertEquals(1, successCount)
        assertEquals(1, noticeCount)
        assertTrue(wasLastCard)
        assertNull(controller.focusTarget(emptyList()))
        controller.finish()
    }

    @Test
    fun recentRemoval_focusLockSurvivesFinishAndReleasesOnlyOnARealDifferentFocus() {
        val lock = RecentRemovalFocusLock()
        lock.own("SERIES:2")
        assertTrue(lock.yieldsScrollObservation())
        lock.onFocused("SERIES:2")
        assertTrue(lock.yieldsScrollObservation())
        lock.onFocused("MOVIE:3")
        assertFalse(lock.yieldsScrollObservation())
        lock.own("MOVIE:3")
        lock.clear()
        assertFalse(lock.yieldsScrollObservation())
    }

    @Test
    fun recentRemoval_releaseEndsTheHoldAndAFreshPressStartsImmediately() {
        val guard = HomeRemovalInputGuard()
        assertFalse(guard.onSelectKey(KeyEventType.KeyDown))
        guard.begin()
        repeat(4) {
            assertTrue(guard.onSelectKey(KeyEventType.KeyDown))
        }
        assertTrue(guard.onSelectKey(KeyEventType.KeyUp))
        assertFalse(guard.active)
        assertFalse(guard.onSelectKey(KeyEventType.KeyDown))
    }

    @Test
    fun recentRemoval_focusUsesNextSurvivingStableKeyThenPrevious() {
        assertEquals(
            "MOVIE:3",
            removalFocusTarget(keys, listOf("MOVIE:1", "MOVIE:3"), "SERIES:2"),
        )
        assertEquals(
            "SERIES:2",
            removalFocusTarget(keys, listOf("MOVIE:1", "SERIES:2"), "MOVIE:3"),
        )
        assertNull(removalFocusTarget(keys, emptyList(), "SERIES:2"))
    }

    @Test
    fun recentRemoval_touchPathDoesNotArmTheRemoteSelectGuard() {
        var guardCalls = 0
        var callback: ((Boolean) -> Unit)? = null
        val controller = HomeHistoryRemovalController(
            removeHistory = { _, result -> callback = result },
            onSuccessfulRemoval = {},
        )

        assertTrue(
            beginRecentHistoryRemoval(
                remote = false,
                controller = controller,
                capturedKey = "MOVIE:1",
                capturedKeys = keys,
                capturedTitle = "فيلم",
                onRemoteRemovalStarted = { guardCalls += 1 },
            ),
        )
        assertEquals(0, guardCalls)
        callback?.invoke(false)
    }
}
