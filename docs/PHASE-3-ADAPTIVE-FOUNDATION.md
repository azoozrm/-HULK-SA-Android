# HULK SA Android — Phase 3 Adaptive UI Foundation

## Status

Implementation and validation branch:

- `phase-3-v0.9.3.0-adaptive-foundation`

Build status: **PASS**.

This phase continues directly from the qualified v0.9.2.0 architecture source. It does not merge or modify `main`.

## Source governance

- All engineering changes are source-level and stored in GitHub.
- The official v0.9.1.20 source archive remains the recovery base.
- Phase 2 source preparation is applied first, then the reviewed Phase 3 source templates and preparation tool.
- APK and AAB files are outputs only.
- No APK, DEX, binary patching, repackaging, or APK-derived development is used.
- Existing ViewModel, Repository, API, HomeContentSnapshot, downloads, recommendations, focus navigation, and performance work remain intact.

## Engineering version

- versionName: `0.9.3.0`
- versionCode: `44`
- minSdk: `23`
- targetSdk: `36`
- compileSdk: `36`

## Adaptive foundation implemented

### Device classes

- Mobile.
- Tablet.
- Television, including Android TV, Google TV, and TV boxes reporting television or Leanback capabilities.

### Window width classes

- Compact: below 600 dp.
- Medium: 600–839 dp.
- Expanded: 840 dp and above.

### Input modes

- Touch.
- Remote / D-pad / gamepad.
- Keyboard.

The latest active input mode controls shared focus chrome. Touch input suppresses TV-style focus scaling and borders on shared buttons, poster cards, history cards, channel items, and adaptive navigation. Remote and keyboard input restore focus feedback. Television devices always retain focus feedback.

Input-source bitmasks are compared against complete Android source constants so keyboard events are not incorrectly classified as D-pad events.

### Navigation adaptation

- Television devices use the cinematic navigation rail.
- Expanded tablet and large-window layouts use the navigation rail.
- Compact and medium mobile/tablet layouts use the existing top navigation.
- Tablet layouts do not inherit television typography or television-only immersive behavior.

### Television detection

Television classification checks:

- `UI_MODE_TYPE_TELEVISION`.
- `FEATURE_LEANBACK`.
- `FEATURE_TELEVISION`.

Only actual television devices are forced to landscape and immersive system-bar behavior.

## Preserved architecture

The following remain shared and unchanged:

- HulkViewModel.
- HulkRepository.
- Xtream/API layer.
- Models and persistence.
- Download repository and download behavior.
- Player request flow.

The phase adds an adaptive presentation environment around the existing application.

## Automated validation

Successful workflow run: `30186296812`.

The workflow passed:

1. Official source extraction.
2. Recovered-source compiler-artifact repair.
3. v0.9.2.0 architecture source preparation.
4. Reviewed Phase 3 source-template preparation.
5. Preparation idempotency check.
6. Adaptive source policy verification.
7. Clean Gradle build and Kotlin compilation.
8. Unit tests, including adaptive classifier and input-source tests.
9. Universal debug APK build.
10. Debug Android App Bundle build.
11. APK and AAB ABI / ELF verification.
12. APK badging verification for version 0.9.3.0-beta / versionCode 44.
13. Complete source and binary delivery packaging.
14. SHA256 generation and verification.

Complete artifact:

- ID: `8627152631`
- Name: `HULK-SA-v0.9.3.0-ADAPTIVE-FOUNDATION`

Diagnostics artifact:

- ID: `8627152793`

## Device acceptance matrix

### Mobile

- Compact phone portrait.
- Phone landscape.
- Touch navigation without persistent TV focus chrome.
- Keyboard or remote input restores focus feedback.

### Tablet

- Medium portrait layout uses top navigation.
- Expanded landscape / large window uses navigation rail.
- Tablet remains touch-friendly and does not use television sizing.

### Television / TV box

- Landscape and immersive mode retained.
- Rail navigation retained.
- D-pad focus feedback retained.
- Leanback and television-feature devices are recognized even when UI mode reporting is incomplete.

## Merge policy

This branch and pull request `#20` remain unmerged until:

- The produced APK is installed and tested by the project owner.
- Mobile and television smoke tests pass.
- The owner explicitly approves the merge.
