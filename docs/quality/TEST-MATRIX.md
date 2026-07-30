# Quality test matrix

The canonical matrix is `qa/quality/config/matrix.json`; schema validation is mandatory. Pixel
resolution alone is not a Compose-size assertion. Width is derived as:

`widthDp = widthPx × 160 / densityDpi`.

## Automated profile groups

| Family | Required boundaries | Profile status |
|---|---|---|
| Phone | 320, 360, ~393, ~411, 480dp; portrait/landscape | Defined; selected profiles await upgraded CI run |
| Tablet | 599, 600, 839, 840, 1000dp; portrait/landscape | Defined; selected profiles await upgraded CI run |
| TV | 720p, 1080p, 4K | Existing Compatibility Lab runtime |
| TV density | TCL-like, Xiaomi-density, Google TV-like | Defined as simulations; never called physical OEM devices |
| Font scale | 1.0, 1.15, 1.30, 1.50; 2.0 on critical phone flows | Defined; runtime coverage varies by tier |

The pre-upgrade Compatibility Lab executed nine profiles, not the full expanded matrix. Its exact
results are in `BASELINE-BEFORE-UPGRADE.md`. A profile becomes “verified” only after its artifact
contains screenshots, XML, logs, and a valid final summary.

## Input and configuration dimensions

| Dimension | Planned coverage | Evidence rule |
|---|---|---|
| Arabic RTL | All shell journeys | Screenshot + hierarchy |
| English LTR | Nightly | Not verified until artifact exists |
| Mixed Arabic/English/numbers | Catalog fixtures | Runtime content evidence |
| Touch | Phone/tablet | Compose/instrumentation |
| D-pad | TV | focus/navigation trace |
| Keyboard/IME | Search | IME trace and exit path |
| Gamepad | When advertised | Not currently verified |
| Gesture/three-button navigation | Physical or managed device | Not currently verified |
| Cutout/edge-to-edge | Emulator configuration | Not currently verified |
| Split/freeform/fold | Resizable managed device | Not currently verified |
| Overscan/title-safe | TV simulation + physical proof | Emulator is advisory; physical proof required |

## Physical targets

Galaxy ARM64, Xiaomi Android TV/box, TCL Android TV, low-spec ARM Android, and ARM64 tablet remain
`NOT_EXECUTED` until the physical evidence runbook is completed. Emulator labels do not inherit
OEM names except explicit “density simulation”.

