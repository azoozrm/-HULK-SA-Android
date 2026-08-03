import json
import unittest
from pathlib import Path


class DeviceMatrixExpansionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = json.loads(
            Path("quality/compatibility-v2/config/device-matrix.json").read_text(encoding="utf-8")
        )
        cls.profiles = {item["id"]: item for item in cls.data["profiles"]}

    def test_original_and_new_profiles_are_present(self):
        required = {
            "phone-small-api29",
            "phone-medium-api35",
            "phone-landscape-font150-api35",
            "tablet-600-portrait-api35",
            "tablet-expanded-landscape-api35",
            "tv-logical-960x540-api36",
            "tv-720p-api36",
            "tv-1080p-api36",
            "phone-320x568-api35",
            "phone-360x800-api35",
            "phone-portrait-font130-api35",
            "phone-short-landscape-api35",
            "phone-cutout-gesture-api35",
            "tablet-medium-landscape-api35",
            "tablet-resizable-medium-api35",
            "foldable-unfolded-api35",
            "tv-4k-api36",
        }
        self.assertEqual(required, set(self.profiles))

    def test_expected_logical_geometry_matches_physical_density(self):
        for profile in self.profiles.values():
            width = round(profile["width_px"] * 160 / profile["density_dpi"])
            height = round(profile["height_px"] * 160 / profile["density_dpi"])
            self.assertEqual(width, profile["expected_width_dp"], profile["id"])
            self.assertEqual(height, profile["expected_height_dp"], profile["id"])

    def test_tv_4k_is_960_by_540_logical_dp(self):
        profile = self.profiles["tv-4k-api36"]
        self.assertEqual((3840, 2160), (profile["width_px"], profile["height_px"]))
        self.assertEqual(640, profile["density_dpi"])
        self.assertEqual((960, 540), (profile["expected_width_dp"], profile["expected_height_dp"]))
        self.assertEqual("TELEVISION", profile["expected_device_class"])
        self.assertEqual("REMOTE", profile["expected_input_mode"])

    def test_cutout_and_gesture_profile_is_explicit(self):
        profile = self.profiles["phone-cutout-gesture-api35"]
        self.assertEqual("tall", profile["cutout_mode"])
        self.assertEqual("gestural", profile["navigation_mode"])


if __name__ == "__main__":
    unittest.main()
