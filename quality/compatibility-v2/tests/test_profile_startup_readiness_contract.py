from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
PROFILE_APP = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/ProfileAwareHulkApp.kt"
STARTUP_READINESS = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ProfileStartupReadiness.kt"
ROUTING_REPAIR = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ProfileRoutingRepair.kt"


class ProfileStartupReadinessContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.app = PROFILE_APP.read_text(encoding="utf-8")
        cls.readiness = STARTUP_READINESS.read_text(encoding="utf-8")
        cls.repair = ROUTING_REPAIR.read_text(encoding="utf-8")

    @staticmethod
    def section(source: str, start: str, end: str) -> str:
        start_index = source.index(start)
        return source[start_index : source.index(end, start_index)]

    def test_startup_publication_is_one_ordered_operation(self) -> None:
        effect = self.section(
            self.app,
            "LaunchedEffect(\n        authenticated,\n        activeAccountId,\n        authenticatedSessionOwner,\n        profileStartupReadiness,",
            "LaunchedEffect(\n        authenticated,\n        activeAccountId,\n        profileStateReady,",
        )

        self.assertIn("profileStartupReadiness.run(", effect)
        self.assertIn("presentation = { profileStartup },", effect)
        self.assertIn("onPublish = { presentation -> profileStartup = presentation }", effect)
        # The caller must not clear the mounted ready presentation unconditionally; the
        # continuation decision is owned by the production operation.
        self.assertNotIn("profileSnapshot = null", effect)
        self.assertNotIn("routingPreferences = null", effect)
        self.assertIn("val continuation = startedFrom.scope == scope &&", self.readiness)
        self.assertIn("startedFrom.snapshot != null &&", self.readiness)
        self.assertIn("startedFrom.routing != null", self.readiness)
        self.assertIn("if (!continuation) {", self.readiness)

        # The previous live mutable read and key-equality dependence must stay gone.
        self.assertNotIn("val snapshot = profileSnapshot\n", self.app)
        self.assertNotIn("profileSnapshot == snapshot", self.app)

        snapshot_load = self.readiness.index("loadSnapshot(accountId)")
        routing_load = self.readiness.index("loadRouting(")
        self.assertLess(snapshot_load, routing_load)
        # Both stages validate the still-current owner/account before publishing.
        self.assertEqual(
            2,
            self.readiness.count("if (!isCurrent(accountId, sessionOwner)) return Result.Abandoned"),
        )

    def test_same_scope_continuation_preserves_the_presentation(self) -> None:
        self.assertIn("data class ProfileStartupPresentation(", self.readiness)
        self.assertIn("data class ProfileStartupScope(", self.readiness)
        # A continuation publishes only after a successful revalidation, and a failed continuation
        # keeps the mounted presentation instead of surfacing the startup failure surface.
        self.assertIn("if (!continuation) onPublish(ProfileStartupPresentation.Failed)", self.readiness)
        self.assertIn("Result.Failed -> if (!continuation) onPublish", self.readiness)

    def test_obsolete_completions_cannot_replace_newer_profile_or_routing_mutations(self) -> None:
        # The startup load publishes only while the presentation it started from is still mounted.
        self.assertIn(
            "val publicationBaseline = if (continuation) startedFrom else presentation()",
            self.readiness,
        )
        self.assertIn("is Result.Ready -> if (presentation() === publicationBaseline) {", self.readiness)
        # The repair captures the live presentation and publishes only while it is still mounted.
        self.assertIn("presentation() === startedFrom", self.repair)
        # One shared production mutation rule for profile and routing mutations.
        self.assertIn(
            "internal fun ProfileStartupPresentation.withProfileSnapshot(",
            self.readiness,
        )
        self.assertIn(
            "internal fun ProfileStartupPresentation.withRoutingPreferences(",
            self.readiness,
        )
        self.assertIn("withProfileSnapshot(", self.app)
        self.assertIn("withRoutingPreferences(", self.app)

    def test_routing_repair_remains_guarded_for_profile_deletion(self) -> None:
        self.assertIn("fun repairRoutingAfterProfileDeletion(expectedAccountId: String)", self.app)
        deletion = self.section(self.app, "onDelete = { profileId ->", "onSelect = ::requestProfileSwitch,")
        self.assertIn("repairRoutingAfterProfileDeletion(expectedAccountId)", deletion)

        # The repair derives its profile ids from the live presentation and only publishes while
        # the captured owner, account scope and presentation are all still current.
        self.assertIn("isSessionOwnerCurrent(sessionOwner) &&", self.repair)
        self.assertIn("activeAccountId() == expectedAccountId &&", self.repair)
        self.assertIn("val startedFrom = presentation()", self.repair)
        self.assertIn("snapshot.profiles.mapTo(linkedSetOf(), UserProfile::id)", self.repair)
        self.assertIn("presentation = { profileStartup }", self.app)
        self.assertIn("profileStartup.withRoutingPreferences(repaired)", self.app)
        self.assertNotIn("isStillCurrent()", self.repair)

    def test_protected_profile_guards_are_preserved(self) -> None:
        self.assertIn("MISSING_PARENTAL_CODE_MESSAGE", self.app)
        self.assertIn("PARENTAL_CODE_SETUP_REQUIRED_MESSAGE", self.app)
        self.assertIn("isSessionOwnerCurrent = AuthenticatedSessionRegistry::isCurrent", self.app)
        self.assertIn("isSessionOwnerCurrent(sessionOwner) && activeAccountId() == accountId", self.readiness)


if __name__ == "__main__":
    unittest.main()
