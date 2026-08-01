from __future__ import annotations

import importlib.util
from pathlib import Path
import struct
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
SPEC = importlib.util.spec_from_file_location(
    "compatibility_run_lab_behavior",
    ROOT / "run-lab.py",
)
assert SPEC is not None and SPEC.loader is not None
LAB = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(LAB)
from lab_config import DEVICES, PAGES


class RuntimeContractTests(unittest.TestCase):
    def test_matrix_contains_exact_nine_profiles_and_physical_tv_contracts(self):
        by_id = {item["id"]: item for item in DEVICES}
        self.assertEqual(9, len(by_id))
        self.assertEqual(
            (1280, 720, 213),
            (
                by_id["android-tv-720p-api36"]["physical_width"],
                by_id["android-tv-720p-api36"]["physical_height"],
                by_id["android-tv-720p-api36"]["density"],
            ),
        )
        self.assertEqual(
            (1920, 1080, 320),
            (
                by_id["android-tv-1080p-api36"]["physical_width"],
                by_id["android-tv-1080p-api36"]["physical_height"],
                by_id["android-tv-1080p-api36"]["density"],
            ),
        )
        self.assertEqual(
            (3840, 2160, 640),
            (
                by_id["android-tv-4k-api36"]["physical_width"],
                by_id["android-tv-4k-api36"]["physical_height"],
                by_id["android-tv-4k-api36"]["density"],
            ),
        )

    def test_page_inventory_is_nonempty_unique_and_contains_downloads(self):
        page_ids = [item["id"] for item in PAGES]
        self.assertTrue(page_ids)
        self.assertEqual(len(page_ids), len(set(page_ids)))
        self.assertIn("downloads", page_ids)

    def test_png_dimensions_reads_header_and_rejects_garbage(self):
        png = b"\x89PNG\r\n\x1a\n" + b"\x00" * 8 + struct.pack(">II", 1920, 1080)
        self.assertEqual((1920, 1080), LAB.png_dimensions(png))
        self.assertIsNone(LAB.png_dimensions(b"not-a-png"))

    def test_xml_page_precondition_accepts_current_page_marker(self):
        result = LAB.evaluate_page_precondition(
            "downloads",
            "launch-1",
            xml_bytes=b'<hierarchy><node content-desc="qa-page:downloads" /></hierarchy>',
            page_evidence=None,
        )
        self.assertTrue(result["established"])
        self.assertEqual("ui_xml", result["source"])

    def test_current_debug_page_evidence_handles_stale_api28_xml(self):
        result = LAB.evaluate_page_precondition(
            "downloads",
            "launch-1",
            xml_bytes=b'<hierarchy><node content-desc="qa-page:home" /></hierarchy>',
            page_evidence={"page": "downloads", "launch_token": "launch-1"},
        )
        self.assertTrue(result["established"])
        self.assertEqual("debug_page_evidence", result["source"])
        self.assertTrue(result["ui_xml_stale"])

    def test_stale_launch_token_cannot_establish_page(self):
        result = LAB.evaluate_page_precondition(
            "downloads",
            "launch-new",
            xml_bytes=b"<hierarchy />",
            page_evidence={"page": "downloads", "launch_token": "launch-old"},
        )
        self.assertFalse(result["established"])

    def test_external_non_product_error_dialog_is_detected(self):
        xml = b'''<hierarchy><node resource-id="android:id/alertTitle" text="Launcher isn't responding" bounds="[0,0][200,50]"/><node resource-id="android:id/aerr_close" text="Close app" bounds="[20,70][180,120]"/></hierarchy>'''
        self.assertEqual((100, 95), LAB.external_error_dialog_center(xml))

    def test_hulk_error_dialog_is_not_auto_closed(self):
        xml = b'''<hierarchy><node resource-id="android:id/alertTitle" text="HULK SA isn't responding" bounds="[0,0][200,50]"/><node resource-id="android:id/aerr_close" text="Close app" bounds="[20,70][180,120]"/></hierarchy>'''
        self.assertIsNone(LAB.external_error_dialog_center(xml))

    def test_visible_packages_exposes_launcher_contamination(self):
        xml = b'''<hierarchy><node package="com.android.tv.launcher"/><node package="sa.hulksa.player.dev"/></hierarchy>'''
        self.assertEqual(
            ["com.android.tv.launcher", "sa.hulksa.player.dev"],
            LAB.visible_package_names(xml),
        )

    def test_download_action_markers_are_revisioned(self):
        xml = b'''<hierarchy><node content-desc="qa-download-action:pause:2 qa-download-action:delete:1"/></hierarchy>'''
        self.assertEqual(
            {("pause", 2), ("delete", 1)},
            LAB.download_action_markers(xml),
        )

    def test_wrapper_installs_geometry_qualified_runtime(self):
        self.assertEqual(
            "qualified_runtime",
            LAB.DeviceLab.download_action_audit.__module__,
        )


if __name__ == "__main__":
    unittest.main()
