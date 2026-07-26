# HULK SA Android — Phase 3 Adaptive UI Foundation

## Status

Implementation and validation branch:

- `phase-3-v0.9.3.0-adaptive-foundation`

This phase continues directly from the qualified v0.9.2.0 architecture source. It does not merge or modify `main`.

## Source governance

- All engineering changes are source-level and stored in GitHub.
- The official v0.9.1.20 source archive remains the recovery base.
- Phase 2 source preparation is applied first, then the reviewed Phase 3 source patch.
- APK and AAB files are outputs only.
- No APK, DEX, binary patching, repackaging, or APK-derived development is used.
- Existing ViewModel, Repository, API, HomeContentSnapshot, downloads, recommendations, focus navigation, and performance work must remain intact.

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

### Navigation adaptation

- Television devices use the cinematic navigation rail.
- Expanded tablet and large-window layouts use the navigation rail.
- Compact and medium mobile/tablet layouts use the existing top navigation.
- Tablet layouts do not inherit television typography or television-only immersive behavior.

### Television detection

Television classification now checks:

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

The workflow must pass:

1. Official source extraction.
2. Recovered-source compiler-artifact repair.
3. v0.9.2.0 architecture source preparation.
4. Reviewed Phase 3 patch application.
5. Adaptive source policy verification.
6. Clean Gradle build.
7. Universal debug APK build.
8. Debug Android App Bundle build.
9. Unit tests, including adaptive classifier tests.
10. APK and AAB ABI / ELF verification.
11. APK badging verification for version 0.9.3.0-beta / versionCode 44.
12. Complete source and binary delivery packaging.
13. SHA256 generation and verification.

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

This branch and its pull request remain unmerged until:

- GitHub Actions passes.
- The produced APK is installed and tested by the project owner.
- Mobile and television smoke tests pass.
- The owner explicitly approves the merge.
