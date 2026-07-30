from __future__ import annotations

from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from qa.quality.scripts.pr_scope import classify_scope  # noqa: E402


class PrScopeTest(unittest.TestCase):
    def test_quality_lab_and_android_test_changes_are_lab_only(self) -> None:
        data = classify_scope(
            [
                "qa/compatibility/gate.py",
                "qa/quality/tests/test_pr_scope.py",
                ".github/workflows/quality-ui.yml",
                "app/src/androidTest/java/sa/hulksa/player/Test.kt",
            ]
        )
        self.assertTrue(data["lab_only"])
        self.assertFalse(data["enforce_product_findings"])
        self.assertEqual(data["product_files"], [])

    def test_test_only_gradle_changes_are_allowed(self) -> None:
        patch = """@@ -1,0 +2,4 @@
+    testOptions {
+        execution = \"ANDROIDX_TEST_ORCHESTRATOR\"
+    }
+    androidTestUtil(\"androidx.test:orchestrator:1.6.1\")
"""
        data = classify_scope(["app/build.gradle.kts", "qa/quality/README.md"], patch)
        self.assertTrue(data["lab_only"])
        self.assertEqual(data["restricted_gradle_lines"], [])

    def test_production_source_change_enables_strict_product_gate(self) -> None:
        data = classify_scope(
            [
                "app/src/main/java/sa/hulksa/player/data/DownloadRepository.kt",
                "qa/compatibility/gate.py",
            ]
        )
        self.assertFalse(data["lab_only"])
        self.assertTrue(data["enforce_product_findings"])
        self.assertIn(
            "app/src/main/java/sa/hulksa/player/data/DownloadRepository.kt",
            data["product_files"],
        )

    def test_production_gradle_change_is_not_misclassified_as_lab_only(self) -> None:
        patch = """@@ -1 +1 @@
-    implementation(\"old:runtime:1\")
+    implementation(\"new:runtime:2\")
"""
        data = classify_scope(["app/build.gradle.kts"], patch)
        self.assertFalse(data["lab_only"])
        self.assertIn("app/build.gradle.kts", data["product_files"])
        self.assertTrue(data["restricted_gradle_lines"])

    def test_missing_diff_evidence_fails_closed(self) -> None:
        data = classify_scope([])
        self.assertFalse(data["lab_only"])
        self.assertTrue(data["enforce_product_findings"])


if __name__ == "__main__":
    unittest.main()
