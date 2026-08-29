from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
HULK_VIEW_MODEL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/HulkViewModel.kt"
TV_PROVIDER = (
    REPO_ROOT
    / "app/src/main/java/sa/hulksa/player/tv/TvPlatformIntegrationProvider.kt"
)


class ProfileHomeTvPlatformTransitionContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.view_model = HULK_VIEW_MODEL.read_text(encoding="utf-8")
        cls.provider = TV_PROVIDER.read_text(encoding="utf-8")

    @classmethod
    def section(cls, start: str, end: str) -> str:
        start_index = cls.view_model.index(start)
        return cls.view_model[start_index : cls.view_model.index(end, start_index)]

    def test_profile_ready_caller_only_schedules_initialization(self) -> None:
        profile_ready = self.section(
            "fun setTvPlatformProfileReady(ready: Boolean)",
            "fun beginTvPlatformProfileSwitch()",
        )
        before_launch, launched = profile_ready.split(
            "tvPlatformInitializationJob = viewModelScope.launch",
            maxsplit=1,
        )

        self.assertNotIn("activeProfileScope()", before_launch)
        self.assertNotIn("tvPlatformIntegration.withInstance", before_launch)
        self.assertIn("tvPlatformIntegration.withInstance", launched)
        self.assertIn("integration.activeProfileScope()", launched)
        self.assertNotIn("delay(", profile_ready)
        self.assertNotIn(".join()", profile_ready)

    def test_provider_dispatches_first_construction_off_ui_thread(self) -> None:
        self.assertIn(
            "private val dispatcher: CoroutineDispatcher = Dispatchers.IO",
            self.provider,
        )
        self.assertIn("suspend fun <R> withInstance", self.provider)
        self.assertIn("withContext(dispatcher)", self.provider)
        self.assertIn("block(instance.value)", self.provider)
        self.assertNotIn("fun get():", self.provider)

    def test_initialization_completion_is_generation_session_and_profile_guarded(self) -> None:
        profile_ready = self.section(
            "fun setTvPlatformProfileReady(ready: Boolean)",
            "fun beginTvPlatformProfileSwitch()",
        )

        for guard in (
            "expectedGeneration",
            "expectedSession",
            "expectedProfileId",
            "isCurrentTvPlatformWork(",
            "scope.profileId != expectedProfileId",
        ):
            self.assertIn(guard, profile_ready)
        guard_index = profile_ready.index("isCurrentTvPlatformWork(")
        ready_index = profile_ready.index("tvPlatformProfileReady = true")
        self.assertLess(guard_index, ready_index)

    def test_profile_switch_and_logout_transition_cancel_pending_initialization(self) -> None:
        transition = self.section(
            "private fun beginTvPlatformProfileTransition(",
            "private fun clearTvPlatformPrograms(",
        )

        self.assertIn("tvPlatformInitializationJob?.cancel()", transition)
        self.assertIn("tvPlatformInitializationJob = null", transition)
        self.assertIn("tvPlatformGeneration++", transition)
        self.assertIn("tvPlatformProfileReady = false", transition)
        self.assertIn("tvExpectedProfileScopeId = null", transition)

    def test_sync_resolution_and_completion_remain_stale_guarded(self) -> None:
        sync = self.section(
            "private fun scheduleTvPlatformSync(immediate: Boolean)",
            "private fun beginTvPlatformProfileTransition(",
        )

        self.assertIn("tvPlatformIntegration.withInstance", sync)
        self.assertIn("integration.activeProfileScope()", sync)
        self.assertGreaterEqual(sync.count("isCurrentTvPlatformWork("), 2)
        self.assertGreaterEqual(sync.count("tvExpectedProfileScopeId == expectedProfileScopeId"), 2)

    def test_deep_links_use_only_the_guarded_resolved_scope(self) -> None:
        deep_link = self.section(
            "private fun resolvePendingTvDeepLink()",
            "private fun openResumeEpisodeFromTvLink(",
        )

        self.assertIn("val activeProfileScopeId = tvExpectedProfileScopeId", deep_link)
        self.assertNotIn("tvPlatformIntegration", deep_link)


if __name__ == "__main__":
    unittest.main()
