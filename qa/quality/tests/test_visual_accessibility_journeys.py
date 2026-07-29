from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from PIL import Image

from qa.quality.accessibility.audit import audit_xml
from qa.quality.journeys.graph_audit import mermaid, validate_journeys
from qa.quality.visual.compare import BaselineError, compare, load_baseline_manifest


ROOT = Path(__file__).resolve().parents[3]


class VisualRegressionTest(unittest.TestCase):
    def test_visual_change_fails_and_writes_diff(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            baseline = root / "before.png"
            current = root / "after.png"
            diff = root / "diff.png"
            Image.new("RGB", (20, 20), "black").save(baseline)
            image = Image.new("RGB", (20, 20), "black")
            for x in range(10):
                for y in range(10):
                    image.putpixel((x, y), (255, 255, 255))
            image.save(current)
            result = compare(baseline, current, diff, changed_ratio_limit=0.01)
            self.assertEqual(result["status"], "FAIL")
            self.assertTrue(diff.is_file())

    def test_dynamic_mask_excludes_declared_region(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            baseline = root / "before.png"
            current = root / "after.png"
            Image.new("RGB", (20, 20), "black").save(baseline)
            Image.new("RGB", (20, 20), "white").save(current)
            result = compare(
                baseline,
                current,
                root / "diff.png",
                masks=[{"left": 0, "top": 0, "right": 19, "bottom": 19}],
            )
            self.assertEqual(result["status"], "PASS")

    def test_unapproved_or_unversioned_baseline_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            manifest = Path(temp) / "manifest.json"
            manifest.write_text(
                json.dumps({"approved": False, "build_sha": "short", "reason": ""}),
                encoding="utf-8",
            )
            with self.assertRaises(BaselineError):
                load_baseline_manifest(manifest)


class AccessibilityAuditTest(unittest.TestCase):
    def test_missing_label_is_critical_and_small_target_is_review(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            hierarchy = Path(temp) / "hierarchy.xml"
            hierarchy.write_text(
                '<hierarchy><node package="sa.hulksa.player.dev" clickable="true" '
                'bounds="[0,0][40,40]"/></hierarchy>',
                encoding="utf-8",
            )
            findings = audit_xml(hierarchy, density=160, is_tv=False)
            self.assertEqual(
                {item["code"] for item in findings},
                {"interactive_missing_label", "interactive_target_too_small"},
            )

    def test_system_ui_is_not_classified_as_product_accessibility(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            hierarchy = Path(temp) / "hierarchy.xml"
            hierarchy.write_text(
                '<hierarchy><node package="com.android.systemui" clickable="true" '
                'bounds="[0,0][10,10]"/></hierarchy>',
                encoding="utf-8",
            )
            self.assertEqual(audit_xml(hierarchy, density=160, is_tv=False), [])


class JourneyGraphTest(unittest.TestCase):
    def test_repository_journeys_are_valid_and_renderable(self) -> None:
        data = json.loads(
            (ROOT / "qa/quality/journeys/journeys.json").read_text(encoding="utf-8")
        )
        self.assertEqual(validate_journeys(data), [])
        graph = mermaid(data["journeys"][1])
        self.assertIn("flowchart TD", graph)
        self.assertIn("|d-pad|", graph)

    def test_uncovered_journey_requires_reason(self) -> None:
        data = {
            "journeys": [
                {
                    "id": "broken",
                    "entry": "a",
                    "nodes": ["a"],
                    "edges": [],
                    "input_modes": ["touch"],
                    "status": "NOT_COVERED",
                }
            ]
        }
        self.assertIn("broken: uncovered journey has no reason", validate_journeys(data))


if __name__ == "__main__":
    unittest.main()
