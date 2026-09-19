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

Operations, reseller, access-code, host, audit, Live Users, Sessions and Devices modules are connected to their current authoritative databases. Phase-7-only modules stay explicitly **غير متاح بعد**. No screen substitutes a failed authority with zero or demo production records.

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
| Active devices | Distinct `cc_devices` rows seen during the explicitly labeled trailing 24-hour window |
| Presence app versions | Latest app version recorded for devices seen during the same 24-hour Presence window |

`users today` remains explicitly unavailable: the merged `cc_app_sessions` schema has no stable `account_id`, and the UI does not invent one from IPTV username, reseller, device or session identifiers. Host health, diagnostics and Phase-7 analytics also remain unavailable.

## Phase 6 owner read models

- `/live-users/` defaults to fresh non-ended sessions and can also show Offline/all states. Search, status, app-version, reseller, host and platform filters, sorting and stable pagination are server-side and retained in query parameters.
- `/sessions/` renders immutable credential, host, device, app-version and lifecycle snapshots. A later code/host rotation never rewrites an older session.
- `/devices/` uses `installation_id` as stable identity and displays the latest schema-owned device/app facts. Inventory existence is never treated as proof that a device is Online.
- Each session page resolves its page of reseller IDs with one bounded reseller query. A reseller lookup failure leaves the authoritative control rows visible with an explicit partial-data warning.
- Reseller, current access-code and current-host screens include read-only Presence usage. Current-code/current-host usage matches the current reseller value exactly, so rotated historical snapshots remain history rather than being silently reassigned.
- ACCESS CODE, IPTV USERNAME and IPTV PASSWORD are displayed directly to the authenticated owner as required. They are not copied to logs, errors, audit JSON, analytics, screenshots or source fixtures with real values.

Phase 6 adds no table, migration or index. No production query-plan measurement established a need for another index; measured production evidence remains the gate for any future schema change.

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
4. Apply the reviewed additive Presence migration with a deployment-only schema account, then keep the runtime control connection limited to existing Operations access plus CRUD on `cc_devices`, `cc_app_sessions`, and `cc_api_rate_limits`.
5. Grant the reseller connection only the existing reseller-owner reads and mutations used by Phase 2.
6. Run PHP lint and `tests/run.php` with the production PHP version.
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
node --check hosting/hulk-control-center/assets/app.js
node hosting/hulk-control-center/tests/navigation.test.js
```

Manual rendered review should cover at least 1440×1000, 834×1112, and 390×844, including navigation, long Arabic labels, keyboard focus, table overflow, login, unavailable, empty, recoverable error, and loading/skeleton primitives.

## Source-truth reconciliation

The production DDL for `admins` and `resolver_rate_limits` was captured with `SHOW CREATE TABLE` and is now represented exactly in `backend/reseller-access/schema.sql`. The version-controlled reseller bootstrap also contains the deployed owner-session authority and the owner panel is represented under `public/hulk-reseller-admin/`.

The supplied Operations snapshot predates the current repository Growth/session hardening in a small set of files. Current GitHub source remains the repository truth and the Control Center reuses it directly. Runtime configuration, credentials, logs and deployment APKs are intentionally not tracked.
