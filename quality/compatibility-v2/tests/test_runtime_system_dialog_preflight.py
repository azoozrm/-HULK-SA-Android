from __future__ import annotations

import os
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path



class RuntimeSystemDialogPreflightTest(unittest.TestCase):
    def setUp(self) -> None:
        self.repo_root = Path(__file__).resolve().parents[3]
        self.helper = self.repo_root / 'quality/compatibility-v2/stabilize_foldable_launcher.sh'

    @staticmethod
    def _write_fake_adb(root: Path, initial_xml: str, cleared_xml: str = '<hierarchy/>') -> tuple[Path, Path]:
        state = root / 'current.xml'
        state.write_text(initial_xml, encoding='utf-8')
        cleared = root / 'cleared.xml'
        cleared.write_text(cleared_xml, encoding='utf-8')
        calls = root / 'calls.txt'
        adb = root / 'adb'
        adb.write_text(
            textwrap.dedent(
                '''\
                #!/usr/bin/env bash
                set -euo pipefail
                echo "$*" >> "$FAKE_ADB_CALLS"
                if [[ "$1" == shell && "$2" == uiautomator && "$3" == dump ]]; then
                  exit 0
                fi
                if [[ "$1" == pull ]]; then
                  cp "$FAKE_ADB_STATE" "$3"
                  exit 0
                fi
                if [[ "$1" == shell && "$2" == input && "$3" == tap ]]; then
                  cp "$FAKE_ADB_CLEARED" "$FAKE_ADB_STATE"
                  exit 0
                fi
                exit 9
                '''
            ),
            encoding='utf-8',
        )
        adb.chmod(0o755)
        return adb, calls

    def _run(self, profile: str, xml: str) -> tuple[subprocess.CompletedProcess[str], str, str]:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            out = root / 'evidence'
            adb, calls = self._write_fake_adb(root, xml)
            env = dict(
                os.environ,
                ADB_BIN=str(adb),
                FAKE_ADB_CALLS=str(calls),
                FAKE_ADB_STATE=str(root / 'current.xml'),
                FAKE_ADB_CLEARED=str(root / 'cleared.xml'),
                PREFLIGHT_MAX_PROBES='1',
                PREFLIGHT_SLEEP_SECONDS='0',
            )
            result = subprocess.run(
                ['bash', str(self.helper), profile, str(out), 'sa.hulksa.player.dev'],
                env=env,
                capture_output=True,
                text=True,
            )
            evidence = (out / 'SYSTEM-DIALOG-PREFLIGHT.txt').read_text(encoding='utf-8')
            call_text = calls.read_text(encoding='utf-8') if calls.exists() else ''
            return result, evidence, call_text

    def test_exact_pixel_launcher_anr_is_closed_by_resource_bounds(self) -> None:
        xml = (
            '<hierarchy><node text="Pixel Launcher isn\'t responding" package="android">'
            '<node text="Close app" resource-id="android:id/aerr_close" package="android" '
            'bounds="[100,200][300,400]"/></node></hierarchy>'
        )
        result, evidence, calls = self._run('foldable-unfolded-api35', xml)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('action=close-pixel-launcher-anr', evidence)
        self.assertIn('launcher_anr_cleared=true', evidence)
        self.assertIn('shell input tap 200 300', calls)

    def test_non_foldable_profile_never_calls_adb(self) -> None:
        result, evidence, calls = self._run('phone-medium-api35', '<hierarchy/>')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('applicable=false', evidence)
        self.assertEqual('', calls)

    def test_target_application_dialog_is_never_dismissed(self) -> None:
        xml = (
            '<hierarchy><node text="HULK SA isn\'t responding" package="android">'
            '<node text="Close app" resource-id="android:id/aerr_close" package="android" '
            'bounds="[100,200][300,400]"/></node></hierarchy>'
        )
        result, evidence, calls = self._run('foldable-unfolded-api35', xml)
        self.assertEqual(3, result.returncode)
        self.assertIn('ambiguous system error dialog; refusing broad dismissal', evidence)
        self.assertNotIn('shell input tap', calls)

    def test_runtime_wrapper_intercepts_only_logcat_clear(self) -> None:
        wrapper = self.repo_root / 'quality/compatibility-v2/run_runtime_profile.sh'
        implementation = self.repo_root / 'quality/compatibility-v2/run_runtime_profile_impl.sh'
        content = wrapper.read_text(encoding='utf-8')
        self.assertIn('"${1:-}" == "logcat"', content)
        self.assertIn('"${2:-}" == "-c"', content)
        self.assertIn('stabilize_foldable_launcher.sh', content)
        self.assertIn('source quality/compatibility-v2/run_runtime_profile_impl.sh "$@"', content)
        self.assertIn('command "$REAL_ADB_BIN" "$@"', content)
        self.assertNotIn('am force-stop sa.hulksa.player', content)
        for script in (wrapper, implementation, self.helper):
            subprocess.run(['bash', '-n', str(script)], check=True)

    def test_original_runtime_implementation_is_preserved_byte_for_byte(self) -> None:
        import hashlib

        implementation = self.repo_root / 'quality/compatibility-v2/run_runtime_profile_impl.sh'
        data = implementation.read_bytes()
        git_blob_sha = hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
        self.assertEqual('2986789454790dc1dac9511f3525b9c81a6a7339', git_blob_sha)



if __name__ == '__main__':
    unittest.main()
