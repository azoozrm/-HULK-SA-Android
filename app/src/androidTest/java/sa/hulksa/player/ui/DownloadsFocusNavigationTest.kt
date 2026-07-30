package sa.hulksa.player.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
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

class DownloadsFocusNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun activeDownloadsExposeAllActionsAndExecuteCallbacksAcrossTwoRows() {
        var pauseResumeCalls = 0
        var priorityCalls = 0
        var cancelCalls = 0
        val downloads = listOf(
            activeDownload(1L, "الحلقة 1", 17L * 1024L * 1024L),
            activeDownload(2L, "الحلقة 2", 9L * 1024L * 1024L),
        )
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
                        navigationMemory = NavigationMemoryStore(),
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
                        onDeleteDownload = { cancelCalls += 1 },
                        onRetryDownload = { pauseResumeCalls += 1 },
                        onToggleWifiOnly = {},
                        onToggleDownloadSchedule = {},
                        onCycleConcurrentDownloads = {},
                        onCycleDownloadPriority = { priorityCalls += 1 },
                        onRunDiagnostics = {},
                        onLogout = {},
                    )
                }
            }
        }

        compose.onAllNodesWithText("ايقاف مؤقت").assertCountEquals(2)
        compose.onAllNodesWithText("عادية").assertCountEquals(2)
        compose.onAllNodesWithText("الغاء").assertCountEquals(2)
        compose.onAllNodesWithText("ايقاف مؤقت")[0].assertIsDisplayed().assertHasClickAction()
        compose.onAllNodesWithText("عادية")[0].assertIsDisplayed().assertHasClickAction()
        compose.onAllNodesWithText("الغاء")[0].assertIsDisplayed().assertHasClickAction()
        compose.onAllNodesWithText("ايقاف مؤقت")[1].assertIsDisplayed().assertHasClickAction()
        compose.onAllNodesWithText("عادية")[1].assertIsDisplayed().assertHasClickAction()
        compose.onAllNodesWithText("الغاء")[1].assertIsDisplayed().assertHasClickAction()

        // D-pad reachability and ordering are enforced by TvLayoutPolicyTest and
        // the strict nine-device Compatibility Lab. This Compose test proves the
        // rendered controls on a second active row execute the production callbacks.
        compose.onAllNodesWithText("ايقاف مؤقت")[1]
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onAllNodesWithText("عادية")[1]
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onAllNodesWithText("الغاء")[1]
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(1, pauseResumeCalls)
        assertEquals(1, priorityCalls)
        assertEquals(1, cancelCalls)
    }

    private fun activeDownload(id: Long, title: String, bytes: Long) = OfflineDownload(
        downloadId = id,
        historyKey = "series:$id",
        title = title,
        posterUrl = null,
        streamKind = "series",
        streamId = id.toInt(),
        extension = "mkv",
        seriesTitle = "مسلسل اختبار",
        season = 1,
        episodeNumber = id.toInt(),
        status = OfflineStatus.DOWNLOADING,
        bytesDownloaded = bytes,
        totalBytes = 387L * 1024L * 1024L,
        bytesPerSecond = 2L * 1024L * 1024L,
        etaSeconds = 120L,
    )
}
