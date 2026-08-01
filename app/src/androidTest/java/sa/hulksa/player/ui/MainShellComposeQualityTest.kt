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
        val downloadState = mutableStateOf(downloads)
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
                            downloads = downloadState.value,
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
                        onDeleteDownload = { deleted ->
                            downloadState.value = downloadState.value.filterNot {
                                it.downloadId == deleted.downloadId
                            }
                        },
                        onRetryDownload = { selected ->
                            downloadState.value = downloadState.value.map { item ->
                                if (item.downloadId != selected.downloadId) item else item.copy(
                                    status = if (item.status == OfflineStatus.PAUSED) {
                                        OfflineStatus.DOWNLOADING
                                    } else {
                                        OfflineStatus.PAUSED
                                    },
                                )
                            }
                        },
                        onToggleWifiOnly = {},
                        onToggleDownloadSchedule = {},
                        onCycleConcurrentDownloads = {},
                        onCycleDownloadPriority = { selected ->
                            downloadState.value = downloadState.value.map { item ->
                                if (item.downloadId == selected.downloadId) {
                                    item.copy(priority = if (item.priority == 1) 0 else 1)
                                } else {
                                    item
                                }
                            }
                        },
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
        compose.onNodeWithContentDescription("كل الشبكات").assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_DOWN)
        primary[0].assertIsFocused()

        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_LEFT)
        priority[0].assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithContentDescription("الجدولة الان").assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_DOWN)
        priority[0].assertIsFocused()

        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_LEFT)
        cancel[0].assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithContentDescription("متزامنة  2").assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_DOWN)
        cancel[0].assertIsFocused()

        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_DOWN)
        cancel[1].assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_UP)
        cancel[0].assertIsFocused()

        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_RIGHT)
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_DOWN)
        priority[1].assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_RIGHT)
        primary[1].assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_UP)
        primary[0].assertIsFocused()

        // Three-row/off-viewport traversal and the final-row boundary remain
        // inside the same logical primary column.
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_DOWN)
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_DOWN)
        primary[2].assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_DOWN)
        primary[2].assertIsFocused()

        // Status mutation reuses the stable requester and keeps focus on the
        // logical primary action across pause and resume.
        primary[2].performClick()
        compose.waitForIdle()
        val resume = compose.onNodeWithContentDescription("استئناف")
        resume.assertIsFocused()
        resume.performClick()
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("ايقاف مؤقت")[2].assertIsFocused()

        // Deleting the focused final row shrinks the fixture from three rows to
        // two and relocates focus to the surviving previous row in the same slot.
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_LEFT)
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_LEFT)
        compose.onAllNodesWithContentDescription("الغاء")[2].assertIsFocused()
        compose.onAllNodesWithContentDescription("الغاء")[2].performClick()
        compose.waitForIdle()
        assertEquals(2, downloadState.value.size)
        compose.onAllNodesWithContentDescription("الغاء")[1].assertIsFocused()
    }

    @Test
    fun tvLiveDpadRoutesNativeKeysAcrossChannelsAndActions() {
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

        val channelOne = compose.onNode(
            hasClickAction() and hasAnyDescendant(hasText("قناة 1")),
            useUnmergedTree = true,
        )
        val channelTwo = compose.onNode(
            hasClickAction() and hasAnyDescendant(hasText("قناة 2")),
            useUnmergedTree = true,
        )
        val play = compose.onNodeWithContentDescription("تشغيل القناة")
        val favorite = compose.onNodeWithContentDescription("+ المفضلة")
        channelOne.performSemanticsAction(SemanticsActions.RequestFocus) { requestFocus ->
            check(requestFocus())
        }
        compose.waitForIdle()
        channelOne.assertIsFocused()

        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_LEFT)
        play.assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_LEFT)
        favorite.assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_RIGHT)
        play.assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_RIGHT)
        channelOne.assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_DOWN)
        channelTwo.assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_LEFT)
        play.assertIsFocused()
        pressSystemKey(AndroidKeyEvent.KEYCODE_DPAD_RIGHT)
        channelTwo.assertIsFocused()
    }
}
