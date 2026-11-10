from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
HULK_APP = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/HulkApp.kt"
MAIN_SHELL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
HOME_HISTORY = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/HomeHistoryRemoval.kt"
MOBILE_ACTIONS = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/MobileMainTopActions.kt"
HOME_FACTS = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/HomeHeroFacts.kt"


class HomeR05WiringContractTest(unittest.TestCase):
    @classmethod
    def section(cls, path: Path, start: str, end: str) -> str:
        text = path.read_text(encoding="utf-8")
        begin = text.index(start)
        return text[begin : text.index(end, begin)]

    def test_notification_opening_does_not_overwrite_home_content_memory(self) -> None:
        opener = self.section(HULK_APP, "val openNotificationCenter:", "BackHandler(")
        self.assertIn("homeNotificationReturnOwner.request()", opener)
        self.assertNotIn(
            'navigationMemory.save(MainDestination.HOME, "", 0, "hero", 0)',
            opener,
        )

    def test_notification_return_owner_is_scoped_and_cancels_on_focus(self) -> None:
        source = HOME_HISTORY.read_text(encoding="utf-8")
        self.assertIn("internal class HomeNotificationReturnOwner", source)
        self.assertIn("initialEntryIntent: Boolean", source)
        self.assertIn("onFocusEvent: (() -> Unit)?", source)
        self.assertIn("onFocusEvent?.invoke()", source)
        self.assertIn("competingRestoreSuppressed = !initialEntryIntent", source)
        self.assertIn("!competingRestoreSuppressed", source)
        self.assertIn(
            "val homeNotificationReturnOwner = remember(navigationMemory, state.account)",
            HULK_APP.read_text(encoding="utf-8"),
        )

    def test_bell_requester_drives_both_header_hosts(self) -> None:
        shell = MAIN_SHELL.read_text(encoding="utf-8")
        self.assertIn("homeNotificationBellRequester.requestFocus()", shell)
        self.assertIn("val homeNotificationBellModifier = Modifier", shell)
        self.assertIn(".focusRequester(homeNotificationBellRequester)", shell)
        self.assertIn(".onPlaced { homeNotificationBellPlacementRevision += 1 }", shell)
        self.assertEqual(3, shell.count("notificationBellModifier = homeNotificationBellModifier"))
        self.assertIn("notificationBellModifier: Modifier = Modifier", MOBILE_ACTIONS.read_text(encoding="utf-8"))
        self.assertIn("modifier = notificationBellModifier", MOBILE_ACTIONS.read_text(encoding="utf-8"))

    def test_section_alignment_is_heading_text_only(self) -> None:
        shell = MAIN_SHELL.read_text(encoding="utf-8")
        self.assertIn("fun homeSectionHeadingRightInsetDp", shell)
        self.assertIn(
            "modifier = modifier.absolutePadding(right = homeSectionHeadingRightInsetDp(isTv))",
            shell,
        )
        for heading in (
            'HomeSectionHeadingText("التنزيلات الجارية", isTv)',
            "HomeSectionHeadingText(title, isTv)",
            'HomeSectionHeadingText("اخر قناة شاهدتها", isTv)',
        ):
            self.assertIn(heading, shell)
        self.assertIn("Box(Modifier.fillMaxWidth().padding(horizontal = if (isTv) TV_PAGE_GUTTER else 0.dp))", shell)

    def test_title_style_preserves_font_and_exact_two_line_slot(self) -> None:
        shell = MAIN_SHELL.read_text(encoding="utf-8")
        policy = (REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/HomeHeroCopyPolicy.kt").read_text(encoding="utf-8")
        self.assertIn("homeHeroTitleTextStyle(LocalTextStyle.current, metrics, title)", shell)
        self.assertIn("internal fun homeHeroTitleTextStyle(", policy)
        self.assertIn("title: String,", policy)
        self.assertIn("homeHeroTitleDirection(title, base.textDirection)", policy)
        self.assertIn("homeHeroTitleIsPureStrongLtr(title)", policy)
        self.assertIn("Character.getDirectionality(codePoint)", policy)
        self.assertIn("TextDirection.Content", policy)
        self.assertIn("platformStyle = PlatformTextStyle(includeFontPadding = false)", policy)
        self.assertIn("fontSize = metrics.titleFontSize", policy)
        self.assertIn("lineHeight = metrics.titleLineHeight", policy)
        self.assertIn("fontWeight = FontWeight.SemiBold", policy)

    def test_genre_and_facts_share_the_same_visible_right_edge(self) -> None:
        facts = HOME_FACTS.read_text(encoding="utf-8")
        self.assertIn(
            "val genreRightInsetPx = with(density) { metrics.factsGroupHorizontalPaddingDp.roundToPx() }",
            facts,
        )
        self.assertIn("val contentWidth = (width - genreRightInsetPx).coerceAtLeast(0)", facts)
        self.assertIn("availableWidthPx = contentWidth", facts)
        self.assertIn("var x = width - genreRightInsetPx", facts)


if __name__ == "__main__":
    unittest.main()
