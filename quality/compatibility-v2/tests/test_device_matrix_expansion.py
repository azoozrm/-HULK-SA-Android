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

    def test_emulator_profile_handles_non_root_images_without_err_trap_abort(self):
        source = Path("quality/compatibility-v2/configure_emulator_profile.sh").read_text(encoding="utf-8")
        self.assertIn('if root_output="$(adb root 2>&1)"; then', source)
        self.assertNotIn('set +e\nroot_output="$(adb root 2>&1)"', source)

    def test_emulator_profile_clears_stale_tv_size_override(self):
        source = Path("quality/compatibility-v2/configure_emulator_profile.sh").read_text(encoding="utf-8")
        self.assertIn("adb shell wm size reset", source)
        self.assertIn('physical_size_after_reset=', source)
        self.assertIn('effective_size_from_output', source)
        self.assertIn('effective emulator size does not match requested profile', source)

    def test_full_matrix_retries_one_failed_emulator_attempt_cleanly(self):
        source = Path(".github/workflows/compatibility-v2-full.yml").read_text(encoding="utf-8")
        self.assertEqual(2, source.count("uses: reactivecircus/android-emulator-runner@v2"))
        self.assertIn("id: runtime-attempt-1", source)
        self.assertIn("continue-on-error: true", source)
        self.assertIn("Clear evidence from failed emulator attempt", source)
        self.assertIn("steps.runtime-attempt-1.outcome == 'failure'", source)

    def test_full_matrix_uses_profile_specific_test_selectors_for_legacy_and_short_windows(self):
        source = Path(".github/workflows/compatibility-v2-full.yml").read_text(encoding="utf-8")
        self.assertIn("'phone-small-api29': ','.join([", source)
        self.assertIn("#phonePortraitOrientationRestoresAfterLandscapePlayback", source)
        self.assertNotIn(
            "compatibility_class + '#phonePortraitLoginFieldsAcceptTypingWithoutCrash',\n                  compatibility_class + '#phonePortraitOrientationRestoresAfterLandscapePlayback'",
            source,
        )
        self.assertIn("'phone-short-landscape-api35': ','.join([", source)
        self.assertIn("#shortLandscapePhoneCanScrollToPrimaryLoginActions", source)
        self.assertIn("'test_class': selectors.get(profile['id'], compatibility_class)", source)
        self.assertEqual(2, source.count("'${{ matrix.test_class }}'"))


if __name__ == "__main__":
    unittest.main()
