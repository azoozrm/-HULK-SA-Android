#!/usr/bin/env python3
"""Fail-closed static validation for HULK SA Compatibility Lab V2."""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import re
import struct
import sys
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from xml.etree import ElementTree

STATUSES = ("PASS", "FAIL", "BLOCKED", "SKIPPED")
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

DEFAULT_LOGO_SHA256 = "bf5c3927eb765ac715c986a792a96368c08b912f69ce76212c09cb965cc79b3d"

APPROVED_BRAND_ASSETS = {
    "app/src/main/res/drawable-nodpi/hulk_sa_logo.png":
        DEFAULT_LOGO_SHA256,
    "app/src/main/res/mipmap-xhdpi/tv_banner.png":
        "d8d50858eb0456756142e30df12eb8ee7381ba2812e5ac780dded93d0eb8a37f",
    "app/src/main/res/mipmap-xhdpi/ic_launcher_tv.png":
        "dc824e126a4633e741e10bce62d1e4c8e2e2e171ce9a57e153ad8219d8ff19b8",
}

ICON_DENSITY_SPECS = {
    "mdpi": {
        "launcher": 48,
        "tv_launcher": 80,
        "banner": (160, 90),
        "notification": 24,
    },
    "hdpi": {
        "launcher": 72,
        "tv_launcher": 120,
        "banner": (240, 135),
        "notification": 36,
    },
    "xhdpi": {
        "launcher": 96,
        "tv_launcher": 160,
        "banner": (320, 180),
        "notification": 48,
    },
    "xxhdpi": {
        "launcher": 144,
        "tv_launcher": 240,
        "banner": (480, 270),
        "notification": 72,
    },
    "xxxhdpi": {
        "launcher": 192,
        "tv_launcher": 320,
        "banner": (640, 360),
        "notification": 96,
    },
}

ICON_ASSET_DIMENSIONS: dict[str, tuple[int, int]] = {}

for density, sizes in ICON_DENSITY_SPECS.items():
    launcher = int(sizes["launcher"])
    tv_launcher = int(sizes["tv_launcher"])
    notification = int(sizes["notification"])
    banner = sizes["banner"]
    assert isinstance(banner, tuple)

    ICON_ASSET_DIMENSIONS.update(
        {
            f"app/src/main/res/mipmap-{density}/ic_launcher.png":
                (launcher, launcher),
            f"app/src/main/res/mipmap-{density}/ic_launcher_round.png":
                (launcher, launcher),
            f"app/src/main/res/mipmap-{density}/ic_launcher_tv.png":
                (tv_launcher, tv_launcher),
            f"app/src/main/res/mipmap-{density}/tv_banner.png":
                banner,
            f"app/src/main/res/drawable-{density}/ic_stat_hulk.png":
                (notification, notification),
        }
    )

CANONICAL_NODPI_DIMENSIONS = {
    "app/src/main/res/drawable-nodpi/hulk_sa_adaptive_background.png":
        (432, 432),
    "app/src/main/res/drawable-nodpi/hulk_sa_adaptive_foreground.png":
        (432, 432),
    "app/src/main/res/drawable-nodpi/hulk_sa_adaptive_monochrome.png":
        (432, 432),
    "app/src/main/res/drawable-nodpi/hulk_sa_logo.png":
        (1024, 1024),
    "app/src/main/res/drawable-nodpi/hulk_sa_mark.png":
        (768, 1024),
    "app/src/main/res/drawable-nodpi/hulk_sa_mark_monochrome.png":
        (768, 1024),
    "app/src/main/res/drawable-nodpi/hulk_sa_tv_channel_logo.png":
        (512, 512),
    "app/src/main/res/drawable-nodpi/hulk_sa_content_fallback_wide.png":
        (1280, 720),
}

OBSOLETE_BRAND_PATHS = (
    "app/src/main/res/drawable/ic_launcher_background.xml",
    "app/src/main/res/drawable/ic_launcher_foreground.xml",
    "app/src/main/res/drawable/ic_launcher_tv_foreground.xml",
    "app/src/main/res/drawable-v33/ic_launcher_monochrome.xml",
    "app/src/main/res/drawable/ic_stat_hulk.xml",
    ".payload/assets/logo.part-00",
)

FORBIDDEN_PRODUCTION_MARKERS = (
    "qaMarker",
    "qaTvPageContent",
    "qa-tv-",
    "compatibilityTestOverlay",
)

LEGACY_PATHS = (
    "qa/compatibility",
    "qa/quality",
    "qa/canonical",
    ".github/workflows/compatibility-lab.yml",
    ".github/workflows/quality-lab-self-validation.yml",
    ".github/workflows/quality-pr.yml",
    ".github/workflows/quality-ui.yml",
    ".github/workflows/quality-nightly.yml",
    ".github/workflows/quality-release.yml",
    ".github/workflows/targeted-compatibility-retest.yml",
    ".github/workflows/generated-source-snapshot.yml",
)


@dataclass(frozen=True)
class Check:
    id: str
    status: str
    message: str
    evidence: list[str]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def png_dimensions(path: Path) -> tuple[int, int] | None:
    """Read PNG dimensions without adding an image-library dependency to CI."""
    try:
        with path.open("rb") as handle:
            if handle.read(8) != b"\x89PNG\r\n\x1a\n":
                return None

            length = struct.unpack(">I", handle.read(4))[0]

            if handle.read(4) != b"IHDR" or length < 8:
                return None

            return struct.unpack(">II", handle.read(8))
    except (OSError, struct.error):
        return None


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def adaptive_icon_contract(
    path: Path,
    require_monochrome: bool,
) -> tuple[bool, str]:
    if not path.is_file():
        return False, f"missing: {path}"

    try:
        root = ElementTree.parse(path).getroot()
    except ElementTree.ParseError as exc:
        return False, f"invalid XML: {path}: {exc}"

    refs: dict[str, str | None] = {}

    for child in root:
        tag = child.tag.rsplit("}", 1)[-1]
        refs[tag] = child.get(ANDROID_NS + "drawable")

    expected = {
        "background": "@drawable/hulk_sa_adaptive_background",
        "foreground": "@drawable/hulk_sa_adaptive_foreground",
    }

    if require_monochrome:
        expected["monochrome"] = "@drawable/hulk_sa_adaptive_monochrome"

    for key, value in expected.items():
        if refs.get(key) != value:
            return (
                False,
                f"{path}: {key} expected {value}, got {refs.get(key)}",
            )

    if not require_monochrome and "monochrome" in refs:
        return False, f"{path}: unexpected monochrome layer"

    return True, str(path)


def player_surface_seek_contract(text: str) -> bool:
    remote_gate = re.search(
        r"val\s+tvRemoteInput\s*=\s*adaptiveUi\.isTelevision\s*\|\|\s*"
        r"adaptiveUi\.inputMode\s*==\s*HulkInputMode\.REMOTE",
        text,
    )
    left = re.search(
        r"KEYCODE_DPAD_LEFT\s*->\s*if\s*\([^)]*surfaceFocused[^)]*\)\s*\{"
        r"(?:(?!KEYCODE_DPAD_RIGHT).)*?"
        r"seekBy\(\s*if\s*\(\s*tvRemoteInput\s*\)\s*"
        r"(?:SEEK_STEP_MS|seekStepMs)\s*else\s*"
        r"-(?:SEEK_STEP_MS|seekStepMs)\s*\)",
        text,
        re.DOTALL,
    )
    right = re.search(
        r"KEYCODE_DPAD_RIGHT\s*->\s*if\s*\([^)]*surfaceFocused[^)]*\)\s*\{"
        r"(?:(?!KEYCODE_MEDIA_REWIND).)*?"
        r"seekBy\(\s*if\s*\(\s*tvRemoteInput\s*\)\s*"
        r"-(?:SEEK_STEP_MS|seekStepMs)\s*else\s*"
        r"(?:SEEK_STEP_MS|seekStepMs)\s*\)",
        text,
        re.DOTALL,
    )
    return remote_gate is not None and left is not None and right is not None


def player_seekbar_contract(text: str) -> bool:
    left = re.search(
        r"KEYCODE_DPAD_LEFT\s*->\s*\{\s*previewMs\s*=\s*"
        r"if\s*\(\s*tvRemoteInput\s*\)\s*\{"
        r"\s*\(previewMs\s*\+\s*(?:SEEK_STEP_MS|seekStepMs)\)"
        r"\.coerceAtMost\(durationMs\)"
        r"\s*\}\s*else\s*\{\s*"
        r"\(previewMs\s*-\s*(?:SEEK_STEP_MS|seekStepMs)\)"
        r"\.coerceAtLeast\(0L\)",
        text,
        re.DOTALL,
    )
    right = re.search(
        r"KEYCODE_DPAD_RIGHT\s*->\s*\{\s*previewMs\s*=\s*"
        r"if\s*\(\s*tvRemoteInput\s*\)\s*\{"
        r"\s*\(previewMs\s*-\s*(?:SEEK_STEP_MS|seekStepMs)\)"
        r"\.coerceAtLeast\(0L\)"
        r"\s*\}\s*else\s*\{\s*"
        r"\(previewMs\s*\+\s*(?:SEEK_STEP_MS|seekStepMs)\)"
        r"\.coerceAtMost\(durationMs\)",
        text,
        re.DOTALL,
    )
    return left is not None and right is not None


def player_focus_race_findings(text: str) -> list[str]:
    findings: list[str] = []

    repeated = re.compile(
        r"repeat\s*\(\s*\d+\s*\)\s*\{"
        r"(?:(?!\n\s*\}).)*?requestFocus\(\)",
        re.DOTALL,
    )
    delayed = re.compile(
        r"delay\(\s*(?:\d+L?|if\s*\([^)]*\)[^\n]+)\s*\)\s*\n\s*"
        r"runCatching\s*\{\s*[A-Za-z0-9_]+\.requestFocus\(\)",
        re.DOTALL,
    )

    if repeated.search(text):
        findings.append("repeated requestFocus retry loop")

    if delayed.search(text):
        findings.append("time-delayed requestFocus")

    return findings


def validate_repo(
    repo_root: Path,
    expected_logo_sha256: str = DEFAULT_LOGO_SHA256,
) -> list[Check]:
    checks: list[Check] = []

    def add(
        check_id: str,
        condition: bool,
        success: str,
        failure: str,
        evidence: list[str],
    ) -> None:
        checks.append(
            Check(
                check_id,
                "PASS" if condition else "FAIL",
                success if condition else failure,
                evidence,
            )
        )

    build_file = repo_root / "app/build.gradle.kts"

    if not build_file.is_file():
        checks.append(
            Check(
                "android-build-file",
                "FAIL",
                "app/build.gradle.kts is missing",
                [str(build_file)],
            )
        )
        return checks

    build_text = _read(build_file)

    add(
        "package-identity",
        'applicationId = "sa.hulksa.player"' in build_text,
        "Application ID is sa.hulksa.player",
        "Application ID changed or is not explicit",
        [str(build_file)],
    )

    add(
        "namespace-identity",
        'namespace = "sa.hulksa.player"' in build_text,
        "Namespace is sa.hulksa.player",
        "Namespace changed or is not explicit",
        [str(build_file)],
    )

    add(
        "version-name",
        'versionName = "0.9.3.20"' in build_text,
        "versionName is 0.9.3.20",
        "versionName is not 0.9.3.20",
        [str(build_file)],
    )

    add(
        "version-code",
        re.search(r"\bversionCode\s*=\s*64\b", build_text) is not None,
        "versionCode is 64",
        "versionCode is not 64",
        [str(build_file)],
    )

    add(
        "reseller-api-runtime-config",
        all(
            marker in build_text
            for marker in (
                'val resellerApiUrl = "https://hulksa.com"',
                'buildConfigField("String", "RESELLER_API_URL"',
                "verifyProductionRuntimeConfig",
            )
        )
        and "http://3162356.xyz:8080" not in build_text,
        "Production uses only the configurable HTTPS reseller API",
        "Reseller API BuildConfig contract is missing or the legacy IPTV host returned",
        [str(build_file)],
    )

    add(
        "abi-policy",
        all(
            abi in build_text
            for abi in (
                "arm64-v8a",
                "armeabi-v7a",
                "x86_64",
            )
        ),
        "Qualified ABI set is present",
        "One or more qualified ABIs are missing",
        [str(build_file)],
    )

    login_file = (
        repo_root
        / "app/src/main/java/sa/hulksa/player/ui/screens/LoginScreen.kt"
    )
    resolver_file = (
        repo_root
        / "app/src/main/java/sa/hulksa/player/data/PortalResolver.kt"
    )
    models_file = (
        repo_root
        / "app/src/main/java/sa/hulksa/player/model/Models.kt"
    )

    login_text = _read(login_file) if login_file.is_file() else ""
    resolver_text = _read(resolver_file) if resolver_file.is_file() else ""
    models_text = _read(models_file) if models_file.is_file() else ""

    access_code_position = login_text.find('label = "كود الدخول"')
    username_position = login_text.find('label = "اسم المستخدم"')
    password_position = login_text.find('label = "كلمة المرور"')

    add(
        "reseller-access-login-order",
        -1 < access_code_position < username_position < password_position,
        "Login fields are ordered access code, username, then password",
        "Login screen does not preserve the required reseller access field order",
        [str(login_file)],
    )

    add(
        "reseller-access-resolution",
        all(
            marker in resolver_text
            for marker in (
                "BuildConfig.RESELLER_API_URL",
                'addPathSegments("api/reseller/resolve")',
                "PortalConfig.Source.ACCESS_CODE",
            )
        )
        and "val accessCode: String" in models_text,
        "Android resolves the access code through the HULK API before IPTV login",
        "Android reseller resolution contract is incomplete",
        [str(resolver_file), str(models_file)],
    )

    add(
        "legacy-iptv-host-absent",
        "http://3162356.xyz:8080"
        not in (build_text + resolver_text + models_text + login_text),
        "Legacy IPTV host is absent from Android production source",
        "Legacy IPTV host is still present in Android production source",
        [str(build_file), str(resolver_file)],
    )

    manifest_file = repo_root / "app/src/main/AndroidManifest.xml"

    if manifest_file.is_file():
        try:
            manifest_root = ElementTree.parse(manifest_file).getroot()

            features = {
                node.get(ANDROID_NS + "name"): node
                for node in manifest_root.findall("uses-feature")
            }

            leanback = features.get("android.software.leanback")
            touchscreen = features.get("android.hardware.touchscreen")
            app = manifest_root.find("application")
            activities = manifest_root.findall("application/activity")

            tv_activity = next(
                (
                    activity
                    for activity in activities
                    if activity.get(ANDROID_NS + "name") == ".TvMainActivity"
                ),
                None,
            )

            launcher_categories = {
                category.get(ANDROID_NS + "name")
                for activity in activities
                for intent_filter in activity.findall("intent-filter")
                for category in intent_filter.findall("category")
            }

            add(
                "manifest-leanback-optional",
                leanback is not None
                and leanback.get(ANDROID_NS + "required") == "false",
                "Leanback is optional",
                "Leanback is missing or required",
                [str(manifest_file)],
            )

            add(
                "manifest-touch-optional",
                touchscreen is not None
                and touchscreen.get(ANDROID_NS + "required") == "false",
                "Touchscreen is optional",
                "Touchscreen is missing or required",
                [str(manifest_file)],
            )

            add(
                "manifest-launchers",
                {
                    "android.intent.category.LAUNCHER",
                    "android.intent.category.LEANBACK_LAUNCHER",
                }.issubset(launcher_categories),
                "Phone and TV launchers are declared",
                "Phone or TV launcher category is missing",
                [str(manifest_file)],
            )

            add(
                "manifest-rtl",
                app is not None
                and app.get(ANDROID_NS + "supportsRtl") == "true",
                "RTL support is enabled",
                "RTL support is not enabled",
                [str(manifest_file)],
            )

            add(
                "manifest-tv-banner",
                app is not None
                and app.get(ANDROID_NS + "icon") == "@mipmap/ic_launcher"
                and app.get(ANDROID_NS + "banner") == "@mipmap/tv_banner"
                and tv_activity is not None
                and tv_activity.get(ANDROID_NS + "icon")
                == "@mipmap/ic_launcher_tv"
                and tv_activity.get(ANDROID_NS + "banner")
                == "@mipmap/tv_banner",
                "Phone and TV use distinct launcher resources and the density-aware TV banner",
                "Phone/TV launcher or TV banner resources are missing, shared, or not density-aware",
                [str(manifest_file)],
            )

        except ElementTree.ParseError as exc:
            checks.append(
                Check(
                    "manifest-parse",
                    "FAIL",
                    f"Manifest XML is invalid: {exc}",
                    [str(manifest_file)],
                )
            )
    else:
        checks.append(
            Check(
                "manifest-present",
                "FAIL",
                "AndroidManifest.xml is missing",
                [str(manifest_file)],
            )
        )

    for relative, approved_hash in APPROVED_BRAND_ASSETS.items():
        expected_hash = (
            expected_logo_sha256
            if relative.endswith("hulk_sa_logo.png")
            else approved_hash
        )

        asset = repo_root / relative

        if relative.endswith("hulk_sa_logo.png"):
            check_id = "approved-logo-sha256"
        elif relative.endswith("tv_banner.png"):
            check_id = "approved-banner-sha256"
        else:
            check_id = "approved-tv-launcher-sha256"

        if asset.is_file():
            actual_hash = sha256(asset)

            add(
                check_id,
                actual_hash == expected_hash,
                f"Approved brand asset bytes are unchanged: {relative}",
                f"Approved brand asset hash changed: {relative} = {actual_hash}",
                [str(asset)],
            )
        else:
            checks.append(
                Check(
                    check_id,
                    "FAIL",
                    f"Approved brand asset is missing: {relative}",
                    [str(asset)],
                )
            )

    icon_dimension_failures: list[str] = []

    for relative, expected_dimensions in ICON_ASSET_DIMENSIONS.items():
        asset = repo_root / relative
        actual_dimensions = (
            png_dimensions(asset)
            if asset.is_file()
            else None
        )

        if actual_dimensions != expected_dimensions:
            icon_dimension_failures.append(
                f"{relative}: expected {expected_dimensions}, "
                f"got {actual_dimensions}"
            )

    add(
        "android-icon-density-matrix",
        not icon_dimension_failures,
        (
            "Phone, round, TV, banner, and notification assets cover "
            "every required density at exact dimensions"
        ),
        (
            "Android icon density matrix is incomplete or dimensionally "
            "invalid: "
            + "; ".join(icon_dimension_failures)
        ),
        icon_dimension_failures or list(ICON_ASSET_DIMENSIONS),
    )

    nodpi_dimension_failures: list[str] = []

    for relative, expected_dimensions in CANONICAL_NODPI_DIMENSIONS.items():
        asset = repo_root / relative
        actual_dimensions = (
            png_dimensions(asset)
            if asset.is_file()
            else None
        )

        if actual_dimensions != expected_dimensions:
            nodpi_dimension_failures.append(
                f"{relative}: expected {expected_dimensions}, "
                f"got {actual_dimensions}"
            )

    add(
        "canonical-brand-nodpi-dimensions",
        not nodpi_dimension_failures,
        "Canonical nodpi HULK SA brand assets have exact approved dimensions",
        (
            "Canonical nodpi HULK SA brand dimensions are invalid: "
            + "; ".join(nodpi_dimension_failures)
        ),
        nodpi_dimension_failures or list(CANONICAL_NODPI_DIMENSIONS),
    )

    adaptive_specs = (
        (
            "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
            False,
        ),
        (
            "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
            False,
        ),
        (
            "app/src/main/res/mipmap-anydpi-v26/ic_launcher_tv.xml",
            False,
        ),
        (
            "app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml",
            True,
        ),
        (
            "app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml",
            True,
        ),
    )

    adaptive_failures: list[str] = []

    for relative, require_monochrome in adaptive_specs:
        ok, evidence = adaptive_icon_contract(
            repo_root / relative,
            require_monochrome=require_monochrome,
        )

        if not ok:
            adaptive_failures.append(evidence)

    add(
        "adaptive-icon-resource-contract",
        not adaptive_failures,
        (
            "Adaptive launcher XML uses canonical background, foreground, "
            "and Android 13 monochrome resources"
        ),
        (
            "Adaptive launcher resource contract is invalid: "
            + "; ".join(adaptive_failures)
        ),
        adaptive_failures or [path for path, _ in adaptive_specs],
    )

    obsolete_brand_assets = [
        relative
        for relative in OBSOLETE_BRAND_PATHS
        if (repo_root / relative).exists()
    ]

    add(
        "obsolete-brand-assets-absent",
        not obsolete_brand_assets,
        "Obsolete launcher and legacy brand payload assets are absent",
        (
            "Obsolete brand assets remain: "
            + ", ".join(obsolete_brand_assets)
        ),
        obsolete_brand_assets or list(OBSOLETE_BRAND_PATHS),
    )

    legacy_tv_banner = (
        repo_root
        / "app/src/main/res/drawable-xhdpi/tv_banner.png"
    )

    add(
        "legacy-tv-banner-resource-absent",
        not legacy_tv_banner.exists(),
        "The obsolete single-density TV banner resource is absent",
        (
            "The obsolete drawable-xhdpi TV banner would override "
            "the density-aware package"
        ),
        [str(legacy_tv_banner)],
    )

    components_file = (
        repo_root
        / "app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt"
    )
    component_text = (
        _read(components_file)
        if components_file.is_file()
        else ""
    )

    add(
        "logo-content-scale",
        "ContentScale.Fit" in component_text,
        "Logo rendering uses ContentScale.Fit",
        "Logo rendering no longer proves ContentScale.Fit",
        [str(components_file)],
    )

    add(
        "brand-runtime-component-ownership",
        components_file.is_file()
        and "R.drawable.hulk_sa_logo" in component_text
        and "R.drawable.hulk_sa_mark" in component_text
        and "R.drawable.hulk_sa_mark_monochrome" in component_text
        and "R.drawable.ic_launcher_foreground" not in component_text
        and "R.mipmap.ic_launcher_tv" not in component_text,
        (
            "HulkComponents owns logo, mark, monochrome badge, and "
            "fallback artwork through canonical brand resources"
        ),
        (
            "HulkComponents still uses obsolete launcher resources or "
            "does not reference the canonical brand package"
        ),
        [str(components_file)],
    )

    tv_home_file = (
        repo_root
        / "app/src/main/java/sa/hulksa/player/tv/TvHomeChannelManager.kt"
    )
    tv_home_text = (
        _read(tv_home_file)
        if tv_home_file.is_file()
        else ""
    )

    add(
        "tv-home-brand-runtime-ownership",
        tv_home_file.is_file()
        and "R.drawable.hulk_sa_tv_channel_logo" in tv_home_text
        and "R.drawable.hulk_sa_content_fallback_wide" in tv_home_text
        and "R.mipmap.ic_launcher_tv" not in tv_home_text,
        (
            "Android TV provider uses canonical channel logo and "
            "wide fallback artwork"
        ),
        (
            "Android TV provider still uses launcher artwork or is "
            "missing canonical TV brand resources"
        ),
        [str(tv_home_file)],
    )

    build_workflow_file = repo_root / ".github/workflows/build-apk.yml"
    build_workflow_text = (
        _read(build_workflow_file)
        if build_workflow_file.is_file()
        else ""
    )

    legacy_workflow_markers = (
        "https://hulksa.com/assets/hulk-official-logo.webp",
        ".payload/assets/logo.part-",
        "cp /tmp/hulk-logo.webp",
        "text.replace('@mipmap/ic_launcher_round'",
        "text.replace('@mipmap/ic_launcher'",
    )

    workflow_overlay_markers = (
        "BRAND_RES_DIRS=(",
        'cp -R "app/src/main/res/$dir/." "project/app/src/main/res/$dir/"',
        (
            "app/src/main/java/sa/hulksa/player/ui/components/"
            "HulkComponents.kt"
        ),
        (
            "project/app/src/main/java/sa/hulksa/player/ui/components/"
            "HulkComponents.kt"
        ),
        (
            "app/src/main/java/sa/hulksa/player/tv/"
            "TvHomeChannelManager.kt"
        ),
        (
            "project/app/src/main/java/sa/hulksa/player/tv/"
            "TvHomeChannelManager.kt"
        ),
        "cmp -s",
    )

    add(
        "build-brand-contract",
        build_workflow_file.is_file()
        and all(
            marker not in build_workflow_text
            for marker in legacy_workflow_markers
        )
        and all(
            marker in build_workflow_text
            for marker in workflow_overlay_markers
        ),
        (
            "APK workflow overlays canonical branch brand resources and "
            "runtime source into the extracted Gradle project"
        ),
        (
            "APK workflow still contains the legacy brand override "
            "contract or does not overlay the canonical branch package"
        ),
        [str(build_workflow_file)],
    )

    present_legacy = [
        path
        for path in LEGACY_PATHS
        if (repo_root / path).exists()
    ]

    add(
        "legacy-lab-removed",
        not present_legacy,
        "Legacy compatibility/quality lab paths are absent",
        "Legacy paths remain: " + ", ".join(present_legacy),
        present_legacy or ["repository tree"],
    )

    main_root = repo_root / "app/src/main"

    production_files = (
        list(main_root.rglob("*.kt"))
        + list(main_root.rglob("*.java"))
        + list(main_root.rglob("*.xml"))
    )

    marker_hits: list[str] = []
    hardcoded_pixel_hits: list[str] = []

    for path in production_files:
        text = _read(path)

        if any(
            marker in text
            for marker in FORBIDDEN_PRODUCTION_MARKERS
        ):
            marker_hits.append(str(path.relative_to(repo_root)))

        if re.search(
            r"\b(?:1920|1080|1280|720)\s*\.\s*px\b",
            text,
        ):
            hardcoded_pixel_hits.append(
                str(path.relative_to(repo_root))
            )

    add(
        "production-test-hooks-absent",
        not marker_hits,
        "No legacy QA marker or overlay is present in production sources",
        "Production QA hooks found: " + ", ".join(marker_hits),
        marker_hits or ["app/src/main"],
    )

    add(
        "hardcoded-pixel-layout-absent",
        not hardcoded_pixel_hits,
        "No fixed 720p/1080p pixel layout literals were found",
        (
            "Hardcoded pixel layout candidates found: "
            + ", ".join(hardcoded_pixel_hits)
        ),
        hardcoded_pixel_hits or ["app/src/main"],
    )

    player_file = (
        repo_root
        / "app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt"
    )

    if player_file.is_file():
        player_text = _read(player_file)

        add(
            "player-surface-dpad-seek-direction",
            player_surface_seek_contract(player_text),
            (
                "TV/remote player surface maps RTL D-pad Left to forward "
                "and Right to rewind while preserving non-TV mapping"
            ),
            (
                "TV/remote player surface RTL D-pad seek contract is "
                "reversed or unverified"
            ),
            [str(player_file)],
        )

        add(
            "player-seekbar-dpad-direction",
            player_seekbar_contract(player_text),
            (
                "TV/remote seek bar maps RTL D-pad Left to forward and "
                "Right to rewind while preserving non-TV mapping"
            ),
            (
                "TV/remote seek bar RTL D-pad direction is reversed "
                "or unverified"
            ),
            [str(player_file)],
        )

        focus_races = player_focus_race_findings(player_text)

        add(
            "player-focus-race-policy",
            not focus_races,
            "Player focus ownership contains no timing retries",
            (
                "Player focus race workaround remains: "
                + ", ".join(focus_races)
            ),
            [str(player_file)],
        )
    else:
        checks.append(
            Check(
                "player-source-present",
                "FAIL",
                "PlayerScreen.kt is missing",
                [str(player_file)],
            )
        )

    workflow_root = repo_root / ".github/workflows"

    v2_workflows = (
        list(workflow_root.glob("compatibility-v2-*.yml"))
        if workflow_root.is_dir()
        else []
    )

    workflow_policy_hits: list[str] = []

    for path in v2_workflows:
        text = _read(path)

        if (
            "continue-on-error:" in text
            or "update-baseline" in text
            or "update_baseline" in text
        ):
            workflow_policy_hits.append(
                str(path.relative_to(repo_root))
            )

    add(
        "v2-workflow-fail-closed",
        not workflow_policy_hits,
        (
            "V2 workflows contain no continue-on-error or automatic "
            "baseline update"
        ),
        (
            "Unsafe V2 workflow policy found: "
            + ", ".join(workflow_policy_hits)
        ),
        workflow_policy_hits or [".github/workflows"],
    )

    return checks


def write_reports(
    checks: list[Check],
    out_dir: Path,
) -> dict[str, object]:
    out_dir.mkdir(parents=True, exist_ok=True)

    summary = {
        status: sum(
            check.status == status
            for check in checks
        )
        for status in STATUSES
    }

    payload = {
        "schema_version": 2,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "summary": summary,
        "checks": [asdict(check) for check in checks],
    }

    (out_dir / "STATIC-RESULT.json").write_text(
        json.dumps(
            payload,
            indent=2,
            ensure_ascii=False,
        )
        + "\n",
        encoding="utf-8",
    )

    md = [
        "# Compatibility V2 static result",
        "",
        "| Status | Count |",
        "|---|---:|",
    ]

    md.extend(
        f"| {status} | {summary[status]} |"
        for status in STATUSES
    )

    md.extend(
        [
            "",
            "## Checks",
            "",
        ]
    )

    md.extend(
        (
            f"- **{check.status}** `{check.id}` -- "
            f"{check.message}"
        )
        for check in checks
    )

    (out_dir / "STATIC-RESULT.md").write_text(
        "\n".join(md) + "\n",
        encoding="utf-8",
    )

    failures = summary["FAIL"]
    skipped = summary["BLOCKED"] + summary["SKIPPED"]

    cases: list[str] = []

    for check in checks:
        body = ""

        if check.status == "FAIL":
            body = (
                f'<failure message="'
                f'{html.escape(check.message, quote=True)}">'
                f"{html.escape(check.message)}</failure>"
            )

        elif check.status in ("BLOCKED", "SKIPPED"):
            body = (
                f'<skipped message="'
                f'{html.escape(check.message, quote=True)}" />'
            )

        cases.append(
            (
                '<testcase classname="compatibility.v2.static" '
                f'name="{html.escape(check.id, quote=True)}">'
                f"{body}</testcase>"
            )
        )

    junit = (
        '<testsuite name="compatibility-v2-static" '
        f'tests="{len(checks)}" '
        f'failures="{failures}" '
        f'skipped="{skipped}">'
        + "".join(cases)
        + "</testsuite>\n"
    )

    (out_dir / "STATIC-RESULT.xml").write_text(
        junit,
        encoding="utf-8",
    )

    return payload


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()

    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path("."),
    )

    parser.add_argument(
        "--out",
        type=Path,
        required=True,
    )

    parser.add_argument(
        "--expected-logo-sha256",
        default=DEFAULT_LOGO_SHA256,
    )

    args = parser.parse_args(argv)

    checks = validate_repo(
        args.repo_root.resolve(),
        args.expected_logo_sha256,
    )

    payload = write_reports(
        checks,
        args.out,
    )

    print(
        json.dumps(
            payload["summary"],
            sort_keys=True,
        )
    )

    return 1 if payload["summary"]["FAIL"] else 0


if __name__ == "__main__":
    sys.exit(main())