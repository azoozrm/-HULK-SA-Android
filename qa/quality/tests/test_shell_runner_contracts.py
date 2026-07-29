from __future__ import annotations

from pathlib import Path
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[3]
INSTRUMENTATION = ROOT / "qa/quality/scripts/run_instrumentation.sh"
NATIVE_EMULATOR = ROOT / "qa/compatibility/run-native-emulator.sh"


class ShellRunnerContractTest(unittest.TestCase):
    def test_shell_runners_are_valid_bash(self) -> None:
        for path in (INSTRUMENTATION, NATIVE_EMULATOR):
            completed = subprocess.run(
                ["bash", "-n", str(path)],
                cwd=ROOT,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(
                completed.returncode,
                0,
                msg=f"{path}: {completed.stderr}",
            )

    def test_instrumentation_retry_is_limited_to_zero_test_infrastructure(self) -> None:
        source = INSTRUMENTATION.read_text(encoding="utf-8")
        self.assertIn("connectedDebugAndroidTest", source)
        self.assertIn("has_zero_test_infrastructure_failure", source)
        self.assertIn("No test results", source)
        self.assertIn('tests=\"[1-9][0-9]*\"', source)
        self.assertIn("runtime_evidence/attempts/attempt-1", source)
        self.assertIn("runtime_evidence/attempts/attempt-2", source)
        self.assertNotIn("|| true\n  ./gradlew", source)

    def test_instrumentation_preserves_failed_command_status(self) -> None:
        source = INSTRUMENTATION.read_text(encoding="utf-8")
        self.assertIn("else\n  first_status=$?\nfi", source)
        self.assertIn("else\n  retry_status=$?\nfi", source)
        self.assertNotIn("fi\nfirst_status=$?", source)
        self.assertNotIn("fi\nretry_status=$?", source)
        self.assertIn('exit "$first_status"', source)
        self.assertIn('exit "$retry_status"', source)

    def test_native_runner_requires_stable_android_services(self) -> None:
        source = NATIVE_EMULATOR.read_text(encoding="utf-8")
        for expected in (
            "service check package",
            "service check activity",
            "am get-current-user",
            "stable_reads >= 6",
            "capture_boot_evidence",
            "-no-metrics",
        ):
            self.assertIn(expected, source)
        self.assertIn('export ANDROID_SERIAL="$SERIAL"', source)
        self.assertIn('"$@"', source)


if __name__ == "__main__":
    unittest.main()
