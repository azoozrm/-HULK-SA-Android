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

    def test_owner_audit_is_arabic_structured_and_technically_secondary(self) -> None:
        domain = self.read(ROOT / "lib" / "phase8.php")
        view = self.read(ROOT / "views" / "audit.php")
        dashboard = self.read(ROOT / "views" / "dashboard.php")
        self.assertIn("function cc_phase8_audit_action_presentation", domain)
        self.assertIn("GROWTH_CONFIG_PUBLISHED", domain)
        self.assertIn("تم تحديث إعدادات التجديد والدعم", domain)
        self.assertIn("LEGACY_CUSTOM_ACTION", self.read(ROOT / "tests" / "phase8.php"))
        self.assertIn("technical_id", domain)
        self.assertIn("'rows'", domain)
        self.assertIn("'direction' => 'ltr'", domain)
        self.assertIn("تفاصيل تقنية", view)
        self.assertIn("audit-event__title", view)
        self.assertIn("cc_phase8_audit_action_presentation", dashboard)
        self.assertNotRegex(view, re.compile(r"<code[^>]*>\s*<\?=\s*cc_e\(\$row\['action'\]\)", re.IGNORECASE))

    def test_navigation_and_settings_use_owner_oriented_arabic(self) -> None:
        routes = self.read(ROOT / "lib" / "routes.php")
        layout = self.read(ROOT / "views" / "layout.php")
        settings = self.read(ROOT / "views" / "settings.php")
        css = self.read(ROOT / "assets" / "app.css")
        for label in ("النشاط المباشر", "الموزعون والوصول", "إدارة التطبيق", "المتابعة والتحليل", "إدارة المركز"):
            self.assertIn(label, routes)
        self.assertIn("navigation__group-title", layout)
        for forbidden_heading in ("Presence", "Diagnostics", "Host Health", "Single-owner boundaries"):
            self.assertNotIn(f'>{forbidden_heading}<', settings)
        self.assertIn("إعدادات مركز التحكم", settings)
        self.assertIn("الجهات المسؤولة عن التعديل", settings)
        self.assertIn("cc_technical_disclosure", settings)
        self.assertIn("--surface-1", css)
        self.assertIn("dashboard-command", css)
        self.assertIn("intdiv", settings)
        self.assertIn("الرئيسية", layout)

    def test_dashboard_distinguishes_partial_evidence_and_keeps_every_warning_visible(self) -> None:
        dashboard = self.read(ROOT / "views" / "dashboard.php")
        audit = self.read(ROOT / "views" / "audit.php")
        self.assertIn("!$hostHealthAvailable || !$diagnosticsAvailable || !$analyticsAvailable", dashboard)
        self.assertIn("!$hostHealthCoverageComplete", dashboard)
        self.assertIn("آخر فحص لم يشمل كل الهوستات المستهدفة", dashboard)
        self.assertNotIn("array_slice($attentionItems, 0, 5)", dashboard)
        self.assertIn("technical_id", dashboard)
        self.assertIn("غير مصنف", audit)

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
        self.assertIn("app.css?v=5.0.1", login)

    def test_closed_rtl_mobile_drawer_is_fully_outside_the_viewport(self) -> None:
        css = self.read(ROOT / "assets" / "app.css")
        mobile_css = css.split("@media (max-width: 860px)", 1)[1].split(
            "@media (max-width: 700px)", 1
        )[0]
        sidebar_match = re.search(r"\.sidebar\s*\{([^}]*)\}", mobile_css)
        self.assertIsNotNone(sidebar_match)
        declarations = {
            name.strip(): value.strip()
            for name, value in re.findall(
                r"([\w-]+)\s*:\s*([^;]+);", sidebar_match.group(1)
            )
        }

        viewport_width = 390.0
        width_match = re.fullmatch(
            r"min\(([\d.]+)vw,\s*([\d.]+)px\)", declarations.get("width", "")
        )
        self.assertIsNotNone(width_match)
        drawer_width = min(
            viewport_width * float(width_match.group(1)) / 100.0,
            float(width_match.group(2)),
        )

        if "right" in declarations:
            closed_left = viewport_width - drawer_width - float(
                declarations["right"].removesuffix("px")
            )
        elif declarations.get("inset-inline-end") == "0":
            # In an RTL document, inline-end resolves to the physical left edge.
            closed_left = 0.0
        else:
            self.fail("mobile drawer has no supported physical anchor")

        translation_match = re.search(
            r"translate(?:X|3d)\(\s*([\d.]+)%", declarations.get("transform", "")
        )
        self.assertIsNotNone(translation_match)
        closed_left += drawer_width * float(translation_match.group(1)) / 100.0
        visible_width = max(
            0.0,
            min(viewport_width, closed_left + drawer_width) - max(0.0, closed_left),
        )

        self.assertEqual(
            visible_width,
            0.0,
            "the closed RTL mobile drawer must not cover or block any page content",
        )

    def test_every_shared_view_has_a_purpose_specific_page_composition(self) -> None:
        components = self.read(ROOT / "views" / "components.php")
        sessions = self.read(ROOT / "views" / "sessions.php")
        devices = self.read(ROOT / "views" / "devices.php")
        resellers = self.read(ROOT / "views" / "resellers.php")
        operations = self.read(ROOT / "views" / "operations.php")
        health = self.read(ROOT / "views" / "host-health.php")
        diagnostics = self.read(ROOT / "views" / "diagnostics.php")
        analytics = self.read(ROOT / "views" / "analytics.php")

        for primitive in (
            "function cc_page_summary",
            "function cc_filter_disclosure",
            "function cc_fact_list",
            "function cc_technical_disclosure",
        ):
            self.assertIn(primitive, components)

        for marker in ("live-users-page", "session-history-page", "credential-disclosure"):
            self.assertIn(marker, sessions)
        self.assertIn("device-inventory-page", devices)

        for marker in (
            "reseller-accounts-page",
            "access-code-management-page",
            "host-management-page",
        ):
            self.assertIn(marker, resellers)

        for marker in (
            "release-management-page",
            "service-control-page",
            "announcement-management-page",
            "feature-control-page",
            "growth-management-page",
        ):
            self.assertIn(marker, operations)

        self.assertIn("host-decision-page", health)
        self.assertIn("failure-insights-page", diagnostics)
        self.assertIn("analytics-workspace", analytics)

    def test_owner_flows_demote_forms_filters_and_technical_metadata(self) -> None:
        operations = self.read(ROOT / "views" / "operations.php")
        resellers = self.read(ROOT / "views" / "resellers.php")
        sessions = self.read(ROOT / "views" / "sessions.php")
        audit = self.read(ROOT / "views" / "audit.php")
        health = self.read(ROOT / "views" / "host-health.php")
        analytics = self.read(ROOT / "views" / "analytics.php")

        self.assertIn('class="form-section"', operations)
        self.assertIn("release-current", operations)
        self.assertIn("service-message-preview", operations)
        self.assertIn("feature-state-group", operations)
        self.assertIn("growth-channel", operations)
        self.assertIn("management-summary", resellers)
        self.assertIn("record-actions", resellers)
        self.assertIn("cc_filter_disclosure", sessions)
        self.assertIn("cc_filter_disclosure", audit)
        self.assertIn("health-attention-list", health)
        self.assertIn("methodology-disclosure", analytics)

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
