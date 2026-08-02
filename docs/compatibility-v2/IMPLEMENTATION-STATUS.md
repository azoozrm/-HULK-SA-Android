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

- Removed the legacy Compatibility/Quality Lab implementation and obsolete runtime hooks.
- Added a central adaptive UI policy covering form factor, window width/height, orientation, and input mode.
- Added fail-closed static, JVM, instrumentation, UI Automator, runtime evidence, geometry, visual, targeted, full-matrix, install-over, and release gates.
- Corrected Player D-pad seek direction and removed repeated/delayed focus retries.
- Added compact logical-TV Login and scrollable short-landscape Login layouts while preserving approved branding.
- Added instrumentation proving Login and Subscribe remain reachable on short landscape with font scale `1.5`.
- Added exact PNG/XML geometry enforcement that rejects swapped or double-rotated evidence.
- Added manual-only Full Matrix, Targeted Retest, and Debug Install Over workflows.
- Added fail-closed production-signing qualification against protected certificate, package, version, and ABI policy.

## Current-head build gates — PASS

### Fast PR

- Run: `30750938561`
- Artifact: `HULK-SA-COMPATIBILITY-V2-FAST-30750938561` (`8834472353`)
- Artifact digest: `sha256:23261112929602b34d69d42aa18e89c257898ec82270112f7ae6b8240ad25e56`
- Static checks: `21 PASS`, `0 FAIL`
- JVM unit tests: `68`, failures/errors/skips: `0/0/0`
- Lint errors: `0`
- Mandatory evidence gate and checksums: `PASS`

### Build Verification

- Run: `30750938562`
- Artifact: `HULK-SA-BUILD-EVIDENCE-30750938562` (`8834506506`)
- Artifact digest: `sha256:aeb1117cd26a8ac4d46795a245e9f8bfcd24d18165555dda088162b9bde12037`
- Debug build, unit tests, lint: `PASS`
- Release/R8: `PASS`
- Package/version identity: `PASS`
- Exact ABI and ELF identity: `PASS`
- Artifact checksums: `PASS`

## API 35 harness root cause and permanent correction

The original API 35 phone/tablet failures were caused by one shared startup/harness sequence rather than independent adaptive-layout defects:

- Framework and PackageManager services could report boot complete before becoming stable.
- Streamed installation and launcher registration could race PackageManager startup.
- `POST_NOTIFICATIONS` could place a system permission window above `MainActivity`.
- A fragile `pipefail + grep -q` pipeline could misreport an installed component as absent.

Permanent corrections require stable framework probes, idle broadcast queues, non-streaming installs, stable launcher registration, notification-permission preconditions for the general API 33+ matrix, file-backed package inspection, and preservation of failure evidence.

## Emulator compatibility qualification — 8/8 PASS

### Phones and tablets — 5/5

- `phone-small-api29`: PASS — Full Matrix run `30729876104`
- `phone-medium-api35`: PASS — run `30731116951`, artifact `8828021943`
- `phone-landscape-font150-api35`: PASS — run `30745322644`, artifact `8832707226`
  - Requested/final geometry: `2340×1080`
  - Font scale: `1.5`
  - Instrumentation: `7 tests`, `0 failures`, `0 errors`, `2` device-not-applicable skips
  - Runtime Evidence Gate: `26 PASS`, `0 FAIL`
  - Checksums: `26/26`
- `tablet-600-portrait-api35`: PASS — run `30744446072`, artifact `8832428245`
- `tablet-expanded-landscape-api35`: PASS — run `30744446072`, artifact `8832429667`

### Android TV — 3/3

- `tv-logical-960x540-api36`: PASS — Full Matrix run `30729876104`
- `tv-720p-api36`: PASS — Full Matrix run `30729876104`
- `tv-1080p-api36`: PASS — Full Matrix run `30729876104`

A prior short-landscape artifact with swapped geometry was deliberately rejected. The permanent evidence gate now validates requested geometry against both PNG IHDR and application hierarchy bounds.

## Debug install-over qualification — PASS

- Final run: `30748646117`
- Artifact: `HULK-SA-COMPATIBILITY-V2-INSTALL-OVER-FOREGROUND-30748646117` (`8833740167`)
- Preserved PR #57 baseline and candidate used one isolated Debug signer.
- Package: `sa.hulksa.player.dev`
- Baseline install: `PASS`
- Candidate `adb install --no-streaming -r`: `PASS`
- Private sentinel data before/after update and after instrumentation: `PASS`
- Instrumentation: `7 tests`, `0 failures`
- Final cold launch and `3/3` foreground probes: `PASS`
- Final HULK screenshot/hierarchy and checksums: `PASS`

This proves the Android update mechanism and app-data retention under an isolated Debug lineage; it is not used as production-signing evidence.

## Production-signed release qualification — PASS

The protected `production-signing` environment validated all five required inputs without exposing their values:

- `HULK_RELEASE_KEYSTORE_BASE64`
- `HULK_RELEASE_KEY_ALIAS`
- `HULK_RELEASE_STORE_PASSWORD`
- `HULK_RELEASE_KEY_PASSWORD`
- `HULK_RELEASE_CERT_SHA256`

Qualification results:

- Run: `30749549026`
- Artifact: `HULK-SA-SIGNED-RELEASE-QUALIFICATION-30749549026` (`8834026890`)
- Artifact digest: `sha256:7918bf7b8f97d60f347cc151558f7a5f534deb55db6734b3c5d511225cf6efc6`
- Signed APK and AAB: `PASS`
- Expected production certificate: matched
- Package/version: `sa.hulksa.player`, `0.9.3.20 (64)`
- Exact ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`; `x86` absent
- APK SHA-256: `9f904d0927de7181b7cf2760b9873d386885c9526bcd289bac250bfad17b5e69`
- AAB SHA-256: `e706667128383e76da7929ee4c784fb8171bb235632abfb1b171fd4903da6443`
- Checksums: `PASS`

No secret or keystore value was printed, committed, or modified.

## Production-signed PR #57 source-lineage install-over — PASS

The preserved PR #57 source and candidate were rebuilt with the same protected production certificate and updated in place on API 35.

- Baseline source: `c291fa7df2f3ee3a04d020cd831f02073a44d514`
- Qualified candidate APK source: `849cc1b68feb27811256acdf149abbce853109cb`
- Baseline APK SHA-256: `c1ee19bfdb04afc367c35b6594689160138129c09fb0074e1daddd27239f46bf`
- Candidate APK SHA-256: `dc201bd6c2c14658f11a63b33d3e508fadc441639bac506ae1bc976fe25b4b51`
- Final run: `30750753716`
- Artifact: `HULK-SA-PRODUCTION-INSTALL-OVER-FINAL-30750753716` (`8834384026`)
- Artifact digest: `sha256:879afb3ad189122775c97254883580b0c14e02e82b13cd63046a8ba67988fac9`
- Baseline install and candidate `adb install --no-streaming -r`: `PASS`
- Certificate, UID/appId, data directory, and `firstInstallTime` continuity: `PASS`
- Candidate cold launch and `3/3` foreground probes: `PASS`
- Final `1080×2340` HULK screenshot, hierarchy, Logcat, and checksums: `PASS`

This source-lineage qualification was followed by a separate exact-binary qualification using the actual APK stored for distribution.

## Exact distributed production APK install-over — PASS

The exact `HULK-SA-v0.9.3.20-SIGNED.apk` binary stored in the verified Google Drive folder `App hulk` was downloaded by the protected workflow and verified before installation.

### Exact baseline identity

- Google Drive file ID: `1J-_Hild8gX-ifBaWx9VQapVvblzkvp7V`
- File size: `3,426,664` bytes
- Exact distributed APK SHA-256: `eaecc4cdc3ec72083aabd6207df5c563169d3b2b4347f5d3677d88ea0cc99377`
- Package: `sa.hulksa.player`
- Version: `0.9.3.20` (`versionCode 64`)
- Production certificate: matched the protected expected certificate

### Candidate identity

- Candidate APK SHA-256: `dc201bd6c2c14658f11a63b33d3e508fadc441639bac506ae1bc976fe25b4b51`
- Package/version and production certificate: matched the exact distributed baseline

### Runtime evidence

- Run: `30751908315` — `PASS`
- Artifact: `HULK-SA-EXACT-DISTRIBUTED-PRODUCTION-INSTALL-OVER-30751908315` (`8834737668`)
- Artifact digest: `sha256:3233e6ad38672c50fdd3c7b9ba985c1ad23eda7acbf03fe70804a7195728b26c`
- Exact distributed baseline installation: `PASS`
- Candidate update using `adb install --no-streaming -r -g`: `PASS`
- UID/appId remained `10209`: `PASS`
- Data directory remained `/data/user/0/sa.hulksa.player`: `PASS`
- `firstInstallTime` remained unchanged: `PASS`
- External app-data sentinel remained intact: `PASS`
- Candidate cold launch: `Status: ok`, `LaunchState: COLD`
- Candidate foreground stability: `3/3` consecutive probes
- Final screenshot: exactly `1080×2340`, manually inspected; HULK Login is visible with no Launcher, permission dialog, or system overlay
- Final hierarchy: `36` nodes, only package `sa.hulksa.player`, required Login actions present
- Crash/ANR scan: clean
- Every artifact checksum: verified

This closes the exact previously distributed production APK upgrade gate. The one-time workflow used to collect this evidence is removed after this record is committed.

## Branding integrity

The approved logo/banner SHA-256 remains:

`2704350ef016a65733ed8eb89cd2d006a8d001c7139a0a535526a780d9691b9e`

No shield, HS letters, HULK SA name, color, gradient, background, package ID, version, endpoint, signing identity, secret, or keystore was changed.

## Remaining BLOCKED qualification

- `BLOCKED: HUMAN-APPROVED FULL-WINDOW VISUAL BASELINES REQUIRED`
- `BLOCKED: PHYSICAL DEVICE OR REAL SERVICE VERIFICATION REQUIRED` for Xiaomi receiver, TCL television, Galaxy/real phone/tablet testing, and real-service download qualification

PR #78 remains Draft, open, and unmerged. No release, force push, package/version change, signing-secret change, keystore change, or automatic visual-baseline approval has been performed. No blocked item is recorded as PASS.
