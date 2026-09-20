from __future__ import annotations

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = ROOT.parents[1]


class PhaseEightContractTests(unittest.TestCase):
    def read(self, path: Path) -> str:
        return path.read_text(encoding="utf-8")

    def test_settings_is_a_safe_read_only_runtime_boundary(self) -> None:
        index = self.read(ROOT / "index.php")
        domain = self.read(ROOT / "lib" / "phase8.php")
        view = self.read(ROOT / "views" / "settings.php")
        self.assertIn("cc_phase8_settings_data($admin)", index)
        self.assertIn("views/settings.php", index)
        for safe_value in (
            "login_max_attempts",
            "login_lock_seconds",
            "heartbeat_seconds",
            "online_ttl_seconds",
            "session_retention_days",
            "retention_days",
            "max_runtime_seconds",
            "max_hosts_per_run",
        ):
            self.assertIn(safe_value, domain)
        for forbidden in (
            "rate_limit_secret",
            "token_secret",
            "password",
            "username",
            "dsn",
            "user_agent",
        ):
            self.assertNotIn(f"['{forbidden}']", domain)
        self.assertNotRegex(view, re.compile(r"<form[^>]+method=[\"']post", re.IGNORECASE))

    def test_audit_search_is_parameterized_stable_and_bounded(self) -> None:
        domain = self.read(ROOT / "lib" / "phase8.php")
        view = self.read(ROOT / "views" / "audit.php")
        self.assertIn("CC_PHASE8_AUDIT_PAGE_SIZE = 50", domain)
        self.assertIn("CC_PHASE8_AUDIT_MAX_PAGES = 200", domain)
        self.assertIn("CC_PHASE8_AUDIT_MAX_WINDOW_DAYS = 366", domain)
        self.assertIn("a.id <= :snapshot_id", domain)
        self.assertIn("ORDER BY a.id DESC", domain)
        self.assertIn("LIMIT 51", domain)
        self.assertIn("->prepare(", domain)
        self.assertIn("details_safe", domain)
        self.assertNotIn("$row['details']", view)
        for field in ("date_from", "date_to", "admin", "action", "reseller_id"):
            self.assertIn(f'name="{field}"', view)

    def test_saved_filters_are_local_bounded_and_exclude_sensitive_fields(self) -> None:
        source = self.read(ROOT / "assets" / "app.js")
        sessions = self.read(ROOT / "views" / "sessions.php")
        devices = self.read(ROOT / "views" / "devices.php")
        resellers = self.read(ROOT / "views" / "resellers.php")
        audit = self.read(ROOT / "views" / "audit.php")
        self.assertIn("localStorage", source)
        self.assertIn("MAX_SAVED_FILTERS = 10", source)
        self.assertIn("safeSavedValue", source)
        for view in (sessions, devices, resellers, audit):
            self.assertIn("data-saved-filters", view)
        allowed_declarations = "\n".join(
            declaration
            for view in (sessions, devices, resellers, audit)
            for declaration in re.findall(r'data-saved-filter-fields="([^"]*)"', view)
        )
        for forbidden in ("q", "host", "access_code", "iptv_username", "iptv_password", "session_id"):
            self.assertNotRegex(allowed_declarations, rf"(?:^|,){re.escape(forbidden)}(?:,|$)")
        for free_text in ("admin", "action", "app_version"):
            self.assertNotRegex(allowed_declarations, rf"(?:^|,){re.escape(free_text)}(?:,|$)")
        self.assertEqual(source.count("form.requestSubmit();"), 1)

    def test_mobile_tables_focus_and_touch_targets_are_refined(self) -> None:
        css = self.read(ROOT / "assets" / "app.css")
        javascript = self.read(ROOT / "assets" / "app.js")
        login = self.read(ROOT / "login.php")
        views = "\n".join(self.read(path) for path in (ROOT / "views").glob("*.php"))
        self.assertIn("data-mobile-cards", views)
        self.assertIn("is-mobile-card-ready", javascript)
        self.assertIn("summary:focus-visible", css)
        self.assertIn("min-height: 44px", css)
        self.assertIn("@media (max-width: 700px)", css)
        self.assertIn("app.css?v=3.0.0", login)

    def test_cross_module_links_use_non_secret_context_only(self) -> None:
        bootstrap = self.read(ROOT / "bootstrap.php")
        health = self.read(ROOT / "views" / "host-health.php")
        reseller = self.read(ROOT / "lib" / "reseller-adapter.php")
        self.assertIn("function cc_context_url", bootstrap)
        self.assertIn("'reseller'", bootstrap)
        self.assertIn("cc_context_url('hosts'", health)
        self.assertIn("reseller_id = :reseller_id", reseller)
        self.assertNotIn("host_fingerprint", bootstrap)

    def test_phase_eight_has_no_schema_api_or_android_change(self) -> None:
        qualification = self.read(ROOT / "PHASE-8-QUALIFICATION.md")
        self.assertIn("COMPLETE", qualification)
        self.assertIn("NOT REQUIRED", qualification)
        self.assertIn("USER / PRODUCTION ACCEPTANCE", qualification)
        self.assertIn("No schema migration", qualification)
        self.assertIn("No API contract change", qualification)
        self.assertIn("No Android source change", qualification)
        self.assertTrue((REPOSITORY / "app" / "src").is_dir())


if __name__ == "__main__":
    unittest.main()
