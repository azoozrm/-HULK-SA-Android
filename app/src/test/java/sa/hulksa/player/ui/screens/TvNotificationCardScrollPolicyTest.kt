package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TvNotificationCardScrollPolicyTest {
    @Test
    fun oneNotificationKeepsHeaderAndSingleCardFocusPathValid() {
        val graph = graph(1)

        assertEquals(
            NotificationTvFocusTarget.ReadAll,
            notificationTvFocusMove(
                graph,
                NotificationTvFocusTarget.Back,
                NotificationFocusDirection.DOWN,
            ),
        )
        assertEquals(
            cardTarget(1, NotificationTvCardAction.OPEN),
            notificationTvFocusMove(
                graph,
                NotificationTvFocusTarget.ReadAll,
                NotificationFocusDirection.DOWN,
            ),
        )
        assertNull(
            notificationTvFocusMove(
                graph,
                cardTarget(1, NotificationTvCardAction.DELETE),
                NotificationFocusDirection.DOWN,
            ),
        )
    }

    @Test
    fun twoNotificationsMoveAcrossCardBoundaryInBothDirections() {
        val graph = graph(2)

        assertEquals(
            cardTarget(2, NotificationTvCardAction.OPEN),
            notificationTvFocusMove(
                graph,
                cardTarget(1, NotificationTvCardAction.DELETE),
                NotificationFocusDirection.DOWN,
            ),
        )
        assertEquals(
            cardTarget(1, NotificationTvCardAction.DELETE),
            notificationTvFocusMove(
                graph,
                cardTarget(2, NotificationTvCardAction.OPEN),
                NotificationFocusDirection.UP,
            ),
        )
    }

    @Test
    fun threeFiveAndTenPlusNotificationsKeepEveryCardBoundaryStable() {
        listOf(3, 5, 10, 12).forEach { count ->
            val graph = graph(count)
            for (index in 1 until count) {
                assertEquals(
                    cardTarget(index + 1, NotificationTvCardAction.OPEN),
                    notificationTvFocusMove(
                        graph,
                        cardTarget(index, NotificationTvCardAction.DELETE),
                        NotificationFocusDirection.DOWN,
                    ),
                )
                assertEquals(
                    cardTarget(index, NotificationTvCardAction.DELETE),
                    notificationTvFocusMove(
                        graph,
                        cardTarget(index + 1, NotificationTvCardAction.OPEN),
                        NotificationFocusDirection.UP,
                    ),
                )
            }
        }
    }

    @Test
    fun partiallyVisibleNextCardUsesOnlyMinimalRevealDelta() {
        assertEquals(
            NotificationTvCardRevealPlan.MinimalScroll(deltaPx = 60),
            notificationTvCardRevealPlan(
                itemOffset = 240,
                itemSize = 120,
                viewportStartOffset = 0,
                viewportEndOffset = 300,
            ),
        )
    }

    @Test
    fun partiallyVisiblePreviousCardUsesOnlyMinimalRevealDelta() {
        assertEquals(
            NotificationTvCardRevealPlan.MinimalScroll(deltaPx = -36),
            notificationTvCardRevealPlan(
                itemOffset = -36,
                itemSize = 120,
                viewportStartOffset = 0,
                viewportEndOffset = 300,
            ),
        )
    }

    @Test
    fun fullyVisibleCardDoesNotRequestAnyListScroll() {
        assertEquals(
            NotificationTvCardRevealPlan.NoScroll,
            notificationTvCardRevealPlan(
                itemOffset = 40,
                itemSize = 120,
                viewportStartOffset = 0,
                viewportEndOffset = 300,
            ),
        )
    }

    @Test
    fun fullyOffscreenTargetUsesDeterministicLazyCompositionReveal() {
        assertEquals(
            NotificationTvCardRevealPlan.ComposeOffscreen,
            notificationTvCardRevealPlan(
                itemOffset = null,
                itemSize = null,
                viewportStartOffset = 0,
                viewportEndOffset = 300,
            ),
        )
    }

    @Test
    fun oversizedVisibleCardDoesNotThrashBetweenBothViewportEdges() {
        assertEquals(
            NotificationTvCardRevealPlan.NoScroll,
            notificationTvCardRevealPlan(
                itemOffset = -20,
                itemSize = 360,
                viewportStartOffset = 0,
                viewportEndOffset = 300,
            ),
        )
    }

    @Test
    fun focusRequestIsSingleShotWithoutArbitraryRetryLoop() {
        var attempts = 0

        val accepted = requestNotificationTvFocusOnce {
            attempts += 1
            false
        }

        assertFalse(accepted)
        assertEquals(1, attempts)
    }

    @Test
    fun disappearingMarkReadActionFallsBackInsideSameCard() {
        val previous = NotificationTvFocusGraph(
            cards = listOf(NotificationTvCardFocusSpec("card-1", markReadVisible = true)),
            unreadCount = 1,
        )
        val current = previous.copy(
            cards = listOf(NotificationTvCardFocusSpec("card-1", markReadVisible = false)),
            unreadCount = 0,
        )

        assertEquals(
            cardTarget(1, NotificationTvCardAction.OPEN),
            notificationTvFocusFallback(
                current = cardTarget(1, NotificationTvCardAction.MARK_READ),
                previousGraph = previous,
                currentGraph = current,
            ),
        )
    }

    @Test
    fun deletingFocusedMiddleCardFallsForwardToNextCardPrimaryAction() {
        val previous = graph(3)
        val current = previous.copy(
            cards = previous.cards.filterNot { it.notificationId == "card-2" },
            unreadCount = 2,
        )

        assertEquals(
            cardTarget(3, NotificationTvCardAction.OPEN),
            notificationTvFocusFallback(
                current = cardTarget(2, NotificationTvCardAction.DELETE),
                previousGraph = previous,
                currentGraph = current,
            ),
        )
    }

    @Test
    fun headerReadAllClearAllAndBackNavigationRemainsUnchanged() {
        val graph = graph(3)

        assertEquals(
            NotificationTvFocusTarget.ClearAll,
            notificationTvFocusMove(
                graph,
                NotificationTvFocusTarget.ReadAll,
                NotificationFocusDirection.RIGHT,
            ),
        )
        assertEquals(
            NotificationTvFocusTarget.ReadAll,
            notificationTvFocusMove(
                graph,
                NotificationTvFocusTarget.ClearAll,
                NotificationFocusDirection.LEFT,
            ),
        )
        assertEquals(
            NotificationTvFocusTarget.Back,
            notificationTvFocusMove(
                graph,
                NotificationTvFocusTarget.ReadAll,
                NotificationFocusDirection.UP,
            ),
        )
        assertEquals(
            NotificationTvFocusTarget.Back,
            notificationTvFocusMove(
                graph,
                NotificationTvFocusTarget.ClearAll,
                NotificationFocusDirection.UP,
            ),
        )
    }

    private fun graph(count: Int): NotificationTvFocusGraph = NotificationTvFocusGraph(
        cards = (1..count).map { index ->
            NotificationTvCardFocusSpec(
                notificationId = "card-$index",
                markReadVisible = true,
            )
        },
        unreadCount = count,
    )

    private fun cardTarget(
        index: Int,
        action: NotificationTvCardAction,
    ): NotificationTvFocusTarget.CardAction = NotificationTvFocusTarget.CardAction(
        notificationId = "card-$index",
        action = action,
    )
}
