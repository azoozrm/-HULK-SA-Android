from __future__ import annotations

import hashlib
import importlib.util
import struct
import sys
import tempfile
import unittest
import zlib
from pathlib import Path

MODULE_PATH = Path(__file__).parents[1] / "static_validate.py"
SPEC = importlib.util.spec_from_file_location("compatibility_v2_static", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class StaticValidationTest(unittest.TestCase):
    @staticmethod
    def png_bytes(width: int, height: int) -> bytes:
        def chunk(kind: bytes, data: bytes) -> bytes:
            checksum = zlib.crc32(kind + data) & 0xFFFFFFFF
            return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", checksum)

        rows = b"".join(b"\x00" + bytes(width * 4) for _ in range(height))
        header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
        return (
            b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(rows))
            + chunk(b"IEND", b"")
        )

    def fixture(self, root: Path, marker: str = "", player: str | None = None) -> str:
        (root / "app/src/main/res/drawable-nodpi").mkdir(parents=True)
        (root / "app/src/main/java/sa/hulksa/player/ui/components").mkdir(parents=True)
        (root / "app/src/main/java/sa/hulksa/player/data").mkdir(parents=True)
        (root / "app/src/main/java/sa/hulksa/player/model").mkdir(parents=True)
        (root / "app/src/main/java/sa/hulksa/player/ui/screens").mkdir(parents=True)
        (root / ".github/workflows").mkdir(parents=True)
        (root / ".github/workflows/build-apk.yml").write_text(
            "find app/src/main/res -type f\n"
            "-name 'hulk_sa_adaptive_*'\n"
            "test ! -e project/app/src/main/res/drawable/ic_stat_hulk.xml\n",
            encoding="utf-8",
        )
        logo = b"approved-test-logo"
        logo_sha = hashlib.sha256(logo).hexdigest()
        (root / "app/src/main/res/drawable-nodpi/hulk_sa_logo.png").write_bytes(logo)
        for relative, dimensions in MODULE.ICON_ASSET_DIMENSIONS.items():
            asset = root / relative
            asset.parent.mkdir(parents=True, exist_ok=True)
            asset.write_bytes(self.png_bytes(*dimensions))
        (root / "app/build.gradle.kts").write_text(
            'namespace = "sa.hulksa.player"\napplicationId = "sa.hulksa.player"\n'
            'versionCode = 64\nversionName = "0.9.3.20"\n'
            'val resellerApiUrl = "https://hulksa.com"\n'
            'val verifyProductionRuntimeConfig = tasks.register("verifyProductionRuntimeConfig")\n'
            'buildConfigField("String", "RESELLER_API_URL", resellerApiUrl)\n'
            'arm64-v8a armeabi-v7a x86_64\n',
            encoding="utf-8",
        )
        (root / "app/src/main/AndroidManifest.xml").write_text(
            '<?xml version="1.0"?><manifest xmlns:android="http://schemas.android.com/apk/res/android">'
            '<uses-feature android:name="android.software.leanback" android:required="false"/>'
            '<uses-feature android:name="android.hardware.touchscreen" android:required="false"/>'
            '<application android:supportsRtl="true" android:icon="@mipmap/ic_launcher" '
            'android:banner="@mipmap/tv_banner">'
            '<activity android:name=".MainActivity"><intent-filter>'
            '<category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity>'
            '<activity android:name=".TvMainActivity" android:icon="@mipmap/ic_launcher_tv" '
            'android:banner="@mipmap/tv_banner"><intent-filter>'
            '<category android:name="android.intent.category.LEANBACK_LAUNCHER"/></intent-filter></activity>'
            '</application></manifest>',
            encoding="utf-8",
        )
        (root / "app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt").write_text(
            f"ContentScale.Fit\n{marker}\n",
            encoding="utf-8",
        )
        (root / "app/src/main/java/sa/hulksa/player/ui/screens/LoginScreen.kt").write_text(
            'label = "كود الدخول"\nlabel = "اسم المستخدم"\nlabel = "كلمة المرور"\n',
            encoding="utf-8",
        )
        (root / "app/src/main/java/sa/hulksa/player/data/PortalResolver.kt").write_text(
            "BuildConfig.RESELLER_API_URL\n"
            'addPathSegments("api/reseller/resolve")\n'
            "PortalConfig.Source.ACCESS_CODE\n",
            encoding="utf-8",
        )
        (root / "app/src/main/java/sa/hulksa/player/model/Models.kt").write_text(
            "data class Credentials(val accessCode: String)\n",
            encoding="utf-8",
        )
        valid_player = """
val tvRemoteInput = adaptiveUi.isTelevision || adaptiveUi.inputMode == HulkInputMode.REMOTE
AndroidKeyEvent.KEYCODE_DPAD_LEFT -> if (!request.isLive && surfaceFocused) {
    seekBy(if (tvRemoteInput) SEEK_STEP_MS else -SEEK_STEP_MS); true
} else false
AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> if (!request.isLive && surfaceFocused) {
    seekBy(if (tvRemoteInput) -SEEK_STEP_MS else SEEK_STEP_MS); true
} else false
AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
    previewMs = if (tvRemoteInput) {
        (previewMs + SEEK_STEP_MS).coerceAtMost(durationMs)
    } else {
        (previewMs - SEEK_STEP_MS).coerceAtLeast(0L)
    }
    true
}
AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
    previewMs = if (tvRemoteInput) {
        (previewMs - SEEK_STEP_MS).coerceAtLeast(0L)
    } else {
        (previewMs + SEEK_STEP_MS).coerceAtMost(durationMs)
    }
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
            for relative in MODULE.APPROVED_BRAND_ASSETS:
                if not relative.endswith("hulk_sa_logo.png"):
                    MODULE.APPROVED_BRAND_ASSETS[relative] = MODULE.sha256(root / relative)
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

    def test_missing_tv_density_asset_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            logo_sha = self.fixture(root)
            (root / "app/src/main/res/mipmap-xxxhdpi/ic_launcher_tv.png").unlink()
            result = next(
                check for check in MODULE.validate_repo(root, logo_sha)
                if check.id == "android-icon-density-matrix"
            )
            self.assertEqual("FAIL", result.status)

    def test_missing_adaptive_foreground_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            logo_sha = self.fixture(root)
            (root / "app/src/main/res/drawable-nodpi/hulk_sa_adaptive_foreground.png").unlink()
            result = next(
                check for check in MODULE.validate_repo(root, logo_sha)
                if check.id == "android-icon-density-matrix"
            )
            self.assertEqual("FAIL", result.status)

    def test_tv_launcher_must_be_distinct_from_phone_launcher(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            logo_sha = self.fixture(root)
            manifest = root / "app/src/main/AndroidManifest.xml"
            manifest.write_text(
                manifest.read_text(encoding="utf-8").replace(
                    '@mipmap/ic_launcher_tv',
                    '@mipmap/ic_launcher',
                ),
                encoding="utf-8",
            )
            result = next(
                check for check in MODULE.validate_repo(root, logo_sha)
                if check.id == "manifest-tv-banner"
            )
            self.assertEqual("FAIL", result.status)

    def test_logo_byte_change_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            expected = self.fixture(root)
            (root / "app/src/main/res/drawable-nodpi/hulk_sa_logo.png").write_bytes(b"changed")
            result = next(
                check for check in MODULE.validate_repo(root, expected)
                if check.id == "approved-logo-sha256"
            )
            self.assertEqual("FAIL", result.status)

    def test_legacy_build_logo_restore_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            logo_sha = self.fixture(root)
            payload = root / ".payload/assets/logo.part-00"
            payload.parent.mkdir(parents=True)
            payload.write_text("legacy-logo", encoding="utf-8")
            workflow = root / ".github/workflows/build-apk.yml"
            workflow.write_text(
                'curl "https://hulksa.com/assets/hulk-official-logo.webp"\n'
                "cat .payload/assets/logo.part-*\n"
                "text.replace('@mipmap/ic_launcher', '@drawable/hulk_sa_logo')\n",
                encoding="utf-8",
            )
            result = next(
                check for check in MODULE.validate_repo(root, logo_sha)
                if check.id == "build-workflow-current-icon-package"
            )
            self.assertEqual("FAIL", result.status)

    def test_dynamic_rtl_player_seek_contract_passes(self) -> None:
        text = """
val tvRemoteInput = adaptiveUi.isTelevision || adaptiveUi.inputMode == HulkInputMode.REMOTE
KEYCODE_DPAD_LEFT -> if (!request.isLive && surfaceFocused) {
    seekBy(if (tvRemoteInput) seekStepMs else -seekStepMs); true
} else false
KEYCODE_DPAD_RIGHT -> if (!request.isLive && surfaceFocused) {
    seekBy(if (tvRemoteInput) -seekStepMs else seekStepMs); true
} else false
KEYCODE_DPAD_LEFT -> {
    previewMs = if (tvRemoteInput) {
        (previewMs + seekStepMs).coerceAtMost(durationMs)
    } else {
        (previewMs - seekStepMs).coerceAtLeast(0L)
    }
    true
}
KEYCODE_DPAD_RIGHT -> {
    previewMs = if (tvRemoteInput) {
        (previewMs - seekStepMs).coerceAtLeast(0L)
    } else {
        (previewMs + seekStepMs).coerceAtMost(durationMs)
    }
    true
}
"""
        self.assertTrue(MODULE.player_surface_seek_contract(text))
        self.assertTrue(MODULE.player_seekbar_contract(text))

    def test_legacy_ltr_player_seek_contract_fails(self) -> None:
        text = """
val tvRemoteInput = adaptiveUi.isTelevision || adaptiveUi.inputMode == HulkInputMode.REMOTE
KEYCODE_DPAD_LEFT -> if (!request.isLive && surfaceFocused) { seekBy(-SEEK_STEP_MS); true }
KEYCODE_DPAD_RIGHT -> if (!request.isLive && surfaceFocused) { seekBy(SEEK_STEP_MS); true }
"""
        self.assertFalse(MODULE.player_surface_seek_contract(text))

    def test_unscoped_rtl_player_seek_contract_fails(self) -> None:
        text = """
KEYCODE_DPAD_LEFT -> if (!request.isLive && surfaceFocused) { seekBy(SEEK_STEP_MS); true }
KEYCODE_DPAD_RIGHT -> if (!request.isLive && surfaceFocused) { seekBy(-SEEK_STEP_MS); true }
"""
        self.assertFalse(MODULE.player_surface_seek_contract(text))

    def test_legacy_ltr_seekbar_contract_fails(self) -> None:
        text = """
KEYCODE_DPAD_LEFT -> { previewMs = (previewMs - SEEK_STEP_MS).coerceAtLeast(0L); true }
KEYCODE_DPAD_RIGHT -> { previewMs = (previewMs + SEEK_STEP_MS).coerceAtMost(durationMs); true }
"""
        self.assertFalse(MODULE.player_seekbar_contract(text))

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
