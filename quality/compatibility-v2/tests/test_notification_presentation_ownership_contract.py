from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
PROFILE_AWARE = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/ProfileAwareHulkApp.kt"
HULK_APP = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/HulkApp.kt"
LOCAL_NOTIFICATIONS = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/LocalNotificationScreens.kt"


class NotificationPresentationOwnershipContractTest(unittest.TestCase):
    @staticmethod
    def read(path: Path) -> str:
        return path.read_text(encoding="utf-8")

    def test_hulk_app_owns_notification_center_rendering(self) -> None:
        text = self.read(HULK_APP)
        self.assertIn(
            "HulkScreen.NOTIFICATION_CENTER -> LocalNotificationCenterScreen(",
            text,
        )
        self.assertNotIn("HulkScreen.NOTIFICATION_CENTER -> Unit", text)
        self.assertEqual(text.count("LocalNotificationCenterScreen("), 1)

    def test_profile_aware_layer_has_no_parallel_notification_renderer(self) -> None:
        text = self.read(PROFILE_AWARE)
        self.assertNotIn("LocalNotificationCenterScreen(", text)
        self.assertNotIn(
            "state.screen == HulkScreen.NOTIFICATION_CENTER -> LocalNotificationCenterScreen(",
            text,
        )
        self.assertIn(
            "activeProfile?.kind == ProfileKind.KIDS &&\n"
            "                state.screen != HulkScreen.NOTIFICATION_CENTER -> KidsProfileExperience(",
            text,
        )
        self.assertIn("HulkApp(", text)

    def test_notification_screen_is_below_adaptive_full_screen_hulk_root(self) -> None:
        text = self.read(HULK_APP)
        adaptive = text.index("ApplyAdaptiveWindowPresentation(")
        root = text.index(".fillMaxSize()\n                .background(windowBackground)")
        screen_switch = text.index("when (state.screen)")
        notification = text.index(
            "HulkScreen.NOTIFICATION_CENTER -> LocalNotificationCenterScreen(",
        )
        self.assertLess(adaptive, root)
        self.assertLess(root, screen_switch)
        self.assertLess(screen_switch, notification)

    def test_notification_screen_keeps_single_safe_area_owner(self) -> None:
        app = self.read(HULK_APP)
        screen = self.read(LOCAL_NOTIFICATIONS)
        safe_policy_start = app.index("val applySafeDrawingInsets =")
        safe_policy_end = app.index("ApplyAdaptiveWindowPresentation(", safe_policy_start)
        safe_policy = app[safe_policy_start:safe_policy_end]
        self.assertIn("state.screen != HulkScreen.NOTIFICATION_CENTER", safe_policy)
        self.assertIn(".safeDrawingPadding()", screen)

    def test_only_hulk_app_calls_notification_center_screen(self) -> None:
        profile_aware = self.read(PROFILE_AWARE)
        hulk_app = self.read(HULK_APP)
        self.assertEqual(
            profile_aware.count("LocalNotificationCenterScreen(")
            + hulk_app.count("LocalNotificationCenterScreen("),
            1,
        )


if __name__ == "__main__":
    unittest.main()
