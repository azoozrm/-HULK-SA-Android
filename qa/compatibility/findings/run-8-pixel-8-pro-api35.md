# Compatibility Lab Run 8 — Pixel 8 Pro API 35

Source workflow run: `30329283830`
Artifact: `compatibility-pixel-8-pro-api35`

## Status

- Overall: `FAIL`
- Cases: 14 / 14 completed
- Critical findings: 1
- Warning findings: 26
- Infrastructure errors: 0

## Verified working

- Installation and capture completed.
- All seven pages opened in portrait and landscape.
- No blank screenshots, out-of-bounds nodes, zero-sized nodes or infrastructure failures were reported.

## Repeated findings

1. Portrait interactive-overlap warnings repeated on Home, Live, Movies and Series.
2. Portrait Live again reported two undersized text bounds (`قت` and `▶`).
3. Start-time and jank warnings remain emulator advisories unless reproduced on physical hardware.

## Release decision

The device is runtime-compatible, but the run contains one critical finding. This remains release-blocking until the exact critical rule is traced from the raw report/JUnit evidence and either fixed in the app or proven to be a lab false positive.
