#!/usr/bin/env python3
"""Apply HULK SA v0.9.3.0 adaptive UI source changes from reviewed templates."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    if old not in text:
        fail(f"source marker not found for {label}")
    return text.replace(old, new, 1)


def update(path: Path, transforms: list[tuple[str, str, str]]) -> None:
    text = path.read_text(encoding="utf-8")
    for old, new, label in transforms:
        text = replace_once(text, old, new, label)
    path.write_text(text, encoding="utf-8")


def install_template(templates: Path, relative_template: str, destination: Path, marker: str) -> None:
    template = templates / relative_template
    if not template.is_file():
        fail(f"adaptive source template missing: {template}")
    if destination.exists() and marker not in destination.read_text(encoding="utf-8"):
        fail(f"destination source marker missing: {destination}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(template.read_text(encoding="utf-8"), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("project", type=Path)
    args = parser.parse_args()
    root = args.project.resolve()
    if not (root / "settings.gradle.kts").is_file():
        fail(f"not an Android project: {root}")

    repo_root = Path(__file__).resolve().parents[1]
    templates = repo_root / "patches/v0.9.3.0-adaptive-source"

    build = root / "app/build.gradle.kts"
    update(build, [
        ("versionCode = 43", "versionCode = 44", "versionCode"),
        ('versionName = "0.9.2.0"', 'versionName = "0.9.3.0"', "versionName"),
        ("// Phase 2: ship one universal APK with only qualified ABIs.", "// Phase 3: preserve the qualified universal ABI set while adding adaptive UI.", "phase comment"),
    ])

    install_template(
        templates,
        "MainActivity.kt",
        root / "app/src/main/java/sa/hulksa/player/MainActivity.kt",
        "class MainActivity",
    )
    install_template(
        templates,
        "HulkApp.kt",
        root / "app/src/main/java/sa/hulksa/player/ui/HulkApp.kt",
        "fun HulkApp",
    )
    install_template(
        templates,
        "AdaptiveUi.kt",
        root / "app/src/main/java/sa/hulksa/player/ui/adaptive/AdaptiveUi.kt",
        "package sa.hulksa.player.ui.adaptive",
    )
    install_template(
        templates,
        "AdaptiveUiClassifierTest.kt",
        root / "app/src/test/java/sa/hulksa/player/ui/adaptive/AdaptiveUiClassifierTest.kt",
        "package sa.hulksa.player.ui.adaptive",
    )

    shell = root / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
    update(shell, [
        ("import sa.hulksa.player.ui.components.BrandLogo\n", "import sa.hulksa.player.ui.adaptive.HulkNavigationType\nimport sa.hulksa.player.ui.adaptive.LocalAdaptiveUi\nimport sa.hulksa.player.ui.components.BrandLogo\n", "MainShell adaptive imports"),
        ("    val colors = LocalHulkColors.current\n    val context = LocalContext.current\n    val toggleFavoriteWithFeedback", "    val colors = LocalHulkColors.current\n    val context = LocalContext.current\n    val adaptiveUi = LocalAdaptiveUi.current\n    val useNavigationRail = adaptiveUi.navigationType == HulkNavigationType.RAIL\n    val toggleFavoriteWithFeedback", "MainShell adaptive state"),
        ("        if (isTv) {\n            Row(Modifier.fillMaxSize())", "        if (useNavigationRail) {\n            Row(Modifier.fillMaxSize())", "MainShell rail selection"),
        ("    val colors = LocalHulkColors.current\n    var focused by remember { mutableStateOf(false) }\n    val active = selected || focused\n    Row(\n", "    val colors = LocalHulkColors.current\n    val adaptiveUi = LocalAdaptiveUi.current\n    var focused by remember { mutableStateOf(false) }\n    val showFocused = focused && adaptiveUi.showFocusHighlights\n    val active = selected || showFocused\n    Row(\n", "NavigationItem adaptive focus"),
        ("                    focused -> colors.gold", "                    showFocused -> colors.gold", "NavigationItem focus background"),
        ("tint = if (focused) Color.Black else if (active)", "tint = if (showFocused) Color.Black else if (active)", "NavigationItem icon focus"),
        ("color = if (focused) Color.Black else if (active)", "color = if (showFocused) Color.Black else if (active)", "NavigationItem text focus"),
    ])

    components = root / "app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt"
    update(components, [
        ("import sa.hulksa.player.ui.theme.LocalHulkColors\n", "import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi\nimport sa.hulksa.player.ui.theme.LocalHulkColors\n", "component adaptive import"),
        ("    val colors = LocalHulkColors.current\n    var focused by remember { mutableStateOf(false) }\n    val scale by animateFloatAsState(if (focused) 1.035f else 1f, label = \"buttonScale\")", "    val colors = LocalHulkColors.current\n    val adaptiveUi = LocalAdaptiveUi.current\n    var focused by remember { mutableStateOf(false) }\n    val showFocused = focused && adaptiveUi.showFocusHighlights\n    val scale by animateFloatAsState(if (showFocused) 1.035f else 1f, label = \"buttonScale\")", "FocusButton state"),
        ("primary && focused ->", "primary && showFocused ->", "FocusButton primary focus"),
        ("        focused -> Color(0xFF2A281B)", "        showFocused -> Color(0xFF2A281B)", "FocusButton background focus"),
        ("                    focused -> 2.dp", "                    showFocused -> 2.dp", "FocusButton border width"),
        ("                    focused -> colors.goldBright", "                    showFocused -> colors.goldBright", "FocusButton border color"),
        ("    val colors = LocalHulkColors.current\n    var focused by remember { mutableStateOf(false) }\n    val shape = RoundedCornerShape(13.dp)\n    val active = focused || selected", "    val colors = LocalHulkColors.current\n    val adaptiveUi = LocalAdaptiveUi.current\n    var focused by remember { mutableStateOf(false) }\n    val showFocused = focused && adaptiveUi.showFocusHighlights\n    val shape = RoundedCornerShape(13.dp)\n    val active = showFocused || selected", "NavRailButton state"),
        ("if (focused) .25f else .13f", "if (showFocused) .25f else .13f", "NavRailButton alpha"),
        ("if (focused) 2.dp else 0.dp", "if (showFocused) 2.dp else 0.dp", "NavRailButton border"),
        ("if (focused) colors.goldBright else Color.Transparent", "if (showFocused) colors.goldBright else Color.Transparent", "NavRailButton color"),
        ("    val colors = LocalHulkColors.current\n    var focused by remember { mutableStateOf(false) }\n    var artworkFailed", "    val colors = LocalHulkColors.current\n    val adaptiveUi = LocalAdaptiveUi.current\n    var focused by remember { mutableStateOf(false) }\n    var artworkFailed", "PosterCard adaptive state"),
        ("    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = \"posterScale\")", "    val showFocused = focused && adaptiveUi.showFocusHighlights\n    val scale by animateFloatAsState(if (showFocused) 1.04f else 1f, label = \"posterScale\")", "PosterCard scale"),
        ("shadowElevation = if (focused) 14.dp.toPx() else 0f", "shadowElevation = if (showFocused) 14.dp.toPx() else 0f", "PosterCard shadow"),
        ("                if (focused) 3.dp else 0.dp", "                if (showFocused) 3.dp else 0.dp", "PosterCard border width"),
        ("                if (focused) colors.goldBright else Color.Transparent", "                if (showFocused) colors.goldBright else Color.Transparent", "PosterCard border color"),
        ("                if (showFocused) 3.dp else 0.dp,\n                if (focused) colors.goldBright else Color.Transparent,", "                if (showFocused) 3.dp else 0.dp,\n                if (showFocused) colors.goldBright else Color.Transparent,", "PosterCard corrected border color"),
        ("    val colors = LocalHulkColors.current\n    var focused by remember { mutableStateOf(false) }\n    val scale by animateFloatAsState(if (focused) 1.035f else 1f, label = \"historyScale\")", "    val colors = LocalHulkColors.current\n    val adaptiveUi = LocalAdaptiveUi.current\n    var focused by remember { mutableStateOf(false) }\n    val showFocused = focused && adaptiveUi.showFocusHighlights\n    val scale by animateFloatAsState(if (showFocused) 1.035f else 1f, label = \"historyScale\")", "HistoryCard state"),
        (".border(if (focused) 3.dp else 0.dp, if (focused) colors.goldBright else Color.Transparent, shape)", ".border(if (showFocused) 3.dp else 0.dp, if (showFocused) colors.goldBright else Color.Transparent, shape)", "HistoryCard border"),
        ("    val colors = LocalHulkColors.current\n    var focused by remember { mutableStateOf(false) }\n    var remoteLongPressHandled", "    val colors = LocalHulkColors.current\n    val adaptiveUi = LocalAdaptiveUi.current\n    var focused by remember { mutableStateOf(false) }\n    var remoteLongPressHandled", "Channel item adaptive state"),
        ("    val active = focused || selected\n    val shape = RoundedCornerShape(11.dp)", "    val showFocused = focused && adaptiveUi.showFocusHighlights\n    val active = showFocused || selected\n    val shape = RoundedCornerShape(11.dp)", "Channel item active state"),
        (".border(if (focused) 2.dp else 0.dp, if (focused) colors.goldBright else Color.Transparent, shape)", ".border(if (showFocused) 2.dp else 0.dp, if (showFocused) colors.goldBright else Color.Transparent, shape)", "Channel item border"),
    ])

    if not any("HomeContentSnapshot" in p.read_text(encoding="utf-8", errors="ignore") for p in (root / "app/src/main").rglob("*.kt")):
        fail("HomeContentSnapshot preservation marker missing")

    print(f"Prepared adaptive source: {root}")
    print("Version: 0.9.3.0 (44)")
    print("Device classes: mobile, tablet, television")
    print("Input modes: touch, remote, keyboard")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
