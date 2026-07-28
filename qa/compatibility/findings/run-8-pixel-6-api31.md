# Compatibility Lab Run 8 — Pixel 6 API 31

Source workflow run: `30329283830`
Artifact: `compatibility-pixel-6-api31`

## Status

- Overall: `FAIL`
- Cases: 14 / 14 completed
- Critical findings: 2
- Warning findings: 28
- Infrastructure errors: 0

## Verified working

- Installation completed.
- All seven pages opened in portrait and landscape.
- Navigation completed across Home, Live, Movies, Series, Search, Downloads and Settings.
- No infrastructure setup failure was reported.

## Repeated findings

1. Interactive overlap warnings appeared in portrait Home and Live, and in landscape Live and Series.
2. Text-at-display-edge warnings appeared across most portrait pages and landscape Settings.
3. Two cases exceeded roughly five seconds startup time on the software-rendered emulator.
4. Emulator jank warnings were widespread and remain advisory unless reproduced on physical hardware.

## Release interpretation

Unlike Nexus 9 and Pixel 4a, the lab classified this device as `FAIL` with two critical findings. The summary confirms the failure count but does not expose a failed-case status directly, so the exact critical rule must be traced in the generated report and raw case evidence before modifying production code. This is now a release-blocking investigation item, not a cosmetic warning.