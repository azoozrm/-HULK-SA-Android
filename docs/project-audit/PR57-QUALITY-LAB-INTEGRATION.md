# PR #57 qualification scope and Quality Lab integration

## Purpose

PR #57 qualifies the v0.9.3.20 product changes for physical Android TV layout and durable downloads without turning this pull request into the project-wide D-pad navigation phase.

The pull request remains a draft. It must not be merged or promoted to a release solely because static checks are green.

## Product scope

The product changes in scope are limited to:

- `app/build.gradle.kts`
  - preserve `sa.hulksa.player`;
  - preserve `versionName 0.9.3.20` and `versionCode 64`;
  - compile the canonical production endpoint and reject release overrides.
- `app/src/main/java/sa/hulksa/player/data/DownloadRepository.kt`
  - bounded HTTP range requests;
  - resume validation through `Content-Range`;
  - finite stalled-read timeout;
  - persistence, cancellation, partial-file integrity and retry recovery.
- `app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt`
  - TV safe gutters for the affected pages;
  - prevent clipping of the live playback actions;
  - qualify TV rail logo size without changing any logo asset;
  - reserve visible space for download progress and actions;
  - preserve phone and tablet branches.
- Focused unit tests for the transport and layout policies directly introduced by these changes.

## Direct download-card focus regression

The download-card hierarchy changed in this pull request so its action row remains visible. The local focus routing retained here is limited to the controls inside the Downloads page and protects that direct layout regression.

It does not establish or enforce a complete application-wide navigation contract.

## Deferred navigation phase

The following work is explicitly outside PR #57:

- complete D-pad traversal across every page;
- generic minimum target counts for Live, Home, Search, Settings or catalogs;
- project-wide focus restoration and routing redesign;
- acceptance enforcement by the removed `DownloadsFocusNavigationTest.kt` instrumentation file.

Those requirements belong to the official navigation and focus phase. The removed instrumentation file must not remain referenced by canonical manifests, workflows, test plans or harnesses.

## Quality Lab support

Quality Lab changes are permitted only where they support the current product source exactly:

- `qa/compatibility/inject_quality_markers.py` recognizes the exact canonical and PR #57 source anchors required for disposable debug instrumentation.
- `qa/compatibility/tests/test_marker_injection.py` exercises both supported layout fixtures and rejects zero matches, multiple matches, unknown shapes and production sources that already contain QA markers.
- Marker injection modifies only the prepared debug checkout. Production source must contain no `qa-tv-*`, `qaMarker` or `qaTvPageContent` semantics.
- `qa/compatibility/prepare-harness.py` applies one strict debug-only API 28 semantics refresh transform after Nexus 9 artifacts proved the visible Downloads page and positive persisted byte progress while UI Automator retained the previous Search marker.
- `qa/compatibility/tests/test_harness_preparation.py` proves that the refresh transform matches the real fixture exactly once, leaves the checked-in fixture unchanged and fails closed for repeated or unknown shapes.

## Canonical integrity

`qa/canonical/canonical-source.sha256` must satisfy all of these conditions on every qualifying head:

- every listed path exists;
- every listed digest matches the repository bytes;
- deleted tests are absent from the manifest;
- changed canonical product and test files are represented by current hashes;
- approved logo assets retain their approved bytes.

## Required verification

A qualifying head must provide evidence for:

1. canonical SHA-256 and logo integrity;
2. generated-source snapshot integrity;
3. Python syntax and Quality Lab unit tests;
4. Kotlin compilation and focused unit tests;
5. Debug APK build and Android instrumentation compilation/execution;
6. Release/R8 build where signing is not required;
7. Compatibility Lab device reports for phone, tablet and TV profiles;
8. artifact inspection for package, version, endpoint, ABIs and checksums.

Physical Xiaomi, TCL and phone verification remains a separate acceptance requirement. Emulator evidence must not be described as a physical-device pass.

## Immutable product identity

- Package: `sa.hulksa.player`
- Version: `0.9.3.20` (`versionCode 64`)
- Production endpoint: `http://3162356.xyz:8080`
- Approved logo assets and colors: unchanged
- Pull request state: open draft until all required evidence is reviewed
