from __future__ import annotations

from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from runtime_guard import classify_blocking_dialog


class RuntimeGuardTests(unittest.TestCase):
    def test_external_launcher_anr_is_retryable_infrastructure(self):
        xml = '''<hierarchy><node>
          <node resource-id="android:id/alertTitle" text="Pixel Launcher isn't responding" bounds="[100,100][900,200]" />
          <node resource-id="android:id/aerr_close" text="Close app" bounds="[200,300][600,420]" />
        </node></hierarchy>'''
        result = classify_blocking_dialog(xml)
        self.assertIsNotNone(result)
        self.assertEqual("infrastructure", result["classification"])
        self.assertEqual("SYSTEM_SERVICE_UNAVAILABLE", result["code"])
        self.assertTrue(result["retry_allowed"])
        self.assertEqual([400, 360], result["dismiss_center"])

    def test_fixture_anr_is_not_retryable(self):
        xml = '''<hierarchy><node>
          <node resource-id="android:id/alertTitle" text="HULK Lab Fixture isn't responding" bounds="[0,0][500,100]" />
          <node resource-id="android:id/aerr_close" text="Close app" bounds="[50,150][250,250]" />
        </node></hierarchy>'''
        result = classify_blocking_dialog(xml)
        self.assertIsNotNone(result)
        self.assertEqual("fixture", result["classification"])
        self.assertEqual("FIXTURE_APP_UNRESPONSIVE", result["code"])
        self.assertFalse(result["retry_allowed"])

    def test_normal_fixture_ui_has_no_retry_classification(self):
        xml = '''<hierarchy><node content-desc="qa-page:fixture">
          <node text="toolbar-wifi" focused="true" focusable="true" />
        </node></hierarchy>'''
        self.assertIsNone(classify_blocking_dialog(xml))

    def test_unrelated_dialog_is_not_treated_as_transient_anr(self):
        xml = '''<hierarchy><node>
          <node resource-id="android:id/alertTitle" text="Permission required" bounds="[0,0][500,100]" />
        </node></hierarchy>'''
        self.assertIsNone(classify_blocking_dialog(xml))


if __name__ == "__main__":
    unittest.main()
