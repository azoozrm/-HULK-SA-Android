from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("run_lab", ROOT / "run-lab.py")
assert SPEC is not None and SPEC.loader is not None
RUN_LAB = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = RUN_LAB
SPEC.loader.exec_module(RUN_LAB)


class RunLabArgumentParsingTest(unittest.TestCase):
    def test_parse_csv_splits_orientation_matrix_value(self) -> None:
        self.assertEqual(
            RUN_LAB.parse_csv("portrait,landscape"),
            ["portrait", "landscape"],
        )

    def test_parse_float_csv_splits_font_scale_matrix_value(self) -> None:
        self.assertEqual(
            RUN_LAB.parse_float_csv("1.0,1.30"),
            [1.0, 1.3],
        )

    def test_single_values_remain_single_entries(self) -> None:
        self.assertEqual(RUN_LAB.parse_csv("landscape"), ["landscape"])
        self.assertEqual(RUN_LAB.parse_float_csv("1.0"), [1.0])

    def test_orientation_geometry_is_calculated_per_entry(self) -> None:
        self.assertEqual(RUN_LAB.oriented_dimensions(1080, 2400, "portrait"), (1080, 2400))
        self.assertEqual(RUN_LAB.oriented_dimensions(1080, 2400, "landscape"), (2400, 1080))


if __name__ == "__main__":
    unittest.main()
