from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).parents[1] / "instrumentation_to_junit.py"
SPEC = importlib.util.spec_from_file_location("compatibility_v2_instrumentation", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class InstrumentationParserTest(unittest.TestCase):
    def test_pass_and_failure_are_not_retried_or_hidden(self) -> None:
        raw = """
INSTRUMENTATION_STATUS: class=sample.Tests
INSTRUMENTATION_STATUS: test=passes
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=sample.Tests
INSTRUMENTATION_STATUS: test=passes
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS: class=sample.Tests
INSTRUMENTATION_STATUS: test=fails
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=sample.Tests
INSTRUMENTATION_STATUS: test=fails
INSTRUMENTATION_STATUS: stack=AssertionError: clipped
INSTRUMENTATION_STATUS_CODE: -2
"""
        cases = MODULE.parse_instrumentation(raw, 1)
        statuses = {case.name: case.status for case in cases}
        self.assertEqual("PASS", statuses["passes"])
        self.assertEqual("FAIL", statuses["fails"])

    def test_assumption_failure_is_reported_as_skipped(self) -> None:
        raw = """
INSTRUMENTATION_STATUS: class=sample.Tests
INSTRUMENTATION_STATUS: test=tvOnly
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=sample.Tests
INSTRUMENTATION_STATUS: test=tvOnly
INSTRUMENTATION_STATUS: stack=org.junit.AssumptionViolatedException: requires television UI mode
INSTRUMENTATION_STATUS_CODE: -4
"""
        cases = MODULE.parse_instrumentation(raw, 0)
        self.assertEqual(1, len(cases))
        self.assertEqual("SKIPPED", cases[0].status)
        self.assertIn("AssumptionViolatedException", cases[0].detail)

    def test_zero_test_infrastructure_failure_is_failure(self) -> None:
        cases = MODULE.parse_instrumentation("INSTRUMENTATION_FAILED: process crashed", 1)
        self.assertEqual("FAIL", cases[0].status)


if __name__ == "__main__":
    unittest.main()
