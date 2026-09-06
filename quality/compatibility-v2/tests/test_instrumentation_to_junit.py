from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).parents[1] / "instrumentation_to_junit.py"
SPEC = importlib.util.spec_from_file_location("compatibility_v2_instrumentation", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)

EVIDENCE_MODULE_PATH = Path(__file__).parents[1] / "evidence_gate.py"
EVIDENCE_SPEC = importlib.util.spec_from_file_location("compatibility_v2_evidence_for_instrumentation", EVIDENCE_MODULE_PATH)
EVIDENCE_MODULE = importlib.util.module_from_spec(EVIDENCE_SPEC)
assert EVIDENCE_SPEC.loader is not None
sys.modules[EVIDENCE_SPEC.name] = EVIDENCE_MODULE
EVIDENCE_SPEC.loader.exec_module(EVIDENCE_MODULE)


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

    def test_empty_output_with_zero_exit_is_failure(self) -> None:
        cases = MODULE.parse_instrumentation("", 0)
        self.assertEqual(1, len(cases))
        self.assertEqual("FAIL", cases[0].status)
        self.assertIn("no terminal per-test status records", cases[0].detail)

    def test_instrumentation_failed_with_zero_exit_is_failure(self) -> None:
        cases = MODULE.parse_instrumentation("INSTRUMENTATION_FAILED: runner died", 0)
        self.assertEqual(1, len(cases))
        self.assertEqual("FAIL", cases[0].status)
        self.assertIn("INSTRUMENTATION_FAILED", cases[0].detail)

    def test_running_without_terminal_result_is_failure_even_with_zero_exit(self) -> None:
        raw = """
INSTRUMENTATION_STATUS: class=sample.Tests
INSTRUMENTATION_STATUS: test=neverFinishes
INSTRUMENTATION_STATUS_CODE: 1
"""
        cases = MODULE.parse_instrumentation(raw, 0)
        self.assertEqual(1, len(cases))
        self.assertEqual("FAIL", cases[0].status)
        self.assertIn("before a terminal result", cases[0].detail)

    def test_valid_terminal_success_failure_and_skipped_are_preserved(self) -> None:
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
INSTRUMENTATION_STATUS: stack=AssertionError: expected
INSTRUMENTATION_STATUS_CODE: -2
INSTRUMENTATION_STATUS: class=sample.Tests
INSTRUMENTATION_STATUS: test=skips
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=sample.Tests
INSTRUMENTATION_STATUS: test=skips
INSTRUMENTATION_STATUS: stack=org.junit.AssumptionViolatedException: expected skip
INSTRUMENTATION_STATUS_CODE: -4
"""
        cases = MODULE.parse_instrumentation(raw, 0)
        statuses = {case.name: case.status for case in cases}
        self.assertEqual({"passes": "PASS", "fails": "FAIL", "skips": "SKIPPED"}, statuses)

    def test_parser_junit_evidence_gate_requires_terminal_success(self) -> None:
        valid_raw = """
INSTRUMENTATION_STATUS: class=sample.Tests
INSTRUMENTATION_STATUS: test=passes
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=sample.Tests
INSTRUMENTATION_STATUS: test=passes
INSTRUMENTATION_STATUS_CODE: 0
"""
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / "INSTRUMENTATION.xml"
            MODULE.write_junit(MODULE.parse_instrumentation(valid_raw, 0), output)
            self.assertEqual("PASS", EVIDENCE_MODULE.check_instrumentation_junit(output).status)

            MODULE.write_junit(MODULE.parse_instrumentation("", 0), output)
            self.assertEqual("FAIL", EVIDENCE_MODULE.check_instrumentation_junit(output).status)


if __name__ == "__main__":
    unittest.main()
