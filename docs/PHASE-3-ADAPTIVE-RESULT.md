# HULK SA Android — Phase 3 Adaptive UI Result

## Result

Status: **PASS — source build and automated validation**.

Owner device smoke testing remains required before merge.

## GitHub validation

- Branch: `phase-3-v0.9.3.0-adaptive-stable`
- Draft PR: `#21`
- Source head commit: `2e6935a476d752c7bac5ca6e3c13e1039a621807`
- Pull-request validation merge commit: `f66324a056c1275fd0e8783d1ad481bb72b49981`
- Workflow run: `30186374255`
- Workflow job: `89751776276`
- Complete artifact ID: `8627175366`
- Complete artifact: `HULK-SA-v0.9.3.0-ADAPTIVE-FOUNDATION`
- Diagnostics artifact ID: `8627175428`

## Passed checks

- Official source extraction.
- Source-only v0.9.1.20 recovery repair.
- Qualified v0.9.2.0 architecture preparation.
- Adaptive source preparation and idempotency.
- Adaptive source-policy verification.
- Kotlin compilation.
- Unit tests, including adaptive classifier tests.
- Universal APK build.
- Android App Bundle build.
- APK and AAB ZIP integrity.
- Exact approved ABI set: `arm64-v8a`, `armeabi-v7a`, `x86_64`.
- Legacy `x86` absence.
- Complete native-library ABI coverage.
- ELF machine verification.
- Complete delivery packaging.
- SHA-256 verification in GitHub Actions and after artifact download.

## Adaptive foundation included

- Mobile, tablet, and television device classes.
- Compact, medium, and expanded width classes.
- Touch, remote, and keyboard input-state foundation.
- Top navigation for compact and medium windows.
- Rail navigation for television and expanded windows.
- Shared focus presentation controlled by adaptive input state.
- TV and TV-box recognition using UI mode, Leanback, and television features.
- Shared ViewModel, Repository, API, downloads, recommendations, HomeContentSnapshot, navigation, and performance logic preserved.

## Produced files and SHA-256

- Universal APK: `94f6e1a784762995de00f81fad45aef4a54b6c64b09bd0653ccefcffbfa7a254`
- Android App Bundle: `020a1c10c0a93a93dff061b99ffc49f7f55347a510d71d0296630c083bc979a7`
- Prepared source ZIP: `8dbfc92c4c2ceb306d8854892382bd78b9af742d887f7c3dff1a5849a6a1ea96`

## Merge gate

Do not merge PR `#21` until the owner confirms installation and smoke tests on available mobile and television devices. This is a debug engineering build, not the signed final v1.0 release.
