from __future__ import annotations

import hashlib
import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).parents[1] / "static_validate.py"
SPEC = importlib.util.spec_from_file_location("compatibility_v2_static", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class StaticValidationTest(unittest.TestCase):
    def fixture(self, root: Path, marker: str = "", player: str | None = None) -> str:
        (root / "app/src/main/res/drawable-nodpi").mkdir(parents=True)
        (root / "app/src/main/java/sa/hulksa/player/ui/components").mkdir(parents=True)
        (root / "app/src/main/java/sa/hulksa/player/ui/screens").mkdir(parents=True)
        (root / ".github/workflows").mkdir(parents=True)
        logo = b"approved-test-logo"
        logo_sha = hashlib.sha256(logo).hexdigest()
        (root / "app/src/main/res/drawable-nodpi/hulk_sa_logo.webp").write_bytes(logo)
        (root / "app/src/main/res/drawable-nodpi/ic_banner.webp").write_bytes(logo)
        (root / "app/build.gradle.kts").write_text(
            'namespace = "sa.hulksa.player"\napplicationId = "sa.hulksa.player"\n'
            'versionCode = 64\nversionName = "0.9.3.20"\n'
            'val productionPortalUrl = "http://3162356.xyz:8080"\n'
            'arm64-v8a armeabi-v7a x86_64\n',
            encoding="utf-8",
        )
        (root / "app/src/main/AndroidManifest.xml").write_text(
            '<?xml version="1.0"?><manifest xmlns:android="http://schemas.android.com/apk/res/android">'
            '<uses-feature android:name="android.software.leanback" android:required="false"/>'
            '<uses-feature android:name="android.hardware.touchscreen" android:required="false"/>'
            '<application android:supportsRtl="true" android:banner="@drawable/tv_banner">'
            '<activity><intent-filter><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity>'
            '<activity><intent-filter><category android:name="android.intent.category.LEANBACK_LAUNCHER"/></intent-filter></activity>'
            '</application></manifest>',
            encoding="utf-8",
        )
        (root / "app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt").write_text(
            f"ContentScale.Fit\n{marker}\n",
            encoding="utf-8",
        )
        valid_player = """
AndroidKeyEvent.KEYCODE_DPAD_LEFT -> if (!request.isLive && surfaceFocused) {
    seekBy(-SEEK_STEP_MS); true
} else false
AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> if (!request.isLive && surfaceFocused) {
    seekBy(SEEK_STEP_MS); true
} else false
AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
    previewMs = (previewMs - SEEK_STEP_MS).coerceAtLeast(0L)
    true
}
AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
    previewMs = (previewMs + SEEK_STEP_MS).coerceAtMost(durationMs)
    true
}
"""
        (root / "app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt").write_text(
            valid_player if player is None else player,
            encoding="utf-8",
        )
        return logo_sha

    def test_valid_repository_contract_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            logo_sha = self.fixture(root)
            original = dict(MODULE.APPROVED_BRAND_ASSETS)
            MODULE.APPROVED_BRAND_ASSETS["app/src/main/res/drawable-nodpi/ic_banner.webp"] = logo_sha
            try:
                checks = MODULE.validate_repo(root, logo_sha)
            finally:
                MODULE.APPROVED_BRAND_ASSETS.clear()
                MODULE.APPROVED_BRAND_ASSETS.update(original)
            self.assertFalse([check for check in checks if check.status == "FAIL"])

    def test_production_marker_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            logo_sha = self.fixture(root, marker="qaTvPageContent")
            checks = MODULE.validate_repo(root, logo_sha)
            result = next(check for check in checks if check.id == "production-test-hooks-absent")
            self.assertEqual("FAIL", result.status)

    def test_logo_byte_change_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            expected = self.fixture(root)
            (root / "app/src/main/res/drawable-nodpi/hulk_sa_logo.webp").write_bytes(b"changed")
            result = next(
                check for check in MODULE.validate_repo(root, expected)
                if check.id == "approved-logo-sha256"
            )
            self.assertEqual("FAIL", result.status)

    def test_reversed_player_seek_contract_fails(self) -> None:
        text = """
KEYCODE_DPAD_LEFT -> if (!request.isLive && surfaceFocused) { seekBy(SEEK_STEP_MS); true }
KEYCODE_DPAD_RIGHT -> if (!request.isLive && surfaceFocused) { seekBy(-SEEK_STEP_MS); true }
"""
        self.assertFalse(MODULE.player_surface_seek_contract(text))

    def test_focus_retry_loop_is_reported(self) -> None:
        text = """
repeat(4) {
    delay(100L)
    runCatching { resumeFocus.requestFocus() }
}
"""
        findings = MODULE.player_focus_race_findings(text)
        self.assertIn("repeated requestFocus retry loop", findings)
        self.assertIn("time-delayed requestFocus", findings)

    def test_lifecycle_owned_focus_without_delay_passes(self) -> None:
        text = "LaunchedEffect(owner) { owner.requestFocus() }"
        self.assertEqual([], MODULE.player_focus_race_findings(text))


if __name__ == "__main__":
    unittest.main()
