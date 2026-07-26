# HULK SA Android — Phase 0 Audit

## Official baseline

- Source target: `v0.9.1.20`
- Stable APK reference: `v0.9.1.19`
- Do not rebuild from scratch.
- Preserve completed Home, Recommendations, Downloads, Focus, and performance work.
- Do not modify `main` directly.

## Mandatory sequence

1. Verify the complete `v0.9.1.20` source.
2. Compare it with the stable `v0.9.1.19` APK.
3. Stabilize Gradle/JDK/AGP/Kotlin build versions.
4. Fix installation and signing compatibility.
5. Validate ABIs and native libraries.
6. Validate TV, tablet, and mobile behavior.
7. Complete tests and release `v1.0`.
8. Start major features only after `v1.0`.

## Initial repository finding

The current `main` branch stores a compact source payload in chunks and reconstructs it during GitHub Actions. Existing history references an older compact payload workflow, so it must not yet be treated as the verified `v0.9.1.20` source.

## Build-minute policy

- No automatic build on push.
- No automatic build on pull request.
- Build only through `workflow_dispatch` after a review batch is complete.
- Use Gradle dependency caching.
- Cancel duplicate builds.
- Keep artifacts for a short retention period.

## Current status

- Public repository confirmed.
- Audit branch created: `phase-0-v0.9.1.20-audit`.
- `main` remains unchanged.
- No GitHub Actions minutes consumed by this audit start.
