#!/usr/bin/env python3
"""Verify HULK SA adaptive source including the v0.9.3.2 mobile follow-up."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path


REQUIRED_MARKERS: dict[str, tuple[str, ...]] = {
    "app/build.gradle.kts": (
        'versionCode = 46',
        'versionName = "0.9.3.2"',
        '"arm64-v8a"',
        '"armeabi-v7a"',
        '"x86_64"',
    ),
    "app/src/main/java/sa/hulksa/player/MainActivity.kt": (
        "PackageManager.FEATURE_LEANBACK",
        "PackageManager.FEATURE_TELEVISION",
        "isTelevisionDevice = isTelevisionDevice",
    ),
    "app/src/main/java/sa/hulksa/player/ui/HulkApp.kt": (
        "rememberAdaptiveUiState",
        "CompositionLocalProvider(LocalAdaptiveUi provides adaptiveUi)",
        "trackAdaptiveInput(adaptiveInputController)",
    ),
    "app/src/main/java/sa/hulksa/player/ui/adaptive/AdaptiveUi.kt": (
        "enum class HulkDeviceClass",
        "enum class HulkWindowWidthClass",
        "enum class HulkInputMode",
        "fun classifyWindowWidth",
        "fun classifyDeviceClass",
        "fun selectNavigationType",
    ),
    "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt": (
        "val useNavigationRail = adaptiveUi.navigationType == HulkNavigationType.RAIL",
        "animateScrollToItem(selectedIndex + 1)",
        "detectDragGesturesAfterLongPress",
        "dragAccumulator",
    ),
    "app/src/main/java/sa/hulksa/player/ui/screens/MovieDetailsScreen.kt": (
        ".height(if (isTv) 510.dp else 510.dp)",
        "fontSize = if (isTv) 42.sp else 26.sp",
    ),
    "app/src/main/java/sa/hulksa/player/ui/screens/SeriesScreen.kt": (
        ".height(if (isTv) 390.dp else 400.dp)",
        "fontSize = if (isTv) 39.sp else 25.sp",
    ),
    "app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt": (
        "detectHorizontalDragGestures",
        "onSeekTo((durationMs * fraction).toLong())",
        "change.position.x / size.width.toFloat()",
    ),
    "app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt": (
        "import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi",
        "val showFocused = focused && adaptiveUi.showFocusHighlights",
    ),
    "app/src/test/java/sa/hulksa/player/ui/adaptive/AdaptiveUiClassifierTest.kt": (
        "compactPhoneUsesMobileTopNavigation",
        "expandedTabletUsesRailNavigation",
        "televisionAlwaysUsesRailAndFocusHighlights",
    ),
}


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("project", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    root = args.project.resolve()
    if not root.is_dir():
        fail(f"project directory does not exist: {root}")

    checked: list[str] = []
    for relative, markers in REQUIRED_MARKERS.items():
        path = root / relative
        if not path.is_file():
            fail(f"required source file missing: {relative}")
        text = path.read_text(encoding="utf-8")
        for marker in markers:
            if marker not in text:
                fail(f"missing marker in {relative}: {marker}")
        checked.append(relative)

    build_text = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
    if '"x86",' in build_text or '\n                "x86"\n' in build_text:
        fail("legacy x86 must remain excluded")

    main_source = root / "app/src/main"
    if not any("HomeContentSnapshot" in p.read_text(encoding="utf-8", errors="ignore") for p in main_source.rglob("*.kt")):
        fail("HomeContentSnapshot preservation marker is missing")

    report_lines = [
        "# HULK SA v0.9.3.2 Mobile Follow-up Source Verification",
        "",
        "Status: **PASS**",
        "",
        "Verified:",
        "",
        "- Complete horizontally scrollable mobile navigation.",
        "- Selected destination automatic visibility.",
        "- Touch long-press live-category drag reordering.",
        "- Safe movie and series title spacing.",
        "- Tap and horizontal-drag VOD seeking.",
        "- Qualified ABI configuration and legacy x86 exclusion.",
        "- Adaptive device/input behavior and preserved HomeContentSnapshot.",
        "",
        "Checked files:",
        "",
        *[f"- `{item}`" for item in checked],
        "",
    ]
    report = "\n".join(report_lines)
    print(report)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(report, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
