from __future__ import annotations

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = ROOT.parents[1]


class ReleaseDeleteParityTests(unittest.TestCase):
    def read(self, path: Path) -> str:
        return path.read_text(encoding="utf-8")

    def test_delete_action_is_allow_listed_only_for_the_releases_module(self) -> None:
        adapter = self.read(ROOT / "lib" / "operations-adapter.php")
        block = re.search(r"\$allowedActions\s*=\s*\[(.*?)\];", adapter, re.DOTALL)
        self.assertIsNotNone(block, "the Operations action allow-list must remain explicit")
        actions = block.group(1)

        self.assertEqual(
            actions.count("'delete_release'"),
            1,
            "delete_release must appear exactly once in the allow-list",
        )
        releases_line = re.search(r"'releases'\s*=>\s*\[([^\]]*)\]", actions)
        self.assertIsNotNone(releases_line)
        self.assertIn("'delete_release'", releases_line.group(1))

        for module in ("service", "announcements", "features", "growth"):
            module_line = re.search(rf"'{module}'\s*=>\s*\[([^\]]*)\]", actions)
            self.assertIsNotNone(module_line)
            self.assertNotIn("delete_release", module_line.group(1))

    def test_control_center_dispatches_to_the_authoritative_operations_delete(self) -> None:
        adapter = self.read(ROOT / "lib" / "operations-adapter.php")
        self.assertIn("'delete_release' => ops_delete_release($db, $admin)", adapter)
        self.assertNotIn("DELETE FROM app_releases", adapter)
        self.assertNotIn("unlink(", adapter)
        self.assertNotIn("app_releases SET", adapter)

    def test_control_center_post_is_csrf_protected_before_dispatch(self) -> None:
        index = self.read(ROOT / "index.php")
        csrf = index.find("cc_require_csrf()")
        dispatch = index.find("cc_operations_handle_post(")
        self.assertGreaterEqual(csrf, 0)
        self.assertGreaterEqual(dispatch, 0)
        self.assertLess(csrf, dispatch, "CSRF must be enforced before any release mutation dispatch")

    def test_release_view_exposes_an_owner_destructive_delete_control(self) -> None:
        view = self.read(ROOT / "views" / "operations.php")
        self.assertIn('name="action" value="delete_release"', view)
        self.assertIn('name="csrf_token"', view)
        self.assertIn('name="release_id"', view)
        self.assertIn("destructive-zone", view)
        self.assertIn("button--danger", view)
        self.assertIn('data-confirm="حذف هذا الإصدار نهائيًا', view)
        self.assertNotIn("apk_path", view)
        self.assertNotIn("apk_sha256", view)

    def test_legacy_operations_route_is_retired_and_control_center_owns_deletion(self) -> None:
        actions = self.read(REPOSITORY / "hosting" / "hulk-operations" / "admin" / "actions.php")
        legacy = self.read(REPOSITORY / "hosting" / "hulk-operations" / "admin" / "delete_release.php")
        self.assertEqual(actions.count("function ops_delete_release("), 1)
        self.assertIn("RELEASE_DELETED", actions)
        self.assertIn("ops_reset_release_settings($db)", actions)
        self.assertIn("ops_legacy_redirect(", legacy)
        self.assertNotIn("ops_delete_release", legacy)
        self.assertNotIn("DELETE FROM app_releases", legacy)
        self.assertNotIn("FOR UPDATE", legacy)

    def test_release_delete_surface_is_narrow_and_schema_free(self) -> None:
        matches = []
        for path in sorted(ROOT.rglob("*.php")):
            if "tests" in path.relative_to(ROOT).parts:
                continue
            if "delete_release" in self.read(path):
                matches.append(path.relative_to(ROOT).as_posix())
        self.assertEqual(matches, ["lib/operations-adapter.php", "views/operations.php"])

        adapter = self.read(ROOT / "lib" / "operations-adapter.php")
        for forbidden in ("CREATE TABLE", "ALTER TABLE", "DROP TABLE", "INSERT INTO app_releases"):
            self.assertNotIn(forbidden, adapter)

        migrations = sorted(path.name for path in (ROOT / "migrations").glob("*.sql"))
        self.assertEqual(
            migrations,
            [
                "2026-09-17-presence-v1.sql",
                "2026-09-19-diagnostics-v1.sql",
                "2026-09-19-host-health-v1.sql",
            ],
        )
        for name in migrations:
            self.assertNotIn("app_releases", self.read(ROOT / "migrations" / name))


if __name__ == "__main__":
    unittest.main()
