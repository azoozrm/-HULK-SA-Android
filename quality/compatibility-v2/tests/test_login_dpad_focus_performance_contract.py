from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
LOGIN_SCREEN = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/LoginScreen.kt"
COMPATIBILITY_INSTRUMENTATION = (
    REPO_ROOT
    / "app/src/androidTest/java/sa/hulksa/player/compatibilityv2/CompatibilityV2InstrumentationTest.kt"
)


class LoginDpadFocusPerformanceContractTest(unittest.TestCase):
    # R03 adopted one centered Login composition; the split brand region and its
    # PremiumCinematicBackground were superseded by the static poster-wall background.
    SCREEN_START = "fun LoginScreen("
    SCREEN_END = "private fun LoginPosterWallBackground("
    PANEL_START = "private fun LoginPanel("
    PANEL_END = "private fun LoginCredentialTextField("
    GROUPS_START = "private fun LoginOptionGroups("
    GROUPS_END = "private fun LoginOption("
    CONTROLLER_START = "internal class LoginKeyboardFocusTransitionController"
    CONTROLLER_END = "private enum class LoginErrorTarget"

    @classmethod
    def setUpClass(cls) -> None:
        cls.source = LOGIN_SCREEN.read_text(encoding="utf-8")
        cls.instrumentation_source = COMPATIBILITY_INSTRUMENTATION.read_text(encoding="utf-8")

    @staticmethod
    def section(source: str, start: str, end: str) -> str:
        start_index = source.index(start)
        return source[start_index : source.index(end, start_index)]

    def screen_section(self) -> str:
        return self.section(self.source, self.SCREEN_START, self.SCREEN_END)

    def panel_section(self) -> str:
        return self.section(self.source, self.PANEL_START, self.PANEL_END)

    def groups_section(self) -> str:
        return self.section(self.source, self.GROUPS_START, self.GROUPS_END)

    def test_last_card_requester_holder_is_not_compose_snapshot_state(self) -> None:
        holder = self.section(
            self.source,
            "internal class LoginCardFocusRequesterHolder(",
            self.CONTROLLER_START,
        )
        screen = self.screen_section()

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

    def test_subscription_uses_one_stable_requester_owner(self) -> None:
        screen = self.screen_section()
        panel = self.panel_section()
        subscription = panel[panel.index('text = "اشتراك جديد"') :]

        self.assertIn("onOpenSubscription = {", screen)
        self.assertIn("subscribeRequester = subscribeRequester", screen)
        self.assertIn(".focusRequester(subscribeRequester)", subscription)
        self.assertIn("up = submitRequester", subscription)
        self.assertNotIn("returnRequester", self.source)

    def test_login_panel_focus_graph_remains_unchanged(self) -> None:
        panel = self.panel_section()
        groups = self.groups_section()

        expected_panel_edges = (
            "down = usernameRequester",
            "up = accessRequester",
            "down = passwordRequester",
            "up = usernameRequester",
            "down = showPasswordRequester",
            "up = showPasswordRequester",
            "down = subscribeRequester",
            "up = submitRequester",
        )
        for edge in expected_panel_edges:
            self.assertIn(edge, panel)

        expected_group_edges = (
            "up = passwordRequester",
            "down = submitRequester",
            "left = rememberRequester",
            "right = showPasswordRequester",
        )
        for edge in expected_group_edges:
            self.assertIn(edge, groups)

        field_labels = ('كود الدخول', 'اسم المستخدم', 'كلمة المرور')
        field_positions = [panel.index(f'label = "{label}"') for label in field_labels]
        self.assertEqual(sorted(field_positions), field_positions)
        # "تسجيل الدخول" is also the heading text, so compare the last (action)
        # occurrence against the subscription action caption.
        primary_action = panel.rindex('text = "تسجيل الدخول"')
        subscription_action = panel.index('text = "اشتراك جديد"')
        self.assertLess(primary_action, subscription_action)

        # Adopted R05 anchoring: show-password is defined before remember in the
        # shared option-group owner; the physical edges are asserted there.
        self.assertLess(
            groups.index('text = "اظهار كلمة المرور"'),
            groups.index('text = "تذكر الحساب"'),
        )

    def test_compatibility_subscription_selectors_match_product_label(self) -> None:
        portrait = self.section(
            self.instrumentation_source,
            "fun phonePortraitLoginFieldsAcceptTypingWithoutCrash()",
            "fun loginFieldsRemainReachableAcrossScrollableLayouts()",
        )
        landscape = self.section(
            self.instrumentation_source,
            "fun shortLandscapePhoneCanScrollToPrimaryLoginActions()",
            "fun phoneWindowUsesTransparentEdgeToEdgeSystemBars()",
        )

        self.assertIn('resolvedVisibleBounds(By.text("اشتراك جديد"), 500L)', portrait)
        self.assertIn('val subscribeSelector = By.text("اشتراك جديد")', landscape)
        self.assertNotIn('By.text("اشتراك او تجديد")', self.instrumentation_source)
        self.assertNotIn('By.textContains("اشترك")', self.instrumentation_source)

    def test_login_field_reachability_uses_one_semantic_page_scroll(self) -> None:
        scroll_helper = self.section(
            self.instrumentation_source,
            "private fun scrollLoginPageOnce(",
            "private fun clickLoginFieldResolved(",
        )
        field_click = self.section(
            self.instrumentation_source,
            "private fun clickLoginFieldResolved(",
            "private fun resolvedVisibleBounds(",
        )
        portrait = self.section(
            self.instrumentation_source,
            "fun phonePortraitLoginFieldsAcceptTypingWithoutCrash()",
            "fun loginFieldsRemainReachableAcrossScrollableLayouts()",
        )
        reachability = self.section(
            self.instrumentation_source,
            "fun loginFieldsRemainReachableAcrossScrollableLayouts()",
            "fun phonePortraitOrientationRestoresAfterLandscapePlayback()",
        )

        self.assertIn("appWindow.findObject(By.scrollable(true))", scroll_helper)
        self.assertIn("page.scroll(direction, 1f)", scroll_helper)
        self.assertNotIn("device.swipe(", scroll_helper)
        self.assertIn("var didScroll = false", field_click)
        self.assertEqual(1, field_click.count("scrollLoginPageOnce()"))
        self.assertIn("if (!didScroll)", field_click)
        for label in ('كود الدخول', 'اسم المستخدم', 'كلمة المرور'):
            selector = f'clickLoginFieldResolved(By.text("{label}"))'
            self.assertIn(selector, portrait)
            self.assertIn(selector, reachability)

    def test_ime_subscription_reachability_uses_one_adaptive_semantic_scroll(self) -> None:
        screen = self.screen_section()
        portrait = self.section(
            self.instrumentation_source,
            "fun phonePortraitLoginFieldsAcceptTypingWithoutCrash()",
            "fun loginFieldsRemainReachableAcrossScrollableLayouts()",
        )

        # R03 moved the subscription action into the bounded centered form on every
        # window; the QR/website routing key is the remaining width switch.
        self.assertIn("val subscriptionUsesQr = isTv || maxWidth >= 600.dp", screen)
        self.assertEqual(1, screen.count("rememberScrollState()"))
        self.assertIn(
            "targetContext.resources.configuration.screenWidthDp >= 600",
            portrait,
        )
        self.assertIn("Direction.UP else Direction.DOWN", portrait)
        self.assertEqual(1, portrait.count("scrollLoginPageOnce(direction)"))
        self.assertNotIn("device.swipe(", portrait)
        self.assertNotIn("repeat(6)", portrait)

    def test_reseller_field_order_is_static_while_runtime_checks_reachability(self) -> None:
        static_validator = (REPO_ROOT / "quality/compatibility-v2/static_validate.py").read_text(encoding="utf-8")
        reachability = self.section(
            self.instrumentation_source,
            "fun loginFieldsRemainReachableAcrossScrollableLayouts()",
            "fun phonePortraitOrientationRestoresAfterLandscapePlayback()",
        )

        self.assertIn('"reseller-access-login-order"', static_validator)
        self.assertIn(
            "-1 < access_code_position < username_position < password_position",
            static_validator,
        )
        self.assertNotIn("fun loginFieldsAppearInRequiredResellerOrder()", self.instrumentation_source)
        self.assertNotIn("must appear before", reachability)

    def test_tv_login_startup_ime_is_suppressed_at_the_field_level(self) -> None:
        screen = self.screen_section()
        panel = self.panel_section()

        # R03 removed the composition-time IME inset read so TV login would not
        # churn on inset-state changes. The owner-authorized engagement-scoped
        # hide (matrix run 39095127782) still lost the startup race on tv-1080p
        # and regressed tv-logical-960x540 (matrix run 39100373599): hiding
        # after Compose's focus-triggered show can re-arm the window manager's
        # CONTROLS_CHANGED re-show. The contract now suppresses the startup
        # show request at its source instead: TV credential fields keep
        # showKeyboardOnFocus disabled until the viewer engages, and the screen
        # must not observe IME inset state anywhere.
        self.assertNotIn("WindowInsets.ime.getBottom", screen)
        self.assertIn("showKeyboardOnFocus = !isTv || viewerEngaged", screen)
        self.assertIn("var viewerEngaged by remember { mutableStateOf(false) }", screen)
        self.assertIn("onPreviewKeyEvent", screen)
        self.assertIn("onPress = { viewerEngaged = true }", screen)
        self.assertEqual(3, panel.count("showKeyboardOnFocus = showKeyboardOnFocus"))
        self.assertIn("if (isTv)", screen)
        self.assertIn(".imePadding()", screen)

    def test_character_input_state_is_read_only_in_editable_subtree_or_submit(self) -> None:
        screen = self.screen_section()
        panel = self.panel_section()
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
            "private fun LoginPosterWallBackground(",
            "private fun LoginPanel(",
        )
        self.assertNotIn("accessCode", background)
        self.assertNotIn("username", background)
        self.assertNotIn("password", background)
        self.assertIn("R.drawable.login_poster_wall_tv", background)
        self.assertIn("R.drawable.login_poster_wall_phone", background)

    def test_tv_non_text_focus_uses_edge_triggered_keyboard_transition(self) -> None:
        controller = self.section(
            self.source,
            self.CONTROLLER_START,
            self.CONTROLLER_END,
        )
        screen = self.screen_section()
        panel = self.panel_section()
        groups = self.groups_section()

        self.assertIn("private var textInputFocused = false", controller)
        self.assertIn("fun onTextInputFocused()", controller)
        self.assertIn("if (!textInputFocused) return", controller)
        self.assertIn("textInputFocused = false", controller)
        self.assertIn("hideKeyboard()", controller)
        self.assertNotIn("mutableStateOf", controller)
        self.assertIn("keyboardFocusTransitionController.onNonTextFocused(hideKeyboard)", screen)
        self.assertGreaterEqual(panel.count("onTextInputFocus()"), 3)
        self.assertGreaterEqual(
            panel.count("onNonTextFocus()") + groups.count("onNonTextFocus()"),
            5,
        )

    def test_keyboard_hide_uses_one_mechanism_per_request(self) -> None:
        screen = self.screen_section()
        hide = self.section(screen, "val hideKeyboard: () -> Unit = {", "val dismissKeyboard")

        self.assertIn("if (keyboardController != null)", hide)
        self.assertIn("keyboardController.hide()", hide)
        self.assertIn("} else {", hide)
        self.assertEqual(1, hide.count("hideSoftInputFromWindow"))
        self.assertNotIn("keyboardController?.hide()", hide)

    def test_tv_login_options_skip_focus_color_animation_hot_path(self) -> None:
        groups = self.groups_section()
        option = self.section(
            self.source,
            "private fun LoginOption(",
            "private fun resolveLoginErrorTarget(",
        )

        # R05 moved the option call sites into the shared group owner; both the
        # fitting row and the stacked fallback must still skip TV focus animation.
        self.assertGreaterEqual(groups.count("animateFocus = !isTv"), 2)
        self.assertIn("animateFocus: Boolean = true", option)
        self.assertIn("val targetBackground", option)
        self.assertIn("val targetOutline", option)
        self.assertIn("if (animateFocus)", option)

    def test_submit_preserves_authentication_values_and_remember_account(self) -> None:
        screen = self.screen_section()
        submit = self.section(screen, "val submit = {", "val openWebsite = {")

        self.assertIn("accessCodeState.value", submit)
        self.assertIn("usernameState.value.trim()", submit)
        self.assertIn("passwordState.value", submit)
        self.assertIn("rememberAccount", submit)
        self.assertEqual(1, submit.count("onLogin("))

    def test_show_password_remember_initial_focus_and_error_routing_are_preserved(self) -> None:
        screen = self.screen_section()
        panel = self.panel_section()

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
        screen = self.screen_section()

        # R03 keeps ONE centered layout: the non-TV branch owns safe drawing and
        # IME padding; the TV branch keeps its own insets without IME padding.
        self.assertEqual(1, screen.count(".imePadding()"))
        self.assertIn(".windowInsetsPadding(WindowInsets.safeDrawing)", screen)

    def test_no_polling_delay_or_debounce_workaround_was_added(self) -> None:
        screen = self.screen_section()
        controller = self.section(
            self.source,
            self.CONTROLLER_START,
            self.CONTROLLER_END,
        )
        combined = (screen + controller).lower()
        self.assertNotIn("delay(", combined)
        self.assertNotIn("debounce", combined)
        self.assertNotIn("polling", combined)
        self.assertNotIn("timer", combined)

    def test_login_does_not_add_new_disk_or_network_work_to_input_hot_path(self) -> None:
        screen = self.screen_section()
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
