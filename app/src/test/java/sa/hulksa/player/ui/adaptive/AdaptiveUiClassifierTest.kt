package sa.hulksa.player.ui.adaptive

import android.view.InputDevice
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
    fun touchSuppressesFocusChromeButKeyboardRestoresIt() {
        assertFalse(shouldShowFocusHighlights(HulkDeviceClass.TABLET, HulkInputMode.TOUCH))
        assertTrue(shouldShowFocusHighlights(HulkDeviceClass.TABLET, HulkInputMode.KEYBOARD))
        assertTrue(shouldShowFocusHighlights(HulkDeviceClass.MOBILE, HulkInputMode.REMOTE))
    }
}
