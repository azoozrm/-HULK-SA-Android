package sa.hulksa.player.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import sa.hulksa.player.HulkScreen
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.MainDestination
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.ui.adaptive.AdaptiveUiState
import sa.hulksa.player.ui.adaptive.HulkDeviceClass
import sa.hulksa.player.ui.adaptive.HulkInputMode
import sa.hulksa.player.ui.adaptive.HulkNavigationType
import sa.hulksa.player.ui.adaptive.HulkWindowWidthClass
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.screens.MainShellScreen
import sa.hulksa.player.ui.screens.NavigationMemoryStore
import sa.hulksa.player.ui.theme.HulkTheme

class MainShellComposeQualityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tvRailDispatchesDownloadsNavigationWithoutProductionTestMarkers() {
        var selectedDestination = MainDestination.HOME
        compose.setContent {
            var destination by remember { mutableStateOf(MainDestination.HOME) }
            val adaptive = AdaptiveUiState(
                deviceClass = HulkDeviceClass.TELEVISION,
                windowWidthClass = HulkWindowWidthClass.EXPANDED,
                navigationType = HulkNavigationType.RAIL,
                inputMode = HulkInputMode.REMOTE,
                screenWidthDp = 960,
                screenHeightDp = 540,
            )
            HulkTheme {
                CompositionLocalProvider(LocalAdaptiveUi provides adaptive) {
                    MainShellScreen(
                        state = HulkUiState(
                            screen = HulkScreen.MAIN,
                            isStarting = false,
                            destination = destination,
                        ),
                        isTv = true,
                        navigationMemory = remember { NavigationMemoryStore() },
                        isFavorite = { false },
                        onSelectDestination = {
                            selectedDestination = it
                            destination = it
                        },
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

        // The collapsed TV rail intentionally hides text labels. The production
        // icon's accessibility label is the stable user-facing semantic target.
        compose.onNodeWithContentDescription("التنزيلات").performClick()
        compose.waitForIdle()

        assertEquals(MainDestination.DOWNLOADS, selectedDestination)
        compose.onNodeWithText("ادارة كاملة للمشاهدة بدون انترنت").fetchSemanticsNode()
    }

    @Test
    fun tvDownloadsDpadPreservesToolbarAndCardActionColumns() {
        val downloads = (1L..3L).map { id ->
            OfflineDownload(
                downloadId = id,
                historyKey = "MOVIE:$id",
                title = "تنزيل تجريبي $id",
                posterUrl = null,
                streamKind = "movie",
                streamId = id.toInt(),
                extension = "mkv",
                status = OfflineStatus.DOWNLOADING,
                bytesDownloaded = 16_000_000,
                totalBytes = 64_000_000,
            )
        }
        compose.setContent {
            val adaptive = AdaptiveUiState(
                deviceClass = HulkDeviceClass.TELEVISION,
                windowWidthClass = HulkWindowWidthClass.EXPANDED,
                navigationType = HulkNavigationType.RAIL,
                inputMode = HulkInputMode.REMOTE,
                screenWidthDp = 960,
                screenHeightDp = 540,
            )
            HulkTheme {
                CompositionLocalProvider(LocalAdaptiveUi provides adaptive) {
                    MainShellScreen(
                        state = HulkUiState(
                            screen = HulkScreen.MAIN,
                            isStarting = false,
                            destination = MainDestination.DOWNLOADS,
                            downloads = downloads,
                        ),
                        isTv = true,
                        navigationMemory = remember { NavigationMemoryStore() },
                        isFavorite = { false },
                        onSelectDestination = {},
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

        compose.waitForIdle()

        val primary = compose.onAllNodesWithContentDescription("ايقاف مؤقت")
        val priority = compose.onAllNodesWithContentDescription("عادية")
        val cancel = compose.onAllNodesWithContentDescription("الغاء")

        compose.waitUntil(timeoutMillis = 1_000) {
            primary[0].fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true
        }
        primary[0].assertIsFocused()
        primary[0].performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("كل الشبكات").assertIsFocused()
        compose.onNodeWithContentDescription("كل الشبكات").performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        primary[0].assertIsFocused()

        primary[0].performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitForIdle()
        priority[0].assertIsFocused()
        priority[0].performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("الجدولة الان").assertIsFocused()
        compose.onNodeWithContentDescription("الجدولة الان").performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        priority[0].assertIsFocused()

        priority[0].performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitForIdle()
        cancel[0].assertIsFocused()
        cancel[0].performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("متزامنة  2").assertIsFocused()
        compose.onNodeWithContentDescription("متزامنة  2").performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        cancel[0].assertIsFocused()

        cancel[0].performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        cancel[1].assertIsFocused()
        cancel[1].performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()
        cancel[0].assertIsFocused()

        cancel[0].performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()
        priority[0].performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        priority[1].assertIsFocused()
        priority[1].performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()
        primary[1].assertIsFocused()
        primary[1].performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()
        primary[0].assertIsFocused()
    }
}
