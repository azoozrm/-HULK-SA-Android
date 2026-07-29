from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw


LAB_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(LAB_ROOT))

from analyze import (  # noqa: E402
    add_case_findings,
    analyze_rail_visual,
    analyze_run,
    download_layout_measurement,
    live_action_measurement,
    tv_content_gutter_measurement,
)
from lab_config import DEVICES, PAGES, matrix_json, validate  # noqa: E402

RUN_LAB_SPEC = importlib.util.spec_from_file_location(
    "compatibility_run_lab",
    LAB_ROOT / "run-lab.py",
)
assert RUN_LAB_SPEC and RUN_LAB_SPEC.loader
RUN_LAB_MODULE = importlib.util.module_from_spec(RUN_LAB_SPEC)
sys.modules[RUN_LAB_SPEC.name] = RUN_LAB_MODULE
RUN_LAB_SPEC.loader.exec_module(RUN_LAB_MODULE)
png_dimensions = RUN_LAB_MODULE.png_dimensions
external_error_dialog_center = RUN_LAB_MODULE.external_error_dialog_center
is_expanded_rail_focus = RUN_LAB_MODULE.is_expanded_rail_focus
visible_package_names = RUN_LAB_MODULE.visible_package_names


class ConfigTests(unittest.TestCase):
    def test_matrix_is_complete_and_serializable(self) -> None:
        validate()
        matrix = json.loads(matrix_json())
        self.assertEqual(9, len(matrix["include"]))
        self.assertEqual({"phone": 4, "tablet": 2, "tv": 3}, {
            family: sum(device["family"] == family for device in DEVICES)
            for family in ("phone", "tablet", "tv")
        })
        self.assertEqual(
            [
                "home",
                "live",
                "movies",
                "series",
                "favorites",
                "search",
                "downloads",
                "settings",
            ],
            [page["id"] for page in PAGES],
        )
        tv_4k = next(device for device in DEVICES if device["id"] == "android-tv-4k-api36")
        self.assertEqual("tv_4k", tv_4k["profile"])
        self.assertEqual("3840x2160", tv_4k["boot_skin"])

    def test_png_dimensions_reads_screencap_header(self) -> None:
        with tempfile.NamedTemporaryFile(suffix=".png") as temporary:
            Image.new("RGB", (2400, 1080), "black").save(temporary.name)
            self.assertEqual((2400, 1080), png_dimensions(Path(temporary.name).read_bytes()))
        self.assertIsNone(png_dimensions(b"not a png"))

    def test_adb_recovers_after_transient_offline_transport(self) -> None:
        offline = subprocess.CompletedProcess(
            ["adb", "devices"],
            1,
            stdout=b"",
            stderr=b"error: device offline",
        )
        waited = subprocess.CompletedProcess(["adb", "wait-for-device"], 0, b"", b"")
        recovered = subprocess.CompletedProcess(["adb", "devices"], 0, b"device", b"")
        with (
            patch(
                "compatibility_run_lab.subprocess.run",
                side_effect=(offline, waited, recovered),
            ) as mocked_run,
            patch("compatibility_run_lab.time.sleep"),
        ):
            result = RUN_LAB_MODULE.Adb().run(["devices"])
        self.assertEqual(0, result.returncode)
        self.assertEqual(b"device", result.stdout)
        self.assertEqual(3, mocked_run.call_count)

    def test_external_error_dialog_close_target_is_detected(self) -> None:
        xml = (
            '<hierarchy><node resource-id="android:id/alertTitle" '
            'text="Pixel Launcher isn&apos;t responding" bounds="[0,0][10,10]" />'
            '<node resource-id="android:id/aerr_close" text="Close app" '
            'bounds="[20,40][220,140]" /></hierarchy>'
        ).encode()
        self.assertEqual((120, 90), external_error_dialog_center(xml))

    def test_visible_package_names_exposes_launcher_contamination(self) -> None:
        xml = (
            '<hierarchy><node package="com.google.android.tvlauncher" '
            'visible-to-user="true" bounds="[0,0][1280,720]" />'
            '<node package="sa.hulksa.player.dev" visible-to-user="false" '
            'bounds="[0,0][1280,720]" /></hierarchy>'
        ).encode()
        self.assertEqual(
            ["com.google.android.tvlauncher"],
            visible_package_names(xml),
        )

    def test_expanded_rtl_rail_focus_is_detected_from_geometry(self) -> None:
        self.assertTrue(
            is_expanded_rail_focus(
                {"bounds": [1536, 420, 1900, 516]},
                1920,
            )
        )
        self.assertFalse(
            is_expanded_rail_focus(
                {"bounds": [1788, 420, 1888, 516]},
                1920,
            )
        )

    def test_download_fixture_contains_expected_client_disconnects(self) -> None:
        source = (LAB_ROOT / "QaActivity.kt").read_text(encoding="utf-8")
        self.assertIn("workers.execute { serveSafely(socket) }", source)
        self.assertIn("catch (_: IOException)", source)
        self.assertIn("catch (_: InterruptedException)", source)

    def test_download_capture_waits_for_positive_byte_marker(self) -> None:
        source = (LAB_ROOT / "run-lab.py").read_text(encoding="utf-8")
        start_page = source.split(
            "    def start_page(self, page: str, case_dir: Path)",
            maxsplit=1,
        )[1].split(
            "    def capture_case(self, page: str, orientation: str, font_scale: float)",
            maxsplit=1,
        )[0]

        self.assertIn("DOWNLOAD_PROGRESS_MARKER", start_page)
        self.assertIn("wait_for_marker(", start_page)
        self.assertIn("timeout=15", start_page)
        self.assertNotIn("3.0 if page == \"downloads\"", start_page)


class AnalyzerTests(unittest.TestCase):
    def tv_gutter_xml(self, content_bounds: str, page: str = "live") -> ET.Element:
        return ET.fromstring(
            '<hierarchy>'
            '<node package="sa.hulksa.player.dev" class="android.view.View" '
            f'content-desc="qa-tv-page-content:{page}" '
            f'bounds="{content_bounds}" />'
            '<node package="sa.hulksa.player.dev" class="android.view.View" '
            'content-desc="qa-tv-rail" bounds="[1740,0][1920,1080]" />'
            '</hierarchy>'
        )

    def test_tv_live_content_gutter_measurement_accepts_eight_dp(self) -> None:
        measurement = tv_content_gutter_measurement(
            list(self.tv_gutter_xml("[16,16][1724,1064]").iter("node")),
            width=1920,
            height=1080,
            density=320,
        )
        self.assertIsNotNone(measurement)
        assert measurement is not None
        self.assertEqual(8.0, measurement["maximum_dp"])

    def test_tv_live_content_gutter_measurement_exposes_old_twenty_three_dp_frame(self) -> None:
        measurement = tv_content_gutter_measurement(
            list(self.tv_gutter_xml("[46,36][1694,1044]").iter("node")),
            width=1920,
            height=1080,
            density=320,
        )
        self.assertIsNotNone(measurement)
        assert measurement is not None
        self.assertEqual(23.0, measurement["maximum_dp"])
        self.assertGreater(measurement["maximum_dp"], measurement["limit_dp"])

    def test_tv_page_capture_fails_when_gutter_markers_are_missing(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            case_root = root / "raw/landscape/font-100/live"
            case_root.mkdir(parents=True)
            image = Image.new("RGB", (1920, 1080), "#090a07")
            ImageDraw.Draw(image).rectangle((100, 100, 1820, 980), fill="#80661f")
            image.save(case_root / "screenshot.png")
            (case_root / "ui.xml").write_text(
                '<?xml version="1.0" encoding="UTF-8"?>'
                '<hierarchy rotation="0">'
                '<node package="sa.hulksa.player.dev" class="android.view.View" '
                'bounds="[0,0][1920,1080]" content-desc="qa-page:live" />'
                "</hierarchy>",
                encoding="utf-8",
            )
            (case_root / "logcat.txt").write_text("", encoding="utf-8")
            result, findings = add_case_findings(
                root,
                {
                    "is_tv": True,
                    "requested_width": 1920,
                    "requested_height": 1080,
                    "requested_density": 320,
                },
                {
                    "id": "landscape/font-100/live",
                    "page": "live",
                    "orientation": "landscape",
                    "font_scale": 1.0,
                    "marker_found": True,
                    "files": {
                        "screenshot": "raw/landscape/font-100/live/screenshot.png",
                        "xml": "raw/landscape/font-100/live/ui.xml",
                        "logcat": "raw/landscape/font-100/live/logcat.txt",
                    },
                },
            )

        self.assertEqual("FAIL", result["status"])
        self.assertIn(
            "tv_page_content_gutter_not_measured",
            {item["code"] for item in findings},
        )

    def test_tv_favorites_content_gutter_is_measured(self) -> None:
        measurement = tv_content_gutter_measurement(
            list(self.tv_gutter_xml("[16,16][1724,1064]", "favorites").iter("node")),
            width=1920,
            height=1080,
            density=320,
            page="favorites",
        )
        self.assertIsNotNone(measurement)
        assert measurement is not None
        self.assertEqual(8.0, measurement["maximum_dp"])

    def test_live_actions_require_physical_bottom_clearance(self) -> None:
        nodes = list(
            ET.fromstring(
                '<hierarchy><node package="sa.hulksa.player.dev" '
                'content-desc="qa-tv-live-actions" bounds="[100,900][1000,1060]" />'
                "</hierarchy>"
            ).iter("node")
        )
        measurement = live_action_measurement(nodes, height=1080, density=320)
        self.assertIsNotNone(measurement)
        assert measurement is not None
        self.assertEqual(10.0, measurement["bottom_dp"])
        self.assertLess(measurement["bottom_dp"], measurement["minimum_dp"])

    def test_two_download_cards_fit_without_overlap(self) -> None:
        nodes = list(
            ET.fromstring(
                '<hierarchy>'
                '<node package="sa.hulksa.player.dev" content-desc="qa-download-transfer:bytes-positive" '
                'bounds="[0,0][1920,1080]" />'
                '<node package="sa.hulksa.player.dev" content-desc="qa-tv-download-list" '
                'bounds="[20,340][1700,1080]" />'
                '<node package="sa.hulksa.player.dev" content-desc="qa-tv-download-card:1" '
                'bounds="[20,340][1500,668]" />'
                '<node package="sa.hulksa.player.dev" content-desc="qa-tv-download-card:2" '
                'bounds="[20,696][1500,1024]" />'
                "</hierarchy>"
            ).iter("node")
        )
        measurement = download_layout_measurement(nodes, density=320)
        self.assertIsNotNone(measurement)
        assert measurement is not None
        self.assertEqual(2, measurement["visible_card_count"])
        self.assertEqual([], measurement["overlaps"])
        self.assertTrue(measurement["transfer_progress"])

    def test_partially_scrolled_download_card_does_not_count_as_fitted(self) -> None:
        nodes = list(
            ET.fromstring(
                '<hierarchy>'
                '<node package="sa.hulksa.player.dev" '
                'content-desc="qa-download-transfer:bytes-positive" '
                'bounds="[0,0][1920,1080]" />'
                '<node package="sa.hulksa.player.dev" content-desc="qa-tv-download-list" '
                'bounds="[20,340][1700,1080]" />'
                '<node package="sa.hulksa.player.dev" content-desc="qa-tv-download-card:1" '
                'bounds="[20,300][1500,628]" />'
                '<node package="sa.hulksa.player.dev" content-desc="qa-tv-download-card:2" '
                'bounds="[20,656][1500,984]" />'
                "</hierarchy>"
            ).iter("node")
        )

        measurement = download_layout_measurement(nodes, density=320)

        self.assertIsNotNone(measurement)
        assert measurement is not None
        self.assertEqual(2, len(measurement["card_bounds_px"]))
        self.assertEqual(1, measurement["visible_card_count"])

    def create_run(self, root: Path, *, out_of_bounds: bool = False) -> None:
        case_root = root / "raw/portrait/font-100/home"
        case_root.mkdir(parents=True)
        image = Image.new("RGB", (100, 200), "#090a07")
        draw = ImageDraw.Draw(image)
        draw.rectangle((10, 20, 90, 180), fill="#80661f")
        image.save(case_root / "screenshot.png")
        bounds = "[10,30][90,60]"
        if out_of_bounds:
            bounds = "[-4,30][110,60]"
        (case_root / "ui.xml").write_text(
            '<?xml version="1.0" encoding="UTF-8"?>'
            '<hierarchy rotation="0">'
            '<node package="sa.hulksa.player.dev" class="android.view.View" '
            'bounds="[0,0][100,200]" content-desc="qa-page:home">'
            f'<node package="sa.hulksa.player.dev" class="android.widget.TextView" '
            f'bounds="{bounds}" text="الرئيسية" />'
            "</node></hierarchy>",
            encoding="utf-8",
        )
        for filename in ("logcat.txt", "crash.logcat.txt", "gfxinfo.txt", "meminfo.txt"):
            (case_root / filename).write_text("", encoding="utf-8")
        manifest = {
            "device": {
                "id": "unit-phone",
                "name": "Unit Phone",
                "family": "phone",
                "api": 35,
                "target": "google_apis",
                "arch": "x86_64",
                "profile": "pixel_6",
                "requested_width": 100,
                "requested_height": 200,
                "requested_density": 160,
                "orientations": "portrait",
                "font_scales": "1.0",
                "is_tv": False,
            },
            "pages": [{"id": "home", "label": "الرئيسية"}],
            "cases": [
                {
                    "id": "portrait/font-100/home",
                    "page": "home",
                    "orientation": "portrait",
                    "font_scale": 1.0,
                    "marker": "qa-page:home",
                    "marker_found": True,
                    "capture_error": None,
                    "start_metrics_ms": {"TotalTime": 200},
                    "files": {
                        "screenshot": "raw/portrait/font-100/home/screenshot.png",
                        "xml": "raw/portrait/font-100/home/ui.xml",
                        "logcat": "raw/portrait/font-100/home/logcat.txt",
                        "crash_log": "raw/portrait/font-100/home/crash.logcat.txt",
                        "gfxinfo": "raw/portrait/font-100/home/gfxinfo.txt",
                        "meminfo": "raw/portrait/font-100/home/meminfo.txt",
                    },
                }
            ],
            "navigation": [
                {
                    "orientation": "portrait",
                    "page": "home",
                    "label": "الرئيسية",
                    "success": True,
                    "reason": None,
                }
            ],
            "focus": [],
            "harness_errors": [],
        }
        (root / "run-manifest.json").write_text(
            json.dumps(manifest, ensure_ascii=False),
            encoding="utf-8",
        )

    def create_rail_visual(
        self,
        root: Path,
        collapsed_size_px: int,
        expanded_size_px: int,
    ) -> tuple[dict[str, object], list[dict[str, object]]]:
        rail_root = root / "focus/landscape/home"
        rail_root.mkdir(parents=True)
        states: dict[str, dict[str, str]] = {}
        for state, size in (
            ("collapsed", collapsed_size_px),
            ("expanded", expanded_size_px),
        ):
            screenshot = rail_root / f"rail-{state}.png"
            xml = rail_root / f"rail-{state}.xml"
            screenshot.write_bytes(b"visual evidence")
            x2 = 1880
            x1 = x2 - size
            y1 = 48
            y2 = y1 + size
            xml.write_text(
                '<?xml version="1.0" encoding="UTF-8"?>'
                '<hierarchy rotation="0">'
                '<node package="sa.hulksa.player.dev" class="android.view.View" '
                'bounds="[0,0][1920,1080]">'
                '<node package="sa.hulksa.player.dev" class="android.widget.ImageView" '
                'content-desc="HULK SA" bounds="[1166,0][1318,81]" />'
                '<node package="sa.hulksa.player.dev" class="android.widget.ImageView" '
                f'content-desc="HULK SA" bounds="[{x1},{y1}][{x2},{y2}]" />'
                "</node></hierarchy>",
                encoding="utf-8",
            )
            states[state] = {
                "screenshot": screenshot.relative_to(root).as_posix(),
                "xml": xml.relative_to(root).as_posix(),
            }
        device: dict[str, object] = {
            "is_tv": True,
            "orientations": "landscape",
            "requested_width": 1920,
            "requested_height": 1080,
            "requested_density": 320,
        }
        entries: list[dict[str, object]] = [
            {
                "orientation": "landscape",
                "page": "home",
                "rail_visual": states,
            }
        ]
        return device, entries

    def test_clean_capture_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.create_run(root)
            summary = analyze_run(root)
            self.assertEqual("PASS", summary["overall_status"])
            self.assertEqual(0, summary["critical_count"])
            self.assertTrue((root / "REPORT.html").is_file())
            self.assertTrue((root / "REPORT.md").is_file())
            self.assertTrue((root / "junit.xml").is_file())

    def test_out_of_bounds_is_critical(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.create_run(root, out_of_bounds=True)
            summary = analyze_run(root)
            self.assertEqual("FAIL", summary["overall_status"])
            self.assertGreaterEqual(summary["critical_count"], 1)
            self.assertIn(
                "out_of_bounds",
                {item["code"] for item in summary["findings"]},
            )

    def test_external_android_error_dialog_is_infrastructure(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.create_run(root)
            manifest_path = root / "run-manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["cases"][0]["marker_found"] = False
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            xml_path = root / "raw/portrait/font-100/home/ui.xml"
            xml_path.write_text(
                '<hierarchy><node package="android" '
                'resource-id="android:id/alertTitle" '
                'text="Pixel Launcher isn&apos;t responding" bounds="[0,0][100,40]" />'
                '<node package="android" resource-id="android:id/aerr_close" '
                'text="Close app" bounds="[0,40][100,80]" /></hierarchy>',
                encoding="utf-8",
            )
            summary = analyze_run(root)
            self.assertEqual("BLOCKED", summary["overall_status"])
            self.assertEqual(0, summary["critical_count"])
            self.assertEqual(1, summary["infrastructure_error_count"])
            self.assertIn(
                "external_system_error_dialog",
                {item["code"] for item in summary["findings"]},
            )

    def test_launcher_capture_is_blocked_before_product_analysis(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.create_run(root)
            manifest_path = root / "run-manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            case = manifest["cases"][0]
            case["marker_found"] = False
            case_root = root / "raw/portrait/font-100/home"
            activity_path = case_root / "activity.txt"
            activity_path.write_text(
                "topResumedActivity=ActivityRecord{123 u0 "
                "com.google.android.tvlauncher/.dialog.ShowDialogsActivity t5}\n",
                encoding="utf-8",
            )
            case["files"]["activity"] = (
                "raw/portrait/font-100/home/activity.txt"
            )
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            xml_path = case_root / "ui.xml"
            xml_path.write_text(
                '<hierarchy><node package="com.google.android.tvlauncher" '
                'text="Buy and rent movies on your TV" '
                'bounds="[0,0][100,200]" /></hierarchy>',
                encoding="utf-8",
            )

            summary = analyze_run(root)

            self.assertEqual("BLOCKED", summary["overall_status"])
            self.assertEqual(0, summary["critical_count"])
            self.assertEqual(1, summary["infrastructure_error_count"])
            codes = {item["code"] for item in summary["findings"]}
            self.assertIn("foreground_package_mismatch", codes)
            self.assertNotIn("page_marker_missing", codes)
            self.assertNotIn("empty_hierarchy", codes)

    def test_unstable_rail_logo_is_a_critical_visual_failure(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            device, entries = self.create_rail_visual(root, 60, 132)
            visual, findings = analyze_rail_visual(root, device, entries)
            self.assertEqual("FAIL", visual[0]["status"])
            codes = {item["code"] for item in findings}
            self.assertIn("rail_logo_size_out_of_policy", codes)
            self.assertIn("rail_logo_size_instability", codes)

    def test_fixed_60dp_logo_fails_xiaomi_density_ratio_gate(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            device, entries = self.create_rail_visual(root, 120, 120)
            visual, findings = analyze_rail_visual(root, device, entries)
            self.assertEqual("FAIL", visual[0]["status"])
            self.assertIn(
                "rail_logo_size_out_of_policy",
                {item["code"] for item in findings},
            )

    def test_stable_three_percent_rail_logo_passes_visual_gate(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            device, entries = self.create_rail_visual(root, 60, 60)
            visual, findings = analyze_rail_visual(root, device, entries)
            self.assertEqual("PASS", visual[0]["status"])
            self.assertEqual([], findings)
            self.assertEqual(0.0, visual[0]["state_delta_dp"])


if __name__ == "__main__":
    unittest.main()
