from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
LOGIN_SCREEN = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/LoginScreen.kt"
PROFILE_PICKER = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/ProfilePickerScreen.kt"
ENTRY_PRESENTATION = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/components/EntryPresentation.kt"


class LoginProfileEntryRedesignContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.login = LOGIN_SCREEN.read_text(encoding="utf-8")
        cls.picker = PROFILE_PICKER.read_text(encoding="utf-8")
        cls.presentation = ENTRY_PRESENTATION.read_text(encoding="utf-8")

    @staticmethod
    def section(source: str, start: str, end: str | None = None) -> str:
        start_index = source.index(start)
        if end is None:
            return source[start_index:]
        return source[start_index : source.index(end, start_index)]

    def test_login_credential_order_remains_access_code_username_password(self) -> None:
        access_code = self.login.find('label = "كود الدخول"')
        username = self.login.find('label = "اسم المستخدم"')
        password = self.login.find('label = "كلمة المرور"')

        self.assertTrue(-1 < access_code < username < password)

    def test_brand_region_is_integrated_into_the_scene_not_a_framed_black_card(self) -> None:
        brand = self.section(
            self.login,
            "private fun LoginBrandRegion(",
            "private fun LoginSubscriptionAction(",
        )

        self.assertIn("BrandLogo(", brand)
        self.assertNotIn("Color.Black", brand)
        self.assertNotIn(".border(", brand)
        self.assertNotIn(".shadow(", brand)

    def test_login_field_focus_decoration_never_changes_layout(self) -> None:
        field = self.section(
            self.login,
            "private fun LoginTextField(",
            "private fun LoginOption(",
        )

        self.assertIn(".border(if (focused) 2.dp else 1.dp", field)
        self.assertIn(".padding(horizontal = 17.dp)", field)
        self.assertNotIn("if (focused) 0.dp", field)

    def test_entry_screens_share_one_presentation_language(self) -> None:
        self.assertIn("EntrySurface(", self.login)
        self.assertIn("EntryActionButton(", self.login)
        self.assertIn("EntrySurface(", self.picker)
        self.assertIn("EntryActionButton(", self.picker)

    def test_entry_screens_avoid_focus_scaling_blur_and_realtime_effects(self) -> None:
        for source in (self.login, self.picker):
            self.assertNotIn("graphicsLayer", source)
            self.assertNotIn("animateFloatAsState", source)
            self.assertNotIn(".blur(", source)
            self.assertNotIn("RenderEffect", source)

    def test_profile_card_maps_active_and_focused_as_separate_states(self) -> None:
        card = self.section(self.picker, "private fun ProfilePickerCard(")

        self.assertIn("profileCardVisualState(", card)
        self.assertIn("ProfileCardVisualState.ACTIVE_FOCUSED", card)
        self.assertIn("highlighted = focused || isActive", card)
        self.assertIn('text = "الحالي"', card)

    def test_profile_entry_options_keep_policy_and_back_ownership_in_the_screen(self) -> None:
        self.assertIn('text = "آخر مستخدم"', self.picker)
        self.assertIn("onSelectDefaultProfile(null)", self.picker)
        self.assertIn("onSelectDefaultProfile(profile.id)", self.picker)
        self.assertIn("BackHandler(enabled = showEntryOptions)", self.picker)
        self.assertIn("profilePreferencesStore.setRouting(", self.picker)

    def test_shared_presentation_owns_no_state_routing_or_focus_requesters(self) -> None:
        for forbidden in (
            "ViewModel",
            "Repository",
            "AccountSessionStore",
            "ProfileStore",
            "ProfileRoutingPreferences",
            "FocusRequester",
            "BackHandler",
            "delay(",
        ):
            self.assertNotIn(forbidden, self.presentation)


if __name__ == "__main__":
    unittest.main()
