# Direct hosts and multiple codes

LOCAL_ROUND prepares source and isolated tests only. No production migration or deployment ran

The existing reseller database remains the database authority. `hosts` owns name/URL/state;
`host_access_codes` owns each code/value/hash/state. No reseller/password row is created for new hosts
The resolver URL, POST JSON `code`, `{host}` success body and existing error/status families remain
Presence, pairing, subscriber notes and health retain numeric `reseller_id` history through host-ID aliases

## Deployment under separate owner authority

1. Back up both databases and deployed source privately. Quiesce resolver/admin/Presence/pairing traffic and scheduled health. Preserve private config, APKs and media
2. Confirm deployed source differences in the section evidence. Do not bulk replace Operations or private config
3. Install `migrations/2026-11-01-direct-hosts.sql` with the schema account into the reseller authority only
4. Using a deployment-only CLI that loads the matching private bootstrap, call `hulk_hosts_migrate($db)` once. This transaction copies exact code values/hashes and host/status/timestamps, one host per legacy ID. Existing duplicate URLs and invalid/missing URLs remain separate; they require specific owner correction. No deduplication, regenerated code, invented host or historical rewrite
5. Verify row counts, exact code/hash/state/ID correspondence privately without exporting values. Verify invalid/missing host resolution fails and inactive hosts remain inactive. Repeat migration returns false without recopying retired data. Partial populated targets fail closed
6. Deploy the matching resolver/bootstrap/domain and Control Center source together; grant minimum required CRUD for hosts/codes, read of migration ledger, existing resolver-rate-limit permissions. Legacy reseller writers are retired in every adapter/CLI. Do not allow mixed versions to write
7. Smoke check with a separate synthetic verification account/host/code and explicit owner permission; resume traffic/health only after checks. Existing sessions, notes, audit, device and health tables remain unchanged

## Rollback

Before any new-authority write, routing/source rollback can restore the matching old source against the unchanged legacy authority after traffic is quiesced. Keep additive tables and migration ledger
After any host/code creation/edit/state change, old reseller rows are a stale snapshot: do NOT route clients to old writable source, rerun backfill, drop tables, or restore an old database over new data. Quiesce affected routes and roll forward; a data-aware reverse conversion needs a separate reviewed plan because one legacy slot cannot represent multiple codes

Both SQLite and isolated native MariaDB exercise preservation, repeated migration, invalid/inactive/duplicate hosts, second/third codes, independent state and uniqueness. Native MySQL5.7 and production-engine11.4 verification remain separate unexecuted checks
