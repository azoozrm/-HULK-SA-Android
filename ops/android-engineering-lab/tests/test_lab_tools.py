"""Deterministic tests for the HULK SA Android lab diagnostics tools.

These tests do not require a device or the Android SDK. They cover the pure parsers and
the safety guards of the shell tools using static fixtures.
"""

from __future__ import annotations

import json
import subprocess
import sys
import unittest
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parents[1]
FIXTURES = Path(__file__).resolve().parent / "fixtures"

sys.path.insert(0, str(TOOLS_DIR))

import benchmark_analysis  # noqa: E402
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


if __name__ == "__main__":
    unittest.main()
