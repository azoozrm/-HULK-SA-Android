from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
PROFILE_APP = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/ProfileAwareHulkApp.kt"
PROFILE_POLICY = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/ProfilePickerPolicy.kt"


class ProfilePickerParentalReadinessContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.app = PROFILE_APP.read_text(encoding="utf-8")
        cls.policy = PROFILE_POLICY.read_text(encoding="utf-8")

    @staticmethod
    def section(source: str, start: str, end: str) -> str:
        start_index = source.index(start)
        return source[start_index : source.index(end, start_index)]

    def test_profile_selection_is_not_globally_blocked_by_parental_migration(self) -> None:
        request = self.section(
            self.app,
            "fun requestProfileSwitch(profile: UserProfile)",
            "fun requestProfileManagement(startCreating: Boolean)",
        )
        self.assertIn("profileSelectionRequiresResolvedParentalState(", request)
        self.assertIn("!parentalCodeStateReady && parentalStateRequired", request)
        self.assertNotIn("switching ||\n            !parentalCodeStateReady ||", request)

    def test_policy_keeps_parental_sensitive_transitions_fail_closed(self) -> None:
        self.assertIn("currentProfileId == targetProfileId", self.policy)
        self.assertIn("currentProfileKind == ProfileKind.KIDS", self.policy)
        self.assertIn("targetProfileKind == ProfileKind.STANDARD", self.policy)
        self.assertIn("targetProfileKind == ProfileKind.KIDS", self.policy)
        self.assertIn("!parentalCodeAvailable", self.policy)


if __name__ == "__main__":
    unittest.main()
