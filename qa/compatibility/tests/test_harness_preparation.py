from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[3]
LAB_ROOT = ROOT / "qa/compatibility"
QA_ACTIVITY = LAB_ROOT / "QaActivity.kt"

sys.path.insert(0, str(LAB_ROOT))
try:
    SPEC = importlib.util.spec_from_file_location(
        "quality_harness_preparation",
        LAB_ROOT / "prepare-harness.py",
    )
    assert SPEC is not None and SPEC.loader is not None
    PREPARATION = importlib.util.module_from_spec(SPEC)
    SPEC.loader.exec_module(PREPARATION)
finally:
    sys.path.pop(0)


class QualityHarnessPreparationTest(unittest.TestCase):
    def test_real_qa_activity_gets_one_native_accessibility_evidence_node(self) -> None:
        original = QA_ACTIVITY.read_text(encoding="utf-8")
        prepared = PREPARATION.prepare_qa_activity(original)

        self.assertNotEqual(original, prepared)
        for expected in (
            "import android.view.View\n",
            "import android.view.accessibility.AccessibilityEvent\n",
            "import androidx.compose.foundation.layout.size\n",
            "import androidx.compose.runtime.key\n",
            "import androidx.compose.ui.unit.dp\n",
            "import androidx.compose.ui.viewinterop.AndroidView\n",
            "val qualityEvidence = buildList {",
            "key(qualityEvidence) {",
            "importantForAccessibility =\n"
            "                            View.IMPORTANT_FOR_ACCESSIBILITY_YES",
            "view.contentDescription = qualityEvidence",
            "AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED",
            "modifier = Modifier.size(1.dp)",
        ):
            self.assertEqual(1, prepared.count(expected), expected)
        self.assertEqual(
            1,
            prepared.count("contentDescription = qualityEvidence"),
        )
        self.assertEqual(original, QA_ACTIVITY.read_text(encoding="utf-8"))

    def test_already_prepared_activity_fails_closed(self) -> None:
        prepared = PREPARATION.prepare_qa_activity(
            QA_ACTIVITY.read_text(encoding="utf-8")
        )
        with self.assertRaisesRegex(ValueError, "already contains"):
            PREPARATION.prepare_qa_activity(prepared)

    def test_unknown_activity_shape_fails_closed(self) -> None:
        unknown = QA_ACTIVITY.read_text(encoding="utf-8").replace(
            ".semantics(mergeDescendants = false) {",
            ".semantics {",
            1,
        )
        with self.assertRaisesRegex(ValueError, "evidence block"):
            PREPARATION.prepare_qa_activity(unknown)


if __name__ == "__main__":
    unittest.main()
