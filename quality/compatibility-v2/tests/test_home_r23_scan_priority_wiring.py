from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
VIEW_MODEL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/HulkViewModel.kt"
GATE = REPO_ROOT / "app/src/main/java/sa/hulksa/player/StartupScanPriorityGate.kt"


class HomeR23StartupScanPriorityWiringTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.vm = VIEW_MODEL.read_text(encoding="utf-8")
        cls.gate = GATE.read_text(encoding="utf-8")

    def test_gate_owns_only_pending_types_and_never_releases_stale_owners(self) -> None:
        self.assertIn("internal class StartupScanPriorityGate", self.gate)
        self.assertIn("this.owner === owner", self.gate)
        self.assertIn("if (!pending.remove(type)) return false", self.gate)
        self.assertIn("if (pending.isEmpty())", self.gate)
        self.assertIn("fun invalidate(owner: Any? = null)", self.gate)

    def test_only_automatic_triggers_are_gated(self) -> None:
        self.assertIn("internal val NotificationScanTrigger.isAutomaticStartupScan", self.gate)
        for automatic in ("APP_START", "APP_RESUME", "PROFILE_SWITCH", "LIBRARY_REFRESH"):
            self.assertIn("NotificationScanTrigger." + automatic + ",", self.gate)
        for manual in ("MANUAL_REFRESH", "NOTIFICATION_CENTER", "MASTER_REENABLE"):
            self.assertIn("NotificationScanTrigger." + manual + ",", self.gate)
        self.assertIn("-> false", self.gate)

    def test_scan_admission_checks_the_gate_and_preserves_intent(self) -> None:
        scan_start = self.vm.index("private fun scanSubscribedSeries(")
        scan_body = self.vm[scan_start : self.vm.index("private fun drainPendingNotificationScan()")]
        self.assertIn(
            "if (trigger.isAutomaticStartupScan && startupScanPriorityGate.isHeld(activeSession))",
            scan_body,
        )
        gate_branch = scan_body.split("startupScanPriorityGate.isHeld(activeSession)")[1][:400]
        self.assertIn("enqueueNotificationScanTrigger(trigger)", gate_branch)
        self.assertIn("return", gate_branch)

    def test_catalog_terminal_callbacks_release_the_gate(self) -> None:
        self.assertEqual(
            2,
            self.vm.count("settleStartupScanPriority(session, type)"),
            "onLoaded and onFailure must both settle the startup round",
        )
        self.assertIn("private fun settleStartupScanPriority(", self.vm)
        self.assertIn('ProviderGateState.RELEASED, "CATALOG_SETTLED", 0', self.vm)

    def test_no_fetch_or_fresh_decision_settles_without_network(self) -> None:
        network = self.vm.index("private fun requestCatalogNetwork(")
        body = self.vm[network : network + 2200]
        self.assertIn("if (type !in catalogRequestOwner.loadingTypes())", body)
        self.assertIn("settleStartupScanPriority(activeSession, type)", body)

    def test_round_starts_on_session_publication_and_profile_switch(self) -> None:
        publish = self.vm.index("ProviderRequestTrace.bindSession()")
        self.assertIn("beginStartupScanPriority(authenticated)", self.vm[publish : publish + 200])
        self.assertIn("resetCatalogRestoreForProfileChange()", self.vm)
        profile_block = self.vm.split("resetCatalogRestoreForProfileChange()")[1][:120]
        self.assertIn("beginStartupScanPriority(activeSession)", profile_block)

    def test_session_logout_and_profile_change_invalidate(self) -> None:
        self.assertGreaterEqual(
            self.vm.count("startupScanPriorityGate.invalidate()"),
            3,
            "logout, profile change and catalog reset must all drop the round",
        )

    def test_no_sleeps_or_new_waits_in_the_gate_path(self) -> None:
        for forbidden in ("Thread.sleep", "SystemClock.sleep", "delay(", "runBlocking"):
            self.assertNotIn(forbidden, self.gate)


if __name__ == "__main__":
    unittest.main()
