from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
TV_ACTIVITY = REPO_ROOT / "app/src/main/java/sa/hulksa/player/TvMainActivity.kt"
MAIN_ACTIVITY = REPO_ROOT / "app/src/main/java/sa/hulksa/player/MainActivity.kt"
MANIFEST = REPO_ROOT / "app/src/main/AndroidManifest.xml"


class TvWindowEdgeToEdgeContractTest(unittest.TestCase):
    @staticmethod
    def tv_activity() -> str:
        return TV_ACTIVITY.read_text(encoding="utf-8")

    @staticmethod
    def tv_on_create(text: str) -> str:
        start = text.index("override fun onCreate(savedInstanceState: Bundle?)")
        end = text.index("override fun onNewIntent", start)
        return text[start:end]

    def test_tv_decor_edge_to_edge_is_configured_before_compose(self) -> None:
        text = self.tv_activity()
        block = self.tv_on_create(text)
        self.assertIn("import androidx.core.view.WindowCompat", text)
        self.assertEqual(text.count("WindowCompat.setDecorFitsSystemWindows(window, false)"), 1)
        super_call = block.index("super.onCreate(savedInstanceState)")
        edge_to_edge = block.index("WindowCompat.setDecorFitsSystemWindows(window, false)")
        compose = block.index("setContent {")
        self.assertLess(super_call, edge_to_edge)
        self.assertLess(edge_to_edge, compose)

    def test_system_bar_hiding_and_legacy_immersive_flags_remain(self) -> None:
        text = self.tv_activity()
        self.assertIn("window.insetsController?.hide(WindowInsets.Type.systemBars())", text)
        for flag in (
            "View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY",
            "View.SYSTEM_UI_FLAG_FULLSCREEN",
            "View.SYSTEM_UI_FLAG_HIDE_NAVIGATION",
            "View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN",
            "View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION",
            "View.SYSTEM_UI_FLAG_LAYOUT_STABLE",
        ):
            self.assertIn(flag, text)

    def test_phone_window_contract_stays_edge_to_edge(self) -> None:
        text = MAIN_ACTIVITY.read_text(encoding="utf-8")
        self.assertIn("private fun configurePhoneWindow()", text)
        self.assertIn("WindowCompat.setDecorFitsSystemWindows(window, false)", text)

    def test_tv_manifest_contract_stays_unchanged(self) -> None:
        text = MANIFEST.read_text(encoding="utf-8")
        start = text.index('android:name=".TvMainActivity"')
        block = text[start : text.index("</activity>", start) if "</activity>" in text[start:] else len(text)]
        self.assertIn('android:launchMode="singleTask"', block)
        self.assertIn('android:screenOrientation="sensorLandscape"', block)
        self.assertIn('android:theme="@style/Theme.HulkSA.TV"', block)

    def test_tv_window_fix_has_no_device_specific_workaround(self) -> None:
        text = self.tv_activity()
        self.assertNotIn("Build.MANUFACTURER", text)
        self.assertNotIn("Build.MODEL", text)
        self.assertNotIn("TCL", text)


if __name__ == "__main__":
    unittest.main()
