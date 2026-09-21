# Phase 9A — Legacy owner read-only cutover runbook

This document records the Phase 9 staged cutover for the legacy owner panels. It contains no
deployment credentials, access codes, hosts, tokens or private values. Stage A is a routing/legacy
entry-point change only.

## Current state: STAGE_A_READ_ONLY

The legacy owner panels stay available for authenticated observation:

- `/hulk-operations/` and `/hulk-operations/admin/` — the owner can still sign in, sign out and read
  the dashboard, releases, announcements, service, features, Growth and audit sections.
- `/hulk-reseller-admin/` — the owner can still sign in, sign out and read the reseller list.

Both panels are read-only. Every owner business mutation is rejected server-side before the
authoritative mutation function runs and is routed to the equivalent Control Center module:

| Legacy entry point | Legacy mutation | Routed to |
|---|---|---|
| `/hulk-operations/admin/index.php` (authenticated POST) | `ops_admin_handle_post()` | `/control-center/` module matching the section (`releases`, `service`, `announcements`, `features`, `growth`, `audit`) |
| `/hulk-operations/admin/delete_release.php` | `ops_delete_release()` | `/control-center/releases/` |
| `/hulk-operations/admin/setup.php` | first-admin web creation | disabled; use the existing CLI recovery tool and `/control-center/` |
| `/hulk-reseller-admin/action.php` | `hulk_admin_create_reseller`, `hulk_admin_set_status`, `hulk_admin_update_host`, `hulk_admin_set_code`, `hulk_admin_rotate_code`, `hulk_admin_reset_password` | `/control-center/resellers/` |
| `/hulk-reseller-admin/action.php` | `login`, `logout` | unchanged session actions |

Preserved and authoritative:

- Owner authentication and logout on both legacy panels remain available.
- HULK SA Control Center remains the sole target owner-facing mutable administration experience.
- The shared authorities are unchanged: `hosting/hulk-operations/admin/actions.php` for Operations
  and `backend/reseller-access/public/.hulk-reseller-app/admin-domain.php` for reseller identities,
  status, host and current access code.
- `/reseller/`, `/api/reseller/resolve/`, `/hulk-operations/api/app/v1/config/`, active APK paths and
  Android contracts are unchanged.
- The databases and all current production data are unchanged.

Blocked mutations are explicit. The legacy panel is not silently redirected for reads, and a blocked
mutation is never reported as a fake success.

## Rollback before Stage B

Rollback is source-only for the legacy entry points:

1. Restore the pre-Phase-9A legacy route code for the files under `hosting/hulk-operations/admin/`
   and `backend/reseller-access/public/hulk-reseller-admin/`.
2. Do not restore an older database over newer writes.
3. Do not run a schema migration or rollback.
4. Do not delete any data or additive Control Center table.

No database restore, schema rollback or data deletion is part of the Phase 9A rollback.

## Next state: STAGE_B_REDIRECT

Stage B turns the legacy owner entry points into `302`/`303` redirects to the equivalent Control
Center modules. Stage B must NOT begin until all of the following hold:

- the Phase 9A pull request is reviewed and explicitly merged;
- Phase 9A is separately deployed to production;
- the required post-deployment smoke passes;
- the approved soak is observed with no critical regression;
- the owner explicitly authorizes Stage B.

This source round intentionally does not implement Stage B and does not define a soak duration.
