import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[3]


class MobileWindowContractTest(unittest.TestCase):
    def test_mobile_navigation_does_not_apply_bottom_navigation_inset_at_top(self) -> None:
        shell = (
            REPO_ROOT
            / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
        ).read_text(encoding="utf-8")

        mobile_navigation = shell.split("private fun MobileNavigation", 1)[1].split(
            "private fun DestinationContent", 1
        )[0]
        self.assertIn(".statusBarsPadding(),", mobile_navigation)
        self.assertNotIn("navigationBarsPadding", mobile_navigation)

    def test_mobile_catalogs_do_not_reserve_persistent_hint_rows(self) -> None:
        shell = (
            REPO_ROOT
            / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
        ).read_text(encoding="utf-8")

        self.assertIn("if (isTv) CatalogInteractionHints(true)", shell)
        self.assertIn("if (isTv) LiveInteractionHints(true)", shell)
        self.assertIn("Spacer(Modifier.height(if (isTv) 9.dp else 4.dp))", shell)
        self.assertIn("Spacer(Modifier.height(if (isTv) 8.dp else 4.dp))", shell)

    def test_mobile_player_uses_adaptive_immersive_landscape_policy(self) -> None:
        app = (
            REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/HulkApp.kt"
        ).read_text(encoding="utf-8")
        policy = (
            REPO_ROOT
            / "app/src/main/java/sa/hulksa/player/ui/adaptive/WindowPresentationPolicy.kt"
        ).read_text(encoding="utf-8")

        self.assertIn("ApplyAdaptiveWindowPresentation(", app)
        self.assertIn("isPlayer = state.screen == HulkScreen.PLAYER", app)
        self.assertIn("HulkSystemBarsMode.IMMERSIVE", policy)
        self.assertIn("HulkOrientationRequest.SENSOR_LANDSCAPE", policy)
        self.assertIn("controller.hide(WindowInsets.Type.systemBars())", policy)
        self.assertIn("controller.hide(WindowInsets.Type.navigationBars())", policy)

    def test_runtime_suite_checks_real_phone_system_bar_visibility(self) -> None:
        instrumentation = (
            REPO_ROOT
            / "app/src/androidTest/java/sa/hulksa/player/compatibilityv2/CompatibilityV2InstrumentationTest.kt"
        ).read_text(encoding="utf-8")

        self.assertIn(
            "fun phoneWindowKeepsStatusBarAndHidesNavigationBar()",
            instrumentation,
        )
        self.assertIn("WindowInsetsCompat.Type.statusBars()", instrumentation)
        self.assertIn("WindowInsetsCompat.Type.navigationBars()", instrumentation)


if __name__ == "__main__":
    unittest.main()
