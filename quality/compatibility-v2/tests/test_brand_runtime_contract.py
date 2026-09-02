from __future__ import annotations

import struct
import unittest
import zlib
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).parents[3]
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
DENSITIES = {
    "mdpi": {"launcher": 48, "tv": 80, "banner": (160, 90), "notification": 24},
    "hdpi": {"launcher": 72, "tv": 120, "banner": (240, 135), "notification": 36},
    "xhdpi": {"launcher": 96, "tv": 160, "banner": (320, 180), "notification": 48},
    "xxhdpi": {"launcher": 144, "tv": 240, "banner": (480, 270), "notification": 72},
    "xxxhdpi": {"launcher": 192, "tv": 320, "banner": (640, 360), "notification": 96},
}


def png_rgba(path: Path) -> tuple[int, int, bytes]:
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise AssertionError(f"{path} is not a PNG")
    pos = 8
    width = height = color_type = bit_depth = interlace = None
    compressed = bytearray()
    while pos < len(data):
        length = struct.unpack(">I", data[pos : pos + 4])[0]
        kind = data[pos + 4 : pos + 8]
        payload = data[pos + 8 : pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, bit_depth, color_type, _, _, interlace = struct.unpack(">IIBBBBB", payload)
        elif kind == b"IDAT":
            compressed.extend(payload)
        elif kind == b"IEND":
            break
    if None in (width, height, color_type, bit_depth, interlace):
        raise AssertionError(f"{path} has no valid IHDR")
    if bit_depth != 8 or color_type != 6 or interlace != 0:
        raise AssertionError(f"{path} must be non-interlaced 8-bit RGBA")
    stride = width * 4
    raw = zlib.decompress(bytes(compressed))
    expected = height * (stride + 1)
    if len(raw) != expected:
        raise AssertionError(f"{path} decompressed size mismatch")
    rows: list[bytearray] = []
    offset = 0
    for _ in range(height):
        filter_type = raw[offset]
        scan = bytearray(raw[offset + 1 : offset + 1 + stride])
        offset += stride + 1
        prev = rows[-1] if rows else bytearray(stride)
        for i in range(stride):
            left = scan[i - 4] if i >= 4 else 0
            up = prev[i]
            up_left = prev[i - 4] if i >= 4 else 0
            if filter_type == 1:
                scan[i] = (scan[i] + left) & 0xFF
            elif filter_type == 2:
                scan[i] = (scan[i] + up) & 0xFF
            elif filter_type == 3:
                scan[i] = (scan[i] + ((left + up) >> 1)) & 0xFF
            elif filter_type == 4:
                p = left + up - up_left
                pa, pb, pc = abs(p - left), abs(p - up), abs(p - up_left)
                predictor = left if pa <= pb and pa <= pc else up if pb <= pc else up_left
                scan[i] = (scan[i] + predictor) & 0xFF
            elif filter_type != 0:
                raise AssertionError(f"{path} uses unsupported PNG filter {filter_type}")
        rows.append(scan)
    return width, height, b"".join(rows)


def alpha_bbox(path: Path) -> tuple[int, int, int, int] | None:
    width, height, rgba = png_rgba(path)
    xs: list[int] = []
    ys: list[int] = []
    for y in range(height):
        row = y * width * 4
        for x in range(width):
            if rgba[row + x * 4 + 3]:
                xs.append(x)
                ys.append(y)
    if not xs:
        return None
    return min(xs), min(ys), max(xs) + 1, max(ys) + 1


def assert_white_alpha(test: unittest.TestCase, path: Path) -> None:
    _, _, rgba = png_rgba(path)
    for i in range(0, len(rgba), 4):
        if rgba[i + 3]:
            test.assertEqual((255, 255, 255), tuple(rgba[i : i + 3]), str(path))


def assert_transparent_rgb_is_clear(test: unittest.TestCase, path: Path) -> None:
    _, _, rgba = png_rgba(path)
    for i in range(0, len(rgba), 4):
        if rgba[i + 3] == 0:
            test.assertEqual((0, 0, 0), tuple(rgba[i : i + 3]), str(path))


def gold_bbox(path: Path) -> tuple[int, int, int, int] | None:
    width, height, rgba = png_rgba(path)
    xs: list[int] = []
    ys: list[int] = []
    for y in range(height):
        row = y * width * 4
        for x in range(width):
            offset = row + x * 4
            r, g, b, a = rgba[offset : offset + 4]
            if a and r > 80 and r > b + 20 and g > b + 10:
                xs.append(x)
                ys.append(y)
    if not xs:
        return None
    return min(xs), min(ys), max(xs) + 1, max(ys) + 1


class BrandRuntimeContractTest(unittest.TestCase):
    def test_in_app_logo_and_mark_are_independent_nodpi_assets(self) -> None:
        logo = ROOT / "app/src/main/res/drawable-nodpi/hulk_sa_logo.png"
        mark = ROOT / "app/src/main/res/drawable-nodpi/hulk_sa_mark.png"
        mono = ROOT / "app/src/main/res/drawable-nodpi/hulk_sa_mark_monochrome.png"
        self.assertEqual((672, 1024), png_rgba(logo)[:2])
        self.assertEqual((768, 1024), png_rgba(mark)[:2])
        self.assertEqual((768, 1024), png_rgba(mono)[:2])
        self.assertLess(logo.stat().st_size, 1024 * 1024)
        self.assertLess(mark.stat().st_size, 1024 * 1024)
        self.assertFalse(list((ROOT / "app/src/main/res").glob("drawable-anydpi*/hulk_sa_logo.*")))
        assert_white_alpha(self, mono)
        for asset in (logo, mark, mono):
            assert_transparent_rgb_is_clear(self, asset)

        tv_channel_logo = ROOT / "app/src/main/res/drawable-nodpi/hulk_sa_tv_channel_logo.png"
        tv_fallback = ROOT / "app/src/main/res/drawable-nodpi/hulk_sa_content_fallback_wide.png"
        self.assertEqual((512, 512), png_rgba(tv_channel_logo)[:2])
        self.assertEqual((1280, 720), png_rgba(tv_fallback)[:2])
        self.assertLess(tv_channel_logo.stat().st_size, 512 * 1024)
        self.assertLess(tv_fallback.stat().st_size, 1024 * 1024)
        assert_transparent_rgb_is_clear(self, tv_channel_logo)

    def test_adaptive_icon_uses_large_safe_shield_only_foreground(self) -> None:
        foreground = ROOT / "app/src/main/res/drawable-nodpi/hulk_sa_adaptive_foreground.png"
        background = ROOT / "app/src/main/res/drawable-nodpi/hulk_sa_adaptive_background.png"
        monochrome = ROOT / "app/src/main/res/drawable-nodpi/hulk_sa_adaptive_monochrome.png"
        self.assertEqual((432, 432), png_rgba(foreground)[:2])
        self.assertEqual((432, 432), png_rgba(background)[:2])
        self.assertEqual((432, 432), png_rgba(monochrome)[:2])
        bbox = alpha_bbox(foreground)
        self.assertIsNotNone(bbox)
        left, top, right, bottom = bbox
        self.assertGreaterEqual(left, 32)
        self.assertGreaterEqual(top, 32)
        self.assertLessEqual(right, 400)
        self.assertLessEqual(bottom, 400)
        self.assertGreaterEqual((right - left) / 432, 0.58)
        self.assertGreaterEqual((bottom - top) / 432, 0.78)
        _, _, bg_rgba = png_rgba(background)
        self.assertTrue(all(bg_rgba[i] == 255 for i in range(3, len(bg_rgba), 4)))
        self.assertEqual(bbox, alpha_bbox(monochrome))
        assert_white_alpha(self, monochrome)
        assert_transparent_rgb_is_clear(self, foreground)
        assert_transparent_rgb_is_clear(self, monochrome)

    def test_launcher_density_matrix_is_complete_and_full_bleed(self) -> None:
        for density, spec in DENSITIES.items():
            for name, expected in (
                ("ic_launcher.png", (spec["launcher"], spec["launcher"])),
                ("ic_launcher_round.png", (spec["launcher"], spec["launcher"])),
                ("ic_launcher_tv.png", (spec["tv"], spec["tv"])),
                ("tv_banner.png", spec["banner"]),
            ):
                path = ROOT / f"app/src/main/res/mipmap-{density}/{name}"
                width, height, rgba = png_rgba(path)
                self.assertEqual(expected, (width, height), str(path))
                if name != "ic_launcher_round.png":
                    self.assertTrue(
                        all(rgba[i] == 255 for i in range(3, len(rgba), 4)),
                        str(path),
                    )
                else:
                    self.assertIsNotNone(alpha_bbox(path))
                if name != "tv_banner.png":
                    mark_bbox = gold_bbox(path)
                    self.assertIsNotNone(mark_bbox, str(path))
                    left, top, right, bottom = mark_bbox
                    self.assertGreaterEqual((right - left) / width, 0.58, str(path))
                    self.assertGreaterEqual((bottom - top) / height, 0.78, str(path))

    def test_notification_icons_are_white_alpha_masks(self) -> None:
        for density, spec in DENSITIES.items():
            path = ROOT / f"app/src/main/res/drawable-{density}/ic_stat_hulk.png"
            self.assertEqual((spec["notification"], spec["notification"]), png_rgba(path)[:2])
            self.assertIsNotNone(alpha_bbox(path))
            assert_white_alpha(self, path)
            assert_transparent_rgb_is_clear(self, path)

    def test_adaptive_xml_points_directly_to_dedicated_assets(self) -> None:
        for relative in (
            "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
            "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
            "app/src/main/res/mipmap-anydpi-v26/ic_launcher_tv.xml",
        ):
            root = ElementTree.parse(ROOT / relative).getroot()
            self.assertEqual(
                "@drawable/hulk_sa_adaptive_background",
                root.find("background").attrib[ANDROID_NS + "drawable"],
                relative,
            )
            self.assertEqual(
                "@drawable/hulk_sa_adaptive_foreground",
                root.find("foreground").attrib[ANDROID_NS + "drawable"],
                relative,
            )
        for relative in (
            "app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml",
            "app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml",
        ):
            root = ElementTree.parse(ROOT / relative).getroot()
            self.assertEqual(
                "@drawable/hulk_sa_adaptive_monochrome",
                root.find("monochrome").attrib[ANDROID_NS + "drawable"],
                relative,
            )

    def test_obsolete_runtime_overrides_and_wrappers_are_absent(self) -> None:
        obsolete = (
            "app/src/main/res/drawable-anydpi-v21/hulk_sa_logo.png",
            "app/src/main/res/drawable/ic_launcher_background.xml",
            "app/src/main/res/drawable/ic_launcher_foreground.png",
            "app/src/main/res/drawable/ic_launcher_foreground.xml",
            "app/src/main/res/drawable/ic_launcher_tv_foreground.xml",
            "app/src/main/res/drawable-v33/ic_launcher_monochrome.xml",
            "app/src/main/res/drawable/ic_stat_hulk.xml",
        )
        for relative in obsolete:
            self.assertFalse((ROOT / relative).exists(), relative)

    def test_production_brand_surfaces_do_not_reuse_launcher_artwork(self) -> None:
        components = (
            ROOT / "app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt"
        ).read_text(encoding="utf-8")
        tv_home = (
            ROOT / "app/src/main/java/sa/hulksa/player/tv/TvHomeChannelManager.kt"
        ).read_text(encoding="utf-8")
        self.assertNotIn("R.drawable.ic_launcher_foreground", components)
        self.assertNotIn("R.mipmap.ic_launcher", components)
        self.assertNotIn("R.mipmap.ic_launcher", tv_home)
        self.assertIn("R.drawable.hulk_sa_mark", components)
        self.assertIn("R.drawable.hulk_sa_mark_monochrome", components)
        self.assertIn("R.drawable.hulk_sa_tv_channel_logo", tv_home)
        self.assertIn("R.drawable.hulk_sa_content_fallback_wide", tv_home)

    def test_tinted_badge_uses_monochrome_mark(self) -> None:
        components = (
            ROOT / "app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt"
        ).read_text(encoding="utf-8")
        start = components.index("fun BrandBadge(")
        end = components.find("\n@Composable", start + 1)
        badge = components[start:] if end < 0 else components[start:end]
        self.assertIn("R.drawable.hulk_sa_mark_monochrome", badge)
        self.assertNotIn("R.drawable.hulk_sa_mark)", badge)


if __name__ == "__main__":
    unittest.main()
