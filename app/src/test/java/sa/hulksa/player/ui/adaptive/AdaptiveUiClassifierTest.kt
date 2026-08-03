package sa.hulksa.player.ui.adaptive

import android.view.InputDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveUiClassifierTest {
    @Test
    fun compactPhoneUsesTouchNavigationWithoutTelevisionRail() {
        val device = classifyDeviceClass(
            isTelevisionDevice = false,
            smallestWidthDp = 411,
            widthDp = 411,
        )
        val window = classifyWindowWidth(411)

        assertEquals(HulkDeviceClass.MOBILE, device)
        assertEquals(HulkWindowWidthClass.COMPACT, window)
        assertEquals(HulkNavigationType.BOTTOM_BAR, selectNavigationType(device, window))
    }

    @Test
    fun compactTabletWindowKeepsTabletFormFactorButUsesCompactNavigation() {
        val device = classifyDeviceClass(
            isTelevisionDevice = false,
            smallestWidthDp = 800,
            widthDp = 500,
        )
        val window = classifyWindowWidth(500)

        assertEquals(HulkDeviceClass.TABLET, device)
        assertEquals(HulkWindowWidthClass.COMPACT, window)
        assertEquals(HulkNavigationType.BOTTOM_BAR, selectNavigationType(device, window))
    }

    @Test
    fun configurationDimensionsRemainStableWhenImeShrinksTheComposeContainer() {
        assertEquals(411, stableWindowDimensionDp(configurationDp = 411, containerDp = 411))
        assertEquals(891, stableWindowDimensionDp(configurationDp = 891, containerDp = 430))
        assertEquals(430, stableWindowDimensionDp(configurationDp = 0, containerDp = 430))
    }

    @Test
    fun compactPortraitPolicyUsesTheFullAvailableWidth() {
        val policy = resolveAdaptiveLayoutPolicy(
            deviceClass = HulkDeviceClass.MOBILE,
            windowWidthClass = HulkWindowWidthClass.COMPACT,
            windowHeightClass = HulkWindowHeightClass.MEDIUM,
            orientation = HulkOrientation.PORTRAIT,
            inputMode = HulkInputMode.TOUCH,
        )

        assertEquals(0, policy.pageHorizontalPaddingDp)
    }

    @Test
    fun windowWidthBreakpointsUseContainerDimensions() {
        assertEquals(HulkWindowWidthClass.COMPACT, classifyWindowWidth(599))
        assertEquals(HulkWindowWidthClass.MEDIUM, classifyWindowWidth(600))
        assertEquals(HulkWindowWidthClass.MEDIUM, classifyWindowWidth(839))
        assertEquals(HulkWindowWidthClass.EXPANDED, classifyWindowWidth(840))
    }

    @Test
    fun windowHeightBreakpointsCoverShortLandscapeAndTallWindows() {
        assertEquals(HulkWindowHeightClass.COMPACT, classifyWindowHeight(479))
        assertEquals(HulkWindowHeightClass.MEDIUM, classifyWindowHeight(480))
        assertEquals(HulkWindowHeightClass.MEDIUM, classifyWindowHeight(899))
        assertEquals(HulkWindowHeightClass.EXPANDED, classifyWindowHeight(900))
    }

    @Test
    fun landscapePhoneRemainsMobileAndDoesNotReceiveTabletRail() {
        val device = classifyDeviceClass(
            isTelevisionDevice = false,
            smallestWidthDp = 411,
            widthDp = 891,
        )
        val window = classifyWindowWidth(891)

        assertEquals(HulkDeviceClass.MOBILE, device)
        assertEquals(HulkWindowWidthClass.EXPANDED, window)
        assertEquals(HulkNavigationType.BOTTOM_BAR, selectNavigationType(device, window))
        assertEquals(HulkOrientation.LANDSCAPE, classifyOrientation(891, 411))
    }

    @Test
    fun mediumTabletUsesRailWithoutTelevisionInteractionSemantics() {
        val device = classifyDeviceClass(
            isTelevisionDevice = false,
            smallestWidthDp = 600,
            widthDp = 800,
        )
        val window = classifyWindowWidth(800)

        assertEquals(HulkDeviceClass.TABLET, device)
        assertEquals(HulkWindowWidthClass.MEDIUM, window)
        assertEquals(HulkNavigationType.RAIL, selectNavigationType(device, window))
    }

    @Test
    fun expandedTabletEnablesTwoPaneOnlyInLandscape() {
        val landscape = resolveAdaptiveLayoutPolicy(
            deviceClass = HulkDeviceClass.TABLET,
            windowWidthClass = HulkWindowWidthClass.EXPANDED,
            windowHeightClass = HulkWindowHeightClass.MEDIUM,
            orientation = HulkOrientation.LANDSCAPE,
            inputMode = HulkInputMode.TOUCH,
        )
        val portrait = resolveAdaptiveLayoutPolicy(
            deviceClass = HulkDeviceClass.TABLET,
            windowWidthClass = HulkWindowWidthClass.EXPANDED,
            windowHeightClass = HulkWindowHeightClass.EXPANDED,
            orientation = HulkOrientation.PORTRAIT,
            inputMode = HulkInputMode.TOUCH,
        )

        assertEquals(HulkNavigationType.RAIL, landscape.navigationType)
        assertTrue(landscape.useTwoPane)
        assertFalse(portrait.useTwoPane)
        assertEquals(HulkContentDensity.SPACIOUS, landscape.contentDensity)
    }

    @Test
    fun televisionPolicyPreservesQualifiedEightDpOuterGutter() {
        val policy = resolveAdaptiveLayoutPolicy(
            deviceClass = HulkDeviceClass.TELEVISION,
            windowWidthClass = HulkWindowWidthClass.EXPANDED,
            windowHeightClass = HulkWindowHeightClass.MEDIUM,
            orientation = HulkOrientation.LANDSCAPE,
            inputMode = HulkInputMode.REMOTE,
        )

        assertEquals(HulkNavigationType.RAIL, policy.navigationType)
        assertEquals(8, policy.pageHorizontalPaddingDp)
        assertEquals(8, policy.pageVerticalPaddingDp)
        assertTrue(policy.restoreFocus)
    }

    @Test
    fun expandedTouchTabletUsesRailWithoutTelevisionFocusChrome() {
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
    fun keyboardAndRemoteSourcesAreClassifiedWithoutBitmaskOverlap() {
        assertEquals(HulkInputMode.KEYBOARD, classifyInputSource(InputDevice.SOURCE_KEYBOARD))
        assertEquals(HulkInputMode.REMOTE, classifyInputSource(InputDevice.SOURCE_DPAD))
        assertEquals(HulkInputMode.REMOTE, classifyInputSource(InputDevice.SOURCE_GAMEPAD))
    }

    @Test
    fun televisionFocusChromeIsSeparatedFromPhoneAndTabletKeyboardFocus() {
        assertFalse(shouldShowFocusHighlights(HulkDeviceClass.TABLET, HulkInputMode.TOUCH))
        assertFalse(shouldShowFocusHighlights(HulkDeviceClass.TABLET, HulkInputMode.KEYBOARD))
        assertFalse(shouldShowFocusHighlights(HulkDeviceClass.MOBILE, HulkInputMode.REMOTE))
        assertTrue(shouldShowKeyboardFocusIndicator(HulkDeviceClass.TABLET, HulkInputMode.KEYBOARD))
        assertTrue(shouldShowKeyboardFocusIndicator(HulkDeviceClass.MOBILE, HulkInputMode.KEYBOARD))
        assertTrue(shouldShowKeyboardFocusIndicator(HulkDeviceClass.MOBILE, HulkInputMode.REMOTE))
        assertFalse(shouldShowKeyboardFocusIndicator(HulkDeviceClass.TELEVISION, HulkInputMode.REMOTE))
    }

    @Test
    fun physical4kTelevisionResolvesToNormalLogicalViewport() {
        assertEquals(LogicalViewportDp(960, 540), logicalViewportDp(3840, 2160, 640))
        assertEquals(HulkWindowWidthClass.EXPANDED, classifyWindowWidth(960))
        assertEquals(HulkWindowHeightClass.MEDIUM, classifyWindowHeight(540))
    }

    @Test
    fun adaptiveGridColumnsRespectMinimumCardWidthAndBounds() {
        assertEquals(3, calculateAdaptiveGridColumns(360, 112, 12, maximumColumns = 6))
        assertEquals(4, calculateAdaptiveGridColumns(800, 148, 16, maximumColumns = 8))
        assertEquals(6, calculateAdaptiveGridColumns(1280, 168, 20, maximumColumns = 6))
    }
}
