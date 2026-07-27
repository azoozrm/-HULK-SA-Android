# HULK SA Android — Canonical Source Parity

## Scope

This change materializes the exact v0.9.3.17 production project reconstructed from audited head `8db147faea8fae0290bf75d53b4194de2035880f` and audit delivery head `0bc9a31a8538a8fadc1f0223d8c69d35ba17bd0a`.

It does not redesign the UI, change business logic, add a feature, implement signing, or remove the historical ZIP/transformation chain.

## Parity method

1. Run the audited 24-step reconstruction once without the debug Compatibility Lab injection.
2. Run the normal Compatibility Lab reconstruction separately.
3. Compare every file outside `app/src/debug`; all files must match byte-for-byte.
4. Record `qa/canonical/v0.9.3.17-baseline.sha256` before canonical-only additions.
5. Materialize the production Gradle project directly in Git.
6. Add Gradle Wrapper 8.13.
7. Apply one intentional source-only difference: a file-scoped AndroidX opt-in for Media3 `UnstableApi` in `PlayerScreen.kt` to clear the documented lint error. This changes no runtime behavior.
8. Record `qa/canonical/canonical-source.sha256` for the committed source and build files.

## Required acceptance evidence

- Clean checkout builds directly through `./gradlew`; no reconstruction creates `app/src/main`.
- Debug and unsigned Release APK/AAB build successfully, including R8/resource shrinking.
- Existing unit tests pass.
- `lintDebug` has no error.
- ABI verification passes exactly for `arm64-v8a`, `armeabi-v7a`, and `x86_64`, with legacy `x86` absent.
- Compatibility QA remains debug-only and is not copied into production source.
- Historical ZIP, scripts, workflows, and audit evidence remain available until this parity PR is accepted.

## Out of scope

Release signing, install/upgrade proof, landscape navigation, TV Search focus, durable downloads, authenticated production E2E, performance qualification, and any feature work remain separate follow-up PRs.
