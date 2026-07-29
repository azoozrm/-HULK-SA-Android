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

    def test_unexpected_source_shape_fails_closed(self) -> None:
        with self.assertRaises(ValueError):
            INJECTION.inject_text("package example\n")


if __name__ == "__main__":
    unittest.main()
