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
            "private enum class LoginComposition",
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
            "private fun LoginTextField(",
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


if __name__ == "__main__":
    unittest.main()
