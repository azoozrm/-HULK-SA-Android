# HULK SA Android — Canonical source governance

## Current authority

The direct Gradle project at repository root is the product source of truth. PR #22 initially materialized v0.9.3.17 and proved byte-for-byte parity with the historical reconstruction. PRs #23–#45 then intentionally evolved the canonical source with signing safeguards, adaptive fixes, recommendation-cache fixes, and durable downloads.

The historical reconstruction chain did not receive those canonical-only changes. Compatibility Lab therefore became split from the actual product when PRs #50 and #51 modified reconstruction patches only.

## v0.9.3.18 reconciliation

This reconciliation does not replace the canonical project with reconstructed files. It ports only the verified responsive changes into the current canonical source:

- versionName `0.9.3.18`, versionCode `62`;
- phone-landscape device classification and top navigation;
- mobile navigation insets/targets;
- TV safe-area adjustments;
- explicit Android TV Search navigation/edit mode.

All canonical work merged after PR #22 remains in place, including durable downloads and signing qualification infrastructure.

## Compatibility authority

`qa/compatibility/prepare-project.sh` now copies the canonical checkout and injects debug-only fixtures. The previous ZIP + patch pipeline remains available as `prepare-reconstructed-project.sh` for historical audit and recovery, but it is not the active product source.

## Required evidence

- canonical manifest verification;
- clean, lint, unit, debug and release/R8 builds from checkout;
- ABI verification for APK/AAB;
- Compatibility Lab on the canonical application across all nine profiles;
- artifact review, not workflow color alone.

Reconstruction history must remain until a separate explicit governance decision; this reconciliation does not delete it.
