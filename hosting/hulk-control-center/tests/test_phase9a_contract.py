from __future__ import annotations

import unittest
from pathlib import Path


# hosting/hulk-control-center/tests/test_phase9a_contract.py
ROOT = Path(__file__).resolve().parents[3]
OPS = ROOT / "hosting/hulk-operations"
RES = ROOT / "backend/reseller-access/public"
CC = ROOT / "hosting/hulk-control-center"


class Phase9ALegacyReadOnlyCutoverTest(unittest.TestCase):
    """Phase 9A makes the legacy owner panels read-only without changing the
    shared authorities, the reseller self-service portal or public contracts."""

    def read(self, path: Path) -> str:
        return path.read_text(encoding="utf-8")

    def test_legacy_operations_owner_mutations_are_gated_before_authority(self) -> None:
        index = self.read(OPS / "admin/index.php")
        delete = self.read(OPS / "admin/delete_release.php")
        self.assertLess(
            index.index("ops_legacy_block_owner_mutation($section)"),
            index.index("ops_admin_handle_post("),
        )
        self.assertLess(
            delete.index("ops_legacy_block_owner_mutation('releases')"),
            delete.index("ops_delete_release($db, $admin)"),
        )
        self.assertNotIn("INSERT INTO app_admin_users", self.read(OPS / "admin/setup.php"))

    def test_legacy_reseller_owner_mutations_are_gated_before_authority(self) -> None:
        action = self.read(RES / "hulk-reseller-admin/action.php")
        self.assertLess(
            action.index("hulk_legacy_owner_allows_session_action($action)"),
            action.index("hulk_admin_create_reseller("),
        )
        for action_name in (
            "create_reseller",
            "set_status",
            "update_host",
            "set_code",
            "rotate_code",
            "reset_password",
        ):
            self.assertIn(f"'{action_name}' =>", action)

    def test_control_center_authority_is_unchanged(self) -> None:
        operations_adapter = self.read(CC / "lib/operations-adapter.php")
        for function in (
            "ops_upload_release",
            "ops_activate_release",
            "ops_disable_release",
            "ops_update_release_policy",
            "ops_delete_release",
            "ops_create_announcement",
            "ops_disable_announcement",
            "ops_update_service_status",
            "ops_toggle_feature_flag",
            "ops_save_growth",
        ):
            self.assertIn(function + "(", operations_adapter)

        reseller_adapter = self.read(CC / "lib/reseller-adapter.php")
        for function in (
            "hulk_admin_create_reseller",
            "hulk_admin_set_status",
            "hulk_admin_update_host",
            "hulk_admin_set_code",
            "hulk_admin_rotate_code",
            "hulk_admin_reset_password",
        ):
            self.assertIn(function + "(", reseller_adapter)

    def test_shared_domain_owners_are_unchanged(self) -> None:
        actions = self.read(OPS / "admin/actions.php")
        self.assertEqual(actions.count("function ops_admin_handle_post("), 1)
        domain = self.read(RES / ".hulk-reseller-app/admin-domain.php")
        for function in (
            "hulk_admin_create_reseller",
            "hulk_admin_set_status",
            "hulk_admin_update_host",
            "hulk_admin_set_code",
            "hulk_admin_rotate_code",
            "hulk_admin_reset_password",
        ):
            self.assertIn(f"function {function}", domain)

    def test_reseller_self_service_is_unchanged(self) -> None:
        portal_action = self.read(RES / "reseller/action.php")
        for action in ("login", "update_host", "rotate_code", "logout"):
            self.assertIn(f"case '{action}':", portal_action)
        self.assertTrue((RES / "reseller/index.php").is_file())

    def test_resolver_contract_is_unchanged(self) -> None:
        resolver = self.read(RES / "api/reseller/resolve/index.php")
        for marker in ("INVALID_CODE", "RESELLER_INACTIVE", "INVALID_HOST", "SERVICE_UNAVAILABLE"):
            self.assertIn(marker, resolver)

    def test_operations_public_config_contract_is_unchanged(self) -> None:
        operations = self.read(OPS / "lib/operations.php")
        endpoint = self.read(OPS / "api/app/v1/config/index.php")
        self.assertIn("'schemaVersion' => 1", operations)
        self.assertIn("ops_public_config_validator($payload)", endpoint)

    def test_release_and_apk_path_contract_is_unchanged(self) -> None:
        rules = self.read(OPS / "releases/.htaccess")
        self.assertIn("Require all denied", rules)
        actions = self.read(OPS / "admin/actions.php")
        self.assertIn("'releases/' . $fileName", actions)


if __name__ == "__main__":
    unittest.main()
