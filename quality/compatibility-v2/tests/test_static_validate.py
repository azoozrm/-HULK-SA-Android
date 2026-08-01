from __future__ import annotations

import hashlib
import importlib.util
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).parents[1] / "static_validate.py"
SPEC = importlib.util.spec_from_file_location("compatibility_v2_static", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class StaticValidationTest(unittest.TestCase):
    def fixture(self, root: Path, marker: str = "") -> str:
        (root / "app/src/main/res/drawable-nodpi").mkdir(parents=True)
        (root / "app/src/main/java/sa/hulksa/player/ui/components").mkdir(parents=True)
        (root / ".github/workflows").mkdir(parents=True)
        logo = b"approved-test-logo"
        logo_sha = hashlib.sha256(logo).hexdigest()
        (root / "app/src/main/res/drawable-nodpi/hulk_sa_logo.webp").write_bytes(logo)
        (root / "app/build.gradle.kts").write_text(
            'namespace = "sa.hulksa.player"\napplicationId = "sa.hulksa.player"\n'
            'versionCode = 64\nversionName = "0.9.3.20"\n'
            'val productionPortalUrl = "http://3162356.xyz:8080"\n'
            'arm64-v8a armeabi-v7a x86_64\n', encoding="utf-8")
        (root / "app/src/main/AndroidManifest.xml").write_text(
            '<?xml version="1.0"?><manifest xmlns:android="http://schemas.android.com/apk/res/android">'
            '<uses-feature android:name="android.software.leanback" android:required="false"/>'
            '<uses-feature android:name="android.hardware.touchscreen" android:required="false"/>'
            '<application android:supportsRtl="true" android:banner="@drawable/tv_banner">'
            '<activity><intent-filter><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity>'
            '<activity><intent-filter><category android:name="android.intent.category.LEANBACK_LAUNCHER"/></intent-filter></activity>'
            '</application></manifest>', encoding="utf-8")
        (root / "app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt").write_text(
            f"ContentScale.Fit\n{marker}\n", encoding="utf-8")
        return logo_sha

    def test_valid_repository_contract_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            logo_sha = self.fixture(root)
            checks = MODULE.validate_repo(root, logo_sha)
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
            result = next(check for check in MODULE.validate_repo(root, expected) if check.id == "approved-logo-sha256")
            self.assertEqual("FAIL", result.status)


if __name__ == "__main__":
    unittest.main()
