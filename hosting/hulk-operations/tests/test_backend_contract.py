from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]


class OperationsBackendContractTest(unittest.TestCase):
    def read(self, relative: str) -> str:
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_no_live_config_or_credentials_are_committed(self) -> None:
        self.assertFalse((ROOT / "config.php").exists())
        example = self.read("config.example.php")
        self.assertEqual(example.count("'CHANGE_ME'"), 2)
        self.assertIn("'bootstrap_token' => ''", example)

    def test_schema_has_safe_defaults_and_unique_version_code(self) -> None:
        schema = self.read("schema.sql")
        self.assertIn("uq_app_releases_version_code", schema)
        self.assertIn("('latest_version_code', '64')", schema)
        self.assertIn("('latest_version_name', '0.9.3.20')", schema)
        self.assertIn("('minimum_supported_version_code', '64')", schema)
        for flag in (
            "downloads_enabled",
            "episode_notifications_enabled",
            "smart_recommendations_enabled",
            "live_tv_pro_enabled",
        ):
            self.assertIn(f"('{flag}', 1)", schema)

    def test_admin_security_controls_are_present(self) -> None:
        bootstrap = self.read("bootstrap.php")
        login = self.read("admin/login.php")
        actions = self.read("admin/actions.php")
        self.assertIn("'httponly' => true", bootstrap)
        self.assertIn("'secure' => true", bootstrap)
        self.assertIn("'samesite' => 'Strict'", bootstrap)
        self.assertIn("session.use_strict_mode", bootstrap)
        self.assertIn("ops_require_csrf", actions)
        self.assertIn("password_verify", login)
        self.assertIn("locked_until", login)
        self.assertIn("PDO::ATTR_EMULATE_PREPARES", bootstrap)
        self.assertGreaterEqual(actions.count("->prepare("), 12)

    def test_apk_upload_is_server_verified(self) -> None:
        actions = self.read("admin/actions.php")
        releases_rules = self.read("releases/.htaccess")
        for marker in (
            "is_uploaded_file",
            "finfo_file",
            "ZipArchive",
            "AndroidManifest.xml",
            "classes.dex",
            "hash_file('sha256'",
            "move_uploaded_file",
        ):
            self.assertIn(marker, actions)
        self.assertRegex(releases_rules, re.compile(r"php", re.IGNORECASE))
        self.assertIn("Require all denied", releases_rules)

    def test_release_lifecycle_and_duplicate_guard_are_explicit(self) -> None:
        actions = self.read("admin/actions.php")
        self.assertIn("SELECT id FROM app_releases WHERE version_code", actions)
        self.assertIn("UPDATE app_releases SET is_active = 0", actions)
        self.assertIn("enabled = 1, is_active = 1", actions)
        self.assertIn("enabled = 0, is_active = 0", actions)
        self.assertNotIn("$_POST['apk_sha256']", actions)

    def test_public_api_is_read_only_and_versioned(self) -> None:
        endpoint = self.read("api/app/v1/config/index.php")
        operations = self.read("lib/operations.php")
        self.assertIn("REQUEST_METHOD", endpoint)
        self.assertIn("Allow: GET", endpoint)
        self.assertIn("'schemaVersion' => 1", operations)
        self.assertIn("'announcement'", operations)
        self.assertIn("'announcements'", operations)
        self.assertIn("ops_normalize_feature_flags", operations)
        self.assertIn("LIMIT 20", operations)

    def test_native_pdo_queries_use_unique_named_placeholders(self) -> None:
        operations = self.read("lib/operations.php")
        self.assertNotIn("starts_at <= :now", operations)
        self.assertNotIn("ends_at > :now", operations)
        self.assertIn("starts_at <= :starts_now", operations)
        self.assertIn("ends_at > :ends_now", operations)
        self.assertIn("'starts_now' => $formattedNow", operations)
        self.assertIn("'ends_now' => $formattedNow", operations)

    def test_admin_dashboard_is_arabic_and_mobile_adaptive(self) -> None:
        dashboard = self.read("admin/index.php")
        layout = self.read("admin/_layout.php")
        login = self.read("admin/login.php")
        styles = self.read("assets/app.css")
        for label in (
            "مركز عمليات HULK SA",
            "الإصدار المنشور حاليًا",
            "الحد الأدنى للإصدار المدعوم",
            "حالة التحديث",
            "حالة الخدمة",
            "وضع الصيانة",
            "الرسالة النشطة",
            "المميزات المفعّلة",
        ):
            self.assertIn(label, dashboard)
        self.assertNotIn("Operations Center", dashboard + layout + login)
        self.assertIn("grid-template-columns: repeat(3, minmax(0, 1fr))", styles)
        self.assertIn(".stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }", styles)
        self.assertIn(".logout-user { display: none; }", styles)

    def test_service_and_feature_mutations_use_closed_allowlists(self) -> None:
        actions = self.read("admin/actions.php")
        policies = self.read("lib/policies.php")
        for status in ("OPERATIONAL", "DEGRADED", "MAINTENANCE"):
            self.assertIn(f"'{status}'", actions)
        for flag in (
            "downloads_enabled",
            "episode_notifications_enabled",
            "smart_recommendations_enabled",
            "live_tv_pro_enabled",
        ):
            self.assertIn(f"'{flag}'", policies)
        self.assertIn("in_array($flagKey, ops_known_feature_flags(), true)", actions)

    def test_package_root_routes_to_protected_admin(self) -> None:
        root_index = self.read("index.php")
        self.assertIn("Location: admin/", root_index)
        self.assertIn("Cache-Control: no-store", root_index)

    def test_audit_filter_excludes_secrets(self) -> None:
        bootstrap = self.read("bootstrap.php")
        self.assertIn("password|secret|token|credential", bootstrap)

    def test_growth_uses_existing_settings_and_safe_migration(self) -> None:
        schema = self.read("schema.sql")
        migration = self.read("migrations/2026-08-23-v240-growth-lite.sql")
        migration_rules = self.read("migrations/.htaccess")
        self.assertNotIn("CREATE TABLE", migration)
        self.assertIn("INSERT IGNORE INTO app_settings", migration)
        self.assertIn("Options -Indexes", migration_rules)
        self.assertIn("Require all denied", migration_rules)
        for key in (
            "growth_enabled",
            "growth_renewal_url",
            "growth_support_url",
            "growth_renewal_qr_mode",
            "growth_support_qr_mode",
            "growth_renewal_banner_days",
        ):
            self.assertIn(key, schema)
            self.assertIn(key, migration)

    def test_growth_admin_is_integrated_and_csrf_protected(self) -> None:
        dashboard = self.read("admin/index.php")
        layout = self.read("admin/_layout.php")
        actions = self.read("admin/actions.php")
        self.assertIn("'growth' => 'TV Growth'", layout)
        self.assertIn("HULK TV Growth", dashboard)
        self.assertIn('name="action" value="save_growth"', dashboard)
        self.assertIn('name="csrf_token"', dashboard)
        self.assertIn("case 'save_growth':", actions)
        self.assertIn("ops_require_csrf", actions)

    def test_growth_url_and_day_validation_are_closed(self) -> None:
        policies = self.read("lib/policies.php")
        actions = self.read("admin/actions.php")
        self.assertIn("ops_growth_renewal_url", policies)
        self.assertIn("ops_growth_support_url", policies)
        self.assertIn("['AUTO', 'CUSTOM']", policies)
        self.assertIn("$days >= 1 && $days <= 30", policies)
        self.assertIn("رابط التجديد يجب أن يكون HTTPS داخل hulksa.com", actions)
        self.assertIn("رابط WhatsApp رسميًا وآمنًا", actions)

    def test_growth_qr_upload_is_restricted_and_non_executable(self) -> None:
        actions = self.read("admin/actions.php")
        policies = self.read("lib/policies.php")
        media_rules = self.read("growth-media/.htaccess")
        for marker in (
            "is_uploaded_file",
            "finfo_file",
            "getimagesize",
            "ops_growth_qr_file_name",
            "move_uploaded_file",
            "max_growth_qr_bytes",
        ):
            self.assertIn(marker, actions)
        self.assertIn("PNG أو WebP", policies)
        self.assertRegex(media_rules, re.compile(r"php|phtml|phar", re.IGNORECASE))
        self.assertIn("Require all denied", media_rules)
        self.assertIn("Options -Indexes -ExecCGI", media_rules)

    def test_growth_custom_qr_replacement_and_deletion_are_audited(self) -> None:
        actions = self.read("admin/actions.php")
        self.assertIn("GROWTH_CONFIG_PUBLISHED", actions)
        self.assertIn("GROWTH_QR_REPLACED", actions)
        self.assertIn("GROWTH_QR_DELETED", actions)
        self.assertIn("ops_growth_safe_unlink", actions)
        self.assertIn("delete_renewal_qr", actions)
        self.assertIn("delete_support_qr", actions)
        self.assertIn("$db->beginTransaction()", actions)

    def test_public_api_emits_optional_growth_without_schema_bump(self) -> None:
        operations = self.read("lib/operations.php")
        self.assertIn("'schemaVersion' => 1", operations)
        self.assertIn("'growth' => ops_growth_snapshot($db)", operations)
        self.assertIn("'customQrUrl'", operations)
        self.assertIn("'daysBeforeExpiry'", operations)
        self.assertIn("ops_growth_custom_qr_path_is_safe", operations)
        self.assertIn("is_file($absolutePath)", operations)

    def test_growth_admin_layout_remains_mobile_adaptive(self) -> None:
        dashboard = self.read("admin/index.php")
        styles = self.read("assets/app.css")
        self.assertIn("growth-form", dashboard)
        self.assertIn("growth-grid", dashboard)
        self.assertIn("growth-banner-card", dashboard)
        self.assertIn("@media (max-width: 650px)", styles)
        self.assertIn(".growth-card-head", styles)
        self.assertIn(".growth-publish .button { width: 100%; }", styles)

    def test_growth_has_no_reseller_or_tracking_integration(self) -> None:
        growth_sources = "\n".join(
            self.read(path)
            for path in (
                "lib/operations.php",
                "lib/policies.php",
                "admin/actions.php",
                "admin/index.php",
            )
        ).lower()
        self.assertNotIn("reseller", growth_sources)
        self.assertNotIn("firebase", growth_sources)
        self.assertNotIn("analytics", growth_sources)


if __name__ == "__main__":
    unittest.main()
