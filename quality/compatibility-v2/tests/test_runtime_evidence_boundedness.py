from __future__ import annotations

import os
import re
import shutil
import subprocess
import tempfile
import time
import unittest
from pathlib import Path

COLLECTOR_PATH = Path(
    os.environ.get(
        "COMPAT_V2_COLLECTOR_UNDER_TEST",
        Path(__file__).parents[1] / "collect_runtime_evidence.sh",
    )
)

FAKE_ADB = r'''#!/usr/bin/env bash
set -u
if [[ -n "${FAKE_ADB_CALL_LOG:-}" ]]; then
  printf '%s\n' "$*" >> "$FAKE_ADB_CALL_LOG"
fi
command="$*"
hang_modes=",${FAKE_ADB_HANG:-},"
if [[ "$command" == *"logcat -d"* && "$hang_modes" == *",logcat-hard-kill,"* ]]; then
  trap '' TERM
  /bin/sleep 10
fi
if [[ "$command" == *"logcat -d"* && "$hang_modes" == *",logcat-early-137,"* ]]; then
  kill -KILL "$$"
fi
if [[ "$command" == *"logcat -d"* && "$hang_modes" == *",logcat,"* ]]; then
  /bin/sleep 10
fi
if [[ "$command" == *"uiautomator dump"* && "$hang_modes" == *",uiautomator,"* ]]; then
  /bin/sleep 10
fi
if [[ "$command" == *"am force-stop"* && "$hang_modes" == *",cleanup,"* && -f "${FAKE_TIMEOUT_MARKER:-/nonexistent}" ]]; then
  /bin/sleep 10
fi

case "$command" in
  "get-serialno") echo "emulator-5554" ;;
  "shell getprop ro.build.version.sdk") echo "35" ;;
  "shell getprop ro.product.model") echo "Fake Phone" ;;
  "shell getprop ro.product.device") echo "fake_device" ;;
  "shell getprop ro.product.cpu.abi") echo "x86_64" ;;
  "shell getprop persist.sys.locale") echo "ar-SA" ;;
  "shell pm list features") ;;
  shell\ pm\ grant\ *) ;;
  shell\ dumpsys\ package\ *)
    echo "sa.hulksa.player.MainActivity"
    echo "android.permission.POST_NOTIFICATIONS: granted=true"
    ;;
  shell\ cmd\ locale\ get-app-locales\ *) echo "Locales for sa.hulksa.player.dev: [ar-SA]" ;;
  "shell wm size") echo "Physical size: 720x1278" ;;
  "shell wm density") echo "Physical density: 360" ;;
  "shell settings get system font_scale") echo "1.0" ;;
  "shell dumpsys window displays") echo "Display: mDisplayId=0 app=720x1278" ;;
  "shell dumpsys window insets") echo "InsetsState" ;;
  "shell dumpsys activity activities") echo "mResumedActivity: ActivityRecord{ sa.hulksa.player.dev/sa.hulksa.player.MainActivity }" ;;
  "shell dumpsys activity top") echo "ACTIVITY sa.hulksa.player.dev/sa.hulksa.player.MainActivity" ;;
  "shell dumpsys window windows") echo "mCurrentFocus=Window{ sa.hulksa.player.dev/sa.hulksa.player.MainActivity }" ;;
  shell\ dumpsys\ meminfo\ *) echo "TOTAL 12345" ;;
  shell\ am\ start\ *) echo "Status: ok" ;;
  shell\ am\ force-stop\ *) ;;
  shell\ input\ keyevent\ *) ;;
  shell\ am\ instrument\ *)
    echo "INSTRUMENTATION_STATUS: class=sample.Tests"
    echo "INSTRUMENTATION_STATUS: test=passes"
    echo "INSTRUMENTATION_STATUS_CODE: 1"
    echo "INSTRUMENTATION_STATUS: class=sample.Tests"
    echo "INSTRUMENTATION_STATUS: test=passes"
    echo "INSTRUMENTATION_STATUS_CODE: 0"
    ;;
  logcat\ -d\ *) echo "fake runtime log" ;;
  shell\ uiautomator\ dump\ *) echo "UI hierarchy dumped" ;;
  pull\ *)
    destination="${@: -1}"
    mkdir -p "$(dirname "$destination")"
    if [[ "$destination" == *.xml ]]; then
      printf '%s\n' '<hierarchy><node package="sa.hulksa.player.dev" bounds="[0,0][720,1278]" /></hierarchy>' > "$destination"
    else
      printf '%s\n' 'fake pulled evidence' > "$destination"
    fi
    ;;
  exec-out\ screencap\ *) printf '\211PNG\r\n\032\nFAKE' ;;
  *) ;;
esac
'''

FAKE_PARSER = r'''#!/usr/bin/env python3
import pathlib
import sys
pathlib.Path(sys.argv[2]).write_text(
    '<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase name="passes" /></testsuite>\n',
    encoding="utf-8",
)
sys.exit(0)
'''


class RuntimeEvidenceBoundednessTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.repo = self.root / "repo"
        script_dir = self.repo / "quality" / "compatibility-v2"
        script_dir.mkdir(parents=True)
        self.collector = script_dir / "collect_runtime_evidence.sh"
        shutil.copy2(COLLECTOR_PATH, self.collector)
        self.collector.chmod(0o755)
        parser = script_dir / "instrumentation_to_junit.py"
        parser.write_text(FAKE_PARSER, encoding="utf-8")
        parser.chmod(0o755)

        self.bin = self.root / "bin"
        self.bin.mkdir()
        adb = self.bin / "adb"
        adb.write_text(FAKE_ADB, encoding="utf-8")
        adb.chmod(0o755)
        fake_sleep = self.bin / "sleep"
        fake_sleep.write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
        fake_sleep.chmod(0o755)

        self.out = self.repo / "build" / "runtime"
        self.out.mkdir(parents=True)
        for name, content in {
            "PROFILE-CONFIG.txt": "result=PASS\nprofile_verified=true\nrequested_size=720x1278\n",
            "INSTALL-READINESS.txt": "result=PASS\n",
            "INSTALLATION.txt": "result=PASS\n",
            "PACKAGE-REGISTRATION.txt": "result=PASS\n",
            "INSTALLED-PACKAGE-DUMP.txt": "sa.hulksa.player.MainActivity\n",
            "APPLICATION-LOCALE.txt": "result=PASS\nlocale_verified=true\nrequested_locale=ar-SA\n",
            "WINDOW-CLASSIFICATION.txt": "result=PASS\nactual_device_class=MOBILE\norientation=PORTRAIT\n",
        }.items():
            (self.out / name).write_text(content, encoding="utf-8")
        self.call_log = self.root / "adb-calls.txt"

    def tearDown(self) -> None:
        self.temp.cleanup()

    def run_collector(
        self,
        *,
        hang: str = "",
        outer_timeout: int = 15,
        collector: Path | None = None,
    ) -> tuple[subprocess.CompletedProcess[str], float]:
        env = os.environ.copy()
        env.update(
            {
                "PATH": f"{self.bin}:{env['PATH']}",
                "FAKE_ADB_CALL_LOG": str(self.call_log),
                "FAKE_ADB_HANG": hang,
                "FAKE_TIMEOUT_MARKER": str(self.out / ".EVIDENCE-COLLECTION-ADB-TIMEOUT"),
                "COMPAT_V2_ADB_TIMEOUT_SECONDS": "1",
                "COMPAT_V2_ADB_KILL_AFTER_SECONDS": "1",
                "COMPAT_V2_CLEANUP_TIMEOUT_SECONDS": "1",
                "COMPAT_V2_COLLECTOR_TIMEOUT_SECONDS": str(outer_timeout),
                "COMPAT_V2_COLLECTOR_KILL_AFTER_SECONDS": "1",
            }
        )
        start = time.monotonic()
        result = subprocess.run(
            ["bash", str(collector or self.collector), "fake-profile", str(self.out)],
            cwd=self.repo,
            env=env,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=25,
        )
        return result, time.monotonic() - start

    def read_adb_event(self, stage: str) -> tuple[int, int, bool]:
        execution = (self.out / "EVIDENCE-COLLECTION-EXECUTION.txt").read_text(encoding="utf-8")
        match = re.search(
            rf"event=adb-command\nstage={re.escape(stage)}\ncommand=[^\n]*\n"
            rf"timeout_seconds=\d+\nelapsed_ms=(\d+)\nexit_status=(\d+)\ntimed_out=(true|false)",
            execution,
        )
        self.assertIsNotNone(match, execution)
        assert match is not None
        return int(match.group(1)), int(match.group(2)), match.group(3) == "true"

    def test_logcat_waiting_for_device_is_bounded_and_explicitly_blocked(self) -> None:
        result, elapsed = self.run_collector(hang="logcat")
        self.assertEqual(3, result.returncode, result.stderr)
        self.assertLess(elapsed, 10.0)
        execution = (self.out / "EVIDENCE-COLLECTION-EXECUTION.txt").read_text(encoding="utf-8")
        adb_elapsed_ms, adb_status, timed_out = self.read_adb_event("logcat")
        self.assertEqual(124, adb_status)
        self.assertTrue(timed_out)
        self.assertGreaterEqual(adb_elapsed_ms, 0)
        self.assertIn("result=BLOCKED", execution)
        self.assertIn("timeout_exit_status=124", execution)
        self.assertTrue((self.out / "INSTRUMENTATION-EXECUTION.txt").is_file())
        self.assertTrue((self.out / "SHA256SUMS.txt").is_file())

    def test_logcat_hard_kill_after_timeout_is_blocked_only_after_boundary(self) -> None:
        result, elapsed = self.run_collector(hang="logcat-hard-kill")
        self.assertEqual(3, result.returncode, result.stderr)
        self.assertLess(elapsed, 10.0)
        execution = (self.out / "EVIDENCE-COLLECTION-EXECUTION.txt").read_text(encoding="utf-8")
        adb_elapsed_ms, adb_status, timed_out = self.read_adb_event("logcat")
        self.assertEqual(137, adb_status)
        self.assertTrue(timed_out)
        self.assertGreaterEqual(adb_elapsed_ms, 1000)
        self.assertIn("event=evidence-timeout-summary", execution)
        self.assertIn("timeout_exit_status=137", execution)
        self.assertIn("result=BLOCKED", execution)
        self.assertTrue((self.out / "SHA256SUMS.txt").is_file())

    def test_early_status_137_is_not_relabelled_as_evidence_timeout(self) -> None:
        result, elapsed = self.run_collector(hang="logcat-early-137")
        self.assertEqual(1, result.returncode, result.stderr)
        self.assertLess(elapsed, 10.0)
        execution = (self.out / "EVIDENCE-COLLECTION-EXECUTION.txt").read_text(encoding="utf-8")
        adb_elapsed_ms, adb_status, timed_out = self.read_adb_event("logcat")
        self.assertEqual(137, adb_status)
        self.assertFalse(timed_out)
        self.assertLess(adb_elapsed_ms, 1000)
        self.assertNotIn("event=evidence-timeout-summary", execution)
        self.assertNotIn("result=BLOCKED", execution)
        self.assertFalse((self.out / ".EVIDENCE-COLLECTION-ADB-TIMEOUT").exists())
        calls = self.call_log.read_text(encoding="utf-8")
        self.assertIn("shell uiautomator dump /sdcard/compatibility-v2-window.xml", calls)
        self.assertIn("pull /sdcard/compatibility-v2-window.xml", calls)
        self.assertIn("exec-out screencap -p", calls)
        self.assertIn("shell dumpsys meminfo sa.hulksa.player.dev", calls)

    def test_uiautomator_hang_preserves_partial_evidence_and_cleanup_is_bounded(self) -> None:
        result, elapsed = self.run_collector(hang="uiautomator,cleanup")
        self.assertEqual(3, result.returncode, result.stderr)
        self.assertLess(elapsed, 12.0)
        execution = (self.out / "EVIDENCE-COLLECTION-EXECUTION.txt").read_text(encoding="utf-8")
        self.assertIn("stage=uiautomator-dump", execution)
        self.assertIn("timed_out=true", execution)
        self.assertIn("cleanup_test_package_status=124", execution)
        self.assertIn("cleanup_app_package_status=124", execution)
        self.assertIn("result=BLOCKED", execution)
        self.assertGreater((self.out / "logcat.txt").stat().st_size, 0)
        self.assertTrue((self.out / "SHA256SUMS.txt").is_file())
        calls = self.call_log.read_text(encoding="utf-8")
        self.assertEqual(1, calls.count("shell am force-stop sa.hulksa.player.dev.test"))
        self.assertEqual(2, calls.count("shell am force-stop sa.hulksa.player.dev\n"))

    def test_outer_guard_stops_future_unprotected_hang(self) -> None:
        source = self.collector.read_text(encoding="utf-8")
        needle = "\nadb_command_stage() {\n"
        self.assertIn(needle, source)
        guarded = self.root / "future-unprotected-collector.sh"
        guarded.write_text(source.replace(needle, "\n/bin/sleep 10\n" + needle, 1), encoding="utf-8")
        guarded.chmod(0o755)
        result, elapsed = self.run_collector(outer_timeout=1, collector=guarded)
        self.assertEqual(3, result.returncode, result.stderr)
        self.assertLess(elapsed, 5.0)
        execution = (self.out / "EVIDENCE-COLLECTION-EXECUTION.txt").read_text(encoding="utf-8")
        self.assertIn("event=outer-collector-timeout", execution)
        self.assertIn("outer_timed_out=true", execution)
        self.assertIn("result=BLOCKED", execution)
        self.assertIn("cleanup_test_package_status=0", execution)
        self.assertIn("cleanup_app_package_status=0", execution)

    def test_normal_successful_collection_is_not_timeout(self) -> None:
        result, elapsed = self.run_collector()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertLess(elapsed, 10.0)
        execution = (self.out / "EVIDENCE-COLLECTION-EXECUTION.txt").read_text(encoding="utf-8")
        self.assertIn("event=evidence-collection-summary", execution)
        self.assertIn("evidence_timed_out=false", execution)
        self.assertIn("result=PASS", execution)
        self.assertNotIn("event=evidence-timeout-summary", execution)
        for required in ("window.xml", "full-window.png", "MEMINFO.txt", "SHA256SUMS.txt"):
            self.assertGreater((self.out / required).stat().st_size, 0)

    def test_instrumentation_600_second_timeout_and_124_137_contract_is_unchanged(self) -> None:
        source = COLLECTOR_PATH.read_text(encoding="utf-8")
        self.assertIn("instrumentation_timeout_seconds=600", source)
        self.assertIn(
            'timeout --signal=TERM --kill-after=15s "${instrumentation_timeout_seconds}s" \\\n  adb shell am instrument -w -r',
            source,
        )
        self.assertIn('"$instrumentation_status" -eq 124', source)
        self.assertIn(
            '"$instrumentation_status" -eq 137 && "$instrumentation_elapsed_ms" -ge "$instrumentation_timeout_ms"',
            source,
        )


if __name__ == "__main__":
    unittest.main()
