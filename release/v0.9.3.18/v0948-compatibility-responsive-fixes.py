#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1]).resolve()


def load(relative: str) -> tuple[Path, str]:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"missing source file: {relative}")
    return path, path.read_text(encoding="utf-8")


def save(path: Path, text: str) -> None:
    path.write_text(text, encoding="utf-8")


def replace_version(relative: str, pattern: str, replacement: str, expected: str, label: str) -> None:
    path, text = load(relative)
    if expected in text:
        print(f"PASS: {label} already applied")
        return
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.MULTILINE)
    if count != 1:
        raise SystemExit(f"missing required version marker: {label}")
    save(path, updated)
    print(f"PASS: {label}")


def ensure_import(text: str, import_line: str) -> tuple[str, bool]:
    if import_line in text:
        return text, False
    lines = text.splitlines(keepends=True)
    indexes = [index for index, line in enumerate(lines) if line.startswith("import ")]
    if not indexes:
        raise SystemExit(f"missing import section for {import_line.strip()}")
    lines.insert(indexes[-1] + 1, import_line)
    return "".join(lines), True


def function_bounds(text: str, name: str) -> tuple[int, int] | None:
    match = re.search(rf"(?:private\s+)?fun\s+{re.escape(name)}\s*\(", text)
    if not match:
        return None
    start = match.start()
    brace = text.find("{", match.end())
    if brace < 0:
        return None
    depth = 0
    for index in range(brace, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return start, index + 1
    return None


def harden_adaptive_classifier() -> None:
    relative = "app/src/main/java/sa/hulksa/player/ui/adaptive/AdaptiveUi.kt"
    path, text = load(relative)

    device_pattern = re.compile(
        r"fun\s+classifyDeviceClass\s*\([^)]*\)\s*:\s*HulkDeviceClass\s*=\s*when\s*\{.*?\n\}",
        re.DOTALL,
    )
    device_replacement = """fun classifyDeviceClass(
    isTelevisionDevice: Boolean,
    smallestWidthDp: Int,
    widthDp: Int,
): HulkDeviceClass = when {
    isTelevisionDevice -> HulkDeviceClass.TELEVISION
    smallestWidthDp >= 600 -> HulkDeviceClass.TABLET
    else -> HulkDeviceClass.MOBILE
}"""
    if device_replacement not in text:
        text, count = device_pattern.subn(device_replacement, text, count=1)
        if count != 1:
            raise SystemExit("missing adaptive device classifier")
        print(f"PASS: adaptive device classifier updates={count}")

    navigation_pattern = re.compile(
        r"fun\s+selectNavigationType\s*\([^)]*\)\s*:\s*HulkNavigationType\s*=\s*when\s*\{.*?\n\}",
        re.DOTALL,
    )
    navigation_replacement = """fun selectNavigationType(
    deviceClass: HulkDeviceClass,
    windowWidthClass: HulkWindowWidthClass,
): HulkNavigationType = when {
    deviceClass == HulkDeviceClass.TELEVISION -> HulkNavigationType.RAIL
    deviceClass == HulkDeviceClass.TABLET && windowWidthClass == HulkWindowWidthClass.EXPANDED -> HulkNavigationType.RAIL
    else -> HulkNavigationType.TOP_BAR
}"""
    if navigation_replacement not in text:
        text, count = navigation_pattern.subn(navigation_replacement, text, count=1)
        if count != 1:
            raise SystemExit("missing adaptive navigation classifier")
        print(f"PASS: adaptive navigation classifier updates={count}")

    save(path, text)

    test_relative = "app/src/test/java/sa/hulksa/player/ui/adaptive/AdaptiveUiClassifierTest.kt"
    test_path, tests = load(test_relative)
    test_name = "fun landscapePhoneDoesNotBecomeTabletOrRail()"
    if test_name not in tests:
        test_case = '''
    @Test
    fun landscapePhoneDoesNotBecomeTabletOrRail() {
        val device = classifyDeviceClass(
            isTelevisionDevice = false,
            smallestWidthDp = 411,
            widthDp = 891,
        )
        val window = classifyWindowWidth(891)

        assertEquals(HulkDeviceClass.MOBILE, device)
        assertEquals(HulkWindowWidthClass.EXPANDED, window)
        assertEquals(HulkNavigationType.TOP_BAR, selectNavigationType(device, window))
    }
'''
        marker = "    @Test\n    fun portraitTabletUsesTabletLayoutWithoutTelevisionSizing()"
        if marker not in tests:
            raise SystemExit("missing adaptive classifier regression test marker")
        tests = tests.replace(marker, test_case + "\n" + marker, 1)
        save(test_path, tests)
        print("PASS: landscape phone classifier regression test added")


def harden_main_shell() -> None:
    relative = "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
    path, text = load(relative)

    for import_line in (
        "import androidx.compose.foundation.layout.navigationBarsPadding\n",
        "import androidx.compose.ui.focus.FocusDirection\n",
        "import androidx.compose.ui.platform.LocalFocusManager\n",
    ):
        text, added = ensure_import(text, import_line)
        if added:
            print(f"PASS: {import_line.strip()} added")

    bounds = function_bounds(text, "MobileNavigation")
    if bounds is None:
        raise SystemExit("missing MobileNavigation")
    start, end = bounds
    block = text[start:end]
    original = block

    if ".navigationBarsPadding()" not in block:
        if ".statusBarsPadding()" in block:
            block = block.replace(
                ".statusBarsPadding()",
                ".statusBarsPadding()\n            .navigationBarsPadding()",
                1,
            )
        else:
            block, count = re.subn(
                r"(Modifier\s*\n(?:\s*\.[^\n]+\n)*?)(\s*\.padding\()",
                r"\1            .navigationBarsPadding()\n\2",
                block,
                count=1,
            )
            print(f"PASS: fallback navigation inset updates={count}")

    block = re.sub(
        r"\.padding\(horizontal\s*=\s*\d+\.dp,\s*vertical\s*=\s*\d+\.dp\)",
        ".padding(horizontal = 8.dp, vertical = 8.dp)",
        block,
        count=1,
    )
    block = re.sub(
        r"horizontalArrangement\s*=\s*Arrangement\.spacedBy\(\d+\.dp\)",
        "horizontalArrangement = Arrangement.spacedBy(6.dp)",
        block,
        count=1,
    )
    block = re.sub(
        r"heightIn\(min\s*=\s*(?:42|44)\.dp\)",
        "heightIn(min = 48.dp)",
        block,
    )
    text = text[:start] + block + text[end:]
    print("PASS: MobileNavigation hardened" if block != original else "PASS: MobileNavigation already hardened")

    text, category_count = re.subn(
        r"PaddingValues\(horizontal\s*=\s*24\.dp,\s*vertical\s*=\s*4\.dp\)",
        "PaddingValues(horizontal = 24.dp, vertical = 8.dp)",
        text,
    )
    print(f"PASS: category row padding updates={category_count}")

    text, rail_count = re.subn(
        r"(state\s*=\s*state,\s*\n\s*)isTv\s*=\s*true,(\s*\n\s*navigationMemory\s*=\s*navigationMemory,)",
        r"\1isTv = isTv,\2",
        text,
        count=1,
    )
    print(f"PASS: rail device-class corrections={rail_count}")

    home_bounds = None
    for candidate in ("HomeScreen", "HomeContent", "HomePage"):
        home_bounds = function_bounds(text, candidate)
        if home_bounds is not None:
            break
    if home_bounds is not None:
        home_start, home_end = home_bounds
        home = text[home_start:home_end]
        if "isTv" in home[:500] and "compatibilityTvBottomPadding" not in home:
            home, home_count = re.subn(
                r"\.fillMaxSize\(\)",
                ".fillMaxSize()\n            .padding(bottom = if (isTv) 32.dp else 0.dp) // compatibilityTvBottomPadding",
                home,
                count=1,
            )
            text = text[:home_start] + home + text[home_end:]
            print(f"PASS: TV Home bottom safe-area updates={home_count}")
        else:
            print("PASS: TV Home function found; no safe-area rewrite required")
    else:
        print("WARN: TV Home function name not discoverable; mobile fixes still applied")

    search_bounds = function_bounds(text, "UnifiedSearchScreen")
    if search_bounds is None:
        raise SystemExit("missing UnifiedSearchScreen")
    search_start, search_end = search_bounds
    search = text[search_start:search_end]
    if "val focusManager = LocalFocusManager.current" not in search:
        marker = "    val colors = LocalHulkColors.current\n"
        if marker not in search:
            raise SystemExit("missing UnifiedSearchScreen color marker")
        search = search.replace(marker, marker + "    val focusManager = LocalFocusManager.current\n", 1)

    old_field = '        HulkTextField(state.searchQuery, onSearch, "ابحث بالاسم او السنة او النوع…", Modifier.fillMaxWidth())'
    if "val searchFieldModifier = Modifier" not in search:
        if old_field not in search:
            raise SystemExit("missing UnifiedSearchScreen field marker")
        replacement = '''        val searchFieldModifier = Modifier
            .fillMaxWidth()
            .then(
                if (isTv) {
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (event.key) {
                                Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                                Key.DirectionLeft -> focusManager.moveFocus(FocusDirection.Left)
                                else -> false
                            }
                        }
                    }
                } else {
                    Modifier
                },
            )
        HulkTextField(
            value = state.searchQuery,
            onValueChange = onSearch,
            label = "ابحث بالاسم او السنة او النوع…",
            modifier = searchFieldModifier,
        )'''
        search = search.replace(old_field, replacement, 1)
        print("PASS: TV search DPAD focus escape added")

    old_grid_call = "            ContentGrid(results, isTv, MainDestination.SEARCH, navigationMemory, isFavorite, onOpen, onToggleFavorite)"
    new_grid_call = '''            ContentGrid(
                results,
                isTv,
                MainDestination.SEARCH,
                navigationMemory,
                isFavorite,
                onOpen,
                onToggleFavorite,
                restoreFocusedCard = isTv,
            )'''
    if new_grid_call not in search:
        if old_grid_call not in search:
            raise SystemExit("missing UnifiedSearchScreen ContentGrid marker")
        search = search.replace(old_grid_call, new_grid_call, 1)
        print("PASS: TV search result focus restoration enabled")
    text = text[:search_start] + search + text[search_end:]

    grid_bounds = function_bounds(text, "ContentGrid")
    if grid_bounds is None:
        raise SystemExit("missing ContentGrid")
    grid_start, grid_end = grid_bounds
    grid = text[grid_start:grid_end]
    old_search_branch = '''        if (destination == MainDestination.SEARCH) {
            if (content.isNotEmpty()) gridState.scrollToItem(0)
            navigationMemory.save(destination, content.firstOrNull()?.let { "${it.type}:${it.id}" }.orEmpty(), 0)
        } else if (restoreFocusedCard && content.isNotEmpty()) {'''
    new_search_branch = '''        if (destination == MainDestination.SEARCH) {
            if (content.isNotEmpty()) gridState.scrollToItem(0)
            navigationMemory.save(destination, content.firstOrNull()?.let { "${it.type}:${it.id}" }.orEmpty(), 0)
            if (restoreFocusedCard && content.isNotEmpty()) {
                delay(120)
                runCatching { targetRequester.requestFocus() }
            }
        } else if (restoreFocusedCard && content.isNotEmpty()) {'''
    if new_search_branch not in grid:
        if old_search_branch not in grid:
            raise SystemExit("missing ContentGrid search focus marker")
        grid = grid.replace(old_search_branch, new_search_branch, 1)
        print("PASS: TV search first result receives focus")
    text = text[:grid_start] + grid + text[grid_end:]

    save(path, text)


replace_version(
    "app/build.gradle.kts",
    r"^\s*versionCode\s*=\s*61\s*$",
    "        versionCode = 62",
    "versionCode = 62",
    "versionCode 62",
)
replace_version(
    "app/build.gradle.kts",
    r'^\s*versionName\s*=\s*"0\.9\.3\.17"\s*$',
    '        versionName = "0.9.3.18"',
    'versionName = "0.9.3.18"',
    "versionName 0.9.3.18",
)
harden_adaptive_classifier()
harden_main_shell()
print("PASS: prepared v0.9.3.18 compatibility responsive fixes")
