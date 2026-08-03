#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(".")


def replace_once(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one exact match, found {count}: {old[:180]!r}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


def width_class(value: int) -> str:
    if value < 600:
        return "COMPACT"
    if value < 840:
        return "MEDIUM"
    return "EXPANDED"


def height_class(value: int) -> str:
    if value < 480:
        return "COMPACT"
    if value < 900:
        return "MEDIUM"
    return "EXPANDED"


def enrich(profile: dict[str, object]) -> dict[str, object]:
    width = int(profile["width_px"])
    height = int(profile["height_px"])
    density = int(profile["density_dpi"])
    logical_width = round(width * 160 / density)
    logical_height = round(height * 160 / density)
    family = str(profile["device_family"])
    expected_device = {
        "phone": "MOBILE",
        "tablet": "TABLET",
        "foldable": "TABLET" if min(logical_width, logical_height) >= 600 else "MOBILE",
        "tv": "TELEVISION",
    }[family]
    result = dict(profile)
    result.update(
        {
            "expected_width_dp": logical_width,
            "expected_height_dp": logical_height,
            "expected_device_class": expected_device,
            "expected_width_class": width_class(logical_width),
            "expected_height_class": height_class(logical_height),
            "expected_orientation": "LANDSCAPE" if logical_width >= logical_height else "PORTRAIT",
            "expected_input_mode": "REMOTE" if expected_device == "TELEVISION" else "TOUCH",
            "cutout_mode": result.get("cutout_mode", "none"),
            "navigation_mode": result.get("navigation_mode", "default"),
        },
    )
    return result


matrix_path = ROOT / "quality/compatibility-v2/config/device-matrix.json"
data = json.loads(matrix_path.read_text(encoding="utf-8"))
original_ids = [item["id"] for item in data["profiles"]]
required_original = [
    "phone-small-api29",
    "phone-medium-api35",
    "phone-landscape-font150-api35",
    "tablet-600-portrait-api35",
    "tablet-expanded-landscape-api35",
    "tv-logical-960x540-api36",
    "tv-720p-api36",
    "tv-1080p-api36",
]
if original_ids != required_original:
    raise SystemExit(f"Unexpected existing profile order: {original_ids}")

new_profiles = [
    {
        "id": "phone-320x568-api35",
        "device_family": "phone",
        "api": 35,
        "target": "google_apis",
        "width_px": 720,
        "height_px": 1278,
        "density_dpi": 360,
        "font_scale": 1.0,
        "locale": "ar-SA",
        "orientation": "portrait",
        "inputs": ["touch", "keyboard"],
    },
    {
        "id": "phone-360x800-api35",
        "device_family": "phone",
        "api": 35,
        "target": "google_apis",
        "width_px": 1080,
        "height_px": 2400,
        "density_dpi": 480,
        "font_scale": 1.0,
        "locale": "ar-SA",
        "orientation": "portrait",
        "inputs": ["touch", "keyboard"],
    },
    {
        "id": "phone-portrait-font130-api35",
        "device_family": "phone",
        "api": 35,
        "target": "google_apis",
        "width_px": 1080,
        "height_px": 2400,
        "density_dpi": 480,
        "font_scale": 1.3,
        "locale": "ar-SA",
        "orientation": "portrait",
        "inputs": ["touch", "keyboard"],
    },
    {
        "id": "phone-short-landscape-api35",
        "device_family": "phone",
        "api": 35,
        "target": "google_apis",
        "width_px": 1920,
        "height_px": 864,
        "density_dpi": 480,
        "font_scale": 1.0,
        "locale": "ar-SA",
        "orientation": "landscape",
        "inputs": ["touch", "keyboard"],
    },
    {
        "id": "phone-cutout-gesture-api35",
        "device_family": "phone",
        "api": 35,
        "target": "google_apis",
        "width_px": 1080,
        "height_px": 2400,
        "density_dpi": 480,
        "font_scale": 1.0,
        "locale": "ar-SA",
        "orientation": "portrait",
        "inputs": ["touch", "keyboard"],
        "cutout_mode": "tall",
        "navigation_mode": "gestural",
    },
    {
        "id": "tablet-medium-landscape-api35",
        "device_family": "tablet",
        "api": 35,
        "target": "google_apis",
        "width_px": 1920,
        "height_px": 1200,
        "density_dpi": 320,
        "font_scale": 1.0,
        "locale": "ar-SA",
        "orientation": "landscape",
        "inputs": ["touch", "keyboard"],
    },
    {
        "id": "tablet-resizable-medium-api35",
        "device_family": "tablet",
        "api": 35,
        "target": "google_apis",
        "width_px": 1200,
        "height_px": 1600,
        "density_dpi": 320,
        "font_scale": 1.0,
        "locale": "ar-SA",
        "orientation": "portrait",
        "inputs": ["touch", "keyboard"],
        "window_mode": "resizable",
    },
    {
        "id": "foldable-unfolded-api35",
        "device_family": "foldable",
        "api": 35,
        "target": "google_apis",
        "width_px": 2208,
        "height_px": 1840,
        "density_dpi": 420,
        "font_scale": 1.0,
        "locale": "ar-SA",
        "orientation": "landscape",
        "inputs": ["touch", "keyboard"],
        "window_mode": "variable",
    },
    {
        "id": "tv-4k-api36",
        "device_family": "tv",
        "api": 36,
        "target": "android-tv",
        "width_px": 3840,
        "height_px": 2160,
        "density_dpi": 640,
        "font_scale": 1.0,
        "locale": "ar-SA",
        "orientation": "landscape",
        "inputs": ["remote", "keyboard"],
    },
]

data["profiles"] = [enrich(item) for item in data["profiles"] + new_profiles]
data["schema_version"] = 3
matrix_path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

configure = "quality/compatibility-v2/configure_emulator_profile.sh"
replace_once(
    configure,
    '''out="${8:?evidence path is required}"
mkdir -p "$(dirname "$out")"
''',
    '''out="${8:?evidence path is required}"
cutout_mode="${9:-none}"
navigation_mode="${10:-default}"
expected_device_class="${11:-UNSPECIFIED}"
expected_input_mode="${12:-UNSPECIFIED}"
expected_width_class="${13:-UNSPECIFIED}"
expected_height_class="${14:-UNSPECIFIED}"
expected_orientation="${15:-UNSPECIFIED}"
classification_out="$(dirname "$out")/WINDOW-CLASSIFICATION.txt"
mkdir -p "$(dirname "$out")"
''',
)
replace_once(
    configure,
    '''record "requested_rotation=$rotation"
''',
    '''record "requested_rotation=$rotation"
record "requested_cutout_mode=$cutout_mode"
record "requested_navigation_mode=$navigation_mode"
record "expected_device_class=$expected_device_class"
record "expected_input_mode=$expected_input_mode"
record "expected_width_class=$expected_width_class"
record "expected_height_class=$expected_height_class"
record "expected_orientation=$expected_orientation"
''',
)
replace_once(
    configure,
    '''adb shell settings put system user_rotation "$rotation"
wait_for_services

stage="verify-window-profile"
''',
    '''adb shell settings put system user_rotation "$rotation"

apply_overlay() {
  local package_name="$1"
  local label="$2"
  local list_output enable_output enable_status
  list_output="$(adb shell cmd overlay list --user 0 2>&1 | tr -d '\r' || true)"
  if [[ "$list_output" != *"$package_name"* ]]; then
    record "result=BLOCKED"
    record "failure_reason=$label overlay is unavailable: $package_name"
    record "overlay_list=${list_output//$'\n'/ | }"
    capture_device_state
    exit 3
  fi
  set +e
  enable_output="$(adb shell cmd overlay enable --user 0 "$package_name" 2>&1)"
  enable_status=$?
  set -e
  record "${label}_overlay=$package_name"
  record "${label}_overlay_enable_status=$enable_status"
  record "${label}_overlay_enable_output=${enable_output//$'\n'/ | }"
  if [[ "$enable_status" -ne 0 ]]; then
    record "result=BLOCKED"
    record "failure_reason=unable to enable $label overlay"
    capture_device_state
    exit 3
  fi
}

if [[ "$cutout_mode" != "none" ]]; then
  stage="apply-cutout-overlay"
  apply_overlay "com.android.internal.display.cutout.emulation.$cutout_mode" "cutout"
fi
if [[ "$navigation_mode" == "gestural" ]]; then
  stage="apply-gesture-navigation-overlay"
  apply_overlay "com.android.internal.systemui.navbar.gestural" "navigation"
elif [[ "$navigation_mode" != "default" ]]; then
  record "result=BLOCKED"
  record "failure_reason=unsupported navigation mode: $navigation_mode"
  capture_device_state
  exit 3
fi
wait_for_services

stage="verify-window-profile"
''',
)
replace_once(
    configure,
    '''if [[ "$actual_rotation" != "$rotation" ]]; then
  record "result=BLOCKED"
  record "failure_reason=emulator rotation was not applied"
  capture_device_state
  exit 3
fi

record "result=PASS"
''',
    '''if [[ "$actual_rotation" != "$rotation" ]]; then
  record "result=BLOCKED"
  record "failure_reason=emulator rotation was not applied"
  capture_device_state
  exit 3
fi

python3 - \
  "$profile" "$width" "$height" "$density" "$font_scale" "$locale" \
  "$expected_device_class" "$expected_input_mode" "$expected_width_class" \
  "$expected_height_class" "$expected_orientation" "$cutout_mode" "$navigation_mode" \
  > "$classification_out" <<'PY'
import sys

(
    profile, width_px, height_px, density_dpi, font_scale, locale,
    expected_device, expected_input, expected_width_class,
    expected_height_class, expected_orientation, cutout_mode, navigation_mode,
) = sys.argv[1:]
width_px = int(width_px)
height_px = int(height_px)
density_dpi = int(density_dpi)
width_dp = round(width_px * 160 / density_dpi)
height_dp = round(height_px * 160 / density_dpi)
width_class = "COMPACT" if width_dp < 600 else "MEDIUM" if width_dp < 840 else "EXPANDED"
height_class = "COMPACT" if height_dp < 480 else "MEDIUM" if height_dp < 900 else "EXPANDED"
orientation = "LANDSCAPE" if width_dp >= height_dp else "PORTRAIT"
checks = {
    "device_class": expected_device,
    "input_mode": expected_input,
    "width_class": expected_width_class,
    "height_class": expected_height_class,
    "orientation": expected_orientation,
}
actual = {
    "width_class": width_class,
    "height_class": height_class,
    "orientation": orientation,
}
failures = []
for key in ("width_class", "height_class", "orientation"):
    if checks[key] != "UNSPECIFIED" and checks[key] != actual[key]:
        failures.append(f"{key}: expected {checks[key]}, actual {actual[key]}")
print(f"profile={profile}")
print(f"requested_physical_size={width_px}x{height_px}")
print(f"effective_physical_size={width_px}x{height_px}")
print(f"effective_density_dpi={density_dpi}")
print(f"effective_logical_width_dp={width_dp}")
print(f"effective_logical_height_dp={height_dp}")
print(f"font_scale={font_scale}")
print(f"locale={locale}")
print(f"window_width_class={width_class}")
print(f"window_height_class={height_class}")
print(f"orientation={orientation}")
print(f"expected_device_class={expected_device}")
print(f"expected_input_mode={expected_input}")
print(f"cutout_mode={cutout_mode}")
print(f"navigation_mode={navigation_mode}")
if failures:
    print("result=FAIL")
    for failure in failures:
        print(f"failure={failure}")
else:
    print("result=PASS")
PY
if ! grep -q '^result=PASS$' "$classification_out"; then
  record "result=FAIL"
  record "failure_reason=logical window classification mismatch"
  cat "$classification_out" >> "$out"
  exit 1
fi

record "result=PASS"
''',
)

runtime = "quality/compatibility-v2/run_runtime_profile.sh"
replace_once(
    runtime,
    '''out="${9:-${EVIDENCE_ROOT:-build/compatibility-v2/runtime/$profile}}"
package="sa.hulksa.player.dev"
''',
    '''out="${9:-${EVIDENCE_ROOT:-build/compatibility-v2/runtime/$profile}}"
cutout_mode="${10:-none}"
navigation_mode="${11:-default}"
expected_device_class="${12:-UNSPECIFIED}"
expected_input_mode="${13:-UNSPECIFIED}"
expected_width_class="${14:-UNSPECIFIED}"
expected_height_class="${15:-UNSPECIFIED}"
expected_orientation="${16:-UNSPECIFIED}"
package="sa.hulksa.player.dev"
''',
)
replace_once(
    runtime,
    '''  "$out/PROFILE-CONFIG.txt"
''',
    '''  "$out/PROFILE-CONFIG.txt" \
  "$cutout_mode" "$navigation_mode" "$expected_device_class" "$expected_input_mode" \
  "$expected_width_class" "$expected_height_class" "$expected_orientation"
''',
)

collector = "quality/compatibility-v2/collect_runtime_evidence.sh"
replace_once(
    collector,
    '''  DEVICE-PROFILE.txt \
  WINDOW-METRICS.txt \
''',
    '''  DEVICE-PROFILE.txt \
  WINDOW-CLASSIFICATION.txt \
  WINDOW-METRICS.txt \
''',
)

spec_path = ROOT / "quality/compatibility-v2/config/evidence-spec.json"
spec = json.loads(spec_path.read_text(encoding="utf-8"))
runtime_scope = spec["scopes"]["runtime"]
if "WINDOW-CLASSIFICATION.txt" not in runtime_scope:
    runtime_scope.insert(runtime_scope.index("WINDOW-METRICS.txt"), "WINDOW-CLASSIFICATION.txt")
spec["schema_version"] = 3
spec_path.write_text(json.dumps(spec, indent=2) + "\n", encoding="utf-8")

gate = "quality/compatibility-v2/evidence_gate.py"
replace_once(
    gate,
    '''        check_text_contains(
            evidence_root / "APPLICATION-LOCALE.txt",
            ["result=PASS", "locale_verified=true", "requested_locale="],
            "runtime-effective-locale",
        ),
''',
    '''        check_text_contains(
            evidence_root / "APPLICATION-LOCALE.txt",
            ["result=PASS", "locale_verified=true", "requested_locale="],
            "runtime-effective-locale",
        ),
        check_text_contains(
            evidence_root / "WINDOW-CLASSIFICATION.txt",
            [
                "result=PASS",
                "requested_physical_size=",
                "effective_physical_size=",
                "effective_density_dpi=",
                "effective_logical_width_dp=",
                "effective_logical_height_dp=",
                "window_width_class=",
                "window_height_class=",
                "orientation=",
                "expected_device_class=",
                "expected_input_mode=",
            ],
            "runtime-window-classification",
        ),
''',
)

full = ROOT / ".github/workflows/compatibility-v2-full.yml"
full_text = full.read_text(encoding="utf-8")
full_text = full_text.replace(
    '''  validate-and-build:
    runs-on: ubuntu-latest
''',
    '''  validate-and-build:
    runs-on: ubuntu-latest
    outputs:
      runtime_matrix: ${{ steps.runtime-matrix.outputs.runtime_matrix }}
''',
    1,
)
checkout_marker = '''      - uses: actions/checkout@v7
      - uses: actions/setup-java@v5
'''
if full_text.count(checkout_marker) < 1:
    raise SystemExit("Full workflow checkout marker not found")
full_text = full_text.replace(
    checkout_marker,
    '''      - uses: actions/checkout@v7
      - name: Resolve runtime matrix from the canonical JSON
        id: runtime-matrix
        run: |
          python3 - <<'PY'
          import json
          import os
          from pathlib import Path
          profiles = json.loads(Path('quality/compatibility-v2/config/device-matrix.json').read_text(encoding='utf-8'))['profiles']
          include = []
          for profile in profiles:
              include.append({
                  'id': profile['id'],
                  'api': profile['api'],
                  'target': profile['target'],
                  'profile': 'tv_1080p' if profile['target'] == 'android-tv' else 'pixel_2',
                  'width': profile['width_px'],
                  'height': profile['height_px'],
                  'density': profile['density_dpi'],
                  'font_scale': profile['font_scale'],
                  'rotation': 0,
                  'locale': profile['locale'],
                  'cutout_mode': profile.get('cutout_mode', 'none'),
                  'navigation_mode': profile.get('navigation_mode', 'default'),
                  'expected_device_class': profile['expected_device_class'],
                  'expected_input_mode': profile['expected_input_mode'],
                  'expected_width_class': profile['expected_width_class'],
                  'expected_height_class': profile['expected_height_class'],
                  'expected_orientation': profile['expected_orientation'],
              })
          with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as out:
              out.write('runtime_matrix=' + json.dumps({'include': include}, separators=(',', ':')) + '\n')
          PY
      - uses: actions/setup-java@v5
''',
    1,
)
start_marker = '''    strategy:
      fail-fast: false
      matrix:
        include:
'''
start = full_text.find(start_marker)
if start < 0:
    raise SystemExit("Full workflow static matrix start not found")
end = full_text.find("    env:\n", start)
if end < 0:
    raise SystemExit("Full workflow matrix end not found")
full_text = full_text[:start] + '''    strategy:
      fail-fast: false
      matrix: ${{ fromJSON(needs.validate-and-build.outputs.runtime_matrix) }}
''' + full_text[end:]
old_script = '''          script: bash quality/compatibility-v2/run_runtime_profile.sh '${{ matrix.id }}' '${{ matrix.width }}' '${{ matrix.height }}' '${{ matrix.density }}' '${{ matrix.font_scale }}' '${{ matrix.rotation }}' '${{ matrix.locale }}' 'sa.hulksa.player.compatibilityv2.CompatibilityV2InstrumentationTest' 'build/compatibility-v2/full/${{ matrix.id }}'
'''
new_script = '''          script: bash quality/compatibility-v2/run_runtime_profile.sh '${{ matrix.id }}' '${{ matrix.width }}' '${{ matrix.height }}' '${{ matrix.density }}' '${{ matrix.font_scale }}' '${{ matrix.rotation }}' '${{ matrix.locale }}' 'sa.hulksa.player.compatibilityv2.CompatibilityV2InstrumentationTest' 'build/compatibility-v2/full/${{ matrix.id }}' '${{ matrix.cutout_mode }}' '${{ matrix.navigation_mode }}' '${{ matrix.expected_device_class }}' '${{ matrix.expected_input_mode }}' '${{ matrix.expected_width_class }}' '${{ matrix.expected_height_class }}' '${{ matrix.expected_orientation }}'
'''
if full_text.count(old_script) != 1:
    raise SystemExit(f"Full workflow runtime invocation count={full_text.count(old_script)}")
full_text = full_text.replace(old_script, new_script, 1)
full.write_text(full_text, encoding="utf-8")

targeted = ".github/workflows/compatibility-v2-targeted.yml"
replace_once(
    targeted,
    '''      locale: ${{ steps.profile.outputs.locale }}
''',
    '''      locale: ${{ steps.profile.outputs.locale }}
      cutout_mode: ${{ steps.profile.outputs.cutout_mode }}
      navigation_mode: ${{ steps.profile.outputs.navigation_mode }}
      expected_device_class: ${{ steps.profile.outputs.expected_device_class }}
      expected_input_mode: ${{ steps.profile.outputs.expected_input_mode }}
      expected_width_class: ${{ steps.profile.outputs.expected_width_class }}
      expected_height_class: ${{ steps.profile.outputs.expected_height_class }}
      expected_orientation: ${{ steps.profile.outputs.expected_orientation }}
''',
)
replace_once(
    targeted,
    '''              'locale': profile['locale'],
''',
    '''              'locale': profile['locale'],
              'cutout_mode': profile.get('cutout_mode', 'none'),
              'navigation_mode': profile.get('navigation_mode', 'default'),
              'expected_device_class': profile['expected_device_class'],
              'expected_input_mode': profile['expected_input_mode'],
              'expected_width_class': profile['expected_width_class'],
              'expected_height_class': profile['expected_height_class'],
              'expected_orientation': profile['expected_orientation'],
''',
)
old_targeted_script = '''          script: bash quality/compatibility-v2/run_runtime_profile.sh '${{ needs.resolve-profile.outputs.profile_id }}' '${{ needs.resolve-profile.outputs.width }}' '${{ needs.resolve-profile.outputs.height }}' '${{ needs.resolve-profile.outputs.density }}' '${{ needs.resolve-profile.outputs.font_scale }}' '${{ needs.resolve-profile.outputs.rotation }}' '${{ needs.resolve-profile.outputs.locale }}' '${{ needs.resolve-profile.outputs.test_class }}' 'build/compatibility-v2/targeted/${{ needs.resolve-profile.outputs.profile_id }}'
'''
new_targeted_script = '''          script: bash quality/compatibility-v2/run_runtime_profile.sh '${{ needs.resolve-profile.outputs.profile_id }}' '${{ needs.resolve-profile.outputs.width }}' '${{ needs.resolve-profile.outputs.height }}' '${{ needs.resolve-profile.outputs.density }}' '${{ needs.resolve-profile.outputs.font_scale }}' '${{ needs.resolve-profile.outputs.rotation }}' '${{ needs.resolve-profile.outputs.locale }}' '${{ needs.resolve-profile.outputs.test_class }}' 'build/compatibility-v2/targeted/${{ needs.resolve-profile.outputs.profile_id }}' '${{ needs.resolve-profile.outputs.cutout_mode }}' '${{ needs.resolve-profile.outputs.navigation_mode }}' '${{ needs.resolve-profile.outputs.expected_device_class }}' '${{ needs.resolve-profile.outputs.expected_input_mode }}' '${{ needs.resolve-profile.outputs.expected_width_class }}' '${{ needs.resolve-profile.outputs.expected_height_class }}' '${{ needs.resolve-profile.outputs.expected_orientation }}'
'''
replace_once(targeted, old_targeted_script, new_targeted_script)

test_path = ROOT / "quality/compatibility-v2/tests/test_device_matrix_expansion.py"
test_path.write_text(
    '''import json
import unittest
from pathlib import Path


class DeviceMatrixExpansionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = json.loads(
            Path("quality/compatibility-v2/config/device-matrix.json").read_text(encoding="utf-8")
        )
        cls.profiles = {item["id"]: item for item in cls.data["profiles"]}

    def test_original_and_new_profiles_are_present(self):
        required = {
            "phone-small-api29",
            "phone-medium-api35",
            "phone-landscape-font150-api35",
            "tablet-600-portrait-api35",
            "tablet-expanded-landscape-api35",
            "tv-logical-960x540-api36",
            "tv-720p-api36",
            "tv-1080p-api36",
            "phone-320x568-api35",
            "phone-360x800-api35",
            "phone-portrait-font130-api35",
            "phone-short-landscape-api35",
            "phone-cutout-gesture-api35",
            "tablet-medium-landscape-api35",
            "tablet-resizable-medium-api35",
            "foldable-unfolded-api35",
            "tv-4k-api36",
        }
        self.assertEqual(required, set(self.profiles))

    def test_expected_logical_geometry_matches_physical_density(self):
        for profile in self.profiles.values():
            width = round(profile["width_px"] * 160 / profile["density_dpi"])
            height = round(profile["height_px"] * 160 / profile["density_dpi"])
            self.assertEqual(width, profile["expected_width_dp"], profile["id"])
            self.assertEqual(height, profile["expected_height_dp"], profile["id"])

    def test_tv_4k_is_960_by_540_logical_dp(self):
        profile = self.profiles["tv-4k-api36"]
        self.assertEqual((3840, 2160), (profile["width_px"], profile["height_px"]))
        self.assertEqual(640, profile["density_dpi"])
        self.assertEqual((960, 540), (profile["expected_width_dp"], profile["expected_height_dp"]))
        self.assertEqual("TELEVISION", profile["expected_device_class"])
        self.assertEqual("REMOTE", profile["expected_input_mode"])

    def test_cutout_and_gesture_profile_is_explicit(self):
        profile = self.profiles["phone-cutout-gesture-api35"]
        self.assertEqual("tall", profile["cutout_mode"])
        self.assertEqual("gestural", profile["navigation_mode"])


if __name__ == "__main__":
    unittest.main()
''',
    encoding="utf-8",
)

for path, markers in {
    "quality/compatibility-v2/config/device-matrix.json": ("tv-4k-api36", "phone-320x568-api35"),
    configure: ("WINDOW-CLASSIFICATION.txt", "effective_logical_width_dp"),
    runtime: ("expected_device_class", "expected_orientation"),
    collector: ("WINDOW-CLASSIFICATION.txt",),
    gate: ("runtime-window-classification",),
    ".github/workflows/compatibility-v2-full.yml": ("fromJSON(needs.validate-and-build.outputs.runtime_matrix)",),
    targeted: ("expected_device_class", "cutout_mode"),
}.items():
    text = (ROOT / path).read_text(encoding="utf-8")
    for marker in markers:
        if marker not in text:
            raise SystemExit(f"{path}: missing matrix expansion marker {marker}")
