from __future__ import annotations

import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]
VIEW_MODEL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/HulkViewModel.kt"
PROCESS_OWNER = REPO_ROOT / "app/src/main/java/sa/hulksa/player/data/DownloadRepositoryProcessOwner.kt"
REPOSITORY = REPO_ROOT / "app/src/main/java/sa/hulksa/player/data/DownloadRepository.kt"


class DownloadSettingsPersistenceContractTest(unittest.TestCase):
    def test_ui_settings_and_resume_persistence_are_io_owned_and_generation_guarded(self) -> None:
        source = VIEW_MODEL.read_text(encoding="utf-8")

        self.assertIn("runDownloadSettingsPersistenceOffMain", source)
        self.assertIn("downloadSettingsMutationGate.writeIfCurrent(attempt)", source)
        self.assertIn("downloadSettingsMutationGate.invalidate()", source)
        self.assertIn("downloadRepository.setSettings(next, expectedAccountId, expectedProfileId)", source)
        self.assertIn("downloadRepository.resume(item.downloadId, expectedAccountId, expectedProfileId)", source)

    def test_settings_mutations_are_bound_to_the_captured_account_and_profile(self) -> None:
        source = PROCESS_OWNER.read_text(encoding="utf-8")

        self.assertIn("fun mutateForOwner(", source)
        self.assertIn("downloadOwnerContextMatches(", source)
        self.assertIn("snapshotForOwner(binding, expectedAccountId, expectedProfileId)", source)

    def test_scheduling_revision_and_existing_download_mutation_paths_remain_explicit(self) -> None:
        source = REPOSITORY.read_text(encoding="utf-8")

        self.assertIn("if (schedulingChanged)", source)
        self.assertIn("KEY_LIFECYCLE_REVISION", source)
        self.assertIn("fun enqueue(", source)
        self.assertIn("internal fun pause(", source)
        self.assertIn("internal fun removeOwner(", source)
        self.assertIn("private fun writeProgressLocked(", source)


if __name__ == "__main__":
    unittest.main()
