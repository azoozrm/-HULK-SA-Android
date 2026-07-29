# HULK SA Compatibility Lab

This lab validates the current HULK SA production composables without changing
production application logic, UI, branding, resources, or release behavior.
The harness is injected into `app/src/debug` only while GitHub Actions builds the
QA APK.

## Architecture

The lab uses a black-box ADB/UIAutomator driver around a deterministic,
debug-only authenticated shell:

1. The official source is reconstructed with the repository's approved release
   preparation chain.
2. `prepare-harness.py` adds `QaActivity` only under `src/debug` and verifies
   that `src/main` has the same SHA-256 tree digest before and after injection.
3. `QaActivity` renders the real `MainShellScreen` and its real Home, Live,
   Movies, Series, Search, Downloads, and Settings content with deterministic
   fixture data.
4. Each emulator captures a screenshot, UI hierarchy XML, app logcat, crash
   buffer, window state, activity state, gfxinfo, meminfo, and launch timing.
5. The analyzer checks display geometry, blank rendering, out-of-bounds nodes,
   collapsed nodes, actionable overlaps, edge/safe-area text, conservative text
   height, crash/ANR signatures, navigation, D-pad focus movement, and the
   collapsed/expanded TV rail-logo geometry. Jank, launch time, and memory remain
   emulator advisories. Non-HULK launcher/system dialogs are dismissed and
   classified as infrastructure, never as app defects.
6. Reports are emitted as JSON, Markdown, HTML, JUnit XML, and GitHub Actions
   job summaries.

Login is intentionally not a test target. Earlier cloud E2E runs proved that
the production portal rejects GitHub-hosted runner connections even while
Android connectivity checks succeed. The deterministic fixture removes this
external blocker and does not contain or publish account credentials.

## Device matrix

| Device | API | Geometry | Density | Coverage |
|---|---:|---:|---:|---|
| Pixel 4a | 29 | 1080×2340 | 440 | portrait, landscape, 100% and 130% font |
| Pixel 6 | 31 | 1080×2400 | 420 | portrait, landscape |
| Pixel 8 Pro | 35 | 1344×2992 | 480 | portrait, landscape |
| Galaxy S24 Ultra | 35 | 1440×3120 | 560 | portrait, landscape |
| Pixel Tablet | 35 | 1600×2560 | 320 | portrait, landscape, 100% and 130% font |
| Nexus 9 | 28 | 1536×2048 | 320 | portrait, landscape |
| Android TV 720p | 36 | 1280×720 | 213 | landscape, RTL, D-pad focus |
| Android TV 1080p | 36 | 1920×1080 | 320 | landscape, RTL, D-pad focus |
| Android TV 4K | 36 | 3840×2160 | 640 | landscape, RTL, D-pad focus |

Touch devices use a stable x86_64 AVD hardware definition with exact display
size and density overrides. TV devices use the published Android TV API 36
x86_64 system image. The 720p profile downsizes a stable 1080p framebuffer;
1080p uses the native `tv_1080p` hardware profile and 4K uses the native
`tv_4k` profile. Android can safely reduce, but cannot increase, an emulator
framebuffer with `wm size`, so using the native 4K profile is required for a
real 3840×2160 screenshot. The requested geometry is verified from the PNG
header before every capture. API 34 Android TV currently has x86 and
arm64-v8a images, but no x86_64 image, so it cannot run this app's qualified
x86_64 emulator ABI.

The native 4K AVD briefly restarts its ADB transport after Android reports
`sys.boot_completed=1`. `run-native-emulator.sh` therefore requires six
consecutive healthy ADB reads before it unlocks the device and starts the lab.
This prevents the emulator runner's early-unlock race from discarding the 4K
capture while keeping the standard runner for the other eight stable profiles.

## Result policy

- `PASS`: no deterministic finding.
- `WARN`: the lab completed, but evidence needs review.
- `FAIL`: deterministic app crash, ANR, render, bounds, navigation, or focus
  failure.
- `BLOCKED`: the emulator, capture, or report infrastructure did not complete.

Infrastructure errors and deterministic critical application findings fail
push and pull-request runs. Manual runs enforce them by default and expose
`enforce_findings` only for an intentional report-only diagnostic run. Warnings
remain visible advisories and do not fail the workflow.

## Artifacts

Each device artifact contains every raw capture and:

- `REPORT.html`
- `REPORT.md`
- `summary.json`
- `junit.xml`
- `run-manifest.json`

The aggregate artifact contains:

- `COMPATIBILITY-LAB-REPORT.html`
- `COMPATIBILITY-LAB-REPORT.md`
- `COMPATIBILITY-LAB-SUMMARY.json`
- evidence bundles (PNG, XML, and logcat) for every finding

## Local static validation

```bash
python3 qa/compatibility/lab_config.py --validate
python3 -m unittest discover -s qa/compatibility/tests -v
bash -n qa/compatibility/prepare-project.sh
```

The emulator matrix itself is designed for GitHub Actions runners with KVM and
the Android SDK.
