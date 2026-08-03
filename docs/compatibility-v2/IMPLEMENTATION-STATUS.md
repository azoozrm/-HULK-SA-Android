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
- Added a central adaptive UI policy separating form factor, current window width and height, orientation, and input mode.
- Added responsive policies and unit contracts for compact phones, short landscape phones, tablets, logical TV windows, 720p TV, and 1080p TV.
- Corrected Player D-pad seeking so Left rewinds and Right fast-forwards on both the player surface and seek bar.
- Removed repeated and time-delayed Player focus retries and replaced them with one state-owned request synchronized to a Compose frame.
- Added Compatibility Lab V2 under `quality/compatibility-v2` with fail-closed `PASS`, `FAIL`, `BLOCKED`, and `SKIPPED` states.
- Added static validation, JVM unit tests, Android instrumentation, UI Automator evidence, screenshots, hierarchy XML, Logcat, window/activity state, memory evidence, checksums, targeted retests, full matrix, visual regression, and build/release qualification.
- Added compact two-pane TV Login layout for logical `960×540` windows while preserving approved branding assets.
- Prevented the software keyboard from covering TV Login at startup and added a settled-window instrumentation assertion.
- Added a scrollable compact-height Login fallback for short landscape phones with enlarged font scale.
- Added an instrumentation test that scrolls the short landscape screen and proves the Login and Subscribe actions remain reachable.
- Added semantic geometry enforcement: the runtime PNG and application hierarchy must match the requested `WIDTH×HEIGHT`; swapped or double-rotated evidence is rejected.
- Added an install-over qualification that builds the preserved PR #57 baseline and candidate with one isolated Debug signer, updates with `adb install -r`, proves private data preservation, reruns instrumentation, and requires the updated HULK application to remain foreground in the final screenshot and hierarchy.
- Added a production-signing qualification that fails closed unless all protected inputs are present and the generated APK/AAB match the expected production certificate, package, version, and ABI policy.
- Full Matrix, Targeted Retest, and Install Over workflows are manual-only. Heavy emulator jobs do not run automatically on every commit.

## Current product build evidence

The final product/test APK revision used for runtime qualification is `a3325f2fce3e04edbe7ed08bd0a0c0a95c06cc9f`.

### Fast PR — PASS

- Run: `30745098354`
- Artifact: `HULK-SA-COMPATIBILITY-V2-FAST-30745098354` (`8832660953`)
- V2 tool tests, including geometry rejection contracts: `PASS`
- Static validation: `PASS`
- JVM unit tests: `PASS`
- Lint: `PASS` with no errors
- Debug APK: present
- AndroidTest APK containing the short-landscape reachability test: present
- Mandatory evidence gate: `PASS`
- Artifact checksums: verified

### Build Verification — PASS

- Run: `30745098353`
- Artifact: `HULK-SA-BUILD-EVIDENCE-30745098353` (`8832669077`)
- Debug APK and AAB: `PASS`
- Release unsigned APK and AAB: `PASS`
- R8/release build: `PASS`
- Package: `sa.hulksa.player`
- `versionName`: `0.9.3.20`
- `versionCode`: `64`
- Exact ABI set: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- ELF machine identity: `PASS`
- Legacy `x86`: absent
- Artifact checksums: verified

## API 35 harness root cause and permanent correction

The original API 35 phone/tablet failures were one shared harness/startup sequence, not four independent adaptive-layout defects:

1. `sys.boot_completed=1` could appear before Activity, Package, Settings, Window, storage, and PackageManager internals were stable.
2. Streamed APK installation could begin while PackageManager internals were incomplete.
3. Launcher registration could remain temporarily unresolved after installation.
4. On API 33+ phones, the notification permission window could own the foreground and leave `MainActivity` at `STARTED` during general layout/lifecycle tests.
5. A `pipefail + grep -q` collector pipeline could falsely report an installed component as missing.

Permanent corrections:

- Require three consecutive stable framework and PackageManager probes before installation.
- Wait for broadcast queues to become idle.
- Install app and instrumentation APKs with `adb install --no-streaming`.
- Require three consecutive successful post-install package/launcher/activity registration probes.
- Record installer readiness, installation output, registration evidence, and full installed-package dumps.
- Establish and verify `POST_NOTIFICATIONS` as a documented precondition for the general API 33+ layout/lifecycle matrix; permission policy remains covered by dedicated unit tests.
- Replace fragile component pipelines with file-backed package inspection.
- Preserve Logcat, hierarchy, screenshot, activity/window state, JUnit, and checksums on failures.

## Emulator compatibility qualification — 8/8 PASS

### Phone and tablet profiles

- `phone-small-api29` — `360×640`, Arabic: `PASS` in Full Matrix run `30729876104`.
- `phone-medium-api35` — `1080×2340`, density 420, Arabic: `PASS` in run `30731116951`, artifact `8828021943`.
- `phone-landscape-font150-api35` — `2340×1080`, density 420, font scale 1.5, Arabic: `PASS` in run `30745322644`, artifact `8832707226`.
  - Instrumentation: `7 tests`, `0 failures`, `0 errors`, `2 device-not-applicable skips`.
  - Short-landscape action reachability test: `PASS`.
  - PNG and XML geometry: exactly `2340×1080`.
  - Runtime Evidence Gate: `26 PASS`, `0 FAIL`.
  - Checksums: `26/26` verified.
- `tablet-600-portrait-api35` — `1200×1920`, density 240, font scale 1.3, Arabic: `PASS` in run `30744446072`, artifact `8832428245`.
- `tablet-expanded-landscape-api35` — `2560×1600`, density 320, English: `PASS` in run `30744446072`, artifact `8832429667`.

### Television profiles

- `tv-logical-960x540-api36`: `PASS` in Full Matrix run `30729876104`; compact two-pane layout visually inspected.
- `tv-720p-api36`: `PASS` in Full Matrix run `30729876104`; Login visible with no startup IME.
- `tv-1080p-api36`: `PASS` in Full Matrix run `30729876104`.

### Rejected evidence that led to stronger gates

A prior short-landscape artifact reported profile success while the final PNG/XML were `1080×2340` instead of requested `2340×1080`. That artifact was rejected manually. The permanent `runtime-window-geometry` gate and regression tests now reject this class of double-rotation automatically.

After correcting orientation, visual inspection found the wide Login panel clipped primary actions at font scale 1.5. The product now switches short non-TV windows to a vertically scrollable compact-height layout, and instrumentation proves the primary actions are reachable after scrolling.

## Install-over qualification — Debug lineage PASS

This qualification proves the Android update mechanism and application-data retention using an isolated Debug certificate generated inside one GitHub Actions runner. It does not claim production-signing continuity.

- Preserved baseline source: `c291fa7df2f3ee3a04d020cd831f02073a44d514` from PR #57.
- Candidate application revision: `2c695f63856d75d9431db6eddbbe8f48a539ba30`; later commits only strengthen the qualification harness and documentation.
- Package: `sa.hulksa.player.dev`.
- Version code on both Debug APKs: `64`.
- One isolated Debug certificate used for both builds: `PASS`.
- Baseline installation with `adb install --no-streaming`: `PASS`.
- Candidate update with `adb install --no-streaming -r`: `PASS`.
- Private sentinel data before update: `PASS`.
- Private sentinel data immediately after update: `PASS`.
- Private sentinel data after instrumentation: `PASS`.
- Instrumentation: `7 tests`, `0 failures`, `3 device-not-applicable skips`.
- Final candidate cold launch: `Status: ok`.
- Candidate foreground stability: `3/3` consecutive probes.
- Final screenshot: `1080×2340`, HULK Login visually inspected.
- Final hierarchy package: only `sa.hulksa.player.dev`.
- Evidence checksums: all files verified.
- Final evidence run: `30748646117`.
- Final artifact: `HULK-SA-COMPATIBILITY-V2-INSTALL-OVER-FOREGROUND-30748646117` (`8833740167`).

The permanent `.github/workflows/compatibility-v2-install-over.yml` workflow is manual-only. The temporary one-time evidence workflow was deleted after successful qualification.

## Production-signed release qualification — PASS

The five protected inputs were validated by the production-signing environment without exposing their values:

- `HULK_RELEASE_KEYSTORE_BASE64`
- `HULK_RELEASE_KEY_ALIAS`
- `HULK_RELEASE_STORE_PASSWORD`
- `HULK_RELEASE_KEY_PASSWORD`
- `HULK_RELEASE_CERT_SHA256`

Qualification results:

- Run: `30749549026` — `PASS`.
- Artifact: `HULK-SA-SIGNED-RELEASE-QUALIFICATION-30749549026` (`8834026890`).
- Artifact digest: `sha256:7918bf7b8f97d60f347cc151558f7a5f534deb55db6734b3c5d511225cf6efc6`.
- Signed APK and signed AAB: present and verified.
- Package: `sa.hulksa.player`.
- Version: `0.9.3.20` (`versionCode 64`).
- Certificate SHA-256: matched the protected expected certificate.
- APK signature schemes v1, v2, and v3: verified.
- Exact ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`; legacy `x86` absent.
- APK SHA-256: `9f904d0927de7181b7cf2760b9873d386885c9526bcd289bac250bfad17b5e69`.
- AAB SHA-256: `e706667128383e76da7929ee4c784fb8171bb235632abfb1b171fd4903da6443`.
- Artifact checksums: all files verified manually.

No signing secret or keystore value was printed, copied into the repository, or modified.

## Production-signed PR #57 source-lineage install-over — PASS

This qualification rebuilt the preserved PR #57 source and the current candidate with the same protected production certificate, then performed an in-place Android update on API 35.

- Preserved baseline source: `c291fa7df2f3ee3a04d020cd831f02073a44d514`.
- Qualified candidate APK source: `849cc1b68feb27811256acdf149abbce853109cb`; changes after the product revision are qualification-workflow and documentation only.
- Package on both APKs: `sa.hulksa.player`.
- Version on both APKs: `0.9.3.20` (`versionCode 64`).
- Production certificate continuity: `PASS`.
- Baseline APK SHA-256: `c1ee19bfdb04afc367c35b6594689160138129c09fb0074e1daddd27239f46bf`.
- Candidate APK SHA-256: `dc201bd6c2c14658f11a63b33d3e508fadc441639bac506ae1bc976fe25b4b51`.
- Baseline installation: `PASS`.
- Candidate update with `adb install --no-streaming -r`: `PASS`.
- Application ID/UID remained `10209`: `PASS`.
- Application data directory remained unchanged: `PASS`.
- `firstInstallTime` remained unchanged: `PASS`.
- Candidate cold launch: `Status: ok`, `LaunchState: COLD`, `MainActivity` foreground.
- Foreground stability: `3/3` consecutive probes.
- Final screenshot: exactly `1080×2340`, visually inspected; approved HULK Login is fully visible with no Launcher, permission window, or other system overlay.
- Final hierarchy: `36` nodes and only package `sa.hulksa.player`.
- Evidence checksums: all files verified manually.
- Final run: `30750753716` — `PASS`.
- Final artifact: `HULK-SA-PRODUCTION-INSTALL-OVER-FINAL-30750753716` (`8834384026`).
- Artifact digest: `sha256:879afb3ad189122775c97254883580b0c14e02e82b13cd63046a8ba67988fac9`.

This proves production-certificate continuity and Android in-place update behavior between two APKs rebuilt from the preserved PR #57 source lineage. It does **not** prove byte-for-byte upgrade compatibility from the exact APK binary previously distributed to users; that binary must be supplied and tested separately.

All one-time production qualification workflows were deleted after evidence collection. The permanent fail-closed signed-release workflow remains in `.github/workflows/signed-release-qualification.yml`.

## Branding integrity

The approved files remain protected by static SHA-256 checks:

- `app/src/main/res/drawable-nodpi/hulk_sa_logo.webp`
- `app/src/main/res/drawable-nodpi/ic_banner.webp`

Expected SHA-256: `2704350ef016a65733ed8eb89cd2d006a8d001c7139a0a535526a780d9691b9e`

No shield, HS letters, HULK SA name, color, gradient, background, package ID, version, endpoint, or signing identity was changed.

## Remaining BLOCKED qualification

- Visual regression approval: `BLOCKED: HUMAN-APPROVED FULL-WINDOW BASELINES REQUIRED`.
- Installation over the exact APK binary previously distributed to users: `BLOCKED: DISTRIBUTED PRODUCTION APK BINARY REQUIRED`.
- Xiaomi receiver, TCL television, Galaxy phone, real small phone, real tablet, and real-service downloads: `BLOCKED: PHYSICAL DEVICE OR REAL SERVICE VERIFICATION REQUIRED`.
- PR remains Draft. No merge, release, force push, signing-secret change, keystore change, package/version change, or automatic baseline approval has been performed.

No blocked item is recorded as PASS.
