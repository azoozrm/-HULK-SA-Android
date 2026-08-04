#!/usr/bin/env python3
from __future__ import annotations

import re
from pathlib import Path

MAIN = Path("app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt")
TEST = Path("app/src/androidTest/java/sa/hulksa/player/compatibilityv2/AdaptiveMainShellComposeTest.kt")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def regex_once(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return updated


def patch_main() -> None:
    text = MAIN.read_text(encoding="utf-8")

    text = replace_once(
        text,
        '''private val TV_PAGE_GUTTER = 8.dp
private val TV_LIVE_ACTION_INSET = 8.dp

@Composable
private fun resolvedPageHorizontalGutter(isTv: Boolean) =
    if (isTv) TV_PAGE_GUTTER else LocalAdaptiveUi.current.layoutPolicy.pageHorizontalPaddingDp.dp''',
        '''private val TV_PAGE_GUTTER = 8.dp
private val TV_LIVE_ACTION_INSET = 8.dp
private val MOBILE_PAGE_GUTTER = 18.dp
private val MOBILE_PAGE_TOP_PADDING = 18.dp

@Composable
private fun resolvedPageHorizontalGutter(isTv: Boolean) =
    if (isTv) {
        TV_PAGE_GUTTER
    } else {
        maxOf(MOBILE_PAGE_GUTTER, LocalAdaptiveUi.current.layoutPolicy.pageHorizontalPaddingDp.dp)
    }''',
        "mobile safe gutters",
    )

    mobile_navigation = '''@Composable
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
    val dockShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(dockShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1A1B14), Color(0xFF0D0E0A)),
                    ),
                )
                .border(1.dp, colors.gold.copy(alpha = .32f), dockShape)
                .testTag("mobile-bottom-navigation"),
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compactLandscape) 54.dp else 68.dp),
                state = navigationState,
                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(destinations, key = { it.destination.name }) { entry ->
                    val active = selected == entry.destination
                    var focused by remember { mutableStateOf(false) }
                    val keyboardFocused = focused && adaptiveUi.showKeyboardFocusIndicator
                    val itemShape = RoundedCornerShape(14.dp)
                    val selectionBorder = if (active || keyboardFocused) {
                        Modifier.border(
                            width = if (keyboardFocused) 2.dp else 1.dp,
                            color = colors.goldBright,
                            shape = itemShape,
                        )
                    } else {
                        Modifier
                    }
                    Column(
                        modifier = Modifier
                            .width(if (compactLandscape) 58.dp else 62.dp)
                            .height(if (compactLandscape) 46.dp else 56.dp)
                            .testTag("mobile-bottom-nav-${entry.destination.name.lowercase(Locale.ROOT)}")
                            .clip(itemShape)
                            .background(if (active) colors.gold.copy(alpha = .14f) else Color.Transparent)
                            .then(selectionBorder)
                            .onFocusChanged { focused = it.isFocused }
                            .clickable(role = Role.Button) { onSelect(entry.destination) }
                            .padding(horizontal = 4.dp, vertical = if (compactLandscape) 5.dp else 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (compactLandscape) 28.dp else 30.dp)
                                .clip(CircleShape)
                                .background(if (active) colors.gold else Color.White.copy(alpha = .06f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = entry.icon,
                                contentDescription = entry.label,
                                tint = if (active) Color.Black else colors.textMuted,
                                modifier = Modifier.size(if (compactLandscape) 19.dp else 18.dp),
                            )
                        }
                        if (!compactLandscape) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = entry.label,
                                color = if (active) colors.text else colors.textMuted,
                                fontSize = 9.sp,
                                lineHeight = 10.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationContent'''
    text = regex_once(
        text,
        r"@Composable\nprivate fun MobileBottomNavigation\(.*?\n\}\n\n@Composable\nprivate fun DestinationContent",
        mobile_navigation,
        "mobile navigation dock",
    )

    text = replace_once(
        text,
        ".height(if (isTv) 374.dp else 288.dp)",
        ".height(if (isTv) 374.dp else 340.dp)",
        "mobile hero height",
    )
    text = replace_once(
        text,
        '''Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 26.dp, vertical = 18.dp),''',
        '''Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = if (isTv) 26.dp else 20.dp, vertical = 18.dp)
                .testTag("home-hero-header"),''',
        "hero header safe inset",
    )
    text = replace_once(
        text,
        'Text("احدث اضافات HULK", color = colors.textMuted, fontSize = 11.sp)',
        'Text("احدث اضافات HULK", color = colors.textMuted, fontSize = 11.sp, modifier = Modifier.testTag("home-hero-section-label"))',
        "hero section tag",
    )
    text = replace_once(
        text,
        '''.align(Alignment.BottomStart)
                .fillMaxWidth(if (isTv) .58f else .86f)
                .padding(start = 27.dp, end = 27.dp, bottom = if (isTv) 38.dp else 24.dp),''',
        '''.align(Alignment.BottomStart)
                .fillMaxWidth(if (isTv) .58f else 1f)
                .padding(
                    start = if (isTv) 27.dp else 20.dp,
                    end = if (isTv) 27.dp else 20.dp,
                    bottom = if (isTv) 38.dp else 24.dp,
                ),''',
        "hero content safe inset",
    )
    text = replace_once(
        text,
        'Text("وصل حديثا", color = colors.goldBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)',
        'Text("وصل حديثا", color = colors.goldBright, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("home-hero-new-label"))',
        "hero new label tag",
    )
    text = replace_once(
        text,
        '''fontSize = if (isTv) 39.sp else 28.sp,
                lineHeight = if (isTv) 47.sp else 34.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,''',
        '''fontSize = if (isTv) 39.sp else 24.sp,
                lineHeight = if (isTv) 47.sp else 29.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("home-hero-headline"),''',
        "hero two-line headline",
    )

    text = replace_once(
        text,
        "vertical = if (isTv) TV_PAGE_GUTTER else 12.dp,",
        "vertical = if (isTv) TV_PAGE_GUTTER else MOBILE_PAGE_TOP_PADDING,",
        "poster catalog top inset",
    )
    text = replace_once(
        text,
        "vertical = if (isTv) TV_PAGE_GUTTER else 11.dp,",
        "vertical = if (isTv) TV_PAGE_GUTTER else MOBILE_PAGE_TOP_PADDING,",
        "live catalog top inset",
    )

    simple_padding = ".padding(resolvedPageHorizontalGutter(isTv))"
    replacement_padding = '''.padding(
                horizontal = resolvedPageHorizontalGutter(isTv),
                vertical = if (isTv) TV_PAGE_GUTTER else MOBILE_PAGE_TOP_PADDING,
            )'''
    count = text.count(simple_padding)
    if count != 3:
        raise SystemExit(f"favorites/search/download page padding: expected 3 anchors, found {count}")
    text = text.replace(simple_padding, replacement_padding)

    text = replace_once(
        text,
        "PaddingValues(horizontal = resolvedPageHorizontalGutter(false), vertical = 15.dp)",
        "PaddingValues(horizontal = resolvedPageHorizontalGutter(false), vertical = MOBILE_PAGE_TOP_PADDING)",
        "settings top inset",
    )

    title_calls = (
        (
            'PageTitle("قائمتي", "كل ما حفظته في مكان واحد", content.size, Icons.Rounded.Star)',
            'PageTitle("قائمتي", "كل ما حفظته في مكان واحد", content.size, Icons.Rounded.Star, isTv)',
        ),
        (
            'PageTitle("البحث", "القنوات والافلام والمسلسلات", results.size, Icons.Rounded.Search)',
            'PageTitle("البحث", "القنوات والافلام والمسلسلات", results.size, Icons.Rounded.Search, isTv)',
        ),
        (
            'PageTitle("التنزيلات", "ادارة كاملة للمشاهدة بدون انترنت", downloads.size, Icons.Rounded.Download)',
            'PageTitle("التنزيلات", "ادارة كاملة للمشاهدة بدون انترنت", downloads.size, Icons.Rounded.Download, isTv)',
        ),
        (
            'PageTitle("الحساب والاعدادات", "ادارة اشتراكك وتجربة المشاهدة", 0, Icons.Rounded.Settings)',
            'PageTitle("الحساب والاعدادات", "ادارة اشتراكك وتجربة المشاهدة", 0, Icons.Rounded.Settings, isTv)',
        ),
    )
    for old, new in title_calls:
        text = replace_once(text, old, new, f"page title call {old}")

    catalog_header = '''@Composable
private fun CatalogHeader(
    title: String,
    resultCount: Int,
    query: String,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    isTv: Boolean,
) {
    val colors = LocalHulkColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTv) 0.dp else 2.dp)
            .testTag("catalog-header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.width(if (isTv) 185.dp else 116.dp)) {
            Text(
                title,
                color = colors.text,
                fontSize = if (isTv) 27.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("catalog-header-title"),
            )
            Text("$resultCount عنصر", color = colors.textMuted, fontSize = 10.sp)
        }
        HulkTextField(query, onSearch, "ابحث في $title…", Modifier.weight(1f).widthIn(max = 630.dp))
        RoundAction(Icons.Rounded.Refresh, "تحديث", onRefresh)
    }
}

@Composable
private fun CategoryBar'''
    text = regex_once(
        text,
        r"@Composable\nprivate fun CatalogHeader\(.*?\n\}\n\n@Composable\nprivate fun CategoryBar",
        catalog_header,
        "catalog header",
    )

    page_title = '''@Composable
private fun PageTitle(title: String, subtitle: String, count: Int, icon: ImageVector, isTv: Boolean) {
    val colors = LocalHulkColors.current
    val iconContainerSize = if (isTv) 42.dp else 38.dp
    val iconSize = if (isTv) 21.dp else 19.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTv) 0.dp else 2.dp, vertical = if (isTv) 0.dp else 3.dp)
            .testTag("page-title"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(iconContainerSize)
                .clip(CircleShape)
                .background(colors.gold.copy(alpha = .12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, title, tint = colors.goldBright, modifier = Modifier.size(iconSize))
        }
        Spacer(Modifier.width(if (isTv) 11.dp else 10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = colors.text,
                fontSize = if (isTv) 25.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("page-title-text"),
            )
            Text(
                if (count > 0) "$subtitle  •  $count" else subtitle,
                color = colors.textMuted,
                fontSize = if (isTv) 11.sp else 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RoundAction'''
    text = regex_once(
        text,
        r"@Composable\nprivate fun PageTitle\(.*?\n\}\n\n@Composable\nprivate fun RoundAction",
        page_title,
        "shared page title",
    )

    MAIN.write_text(text, encoding="utf-8")


def patch_test() -> None:
    text = TEST.read_text(encoding="utf-8")
    text = replace_once(
        text,
        "private fun render(adaptive: AdaptiveUiState) {",
        "private fun render(adaptive: AdaptiveUiState, initialDestination: MainDestination = MainDestination.HOME) {",
        "test render signature",
    )
    text = replace_once(
        text,
        "var destination by remember { mutableStateOf(MainDestination.HOME) }",
        "var destination by remember { mutableStateOf(initialDestination) }",
        "test initial destination",
    )
    text = replace_once(
        text,
        '''        assertTrue("Bottom navigation overlaps the content viewport", content.bottom <= navigation.top + 1f)
        captureEvidence("phone-portrait-bottom-navigation")''',
        '''        assertTrue("Bottom navigation overlaps the content viewport", content.bottom <= navigation.top + 1f)
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val root = composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Bottom dock touches the left edge", navigation.left >= 9f * density)
        assertTrue("Bottom dock touches the right edge", root.right - navigation.right >= 9f * density)
        captureEvidence("phone-portrait-bottom-navigation")''',
        "bottom dock bounds assertion",
    )

    extra_tests = '''
    @Test
    fun phonePortraitCatalogHeaderKeepsSafeHorizontalInsets() {
        render(
            AdaptiveUiState(
                deviceClass = HulkDeviceClass.MOBILE,
                windowWidthClass = HulkWindowWidthClass.COMPACT,
                navigationType = HulkNavigationType.BOTTOM_BAR,
                inputMode = HulkInputMode.TOUCH,
                screenWidthDp = 360,
                screenHeightDp = 800,
            ),
            MainDestination.LIVE,
        )
        composeRule.onNodeWithTag("catalog-header").assertIsDisplayed()
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val root = composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val header = composeRule.onNodeWithTag("catalog-header").fetchSemanticsNode().boundsInRoot
        assertTrue("Catalog header touches the left edge", header.left >= 16f * density)
        assertTrue("Catalog header touches the right edge", root.right - header.right >= 16f * density)
        captureEvidence("phone-portrait-catalog-header")
    }

    @Test
    fun phonePortraitPageTitleKeepsSafeHorizontalInsets() {
        render(
            AdaptiveUiState(
                deviceClass = HulkDeviceClass.MOBILE,
                windowWidthClass = HulkWindowWidthClass.COMPACT,
                navigationType = HulkNavigationType.BOTTOM_BAR,
                inputMode = HulkInputMode.TOUCH,
                screenWidthDp = 360,
                screenHeightDp = 800,
            ),
            MainDestination.FAVORITES,
        )
        composeRule.onNodeWithTag("page-title").assertIsDisplayed()
        composeRule.onNodeWithTag("page-title-text").assertIsDisplayed()
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val root = composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val header = composeRule.onNodeWithTag("page-title").fetchSemanticsNode().boundsInRoot
        assertTrue("Page title touches the left edge", header.left >= 16f * density)
        assertTrue("Page title touches the right edge", root.right - header.right >= 16f * density)
        captureEvidence("phone-portrait-page-title")
    }
'''
    marker = "\n    @Test\n    fun tabletAndTelevisionUseRailInsteadOfPhoneBottomNavigation() {"
    text = replace_once(text, marker, extra_tests + marker, "adaptive test insertion")
    TEST.write_text(text, encoding="utf-8")


def main() -> None:
    patch_main()
    patch_test()
    print("Applied PR79 mobile layout polish")


if __name__ == "__main__":
    main()
