from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
DETAILS = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/DetailsProTvPolishScreens.kt"
SERIES_UI = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/SeriesApprovedUi.kt"
MOBILE_DETAILS = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/DetailsProScreens.kt"
COMPONENTS = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt"
MOVIES_UI = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MoviesApprovedUi.kt"


class SeriesDetailsLazyFocusContractTest(unittest.TestCase):
    @staticmethod
    def source() -> str:
        return DETAILS.read_text(encoding="utf-8")

    @classmethod
    def section(cls, start: str, end: str) -> str:
        text = cls.source()
        start_index = text.index(start)
        return text[start_index : text.index(end, start_index)]

    @classmethod
    def series_ui_section(cls, start: str, end: str) -> str:
        text = SERIES_UI.read_text(encoding="utf-8")
        start_index = text.index(start)
        return text[start_index : text.index(end, start_index)]

    def test_offscreen_episode_target_scrolls_before_focus_after_real_lazy_layout_signal(self) -> None:
        series = self.section(
            "private fun SeriesDetailsProTvPolished(",
            "@Composable\nprivate fun DetailsTvEpisodeUnit(",
        )
        effect_start = series.index("LaunchedEffect(pendingLazyFocus, listState)")
        effect_end = series.index("LaunchedEffect(series.id, targetEpisode?.id)", effect_start)
        effect = series[effect_start:effect_end]

        scroll = effect.index("listState.scrollToItem(sourceIndex + 1)")
        signal = effect.index("snapshotFlow", scroll)
        visibility = effect.index("it.key == request.targetItemKey", signal)
        wait = effect.index(".first { it }", visibility)
        focus = effect.index("request.targetRequester.requestFocus()", wait)

        self.assertLess(scroll, signal)
        self.assertLess(signal, visibility)
        self.assertLess(visibility, wait)
        self.assertLess(wait, focus)
        self.assertNotIn("delay(", effect)
        self.assertNotIn("while (", effect)

    def test_episode_and_related_lazy_down_targets_keep_deterministic_column_mapping(self) -> None:
        series = self.section(
            "private fun SeriesDetailsProTvPolished(",
            "@Composable\nprivate fun DetailsTvEpisodeUnit(",
        )
        self.assertIn('val sourceRowKey = "series_tv_polished_episode_row_${selectedSeason}_$rowIndex"', series)
        self.assertIn("val nextColumn = columnIndex.coerceAtMost(nextEpisodes.lastIndex)", series)
        self.assertIn('"series_tv_polished_episode_row_${selectedSeason}_${rowIndex + 1}"', series)
        self.assertIn('"series_tv_polished_related"', series)
        self.assertIn("val lazyDownRequest = if (downTarget != null && lazyDownItemKey != null) {", series)
        self.assertIn("DetailsTvLazyFocusRequest(", series)
        self.assertIn("sourceItemKey = sourceRowKey", series)
        self.assertIn("targetItemKey = lazyDownItemKey", series)
        self.assertIn("targetRequester = downTarget", series)
        self.assertIn("pendingLazyFocus = request", series)
        self.assertIn("downCard = null", series)

    def test_boxed_episode_action_delegates_lazy_down_and_keeps_touch_activation(self) -> None:
        episode = self.section(
            "private fun DetailsTvEpisodeUnit(",
            "@Composable\nprivate fun DetailsTvInfoItem(",
        )
        card = self.series_ui_section(
            "internal fun EpisodeBoxedCard(",
            "private fun EpisodeArtworkBadge(",
        )

        # The TV episode card is now the shared boxed card: it keeps exactly one lazy move-down
        # binding and one pending-move cancel binding, wired through the card's focus links.
        self.assertGreaterEqual(episode.count("EpisodeBoxedCard("), 1)
        self.assertGreaterEqual(episode.count("onMoveDown = onMoveDown"), 1)
        self.assertGreaterEqual(episode.count("onCancelPendingMove = onCancelPendingMove"), 1)
        self.assertIn("EpisodeFocusLinks(", episode)
        self.assertIn("cancelRequester", episode)

        # The shared card owns the lazy-down override and the touch activation exactly once.
        self.assertIn("if (onMoveDown != null) {", card)
        self.assertIn("onMoveDown()", card)
        self.assertIn("onCancelPendingMove()", card)
        self.assertIn(".clickable(role = Role.Button, onClick = onPlay)", card)
        self.assertIn(".onPreviewKeyEvent { event ->", card)

    def test_boxed_episode_card_keeps_full_width_download_and_stable_footer(self) -> None:
        card = self.series_ui_section(
            "internal fun EpisodeBoxedCard(",
            "private fun EpisodeArtworkBadge(",
        )
        self.assertIn("text = downloadCaption", card)
        self.assertIn(".fillMaxWidth()", card)
        self.assertIn("minLines = 1", card)
        self.assertIn("statusLabel.orEmpty()", card)
        self.assertIn("identityLabel", card)


if __name__ == "__main__":
    unittest.main()


class SeriesR05PlacementContractTest(unittest.TestCase):
    @staticmethod
    def series_ui_section(start: str, end: str) -> str:
        text = SERIES_UI.read_text(encoding="utf-8")
        start_index = text.index(start)
        return text[start_index : text.index(end, start_index)]

    def test_series_hero_metadata_order_and_icon_removal(self) -> None:
        hero = self.series_ui_section(
            "internal fun SeriesDetailsHeroMetadataRow(",
            "internal fun SeriesDetailsActionsBar(",
        )
        genre = hero.index("DetailsHeroMetadataEntry(it)")
        quality = hero.index("quality = true")
        season = hero.index("seasonCount = it, seasonIcon = false")
        episode = hero.index("episodeCount = it")
        rating = hero.index("rating = true")
        self.assertLess(genre, quality)
        self.assertLess(quality, season)
        self.assertLess(season, episode)
        self.assertLess(episode, rating)

    def test_boxed_episode_download_down_reaches_cancel_and_focus_recovers(self) -> None:
        card = self.series_ui_section(
            "internal fun EpisodeBoxedCard(",
            "private fun EpisodeArtworkBadge(",
        )
        self.assertIn("val cancelVisible = cancelCaption != null && cancelRequester != null", card)
        self.assertIn("down = if (cancelVisible) {", card)
        self.assertIn("cancelRequester ?: FocusRequester.Cancel", card)
        self.assertIn("up = cancelLinks.up ?: actionRequester", card)
        self.assertIn("down = cancelLinks.down ?: FocusRequester.Cancel", card)
        self.assertIn("LaunchedEffect(cancelVisible)", card)
        self.assertIn("if (!cancelVisible && cancelRestorePending) {", card)
        self.assertIn("cancelRestorePending = true", card)
        self.assertIn("runCatching { actionRequester.requestFocus() }", card)

    def test_episode_status_area_matches_recent_time_treatment(self) -> None:
        card = self.series_ui_section(
            "internal fun EpisodeBoxedCard(",
            "private fun EpisodeArtworkBadge(",
        )
        self.assertIn("text = statusLabel.orEmpty()", card)
        self.assertIn("RecentTimeRow(", card)
        self.assertIn("elapsedText = statusElapsedText.orEmpty()", card)
        self.assertIn("totalText = statusTotalText,", card)
        self.assertIn(
            "valueColor = if (statusElapsedText.isNullOrBlank()) Color.Transparent else colors.text",
            card,
        )

    def test_episode_resume_caption_uses_exact_approved_wording(self) -> None:
        mobile = MOBILE_DETAILS.read_text(encoding="utf-8")
        tv = DETAILS.read_text(encoding="utf-8")
        for wrapper in (mobile, tv):
            self.assertIn('progress != null && historyEntry != null -> "اكمل المشاهدة"', wrapper)
            self.assertIn('completed -> "تمت المشاهدة"', wrapper)
            self.assertNotIn("استكمال", wrapper)

    def test_shared_recent_time_row_keeps_gold_icon_left_of_ltr_time(self) -> None:
        components = COMPONENTS.read_text(encoding="utf-8")
        start = components.index("internal fun RecentTimeRow(")
        row = components[start : components.index("\n}\n", start)]
        self.assertIn("CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)", row)
        icon = row.index("imageVector = Icons.Rounded.Schedule")
        elapsed = row.index("text = elapsedText")
        separator = row.index('text = " / "')
        total = row.index("text = totalText")
        self.assertLess(icon, elapsed)
        self.assertLess(elapsed, separator)
        self.assertLess(separator, total)
        self.assertIn("BoxedHistoryCard(", components)
        history_start = components.index("fun BoxedHistoryCard(")
        history = components[history_start:]
        self.assertIn("RecentTimeRow(", history)
        self.assertIn("movieRecentElapsedText(entry.positionMs)", history)
        self.assertIn("movieRecentTotalText(entry.durationMs)", history)

    def test_info_tab_season_field_is_text_only(self) -> None:
        movies = MOVIES_UI.read_text(encoding="utf-8")
        start = movies.index("internal fun DetailsInfoGrid(")
        grid = movies[start : movies.index("internal fun MovieDetailsInfoGrid(", start)]
        self.assertIn('word = "موسم"', grid)
        self.assertIn("SeriesEpisodeCountInline(", grid)
        self.assertNotIn("SeriesSeasonCountInline(", grid)
        self.assertNotIn("iconSizeDp", grid)
        self.assertNotIn("iconTint", grid)
        series = SERIES_UI.read_text(encoding="utf-8")
        self.assertIn('DetailsInfoCell(label = "المواسم", seasonCount = it)', series)

    def test_current_episode_box_is_removed_from_episode_headers(self) -> None:
        tv = DETAILS.read_text(encoding="utf-8")
        mobile = (REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/DetailsProScreens.kt").read_text(
            encoding="utf-8"
        )
        self.assertNotIn("الحلقة الحالية", tv)
        self.assertNotIn("الحلقة الحالية", mobile)


class SeriesR06ReviewContractTest(unittest.TestCase):
    @staticmethod
    def count(text: str, needle: str) -> int:
        return text.count(needle)

    def test_series_player_lock_feeds_the_authoritative_foreground_signal(self) -> None:
        player = (REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt").read_text(
            encoding="utf-8"
        )
        start = player.index("val vodForegroundModalActive = playerVodForegroundDecisionActive(")
        signal = player[start : player.index(")\n", start)]
        self.assertIn("controlsLocked = controlsLocked,", signal)
        self.assertIn("nextCountdownActive = nextCountdown >= 0,", signal)
        self.assertIn("resumePromptVisible = resumePromptVisible,", signal)
        self.assertIn("errorModalActive = movieErrorActive,", signal)
        self.assertIn("unlockVisible = unlockVisible,", signal)
        self.assertNotIn("!request.isLive &&", signal)

    def test_series_details_retry_disappearance_restores_focus_on_both_series_routes(self) -> None:
        for route in (MOBILE_DETAILS, DETAILS):
            text = route.read_text(encoding="utf-8")
            self.assertEqual(2, self.count(text, "LaunchedEffect(errorMessage)"))
            # Consuming the flag alone never established cancellation; the restoration now carries
            # a generation token and is invalidated by a new error, selection, user key or Retry
            # re-focus.
            self.assertEqual(1, self.count(text, "detailsErrorRetryFocused = false"))
            self.assertIn("val focusRestoration = remember(series.id) { SeriesFocusRestorationOwner() }", text)
            self.assertEqual(4, self.count(text, "focusRestoration.invalidate()"))
            self.assertIn("if (focused) focusRestoration.invalidate()", text)
            # The series consumer is the second effect (the movie route keeps its own).
            first_effect = text.index("LaunchedEffect(errorMessage)")
            effect_start = text.index("LaunchedEffect(errorMessage)", first_effect + 1)
            block = text[effect_start : text.index("\n    }\n", effect_start)]
            self.assertIn("if (errorMessage != null) {", block)
            self.assertIn("focusRestoration.invalidate()", block)
            self.assertIn("val token = focusRestoration.begin()", block)
            self.assertIn("seriesDetailsRestoreFocus(", block)
            self.assertIn("isCurrent = { focusRestoration.isCurrent(token) },", block)
            # The reveal awaits real layout before requesting focus, and the focus chain uses the
            # actual granted-Boolean result (never `.isSuccess`, which hides a normal false).
            self.assertIn("snapshotFlow {", block)
            self.assertIn(".first { it }", block)
            self.assertIn("withFrameNanos { }", block)
            self.assertIn("{ tabRequesters.getValue(selectedTab).requestFocus() }", block)
            self.assertIn("{ playRequester.requestFocus() }", block)
            self.assertIn("{ heroReturnRequester.requestFocus() }", block)
            self.assertNotIn(".isSuccess", block)
            self.assertNotIn("delay(", block)

    def test_series_details_restore_owner_binds_request_lifetime(self) -> None:
        text = SERIES_UI.read_text(encoding="utf-8")
        owner = text[text.index("internal class SeriesFocusRestorationOwner"):]
        owner = owner[: owner.index("internal suspend fun seriesDetailsRestoreFocus(")]
        self.assertIn("private var generation = 0", owner)
        self.assertIn("fun begin(): Int {", owner)
        self.assertIn("generation += 1", owner)
        self.assertIn("fun invalidate() {", owner)
        self.assertIn("fun isCurrent(token: Int): Boolean = token == generation", owner)
        self.assertIn("internal fun seriesDetailsNoteNewFocusOwner(owner: SeriesFocusRestorationOwner) {", text)
        flow = text[text.index("internal suspend fun seriesDetailsRestoreFocus("):]
        flow = flow[: flow.index("\n}\n") + 3]
        self.assertIn("reveal()", flow)
        self.assertIn("if (!isCurrent()) return null", flow)
        self.assertIn("return seriesDetailsFirstGrantedFocus(requests)", flow)

    def test_series_hero_focus_transfer_invalidates_pending_restoration(self) -> None:
        for route in (MOBILE_DETAILS, DETAILS):
            text = route.read_text(encoding="utf-8")
            self.assertEqual(1, self.count(text, "seriesDetailsNoteNewFocusOwner(focusRestoration)"))
            marker = text.index("seriesDetailsNoteNewFocusOwner(focusRestoration)")
            block = text[max(0, marker - 200) : marker + 120]
            self.assertIn("onActionFocused = {", block)
            self.assertIn("heroReturnRequester = it", block)

    def test_series_details_first_up_hands_episodes_off_without_selection_reveal(self) -> None:
        text = SERIES_UI.read_text(encoding="utf-8")
        helper = text[text.index("internal fun seriesDetailsTabUpHandsOffToWatchAction("):]
        helper = helper[: helper.index("internal fun seriesDetailsTabSelectionNeedsReveal(")]
        self.assertIn("tab == SeriesDetailsTab.EPISODES ||", helper)
        self.assertIn("tab == SeriesDetailsTab.INFORMATION ||", helper)
        self.assertIn("tab == SeriesDetailsTab.RELATED", helper)
        reveal = text[text.index("internal fun seriesDetailsTabSelectionNeedsReveal("):]
        reveal = reveal[: reveal.index("internal fun seriesDetailsFirstGrantedFocus(")]
        self.assertIn("tab == SeriesDetailsTab.INFORMATION ||", reveal)
        self.assertIn("tab == SeriesDetailsTab.RELATED", reveal)
        self.assertNotIn("EPISODES", reveal)
        for route in (MOBILE_DETAILS, DETAILS):
            route_text = route.read_text(encoding="utf-8")
            # Selection keeps its own narrower reveal; the UP handoff owns the Episodes case.
            self.assertIn("if (seriesDetailsTabSelectionNeedsReveal(tab)) {", route_text)
            up_marker = route_text.index("seriesDetailsTabUpHandsOffToWatchAction(selectedTab)")
            up_block = route_text[up_marker : route_text.index("} else {", up_marker)]
            self.assertIn("val token = focusRestoration.begin()", up_block)
            self.assertIn("seriesDetailsRestoreFocus(", up_block)
            self.assertIn("isCurrent = { focusRestoration.isCurrent(token) },", up_block)
            self.assertIn("{ playRequester.requestFocus() }", up_block)
            # The first-UP handoff targets the primary action or the back button only; an
            # unrelated hero action (favorite/notification) is never a fallback.
            self.assertNotIn("heroReturnRequester.requestFocus()", up_block)
            self.assertNotIn("delay(", up_block)
