# HULK SA Android — Phase 3 Adaptive Foundation Result

Status: **PASS**

## Source

- Branch: `phase-3-v0.9.3.0-adaptive-foundation`
- Validated source head: `f973500ad8b358ccf77d40649fc859c88014a74a`
- Pull-request validation merge commit: `21996c7dbd917a0d70d7cb652a09e4dcab90dc87`
- Draft pull request: `#20`
- Build method: official source archive plus reviewed source-only repair, architecture preparation, adaptive templates, and preparation tooling.
- APK/DEX patching or repackaging: not used.

## GitHub Actions

- Workflow: `Build HULK SA v0.9.3.0 Adaptive Foundation`
- Run: `30186296812`
- Result: success
- Complete artifact ID: `8627152631`
- Complete artifact: `HULK-SA-v0.9.3.0-ADAPTIVE-FOUNDATION`
- Diagnostics artifact ID: `8627152793`

## Passed validation

- Official source extraction.
- Recovered-source repair.
- Qualified Phase 2 architecture preparation.
- Adaptive source preparation.
- Adaptive preparation idempotency.
- Adaptive source policy verification.
- Kotlin compilation.
- Unit tests.
- Mobile/tablet/television classification tests.
- Keyboard/D-pad/gamepad input-source classification tests.
- Universal APK generation.
- Android App Bundle generation.
- APK and AAB ZIP integrity.
- Exact approved ABI set.
- Native library ABI coverage.
- ELF machine identity.
- Legacy x86 absence.
- APK package/version badging.
- Complete delivery packaging.
- SHA256 verification.

## Version and package

- applicationId: `sa.hulksa.player`
- debug applicationId: `sa.hulksa.player.dev`
- versionName: `0.9.3.0-beta`
- versionCode: `44`
- compileSdk: `36`
- targetSdk: `36`
- minSdk: `23`

## Qualified architectures

- `arm64-v8a` — `EM_AARCH64`
- `armeabi-v7a` — `EM_ARM`
- `x86_64` — `EM_X86_64`
- Excluded: `x86`

## Produced SHA256

- Universal APK: `7f4b3d60b608647074a053881fe54e04a0e8318ea977852a17c36e84364c4b45`
- Android App Bundle: `2f332575459de6c2c2bd9c86a1edf48b9689d9c2a0a403591bda965d3be90cd3`
- Prepared source ZIP: `bdc59199d13103be41e07d103612b8186f3e6023281b9ad5db013f3dc35a96aa`
- Complete GitHub artifact ZIP: `68b4652da716182820f01ecca3512e18d35472dfd58f9f82fdfb1bb035ab43ea`

## Manual acceptance required

The pull request remains draft and unmerged. The owner must install the APK and smoke-test:

- Mobile touch behavior.
- Television / TV box D-pad behavior.
- Login, home, playback, details, series, downloads, and back navigation.
- Absence of persistent television focus chrome during touch use.
- Preservation of television focus feedback during remote use.
