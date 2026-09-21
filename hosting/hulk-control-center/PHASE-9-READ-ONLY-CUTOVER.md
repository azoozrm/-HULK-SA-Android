# Phase 9 — Legacy owner cutover runbook

This document records the Phase 9 staged cutover for the legacy owner panels. It contains no
deployment credentials, access codes, hosts, tokens or private values. Phase 9 changes legacy
owner entry-point routing only.

## Stage status

| Stage | State | Notes |
|---|---|---|
| Phase 9A — legacy owner panels read-only | **MERGED + DEPLOYED + read-only** | PR #296 merged (`4bd33265`); deployed to production and byte-verified; production smoke passed. |
| Phase 9B — legacy owner `302`/`303` redirects | **MERGED + DEPLOYED + VERIFIED** | PR #297 merged at `5767d537`; production deployment completed at 2026-09-21T14:15:05Z; deployed bytes matched merged source and production redirect smoke passed 32/32. |
| Soak | **SOAK: OWNER_WAIVED** | The owner explicitly waived the Phase 9A → Phase 9B soak for this entry gate. This is an owner override; no soak was run or observed, and no soak PASS is claimed. |
| Production Phase 9B | **COMPLETE** | Legacy owner administration routes now redirect to HULK SA Control Center; Control Center is the sole owner-facing administration entry. |

## Phase 9A — read-only (merged, deployed)

The legacy owner panels remained available for authenticated observation:

- `/hulk-operations/` and `/hulk-operations/admin/` — the owner could sign in, sign out and read the
  dashboard, releases, announcements, service, features, Growth and audit sections.
- `/hulk-reseller-admin/` — the owner could sign in, sign out and read the reseller list.

Both panels were read-only: every owner business mutation was rejected server-side before the
authoritative mutation function ran. Phase 9A is preserved as historical fact; it was not
reconstructed or falsified by Phase 9B.

## Phase 9B — legacy redirect (merged, deployed, verified)

Stage B retires the remaining legacy owner-facing administration entry points. They no longer
authenticate a legacy owner, read the database, process CSRF or run any legacy business action; each
request is redirected to the equivalent HULK SA Control Center route. The retired routes are minimal
redirect files.

### Operations redirect contract

| Legacy entry point | Method | Destination | Status |
|---|---|---|---|
| `/hulk-operations/` | GET | `/control-center/` | 302 |
| `/hulk-operations/admin/` (`dashboard` or no section) | GET | `/control-center/` | 302 |
| `/hulk-operations/admin/?section=releases` | GET | `/control-center/releases/` | 302 |
| `/hulk-operations/admin/?section=announcements` | GET | `/control-center/announcements/` | 302 |
| `/hulk-operations/admin/?section=service` | GET | `/control-center/service/` | 302 |
| `/hulk-operations/admin/?section=features` | GET | `/control-center/features/` | 302 |
| `/hulk-operations/admin/?section=growth` | GET | `/control-center/growth/` | 302 |
| `/hulk-operations/admin/?section=audit` | GET | `/control-center/audit/` | 302 |
| unknown/untrusted `section` | GET | `/control-center/` | 302 |
| `/hulk-operations/admin/login.php` | GET/POST | `/control-center/login.php` | 302/303 |
| `/hulk-operations/admin/logout.php` | GET/POST | `/control-center/` | 302/303 |
| `/hulk-operations/admin/setup.php` | GET/POST | `/control-center/login.php` | 302/303 |
| `/hulk-operations/admin/delete_release.php` | GET/POST | `/control-center/releases/` | 302/303 |
| any of the above | POST | equivalent module | 303 |

Only an allow-listed legacy section can select a destination; any other value falls back to
`/control-center/`. Destination paths are fixed and are never built from request input.

The legacy web first-admin workflow no longer renders or writes to the database. The preserved CLI
recovery tool `hosting/hulk-operations/tools/create_admin.php` is unchanged.

The legacy logout route is an externally reachable owner entry, so it is included in the redirect
cutover. The retired operations owner session no longer exists; the route redirects to Control
Center and never mutates Control Center session state (the Control Center session cookie is
separate and scoped to `/control-center/`).

### Reseller owner redirect contract

| Legacy entry point | Method | Destination | Status |
|---|---|---|---|
| `/hulk-reseller-admin/` | GET | `/control-center/resellers/` | 302 |
| `/hulk-reseller-admin/action.php` (`action=login`) | POST | `/control-center/login.php` | 303 |
| `/hulk-reseller-admin/action.php` (any other action) | POST | `/control-center/resellers/` | 303 |
| `/hulk-reseller-admin/action.php` | GET | `/control-center/resellers/` | 302 |

The retired action endpoint no longer performs login, logout, CSRF verification, session ownership,
database access or any `hulk_admin_*` mutation. No authentication bridge is created between the
legacy reseller admin and Control Center.

### Production deployment verification

The production cutover was completed from merged official HEAD
`5767d53775727d2c0292e4a05e223dd9bd5c216b` after post-merge CI run `35609362863` succeeded.

Deployment verification established:

- pre-deploy production routing files matched the Phase 9A baseline with no unexplained drift;
- a new private pre-deploy rollback backup was created at
  `/home/hulknjcx/private/backups/phase9b-predeploy-20260921T141505Z/`;
- the existing Phase 9A backup remained untouched;
- all 10 deployed runtime files matched the merged Phase 9B source by SHA-256;
- both retired `read-only.php` files were removed and both Phase 9B `redirect.php` files were present;
- production PHP 8.2.33 syntax checks passed for all deployed runtime files;
- automated production redirect validation passed 32/32 with exact `302`/`303` destinations and
  zero-byte redirect bodies;
- `/control-center/`, `/reseller/`, resolver invalid-code behavior, Operations
  `schemaVersion = 1`, weak ETag / `If-None-Match` `304` zero-body behavior and the active APK path
  remained intact;
- no database/schema/migration/config/cron/APK/source/GitHub mutation was part of the production
  routing deployment.

No rollback was required.

### Preserved and authoritative

- HULK SA Control Center is the sole owner-facing administration entry and keeps its current owner
  authentication and mutation authority.
- The shared authorities are unchanged: `hosting/hulk-operations/admin/actions.php` for Operations
  and `backend/reseller-access/public/.hulk-reseller-app/admin-domain.php` for reseller identities,
  status, host and current access code.
- `/reseller/`, `/api/reseller/resolve/`, `/hulk-operations/api/app/v1/config/`, the
  `schemaVersion = 1` contract, the stable weak ETag/`If-None-Match` behavior, active APK paths and
  hashes, update policy, service state, announcements, feature flags, Growth payload, reseller
  self-service login/host update/code rotation, and Android behavior are unchanged.
- No database, schema, migration, cron, secret, dependency or Android change is part of the cutover.

### Rollback

Phase 9B rollback is source routing only. Restore the Phase 9A read-only legacy owner routes for
`hosting/hulk-operations/**` and `backend/reseller-access/public/hulk-reseller-admin/**` using the
preserved Phase 9B pre-deploy routing backup or by reverting the Phase 9B routing commit. Do not
restore an older database over newer writes, do not run a schema migration or rollback, and do not
delete data or additive Control Center tables. The protected Phase 9A deployment backup remains a
separate historical rollback asset.

## Current production state

**Phase 9 production cutover is complete.** Phase 9A read-only and Phase 9B redirect are both merged,
deployed and verified. Legacy owner administration entry points now redirect to HULK SA Control
Center, which is the sole owner-facing administration entry.

Soak remains **SOAK: OWNER_WAIVED** as an owner override. No soak PASS is claimed.
