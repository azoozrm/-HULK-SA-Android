from __future__ import annotations

import os
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
WORKFLOW = ROOT / ".github/workflows/compatibility-v2-full.yml"


class FullMatrixBuildVersionTest(unittest.TestCase):
    def run_build(
        self, event: str, version_code: str, version_name: str, apk_version_code: str
    ) -> tuple[subprocess.CompletedProcess[str], list[str], bool]:
        _, _, step = WORKFLOW.read_text(encoding="utf-8").partition(
            "      - name: Assemble matrix test binaries\n"
        )
        _, _, script = step.split("\n      - ", 1)[0].partition("        run: |\n")
        self.assertTrue(script, "Full Matrix needs a test binary assembly step")

        with tempfile.TemporaryDirectory() as directory:
            workspace = Path(directory)
            (workspace / "build/compatibility-v2/static").mkdir(parents=True)
            (workspace / "gradlew").write_text(
                "#!/usr/bin/env bash\n"
                "set -euo pipefail\n"
                "printf '%s\\n' \"$@\" > gradle-args\n"
                "mkdir -p app/build/outputs/apk/{debug,androidTest/debug}\n"
                "touch app/build/outputs/apk/debug/app-debug.apk\n"
                "touch app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk\n",
                encoding="utf-8",
            )
            (workspace / "apkanalyzer").write_text(
                "#!/usr/bin/env bash\n"
                "set -euo pipefail\n"
                "test \"$1 $2\" = 'manifest version-code'\n"
                "test -f \"$3\"\n"
                "printf '%s\\n' \"$FIXTURE_APK_VERSION_CODE\"\n",
                encoding="utf-8",
            )
            for executable in ("gradlew", "apkanalyzer"):
                (workspace / executable).chmod(0o755)

            result = subprocess.run(
                ["bash", "-c", textwrap.dedent(script)],
                cwd=workspace,
                env={
                    **os.environ,
                    "PATH": f"{workspace}:{os.environ['PATH']}",
                    "GITHUB_EVENT_NAME": event,
                    "QUALIFICATION_VERSION_CODE": version_code,
                    "QUALIFICATION_VERSION_NAME": version_name,
                    "FIXTURE_APK_VERSION_CODE": apk_version_code,
                },
                capture_output=True,
                text=True,
                timeout=15,
            )
            args_file = workspace / "gradle-args"
            args = args_file.read_text(encoding="utf-8").splitlines() if args_file.exists() else []
            packaged = (workspace / "build/compatibility-v2/binaries/app-debug.apk").exists()
            return result, args, packaged

    def test_manual_dispatch_builds_the_requested_qualification_version(self) -> None:
        result, args, packaged = self.run_build("workflow_dispatch", "70", "1.0.2", "70")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("-PHULK_QUALIFICATION_VERSION_CODE=70", args)
        self.assertIn("-PHULK_QUALIFICATION_VERSION_NAME=1.0.2", args)
        self.assertTrue(packaged)

    def test_wrong_apk_version_is_rejected_before_packaging(self) -> None:
        result, _, packaged = self.run_build("workflow_dispatch", "70", "1.0.2", "68")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Expected matrix APK versionCode 70, found 68", result.stderr)
        self.assertFalse(packaged)

    def test_manual_dispatch_without_both_inputs_fails_before_build(self) -> None:
        result, args, packaged = self.run_build("workflow_dispatch", "70", "", "68")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual([], args)
        self.assertFalse(packaged)

    def test_historical_push_keeps_its_default_build(self) -> None:
        result, args, packaged = self.run_build("push", "", "", "68")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertNotIn("HULK_QUALIFICATION_VERSION", "\n".join(args))
        self.assertTrue(packaged)
