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
        print(f"PASS: adaptive navigation classifier updates={count}")

    save(path, text)


def harden_main_shell() -> None:
    relative = "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
    path, text = load(relative)

    for import_line in (
        "import androidx.compose.foundation.layout.navigationBarsPadding\n",
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

    # Preserve the real device type when a navigation rail is used on tablets.
    text, rail_count = re.subn(
        r"(state\s*=\s*state,\s*\n\s*)isTv\s*=\s*true,(\s*\n\s*navigationMemory\s*=\s*navigationMemory,)",
        r"\1isTv = isTv,\2",
        text,
        count=1,
    )
    print(f"PASS: rail device-class corrections={rail_count}")

    # Add explicit bottom breathing room to the TV Home surface when its function is discoverable.
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
