#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])


def rep(path: str, old: str, new: str, label: str, count: int = 1) -> None:
    target = root / path
    text = target.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"missing {label}")
    target.write_text(text.replace(old, new, count), encoding="utf-8")


rep("app/build.gradle.kts", "versionCode = 61", "versionCode = 62", "versionCode")
rep("app/build.gradle.kts", 'versionName = "0.9.3.17"', 'versionName = "0.9.3.18"', "versionName")

main = "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
rep(
    main,
    "import androidx.compose.foundation.layout.heightIn\n",
    "import androidx.compose.foundation.layout.heightIn\nimport androidx.compose.foundation.layout.navigationBarsPadding\n",
    "MainShell navigationBarsPadding import",
)
rep(
    main,
    """        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF090A07))
            .statusBarsPadding()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
""",
    """        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF090A07))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
""",
    "mobile navigation safe-area and spacing",
)
rep(
    main,
    """                modifier = Modifier.weight(1f).heightIn(min = 42.dp),
""",
    """                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
""",
    "mobile navigation minimum touch height",
)
rep(
    main,
    """        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
""",
    """        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
""",
    "catalog row vertical breathing room",
    count=2,
)

components = "app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt"
rep(
    components,
    "import androidx.compose.foundation.layout.heightIn\n",
    "import androidx.compose.foundation.layout.heightIn\nimport androidx.compose.foundation.layout.widthIn\n",
    "FocusButton widthIn import",
)
rep(
    components,
    """        modifier = modifier
            .scale(scale)
""",
    """        modifier = modifier
            .widthIn(min = 48.dp)
            .heightIn(min = 48.dp)
            .scale(scale)
""",
    "FocusButton minimum adaptive bounds",
)

print("Prepared v0.9.3.18 compatibility responsive fixes")
