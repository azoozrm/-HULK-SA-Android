from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PUBLIC = ROOT / "public"
BOOTSTRAP = PUBLIC / ".hulk-reseller-app/bootstrap.php"
API = PUBLIC / "api/reseller/resolve/index.php"
PORTAL = PUBLIC / "reseller/index.php"
ACTION = PUBLIC / "reseller/action.php"
SCHEMA = ROOT / "schema.sql"


class ResellerBackendContractTest(unittest.TestCase):
    def test_required_deployment_files_are_present(self) -> None:
        required = (
            BOOTSTRAP,
            API,
            PORTAL,
            ACTION,
            SCHEMA,
            PUBLIC / ".hulk-reseller-app/.htaccess",
            PUBLIC / ".hulk-reseller-app/config.example.php",
            PUBLIC / ".hulk-reseller-app/admin-domain.php",
            PUBLIC / "hulk-reseller-admin/index.php",
            PUBLIC / "hulk-reseller-admin/action.php",
            ROOT / "tests/run.php",
        )
        self.assertFalse([str(path) for path in required if not path.is_file()])

    def test_database_contains_required_reseller_fields(self) -> None:
        schema = SCHEMA.read_text(encoding="utf-8")
        for field in (
            "reseller_id",
            "reseller_name",
            "host",
            "access_code",
            "status",
        ):
            self.assertRegex(schema, rf"\b{field}\b")
        self.assertIn("access_code_hash", schema)
        self.assertIn("password_hash", schema)
        for table in ("admins", "resolver_rate_limits"):
            self.assertRegex(schema, rf"CREATE TABLE IF NOT EXISTS {table}\b")
        self.assertIn("admins_username_key_unique", schema)
        self.assertIn("resolver_rate_limits_window_idx", schema)

    def test_access_codes_use_high_entropy_canonical_format(self) -> None:
        bootstrap = BOOTSTRAP.read_text(encoding="utf-8")
        self.assertIn("ABCDEFGHJKMNPQRSTUVWXYZ23456789", bootstrap)
        self.assertIn("HULK_ACCESS_CODE_PAYLOAD_LENGTH = 16", bootstrap)
        self.assertIn("random_int", bootstrap)
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

    def test_https_detection_does_not_trust_client_forwarded_header(self) -> None:
        bootstrap = BOOTSTRAP.read_text(encoding="utf-8")
        self.assertNotIn("HTTP_X_FORWARDED_PROTO", bootstrap)
        self.assertIn("SERVER_PORT", bootstrap)
        self.assertIn("Content-Security-Policy", bootstrap)

        portal = PORTAL.read_text(encoding="utf-8")
        action = ACTION.read_text(encoding="utf-8")
        self.assertIn("if (!hulk_is_https())", portal)
        self.assertLess(
            action.index("if (!hulk_is_https())"),
            action.index("hulk_start_session();"),
        )

    def test_portal_is_limited_to_requested_actions(self) -> None:
        action = ACTION.read_text(encoding="utf-8")
        for action_name in ("login", "update_host", "rotate_code", "logout"):
            self.assertIn(f"case '{action_name}'", action)
        self.assertIn("hulk_verify_csrf", action)
        self.assertIn("password_verify", action)

    def test_owner_mutations_share_one_domain(self) -> None:
        domain = (PUBLIC / ".hulk-reseller-app/admin-domain.php").read_text(encoding="utf-8")
        owner_action = (PUBLIC / "hulk-reseller-admin/action.php").read_text(encoding="utf-8")
        bootstrap = BOOTSTRAP.read_text(encoding="utf-8")
        for function in (
            "hulk_admin_create_reseller",
            "hulk_admin_set_status",
            "hulk_admin_update_host",
            "hulk_admin_set_code",
            "hulk_admin_rotate_code",
            "hulk_admin_reset_password",
        ):
            self.assertIn(f"function {function}", domain)
            self.assertIn(f"{function}(", owner_action)
        self.assertIn("require_once __DIR__ . '/admin-domain.php'", bootstrap)
        self.assertIn("hulk_start_session('admin')", owner_action)

    def test_no_runtime_config_or_reseller_data_is_tracked(self) -> None:
        self.assertFalse((PUBLIC / ".hulk-reseller-app/config.php").exists())
        combined = "\n".join(
            path.read_text(encoding="utf-8")
            for path in ROOT.rglob("*")
            if path.is_file() and path.suffix in {".php", ".sql"}
        )
        self.assertNotIn("3162356.xyz:8080", combined)
        self.assertNotRegex(combined, r"mysql:host=[^;]+;dbname=(?!REPLACE_ME)")


if __name__ == "__main__":
    unittest.main()
