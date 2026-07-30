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
    "semantics-imports",
    "marker-helpers",
    "tv-rail-marker",
    "home-content-marker",
    "poster-catalog-content-marker",
    "live-content-marker",
    "live-actions-marker",
    "favorites-content-marker",
    "search-content-marker",
    "downloads-content-marker",
    "downloads-list-marker",
    "download-card-marker",
    "settings-content-marker",
)

HOME_LEGACY = (
    "            .fillMaxSize()\n"
    "            .padding(bottom = if (isTv) 32.dp else 0.dp)"
)
HOME_PRODUCT = (
    "            .fillMaxSize()\n"
    "            .padding(if (isTv) TV_PAGE_GUTTER else 0.dp),"
)


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise AssertionError(f"{label}: expected one fixture anchor, found {count}")
    return source.replace(old, new, 1)


def patch_segment(
    source: str,
    start_marker: str,
    end_marker: str,
    old: str,
    new: str,
    label: str,
) -> str:
    start = source.index(start_marker)
    end = source.index(end_marker, start + len(start_marker))
    segment = replace_once(source[start:end], old, new, label)
    return source[:start] + segment + source[end:]


def product_layout_shape(source: str) -> str:
    """Return a product-layout fixture from either supported source shape.

    The Quality PR gate runs this test suite against the exact pull-request
    source. A product PR can therefore already contain the v0.9.3.20 anchors.
    Treat that as a valid fixture input instead of trying to transform it a
    second time. Unknown or mixed home shapes still fail closed.
    """

    home_start = source.index("private fun CinemaHomeScreen(")
    home_end = source.index("private fun HomeSectionPadding(", home_start)
    home_segment = source[home_start:home_end]
    legacy_count = home_segment.count(HOME_LEGACY)
    product_count = home_segment.count(HOME_PRODUCT)

    if legacy_count == 0 and product_count == 1:
        return source
    if legacy_count != 1 or product_count != 0:
        raise AssertionError(
            "home: expected exactly one legacy or product fixture anchor; "
            f"legacy={legacy_count}, product={product_count}"
        )

    source = patch_segment(
        source,
        "private fun CinemaHomeScreen(",
        "private fun HomeSectionPadding(",
        HOME_LEGACY,
        HOME_PRODUCT,
        "home",
    )
    source = patch_segment(
        source,
        "private fun PosterCatalogScreen(",
        "private fun LiveCatalogScreen(",
        "Column(Modifier.fillMaxSize().padding(horizontal = if (isTv) 24.dp "
        "else 13.dp, vertical = if (isTv) 19.dp else 12.dp)) {",
        "    Column(\n"
        "        Modifier\n"
        "            .fillMaxSize()\n"
        "            .padding(\n"
        "                horizontal = if (isTv) TV_PAGE_GUTTER else 13.dp,\n"
        "                vertical = if (isTv) TV_PAGE_GUTTER else 12.dp,\n"
        "            ),\n"
        "    ) {",
        "poster catalog",
    )
    source = patch_segment(
        source,
        "private fun LiveCatalogScreen(",
        "private fun LiveStage(",
        "Column(Modifier.fillMaxSize().padding(horizontal = if (isTv) 23.dp "
        "else 12.dp, vertical = if (isTv) 18.dp else 11.dp)) {",
        "    Column(\n"
        "        Modifier\n"
        "            .fillMaxSize()\n"
        "            .padding(\n"
        "                horizontal = if (isTv) TV_PAGE_GUTTER else 12.dp,\n"
        "                vertical = if (isTv) TV_PAGE_GUTTER else 11.dp,\n"
        "            ),\n"
        "    ) {",
        "live catalog",
    )
    source = patch_segment(
        source,
        "private fun LiveStage(",
        "private fun FavoritesScreen(",
        "Row(Modifier.fillMaxWidth(), "
        "horizontalArrangement = Arrangement.spacedBy(12.dp)) {",
        "                    Row(\n"
        "                        Modifier.fillMaxWidth(),\n"
        "                        horizontalArrangement = Arrangement.spacedBy(12.dp),\n"
        "                    ) {",
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
        source = patch_segment(
            source,
            function_name,
            next_name,
            "Column(Modifier.fillMaxSize().padding(if (isTv) 24.dp else 13.dp)) {",
            "    Column(\n"
            "        Modifier\n"
            "            .fillMaxSize()\n"
            "            .padding(if (isTv) TV_PAGE_GUTTER else 13.dp),\n"
            "    ) {",
            destination,
        )
    source = patch_segment(
        source,
        "private fun DownloadCard(",
        "private fun DownloadProgress(",
        "            .height(if (isTv) 220.dp else 220.dp)\n"
        "            .clip(shape)",
        "            .height(if (isTv) 164.dp else 220.dp)\n"
        "            .clip(shape)",
        "download card",
    )
    source = patch_segment(
        source,
        "private fun SettingsScreen(",
        "private fun AccountMetric(",
        "modifier = Modifier.fillMaxSize(),",
        "        modifier = Modifier\n"
        "            .fillMaxSize()\n"
        "            .padding(if (isTv) TV_PAGE_GUTTER else 0.dp),",
        "settings",
    )
    return source


class QualityMarkerInjectionTest(unittest.TestCase):
    def assert_complete_injection(self, source: str) -> None:
        patched, report = INJECTION.inject_text(source)
        self.assertEqual(13, report["replacement_count"])
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

    def test_injection_is_strict_disposable_and_complete(self) -> None:
        original = SOURCE.read_bytes()
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / "MainShellScreen.kt"
            report_path = Path(temp) / "quality-marker-injection.json"
            shutil.copy2(SOURCE, target)

            report = INJECTION.inject_file(target, report_path)
            patched = target.read_text(encoding="utf-8")

            self.assertEqual(13, report["replacement_count"])
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

    def test_product_layout_shape_preserves_the_same_fail_closed_contract(self) -> None:
        source = product_layout_shape(SOURCE.read_text(encoding="utf-8"))
        self.assertNotIn("qa-tv-", source)
        self.assert_complete_injection(source)

    def test_product_layout_fixture_builder_accepts_already_product_source(self) -> None:
        source = product_layout_shape(SOURCE.read_text(encoding="utf-8"))
        self.assertEqual(source, product_layout_shape(source))
        self.assert_complete_injection(source)

    def test_ambiguous_supported_shape_fails_closed(self) -> None:
        product = product_layout_shape(SOURCE.read_text(encoding="utf-8"))
        ambiguous = patch_segment(
            product,
            "private fun CinemaHomeScreen(",
            "private fun HomeSectionPadding(",
            HOME_PRODUCT,
            HOME_LEGACY + "\n" + HOME_PRODUCT,
            "ambiguous home",
        )
        with self.assertRaisesRegex(ValueError, "supported source shape"):
            INJECTION.inject_text(ambiguous)

    def test_unexpected_source_shape_fails_closed(self) -> None:
        with self.assertRaises(ValueError):
            INJECTION.inject_text("package example\n")


if __name__ == "__main__":
    unittest.main()
