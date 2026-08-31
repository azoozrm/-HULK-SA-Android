package sa.hulksa.player.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import sa.hulksa.player.data.LocalEpisodeNotification
import sa.hulksa.player.data.LocalNotificationItem
import sa.hulksa.player.ui.theme.HulkTheme

class TvNotificationCenterFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun threeCardsKeepExactFocusAndSerializeRepeatedOffscreenMove() {
        verifyFullTraversal(cardCount = 3, repeatAtCard = 3)
    }

    @Test
    fun fiveCardsKeepExactFocusThroughEveryBoundary() {
        verifyFullTraversal(cardCount = 5)
    }

    @Test
    fun tenCardsKeepExactFocusThroughEveryBoundary() {
        verifyFullTraversal(cardCount = 10)
    }

    @Test
    fun markReadAndDeleteMutationsUseDeterministicIdFallbacks() {
        val harness = setNotificationCenter(cardCount = 3)
        enterFirstCard(harness)

        val cardOneOpen = cardTarget(1, NotificationTvCardAction.OPEN)
        val cardOneMarkRead = cardTarget(1, NotificationTvCardAction.MARK_READ)
        val cardOneDelete = cardTarget(1, NotificationTvCardAction.DELETE)
        moveAndAssert(harness, cardOneOpen, cardOneMarkRead, Key.DirectionDown)
        val sameCardScroll = scrollPosition(harness)

        activateAndAssert(harness, cardOneMarkRead, cardOneOpen)
        assertEquals(sameCardScroll, scrollPosition(harness))

        moveAndAssert(harness, cardOneOpen, cardOneDelete, Key.DirectionDown)
        activateAndAssert(
            harness,
            cardOneDelete,
            cardTarget(2, NotificationTvCardAction.OPEN),
        )
    }

    @Test
    fun readAllDownHandsFocusToExactFirstCardOpenAction() {
        val harness = setNotificationCenter(cardCount = 3)
        moveAndAssert(
            harness,
            NotificationTvFocusTarget.Back,
            NotificationTvFocusTarget.ClearAll,
            Key.DirectionLeft,
        )
        moveAndAssert(
            harness,
            NotificationTvFocusTarget.ClearAll,
            NotificationTvFocusTarget.ReadAll,
            Key.DirectionLeft,
        )
        moveAndAssertStops(harness, NotificationTvFocusTarget.ReadAll, Key.DirectionUp)
        moveAndAssert(
            harness,
            NotificationTvFocusTarget.ReadAll,
            cardTarget(1, NotificationTvCardAction.OPEN),
            Key.DirectionDown,
        )
    }

    @Test
    fun clearAllDownHandsFocusToExactFirstCardOpenAction() {
        val harness = setNotificationCenter(cardCount = 3)
        moveAndAssert(
            harness,
            NotificationTvFocusTarget.Back,
            NotificationTvFocusTarget.ClearAll,
            Key.DirectionLeft,
        )
        moveAndAssertStops(harness, NotificationTvFocusTarget.ClearAll, Key.DirectionUp)
        moveAndAssert(
            harness,
            NotificationTvFocusTarget.ClearAll,
            cardTarget(1, NotificationTvCardAction.OPEN),
            Key.DirectionDown,
        )
    }

    @Test
    fun readAllDisabledUsesClearAllThenExactFirstCardOpenAction() {
        val harness = setNotificationCenter(
            cardCount = 3,
            initiallyRead = true,
        )
        moveAndAssert(
            harness,
            NotificationTvFocusTarget.Back,
            NotificationTvFocusTarget.ClearAll,
            Key.DirectionLeft,
        )
        moveAndAssertStops(harness, NotificationTvFocusTarget.ClearAll, Key.DirectionLeft)
        moveAndAssert(
            harness,
            NotificationTvFocusTarget.ClearAll,
            cardTarget(1, NotificationTvCardAction.OPEN),
            Key.DirectionDown,
        )
    }

    @Test
    fun headerHandoffComposesInitiallyUnplacedFirstCardBeforeRequestingFocus() {
        val harness = setNotificationCenter(
            cardCount = 10,
            initialFirstVisibleItemIndex = 9,
        )
        val cardOneOpen = cardTarget(1, NotificationTvCardAction.OPEN)
        composeRule.onNodeWithTag(notificationTvFocusTag(cardOneOpen)).assertDoesNotExist()

        moveAndAssert(
            harness,
            NotificationTvFocusTarget.Back,
            cardOneOpen,
            Key.DirectionDown,
        )
        composeRule.runOnIdle {
            assertEquals(0, harness.listState.firstVisibleItemIndex)
        }
    }

    @Test
    fun repeatedDownDuringOffscreenHeaderHandoffCannotLeaveFocusInHeader() {
        val harness = setNotificationCenter(
            cardCount = 10,
            initialFirstVisibleItemIndex = 9,
        )
        moveAndAssert(
            harness = harness,
            source = NotificationTvFocusTarget.Back,
            target = cardTarget(1, NotificationTvCardAction.OPEN),
            key = Key.DirectionDown,
            keyPressCount = 4,
        )
    }

    @Test
    fun oneCardCenterOwnsFullAvailableHeight() {
        verifyFullHeight(cardCount = 1)
    }

    @Test
    fun twoCardCenterOwnsFullAvailableHeight() {
        verifyFullHeight(cardCount = 2)
    }

    @Test
    fun fiveCardCenterOwnsFullAvailableHeight() {
        verifyFullHeight(cardCount = 5)
    }

    private fun verifyFullTraversal(cardCount: Int, repeatAtCard: Int? = null) {
        val harness = setNotificationCenter(cardCount)
        enterFirstCard(harness)

        val cardOneOpen = cardTarget(1, NotificationTvCardAction.OPEN)
        val cardOneMarkRead = cardTarget(1, NotificationTvCardAction.MARK_READ)
        val cardOneDelete = cardTarget(1, NotificationTvCardAction.DELETE)
        val cardOneScroll = scrollPosition(harness)
        moveAndAssert(harness, cardOneOpen, cardOneMarkRead, Key.DirectionDown)
        assertEquals(cardOneScroll, scrollPosition(harness))
        moveAndAssert(harness, cardOneMarkRead, cardOneDelete, Key.DirectionDown)
        assertEquals(cardOneScroll, scrollPosition(harness))

        var current = cardOneDelete
        for (cardIndex in 2..cardCount) {
            val open = cardTarget(cardIndex, NotificationTvCardAction.OPEN)
            moveAndAssert(
                harness = harness,
                source = current,
                target = open,
                key = Key.DirectionDown,
                keyPressCount = if (cardIndex == repeatAtCard) 4 else 1,
            )
            composeRule.onNodeWithTag(notificationTvFocusTag(open)).assertIsDisplayed()

            val markRead = cardTarget(cardIndex, NotificationTvCardAction.MARK_READ)
            val delete = cardTarget(cardIndex, NotificationTvCardAction.DELETE)
            moveAndAssert(harness, open, markRead, Key.DirectionDown)
            moveAndAssert(harness, markRead, delete, Key.DirectionDown)
            current = delete
        }

        for (cardIndex in cardCount downTo 1) {
            val delete = cardTarget(cardIndex, NotificationTvCardAction.DELETE)
            val markRead = cardTarget(cardIndex, NotificationTvCardAction.MARK_READ)
            val open = cardTarget(cardIndex, NotificationTvCardAction.OPEN)
            moveAndAssert(harness, delete, markRead, Key.DirectionUp)
            moveAndAssert(harness, markRead, open, Key.DirectionUp)
            if (cardIndex > 1) {
                val previousDelete = cardTarget(cardIndex - 1, NotificationTvCardAction.DELETE)
                moveAndAssert(harness, open, previousDelete, Key.DirectionUp)
            }
        }
    }

    private fun enterFirstCard(harness: Harness) {
        moveAndAssert(
            harness,
            NotificationTvFocusTarget.Back,
            cardTarget(1, NotificationTvCardAction.OPEN),
            Key.DirectionDown,
        )
    }

    private fun moveAndAssert(
        harness: Harness,
        source: NotificationTvFocusTarget,
        target: NotificationTvFocusTarget,
        key: Key,
        keyPressCount: Int = 1,
    ) {
        harness.transitions.clear()
        composeRule.onNodeWithTag(notificationTvFocusTag(source))
            .assertIsFocused()
            .performKeyInput {
                repeat(keyPressCount) { pressKey(key) }
            }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            harness.transitions.lastOrNull() == target
        }
        composeRule.onNodeWithTag(notificationTvFocusTag(target)).assertIsFocused()
        composeRule.runOnIdle {
            assertEquals(listOf(target), harness.transitions.toList())
        }
        assertHeadersAreNotFocusedUnless(target)
    }

    private fun moveAndAssertStops(
        harness: Harness,
        source: NotificationTvFocusTarget,
        key: Key,
    ) {
        harness.transitions.clear()
        composeRule.onNodeWithTag(notificationTvFocusTag(source))
            .assertIsFocused()
            .performKeyInput { pressKey(key) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(notificationTvFocusTag(source)).assertIsFocused()
        composeRule.runOnIdle {
            assertTrue(harness.transitions.isEmpty())
        }
    }

    private fun activateAndAssert(
        harness: Harness,
        source: NotificationTvFocusTarget,
        target: NotificationTvFocusTarget,
    ) {
        harness.transitions.clear()
        composeRule.onNodeWithTag(notificationTvFocusTag(source))
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            harness.transitions.lastOrNull() == target
        }
        composeRule.onNodeWithTag(notificationTvFocusTag(target)).assertIsFocused()
        composeRule.runOnIdle {
            assertEquals(listOf(target), harness.transitions.toList())
        }
    }

    private fun assertHeadersAreNotFocusedUnless(target: NotificationTvFocusTarget) {
        listOf(
            NotificationTvFocusTarget.Back,
            NotificationTvFocusTarget.ReadAll,
            NotificationTvFocusTarget.ClearAll,
        ).filterNot { it == target }.forEach { header ->
            composeRule.onNodeWithTag(notificationTvFocusTag(header)).assertIsNotFocused()
        }
    }

    private fun verifyFullHeight(cardCount: Int) {
        val harness = setNotificationCenter(cardCount = cardCount)
        composeRule.waitForIdle()

        val containerBounds = composeRule.onNodeWithTag(TEST_CONTAINER_TAG)
            .fetchSemanticsNode().boundsInRoot
        val rootBounds = composeRule.onNodeWithTag(NOTIFICATION_TV_CENTER_ROOT_TAG)
            .fetchSemanticsNode().boundsInRoot
        val listBounds = composeRule.onNodeWithTag(NOTIFICATION_TV_CENTER_LIST_TAG)
            .fetchSemanticsNode().boundsInRoot
        val firstCardBounds = composeRule.onNodeWithTag(notificationTvCardContainerTag("card-1"))
            .fetchSemanticsNode().boundsInRoot
        val contentBottomPadding = with(composeRule.density) {
            harness.metrics.topPaddingDp.dp.toPx()
        }
        val expectedCardHeight = with(composeRule.density) {
            maxOf(harness.metrics.posterHeightDp + 20, 152).dp.toPx()
        }

        assertEquals(containerBounds.top, rootBounds.top, 0.5f)
        assertEquals(containerBounds.bottom, rootBounds.bottom, 0.5f)
        assertEquals(rootBounds.bottom - contentBottomPadding, listBounds.bottom, 1f)
        assertTrue(listBounds.height > 0f)
        assertEquals(expectedCardHeight, firstCardBounds.height, 1f)
    }

    private fun setNotificationCenter(
        cardCount: Int,
        initialFirstVisibleItemIndex: Int = 0,
        initiallyRead: Boolean = false,
        containerHeight: Dp = 420.dp,
    ): Harness {
        val harness = Harness()
        composeRule.setContent {
            var notifications by remember(cardCount) {
                mutableStateOf(episodeNotifications(cardCount, initiallyRead))
            }
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = initialFirstVisibleItemIndex,
            )
            val metrics = localNotificationCenterMetrics(
                widthDp = 720,
                heightDp = containerHeight.value.toInt(),
                isTv = true,
            )
            harness.listState = listState
            harness.metrics = metrics
            HulkTheme {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(containerHeight)
                        .testTag(TEST_CONTAINER_TAG),
                ) {
                    TvLocalNotificationCenter(
                        notifications = notifications,
                        unreadCount = notifications.count { !it.read },
                        metrics = metrics,
                        onBack = {},
                        onOpen = {},
                        onMarkRead = { target ->
                            notifications = notifications.map { item ->
                                if (item.id == target.id && item is LocalNotificationItem.Episode) {
                                    item.copy(notification = item.notification.copy(read = true))
                                } else {
                                    item
                                }
                            }
                        },
                        onReadAll = {
                            notifications = notifications.map { item ->
                                if (item is LocalNotificationItem.Episode) {
                                    item.copy(notification = item.notification.copy(read = true))
                                } else {
                                    item
                                }
                            }
                        },
                        onDelete = { target ->
                            notifications = notifications.filterNot { it.id == target.id }
                        },
                        onClearAll = { notifications = emptyList() },
                        modifier = Modifier.fillMaxSize(),
                        listState = listState,
                        onFocusedTargetChanged = { harness.transitions += it },
                    )
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            harness.transitions.lastOrNull() == NotificationTvFocusTarget.Back
        }
        composeRule.onNodeWithTag(notificationTvFocusTag(NotificationTvFocusTarget.Back))
            .assertIsFocused()
        return harness
    }

    private fun scrollPosition(harness: Harness): Pair<Int, Int> = composeRule.runOnIdle {
        harness.listState.firstVisibleItemIndex to harness.listState.firstVisibleItemScrollOffset
    }

    private fun episodeNotifications(
        count: Int,
        read: Boolean = false,
    ): List<LocalNotificationItem> =
        (1..count).map { index ->
            LocalNotificationItem.Episode(
                LocalEpisodeNotification(
                    id = "card-$index",
                    accountId = "account",
                    profileId = "profile",
                    seriesId = index,
                    episodeStableKey = "episode-$index",
                    episodeId = index,
                    seasonNumber = 1,
                    episodeNumber = index,
                    seriesName = "Series $index",
                    posterUrl = null,
                    categoryId = "category",
                    createdAtEpochMs = 1_000L - index,
                    read = read,
                    popupShown = false,
                    batchId = "batch",
                ),
            )
        }

    private fun cardTarget(
        index: Int,
        action: NotificationTvCardAction,
    ): NotificationTvFocusTarget.CardAction = NotificationTvFocusTarget.CardAction(
        notificationId = "card-$index",
        action = action,
    )

    private class Harness {
        lateinit var listState: LazyListState
        lateinit var metrics: LocalNotificationCenterMetrics
        val transitions = CopyOnWriteArrayList<NotificationTvFocusTarget>()
    }

    private companion object {
        const val TEST_CONTAINER_TAG = "notification-tv-test-container"
    }
}
