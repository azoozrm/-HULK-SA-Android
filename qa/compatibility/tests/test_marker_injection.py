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


class QualityMarkerInjectionTest(unittest.TestCase):
    def test_injection_is_strict_disposable_and_complete(self) -> None:
        original = SOURCE.read_bytes()
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / "MainShellScreen.kt"
            report_path = Path(temp) / "quality-marker-injection.json"
            shutil.copy2(SOURCE, target)

            report = INJECTION.inject_file(target, report_path)
            patched = target.read_text(encoding="utf-8")

            self.assertEqual(13, report["replacement_count"])
            self.assertNotEqual(
                report["original_sha256"],
                report["instrumented_sha256"],
            )
            self.assertTrue(report_path.is_file())
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
            self.assertEqual(original, SOURCE.read_bytes())

            with self.assertRaisesRegex(ValueError, "already contains a marker"):
                INJECTION.inject_file(target)

    def test_v09320_layout_shape_is_fully_instrumented(self) -> None:
        modern_source = '''import androidx.compose.ui.semantics.Role
private const val CONTINUE_CATEGORY_ID = "__hulk_continue__"

private fun CinematicNavigationRail(
    selected: MainDestination,
) {
    Column(
        modifier = Modifier
            .width(railWidth)
            .fillMaxHeight()
            .focusGroup()
    )
}

private fun NavigationItem(
)

private fun CinemaHomeScreen(
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isTv) TV_PAGE_GUTTER else 0.dp),
    )
}

private fun HomeSectionPadding(
)

private fun PosterCatalogScreen(
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(
                horizontal = if (isTv) TV_PAGE_GUTTER else 13.dp,
                vertical = if (isTv) TV_PAGE_GUTTER else 12.dp,
            ),
    ) {
    }
}

private fun LiveCatalogScreen(
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(
                horizontal = if (isTv) TV_PAGE_GUTTER else 12.dp,
                vertical = if (isTv) TV_PAGE_GUTTER else 11.dp,
            ),
    ) {
    }
}

private fun LiveStage(
) {
    Box(Modifier.fillMaxWidth().padding(bottom = TV_LIVE_ACTION_INSET)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                    }
    }
}

private fun FavoritesScreen(
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(if (isTv) TV_PAGE_GUTTER else 13.dp),
    ) {
    }
}

private fun UnifiedSearchScreen(
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(if (isTv) TV_PAGE_GUTTER else 13.dp),
    ) {
    }
}

private fun TvSearchField(
)

private fun DownloadsScreen(
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(if (isTv) TV_PAGE_GUTTER else 13.dp),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun DownloadCard(
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isTv) 164.dp else 220.dp)
            .clip(shape)
    )
}

private fun DownloadProgress(
)

private fun SettingsScreen(
) {
    LazyColumn(
        state = settingsListState,
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isTv) TV_PAGE_GUTTER else 0.dp),
    )
}

private fun AccountMetric(
)
'''
        patched, report = INJECTION.inject_text(modern_source)
        self.assertEqual(13, report["replacement_count"])
        self.assertTrue(all(item.endswith(":v09320") for item in report["replacements"] if ":" in item))
        for marker in INJECTION.MARKERS:
            self.assertIn(marker, patched)
        self.assertIn("qaTvPageContent(isTv, destination)", patched)
        self.assertIn(".height(if (isTv) 164.dp else 220.dp)", patched)

    def test_supported_shape_selection_is_strict(self) -> None:
        variants = (
            ("legacy", "legacy-shape", "legacy-patched"),
            ("v09320", "modern-shape", "modern-patched"),
        )
        changes: list[str] = []
        patched = INJECTION.replace_one_of(
            "prefix modern-shape suffix",
            variants,
            "adaptive-anchor",
            changes,
        )
        self.assertEqual("prefix modern-patched suffix", patched)
        self.assertEqual(["adaptive-anchor:v09320"], changes)

        with self.assertRaisesRegex(ValueError, "found 0"):
            INJECTION.replace_one_of(
                "unknown-shape",
                variants,
                "adaptive-anchor",
                [],
            )
        with self.assertRaisesRegex(ValueError, "found 2"):
            INJECTION.replace_one_of(
                "legacy-shape modern-shape",
                variants,
                "adaptive-anchor",
                [],
            )

    def test_unexpected_source_shape_fails_closed(self) -> None:
        with self.assertRaises(ValueError):
            INJECTION.inject_text("package example\n")


if __name__ == "__main__":
    unittest.main()
