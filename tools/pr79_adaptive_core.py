#!/usr/bin/env python3
from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    target = Path(path)
    text = target.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one exact match, found {count}: {old[:180]!r}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


adaptive = "app/src/main/java/sa/hulksa/player/ui/adaptive/AdaptiveUi.kt"
replace_once(
    adaptive,
    '''enum class HulkNavigationType {
    TOP_BAR,
    RAIL,
}
''',
    '''enum class HulkNavigationType {
    TOP_BAR,
    BOTTOM_BAR,
    RAIL,
}
''',
)
replace_once(
    adaptive,
    '''    val showFocusHighlights: Boolean
        get() = shouldShowFocusHighlights(deviceClass, inputMode)

    val layoutPolicy: AdaptiveLayoutPolicy
''',
    '''    val showFocusHighlights: Boolean
        get() = shouldShowFocusHighlights(deviceClass, inputMode)

    val showKeyboardFocusIndicator: Boolean
        get() = shouldShowKeyboardFocusIndicator(deviceClass, inputMode)

    val layoutPolicy: AdaptiveLayoutPolicy
''',
)
replace_once(
    adaptive,
    '''        navigationType = HulkNavigationType.TOP_BAR,
''',
    '''        navigationType = HulkNavigationType.BOTTOM_BAR,
''',
)
replace_once(
    adaptive,
    '''            while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                controller.recordTouchInput()
            }
''',
    '''            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.any { it.pressed || it.previousPressed }) {
                    controller.recordTouchInput()
                }
            }
''',
)
replace_once(
    adaptive,
    '''internal fun stableWindowDimensionDp(configurationDp: Int, containerDp: Int): Int =
    configurationDp.takeIf { it > 0 } ?: containerDp.coerceAtLeast(1)

fun classifyInputSource(source: Int): HulkInputMode {
''',
    '''internal fun stableWindowDimensionDp(configurationDp: Int, containerDp: Int): Int =
    configurationDp.takeIf { it > 0 } ?: containerDp.coerceAtLeast(1)

@Immutable
data class LogicalViewportDp(
    val widthDp: Int,
    val heightDp: Int,
)

fun logicalViewportDp(
    physicalWidthPx: Int,
    physicalHeightPx: Int,
    densityDpi: Int,
): LogicalViewportDp {
    require(physicalWidthPx > 0 && physicalHeightPx > 0) { "Physical viewport must be positive" }
    require(densityDpi > 0) { "Density must be positive" }
    return LogicalViewportDp(
        widthDp = ((physicalWidthPx * 160.0) / densityDpi).roundToInt().coerceAtLeast(1),
        heightDp = ((physicalHeightPx * 160.0) / densityDpi).roundToInt().coerceAtLeast(1),
    )
}

fun calculateAdaptiveGridColumns(
    availableWidthDp: Int,
    minimumItemWidthDp: Int,
    horizontalSpacingDp: Int,
    minimumColumns: Int = 1,
    maximumColumns: Int = 12,
): Int {
    require(availableWidthDp > 0)
    require(minimumItemWidthDp > 0)
    require(horizontalSpacingDp >= 0)
    require(minimumColumns > 0 && maximumColumns >= minimumColumns)
    val columns = (availableWidthDp + horizontalSpacingDp) /
        (minimumItemWidthDp + horizontalSpacingDp)
    return columns.coerceIn(minimumColumns, maximumColumns)
}

fun classifyInputSource(source: Int): HulkInputMode {
''',
)
replace_once(
    adaptive,
    '''    deviceClass == HulkDeviceClass.TABLET && windowWidthClass != HulkWindowWidthClass.COMPACT -> HulkNavigationType.RAIL
    else -> HulkNavigationType.TOP_BAR
}
''',
    '''    deviceClass == HulkDeviceClass.TABLET && windowWidthClass != HulkWindowWidthClass.COMPACT -> HulkNavigationType.RAIL
    else -> HulkNavigationType.BOTTOM_BAR
}
''',
)
replace_once(
    adaptive,
    '''    else -> AdaptiveLayoutPolicy(
        navigationType = HulkNavigationType.TOP_BAR,
''',
    '''    else -> AdaptiveLayoutPolicy(
        navigationType = HulkNavigationType.BOTTOM_BAR,
''',
)
replace_once(
    adaptive,
    '''fun shouldShowFocusHighlights(
    deviceClass: HulkDeviceClass,
    inputMode: HulkInputMode,
): Boolean = deviceClass == HulkDeviceClass.TELEVISION || inputMode != HulkInputMode.TOUCH
''',
    '''fun shouldShowFocusHighlights(
    deviceClass: HulkDeviceClass,
    inputMode: HulkInputMode,
): Boolean = deviceClass == HulkDeviceClass.TELEVISION

fun shouldShowKeyboardFocusIndicator(
    deviceClass: HulkDeviceClass,
    inputMode: HulkInputMode,
): Boolean = deviceClass != HulkDeviceClass.TELEVISION && inputMode == HulkInputMode.KEYBOARD
''',
)

main_shell = "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
replace_once(
    main_shell,
    '''import androidx.compose.foundation.layout.isImeVisible
''',
    '''import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
''',
)
replace_once(
    main_shell,
    '''import androidx.compose.ui.platform.LocalSoftwareKeyboardController
''',
    '''import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
''',
)
replace_once(
    main_shell,
    '''        } else {
            Column(Modifier.fillMaxSize()) {
                MobileNavigation(state.destination, rememberingSelectDestination)
                Box(Modifier.weight(1f).clipToBounds()) {
                    DestinationContent(
                        state = state,
                        isTv = false,
                        navigationMemory = navigationMemory,
                        isFavorite = resolvedIsFavorite,
                        onSelectCategory = onSelectCategory,
                        onSearch = onSearch,
                        onOpen = onOpen,
                        onOpenHistory = onOpenHistory,
                        onToggleFavorite = toggleFavoriteWithFeedback,
                        onRefresh = onRefresh,
                        onSelectDestination = onSelectDestination,
                        onClearHistory = onClearHistory,
                        onPlayDownload = onPlayDownload,
                        onDeleteDownload = onDeleteDownload,
                        onRetryDownload = onRetryDownload,
                        onToggleWifiOnly = onToggleWifiOnly,
                        onToggleDownloadSchedule = onToggleDownloadSchedule,
                        onCycleConcurrentDownloads = onCycleConcurrentDownloads,
                        onCycleDownloadPriority = onCycleDownloadPriority,
                        onRunDiagnostics = onRunDiagnostics,
                        onLogout = onLogout,
                    )
                }
            }
        }
''',
    '''        } else {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .testTag("main-shell-content"),
                ) {
                    DestinationContent(
                        state = state,
                        isTv = false,
                        navigationMemory = navigationMemory,
                        isFavorite = resolvedIsFavorite,
                        onSelectCategory = onSelectCategory,
                        onSearch = onSearch,
                        onOpen = onOpen,
                        onOpenHistory = onOpenHistory,
                        onToggleFavorite = toggleFavoriteWithFeedback,
                        onRefresh = onRefresh,
                        onSelectDestination = rememberingSelectDestination,
                        onClearHistory = onClearHistory,
                        onPlayDownload = onPlayDownload,
                        onDeleteDownload = onDeleteDownload,
                        onRetryDownload = onRetryDownload,
                        onToggleWifiOnly = onToggleWifiOnly,
                        onToggleDownloadSchedule = onToggleDownloadSchedule,
                        onCycleConcurrentDownloads = onCycleConcurrentDownloads,
                        onCycleDownloadPriority = onCycleDownloadPriority,
                        onRunDiagnostics = onRunDiagnostics,
                        onLogout = onLogout,
                    )
                }
                MobileBottomNavigation(state.destination, rememberingSelectDestination)
            }
        }
''',
)
replace_once(
    main_shell,
    '''        modifier = Modifier
            .width(railWidth)
''',
    '''        modifier = Modifier
            .testTag("adaptive-navigation-rail")
            .width(railWidth)
''',
)
replace_once(
    main_shell,
    '''@Composable
private fun MobileNavigation(selected: MainDestination, onSelect: (MainDestination) -> Unit) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val compactLandscape =
        adaptiveUi.screenWidthDp > adaptiveUi.screenHeightDp && adaptiveUi.screenHeightDp < 520
    val navigationState = rememberLazyListState()
    LaunchedEffect(selected) {
        val selectedIndex = destinations.indexOfFirst { it.destination == selected }.coerceAtLeast(0)
        navigationState.animateScrollToItem(selectedIndex + 1)
    }
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface),
        state = navigationState,
        contentPadding = PaddingValues(
            horizontal = 8.dp,
            vertical = if (compactLandscape) 4.dp else 8.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(if (compactLandscape) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item { BrandBadge(Modifier.size(if (compactLandscape) 34.dp else 40.dp)) }
        items(destinations, key = { it.destination.name }) { entry ->
            FocusButton(
                text = entry.label,
                onClick = { onSelect(entry.destination) },
                primary = selected == entry.destination,
                compact = true,
                modifier = Modifier.heightIn(min = if (compactLandscape) 42.dp else 48.dp),
            )
        }
    }
}
''',
    '''@Composable
private fun MobileBottomNavigation(selected: MainDestination, onSelect: (MainDestination) -> Unit) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val compactLandscape =
        adaptiveUi.orientation == HulkOrientation.LANDSCAPE &&
            adaptiveUi.windowHeightClass == HulkWindowHeightClass.COMPACT
    val navigationState = rememberLazyListState()
    LaunchedEffect(selected) {
        val selectedIndex = destinations.indexOfFirst { it.destination == selected }.coerceAtLeast(0)
        navigationState.animateScrollToItem(selectedIndex)
    }
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .heightIn(min = if (compactLandscape) 52.dp else 64.dp)
            .background(Color(0xFF090A07))
            .testTag("mobile-bottom-navigation"),
        state = navigationState,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(destinations, key = { it.destination.name }) { entry ->
            val active = selected == entry.destination
            Column(
                modifier = Modifier
                    .width(if (compactLandscape) 56.dp else 66.dp)
                    .heightIn(min = 48.dp)
                    .testTag("mobile-bottom-nav-${entry.destination.name.lowercase(Locale.ROOT)}")
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) colors.gold.copy(alpha = .16f) else Color.Transparent)
                    .clickable(role = Role.Button) { onSelect(entry.destination) }
                    .padding(horizontal = 4.dp, vertical = if (compactLandscape) 7.dp else 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = entry.label,
                    tint = if (active) colors.goldBright else colors.textMuted,
                    modifier = Modifier.size(if (compactLandscape) 23.dp else 22.dp),
                )
                if (!compactLandscape) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = entry.label,
                        color = if (active) colors.text else colors.textMuted,
                        fontSize = 9.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
''',
)

classifier_test = "app/src/test/java/sa/hulksa/player/ui/adaptive/AdaptiveUiClassifierTest.kt"
text = Path(classifier_test).read_text(encoding="utf-8")
text = text.replace("HulkNavigationType.TOP_BAR", "HulkNavigationType.BOTTOM_BAR")
text = text.replace(
    '''    fun touchSuppressesFocusChromeButKeyboardRestoresIt() {
        assertFalse(shouldShowFocusHighlights(HulkDeviceClass.TABLET, HulkInputMode.TOUCH))
        assertTrue(shouldShowFocusHighlights(HulkDeviceClass.TABLET, HulkInputMode.KEYBOARD))
        assertTrue(shouldShowFocusHighlights(HulkDeviceClass.MOBILE, HulkInputMode.REMOTE))
    }
''',
    '''    fun televisionFocusChromeIsSeparatedFromPhoneAndTabletKeyboardFocus() {
        assertFalse(shouldShowFocusHighlights(HulkDeviceClass.TABLET, HulkInputMode.TOUCH))
        assertFalse(shouldShowFocusHighlights(HulkDeviceClass.TABLET, HulkInputMode.KEYBOARD))
        assertFalse(shouldShowFocusHighlights(HulkDeviceClass.MOBILE, HulkInputMode.REMOTE))
        assertTrue(shouldShowKeyboardFocusIndicator(HulkDeviceClass.TABLET, HulkInputMode.KEYBOARD))
        assertTrue(shouldShowKeyboardFocusIndicator(HulkDeviceClass.MOBILE, HulkInputMode.KEYBOARD))
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
        assertEquals(2, calculateAdaptiveGridColumns(360, 112, 12, maximumColumns = 6))
        assertEquals(4, calculateAdaptiveGridColumns(800, 148, 16, maximumColumns = 8))
        assertEquals(6, calculateAdaptiveGridColumns(1280, 168, 20, maximumColumns = 6))
    }
''',
)
if "HulkNavigationType.TOP_BAR" in text:
    raise SystemExit("A phone/tablet TOP_BAR expectation still remains")
if "physical4kTelevisionResolvesToNormalLogicalViewport" not in text:
    raise SystemExit("4K logical viewport coverage was not inserted")
Path(classifier_test).write_text(text, encoding="utf-8")

compose_test = Path(
    "app/src/androidTest/java/sa/hulksa/player/compatibilityv2/AdaptiveMainShellComposeTest.kt",
)
compose_test.write_text(
    '''package sa.hulksa.player.compatibilityv2

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
    }
}
''',
    encoding="utf-8",
)

for path, markers in {
    adaptive: ("BOTTOM_BAR", "shouldShowKeyboardFocusIndicator", "logicalViewportDp"),
    main_shell: ("mobile-bottom-navigation", "navigationBarsPadding", "adaptive-navigation-rail"),
    str(compose_test): ("phonePortraitUsesBottomNavigationWithoutContentOverlap",),
}.items():
    data = Path(path).read_text(encoding="utf-8")
    for marker in markers:
        if marker not in data:
            raise SystemExit(f"{path}: missing expected marker {marker}")
