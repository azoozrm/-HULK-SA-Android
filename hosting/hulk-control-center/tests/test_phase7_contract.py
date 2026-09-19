from __future__ import annotations

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = ROOT.parents[1]


class PhaseSevenContractTests(unittest.TestCase):
    def read(self, path: Path) -> str:
        return path.read_text(encoding="utf-8")

    def test_required_phase_seven_files_exist(self) -> None:
        required = [
            ROOT / "migrations" / "2026-09-19-diagnostics-v1.sql",
            ROOT / "migrations" / "2026-09-19-host-health-v1.sql",
            ROOT / "lib" / "diagnostics.php",
            ROOT / "lib" / "diagnostics-http.php",
            ROOT / "lib" / "host-health.php",
            ROOT / "lib" / "phase7-read-model.php",
            ROOT / "tools" / "host_health.php",
            ROOT / "tools" / "host_health_dns.php",
            ROOT / "api" / "app" / "v1" / "diagnostics" / "events" / "index.php",
            ROOT / "views" / "host-health.php",
            ROOT / "views" / "diagnostics.php",
            ROOT / "views" / "analytics.php",
            ROOT / "tests" / "phase7.php",
        ]
        self.assertFalse([str(path) for path in required if not path.is_file()])

    def test_migrations_are_additive_and_do_not_duplicate_host_authority(self) -> None:
        diagnostics = self.read(ROOT / "migrations" / "2026-09-19-diagnostics-v1.sql")
        health = self.read(ROOT / "migrations" / "2026-09-19-host-health-v1.sql")
        combined = diagnostics + "\n" + health
        self.assertIn("CREATE TABLE IF NOT EXISTS cc_diagnostic_events", diagnostics)
        self.assertIn("CREATE TABLE IF NOT EXISTS cc_host_health_checks", health)
        self.assertIn("'POLICY_BLOCKED'", health)
        self.assertNotIn("cc_host_health_targets", combined)
        self.assertNotRegex(combined, re.compile(r"\b(?:DROP|ALTER)\s+TABLE\b", re.IGNORECASE))
        for forbidden in ("access_code", "iptv_username", "iptv_password", "bearer", "presence_token"):
            self.assertNotIn(forbidden, combined.lower())

    def test_host_runner_is_cli_only_bounded_and_uses_current_normalized_hosts(self) -> None:
        domain = self.read(ROOT / "lib" / "host-health.php")
        runner = self.read(ROOT / "tools" / "host_health.php")
        dns_worker = self.read(ROOT / "tools" / "host_health_dns.php")
        self.assertIn("PHP_SAPI !== 'cli'", runner)
        self.assertIn("set_time_limit", runner)
        self.assertIn("default_socket_timeout", runner)
        self.assertIn("RES_OPTIONS=attempts:1 timeout:2", runner)
        self.assertIn("cc_host_health_apply_database_deadlines", runner)
        self.assertIn("PDO::ATTR_TIMEOUT", domain)
        self.assertIn("hulk_normalize_host", domain)
        self.assertIn("CURLOPT_CONNECTTIMEOUT_MS", domain)
        self.assertIn("CURLOPT_TIMEOUT_MS", domain)
        self.assertIn("CURLOPT_FOLLOWLOCATION => false", domain)
        self.assertIn("CURLOPT_RESOLVE", domain)
        self.assertIn("proc_open", domain)
        self.assertIn("proc_terminate", domain)
        self.assertNotIn("dns_get_record", domain)
        self.assertIn("PHP_SAPI !== 'cli'", dns_worker)
        self.assertIn("dns_get_record", dns_worker)
        self.assertIn("FILTER_FLAG_NO_PRIV_RANGE", domain)
        self.assertIn("FILTER_FLAG_NO_RES_RANGE", domain)
        self.assertIn("max_hosts_per_run", domain)
        self.assertIn("max_runtime_seconds", domain)
        self.assertIn("GET_LOCK", domain)
        self.assertIn("RELEASE_LOCK", domain)
        self.assertIn("cc_host_health_candidate_window", domain)
        self.assertIn("hash('sha256', $host)", domain)
        self.assertNotRegex(domain, re.compile(r"\b(?:UPDATE|INSERT\s+INTO)\s+resellers\b", re.IGNORECASE))
        self.assertNotIn("$_GET", domain)
        self.assertNotIn("$_POST", domain)

    def test_diagnostics_accepts_only_typed_bounded_session_authorized_events(self) -> None:
        domain = self.read(ROOT / "lib" / "diagnostics.php")
        boundary = self.read(ROOT / "lib" / "diagnostics-http.php")
        migration = self.read(ROOT / "migrations" / "2026-09-19-diagnostics-v1.sql")
        self.assertIn("CC_DIAGNOSTICS_MAX_BODY_BYTES = 4096", boundary)
        self.assertIn("cc_presence_authorized_session", domain)
        self.assertIn("cc_diagnostic_event_types", domain)
        self.assertIn("cc_diagnostic_error_codes", domain)
        self.assertIn("cc_presence_assert_keys", domain)
        self.assertIn("for ($attempt = 0; $attempt < 2; $attempt++)", domain)
        self.assertIn("cc_presence_retryable_database_conflict", domain)
        self.assertIn("UNIQUE KEY uq_cc_diagnostic_events_event", migration)
        self.assertIn("ON UPDATE RESTRICT ON DELETE CASCADE", migration)
        self.assertNotRegex(boundary, re.compile(r"error_log\([^;]*(?:body|payload|exception)", re.IGNORECASE))
        forbidden_fields = (
            "message", "details", "stack", "log", "accessCode", "iptvUsername",
            "iptvPassword", "token", "credential", "history",
        )
        for field in forbidden_fields:
            self.assertNotIn(f"'{field}' =>", domain)

    def test_retention_and_transient_health_semantics_are_explicit(self) -> None:
        diagnostics = self.read(ROOT / "lib" / "diagnostics.php")
        health = self.read(ROOT / "lib" / "host-health.php")
        read_model = self.read(ROOT / "lib" / "phase7-read-model.php")
        self.assertIn("retention_days", diagnostics)
        self.assertIn("cleanup_batch_size", diagnostics)
        self.assertIn("retention_days", health)
        self.assertIn("cleanup_batch_size", health)
        self.assertIn("TRANSIENT_FAILURE", read_model)
        self.assertIn("UNREACHABLE", read_model)
        self.assertIn("NOT_CHECKED", read_model)

    def test_read_models_and_dashboard_keep_unavailable_distinct_from_zero(self) -> None:
        index = self.read(ROOT / "index.php")
        dashboard = self.read(ROOT / "lib" / "dashboard.php")
        view = self.read(ROOT / "views" / "dashboard.php")
        read_model = self.read(ROOT / "lib" / "phase7-read-model.php")
        for module in ("host-health", "diagnostics", "analytics"):
            self.assertIn(f"'{module}'", index)
        for definition in (
            "host_health_summary",
            "application_failures",
            "session_trend",
            "version_adoption_trend",
        ):
            self.assertIn(definition, dashboard)
            self.assertIn(definition, read_model)
        self.assertIn("available", view)
        self.assertNotIn("APK downloads", read_model)
        self.assertNotIn("app_releases", read_model)

    def test_phase_seven_does_not_modify_android_source(self) -> None:
        self.assertTrue((REPOSITORY / "app" / "src").is_dir())
        phase_seven_sources = "\n".join(
            self.read(path)
            for path in (ROOT / "lib").glob("*health*.php")
        ) + "\n" + self.read(ROOT / "lib" / "diagnostics.php")
        self.assertNotIn("app/src/", phase_seven_sources)


if __name__ == "__main__":
    unittest.main()
