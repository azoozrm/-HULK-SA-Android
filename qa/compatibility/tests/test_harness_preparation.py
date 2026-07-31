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
    def test_real_qa_activity_gets_one_fail_closed_semantics_refresh_key(self) -> None:
        original = QA_ACTIVITY.read_text(encoding="utf-8")
        prepared = PREPARATION.prepare_qa_activity(original)

        self.assertNotEqual(original, prepared)
        self.assertEqual(
            1,
            prepared.count("import androidx.compose.runtime.key\n"),
        )
        self.assertEqual(
            1,
            prepared.count(
                "    key(\n"
                "        pageMarker,\n"
                "        hasOriginByteProgress,\n"
                "        hasRealDownloadProgress,\n"
                "        lastDownloadAction,\n"
                "    ) {"
            ),
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
        with self.assertRaisesRegex(ValueError, "Box anchor"):
            PREPARATION.prepare_qa_activity(unknown)


if __name__ == "__main__":
    unittest.main()
