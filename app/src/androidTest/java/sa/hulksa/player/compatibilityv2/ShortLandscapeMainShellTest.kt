package sa.hulksa.player.compatibilityv2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import org.junit.Rule
import org.junit.Test
import sa.hulksa.player.HulkScreen
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.MainDestination
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.ui.adaptive.AdaptiveUiState
import sa.hulksa.player.ui.adaptive.HulkDeviceClass
import sa.hulksa.player.ui.adaptive.HulkInputMode
import sa.hulksa.player.ui.adaptive.HulkNavigationType
import sa.hulksa.player.ui.adaptive.HulkOrientation
import sa.hulksa.player.ui.adaptive.HulkWindowHeightClass
import sa.hulksa.player.ui.adaptive.HulkWindowWidthClass
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.screens.MainShellScreen
import sa.hulksa.player.ui.screens.NavigationMemoryStore
import sa.hulksa.player.ui.theme.HulkTheme
import sa.hulksa.player.ui.theme.LocalHulkColors

class ShortLandscapeMainShellTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun movieHeaderAndCategoriesScrollWithPosterGrid() {
        verifyCatalogScroll(
            type = ContentType.MOVIE,
            destination = MainDestination.MOVIES,
            categoryName = "فئة افلام الاختبار",
            itemPrefix = "فيلم اختبار",
            itemCount = 40,
            evidenceName = "movies",
        )
    }

    @Test
    fun seriesHeaderAndCategoriesScrollWithPosterGrid() {
        verifyCatalogScroll(
            type = ContentType.SERIES,
            destination = MainDestination.SERIES,
            categoryName = "فئة مسلسلات الاختبار",
            itemPrefix = "مسلسل اختبار",
            itemCount = 41,
            evidenceName = "series",
        )
    }

    @Test
    fun liveHeaderAndCategoriesScrollWithChannelList() {
        val categoryName = "فئة قنوات الاختبار"
        val itemCount = 42
        val catalog = fakeCatalog(
            type = ContentType.LIVE,
            categoryName = categoryName,
            itemPrefix = "قناة اختبار",
            itemCount = itemCount,
        )
        setMainShell(
            state = HulkUiState(
                screen = HulkScreen.MAIN,
                isStarting = false,
                destination = MainDestination.LIVE,
                selectedType = ContentType.LIVE,
                catalogs = mapOf(ContentType.LIVE to catalog),
            ),
        )

        composeRule.onNodeWithText("$itemCount عنصر").assertIsDisplayed()
        composeRule.onNodeWithText(categoryName).assertIsDisplayed()

        swipeMainContentUp(times = 12)

        composeRule.onNodeWithText("$itemCount عنصر").assertIsNotDisplayed()
        composeRule.onNodeWithText(categoryName).assertIsNotDisplayed()
        composeRule.onNodeWithText("قناة اختبار $itemCount").assertIsDisplayed()
        captureFullWindowEvidence("live")
    }

    private fun verifyCatalogScroll(
        type: ContentType,
        destination: MainDestination,
        categoryName: String,
        itemPrefix: String,
        itemCount: Int,
        evidenceName: String,
    ) {
        val catalog = fakeCatalog(type, categoryName, itemPrefix, itemCount)
        setMainShell(
            state = HulkUiState(
                screen = HulkScreen.MAIN,
                isStarting = false,
                destination = destination,
                selectedType = type,
                catalogs = mapOf(type to catalog),
            ),
        )

        composeRule.onNodeWithText("$itemCount عنصر").assertIsDisplayed()
        composeRule.onNodeWithText(categoryName).assertIsDisplayed()

        swipeMainContentUp(times = 6)

        composeRule.onNodeWithText("$itemCount عنصر").assertIsNotDisplayed()
        composeRule.onNodeWithText(categoryName).assertIsNotDisplayed()
        composeRule.onNodeWithText("$itemPrefix $itemCount").assertIsDisplayed()
        captureFullWindowEvidence(evidenceName)
    }

    private fun setMainShell(state: HulkUiState) {
        composeRule.setContent {
            HulkTheme {
                val colors = LocalHulkColors.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surface),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing),
                    ) {
                        CompositionLocalProvider(LocalAdaptiveUi provides shortLandscapeUi()) {
                            MainShellScreen(
                                state = state,
                                isTv = false,
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
        }
        composeRule.waitForIdle()
    }

    private fun swipeMainContentUp(times: Int) {
        repeat(times) {
            composeRule.onRoot().performTouchInput { swipeUp(durationMillis = 350) }
            composeRule.waitForIdle()
        }
    }

    private fun captureFullWindowEvidence(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val evidenceRoot = File(
            requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)) {
                "External evidence directory is unavailable"
            },
            "short-landscape-evidence",
        )
        check(evidenceRoot.mkdirs() || evidenceRoot.isDirectory) {
            "Unable to create short-landscape evidence directory: $evidenceRoot"
        }
        val screenshot = File(evidenceRoot, "$name-full-window.png")
        val hierarchy = File(evidenceRoot, "$name-window.xml")
        check(device.takeScreenshot(screenshot)) {
            "Unable to capture short-landscape screenshot: $screenshot"
        }
        device.dumpWindowHierarchy(hierarchy)
        check(screenshot.isFile && screenshot.length() > 0L) {
            "Short-landscape screenshot is empty: $screenshot"
        }
        check(hierarchy.isFile && hierarchy.length() > 0L) {
            "Short-landscape hierarchy is empty: $hierarchy"
        }
    }

    private fun shortLandscapeUi(): AdaptiveUiState = AdaptiveUiState(
        deviceClass = HulkDeviceClass.MOBILE,
        windowWidthClass = HulkWindowWidthClass.EXPANDED,
        windowHeightClass = HulkWindowHeightClass.COMPACT,
        orientation = HulkOrientation.LANDSCAPE,
        navigationType = HulkNavigationType.TOP_BAR,
        inputMode = HulkInputMode.TOUCH,
        screenWidthDp = 900,
        screenHeightDp = 411,
        deviceSmallestWidthDp = 411,
        fontScale = 1.5f,
        density = 2.625f,
    )

    private fun fakeCatalog(
        type: ContentType,
        categoryName: String,
        itemPrefix: String,
        itemCount: Int,
    ): Catalog {
        val categoryId = "test-${type.name.lowercase()}"
        return Catalog(
            categories = listOf(Category(categoryId, categoryName, type)),
            items = (1..itemCount).map { index ->
                ContentItem(
                    id = index,
                    name = "$itemPrefix $index",
                    categoryId = categoryId,
                    type = type,
                    posterUrl = null,
                    rating = null,
                    year = "2026",
                    containerExtension = if (type == ContentType.LIVE) null else "mp4",
                )
            },
        )
    }
}
