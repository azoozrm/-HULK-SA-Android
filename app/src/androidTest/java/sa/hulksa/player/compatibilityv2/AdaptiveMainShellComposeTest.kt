package sa.hulksa.player.compatibilityv2

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import sa.hulksa.player.HulkScreen
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.MainDestination
import sa.hulksa.player.ui.adaptive.AdaptiveUiState
import sa.hulksa.player.ui.adaptive.HulkDeviceClass
import sa.hulksa.player.ui.adaptive.HulkInputMode
import sa.hulksa.player.ui.adaptive.HulkNavigationType
import sa.hulksa.player.ui.adaptive.HulkWindowWidthClass
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.screens.MainShellScreen
import sa.hulksa.player.ui.screens.NavigationMemoryStore
import sa.hulksa.player.ui.theme.HulkTheme

class AdaptiveMainShellComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun render(adaptive: AdaptiveUiState) {
        composeRule.setContent {
            HulkTheme {
                CompositionLocalProvider(LocalAdaptiveUi provides adaptive) {
                    var destination by remember { mutableStateOf(MainDestination.HOME) }
                    MainShellScreen(
                        state = HulkUiState(
                            screen = HulkScreen.MAIN,
                            isStarting = false,
                            destination = destination,
                        ),
                        isTv = adaptive.isTelevision,
                        navigationMemory = remember { NavigationMemoryStore() },
                        isFavorite = { false },
                        onSelectDestination = { destination = it },
                        onSelectCategory = {},
                        onSearch = {},
                        onOpen = {},
                        onOpenHistory = {},
                        onToggleFavorite = {},
                        onRefresh = {},
                        onClearHistory = {},
                        onPlayDownload = {},
                        onDeleteDownload = {},
                        onRetryDownload = {},
                        onToggleWifiOnly = {},
                        onToggleDownloadSchedule = {},
                        onCycleConcurrentDownloads = {},
                        onCycleDownloadPriority = {},
                        onRunDiagnostics = {},
                        onLogout = {},
                    )
                }
            }
        }
    }

    @Test
    fun phonePortraitUsesBottomNavigationWithoutContentOverlap() {
        render(
            AdaptiveUiState(
                deviceClass = HulkDeviceClass.MOBILE,
                windowWidthClass = HulkWindowWidthClass.COMPACT,
                navigationType = HulkNavigationType.BOTTOM_BAR,
                inputMode = HulkInputMode.TOUCH,
                screenWidthDp = 360,
                screenHeightDp = 800,
            ),
        )
        composeRule.onNodeWithTag("mobile-bottom-navigation").assertIsDisplayed()
        composeRule.onNodeWithTag("adaptive-navigation-rail").assertDoesNotExist()
        val content = composeRule.onNodeWithTag("main-shell-content").fetchSemanticsNode().boundsInRoot
        val navigation = composeRule.onNodeWithTag("mobile-bottom-navigation").fetchSemanticsNode().boundsInRoot
        assertTrue("Bottom navigation overlaps the content viewport", content.bottom <= navigation.top + 1f)
        captureEvidence("phone-portrait-bottom-navigation")
    }

    @Test
    fun shortLandscapePhoneKeepsCompactBottomNavigation() {
        render(
            AdaptiveUiState(
                deviceClass = HulkDeviceClass.MOBILE,
                windowWidthClass = HulkWindowWidthClass.EXPANDED,
                navigationType = HulkNavigationType.BOTTOM_BAR,
                inputMode = HulkInputMode.TOUCH,
                screenWidthDp = 800,
                screenHeightDp = 360,
            ),
        )
        composeRule.onNodeWithTag("mobile-bottom-navigation").assertIsDisplayed()
        composeRule.onNodeWithTag("mobile-bottom-nav-home").assertIsDisplayed()
        composeRule.onNodeWithTag("adaptive-navigation-rail").assertDoesNotExist()
        captureEvidence("phone-short-landscape-bottom-navigation")
    }

    @Test
    fun tabletAndTelevisionUseRailInsteadOfPhoneBottomNavigation() {
        render(
            AdaptiveUiState(
                deviceClass = HulkDeviceClass.TABLET,
                windowWidthClass = HulkWindowWidthClass.MEDIUM,
                navigationType = HulkNavigationType.RAIL,
                inputMode = HulkInputMode.TOUCH,
                screenWidthDp = 800,
                screenHeightDp = 600,
            ),
        )
        composeRule.onNodeWithTag("adaptive-navigation-rail").assertIsDisplayed()
        composeRule.onNodeWithTag("mobile-bottom-navigation").assertDoesNotExist()
        captureEvidence("tablet-navigation-rail")
    }

    private fun captureEvidence(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val root = File(
            requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)),
            "adaptive-main-shell-evidence",
        )
        check(root.mkdirs() || root.isDirectory) { "Unable to create adaptive evidence directory" }
        val screenshot = File(root, "$name.png")
        val hierarchy = File(root, "$name.xml")
        check(device.takeScreenshot(screenshot)) { "Unable to capture $name screenshot" }
        device.dumpWindowHierarchy(hierarchy)
        check(screenshot.isFile && screenshot.length() > 0L) { "Empty adaptive screenshot: $screenshot" }
        check(hierarchy.isFile && hierarchy.length() > 0L) { "Empty adaptive hierarchy: $hierarchy" }
    }
}
