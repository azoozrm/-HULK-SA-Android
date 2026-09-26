"""Deterministic tests for the HULK SA Android lab diagnostics tools.

These tests do not require a device or the Android SDK. They cover the pure parsers and
the safety guards of the shell tools using static fixtures.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parents[1]
FIXTURES = Path(__file__).resolve().parent / "fixtures"

sys.path.insert(0, str(TOOLS_DIR))

import benchmark_analysis  # noqa: E402
import physical_preflight  # noqa: E402
import uiautomator_focus  # noqa: E402


class BenchmarkAnalysisTest(unittest.TestCase):
    def setUp(self) -> None:
        self.data = benchmark_analysis.load_benchmark_data(FIXTURES / "benchmarkData.sample.json")
        self.summaries = benchmark_analysis.summarize_benchmarks(self.data)

    def test_summary_preserves_identity(self) -> None:
        self.assertEqual(1, len(self.summaries))
        self.assertEqual(
            "sa.hulksa.player.macrobenchmark.FirstEntryNavigationBenchmarks",
            self.summaries[0]["className"],
        )
        self.assertEqual("firstEntryNavigationFromTvMain", self.summaries[0]["name"])

    def test_valid_metric_classification(self) -> None:
        metric = self.summaries[0]["metrics"]["frameCount"]
        self.assertEqual("VALID", metric["classification"]["status"])
        self.assertEqual(3, metric["classification"]["count"])
        self.assertFalse(metric["classification"]["noisy"])
        self.assertEqual([10.0, 11.0, 12.0], metric["runs"])

    def test_empty_metric_is_missing_not_guessed(self) -> None:
        metric = self.summaries[0]["metrics"]["emptyMetric"]
        self.assertEqual("MISSING", metric["classification"]["status"])

    def test_absent_metric_is_unknown(self) -> None:
        self.assertEqual("UNKNOWN", benchmark_analysis.classify_metric(None)["status"])

    def test_non_finite_sample_is_invalid(self) -> None:
        import math

        result = benchmark_analysis.classify_metric({"runs": [1.0, math.inf]})
        self.assertEqual("INVALID", result["status"])

    def test_compilation_context_labels_test_apk(self) -> None:
        context = benchmark_analysis.compilation_context(self.data)
        self.assertEqual("run-from-apk", context["test_apk_context_compilationMode"])
        self.assertEqual(28, context["test_apk_context_sdk"])
        self.assertIn("TEST APK", context["note"])


class UiAutomatorFocusTest(unittest.TestCase):
    def test_extracts_only_focused_nodes(self) -> None:
        nodes = uiautomator_focus.focused_nodes(FIXTURES / "ui-focus.sample.xml")
        self.assertEqual(1, len(nodes))
        self.assertEqual("كود الدخول", nodes[0]["content_desc"])
        self.assertEqual("android.widget.EditText", nodes[0]["class"])

    def test_primary_label_prefers_content_description(self) -> None:
        nodes = uiautomator_focus.focused_nodes(FIXTURES / "ui-focus.sample.xml")
        self.assertEqual("كود الدخول", uiautomator_focus.primary_label(nodes))

    def test_focus_parser_never_emits_editable_text(self) -> None:
        nodes = uiautomator_focus.focused_nodes(FIXTURES / "ui-focus-secret.sample.xml")
        dumped = json.dumps(nodes, ensure_ascii=False)
        self.assertNotIn("HULK-SECRET-ACCESS-0001", dumped)
        self.assertNotIn("HULK-SECRET-PASSWORD-9999", dumped)
        for node in nodes:
            self.assertNotIn("text", node)

    def test_primary_label_ignores_editable_text(self) -> None:
        nodes = uiautomator_focus.focused_nodes(FIXTURES / "ui-focus-secret.sample.xml")
        without_description = [n for n in nodes if n["content_desc"] == ""]
        self.assertTrue(without_description)
        for node in without_description:
            self.assertEqual("", uiautomator_focus.primary_label([node]))
        self.assertEqual("كود الدخول", uiautomator_focus.primary_label(nodes))

    def test_focus_parser_source_never_references_node_text(self) -> None:
        source = (TOOLS_DIR / "uiautomator_focus.py").read_text(encoding="utf-8")
        self.assertNotIn('"text"', source)
        self.assertNotIn('get("text")', source)


class ApkInspectInstrumentationTargetTest(unittest.TestCase):
    """Gate 4D1b: annotated aapt2 targetPackage parsing in the APK inspector."""

    def extract_target(self, xmltree: Path) -> subprocess.CompletedProcess:
        return subprocess.run(
            [
                "bash",
                "-c",
                'source "$1"; lab_instrumentation_target_package "$2"',
                "bash",
                str(TOOLS_DIR / "lib-lab.sh"),
                str(xmltree),
            ],
            capture_output=True,
            text=True,
            check=False,
        )

    def test_annotated_aapt2_instrumentation_target_resolves(self) -> None:
        result = self.extract_target(FIXTURES / "apk-manifest-instrumentation.sample.txt")
        self.assertEqual(0, result.returncode)
        self.assertEqual("sa.hulksa.player.dev", result.stdout.strip())

    def test_ordinary_app_manifest_yields_no_instrumentation_target(self) -> None:
        result = self.extract_target(FIXTURES / "apk-manifest-ordinary-app.sample.txt")
        self.assertEqual(0, result.returncode)
        self.assertEqual("", result.stdout.strip())

    def test_resource_id_annotation_value_is_not_hardcoded(self) -> None:
        fragment = (
            "      E: instrumentation (line=9)\n"
            "        A: http://schemas.android.com/apk/res/android:targetPackage(0x01010555)"
            '="sa.hulksa.player.dev" (Raw: "sa.hulksa.player.dev")\n'
        )
        with tempfile.TemporaryDirectory() as tmp:
            xmltree = Path(tmp) / "manifest.xml"
            xmltree.write_text(fragment, encoding="utf-8")
            result = self.extract_target(xmltree)
        self.assertEqual(0, result.returncode)
        self.assertEqual("sa.hulksa.player.dev", result.stdout.strip())

    def test_legacy_no_resource_id_form_still_resolves(self) -> None:
        fragment = (
            "      E: instrumentation (line=9)\n"
            '        A: android:targetPackage="sa.hulksa.player.dev"\n'
        )
        with tempfile.TemporaryDirectory() as tmp:
            xmltree = Path(tmp) / "manifest.xml"
            xmltree.write_text(fragment, encoding="utf-8")
            result = self.extract_target(xmltree)
        self.assertEqual(0, result.returncode)
        self.assertEqual("sa.hulksa.player.dev", result.stdout.strip())

    def test_target_outside_instrumentation_element_is_ignored(self) -> None:
        fragment = (
            "N: android=http://schemas.android.com/apk/res/android (line=2)\n"
            "  E: manifest (line=2)\n"
            "    A: http://schemas.android.com/apk/res/android:targetPackage(0x01010021)"
            '="wrong.package" (Raw: "wrong.package")\n'
            "    E: instrumentation (line=9)\n"
            "      A: http://schemas.android.com/apk/res/android:targetPackage(0x01010021)"
            '="sa.hulksa.player.dev" (Raw: "sa.hulksa.player.dev")\n'
        )
        with tempfile.TemporaryDirectory() as tmp:
            xmltree = Path(tmp) / "manifest.xml"
            xmltree.write_text(fragment, encoding="utf-8")
            result = self.extract_target(xmltree)
        self.assertEqual(0, result.returncode)
        self.assertEqual("sa.hulksa.player.dev", result.stdout.strip())


class ToolGuardTest(unittest.TestCase):
    def run_tool(self, relative: str, *args: str) -> subprocess.CompletedProcess:
        return subprocess.run(
            [str(TOOLS_DIR / relative), *args],
            capture_output=True,
            text=True,
            check=False,
        )

    def test_apk_inspect_requires_argument(self) -> None:
        result = self.run_tool("apk-inspect.sh")
        self.assertEqual(2, result.returncode)

    def test_apk_inspect_rejects_missing_file(self) -> None:
        result = self.run_tool("apk-inspect.sh", "/nonexistent/does-not-exist.apk")
        self.assertEqual(3, result.returncode)
        self.assertIn("not found", result.stderr)

    def test_focus_probe_refuses_protected_package(self) -> None:
        result = self.run_tool("focus-dpad-probe.sh", "sa.hulksa.player", "/tmp/hulk-lab-guard")
        self.assertEqual(12, result.returncode)
        self.assertIn("protected package", result.stderr)

    def test_runtime_capture_refuses_protected_dev_package(self) -> None:
        result = self.run_tool("runtime-capture.sh", "sa.hulksa.player.dev", "/tmp/hulk-lab-guard")
        self.assertEqual(12, result.returncode)
        self.assertIn("protected package", result.stderr)

    def test_focus_probe_never_persists_raw_ui_xml(self) -> None:
        script = (TOOLS_DIR / "focus-dpad-probe.sh").read_text(encoding="utf-8")
        self.assertIn("mktemp", script)
        self.assertNotIn('"$OUT/ui-', script)
        self.assertNotIn("ui-before.xml", script)
        self.assertNotIn("ui-after.xml", script)

    def test_runtime_capture_ui_capture_is_opt_in(self) -> None:
        script = (TOOLS_DIR / "runtime-capture.sh").read_text(encoding="utf-8")
        self.assertIn("--capture-ui", script)
        self.assertIn("CAPTURE_UI=false", script)
        self.assertIn("credential-safe default", script)

    def test_focus_probe_registers_interruption_safe_cleanup(self) -> None:
        script = (TOOLS_DIR / "focus-dpad-probe.sh").read_text(encoding="utf-8")
        self.assertIn("cleanup_evidence()", script)
        self.assertIn("trap 'cleanup_evidence' EXIT", script)
        self.assertIn("trap 'cleanup_evidence; exit 130' INT TERM HUP", script)
        cleanup = script[script.index("cleanup_evidence()"):script.index("trap 'cleanup_evidence'")]
        self.assertIn('rm -f "$LAB_TMP_FILE"', cleanup)
        self.assertIn('shell rm -f "$REMOTE_UI"', cleanup)
        self.assertNotIn("$OUT", cleanup)

    def test_runtime_capture_registers_remote_cleanup_when_capture_ui(self) -> None:
        script = (TOOLS_DIR / "runtime-capture.sh").read_text(encoding="utf-8")
        self.assertIn("cleanup_remote_ui()", script)
        self.assertIn('if [[ "$CAPTURE_UI" == true ]]', script)
        self.assertIn("trap 'cleanup_remote_ui' EXIT", script)
        self.assertIn("trap 'cleanup_remote_ui; exit 130' INT TERM HUP", script)
        cleanup = script[script.index("cleanup_remote_ui()"):script.index("trap 'cleanup_remote_ui'")]
        self.assertIn('shell rm -f "$REMOTE_UI"', cleanup)
        # Cleanup must never delete intentionally produced evidence files.
        self.assertNotIn("$OUT", cleanup)
        self.assertNotIn("rm -rf", cleanup)

    def test_perfetto_analyze_requires_json(self) -> None:
        result = self.run_tool("perfetto-analyze.sh")
        self.assertEqual(2, result.returncode)

    def test_perfetto_analyze_reports_unknown_target_compilation(self) -> None:
        result = self.run_tool(
            "perfetto-analyze.sh",
            str(FIXTURES / "benchmarkData.sample.json"),
        )
        self.assertEqual(0, result.returncode)
        self.assertIn("target_compilation=UNKNOWN", result.stdout)
        self.assertIn('"status": "VALID"', result.stdout)

    def test_focus_probe_refuses_persistent_preview_and_benchmark(self) -> None:
        for package in ("sa.hulksa.player.preview", "sa.hulksa.player.benchmark"):
            with self.subTest(package=package):
                result = self.run_tool("focus-dpad-probe.sh", package, "/tmp/hulk-lab-guard")
                self.assertEqual(12, result.returncode)
                self.assertIn("protected package", result.stderr)

    def test_runtime_capture_refuses_persistent_preview_and_benchmark(self) -> None:
        for package in ("sa.hulksa.player.preview", "sa.hulksa.player.benchmark"):
            with self.subTest(package=package):
                result = self.run_tool("runtime-capture.sh", package, "/tmp/hulk-lab-guard")
                self.assertEqual(12, result.returncode)
                self.assertIn("protected package", result.stderr)


def _run_lab(body: str, *arguments: str, env: dict | None = None) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["bash", "-c", body, "bash", str(TOOLS_DIR / "lib-lab.sh"), *arguments],
        capture_output=True,
        text=True,
        check=False,
        env=env,
    )


class LabPackageSafetyTest(unittest.TestCase):
    """ES-01: generic diagnostics refuse all protected/persistent identities."""

    PROTECTED = (
        "sa.hulksa.player",
        "sa.hulksa.player.dev",
        "sa.hulksa.player.preview",
        "sa.hulksa.player.benchmark",
    )

    def test_generic_guard_rejects_all_protected_identities(self) -> None:
        for package in self.PROTECTED:
            with self.subTest(package=package):
                result = _run_lab('source "$1"; lab_guard_test_package "$2"', package)
                self.assertEqual(12, result.returncode)
                self.assertIn("protected package", result.stderr)

    def test_generic_guard_allows_disposable_package(self) -> None:
        result = _run_lab(
            'source "$1"; lab_guard_test_package "$2"',
            "sa.hulksa.player.fixture.disposable",
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_package_classification_is_exact(self) -> None:
        expected = {
            "sa.hulksa.player": "production",
            "sa.hulksa.player.dev": "dev",
            "sa.hulksa.player.preview": "persistent-preview",
            "sa.hulksa.player.benchmark": "persistent-benchmark",
            "sa.hulksa.player.fixture.disposable": "disposable",
        }
        for package, role in expected.items():
            with self.subTest(package=package):
                result = _run_lab('source "$1"; lab_package_class "$2"', package)
                self.assertEqual(0, result.returncode)
                self.assertEqual(role, result.stdout.strip())

    def test_dedicated_persistent_identities_remain_classifiable(self) -> None:
        """The dedicated refresh path classifies persistent packages without the generic guard."""
        for package, role in (
            ("sa.hulksa.player.preview", "persistent-preview"),
            ("sa.hulksa.player.benchmark", "persistent-benchmark"),
        ):
            with self.subTest(package=package):
                result = _run_lab('source "$1"; lab_package_class "$2"', package)
                self.assertEqual(0, result.returncode)
                self.assertEqual(role, result.stdout.strip())


class DeviceSelectionTest(unittest.TestCase):
    """ES-07: explicit serial selection; no silent first-device choice."""

    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.bin_dir = Path(self.tmp.name) / "bin"
        self.bin_dir.mkdir()
        adb = self.bin_dir / "adb"
        adb.write_text(
            "#!/usr/bin/env bash\n"
            'if [[ "${1:-}" == "devices" ]]; then cat "$FAKE_ADB_DEVICES"; exit 0; fi\n'
            "exit 0\n",
            encoding="utf-8",
        )
        adb.chmod(0o755)
        self.devices_file = Path(self.tmp.name) / "devices.txt"
        self.env = dict(os.environ)
        self.env["PATH"] = f"{self.bin_dir}:{self.env['PATH']}"

    def set_devices(self, *entries: str) -> None:
        body = "List of devices attached\n" + "".join(f"{entry}\n" for entry in entries)
        self.devices_file.write_text(body, encoding="utf-8")
        self.env["FAKE_ADB_DEVICES"] = str(self.devices_file)

    def select_serial(self, explicit: str = "") -> subprocess.CompletedProcess:
        if explicit:
            self.env["HULK_ADB_SERIAL"] = explicit
        else:
            self.env.pop("HULK_ADB_SERIAL", None)
        return _run_lab('source "$1"; lab_adb_serial', env=self.env)

    def test_single_device_is_unambiguous(self) -> None:
        self.set_devices("SERIAL_A\tdevice")
        result = self.select_serial()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("SERIAL_A", result.stdout.strip())

    def test_multiple_devices_fail_closed_without_explicit_selection(self) -> None:
        self.set_devices("SERIAL_A\tdevice", "SERIAL_B\tdevice")
        result = self.select_serial()
        self.assertEqual(11, result.returncode)
        self.assertIn("multiple adb devices", result.stderr)
        self.assertNotIn("SERIAL_A", result.stdout)

    def test_zero_devices_fail_closed(self) -> None:
        self.set_devices()
        result = self.select_serial()
        self.assertEqual(11, result.returncode)
        self.assertIn("no adb device", result.stderr)

    def test_offline_or_unauthorized_devices_are_not_selected(self) -> None:
        self.set_devices("SERIAL_A\toffline", "SERIAL_B\tunauthorized")
        result = self.select_serial()
        self.assertEqual(11, result.returncode)
        self.assertIn("no adb device", result.stderr)

    def test_explicit_serial_is_honored_when_present(self) -> None:
        self.set_devices("SERIAL_A\tdevice", "SERIAL_B\tdevice")
        result = self.select_serial(explicit="SERIAL_B")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("SERIAL_B", result.stdout.strip())

    def test_explicit_serial_absent_from_device_state_fails_closed(self) -> None:
        self.set_devices("SERIAL_A\tdevice")
        result = self.select_serial(explicit="GHOST_SERIAL")
        self.assertEqual(11, result.returncode)
        self.assertIn("not present in adb 'device' state", result.stderr)
        self.assertNotIn("SERIAL_A", result.stdout)


class PhysicalPreflightJudgeTest(unittest.TestCase):
    """ES-07: deterministic fail-closed identity judgment (no device/SDK needed)."""

    SERIAL = "emulator-0000"
    SIGNER = "a" * 64
    SOURCE = "c" * 40

    def base_facts(self, **overrides: str) -> dict:
        facts = {
            "adb_serial": self.SERIAL,
            "device_manufacturer": "Xiaomi",
            "device_model": "MIBOX4",
            "device_device": "mibox4",
            "package": "sa.hulksa.player.preview",
            "expect_package": "sa.hulksa.player.preview",
            "expect_surface": "mibox4",
            "installed_present": "yes",
            "installed_version_code": "66",
            "installed_version_name": "0.9.3.22",
            "installed_apk_sha256": "1" * 64,
            "installed_signer_sha256": self.SIGNER,
            "candidate_apk_path": "/tmp/app-preview.apk",
            "candidate_package": "sa.hulksa.player.preview",
            "candidate_version_code": "67",
            "candidate_version_name": "0.9.3.23",
            "candidate_signer_sha256": self.SIGNER,
            "candidate_apk_sha256": "2" * 64,
            "expect_version_code": "67",
            "expect_version_name": "0.9.3.23",
            "expect_installed_version_code": "66",
            "expect_installed_version_name": "0.9.3.22",
            "expect_signer_sha256": self.SIGNER,
            "source_commit": self.SOURCE,
            "expect_source_commit": self.SOURCE,
            "state_preserving": "yes",
        }
        facts.update(overrides)
        return facts

    def assert_fails_with(self, message: str, **overrides: str) -> None:
        result = physical_preflight.evaluate(self.base_facts(**overrides))
        self.assertEqual("FAIL", result["status"])
        self.assertTrue(
            any(message in failure for failure in result["failures"]),
            f"{message!r} not in {result['failures']}",
        )

    def test_dedicated_persistent_preview_refresh_passes(self) -> None:
        result = physical_preflight.evaluate(self.base_facts())
        self.assertEqual("PASS", result["status"], result["failures"])

    def test_dedicated_persistent_benchmark_refresh_passes(self) -> None:
        signer = "b" * 64
        result = physical_preflight.evaluate(
            self.base_facts(
                package="sa.hulksa.player.benchmark",
                expect_package="sa.hulksa.player.benchmark",
                candidate_package="sa.hulksa.player.benchmark",
                installed_signer_sha256=signer,
                candidate_signer_sha256=signer,
                expect_signer_sha256=signer,
                candidate_version_name="0.9.3.23.benchmark",
                expect_version_name="0.9.3.23.benchmark",
                installed_version_name="0.9.3.22.benchmark",
                expect_installed_version_name="0.9.3.22.benchmark",
            )
        )
        self.assertEqual("PASS", result["status"], result["failures"])

    def test_disposable_test_package_remains_allowed(self) -> None:
        result = physical_preflight.evaluate(
            self.base_facts(
                package="sa.hulksa.player.fixture",
                expect_package="sa.hulksa.player.fixture",
                candidate_package="sa.hulksa.player.fixture",
                installed_signer_sha256="",
                expect_signer_sha256="",
            )
        )
        self.assertEqual("PASS", result["status"], result["failures"])

    def test_production_package_refused(self) -> None:
        self.assert_fails_with("refusing production package", package="sa.hulksa.player")

    def test_package_mismatch_rejected(self) -> None:
        self.assert_fails_with(
            "does not match target", candidate_package="sa.hulksa.player.dev"
        )

    def test_expected_package_mismatch_rejected(self) -> None:
        self.assert_fails_with(
            "does not match target", expect_package="sa.hulksa.player.benchmark"
        )

    def test_candidate_version_mismatch_rejected(self) -> None:
        self.assert_fails_with(
            "candidate APK versionCode", expect_version_code="66"
        )
        self.assert_fails_with(
            "candidate APK versionName", expect_version_name="0.9.3.99"
        )

    def test_installed_version_mismatch_rejected(self) -> None:
        self.assert_fails_with(
            "installed versionCode", expect_installed_version_code="65"
        )
        self.assert_fails_with(
            "installed versionName", expect_installed_version_name="0.9.3.21"
        )

    def test_signer_mismatch_rejected(self) -> None:
        self.assert_fails_with(
            "candidate APK signer", expect_signer_sha256="f" * 64
        )

    def test_missing_installed_signer_fails_continuity(self) -> None:
        self.assert_fails_with("continuity not proven", installed_signer_sha256="")

    def test_persistent_package_requires_signer_expectation(self) -> None:
        self.assert_fails_with(
            "requires --expect-signer-sha256", expect_signer_sha256=""
        )

    def test_persistent_package_requires_candidate_artifact(self) -> None:
        self.assert_fails_with("requires an explicit --apk", candidate_apk_path="")

    def test_source_commit_mismatch_rejected(self) -> None:
        self.assert_fails_with("does not match expected", source_commit="d" * 40)

    def test_worktree_head_mismatch_rejected(self) -> None:
        self.assert_fails_with("does not match worktree HEAD", worktree_head="e" * 40)

    def test_state_preserving_declaration_required(self) -> None:
        self.assert_fails_with("not declared", state_preserving="no")

    def test_surface_mismatch_rejected(self) -> None:
        self.assert_fails_with("surface mismatch", device_model="SM-A065F")

    def test_unknown_surface_rejected(self) -> None:
        self.assert_fails_with("unknown expected owner-approved surface", expect_surface="pixel")

    def test_missing_device_identity_rejected(self) -> None:
        self.assert_fails_with(
            "device manufacturer/model/device identity missing",
            device_manufacturer="",
            device_model="",
            device_device="",
        )

    def test_missing_serial_rejected(self) -> None:
        self.assert_fails_with("selected adb serial missing", adb_serial="")

    def test_not_installed_package_rejected(self) -> None:
        self.assert_fails_with("not installed", installed_present="no")

    def test_temporary_setting_without_restore_rejected(self) -> None:
        result = physical_preflight.evaluate(
            self.base_facts(), [("font_scale", "")]
        )
        self.assertEqual("FAIL", result["status"])
        self.assertTrue(
            any("has no restore value" in failure for failure in result["failures"])
        )

    def test_temporary_setting_with_restore_is_recorded(self) -> None:
        result = physical_preflight.evaluate(
            self.base_facts(), [("font_scale", "1.0")]
        )
        self.assertEqual("PASS", result["status"], result["failures"])
        contract = dict(result["evidence"])["temporary_setting_contract"]
        self.assertEqual("font_scale=restore_supplied", contract)

    def test_evidence_is_deterministic_and_complete(self) -> None:
        first = physical_preflight.evaluate(self.base_facts())
        second = physical_preflight.evaluate(self.base_facts())
        self.assertEqual(first["evidence"], second["evidence"])
        self.assertEqual(
            [key for key, _ in first["evidence"]],
            list(physical_preflight.EVIDENCE_KEYS),
        )

    def test_evidence_sanitizes_control_and_separator_characters(self) -> None:
        result = physical_preflight.evaluate(
            self.base_facts(device_model="MIBOX4\ninjected=value|pipe")
        )
        rendered = physical_preflight.render_evidence(result)
        self.assertNotIn("\ninjected", rendered)
        self.assertNotIn("|pipe", rendered)
        body_lines = rendered.strip().splitlines()
        self.assertEqual(len(physical_preflight.EVIDENCE_KEYS) + 1, len(body_lines))

    def test_evidence_never_contains_raw_dumps(self) -> None:
        result = physical_preflight.evaluate(self.base_facts())
        rendered = physical_preflight.render_evidence(result)
        self.assertNotIn("password", rendered.lower())
        self.assertNotIn("token", rendered.lower())


class PhysicalPreflightCliTest(unittest.TestCase):
    """ES-07: offline CLI contract (usage/refusal/device ambiguity) without a device."""

    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.bin_dir = Path(self.tmp.name) / "bin"
        self.bin_dir.mkdir()
        adb = self.bin_dir / "adb"
        adb.write_text(
            "#!/usr/bin/env bash\n"
            'if [[ "${1:-}" == "devices" ]]; then cat "$FAKE_ADB_DEVICES"; exit 0; fi\n'
            "exit 0\n",
            encoding="utf-8",
        )
        adb.chmod(0o755)
        self.devices_file = Path(self.tmp.name) / "devices.txt"
        self.env = dict(os.environ)
        self.env["PATH"] = f"{self.bin_dir}:{self.env['PATH']}"

    def set_devices(self, *entries: str) -> None:
        body = "List of devices attached\n" + "".join(f"{entry}\n" for entry in entries)
        self.devices_file.write_text(body, encoding="utf-8")
        self.env["FAKE_ADB_DEVICES"] = str(self.devices_file)

    def run_tool(self, *args: str) -> subprocess.CompletedProcess:
        return subprocess.run(
            [str(TOOLS_DIR / "physical-preflight.sh"), *args],
            capture_output=True,
            text=True,
            check=False,
            env=self.env,
        )

    def test_requires_target_package(self) -> None:
        result = self.run_tool()
        self.assertEqual(2, result.returncode)

    def test_refuses_production_package_before_device_access(self) -> None:
        result = self.run_tool("--package", "sa.hulksa.player", "--state-preserving")
        self.assertEqual(12, result.returncode)
        self.assertIn("production package", result.stderr)

    def test_requires_state_preserving_declaration(self) -> None:
        result = self.run_tool("--package", "sa.hulksa.player.preview")
        self.assertEqual(2, result.returncode)
        self.assertIn("state-preserving", result.stderr)

    def test_rejects_malformed_temporary_setting(self) -> None:
        result = self.run_tool(
            "--package",
            "sa.hulksa.player.preview",
            "--state-preserving",
            "--temporary-setting",
            "font_scale",
        )
        self.assertEqual(2, result.returncode)
        self.assertIn("NAME=RESTORE", result.stderr)

    def test_multiple_devices_fail_closed(self) -> None:
        self.set_devices("SERIAL_A\tdevice", "SERIAL_B\tdevice")
        result = self.run_tool("--package", "sa.hulksa.player.preview", "--state-preserving")
        self.assertEqual(10, result.returncode)
        self.assertIn("multiple adb devices", result.stderr)

    def test_zero_devices_fail_closed(self) -> None:
        self.set_devices()
        result = self.run_tool("--package", "sa.hulksa.player.preview", "--state-preserving")
        self.assertEqual(10, result.returncode)
        self.assertIn("no adb device", result.stderr)

    def test_explicit_absent_serial_fails_closed(self) -> None:
        self.set_devices("SERIAL_A\tdevice")
        result = self.run_tool(
            "--package",
            "sa.hulksa.player.preview",
            "--state-preserving",
            "--serial",
            "GHOST",
        )
        self.assertEqual(10, result.returncode)
        self.assertIn("not present", result.stderr)


class PhysicalPreflightEndToEndTest(unittest.TestCase):
    """ES-07: full read-only collection pipeline against a fake adb + fake SDK."""

    SIGNER = "a" * 64
    SOURCE = "c" * 40

    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = Path(self.tmp.name)
        self.bin_dir = root / "bin"
        self.bin_dir.mkdir()
        self.sdk = root / "sdk"
        build_tools = self.sdk / "build-tools" / "36.0.0"
        build_tools.mkdir(parents=True)

        self.candidate = self._write_apk(
            root / "candidate.apk", version_code="67", version_name="0.9.3.23"
        )
        self.installed = self._write_apk(
            root / "installed.apk", version_code="66", version_name="0.9.3.22"
        )
        self.devices_file = root / "devices.txt"
        self.devices_file.write_text(
            "List of devices attached\nSERIAL_A\tdevice\n", encoding="utf-8"
        )

        self._write_tools(build_tools)
        self.env = dict(os.environ)
        self.env["PATH"] = f"{self.bin_dir}:{self.env['PATH']}"
        self.env["HULK_ANDROID_SDK"] = str(self.sdk)
        self.env["FAKE_ADB_DEVICES"] = str(self.devices_file)
        self.env["FAKE_INSTALLED_APK"] = str(self.installed)

    def _write_apk(self, path: Path, version_code: str, version_name: str) -> Path:
        path.write_text("dummy apk\n", encoding="utf-8")
        path.with_suffix(".apk.badging").write_text(
            f"package: name='sa.hulksa.player.preview' versionCode='{version_code}' "
            f"versionName='{version_name}'\n"
            "minSdkVersion:'23'\n"
            "targetSdkVersion:'36'\n"
            "launchable-activity: name='sa.hulksa.player.MainActivity'\n",
            encoding="utf-8",
        )
        path.with_suffix(".apk.xmltree").write_text("", encoding="utf-8")
        path.with_suffix(".apk.signer").write_text(self.SIGNER + "\n", encoding="utf-8")
        return path

    def _write_tools(self, build_tools: Path) -> None:
        aapt2 = build_tools / "aapt2"
        aapt2.write_text(
            "#!/usr/bin/env bash\n"
            "apk=\"\"\n"
            "for arg in \"$@\"; do [[ \"$arg\" == *.apk ]] && apk=\"$arg\"; done\n"
            "case \"$1 $2\" in\n"
            "  'dump badging') cat \"$apk.badging\" 2>/dev/null || true ;;\n"
            "  'dump xmltree') cat \"$apk.xmltree\" 2>/dev/null || true ;;\n"
            "esac\n"
            "exit 0\n",
            encoding="utf-8",
        )
        aapt2.chmod(0o755)
        apksigner = build_tools / "apksigner"
        apksigner.write_text(
            "#!/usr/bin/env bash\n"
            "apk=\"\"\n"
            "for arg in \"$@\"; do [[ \"$arg\" == *.apk ]] && apk=\"$arg\"; done\n"
            "for arg in \"$@\"; do\n"
            "  if [[ \"$arg\" == '--print-certs' ]]; then\n"
            "    echo 'Signer #1 certificate DN: C=US, O=Android, CN=Android Debug'\n"
            "    echo \"Signer #1 certificate SHA-256 digest: $(cat \"$apk.signer\")\"\n"
            "    exit 0\n"
            "  fi\n"
            "done\n"
            "echo 'Verified using v2 scheme (APK Signature Scheme v2): true'\n"
            "exit 0\n",
            encoding="utf-8",
        )
        apksigner.chmod(0o755)

        adb = self.bin_dir / "adb"
        adb.write_text(
            "#!/usr/bin/env bash\n"
            "if [[ \"${1:-}\" == 'devices' ]]; then cat \"$FAKE_ADB_DEVICES\"; exit 0; fi\n"
            "if [[ \"${1:-}\" == '-s' ]]; then shift 2; fi\n"
            "case \"${1:-}\" in\n"
            "  shell)\n"
            "    shift\n"
            "    case \"${1:-}\" in\n"
            "      getprop)\n"
            "        case \"${2:-}\" in\n"
            "          ro.product.manufacturer) echo Xiaomi ;;\n"
            "          ro.product.model) echo MIBOX4 ;;\n"
            "          ro.product.device) echo mibox4 ;;\n"
            "        esac\n"
            "        ;;\n"
            "      pm) echo 'package:/data/app/base.apk' ;;\n"
            "    esac\n"
            "    ;;\n"
            "  pull)\n"
            "    dest=\"$3\"\n"
            "    cp \"$FAKE_INSTALLED_APK\" \"$dest\"\n"
            "    cp \"$FAKE_INSTALLED_APK.badging\" \"$dest.badging\"\n"
            "    cp \"$FAKE_INSTALLED_APK.xmltree\" \"$dest.xmltree\"\n"
            "    cp \"$FAKE_INSTALLED_APK.signer\" \"$dest.signer\"\n"
            "    ;;\n"
            "esac\n"
            "exit 0\n",
            encoding="utf-8",
        )
        adb.chmod(0o755)

    def run_preflight(self, *extra: str) -> subprocess.CompletedProcess:
        outdir = Path(self.tmp.name) / "evidence"
        return subprocess.run(
            [
                str(TOOLS_DIR / "physical-preflight.sh"),
                "--package",
                "sa.hulksa.player.preview",
                "--apk",
                str(self.candidate),
                "--expect-package",
                "sa.hulksa.player.preview",
                "--expect-version-code",
                "67",
                "--expect-version-name",
                "0.9.3.23",
                "--expect-installed-version-code",
                "66",
                "--expect-installed-version-name",
                "0.9.3.22",
                "--expect-signer-sha256",
                self.SIGNER,
                "--expect-surface",
                "mibox4",
                "--source-commit",
                self.SOURCE,
                "--expect-source-commit",
                self.SOURCE,
                "--state-preserving",
                "--serial",
                "SERIAL_A",
                "--out",
                str(outdir),
                *extra,
            ],
            capture_output=True,
            text=True,
            check=False,
            env=self.env,
        ), outdir

    def test_read_only_preflight_passes_and_writes_sanitized_evidence(self) -> None:
        result, outdir = self.run_preflight()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("preflight_status=PASS", result.stdout)
        self.assertIn("installed_version_code=66", result.stdout)
        self.assertIn("candidate_version_code=67", result.stdout)
        self.assertIn("package_role=persistent-preview", result.stdout)
        evidence = (outdir / "physical-preflight.txt").read_text(encoding="utf-8")
        self.assertIn("preflight_status=PASS", evidence)

    def test_identity_mismatch_fails_closed(self) -> None:
        result, _ = self.run_preflight("--expect-version-code", "99")
        self.assertEqual(5, result.returncode)
        self.assertIn("preflight_status=FAIL", result.stdout)
        self.assertIn("does not match expected", result.stdout)


if __name__ == "__main__":
    unittest.main()
