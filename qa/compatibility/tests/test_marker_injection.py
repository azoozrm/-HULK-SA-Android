from __future__ import annotations

import importlib.util
from pathlib import Path
import shutil
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[3]
LAB_ROOT = ROOT / "qa/compatibility"
SOURCE = (
    ROOT
    / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
)
SPEC = importlib.util.spec_from_file_location(
    "quality_marker_injection",
    LAB_ROOT / "inject_quality_markers.py",
)
assert SPEC is not None and SPEC.loader is not None
INJECTION = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(INJECTION)


EXPECTED_REPLACEMENTS = (
    "focus-trace-imports",
    "semantics-imports",
    "marker-helpers",
    "tv-rail-marker",
    "home-content-marker",
    "poster-catalog-content-marker",
    "live-content-marker",
    "live-actions-marker",
    "live-channel-focus-marker",
    "live-play-focus-marker",
    "live-favorite-focus-marker",
    "favorites-content-marker",
    "search-content-marker",
    "downloads-content-marker",
    "downloads-list-marker",
    "download-card-marker",
    "settings-content-marker",
    "download-request-start-trace",
    "download-scroll-trace",
    "download-layout-ready-trace",
    "download-request-result-trace",
    "download-key-target-trace",
    "download-key-result-trace",
    "download-restore-trace",
    "download-toolbar-wifi-focused-trace",
    "download-toolbar-schedule-focused-trace",
    "download-toolbar-concurrent-focused-trace",
    "download-on-focused-trace",
)

HOME_CANONICAL = (
    "            .fillMaxSize()\n"
    "            .padding(bottom = if (isTv) 32.dp else 0.dp)"
)
HOME_PRODUCT = (
    "            .fillMaxSize()\n"
    "            .padding(if (isTv) TV_PAGE_GUTTER else 0.dp),"
)
POSTER_CANONICAL = (
    "Column(Modifier.fillMaxSize().padding(horizontal = if (isTv) 24.dp "
    "else 13.dp, vertical = if (isTv) 19.dp else 12.dp)) {"
)
POSTER_PRODUCT = (
    "    Column(\n"
    "        Modifier\n"
    "            .fillMaxSize()\n"
    "            .padding(\n"
    "                horizontal = if (isTv) TV_PAGE_GUTTER else 13.dp,\n"
    "                vertical = if (isTv) TV_PAGE_GUTTER else 12.dp,\n"
    "            ),\n"
    "    ) {"
)
LIVE_CANONICAL = (
    "Column(Modifier.fillMaxSize().padding(horizontal = if (isTv) 23.dp "
    "else 12.dp, vertical = if (isTv) 18.dp else 11.dp)) {"
)
LIVE_PRODUCT = (
    "    Column(\n"
    "        Modifier\n"
    "            .fillMaxSize()\n"
    "            .padding(\n"
    "                horizontal = if (isTv) TV_PAGE_GUTTER else 12.dp,\n"
    "                vertical = if (isTv) TV_PAGE_GUTTER else 11.dp,\n"
    "            )\n"
    "            .onPreviewKeyEvent { event ->\n"
    "                if (\n"
    "                    isTv && event.type == KeyEventType.KeyDown &&\n"
    "                    event.key in setOf(\n"
    "                        Key.DirectionLeft,\n"
    "                        Key.DirectionRight,\n"
    "                        Key.DirectionUp,\n"
    "                        Key.DirectionDown,\n"
    "                    )\n"
    "                ) {\n"
    "                    liveUserInteracted = true\n"
    "                    liveRestoreJob?.cancel()\n"
    "                }\n"
    "                false\n"
    "            },\n"
    "    ) {"
)
LIVE_ACTIONS_CANONICAL = (
    "Row(Modifier.fillMaxWidth(), "
    "horizontalArrangement = Arrangement.spacedBy(12.dp)) {"
)
LIVE_ACTIONS_PRODUCT = (
    "                    Row(\n"
    "                        Modifier.fillMaxWidth(),\n"
    "                        horizontalArrangement = Arrangement.spacedBy(12.dp),\n"
    "                    ) {"
)
PAGE_CANONICAL = (
    "Column(Modifier.fillMaxSize().padding(if (isTv) 24.dp else 13.dp)) {"
)
PAGE_PRODUCT = (
    "    Column(\n"
    "        Modifier\n"
    "            .fillMaxSize()\n"
    "            .padding(if (isTv) TV_PAGE_GUTTER else 13.dp),\n"
    "    ) {"
)
DOWNLOADS_PRODUCT = PAGE_PRODUCT
DOWNLOAD_CARD_CANONICAL = (
    "            .height(if (isTv) 220.dp else 220.dp)\n"
    "            .clip(shape)"
)
DOWNLOAD_CARD_PRODUCT = (
    "            .height(if (isTv) 164.dp else 220.dp)\n"
    "            .clip(shape)"
)
SETTINGS_CANONICAL = "modifier = Modifier.fillMaxSize(),"
SETTINGS_PRODUCT = (
    "        modifier = Modifier\n"
    "            .fillMaxSize()\n"
    "            .padding(if (isTv) TV_PAGE_GUTTER else 0.dp),"
)


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise AssertionError(f"{label}: expected one fixture anchor, found {count}")
    return source.replace(old, new, 1)


def segment_bounds(
    source: str,
    start_marker: str,
    end_marker: str,
    label: str,
) -> tuple[int, int]:
    start = source.find(start_marker)
    if start < 0:
        raise AssertionError(f"{label}: start marker not found")
    end = source.find(end_marker, start + len(start_marker))
    if end < 0:
        raise AssertionError(f"{label}: end marker not found")
    return start, end


def normalize_segment_shape(
    source: str,
    start_marker: str,
    end_marker: str,
    canonical: str,
    product: str,
    target: str,
    label: str,
) -> str:
    start, end = segment_bounds(source, start_marker, end_marker, label)
    segment = source[start:end]
    canonical_count = segment.count(canonical)
    product_count = segment.count(product)
    total = canonical_count + product_count
    if total != 1:
        raise AssertionError(
            f"{label}: expected exactly one qualified fixture shape, found {total} "
            f"(canonical={canonical_count}, product={product_count})"
        )
    if target == "canonical":
        if canonical_count == 1:
            return source
        normalized = segment.replace(product, canonical, 1)
    elif target == "product":
        if product_count == 1:
            return source
        normalized = segment.replace(canonical, product, 1)
    else:
        raise AssertionError(f"unsupported fixture target: {target}")
    return source[:start] + normalized + source[end:]


def layout_shape(source: str, target: str) -> str:
    """Return a deterministic canonical or PR #57 layout fixture.

    The checked-out production source may already be either supported shape.
    Tests normalize from whichever shape is current so both contracts are always
    exercised without importing Quality Lab semantics into production source.
    """

    source = normalize_segment_shape(
        source,
        "private fun CinemaHomeScreen(",
        "private fun HomeSectionPadding(",
        HOME_CANONICAL,
        HOME_PRODUCT,
        target,
        "home",
    )
    source = normalize_segment_shape(
        source,
        "private fun PosterCatalogScreen(",
        "private fun LiveCatalogScreen(",
        POSTER_CANONICAL,
        POSTER_PRODUCT,
        target,
        "poster catalog",
    )
    source = normalize_segment_shape(
        source,
        "private fun LiveCatalogScreen(",
        "private fun LiveStage(",
        LIVE_CANONICAL,
        LIVE_PRODUCT,
        target,
        "live catalog",
    )
    source = normalize_segment_shape(
        source,
        "private fun LiveStage(",
        "private fun FavoritesScreen(",
        LIVE_ACTIONS_CANONICAL,
        LIVE_ACTIONS_PRODUCT,
        target,
        "live actions",
    )
    for function_name, next_name, destination in (
        (
            "private fun FavoritesScreen(",
            "private fun UnifiedSearchScreen(",
            "favorites",
        ),
        (
            "private fun UnifiedSearchScreen(",
            "private fun TvSearchField(",
            "search",
        ),
        (
            "private fun DownloadsScreen(",
            "private fun DownloadCard(",
            "downloads",
        ),
    ):
        product_shape = DOWNLOADS_PRODUCT if destination == "downloads" else PAGE_PRODUCT
        source = normalize_segment_shape(
            source,
            function_name,
            next_name,
            PAGE_CANONICAL,
            product_shape,
            target,
            destination,
        )
    source = normalize_segment_shape(
        source,
        "private fun DownloadCard(",
        "private fun DownloadProgress(",
        DOWNLOAD_CARD_CANONICAL,
        DOWNLOAD_CARD_PRODUCT,
        target,
        "download card",
    )
    source = normalize_segment_shape(
        source,
        "private fun SettingsScreen(",
        "private fun AccountMetric(",
        SETTINGS_CANONICAL,
        SETTINGS_PRODUCT,
        target,
        "settings",
    )
    return source


def ambiguous_home_shape(source: str) -> str:
    canonical = layout_shape(source, "canonical")
    start, end = segment_bounds(
        canonical,
        "private fun CinemaHomeScreen(",
        "private fun HomeSectionPadding(",
        "ambiguous home",
    )
    segment = replace_once(
        canonical[start:end],
        HOME_CANONICAL,
        HOME_CANONICAL + "\n" + HOME_PRODUCT,
        "ambiguous home",
    )
    return canonical[:start] + segment + canonical[end:]


class QualityMarkerInjectionTest(unittest.TestCase):
    def assert_complete_injection(self, source: str) -> None:
        patched, report = INJECTION.inject_text(source)
        self.assertEqual(len(EXPECTED_REPLACEMENTS), report["replacement_count"])
        self.assertEqual(list(EXPECTED_REPLACEMENTS), report["replacements"])
        for marker in INJECTION.MARKERS:
            self.assertIn(marker, patched)
        for destination in (
            "HOME",
            "LIVE",
            "FAVORITES",
            "SEARCH",
            "DOWNLOADS",
            "SETTINGS",
        ):
            self.assertIn(
                f"qaTvPageContent(isTv, MainDestination.{destination})",
                patched,
            )
        self.assertIn("qaTvPageContent(isTv, destination)", patched)
        self.assertIn("BuildConfig.DEBUG", patched)
        self.assertIn("HULK_QA_FOCUS", patched)
        self.assertIn("second_within_500ms", patched)

    def test_current_source_injection_is_strict_disposable_and_complete(self) -> None:
        original = SOURCE.read_bytes()
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / "MainShellScreen.kt"
            report_path = Path(temp) / "quality-marker-injection.json"
            shutil.copy2(SOURCE, target)

            report = INJECTION.inject_file(target, report_path)
            patched = target.read_text(encoding="utf-8")

            self.assertEqual(len(EXPECTED_REPLACEMENTS), report["replacement_count"])
            self.assertEqual(list(EXPECTED_REPLACEMENTS), report["replacements"])
            self.assertNotEqual(
                report["original_sha256"],
                report["instrumented_sha256"],
            )
            self.assertTrue(report_path.is_file())
            for marker in INJECTION.MARKERS:
                self.assertIn(marker, patched)
            self.assertEqual(original, SOURCE.read_bytes())

            with self.assertRaisesRegex(ValueError, "already contains a marker"):
                INJECTION.inject_file(target)

    def test_canonical_layout_shape_preserves_the_fail_closed_contract(self) -> None:
        source = layout_shape(SOURCE.read_text(encoding="utf-8"), "canonical")
        self.assertNotIn("qa-tv-", source)
        self.assert_complete_injection(source)

    def test_product_layout_shape_preserves_the_same_fail_closed_contract(self) -> None:
        source = layout_shape(SOURCE.read_text(encoding="utf-8"), "product")
        self.assertNotIn("qa-tv-", source)
        self.assert_complete_injection(source)

    def test_ambiguous_supported_shape_fails_closed(self) -> None:
        ambiguous = ambiguous_home_shape(SOURCE.read_text(encoding="utf-8"))
        with self.assertRaisesRegex(ValueError, "supported source shape"):
            INJECTION.inject_text(ambiguous)

    def test_fixture_normalization_rejects_unknown_source_shape(self) -> None:
        source = SOURCE.read_text(encoding="utf-8").replace(
            HOME_CANONICAL,
            "            .fillMaxSize()\n            .padding(99.dp)",
            1,
        ).replace(
            HOME_PRODUCT,
            "            .fillMaxSize()\n            .padding(99.dp)",
            1,
        )
        with self.assertRaisesRegex(AssertionError, "qualified fixture shape"):
            layout_shape(source, "canonical")

    def test_unexpected_source_shape_fails_closed(self) -> None:
        with self.assertRaises(ValueError):
            INJECTION.inject_text("package example\n")


if __name__ == "__main__":
    unittest.main()
