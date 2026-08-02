#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel: str, text: str) -> None:
    (ROOT / rel).write_text(text, encoding="utf-8")


def replace_once(rel: str, old: str, new: str) -> None:
    text = read(rel)
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{rel}: expected one match, found {count}: {old[:120]!r}")
    write(rel, text.replace(old, new, 1))


def replace_between(rel: str, start: str, end: str, replacement: str) -> None:
    text = read(rel)
    start_index = text.find(start)
    if start_index < 0:
        raise RuntimeError(f"{rel}: start marker not found: {start!r}")
    end_index = text.find(end, start_index + len(start))
    if end_index < 0:
        raise RuntimeError(f"{rel}: end marker not found: {end!r}")
    write(rel, text[:start_index] + replacement.rstrip() + "\n\n" + text[end_index:])


# Window policy: draw the app behind cutouts/system bars, but keep normal pages non-immersive.
policy = "app/src/main/java/sa/hulksa/player/ui/adaptive/WindowPresentationPolicy.kt"
replace_once(
    policy,
    "import android.view.WindowInsetsController\n",
    "import android.view.WindowInsetsController\nimport android.view.WindowManager\n",
)
replace_once(
    policy,
    """    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
""",
    """    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val attributes = window.attributes
        attributes.layoutInDisplayCutoutMode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        window.attributes = attributes
    }
    window.decorView.setBackgroundColor(Color.rgb(17, 17, 8))

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
""",
)

# Central safe drawing: the backdrop reaches every pixel; interactive content avoids status/nav/cutout.
app = "app/src/main/java/sa/hulksa/player/ui/HulkApp.kt"
replace_once(
    app,
    """import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
""",
    """import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
""",
)
replace_once(
    app,
    """    val colors = LocalHulkColors.current
    val applyNavigationSafeArea =
        !isTv && state.screen != HulkScreen.PLAYER && state.screen != HulkScreen.LOGIN
""",
    """    val colors = LocalHulkColors.current
    val windowBackground =
        if (state.screen == HulkScreen.LOGIN || state.screen == HulkScreen.PLAYER) {
            colors.background
        } else {
            colors.surface
        }
    val applySafeDrawingInsets =
        !isTv && state.screen != HulkScreen.PLAYER && state.screen != HulkScreen.LOGIN
""",
)
replace_once(app, ".background(colors.background)\n                .trackAdaptiveInput", ".background(windowBackground)\n                .trackAdaptiveInput")
replace_once(
    app,
    """                        if (applyNavigationSafeArea) {
                            Modifier.navigationBarsPadding()
""",
    """                        if (applySafeDrawingInsets) {
                            Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
""",
)

# Theme startup window must not flash opaque black bars before Compose applies its policy.
themes = "app/src/main/res/values/themes.xml"
replace_once(
    themes,
    """        <item name="android:windowLightStatusBar">false</item>
        <item name="android:statusBarColor">@color/hulk_black</item>
        <item name="android:navigationBarColor">@color/hulk_black</item>
        <item name="android:windowBackground">@color/hulk_black</item>
""",
    """        <item name="android:windowLightStatusBar">false</item>
        <item name="android:windowLightNavigationBar">false</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowDrawsSystemBarBackgrounds">true</item>
        <item name="android:windowLayoutInDisplayCutoutMode">shortEdges</item>
        <item name="android:enforceStatusBarContrast">false</item>
        <item name="android:enforceNavigationBarContrast">false</item>
        <item name="android:windowBackground">@color/hulk_surface</item>
""",
)
colors = "app/src/main/res/values/colors.xml"
replace_once(colors, "    <color name=" + '"hulk_black"' + ">#050505</color>\n", "    <color name=\"hulk_black\">#050505</color>\n    <color name=\"hulk_surface\">#111108</color>\n")

# Login: compact short-landscape composition and a logo that actually respects its parent bounds.
login = "app/src/main/java/sa/hulksa/player/ui/screens/LoginScreen.kt"
replace_once(
    login,
    """        val compactHeight = !isTv && maxHeight < 600.dp
        val wide = maxWidth >= wideThreshold && !compactHeight
""",
    """        val compactHeight = !isTv && maxHeight < 600.dp
        val compactMobileLandscape = !isTv && maxWidth > maxHeight && maxHeight < 520.dp
        val wide = maxWidth >= wideThreshold && (!compactHeight || compactMobileLandscape)
""",
)
replace_once(
    login,
    """            val horizontalPadding = when {
                isTv && maxWidth >= 1440.dp -> 58.dp
""",
    """            val horizontalPadding = when {
                compactMobileLandscape -> 12.dp
                isTv && maxWidth >= 1440.dp -> 58.dp
""",
)
replace_once(
    login,
    """            val verticalPadding = when {
                compactWideTv -> 8.dp
""",
    """            val verticalPadding = when {
                compactMobileLandscape -> 4.dp
                compactWideTv -> 8.dp
""",
)
replace_once(
    login,
    """            val itemSpacing = when {
                compactWideTv -> 20.dp
""",
    """            val itemSpacing = when {
                compactMobileLandscape -> 12.dp
                compactWideTv -> 20.dp
""",
)
replace_once(login, ".heightIn(max = 680.dp),", ".heightIn(max = if (compactMobileLandscape) 390.dp else 680.dp),")
replace_once(
    login,
    """                        modifier = Modifier.width(
                            when {
                                compactWideTv -> 440.dp
""",
    """                        compact = compactMobileLandscape,
                        modifier = Modifier.width(
                            when {
                                compactMobileLandscape -> 520.dp
                                compactWideTv -> 440.dp
""",
)
replace_once(login, ".height(if (compactWideTv) 280.dp else 330.dp)", ".height(if (compactMobileLandscape) 250.dp else if (compactWideTv) 280.dp else 330.dp)")
replace_once(
    login,
    """                    LoginBrand(
                        isTv = isTv,
                        modifier = Modifier
""",
    """                    LoginBrand(
                        isTv = isTv,
                        compact = compactMobileLandscape,
                        modifier = Modifier
""",
)
replace_once(
    login,
    """                LoginBrand(
                    isTv = false,
                    modifier = Modifier.height(
""",
    """                LoginBrand(
                    isTv = false,
                    compact = compactHeight,
                    modifier = Modifier.height(
""",
)
replace_once(
    login,
    """                    initialFocusRequester = if (isTv) tvInitialFocusRequester else null,
                    modifier = Modifier
                        .fillMaxWidth()
""",
    """                    initialFocusRequester = if (isTv) tvInitialFocusRequester else null,
                    compact = compactHeight,
                    modifier = Modifier
                        .fillMaxWidth()
""",
)
replace_once(
    login,
    """private fun LoginBrand(
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
""",
    """private fun LoginBrand(
    isTv: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val haloSize = when {
        isTv -> 390.dp
        compact -> 112.dp
        else -> 196.dp
    }
    val logoSize = when {
        isTv -> 306.dp
        compact -> 92.dp
        else -> 160.dp
    }
""",
)
replace_once(login, ".size(if (isTv) 390.dp else 196.dp)", ".size(haloSize)")
replace_once(login, ".size(if (isTv) 306.dp else 160.dp)", ".size(logoSize)")
replace_once(login, "val logoShape = RoundedCornerShape(if (isTv) 32.dp else 22.dp)", "val logoShape = RoundedCornerShape(if (isTv) 32.dp else if (compact) 16.dp else 22.dp)")
replace_once(login, ".padding(if (isTv) 10.dp else 6.dp),", ".padding(if (isTv) 10.dp else if (compact) 4.dp else 6.dp),")
replace_once(
    login,
    """    initialFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val panelShape = RoundedCornerShape(26.dp)
""",
    """    initialFocusRequester: FocusRequester?,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val panelShape = RoundedCornerShape(if (compact) 18.dp else 26.dp)
    val panelHorizontalPadding = if (compact) 18.dp else 28.dp
    val panelVerticalPadding = if (compact) 8.dp else 22.dp
""",
)
replace_once(login, ".padding(horizontal = 28.dp, vertical = 22.dp),", ".padding(horizontal = panelHorizontalPadding, vertical = panelVerticalPadding),")
replace_once(login, "fontSize = 31.sp,", "fontSize = if (compact) 22.sp else 31.sp,")
replace_once(login, "fontSize = 13.sp,", "fontSize = if (compact) 10.sp else 13.sp,")
replace_once(login, "Spacer(Modifier.height(4.dp))\n        Text(\n            text = \"ادخل بيانات الاشتراك الخاص بك\"", "Spacer(Modifier.height(if (compact) 1.dp else 4.dp))\n        Text(\n            text = \"ادخل بيانات الاشتراك الخاص بك\"")
replace_once(login, "Spacer(Modifier.height(7.dp))", "Spacer(Modifier.height(if (compact) 3.dp else 7.dp))")
replace_once(login, "Spacer(Modifier.height(18.dp))", "Spacer(Modifier.height(if (compact) 6.dp else 18.dp))")
replace_once(login, ".heightIn(min = 55.dp),", ".heightIn(min = if (compact) 42.dp else 55.dp),")
replace_once(login, "Spacer(Modifier.height(10.dp))", "Spacer(Modifier.height(if (compact) 6.dp else 10.dp))")
replace_once(login, ".fillMaxWidth()\n                .heightIn(min = 55.dp),", ".fillMaxWidth()\n                .heightIn(min = if (compact) 42.dp else 55.dp),")
replace_once(login, "Spacer(Modifier.height(8.dp))\n\n        LoginOption(", "Spacer(Modifier.height(if (compact) 4.dp else 8.dp))\n\n        LoginOption(")
replace_once(login, "            onFocused = onNonTextFocus,\n        )\n        LoginOption(", "            onFocused = onNonTextFocus,\n            compact = compact,\n        )\n        LoginOption(")
replace_once(login, "            onFocused = onNonTextFocus,\n        )\n\n        if (errorMessage != null)", "            onFocused = onNonTextFocus,\n            compact = compact,\n        )\n\n        if (errorMessage != null)")
replace_once(login, "Spacer(Modifier.height(13.dp))", "Spacer(Modifier.height(if (compact) 6.dp else 13.dp))")
replace_once(login, ".heightIn(min = 52.dp),", ".heightIn(min = if (compact) 40.dp else 52.dp),")
replace_once(login, "Spacer(Modifier.height(8.dp))\n        FocusButton(", "Spacer(Modifier.height(if (compact) 4.dp else 8.dp))\n        FocusButton(")
replace_once(login, ".fillMaxWidth()\n                .heightIn(min = 52.dp),", ".fillMaxWidth()\n                .heightIn(min = if (compact) 40.dp else 52.dp),")
replace_once(
    login,
    """        Spacer(Modifier.height(4.dp))
        Text(
            text = "hulksa.com",
            color = colors.goldBright.copy(alpha = .86f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onOpenWebsite)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
""",
    """        if (!compact) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "hulksa.com",
                color = colors.goldBright.copy(alpha = .86f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClick = onOpenWebsite)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
""",
)
replace_once(
    login,
    """private fun LoginOption(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
) {
""",
    """private fun LoginOption(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    compact: Boolean = false,
) {
""",
)
replace_once(login, ".heightIn(min = 48.dp)\n            .clip", ".heightIn(min = if (compact) 36.dp else 48.dp)\n            .clip")

# Main shell: use one scroll surface on short landscape and reserve all safe drawing insets centrally.
shell = "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
replace_once(shell, "import androidx.compose.foundation.layout.statusBarsPadding\n", "")
replace_once(shell, "import androidx.compose.foundation.lazy.grid.GridCells\n", "import androidx.compose.foundation.lazy.grid.GridCells\nimport androidx.compose.foundation.lazy.grid.GridItemSpan\n")
replace_once(shell, "    Box(Modifier.fillMaxSize().background(colors.background)) {", "    Box(Modifier.fillMaxSize().background(colors.surface)) {")

mobile_nav = '''@Composable
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
'''
replace_between(shell, "@Composable\nprivate fun MobileNavigation", "@Composable\nprivate fun DestinationContent", mobile_nav)

poster = '''@Composable
private fun PosterCatalogScreen(
    title: String,
    type: ContentType,
    destination: MainDestination,
    state: HulkUiState,
    isTv: Boolean,
    navigationMemory: NavigationMemoryStore,
    isFavorite: (ContentItem) -> Boolean,
    onSelectCategory: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onOpenHistory: (HistoryEntry) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onRefresh: () -> Unit,
) {
    val catalog = state.catalogs[type]
    val ordered = remember(catalog) { newest(catalog?.items.orEmpty()) }
    val visible = remember(ordered, state.selectedCategoryId, state.searchQuery, state.favorites) {
        ordered.filter { item ->
            categoryMatches(item, state.selectedCategoryId, isFavorite) &&
                item.matchesSearch(state.searchQuery)
        }
    }
    val continueWatching = remember(state.history, state.searchQuery, type) {
        val kind = if (type == ContentType.MOVIE) "movie" else "series"
        state.history.filter { entry ->
            entry.streamKind == kind && entry.isResumable() &&
                (state.searchQuery.isBlank() || entry.title.contains(state.searchQuery.trim(), ignoreCase = true))
        }
    }
    val showingContinue = state.selectedCategoryId == CONTINUE_CATEGORY_ID
    val resultCount = if (showingContinue) continueWatching.size else visible.size
    val adaptiveUi = LocalAdaptiveUi.current
    val compactLandscape =
        !isTv && adaptiveUi.screenWidthDp > adaptiveUi.screenHeightDp && adaptiveUi.screenHeightDp < 520

    if (compactLandscape) {
        CompactLandscapePosterCatalog(
            title = title,
            type = type,
            destination = destination,
            state = state,
            navigationMemory = navigationMemory,
            isFavorite = isFavorite,
            onSelectCategory = onSelectCategory,
            onSearch = onSearch,
            onOpen = onOpen,
            onOpenHistory = onOpenHistory,
            onToggleFavorite = onToggleFavorite,
            onRefresh = onRefresh,
            categories = catalog?.categories.orEmpty(),
            ordered = ordered,
            visible = visible,
            continueWatching = continueWatching,
            showingContinue = showingContinue,
            resultCount = resultCount,
            catalogAvailable = catalog != null,
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(
                horizontal = if (isTv) TV_PAGE_GUTTER else 13.dp,
                vertical = if (isTv) TV_PAGE_GUTTER else 12.dp,
            ),
    ) {
        CatalogHeader(title, resultCount, state.searchQuery, onSearch, onRefresh, isTv)
        if (state.errorMessage != null) { Spacer(Modifier.height(10.dp)); ErrorNotice(state.errorMessage) }
        Spacer(Modifier.height(11.dp))
        ReorderableCatalogCategoryBar(type, catalog?.categories.orEmpty(), ordered, state.selectedCategoryId, onSelectCategory, isTv)
        if (isTv) CatalogInteractionHints(true)
        Spacer(Modifier.height(if (isTv) 9.dp else 4.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (showingContinue && continueWatching.isNotEmpty()) {
                HistoryGrid(continueWatching, isTv, destination, navigationMemory, onOpenHistory)
            } else if (showingContinue) {
                EmptyState("لا توجد مشاهدة غير مكتملة في $title")
            } else if (catalog == null && type in state.loadingTypes) {
                LoadingRing(label = "جاري تحميل $title…", modifier = Modifier.align(Alignment.Center))
            } else if (visible.isEmpty()) {
                EmptyState("لا توجد نتائج مطابقة")
            } else {
                ContentGrid(
                    visible, isTv, destination, navigationMemory, isFavorite, onOpen, onToggleFavorite,
                    restoreFocusedCard = state.searchQuery.isBlank(),
                )
            }
        }
    }
}

@Composable
private fun CompactLandscapePosterCatalog(
    title: String,
    type: ContentType,
    destination: MainDestination,
    state: HulkUiState,
    navigationMemory: NavigationMemoryStore,
    isFavorite: (ContentItem) -> Boolean,
    onSelectCategory: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onOpenHistory: (HistoryEntry) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onRefresh: () -> Unit,
    categories: List<Category>,
    ordered: List<ContentItem>,
    visible: List<ContentItem>,
    continueWatching: List<HistoryEntry>,
    showingContinue: Boolean,
    resultCount: Int,
    catalogAvailable: Boolean,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (showingContinue) 180.dp else 105.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, top = 7.dp, end = 12.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item(key = "catalog-header", span = { GridItemSpan(maxLineSpan) }) {
            CatalogHeader(title, resultCount, state.searchQuery, onSearch, onRefresh, false)
        }
        if (state.errorMessage != null) {
            item(key = "catalog-error", span = { GridItemSpan(maxLineSpan) }) {
                ErrorNotice(state.errorMessage)
            }
        }
        item(key = "catalog-categories", span = { GridItemSpan(maxLineSpan) }) {
            ReorderableCatalogCategoryBar(
                type,
                categories,
                ordered,
                state.selectedCategoryId,
                onSelectCategory,
                false,
            )
        }
        when {
            showingContinue && continueWatching.isNotEmpty() -> {
                itemsIndexed(continueWatching, key = { _, entry -> entry.key }) { index, entry ->
                    HistoryCard(
                        entry = entry,
                        onClick = { onOpenHistory(entry) },
                        modifier = Modifier.fillMaxWidth(),
                        onFocused = { navigationMemory.save(destination, entry.key, index) },
                    )
                }
            }
            showingContinue -> {
                item(key = "catalog-empty-history", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState("لا توجد مشاهدة غير مكتملة في $title")
                }
            }
            !catalogAvailable && type in state.loadingTypes -> {
                item(key = "catalog-loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        LoadingRing(label = "جاري تحميل $title…")
                    }
                }
            }
            visible.isEmpty() -> {
                item(key = "catalog-empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState("لا توجد نتائج مطابقة")
                }
            }
            else -> {
                itemsIndexed(visible, key = { _, item -> "${item.type}:${item.id}" }) { index, item ->
                    val key = "${item.type}:${item.id}"
                    CompactPosterCard(
                        item = item,
                        isFavorite = isFavorite(item),
                        onClick = { onOpen(item) },
                        modifier = Modifier.fillMaxWidth(),
                        onLongClick = { onToggleFavorite(item) },
                        onFocused = { navigationMemory.save(destination, key, index) },
                    )
                }
            }
        }
    }
}
'''
replace_between(shell, "@Composable\nprivate fun PosterCatalogScreen", "@Composable\nprivate fun LiveCatalogScreen", poster)

live = '''@Composable
private fun LiveCatalogScreen(
    state: HulkUiState,
    isTv: Boolean,
    navigationMemory: NavigationMemoryStore,
    isFavorite: (ContentItem) -> Boolean,
    onSelectCategory: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onRefresh: () -> Unit,
) {
    val catalog = state.catalogs[ContentType.LIVE]
    val visible = remember(catalog, state.selectedCategoryId, state.searchQuery, state.favorites) {
        catalog?.items.orEmpty().filter { item ->
            categoryMatches(item, state.selectedCategoryId, isFavorite) &&
                item.matchesSearch(state.searchQuery)
        }
    }
    val adaptiveUi = LocalAdaptiveUi.current
    val compactLandscape =
        !isTv && adaptiveUi.screenWidthDp > adaptiveUi.screenHeightDp && adaptiveUi.screenHeightDp < 520

    if (compactLandscape) {
        CompactLandscapeLiveCatalog(
            state = state,
            navigationMemory = navigationMemory,
            isFavorite = isFavorite,
            onSelectCategory = onSelectCategory,
            onSearch = onSearch,
            onOpen = onOpen,
            onToggleFavorite = onToggleFavorite,
            onRefresh = onRefresh,
            categories = catalog?.categories.orEmpty(),
            allItems = catalog?.items.orEmpty(),
            visible = visible,
            catalogAvailable = catalog != null,
        )
        return
    }

    val remembered = navigationMemory.position(MainDestination.LIVE)
    val rememberedIndex = remembered.itemIndex.coerceIn(0, visible.lastIndex.coerceAtLeast(0))
    var preview by remember(catalog, state.selectedCategoryId) { mutableStateOf<ContentItem?>(null) }
    val channelRequester = remember { FocusRequester() }
    val playRequester = remember { FocusRequester() }
    val favoriteRequester = remember { FocusRequester() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = rememberedIndex)
    LaunchedEffect(listState, visible) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            visible.getOrNull(index)?.let { navigationMemory.save(MainDestination.LIVE, "${it.type}:${it.id}", index) }
        }
    }
    LaunchedEffect(visible) {
        if (preview == null || preview !in visible) {
            preview = visible.firstOrNull { "${it.type}:${it.id}" == remembered.itemKey } ?: visible.getOrNull(rememberedIndex) ?: visible.firstOrNull()
        }
        if (state.searchQuery.isBlank() && remembered.itemKey.isNotBlank() && visible.isNotEmpty()) {
            listState.scrollToItem(rememberedIndex)
            delay(180)
            runCatching { channelRequester.requestFocus() }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(
                horizontal = if (isTv) TV_PAGE_GUTTER else 12.dp,
                vertical = if (isTv) TV_PAGE_GUTTER else 11.dp,
            ),
    ) {
        CatalogHeader("البث المباشر", visible.size, state.searchQuery, onSearch, onRefresh, isTv)
        if (state.errorMessage != null) { Spacer(Modifier.height(9.dp)); ErrorNotice(state.errorMessage) }
        Spacer(Modifier.height(10.dp))
        ReorderableLiveCategoryBar(catalog?.categories.orEmpty(), catalog?.items.orEmpty(), state.selectedCategoryId, onSelectCategory)
        if (isTv) LiveInteractionHints(true)
        Spacer(Modifier.height(if (isTv) 8.dp else 4.dp))
        if (catalog == null && ContentType.LIVE in state.loadingTypes) {
            LoadingRing(label = "جاري تحميل القنوات…", modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 90.dp))
        } else if (visible.isEmpty()) {
            EmptyState("لا توجد قنوات مطابقة")
        } else if (isTv) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(
                    modifier = Modifier.width(408.dp).fillMaxHeight().clip(RoundedCornerShape(18.dp))
                        .background(Color(0xA30D0E0B)).padding(9.dp),
                ) {
                    Text("القنوات", color = LocalHulkColors.current.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp))
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        itemsIndexed(visible, key = { _, channel -> channel.id }) { index, channel ->
                            val key = "${channel.type}:${channel.id}"
                            val restore = key == remembered.itemKey || (remembered.itemKey.isBlank() && index == rememberedIndex)
                            ChannelListItem(
                                item = channel,
                                selected = preview?.id == channel.id,
                                onFocused = {
                                    preview = channel
                                    navigationMemory.save(MainDestination.LIVE, key, index)
                                },
                                onClick = { onOpen(channel) },
                                modifier = Modifier.restoreFocus(restore, channelRequester).focusProperties {
                                    left = playRequester
                                },
                                isFavorite = isFavorite(channel),
                                onLongClick = { onToggleFavorite(channel) },
                            )
                        }
                    }
                }
                LiveStage(
                    item = preview,
                    isFavorite = preview?.let(isFavorite) == true,
                    channelRequester = channelRequester,
                    playRequester = playRequester,
                    favoriteRequester = favoriteRequester,
                    onWatch = { preview?.let(onOpen) },
                    onToggleFavorite = { preview?.let(onToggleFavorite) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        } else {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(visible, key = { _, channel -> channel.id }) { index, channel ->
                    ChannelListItem(
                        item = channel,
                        selected = false,
                        onFocused = { navigationMemory.save(MainDestination.LIVE, "${channel.type}:${channel.id}", index) },
                        onClick = { onOpen(channel) },
                        isFavorite = isFavorite(channel),
                        onLongClick = { onToggleFavorite(channel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactLandscapeLiveCatalog(
    state: HulkUiState,
    navigationMemory: NavigationMemoryStore,
    isFavorite: (ContentItem) -> Boolean,
    onSelectCategory: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onRefresh: () -> Unit,
    categories: List<Category>,
    allItems: List<ContentItem>,
    visible: List<ContentItem>,
    catalogAvailable: Boolean,
) {
    val remembered = navigationMemory.position(MainDestination.LIVE)
    val rememberedIndex = remembered.itemIndex.coerceIn(0, visible.lastIndex.coerceAtLeast(0))
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, top = 7.dp, end = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        item(key = "live-header") {
            CatalogHeader("البث المباشر", visible.size, state.searchQuery, onSearch, onRefresh, false)
        }
        if (state.errorMessage != null) {
            item(key = "live-error") { ErrorNotice(state.errorMessage) }
        }
        item(key = "live-categories") {
            ReorderableLiveCategoryBar(categories, allItems, state.selectedCategoryId, onSelectCategory)
        }
        when {
            !catalogAvailable && ContentType.LIVE in state.loadingTypes -> {
                item(key = "live-loading") {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        LoadingRing(label = "جاري تحميل القنوات…")
                    }
                }
            }
            visible.isEmpty() -> item(key = "live-empty") { EmptyState("لا توجد قنوات مطابقة") }
            else -> itemsIndexed(visible, key = { _, channel -> channel.id }) { index, channel ->
                ChannelListItem(
                    item = channel,
                    selected = false,
                    onFocused = {
                        navigationMemory.save(MainDestination.LIVE, "${channel.type}:${channel.id}", index)
                    },
                    onClick = { onOpen(channel) },
                    isFavorite = isFavorite(channel),
                    onLongClick = { onToggleFavorite(channel) },
                )
            }
        }
    }
    LaunchedEffect(visible, remembered.itemKey) {
        if (visible.isNotEmpty() && remembered.itemKey.isNotBlank()) {
            listState.scrollToItem(rememberedIndex + 2)
        }
    }
}
'''
replace_between(shell, "@Composable\nprivate fun LiveCatalogScreen", "@Composable\nprivate fun LiveStage", live)

# Static contracts: fail if cutout/safe drawing or single-scroll landscape wiring regresses.
test_file = "quality/compatibility-v2/tests/test_mobile_window_contract.py"
write(
    test_file,
    '''import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[3]


class MobileWindowContractTest(unittest.TestCase):
    def test_mobile_navigation_uses_central_safe_drawing_insets(self) -> None:
        shell = (REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt").read_text(encoding="utf-8")
        mobile_navigation = shell.split("private fun MobileNavigation", 1)[1].split("private fun DestinationContent", 1)[0]
        self.assertNotIn("statusBarsPadding", mobile_navigation)
        self.assertNotIn("navigationBarsPadding", mobile_navigation)
        self.assertIn("compactLandscape", mobile_navigation)

    def test_mobile_root_fills_window_and_reserves_complete_safe_drawing(self) -> None:
        app = (REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/HulkApp.kt").read_text(encoding="utf-8")
        self.assertIn(".fillMaxSize()\n                .background(windowBackground)", app)
        self.assertIn("Modifier.windowInsetsPadding(WindowInsets.safeDrawing)", app)
        self.assertIn("state.screen != HulkScreen.PLAYER", app)
        self.assertIn("state.screen != HulkScreen.LOGIN", app)

    def test_window_policy_draws_behind_display_cutout_without_immersive_normal_pages(self) -> None:
        policy = (REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/adaptive/WindowPresentationPolicy.kt").read_text(encoding="utf-8")
        theme = (REPO_ROOT / "app/src/main/res/values/themes.xml").read_text(encoding="utf-8")
        self.assertIn("LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS", policy)
        self.assertIn("LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES", policy)
        self.assertIn("controller.show(WindowInsets.Type.systemBars())", policy)
        self.assertIn("window.navigationBarColor = Color.TRANSPARENT", policy)
        self.assertIn("window.isNavigationBarContrastEnforced = false", policy)
        self.assertIn("android:windowLayoutInDisplayCutoutMode\">shortEdges", theme)
        self.assertIn("android:navigationBarColor\">@android:color/transparent", theme)

    def test_short_landscape_login_has_real_compact_dimensions(self) -> None:
        login = (REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/LoginScreen.kt").read_text(encoding="utf-8")
        self.assertIn("compactMobileLandscape", login)
        self.assertIn("compact -> 112.dp", login)
        self.assertIn("compact -> 92.dp", login)
        self.assertIn("min = if (compact) 42.dp else 55.dp", login)
        self.assertIn("if (!compact)", login)

    def test_short_landscape_catalogs_use_one_vertical_scroll_surface(self) -> None:
        shell = (REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt").read_text(encoding="utf-8")
        self.assertIn("private fun CompactLandscapePosterCatalog", shell)
        self.assertIn("item(key = \"catalog-header\", span = { GridItemSpan(maxLineSpan) })", shell)
        self.assertIn("item(key = \"catalog-categories\", span = { GridItemSpan(maxLineSpan) })", shell)
        self.assertIn("private fun CompactLandscapeLiveCatalog", shell)
        live = shell.split("private fun CompactLandscapeLiveCatalog", 1)[1].split("private fun LiveStage", 1)[0]
        self.assertIn("LazyColumn(", live)
        self.assertIn("item(key = \"live-header\")", live)
        self.assertIn("item(key = \"live-categories\")", live)

    def test_mobile_player_restores_real_pre_player_orientation(self) -> None:
        app = (REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/HulkApp.kt").read_text(encoding="utf-8")
        policy = (REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/adaptive/WindowPresentationPolicy.kt").read_text(encoding="utf-8")
        self.assertIn("ApplyAdaptiveWindowPresentation(", app)
        self.assertIn("isPlayer = state.screen == HulkScreen.PLAYER", app)
        self.assertIn("prePlayerOrientation = activity.resources.configuration.orientation", policy)
        self.assertIn("restoreOrientationRequest(prePlayerOrientation)", policy)
        self.assertIn("ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT", policy)
        self.assertIn("controller.hide(WindowInsets.Type.systemBars())", policy)

    def test_runtime_suite_checks_safe_drawing_and_cutout(self) -> None:
        instrumentation = (REPO_ROOT / "app/src/androidTest/java/sa/hulksa/player/compatibilityv2/CompatibilityV2InstrumentationTest.kt").read_text(encoding="utf-8")
        self.assertIn("WindowInsetsCompat.Type.displayCutout()", instrumentation)
        self.assertIn("safeContentBounds", instrumentation)
        self.assertIn("layoutInDisplayCutoutMode", instrumentation)
        self.assertIn("immersive_cling_title", instrumentation)


if __name__ == "__main__":
    unittest.main()
''',
)

# Runtime safe-area probe must include status bars and display cutouts, not navigation only.
instrumentation = "app/src/androidTest/java/sa/hulksa/player/compatibilityv2/CompatibilityV2InstrumentationTest.kt"
replace_once(instrumentation, "import android.graphics.Rect\n", "import android.graphics.Rect\nimport android.os.Build\n")
replace_once(instrumentation, "import android.view.KeyEvent\n", "import android.view.KeyEvent\nimport android.view.WindowManager\n")
replace_once(
    instrumentation,
    """                val navigationInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                safeContentBounds = Rect(
                    navigationInsets.left,
                    0,
                    activity.window.decorView.width - navigationInsets.right,
                    activity.window.decorView.height - navigationInsets.bottom,
                )
""",
    """                val safeInsets = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
                safeContentBounds = Rect(
                    safeInsets.left,
                    safeInsets.top,
                    activity.window.decorView.width - safeInsets.right,
                    activity.window.decorView.height - safeInsets.bottom,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    assertTrue(
                        "Window must draw behind short-edge display cutouts",
                        activity.window.attributes.layoutInDisplayCutoutMode ==
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES ||
                            activity.window.attributes.layoutInDisplayCutoutMode ==
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
                    )
                }
""",
)

print("Applied guarded physical-phone landscape, cutout, and single-scroll fixes.")
