# Phase 9 — Legacy owner cutover runbook

This document records the Phase 9 staged cutover for the legacy owner panels. It contains no
deployment credentials, access codes, hosts, tokens or private values. Phase 9 changes legacy
owner entry-point routing only.

## Stage status

| Stage | State | Notes |
|---|---|---|
| Phase 9A — legacy owner panels read-only | **MERGED + DEPLOYED + read-only** | PR #296 merged (`4bd33265`); deployed to production and byte-verified; production smoke passed. |
| Phase 9B — legacy owner `302`/`303` redirects | **SOURCE CANDIDATE / PR** | Implemented in this round as one source branch and one PR. **Not merged, not deployed.** Production still runs the Phase 9A read-only behavior. |
| Soak | **SOAK: OWNER_WAIVED** | The owner explicitly waived the Phase 9A → Phase 9B soak for this entry gate. This is an owner override; no soak was run or observed, and no soak PASS is claimed. |
| Production Phase 9B | **NOT DEPLOYED** | Phase 9 is not fully production-cut-over until Stage B is reviewed, merged and separately authorized for deployment. |

## Phase 9A — read-only (merged, deployed)

The legacy owner panels remained available for authenticated observation:

- `/hulk-operations/` and `/hulk-operations/admin/` — the owner could sign in, sign out and read the
  dashboard, releases, announcements, service, features, Growth and audit sections.
- `/hulk-reseller-admin/` — the owner could sign in, sign out and read the reseller list.

Both panels were read-only: every owner business mutation was rejected server-side before the
authoritative mutation function ran. Phase 9A is preserved as historical fact; it was not
reconstructed or falsified by this round.

## Phase 9B — legacy redirect (source candidate)

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
`hosting/hulk-operations/**` and `backend/reseller-access/public/hulk-reseller-admin/**` by reverting
the Phase 9B routing commit. Do not restore an older database over newer writes, do not run a schema
migration or rollback, and do not delete data or additive Control Center tables. The protected
production Phase 9A deployment backup remains a deployment rollback asset and is not touched by this
source round.

## Current production state

Production runs the Phase 9A read-only behavior. Phase 9B is a source candidate only and is **not
deployed**; this runbook does not claim a full Phase 9 production cutover until Stage B is reviewed,
merged and separately authorized for deployment.
