# HULK SA Adaptive UI and Compatibility Lab V2 — implementation status

## Verified source baseline

- Source PR: `#57`
- Verified preserved head: `c291fa7df2f3ee3a04d020cd831f02073a44d514`
- New branch: `rebuild/adaptive-ui-compatibility-lab-v2`
- New draft PR: `#78`
- Base: `phase-3-v0.9.3.0-adaptive-foundation`
- Package: `sa.hulksa.player`
- Version: `0.9.3.20` (`versionCode 64`)

## Completed changes

- Removed the legacy `qa/compatibility`, `qa/quality`, and `qa/canonical` implementations and their workflows, fixtures, generated snapshots, analyzers, reports, baselines, retry policy, marker injection, documentation, and old runtime tools.
- Removed old Android test fixtures that did not exercise the installed production application.
- Introduced a central adaptive policy that separates form factor, current window size, orientation, and active input mode.
- Added height classes, responsive design tokens, tablet rail policy, compact-window fallback, TV focus presentation policy, and unit tests.
- Rebuilt Compatibility Lab V2 under `quality/compatibility-v2` without production markers or test endpoints.
- Added fail-closed static, unit, instrumentation, UI Automator, evidence, visual, full-matrix, targeted, and signed-release gates.
- Adapted Login for compact TV windows, safe drawing, IME, scrolling, and 48dp interactive targets while preserving the approved logo asset.

## Proven open product failures

These are intentionally quality-gate failures, not PASS:

1. `PlayerScreen.kt` maps D-pad Left/Right seek directions opposite to the media rewind/fast-forward contract on the player surface and seek bar.
2. `PlayerScreen.kt` contains repeated and time-delayed `requestFocus()` retries for controls, resume, next episode, unlock, and channel browser focus.

Compatibility V2 static validation fails until the product code removes both defects. The test is not weakened and no expected result is rewritten.

## Execution status

- Repository mutation and source inspection: `PASS`.
- Legacy lab removal from the active branch: `PASS` by tree inspection.
- Package/version/Manifest/logo source contracts: implemented; runtime result is `BLOCKED` until a GitHub Actions run ID is available.
- Debug/release build, JVM unit, lint, Android instrumentation, D-pad matrix, runtime diagnostics, and artifact inspection: `BLOCKED: NO WORKFLOW RUN ID EXPOSED TO THE CONNECTED GITHUB SESSION`.
- Visual regression: `BLOCKED: HUMAN-APPROVED FULL-WINDOW BASELINE REQUIRED`.
- Xiaomi receiver, TCL TV, Galaxy phone, small phone, tablet, install-over, and real-service downloads: `BLOCKED: PHYSICAL DEVICE OR REAL SERVICE VERIFICATION REQUIRED`.

No blocked item is recorded as PASS.

## Branding integrity

The approved files are read-only and verified by SHA-256:

- `app/src/main/res/drawable-nodpi/hulk_sa_logo.webp`
- `app/src/main/res/drawable-nodpi/ic_banner.webp`

Expected SHA-256: `2704350ef016a65733ed8eb89cd2d006a8d001c7139a0a535526a780d9691b9e`

No logo file, shield, HS letters, HULK SA name, color, gradient, background, or visual composition was changed.
