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
    "qa-tv-live-channel:",
    "qa-tv-live-play",
    "qa-tv-live-favorite",
    "qa-tv-download-list",
    "qa-tv-download-card:",
)

HELPERS = '''
private const val QA_TV_PAGE_CONTENT_PREFIX = "qa-tv-page-content:"
private const val QA_TV_LIVE_ACTIONS = "qa-tv-live-actions"
private const val QA_TV_LIVE_CHANNEL_PREFIX = "qa-tv-live-channel:"
private const val QA_TV_LIVE_PLAY = "qa-tv-live-play"
private const val QA_TV_LIVE_FAVORITE = "qa-tv-live-favorite"
private const val QA_TV_DOWNLOAD_LIST = "qa-tv-download-list"
private const val QA_TV_DOWNLOAD_CARD_PREFIX = "qa-tv-download-card:"
private const val QA_FOCUS_TRACE_TAG = "HULK_QA_FOCUS"
private var qaLastFocusRequestAtMs = -1L
private var qaLastFocusRequestTarget = ""

private fun DownloadFocusLocation.qaFocusId(): String = when (zone) {
    DownloadFocusZone.TOOLBAR -> "toolbar-${slot.name.lowercase(Locale.ROOT)}"
    DownloadFocusZone.CARD -> "row-${row + 1}-${slot.name.lowercase(Locale.ROOT)}"
}

private fun qaFocusTrace(event: String, target: String, detail: String = "") {
    if (BuildConfig.DEBUG) {
        Log.i(
            QA_FOCUS_TRACE_TAG,
            "timestamp_ns=${SystemClock.elapsedRealtimeNanos()} event=$event target=$target $detail".trim(),
        )
    }
}

private fun qaTraceFocusRequest(target: DownloadFocusLocation, result: Boolean) {
    if (!BuildConfig.DEBUG) return
    val now = SystemClock.elapsedRealtime()
    val delta = if (qaLastFocusRequestAtMs >= 0L) now - qaLastFocusRequestAtMs else -1L
    val secondWithin500Ms = qaLastFocusRequestTarget.isNotEmpty() && delta in 0L..500L
    qaFocusTrace(
        event = "request-focus",
        target = target.qaFocusId(),
        detail = "result=$result second_within_500ms=$secondWithin500Ms " +
            "previous=$qaLastFocusRequestTarget delta_ms=$delta",
    )
    qaLastFocusRequestAtMs = now
    qaLastFocusRequestTarget = target.qaFocusId()
}

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


def inject_text(source: str) -> tuple[str, dict[str, Any]]:
    if any(marker in source for marker in MARKERS):
        raise ValueError(
            "quality marker injection refused: source already contains a marker"
        )

    changes: list[str] = []
    source = replace_once(
        source,
        "import android.content.Intent\n",
        "import android.content.Intent\n"
        "import android.os.SystemClock\n"
        "import android.util.Log\n",
        "focus-trace-imports",
        changes,
    )
    source = replace_once(
        source,
        "import androidx.compose.ui.semantics.Role\n",
        "import androidx.compose.ui.semantics.Role\n"
        "import androidx.compose.ui.semantics.contentDescription\n"
        "import androidx.compose.ui.semantics.semantics\n",
        "semantics-imports",
        changes,
    )
    source = replace_once(
        source,
        'private const val CONTINUE_CATEGORY_ID = "__hulk_continue__"\n',
        'private const val CONTINUE_CATEGORY_ID = "__hulk_continue__"\n'
        + HELPERS,
        "marker-helpers",
        changes,
    )
    source = patch_segment(
        source,
        "private fun CinematicNavigationRail(",
        "private fun NavigationItem(",
        "            .fillMaxHeight()\n            .focusGroup()",
        "            .fillMaxHeight()\n"
        "            .qaMarker(isTv = true, description = \"qa-tv-rail\")\n"
        "            .focusGroup()",
        "tv-rail-marker",
        changes,
    )
    source = patch_segment_one_of(
        source,
        "private fun CinemaHomeScreen(",
        "private fun HomeSectionPadding(",
        (
            (
                "            .fillMaxSize()\n"
                "            .padding(bottom = if (isTv) 32.dp else 0.dp)",
                "            .fillMaxSize()\n"
                "            .qaTvPageContent(isTv, MainDestination.HOME)\n"
                "            .padding(bottom = if (isTv) 32.dp else 0.dp)",
            ),
            (
                "            .fillMaxSize()\n"
                "            .padding(if (isTv) TV_PAGE_GUTTER else 0.dp),",
                "            .fillMaxSize()\n"
                "            .padding(if (isTv) TV_PAGE_GUTTER else 0.dp)\n"
                "            .qaTvPageContent(isTv, MainDestination.HOME),",
            ),
        ),
        "home-content-marker",
        changes,
    )
    source = patch_segment_one_of(
        source,
        "private fun PosterCatalogScreen(",
        "private fun LiveCatalogScreen(",
        (
            (
                "Column(Modifier.fillMaxSize().padding(horizontal = if (isTv) 24.dp "
                "else 13.dp, vertical = if (isTv) 19.dp else 12.dp)) {",
                "Column(Modifier.fillMaxSize().padding(horizontal = if (isTv) 24.dp "
                "else 13.dp, vertical = if (isTv) 19.dp else 12.dp)"
                ".qaTvPageContent(isTv, destination)) {",
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
                "            .qaTvPageContent(isTv, destination),\n"
                "    ) {",
            ),
        ),
        "poster-catalog-content-marker",
        changes,
    )
    source = patch_segment_one_of(
        source,
        "private fun LiveCatalogScreen(",
        "private fun LiveStage(",
        (
            (
                "Column(Modifier.fillMaxSize().padding(horizontal = if (isTv) 23.dp "
                "else 12.dp, vertical = if (isTv) 18.dp else 11.dp)) {",
                "Column(Modifier.fillMaxSize().padding(horizontal = if (isTv) 23.dp "
                "else 12.dp, vertical = if (isTv) 18.dp else 11.dp)"
                ".qaTvPageContent(isTv, MainDestination.LIVE)) {",
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
                "            .qaTvPageContent(isTv, MainDestination.LIVE),\n"
                "    ) {",
            ),
            (
                "            .padding(\n"
                "                horizontal = if (isTv) TV_PAGE_GUTTER else 12.dp,\n"
                "                vertical = if (isTv) TV_PAGE_GUTTER else 11.dp,\n"
                "            )\n"
                "            .onPreviewKeyEvent { event ->",
                "            .padding(\n"
                "                horizontal = if (isTv) TV_PAGE_GUTTER else 12.dp,\n"
                "                vertical = if (isTv) TV_PAGE_GUTTER else 11.dp,\n"
                "            )\n"
                "            .qaTvPageContent(isTv, MainDestination.LIVE)\n"
                "            .onPreviewKeyEvent { event ->",
            ),
        ),
        "live-content-marker",
        changes,
    )
    source = patch_segment_one_of(
        source,
        "private fun LiveStage(",
        "private fun FavoritesScreen(",
        (
            (
                "Row(Modifier.fillMaxWidth(), "
                "horizontalArrangement = Arrangement.spacedBy(12.dp)) {",
                "Row(Modifier.fillMaxWidth().qaMarker(isTv = true, "
                "description = QA_TV_LIVE_ACTIONS), "
                "horizontalArrangement = Arrangement.spacedBy(12.dp)) {",
            ),
            (
                "                    Row(\n"
                "                        Modifier.fillMaxWidth(),\n"
                "                        horizontalArrangement = Arrangement.spacedBy(12.dp),\n"
                "                    ) {",
                "                    Row(\n"
                "                        Modifier.fillMaxWidth()\n"
                "                            .qaMarker(isTv = true, description = QA_TV_LIVE_ACTIONS),\n"
                "                        horizontalArrangement = Arrangement.spacedBy(12.dp),\n"
                "                    ) {",
            ),
        ),
        "live-actions-marker",
        changes,
    )
    source = patch_segment(
        source,
        "private fun LiveCatalogScreen(",
        "private fun LiveStage(",
        "                                modifier = Modifier\n"
        "                                    .focusRequester(requester)\n"
        "                                    .onPreviewKeyEvent { event ->",
        "                                modifier = Modifier\n"
        "                                    .focusRequester(requester)\n"
        "                                    .qaMarker(true, \"$QA_TV_LIVE_CHANNEL_PREFIX${channel.id}\")\n"
        "                                    .onPreviewKeyEvent { event ->",
        "live-channel-focus-marker",
        changes,
    )
    source = patch_segment(
        source,
        "private fun LiveStage(",
        "private fun FavoritesScreen(",
        "                                .focusRequester(playRequester)\n"
        "                                .onPreviewKeyEvent { event ->",
        "                                .focusRequester(playRequester)\n"
        "                                .qaMarker(true, QA_TV_LIVE_PLAY)\n"
        "                                .onPreviewKeyEvent { event ->",
        "live-play-focus-marker",
        changes,
    )
    source = patch_segment(
        source,
        "private fun LiveStage(",
        "private fun FavoritesScreen(",
        "                                .focusRequester(favoriteRequester)\n"
        "                                .onPreviewKeyEvent { event ->",
        "                                .focusRequester(favoriteRequester)\n"
        "                                .qaMarker(true, QA_TV_LIVE_FAVORITE)\n"
        "                                .onPreviewKeyEvent { event ->",
        "live-favorite-focus-marker",
        changes,
    )
    for function_name, next_name, destination in (
        (
            "private fun FavoritesScreen(",
            "private fun UnifiedSearchScreen(",
            "FAVORITES",
        ),
        (
            "private fun UnifiedSearchScreen(",
            "private fun TvSearchField(",
            "SEARCH",
        ),
        (
            "private fun DownloadsScreen(",
            "private fun DownloadCard(",
            "DOWNLOADS",
        ),
    ):
        options = [
            (
                "Column(Modifier.fillMaxSize().padding(if (isTv) 24.dp else 13.dp)) {",
                "Column(Modifier.fillMaxSize().padding(if (isTv) 24.dp else 13.dp)"
                f".qaTvPageContent(isTv, MainDestination.{destination})) {{",
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
                f"            .qaTvPageContent(isTv, MainDestination.{destination}),\n"
                "    ) {",
            ),
        ]
        if destination == "DOWNLOADS":
            options.append(
                (
                    "    Column(\n"
                    "        Modifier\n"
                    "            .fillMaxSize()\n"
                    "            .padding(if (isTv) TV_PAGE_GUTTER else 13.dp)\n"
                    "            .onPreviewKeyEvent(handleDownloadDirectionalKey),\n"
                    "    ) {",
                    "    Column(\n"
                    "        Modifier\n"
                    "            .fillMaxSize()\n"
                    "            .padding(if (isTv) TV_PAGE_GUTTER else 13.dp)\n"
                    f"            .qaTvPageContent(isTv, MainDestination.{destination})\n"
                    "            .onPreviewKeyEvent(handleDownloadDirectionalKey),\n"
                    "    ) {",
                )
            )
        source = patch_segment_one_of(
            source,
            function_name,
            next_name,
            tuple(options),
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
                "            .qaMarker(isTv, "
                "\"$QA_TV_DOWNLOAD_CARD_PREFIX${item.downloadId}\")\n"
                "            .clip(shape)",
            ),
            (
                "            .height(if (isTv) 164.dp else 220.dp)\n"
                "            .clip(shape)",
                "            .height(if (isTv) 164.dp else 220.dp)\n"
                "            .qaMarker(isTv, "
                "\"$QA_TV_DOWNLOAD_CARD_PREFIX${item.downloadId}\")\n"
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
                "modifier = Modifier.fillMaxSize()"
                ".qaTvPageContent(isTv, MainDestination.SETTINGS),",
            ),
            (
                "        modifier = Modifier\n"
                "            .fillMaxSize()\n"
                "            .padding(if (isTv) TV_PAGE_GUTTER else 0.dp),",
                "        modifier = Modifier\n"
                "            .fillMaxSize()\n"
                "            .padding(if (isTv) TV_PAGE_GUTTER else 0.dp)\n"
                "            .qaTvPageContent(isTv, MainDestination.SETTINGS),",
            ),
        ),
        "settings-content-marker",
        changes,
    )
    source = patch_segment(
        source,
        "private suspend fun requestDownloadFocusWhenReady(",
        "@Composable\nfun MainShellScreen(",
        "): Boolean {\n    if (target.zone == DownloadFocusZone.CARD) {",
        "): Boolean {\n"
        "    qaFocusTrace(\n"
        "        event = \"request-start\",\n"
        "        target = target.qaFocusId(),\n"
        "        detail = \"visible=${listState.layoutInfo.visibleItemsInfo.map { it.index }}\",\n"
        "    )\n"
        "    if (target.zone == DownloadFocusZone.CARD) {",
        "download-request-start-trace",
        changes,
    )
    source = patch_segment(
        source,
        "private suspend fun requestDownloadFocusWhenReady(",
        "@Composable\nfun MainShellScreen(",
        "            listState.scrollToItem(target.row, scrollOffset = 0)",
        "            qaFocusTrace(\"scroll-start\", target.qaFocusId(), "
        "\"visible=${listState.layoutInfo.visibleItemsInfo.map { it.index }}\")\n"
        "            listState.scrollToItem(target.row, scrollOffset = 0)\n"
        "            qaFocusTrace(\"scroll-end\", target.qaFocusId(), "
        "\"visible=${listState.layoutInfo.visibleItemsInfo.map { it.index }}\")",
        "download-scroll-trace",
        changes,
    )
    source = patch_segment(
        source,
        "private suspend fun requestDownloadFocusWhenReady(",
        "@Composable\nfun MainShellScreen(",
        "        if (!visible) return false",
        "        qaFocusTrace(\"layout-ready\", target.qaFocusId(), "
        "\"visible=$visible items=${listState.layoutInfo.visibleItemsInfo.map { it.index }}\")\n"
        "        if (!visible) return false",
        "download-layout-ready-trace",
        changes,
    )
    source = patch_segment(
        source,
        "private suspend fun requestDownloadFocusWhenReady(",
        "@Composable\nfun MainShellScreen(",
        "        val requested = requester?.let { "
        "runCatching { it.requestFocus() }.getOrDefault(false) } == true\n"
        "        if (requested) return true",
        "        val requested = requester?.let { "
        "runCatching { it.requestFocus() }.getOrDefault(false) } == true\n"
        "        qaTraceFocusRequest(target, requested)\n"
        "        if (requested) return true",
        "download-request-result-trace",
        changes,
    )
    source = patch_segment(
        source,
        "private fun DownloadsScreen(",
        "private fun DownloadCard(",
        "        val target = nextDownloadFocus(downloads.size, current, move)\n"
        "        downloadsNavigationJob?.cancel()",
        "        val target = nextDownloadFocus(downloads.size, current, move)\n"
        "        qaFocusTrace(\n"
        "            event = \"key-target\",\n"
        "            target = current.qaFocusId(),\n"
        "            detail = \"move=$move calculated=${target?.qaFocusId() ?: \"boundary\"}\",\n"
        "        )\n"
        "        downloadsNavigationJob?.cancel()",
        "download-key-target-trace",
        changes,
    )
    source = patch_segment(
        source,
        "private fun DownloadsScreen(",
        "private fun DownloadCard(",
        "            moveDownloadFocus(pendingDownloadFocus ?: focusedTarget, move)",
        "            val before = pendingDownloadFocus ?: focusedTarget\n"
        "            val consumed = moveDownloadFocus(before, move)\n"
        "            qaFocusTrace(\n"
        "                event = \"key-result\",\n"
        "                target = before.qaFocusId(),\n"
        "                detail = \"key=${event.key} consumed=$consumed\",\n"
        "            )\n"
        "            consumed",
        "download-key-result-trace",
        changes,
    )
    source = patch_segment(
        source,
        "private fun DownloadsScreen(",
        "private fun DownloadCard(",
        "            val restoreTarget = DownloadFocusLocation.card(targetRow, DownloadFocusSlot.PRIMARY)\n"
        "            downloadsRestoreComplete = requestDownloadFocusWhenReady(",
        "            val restoreTarget = DownloadFocusLocation.card(targetRow, DownloadFocusSlot.PRIMARY)\n"
        "            qaFocusTrace(\"restore-activate\", restoreTarget.qaFocusId())\n"
        "            downloadsRestoreComplete = requestDownloadFocusWhenReady(",
        "download-restore-trace",
        changes,
    )
    for slot_name, slot_enum in (
        ("wifi", "WIFI"),
        ("schedule", "SCHEDULE"),
        ("concurrent", "CONCURRENT"),
    ):
        assignment = (
            "                        currentDownloadFocus = "
            f"DownloadFocusLocation.toolbar(DownloadFocusSlot.{slot_enum})\n"
            "                        if (pendingDownloadFocus == currentDownloadFocus) "
            "pendingDownloadFocus = null\n"
            "                        currentDownloadItemId = null"
        )
        source = patch_segment(
            source,
            "private fun DownloadsScreen(",
            "private fun DownloadCard(",
            assignment,
            assignment + "\n"
            f"                        qaFocusTrace(\"on-focused\", \"toolbar-{slot_name}\")",
            f"download-toolbar-{slot_name}-focused-trace",
            changes,
        )
    source = patch_segment(
        source,
        "private fun DownloadCard(",
        "private fun DownloadProgress(",
        "        onFocusLocation(location)\n        onFocused()",
        "        onFocusLocation(location)\n"
        "        qaFocusTrace(\"on-focused\", location.qaFocusId())\n"
        "        onFocused()",
        "download-on-focused-trace",
        changes,
    )

    missing = [marker for marker in MARKERS if marker not in source]
    if missing:
        raise ValueError(f"injection incomplete; missing markers: {missing}")
    report = {
        "schema_version": 1,
        "replacement_count": len(changes),
        "replacements": changes,
        "markers": list(MARKERS),
    }
    return source, report


def inject_file(path: Path, report_path: Path | None = None) -> dict[str, Any]:
    original = path.read_text(encoding="utf-8")
    patched, report = inject_text(original)
    report["original_sha256"] = sha256_text(original)
    report["instrumented_sha256"] = sha256_text(patched)
    report["source_path"] = path.as_posix()
    path.write_text(patched, encoding="utf-8")
    if report_path is not None:
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(
            json.dumps(report, indent=2) + "\n",
            encoding="utf-8",
        )
    return report


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    report = inject_file(args.source, args.report)
    print(
        "PASS: injected "
        f"{report['replacement_count']} debug-only quality marker layers"
    )


if __name__ == "__main__":
    main()
