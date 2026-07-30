from __future__ import annotations

import unittest

from qa.compatibility.focus_contract import (
    Bounds,
    focus_coverage,
    focus_key_sequence,
    minimum_unique_targets,
    visible_interactive_targets,
)


class FocusContractTest(unittest.TestCase):
    def test_download_sequence_visits_toolbar_and_three_action_rows(self) -> None:
        sequence = [name for name, _code in focus_key_sequence("downloads", exhaustive=False)]
        self.assertEqual(
            sequence,
            [
                "UP", "LEFT", "LEFT", "DOWN", "RIGHT", "RIGHT",
                "DOWN", "LEFT", "LEFT", "DOWN", "RIGHT", "RIGHT",
                "UP", "UP", "UP",
            ],
        )
        self.assertGreaterEqual(minimum_unique_targets("downloads"), 9)

    def test_exhaustive_contract_is_stronger_than_smoke(self) -> None:
        smoke = focus_key_sequence("movies", exhaustive=False)
        exhaustive = focus_key_sequence("movies", exhaustive=True)
        self.assertGreater(len(exhaustive), len(smoke))
        self.assertGreaterEqual(sum(name == "DOWN" for name, _ in exhaustive), 9)
        self.assertGreaterEqual(sum(name == "UP" for name, _ in exhaustive), 9)

    def test_visible_clickable_without_focus_is_reported(self) -> None:
        xml = b'''<hierarchy>
          <node package="sa.hulksa.player.dev" visible-to-user="true" enabled="true"
                clickable="true" focusable="false" bounds="[0,0][100,100]" text="lost" />
          <node package="sa.hulksa.player.dev" visible-to-user="true" enabled="true"
                clickable="true" focusable="true" bounds="[100,0][200,100]" content-desc="ok" />
        </hierarchy>'''
        targets, unfocusable = visible_interactive_targets(
            xml,
            package="sa.hulksa.player.dev",
            display_width=200,
            display_height=100,
        )
        self.assertEqual([item["label"] for item in targets], ["ok"])
        self.assertEqual([item["label"] for item in unfocusable], ["lost"])

    def test_focus_coverage_tolerates_focus_scale_but_not_other_control(self) -> None:
        targets = [
            {"label": "الغاء", "bounds": [100, 100, 300, 200]},
            {"label": "عادية", "bounds": [320, 100, 500, 200]},
        ]
        observed = [
            {"text": "الغاء", "bounds": [90, 90, 310, 210]},
        ]
        coverage = focus_coverage(targets, observed)
        self.assertEqual(coverage["reached_count"], 1)
        self.assertEqual(coverage["named_reached_count"], 1)
        self.assertEqual(coverage["unreached_named"][0]["label"], "عادية")

    def test_bounds_helpers_remain_positive(self) -> None:
        self.assertEqual(Bounds(0, 0, 10, 20).area, 200)
        self.assertEqual(Bounds(0, 0, 10, 20).center, (5.0, 10.0))


if __name__ == "__main__":
    unittest.main()
