package sa.hulksa.player.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import sa.hulksa.player.HulkScreen
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.MainDestination
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
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

    private fun pressSystemKey(keyCode: Int) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keyCode)
        Thread.sleep(180)
        compose.waitForIdle()
    }

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
        lateinit var platformInputMode: InputModeManager
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
            platformInputMode = LocalInputModeManager.current
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

        compose.runOnIdle {
            check(platformInputMode.requestInputMode(InputMode.Keyboard))
        }
        primary[0].performSemanticsAction(SemanticsActions.RequestFocus) { requestFocus ->
            check(requestFocus())
        }
        compose.waitForIdle()
        primary[0].assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_UP)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("كل الشبكات").assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_DOWN)
        Thread.sleep(180)
        compose.waitForIdle()
        primary[0].assertIsFocused()

        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_LEFT)
        compose.waitForIdle()
        priority[0].assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_UP)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("الجدولة الان").assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_DOWN)
        Thread.sleep(180)
        compose.waitForIdle()
        priority[0].assertIsFocused()

        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_LEFT)
        compose.waitForIdle()
        cancel[0].assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_UP)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("متزامنة  2").assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_DOWN)
        Thread.sleep(180)
        compose.waitForIdle()
        cancel[0].assertIsFocused()

        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_DOWN)
        Thread.sleep(180)
        compose.waitForIdle()
        cancel[1].assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_UP)
        compose.waitForIdle()
        cancel[0].assertIsFocused()

        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_RIGHT)
        compose.waitForIdle()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_DOWN)
        Thread.sleep(180)
        compose.waitForIdle()
        priority[1].assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_RIGHT)
        compose.waitForIdle()
        primary[1].assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_UP)
        compose.waitForIdle()
        primary[0].assertIsFocused()
    }

    @Test
    fun tvLiveDpadRoutesNativeKeysAcrossChannelActions() {
        lateinit var platformInputMode: InputModeManager
        val channels = (1..2).map { id ->
            ContentItem(
                id = id,
                name = "قناة $id",
                categoryId = "qa-live",
                type = ContentType.LIVE,
                posterUrl = null,
                rating = null,
                year = null,
                containerExtension = "ts",
            )
        }
        compose.setContent {
            platformInputMode = LocalInputModeManager.current
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
                            destination = MainDestination.LIVE,
                            catalogs = mapOf(
                                ContentType.LIVE to Catalog(
                                    categories = emptyList(),
                                    items = channels,
                                ),
                            ),
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
        compose.runOnIdle {
            check(platformInputMode.requestInputMode(InputMode.Keyboard))
        }

        val channel = compose.onNode(
            hasClickAction() and hasAnyDescendant(hasText("قناة 1")),
            useUnmergedTree = true,
        )
        val play = compose.onNodeWithContentDescription("تشغيل القناة")
        val favorite = compose.onNodeWithContentDescription("+ المفضلة")
        channel.performSemanticsAction(SemanticsActions.RequestFocus) { requestFocus ->
            check(requestFocus())
        }
        compose.waitForIdle()
        channel.assertIsFocused()

        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_LEFT)
        play.assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_LEFT)
        favorite.assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_RIGHT)
        play.assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_RIGHT)
        channel.assertIsFocused()
    }

}
