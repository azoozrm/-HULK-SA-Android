# HULK SA — TV Sign-in Pairing Backend (effective contract + owner runbook)

Status: LOCAL backend candidate **R06** (`tv-mobile-pairing-20261014`, backend round).
Disabled by default. Nothing here is a production-readiness claim; production enablement, live
credentials and E2E remain BLOCKED until the owner-run qualification below is completed.

R06 completes the maintenance finalization and accounting (confirmed release outcomes, connection-close fallback, partial-progress accounting, hard supervisor bound) plus strict worker-ready evidence; R05 closed the R04 maintenance-budget defect (whole-invocation per-statement budget) and the last story-copy normalization; R04 closed the three remaining R03 items (whole-run budget + supported flavor bounds; atomic first rate row + coordinated concurrency; page SVG/notice/copy fidelity); R03 corrected the R02 defects without changing the accepted result:
TV shows the pairing code/QR/expiry (Android round), the browser activates the TV, and the TV
authenticates through the existing owners. History (preflight R01–R03 and backend R01/R02
packets) stays unchanged under `/srv/hulk-android/reports/2026/tv-mobile-pairing-20261014/`.
R03 highlights: native-PDO-safe test suite with an explicit mock runner, pairing-owned bounded
DB connections with fail-closed deadlines, authentication before per-request rate allocation,
provider non-2xx rejection, deny-after-expiry truthfulness, actual body bounds and pre-session
insecure rejection, and the adopted notice/icon/copy family.

## 1. Scope implemented

| Surface | Path |
|---|---|
| Migration | `hosting/hulk-control-center/migrations/2026-10-14-pairing-v1.sql` |
| Core library | `hosting/hulk-control-center/lib/pairing.php` |
| TV JSON transport | `hosting/hulk-control-center/lib/pairing-http.php` |
| TV API | `hosting/hulk-control-center/api/app/v1/pairing/{create,claim,ack,cancel}/index.php` |
| Public page | `hosting/hulk-control-center/pair/index.php` + `views/pairing.php` + `views/pairing-icons.php` |
| Page assets | `hosting/hulk-control-center/assets/pairing.{css,js}` + `assets/fonts/*.ttf` |
| Tools | `tools/pairing_cleanup.php`, `tools/pairing_selftest.php` |
| Config example | `hosting/hulk-control-center/config.example.php` (`pairing` block, disabled) |
| Discovery (advertisement) | `hosting/hulk-operations/lib/operations.php` optional `pairing` object |
| Tests | `tests/pairing.php` (pure + behavioral selector), `tests/pairing-native.php`, `tests/pairing-native-rate-worker.php`, `tests/pairing-db-fixture.php` (conditional mock), `tests/run-mock.php` (explicit mock runner), `tests/test_pairing_contract.py` |

Test selection (R03): the default `tests/run.php` runs pure checks plus behavioral checks against
a real disposable DB when `HULK_PAIRING_TEST_DSN` (+ user/pass/config) is provided; the mock is
loaded only by `tests/run-mock.php` on machines without PDO. The fixture declares classes
conditionally, so native PDO is never shadowed or early-bound.

No Android source is touched by this round.

## 2. Storage, crypto, tokens (R02-corrected)

- `cc_pairing_requests` has **no plaintext credential columns**; credentials exist transiently in
  memory and at rest only inside the AEAD envelope (AAD bound to request id, expiry and key id).
- Envelope keys decode to **exactly 32 bytes**; HMAC secrets decode to **≥ 32 bytes**; all
  material independent (duplicates rejected) and never equal to Presence secrets
  (`PRESENCE_SECRET_REUSE`). Placeholder/missing/wrong-size → readiness fails; **no plaintext
  fallback ever**.
- **Claim tokens are secret-proven**: `claimToken = base64url(HMAC-SHA256(claim_secret,
  request_id ∥ 0x00 ∥ clientSecret))`; the stored `claim_token_hash` and the create/replay
  response derive from the **client secret**, so a replay with the matching client secret returns
  the identical token (R01 defect corrected). Raw tokens/secrets are never persisted.
- Wire client secrets are **canonical unpadded base64url** (43 chars, exactly 32 decoded bytes,
  `-`/`_` allowed); standard base64 characters, padding, wrong lengths and noncanonical trailing
  bits are rejected. Config keys keep standard base64 (separate contract).
- Readiness also requires the selected crypto primitive (sodium XChaCha20-Poly1305 or OpenSSL
  AES-256-GCM) and is enforced on the **page GET, approval and claim** paths, not only on
  selftest. Claim-secret rotation stays gated by disable + `--drain` to `handoff_remaining=0`.

## 3. Enable authority

Single authority: `pairing.enabled` in the private Control Center config. `cc_pairing_enabled()`
re-reads that same file (cache-bypassing) and is consulted at approval start/commit and claim
delivery authorization; unreadable → disabled. Operations `pairing` discovery is advertisement
only. `ack`/`cancel` are always processed.

## 4. Delivery / ACK / cancel semantics

- `create` is idempotent per `(installation_id, client_request_id)` with the client-secret proof;
  an idempotency id alone authorizes nothing (`IDEMPOTENCY_MISMATCH`, `CREATE_CONFLICT`).
- `claim` never changes state; delivery is retryable while APPROVED until ACK/cancel/expiry,
  bounded by `max_deliveries`; the enable authority is re-read at delivery time.
- `ack` is the authenticated terminal receipt (idempotent), independent of later Android login.
- `cancel` closes PENDING/APPROVED and destroys the envelope; terminal states are idempotent.
- Cancel committed before claim authorization prevents new delivery; a claim authorized first
  leaves APPROVED and later cancel can still close it (in-flight bytes cannot be retracted; the
  TV discards them for a locally invalidated attempt). Unobserved remote disable cannot invalidate
  an already-authorized response; local ownership decides handoff. No push/revocation service.
- **Live clock**: approval re-reads server time at commit after provider IO and row-lock waits;
  claim/ack/cancel refresh time after locking. A request that expired during provider IO or lock
  latency is rejected at commit and stores nothing (`PAIRING_EXPIRED`, envelope never sealed).
- **Commit rechecks** now cover state, expiry, fresh enable, reseller status, host, and
  **access-code/hash rotation** (`INVALID_ACCESS_CODE` / `RESELLER_INACTIVE` / `HOST_ROTATED`).
  Provider IO never runs inside the DB transaction.

## 5. Provider transport (R02-corrected)

- `cc_pairing_provider_target()` preserves scheme-specific explicit ports (only the scheme's own
  default port is omitted), exact path prefixes and root hosts (`/player_api.php`, no `//`), and
  keeps the pin authority (`host:port:address`) in exact agreement with the URL authority,
  including IPv4 literals and bracketed IPv6.
- The response is collected through a **streaming write callback with a hard 64 KiB ceiling**;
  overflow aborts transfer, clears the body and fails closed (`SERVICE_BLOCKED`,
  `RESPONSE_TOO_LARGE`) instead of truncating an unbounded buffer.
- **HTTP acceptance matches the Xtream request contract**: only 2xx responses are parsed for
  `auth`/`status`. A 302/401/503 body that still contains success JSON is never approval:
  401/403 map to `INVALID_CREDENTIALS`; other non-2xx map to `SERVICE_BLOCKED`
  (`HTTP_STATUS_*`). Blank/missing status acceptance and all target/pin/port/path/TLS/
  no-redirect/public-address rules are unchanged.
- TLS verification, public-address-only checks, no redirects/proxy and bounded DNS/connect/total
  execution are preserved. The bounded DNS path must be qualified in the actual web SAPI before
  enablement (CLI evidence is insufficient).

## 6. Page and form binding (R03-corrected)

- **HTTPS is guarded before any session/form/credential handling**: non-HTTPS requests render an
  insecure notice with HTTP 400 and start no session/CSRF state (verified: no Set-Cookie on the
  insecure path). The only local harness exemption is explicit synthetic loopback testing:
  `PHP_SAPI === 'cli-server'` + `REMOTE_ADDR` in {127.0.0.1, ::1} + `HULK_PAIRING_LOCAL_HARNESS=1`.
  Pairing session cookies are **always Secure**, `HttpOnly`, `SameSite=Strict`, scoped to
  `/control-center/pair/`, independent of admin/Presence sessions.
- POST requires `application/x-www-form-urlencoded`, a declared-length bound AND an **actual
  bounded read of `php://input`** (≤ 16384 bytes; oversized actual bodies get 413 even without
  `Content-Length`). Method guard: GET/POST only (405 + Allow). CSRF verified before any action;
  failures return 419. Rate limiting returns 429 with a true `Retry-After`.
- **Per-form/attempt proof binding**: every rendered confirm view carries a random per-render
  `form_token` (43-char base64url; only its SHA-256 is stored in the scoped session, ≤ 5 live
  proofs, 30-minute TTL). Approve/deny must present the matching `request_id` + `form_token`;
  the action always targets the TV that form was rendered for. A mutable session id is never the
  authorization (verified over real HTTP: lookup A then B in one session; submitting A's form
  denied A while B stayed PENDING). A fresh GET never resumes a previous confirm, so a new QR
  fragment cannot silently leave an old TV selected.
- **Reject/Change are separate forms with no credential fields** (deny carries the proof, change
  carries only CSRF); reject/change work with empty credential inputs and never submit password
  data with those actions. Deny is serialized under the row lock with a fresh clock: a logically
  expired request transitions to EXPIRED (envelope destroyed) and is reported as expired, never
  as a successful denial; a concurrent approval is reported truthfully (verified natively).
- Approved state shows only `تمت الموافقة على الطلب` and
  `ارجع الى التلفزيون لمتابعة تسجيل الدخول`. No admin shell, ads or implementation jargon.
- The page session stores only CSRF/proof hashes; IPTV values never enter the session, browser
  storage, URLs, logs or fixtures. Output is escaped; responses are no-store.

## 7. Design fidelity (R04-corrected)

- Local IBM Plex Sans Arabic assets (`assets/fonts/ibm_plex_sans_arabic_{regular,medium,semibold,bold}.ttf`,
  copied from the accepted app font family) with `@font-face`; **no Google Fonts import and no
  external CSP exceptions** (`style-src 'self'; font-src 'self'`).
- The HULK SA mark is the repository's accepted mark (`assets/hulk-sa-mark.svg`, the same HS path
  as the accepted app icon family). Page actions/errors use a **page-owned icon set**
  (`views/pairing-icons.php`) whose path data is copied verbatim from the official Material Design
  Icons **ROUNDED** artifacts — the same source the pinned Compose
  `material-icons-extended` (`Icons.Rounded.*`, BOM 2026.06.00) is generated from. Exact source
  files and hashes are retained in the R04 evidence (`tooling/icon-sources/*.svg`). Mapping:
  TV activation `tv` (hardware/tv), deny `close` (navigation/close), change/restart `refresh`
  (navigation/refresh), continue `arrow_back` (navigation/arrow_back), approved `check_circle`
  (action/check_circle), errors/warnings/unavailable `error_outline` (alert/error_outline),
  insecure `lock` (action/lock), info `info` (action/info). The global admin `cc_icon()` set and
  all original app assets are untouched.
- Notices use the accepted shared atoms: surface `#11120D`, 1dp gold border at .45 alpha, ivory
  text, **one gold semantic glyph physically LEFT after the caption (caption physically RIGHT in
  RTL)**, **12dp horizontal / 9dp vertical padding**, 8dp gap, 12dp radius — no red/pink error
  skin. Action CSS targets the emitted `.pairing-icon` class with the existing 17dp glyph slot
  and primary/secondary/quiet tints. New authored copy is normalized (`أ/إ/آ → ا`, no diacritics,
  no terminal punctuation, spaced Arabic commas).
- Mobile one-column; desktop keeps the form at physical RIGHT and the explanation at physical
  LEFT. Caption physically RIGHT with its single icon physically LEFT (one source gap owner
  `--pairing-action-gap`), complete captions, no focus scale/size jump, no decorative drop shadow.

## 8. Capacity, rates, cleanup (R04-corrected)

- `max_retained_rows` counts all requests; admission is count+insert under a short pairing-only
  lock. At capacity new creates get 503 `PAIRING_CAPACITY` with `retryAfterSeconds=60`; replay and
  ACK/cancel/expiry keep working.
- Additive rate scopes in `cc_api_rate_limits` (`pairing_create_min/hour`, `pairing_claim_req/ip`,
  `pairing_ack_req`, `pairing_cancel_req`, `pairing_page_lookup`, `pairing_page_approve_ip/req`)
  with atomic counting; Presence scopes/budgets/cleanup untouched.
- **Authentication precedes per-request counters**: claim applies the claim IP guard first, then
  authenticates the bearer token, then allocates `pairing_claim_req`; ack/cancel authenticate
  before `pairing_ack_req`/`pairing_cancel_req`. Invalid UUIDs/tokens allocate no per-request
  rate rows (including while disabled). Every scope name is ≤ 32 chars and DB-backed.
- **Atomic first row / increment**: rate limiting is ONE upsert statement
  (`INSERT ... ON DUPLICATE KEY UPDATE` with window-reset logic and `LAST_INSERT_ID(expr)`
  carrying this connection's assigned attempt count). Concurrent absent-key requests cannot lose
  a count or fail a bare duplicate INSERT; retries are bounded to two retryable conflicts
  (deadlock/lock-timeout/duplicate). Transaction ownership stays with the caller; budgets,
  windows, retry timing and exactly-once counting are preserved.
- Pairing owns a bounded, **scoped cleanup for its own rate scopes only**
  (`cc_pairing_rate_limit_cleanup`): expired windows are deleted in ≤ 200-row batches while live
  windows and all Presence rows are preserved. It is invoked by the cleanup tool; Presence's
  shared cleanup code is not modified.
- Cleanup ordinary run: ≤ 200 expiry/envelope-clearing transitions + ≤ 200 metadata deletions +
  ≤ 200 pairing rate-window deletions. **Drain** (`--drain`, refused while pairing is enabled)
  purges ALL non-terminal handoff rows with `CANCELLED`/`DISABLED_PURGE` semantics in bounded
  batches; completion is `handoff_remaining=0` and never requires deleting 24 h-retained terminal
  metadata (`metadata_retained` reported separately).
- **One whole-invocation budget with a hard owner (R06)**: pairing uses its own connection
  factory (independent of `cc_db()`/Presence) with flavor detection via
  `PDO::ATTR_SERVER_VERSION`. MariaDB is the supported flavor: `innodb_lock_wait_timeout=3`,
  `lock_wait_timeout=5`, and `max_statement_time=5`, which bounds reads **and DML**. MySQL's
  `max_execution_time` bounds read-only SELECT only, so **MySQL fails closed explicitly**; there
  is no catch-and-ignore anywhere. `CcPairingBudget` carries ONE absolute soft budget (started
  before any setup) through connection setup, each deadline `SET`, lock acquisition, **every
  batch SELECT/UPDATE/DELETE**, every rate-cleanup statement, the **single combined summary
  query** (`SUM(state IN …), COUNT(*)`) and finalization; a statement is dispatched only while
  its verified bound plus the summary and finalizer allowance still fit, and work that cannot fit
  is refused before it starts. MariaDB timeout checks are cooperative, so the tool's
  **supervisor** is the actual hard wall-clock owner: it spawns the child with the same
  interpreter configuration, enforces `--budget-seconds=N` minus a 0.5 s kill grace (SIGTERM then
  SIGKILL) and reports `child_overrun`/`total_elapsed` truthfully — an overrun is never a
  complete result. Finalization reports the **actual** `RELEASE_LOCK` outcome
  (`released`/`not_held`/`refused`/`failed`); when the dispatch cannot fit, the child closes the
  connection and reports the `connection_closed` fallback (server-side release on session end,
  natively proven bounded), never a false `released`. Aborted statements keep the confirmed
  partial counts (`aborted`/`aborted_reason`), a refused rate-cleanup statement keeps the
  already-committed deletions (`rate_cleanup_stopped`), and an unknown in-flight outcome is
  reported `rate_cleanup_unconfirmed` rather than zero. The tool rolls back any outer transaction
  on every exit and prints after finalization with refreshed flags. Each batch update is one
  statement, so a stop or an aborted DML leaves a known retry-safe state. Actual hosting
  scheduler/runtime/capacity proof stays BLOCKED.

## 9. TV API outcomes

Unchanged from the R01 contract except: `claim` delivery now re-checks readiness/enable with a
fresh clock; envelope-invalid still transitions to `CANCELLED`/`ENVELOPE_INVALID` or returns 503
when the transition cannot commit; `PAIRING_CAPACITY` documented; controlled codes and HTTP
statuses as before. JSON rules (HTTPS, POST, JSON content type, ≤ 4096 bytes, no-store, bearer)
apply to the TV API only; the page uses HTML form responses.

## 10. Local validation status (R06)

**Native disposable fixture (reused from R03, state rechecked).** Isolated loopback-only stack
under the task evidence directory (no root, no system install): PHP 8.3.6 CLI (pdo, pdo_mysql,
pdo_sqlite, mysqli, ctype, curl, mbstring, fileinfo, sodium, openssl) and MariaDB 10.11.14 on
127.0.0.1:33077 with a dedicated datadir/socket/user and synthetic keys. Exact setup/versions/
commands are retained in the R03 evidence; R05 re-verified the server and migrated schema before
running.

- PHP lint (native 8.3 and fallback 8.2): 0 errors.
- Control Center PHP suite on native PHP + MariaDB: **PASS 593 checks**. Per-case raw traces are
  retained in `logs/pairing-native-trace.jsonl`, including the R06 finalization/accounting cases:
  - `RELEASE_LOCK` outcomes reported from the server result (`released`, then `not_held` on a
    second attempt); a budget that cannot fit the finalizer reports `refused`; a connection killed
    during release reports `failed` and the lock is free afterwards;
  - abrupt process death (SIGKILL): the named lock is released by connection close within a
    measured bounded fallback (12 ms observed against the 5 s test bound);
  - partial rate deletion followed by a refused statement keeps the confirmed count
    (`rate_limits_deleted=1`, `rate_cleanup_stopped=true`, counts unconfirmed) instead of zero;
  - a row-locked cleanup UPDATE aborts at the lock-wait bound (3.0 s observed), leaves the row and
    envelope untouched (`aborted=true`, `aborted_reason=PDOException`) and succeeds on retry;
  - the tool reports `aborted=true`, exits 1 with `lock_release=released`, and its retry completes
    with `aborted=false counts_confirmed=true`;
  - supervisor hard bound: a silent-listener connect stall is killed at the hard deadline
    (11.8 s wall for a 12 s budget) with `child_overrun=true`,
    `lock_release=unknown_child_killed`, `incomplete=true` and exit 0 — never reported complete.
  - readiness-gated concurrency traces retain each worker's ready record (isolation), result and
    exit: absent key 10/10 → attempts=10; existing key → 10; over-budget exit 10 → 11; last-slot
    exits 0,7,7,7 → rows=100.
  - budget refusal at setup (`--budget-seconds=6`): refused before any work, invocations stay
    inside the budget (wall 72 ms), lock free afterwards;
  - late drain purge UPDATE refused after the SELECT completed: no purge, request still
    APPROVED with its envelope intact (`purged=0`, `budget_exceeded=true`,
    `counts_confirmed=false`);
  - late ordinary expire UPDATE refused: row untouched (`expired=0`);
  - partial work then summary refusal: applied expiry is counted, both summary values are
    `unconfirmed` (`expired=1`, counts null);
  - 12 s tool invocation completes inside its stated budget with `total_elapsed`, truthful flags
    and `lock_released=true`;
  - overlap: a held advisory lock makes the second invocation skip without work and the lock is
    free after the winner releases;
  - aborted blocked DML: state/envelope/delivery_count unchanged; retry succeeds;
  - worker-ready coordination (connections prepared before the barrier opens): absent key
    10/10 exits 0 → `attempts=10`; existing key 9 workers → `attempts=10`; over-budget
    contender exit 10 → `attempts=11`; every worker reported a valid isolation
    (`REPEATABLE-READ`); last capacity slot: 4 coordinated creates → exactly one row at the
    bound (exits 0,7,7,7; rows=100);
  - all R04/R03 cases retained: MariaDB plan supported / MySQL fail-closed, blocked admission
    lock ~3 s, `SLEEP(30)` and long DML aborted, disable-during-provider-IO, replay/claim/ACK/
    cancel/expiry, rotation, invalid-auth nonallocation, live/Presence rate windows.
- Mock runner (`tests/run-mock.php`, no-PDO fallback): PASS 458 checks.
- Operations PHP suite on native PHP: PASS 232 checks. Control Center Python: 98 OK. Operations
  Python: 24 OK. `node --check` + navigation: OK.
- Native HTTP (`php -S` + real MariaDB + proxy-trusted HTTPS flag): page copy renders the
  normalized story; notice renders text-before-glyph; invalid-auth claim → 404 with **no**
  `pairing_claim_req` row.
- Tools: selftest `ready=true envelope_round_trip=ok`; default tool run and `--budget-seconds=6`
  refusal logs; drain refused while enabled; disabled drain completes with
  `handoff_remaining=0`.
- Icon artifacts: the eight Rounded source SVGs are retained with SHA-256 in the R04 evidence
  (unchanged in R05).

**Still BLOCKED (production-only, not claimed)**: the actual hosting web-SAPI/opcache
interpreter behavior; the killable DNS-helper execution path in production; production schema/
config/key/cron/capacity state; hosting scheduler throughput; real MySQL DML-bound execution
(design fails closed; only the pure plan was exercised).

## 11. Owner-run hosting qualification and runbook (BLOCKED until executed)

The lab has no hosting access. Execute on the hosting account (cPanel/phpMyAdmin or equivalent);
return only statuses/counts, never secrets or customer data.

1. **Upload** the changed Control Center files (including `assets/fonts/*.ttf`) and the minimal
   Operations changes to their existing paths under `public_html/`.
2. **Migration**: import `migrations/2026-10-14-pairing-v1.sql` with a deployment-only schema
   account; expect `cc_pairing_requests` + `cc_pairing_admission`, no existing table altered.
3. **Keys/secrets**: generate locally: `claim_secret`, `rate_limit_secret` (base64 ≥ 32 random
   bytes each) and an `envelope_keys` value (base64 of exactly 32 random bytes); install in the
   private config outside the web root with `pairing.enabled=false`; verify non-reachability.
4. **Self-test**: `php tools/pairing_selftest.php` → expect `enabled=false ready=true
   envelope_round_trip=ok`; additionally qualify the **web SAPI** with one real request-path
   check (a temporary authenticated probe with synthetic values) covering interpreter/opcache
   and the killable DNS helper.
5. **Cron**: add the pairing cleanup entry (once per minute target) with the private config env.
6. **Read-only probes**: API GET 405; page GET renders (HTTPS), missing CSRF 419; fonts served.
7. **Enablement**: set `pairing.enabled=true` and enable the Operations advertisement in the same
   window.
8. **Disable/rollback**: `pairing.enabled=false`, advertisement off, then
   `php tools/pairing_cleanup.php --drain` until `handoff_remaining=0`; verify claim returns
   `PAIRING_DISABLED` while ack/cancel still close attempts. Keep additive tables.

CLI include tests and the local fixture do NOT qualify the web SAPI, opcache fresh authority or
the DNS helper; those production proofs remain BLOCKED and must be captured in this runbook.
