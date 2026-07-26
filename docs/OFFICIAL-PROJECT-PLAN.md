# HULK SA Android — Official Project Plan and Handoff

This file is the permanent source of truth for every new ChatGPT conversation and every future development session.

## Core rule

- Continue the existing project. Do not restart from scratch.
- Do not redesign or modify completed work unless fixing a verified defect.
- Every build must be produced from source code only.
- All future engineering changes must be source changes stored and reviewable in GitHub.
- APK and AAB files are outputs, test artifacts, and stability references only; they are never the development base.
- Do not rebuild, patch, modify DEX, edit a compiled APK, or repackage an old APK.
- APK v0.9.1.19 is only the stability, installation, signing, and behavior reference.
- Source v0.9.1.20 is the approved recovery and development base.
- Do not modify `main` directly. Work in a dedicated branch, validate, then merge only after approval.
- Every version must preserve a complete Git history, workflow logs, source package, binaries, reports, changelog, and checksums.

## Completed work that must be preserved

- HomeContentSnapshot.
- Smart Home optimization.
- Recommendation improvements.
- Downloads redesign and related download logic.
- Focus and remote-navigation improvements.
- Performance improvements.

## Mandatory engineering sequence

1. Inspect and recover the complete v0.9.1.20 source.
2. Stabilize source build.
3. Fix installation, update path, signing, and package compatibility before the final stable release.
4. Qualify supported architectures.
5. Adapt and validate mobile, tablet, Android TV, Google TV, and TV Box behavior.
6. Run tests and regression checks.
7. Publish a stable v1.0.
8. Begin major new features only after v1.0.

The first major feature after v1.0 is Multi Profile.

## Current verified source facts

- package/applicationId: `sa.hulksa.player`
- Debug applicationId: `sa.hulksa.player.dev`
- Stable source base versionName: `0.9.1.20`
- Stable source base versionCode: `42`
- Phase 2 engineering versionName: `0.9.2.0`
- Phase 2 engineering versionCode: `43`
- Phase 3 engineering versionName: `0.9.3.0`
- Phase 3 engineering versionCode: `44`
- compileSdk: `36`
- targetSdk: `36`
- minSdk: `23`
- Android Gradle Plugin: `8.13.2`
- Kotlin: `2.2.21`
- Source audit found 23 Kotlin files, 7 XML files, and 41 total files before Phase 3 adaptive additions.

## Current GitHub state

Repository: `azoozrm/-HULK-SA-Android`

Completed source-build branch:

- `phase-0-v0.9.1.20-audit`

Completed architecture branch pending owner merge approval:

- `phase-2-v0.9.2.0-architecture`

Completed adaptive-foundation branch pending owner device acceptance and merge approval:

- `phase-3-v0.9.3.0-adaptive-foundation`

Relevant completed Phase 0 commits:

- Phase 0 audit document: `e92a2eef7f83b0b9672021f82abc98bae67e79fd`
- Phase 0 audit workflow: `e1193140dc61fe9205d8c28b5ca3946eb3811294`
- Audit uploaded source archive workflow update: `cf482665cef2c9123d3bd3ebd47b2c270935ade0`
- Source-only build workflow: `f365b6d5c8a3662426e29d7dd8db68c3e19b9cd8`
- Full Gradle log capture and monitored build workflow: `c2fb77f1aa42084008a37d362b031804a56604d2`
- First Compose stability-field repair: `8db2c097d98a1903c793d78ff308f57a28dbc8f6`
- Final Compose/JvmField build repair and successful source build: `fbac81787054f07dd4e996e2e9be55954bfb601a`

Phase 2 commits:

- Source-only v0.9.2.0 architecture preparation: `bcdf646e9bd14c50871ca6277498deedbf0ae60f`
- APK/AAB ELF architecture verifier: `8714e9f3441e290db23907cd6a49ae6066e98d54`
- Phase 2 architecture qualification document: `036ccbfb3c7bf98fd2631387607a3e16c5bf928e`
- Phase 2 source-built APK and AAB workflow: `69044a2a134e4062866b5077386bb311c62ae8eb`
- Source-only governance and official Phase 2 activation: `ed659e10bef0e252ec46f8bfcdccf41e69c29edd`
- Successful Phase 2 result record: `bac74bfda1fbc383a1e1eb5d3d7e0e8feb82cacc`

Key Phase 3 commits:

- Initial adaptive source plan: `4b62b6f20aabe8dca7a6da3dfde58b0f94f39c45`
- Adaptive source policy verifier: `fab6928b1ab43a7bab94ca26eb343586c4fe7d13`
- Adaptive foundation specification: `07a7b8f20241756a1f8edce11327c85883478a80`
- Source-template preparation tool: `5d5b06c69aeeef13fd28222800c6b869f4361dd2`
- Reviewed adaptive templates: `83834fc0b0038f1cd614e4058c1e736189f8c8cc`, `fb28ce13b95ebe141b0c77ec21eaad413eda4768`, `a45cc0249fa262875e792f5973e7147b362e9421`, `024f8060c0b3386568ddcd0aab720f8e32412e8a`
- Source-template build workflow: `d4b16f4a32e31dc630aff837d638979bccae81b4`
- Correct Compose input-source implementation and successful validated source head: `f973500ad8b358ccf77d40649fc859c88014a74a`
- Obsolete corrupt patch removed: `b154b76c9ac87676971a72cb5df2adf2e9d5f3f6`
- Phase 3 successful result: `docs/PHASE-3-ADAPTIVE-RESULT.md`

Workflows:

- `.github/workflows/phase0-audit.yml`
- `.github/workflows/build-v09120.yml`
- `.github/workflows/build-v0920-architecture.yml`
- `.github/workflows/build-v0930-adaptive-foundation.yml`

Validation pull requests:

- PR `#18` — `Phase 0 — build and repair HULK SA v0.9.1.20`
- Draft PR `#19` — `Phase 2 — qualify v0.9.2.0 Android architectures`
- Draft PR `#20` — `Phase 3 — adaptive UI foundation v0.9.3.0`

None of these pull requests may be merged without explicit owner approval.

## Verified v0.9.1.20 build status

- Source audit succeeded.
- `clean` succeeded.
- `:app:assembleDebug` succeeded.
- `testDebugUnitTest` succeeded.
- The first APK built directly from the official v0.9.1.20 source was produced successfully.
- Complete delivery packaging succeeded.
- SHA256 verification succeeded.
- Successful workflow run: `30184291495`.
- GitHub artifact: `HULK-SA-v0.9.1.20-COMPLETE`.
- APK SHA256: `2d766d539091fce11254e3481d900725be3880898034e80937b53ec2443d4d40`.
- This APK was the accepted Phase 2 source baseline, not the final v1.0 release.

## Phase 2 architecture qualification — completed

Status: **PASS**.

GitHub validation:

- Source head commit: `ed659e10bef0e252ec46f8bfcdccf41e69c29edd`
- Pull-request validation merge commit: `ffc5c20d2f8e724ab376bee4b7f8306e4d95ae61`
- Workflow run: `30185048162`
- Workflow job: `89748091903`
- Complete artifact ID: `8626765893`
- Complete artifact: `HULK-SA-v0.9.2.0-ARCHITECTURE-QUALIFIED`
- Diagnostics artifact ID: `8626765957`
- Detailed result: `docs/PHASE-2-ARCHITECTURE-RESULT.md`

Qualified architectures:

- `arm64-v8a` — ELF `EM_AARCH64`
- `armeabi-v7a` — ELF `EM_ARM`
- `x86_64` — ELF `EM_X86_64`

Excluded:

- `x86`

The project owner installed the Phase 2 Universal APK on a Xiaomi device and confirmed installation, launch, navigation, login, and playback worked.

## Phase 3 adaptive foundation — completed

Status: **PASS**, pending manual device acceptance.

GitHub validation:

- Validated source head: `f973500ad8b358ccf77d40649fc859c88014a74a`
- Pull-request validation merge commit: `21996c7dbd917a0d70d7cb652a09e4dcab90dc87`
- Workflow run: `30186296812`
- Complete artifact ID: `8627152631`
- Complete artifact: `HULK-SA-v0.9.3.0-ADAPTIVE-FOUNDATION`
- Diagnostics artifact ID: `8627152793`
- Detailed result: `docs/PHASE-3-ADAPTIVE-RESULT.md`

Implemented adaptive foundation:

- Mobile, tablet, and television device classes.
- Compact, medium, and expanded window-width classes.
- Touch, D-pad/remote/gamepad, and keyboard input modes.
- Correct Android input-source bitmask classification.
- Cinematic rail navigation for television and expanded windows.
- Existing top navigation for compact and medium windows.
- TV-style shared focus chrome suppressed during touch input.
- Focus feedback restored during keyboard or remote input and always retained on television.
- Television detection through UI mode, Leanback, and television device features.
- Forced landscape and immersive mode restricted to actual television devices.
- Same ViewModel, Repository, API, persistence, downloads, player flow, and domain models.

Passed checks:

- Official source extraction and repair.
- Phase 2 architecture preparation.
- Phase 3 source-template preparation.
- Idempotent source preparation.
- Adaptive source policy verification.
- Kotlin compilation.
- Unit tests and adaptive classification tests.
- Universal APK and AAB generation.
- APK/AAB ZIP, ABI, native-library coverage, and ELF verification.
- Version/package badging.
- Complete delivery packaging.
- SHA256 verification.

Produced SHA256 values:

- Universal APK: `7f4b3d60b608647074a053881fe54e04a0e8318ea977852a17c36e84364c4b45`
- Android App Bundle: `2f332575459de6c2c2bd9c86a1edf48b9689d9c2a0a403591bda965d3be90cd3`
- Prepared source ZIP: `bdc59199d13103be41e07d103612b8186f3e6023281b9ad5db013f3dc35a96aa`

## Next engineering stage

First complete manual Phase 3 acceptance on:

- Xiaomi/mobile touch device.
- Android TV, Google TV, or TV box using D-pad remote.
- Tablet if available, or an expanded-window Android device/emulator during the next automated test stage.

Required smoke tests:

- Installation and launch.
- Login and logout.
- Home and navigation.
- Movie details and movie playback.
- Series details and episode playback.
- Live channel playback.
- Downloads.
- Back navigation.
- Touch use without persistent television focus borders/scaling.
- Remote use with visible focus feedback and reliable rail navigation.

After manual acceptance, continue source-only adaptation and regression validation for individual screens and component sizing. Do not redesign the approved application. Fix only verified mobile, tablet, TV, focus, density, overflow, or navigation defects.

Installation/update compatibility and release signing remain mandatory gates before v1.0. No APK or signing binary work may replace source development.

## Release and delivery rule for every approved version

Every approved version must be stored in GitHub and delivered as a complete versioned package containing:

- Full Android source ZIP.
- APK built from that exact source commit.
- Android App Bundle when required by the stage.
- Release APK when signing is available and approved.
- Build report.
- Architecture/device reports required by the stage.
- Changelog.
- SHA256 checksums.
- Git commit and version tag.

A version is not approved merely because an APK was produced. It must have no known blocking defect and must pass the required build, test, installation, signing, architecture, device, and regression checks for its stage.

## Instruction for a new conversation

Read this file first, inspect the latest commits, workflows, runs, jobs, and logs in the repository, then continue from the current GitHub state. Do not ask the user to re-explain the project, do not restart from scratch, and do not use an APK as the development source.
