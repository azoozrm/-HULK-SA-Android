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
    analyze_xml,
    analyze_rail_visual,
    analyze_run,
    analyze_download_actions,
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
download_action_markers = RUN_LAB_MODULE.download_action_markers
focused_node = RUN_LAB_MODULE.focused_node
download_focus_target = RUN_LAB_MODULE.download_focus_target
download_focus_graph = RUN_LAB_MODULE.download_focus_graph
plan_download_focus_path = RUN_LAB_MODULE.plan_download_focus_path
evaluate_page_precondition = RUN_LAB_MODULE.evaluate_page_precondition


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

    def test_focused_node_reads_text_from_semantics_descendants(self) -> None:
        xml = (
            '<hierarchy><node package="sa.hulksa.player.dev" focused="true" '
            'focusable="true" clickable="true" bounds="[0,0][200,80]">'
            '<node package="sa.hulksa.player.dev" text="ايقاف مؤقت" '
            'bounds="[20,10][180,70]" /></node></hierarchy>'
        ).encode()
        self.assertEqual("ايقاف مؤقت", focused_node(xml)["text"])

    def test_current_debug_page_evidence_overrides_stale_api28_xml(self) -> None:
        result = evaluate_page_precondition(
            "downloads",
            "launch-123",
            xml_bytes=(
                '<hierarchy><node package="sa.hulksa.player.dev" '
                'content-desc="qa-page:search" bounds="[0,0][100,100]" /></hierarchy>'
            ).encode(),
            page_evidence={
                "schema_version": 1,
                "page": "downloads",
                "launch_token": "launch-123",
            },
        )

        self.assertTrue(result["established"])
        self.assertEqual("debug_page_evidence", result["source"])
        self.assertEqual("search", result["xml_page"])
        self.assertTrue(result["ui_xml_stale"])

    def test_stale_debug_page_evidence_cannot_establish_precondition(self) -> None:
        result = evaluate_page_precondition(
            "downloads",
            "launch-current",
            xml_bytes=(
                '<hierarchy><node package="sa.hulksa.player.dev" '
                'content-desc="qa-page:search" bounds="[0,0][100,100]" /></hierarchy>'
            ).encode(),
            page_evidence={
                "schema_version": 1,
                "page": "downloads",
                "launch_token": "launch-old",
            },
        )

        self.assertFalse(result["established"])
        self.assertEqual("search", result["actual_page"])

    def test_download_fixture_prepares_repository_off_main_thread(self) -> None:
        source = (LAB_ROOT / "QaActivity.kt").read_text(encoding="utf-8")
        self.assertIn("downloadHarnessState by mutableStateOf<QaDownloadHarness?>(null)", source)
        self.assertIn("lifecycleScope.launch", source)
        self.assertIn("withContext(Dispatchers.IO)", source)
        self.assertLess(source.index("setContent {"), source.index("withContext(Dispatchers.IO)"))

    def test_download_origin_survives_activity_recreation_for_same_launch(self) -> None:
        source = (LAB_ROOT / "QaActivity.kt").read_text(encoding="utf-8")
        self.assertIn("private object QaDownloadHarnessProcessOwner", source)
        self.assertIn("activeLaunchToken == launchToken", source)
        self.assertIn("activeHarness?.origin?.close()", source)
        self.assertIn("return QaDownloadHarness(repository, server, launchToken)", source)
        self.assertNotIn("override fun onDestroy()", source)
        activity = source.split("class QaActivity", maxsplit=1)[1].split(
            "private object QaDownloadHarnessProcessOwner",
            maxsplit=1,
        )[0]
        self.assertNotIn("QaRangeServer", activity)

    def test_tv_focus_sequences_are_page_specific_and_actions_are_isolated(self) -> None:
        source = (LAB_ROOT / "run-lab.py").read_text(encoding="utf-8")
        self.assertIn('page == "live"', source)
        self.assertIn('page == "downloads"', source)
        for scope in (
            'restart("navigation")',
            'restart("wifi-action")',
            'restart("schedule-action")',
            'restart("concurrent-action")',
            'restart("pause-action")',
            'restart("priority-action")',
            'restart("cancel-action")',
        ):
            self.assertIn(scope, source)
        self.assertIn('"before_xml"', source)
        self.assertIn("node_text_with_descendants", source)

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

    def test_download_fixture_removes_process_owner_records_before_enqueue(self) -> None:
        source = (LAB_ROOT / "QaActivity.kt").read_text(encoding="utf-8")
        prepare = source.split(
            "    private fun prepare(context: Context, launchToken: String): QaDownloadHarness",
            maxsplit=1,
        )[1].split(
            "\n    }\n}\n\nprivate data class QaDownloadHarness",
            maxsplit=1,
        )[0]

        cleanup = "repository.remove(downloadId)"
        enqueue = "repeat(3) { index ->"
        self.assertIn("WorkManager can restore a previous debug-fixture worker", prepare)
        self.assertIn(cleanup, prepare)
        self.assertIn(enqueue, prepare)
        self.assertLess(prepare.index(cleanup), prepare.index(enqueue))

    def test_download_action_planning_waits_for_stable_restored_focus(self) -> None:
        source = (LAB_ROOT / "run-lab.py").read_text(encoding="utf-8")
        helper = source.split(
            "def wait_for_download_focus_stability(",
            maxsplit=1,
        )[1].split("\ndef parse_start_metrics", maxsplit=1)[0]
        audit = source.split(
            "def _deterministic_download_action_audit",
            maxsplit=1,
        )[1]
        self.assertIn("stable_for: float = 0.65", helper)
        self.assertIn("target != last_target", helper)
        self.assertIn("wait_for_download_focus_stability(", audit)
        self.assertLess(
            audit.index("wait_for_download_focus_stability("),
            audit.index("plan_download_focus_path("),
        )

    def test_product_download_focus_consumes_directional_keys_at_screen_boundary(self) -> None:
        source = (
            LAB_ROOT.parents[1]
            / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
        ).read_text(encoding="utf-8")
        policy = source.split(
            "private fun Modifier.applyDownloadFocusPolicy(",
            maxsplit=1,
        )[1].split("\n\n\n@Composable", maxsplit=1)[0]
        downloads = source.split(
            "private fun DownloadsScreen(",
            maxsplit=1,
        )[1].split("\n@Composable\nprivate fun DownloadCard", maxsplit=1)[0]
        self.assertNotIn(".onPreviewKeyEvent", policy)
        self.assertIn("var currentDownloadFocus by remember", downloads)
        self.assertIn("import androidx.compose.ui.input.key.KeyEvent", source)
        self.assertIn("val handleDownloadDirectionalKey: (KeyEvent) -> Boolean", downloads)
        self.assertIn(".onPreviewKeyEvent(handleDownloadDirectionalKey)", downloads)
        self.assertIn("Key.DirectionDown -> DownloadFocusMove.DOWN", downloads)
        self.assertIn("currentDownloadFocus?.let { moveDownloadFocus(it, move) }", downloads)
        self.assertIn("downloadsState.layoutInfo.visibleItemsInfo", downloads)
        self.assertIn(
            "downloadsState.scrollToItem(target.row, scrollOffset = 0)",
            downloads,
        )
        self.assertIn("requester.requestFocus()", downloads)
        self.assertIn("onFocusLocation = { currentDownloadFocus = it }", downloads)


    def test_download_fixture_uses_real_repository_actions_and_slow_active_transfer(self) -> None:
        source = (LAB_ROOT / "QaActivity.kt").read_text(encoding="utf-8")
        for contract in (
            "repository.pause(item.downloadId)",
            "repository.resume(item.downloadId)",
            "repository.cyclePriority(item.downloadId)",
            "repository.setConcurrentDownloads(next)",
            "repository.remove(item.downloadId)",
            'private const val QA_DOWNLOAD_WRITE_DELAY_MS = 40L',
        ):
            self.assertIn(contract, source)
        self.assertNotIn("onRetryDownload = {},", source)
        self.assertNotIn("onCycleConcurrentDownloads = {},", source)
        self.assertNotIn("onCycleDownloadPriority = {},", source)

    def test_download_action_markers_are_versioned_and_parseable(self) -> None:
        xml = (
            '<hierarchy><node package="sa.hulksa.player.dev" '
            'content-desc="qa-page:downloads,qa-download-action:pause:1,'
            'qa-download-action:priority:2" bounds="[0,0][10,10]" /></hierarchy>'
        ).encode()
        self.assertEqual({("pause", 1), ("priority", 2)}, download_action_markers(xml))



    def test_download_action_runner_matches_analyzer_required_ids(self) -> None:
        source = (LAB_ROOT / "run-lab.py").read_text(encoding="utf-8")
        required_ids = (
            "top-wifi-executes",
            "top-schedule-executes",
            "top-concurrent-executes",
            "row-1-primary",
            "row-1-pause",
            "row-1-priority",
            "row-1-priority-executes",
            "row-1-cancel",
            "row-2-cancel",
            "row-2-priority",
            "row-2-primary",
            "row-2-pause",
            "cancel-row-1-executes",
        )
        for check_id in required_ids:
            self.assertIn(f'inspect("{check_id}"', source)
        self.assertNotIn('inspect("row-1-pause-executes"', source)


class AnalyzerTests(unittest.TestCase):

    def test_download_action_audit_classifies_unreachable_and_unexecuted_controls(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            evidence = root / "focus/landscape/downloads-actions/01-step"
            evidence.mkdir(parents=True)
            for name in ("screenshot.png", "ui.xml", "logcat.txt"):
                (evidence / name).write_bytes(b"x")
            normalized, findings = analyze_download_actions(
                root,
                {"is_tv": True},
                [
                    {
                        "orientation": "landscape",
                        "checks": [
                            {
                                "id": "row-1-primary",
                                "success": False,
                                "expected_action": None,
                                "reason": "expected focused control was not reached",
                                "evidence": {
                                    "screenshot": "focus/landscape/downloads-actions/01-step/screenshot.png",
                                    "xml": "focus/landscape/downloads-actions/01-step/ui.xml",
                                    "logcat": "focus/landscape/downloads-actions/01-step/logcat.txt",
                                },
                            },
                            {
                                "id": "row-1-pause",
                                "success": False,
                                "expected_action": "pause",
                                "reason": "expected action marker was not emitted",
                                "evidence": {},
                            },
                        ],
                    }
                ],
            )
        self.assertEqual("BLOCKED", normalized[0]["status"])
        codes = {item["code"] for item in findings}
        self.assertIn("download_action_audit_incomplete", codes)
        self.assertIn("tv_download_action_unreachable", codes)
        self.assertIn("tv_download_action_not_executed", codes)
        self.assertIn("tv_download_row_navigation_incomplete", codes)

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

    def test_download_origin_and_repository_progress_are_independent(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            xml = Path(temporary) / "ui.xml"
            xml.write_text(
                '<hierarchy><node package="sa.hulksa.player.dev" '
                'content-desc="qa-page:downloads,qa-download-origin:bytes-positive" '
                'bounds="[0,0][1080,1920]" /></hierarchy>',
                encoding="utf-8",
            )

            result = analyze_xml(
                xml,
                width=1080,
                height=1920,
                density=420,
                font_scale=1.0,
                is_tv=False,
                page="downloads",
            )

        self.assertTrue(result["download_origin_progress"])
        self.assertFalse(result["download_transfer_progress"])

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

    def test_direct_boundary_bytes_override_stale_download_semantics(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.create_run(root)
            manifest_path = root / "run-manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["pages"] = [{"id": "downloads", "label": "التنزيلات"}]
            manifest["navigation"][0].update(
                {"page": "downloads", "label": "التنزيلات"}
            )
            case = manifest["cases"][0]
            case.update(
                {
                    "id": "portrait/font-100/downloads",
                    "page": "downloads",
                    "marker": "qa-page:downloads",
                    "marker_found": False,
                    "page_precondition": {
                        "established": True,
                        "expected_page": "downloads",
                        "actual_page": "downloads",
                        "source": "debug_page_evidence",
                        "xml_page": "search",
                        "debug_page": "downloads",
                        "ui_xml_stale": True,
                        "reason": None,
                    },
                }
            )
            evidence_path = root / "raw/portrait/font-100/home/download-file-evidence.json"
            evidence_path.write_text(
                json.dumps(
                    {
                        "schema_version": 1,
                        "origin_bytes": 171245571,
                        "repository_bytes": 170393600,
                        "partial_file_bytes": 36175872,
                        "completed_file_bytes": 134217728,
                        "persisted_state": {"exists": True, "length": 4712},
                        "origin_request_ledger": ["GET /fixture-1.mp4|Range=bytes=0-4194303"],
                    }
                ),
                encoding="utf-8",
            )
            case["files"]["download_file_evidence"] = (
                "raw/portrait/font-100/home/download-file-evidence.json"
            )
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            xml_path = root / "raw/portrait/font-100/home/ui.xml"
            xml_path.write_text(
                '<?xml version="1.0" encoding="UTF-8"?>'
                '<hierarchy rotation="0"><node package="sa.hulksa.player.dev" '
                'bounds="[0,0][100,200]" content-desc="qa-page:search">'
                '<node package="sa.hulksa.player.dev" bounds="[10,30][90,60]" '
                'text="التنزيلات" /></node></hierarchy>',
                encoding="utf-8",
            )

            summary = analyze_run(root)

            codes = {item["code"] for item in summary["findings"]}
            self.assertNotIn("download_transfer_no_byte_progress", codes)
            self.assertNotIn("page_start_precondition_not_established", codes)
            self.assertIn("ui_semantics_page_marker_stale", codes)
            self.assertEqual(0, summary["product_critical_count"])
            self.assertEqual(170393600, summary["cases"][0]["download_boundary"]["repository_bytes"])

    def test_mismatched_loopback_origin_blocks_dependent_transfer_assertion(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.create_run(root)
            manifest_path = root / "run-manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["pages"] = [{"id": "downloads", "label": "التنزيلات"}]
            manifest["navigation"][0].update(
                {"page": "downloads", "label": "التنزيلات"}
            )
            case = manifest["cases"][0]
            case.update(
                {
                    "id": "portrait/font-100/downloads",
                    "page": "downloads",
                    "marker": "qa-page:downloads",
                    "marker_found": True,
                }
            )
            evidence_path = root / "raw/portrait/font-100/home/download-file-evidence.json"
            evidence_path.write_text(
                json.dumps(
                    {
                        "schema_version": 2,
                        "launch_token": "launch-current",
                        "origin_base_url": "http://127.0.0.1:41000",
                        "origin_running": True,
                        "origin_bytes": 0,
                        "repository_bytes": 0,
                        "repository_candidate_urls": [
                            "http://127.0.0.1:39000/fixture-1.mp4"
                        ],
                        "failed_record_count": 3,
                        "maximum_retry_count": 3,
                        "partial_file_bytes": 0,
                        "completed_file_bytes": 0,
                        "persisted_state": {"exists": True, "length": 5002},
                        "origin_request_ledger": [],
                    }
                ),
                encoding="utf-8",
            )
            case["files"]["download_file_evidence"] = (
                "raw/portrait/font-100/home/download-file-evidence.json"
            )
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            xml_path = root / "raw/portrait/font-100/home/ui.xml"
            xml_path.write_text(
                '<?xml version="1.0" encoding="UTF-8"?>'
                '<hierarchy rotation="0"><node package="sa.hulksa.player.dev" '
                'bounds="[0,0][100,200]" content-desc="qa-page:downloads">'
                '<node package="sa.hulksa.player.dev" bounds="[10,30][90,60]" '
                'text="التنزيلات" /></node></hierarchy>',
                encoding="utf-8",
            )

            summary = analyze_run(root)

            self.assertEqual("BLOCKED", summary["overall_status"])
            self.assertEqual(0, summary["product_critical_count"])
            self.assertEqual(1, summary["primary_root_cause_count"])
            findings = {item["code"]: item for item in summary["findings"]}
            primary = findings["download_loopback_origin_unavailable"]
            dependent = findings["download_transfer_no_byte_progress"]
            self.assertEqual("primary", primary["finding_role"])
            self.assertEqual("blocked_assertion", dependent["finding_role"])
            self.assertEqual(primary["root_cause_id"], dependent["root_cause_id"])

    def test_running_matching_loopback_origin_without_requests_remains_product_failure(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.create_run(root)
            manifest_path = root / "run-manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["pages"] = [{"id": "downloads", "label": "التنزيلات"}]
            manifest["navigation"][0].update(
                {"page": "downloads", "label": "التنزيلات"}
            )
            case = manifest["cases"][0]
            case.update(
                {
                    "id": "portrait/font-100/downloads",
                    "page": "downloads",
                    "marker": "qa-page:downloads",
                    "marker_found": True,
                }
            )
            evidence_path = root / "raw/portrait/font-100/home/download-file-evidence.json"
            evidence_path.write_text(
                json.dumps(
                    {
                        "schema_version": 2,
                        "launch_token": "launch-current",
                        "origin_base_url": "http://127.0.0.1:41000",
                        "origin_running": True,
                        "origin_bytes": 0,
                        "repository_bytes": 0,
                        "repository_candidate_urls": [
                            "http://127.0.0.1:41000/fixture-1.mp4"
                        ],
                        "failed_record_count": 3,
                        "maximum_retry_count": 3,
                        "partial_file_bytes": 0,
                        "completed_file_bytes": 0,
                        "persisted_state": {"exists": True, "length": 5002},
                        "origin_request_ledger": [],
                    }
                ),
                encoding="utf-8",
            )
            case["files"]["download_file_evidence"] = (
                "raw/portrait/font-100/home/download-file-evidence.json"
            )
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            xml_path = root / "raw/portrait/font-100/home/ui.xml"
            xml_path.write_text(
                '<?xml version="1.0" encoding="UTF-8"?>'
                '<hierarchy rotation="0"><node package="sa.hulksa.player.dev" '
                'bounds="[0,0][100,200]" content-desc="qa-page:downloads">'
                '<node package="sa.hulksa.player.dev" bounds="[10,30][90,60]" '
                'text="التنزيلات" /></node></hierarchy>',
                encoding="utf-8",
            )

            summary = analyze_run(root)

            self.assertEqual("FAIL", summary["overall_status"])
            self.assertEqual(1, summary["product_critical_count"])
            codes = {item["code"] for item in summary["findings"]}
            self.assertIn("download_transfer_no_byte_progress", codes)
            self.assertNotIn("download_loopback_origin_unavailable", codes)

    def test_stale_download_file_evidence_is_blocked_by_launch_token(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.create_run(root)
            manifest_path = root / "run-manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["pages"] = [{"id": "downloads", "label": "التنزيلات"}]
            manifest["navigation"][0].update({"page": "downloads", "label": "التنزيلات"})
            case = manifest["cases"][0]
            case.update(
                {
                    "id": "portrait/font-100/downloads",
                    "page": "downloads",
                    "marker": "qa-page:downloads",
                    "marker_found": True,
                    "page_precondition": {
                        "established": True,
                        "expected_page": "downloads",
                        "actual_page": "downloads",
                        "source": "ui_xml",
                        "launch_token": "launch-current",
                        "reason": None,
                    },
                }
            )
            evidence_path = root / "raw/portrait/font-100/home/download-file-evidence.json"
            evidence_path.write_text(
                json.dumps(
                    {
                        "schema_version": 2,
                        "launch_token": "launch-stale",
                        "origin_base_url": "http://127.0.0.1:41000",
                        "origin_running": True,
                        "origin_bytes": 100,
                        "repository_bytes": 100,
                        "repository_candidate_urls": ["http://127.0.0.1:41000/fixture-1.mp4"],
                        "partial_file_bytes": 100,
                        "completed_file_bytes": 0,
                        "persisted_state": {"exists": True, "length": 100},
                        "origin_request_ledger": ["bytes=0-99"],
                    }
                ),
                encoding="utf-8",
            )
            case["files"]["download_file_evidence"] = "raw/portrait/font-100/home/download-file-evidence.json"
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            xml_path = root / "raw/portrait/font-100/home/ui.xml"
            xml_path.write_text(
                '<?xml version="1.0" encoding="UTF-8"?>'
                '<hierarchy rotation="0"><node package="sa.hulksa.player.dev" '
                'bounds="[0,0][100,200]" content-desc="qa-page:downloads">'
                '<node package="sa.hulksa.player.dev" bounds="[10,30][90,60]" '
                'text="التنزيلات" /></node></hierarchy>',
                encoding="utf-8",
            )

            summary = analyze_run(root)

            self.assertEqual("BLOCKED", summary["overall_status"])
            self.assertEqual(0, summary["product_critical_count"])
            findings = {item["code"]: item for item in summary["findings"]}
            self.assertEqual("primary", findings["download_file_evidence_launch_mismatch"]["finding_role"])
            self.assertEqual("blocked_assertion", findings["download_transfer_no_byte_progress"]["finding_role"])
            self.assertFalse(summary["cases"][0]["download_boundary"]["evidence_current"])

    def test_invalid_page_precondition_blocks_dependent_product_checks(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.create_run(root, out_of_bounds=True)
            manifest_path = root / "run-manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            case = manifest["cases"][0]
            case["marker_found"] = False
            case["page_precondition"] = {
                "established": False,
                "expected_page": "home",
                "actual_page": "search",
                "source": None,
                "xml_page": "search",
                "debug_page": "search",
                "ui_xml_stale": False,
                "reason": "expected page 'home', observed 'search'",
            }
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

            summary = analyze_run(root)

            self.assertEqual("BLOCKED", summary["overall_status"])
            self.assertEqual(0, summary["product_critical_count"])
            self.assertGreaterEqual(summary["fixture_critical_count"], 2)
            self.assertEqual(1, summary["primary_root_cause_count"])
            self.assertGreaterEqual(summary["downstream_count"], 1)
            findings = {item["code"]: item for item in summary["findings"]}
            self.assertEqual(
                "primary",
                findings["page_start_precondition_not_established"]["finding_role"],
            )
            self.assertEqual("blocked_assertion", findings["out_of_bounds"]["finding_role"])
            self.assertEqual(
                findings["page_start_precondition_not_established"]["root_cause_id"],
                findings["out_of_bounds"]["root_cause_id"],
            )

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

class DeterministicDownloadFocusTests(unittest.TestCase):
    @staticmethod
    def xml(label: str, bounds: str = "[100,100][400,180]") -> bytes:
        return (
            '<hierarchy><node package="sa.hulksa.player.dev" focused="true" '
            'focusable="true" clickable="true" '
            f'content-desc="{label}" bounds="{bounds}" /></hierarchy>'
        ).encode()

    def test_focus_can_start_on_wifi(self) -> None:
        self.assertEqual("toolbar-wifi", download_focus_target(self.xml("WiFi فقط"), 1920)[0])
        self.assertEqual([], plan_download_focus_path("toolbar-wifi", "toolbar-wifi"))

    def test_focus_can_start_on_concurrent(self) -> None:
        self.assertEqual("toolbar-concurrent", download_focus_target(self.xml("متزامنة 2"), 1920)[0])
        self.assertEqual(
            [("RIGHT", "toolbar-schedule"), ("RIGHT", "toolbar-wifi")],
            plan_download_focus_path("toolbar-concurrent", "toolbar-wifi"),
        )

    def test_focus_can_start_on_pause(self) -> None:
        xml = (
            '<hierarchy>'
            '<node package="sa.hulksa.player.dev" focused="true" focusable="true" clickable="true" '
            'content-desc="ايقاف مؤقت" bounds="[100,300][400,380]" />'
            '<node package="sa.hulksa.player.dev" focusable="true" clickable="true" '
            'content-desc="ايقاف مؤقت" bounds="[100,500][400,580]" />'
            '</hierarchy>'
        ).encode()
        self.assertEqual("row-1-primary", download_focus_target(xml, 1920)[0])
        path = plan_download_focus_path("row-1-primary", "toolbar-wifi")
        self.assertEqual([("UP", "toolbar-wifi")], path)

    def test_focus_can_start_on_rtl_rail(self) -> None:
        target, _ = download_focus_target(self.xml("التنزيلات", "[1500,100][1900,200]"), 1920)
        self.assertEqual("rail-item", target)
        self.assertEqual([("LEFT", "toolbar-wifi")], plan_download_focus_path(target, "toolbar-wifi"))

    def test_unknown_focus_is_blocked(self) -> None:
        target, _ = download_focus_target(self.xml("عنصر مجهول"), 1920)
        self.assertIsNone(target)
        self.assertIsNone(plan_download_focus_path(target, "toolbar-wifi"))

    def test_rtl_horizontal_graph_matches_contract(self) -> None:
        graph = download_focus_graph(3)
        self.assertEqual("toolbar-schedule", graph["toolbar-wifi"]["LEFT"])
        self.assertEqual("toolbar-concurrent", graph["toolbar-schedule"]["LEFT"])
        self.assertEqual("row-1-priority", graph["row-1-primary"]["LEFT"])
        self.assertEqual("row-1-cancel", graph["row-1-priority"]["LEFT"])



    def test_direct_file_evidence_kotlin_expressions_are_compile_safe(self) -> None:
        fixture = (LAB_ROOT / "QaActivity.kt").read_text(encoding="utf-8")
        self.assertNotIn('endsWith(\\".part\\")', fixture)
        self.assertIn('files.filter { it.name.endsWith(".part") }', fixture)
        self.assertIn('files.filterNot { it.name.endsWith(".part") }', fixture)

    def test_direct_file_evidence_contract_is_internal_and_independent(self) -> None:
        runner = (LAB_ROOT / "run-lab.py").read_text(encoding="utf-8")
        fixture = (LAB_ROOT / "QaActivity.kt").read_text(encoding="utf-8")
        self.assertIn('run-as", PACKAGE, "cat", "files/qa-download-file-evidence.json"', runner)
        self.assertNotIn('run-as",\n                            PACKAGE,\n                            "ls"', runner)
        for field in (
            "launch_token",
            "origin_base_url",
            "origin_running",
            "origin_bytes",
            "repository_bytes",
            "repository_candidate_urls",
            "failed_record_count",
            "maximum_retry_count",
            "partial_file_bytes",
            "completed_file_bytes",
            "persisted_state",
            "origin_request_ledger",
        ):
            self.assertIn(field, fixture)
        self.assertIn("MessageDigest.getInstance(\"SHA-256\")", fixture)
