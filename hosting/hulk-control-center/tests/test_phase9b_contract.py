from __future__ import annotations

import re
import unittest
from pathlib import Path


# hosting/hulk-control-center/tests/test_phase9b_contract.py
ROOT = Path(__file__).resolve().parents[3]
OPS = ROOT / "hosting/hulk-operations"
RES = ROOT / "backend/reseller-access/public"
CC = ROOT / "hosting/hulk-control-center"

OPS_REDIRECT_ROUTES = (
    OPS / "index.php",
    OPS / "admin/index.php",
    OPS / "admin/login.php",
    OPS / "admin/logout.php",
    OPS / "admin/setup.php",
    OPS / "admin/delete_release.php",
    OPS / "admin/redirect.php",
)
RES_REDIRECT_ROUTES = (
    RES / "hulk-reseller-admin/index.php",
    RES / "hulk-reseller-admin/action.php",
    RES / "hulk-reseller-admin/redirect.php",
)


class Phase9BLegacyRedirectCutoverTest(unittest.TestCase):
    """Phase 9B retires the legacy owner entry points and replaces the Phase 9A
    read-only experience with deterministic 302/303 redirects to the equivalent
    HULK SA Control Center modules, without changing any shared authority or
    public/runtime contract."""

    def read(self, path: Path) -> str:
        return path.read_text(encoding="utf-8")

    def ops_redirect(self) -> str:
        return self.read(OPS / "admin/redirect.php")

    def res_redirect(self) -> str:
        return self.read(RES / "hulk-reseller-admin/redirect.php")

    # 1-4: Operations redirect mapping.
    def test_operations_root_and_sections_map_to_exact_control_center_modules(self) -> None:
        redirect = self.ops_redirect()
        self.assertIn("ops_legacy_redirect(", self.read(OPS / "index.php"))
        self.assertIn("ops_legacy_redirect(", self.read(OPS / "admin/index.php"))
        self.assertIn("'/control-center/'", redirect)
        for module in ("releases", "announcements", "service", "features", "growth", "audit"):
            self.assertIn(f"'/control-center/{module}/'", redirect)
        # Only an allow-listed section can select a destination.
        self.assertIn(
            "in_array($section, ops_legacy_control_center_modules(), true) ? $section : 'dashboard'",
            redirect,
        )

    # 5-6: 302 for GET/navigation, 303 for POST.
    def test_operations_and_reseller_status_codes(self) -> None:
        for redirect in (self.ops_redirect(), self.res_redirect()):
            self.assertIn("=== 'POST' ? 303 : 302", redirect)
            self.assertIn("header('Location: ' . ", redirect)

    # 7: legacy Operations login no longer authenticates.
    def test_legacy_operations_login_no_longer_authenticates(self) -> None:
        login = self.read(OPS / "admin/login.php")
        self.assertIn("ops_legacy_control_center_login_path()", login)
        self.assertNotIn("password_verify", login)
        self.assertNotIn("$_POST", login)
        self.assertIn("'/control-center/login.php'", self.ops_redirect())

    # 8: legacy delete-release cannot call ops_delete_release().
    def test_legacy_delete_release_cannot_call_authority(self) -> None:
        delete = self.read(OPS / "admin/delete_release.php")
        self.assertNotIn("ops_delete_release", delete)
        self.assertIn("ops_legacy_control_center_path('releases')", delete)

    # 9: legacy web setup cannot create an admin.
    def test_legacy_web_setup_cannot_create_admin(self) -> None:
        setup = self.read(OPS / "admin/setup.php")
        self.assertNotIn("INSERT", setup)
        self.assertNotIn("app_admin_users", setup)
        self.assertNotIn("ops_db(", setup)
        self.assertIn("ops_legacy_control_center_login_path()", setup)

    # 10: legacy reseller owner GET redirects to the resellers module.
    def test_legacy_reseller_owner_get_redirects_to_resellers(self) -> None:
        index = self.read(RES / "hulk-reseller-admin/index.php")
        self.assertIn("hulk_legacy_owner_redirect(", index)
        self.assertIn("hulk_legacy_owner_control_center_url()", index)
        self.assertIn("'/control-center/resellers/'", self.res_redirect())

    # 11: legacy reseller owner POST cannot execute any hulk_admin_* mutation.
    def test_legacy_reseller_owner_post_cannot_execute_mutations(self) -> None:
        action = self.read(RES / "hulk-reseller-admin/action.php")
        for marker in ("hulk_db(", "hulk_verify_csrf", "hulk_start_session", "$_SESSION"):
            self.assertNotIn(marker, action)
        for mutation in (
            "hulk_admin_create_reseller",
            "hulk_admin_set_status",
            "hulk_admin_update_host",
            "hulk_admin_set_code",
            "hulk_admin_rotate_code",
            "hulk_admin_reset_password",
        ):
            self.assertNotIn(mutation, action)

    # 12: a direct historical login POST cannot authenticate.
    def test_legacy_owner_login_post_routes_to_control_center_login(self) -> None:
        action = self.read(RES / "hulk-reseller-admin/action.php")
        self.assertIn("$action === 'login' ? hulk_legacy_owner_login_url()", action)
        self.assertIn("'/control-center/login.php'", self.res_redirect())

    # 13: shared Operations authority unchanged.
    def test_shared_operations_authority_is_unchanged(self) -> None:
        actions = self.read(OPS / "admin/actions.php")
        self.assertEqual(actions.count("function ops_admin_handle_post("), 1)
        self.assertEqual(actions.count("function ops_delete_release("), 1)
        adapter = self.read(CC / "lib/operations-adapter.php")
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
            self.assertIn(function + "(", adapter)

    # 14: shared reseller authority unchanged.
    def test_shared_reseller_authority_is_unchanged(self) -> None:
        domain = self.read(RES / ".hulk-reseller-app/admin-domain.php")
        adapter = self.read(CC / "lib/reseller-adapter.php")
        for function in (
            "hulk_admin_create_reseller",
            "hulk_admin_set_status",
            "hulk_admin_update_host",
            "hulk_admin_set_code",
            "hulk_admin_rotate_code",
            "hulk_admin_reset_password",
        ):
            self.assertIn(f"function {function}", domain)
            self.assertIn(function + "(", adapter)

    # 15: reseller self-service unchanged.
    def test_reseller_self_service_is_unchanged(self) -> None:
        portal_action = self.read(RES / "reseller/action.php")
        for action in ("login", "update_host", "rotate_code", "logout"):
            self.assertIn(f"case '{action}':", portal_action)
        self.assertTrue((RES / "reseller/index.php").is_file())

    # 16: resolver contract unchanged.
    def test_resolver_contract_is_unchanged(self) -> None:
        resolver = self.read(RES / "api/reseller/resolve/index.php")
        for marker in ("INVALID_CODE", "RESELLER_INACTIVE", "INVALID_HOST", "SERVICE_UNAVAILABLE"):
            self.assertIn(marker, resolver)

    # 17: Operations config endpoint unchanged.
    def test_operations_public_config_contract_is_unchanged(self) -> None:
        operations = self.read(OPS / "lib/operations.php")
        endpoint = self.read(OPS / "api/app/v1/config/index.php")
        self.assertIn("'schemaVersion' => 1", operations)
        self.assertIn("ops_public_config_validator($payload)", endpoint)

    # 18: APK/release serving paths unchanged.
    def test_release_and_apk_path_contract_is_unchanged(self) -> None:
        rules = self.read(OPS / "releases/.htaccess")
        self.assertIn("Require all denied", rules)
        actions = self.read(OPS / "admin/actions.php")
        self.assertIn("'releases/' . $fileName", actions)

    # 19: Control Center route registry intact.
    def test_control_center_route_registry_is_intact(self) -> None:
        routes = self.read(CC / "lib/routes.php")
        self.assertIn("function cc_modules(): array", routes)
        for module in (
            "dashboard",
            "resellers",
            "releases",
            "service",
            "announcements",
            "features",
            "growth",
            "audit",
        ):
            self.assertIn(f"'{module}' =>", routes)

    # 20: the routing cutover introduces no schema/database/Android change.
    def test_routing_cutover_adds_no_schema_database_or_android_change(self) -> None:
        sources = "\n".join(self.read(path) for path in OPS_REDIRECT_ROUTES + RES_REDIRECT_ROUTES)
        lowered = sources.lower()
        for marker in (
            "create table",
            "alter table",
            "insert into",
            "delete from",
            "drop table",
            "truncate",
            "applicationid",
            "androidmanifest",
        ):
            self.assertNotIn(marker, lowered)

    # 21: no bootstrap/database access is required to perform the redirect.
    def test_redirects_require_no_bootstrap_or_database(self) -> None:
        for path in OPS_REDIRECT_ROUTES + RES_REDIRECT_ROUTES:
            source = self.read(path)
            for marker in ("bootstrap.php", "ops_db(", "hulk_db(", "new PDO", "PDO(", "session_start"):
                self.assertNotIn(marker, source, f"{path.name} must not require {marker}")

    # 22: soak is recorded as OWNER_WAIVED, not PASS.
    def test_soak_is_recorded_as_owner_waived(self) -> None:
        runbook = self.read(CC / "PHASE-9-READ-ONLY-CUTOVER.md")
        self.assertIn("SOAK: OWNER_WAIVED", runbook)
        self.assertIn("OWNER_WAIVED", runbook)
        self.assertNotIn("soak: pass", runbook.lower())


if __name__ == "__main__":
    unittest.main()
