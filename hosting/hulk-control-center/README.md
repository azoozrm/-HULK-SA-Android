# HULK SA Control Center

Production foundation for the owner-facing administration application at:

`https://hulksa.com/control-center/`

The application combines the Arabic RTL owner experience with the existing Operations and reseller authorities. Its Dashboard and owner modules read authoritative Presence session/device telemetry without changing the Android application or creating a competing reseller authority.

## Runtime

- PHP 8.1 or newer.
- PDO MySQL.
- HTTPS.
- Apache/LiteSpeed with `.htaccess` enabled.
- Existing HULK Operations schema containing `app_admin_users`.

There is no frontend build pipeline and no external runtime dependency. CSS, JavaScript, and the approved HULK SA mark are local.

## Configuration

Copy `config.example.php` to an untracked `config.php`, or preferably store the configuration outside the document root and expose its absolute path through:

`HULK_CONTROL_CENTER_CONFIG`

Configure both named authorities:

- `databases.control`: the existing HULK Operations/control-plane database and administrative audit authority.
- `databases.reseller`: the existing reseller database owning reseller identity, state, password hash, host and current access code.

Do not commit runtime credentials. `config.php` is ignored and denied by `.htaccess`.

The `presence` configuration owns the 60-second heartbeat, 180-second Online TTL, 180-day session/device retention, cleanup batch size, and persistent API limits. Install independent random `rate_limit_secret` and `token_secret` values of at least 32 bytes outside source control. The first HMACs client network identifiers before persistence. The second combines with a per-session random nonce so valid start retries return the same opaque token while only its hash is stored. Neither is a credential-encryption key.

In production the reseller shared runtime is discovered at the sibling
`public_html/.hulk-reseller-app/bootstrap.php`. A deployment with a different layout may set
`HULK_RESELLER_BOOTSTRAP` to its absolute path; the target must remain outside the public route and
contain the version-matched `admin-domain.php`.

## Authentication contract

Control Center authenticates the existing `app_admin_users` authority. It preserves Operations behavior:

- enabled-account check;
- password hash verification;
- per-session failed-attempt lockout;
- persisted `failed_attempts` and `locked_until` tracking;
- reset and `last_login_at` update after successful authentication;
- revalidation of the authoritative owner record on authenticated requests.

Its independent session cookie is scoped to `/control-center/`, uses `Secure`, `HttpOnly`, and `SameSite=Strict`, and does not alter the legacy Operations session.

## Routes

The allow-list is defined in `lib/routes.php`. Apache rewrites friendly module URLs such as `/control-center/live-users/` to the single authenticated shell. Unknown routes render a safe 404 state and never select files dynamically.

Operations, reseller, access-code, host, audit, Live Users, Sessions, Devices, Host Health, Diagnostics and Analytics modules are connected to their authoritative databases. No screen substitutes a failed authority with zero or demo production records.

## Dashboard V1 read contract

Dashboard reads Operations, reseller and Presence authorities independently and composes them in PHP. If an authority fails, its section renders a recoverable unavailable state while valid data from the other authorities remains visible. A failed read is never converted to zero.

| Dashboard value | Authoritative definition |
|---|---|
| Service status | `ops_service_snapshot()` from `app_service_status.id=1`, identical to the public Operations contract |
| Current version and update policy | `ops_update_snapshot()`, including the active release/settings fallback used by the public contract |
| Active APK release | `ops_active_release()`: highest enabled, active `version_code` |
| Current announcements | Enabled announcements inside the current start/end window and accepted by `ops_announcement_is_active()` |
| Enabled features | `true` values from `ops_feature_snapshot()` over the exact known-feature allow-list |
| Recent administration | Latest eight `app_admin_audit` rows; only action, administrator name, and time are displayed |
| Total/active resellers | Counts from `resellers`; active means exactly `status='active'` |
| Configured active hosts | Active reseller rows whose current host is accepted by `hulk_normalize_host()` |
| Resolver-ready codes | Active reseller with a valid current host, canonical current access code, and matching SHA-256 code hash |
| Online Now | `ended_at IS NULL` and `last_seen_at >= server_now - configured_online_ttl` |
| Sessions today | Sessions whose server `started_at` is inside the current Control Center day |
| Active devices | Distinct `installation_id` values whose sessions heartbeated during the explicitly labeled trailing 24-hour window |
| Presence app versions | Latest recent session-version snapshot per `installation_id` seen during the same 24-hour Presence window |

`users today` remains explicitly unavailable: the merged `cc_app_sessions` schema has no stable `account_id`, and the UI does not invent one from IPTV username, reseller, device or session identifiers.

## Phase 6 owner read models

- `/live-users/` defaults to fresh non-ended sessions and can also show Offline/all states. Search, status, app-version, reseller, host and platform filters, sorting and stable pagination are server-side and retained in query parameters.
- `/sessions/` renders immutable credential, host, device, app-version and lifecycle snapshots. A later code/host rotation never rewrites an older session.
- `/devices/` uses `installation_id` as stable identity and displays the latest schema-owned device/app facts. Inventory existence is never treated as proof that a device is Online.
- Each session page resolves its page of reseller IDs with one bounded reseller query. A reseller lookup failure leaves the authoritative control rows visible with an explicit partial-data warning.
- Reseller, current access-code and current-host screens include read-only Presence usage. Current-code/current-host usage matches the current reseller value exactly, so rotated historical snapshots remain history rather than being silently reassigned.
- ACCESS CODE, IPTV USERNAME and IPTV PASSWORD are displayed directly to the authenticated owner as required. They are not copied to logs, errors, audit JSON, analytics, screenshots or source fixtures with real values.

Phase 6 adds no table, migration or index. No production query-plan measurement established a need for another index; measured production evidence remains the gate for any future schema change.

## Phase 7 Host Health, Diagnostics and Analytics

The hosting gate was qualified before implementation from production read-only evidence: cPanel cron is available and active; PHP CLI 8.2.33 exposes PDO MySQL, cURL and OpenSSL; and PHP CLI resolved, connected to, and received HTTP 200 from every then-current normalized active reseller host. The observed CLI has no execution-time limit, so the application does not rely on that setting.

### Host Health contract

`tools/host_health.php` is a CLI-only, scheduler-compatible bounded invocation. It selects active current `resellers.host` values from the reseller authority, passes each through the existing `hulk_normalize_host()` contract, and probes only HTTP/S targets discovered by that path. It accepts no URL or target input.

Each invocation opens dedicated authority connections with a three-second PDO connect timeout, applies one-second session statement/lock deadlines to both MySQL connections, and obtains a non-blocking advisory lock, so an overlapping cron run exits without probing. It considers a rotating window of at most 500 current candidates every five minutes, probes at most 20, stops starting probe work after 40 seconds, and reserves the final 5 seconds of its 45-second application limit for bounded cleanup. The rotation prevents reseller IDs above the bounded read window from being permanently starved; the UI explicitly labels its summary partial if more than 500 active host rows exist.

DNS A/AAAA lookup runs in a short-lived PHP CLI child process with a two-second parent-enforced wall-clock deadline and forced termination; the main scheduler never calls blocking DNS directly. A missing/disabled `proc_open`, or failure to start that child, fails the invocation closed before it stores a false host observation or makes a network connection. Results must all be publicly routable and the approved result is pinned into cURL, so literal or resolved private, loopback, link-local, reserved, and metadata destinations are not contacted. The subsequent HEAD request uses the remainder of the five-second per-host deadline, a three-second connect cap, HTTP/S-only protocols, and no redirects. Cleanup first selects at most its configured batch under the statement deadline, then deletes only those primary keys. A slow DNS resolver or target can consume only its bounded per-host budget and cannot remove the work bound for the invocation. Output contains counts only.

`cc_host_health_checks` stores the reseller ID, SHA-256 fingerprint of the exact normalized current host, checked time, DNS/TCP/HTTP result facts, latency, and a controlled failure code. It never stores the raw host. Host rotation creates a new fingerprint and never rewrites older snapshots. UI state is defined as:

| State | Definition |
|---|---|
| `NOT_CHECKED` | The current valid host has no stored observation. |
| `HEALTHY` | The latest observation completed with HTTP 2xx/3xx. |
| `DEGRADED` | The latest HTTP status is outside 2xx/3xx, or target policy blocked a non-public address before connection. |
| `TRANSIENT_FAILURE` | The latest transport observation failed but the preceding observation for that same current fingerprint did not also fail. |
| `UNREACHABLE` | The latest two observations for the same current fingerprint are transport failures. |

Health history retention is 90 days and cleanup is capped at 200 rows per invocation. No `cc_host_health_targets` table exists because the scheduler can read current targets directly from `resellers.host`.

### Typed diagnostics contract

The additive endpoint is:

```text
POST /control-center/api/app/v1/diagnostics/events
Authorization: Bearer <presenceToken>
Content-Type: application/json
```

The body is capped at 4096 bytes and must contain exactly:

```json
{
  "contractVersion": 1,
  "eventId": "00000000-0000-4000-8000-000000000000",
  "sessionId": "00000000-0000-4000-8000-000000000000",
  "eventType": "PLAYBACK_START_FAILURE",
  "errorCode": "NETWORK_TIMEOUT",
  "occurredAtEpochMs": 0
}
```

The example identifiers are synthetic. `eventType` and `errorCode` are fixed allow-lists with controlled compatible pairs. Occurrence time must be inside the 30-day retention window and no more than five minutes in the future. The Presence session ID and token authorize the request; the session itself must still have a lifecycle fact inside that retention window, and an ended session cannot receive an event occurring after its end grace. App, device and host-fingerprint facts are copied only from that immutable server-side session snapshot. Event IDs are idempotent, conflicting reuse fails closed, and the client rate limit persists only an HMAC of the client identifier.

The contract has no free-text message, payload, stack trace, raw log, viewing-history, host URL, access code, IPTV credential, bearer token or arbitrary analytics field. `cc_diagnostic_events` retains only typed facts for 30 days, with cleanup capped at 200 rows per invocation. Its session relation cascades only when existing Presence retention deletes that session, preventing Diagnostics from blocking the older Presence cleanup contract. Android emission is intentionally not part of Phase 7 and requires a separate gated Android round.

### Read-model definitions

| View | Stored-fact definition |
|---|---|
| Host Health summary | Latest two `cc_host_health_checks` rows for each current reseller ID + current host fingerprint. |
| Host Health history | Latest 200 append-only check facts, including historical fingerprints. |
| Failures by code | Top 50 retained `cc_diagnostic_events` groups by `event_type` + `error_code`; a 51st row marks the view partial. |
| Failures by app version | Top 25 retained-event groups by stored app-version snapshot; a 26th row marks the view partial. |
| Failures by device/platform | Top 25 retained-event groups by stored platform/manufacturer/model snapshot; a 26th row marks the view partial. |
| Failures by host snapshot | Top 25 retained-event groups by stored host fingerprint; a 26th row marks the view partial. |
| Session trend | `cc_app_sessions`, grouped by `DATE(started_at)` over 30 days. |
| Version/adoption trend | Latest session ID per device per day, grouped by stored app version over 30 days; bounded to 500 displayed rows with an explicit partial state. |
| Current adoption distribution | Top 25 versions from the latest session per device with `last_seen_at` in the trailing 24 hours; a 26th row marks the view partial. |

No adoption metric uses APK downloads. Empty stored facts, an unavailable authority, and an unchecked host are separate states. SQL aggregation remains in normal read models. No daily rollup table was added. Phase 7 adds no speculative analytics index: no production-volume query-plan or timing evidence exists yet, and the bounded synthetic plans only establish executable query shapes. Capture real post-deployment `EXPLAIN` and latency measurements before proposing any index or rollup.

## Presence backend

The additive API is dark until the separate Android integration is deployed:

```text
POST /control-center/api/app/v1/presence/start
POST /control-center/api/app/v1/presence/heartbeat
POST /control-center/api/app/v1/presence/end
```

`start` accepts contract version 1, the current Android UUID `sessionId` and stable UUID `installationId`, the access-code and exact IPTV credential/host snapshot, client authentication time as metadata, device metadata, and app version metadata. It validates the current code, active reseller, and current host through the reseller authority before writing the control database. A matching retry returns the same logical session and same derived opaque token; a conflicting or ended session ID is not reused.

`heartbeat` and `end` require `Authorization: Bearer <presenceToken>` plus the matching session ID. Only SHA-256 token hashes are stored. End is idempotent and accepts `LOGOUT`, `ACCOUNT_REPLACED`, or `APP_SHUTDOWN`. Heartbeat never reactivates an ended session.

Online is derived only as:

```text
ended_at IS NULL AND last_seen_at >= server_now - online_ttl
```

Presence failure is intentionally independent of IPTV authentication. No endpoint mutates reseller data, Operations state, or Android authentication state, and request bodies/credentials are never logged or audited.

### Credential storage decision

The current deployment evidence does not prove a supported PHP sodium/OpenSSL configuration plus a secret key outside `public_html`. The approved exact access-code, IPTV username/password, and authenticated-host snapshots therefore remain direct columns in the restricted control-plane database. Transparent encryption may be added only after those runtime/key-storage facts are proven; credentials must never enter logs, audit data, diagnostics, fixtures with real values, screenshots, commit messages, or PR text.

### Migration and cleanup

Apply `migrations/2026-09-17-presence-v1.sql` to the Operations/control database using the deployment database account before exposing the endpoints. It adds only `cc_schema_migrations`, `cc_devices`, `cc_app_sessions`, and `cc_api_rate_limits`; it does not alter existing tables. Keep the additive tables on route rollback.

Run the bounded cleanup from hosting cron (daily is sufficient):

```bash
HULK_CONTROL_CENTER_CONFIG=/absolute/private/config.php \
  php /absolute/public_html/control-center/tools/presence_cleanup.php
```

Each invocation deletes at most the configured batch from each category: ended or stale sessions beyond retention, unreferenced inactive devices beyond retention, and expired rate-limit windows. Cleanup does not define Online state.

For Phase 7, apply both additive migrations to the Operations/control database before exposing its endpoint or pages:

```text
migrations/2026-09-19-diagnostics-v1.sql
migrations/2026-09-19-host-health-v1.sql
```

Do not drop either table during route rollback. After the migrations and private configuration are deployed, schedule one bounded CLI invocation at an interval approved for the hosting capacity:

```bash
HULK_CONTROL_CENTER_CONFIG=/absolute/private/config.php \
  php /absolute/public_html/control-center/tools/host_health.php
```

The command performs the bounded current-host probes, bounded 90-day Host Health cleanup, and bounded 30-day Diagnostics cleanup. This repository change does not create or modify the production cron entry.

Operations config supports an optional schema-v1 `presence` discovery object with `enabled`, `baseUrl`, `heartbeatSeconds`, and `onlineTtlSeconds`. It is omitted when disabled or absent. The Phase 5 Android integration consumes this object independently of IPTV authentication success.

## Shared mutation paths

- Control Center loads the existing `hulk-operations/admin/actions.php` domain functions. The legacy Operations panel uses those same functions, so release, service, announcement, feature and Growth rules are not copied.
- Reseller-owner mutations live in `.hulk-reseller-app/admin-domain.php` and are used by both the version-controlled legacy owner adapter and Control Center.
- Successful Control Center reseller mutations write a non-secret event to `app_admin_audit`. Passwords and access-code values are never placed in audit details.
- The two databases remain physically separate. No current mutable state is duplicated.

## Deployment preflight

1. Confirm the deployment target maps this directory to `/control-center/`.
2. Confirm HTTPS and `.htaccess` are active.
3. Keep `config.php` untracked, or use `HULK_CONTROL_CENTER_CONFIG` outside the web root.
4. Apply the reviewed additive Presence and Phase 7 migrations with a deployment-only schema account, then keep the runtime control connection limited to existing Operations access plus required CRUD on `cc_devices`, `cc_app_sessions`, `cc_api_rate_limits`, `cc_diagnostic_events`, and `cc_host_health_checks`.
5. Grant the reseller connection only the existing reseller-owner reads and mutations used by Phase 2.
6. Confirm `php -r 'exit(function_exists("proc_open") ? 0 : 1);'` succeeds in the production CLI, configure the bounded `diagnostics` and `host_health` values from `config.example.php`, then run PHP lint and `tests/run.php` with the production PHP version. If `proc_open` is disabled, DNS probing fails closed until hosting enables it; do not replace it with synchronous page-request DNS.
7. Verify unauthenticated module requests redirect to `/control-center/login.php`.
8. Verify successful and failed login, lockout, logout, and CSRF rejection against a non-production verification account.
9. Inspect desktop, tablet, and mobile layouts in Arabic RTL before enabling the route.
10. Confirm legacy Operations, reseller administration, `/reseller/`, resolver, APK, and Android endpoints are unchanged.

Rollback is discovery/route-level: keep Operations Presence discovery disabled or absent and disable the Presence API route. Retain additive session/device history; never roll back by dropping the new tables or restoring an older database over newer writes.

## Validation

```bash
find hosting/hulk-control-center -type f -name '*.php' -print0 \
  | xargs -0 -n1 php -l
php hosting/hulk-control-center/tests/run.php
python3 -m unittest hosting/hulk-control-center/tests/test_presence_contract.py
python3 -m unittest hosting/hulk-control-center/tests/test_phase6_contract.py
python3 -m unittest hosting/hulk-control-center/tests/test_phase7_contract.py
node --check hosting/hulk-control-center/assets/app.js
node hosting/hulk-control-center/tests/navigation.test.js
```

Manual rendered review should cover at least 1440×1000, 834×1112, and 390×844, including navigation, long Arabic labels, keyboard focus, table overflow, login, unavailable, empty, recoverable error, and loading/skeleton primitives.

## Source-truth reconciliation

The production DDL for `admins` and `resolver_rate_limits` was captured with `SHOW CREATE TABLE` and is now represented exactly in `backend/reseller-access/schema.sql`. The version-controlled reseller bootstrap also contains the deployed owner-session authority and the owner panel is represented under `public/hulk-reseller-admin/`.

The supplied Operations snapshot predates the current repository Growth/session hardening in a small set of files. Current GitHub source remains the repository truth and the Control Center reuses it directly. Runtime configuration, credentials, logs and deployment APKs are intentionally not tracked.
