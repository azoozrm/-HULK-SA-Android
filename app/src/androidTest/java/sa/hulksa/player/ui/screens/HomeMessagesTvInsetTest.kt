package sa.hulksa.player.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import sa.hulksa.player.data.OperationsAnnouncement
import sa.hulksa.player.data.OperationsAnnouncementSeverity
import sa.hulksa.player.data.OperationsAnnouncementTarget
import sa.hulksa.player.data.OperationsDownloadUiState
import sa.hulksa.player.data.OperationsUpdateConfig
import sa.hulksa.player.data.RenewalBannerContent
import sa.hulksa.player.ui.adaptive.AdaptiveUiState
import sa.hulksa.player.ui.adaptive.HulkDeviceClass
import sa.hulksa.player.ui.adaptive.HulkInputMode
import sa.hulksa.player.ui.adaptive.HulkNavigationType
import sa.hulksa.player.ui.adaptive.HulkWindowWidthClass
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.adaptive.tvPremiumWindowPolicy
import sa.hulksa.player.ui.theme.HulkTheme

/**
 * Real Compose geometry for the Home message row on an isolated disposable Debug target only.
 * No screenshots, credentials, persistence, network or persistent-package data are touched.
 *
 * The production chain under test is exactly:
 * HomeSectionPadding(TV 8dp per side) -> HomeMessagesZone -> RTL Row -> weighted HomeMessageCard.
 * `absolutePadding(left=...)` is physical-left and must not move the existing right edge.
 */
class HomeMessagesTvInsetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private data class Viewport(val width: Int, val height: Int, val label: String)

    private val viewport = mutableStateOf(Viewport(960, 540, "compact"))
    private val renewal = RenewalBannerContent(
        daysRemaining = 1,
        title = "اشتراكك ينتهي غدا",
        subtitle = "جدد اشتراكك الان",
    )
    private val announcement = OperationsAnnouncement(
        id = "synthetic-home-message",
        enabled = true,
        title = "إعلان تجريبي",
        message = "رسالة إعلان تجريبية",
        severity = OperationsAnnouncementSeverity.INFO,
        target = OperationsAnnouncementTarget.TV,
        showOnce = false,
        persistent = false,
        minimumVersionCode = null,
        maximumVersionCode = null,
        startsAtEpochSeconds = 0L,
        endsAtEpochSeconds = null,
    )
    private val update = OperationsUpdateConfig(
        latestVersionCode = 70,
        latestVersionName = "1.0.2",
    )
    private val updateTitle = "يتوفر تحديث جديد"

    @Test
    fun renewalAloneFitsPhysicalLeftOnCompactTv() {
        viewport.value = Viewport(960, 540, "compact")
        setScreen(renewal = renewal)
        assertCardBounds("renewal alone compact", "اشتراكك ينتهي غدا")
    }

    @Test
    fun renewalAloneFitsPhysicalLeftOnStandardTv() {
        viewport.value = Viewport(1280, 720, "standard")
        setScreen(renewal = renewal)
        assertCardBounds("renewal alone standard", "اشتراكك ينتهي غدا")
    }

    @Test
    fun renewalAloneFitsPhysicalLeftOnWideTv() {
        viewport.value = Viewport(1920, 1080, "wide")
        setScreen(renewal = renewal)
        assertCardBounds("renewal alone wide", "اشتراكك ينتهي غدا")
    }

    @Test
    fun multipleMessagesKeepLeftInsetRightEdgeAndRtlOrderOnCompactTv() {
        viewport.value = Viewport(960, 540, "compact")
        setScreen(renewal = renewal, announcement = announcement, optionalUpdate = update)
        assertMultipleMessageBounds("multiple compact")
    }

    @Test
    fun multipleMessagesKeepLeftInsetRightEdgeAndRtlOrderOnStandardTv() {
        viewport.value = Viewport(1280, 720, "standard")
        setScreen(renewal = renewal, announcement = announcement, optionalUpdate = update)
        assertMultipleMessageBounds("multiple standard")
    }

    @Test
    fun phoneStackKeepsFullWidthWithoutTvInset() {
        viewport.value = Viewport(360, 800, "phone")
        setScreen(renewal = renewal, announcement = announcement, isTv = false)
        composeRule.waitForIdle()
        val renewalBounds = bounds("اشتراكك ينتهي غدا")
        val announcementBounds = bounds("إعلان تجريبي")
        assertEquals("phone renewal left", 0f, renewalBounds.left.value, .51f)
        assertEquals("phone renewal right", 360f, renewalBounds.right.value, .51f)
        assertEquals("phone announcement left", 0f, announcementBounds.left.value, .51f)
        assertTrue(
            "phone stack order changed: $renewalBounds then $announcementBounds",
            announcementBounds.top > renewalBounds.bottom,
        )
        Log.i(LOG_TAG, "phone/360x800 renewal=$renewalBounds announcement=$announcementBounds")
    }

    private fun assertCardBounds(label: String, title: String) {
        composeRule.waitForIdle()
        val size = viewport.value
        val expectedLeft = expectedLeft(size)
        val expectedRight = expectedRight(size)
        val normal = bounds(title)
        assertBounds("$label normal", normal, expectedLeft, expectedRight)
        card(title).requestFocus()
        card(title).assertIsFocused()
        val focused = bounds(title)
        assertBounds("$label focused", focused, expectedLeft, expectedRight)
        assertEquals("$label focus moved the card", normal, focused)
        Log.i(LOG_TAG, "$label viewport=${size.width}x${size.height} inset=${inset(size)} " +
            "expectedLeft=$expectedLeft expectedRight=$expectedRight bounds=$normal focused=$focused")
    }

    private fun assertMultipleMessageBounds(label: String) {
        composeRule.waitForIdle()
        val size = viewport.value
        val expectedLeft = expectedLeft(size)
        val expectedRight = expectedRight(size)
        val titles = listOf(renewal.title, announcement.title, updateTitle)
        val measured = titles.associateWith { bounds(it) }
        measured.forEach { (title, cardBounds) ->
            assertTrue(
                "$label $title left=${cardBounds.left.value} escaped expectedLeft=$expectedLeft",
                cardBounds.left.value >= expectedLeft - .51f,
            )
            assertTrue(
                "$label $title right=${cardBounds.right.value} escaped expectedRight=$expectedRight",
                cardBounds.right.value <= expectedRight + .51f,
            )
        }
        val leftmost = measured.minByOrNull { it.value.left.value }!!
        val rightmost = measured.maxByOrNull { it.value.right.value }!!
        assertEquals("$label leftmost=${leftmost.key}", expectedLeft, leftmost.value.left.value, .51f)
        assertEquals("$label rightmost=${rightmost.key}", expectedRight, rightmost.value.right.value, .51f)
        // RTL row order is preserved: renewal is physically right, update is physically left.
        assertEquals("$label renewal right edge", expectedRight, measured.getValue(renewal.title).right.value, .51f)
        assertEquals("$label update left edge", expectedLeft, measured.getValue(updateTitle).left.value, .51f)
        assertTrue(
            "$label announcement is not between renewal and update: $measured",
            measured.getValue(announcement.title).left.value >= expectedLeft - .51f &&
                measured.getValue(announcement.title).right.value <= expectedRight + .51f,
        )
        val focusTitle = leftmost.key
        card(focusTitle).requestFocus()
        card(focusTitle).assertIsFocused()
        measured.forEach { (title, cardBounds) ->
            val focusedBounds = bounds(title)
            assertTrue(
                "$label $title focused left=${focusedBounds.left.value} escaped expectedLeft=$expectedLeft",
                focusedBounds.left.value >= expectedLeft - .51f,
            )
            assertTrue(
                "$label $title focused right=${focusedBounds.right.value} escaped expectedRight=$expectedRight",
                focusedBounds.right.value <= expectedRight + .51f,
            )
            assertEquals("$label $title moved by focus", cardBounds, focusedBounds)
        }
        Log.i(LOG_TAG, "$label viewport=${size.width}x${size.height} inset=${inset(size)} " +
            "expectedLeft=$expectedLeft expectedRight=$expectedRight bounds=$measured focused=$focusTitle")
    }

    private fun setScreen(
        renewal: RenewalBannerContent? = null,
        announcement: OperationsAnnouncement? = null,
        optionalUpdate: OperationsUpdateConfig? = null,
        isTv: Boolean = true,
    ) {
        composeRule.setContent {
            val size = viewport.value
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(size.width.dp, size.height.dp)),
            ) {
                CompositionLocalProvider(
                    LocalAdaptiveUi provides AdaptiveUiState(
                        deviceClass = if (isTv) HulkDeviceClass.TELEVISION else HulkDeviceClass.MOBILE,
                        windowWidthClass = HulkWindowWidthClass.EXPANDED,
                        navigationType = if (isTv) HulkNavigationType.RAIL else HulkNavigationType.TOP_BAR,
                        inputMode = if (isTv) HulkInputMode.REMOTE else HulkInputMode.TOUCH,
                        screenWidthDp = size.width,
                        screenHeightDp = size.height,
                    ),
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                ) {
                    HulkTheme {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .testTag("home-messages-viewport"),
                        ) {
                            HomeSectionPadding(isTv = isTv) {
                                HomeMessagesZone(
                                    renewal = renewal,
                                    announcement = announcement,
                                    optionalUpdate = optionalUpdate,
                                    download = OperationsDownloadUiState(),
                                    isTv = isTv,
                                    rowIndex = 1,
                                    remembered = NavigationPosition(),
                                    navigationMemory = remember { NavigationMemoryStore() },
                                    homeModelIsCurrent = false,
                                    railFocusRevision = 0L,
                                    homeReturnRailBaseline = 0L,
                                    onRenewal = {},
                                    onOpenAnnouncementDetail = {},
                                    onOpenUpdateDetail = {},
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun card(title: String) = composeRule.onNode(hasText(title) and hasClickAction())

    private fun bounds(title: String): DpRect = card(title).getUnclippedBoundsInRoot()

    private fun inset(size: Viewport): Float =
        tvPremiumWindowPolicy(size.width, size.height).horizontalSafeInsetDp

    private fun expectedLeft(size: Viewport): Float = TV_PAGE_GUTTER.value + inset(size)

    private fun expectedRight(size: Viewport): Float = size.width - TV_PAGE_GUTTER.value

    private fun assertBounds(label: String, bounds: DpRect, expectedLeft: Float, expectedRight: Float) {
        assertTrue(
            "$label left=${bounds.left.value} expected=$expectedLeft",
            abs(bounds.left.value - expectedLeft) < .51f,
        )
        assertTrue(
            "$label right=${bounds.right.value} expected=$expectedRight",
            abs(bounds.right.value - expectedRight) < .51f,
        )
    }

    private companion object {
        const val LOG_TAG = "R11HomeMessageInset"
    }
}
