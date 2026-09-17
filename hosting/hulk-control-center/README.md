# HULK SA Control Center — Phase 3

Production foundation for the owner-facing administration application at:

`https://hulksa.com/control-center/`

Phase 3 keeps the Phase 1 Arabic RTL foundation and Phase 2 administration workflows, then adds Dashboard V1 from the two existing authoritative databases. It does not add Presence, Android changes, analytics, diagnostics, adoption tracking, or host-health probing.

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

Operations, reseller, access-code, host and audit modules are connected to their current authoritative databases. Remaining modules stay explicitly **غير متاح بعد**. Dashboard V1 contains no fabricated counts or demo production records.

## Dashboard V1 read contract

Dashboard reads the two PDO authorities independently and composes them in PHP. If either authority fails, its section renders a recoverable unavailable state while valid data from the other authority remains visible. A failed read is never converted to zero.

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

Online users, sessions, devices, client-version adoption, host health, diagnostics, and usage analytics stay explicitly unavailable because no current server-side authority owns those measurements.

## Shared mutation paths

- Control Center loads the existing `hulk-operations/admin/actions.php` domain functions. The legacy Operations panel uses those same functions, so release, service, announcement, feature and Growth rules are not copied.
- Reseller-owner mutations live in `.hulk-reseller-app/admin-domain.php` and are used by both the version-controlled legacy owner adapter and Control Center.
- Successful Control Center reseller mutations write a non-secret event to `app_admin_audit`. Passwords and access-code values are never placed in audit details.
- The two databases remain physically separate. No current mutable state is duplicated.

## Deployment preflight

1. Confirm the deployment target maps this directory to `/control-center/`.
2. Confirm HTTPS and `.htaccess` are active.
3. Keep `config.php` untracked, or use `HULK_CONTROL_CENTER_CONFIG` outside the web root.
4. Grant the control connection only the existing Operations reads and mutations used by Phase 2, including `app_admin_users` authentication/lockout and `app_admin_audit`; do not grant schema-management privileges.
5. Grant the reseller connection only the existing reseller-owner reads and mutations used by Phase 2.
6. Run PHP lint and `tests/run.php` with the production PHP version.
7. Verify unauthenticated module requests redirect to `/control-center/login.php`.
8. Verify successful and failed login, lockout, logout, and CSRF rejection against a non-production verification account.
9. Inspect desktop, tablet, and mobile layouts in Arabic RTL before enabling the route.
10. Confirm legacy Operations, reseller administration, `/reseller/`, resolver, APK, and Android endpoints are unchanged.

Rollback is route-level: disable `/control-center/` or restore its previous package. Phase 3 has no migration; completed Phase 2 mutations remain authoritative and visible in the legacy panels, so rollback never restores an older database over newer writes.

## Validation

```bash
find hosting/hulk-control-center -type f -name '*.php' -print0 \
  | xargs -0 -n1 php -l
php hosting/hulk-control-center/tests/run.php
node --check hosting/hulk-control-center/assets/app.js
node hosting/hulk-control-center/tests/navigation.test.js
```

Manual rendered review should cover at least 1440×1000, 834×1112, and 390×844, including navigation, long Arabic labels, keyboard focus, table overflow, login, unavailable, empty, recoverable error, and loading/skeleton primitives.

## Source-truth reconciliation

The production DDL for `admins` and `resolver_rate_limits` was captured with `SHOW CREATE TABLE` and is now represented exactly in `backend/reseller-access/schema.sql`. The version-controlled reseller bootstrap also contains the deployed owner-session authority and the owner panel is represented under `public/hulk-reseller-admin/`.

The supplied Operations snapshot predates the current repository Growth/session hardening in a small set of files. Current GitHub source remains the repository truth and the Control Center reuses it directly. Runtime configuration, credentials, logs and deployment APKs are intentionally not tracked.
