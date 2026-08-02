# HULK SA Adaptive UI and Compatibility Lab V2 — implementation status

## Verified identity

- Source PR: `#57`
- Preserved product head: `c291fa7df2f3ee3a04d020cd831f02073a44d514`
- Replacement branch: `rebuild/adaptive-ui-compatibility-lab-v2`
- Draft PR: `#78`
- Base: `phase-3-v0.9.3.0-adaptive-foundation`
- Package: `sa.hulksa.player`
- Version: `0.9.3.20` (`versionCode 64`)
- Qualified ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- Excluded legacy ABI: `x86`

## Completed product and lab changes

- Removed the legacy `qa/compatibility`, `qa/quality`, and `qa/canonical` labs, workflows, fixtures, generated baselines, reports, retry policy, production markers, overlays, and obsolete runtime tools.
- Added a central adaptive UI policy separating form factor, window width and height, orientation, and input mode.
- Added responsive policies and unit contracts for compact phones, landscape phones, tablets, logical TV windows, 720p TV, and 1080p TV.
- Corrected Player D-pad seeking so Left rewinds and Right fast-forwards on both the player surface and seek bar.
- Removed repeated and time-delayed Player focus retries and replaced them with one state-owned request synchronized to a Compose frame.
- Added Compatibility Lab V2 under `quality/compatibility-v2` with fail-closed `PASS`, `FAIL`, `BLOCKED`, and `SKIPPED` states.
- Added static validation, JVM unit tests, Android instrumentation, UI Automator evidence, full-window screenshots, hierarchy XML, Logcat, window/activity state, memory evidence, checksums, targeted retests, full matrix, visual regression, and build/release qualification.
- Added compact two-pane TV Login layout for logical `960×540` windows while preserving the approved logo and banner assets.
- Prevented the software keyboard from covering the TV Login screen at startup and added an instrumentation assertion for the settled-window state.
- Full Matrix and Targeted Retest workflows are manual-only. No heavy emulator matrix runs automatically on every commit.

## Current-head build evidence

Current implementation head before this documentation-only update: `c9b207d66b2595dcca11e915d7416804848a156e`.

### Fast PR

- Run: `30731238610`
- Artifact: `HULK-SA-COMPATIBILITY-V2-FAST-30731238610` (`8828084067`)
- V2 static validation: `21 PASS`, `0 FAIL`, `0 BLOCKED`, `0 SKIPPED`
- JVM unit tests: `68 executed`, `0 failures`, `0 errors`, `0 skipped`
- Lint: `0 errors`, `38 warnings`, `1 hint`
- Debug APK: present
- AndroidTest APK: present
- Mandatory evidence gate: `5/5 PASS`
- Artifact checksums: verified

### Build Verification

- Run: `30731238613`
- Artifact: `HULK-SA-BUILD-EVIDENCE-30731238613` (`8828098882`)
- Debug APK and AAB: `PASS`
- Release unsigned APK and AAB: `PASS`
- R8/release build: `PASS`
- Package: `sa.hulksa.player`
- `versionName`: `0.9.3.20`
- `versionCode`: `64`
- ABI qualification for all four archives: `PASS`
- Exact ABI set: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- ELF machine identity: `PASS`
- Legacy `x86`: absent
- Artifact checksums: verified

## Runtime qualification

### Proven API 35 root cause and permanent fix

The repeated API 35 phone/tablet failures were one shared harness/startup sequence, not four independent adaptive-layout defects:

1. `sys.boot_completed=1` could appear before Activity, Package, Settings, Window, storage, and PackageManager internals were stable.
2. Streamed APK installation could begin while PackageManager internals were incomplete.
3. Launcher registration could remain temporarily unresolved after installation.
4. On API 33+ phones, the notification permission window could own the foreground and leave `MainActivity` at `STARTED` during general layout/lifecycle tests.
5. A `pipefail + grep -q` collector pipeline could report a missing component even when the installed package dump contained it.

Permanent corrections:

- Require three consecutive stable framework and PackageManager probes before installation.
- Wait for broadcast queues to become idle.
- Install app and instrumentation APKs with `adb install --no-streaming`.
- Require three consecutive successful post-install package/launcher/activity registration probes.
- Record installer readiness, installation output, package registration, and full installed-package dumps.
- Establish and verify `POST_NOTIFICATIONS` as a documented precondition for the general API 33+ layout/lifecycle matrix; the permission decision remains covered by dedicated unit tests.
- Replace the fragile component pipeline with file-backed package inspection.
- Preserve Logcat, hierarchy, screenshot, activity/window state, JUnit, and checksums when runtime execution fails.

### Successful API 35 proof

- Run: `30731116951`
- Artifact: `HULK-SA-COMPATIBILITY-V2-API35-SMOKE-30731116951` (`8828021943`)
- Profile: `phone-medium-api35`
- Window: `1080×2340`, density `420`, locale `ar-SA`
- Installer readiness: `PASS`
- Non-streaming app/test installation: `PASS`
- Delayed launcher registration wait: `PASS`
- Notification permission precondition: `PASS`
- Instrumentation: `6 tests`, `0 failures`, `0 errors`, `2 device-not-applicable skips`
- Foreground application: `PASS`
- IME hidden: `PASS`
- Full-window screenshot and hierarchy: `PASS`
- Runtime evidence gate: all checks `PASS`
- Evidence checksums: `26/26` verified

### Previous full-matrix evidence

Full Matrix run `30729876104` proved the following profiles before the API 35 harness correction:

- `phone-small-api29`: `PASS`
- `tv-logical-960x540-api36`: `PASS`
- `tv-720p-api36`: `PASS`
- `tv-1080p-api36`: `PASS`

The four API 35 jobs in that run failed through the shared startup/permission harness sequence described above. `phone-medium-api35` has since passed after the permanent correction. The remaining three API 35 profiles are not promoted to PASS until individually rerun with the corrected harness:

- `phone-landscape-font150-api35`: `PENDING TARGETED CONFIRMATION`
- `tablet-600-portrait-api35`: `PENDING TARGETED CONFIRMATION`
- `tablet-expanded-landscape-api35`: `PENDING TARGETED CONFIRMATION`

## Branding integrity

The approved files remain protected by static SHA-256 checks:

- `app/src/main/res/drawable-nodpi/hulk_sa_logo.webp`
- `app/src/main/res/drawable-nodpi/ic_banner.webp`

Expected SHA-256: `2704350ef016a65733ed8eb89cd2d006a8d001c7139a0a535526a780d9691b9e`

No shield, HS letters, HULK SA name, color, gradient, background, package ID, version, endpoint, or signing identity was changed.

## Remaining BLOCKED qualification

- Visual regression approval: `BLOCKED: HUMAN-APPROVED FULL-WINDOW BASELINES REQUIRED`.
- Xiaomi receiver, TCL television, Galaxy phone, real small phone, real tablet, install-over-existing-version, signing continuity, and real-service downloads: `BLOCKED: PHYSICAL DEVICE OR REAL SERVICE VERIFICATION REQUIRED`.
- PR remains Draft. No merge, release, force push, automatic baseline update, or production signing operation has been performed.

No pending or blocked item is recorded as PASS.