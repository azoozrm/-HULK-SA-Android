from __future__ import annotations

"""Behavioral compatibility tests layered over the preserved legacy test corpus.

The previous file mixed valuable analyzer tests with source-format assertions that
broke whenever runtime code was split into modules. The complete legacy corpus is
preserved in ``lab_contracts_legacy.py``; only brittle assertions are replaced
here with executable contracts against the imported runtime and policy modules.
"""

import importlib.util
import json
from pathlib import Path
from types import MethodType, SimpleNamespace
import sys
import tempfile
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
LEGACY_SPEC = importlib.util.spec_from_file_location(
    "compatibility_lab_contracts_legacy",
    HERE / "lab_contracts_legacy.py",
)
if LEGACY_SPEC is None or LEGACY_SPEC.loader is None:
    raise RuntimeError("cannot load preserved lab test corpus")
LEGACY = importlib.util.module_from_spec(LEGACY_SPEC)
sys.modules[LEGACY_SPEC.name] = LEGACY
LEGACY_SPEC.loader.exec_module(LEGACY)

for _name in dir(LEGACY):
    if not _name.startswith("__"):
        globals().setdefault(_name, getattr(LEGACY, _name))

ConfigTests = LEGACY.ConfigTests
AnalyzerTests = LEGACY.AnalyzerTests
DeterministicDownloadFocusTests = LEGACY.DeterministicDownloadFocusTests
RUN_LAB_MODULE = LEGACY.RUN_LAB_MODULE
CORE = RUN_LAB_MODULE.CORE

import qualification_policy as POLICY  # noqa: E402
import qualified_runtime as QUALIFIED_RUNTIME  # noqa: E402


# Synthetic analyzer captures must carry the same mandatory provenance fields as
# runtime artifacts. This keeps unit tests honest without weakening fail-closed
# production provenance enforcement.
_ORIGINAL_CREATE_RUN = AnalyzerTests.create_run


def _create_run_with_provenance(self, root: Path, *args, **kwargs) -> None:
    _ORIGINAL_CREATE_RUN(self, root, *args, **kwargs)
    manifest_path = root / "run-manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["provenance"] = {
        "source_head_sha": "1" * 40,
        "base_sha": "2" * 40,
        "tested_commit_sha": "3" * 40,
        "merge_sha": "4" * 40,
        "lab_apk_sha256": "5" * 64,
        "workflow_run_id": "100",
        "workflow_run_attempt": "1",
    }
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False),
        encoding="utf-8",
    )


AnalyzerTests.create_run = _create_run_with_provenance


def _stable_toolbar_concurrent(self) -> None:
    node = {"text": "متزامنة 2", "bounds": [10, 10, 100, 60]}
    with tempfile.TemporaryDirectory() as temporary:
        with (
            patch.object(CORE, "dump_xml", side_effect=(b"first", b"second")),
            patch.object(
                CORE,
                "download_focus_target",
                side_effect=(("toolbar-concurrent", node), ("toolbar-concurrent", node)),
            ),
            patch.object(CORE.time, "sleep"),
        ):
            stable, target, observed, xml = CORE.wait_for_supported_download_focus(
                object(),
                Path(temporary) / "initial.xml",
                1280,
                timeout=1.0,
                consecutive_reads=2,
            )
    self.assertTrue(stable)
    self.assertEqual("toolbar-concurrent", target)
    self.assertEqual(node, observed)
    self.assertEqual(b"second", xml)


ConfigTests.test_supported_download_start_accepts_stable_toolbar_concurrent = (
    _stable_toolbar_concurrent
)


def _focus_graph_is_geometry_derived(self) -> None:
    xml = (
        '<hierarchy>'
        '<node focusable="true" content-desc="كل الشبكات" bounds="[900,40][1180,120]" />'
        '<node focusable="true" content-desc="الجدولة" bounds="[600,40][880,120]" />'
        '<node focusable="true" content-desc="متزامنة 2" bounds="[300,40][580,120]" />'
        '<node focusable="true" content-desc="ايقاف مؤقت" bounds="[300,260][580,340]" />'
        '<node focusable="true" content-desc="عالية" bounds="[600,260][880,340]" />'
        '<node focusable="true" content-desc="الغاء" bounds="[900,260][1180,340]" />'
        '<node focusable="true" content-desc="ايقاف مؤقت" bounds="[300,460][580,540]" />'
        '<node focusable="true" content-desc="عادية" bounds="[600,460][880,540]" />'
        '<node focusable="true" content-desc="الغاء" bounds="[900,460][1180,540]" />'
        '</hierarchy>'
    ).encode()
    layout = QUALIFIED_RUNTIME.extract_layout(xml)
    graph = QUALIFIED_RUNTIME.build_spatial_graph(layout)
    self.assertEqual("row-1-primary", graph["toolbar-concurrent"]["DOWN"])
    self.assertEqual(
        [("DOWN", "row-1-primary")],
        QUALIFIED_RUNTIME.shortest_path(
            graph,
            "toolbar-concurrent",
            "row-1-primary",
        ),
    )


ConfigTests.test_tv_focus_sequences_are_page_specific_and_actions_are_isolated = (
    _focus_graph_is_geometry_derived
)


def _download_page_waits_for_progress_behaviorally(self) -> None:
    class FakeAdb:
        def shell(self, _args, **_kwargs):
            return CORE.CommandResult(0, b"", b"")

    lab = object.__new__(CORE.DeviceLab)
    lab.adb = FakeAdb()
    lab.args = SimpleNamespace(is_tv=True, width=1280, height=720)
    lab.current_orientation = "landscape"
    page_evidence = {
        "established": True,
        "expected_page": "downloads",
        "actual_page": "downloads",
        "source": "ui_xml",
        "launch_token": "launch-test",
        "xml_page": "downloads",
        "debug_page": "downloads",
        "ui_xml_stale": False,
        "reason": None,
    }
    with tempfile.TemporaryDirectory() as temporary:
        case_dir = Path(temporary)
        with (
            patch.object(CORE, "wait_for_page_precondition", return_value=dict(page_evidence)),
            patch.object(CORE, "wait_for_geometry", return_value=(True, (1280, 720))),
            patch.object(CORE, "wait_for_marker", return_value=True) as wait_marker,
        ):
            CORE.DeviceLab.start_page(lab, "downloads", case_dir)
    self.assertEqual(CORE.DOWNLOAD_PROGRESS_MARKER, wait_marker.call_args.args[1])
    self.assertEqual(15, wait_marker.call_args.kwargs["timeout"])


ConfigTests.test_download_capture_waits_for_positive_byte_marker = (
    _download_page_waits_for_progress_behaviorally
)


def _installed_action_audit_emits_declared_checks(self) -> None:
    targets = [
        "toolbar-wifi",
        "toolbar-schedule",
        "toolbar-concurrent",
        "row-1-primary",
        "row-1-primary",
        "row-2-primary",
        "row-1-priority",
        "row-1-cancel",
    ]
    actions = [
        "wifi",
        "schedule",
        "concurrent",
        "pause",
        "resume",
        "pause",
        "priority",
        "delete",
    ]
    expected_ids = [
        "top-wifi-executes",
        "top-schedule-executes",
        "top-concurrent-executes",
        "row-1-pause",
        "row-1-resume",
        "row-2-pause",
        "row-1-priority-executes",
        "delete-row-1-executes",
    ]
    state = {"index": -1, "target": None, "action": None, "marker_reads": 0}
    layout_xml = (
        '<hierarchy>'
        '<node focusable="true" content-desc="كل الشبكات" bounds="[900,40][1180,120]" />'
        '<node focusable="true" content-desc="الجدولة" bounds="[600,40][880,120]" />'
        '<node focusable="true" content-desc="متزامنة 2" bounds="[300,40][580,120]" />'
        '<node focusable="true" content-desc="ايقاف مؤقت" bounds="[300,260][580,340]" />'
        '<node focusable="true" content-desc="عالية" bounds="[600,260][880,340]" />'
        '<node focusable="true" content-desc="الغاء" bounds="[900,260][1180,340]" />'
        '<node focusable="true" content-desc="ايقاف مؤقت" bounds="[300,460][580,540]" />'
        '</hierarchy>'
    ).encode()

    class FakeDeviceLab:
        pass

    class FakeAdb:
        def shell(self, _args, **_kwargs):
            return SimpleNamespace(returncode=0, stdout=b"", stderr=b"")

    def safe_write(path: Path, data) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        if isinstance(data, bytes):
            path.write_bytes(data)
        else:
            path.write_text(str(data), encoding="utf-8")

    def wait_supported(_adb, _path, _width, **_kwargs):
        label = "استئناف" if state["action"] == "resume" else "ايقاف مؤقت"
        return True, state["target"], {"text": label}, layout_xml

    def focus_target(_xml, _width):
        label = "استئناف" if state["action"] == "resume" else "ايقاف مؤقت"
        return state["target"], {"text": label}

    def markers(_xml):
        state["marker_reads"] += 1
        if state["marker_reads"] == 1:
            return set()
        return {(state["action"], 1)}

    fake_core = SimpleNamespace(
        DeviceLab=FakeDeviceLab,
        LabError=RuntimeError,
        oriented_dimensions=lambda width, height, _orientation: (width, height),
        wait_for_supported_download_focus=wait_supported,
        wait_for_stable_focus=lambda _adb, expected, _path, _identify, **_kwargs: (
            True,
            expected,
            {"text": "ok"},
            layout_xml,
        ),
        download_focus_target=focus_target,
        download_action_markers=markers,
        dump_xml=lambda _adb, path, **_kwargs: (safe_write(path, layout_xml) or layout_xml),
        capture_png=lambda _adb, path: (safe_write(path, b"png") or b"png"),
        write_logcat_with_focus_trace=lambda _adb, path: (
            safe_write(path, "") or safe_write(path.with_name("focus-events.log"), "") or path.with_name("focus-events.log")
        ),
        safe_write=safe_write,
    )
    QUALIFIED_RUNTIME.install(fake_core)

    with tempfile.TemporaryDirectory() as temporary:
        lab = FakeDeviceLab()
        lab.out = Path(temporary)
        lab.args = SimpleNamespace(width=1280, height=720)
        lab.adb = FakeAdb()
        lab.manifest = {"download_actions": []}
        lab.flush_manifest = lambda: None

        def start_page(_self, _page, _path):
            state["index"] += 1
            state["target"] = targets[state["index"]]
            state["action"] = actions[state["index"]]
            state["marker_reads"] = 0
            return "", {"established": True}

        lab.start_page = MethodType(start_page, lab)
        fake_core.DeviceLab.download_action_audit(lab, "landscape")
        checks = lab.manifest["download_actions"][0]["checks"]

    self.assertEqual(expected_ids, [check["id"] for check in checks])
    self.assertTrue(all(check["success"] for check in checks))


ConfigTests.test_download_action_runner_matches_analyzer_required_ids = (
    _installed_action_audit_emits_declared_checks
)


def _unknown_start_is_fixture(self) -> None:
    findings = POLICY.classify_download_actions(
        [
            {
                "orientation": "landscape",
                "checks": [
                    {
                        "id": "top-wifi-executes",
                        "success": False,
                        "source": "FIXTURE",
                        "precondition_established": False,
                        "reason": "START_FOCUS_NOT_ESTABLISHED: observed unknown",
                    }
                ],
            }
        ]
    )
    self.assertEqual(1, len(findings))
    self.assertEqual("fixture", findings[0]["classification"])
    self.assertFalse(findings[0]["product_strict"])


ConfigTests.test_runner_blocks_unknown_start_state_as_fixture = _unknown_start_is_fixture


def _wrong_marker_is_navigation_mismatch(self) -> None:
    findings = POLICY.classify_download_actions(
        [
            {
                "orientation": "landscape",
                "checks": [
                    {
                        "id": "top-wifi-executes",
                        "success": False,
                        "source": "PRODUCT",
                        "precondition_established": True,
                        "key_press_confirmed": True,
                        "expected_action": "wifi",
                        "reason": "NAVIGATION_TARGET_MISMATCH: callback schedule:1 was emitted",
                    }
                ],
            }
        ]
    )
    self.assertEqual("navigation_target_mismatch", findings[0]["code"])
    self.assertNotEqual("action_callback_not_executed", findings[0]["code"])


ConfigTests.test_runner_classifies_wrong_marker_as_navigation_mismatch = (
    _wrong_marker_is_navigation_mismatch
)


def _external_dialog_is_non_retryable_infrastructure(self) -> None:
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        self.create_run(root)
        manifest_path = root / "run-manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["cases"][0]["marker_found"] = False
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        xml_path = root / "raw/portrait/font-100/home/ui.xml"
        xml_path.write_text(
            '<hierarchy><node package="android" resource-id="android:id/alertTitle" '
            'text="Pixel Launcher isn&apos;t responding" bounds="[0,0][100,40]" />'
            '<node package="android" resource-id="android:id/aerr_close" '
            'text="Close app" bounds="[0,40][100,80]" /></hierarchy>',
            encoding="utf-8",
        )
        summary = LEGACY.analyze_run(root)
    self.assertEqual("BLOCKED", summary["overall_status"])
    self.assertEqual(0, summary["critical_count"])
    self.assertEqual(1, summary["infrastructure_invalidity_count"])
    self.assertEqual(0, summary["infrastructure_error_count"])
    self.assertIn(
        "external_system_error_dialog",
        {item["code"] for item in summary["findings"]},
    )


AnalyzerTests.test_external_android_error_dialog_is_infrastructure = (
    _external_dialog_is_non_retryable_infrastructure
)


def _launcher_contamination_is_non_retryable_infrastructure(self) -> None:
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
        case["files"]["activity"] = "raw/portrait/font-100/home/activity.txt"
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        (case_root / "ui.xml").write_text(
            '<hierarchy><node package="com.google.android.tvlauncher" '
            'text="Buy and rent movies on your TV" bounds="[0,0][100,200]" /></hierarchy>',
            encoding="utf-8",
        )
        summary = LEGACY.analyze_run(root)
    self.assertEqual("BLOCKED", summary["overall_status"])
    self.assertEqual(0, summary["critical_count"])
    self.assertEqual(1, summary["infrastructure_invalidity_count"])
    self.assertEqual(0, summary["infrastructure_error_count"])
    codes = {item["code"] for item in summary["findings"]}
    self.assertIn("foreground_package_mismatch", codes)
    self.assertNotIn("page_marker_missing", codes)
    self.assertNotIn("empty_hierarchy", codes)


AnalyzerTests.test_launcher_capture_is_blocked_before_product_analysis = (
    _launcher_contamination_is_non_retryable_infrastructure
)


def _invalid_precondition_has_one_root(self) -> None:
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
        summary = LEGACY.analyze_run(root)
    self.assertEqual("BLOCKED", summary["overall_status"])
    self.assertEqual(0, summary["product_critical_count"])
    self.assertEqual(1, summary["fixture_critical_count"])
    self.assertEqual(1, summary["primary_root_cause_count"])
    self.assertGreaterEqual(summary["downstream_count"], 1)
    findings = {item["code"]: item for item in summary["findings"]}
    root = findings["page_start_precondition_not_established"]["root_cause_id"]
    self.assertEqual(POLICY.PRIMARY, findings["page_start_precondition_not_established"]["finding_role"])
    self.assertEqual(POLICY.DOWNSTREAM, findings["out_of_bounds"]["finding_role"])
    self.assertEqual(root, findings["out_of_bounds"]["root_cause_id"])


AnalyzerTests.test_invalid_page_precondition_blocks_dependent_product_checks = (
    _invalid_precondition_has_one_root
)


def _direct_file_evidence_is_captured_via_run_as(self) -> None:
    payload = b'{"origin_bytes":10,"repository_bytes":10}'

    class FakeAdb:
        def __init__(self) -> None:
            self.calls = []

        def shell(self, args, **_kwargs):
            self.calls.append(list(args))
            if list(args) == [
                "run-as",
                CORE.PACKAGE,
                "cat",
                "files/qa-download-file-evidence.json",
            ]:
                return CORE.CommandResult(0, payload, b"")
            return CORE.CommandResult(0, b"", b"")

    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        lab = object.__new__(CORE.DeviceLab)
        lab.out = root
        lab.raw = root / "raw"
        lab.args = SimpleNamespace()
        lab.adb = FakeAdb()
        lab.manifest = {"cases": [], "harness_errors": []}
        lab.record_harness_error = MethodType(lambda _self, _scope, _exc: None, lab)
        lab.flush_manifest = MethodType(lambda _self: None, lab)
        lab.start_page = MethodType(
            lambda _self, _page, _case_dir: (
                "",
                {
                    "established": True,
                    "expected_page": "downloads",
                    "actual_page": "downloads",
                    "source": "ui_xml",
                    "reason": None,
                },
            ),
            lab,
        )

        def fake_png(_adb, path: Path):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"png")
            return b"png"

        def fake_xml(_adb, path: Path, **_kwargs):
            data = b'<hierarchy><node package="sa.hulksa.player.dev" /></hierarchy>'
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
            return data

        with (
            patch.object(CORE, "capture_png", side_effect=fake_png),
            patch.object(CORE, "dump_xml", side_effect=fake_xml),
            patch.object(CORE, "command_text", return_value=""),
        ):
            CORE.DeviceLab.capture_case(lab, "downloads", "landscape", 1.0)

        direct_call = [
            "run-as",
            CORE.PACKAGE,
            "cat",
            "files/qa-download-file-evidence.json",
        ]
        self.assertIn(direct_call, lab.adb.calls)
        self.assertFalse(any("ls" in call for call in lab.adb.calls))
        case = lab.manifest["cases"][0]
        evidence_path = root / case["files"]["download_file_evidence"]
        self.assertEqual(payload, evidence_path.read_bytes())


DeterministicDownloadFocusTests.test_direct_file_evidence_contract_is_internal_and_independent = (
    _direct_file_evidence_is_captured_via_run_as
)
