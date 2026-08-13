from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PUBLIC = ROOT / "public"
APP = PUBLIC / ".hulk-reseller-app"
BOOTSTRAP = APP / "bootstrap.php"
API = PUBLIC / "api/reseller/resolve/index.php"
PORTAL = PUBLIC / "reseller/index.php"
PORTAL_ACTION = PUBLIC / "reseller/action.php"
ADMIN = PUBLIC / "hulk-reseller-admin/index.php"
ADMIN_ACTION = PUBLIC / "hulk-reseller-admin/action.php"
ASSETS = PUBLIC / "reseller/assets"
SCHEMA = ROOT / "schema.sql"


class ResellerBackendContractTest(unittest.TestCase):
    def test_required_deployment_files_are_present(self) -> None:
        required = (
            BOOTSTRAP,
            API,
            PORTAL,
            PORTAL_ACTION,
            ADMIN,
            ADMIN_ACTION,
            SCHEMA,
            APP / ".htaccess",
            APP / "config.example.php",
            ASSETS / "styles.css",
            ASSETS / "portal.js",
            ASSETS / "hulk-logo.png",
            ASSETS / "hulk-icon.png",
            ASSETS / "fonts/OFL-1.1.txt",
        )
        self.assertFalse([str(path) for path in required if not path.is_file()])

    def test_database_contains_admin_reseller_and_rate_limit_tables(self) -> None:
        schema = SCHEMA.read_text(encoding="utf-8")
        for table in ("admins", "resellers", "resolver_rate_limits"):
            self.assertRegex(schema, rf"CREATE TABLE IF NOT EXISTS {table}\b")
        for field in (
            "reseller_id",
            "reseller_name",
            "host",
            "access_code",
            "status",
            "access_code_hash",
            "password_hash",
        ):
            self.assertRegex(schema, rf"\b{field}\b")

    def test_access_codes_support_short_current_and_legacy_formats(self) -> None:
        bootstrap = BOOTSTRAP.read_text(encoding="utf-8")
        self.assertIn("ABCDEFGHJKMNPQRSTUVWXYZ23456789", bootstrap)
        self.assertIn("HULK_ACCESS_CODE_PAYLOAD_LENGTH = 8", bootstrap)
        self.assertIn("HULK_CUSTOM_ACCESS_CODE_MAX_LENGTH = 12", bootstrap)
        self.assertIn("HULK_LEGACY_ACCESS_CODE_PAYLOAD_LENGTH = 16", bootstrap)
        self.assertIn("random_int", bootstrap)
        self.assertIn("preg_match('/[A-Z]/'", bootstrap)
        self.assertRegex(bootstrap, re.escape("'HULK-' . implode('-', str_split($payload, 4))"))

    def test_api_has_clear_required_error_contract(self) -> None:
        api = API.read_text(encoding="utf-8")
        for marker in (
            "INVALID_CODE",
            "RESELLER_INACTIVE",
            "INVALID_HOST",
            "SERVICE_UNAVAILABLE",
            "HTTPS_REQUIRED",
        ):
            self.assertIn(marker, api)
        self.assertIn("hulk_json(['host' => $host])", api)
        self.assertIn("access_code_hash", api)

    def test_https_and_local_only_content_policy(self) -> None:
        bootstrap = BOOTSTRAP.read_text(encoding="utf-8")
        self.assertNotIn("HTTP_X_FORWARDED_PROTO", bootstrap)
        self.assertIn("SERVER_PORT", bootstrap)
        for directive in (
            "default-src 'none'",
            "script-src 'self'",
            "style-src 'self'",
            "img-src 'self'",
            "font-src 'self'",
            "form-action 'self'",
        ):
            self.assertIn(directive, bootstrap)
        self.assertNotIn("'unsafe-inline'", bootstrap)

        for page in (PORTAL, ADMIN):
            text = page.read_text(encoding="utf-8")
            self.assertIn("if (!hulk_is_https())", text)
            self.assertIn('/reseller/assets/portal.js', text)

        portal_action = PORTAL_ACTION.read_text(encoding="utf-8")
        admin_action = ADMIN_ACTION.read_text(encoding="utf-8")
        self.assertLess(
            portal_action.index("if (!hulk_is_https())"),
            portal_action.index("hulk_start_session();"),
        )
        self.assertLess(
            admin_action.index("if (!hulk_is_https())"),
            admin_action.index("hulk_start_session('admin');"),
        )

    def test_portal_and_admin_actions_require_csrf_and_authentication(self) -> None:
        portal_action = PORTAL_ACTION.read_text(encoding="utf-8")
        for action_name in (
            "login",
            "update_host",
            "rotate_code",
            "set_code",
            "change_password",
            "logout",
        ):
            self.assertIn(f"case '{action_name}'", portal_action)
        self.assertIn("hulk_verify_csrf", portal_action)
        self.assertIn("password_verify", portal_action)
        self.assertIn("hulk_require_reseller", portal_action)

        admin_action = ADMIN_ACTION.read_text(encoding="utf-8")
        for action_name in (
            "login",
            "create_reseller",
            "set_status",
            "update_host",
            "rotate_code",
            "set_code",
            "reset_password",
            "logout",
        ):
            self.assertIn(f"case '{action_name}'", admin_action)
        self.assertIn("hulk_verify_csrf", admin_action)
        self.assertIn("hulk_require_admin", admin_action)

    def test_admin_login_has_no_ten_character_minimum(self) -> None:
        admin = ADMIN.read_text(encoding="utf-8")
        admin_action = ADMIN_ACTION.read_text(encoding="utf-8")
        self.assertNotIn('minlength="10"', admin)
        self.assertNotIn("strlen($password) < 10", admin_action)
        self.assertIn("$password === ''", admin_action)

    def test_brand_and_font_assets_are_local(self) -> None:
        styles = (ASSETS / "styles.css").read_text(encoding="utf-8")
        self.assertIn('@font-face', styles)
        self.assertIn('IBM Plex Sans Arabic', styles)
        self.assertNotIn("fonts.googleapis.com", styles)
        self.assertGreater((ASSETS / "hulk-logo.png").stat().st_size, 100_000)
        self.assertGreater((ASSETS / "hulk-icon.png").stat().st_size, 1_000)

    def test_no_runtime_config_credentials_or_reseller_data_are_tracked(self) -> None:
        self.assertFalse((APP / "config.php").exists())
        combined = "\n".join(
            path.read_text(encoding="utf-8")
            for path in ROOT.rglob("*")
            if path.is_file() and path.suffix in {".php", ".sql"}
        )
        legacy_host = "3162356" + ".xyz:8080"
        self.assertNotIn(legacy_host, combined)
        self.assertNotRegex(combined, r"mysql:host=[^;]+;dbname=(?!REPLACE_ME)")


if __name__ == "__main__":
    unittest.main()
