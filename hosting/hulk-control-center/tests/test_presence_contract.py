from __future__ import annotations

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = ROOT.parents[1]
OPERATIONS = REPOSITORY / "hosting" / "hulk-operations"
ANDROID_CONFIG = (
    REPOSITORY
    / "app"
    / "src"
    / "main"
    / "java"
    / "sa"
    / "hulksa"
    / "player"
    / "data"
    / "OperationsConfig.kt"
)


class PresenceContractTest(unittest.TestCase):
    def read(self, path: Path) -> str:
        return path.read_text(encoding="utf-8")

    def test_required_backend_files_exist(self) -> None:
        required = [
            ROOT / "lib" / "presence.php",
            ROOT / "lib" / "presence-http.php",
            ROOT / "migrations" / "2026-09-17-presence-v1.sql",
            ROOT / "tools" / "presence_cleanup.php",
        ]
        required.extend(
            ROOT / "api" / "app" / "v1" / "presence" / operation / "index.php"
            for operation in ("start", "heartbeat", "end")
        )
        self.assertFalse([str(path) for path in required if not path.is_file()])

    def test_schema_is_additive_and_indexed(self) -> None:
        migration = self.read(ROOT / "migrations" / "2026-09-17-presence-v1.sql")
        for table in (
            "cc_schema_migrations",
            "cc_devices",
            "cc_app_sessions",
            "cc_api_rate_limits",
        ):
            self.assertIn(f"CREATE TABLE IF NOT EXISTS {table}", migration)
        for index in (
            "uq_cc_devices_installation",
            "uq_cc_app_sessions_session",
            "uq_cc_app_sessions_token_hash",
            "idx_cc_app_sessions_online",
            "idx_cc_app_sessions_reseller",
            "idx_cc_app_sessions_device",
            "idx_cc_app_sessions_started",
            "idx_cc_app_sessions_version",
            "idx_cc_api_rate_limits_window",
        ):
            self.assertIn(index, migration)
        self.assertNotRegex(migration, re.compile(r"\b(?:DROP|ALTER)\s+TABLE\b", re.IGNORECASE))
        self.assertNotIn("account_id", migration)

    def test_exact_snapshot_and_hash_only_token_contract(self) -> None:
        migration = self.read(ROOT / "migrations" / "2026-09-17-presence-v1.sql")
        domain = self.read(ROOT / "lib" / "presence.php")
        for field in (
            "access_code_snapshot",
            "iptv_username",
            "iptv_password",
            "host_snapshot",
            "presence_token_nonce",
            "presence_token_hash",
        ):
            self.assertIn(field, migration)
        self.assertIn("hash('sha256', $token)", domain)
        self.assertNotIn("presence_token VARCHAR", migration)
        self.assertNotIn("ops_audit(", domain)

    def test_http_boundary_is_bounded_and_does_not_log_bodies(self) -> None:
        boundary = self.read(ROOT / "lib" / "presence-http.php")
        self.assertIn("CC_PRESENCE_MAX_BODY_BYTES = 16384", boundary)
        self.assertIn("REQUEST_METHOD", boundary)
        self.assertIn("CONTENT_LENGTH", boundary)
        self.assertIn("Authorization", self.read(ROOT / "README.md"))
        self.assertIn("HTTP_AUTHORIZATION", boundary)
        self.assertIn("Presence request failed.", boundary)
        self.assertNotRegex(boundary, re.compile(r"error_log\([^;]*(?:body|payload|exception)", re.IGNORECASE))

    def test_presence_failures_cannot_mutate_existing_authorities(self) -> None:
        domain = self.read(ROOT / "lib" / "presence.php")
        self.assertNotIn("UPDATE resellers", domain)
        self.assertNotIn("INSERT INTO resellers", domain)
        self.assertNotIn("app_releases", domain)
        self.assertNotIn("app_service_status", domain)
        self.assertNotIn("app_settings", domain)

    def test_start_retry_has_unique_key_race_recovery(self) -> None:
        domain = self.read(ROOT / "lib" / "presence.php")
        self.assertIn("for ($attempt = 0; $attempt < 2; $attempt++)", domain)
        self.assertIn("function cc_presence_retryable_database_conflict", domain)
        self.assertIn("['23000', '40001']", domain)
        self.assertIn("[1062, 1205, 1213]", domain)
        self.assertIn("uq_cc_app_sessions_session", self.read(ROOT / "migrations" / "2026-09-17-presence-v1.sql"))

    def test_discovery_is_optional_and_schema_v1_stays_compatible(self) -> None:
        config = self.read(OPERATIONS / "config.example.php")
        operations = self.read(OPERATIONS / "lib" / "operations.php")
        parser = self.read(ANDROID_CONFIG)
        self.assertIn("'enabled' => false", config)
        self.assertIn("$payload['presence'] = $presence", operations)
        self.assertIn("'schemaVersion' => 1", operations)
        self.assertIn('val root = JSONObject(rawJson)', parser)
        self.assertNotIn('getJSONObject("presence")', parser)
        self.assertNotIn('"presence"', parser)

    def test_retention_is_bounded_and_online_is_independent(self) -> None:
        domain = self.read(ROOT / "lib" / "presence.php")
        cleanup = self.read(ROOT / "tools" / "presence_cleanup.php")
        self.assertIn("cleanup_batch_size", domain)
        self.assertIn("LIMIT ' . $batch", domain)
        self.assertIn("ended_at", domain)
        self.assertIn("last_seen_at", domain)
        self.assertIn("PHP_SAPI !== 'cli'", cleanup)
        self.assertIn("cc_presence_is_online", domain)
        self.assertNotIn("cc_presence_cleanup(", self.read(ROOT / "lib" / "presence-http.php"))

    def test_public_legacy_routes_are_not_reimplemented(self) -> None:
        presence_sources = "\n".join(
            self.read(path)
            for path in (ROOT / "lib").glob("presence*.php")
        )
        self.assertNotIn("/api/reseller/resolve/", presence_sources)
        self.assertNotIn("/reseller/", presence_sources)
        self.assertNotIn("releases/", presence_sources)


if __name__ == "__main__":
    unittest.main()
