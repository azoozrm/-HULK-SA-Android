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
SPEC = importlib.util.spec_from_file_location(
    "compatibility_v2_static",
    MODULE_PATH,
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class StaticValidationTest(unittest.TestCase):
    @staticmethod
    def png_bytes(width: int, height: int) -> bytes:
        def chunk(kind: bytes, data: bytes) -> bytes:
            checksum = zlib.crc32(kind + data) & 0xFFFFFFFF
            return (
                struct.pack(">I", len(data))
                + kind
                + data
                + struct.pack(">I", checksum)
            )

        rows = b"".join(
            b"\x00" + bytes(width * 4)
            for _ in range(height)
        )
        header = struct.pack(
            ">IIBBBBB",
            width,
            height,
            8,
            6,
            0,
            0,
            0,
        )

        return (
            b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(rows))
            + chunk(b"IEND", b"")
        )

    @staticmethod
    def adaptive_xml(monochrome: bool) -> str:
        monochrome_node = (
            '\n    <monochrome '
            'android:drawable="@drawable/hulk_sa_adaptive_monochrome" />'
            if monochrome
            else ""
        )

        return (
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon '
            'xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background '
            'android:drawable="@drawable/hulk_sa_adaptive_background" />\n'
            '    <foreground '
            'android:drawable="@drawable/hulk_sa_adaptive_foreground" />'
            f"{monochrome_node}\n"
            "</adaptive-icon>\n"
        )

    def fixture(
        self,
        root: Path,
        marker: str = "",
        player: str | None = None,
    ) -> str:
        (root / "app/src/main/res/drawable-nodpi").mkdir(
            parents=True,
            exist_ok=True,
        )
        (root / "app/src/main/java/sa/hulksa/player/ui/components").mkdir(
            parents=True,
            exist_ok=True,
        )
        (root / "app/src/main/java/sa/hulksa/player/tv").mkdir(
            parents=True,
            exist_ok=True,
        )
        (root / "app/src/main/java/sa/hulksa/player/data").mkdir(
            parents=True,
            exist_ok=True,
        )
        (root / "app/src/main/java/sa/hulksa/player/model").mkdir(
            parents=True,
            exist_ok=True,
        )
        (root / "app/src/main/java/sa/hulksa/player/ui/screens").mkdir(
            parents=True,
            exist_ok=True,
        )
        (root / ".github/workflows").mkdir(
            parents=True,
            exist_ok=True,
        )

        # Canonical nodpi package.
        for relative, dimensions in MODULE.CANONICAL_NODPI_DIMENSIONS.items():
            asset = root / relative
            asset.parent.mkdir(parents=True, exist_ok=True)
            asset.write_bytes(
                self.png_bytes(*dimensions)
            )

        logo_path = (
            root
            / "app/src/main/res/drawable-nodpi/hulk_sa_logo.png"
        )
        logo_sha = hashlib.sha256(
            logo_path.read_bytes()
        ).hexdigest()

        # Density-specific launcher / round / TV / banner / notification package.
        for relative, dimensions in MODULE.ICON_ASSET_DIMENSIONS.items():
            asset = root / relative
            asset.parent.mkdir(parents=True, exist_ok=True)
            asset.write_bytes(
                self.png_bytes(*dimensions)
            )

        # Adaptive launcher XML ownership.
        v26 = root / "app/src/main/res/mipmap-anydpi-v26"
        v33 = root / "app/src/main/res/mipmap-anydpi-v33"
        v26.mkdir(parents=True, exist_ok=True)
        v33.mkdir(parents=True, exist_ok=True)

        for name in (
            "ic_launcher.xml",
            "ic_launcher_round.xml",
            "ic_launcher_tv.xml",
        ):
            (v26 / name).write_text(
                self.adaptive_xml(monochrome=False),
                encoding="utf-8",
            )

        for name in (
            "ic_launcher.xml",
            "ic_launcher_round.xml",
        ):
            (v33 / name).write_text(
                self.adaptive_xml(monochrome=True),
                encoding="utf-8",
            )

        (root / "app/build.gradle.kts").write_text(
            'namespace = "sa.hulksa.player"\n'
            'applicationId = "sa.hulksa.player"\n'
            'versionCode = 64\n'
            'versionName = "0.9.3.20"\n'
            'val resellerApiUrl = "https://hulksa.com"\n'
            'val verifyProductionRuntimeConfig = '
            'tasks.register("verifyProductionRuntimeConfig")\n'
            'buildConfigField("String", "RESELLER_API_URL", resellerApiUrl)\n'
            'arm64-v8a armeabi-v7a x86_64\n',
            encoding="utf-8",
        )

        (root / "app/src/main/AndroidManifest.xml").write_text(
            '<?xml version="1.0"?>'
            '<manifest '
            'xmlns:android="http://schemas.android.com/apk/res/android">'
            '<uses-feature '
            'android:name="android.software.leanback" '
            'android:required="false"/>'
            '<uses-feature '
            'android:name="android.hardware.touchscreen" '
            'android:required="false"/>'
            '<application '
            'android:supportsRtl="true" '
            'android:icon="@mipmap/ic_launcher" '
            'android:banner="@mipmap/tv_banner">'
            '<activity android:name=".MainActivity">'
            '<intent-filter>'
            '<category '
            'android:name="android.intent.category.LAUNCHER"/>'
            '</intent-filter>'
            '</activity>'
            '<activity '
            'android:name=".TvMainActivity" '
            'android:icon="@mipmap/ic_launcher_tv" '
            'android:banner="@mipmap/tv_banner">'
            '<intent-filter>'
            '<category '
            'android:name="android.intent.category.LEANBACK_LAUNCHER"/>'
            '</intent-filter>'
            '</activity>'
            '</application>'
            '</manifest>',
            encoding="utf-8",
        )

        (
            root
            / "app/src/main/java/sa/hulksa/player/ui/components/"
            "HulkComponents.kt"
        ).write_text(
            "ContentScale.Fit\n"
            "R.drawable.hulk_sa_logo\n"
            "R.drawable.hulk_sa_mark\n"
            "R.drawable.hulk_sa_mark_monochrome\n"
            f"{marker}\n",
            encoding="utf-8",
        )

        (
            root
            / "app/src/main/java/sa/hulksa/player/tv/"
            "TvHomeChannelManager.kt"
        ).write_text(
            "R.drawable.hulk_sa_tv_channel_logo\n"
            "R.drawable.hulk_sa_content_fallback_wide\n",
            encoding="utf-8",
        )

        (
            root
            / "app/src/main/java/sa/hulksa/player/ui/screens/"
            "LoginScreen.kt"
        ).write_text(
            'label = "كود الدخول"\n'
            'label = "اسم المستخدم"\n'
            'label = "كلمة المرور"\n',
            encoding="utf-8",
        )

        (
            root
            / "app/src/main/java/sa/hulksa/player/data/"
            "PortalResolver.kt"
        ).write_text(
            "BuildConfig.RESELLER_API_URL\n"
            'addPathSegments("api/reseller/resolve")\n'
            "PortalConfig.Source.ACCESS_CODE\n",
            encoding="utf-8",
        )

        (
            root
            / "app/src/main/java/sa/hulksa/player/model/"
            "Models.kt"
        ).write_text(
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

        (
            root
            / "app/src/main/java/sa/hulksa/player/ui/screens/"
            "PlayerScreen.kt"
        ).write_text(
            valid_player if player is None else player,
            encoding="utf-8",
        )

        # Canonical build workflow ownership.
        (root / ".github/workflows/build-apk.yml").write_text(
            """
name: Build HULK SA APK
jobs:
  build:
    steps:
      - name: Restore canonical HULK SA branding
        shell: bash
        run: |
          BRAND_RES_DIRS=(
            drawable-mdpi
            drawable-hdpi
            drawable-xhdpi
            drawable-xxhdpi
            drawable-xxxhdpi
            drawable-nodpi
            mipmap-anydpi-v26
            mipmap-anydpi-v33
            mipmap-mdpi
            mipmap-hdpi
            mipmap-xhdpi
            mipmap-xxhdpi
            mipmap-xxxhdpi
          )
          for dir in "${BRAND_RES_DIRS[@]}"; do
            cp -R "app/src/main/res/$dir/." "project/app/src/main/res/$dir/"
          done
          cp app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt project/app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt
          cp app/src/main/java/sa/hulksa/player/tv/TvHomeChannelManager.kt project/app/src/main/java/sa/hulksa/player/tv/TvHomeChannelManager.kt
          cmp -s app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt project/app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt
          cmp -s app/src/main/java/sa/hulksa/player/tv/TvHomeChannelManager.kt project/app/src/main/java/sa/hulksa/player/tv/TvHomeChannelManager.kt
""",
            encoding="utf-8",
        )

        return logo_sha

    def validate_fixture(
        self,
        root: Path,
        logo_sha: str,
    ):
        original = dict(MODULE.APPROVED_BRAND_ASSETS)

        for relative in MODULE.APPROVED_BRAND_ASSETS:
            if not relative.endswith("hulk_sa_logo.png"):
                MODULE.APPROVED_BRAND_ASSETS[relative] = MODULE.sha256(
                    root / relative
                )

        try:
            return MODULE.validate_repo(root, logo_sha)
        finally:
            MODULE.APPROVED_BRAND_ASSETS.clear()
            MODULE.APPROVED_BRAND_ASSETS.update(original)

    def test_valid_repository_contract_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            logo_sha = self.fixture(root)

            checks = self.validate_fixture(
                root,
                logo_sha,
            )

            self.assertFalse(
                [
                    check
                    for check in checks
                    if check.status == "FAIL"
                ]
            )

    def test_production_marker_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            logo_sha = self.fixture(
                root,
                marker="qaTvPageContent",
            )

            checks = self.validate_fixture(
                root,
                logo_sha,
            )

            result = next(
                check
                for check in checks
                if check.id == "production-test-hooks-absent"
            )

            self.assertEqual(
                "FAIL",
                result.status,
            )

    def test_missing_tv_density_asset_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            logo_sha = self.fixture(root)

            (
                root
                / "app/src/main/res/mipmap-xxxhdpi/"
                "ic_launcher_tv.png"
            ).unlink()

            result = next(
                check
                for check in MODULE.validate_repo(
                    root,
                    logo_sha,
                )
                if check.id == "android-icon-density-matrix"
            )

            self.assertEqual(
                "FAIL",
                result.status,
            )

    def test_wrong_canonical_nodpi_dimension_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            logo_sha = self.fixture(root)

            target = (
                root
                / "app/src/main/res/drawable-nodpi/"
                "hulk_sa_tv_channel_logo.png"
            )

            target.write_bytes(
                self.png_bytes(
                    256,
                    256,
                )
            )

            result = next(
                check
                for check in MODULE.validate_repo(
                    root,
                    logo_sha,
                )
                if check.id == "canonical-brand-nodpi-dimensions"
            )

            self.assertEqual(
                "FAIL",
                result.status,
            )

    def test_tv_launcher_must_be_distinct_from_phone_launcher(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            logo_sha = self.fixture(root)

            manifest = root / "app/src/main/AndroidManifest.xml"

            manifest.write_text(
                manifest.read_text(
                    encoding="utf-8",
                ).replace(
                    "@mipmap/ic_launcher_tv",
                    "@mipmap/ic_launcher",
                ),
                encoding="utf-8",
            )

            result = next(
                check
                for check in MODULE.validate_repo(
                    root,
                    logo_sha,
                )
                if check.id == "manifest-tv-banner"
            )

            self.assertEqual(
                "FAIL",
                result.status,
            )

    def test_logo_byte_change_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            expected = self.fixture(root)

            (
                root
                / "app/src/main/res/drawable-nodpi/"
                "hulk_sa_logo.png"
            ).write_bytes(
                b"changed"
            )

            result = next(
                check
                for check in MODULE.validate_repo(
                    root,
                    expected,
                )
                if check.id == "approved-logo-sha256"
            )

            self.assertEqual(
                "FAIL",
                result.status,
            )

    def test_adaptive_v33_monochrome_is_required(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            logo_sha = self.fixture(root)

            target = (
                root
                / "app/src/main/res/mipmap-anydpi-v33/"
                "ic_launcher.xml"
            )

            target.write_text(
                self.adaptive_xml(
                    monochrome=False,
                ),
                encoding="utf-8",
            )

            result = next(
                check
                for check in MODULE.validate_repo(
                    root,
                    logo_sha,
                )
                if check.id == "adaptive-icon-resource-contract"
            )

            self.assertEqual(
                "FAIL",
                result.status,
            )

    def test_obsolete_launcher_resource_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            logo_sha = self.fixture(root)

            obsolete = (
                root
                / "app/src/main/res/drawable/"
                "ic_launcher_foreground.xml"
            )

            obsolete.parent.mkdir(
                parents=True,
                exist_ok=True,
            )

            obsolete.write_text(
                "<vector />",
                encoding="utf-8",
            )

            result = next(
                check
                for check in MODULE.validate_repo(
                    root,
                    logo_sha,
                )
                if check.id == "obsolete-brand-assets-absent"
            )

            self.assertEqual(
                "FAIL",
                result.status,
            )

    def test_hulk_components_cannot_use_legacy_launcher_artwork(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            logo_sha = self.fixture(root)

            components = (
                root
                / "app/src/main/java/sa/hulksa/player/ui/components/"
                "HulkComponents.kt"
            )

            components.write_text(
                components.read_text(
                    encoding="utf-8",
                )
                + "\nR.drawable.ic_launcher_foreground\n",
                encoding="utf-8",
            )

            result = next(
                check
                for check in MODULE.validate_repo(
                    root,
                    logo_sha,
                )
                if check.id == "brand-runtime-component-ownership"
            )

            self.assertEqual(
                "FAIL",
                result.status,
            )

    def test_tv_home_cannot_use_launcher_as_provider_artwork(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            logo_sha = self.fixture(root)

            tv_home = (
                root
                / "app/src/main/java/sa/hulksa/player/tv/"
                "TvHomeChannelManager.kt"
            )

            tv_home.write_text(
                tv_home.read_text(
                    encoding="utf-8",
                )
                + "\nR.mipmap.ic_launcher_tv\n",
                encoding="utf-8",
            )

            result = next(
                check
                for check in MODULE.validate_repo(
                    root,
                    logo_sha,
                )
                if check.id == "tv-home-brand-runtime-ownership"
            )

            self.assertEqual(
                "FAIL",
                result.status,
            )

    def test_legacy_build_brand_download_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            logo_sha = self.fixture(root)

            workflow = (
                root
                / ".github/workflows/build-apk.yml"
            )

            workflow.write_text(
                workflow.read_text(
                    encoding="utf-8",
                )
                + (
                    "\n"
                    "curl https://hulksa.com/assets/"
                    "hulk-official-logo.webp\n"
                ),
                encoding="utf-8",
            )

            result = next(
                check
                for check in MODULE.validate_repo(
                    root,
                    logo_sha,
                )
                if check.id == "build-brand-contract"
            )

            self.assertEqual(
                "FAIL",
                result.status,
            )

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

        self.assertTrue(
            MODULE.player_surface_seek_contract(text)
        )
        self.assertTrue(
            MODULE.player_seekbar_contract(text)
        )

    def test_legacy_ltr_player_seek_contract_fails(self) -> None:
        text = """
val tvRemoteInput = adaptiveUi.isTelevision || adaptiveUi.inputMode == HulkInputMode.REMOTE
KEYCODE_DPAD_LEFT -> if (!request.isLive && surfaceFocused) {
    seekBy(-SEEK_STEP_MS); true
}
KEYCODE_DPAD_RIGHT -> if (!request.isLive && surfaceFocused) {
    seekBy(SEEK_STEP_MS); true
}
"""

        self.assertFalse(
            MODULE.player_surface_seek_contract(text)
        )

    def test_unscoped_rtl_player_seek_contract_fails(self) -> None:
        text = """
KEYCODE_DPAD_LEFT -> if (!request.isLive && surfaceFocused) {
    seekBy(SEEK_STEP_MS); true
}
KEYCODE_DPAD_RIGHT -> if (!request.isLive && surfaceFocused) {
    seekBy(-SEEK_STEP_MS); true
}
"""

        self.assertFalse(
            MODULE.player_surface_seek_contract(text)
        )

    def test_legacy_ltr_seekbar_contract_fails(self) -> None:
        text = """
KEYCODE_DPAD_LEFT -> {
    previewMs = (previewMs - SEEK_STEP_MS).coerceAtLeast(0L)
    true
}
KEYCODE_DPAD_RIGHT -> {
    previewMs = (previewMs + SEEK_STEP_MS).coerceAtMost(durationMs)
    true
}
"""

        self.assertFalse(
            MODULE.player_seekbar_contract(text)
        )

    def test_focus_retry_loop_is_reported(self) -> None:
        text = """
repeat(4) {
    delay(100L)
    runCatching { resumeFocus.requestFocus() }
}
"""

        findings = MODULE.player_focus_race_findings(text)

        self.assertIn(
            "repeated requestFocus retry loop",
            findings,
        )

        self.assertIn(
            "time-delayed requestFocus",
            findings,
        )

    def test_lifecycle_owned_focus_without_delay_passes(self) -> None:
        text = (
            "LaunchedEffect(owner) { "
            "owner.requestFocus() "
            "}"
        )

        self.assertEqual(
            [],
            MODULE.player_focus_race_findings(text),
        )


if __name__ == "__main__":
    unittest.main()