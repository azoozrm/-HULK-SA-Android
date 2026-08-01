from __future__ import annotations

import unittest

from qa.lab_verifier.scope_check import normalize_paths, validate_scope


class ScopeCheckTests(unittest.TestCase):
    def test_accepts_only_lab_paths(self) -> None:
        report = validate_scope(
            [
                "qa/compatibility/gate.py",
                "qa/lab_verifier/verifier.py",
                "qa/lab_fixture_app/app/build.gradle.kts",
                ".github/workflows/compatibility-lab.yml",
                ".github/workflows/quality-lab-independent-qualification.yml",
            ]
        )
        self.assertTrue(report["valid"])
        self.assertEqual(report["invalid_paths"], [])

    def test_rejects_product_source(self) -> None:
        report = validate_scope(
            [
                "qa/compatibility/gate.py",
                "app/src/main/java/sa/hulksa/player/MainActivity.kt",
            ]
        )
        self.assertFalse(report["valid"])
        self.assertEqual(
            report["invalid_paths"],
            ["app/src/main/java/sa/hulksa/player/MainActivity.kt"],
        )

    def test_empty_comparison_is_not_valid(self) -> None:
        report = validate_scope([])
        self.assertFalse(report["valid"])
        self.assertEqual(report["changed_file_count"], 0)

    def test_normalization_is_deterministic(self) -> None:
        self.assertEqual(
            normalize_paths(["qa/compatibility/gate.py\n", "qa/compatibility/gate.py", ""]),
            ["qa/compatibility/gate.py"],
        )

    def test_rejects_unsafe_paths(self) -> None:
        for value in ("../app/build.gradle.kts", "/tmp/file", "qa\\compatibility\\gate.py"):
            with self.subTest(value=value):
                with self.assertRaises(ValueError):
                    normalize_paths([value])


if __name__ == "__main__":
    unittest.main()
