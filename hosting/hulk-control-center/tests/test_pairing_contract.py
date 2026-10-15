from __future__ import annotations

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = ROOT.parents[1]
OPERATIONS = REPOSITORY / "hosting" / "hulk-operations"


class PairingContractTest(unittest.TestCase):
    def read(self, path: Path) -> str:
        return path.read_text(encoding="utf-8")

    def test_required_backend_files_exist(self) -> None:
        required = [
            ROOT / "lib" / "pairing.php",
            ROOT / "lib" / "pairing-http.php",
            ROOT / "migrations" / "2026-10-14-pairing-v1.sql",
            ROOT / "tools" / "pairing_cleanup.php",
            ROOT / "tools" / "pairing_selftest.php",
            ROOT / "pair" / "index.php",
            ROOT / "views" / "pairing.php",
            ROOT / "assets" / "pairing.css",
            ROOT / "assets" / "pairing.js",
        ]
        required.extend(
            ROOT / "api" / "app" / "v1" / "pairing" / operation / "index.php"
            for operation in ("create", "claim", "ack", "cancel")
        )
        self.assertFalse([str(path) for path in required if not path.is_file()])

    def test_migration_has_no_plaintext_credential_columns(self) -> None:
        migration = self.read(ROOT / "migrations" / "2026-10-14-pairing-v1.sql")
        self.assertIn("cc_pairing_requests", migration)
        self.assertIn("cc_pairing_admission", migration)
        self.assertIn("envelope_blob", migration)
        for forbidden in ("iptv_password", "iptv_username", "access_code TEXT", "password TEXT"):
            self.assertNotIn(forbidden, migration)
        self.assertNotIn("access_code VARCHAR", migration)

    def test_envelope_is_bound_and_secret_sizes_enforced(self) -> None:
        lib = self.read(ROOT / "lib" / "pairing.php")
        self.assertIn("function cc_pairing_seal_credentials", lib)
        self.assertIn("function cc_pairing_unseal_credentials", lib)
        self.assertIn("cc_pairing_envelope_aad", lib)
        self.assertIn("HULKPAIR1", lib)
        self.assertIn("sodium_crypto_aead_xchacha20poly1305_ietf_encrypt", lib)
        self.assertIn("aes-256-gcm", lib)
        # Exactly 32 decoded bytes per envelope key; >= 32 for HMAC secrets.
        self.assertIn("strlen($key) !== 32", lib)
        self.assertIn("strlen($claimSecret) < 32", lib)
        self.assertIn("strlen($rateSecret) < 32", lib)
        # No plaintext fallback path exists.
        self.assertNotIn("plaintext_fallback", lib)
        self.assertIn("PAIRING_UNAVAILABLE", lib)

    def test_idempotent_create_and_retryable_delivery(self) -> None:
        lib = self.read(ROOT / "lib" / "pairing.php")
        self.assertIn("function cc_pairing_claim_token", lib)
        self.assertIn("IDEMPOTENCY_MISMATCH", lib)
        self.assertIn("CREATE_CONFLICT", lib)
        # Claim is non-consuming: the only state writes inside cc_pairing_claim are
        # EXPIRED, CANCELLED (envelope invalid) and the delivery counter.
        claim = lib[lib.index("function cc_pairing_claim("):lib.index("function cc_pairing_ack(")]
        self.assertNotIn("'ACKED'", claim)
        self.assertIn("delivery_count = delivery_count + 1", claim)
        self.assertIn("ENVELOPE_INVALID", claim)

    def test_ack_means_receipt_and_cancel_is_idempotent(self) -> None:
        lib = self.read(ROOT / "lib" / "pairing.php")
        ack = lib[lib.index("function cc_pairing_ack("):lib.index("function cc_pairing_cancel(")]
        self.assertIn("'ACKED'", ack)
        self.assertIn("terminal_reason = \\'ACKED\\'", ack)
        cancel = lib[lib.index("function cc_pairing_cancel("):lib.index("function cc_pairing_approve(")]
        self.assertIn("'CANCELLED'", cancel)
        self.assertIn("cc_pairing_is_terminal", cancel)

    def test_provider_parity_and_bounded_transport(self) -> None:
        lib = self.read(ROOT / "lib" / "pairing.php")
        self.assertIn("user_info", lib)
        self.assertIn("!== 1", lib)
        # Blank/missing status accepted; non-empty non-Active rejected.
        self.assertIn("$accountStatus !== '' && strcasecmp($accountStatus, 'Active') !== 0", lib)
        self.assertIn("CURLOPT_FOLLOWLOCATION => false", lib)
        self.assertIn("CURLOPT_RESOLVE => [$resolve]", lib)
        self.assertIn("CURLOPT_SSL_VERIFYPEER => true", lib)
        self.assertIn("cc_host_health_public_ip", lib)
        self.assertIn("DNS_RUNTIME_UNAVAILABLE", lib)
        self.assertNotIn("gethostbyname", lib)
        self.assertNotIn("dns_get_record($host", lib)

    def test_approval_commit_rechecks_without_io_lock(self) -> None:
        lib = self.read(ROOT / "lib" / "pairing.php")
        approve = lib[lib.index("function cc_pairing_approve("):lib.index("function cc_pairing_deny(")]
        # Provider probe happens before the commit transaction is opened.
        probe_index = approve.index("$probeFn(")
        begin_index = approve.index("beginTransaction()")
        self.assertLess(probe_index, begin_index)
        self.assertIn("HOST_ROTATED", approve)
        self.assertIn("cc_pairing_enabled()", approve)
        self.assertIn("WHERE pairing_id = :pairing_id AND state = \\'PENDING\\'", approve)

    def test_fresh_authority_is_used_at_commit_and_delivery(self) -> None:
        lib = self.read(ROOT / "lib" / "pairing.php")
        self.assertIn("function cc_pairing_authority(bool $fresh = true)", lib)
        claim = lib[lib.index("function cc_pairing_claim("):lib.index("function cc_pairing_ack(")]
        self.assertIn("cc_pairing_enabled()", claim)
        self.assertIn("PAIRING_DISABLED", claim)
        ack = lib[lib.index("function cc_pairing_ack("):lib.index("function cc_pairing_cancel(")]
        self.assertNotIn("cc_pairing_enabled()", ack)
        cancel = lib[lib.index("function cc_pairing_cancel("):lib.index("function cc_pairing_approve(")]
        self.assertNotIn("cc_pairing_enabled()", cancel)

    def test_capacity_admission_is_race_safe_and_bounded(self) -> None:
        lib = self.read(ROOT / "lib" / "pairing.php")
        create = lib[lib.index("function cc_pairing_create("):lib.index("function cc_pairing_duplicate_key(")]
        self.assertIn("cc_pairing_admission", create)
        self.assertIn("FOR UPDATE", create)
        self.assertIn("max_retained_rows", create)
        self.assertIn("PAIRING_CAPACITY", lib)
        self.assertIn("CC_PAIRING_CAPACITY_RETRY_SECONDS = 60", lib)
        # No network IO inside the admission transaction.
        self.assertNotIn("curl_", create)
        self.assertNotIn("file_get_contents", create)

    def test_cleanup_bounds_and_drain(self) -> None:
        lib = self.read(ROOT / "lib" / "pairing.php")
        cleanup = lib[lib.index("function cc_pairing_cleanup("):lib.index("* Page session, CSRF and page rendering helpers")]
        self.assertIn("CC_PAIRING_CLEANUP_BATCH = 200", lib)
        self.assertIn("state = \\'EXPIRED\\'", cleanup)
        self.assertIn("DELETE FROM cc_pairing_requests", cleanup)
        self.assertIn("$drain", cleanup)
        tool = self.read(ROOT / "tools" / "pairing_cleanup.php")
        self.assertIn("--drain", tool)
        self.assertIn("handoff_remaining=%s", tool)
        self.assertIn("budget_exceeded=%s", tool)
        self.assertIn("counts_confirmed=%s", tool)

    def test_rate_scopes_are_additive_and_windowed(self) -> None:
        lib = self.read(ROOT / "lib" / "pairing.php")
        http = self.read(ROOT / "lib" / "pairing-http.php")
        page = self.read(ROOT / "pair" / "index.php")
        haystack = lib + http + page
        self.assertIn("function cc_pairing_rate_limit", lib)
        self.assertIn("cc_api_rate_limits", lib)
        for scope in (
            "pairing_create_min",
            "pairing_create_hour",
            "pairing_claim_req",
            "pairing_claim_ip",
            "pairing_ack_req",
            "pairing_cancel_req",
            "pairing_page_lookup",
            "pairing_page_approve_ip",
            "pairing_page_approve_req",
        ):
            self.assertIn(scope, haystack, scope)
        presence = self.read(ROOT / "lib" / "presence.php")
        # Presence scopes, budgets and cleanup are untouched by pairing.
        self.assertNotIn("pairing_", presence)

    def test_tv_api_json_scope_and_page_html_scope(self) -> None:
        http = self.read(ROOT / "lib" / "pairing-http.php")
        self.assertIn("application/json", http)
        self.assertIn("BODY_TOO_LARGE", http)
        self.assertIn("METHOD_NOT_ALLOWED", http)
        self.assertIn("cc_pairing_dispatch", http)
        page = self.read(ROOT / "pair" / "index.php")
        self.assertIn("cc_pairing_render_page", page)
        self.assertIn("csrf_token", page)
        # No IPTV value is stored in the page session.
        self.assertNotIn("$_SESSION['access", page)
        self.assertNotIn("$_SESSION[\"access", page)
        self.assertNotIn("$_SESSION['password", page)
        self.assertNotIn("$_SESSION['iptv", page)
        view = self.read(ROOT / "views" / "pairing.php")
        self.assertIn("cc_e(", view)
        self.assertIn('dir="ltr"', view)
        self.assertIn("تمت الموافقة على الطلب", view)
        self.assertIn("ارجع الى التلفزيون لمتابعة تسجيل الدخول", view)
        self.assertIn("رفض الطلب", view)
        self.assertIn("تغيير الرمز", view)
        # R07 splits the show-password control into the visible label and an sr-only suffix.
        self.assertIn("pairing-show-password", view)
        self.assertIn("اظهار", view)
        self.assertNotIn("remember", view.lower())

    def test_page_never_claims_android_login_completed(self) -> None:
        view = self.read(ROOT / "views" / "pairing.php")
        self.assertNotIn("تم تسجيل الدخول", view)
        self.assertNotIn("اكتمل تسجيل الدخول", view)
        self.assertIn("تفعيل التلفزيون", view)

    def test_page_never_renders_credential_values_back(self) -> None:
        view = self.read(ROOT / "views" / "pairing.php")
        for input_id in ("pairing-access-code", "pairing-username", "pairing-password"):
            match = re.search(r'<input id="' + input_id + r'"[^>]*>', view)
            self.assertIsNotNone(match, input_id)
            self.assertNotIn("value=", match.group(0), input_id)
        page = self.read(ROOT / "pair" / "index.php")
        # The page stores no session state directly; CSRF and per-form/attempt
        # proofs are handled inside lib/pairing.php (hashes only).
        session_writes = set(re.findall(r"\$_SESSION\[[^\]]+\]\s*=", page))
        self.assertEqual(session_writes, set())

    def test_discovery_is_advertisement_only(self) -> None:
        operations = self.read(OPERATIONS / "lib" / "operations.php")
        self.assertIn("function ops_pairing_discovery", operations)
        self.assertIn("$payload['pairing'] = $pairing", operations)
        config = self.read(ROOT / "config.example.php")
        self.assertIn("'pairing' => [", config)
        self.assertIn("'enabled' => false", config)
        self.assertIn("envelope_keys", config)
        self.assertIn("active_envelope_key_id", config)
        # Operations remains credential-free.
        self.assertNotIn("iptv", operations)

    def test_no_secret_or_token_material_in_logs_or_urls(self) -> None:
        lib = self.read(ROOT / "lib" / "pairing.php")
        for forbidden in (
            "error_log($accessCode",
            "error_log($password",
            "error_log($claimToken",
            "verificationUrl' => 'https://hulksa.com/control-center/pair/?",
        ):
            self.assertNotIn(forbidden, lib)
        self.assertIn("#C=", lib)
        http = self.read(ROOT / "lib" / "pairing-http.php")
        self.assertNotIn("error_log($", http.replace("error_log('HULK Control Center pairing request failed.')", ""))

    def test_no_production_hosting_claims(self) -> None:
        lib = self.read(ROOT / "lib" / "pairing.php")
        production_db_marker = bytes([104, 117, 108, 107, 110, 106, 99, 120, 95]).decode("ascii")
        self.assertNotIn(production_db_marker, lib)
        self.assertNotIn("-----BEGIN", lib)

    def test_token_identity_is_secret_proven(self) -> None:
        lib = self.read(ROOT / "lib" / "pairing.php")
        snippet = lib[lib.index("function cc_pairing_build_create_response"):lib.index("function cc_pairing_create(")]
        self.assertIn("$clientSecret", snippet)
        self.assertNotIn("client_request_id", snippet)

    def test_wire_base64url_rules_are_canonical(self) -> None:
        lib = self.read(ROOT / "lib" / "pairing.php")
        self.assertIn("function cc_pairing_b64url_decode_strict", lib)
        self.assertIn("hash_equals($canonical, $value)", lib)
        self.assertIn("canonical unpadded base64url", lib)
        self.assertIn("cc_pairing_optional_text", lib)

    def test_https_guard_and_secure_cookie(self) -> None:
        page = self.read(ROOT / "pair" / "index.php")
        self.assertIn("PHP_SAPI === 'cli-server'", page)
        self.assertIn("HULK_PAIRING_LOCAL_HARNESS", page)
        self.assertIn("in_array($remoteAddress, ['127.0.0.1', '::1'], true)", page)
        self.assertIn("cc_pairing_render_page(['step' => 'insecure', 'notice' => null, 'csrf' => ''])", page)
        lib = self.read(ROOT / "lib" / "pairing.php")
        cookie = lib[lib.index("function cc_pairing_start_session"):lib.index("function cc_pairing_csrf_token")]
        self.assertIn("'secure' => true", cookie)
        self.assertIn("application/x-www-form-urlencoded", page)
        self.assertIn("CC_PAIRING_PAGE_MAX_BODY_BYTES", page)
        self.assertIn("php://input", page)
        self.assertIn("Retry-After", page)

    def test_native_pdo_safe_default_suite(self) -> None:
        fixture = self.read(ROOT / "tests" / "pairing-db-fixture.php")
        self.assertIn("if (!class_exists('PDO', false)) {", fixture)
        self.assertIn("CC_PAIRING_TEST_FIXTURE_ACTIVE", fixture)
        tests = self.read(ROOT / "tests" / "pairing.php")
        self.assertIn("HULK_PAIRING_TEST_FIXTURE", tests)
        self.assertIn("HULK_PAIRING_TEST_DSN", tests)
        runner = ROOT / "tests" / "run-mock.php"
        self.assertTrue(runner.is_file())
        run = self.read(ROOT / "tests" / "run.php")
        self.assertIn("pairing.php", run)
        native = self.read(ROOT / "tests" / "pairing-native.php")
        self.assertIn("HULK_PAIRING_TEST_DSN", native)

    def test_auth_before_per_request_rate_allocation(self) -> None:
        http = self.read(ROOT / "lib" / "pairing-http.php")
        claim = http[http.index("if ($operation === 'claim')"):http.index("if ($operation === 'ack')")]
        self.assertLess(claim.index("pairing_claim_ip"), claim.index("cc_pairing_authenticate"))
        self.assertLess(claim.index("cc_pairing_authenticate"), claim.index("pairing_claim_req"))
        ack = http[http.index("if ($operation === 'ack')"):http.index("cc_pairing_cancel(cc_pairing_db()")]
        self.assertLess(ack.index("cc_pairing_authenticate"), ack.index("pairing_ack_req"))

    def test_provider_non_2xx_is_not_approval(self) -> None:
        lib = self.read(ROOT / "lib" / "pairing.php")
        probe = lib[lib.index("function cc_pairing_probe_provider("):lib.index("function cc_pairing_default_http_get(")]
        self.assertIn("HTTP_STATUS_", probe)
        self.assertIn("HTTP_AUTH_STATUS", probe)
        self.assertLess(probe.index("$status < 200 || $status >= 300"), probe.index("json_decode($body"))

    def test_deny_checks_expiry_and_bounds_fail_closed(self) -> None:
        lib = self.read(ROOT / "lib" / "pairing.php")
        deny = lib[lib.index("function cc_pairing_deny("):lib.index("function cc_pairing_start_session(")]
        self.assertIn("expires_at", deny)
        self.assertIn("terminal_reason = \\'EXPIRED\\'", deny)
        self.assertIn("max_statement_time", lib)
        self.assertIn("max_execution_time", lib)
        self.assertNotIn("A rejected deadline statement must not abort", lib)
        self.assertIn("cc_pairing_rate_limit_cleanup", lib)

    def test_form_proof_binding_is_server_validated(self) -> None:
        page = self.read(ROOT / "pair" / "index.php")
        self.assertIn("cc_pairing_form_proof_verify", page)
        self.assertNotIn("$_SESSION['pairing_request_id']", page)
        view = self.read(ROOT / "views" / "pairing.php")
        self.assertIn('name="request_id"', view)
        self.assertIn('name="form_token"', view)
        lib = self.read(ROOT / "lib" / "pairing.php")
        self.assertIn("function cc_pairing_form_proof_issue", lib)
        self.assertIn("function cc_pairing_form_proof_verify", lib)
        self.assertIn("CC_PAIRING_FORM_PROOF_MAX", lib)

    def test_reject_and_change_forms_exclude_credentials(self) -> None:
        view = self.read(ROOT / "views" / "pairing.php")
        forms = re.findall(r"<form\b.*?</form>", view, re.S)
        deny_forms = [form for form in forms if 'value="deny"' in form]
        change_forms = [form for form in forms if 'value="change"' in form]
        self.assertTrue(deny_forms)
        self.assertTrue(change_forms)
        for form in deny_forms + change_forms:
            self.assertNotIn('name="password"', form)
            self.assertNotIn('name="access_code"', form)
            self.assertNotIn('name="username"', form)
        approve_forms = [form for form in forms if 'value="approve"' in form]
        self.assertTrue(approve_forms)
        self.assertIn('name="password"', approve_forms[0])
        self.assertIn("required", approve_forms[0])

    def test_provider_target_and_streaming_limit(self) -> None:
        lib = self.read(ROOT / "lib" / "pairing.php")
        self.assertIn("function cc_pairing_provider_target", lib)
        self.assertIn("function cc_pairing_response_collector", lib)
        self.assertIn("RESPONSE_TOO_LARGE", lib)
        self.assertIn("CURLOPT_WRITEFUNCTION", lib)
        self.assertNotIn("CURLOPT_RETURNTRANSFER => true", lib)
        self.assertIn("$defaultPort = ($scheme === 'http' && $port === 80) || ($scheme === 'https' && $port === 443)", lib)

    def test_live_clock_rotation_recheck_and_php_host_label(self) -> None:
        lib = self.read(ROOT / "lib" / "pairing.php")
        self.assertIn("$commitNow = $clock();", lib)
        self.assertIn("cc_pairing_host_display_label", lib)
        self.assertNotIn("trustedHostDisplayLabel", lib)
        approve = lib[lib.index("function cc_pairing_approve("):lib.index("function cc_pairing_deny(")]
        self.assertIn("access_code_hash", approve)

    def test_drain_semantics_and_bounds(self) -> None:
        lib = self.read(ROOT / "lib" / "pairing.php")
        self.assertIn("DISABLED_PURGE", lib)
        self.assertIn("handoff_remaining", lib)
        self.assertIn("cc_pairing_maintenance_database", lib)
        self.assertIn("cc_pairing_acquire_maintenance_lock", lib)
        self.assertIn("CC_PAIRING_MAINTENANCE_SECONDS", lib)
        tool = self.read(ROOT / "tools" / "pairing_cleanup.php")
        self.assertIn("--drain", tool)
        self.assertIn("handoff_remaining=%s", tool)
        self.assertIn("budget_exceeded", lib)
        self.assertIn("CC_PAIRING_MAINTENANCE_FINALIZER_ALLOWANCE_SECONDS", lib)
        self.assertIn("class CcPairingBudget", lib)
        self.assertIn("function cc_pairing_budget", lib)
        self.assertIn("drain refused", tool)

    def test_budget_is_carried_through_every_dispatch(self) -> None:
        lib = self.read(ROOT / "lib" / "pairing.php")
        budget_class = lib[lib.index("final class CcPairingBudget"):lib.index("function cc_pairing_budget")]
        for marker in ("allowWork", "allowSummary", "summaryRefused", "workRefused", "remaining", "statementBoundSeconds"):
            self.assertIn(marker, budget_class)
        cleanup = lib[lib.index("function cc_pairing_cleanup("):lib.index("function cc_pairing_destroy_envelope_sql(")]
        self.assertIn("$guarded", cleanup)
        self.assertIn("allowSummary", cleanup)
        self.assertIn("SELECT SUM(state IN (\\'PENDING\\', \\'APPROVED\\')) AS handoff_count, COUNT(*) AS total_count", cleanup)
        self.assertIn("CcPairingBudget $budget = null", lib)
        self.assertIn("!$budget->allowWork()", lib)
        tool = self.read(ROOT / "tools" / "pairing_cleanup.php")
        for marker in ("--budget-seconds", "total_elapsed", "lock_released", "rolled_back", "Pairing maintenance refused", "statement_bound"):
            self.assertIn(marker, tool)

    def test_finalization_outcomes_and_partial_accounting(self) -> None:
        lib = self.read(ROOT / "lib" / "pairing.php")
        budget_class = lib[lib.index("final class CcPairingBudget"):lib.index("function cc_pairing_budget")]
        self.assertIn("allowFinalizer", budget_class)
        self.assertIn("finalizerRefused", budget_class)
        release = lib[lib.index("function cc_pairing_release_maintenance_lock"):lib.index("function cc_pairing_release_maintenance_lock") + 2000]
        for outcome in ("released", "not_held", "refused", "failed"):
            self.assertIn("'" + outcome + "'", release)
        cleanup = lib[lib.index("function cc_pairing_cleanup("):lib.index("function cc_pairing_destroy_envelope_sql(")]
        for marker in ("aborted_reason", "rate_cleanup_stopped", "rate_cleanup_unconfirmed", "cc_pairing_rate_limit_cleanup_outcome"):
            self.assertIn(marker, cleanup)
        tool = self.read(ROOT / "tools" / "pairing_cleanup.php")
        for marker in (
            "HULK_PAIRING_MAINTENANCE_SUPERVISED",
            "proc_open",
            "proc_terminate",
            "child_overrun",
            "connection_closed",
            "lock_release=",
            "hard_deadline=",
        ):
            self.assertIn(marker, tool)

    def test_story_copy_normalization_and_no_terminal_stops(self) -> None:
        view = self.read(ROOT / "views" / "pairing.php")
        self.assertIn("هنا ، ثم", view)
        self.assertNotIn("الاشتراك، ثم", view)
        self.assertNotIn("الطلب. بعد", view)
        self.assertNotIn("تسجيل الدخول.</p>", view)
        story = re.search(r"<p>(ادخل بيانات اشتراكك هنا.*?)</p>", view, re.S)
        self.assertIsNotNone(story)
        self.assertNotIn(".", story.group(1))

    def test_readiness_independence_and_page_gate(self) -> None:
        lib = self.read(ROOT / "lib" / "pairing.php")
        self.assertIn("DUPLICATE_KEY_MATERIAL", lib)
        self.assertIn("PRESENCE_SECRET_REUSE", lib)
        self.assertIn("CRYPTO_UNAVAILABLE", lib)
        page = self.read(ROOT / "pair" / "index.php")
        self.assertIn("cc_pairing_readiness($settings)", page)
        approve = lib[lib.index("function cc_pairing_approve("):lib.index("function cc_pairing_deny(")]
        self.assertIn("cc_pairing_readiness", approve)

    def test_local_fonts_and_no_external_csp_exceptions(self) -> None:
        css = self.read(ROOT / "assets" / "pairing.css")
        self.assertIn("@font-face", css)
        self.assertNotIn("fonts.googleapis.com", css)
        self.assertNotIn("@import", css)
        view = self.read(ROOT / "views" / "pairing.php")
        self.assertNotIn("fonts.googleapis.com", view)
        self.assertNotIn("fonts.gstatic.com", view)
        self.assertIn("font-src 'self'", view)
        for font in ("regular", "medium", "semibold", "bold"):
            self.assertTrue((ROOT / "assets" / "fonts" / ("ibm_plex_sans_arabic_" + font + ".ttf")).is_file())

    def test_page_icon_family_is_page_owned(self) -> None:
        view = self.read(ROOT / "views" / "pairing.php")
        self.assertNotIn("cc_icon(", view)
        self.assertIn("cc_pairing_icon(", view)
        icons_path = ROOT / "views" / "pairing-icons.php"
        self.assertTrue(icons_path.is_file())
        icons = self.read(icons_path)
        for name in ("tv", "close", "refresh", "arrow_back", "check_circle", "error_outline"):
            self.assertIn("'" + name + "' =>", icons)
        # Exact Rounded-set geometry source is recorded in the icon module.
        self.assertIn("materialiconsround", icons)
        css = self.read(ROOT / "assets" / "pairing.css")
        self.assertNotIn("#FF746C", css)
        self.assertNotIn("--pairing-danger", css)

    def test_action_selectors_match_emitted_icon_class(self) -> None:
        css = self.read(ROOT / "assets" / "pairing.css")
        self.assertIn(".pairing-button{", css)
        self.assertIn(".pairing-page svg{display:block;width:19px;height:19px;flex-shrink:0}", css)
        self.assertNotIn(".pairing-button__icon .icon {", css)
        self.assertNotIn(".pairing-button--primary .icon {", css)

    def test_notice_order_padding_and_copy_normalization(self) -> None:
        view = self.read(ROOT / "views" / "pairing.php")
        notice_index = view.index('class="pairing-notice"')
        notice_block = view[notice_index:view.index("</div>", notice_index)]
        copy_index = notice_block.index("cc_pairing_display_copy")
        icon_index = notice_block.index("cc_pairing_icon('error_outline')")
        self.assertLess(copy_index, icon_index)
        css = self.read(ROOT / "assets" / "pairing.css")
        self.assertIn("padding:12px 14px", css)
        for path in (ROOT / "views" / "pairing.php", ROOT / "pair" / "index.php"):
            authored = self.read(path)
            # The display-copy normalizer legitimately contains the source characters it maps.
            authored = authored.replace("['\u0623' => '\u0627', '\u0625' => '\u0627', '\u0622' => '\u0627',", "")
            self.assertIsNone(
                re.search("[\u0622\u0623\u0625\u064b-\u0652\u0670]", authored),
                str(path),
            )


if __name__ == "__main__":
    unittest.main()
