# Quality Lab handoff

## Source

- Product base: `phase-3-v0.9.3.0-adaptive-foundation`
- Base SHA: `55ee9a136d3557a97daa9b9c2a4821de75108652`
- Lab branch: `quality/hulk-sa-quality-engineering-lab`
- PR #57 was inspected but not merged, closed, or retargeted.
- Only the newer Compatibility Lab engine and release verifier were extracted; its unapproved
  product fixes were not imported into the lab branch.

## Baseline

Run `30383727238` at `ca4c86b561d7bd3f99de0ff6f11b322231c7a01b` is the pre-upgrade
baseline. It is `BLOCKED`: one TV device had no valid runtime evidence, all 126 executed captures
were WARN, 24 reported overlaps were ancestor/child false positives, and visible defects were
missed. It is not an approved visual baseline.

## First reviewer actions

1. Inspect the draft PR commits by layer.
2. Confirm all GitHub Actions files parse.
3. Run Quality PR Intelligence and Quality PR.
4. Run Quality UI and download all device/aggregate artifacts.
5. Inspect screenshots/XML/traces, especially Xiaomi-density TV and downloads.
6. Keep any proven product fixes in separate commits or a separate PR.
7. Do not merge until physical evidence closes the release requirements.

## External blockers

Physical devices, protected production authentication, real playback/download, ARM runtime, and
physical Macrobenchmark are not available to public PR jobs. Use the runbook and protected
workflows; otherwise preserve `NOT EXECUTED`/`BLOCKED`.

