from __future__ import annotations

import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
MAIN_ACTIVITY = REPO_ROOT / "app/src/main/java/sa/hulksa/player/MainActivity.kt"
HULK_APP = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/HulkApp.kt"
ADAPTIVE_UI = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/adaptive/AdaptiveUi.kt"
PLAYER_SCREEN = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt"
PLAYER_PRO = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/PlayerProEpisodeNavigation.kt"
LIVE_BROWSER = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/LiveTvProChannelBrowser.kt"
BOTTOM_NAV = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/StableMobileBottomNavigation.kt"


class MobileLiveUxQualificationTest(unittest.TestCase):
    @staticmethod
    def read(path: Path) -> str:
        return path.read_text(encoding="utf-8")

    def test_phone_window_keeps_edge_to_edge_and_cutout_contract(self) -> None:
        text = self.read(MAIN_ACTIVITY)
        self.assertIn("WindowCompat.setDecorFitsSystemWindows(window, false)", text)
        self.assertIn("window.statusBarColor = Color.TRANSPARENT", text)
        self.assertIn("window.navigationBarColor = Color.TRANSPARENT", text)
        self.assertIn("attributes.layoutInDisplayCutoutMode", text)
        self.assertIn("WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS", text)
        self.assertIn("WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES", text)

    def test_phone_player_orientation_policy_is_transition_safe(self) -> None:
        text = self.read(MAIN_ACTIVITY)
        policy = re.compile(
            r"val targetOrientation = when \{\s*"
            r"largeScreen -> ActivityInfo\.SCREEN_ORIENTATION_UNSPECIFIED\s*"
            r"screen == HulkScreen\.PLAYER -> ActivityInfo\.SCREEN_ORIENTATION_SENSOR_LANDSCAPE\s*"
            r"else -> ActivityInfo\.SCREEN_ORIENTATION_PORTRAIT",
            re.DOTALL,
        )
        self.assertRegex(text, policy)
        self.assertIn("override fun onConfigurationChanged(newConfig: Configuration)", text)
        self.assertIn("applyPhoneOrientationPolicy(currentScreen, newConfig)", text)
        self.assertIn("observePhoneOrientationPolicy()", text)

    def test_live_main_content_and_mobile_navigation_respect_safe_drawing(self) -> None:
        app = self.read(HULK_APP)
        bottom_nav = self.read(BOTTOM_NAV)
        self.assertIn("val applySafeDrawingInsets =", app)
        self.assertIn("!isTv && !isPhoneHome && state.screen != HulkScreen.PLAYER && state.screen != HulkScreen.LOGIN", app)
        self.assertIn("Modifier.windowInsetsPadding(WindowInsets.safeDrawing)", app)
        self.assertIn("adaptiveUi.navigationType != HulkNavigationType.RAIL", app)
        self.assertIn("StableMobileBottomNavigation(", app)
        self.assertIn(".navigationBarsPadding()", bottom_nav)
        self.assertIn(".fillMaxWidth()", bottom_nav)

    def test_adaptive_classifier_covers_phone_tablet_and_foldable_windows(self) -> None:
        text = self.read(ADAPTIVE_UI)
        self.assertIn("LocalWindowInfo.current.containerDpSize", text)
        self.assertIn("val smallestWidthDp = minOf(widthDp, heightDp)", text)
        self.assertIn("widthDp < 600 -> HulkWindowWidthClass.COMPACT", text)
        self.assertIn("widthDp < 840 -> HulkWindowWidthClass.MEDIUM", text)
        self.assertIn("smallestWidthDp >= 600 -> HulkDeviceClass.TABLET", text)
        self.assertIn("deviceClass == HulkDeviceClass.TABLET && windowWidthClass == HulkWindowWidthClass.EXPANDED", text)

    def test_live_browser_has_compact_and_wide_adaptive_layouts(self) -> None:
        text = self.read(LIVE_BROWSER)
        self.assertIn("val stackedMobile = !tvLayout && adaptiveUi.screenWidthDp < 600", text)
        self.assertIn(".fillMaxHeight(.96f)", text)
        self.assertIn(".height((adaptiveUi.screenHeightDp * .31f).coerceIn(190f, 255f).dp)", text)
        self.assertIn("ChannelPane(Modifier.fillMaxWidth().weight(1f))", text)
        self.assertIn(".fillMaxWidth(if (tvLayout) .82f else .94f)", text)
        self.assertIn("ChannelPane(Modifier.weight(1f).fillMaxHeight())", text)

    def test_live_touch_swipe_uses_existing_queued_zapping_path(self) -> None:
        player = self.read(PLAYER_SCREEN)
        pro = self.read(PLAYER_PRO)
        self.assertIn("detectTapGestures(onTap = {", player)
        self.assertIn("controlsVisible = !controlsVisible", player)
        self.assertIn("detectVerticalDragGestures(", player)
        self.assertIn("verticalDrag <= -55f -> latestSwitchRelative(1)", player)
        self.assertIn("verticalDrag >= 55f -> latestSwitchRelative(-1)", player)
        self.assertIn("onSelectLiveChannel = ::queuePlayerRequestedLiveChannel", pro)
        self.assertIn("queueLiveRelative(relativeDelta)", pro)

    def test_mobile_back_closes_browser_and_picture_size_before_playback_exit(self) -> None:
        player = self.read(PLAYER_SCREEN)
        browser_before_panel = re.compile(
            r"fun handleBackAction\(\) \{\s*when \{\s*"
            r"browserVisible -> browserVisible = false\s*"
            r"finalError != null -> saveAndBack\(\)\s*"
            r"activePanel != null -> activePanel = null",
            re.DOTALL,
        )
        self.assertRegex(player, browser_before_panel)
        self.assertIn("onResize = { activePanel = PlayerPanel.RESIZE }", player)
        self.assertIn("browserVisible || activePanel != null -> null", player)


if __name__ == "__main__":
    unittest.main()
