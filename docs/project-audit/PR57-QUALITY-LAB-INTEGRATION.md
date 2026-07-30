# PR #57 product-only integration over Quality Lab

## Integration source

- Quality Lab base: `d26b4f36841e6a418796bb86829a94d62a162bdd` (merged PR #59)
- Scope-aware Quality Lab base update: `c3ab24a830750384d651e19f042c3022e89d9c7d` (merged PR #60)
- Archived PR #57 head: `93b42c0e043d0fb43792f03db9a97903898fbca2`
- Archive branch: `archive/pr57-before-quality-lab-integration`

## Preserved Quality Lab policy

The merged PR #59 Quality Engineering Lab is authoritative. No legacy Compatibility Lab workflow, analyzer, fixture, marker, matrix, retry policy, baseline, or quality report file from the archived PR #57 head was imported.

Quality-only semantics that existed in the archived production `MainShellScreen.kt` were removed. The approved debug-only injection layer remains the sole owner of `qa-tv-*` semantics.

## Imported product scope

Only the following product/release files were imported from the archived PR #57 head:

- `app/build.gradle.kts`
- `app/src/main/java/sa/hulksa/player/data/DownloadRepository.kt`
- `app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt`
- `app/src/test/java/sa/hulksa/player/data/DownloadTransportPolicyTest.kt`
- `app/src/test/java/sa/hulksa/player/ui/screens/TvLayoutPolicyTest.kt`
- `qa/canonical/canonical-source.sha256`

## Physical download-focus regression and repair

Physical Xiaomi evidence on signed `0.9.3.20 (64)` proved that active download cards rendered their metadata while the pause/resume, priority and cancel controls were clipped and unreachable. D-pad navigation stayed in the top settings row and did not enter multiple active download cards.

The clean repair adds:

- a deterministic RTL-aware D-pad graph from Wi-Fi, schedule and concurrency controls into the matching action column of every download row;
- explicit vertical movement through all active download rows and explicit horizontal movement through primary, priority and cancel actions;
- a card layout that reserves a fixed visible action row instead of allowing metadata/progress content to consume it;
- visible outlined secondary actions and focus callbacks on every action;
- unit graph coverage plus a Compose test with two active downloads that verifies visibility, focus order and callback execution.

The regression test file is:

- `app/src/androidTest/java/sa/hulksa/player/ui/DownloadsFocusNavigationTest.kt`

Clean product-repair head before the ordinary retrigger commit: `8862bba41e710e86f68feafa4902f58a8ecd4ef2`.

## Atomic cleanup evidence

- Production source contains no `qa-tv-*`, `qaTvPageContent`, or `qaMarker` semantics.
- Canonical hashes were rebuilt from the actual repository bytes, not copied manually.
- `ArtworkUrlTest.kt` and `TvLayoutPolicyTest.kt` hashes match their current files.
- Temporary repair and sanitizer workflows are absent from the final PR diff.
- A broad staging mistake was removed by rebuilding from clean head `c291fa7df2f3ee3a04d020cd831f02073a44d514` and preserving exactly four intended repair files.
- The ordinary documentation commit retriggers the complete PR workflow suite because GitHub does not recursively trigger workflows from a `GITHUB_TOKEN` bot push.

## Integrity conditions

- Approved logo assets and colors are unchanged.
- The approved logo SHA-256 remains `2704350ef016a65733ed8eb89cd2d006a8d001c7139a0a535526a780d9691b9e`.
- Production package remains `sa.hulksa.player`.
- Candidate version is `0.9.3.20` / `versionCode 64`.
- Production endpoint remains unchanged and `CONFIG_URL` remains empty.
- Product findings are enforced fail-closed by the merged Quality Lab.

## Acceptance path

1. Run PR static, canonical, generated-source, intelligence, instrumentation and full Compatibility Lab gates on the exact integrated head.
2. Inspect every expected artifact and separate product findings from infrastructure failures.
3. Extend the independent Quality Lab contract so active multi-download actions and full-page focus reachability cannot pass on no-op fixtures.
4. Do not merge PR #57 unless all deterministic product gates pass.
5. After CI passes, require physical Xiaomi, TCL and Galaxy evidence before signed release qualification and final merge.
