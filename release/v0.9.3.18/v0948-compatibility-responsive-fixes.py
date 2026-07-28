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
        return text, False
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


def harden_main_shell() -> None:
    relative = "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
    path, text = load(relative)
    text, import_added = ensure_import(
        text,
        "import androidx.compose.foundation.layout.navigationBarsPadding\n",
    )

    bounds = function_bounds(text, "private fun MobileNavigation")
    if bounds is None:
        print("WARN: MobileNavigation function not found; navigation patch skipped")
    else:
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

    text, catalog_count = re.subn(
        r"PaddingValues\(horizontal\s*=\s*24\.dp,\s*vertical\s*=\s*4\.dp\)",
        "PaddingValues(horizontal = 24.dp, vertical = 8.dp)",
        text,
    )
    print(f"PASS: category row padding updates={catalog_count}")
    save(path, text)
    if import_added:
        print("PASS: navigationBarsPadding import added")


replace_required("app/build.gradle.kts", "versionCode = 61", "versionCode = 62", "versionCode 62")
replace_required(
    "app/build.gradle.kts",
    'versionName = "0.9.3.17"',
    'versionName = "0.9.3.18"',
    "versionName 0.9.3.18",
)
harden_main_shell()
print("PASS: prepared v0.9.3.18 compatibility responsive fixes")
