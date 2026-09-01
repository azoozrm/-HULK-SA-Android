from __future__ import annotations

import hashlib
import re
import struct
import unittest
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).parents[3]
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
RUNTIME_SHA256 = "adca7895c43cb8934f24b2f9988c4b9e4bc6a11463b7dc6827cded30fb3a4efa"
RUNTIME_DIMENSIONS = (432, 432)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def png_ihdr(path: Path) -> tuple[int, int, int]:
    with path.open("rb") as handle:
        if handle.read(8) != b"\x89PNG\r\n\x1a\n":
            raise AssertionError(f"{path} is not a PNG")
        length = struct.unpack(">I", handle.read(4))[0]
        if handle.read(4) != b"IHDR" or length != 13:
            raise AssertionError(f"{path} has an invalid IHDR")
        width, height, _, color_type, _, _, _ = struct.unpack(">IIBBBBB", handle.read(13))
    return width, height, color_type


class BrandRuntimeContractTest(unittest.TestCase):
    def test_runtime_logo_is_optimized_transparent_asset(self) -> None:
        runtime_logo = ROOT / "app/src/main/res/drawable-anydpi-v21/hulk_sa_logo.png"
        self.assertTrue(runtime_logo.is_file())
        self.assertEqual(RUNTIME_SHA256, sha256(runtime_logo))
        width, height, color_type = png_ihdr(runtime_logo)
        self.assertEqual(RUNTIME_DIMENSIONS, (width, height))
        self.assertEqual(6, color_type)
        self.assertLess(runtime_logo.stat().st_size, 256 * 1024)

    def test_compose_foreground_is_direct_raster_resource(self) -> None:
        raster = ROOT / "app/src/main/res/drawable/ic_launcher_foreground.png"
        legacy_xml = ROOT / "app/src/main/res/drawable/ic_launcher_foreground.xml"
        self.assertTrue(raster.is_file())
        self.assertFalse(legacy_xml.exists())
        self.assertEqual(RUNTIME_SHA256, sha256(raster))
        self.assertEqual((*RUNTIME_DIMENSIONS, 6), png_ihdr(raster))

    def test_adaptive_launchers_keep_launcher_safe_zone_source(self) -> None:
        adaptive_paths = (
            "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
            "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
            "app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml",
            "app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml",
        )
        for relative in adaptive_paths:
            root = ElementTree.parse(ROOT / relative).getroot()
            foreground = root.find("foreground")
            self.assertIsNotNone(foreground, relative)
            self.assertEqual(
                "@drawable/hulk_sa_adaptive_foreground",
                foreground.attrib[ANDROID_NS + "drawable"],
                relative,
            )

        tv_foreground = ElementTree.parse(
            ROOT / "app/src/main/res/drawable/ic_launcher_tv_foreground.xml"
        ).getroot()
        self.assertEqual(
            "@drawable/hulk_sa_adaptive_foreground",
            tv_foreground.attrib[ANDROID_NS + "drawable"],
        )

    def test_anydpi_runtime_override_is_supported_by_min_sdk(self) -> None:
        gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        match = re.search(r"\bminSdk\s*=\s*(\d+)", gradle)
        self.assertIsNotNone(match)
        self.assertGreaterEqual(int(match.group(1)), 21)


if __name__ == "__main__":
    unittest.main()
