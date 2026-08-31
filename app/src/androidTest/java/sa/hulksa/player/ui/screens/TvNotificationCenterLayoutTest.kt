package sa.hulksa.player.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import sa.hulksa.player.data.LocalEpisodeNotification
import sa.hulksa.player.data.LocalNotificationItem
import sa.hulksa.player.ui.theme.HulkTheme

class TvNotificationCenterLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun oneCardBackgroundFillsEntireWindow() = verifyEdgeToEdgeBackground(cardCount = 1)

    @Test
    fun twoCardBackgroundFillsEntireWindow() = verifyEdgeToEdgeBackground(cardCount = 2)

    @Test
    fun fiveCardBackgroundFillsEntireWindow() = verifyEdgeToEdgeBackground(cardCount = 5)

    @Test
    fun maxContentWidthConstrainsContentOnlyAndCardsStayInset() {
        var configuredMaxWidthDp = 0
        composeRule.setContent {
            HulkTheme {
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .testTag(TEST_WINDOW_TAG),
                ) {
                    val availableWidthDp = maxWidth.value.roundToInt().coerceAtLeast(1)
                    val maxContentWidthDp = (availableWidthDp * .75f)
                        .roundToInt()
                        .coerceAtLeast(120)
                    configuredMaxWidthDp = maxContentWidthDp
                    val metrics = LocalNotificationCenterMetrics(
                        horizontalPaddingDp = 8,
                        topPaddingDp = 8,
                        maxContentWidthDp = maxContentWidthDp,
                        posterWidthDp = 36,
                        posterHeightDp = 54,
                        actionWidthDp = 52,
                    )
                    TvLocalNotificationCenter(
                        notifications = episodeNotifications(1),
                        unreadCount = 1,
                        metrics = metrics,
                        onBack = {},
                        onOpen = {},
                        onMarkRead = {},
                        onReadAll = {},
                        onDelete = {},
                        onClearAll = {},
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val windowBounds = bounds(TEST_WINDOW_TAG)
        val contentBounds = bounds(NOTIFICATION_TV_CENTER_ROOT_TAG)
        val cardBounds = bounds(notificationTvCardContainerTag("card-1"))
        val expectedMaxWidthPx = with(composeRule.density) {
            configuredMaxWidthDp.dp.toPx()
        }

        assertTrue(contentBounds.width <= expectedMaxWidthPx + 1f)
        assertTrue(contentBounds.width < windowBounds.width)
        assertTrue(cardBounds.left > contentBounds.left)
        assertTrue(cardBounds.right < contentBounds.right)
    }

    private fun verifyEdgeToEdgeBackground(cardCount: Int) {
        composeRule.setContent {
            HulkTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .testTag(TEST_WINDOW_TAG),
                ) {
                    val notifications = episodeNotifications(cardCount)
                    LocalNotificationCenterScreen(
                        notifications = notifications,
                        unreadCount = notifications.size,
                        isTv = true,
                        onBack = {},
                        onOpen = {},
                        onMarkRead = {},
                        onReadAll = {},
                        onDelete = {},
                        onClearAll = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val windowBounds = bounds(TEST_WINDOW_TAG)
        val screenRootBounds = bounds(NOTIFICATION_CENTER_SCREEN_ROOT_TAG)
        val backgroundBounds = bounds(NOTIFICATION_TV_BACKGROUND_TAG)
        val safeContentBounds = bounds(NOTIFICATION_TV_SAFE_CONTENT_TAG)
        val contentBounds = bounds(NOTIFICATION_TV_CENTER_ROOT_TAG)
        val headerBounds = bounds(notificationTvFocusTag(NotificationTvFocusTarget.Back))
        val firstCardBounds = bounds(notificationTvCardContainerTag("card-1"))

        assertSameBounds(windowBounds, screenRootBounds)
        assertSameBounds(windowBounds, backgroundBounds)
        assertContained(backgroundBounds, safeContentBounds)
        assertContained(safeContentBounds, contentBounds)
        assertContained(safeContentBounds, headerBounds)
        assertContained(safeContentBounds, firstCardBounds)
        assertTrue(firstCardBounds.left > backgroundBounds.left)
        assertTrue(firstCardBounds.right < backgroundBounds.right)
    }

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag)
        .fetchSemanticsNode()
        .boundsInRoot

    private fun assertSameBounds(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 0.5f)
        assertEquals(expected.top, actual.top, 0.5f)
        assertEquals(expected.right, actual.right, 0.5f)
        assertEquals(expected.bottom, actual.bottom, 0.5f)
    }

    private fun assertContained(outer: Rect, inner: Rect) {
        assertTrue(inner.left >= outer.left - 0.5f)
        assertTrue(inner.top >= outer.top - 0.5f)
        assertTrue(inner.right <= outer.right + 0.5f)
        assertTrue(inner.bottom <= outer.bottom + 0.5f)
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

    private companion object {
        const val TEST_WINDOW_TAG = "notification-tv-layout-test-window"
    }
}
