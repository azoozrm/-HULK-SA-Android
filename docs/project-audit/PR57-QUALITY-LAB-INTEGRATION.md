# PR #57 product-only integration over Quality Lab

## Integration source

- Quality Lab base: `d26b4f36841e6a418796bb86829a94d62a162bdd` (merged PR #59)
- Archived PR #57 head: `93b42c0e043d0fb43792f03db9a97903898fbca2`
- Archive branch: `archive/pr57-before-quality-lab-integration`

## Preserved Quality Lab policy

The merged PR #59 Quality Engineering Lab is authoritative. No legacy Compatibility Lab workflow, analyzer, fixture, marker, matrix, retry policy, baseline, or quality report file from the archived PR #57 head was imported.

## Imported product scope

Only the following product/release files were imported from the archived PR #57 head:

- `app/build.gradle.kts`
- `app/src/main/java/sa/hulksa/player/data/DownloadRepository.kt`
- `app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt`
- `app/src/test/java/sa/hulksa/player/data/DownloadTransportPolicyTest.kt`
- `app/src/test/java/sa/hulksa/player/ui/screens/TvLayoutPolicyTest.kt`
- `qa/canonical/canonical-source.sha256`

## Integrity conditions

- Approved logo assets and colors are unchanged.
- The approved logo SHA-256 remains `2704350ef016a65733ed8eb89cd2d006a8d001c7139a0a535526a780d9691b9e`.
- Production package remains `sa.hulksa.player`.
- Candidate version is `0.9.3.20` / `versionCode 64`.
- Production endpoint remains `http://3162356.xyz:8080` with an empty `CONFIG_URL`.
- Product findings are enforced fail-closed by the merged Quality Lab.

## Acceptance path

1. Run PR static, canonical, generated-source, intelligence, instrumentation and full Compatibility Lab gates on the exact integrated head.
2. Inspect every expected artifact and separate product findings from infrastructure failures.
3. Do not merge PR #57 unless all deterministic product gates pass.
4. After CI passes, require physical Xiaomi, TCL and Galaxy evidence before signed release qualification and final merge.
