# HULK SA Control Center — Phase 1

Production foundation for the owner-facing administration application at:

`https://hulksa.com/control-center/`

Phase 1 contains the authenticated Arabic RTL shell only. It does not connect or mutate Operations business modules, reseller records, Android contracts, Presence, analytics, diagnostics, or host health.

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

- `databases.control`: the existing HULK Operations/control-plane database. Phase 1 reads and updates only `app_admin_users` as required by the established login/lockout contract.
- `databases.reseller`: the existing reseller database. Phase 1 defines the connection only and never opens it during a request.

Do not commit runtime credentials. `config.php` is ignored and denied by `.htaccess`.

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

All final modules are registered, but their business data remains explicitly **غير متاح بعد** until the appropriate implementation phase. Phase 1 contains no fabricated counts or demo production records.

## Deployment preflight

1. Confirm the deployment target maps this directory to `/control-center/`.
2. Confirm HTTPS and `.htaccess` are active.
3. Keep `config.php` untracked, or use `HULK_CONTROL_CENTER_CONFIG` outside the web root.
4. Verify the control DB user can select and update only the existing authentication fields required by `app_admin_users`.
5. Configure the reseller connection for future use without granting Phase 1 workflows access to mutate it.
6. Run PHP lint and `tests/run.php` with the production PHP version.
7. Verify unauthenticated module requests redirect to `/control-center/login.php`.
8. Verify successful and failed login, lockout, logout, and CSRF rejection against a non-production verification account.
9. Inspect desktop, tablet, and mobile layouts in Arabic RTL before enabling the route.
10. Confirm legacy Operations, reseller administration, `/reseller/`, resolver, APK, and Android endpoints are unchanged.

Rollback is route-level: disable `/control-center/` or remove this package. Phase 1 has no migration and no business-data rollback.

## Validation

```bash
find hosting/hulk-control-center -type f -name '*.php' -print0 \
  | xargs -0 -n1 php -l
php hosting/hulk-control-center/tests/run.php
node --check hosting/hulk-control-center/assets/app.js
node hosting/hulk-control-center/tests/navigation.test.js
```

Manual rendered review should cover at least 1440×1000, 834×1112, and 390×844, including navigation, long Arabic labels, keyboard focus, table overflow, login, unavailable, empty, recoverable error, and loading/skeleton primitives.

## Phase 2 source-drift gate

The deployed reseller administration snapshot depends on `admins` and `resolver_rate_limits`, while the official repository schema currently defines only `resellers`. Operations deployment/source drift was also observed during Phase 0. Before Phase 2 introduces any owner business workflow, the deployed sources and schema-only DDL must be reconciled into version-controlled truth.

This Phase 1 package intentionally does not attempt that reconciliation.
