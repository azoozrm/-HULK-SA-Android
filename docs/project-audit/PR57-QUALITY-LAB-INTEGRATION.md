# PR #57 scope and qualification contract

## Purpose

PR #57 qualifies the limited HULK SA Android product changes for `0.9.3.20` (`versionCode 64`): physical Android TV layout corrections and durable download transport behavior.

This pull request remains an open draft. A green workflow alone is not physical-device acceptance and does not authorize merge or release.

## Product changes in scope

The product diff is limited to:

- `app/build.gradle.kts`
  - package remains `sa.hulksa.player`;
  - version remains `0.9.3.20` (`64`);
  - production runtime endpoint remains `http://3162356.xyz:8080`;
  - release builds reject endpoint overrides.
- `app/src/main/java/sa/hulksa/player/data/DownloadRepository.kt`
  - bounded sequential range requests;
  - `Content-Range` validation;
  - safe fallback when a server ignores a range and returns HTTP 200;
  - persisted byte progress, cancellation and partial-file integrity;
  - finite stalled-read timeout and retry recovery.
- `app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt`
  - TV safe gutters on the affected destinations;
  - bottom clearance for Live actions;
  - viewport-derived rail-logo layout without changing logo bytes;
  - download-card height and focus restoration required by the card-layout regression;
  - phone and tablet branches preserved.
- Focused unit tests for the transport and TV-layout policies introduced by this PR.

## Focus classification

The direct ability to expose and use the controls inside a Downloads card is an in-scope regression contract because PR #57 changes that card's visible layout.

A complete cross-application D-pad graph, generic focus target counts, and centralized focus restoration across every page belong to the later navigation and focus phase. They must not be enforced as PR #57 acceptance unless Git history proves a direct regression caused by this product diff.

## Quality Lab correction in scope

The Compatibility and Quality Lab implementation remains owned by the base branch except for one narrow evidence-boundary correction:

- `qa/compatibility/gate.py` reconciles an Android 9 stale UI Automator hierarchy only when all independent evidence agrees: the Downloads XML is byte-identical to the immediately preceding Search XML, the screenshots differ, Downloads navigation succeeded, the foreground package is correct, repository state contains positive bytes from a loopback fixture, and captured fixture files contain positive bytes.
- `qa/compatibility/tests/test_gate_reconciliation.py` proves the positive path and preserves fail-closed behavior when the XML is not stale or byte evidence is absent.

This correction does not infer transport from a UI marker, does not alter production source, and does not suppress unrelated critical findings. It records every reclassification in `GATE-CORRECTIONS.json` with hashes and source evidence.

Disposable marker instrumentation remains debug-only and fail-closed. It must not modify the original production checkout or enter release artifacts.

Branch-local experimental accessibility overlays, duplicate analyzer adapters, source-shape adaptations and future-stage global focus enforcement accumulated during earlier PR iterations were removed from the final tree.

## Final changed-file inventory

The final intended PR diff contains nine files:

- `app/build.gradle.kts`
- `app/src/main/java/sa/hulksa/player/data/DownloadRepository.kt`
- `app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt`
- `app/src/test/java/sa/hulksa/player/data/DownloadTransportPolicyTest.kt`
- `app/src/test/java/sa/hulksa/player/ui/screens/TvLayoutPolicyTest.kt`
- `docs/project-audit/PR57-QUALITY-LAB-INTEGRATION.md`
- `qa/canonical/canonical-source.sha256`
- `qa/compatibility/gate.py`
- `qa/compatibility/tests/test_gate_reconciliation.py`

No workflow, device matrix, baseline, retry policy, report schema, production resource or logo asset is changed by the final PR diff.

## Canonical integrity

`qa/canonical/canonical-source.sha256` is a deterministic inventory of the canonical app, test and build inputs. Qualification requires:

- every listed path exists;
- no duplicate, absolute or repository-external path exists;
- every digest matches the checked-in bytes;
- the two PR-specific product unit tests are included;
- deleted or abandoned test files are absent;
- approved identity assets remain byte-identical.

Approved identity SHA-256:

- `app/src/main/res/drawable-nodpi/hulk_sa_logo.webp`: `2704350ef016a65733ed8eb89cd2d006a8d001c7139a0a535526a780d9691b9e`
- `app/src/main/res/drawable-nodpi/ic_banner.webp`: `2704350ef016a65733ed8eb89cd2d006a8d001c7139a0a535526a780d9691b9e`

## Evidence policy

Only workflow runs whose `head_sha` equals the current PR head may qualify the final tree. Older runs are historical evidence and must be marked `OLD HEAD — DO NOT USE FOR FINAL QUALIFICATION`.

Required evidence includes canonical verification, generated-source comparison, unit and instrumentation results, APK/AAB/R8/lint outputs where available, Compatibility Lab reports, screenshots, UI XML, download repository state, transferred file bytes and artifact inspection.

Physical Xiaomi, TCL, phone, tablet, install-over, production signing and real-account download checks remain `NOT VERIFIED` unless their own artifacts are supplied.

## Immutable identity

- App name and branding: HULK SA, unchanged
- Package: `sa.hulksa.player`
- Version name: `0.9.3.20`
- Version code: `64`
- Production endpoint: `http://3162356.xyz:8080`
- PR state requirement: open draft; no merge or release from this qualification task
