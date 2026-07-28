# Compatibility Lab Run 8 — Nexus 9 API 28

Source workflow run: `30329283830`
Artifact: `compatibility-nexus-9-api28`

## Status

- Overall: `WARN`
- Cases: 14 / 14 completed
- Critical findings: 0
- Warning findings: 24
- Infrastructure errors: 0
- JUnit failures: 0

## Verified working

- Installation completed.
- All seven pages opened in portrait and landscape.
- Navigation succeeded for Home, Live, Movies, Series, Search, Downloads and Settings.
- No crash, ANR, blank page, out-of-bounds node, zero-sized node or infrastructure failure was reported.

## Actionable findings

1. Portrait interactive overlap warnings:
   - Home
   - Live
   - Movies
   - Series
2. Portrait Live reported two undersized text bounds (`قت` and `▶`).
3. Portrait page start warnings ranged from about 6.0 to 7.6 seconds on the software-rendered emulator.
4. All cases reported emulator jank warnings. The lab itself classifies these values as advisory rather than deterministic release gates.

## Interpretation

This device is compatible at the runtime and navigation level, but the portrait overlap and undersized-text findings require review against the screenshots and UI hierarchy before release qualification. Performance warnings must be treated as emulator advisories unless reproduced on physical hardware.
