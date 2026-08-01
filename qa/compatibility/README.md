# HULK SA Compatibility Lab

This lab validates the current HULK SA production composables without changing
repository production application logic, UI, branding, resources, or release
behavior. The disposable prepared checkout receives `QaActivity` under
`app/src/debug` plus measurement-only `BuildConfig.DEBUG` semantics in its
temporary copy of `MainShellScreen.kt`; the repository `app/src/main` remains
byte-identical to the official product base.

## Architecture

The lab uses a black-box ADB/UIAutomator driver around a deterministic,
debug-only authenticated shell:

1. The official source is reconstructed with the repository's approved release
   preparation chain.
2. `prepare-harness.py` adds `QaActivity` under `src/debug`, then
   `inject_quality_markers.py` applies 28 strict measurement-only semantics
   replacements to the disposable checkout's `MainShellScreen.kt`. It records
   the original and instrumented SHA-256 values, rejects an unexpected source
   shape, and verifies that no other `src/main` file changed.
3. `QaActivity` renders the real `MainShellScreen` and its real Home, Live,
   Movies, Series, Favorites, Search, Downloads, and Settings content with
   deterministic fixture data. Downloads additionally use a debug-only
   loopback byte-range origin and the production `DownloadRepository`.
4. Each emulator captures a screenshot, UI hierarchy XML, app logcat, crash
   buffer, window state, activity state, gfxinfo, meminfo, and launch timing.
5. The analyzer checks display geometry, blank rendering, out-of-bounds nodes,
   collapsed nodes, actionable overlaps, edge/safe-area text, conservative text
   height, crash/ANR signatures, navigation, D-pad focus movement, and the
   collapsed/expanded TV rail-logo geometry, every TV page's rail/outer gutter,
   the live action-row bottom clearance, two-card download fit, and real
   download byte progress. Jank, launch time, and memory remain emulator
   advisories. Non-HULK launcher/system dialogs are dismissed and classified as
   infrastructure, never as app defects.
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
x86_64 system image and native, resolution-specific `tv_720p`, `tv_1080p`, and
`tv_4k` hardware profiles. Their hardware profile, boot skin, physical display,
logical display, viewport, result directory, and artifact name are one exact
matrix contract. The native runner verifies physical `wm size` and `wm density`
before handing control to the lab, and the requested geometry is verified again
from the PNG header before every capture. API 34 Android TV currently has x86 and
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
- `FAIL`: a Product failure proven after target focus, key delivery, and marker
  preconditions are all established.
- `BLOCKED`: infrastructure, missing mandatory evidence, stale page evidence,
  or a fixture/start-state precondition prevented a valid Product assertion.

Infrastructure errors and deterministic critical application findings fail
push and pull-request runs. Manual runs enforce them by default and expose
`enforce_findings` only for an intentional report-only diagnostic run. Warnings
remain visible advisories and do not fail the workflow. Raw assertions remain
in the evidence even when reconciliation marks them downstream or blocked.

For Android TV, every page audit measures the authenticated content surface
against the navigation rail. The captured `qa-tv-page-content:<page>` and
`qa-tv-rail` bounds fail qualification when any outer gutter exceeds 12dp, so
the former 23dp frame cannot pass merely because every node remains inside the
physical display. Live additionally requires at least 14dp physical clearance
below the action row. The rail logo is qualified at 2.8–3.5% of screen width,
not at a fixed dp size, so receiver density differences cannot silently double
its optical size.

The Downloads scenario is not a static progress mock. It starts three records
through the production repository against a loopback origin that serves bounded
ranges but deliberately stalls an open-ended `bytes=N-` request. Qualification
requires observed byte progress plus two complete, non-overlapping TV cards.

## Artifacts

Each device artifact contains every raw capture and:

- `REPORT.html`
- `REPORT.md`
- `summary.json`
- `junit.xml`
- `run-manifest.json`
- `app/src/debug/quality-marker-injection.json` from the disposable build
- `PROVENANCE.json` binding the result to source head, tested merge commit,
  workflow run, attempt, and lab APK SHA-256

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

## Full TV focus and download-action contract

The TV audit no longer treats two unique focus targets as sufficient. Every page
has a minimum focus-coverage policy, and Downloads additionally runs a
deterministic physical D-pad action audit. After every restart, the audit reads
the actual focused node, plans a route through the current RTL graph, and proves
the target over consecutive UI hierarchy reads before sending CENTER. Wi-Fi
mode, scheduling, concurrency, pause/resume, priority, delete, and movement
across two active rows are retained as mandatory evidence.

A fixture or unknown start state is `BLOCKED`; it is never counted as a Product
callback failure. A callback failure is Product only when target focus, key
press, marker revision before the key, and absence of a different callback
marker are all proven. Wrong callback markers are navigation/focus mismatches,
and dependent findings remain visible as downstream evidence without inflating
the Product-critical count.
