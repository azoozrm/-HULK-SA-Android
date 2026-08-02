package sa.hulksa.player.compatibilityv2

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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

        swipeMainContentUp(times = 5)

        composeRule.onNodeWithText("$itemCount عنصر").assertIsNotDisplayed()
        composeRule.onNodeWithText(categoryName).assertIsNotDisplayed()
        composeRule.onNodeWithText("قناة اختبار $itemCount").assertIsDisplayed()
    }

    private fun verifyCatalogScroll(
        type: ContentType,
        destination: MainDestination,
        categoryName: String,
        itemPrefix: String,
        itemCount: Int,
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

        swipeMainContentUp(times = 4)

        composeRule.onNodeWithText("$itemCount عنصر").assertIsNotDisplayed()
        composeRule.onNodeWithText(categoryName).assertIsNotDisplayed()
        composeRule.onNodeWithText("$itemPrefix $itemCount").assertIsDisplayed()
    }

    private fun setMainShell(state: HulkUiState) {
        composeRule.setContent {
            HulkTheme {
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
        composeRule.waitForIdle()
    }

    private fun swipeMainContentUp(times: Int) {
        repeat(times) {
            composeRule.onRoot().performTouchInput { swipeUp(durationMillis = 350) }
            composeRule.waitForIdle()
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
