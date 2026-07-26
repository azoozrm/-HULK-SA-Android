# HULK SA Android — Phase 2 Architecture Qualification

## Purpose

Qualify the existing official Android source for broad device support before adaptive UI work or major features.

## Source governance

- All engineering changes are applied to the official source stored in GitHub.
- APK files are outputs and test references only.
- Do not patch DEX, edit a compiled APK, repackage an APK, or use an APK as the development base.
- Every produced APK and AAB must be reproducible from the exact source commit and GitHub Actions workflow.
- Completed work must remain intact: HomeContentSnapshot, Smart Home optimization, recommendations, downloads redesign, focus/navigation improvements, and performance improvements.

## Working branch and version

- Branch: `phase-2-v0.9.2.0-architecture`
- Source base: official `v0.9.1.20`
- Engineering version: `v0.9.2.0`
- versionCode: `43`
- minSdk: `23`
- targetSdk: `36`
- compileSdk: `36`

## Qualified ABI policy

The universal APK and Android App Bundle must contain exactly:

- `arm64-v8a`
- `armeabi-v7a`
- `x86_64`

`x86` is intentionally excluded because it is a legacy architecture and is not required for the supported customer device matrix. `x86_64` remains available for modern emulators and compatible test environments.

## Native-library acceptance checks

The workflow must fail when any of the following occurs:

- A qualified ABI is missing.
- An unapproved ABI is present.
- One native library is present for only part of the qualified ABI set.
- An ELF file is stored under the wrong ABI directory.
- The archive is corrupt.
- The legacy `x86` ABI is present.

The verifier reads the ELF header of every `.so` and validates the machine identity:

- `arm64-v8a` → `EM_AARCH64`
- `armeabi-v7a` → `EM_ARM`
- `x86_64` → `EM_X86_64`

## Outputs

GitHub Actions must produce from the same prepared source tree:

- Universal debug APK.
- Debug Android App Bundle.
- Full prepared source ZIP.
- APK architecture report.
- AAB architecture report.
- Build report.
- Changelog.
- SHA256 checksums.
- Complete diagnostic logs.

Release signing is a separate release gate. Phase 2 architecture qualification must not introduce private signing material into GitHub.

## Exit criteria

Phase 2 is complete only when:

1. Clean source build passes.
2. Unit tests pass.
3. Universal APK is generated.
4. Android App Bundle is generated.
5. Both archives contain exactly the approved ABI set.
6. All native libraries have complete ABI coverage.
7. ELF machine validation passes.
8. Source, binaries, reports, logs, and checksums are retained in GitHub Actions.
9. No approved feature or behavior is removed.

After these checks pass, the project moves to adaptive mobile, tablet, Android TV, Google TV, and TV Box interface qualification.
