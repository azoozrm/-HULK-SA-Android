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


def replace_required(relative: str, old: str, new: str, label: str) -> None:
    path, text = load(relative)
    if new in text:
        print(f"PASS: {label} already applied")
        return
    if old not in text:
        raise SystemExit(f"missing required marker: {label}")
    save(path, text.replace(old, new, 1))
    print(f"PASS: {label}")


def ensure_import(text: str, import_line: str) -> tuple[str, bool]:
    if import_line in text:
        return text, False
    lines = text.splitlines(keepends=True)
    import_indexes = [index for index, line in enumerate(lines) if line.startswith("import ")]
    if not import_indexes:
        raise SystemExit(f"missing import section for {import_line.strip()}")
    lines.insert(import_indexes[-1] + 1, import_line)
    return "".join(lines), True


def function_bounds(text: str, signature: str) -> tuple[int, int] | None:
    start = text.find(signature)
    if start < 0:
        return None
    brace = text.find("{", start)
    if brace < 0:
        return None
    depth = 0
    for index in range(brace, len(text)):
        char = text[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return start, index + 1
    return None


def replace_function(text: str, signature: str, replacement: str, label: str) -> str:
    bounds = function_bounds(text, signature)
    if bounds is None:
        raise SystemExit(f"missing function: {label}")
    start, end = bounds
    current = text[start:end]
    if current.strip() == replacement.strip():
        print(f"PASS: {label} already applied")
        return text
    print(f"PASS: {label}")
    return text[:start] + replacement.rstrip() + text[end:]


def harden_adaptive_classifier() -> None:
    relative = "app/src/main/java/sa/hulksa/player/ui/adaptive/AdaptiveUi.kt"
    path, text = load(relative)
    old_device = """fun classifyDeviceClass(
    isTelevisionDevice: Boolean,
    smallestWidthDp: Int,
    widthDp: Int,
): HulkDeviceClass = when {
    isTelevisionDevice -> HulkDeviceClass.TELEVISION
    smallestWidthDp >= 600 || widthDp >= 840 -> HulkDeviceClass.TABLET
    else -> HulkDeviceClass.MOBILE
}"""
    new_device = """fun classifyDeviceClass(
    isTelevisionDevice: Boolean,
    smallestWidthDp: Int,
    widthDp: Int,
): HulkDeviceClass = when {
    isTelevisionDevice -> HulkDeviceClass.TELEVISION
    smallestWidthDp >= 600 -> HulkDeviceClass.TABLET
    else -> HulkDeviceClass.MOBILE
}"""
    if new_device not in text:
        if old_device not in text:
            raise SystemExit("missing adaptive device classifier")
        text = text.replace(old_device, new_device, 1)

    old_navigation = """fun selectNavigationType(
    deviceClass: HulkDeviceClass,
    windowWidthClass: HulkWindowWidthClass,
): HulkNavigationType = when {
    deviceClass == HulkDeviceClass.TELEVISION -> HulkNavigationType.RAIL
    windowWidthClass == HulkWindowWidthClass.EXPANDED -> HulkNavigationType.RAIL
    else -> HulkNavigationType.TOP_BAR
}"""
    new_navigation = """fun selectNavigationType(
    deviceClass: HulkDeviceClass,
    windowWidthClass: HulkWindowWidthClass,
): HulkNavigationType = when {
    deviceClass == HulkDeviceClass.TELEVISION -> HulkNavigationType.RAIL
    deviceClass == HulkDeviceClass.TABLET && windowWidthClass == HulkWindowWidthClass.EXPANDED -> HulkNavigationType.RAIL
    else -> HulkNavigationType.TOP_BAR
}"""
    if new_navigation not in text:
        if old_navigation not in text:
            raise SystemExit("missing adaptive navigation classifier")
        text = text.replace(old_navigation, new_navigation, 1)

    save(path, text)
    print("PASS: landscape phones remain mobile with top navigation")

    test_relative = "app/src/test/java/sa/hulksa/player/ui/adaptive/AdaptiveUiClassifierTest.kt"
    test_path, tests = load(test_relative)
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
    if "fun landscapePhoneDoesNotBecomeTabletOrRail()" not in tests:
        marker = "    @Test\n    fun portraitTabletUsesTabletLayoutWithoutTelevisionSizing()"
        if marker not in tests:
            raise SystemExit("missing adaptive classifier test marker")
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

    bounds = function_bounds(text, "private fun MobileNavigation")
    if bounds is None:
        raise SystemExit("missing MobileNavigation")
    start, end = bounds
    block = text[start:end]
    original = block
    if ".navigationBarsPadding()" not in block:
        block = block.replace(
            ".statusBarsPadding()",
            ".statusBarsPadding()\n            .navigationBarsPadding()",
            1,
        )
    block = re.sub(
        r"contentPadding\s*=\s*PaddingValues\(horizontal\s*=\s*\d+\.dp,\s*vertical\s*=\s*\d+\.dp\)",
        "contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)",
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

    text, catalog_count = re.subn(
        r"PaddingValues\(horizontal\s*=\s*24\.dp,\s*vertical\s*=\s*4\.dp\)",
        "PaddingValues(horizontal = 24.dp, vertical = 8.dp)",
        text,
    )
    print(f"PASS: category row padding updates={catalog_count}")

    rail_branch = """                        state = state,
                        isTv = true,
                        navigationMemory = navigationMemory,"""
    corrected_branch = """                        state = state,
                        isTv = isTv,
                        navigationMemory = navigationMemory,"""
    if corrected_branch not in text:
        if rail_branch not in text:
            raise SystemExit("missing rail content device marker")
        text = text.replace(rail_branch, corrected_branch, 1)
        print("PASS: rail content preserves the real device class")

    search_bounds = function_bounds(text, "private fun UnifiedSearchScreen")
    if search_bounds is None:
        raise SystemExit("missing UnifiedSearchScreen")
    search_start, search_end = search_bounds
    search_block = text[search_start:search_end]
    if "val focusManager = LocalFocusManager.current" not in search_block:
        search_block = search_block.replace(
            "    val colors = LocalHulkColors.current\n",
            "    val colors = LocalHulkColors.current\n    val focusManager = LocalFocusManager.current\n",
            1,
        )
    old_field = '        HulkTextField(state.searchQuery, onSearch, "ابحث بالاسم او السنة او النوع…", Modifier.fillMaxWidth())'
    new_field = '''        val searchFieldModifier = Modifier
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
    if new_field not in search_block:
        if old_field not in search_block:
            raise SystemExit("missing unified search field marker")
        search_block = search_block.replace(old_field, new_field, 1)
        print("PASS: TV search can move focus out of the text field")
    text = text[:search_start] + search_block + text[search_end:]

    save(path, text)


replace_required("app/build.gradle.kts", "versionCode = 61", "versionCode = 62", "versionCode 62")
replace_required(
    "app/build.gradle.kts",
    'versionName = "0.9.3.17"',
    'versionName = "0.9.3.18"',
    "versionName 0.9.3.18",
)
harden_adaptive_classifier()
harden_main_shell()
print("PASS: prepared v0.9.3.18 compatibility responsive fixes")
