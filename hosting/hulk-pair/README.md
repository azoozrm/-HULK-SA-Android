# HULK Pair — public TV activation entry

This folder contains the repository source for the public activation entry
`https://hulksa.com/pair/`.

- Deployment mapping: `hosting/hulk-control-center` → `public_html/control-center` and
  `hosting/hulk-pair` → `public_html/pair` (see the R07 `SOURCE-SYNC.json` mapping in the task
  evidence).
- `index.php` only defines `HULK_PAIRING_PUBLIC_ENTRY` and reuses the existing page controller in
  `control-center/pair/index.php`; validation, CSRF, form proofs, provider verification, approval,
  claim/ACK/cancel and cleanup are unchanged and controller-owned.
- The controller's session cookie path switches to `/pair/` only for this public entry; the legacy
  `/control-center/pair/` route keeps its original cookie scope for already-installed R04 clients.
- The JSON API output and Operations discovery are unchanged in R07.

Deployment is owner-side: upload `HULK-PAIR-HOSTING-R07.zip` to premium263 and run its installer;
this repository folder is the reviewed source, not a deployment action.
