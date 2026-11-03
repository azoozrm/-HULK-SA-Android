import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class ControlCenterShellContractTests(unittest.TestCase):
    def read(self, name: str) -> str:
        return (ROOT / name).read_text(encoding="utf-8")

    def test_primary_navigation_has_exactly_the_seven_owner_destinations(self) -> None:
        routes = self.read("lib/routes.php")
        block = re.search(
            r"function cc_main_navigation\(\): array\s*\{(.*?)\n\}", routes, re.S
        )
        self.assertIsNotNone(block)
        keys = re.findall(r"'([a-z-]+)' => \[", block.group(1))
        self.assertEqual(
            keys,
            ["dashboard", "live-users", "hosts", "sessions", "announcements", "releases", "service"],
            "the main shell must expose exactly the seven owner destinations",
        )
        for label in ("نظرة عامة", "المتصلون الآن", "الهوستات والأكواد", "السجلات", "الرسائل", "تحديث التطبيق", "التحكم بالتطبيق"):
            self.assertIn(label, block.group(1))

    def test_default_route_and_active_tab_families_are_stable(self) -> None:
        bootstrap = self.read("bootstrap.php")
        routes = self.read("lib/routes.php")
        self.assertIn("function cc_url(string $module = 'dashboard')", bootstrap)
        self.assertIn("$key = 'dashboard';", routes)
        active = re.search(
            r"function cc_active_main_navigation\(string \$moduleKey\): \?string\s*\{(.*?)\n\}",
            routes,
            re.S,
        )
        self.assertIsNotNone(active)
        for module in ("live-users", "sessions", "devices", "announcements", "hosts", "access-codes", "resellers", "releases"):
            self.assertIn(f"'{module}'", active.group(1))

    def test_context_navigation_preserves_every_existing_tool(self) -> None:
        routes = self.read("lib/routes.php")
        layout = self.read("views/layout.php")
        self.assertIn("function cc_context_navigation", routes)
        self.assertIn("cc_context_navigation($activeKey)", layout)
        self.assertIn("context-nav", layout)
        self.assertNotIn('data-settings-menu', layout)
        for key in ("dashboard", "analytics", "diagnostics", "audit", "hosts", "access-codes", "host-health", "live-users", "sessions", "devices", "service", "features", "growth", "settings"):
            self.assertIn(f"'{key}'", routes)
        self.assertIn('class="sidebar"', layout)
        self.assertIn('bottom-nav', layout)

    def test_host_context_has_one_combined_management_destination(self) -> None:
        routes = self.read("lib/routes.php")
        context = re.search(r"'hosts' => \[\s*'hosts' => 'الهوستات والأكواد', 'host-health' => 'حالة الهوستات',\s*\]", routes)
        self.assertIsNotNone(context)
        bootstrap = self.read("bootstrap.php")
        self.assertIn("['access-codes','resellers']", bootstrap)
        self.assertIn("$module = 'hosts'", bootstrap)

    def test_online_now_uses_exact_grouped_presence_identity(self) -> None:
        model = self.read("lib/presence-read-model.php")
        self.assertIn("function cc_presence_online_now_page", model)
        self.assertRegex(
            model,
            re.compile(
                r"GROUP BY s\.reseller_id, HEX\(s\.host_snapshot\), HEX\(s\.iptv_username\)",
                re.IGNORECASE,
            ),
        )
        self.assertIn("UPPER(HEX(s.host_snapshot))", model)
        self.assertIn("UPPER(HEX(s.iptv_username))", model)
        self.assertIn(
            "ORDER BY last_activity_at DESC, s.reseller_id DESC, "
            "HEX(s.host_snapshot) ASC, HEX(s.iptv_username) ASC",
            model,
        )
        self.assertIn("function cc_presence_subscription_key", model)
        key = re.search(
            r"function cc_presence_subscription_key\([^)]*\): string\s*\{(.*?)\n\}",
            model,
            re.S,
        )
        self.assertIsNotNone(key)
        self.assertIn("$resellerId", key.group(1))
        self.assertIn("$host", key.group(1))
        self.assertIn("$username", key.group(1))
        self.assertNotIn("strtolower", key.group(1))

    def test_online_now_details_are_bounded_and_partial_aware(self) -> None:
        model = self.read("lib/presence-read-model.php")
        view = self.read("views/online-now.php")
        self.assertIn("CC_PRESENCE_ONLINE_DEVICE_LIMIT", model)
        self.assertIn("devices_truncated", model)
        online_model = model.split("function cc_presence_online_now_page(", 1)[1].split("function cc_presence_device_page(", 1)[0]
        self.assertNotIn("['sessions']", online_model)
        self.assertIn("ORDER BY s.last_seen_at DESC, s.id DESC LIMIT 1", model)
        self.assertIn("CC_PRESENCE_ONLINE_DEVICE_LIMIT + 1", model)
        self.assertIn("devices_truncated", view)
        self.assertIn("subscription-devices__partial", view)

    def test_online_now_view_displays_required_presence_fields_and_pagination(self) -> None:
        view = self.read("views/online-now.php")
        for field in (
            "access_code_snapshot",
            "iptv_username",
            "iptv_password",
            "host_snapshot",
            "platform_class",
            "last_seen_at",
        ):
            self.assertIn(field, view)
        self.assertIn("presence-pagination", view)
        self.assertIn("total_subscriptions", view)
        self.assertIn("multi_device_subscriptions", view)
        self.assertIn("live-users-page", view)
        self.assertIn("subscription-credentials", view)
        self.assertNotIn("credential-disclosure", view)
        self.assertNotIn("ضمن الموزع والهوست", view)
        self.assertNotIn("لا يتم الدمج بالاسم وحده", view)
        self.assertIn("kpis", view)
        self.assertIn("قائمة المتصلين", view)
        self.assertIn("subscription-row-details", view)
        self.assertIn("subscription-row", view)
        self.assertIn("check_subscriber", view)
        self.assertIn("save_subscriber_note", view)
        self.assertIn("subscriber-check-result", view)
        self.assertIn("subscriber-note", view)
        self.assertIn("data-submit-once", view)
        self.assertIn("cc_e($note)", view)
        self.assertIn("cc_e($noteDraft", view)
        self.assertNotIn("reveal", view.lower())
        self.assertNotIn("mask", view.lower())

    def test_apk_picker_has_no_restrictive_client_accept_filter(self) -> None:
        operations = self.read("views/operations.php")
        match = re.search(r'<input[^>]*name="apk"[^>]*>', operations)
        self.assertIsNotNone(match)
        self.assertIn('type="file"', match.group(0))
        self.assertNotIn("accept=", match.group(0))
        self.assertIn("required", match.group(0))
        policies = (ROOT.parent / "hulk-operations" / "lib" / "policies.php").read_text(encoding="utf-8")
        self.assertIn("application/vnd.android.package-archive", policies)
        self.assertIn("ops_apk_descriptor_errors", policies)

    def test_shell_uses_the_light_reference_layout(self) -> None:
        layout = self.read("views/layout.php")
        css = self.read("assets/reference.css")
        self.assertIn('<html lang="ar" dir="rtl">', layout)
        for token in ("--paper:#f7f6f2", "--dark:#181a16", "--gold:#e6c352", "--ink:#20231e"):
            self.assertIn(token, css)
        self.assertIn(".shell", css)
        self.assertIn(".sidebar", css)
        self.assertIn(".nav-btn", css)
        self.assertIn(".bottom-nav", css)
        self.assertIn(".kpis", css)
        self.assertIn(".login-preview", css)
        self.assertIn("@media(max-width:760px)", css)
        self.assertIn('class="sidebar"', layout)
        self.assertIn("bottom-nav", layout)
        self.assertIn("context-nav", layout)
        self.assertNotIn("primary-tabs__track", layout)

    def test_subscriber_check_and_notes_identity_contract(self) -> None:
        identity = self.read("lib/subscriber-identity.php")
        notes = self.read("lib/subscriber-notes.php")
        session = self.read("lib/subscriber-session.php")
        migration = self.read("migrations/2026-10-19-subscriber-notes-v1.sql")
        self.assertIn("hash('sha256'", identity)
        self.assertIn("cc_subscriber_note_host_key", notes)
        self.assertIn("cc_subscriber_note_username_key", notes)
        self.assertNotIn("strtolower", identity)
        self.assertIn("ON DUPLICATE KEY UPDATE", notes)
        self.assertIn("ON CONFLICT(reseller_id, host_key, username_key)", notes)
        self.assertIn("CC_SUBSCRIBER_NOTE_MAX_LENGTH", notes)
        self.assertIn("cc_subscriber_check_attach_results", session)
        self.assertIn("cc_subscriber_note_attach_drafts", notes)
        self.assertIn("CREATE TABLE IF NOT EXISTS cc_subscriber_notes", migration)
        self.assertIn("UNIQUE KEY uq_cc_subscriber_notes_identity", migration)
        self.assertIn("INSERT IGNORE INTO cc_schema_migrations", migration)
        self.assertNotIn("DROP TABLE", migration)
        self.assertNotIn("TRUNCATE", migration)
        self.assertNotIn("ALTER TABLE", migration)

    def test_subscriber_actions_are_bounded_and_csrf_handled(self) -> None:
        subscriber = self.read("lib/subscriber.php")
        check = self.read("lib/subscriber-check.php")
        index = self.read("index.php")
        self.assertIn("cc_require_csrf()", index)
        self.assertIn("cc_subscriber_handle_post", index)
        self.assertIn("cc_subscriber_resolve_online_subscription", subscriber)
        self.assertIn("ORDER BY s.last_seen_at DESC, s.id DESC LIMIT 1", subscriber)
        self.assertIn("CC_SUBSCRIBER_CHECK_CONNECT_TIMEOUT_MS", check)
        self.assertIn("CC_SUBSCRIBER_CHECK_REQUEST_TIMEOUT_MS", check)
        self.assertIn("CC_SUBSCRIBER_CHECK_BODY_LIMIT", check)
        self.assertIn("cc_pairing_provider_target", check)
        self.assertIn("cc_host_health_public_ip", check)
        self.assertIn("cc_subscriber_check_provider", subscriber)
        self.assertNotIn("$_POST['host']", subscriber)
        self.assertNotIn("$_POST['password']", subscriber)

    def test_access_codes_are_shown_directly_to_the_authorized_owner(self) -> None:
        view = self.read("views/resellers.php")
        self.assertIn("credential-value", view)
        self.assertRegex(view, r'<code class="credential-value" dir="ltr">')
        self.assertNotIn("code-reveal", view)
        self.assertNotIn("اظهار الكود", view)
        self.assertIn("المتصلون الآن", view)
        self.assertIn("code_online_available", view)

    def test_announcement_update_and_inactive_upload_contract(self) -> None:
        operations = self.read("views/operations.php")
        adapter = self.read("lib/operations-adapter.php")
        actions = (ROOT.parent / "hulk-operations" / "admin" / "actions.php").read_text(encoding="utf-8")
        self.assertIn("update_announcement", operations)
        self.assertIn("update_announcement", adapter)
        self.assertIn("function ops_update_announcement", actions)
        self.assertIn("VALUES (:version_name, :version_code, :minimum_supported_version_code, :apk_path,", actions)
        self.assertIn(":required, 0, 0)", actions)


if __name__ == "__main__":
    unittest.main()
