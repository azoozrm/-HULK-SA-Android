from __future__ import annotations

import hashlib
import importlib.util
import json
import struct
import sys
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).parents[1] / "evidence_gate.py"
SPEC = importlib.util.spec_from_file_location("compatibility_v2_evidence", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class EvidenceGateTest(unittest.TestCase):
    def test_missing_required_evidence_is_fail(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            spec = root / "spec.json"
            spec.write_text(json.dumps({"scopes": {"fast": ["screenshot.png", "logcat.txt"]}}), encoding="utf-8")
            (root / "screenshot.png").write_bytes(b"png")
            checks = MODULE.gate_evidence(spec, "fast", root)
            self.assertEqual(["PASS", "FAIL"], [check.status for check in checks])

    def test_empty_evidence_is_fail(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            spec = root / "spec.json"
            spec.write_text(json.dumps({"scopes": {"fast": ["UNIT-TESTS.xml"]}}), encoding="utf-8")
            (root / "UNIT-TESTS.xml").touch()
            checks = MODULE.gate_evidence(spec, "fast", root)
            self.assertEqual("FAIL", checks[0].status)

    def test_complete_non_empty_evidence_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            spec = root / "spec.json"
            spec.write_text(json.dumps({"scopes": {"fast": ["a.txt", "b.xml"]}}), encoding="utf-8")
            (root / "a.txt").write_text("a", encoding="utf-8")
            (root / "b.xml").write_text("<testsuite/>", encoding="utf-8")
            checks = MODULE.gate_evidence(spec, "fast", root)
            self.assertTrue(all(check.status == "PASS" for check in checks))

    def write_runtime_fixture(self, root: Path, *, app_foreground: bool, locale_verified: bool = True) -> Path:
        package = "sa.hulksa.player.dev"
        visible_package = package if app_foreground else "com.android.settings"
        locale_result = "result=PASS\nlocale_verified=true\n" if locale_verified else "result=BLOCKED\n"
        files = {
            "PROFILE-CONFIG.txt": "result=PASS\nprofile_verified=true\nlocale_mode=system-root\n",
            "APPLICATION-LOCALE.txt": f"requested_locale=ar-SA\n{locale_result}",
            "INSTRUMENTATION.xml": '<testsuite tests="5" failures="0" errors="0" skipped="1"/>\n',
            "FOREGROUND-APP.txt": f"package={package}\nresolved_activity={package}/.MainActivity\nStatus: ok\n",
            "ACTIVITY-TOP.txt": f"ACTIVITY {visible_package}/.MainActivity\n",
            "window.xml": f'<hierarchy><node package="{visible_package}" /></hierarchy>\n',
        }
        for name, content in files.items():
            (root / name).write_text(content, encoding="utf-8")
        png_header = b"\x89PNG\r\n\x1a\n" + b"\x00\x00\x00\x0dIHDR" + struct.pack(">II", 360, 640)
        (root / "full-window.png").write_bytes(png_header)

        checksum_lines = []
        for path in sorted(root.iterdir()):
            if path.name == "SHA256SUMS.txt" or not path.is_file():
                continue
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            checksum_lines.append(f"{digest}  {path.name}")
        (root / "SHA256SUMS.txt").write_text("\n".join(checksum_lines) + "\n", encoding="utf-8")

        spec = root / "spec.json"
        spec.write_text(
            json.dumps(
                {
                    "scopes": {
                        "runtime": [
                            "PROFILE-CONFIG.txt",
                            "APPLICATION-LOCALE.txt",
                            "INSTRUMENTATION.xml",
                            "FOREGROUND-APP.txt",
                            "ACTIVITY-TOP.txt",
                            "window.xml",
                            "full-window.png",
                            "SHA256SUMS.txt",
                        ]
                    }
                }
            ),
            encoding="utf-8",
        )
        return spec

    def test_runtime_semantics_pass_for_foreground_application(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            spec = self.write_runtime_fixture(root, app_foreground=True)
            checks = MODULE.gate_evidence(spec, "runtime", root)
            self.assertTrue(all(check.status == "PASS" for check in checks), checks)

    def test_home_screen_evidence_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            spec = self.write_runtime_fixture(root, app_foreground=False)
            checks = MODULE.gate_evidence(spec, "runtime", root)
            failures = {check.id for check in checks if check.status == "FAIL"}
            self.assertIn("runtime-foreground-activity", failures)
            self.assertIn("runtime-window-package", failures)

    def test_unverified_effective_locale_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            spec = self.write_runtime_fixture(root, app_foreground=True, locale_verified=False)
            checks = MODULE.gate_evidence(spec, "runtime", root)
            failures = {check.id for check in checks if check.status == "FAIL"}
            self.assertIn("runtime-effective-locale", failures)


if __name__ == "__main__":
    unittest.main()
