# Compatibility Lab Run 8 — Pixel 4a API 29

Source workflow run: `30329283830`
Artifact: `compatibility-pixel-4a-api29`

## Status

- Overall: `WARN`
- Cases: 28 / 28 completed
- Critical findings: 0
- Warning findings: 61
- Infrastructure errors: 0
- JUnit failures: 0

## Verified working

- Installation completed.
- All seven pages opened in portrait and landscape.
- Both font scales (`1.0` and `1.30`) completed.
- Navigation succeeded for Home, Live, Movies, Series, Search, Downloads and Settings.
- No crash, ANR, blank page, out-of-bounds node, zero-sized node or infrastructure failure was reported.

## Repeated actionable findings

1. Bottom-navigation overlap warnings appeared in portrait, including Home and multiple pages at `fontScale=1.30`.
2. Several labels touched a physical display edge in portrait; the affected labels varied by page.
3. Conservative text-clipping warnings increased at `fontScale=1.30`, especially Search, Downloads and Settings.
4. Landscape Live reported overlapping actionable elements at both font scales.
5. Emulator jank warnings were reported across the matrix and remain advisory according to the lab.

## Interpretation

Runtime and navigation compatibility are intact. The repeated phone findings point to adaptive navigation width/spacing and large-font resilience rather than a device-specific crash. These must be correlated with Pixel 6, Pixel 8 Pro and Galaxy S24 Ultra before changing production UI.