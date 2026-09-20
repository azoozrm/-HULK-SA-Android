# Phase 8 qualification and owner runbooks

This document records repository-verifiable Phase 8 parity and the production gates that must remain explicit before Phase 9. It contains no deployment credentials or private host values.

## Parity classification

| Phase 8 item | Classification | Evidence / decision |
|---|---|---|
| Existing Operations and reseller administration parity | COMPLETE | Releases, service, announcements, feature flags, Growth, resellers, current access codes and current hosts continue through the existing single-owner adapters. |
| Presence, Host Health, Diagnostics and Analytics production states | COMPLETE | Phase 4–7 read models already separate loading, empty, partial and recoverable unavailable states. Phase 8 preserves those owners. |
| Settings | GAP — implemented | `/settings/` now gives a useful read-only view of safe runtime bounds and links each mutable concern to its authoritative module. It never projects database configuration, secrets, cookie names or private targets. |
| Admin account management | NOT REQUIRED | Current authority proves authentication through `app_admin_users`, first-account setup and CLI account creation, but no role/RBAC or account-management product contract. Adding enable/disable/reset UI would invent semantics and unsafe last-admin/self-lockout policy. |
| Saved filters | GAP — implemented | Repeated safe filters can be named and kept in browser `localStorage`, capped at 10 per module. Free search, host, session, code and credential fields are excluded. GET forms remain deep-linkable. |
| Full audit search | GAP — implemented | Server-side date, administrator, action-prefix and reseller-ID filters are parameterized. Pagination is frozen by maximum audit ID, fetches 50+1 rows, exposes only allow-listed safe detail keys and caps a search at 200 pages / 10,000 rows. |
| Responsive / accessibility / Arabic RTL refinement | GAP — implemented; rendered sign-off pending | Tables opt into progressive mobile card rendering, long values wrap, mobile/navigation/pagination controls meet a 44 px target, focus is visible for links/buttons/inputs/selects/textareas/summaries, and existing RTL navigation/focus trapping remains. |
| Cross-module navigation | GAP — implemented | Host Health links by numeric reseller ID to the authoritative current-host and session contexts. Settings links to existing owners. Generated context URLs accept only allow-listed non-secret fields. |
| Performance | COMPLETE with Phase 8 audit refinement | No production evidence supports a new index, cache or rollup. Audit search is bounded before reading rows; saved filters are local and create no request loop. |
| Documentation and runbooks | GAP — implemented | This document and the Control Center README cover recovery, configuration boundaries, cleanup, backup rehearsal, rollback and Phase 9 prerequisites. |
| Backup / disaster rehearsal | USER / PRODUCTION ACCEPTANCE | The safe rehearsal procedure is defined below. This workspace has no production-safe staging database or MySQL client, so no restore was executed and this gate is not PASS. |
| Owner acceptance | USER / PRODUCTION ACCEPTANCE | The owner must review the live deployed pages and sign off the matrix below before Phase 9. |

## Query budgets

| Read | Budget | Index / evidence decision |
|---|---|---|
| Audit search | Default 30-day window; maximum 366 days; 51 fetched rows; 50 displayed; 200 pages maximum; immutable maximum-ID snapshot | Existing primary key provides stable ordering and `idx_app_admin_audit_created` bounds the date range. Administrator and safe-detail filters execute inside that bounded window. No new index without live `EXPLAIN` and latency evidence. |
| Presence sessions / devices | Existing 25-row pages and current SQL pagination | Unchanged Phase 6 contract. |
| Reseller owner pages | Existing 25-row pages; exact numeric reseller context is additive | Existing primary key is used by the new exact context filter. |
| Host Health / Diagnostics / Analytics | Existing Phase 7 limits: 200 history rows, top 50/25 groups and 500 adoption rows | Current production measurements did not justify another index or rollup. |
| Saved filters / responsive tables | At most 10 local saved views per module; DOM transformation only on already-rendered bounded tables | No database, API, polling or background request. |

Production acceptance must capture `EXPLAIN` plus observed latency for the final audit shapes at current production volume. A new index is allowed only in a separately reviewed additive migration if that evidence exceeds the approved owner-page budget.

## Accessibility / RTL acceptance matrix

| State | Repository evidence | Live acceptance |
|---|---|---|
| 1440 × 1000 desktop | Persistent RTL sidebar; bounded density; responsive settings and audit grids | Confirm all modules, tables, forms, empty/error/partial states and keyboard order in a real browser. |
| 834 × 1112 tablet | Compact rail below 1180 px; two-column filters/settings collapse; no fixed device-specific offsets | Confirm long Arabic labels, rail tooltips, form wrapping and table/card transitions. |
| 390 × 844 mobile | Accessible modal drawer with inert background/focus trap; 44 px targets; progressive mobile cards; one-column saved filters/settings | Confirm no clipping, unintended horizontal page scroll, focus loss or hidden action. |
| Keyboard | `:focus-visible` covers interactive controls and drawer focus is restored on Escape | Tab through every module and destructive confirmation. |
| Screen reader / names | Arabic `lang`/RTL, skip link, landmarks, form labels, table scroll labels and live saved-filter status | Verify with the owner's available iOS VoiceOver/browser combination. |
| Reduced motion | Existing `prefers-reduced-motion` rule disables nonessential movement | Confirm with OS reduced-motion preference. |

Automated source/JavaScript checks do not replace this rendered live acceptance.

## Owner authentication and recovery

1. A temporary lock is resolved by the existing lockout duration; do not bypass it from the UI.
2. If the only password is lost, use the existing CLI-only `hosting/hulk-operations/tools/create_admin.php <new-safe-username>` from the production PHP CLI with the private Operations config installed. Enter the new password only through the interactive prompt.
3. Sign in to both legacy Operations and Control Center with the recovery account, then inspect `app_admin_audit` for `ADMIN_CREATED`.
4. Account disable/reset/removal is not performed by Control Center Phase 8. Any later account lifecycle UI requires an approved role model and explicit self-lockout/last-admin rules.
5. Never put the password, hash, session cookie or recovery value in shell history, logs, screenshots, URLs, audit details or PR text.

## Configuration boundaries

- Operations remains the only mutable owner of releases, update policy, service state, announcements, feature flags and Growth.
- The reseller domain remains the only mutable owner of reseller status, password, current host and current access code.
- Private deployment configuration remains outside the document root. The Settings page projects only allow-listed non-secret numeric/policy facts.
- Presence, Diagnostics and Host Health do not become configuration editors in Phase 8.

## Presence cleanup and Host Health maintenance

- Run `tools/presence_cleanup.php` on the existing daily schedule with `HULK_CONTROL_CENTER_CONFIG` pointing to the private file. Confirm counts only; output must not contain identifiers or credentials.
- Run `tools/host_health.php` on the approved schedule. Preserve its advisory lock, DNS/connect/request/work/runtime bounds and cleanup reserve.
- Investigate repeated `UNREACHABLE` only after two observations for the same current fingerprint. A single transport failure remains transient.
- If the scheduler becomes unsafe, stop that cron entry and hide the affected module at the route level; retain additive history.

## Diagnostics and retention

- Diagnostics accepts only the typed bounded contract documented in the README. Do not add raw logs, free text, stack traces or credential fields.
- Retention remains 30 days for Diagnostics, 90 days for Host Health and the configured bounded values for Presence.
- Cleanup remains batch-limited. Do not replace it with an unbounded delete.

## Backup / restore rehearsal before Phase 9

Perform this only against isolated staging databases and a staging document root:

1. Take hosting-native or `mysqldump --single-transaction` logical backups of both authoritative databases. Supply credentials interactively or through the hosting credential mechanism, never in the command text.
2. Record UTC time, database engine/version, file byte size and SHA-256 for each encrypted/private backup artifact.
3. Create empty isolated staging databases. Never target either production schema and never restore over a database containing newer writes.
4. Restore both backups into staging, then compare table lists and row counts for `app_admin_users`, `app_admin_audit`, Operations tables, `resellers`, Presence tables and Phase 7 tables.
5. Point a staging-only private Control Center config at the restored databases. Keep public Operations discovery, resolver and production cron disabled.
6. Run PHP lint, the full Control Center behavioral/contract/navigation stack, login/lockout/logout, read every module, and execute reversible non-production mutations through the authoritative adapters.
7. Record the commands, timestamps, backup hashes, restore duration, verification results and rollback decision in a private operations record without credentials.
8. Destroy only the isolated staging databases after sign-off. Preserve the protected backup according to the owner retention policy.

This repository round does not run the rehearsal and does not write production data.

## Module rollback and Phase 9 prerequisites

- Roll back an affected Control Center module by disabling its route/navigation entry while leaving the authoritative legacy panel and additive data intact.
- Do not drop Phase 4–7 tables during route rollback.
- Before Phase 9: complete the restore rehearsal, production `EXPLAIN`/latency capture, live 1440/834/390 rendered matrix, owner workflow sign-off, legacy Operations/reseller regression, resolver/API/APK smoke checks and an approved soak/rollback window.
- Phase 9 must remain a separate branch and PR. No legacy route becomes read-only or redirects as part of Phase 8.

## Impact statement

- No schema migration.
- No API contract change.
- No Android source change.
- No production deployment, database write, cron change, release, tag or merge.
