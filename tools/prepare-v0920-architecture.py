#!/usr/bin/env python3
"""Prepare the official HULK SA source for v0.9.2.0 architecture qualification.

This script edits source code/configuration only. It never reads, patches, repackages,
or signs an APK. The operation is intentionally narrow and idempotent.
"""

from __future__ import annotations

from pathlib import Path
import re
import sys
from typing import NoReturn

TARGET_VERSION_NAME = "0.9.2.0"
TARGET_VERSION_CODE = 43
TARGET_ABIS = ("arm64-v8a", "armeabi-v7a", "x86_64")


def fail(message: str) -> NoReturn:
    raise SystemExit(message)


def replace_exact_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count == 1:
        return text.replace(old, new, 1)
    if count == 0 and new in text:
        return text
    fail(f"Expected exactly one {label} marker, found {count}: {old!r}")


def main() -> int:
    if len(sys.argv) != 2:
        fail("Usage: prepare-v0920-architecture.py <project-root>")

    project_root = Path(sys.argv[1]).resolve()
    gradle_file = project_root / "app/build.gradle.kts"
    manifest_file = project_root / "app/src/main/AndroidManifest.xml"

    if not gradle_file.is_file():
        fail(f"Missing Gradle source file: {gradle_file}")
    if not manifest_file.is_file():
        fail(f"Missing manifest source file: {manifest_file}")

    original = gradle_file.read_text(encoding="utf-8")
    updated = original

    view_model = project_root / "app/src/main/java/sa/hulksa/player/HulkViewModel.kt"
    if view_model.is_file() and "`$stable`" in view_model.read_text(encoding="utf-8"):
        fail("Recovered Compose `$stable` artifacts remain. Run repair-v09120.py first.")

    updated = replace_exact_once(
        updated,
        "versionCode = 42",
        f"versionCode = {TARGET_VERSION_CODE}",
        "versionCode",
    )
    updated = replace_exact_once(
        updated,
        'versionName = "0.9.1.20"',
        f'versionName = "{TARGET_VERSION_NAME}"',
        "versionName",
    )

    abi_block = (
        "\n        // Phase 2: ship one universal APK with only qualified ABIs.\n"
        "        // x86 is intentionally excluded; x86_64 remains for emulators/tests.\n"
        "        ndk {\n"
        "            abiFilters += listOf(\n"
        '                "arm64-v8a",\n'
        '                "armeabi-v7a",\n'
        '                "x86_64",\n'
        "            )\n"
        "        }\n"
    )

    if "abiFilters" not in updated:
        marker = "        vectorDrawables.useSupportLibrary = true\n"
        if updated.count(marker) != 1:
            fail("Could not locate the unique vectorDrawables marker for ABI insertion.")
        updated = updated.replace(marker, marker + abi_block, 1)
    else:
        for abi in TARGET_ABIS:
            if f'"{abi}"' not in updated:
                fail(f"Existing ABI configuration does not include required ABI: {abi}")
        without_x86_64 = re.sub(r'"x86_64"', "", updated)
        if '"x86"' in without_x86_64:
            fail("Existing ABI configuration still contains forbidden x86.")

    required_markers = (
        'namespace = "sa.hulksa.player"',
        'applicationId = "sa.hulksa.player"',
        "compileSdk = 36",
        "minSdk = 23",
        "targetSdk = 36",
        'applicationIdSuffix = ".dev"',
        'versionNameSuffix = "-beta"',
    )
    for marker in required_markers:
        if marker not in updated:
            fail(f"Required approved source marker disappeared: {marker}")

    if updated.count(f"versionCode = {TARGET_VERSION_CODE}") != 1:
        fail("Target versionCode is not unique after preparation.")
    if updated.count(f'versionName = "{TARGET_VERSION_NAME}"') != 1:
        fail("Target versionName is not unique after preparation.")

    gradle_file.write_text(updated, encoding="utf-8")
    print(f"Prepared source: {gradle_file}")
    print(f"Version: {TARGET_VERSION_NAME} ({TARGET_VERSION_CODE})")
    print("Qualified ABIs: " + ", ".join(TARGET_ABIS))
    print("Output policy: Universal APK + Android App Bundle from this source tree")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
