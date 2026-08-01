from __future__ import annotations

import unittest

from qa.lab_verifier.focus_stability import evaluate_sequence


def xml(label: str, *, focused: bool = True) -> str:
    return (
        '<hierarchy><node text="' + label + '" content-desc="' + label + '" '
        'focused="' + ('true' if focused else 'false') + '" bounds="[0,0][100,50]" />'
        '</hierarchy>'
    )


class FocusStabilityTests(unittest.TestCase):
    def test_allows_asynchronous_transition_then_two_stable_reads(self) -> None:
        report = evaluate_sequence(
            [xml("toolbar-wifi"), xml("row-1-primary"), xml("row-1-primary")],
            "row-1-primary",
        )
        self.assertTrue(report["stable"])
        self.assertEqual(2, report["matching_suffix_reads"])

    def test_one_matching_read_is_not_stable(self) -> None:
        report = evaluate_sequence(
            [xml("toolbar-wifi"), xml("row-1-primary")],
            "row-1-primary",
        )
        self.assertFalse(report["stable"])
        self.assertEqual("WAIT", report["classification"])

    def test_non_consecutive_matches_are_not_stable(self) -> None:
        report = evaluate_sequence(
            [xml("row-1-primary"), xml("toolbar-wifi"), xml("row-1-primary")],
            "row-1-primary",
        )
        self.assertFalse(report["stable"])
        self.assertEqual(1, report["matching_suffix_reads"])

    def test_multiple_focused_nodes_are_blocked(self) -> None:
        report = evaluate_sequence(
            ['<hierarchy><node text="a" focused="true"/><node text="b" focused="true"/></hierarchy>'],
            "a",
        )
        self.assertEqual("BLOCKED", report["classification"])
        self.assertEqual("FOCUS_CARDINALITY_INVALID", report["code"])

    def test_malformed_xml_is_blocked(self) -> None:
        report = evaluate_sequence(["<hierarchy>"], "row-1-primary")
        self.assertEqual("BLOCKED", report["classification"])
        self.assertEqual("FOCUS_XML_PARSE_FAILURE", report["code"])


if __name__ == "__main__":
    unittest.main()
