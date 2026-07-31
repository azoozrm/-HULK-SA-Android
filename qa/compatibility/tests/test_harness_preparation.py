from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
import unittest


LAB_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(LAB_ROOT))

SPEC = importlib.util.spec_from_file_location(
    "compatibility_prepare_harness",
    LAB_ROOT / "prepare-harness.py",
)
assert SPEC is not None and SPEC.loader is not None
PREPARE_HARNESS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PREPARE_HARNESS)


class HarnessPreparationTest(unittest.TestCase):
    def test_download_fixture_stabilization_is_debug_only_and_complete(self) -> None:
        source_path = LAB_ROOT / "QaActivity.kt"
        original = source_path.read_text(encoding="utf-8")

        prepared, changes = PREPARE_HARNESS.stabilize_qa_activity(original)

        self.assertEqual(
            [
                "download-refresh-state",
                "download-refresh-loop",
                "download-poll-interval",
            ],
            changes,
        )
        self.assertIn(
            "private const val QA_DOWNLOAD_POLL_MS = 1_000L",
            prepared,
        )
        self.assertNotIn(
            "private const val QA_DOWNLOAD_POLL_MS = 100L",
            prepared,
        )
        self.assertIn(
            "state.downloads != downloads || state.downloadSettings != settings",
            prepared,
        )
        self.assertIn(
            "originBytesServed != nextOriginBytesServed",
            prepared,
        )
        self.assertIn(
            "private const val QA_DOWNLOAD_POLL_MS = 100L",
            source_path.read_text(encoding="utf-8"),
        )

    def test_download_fixture_stabilization_fails_closed_on_shape_drift(self) -> None:
        with self.assertRaisesRegex(ValueError, "expected exactly one fixture anchor"):
            PREPARE_HARNESS.stabilize_qa_activity("package example\n")

    def test_download_fixture_stabilization_rejects_ambiguous_anchor(self) -> None:
        original = (LAB_ROOT / "QaActivity.kt").read_text(encoding="utf-8")
        duplicate = original + "\nprivate const val QA_DOWNLOAD_POLL_MS = 100L\n"

        with self.assertRaisesRegex(ValueError, "download-poll-interval"):
            PREPARE_HARNESS.stabilize_qa_activity(duplicate)


if __name__ == "__main__":
    unittest.main()
