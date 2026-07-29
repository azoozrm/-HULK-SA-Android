from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest


LAB_ROOT = Path(__file__).resolve().parents[1]


def load_script(name: str, filename: str):
    spec = importlib.util.spec_from_file_location(name, LAB_ROOT / filename)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


ANALYZE = load_script("compatibility_analyze_qualified", "analyze-qualified.py")
RUN = load_script("compatibility_run_lab_qualified", "run-lab-qualified.py")


class QualifiedAnalyzerTests(unittest.TestCase):
    def test_orientation_list_is_normalized_without_repr_artifacts(self) -> None:
        device = ANALYZE.normalize_device({"orientations": ["landscape", "portrait"]})
        self.assertEqual("landscape,portrait", device["orientations"])
        self.assertNotIn("[", device["orientations"])

    def test_orientation_csv_remains_compatible(self) -> None:
        device = ANALYZE.normalize_device({"orientations": "landscape"})
        self.assertEqual("landscape", device["orientations"])

    def test_manifest_normalizes_all_sequence_fields_before_reporting(self) -> None:
        manifest = ANALYZE.normalize_manifest(
            {
                "device": {
                    "orientations": ["portrait", "landscape"],
                    "font_scales": [1.0, 1.3],
                }
            }
        )
        self.assertEqual("portrait,landscape", manifest["device"]["orientations"])
        self.assertEqual("1.0,1.3", manifest["device"]["font_scales"])


class QualifiedRunnerTests(unittest.TestCase):
    def write_manifest(self, root: Path, case_count: int) -> None:
        root.mkdir(parents=True, exist_ok=True)
        (root / "run-manifest.json").write_text(
            json.dumps({"cases": [{"id": str(index)} for index in range(case_count)]}),
            encoding="utf-8",
        )

    def test_download_page_uses_bounded_marker_wait(self) -> None:
        self.assertEqual(45.0, RUN.page_marker_timeout("downloads"))
        self.assertEqual(15.0, RUN.page_marker_timeout("home"))
        self.assertGreater(RUN.DOWNLOAD_PROGRESS_TIMEOUT_SECONDS, 0)

    def test_real_retry_is_selected_and_primary_is_preserved(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "device"
            self.write_manifest(root, 1)
            (root / "summary.json").write_text('{"overall_status":"BLOCKED"}', encoding="utf-8")
            primary = RUN.preserve_primary(root)
            retry = root / "attempts" / "retry"
            self.write_manifest(retry, 2)
            selected = RUN.select_attempt(root, primary, retry)
            self.assertEqual("retry", selected)
            self.assertEqual(2, len(json.loads((root / "run-manifest.json").read_text())["cases"]))
            self.assertTrue((root / "attempts/primary/run-manifest.json").is_file())

    def test_fallback_retry_cannot_replace_real_primary(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "device"
            self.write_manifest(root, 1)
            primary = RUN.preserve_primary(root)
            retry = root / "attempts" / "retry"
            retry.mkdir(parents=True)
            (retry / "summary.json").write_text(
                '{"status":"infrastructure_blocked"}',
                encoding="utf-8",
            )
            selected = RUN.select_attempt(root, primary, retry)
            self.assertEqual("primary", selected)
            self.assertEqual(1, len(json.loads((root / "run-manifest.json").read_text())["cases"]))
            selection = json.loads((root / "selected-attempt.json").read_text())
            self.assertTrue(selection["primary_has_real_manifest"])
            self.assertFalse(selection["retry_has_real_manifest"])


class WorkflowContractTests(unittest.TestCase):
    def test_workflow_uses_qualified_entrypoints_and_native_720p(self) -> None:
        workflow = (LAB_ROOT.parents[1] / ".github/workflows/compatibility-lab.yml").read_text(
            encoding="utf-8"
        )
        self.assertIn("run-device-job.sh", workflow)
        self.assertNotIn("reactivecircus/android-emulator-runner", workflow)
        runner = (LAB_ROOT / "run-device-job.sh").read_text(encoding="utf-8")
        self.assertIn("run-lab-isolated.py", runner)
        isolated = (LAB_ROOT / "run-lab-isolated.py").read_text(encoding="utf-8")
        self.assertIn("run-lab-qualified.py", isolated)
        self.assertIn("analyze-qualified.py", runner)


if __name__ == "__main__":
    unittest.main()
