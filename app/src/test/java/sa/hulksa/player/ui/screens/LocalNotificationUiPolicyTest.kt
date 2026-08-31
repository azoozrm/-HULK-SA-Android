package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LocalNotificationUiPolicyTest {
    @Test
    fun tvNotificationCenterMetricsQualifyAt720p1080pAnd4kLogicalSizes() {
        val profiles = listOf(
            localNotificationCenterMetrics(960, 540, isTv = true),
            localNotificationCenterMetrics(1280, 720, isTv = true),
            localNotificationCenterMetrics(1920, 1080, isTv = true),
            localNotificationCenterMetrics(3840, 2160, isTv = true),
        )

        profiles.forEach { metrics ->
            assertTrue(metrics.horizontalPaddingDp in 32..96)
            assertTrue(metrics.topPaddingDp in 22..96)
            assertTrue(metrics.maxContentWidthDp in 840..3_200)
            assertTrue(metrics.posterWidthDp in 68..96)
            assertTrue(metrics.actionWidthDp in 148..178)
            assertTrue(abs(metrics.posterHeightDp - metrics.posterWidthDp * 1.5f) <= 2f)
        }
        assertTrue(profiles.zipWithNext().all { (first, second) ->
            first.maxContentWidthDp <= second.maxContentWidthDp
        })
    }

    @Test
    fun notificationHeaderNavigationEntersBulkActionsAndStopsAtOuterEdges() {
        val graph = focusGraph()

        assertEquals(
            NotificationTvFocusTarget.ReadAll,
            notificationTvFocusMove(
                graph,
                NotificationTvFocusTarget.Back,
                NotificationFocusDirection.DOWN,
            ),
        )
        assertNull(notificationTvFocusMove(graph, NotificationTvFocusTarget.Back, NotificationFocusDirection.UP))
        assertNull(notificationTvFocusMove(graph, NotificationTvFocusTarget.Back, NotificationFocusDirection.LEFT))
        assertNull(notificationTvFocusMove(graph, NotificationTvFocusTarget.Back, NotificationFocusDirection.RIGHT))
    }

    @Test
    fun notificationBulkActionsFollowRtlGeometryAndReachFirstCard() {
        val graph = focusGraph()
        val firstOpen = cardTarget("first", NotificationTvCardAction.OPEN)

        assertEquals(
            NotificationTvFocusTarget.Back,
            notificationTvFocusMove(graph, NotificationTvFocusTarget.ReadAll, NotificationFocusDirection.UP),
        )
        assertEquals(
            NotificationTvFocusTarget.ClearAll,
            notificationTvFocusMove(graph, NotificationTvFocusTarget.ReadAll, NotificationFocusDirection.RIGHT),
        )
        assertNull(
            notificationTvFocusMove(graph, NotificationTvFocusTarget.ReadAll, NotificationFocusDirection.LEFT),
        )
        assertEquals(
            firstOpen,
            notificationTvFocusMove(graph, NotificationTvFocusTarget.ReadAll, NotificationFocusDirection.DOWN),
        )

        assertEquals(
            NotificationTvFocusTarget.ReadAll,
            notificationTvFocusMove(graph, NotificationTvFocusTarget.ClearAll, NotificationFocusDirection.LEFT),
        )
        assertEquals(
            NotificationTvFocusTarget.Back,
            notificationTvFocusMove(graph, NotificationTvFocusTarget.ClearAll, NotificationFocusDirection.RIGHT),
        )
        assertEquals(
            NotificationTvFocusTarget.Back,
            notificationTvFocusMove(graph, NotificationTvFocusTarget.ClearAll, NotificationFocusDirection.UP),
        )
        assertEquals(
            firstOpen,
            notificationTvFocusMove(graph, NotificationTvFocusTarget.ClearAll, NotificationFocusDirection.DOWN),
        )
    }

    @Test
    fun unreadNotificationExposesAllActionsInStableVerticalOrder() {
        val graph = focusGraph()
        val open = cardTarget("first", NotificationTvCardAction.OPEN)
        val markRead = cardTarget("first", NotificationTvCardAction.MARK_READ)
        val delete = cardTarget("first", NotificationTvCardAction.DELETE)

        assertEquals(
            NotificationTvFocusTarget.ReadAll,
            notificationTvFocusMove(graph, open, NotificationFocusDirection.UP),
        )
        assertEquals(markRead, notificationTvFocusMove(graph, open, NotificationFocusDirection.DOWN))
        assertEquals(open, notificationTvFocusMove(graph, markRead, NotificationFocusDirection.UP))
        assertEquals(delete, notificationTvFocusMove(graph, markRead, NotificationFocusDirection.DOWN))
        assertEquals(markRead, notificationTvFocusMove(graph, delete, NotificationFocusDirection.UP))
    }

    @Test
    fun readNotificationSkipsUnavailableMarkReadActionWithoutBreakingGraph() {
        val graph = focusGraph()
        val open = cardTarget("middle", NotificationTvCardAction.OPEN)
        val delete = cardTarget("middle", NotificationTvCardAction.DELETE)
        val unavailable = cardTarget("middle", NotificationTvCardAction.MARK_READ)

        assertTrue(unavailable !in notificationTvFocusableTargets(graph))
        assertEquals(
            cardTarget("first", NotificationTvCardAction.DELETE),
            notificationTvFocusMove(graph, open, NotificationFocusDirection.UP),
        )
        assertEquals(delete, notificationTvFocusMove(graph, open, NotificationFocusDirection.DOWN))
        assertEquals(open, notificationTvFocusMove(graph, delete, NotificationFocusDirection.UP))
    }

    @Test
    fun firstMiddleAndLastCardsFormOneDeterministicVerticalPath() {
        val graph = focusGraph()

        assertEquals(
            cardTarget("middle", NotificationTvCardAction.OPEN),
            notificationTvFocusMove(
                graph,
                cardTarget("first", NotificationTvCardAction.DELETE),
                NotificationFocusDirection.DOWN,
            ),
        )
        assertEquals(
            cardTarget("last", NotificationTvCardAction.OPEN),
            notificationTvFocusMove(
                graph,
                cardTarget("middle", NotificationTvCardAction.DELETE),
                NotificationFocusDirection.DOWN,
            ),
        )
        assertEquals(
            cardTarget("middle", NotificationTvCardAction.DELETE),
            notificationTvFocusMove(
                graph,
                cardTarget("last", NotificationTvCardAction.OPEN),
                NotificationFocusDirection.UP,
            ),
        )
        assertNull(
            notificationTvFocusMove(
                graph,
                cardTarget("last", NotificationTvCardAction.DELETE),
                NotificationFocusDirection.DOWN,
            ),
        )
    }

    @Test
    fun fullDownAndUpTraversalVisitsEveryActionExactlyInGraphOrder() {
        val graph = focusGraph()
        val expectedDownPath = listOf(
            NotificationTvFocusTarget.Back,
            NotificationTvFocusTarget.ReadAll,
            cardTarget("first", NotificationTvCardAction.OPEN),
            cardTarget("first", NotificationTvCardAction.MARK_READ),
            cardTarget("first", NotificationTvCardAction.DELETE),
            cardTarget("middle", NotificationTvCardAction.OPEN),
            cardTarget("middle", NotificationTvCardAction.DELETE),
            cardTarget("last", NotificationTvCardAction.OPEN),
            cardTarget("last", NotificationTvCardAction.MARK_READ),
            cardTarget("last", NotificationTvCardAction.DELETE),
        )

        val actualDownPath = mutableListOf<NotificationTvFocusTarget>()
        var current: NotificationTvFocusTarget? = NotificationTvFocusTarget.Back
        repeat(expectedDownPath.size) {
            val node = checkNotNull(current)
            actualDownPath += node
            current = notificationTvFocusMove(graph, node, NotificationFocusDirection.DOWN)
        }
        assertNull(current)
        assertEquals(expectedDownPath, actualDownPath)

        val actualUpPath = mutableListOf<NotificationTvFocusTarget>()
        current = expectedDownPath.last()
        repeat(expectedDownPath.size) {
            val node = checkNotNull(current)
            actualUpPath += node
            current = notificationTvFocusMove(graph, node, NotificationFocusDirection.UP)
        }
        assertNull(current)
        assertEquals(expectedDownPath.reversed(), actualUpPath)
    }

    @Test
    fun disabledReadAllIsRemovedFromFocusGraph() {
        val graph = focusGraph(unreadCount = 0, markReadVisible = false)

        assertTrue(NotificationTvFocusTarget.ReadAll !in notificationTvFocusableTargets(graph))
        assertEquals(
            NotificationTvFocusTarget.ClearAll,
            notificationTvFocusMove(
                graph,
                NotificationTvFocusTarget.Back,
                NotificationFocusDirection.DOWN,
            ),
        )
        assertNull(
            notificationTvFocusMove(
                graph,
                NotificationTvFocusTarget.ClearAll,
                NotificationFocusDirection.LEFT,
            ),
        )
    }

    @Test
    fun deletingMiddleNotificationMovesFocusToNextCardPrimaryAction() {
        val previous = focusGraph()
        val current = previous.copy(cards = previous.cards.filterNot { it.notificationId == "middle" })

        assertEquals(
            cardTarget("last", NotificationTvCardAction.OPEN),
            notificationTvFocusFallback(
                current = cardTarget("middle", NotificationTvCardAction.DELETE),
                previousGraph = previous,
                currentGraph = current,
            ),
        )
    }

    @Test
    fun deletingLastNotificationMovesFocusToPreviousCardLastAction() {
        val previous = focusGraph()
        val current = previous.copy(cards = previous.cards.filterNot { it.notificationId == "last" })

        assertEquals(
            cardTarget("middle", NotificationTvCardAction.DELETE),
            notificationTvFocusFallback(
                current = cardTarget("last", NotificationTvCardAction.DELETE),
                previousGraph = previous,
                currentGraph = current,
            ),
        )
    }

    @Test
    fun deletingOnlyRemainingNotificationMovesFocusSafelyToBack() {
        val previous = NotificationTvFocusGraph(
            cards = listOf(NotificationTvCardFocusSpec("only", markReadVisible = true)),
            unreadCount = 1,
        )
        val empty = NotificationTvFocusGraph(emptyList(), unreadCount = 0)

        assertEquals(
            NotificationTvFocusTarget.Back,
            notificationTvFocusFallback(
                current = cardTarget("only", NotificationTvCardAction.DELETE),
                previousGraph = previous,
                currentGraph = empty,
            ),
        )
    }

    @Test
    fun markingNotificationReadKeepsFocusInsideSameCard() {
        val previous = focusGraph()
        val current = previous.copy(
            cards = previous.cards.map { card ->
                if (card.notificationId == "first") card.copy(markReadVisible = false) else card
            },
            unreadCount = 1,
        )

        assertEquals(
            cardTarget("first", NotificationTvCardAction.OPEN),
            notificationTvFocusFallback(
                current = cardTarget("first", NotificationTvCardAction.MARK_READ),
                previousGraph = previous,
                currentGraph = current,
            ),
        )
    }

    @Test
    fun readAllAndClearAllRecoverFocusToExistingHeaderTargets() {
        val previous = focusGraph()
        val allRead = previous.copy(
            cards = previous.cards.map { it.copy(markReadVisible = false) },
            unreadCount = 0,
        )
        assertEquals(
            NotificationTvFocusTarget.ClearAll,
            notificationTvFocusFallback(
                current = NotificationTvFocusTarget.ReadAll,
                previousGraph = previous,
                currentGraph = allRead,
            ),
        )

        val empty = NotificationTvFocusGraph(emptyList(), unreadCount = 0)
        assertEquals(
            NotificationTvFocusTarget.Back,
            notificationTvFocusFallback(
                current = NotificationTvFocusTarget.ClearAll,
                previousGraph = allRead,
                currentGraph = empty,
            ),
        )
        NotificationFocusDirection.entries.forEach { direction ->
            assertNull(notificationTvFocusMove(empty, NotificationTvFocusTarget.Back, direction))
        }
    }

    @Test
    fun everyHorizontalCardEdgeAndEveryOuterVerticalEdgeStops() {
        val graph = focusGraph()
        val cardTargets = notificationTvFocusableTargets(graph)
            .filterIsInstance<NotificationTvFocusTarget.CardAction>()

        cardTargets.forEach { target ->
            assertNull(notificationTvFocusMove(graph, target, NotificationFocusDirection.LEFT))
            assertNull(notificationTvFocusMove(graph, target, NotificationFocusDirection.RIGHT))
        }
        assertNull(
            notificationTvFocusMove(
                graph,
                NotificationTvFocusTarget.Back,
                NotificationFocusDirection.UP,
            ),
        )
        assertNull(
            notificationTvFocusMove(
                graph,
                cardTarget("last", NotificationTvCardAction.DELETE),
                NotificationFocusDirection.DOWN,
            ),
        )
    }

    @Test
    fun everyFocusMoveEitherStopsOrTargetsAnExistingNode() {
        listOf(
            focusGraph(),
            focusGraph(unreadCount = 0, markReadVisible = false),
            NotificationTvFocusGraph(emptyList(), unreadCount = 0),
        ).forEach { graph ->
            val nodes = notificationTvFocusableTargets(graph)
            nodes.forEach { node ->
                NotificationFocusDirection.entries.forEach { direction ->
                    val target = notificationTvFocusMove(graph, node, direction)
                    assertTrue(target == null || target in nodes)
                }
            }
        }
    }

    @Test
    fun seriesDetailsActionsPreservePreviousWithAdaptiveBalancedRows() {
        assertEquals(1, detailsProMobileActionColumns(320))
        assertEquals(1, detailsProMobileActionColumns(359))
        assertEquals(2, detailsProMobileActionColumns(360))
        assertEquals(2, detailsProMobileActionColumns(840))
        assertEquals(5, seriesDetailsActionCount())
        assertEquals(2, seriesDetailsActionColumns(isTv = false))
        assertEquals(3, seriesDetailsActionColumns(isTv = true))
        assertTrue(seriesDetailsPrimarySpansFullWidth(isTv = false))
        assertTrue(!seriesDetailsPrimarySpansFullWidth(isTv = true))
        assertEquals(50, seriesDetailsActionHeightDp())
        assertTrue(!detailsProActionsUseSingleRow(isTv = false, wide = true, actionCount = 3))
        assertTrue(detailsProActionsUseSingleRow(isTv = true, wide = true, actionCount = 5))
    }

    @Test
    fun homeBellMatchesRefreshVisualSizeWithSafeTouchTarget() {
        assertEquals(44, homeHeaderActionVisualSizeDp())
        assertEquals(48, homeHeaderActionTouchSizeDp())
        assertEquals(homeHeaderActionVisualSizeDp(), notificationBellSizeDp(isTv = false))
        assertEquals(homeHeaderActionVisualSizeDp(), notificationBellSizeDp(isTv = true))
    }

    @Test
    fun unreadBadgeSupportsThreeDigitCountsWithoutDistortion() {
        assertNull(notificationBadgeMetrics(0))
        assertEquals("1", notificationBadgeMetrics(1)?.label)
        assertEquals(18, notificationBadgeMetrics(1)?.widthDp)
        assertEquals("99", notificationBadgeMetrics(99)?.label)
        assertEquals(20, notificationBadgeMetrics(99)?.widthDp)
        assertEquals("100", notificationBadgeMetrics(100)?.label)
        assertEquals(24, notificationBadgeMetrics(100)?.widthDp)
        assertEquals(18, notificationBadgeMetrics(100)?.heightDp)
        assertEquals("999", notificationBadgeMetrics(999)?.label)
        assertEquals(24, notificationBadgeMetrics(999)?.widthDp)
        assertEquals("999+", notificationBadgeMetrics(1_000)?.label)
        assertEquals(30, notificationBadgeMetrics(1_000)?.widthDp)
    }

    @Test
    fun mobileNotificationButtonUsesCompleteCompactLabelsWithoutChangingTvCopy() {
        assertEquals("تنبيهات الحلقات", seriesNotificationButtonLabel(enabled = false, isTv = false))
        assertEquals("التنبيهات مفعلة", seriesNotificationButtonLabel(enabled = true, isTv = false))
        assertEquals("نبهني عند نزول حلقة جديدة", seriesNotificationButtonLabel(enabled = false, isTv = true))
        assertEquals(12, seriesNotificationButtonTextSizeSp(isTv = false))
        assertNull(seriesNotificationButtonTextSizeSp(isTv = true))
        assertEquals(50, seriesDetailsActionHeightDp())
        assertEquals(2, seriesDetailsActionColumns(isTv = false))
    }

    @Test
    fun phoneTabletAndFoldableCenterMetricsStayResponsive() {
        val phone = localNotificationCenterMetrics(360, 800, isTv = false)
        val landscape = localNotificationCenterMetrics(800, 360, isTv = false)
        val tablet = localNotificationCenterMetrics(840, 1180, isTv = false)
        val foldable = localNotificationCenterMetrics(673, 841, isTv = false)

        assertEquals(14, phone.horizontalPaddingDp)
        assertEquals(24, landscape.horizontalPaddingDp)
        assertEquals(24, tablet.horizontalPaddingDp)
        assertEquals(24, foldable.horizontalPaddingDp)
        assertTrue(phone.posterWidthDp < tablet.posterWidthDp)
        assertTrue(landscape.maxContentWidthDp <= 900)
    }

    @Test
    fun relativeTimeNeverShowsNegativeValues() {
        assertEquals("الآن", localNotificationRelativeTime(createdAtEpochMs = 2_000L, nowEpochMs = 1_000L))
        assertEquals("منذ 2 ساعة", localNotificationRelativeTime(0L, 2L * 60L * 60L * 1_000L))
    }

    private fun focusGraph(
        unreadCount: Int = 2,
        markReadVisible: Boolean = true,
    ): NotificationTvFocusGraph = NotificationTvFocusGraph(
        cards = listOf(
            NotificationTvCardFocusSpec("first", markReadVisible = markReadVisible),
            NotificationTvCardFocusSpec("middle", markReadVisible = false),
            NotificationTvCardFocusSpec("last", markReadVisible = markReadVisible),
        ),
        unreadCount = unreadCount,
    )

    private fun cardTarget(
        notificationId: String,
        action: NotificationTvCardAction,
    ): NotificationTvFocusTarget.CardAction = NotificationTvFocusTarget.CardAction(
        notificationId = notificationId,
        action = action,
    )
}
