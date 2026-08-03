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

    def write_runtime_fixture(
        self,
        root: Path,
        *,
        app_foreground: bool,
        locale_verified: bool = True,
        ime_hidden: bool = True,
        requested_size: tuple[int, int] = (360, 640),
        png_size: tuple[int, int] | None = None,
        xml_size: tuple[int, int] | None = None,
    ) -> Path:
        package = "sa.hulksa.player.dev"
        visible_package = package if app_foreground else "com.android.settings"
        locale_result = "result=PASS\nlocale_verified=true\n" if locale_verified else "result=BLOCKED\n"
        ime_result = "result=PASS\nime_hidden=true\n" if ime_hidden else "result=FAIL\nime_hidden=false\n"
        png_size = png_size or requested_size
        xml_size = xml_size or requested_size
        orientation = "LANDSCAPE" if requested_size[0] > requested_size[1] else "PORTRAIT"
        files = {
            "PROFILE-CONFIG.txt": (
                f"requested_size={requested_size[0]}x{requested_size[1]}\n"
                "result=PASS\nprofile_verified=true\nlocale_mode=system-root\n"
            ),
            "APPLICATION-LOCALE.txt": f"requested_locale=ar-SA\n{locale_result}",
            "WINDOW-CLASSIFICATION.txt": (
                "result=PASS\n"
                f"requested_physical_size={requested_size[0]}x{requested_size[1]}\n"
                f"effective_physical_size={requested_size[0]}x{requested_size[1]}\n"
                "effective_density_dpi=160\n"
                f"effective_logical_width_dp={requested_size[0]}\n"
                f"effective_logical_height_dp={requested_size[1]}\n"
                "window_width_class=COMPACT\n"
                "window_height_class=MEDIUM\n"
                f"orientation={orientation}\n"
                "actual_device_class=MOBILE\n"
                "actual_input_mode=TOUCH\n"
                "expected_device_class=MOBILE\n"
                "expected_input_mode=TOUCH\n"
            ),
            "INSTRUMENTATION.xml": '<testsuite tests="6" failures="0" errors="0" skipped="1"/>\n',
            "FOREGROUND-APP.txt": f"package={package}\nresolved_activity={package}/.MainActivity\nStatus: ok\n",
            "IME-STATE.txt": ime_result,
            "ACTIVITY-TOP.txt": f"ACTIVITY {visible_package}/.MainActivity\n",
            "window.xml": (
                f'<hierarchy><node package="{visible_package}" '
                f'bounds="[0,0][{xml_size[0]},{xml_size[1]}]" /></hierarchy>\n'
            ),
        }
        for name, content in files.items():
            (root / name).write_text(content, encoding="utf-8")
        png_header = b"\x89PNG\r\n\x1a\n" + b"\x00\x00\x00\x0dIHDR" + struct.pack(">II", *png_size)
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
                            "WINDOW-CLASSIFICATION.txt",
                            "INSTRUMENTATION.xml",
                            "FOREGROUND-APP.txt",
                            "IME-STATE.txt",
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

    def test_missing_window_classification_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            spec = self.write_runtime_fixture(root, app_foreground=True)
            (root / "WINDOW-CLASSIFICATION.txt").unlink()
            checks = MODULE.gate_evidence(spec, "runtime", root)
            failures = {check.id for check in checks if check.status == "FAIL"}
            self.assertIn("WINDOW-CLASSIFICATION.txt", failures)
            self.assertIn("runtime-window-classification", failures)

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

    def test_visible_ime_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            spec = self.write_runtime_fixture(root, app_foreground=True, ime_hidden=False)
            checks = MODULE.gate_evidence(spec, "runtime", root)
            failures = {check.id for check in checks if check.status == "FAIL"}
            self.assertIn("runtime-ime-hidden", failures)

    def test_rotated_screenshot_geometry_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            spec = self.write_runtime_fixture(
                root,
                app_foreground=True,
                requested_size=(2340, 1080),
                png_size=(1080, 2340),
                xml_size=(1080, 2340),
            )
            checks = MODULE.gate_evidence(spec, "runtime", root)
            geometry = next(check for check in checks if check.id == "runtime-window-geometry")
            self.assertEqual("FAIL", geometry.status)
            self.assertIn("Requested 2340x1080", geometry.message)

    def test_matching_landscape_geometry_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            spec = self.write_runtime_fixture(
                root,
                app_foreground=True,
                requested_size=(2340, 1080),
            )
            checks = MODULE.gate_evidence(spec, "runtime", root)
            geometry = next(check for check in checks if check.id == "runtime-window-geometry")
            self.assertEqual("PASS", geometry.status)


if __name__ == "__main__":
    unittest.main()
