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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
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
            NotificationTvFocusTarget.ReadAll,
            Key.DirectionDown,
        )
        moveAndAssert(
            harness,
            NotificationTvFocusTarget.ReadAll,
            NotificationTvFocusTarget.ClearAll,
            Key.DirectionRight,
        )
        moveAndAssert(
            harness,
            NotificationTvFocusTarget.ClearAll,
            NotificationTvFocusTarget.ReadAll,
            Key.DirectionLeft,
        )
        moveAndAssert(
            harness,
            NotificationTvFocusTarget.ReadAll,
            NotificationTvFocusTarget.Back,
            Key.DirectionUp,
        )
        moveAndAssert(
            harness,
            NotificationTvFocusTarget.Back,
            NotificationTvFocusTarget.ReadAll,
            Key.DirectionDown,
        )
        moveAndAssert(
            harness,
            NotificationTvFocusTarget.ReadAll,
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

    private fun setNotificationCenter(cardCount: Int): Harness {
        val harness = Harness()
        composeRule.setContent {
            var notifications by remember(cardCount) {
                mutableStateOf(episodeNotifications(cardCount))
            }
            val listState = rememberLazyListState()
            harness.listState = listState
            HulkTheme {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                ) {
                    TvLocalNotificationCenter(
                        notifications = notifications,
                        unreadCount = notifications.count { !it.read },
                        metrics = localNotificationCenterMetrics(
                            widthDp = 720,
                            heightDp = 420,
                            isTv = true,
                        ),
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

    private fun episodeNotifications(count: Int): List<LocalNotificationItem> =
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
                    read = false,
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
        val transitions = CopyOnWriteArrayList<NotificationTvFocusTarget>()
    }
}
