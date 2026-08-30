from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
LOGIN_SCREEN = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/LoginScreen.kt"


class LoginDpadFocusPerformanceContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = LOGIN_SCREEN.read_text(encoding="utf-8")

    @staticmethod
    def section(source: str, start: str, end: str) -> str:
        start_index = source.index(start)
        return source[start_index : source.index(end, start_index)]

    def test_last_card_requester_holder_is_not_compose_snapshot_state(self) -> None:
        holder = self.section(
            self.source,
            "internal class LoginCardFocusRequesterHolder(",
            "internal class LoginKeyboardFocusTransitionController",
        )
        screen = self.section(
            self.source,
            "fun LoginScreen(",
            "private fun PremiumCinematicBackground(",
        )

        self.assertIn("private var currentRequester = initialRequester", holder)
        self.assertIn("fun update(requester: FocusRequester)", holder)
        self.assertIn("fun current(): FocusRequester = currentRequester", holder)
        self.assertNotIn("mutableStateOf", holder)
        self.assertNotIn("MutableState", holder)
        self.assertIn("val lastCardFocusRequester = remember(submitRequester)", screen)
        self.assertIn("LoginCardFocusRequesterHolder(submitRequester)", screen)
        self.assertIn(
            "onCardFocusChanged = lastCardFocusRequester::update",
            screen,
        )
        self.assertNotIn(
            "var lastCardFocusRequester by remember { mutableStateOf(submitRequester) }",
            screen,
        )
        self.assertNotIn("{ lastCardFocusRequester = it }", screen)

    def test_subscription_resolves_latest_requester_when_focus_returns(self) -> None:
        screen = self.section(
            self.source,
            "fun LoginScreen(",
            "private fun PremiumCinematicBackground(",
        )
        subscription = self.section(
            self.source,
            "private fun LoginSubscriptionAction(",
            "private fun LoginPanel(",
        )

        self.assertEqual(
            2,
            screen.count("returnRequester = lastCardFocusRequester::current"),
        )
        self.assertIn("returnRequester: () -> FocusRequester", subscription)
        self.assertIn("right = returnRequester()", subscription)
        self.assertNotIn("returnRequester: FocusRequester", subscription)

    def test_login_panel_focus_graph_remains_unchanged(self) -> None:
        panel = self.section(
            self.source,
            "private fun LoginPanel(",
            "private fun LoginCredentialTextField(",
        )

        expected_edges = (
            "down = usernameRequester",
            "up = accessRequester",
            "down = passwordRequester",
            "up = usernameRequester",
            "down = rememberRequester",
            "up = passwordRequester",
            "down = showPasswordRequester",
            "up = rememberRequester",
            "down = submitRequester",
            "up = showPasswordRequester",
            "down = if (showSecondaryAction) subscribeRequester else FocusRequester.Cancel",
            "up = submitRequester",
        )
        for edge in expected_edges:
            self.assertIn(edge, panel)

        labels = (
            'label = "كود الدخول"',
            'label = "اسم المستخدم"',
            'label = "كلمة المرور"',
            'text = "تذكر الحساب"',
            'text = "اظهر كلمة المرور"',
            'text = "دخول الى HULK"',
            'text = "اشتراك جديد"',
        )
        positions = [panel.index(label) for label in labels]
        self.assertEqual(sorted(positions), positions)

    def test_tv_login_does_not_observe_ime_inset_state(self) -> None:
        screen = self.section(
            self.source,
            "fun LoginScreen(",
            "private fun PremiumCinematicBackground(",
        )
        ime_read = "WindowInsets.ime.getBottom(density) > 0"
        self.assertEqual(1, screen.count(ime_read))
        ime_index = screen.index(ime_read)
        guard = screen[screen.rfind("val imeVisible", 0, ime_index) : ime_index]
        self.assertIn("if (isTv)", guard)
        self.assertIn("false", guard)
        self.assertIn("} else {", guard)

    def test_character_input_state_is_read_only_in_editable_subtree_or_submit(self) -> None:
        screen = self.section(
            self.source,
            "fun LoginScreen(",
            "private fun PremiumCinematicBackground(",
        )
        panel = self.section(
            self.source,
            "private fun LoginPanel(",
            "private fun LoginCredentialTextField(",
        )
        credential_field = self.section(
            self.source,
            "private fun LoginCredentialTextField(",
            "private fun LoginTextField(",
        )

        for state_name in ("accessCodeState", "usernameState", "passwordState"):
            self.assertIn(f"val {state_name} =", screen)
            self.assertIn(f"{state_name} = {state_name}", screen)
            self.assertIn(f"state = {state_name}", panel)
        self.assertNotIn("var accessCode by", screen)
        self.assertNotIn("var username by", screen)
        self.assertNotIn("var password by", screen)
        self.assertIn("value = state.value", credential_field)
        self.assertIn("onValueChange = { state.value = it }", credential_field)

    def test_static_cinematic_visuals_do_not_depend_on_credential_values(self) -> None:
        background = self.section(
            self.source,
            "private fun PremiumCinematicBackground(",
            "private fun LoginBrandRegion(",
        )
        brand = self.section(
            self.source,
            "private fun LoginBrandRegion(",
            "private fun LoginSubscriptionAction(",
        )
        for section in (background, brand):
            self.assertNotIn("accessCode", section)
            self.assertNotIn("username", section)
            self.assertNotIn("password", section)

    def test_tv_non_text_focus_uses_edge_triggered_keyboard_transition(self) -> None:
        controller = self.section(
            self.source,
            "internal class LoginKeyboardFocusTransitionController",
            "private enum class LoginComposition",
        )
        screen = self.section(
            self.source,
            "fun LoginScreen(",
            "private fun PremiumCinematicBackground(",
        )
        panel = self.section(
            self.source,
            "private fun LoginPanel(",
            "private fun LoginCredentialTextField(",
        )

        self.assertIn("private var textInputFocused = false", controller)
        self.assertIn("fun onTextInputFocused()", controller)
        self.assertIn("if (!textInputFocused) return", controller)
        self.assertIn("textInputFocused = false", controller)
        self.assertIn("hideKeyboard()", controller)
        self.assertNotIn("mutableStateOf", controller)
        self.assertIn("keyboardFocusTransitionController.onNonTextFocused(hideKeyboard)", screen)
        self.assertGreaterEqual(panel.count("onTextInputFocus()"), 3)
        self.assertGreaterEqual(panel.count("onNonTextFocus()"), 3)

    def test_submit_preserves_authentication_values_and_remember_account(self) -> None:
        screen = self.section(
            self.source,
            "fun LoginScreen(",
            "private fun PremiumCinematicBackground(",
        )
        submit = self.section(screen, "val submit = {", "val openWebsite = {")

        self.assertIn("accessCodeState.value", submit)
        self.assertIn("usernameState.value.trim()", submit)
        self.assertIn("passwordState.value", submit)
        self.assertIn("rememberAccount", submit)
        self.assertEqual(1, submit.count("onLogin("))

    def test_show_password_remember_initial_focus_and_error_routing_are_preserved(self) -> None:
        screen = self.section(
            self.source,
            "fun LoginScreen(",
            "private fun PremiumCinematicBackground(",
        )
        panel = self.section(
            self.source,
            "private fun LoginPanel(",
            "private fun LoginCredentialTextField(",
        )

        self.assertIn("var showPassword by rememberSaveable", screen)
        self.assertIn("var rememberAccount by rememberSaveable", screen)
        self.assertIn("onShowPasswordChange = { showPassword = !showPassword }", screen)
        self.assertIn("onRememberChange = { rememberAccount = !rememberAccount }", screen)
        self.assertIn("tvInitialFocusRequester.requestFocus()", screen)
        self.assertIn("initialFocusRequester = if (isTv) tvInitialFocusRequester else null", screen)
        self.assertIn("resolveLoginErrorTarget(errorMessage, usernameState.value, passwordState.value)", panel)
        self.assertIn("accessRequester.requestFocus()", panel)
        self.assertIn("usernameRequester.requestFocus()", panel)
        self.assertIn("passwordRequester.requestFocus()", panel)

    def test_mobile_ime_behavior_remains_enabled(self) -> None:
        screen = self.section(
            self.source,
            "fun LoginScreen(",
            "private fun PremiumCinematicBackground(",
        )
        self.assertIn(".then(if (isTv) Modifier else Modifier.imePadding())", screen)
        self.assertGreaterEqual(screen.count(".imePadding()"), 2)
        self.assertIn(".windowInsetsPadding(WindowInsets.safeDrawing)", screen)

    def test_no_polling_delay_or_debounce_workaround_was_added(self) -> None:
        screen = self.section(
            self.source,
            "fun LoginScreen(",
            "private fun PremiumCinematicBackground(",
        )
        controller = self.section(
            self.source,
            "internal class LoginKeyboardFocusTransitionController",
            "private enum class LoginComposition",
        )
        combined = (screen + controller).lower()
        self.assertNotIn("delay(", combined)
        self.assertNotIn("debounce", combined)
        self.assertNotIn("polling", combined)
        self.assertNotIn("timer", combined)

    def test_login_does_not_add_new_disk_or_network_work_to_input_hot_path(self) -> None:
        screen = self.section(
            self.source,
            "fun LoginScreen(",
            "private fun PremiumCinematicBackground(",
        )
        credential_field = self.section(
            self.source,
            "private fun LoginCredentialTextField(",
            "private fun LoginTextField(",
        )
        self.assertEqual(1, screen.count("AccountSessionStore"))
        self.assertIn("val persistedAccessCode = remember(view.context)", screen)
        self.assertNotIn("AccountSessionStore", credential_field)
        self.assertNotIn("Repository", credential_field)
        self.assertNotIn("http", credential_field.lower())


if __name__ == "__main__":
    unittest.main()
