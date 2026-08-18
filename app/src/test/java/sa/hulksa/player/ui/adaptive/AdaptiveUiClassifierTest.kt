package sa.hulksa.player.ui.adaptive

import android.view.InputDevice
import android.view.KeyEvent as AndroidKeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveUiClassifierTest {
    @Test
    fun compactPhoneUsesMobileTopNavigation() {
        val device = classifyDeviceClass(
            isTelevisionDevice = false,
            smallestWidthDp = 411,
            widthDp = 411,
        )
        val window = classifyWindowWidth(411)

        assertEquals(HulkDeviceClass.MOBILE, device)
        assertEquals(HulkWindowWidthClass.COMPACT, window)
        assertEquals(HulkNavigationType.TOP_BAR, selectNavigationType(device, window))
    }

    @Test
    fun resizedCompactContainerDoesNotKeepThePhysicalDisplayLayout() {
        val device = classifyDeviceClass(
            isTelevisionDevice = false,
            smallestWidthDp = 500,
            widthDp = 500,
        )
        val window = classifyWindowWidth(500)

        assertEquals(HulkDeviceClass.MOBILE, device)
        assertEquals(HulkWindowWidthClass.COMPACT, window)
        assertEquals(HulkNavigationType.TOP_BAR, selectNavigationType(device, window))
    }

    @Test
    fun windowWidthBreakpointsUseContainerDimensions() {
        assertEquals(HulkWindowWidthClass.COMPACT, classifyWindowWidth(599))
        assertEquals(HulkWindowWidthClass.MEDIUM, classifyWindowWidth(600))
        assertEquals(HulkWindowWidthClass.MEDIUM, classifyWindowWidth(839))
        assertEquals(HulkWindowWidthClass.EXPANDED, classifyWindowWidth(840))
    }

    @Test
    fun landscapePhoneDoesNotBecomeTabletOrRail() {
        val device = classifyDeviceClass(
            isTelevisionDevice = false,
            smallestWidthDp = 411,
            widthDp = 891,
        )
        val window = classifyWindowWidth(891)

        assertEquals(HulkDeviceClass.MOBILE, device)
        assertEquals(HulkWindowWidthClass.EXPANDED, window)
        assertEquals(HulkNavigationType.TOP_BAR, selectNavigationType(device, window))
    }

    @Test
    fun portraitTabletUsesTabletLayoutWithoutTelevisionSizing() {
        val device = classifyDeviceClass(
            isTelevisionDevice = false,
            smallestWidthDp = 600,
            widthDp = 800,
        )
        val window = classifyWindowWidth(800)

        assertEquals(HulkDeviceClass.TABLET, device)
        assertEquals(HulkWindowWidthClass.MEDIUM, window)
        assertEquals(HulkNavigationType.TOP_BAR, selectNavigationType(device, window))
    }

    @Test
    fun expandedTabletUsesRailNavigation() {
        val device = classifyDeviceClass(
            isTelevisionDevice = false,
            smallestWidthDp = 600,
            widthDp = 1280,
        )
        val window = classifyWindowWidth(1280)

        assertEquals(HulkDeviceClass.TABLET, device)
        assertEquals(HulkWindowWidthClass.EXPANDED, window)
        assertEquals(HulkNavigationType.RAIL, selectNavigationType(device, window))
    }

    @Test
    fun expandedTouchTabletUsesRailWithoutTelevisionInteractionModel() {
        val state = AdaptiveUiState(
            deviceClass = HulkDeviceClass.TABLET,
            windowWidthClass = HulkWindowWidthClass.EXPANDED,
            navigationType = HulkNavigationType.RAIL,
            inputMode = HulkInputMode.TOUCH,
            screenWidthDp = 1280,
            screenHeightDp = 800,
        )

        assertEquals(HulkNavigationType.RAIL, state.navigationType)
        assertFalse(state.isTelevision)
        assertFalse(state.showFocusHighlights)
    }

    @Test
    fun televisionAlwaysUsesRailAndFocusHighlights() {
        val device = classifyDeviceClass(
            isTelevisionDevice = true,
            smallestWidthDp = 540,
            widthDp = 960,
        )
        val window = classifyWindowWidth(960)

        assertEquals(HulkDeviceClass.TELEVISION, device)
        assertEquals(HulkNavigationType.RAIL, selectNavigationType(device, window))
        assertTrue(shouldShowFocusHighlights(device, HulkInputMode.TOUCH))
    }

    @Test
    fun compactTvWindowGetsExtraSafeAreaAndQualifiedRailMetrics() {
        val policy = tvPremiumWindowPolicy(screenWidthDp = 960, screenHeightDp = 540)

        assertEquals(18f, policy.horizontalSafeInsetDp, 0.001f)
        assertEquals(16f, policy.verticalSafeInsetDp, 0.001f)
        assertEquals(0.96f, policy.contentWidthFraction, 0.001f)
        assertEquals(88f, policy.railCollapsedWidthDp, 0.001f)
        assertEquals(194f, policy.railExpandedWidthDp, 0.001f)
        assertEquals(54f, policy.railLogoSizeDp, 0.001f)
        assertEquals(46f, policy.railItemHeightDp, 0.001f)
        assertEquals(23f, policy.railIconSizeDp, 0.001f)
        assertEquals(14f, policy.railLabelSizeSp, 0.001f)
        assertEquals(2f, policy.focusBorderWidthDp, 0.001f)
        assertEquals(1.02f, policy.focusScale, 0.001f)
    }

    @Test
    fun standardTvWindowPreservesQualifiedShellProportions() {
        val policy = tvPremiumWindowPolicy(screenWidthDp = 1280, screenHeightDp = 720)

        assertEquals(8f, policy.horizontalSafeInsetDp, 0.001f)
        assertEquals(8f, policy.verticalSafeInsetDp, 0.001f)
        assertEquals(0.95f, policy.contentWidthFraction, 0.001f)
        assertEquals(91.42857f, policy.railCollapsedWidthDp, 0.001f)
        assertEquals(206.45161f, policy.railExpandedWidthDp, 0.001f)
        assertEquals(72f, policy.railLogoSizeDp, 0.001f)
        assertEquals(49.65517f, policy.railItemHeightDp, 0.001f)
        assertEquals(24f, policy.railIconSizeDp, 0.001f)
        assertEquals(14.4f, policy.railLabelSizeSp, 0.001f)
        assertEquals(2f, policy.focusBorderWidthDp, 0.001f)
        assertEquals(1.025f, policy.focusScale, 0.001f)
    }

    @Test
    fun largeTvWindowCapsRailIdentityAndSpacing() {
        val policy = tvPremiumWindowPolicy(screenWidthDp = 1920, screenHeightDp = 1080)

        assertEquals(8f, policy.horizontalSafeInsetDp, 0.001f)
        assertEquals(8f, policy.verticalSafeInsetDp, 0.001f)
        assertEquals(0.94f, policy.contentWidthFraction, 0.001f)
        assertEquals(102f, policy.railCollapsedWidthDp, 0.001f)
        assertEquals(236f, policy.railExpandedWidthDp, 0.001f)
        assertEquals(78f, policy.railLogoSizeDp, 0.001f)
        assertEquals(56f, policy.railItemHeightDp, 0.001f)
        assertEquals(28f, policy.railIconSizeDp, 0.001f)
        assertEquals(17f, policy.railLabelSizeSp, 0.001f)
        assertEquals(1.03f, policy.focusScale, 0.001f)
    }

    @Test
    fun adaptiveStateExposesTheSameTvPremiumPolicyForScreens() {
        val state = AdaptiveUiState(
            deviceClass = HulkDeviceClass.TELEVISION,
            windowWidthClass = HulkWindowWidthClass.EXPANDED,
            navigationType = HulkNavigationType.RAIL,
            inputMode = HulkInputMode.REMOTE,
            screenWidthDp = 1280,
            screenHeightDp = 720,
        )

        assertEquals(tvPremiumWindowPolicy(1280, 720), state.tvPremiumPolicy)
    }

    @Test
    fun keyboardAndRemoteSourcesAreClassifiedWithoutBitmaskOverlap() {
        assertEquals(HulkInputMode.KEYBOARD, classifyInputSource(InputDevice.SOURCE_KEYBOARD))
        assertEquals(HulkInputMode.REMOTE, classifyInputSource(InputDevice.SOURCE_DPAD))
        assertEquals(HulkInputMode.REMOTE, classifyInputSource(InputDevice.SOURCE_GAMEPAD))
    }

    @Test
    fun televisionTreatsKeyboardReportedDpadKeysAsRemoteNavigation() {
        assertEquals(
            HulkInputMode.REMOTE,
            classifyInputEvent(
                source = InputDevice.SOURCE_KEYBOARD,
                keyCode = AndroidKeyEvent.KEYCODE_DPAD_UP,
                isTelevisionDevice = true,
            ),
        )
        assertEquals(
            HulkInputMode.REMOTE,
            classifyInputEvent(
                source = InputDevice.SOURCE_KEYBOARD,
                keyCode = AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                isTelevisionDevice = true,
            ),
        )
    }

    @Test
    fun nonTvKeyboardKeepsKeyboardInteractionModelForArrowKeys() {
        assertEquals(
            HulkInputMode.KEYBOARD,
            classifyInputEvent(
                source = InputDevice.SOURCE_KEYBOARD,
                keyCode = AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                isTelevisionDevice = false,
            ),
        )
    }

    @Test
    fun trueRemoteSourceStaysRemoteEvenForNonNavigationKeys() {
        assertEquals(
            HulkInputMode.REMOTE,
            classifyInputEvent(
                source = InputDevice.SOURCE_DPAD,
                keyCode = AndroidKeyEvent.KEYCODE_BACK,
                isTelevisionDevice = true,
            ),
        )
    }

    @Test
    fun touchSuppressesFocusChromeButKeyboardRestoresIt() {
        assertFalse(shouldShowFocusHighlights(HulkDeviceClass.TABLET, HulkInputMode.TOUCH))
        assertTrue(shouldShowFocusHighlights(HulkDeviceClass.TABLET, HulkInputMode.KEYBOARD))
        assertTrue(shouldShowFocusHighlights(HulkDeviceClass.MOBILE, HulkInputMode.REMOTE))
    }
}
