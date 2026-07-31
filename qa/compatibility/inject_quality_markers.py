#!/usr/bin/env python3
"""Add measurement-only semantics to the temporary Compatibility Lab checkout.

The repository production source remains unchanged. This transformer runs only
inside the disposable prepared project used to build the debug lab APK. Every
replacement is strict and additive: an unexpected or ambiguous source shape
fails closed.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any, Iterable


MARKERS = (
    "qa-tv-rail",
    "qa-tv-page-content:",
    "qa-tv-live-actions",
    "qa-tv-download-list",
    "qa-tv-download-card:",
)

HELPERS = '''
private const val QA_TV_PAGE_CONTENT_PREFIX = "qa-tv-page-content:"
private const val QA_TV_LIVE_ACTIONS = "qa-tv-live-actions"
private const val QA_TV_DOWNLOAD_LIST = "qa-tv-download-list"
private const val QA_TV_DOWNLOAD_CARD_PREFIX = "qa-tv-download-card:"

private fun Modifier.qaTvPageContent(
    isTv: Boolean,
    destination: MainDestination,
): Modifier = then(
    if (isTv && BuildConfig.DEBUG) {
        Modifier.semantics {
            contentDescription = "$QA_TV_PAGE_CONTENT_PREFIX${destination.name.lowercase(Locale.ROOT)}"
        }
    } else {
        Modifier
    },
)

private fun Modifier.qaMarker(isTv: Boolean, description: String): Modifier = then(
    if (isTv && BuildConfig.DEBUG) {
        Modifier.semantics { contentDescription = description }
    } else {
        Modifier
    },
)
'''


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def replace_once(
    source: str,
    old: str,
    new: str,
    label: str,
    changes: list[str],
) -> str:
    count = source.count(old)
    if count != 1:
        raise ValueError(f"{label}: expected exactly one match, found {count}")
    changes.append(label)
    return source.replace(old, new, 1)


def replace_one_of(
    source: str,
    options: Iterable[tuple[str, str]],
    label: str,
    changes: list[str],
) -> str:
    matches: list[tuple[str, str, int]] = []
    for old, new in options:
        count = source.count(old)
        if count:
            matches.append((old, new, count))
    total = sum(count for _, _, count in matches)
    if total != 1:
        detail = ", ".join(str(count) for _, _, count in matches) or "none"
        raise ValueError(
            f"{label}: expected exactly one supported source shape, found {total} "
            f"(matched counts: {detail})"
        )
    old, new, _count = matches[0]
    changes.append(label)
    return source.replace(old, new, 1)


def segment_bounds(
    source: str,
    start_marker: str,
    end_marker: str,
    label: str,
) -> tuple[int, int]:
    start = source.find(start_marker)
    if start < 0:
        raise ValueError(f"{label}: start marker not found: {start_marker}")
    end = source.find(end_marker, start + len(start_marker))
    if end < 0:
        raise ValueError(f"{label}: end marker not found: {end_marker}")
    return start, end


def patch_segment(
    source: str,
    start_marker: str,
    end_marker: str,
    old: str,
    new: str,
    label: str,
    changes: list[str],
) -> str:
    start, end = segment_bounds(source, start_marker, end_marker, label)
    segment = source[start:end]
    patched = replace_once(segment, old, new, label, changes)
    return source[:start] + patched + source[end:]


def patch_segment_one_of(
    source: str,
    start_marker: str,
    end_marker: str,
    options: Iterable[tuple[str, str]],
    label: str,
    changes: list[str],
) -> str:
    start, end = segment_bounds(source, start_marker, end_marker, label)
    segment = source[start:end]
    patched = replace_one_of(segment, options, label, changes)
    return source[:start] + patched + source[end:]


def page_content_options(destination: str) -> tuple[tuple[str, str], ...]:
    marker = f".qaTvPageContent(isTv, MainDestination.{destination})"
    return (
        (
            "Column(Modifier.fillMaxSize().padding(if (isTv) 24.dp else 13.dp)) {",
            "Column(Modifier.fillMaxSize().padding(if (isTv) 24.dp else 13.dp)"
            f"{marker}) {{",
        ),
        (
            "    Column(\n"
            "        Modifier\n"
            "            .fillMaxSize()\n"
            "            .padding(if (isTv) TV_PAGE_GUTTER else 13.dp),\n"
            "    ) {",
            "    Column(\n"
            "        Modifier\n"
            "            .fillMaxSize()\n"
            "            .padding(if (isTv) TV_PAGE_GUTTER else 13.dp)\n"
            f"            {marker},\n"
            "    ) {",
        ),
        (
            "    Column(\n"
            "        Modifier\n"
            "            .fillMaxSize()\n"
            "            .padding(\n"
            "                horizontal = if (isTv) TV_PAGE_GUTTER else 13.dp,\n"
            "                vertical = if (isTv) TV_PAGE_GUTTER else 12.dp,\n"
            "            ),\n"
            "    ) {",
            "    Column(\n"
            "        Modifier\n"
            "            .fillMaxSize()\n"
            "            .padding(\n"
            "                horizontal = if (isTv) TV_PAGE_GUTTER else 13.dp,\n"
            "                vertical = if (isTv) TV_PAGE_GUTTER else 12.dp,\n"
            "            )\n"
            f"            {marker},\n"
            "    ) {",
        ),
        (
            "    Column(\n"
            "        Modifier\n"
            "            .fillMaxSize()\n"
            "            .padding(\n"
            "                horizontal = if (isTv) TV_PAGE_GUTTER else 12.dp,\n"
            "                vertical = if (isTv) TV_PAGE_GUTTER else 11.dp,\n"
            "            ),\n"
            "    ) {",
            "    Column(\n"
            "        Modifier\n"
            "            .fillMaxSize()\n"
            "            .padding(\n"
            "                horizontal = if (isTv) TV_PAGE_GUTTER else 12.dp,\n"
            "                vertical = if (isTv) TV_PAGE_GUTTER else 11.dp,\n"
            "            )\n"
            f"            {marker},\n"
            "    ) {",
        ),
        (
            "    LazyColumn(\n"
            "        state = listState,\n"
            "        modifier = Modifier\n"
            "            .fillMaxSize()\n"
            "            .padding(if (isTv) TV_PAGE_GUTTER else 0.dp),",
            "    LazyColumn(\n"
            "        state = listState,\n"
            "        modifier = Modifier\n"
            "            .fillMaxSize()\n"
            "            .padding(if (isTv) TV_PAGE_GUTTER else 0.dp)\n"
            f"            {marker},",
        ),
    )


def inject_text(source: str) -> tuple[str, dict[str, Any]]:
    original = source
    changes: list[str] = []

    source = replace_once(
        source,
        "import androidx.compose.ui.semantics.Role",
        "import androidx.compose.ui.semantics.Role\n"
        "import androidx.compose.ui.semantics.contentDescription\n"
        "import androidx.compose.ui.semantics.semantics",
        "semantics-imports",
        changes,
    )
    source = replace_once(
        source,
        'private const val WEBSITE_URL = "https://hulksa.com/"',
        HELPERS + '\nprivate const val WEBSITE_URL = "https://hulksa.com/"',
        "qa-helpers",
        changes,
    )
    source = patch_segment(
        source,
        "private fun CinematicNavigationRail(",
        "private fun NavigationItem(",
        ".focusGroup()",
        ".focusGroup()\n            .qaMarker(true, \"qa-tv-rail\")",
        "rail-marker",
        changes,
    )
    for function_name, next_name, destination in (
        ("private fun CinemaHomeScreen(", "private fun ActiveDownloadsSection(", "HOME"),
        ("private fun LiveCatalogScreen(", "private fun LiveStage(", "LIVE"),
        ("private fun PosterCatalogScreen(", "private fun LiveCatalogScreen(", "MOVIES"),
        ("private fun FavoritesScreen(", "private fun UnifiedSearchScreen(", "FAVORITES"),
        ("private fun UnifiedSearchScreen(", "private fun TvSearchField(", "SEARCH"),
        ("private fun DownloadsScreen(", "private fun DownloadCard(", "DOWNLOADS"),
    ):
        source = patch_segment_one_of(
            source,
            function_name,
            next_name,
            page_content_options(destination),
            f"{destination.lower()}-content-marker",
            changes,
        )
    source = patch_segment(
        source,
        "private fun DownloadsScreen(",
        "private fun DownloadCard(",
        "modifier = Modifier.fillMaxSize(),",
        "modifier = Modifier.fillMaxSize().qaMarker(isTv, QA_TV_DOWNLOAD_LIST),",
        "downloads-list-marker",
        changes,
    )
    source = patch_segment_one_of(
        source,
        "private fun DownloadCard(",
        "private fun DownloadProgress(",
        (
            (
                "            .height(if (isTv) 220.dp else 220.dp)\n"
                "            .clip(shape)",
                "            .height(if (isTv) 220.dp else 220.dp)\n"
                "            .qaMarker(isTv, \"$QA_TV_DOWNLOAD_CARD_PREFIX${item.downloadId}\")\n"
                "            .clip(shape)",
            ),
            (
                "            .height(if (isTv) 164.dp else 220.dp)\n"
                "            .clip(shape)",
                "            .height(if (isTv) 164.dp else 220.dp)\n"
                "            .qaMarker(isTv, \"$QA_TV_DOWNLOAD_CARD_PREFIX${item.downloadId}\")\n"
                "            .clip(shape)",
            ),
            (
                "            .heightIn(min = cardHeight)\n"
                "            .clip(shape)",
                "            .heightIn(min = cardHeight)\n"
                "            .qaMarker(isTv, \"$QA_TV_DOWNLOAD_CARD_PREFIX${item.downloadId}\")\n"
                "            .clip(shape)",
            ),
        ),
        "download-card-marker",
        changes,
    )
    source = patch_segment_one_of(
        source,
        "private fun SettingsScreen(",
        "private fun AccountMetric(",
        (
            (
                "modifier = Modifier.fillMaxSize(),",
                "modifier = Modifier.fillMaxSize().qaTvPageContent(isTv, MainDestination.SETTINGS),",
            ),
            (
                "modifier = Modifier\n            .fillMaxSize()\n            .padding(if (isTv) TV_PAGE_GUTTER else 0.dp),",
                "modifier = Modifier\n            .fillMaxSize()\n            .padding(if (isTv) TV_PAGE_GUTTER else 0.dp)\n            .qaTvPageContent(isTv, MainDestination.SETTINGS),",
            ),
        ),
        "settings-content-marker",
        changes,
    )
    source = patch_segment(
        source,
        "private fun LiveStage(",
        "private fun FavoritesScreen(",
        "Box(Modifier.fillMaxWidth().padding(bottom = TV_LIVE_ACTION_INSET)) {",
        "Box(Modifier.fillMaxWidth().padding(bottom = TV_LIVE_ACTION_INSET).qaMarker(true, QA_TV_LIVE_ACTIONS)) {",
        "live-actions-marker",
        changes,
    )

    report = {
        "input_sha256": sha256_text(original),
        "output_sha256": sha256_text(source),
        "changes": changes,
        "markers": list(MARKERS),
    }
    return source, report


def inject_file(path: Path, report_path: Path | None = None) -> dict[str, Any]:
    original = path.read_text(encoding="utf-8")
    patched, report = inject_text(original)
    path.write_text(patched, encoding="utf-8")
    if report_path is not None:
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, indent=2, sort_keys=True), encoding="utf-8")
    return report


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("path", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    report = inject_file(args.path, args.report)
    print(json.dumps(report, sort_keys=True))


if __name__ == "__main__":
    main()
