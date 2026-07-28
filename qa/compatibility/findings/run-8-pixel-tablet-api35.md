# Compatibility Lab Run 8 — Pixel Tablet API 35

Source workflow run: `30329283830`
Artifact: `compatibility-pixel-tablet-api35`

## Status

- Overall: `WARN`
- Cases: 28 / 28 completed
- Critical findings: 0
- Warning findings: 39
- Infrastructure errors: 0

## Verified working

- Installation and navigation completed across all seven pages.
- Portrait and landscape completed at fontScale 1.0 and 1.30.
- No crash, ANR, blank page, out-of-bounds node, zero-sized node or infrastructure failure was reported.

## Findings

- Repeated portrait interactive-overlap warnings, especially in content-heavy pages.
- Large-font runs remained operational but produced spacing/layout warnings.
- Performance and jank warnings are emulator advisories unless reproduced on physical hardware.

## Interpretation

Tablet runtime compatibility is confirmed. The remaining work is layout hardening for portrait and large-font configurations, not runtime stabilization.