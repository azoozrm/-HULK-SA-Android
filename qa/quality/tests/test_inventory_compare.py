from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from qa.quality.inventory.compare import compare, normalize


class InventoryCompareTest(unittest.TestCase):
    def test_source_line_drift_is_non_semantic(self) -> None:
        expected = {
            "schema_version": 1,
            "screens": [
                {
                    "id": "composable:DownloadsScreen",
                    "source": "app/MainShellScreen.kt",
                    "line": 1514,
                    "route": "downloads",
                }
            ],
        }
        actual = {
            "schema_version": 1,
            "screens": [
                {
                    "id": "composable:DownloadsScreen",
                    "source": "app/MainShellScreen.kt",
                    "line": 1438,
                    "route": "downloads",
                }
            ],
        }
        self.assertEqual(normalize(expected), normalize(actual))

    def test_semantic_inventory_change_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            expected = root / "expected.json"
            actual = root / "actual.json"
            expected.write_text(
                json.dumps(
                    {
                        "schema_version": 1,
                        "actions": [
                            {
                                "id": "DownloadsScreen:onRetryDownload",
                                "action": "click",
                                "line": 10,
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            actual.write_text(
                json.dumps(
                    {
                        "schema_version": 1,
                        "actions": [
                            {
                                "id": "DownloadsScreen:onDeleteDownload",
                                "action": "click",
                                "line": 20,
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )

            matches, diff = compare(expected, actual)

            self.assertFalse(matches)
            self.assertIn("onRetryDownload", diff)
            self.assertIn("onDeleteDownload", diff)


if __name__ == "__main__":
    unittest.main()
