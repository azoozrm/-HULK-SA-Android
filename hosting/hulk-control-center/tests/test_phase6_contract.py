import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = ROOT.parents[1]


class PhaseSixContractTests(unittest.TestCase):
    def read(self, path: Path) -> str:
        return path.read_text(encoding="utf-8")

    def test_read_model_is_read_only_and_uses_authoritative_presence_tables(self) -> None:
        source = self.read(ROOT / "lib" / "presence-read-model.php")
        self.assertIn("cc_app_sessions", source)
        self.assertIn("cc_devices", source)
        self.assertNotRegex(
            source,
            re.compile(r"\b(?:INSERT\s+INTO|UPDATE\s+\w|DELETE\s+FROM|REPLACE\s+INTO)\b", re.IGNORECASE),
        )
        self.assertNotIn("account_id", source)

    def test_live_sessions_and_devices_routes_are_authenticated_and_connected(self) -> None:
        index = self.read(ROOT / "index.php")
        self.assertLess(index.index("cc_require_admin()"), index.index("cc_presence_page_data("))
        for module in ("live-users", "sessions", "devices"):
            self.assertIn(f"'{module}'", index)
        self.assertIn("views/sessions.php", index)
        self.assertIn("views/devices.php", index)

    def test_session_snapshots_are_direct_and_never_use_reveal_ui(self) -> None:
        view = self.read(ROOT / "views" / "sessions.php")
        for field in ("access_code_snapshot", "iptv_username", "iptv_password", "host_snapshot"):
            self.assertIn(field, view)
        self.assertNotIn("reveal", view.lower())
        self.assertNotIn("mask", view.lower())

    def test_dashboard_enables_only_authoritative_phase_six_metrics(self) -> None:
        source = self.read(ROOT / "lib" / "dashboard.php")
        view = self.read(ROOT / "views" / "dashboard.php")
        for metric in ("online_now", "sessions_today", "active_devices"):
            self.assertIn(metric, source)
            self.assertIn(metric, view)
        self.assertIn("غير متاح لعدم وجود هوية حساب ثابتة", view)
        self.assertIn("Presence", view)

    def test_reseller_code_and_host_views_include_presence_usage_without_mutation(self) -> None:
        adapter = self.read(ROOT / "lib" / "reseller-adapter.php")
        view = self.read(ROOT / "views" / "resellers.php")
        self.assertIn("cc_presence_usage_for_resellers", adapter)
        self.assertIn("presence_usage_available", adapter)
        self.assertIn("آخر استخدام", view)
        self.assertIn("متصل الآن", view)

    def test_mobile_representation_avoids_wide_table_dependency(self) -> None:
        sessions = self.read(ROOT / "views" / "sessions.php")
        devices = self.read(ROOT / "views" / "devices.php")
        css = self.read(ROOT / "assets" / "app.css")
        self.assertIn("presence-mobile-list", sessions)
        self.assertIn("presence-mobile-list", devices)
        self.assertIn("@media (max-width: 700px)", css)
        self.assertIn(".presence-mobile-list", css)

    def test_no_android_source_is_part_of_phase_six_contract(self) -> None:
        source = self.read(ROOT / "lib" / "presence-read-model.php")
        self.assertNotIn("app/src/", source)
        self.assertTrue((REPOSITORY / "app" / "src").is_dir())


if __name__ == "__main__":
    unittest.main()
