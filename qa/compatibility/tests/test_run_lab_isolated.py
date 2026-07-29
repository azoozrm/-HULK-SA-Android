from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest


LAB_ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "compatibility_run_lab_isolated",
    LAB_ROOT / "run-lab-isolated.py",
)
assert SPEC and SPEC.loader
RUN = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = RUN
SPEC.loader.exec_module(RUN)


class IsolationLayerTests(unittest.TestCase):
    def test_display_override_uses_requested_geometry_without_faking_analyzer(self) -> None:
        commands = RUN.display_override_commands((2400, 1080), 420, 1.3)
        self.assertIn(["wm", "size", "2400x1080"], commands)
        self.assertIn(["wm", "density", "420"], commands)
        self.assertIn(["settings", "put", "system", "font_scale", "1.30"], commands)
        self.assertIn(["wm", "user-rotation", "lock", "0"], commands)
        self.assertNotIn(["wm", "user-rotation", "lock", "1"], commands)

    def test_download_state_records_bytes_and_status(self) -> None:
        xml = """<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
        <map><string name="downloads">[{&quot;historyKey&quot;:&quot;QA_DOWNLOAD:1&quot;,&quot;status&quot;:&quot;DOWNLOADING&quot;,&quot;bytesDownloaded&quot;:524288,&quot;totalBytes&quot;:67108864,&quot;retryCount&quot;:0,&quot;errorMessage&quot;:null}]</string></map>"""
        state = RUN.parse_download_state_xml(xml)
        self.assertEqual(1, len(state))
        self.assertEqual("DOWNLOADING", state[0]["status"])
        self.assertEqual(524288, state[0]["bytesDownloaded"])

    def test_stable_tv_evidence_requires_found_and_paired_files(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            case = Path(temporary)
            (case / "tv-download-layout-wait.json").write_text(
                json.dumps({"found": True}), encoding="utf-8"
            )
            (case / "tv-download-layout-wait.xml").write_text("<hierarchy/>", encoding="utf-8")
            self.assertIsNone(RUN.stable_tv_evidence(case))
            (case / "tv-download-layout-wait.png").write_bytes(b"png")
            evidence = RUN.stable_tv_evidence(case)
            self.assertIsNotNone(evidence)
            assert evidence is not None
            self.assertEqual("tv-download-layout-wait.xml", evidence[0].name)
            self.assertEqual("tv-download-layout-wait.png", evidence[1].name)

    def test_source_resets_fixture_and_preserves_strict_evidence(self) -> None:
        source = (LAB_ROOT / "run-lab-isolated.py").read_text(encoding="utf-8")
        self.assertIn('["pm", "clear", core.PACKAGE]', source)
        self.assertIn('"fixture_reinitialized"', source)
        self.assertIn('"download-state.json"', source)
        self.assertIn('"paired evidence satisfied the unchanged two-card gate"', source)
        self.assertIn("DISPLAY_ATTEMPTS = 3", source)
        self.assertIn("qualified.wait_for_stable_geometry", source)
        self.assertIn('"geometry_stable"', source)
        self.assertIn('"logical_display_override"', source)
        self.assertIn('["wm", "size", "reset"]', source)


if __name__ == "__main__":
    unittest.main()
