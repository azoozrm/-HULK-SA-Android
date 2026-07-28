# Canonical source evidence

- `v0.9.3.17-baseline.sha256` is the historical reconstruction baseline accepted by PR #22.
- `canonical-source.sha256` records the current direct Gradle project, including source added after PR #22.
- `sync-v09318.py` is the auditable one-time migration that ports the verified v0.9.3.18 responsive fixes into the canonical source without replacing later canonical work.
- Canonical CI verifies this manifest and builds directly from checkout.
- Compatibility Lab also copies the canonical checkout and injects only `app/src/debug` fixtures.
- `qa/compatibility/prepare-reconstructed-project.sh` preserves the historical ZIP + patch reconstruction path for audit/recovery; it is no longer the active product source.
