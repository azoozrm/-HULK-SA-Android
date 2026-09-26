from __future__ import annotations

import re
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
WORKFLOW_PATH = REPO_ROOT / ".github" / "workflows" / "production-authenticated-smoke.yml"
SMOKE_TEST_PATH = (
    REPO_ROOT
    / "app"
    / "src"
    / "androidTest"
    / "java"
    / "sa"
    / "hulksa"
    / "player"
    / "ProductionAuthenticatedSmokeTest.kt"
)
PROTECTED_INPUTS = {
    "ARG_ACCESS_CODE": ("hulkE2eAccessCode", "HULK_E2E_ACCESS_CODE"),
    "ARG_USERNAME": ("hulkE2eUsername", "HULK_E2E_USERNAME"),
    "ARG_PASSWORD": ("hulkE2ePassword", "HULK_E2E_PASSWORD"),
}


class ProductionSmokeWorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.workflow = WORKFLOW_PATH.read_text(encoding="utf-8")
        cls.smoke_test = SMOKE_TEST_PATH.read_text(encoding="utf-8")

    def test_protected_environment_maps_every_required_secret(self) -> None:
        self.assertIn("environment: production-e2e", self.workflow)
        for _, secret_name in PROTECTED_INPUTS.values():
            self.assertIn(
                f"{secret_name}: ${{{{ secrets.{secret_name} }}}}",
                self.workflow,
                f"{secret_name} is not mapped from the protected environment",
            )

    def test_validation_fails_closed_without_revealing_values(self) -> None:
        self.assertIn("set -euo pipefail", self.workflow)
        self.assertNotRegex(self.workflow, r"(?m)^\s*set\s+-[a-z]*x")
        self.assertNotIn('echo "$HULK_E2E_', self.workflow)
        for _, secret_name in PROTECTED_INPUTS.values():
            self.assertIn(
                f'[[ -n "${secret_name}" ]]',
                self.workflow,
                f"{secret_name} is not validated for non-emptiness",
            )

    def test_instrumentation_receives_every_required_argument(self) -> None:
        self.assertIn(
            "-Pandroid.testInstrumentationRunnerArguments.class=sa.hulksa.player.ProductionAuthenticatedSmokeTest",
            self.workflow,
        )
        self.assertIn(
            "-Pandroid.testInstrumentationRunnerArguments.hulkProductionE2e=true",
            self.workflow,
        )
        for argument, environment in PROTECTED_INPUTS.values():
            self.assertIn(
                f'-Pandroid.testInstrumentationRunnerArguments.{argument}="${environment}"',
                self.workflow,
                f"{argument} is not passed to the instrumentation test",
            )

    def test_argument_names_still_match_the_instrumentation_contract(self) -> None:
        for constant, (argument, _) in PROTECTED_INPUTS.items():
            self.assertIn(
                f'const val {constant} = "{argument}"',
                self.smoke_test,
                f"ProductionAuthenticatedSmokeTest no longer declares {constant}",
            )

    def test_workflow_references_only_the_expected_secret_names(self) -> None:
        referenced = set(re.findall(r"secrets\.([A-Za-z0-9_]+)", self.workflow))
        self.assertEqual(
            {secret_name for _, secret_name in PROTECTED_INPUTS.values()},
            referenced,
        )


if __name__ == "__main__":
    unittest.main()
