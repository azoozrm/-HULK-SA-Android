#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])


def replace_once(path: str, old: str, new: str, label: str) -> None:
    target = root / path
    text = target.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"missing {label}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_regex_once(path: str, pattern: str, replacement: str, label: str) -> None:
    target = root / path
    text = target.read_text(encoding="utf-8")
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.MULTILINE)
    if count == 0:
        raise SystemExit(f"missing {label}")
    target.write_text(updated, encoding="utf-8")


replace_once("app/build.gradle.kts", "versionCode = 61", "versionCode = 62", "versionCode")
replace_once("app/build.gradle.kts", 'versionName = "0.9.3.17"', 'versionName = "0.9.3.18"', "versionName")

main = "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
replace_once(
    main,
    "import androidx.compose.foundation.layout.heightIn\n",
    "import androidx.compose.foundation.layout.heightIn\nimport androidx.compose.foundation.layout.navigationBarsPadding\n",
    "MainShell navigationBarsPadding import",
)
replace_regex_once(
    main,
    r"(?s)(private fun MobileNavigation\(.*?Modifier\n\s*\.fillMaxWidth\(\)\n\s*\.background\(Color\(0xFF090A07\)\)\n\s*\.statusBarsPadding\(\))\n\s*\.padding\(horizontal = 6\.dp, vertical = 6\.dp\),\n\s*horizontalArrangement = Arrangement\.spacedBy\(4\.dp\),",
    r"\1\n            .navigationBarsPadding()\n            .padding(horizontal = 8.dp, vertical = 8.dp),\n        horizontalArrangement = Arrangement.spacedBy(6.dp),",
    "mobile navigation safe-area and spacing",
)
replace_once(
    main,
    "modifier = Modifier.weight(1f).heightIn(min = 42.dp)",
    "modifier = Modifier.weight(1f).heightIn(min = 48.dp)",
    "mobile navigation minimum touch height",
)
replace_regex_once(
    main,
    r"contentPadding = PaddingValues\(horizontal = 24\.dp, vertical = 4\.dp\),",
    "contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),",
    "catalog row vertical breathing room",
)
replace_regex_once(
    main,
    r"contentPadding = PaddingValues\(horizontal = 24\.dp, vertical = 4\.dp\),",
    "contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),",
    "live row vertical breathing room",
)

components = "app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt"
replace_once(
    components,
    "import androidx.compose.foundation.layout.heightIn\n",
    "import androidx.compose.foundation.layout.heightIn\nimport androidx.compose.foundation.layout.widthIn\n",
    "FocusButton widthIn import",
)
replace_regex_once(
    components,
    r"modifier = modifier\n\s*\.scale\(scale\)",
    "modifier = modifier\n            .widthIn(min = 48.dp)\n            .heightIn(min = 48.dp)\n            .scale(scale)",
    "FocusButton minimum adaptive bounds",
)

print("Prepared v0.9.3.18 compatibility responsive fixes")
