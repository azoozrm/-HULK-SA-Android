# HULK SA Android — Official Project Plan and Handoff

This file is the permanent source of truth for every new ChatGPT conversation and every future development session.

## Core rule

- Continue the existing project. Do not restart from scratch.
- Do not redesign or modify completed work unless fixing a verified defect.
- Every build must be produced from source code only.
- Do not rebuild, patch, modify DEX, or repackage an old APK.
- APK v0.9.1.19 is only the stability, installation, signing, and behavior reference.
- Source v0.9.1.20 is the approved development base.
- Do not modify `main` directly. Work in a dedicated branch, validate, then merge only after approval.

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
3. Fix installation, update path, signing, and package compatibility.
4. Qualify supported architectures.
5. Adapt and validate mobile, tablet, and Android TV behavior.
6. Run tests and regression checks.
7. Publish a stable v1.0.
8. Begin major new features only after v1.0.

The first major feature after v1.0 is Multi Profile.

## Current verified source facts

- package/applicationId: `sa.hulksa.player`
- versionName: `0.9.1.20`
- versionCode: `42`
- compileSdk: `36`
- targetSdk: `36`
- minSdk: `23`
- Android Gradle Plugin: `8.13.2`
- Kotlin: `2.2.21`
- Source audit found 23 Kotlin files, 7 XML files, and 41 total files.

## Current GitHub state

Repository: `azoozrm/-HULK-SA-Android`

Current working branch: `phase-0-v0.9.1.20-audit`

Relevant commits:

- Phase 0 audit document: `e92a2eef7f83b0b9672021f82abc98bae67e79fd`
- Phase 0 audit workflow: `e1193140dc61fe9205d8c28b5ca3946eb3811294`
- Audit uploaded source archive workflow update: `cf482665cef2c9123d3bd3ebd47b2c270935ade0`
- Source-only build workflow: `f365b6d5c8a3662426e29d7dd8db68c3e19b9cd8`
- Full Gradle log capture and monitored build workflow: `c2fb77f1aa42084008a37d362b031804a56604d2`
- First Compose stability-field repair: `8db2c097d98a1903c793d78ff308f57a28dbc8f6`
- Final Compose/JvmField build repair and successful source build: `fbac81787054f07dd4e996e2e9be55954bfb601a`

Workflows:

- `.github/workflows/phase0-audit.yml`
- `.github/workflows/build-v09120.yml`

Open validation PR:

- PR `#18` — `Phase 0 — build and repair HULK SA v0.9.1.20`

## Current build status

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
- This APK is a debug-signed engineering build and is not yet the approved stable release.

## Current engineering stage

Stages 1 and 2 of the mandatory engineering sequence are complete:

- Complete v0.9.1.20 source recovered and audited.
- Source build stabilized and verified.

The active stage is now stage 3:

- Compare package name, version code, manifest identity, signing certificate, supported update path, and installation behavior against the stable v0.9.1.19 reference APK.
- Determine whether v0.9.1.20 can update v0.9.1.19 in place without uninstalling and without losing application data.
- Do not copy, extract, reuse, or derive a private signing key from the reference APK.
- If the original signing key is unavailable, document the signing incompatibility and establish an approved source-based release-signing strategy before v1.0.
- After installation/signing compatibility is resolved or formally documented, continue to architecture qualification.

## Release and delivery rule for every approved version

Every approved version must be stored in GitHub and delivered as a complete versioned package containing:

- Full Android source ZIP.
- APK built from that exact source commit.
- Release APK when signing is available and approved.
- Build report.
- Changelog.
- SHA256 checksums.
- Git commit and version tag.

A version is not approved merely because an APK was produced. It must have no known blocking defect and must pass the required build, test, installation, signing, and regression checks for its stage.

## Instruction for a new conversation

Read this file first, inspect the latest commits, workflows, runs, jobs, and logs in the repository, then continue from the current GitHub state. Do not ask the user to re-explain the project and do not restart from scratch.
