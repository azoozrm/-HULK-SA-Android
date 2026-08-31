package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvNotificationCardScrollPolicyTest {
    @Test
    fun oneNotificationKeepsHeaderAndSingleCardFocusPathValid() {
        val graph = graph(1)

        assertEquals(
            cardTarget(1, NotificationTvCardAction.OPEN),
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
    fun unreadCardMovesThroughEveryActionWithoutSkipping() {
        val graph = graph(2)

        assertEquals(
            cardTarget(1, NotificationTvCardAction.MARK_READ),
            notificationTvFocusMove(
                graph,
                cardTarget(1, NotificationTvCardAction.OPEN),
                NotificationFocusDirection.DOWN,
            ),
        )
        assertEquals(
            cardTarget(1, NotificationTvCardAction.DELETE),
            notificationTvFocusMove(
                graph,
                cardTarget(1, NotificationTvCardAction.MARK_READ),
                NotificationFocusDirection.DOWN,
            ),
        )
        assertEquals(
            cardTarget(1, NotificationTvCardAction.MARK_READ),
            notificationTvFocusMove(
                graph,
                cardTarget(1, NotificationTvCardAction.DELETE),
                NotificationFocusDirection.UP,
            ),
        )
        assertEquals(
            cardTarget(1, NotificationTvCardAction.OPEN),
            notificationTvFocusMove(
                graph,
                cardTarget(1, NotificationTvCardAction.MARK_READ),
                NotificationFocusDirection.UP,
            ),
        )
    }

    @Test
    fun readCardSkipsHiddenMarkReadInBothDirections() {
        val graph = NotificationTvFocusGraph(
            cards = listOf(
                NotificationTvCardFocusSpec("card-1", markReadVisible = false),
                NotificationTvCardFocusSpec("card-2", markReadVisible = false),
            ),
            unreadCount = 0,
        )

        assertEquals(
            cardTarget(1, NotificationTvCardAction.DELETE),
            notificationTvFocusMove(
                graph,
                cardTarget(1, NotificationTvCardAction.OPEN),
                NotificationFocusDirection.DOWN,
            ),
        )
        assertEquals(
            cardTarget(1, NotificationTvCardAction.OPEN),
            notificationTvFocusMove(
                graph,
                cardTarget(1, NotificationTvCardAction.DELETE),
                NotificationFocusDirection.UP,
            ),
        )
        assertEquals(
            cardTarget(2, NotificationTvCardAction.OPEN),
            notificationTvFocusMove(
                graph,
                cardTarget(1, NotificationTvCardAction.DELETE),
                NotificationFocusDirection.DOWN,
            ),
        )
        assertEquals(
            cardTarget(1, NotificationTvCardAction.OPEN),
            notificationTvFocusMove(
                graph,
                NotificationTvFocusTarget.Back,
                NotificationFocusDirection.DOWN,
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
    fun sameCardMovementNeverQueriesListComposition() {
        var compositionQueries = 0

        val needsComposition = notificationTvTargetNeedsOffscreenComposition(
            current = cardTarget(3, NotificationTvCardAction.OPEN),
            target = cardTarget(3, NotificationTvCardAction.MARK_READ),
            isTargetCardComposed = {
                compositionQueries += 1
                false
            },
        )

        assertFalse(needsComposition)
        assertEquals(0, compositionQueries)
    }

    @Test
    fun partiallyOrFullyVisibleTargetUsesOnlyAttachedFocus() {
        assertFalse(
            notificationTvTargetNeedsOffscreenComposition(
                current = cardTarget(2, NotificationTvCardAction.DELETE),
                target = cardTarget(3, NotificationTvCardAction.OPEN),
                isTargetCardComposed = { true },
            ),
        )
    }

    @Test
    fun fullyOffscreenTargetUsesOneCompositionTransaction() {
        assertEquals(
            true,
            notificationTvTargetNeedsOffscreenComposition(
                current = cardTarget(2, NotificationTvCardAction.DELETE),
                target = cardTarget(3, NotificationTvCardAction.OPEN),
                isTargetCardComposed = { false },
            ),
        )
    }

    @Test
    fun headerMovementNeverQueriesListComposition() {
        var compositionQueries = 0

        val needsComposition = notificationTvTargetNeedsOffscreenComposition(
            current = NotificationTvFocusTarget.Back,
            target = NotificationTvFocusTarget.ClearAll,
            isTargetCardComposed = {
                compositionQueries += 1
                false
            },
        )

        assertFalse(needsComposition)
        assertEquals(0, compositionQueries)
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
    fun deletingLastCardFallsBackToPreviousCardLastActionById() {
        val previous = graph(3)
        val current = previous.copy(
            cards = previous.cards.filterNot { it.notificationId == "card-3" },
            unreadCount = 2,
        )

        assertEquals(
            cardTarget(2, NotificationTvCardAction.DELETE),
            notificationTvFocusFallback(
                current = cardTarget(3, NotificationTvCardAction.DELETE),
                previousGraph = previous,
                currentGraph = current,
            ),
        )
    }

    @Test
    fun deletingOnlyCardFallsBackToBack() {
        val previous = graph(1)
        val current = NotificationTvFocusGraph(emptyList(), unreadCount = 0)

        assertEquals(
            NotificationTvFocusTarget.Back,
            notificationTvFocusFallback(
                current = cardTarget(1, NotificationTvCardAction.DELETE),
                previousGraph = previous,
                currentGraph = current,
            ),
        )
    }

    @Test
    fun headerReadAllClearAllAndBackNavigationFollowsGeometry() {
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
        assertNull(
            notificationTvFocusMove(
                graph,
                NotificationTvFocusTarget.ReadAll,
                NotificationFocusDirection.UP,
            ),
        )
        assertNull(
            notificationTvFocusMove(
                graph,
                NotificationTvFocusTarget.ClearAll,
                NotificationFocusDirection.UP,
            ),
        )
    }

    @Test
    fun customGraphBlocksOnlyDirectionsWithoutOwnedTargets() {
        val graph = graph(3)

        val readAllBlocked = notificationTvBlockedDirections(
            graph = graph,
            current = NotificationTvFocusTarget.ReadAll,
        )
        assertFalse(NotificationFocusDirection.DOWN in readAllBlocked)
        assertFalse(NotificationFocusDirection.RIGHT in readAllBlocked)
        assertTrue(NotificationFocusDirection.LEFT in readAllBlocked)
        assertTrue(NotificationFocusDirection.UP in readAllBlocked)

        val firstOpenBlocked = notificationTvBlockedDirections(
            graph = graph,
            current = cardTarget(1, NotificationTvCardAction.OPEN),
        )
        assertFalse(NotificationFocusDirection.DOWN in firstOpenBlocked)
        assertFalse(NotificationFocusDirection.UP in firstOpenBlocked)
        assertTrue(NotificationFocusDirection.LEFT in firstOpenBlocked)
        assertTrue(NotificationFocusDirection.RIGHT in firstOpenBlocked)
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
