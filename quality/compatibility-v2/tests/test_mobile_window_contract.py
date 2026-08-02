import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[3]


class MobileWindowContractTest(unittest.TestCase):
    """Permanent contracts derived from the physical Galaxy landscape evidence."""

    def test_mobile_navigation_uses_central_safe_drawing_insets(self) -> None:
        shell = (REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt").read_text(encoding="utf-8")
        mobile_navigation = shell.split("private fun MobileNavigation", 1)[1].split("private fun DestinationContent", 1)[0]
        self.assertNotIn("statusBarsPadding", mobile_navigation)
        self.assertNotIn("navigationBarsPadding", mobile_navigation)
        self.assertIn("compactLandscape", mobile_navigation)

    def test_mobile_root_fills_window_and_reserves_complete_safe_drawing(self) -> None:
        app = (REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/HulkApp.kt").read_text(encoding="utf-8")
        self.assertIn(".fillMaxSize()\n                .background(windowBackground)", app)
        self.assertIn("Modifier.windowInsetsPadding(WindowInsets.safeDrawing)", app)
        self.assertIn("state.screen != HulkScreen.PLAYER", app)
        self.assertIn("state.screen != HulkScreen.LOGIN", app)

    def test_window_policy_draws_behind_display_cutout_without_immersive_normal_pages(self) -> None:
        policy = (REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/adaptive/WindowPresentationPolicy.kt").read_text(encoding="utf-8")
        theme = (REPO_ROOT / "app/src/main/res/values/themes.xml").read_text(encoding="utf-8")
        self.assertIn("LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS", policy)
        self.assertIn("LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES", policy)
        self.assertIn("Build.VERSION.SDK_INT >= Build.VERSION_CODES.P", policy)
        self.assertIn("controller.show(WindowInsets.Type.systemBars())", policy)
        self.assertIn("window.navigationBarColor = Color.TRANSPARENT", policy)
        self.assertIn("window.isNavigationBarContrastEnforced = false", policy)
        self.assertIn("android:navigationBarColor\">@android:color/transparent", theme)
        self.assertNotIn("android:windowLayoutInDisplayCutoutMode", theme)
        self.assertNotIn("android:enforceNavigationBarContrast", theme)
        self.assertNotIn("android:windowLightNavigationBar", theme)

    def test_short_landscape_login_has_real_compact_dimensions(self) -> None:
        login = (REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/LoginScreen.kt").read_text(encoding="utf-8")
        self.assertIn("compactMobileLandscape", login)
        self.assertIn("compact -> 112.dp", login)
        self.assertIn("compact -> 92.dp", login)
        self.assertIn("min = if (compact) 38.dp else 55.dp", login)
        self.assertIn("if (!compact)", login)
        self.assertIn("Modifier.fillMaxHeight()", login)
        self.assertIn("Modifier.verticalScroll(panelScrollState)", login)
        self.assertIn("compact = compact", login)

    def test_short_landscape_catalogs_use_one_vertical_scroll_surface(self) -> None:
        shell = (REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt").read_text(encoding="utf-8")
        self.assertIn("private fun CompactLandscapePosterCatalog", shell)
        self.assertIn("item(key = \"catalog-header\", span = { GridItemSpan(maxLineSpan) })", shell)
        self.assertIn("item(key = \"catalog-categories\", span = { GridItemSpan(maxLineSpan) })", shell)
        self.assertIn("private fun CompactLandscapeLiveCatalog", shell)
        live = shell.split("private fun CompactLandscapeLiveCatalog", 1)[1].split("private fun LiveStage", 1)[0]
        self.assertIn("LazyColumn(", live)
        self.assertIn("item(key = \"live-header\")", live)
        self.assertIn("item(key = \"live-categories\")", live)

    def test_mobile_destination_content_is_clipped_below_top_navigation(self) -> None:
        shell = (REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt").read_text(encoding="utf-8")
        self.assertEqual(shell.count("Box(Modifier.weight(1f).clipToBounds())"), 1)
        self.assertIn("import androidx.compose.ui.draw.clipToBounds", shell)

    def test_mobile_player_restores_real_pre_player_orientation(self) -> None:
        app = (REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/HulkApp.kt").read_text(encoding="utf-8")
        policy = (REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/adaptive/WindowPresentationPolicy.kt").read_text(encoding="utf-8")
        self.assertIn("ApplyAdaptiveWindowPresentation(", app)
        self.assertIn("isPlayer = state.screen == HulkScreen.PLAYER", app)
        self.assertIn("prePlayerOrientation = activity.resources.configuration.orientation", policy)
        self.assertIn("restoreOrientationRequest(prePlayerOrientation)", policy)
        self.assertIn("ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT", policy)
        self.assertIn("controller.hide(WindowInsets.Type.systemBars())", policy)

    def test_runtime_suite_checks_safe_drawing_and_cutout(self) -> None:
        instrumentation = (REPO_ROOT / "app/src/androidTest/java/sa/hulksa/player/compatibilityv2/CompatibilityV2InstrumentationTest.kt").read_text(encoding="utf-8")
        self.assertIn("WindowInsetsCompat.Type.displayCutout()", instrumentation)
        self.assertIn("safeContentBounds", instrumentation)
        self.assertIn("layoutInDisplayCutoutMode", instrumentation)
        self.assertIn("immersive_cling_title", instrumentation)


if __name__ == "__main__":
    unittest.main()
