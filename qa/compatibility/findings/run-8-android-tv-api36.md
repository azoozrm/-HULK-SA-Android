# Compatibility Lab Run 8 — Android TV API 36

Source workflow run: `30329283830`
Artifacts:
- `compatibility-android-tv-720p-api36`
- `compatibility-android-tv-1080p-api36`
- `compatibility-android-tv-4k-api36`

## Results

### Android TV 720p
- Overall: `FAIL`
- Cases: 7 / 7 completed
- Critical findings: 1
- Warning findings: 16
- Infrastructure errors: 0

### Android TV 1080p
- Overall: `FAIL`
- Cases: 7 / 7 completed
- Critical findings: 1
- Warning findings: 16
- Infrastructure errors: 0

### Android TV 4K
- Overall: `FAIL`
- Cases: 7 / 7 completed
- Critical findings: 1
- Warning findings: 13
- Infrastructure errors: 0

## Verified working

- Native Android TV boot and capture completed at all three resolutions.
- All seven pages were captured.
- Focus was present on the launcher/home path.
- No infrastructure failure was reported.

## Repeated release-blocking pattern

The same Home-page defect reproduced at 720p, 1080p and 4K:

- Continue-watching metadata is clipped against the bottom screen edge.
- The same labels are reported as unsafe TV text and undersized text.
- The defect scales with resolution, so it is a layout/safe-area issue rather than a single emulator-resolution anomaly.

Affected examples:
- `استكمال المشاهدة  •  33:20`
- `استكمال المشاهدة  •  16:40`

## Interpretation

Android TV runtime compatibility is confirmed, but release qualification is blocked by a deterministic Home-page bottom-edge clipping defect reproduced at all supported TV resolutions. This should be fixed in one responsive TV layout patch and revalidated across 720p, 1080p and 4K.