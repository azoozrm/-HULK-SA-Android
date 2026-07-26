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
- Source v0.9.1.20 is the approved development base.
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
- compileSdk: `36`
- targetSdk: `36`
- minSdk: `23`
- Android Gradle Plugin: `8.13.2`
- Kotlin: `2.2.21`
- Source audit found 23 Kotlin files, 7 XML files, and 41 total files.

## Current GitHub state

Repository: `azoozrm/-HULK-SA-Android`

Completed source-build branch:

- `phase-0-v0.9.1.20-audit`

Completed architecture branch pending owner merge approval:

- `phase-2-v0.9.2.0-architecture`

Relevant completed commits:

- Phase 0 audit document: `e92a2eef7f83b0b9672021f82abc98bae67e79fd`
- Phase 0 audit workflow: `e1193140dc61fe9205d8c28b5ca3946eb3811294`
- Audit uploaded source archive workflow update: `cf482665cef2c9123d3bd3ebd47b2c270935ade0`
- Source-only build workflow: `f365b6d5c8a3662426e29d7dd8db68c3e19b9cd8`
- Full Gradle log capture and monitored build workflow: `c2fb77f1aa42084008a37d362b031804a56604d2`
- First Compose stability-field repair: `8db2c097d98a1903c793d78ff308f57a28dbc8f6`
- Final Compose/JvmField build repair and successful source build: `fbac81787054f07dd4e996e2e9be55954bfb601a`
- Successful-build plan update: `9998510ff3e565b256fe7813014ca0c506d48bd5`

Phase 2 commits:

- Source-only v0.9.2.0 architecture preparation: `bcdf646e9bd14c50871ca6277498deedbf0ae60f`
- APK/AAB ELF architecture verifier: `8714e9f3441e290db23907cd6a49ae6066e98d54`
- Phase 2 architecture qualification document: `036ccbfb3c7bf98fd2631387607a3e16c5bf928e`
- Phase 2 source-built APK and AAB workflow: `69044a2a134e4062866b5077386bb311c62ae8eb`
- Source-only governance and official Phase 2 activation: `ed659e10bef0e252ec46f8bfcdccf41e69c29edd`
- Successful Phase 2 result record: `bac74bfda1fbc383a1e1eb5d3d7e0e8feb82cacc`

Workflows:

- `.github/workflows/phase0-audit.yml`
- `.github/workflows/build-v09120.yml`
- `.github/workflows/build-v0920-architecture.yml`

Validation pull requests:

- PR `#18` — `Phase 0 — build and repair HULK SA v0.9.1.20`
- Draft PR `#19` — `Phase 2 — qualify v0.9.2.0 Android architectures`

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

Passed checks:

- Source-only preparation.
- Approved source-marker preservation.
- Clean Gradle build.
- Universal APK generation.
- Android App Bundle generation.
- Unit tests.
- APK ZIP and architecture verification.
- AAB ZIP and architecture verification.
- Complete native-library ABI coverage.
- ELF machine identity validation.
- Legacy x86 absence.
- Complete delivery packaging.
- SHA256 verification.
- Diagnostic upload.

Produced SHA256 values:

- Universal APK: `fdd840bd5c217daca9262743fea327cd71b158de4e9c4f7372da727b725ddb04`
- Android App Bundle: `e28d1a335055046f6d441786fdf506c6b0cf5af53696e1dc58aeece4946b31ab`
- Prepared source ZIP: `f4a8fe1444fd0c7502ee707d3edc42d8d66ff1d9708c19d521a70b43ad95db7f`

Installation and release-signing compatibility remain mandatory gates before v1.0, but no APK or signing binary work is allowed to replace source development.

## Next engineering stage

Begin the Adaptive UI foundation for:

- Mobile.
- Tablet.
- Android TV.
- Google TV.
- TV Box.
- Touch, remote, and keyboard input without conflict.
- Multiple screen sizes and densities.

The data and domain layers remain shared:

- Same ViewModel.
- Same Repository.
- Same API.
- Adaptive presentation according to device class and available window size.

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
