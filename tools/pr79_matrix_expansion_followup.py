#!/usr/bin/env python3
from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    target = Path(path)
    text = target.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one exact match, found {count}: {old[:180]!r}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


configure = "quality/compatibility-v2/configure_emulator_profile.sh"
replace_once(
    configure,
    '''python3 - \\
  "$profile" "$width" "$height" "$density" "$font_scale" "$locale" \\
  "$expected_device_class" "$expected_input_mode" "$expected_width_class" \\
  "$expected_height_class" "$expected_orientation" "$cutout_mode" "$navigation_mode" \\
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
''',
    '''effective_size="$(printf '%s\\n' "$size_output" | grep -Eo '[0-9]+x[0-9]+' | tail -1)"
effective_density="$(printf '%s\\n' "$density_output" | grep -Eo '[0-9]+' | tail -1)"
actual_device_class=MOBILE
actual_input_mode=TOUCH
if adb shell pm list features 2>/dev/null | tr -d '\\r' | grep -q '^feature:android.software.leanback$'; then
  actual_device_class=TELEVISION
  actual_input_mode=REMOTE
else
  effective_width_px="${effective_size%x*}"
  effective_height_px="${effective_size#*x}"
  effective_width_dp=$(( (effective_width_px * 160 + effective_density / 2) / effective_density ))
  effective_height_dp=$(( (effective_height_px * 160 + effective_density / 2) / effective_density ))
  if (( effective_width_dp < effective_height_dp )); then
    effective_smallest_dp=$effective_width_dp
  else
    effective_smallest_dp=$effective_height_dp
  fi
  if (( effective_smallest_dp >= 600 )); then
    actual_device_class=TABLET
  fi
fi

python3 - \\
  "$profile" "$width" "$height" "$density" "$font_scale" "$locale" \\
  "$expected_device_class" "$expected_input_mode" "$expected_width_class" \\
  "$expected_height_class" "$expected_orientation" "$cutout_mode" "$navigation_mode" \\
  "$effective_size" "$effective_density" "$actual_device_class" "$actual_input_mode" \\
  > "$classification_out" <<'PY'
import sys

(
    profile, requested_width_px, requested_height_px, requested_density_dpi,
    font_scale, locale, expected_device, expected_input, expected_width_class,
    expected_height_class, expected_orientation, cutout_mode, navigation_mode,
    effective_size, effective_density_dpi, actual_device, actual_input,
) = sys.argv[1:]
requested_width_px = int(requested_width_px)
requested_height_px = int(requested_height_px)
requested_density_dpi = int(requested_density_dpi)
effective_width_px, effective_height_px = map(int, effective_size.split("x", 1))
effective_density_dpi = int(effective_density_dpi)
width_dp = round(effective_width_px * 160 / effective_density_dpi)
height_dp = round(effective_height_px * 160 / effective_density_dpi)
width_class = "COMPACT" if width_dp < 600 else "MEDIUM" if width_dp < 840 else "EXPANDED"
height_class = "COMPACT" if height_dp < 480 else "MEDIUM" if height_dp < 900 else "EXPANDED"
orientation = "LANDSCAPE" if width_dp >= height_dp else "PORTRAIT"
expected = {
    "device_class": expected_device,
    "input_mode": expected_input,
    "width_class": expected_width_class,
    "height_class": expected_height_class,
    "orientation": expected_orientation,
}
actual = {
    "device_class": actual_device,
    "input_mode": actual_input,
    "width_class": width_class,
    "height_class": height_class,
    "orientation": orientation,
}
failures = []
if (effective_width_px, effective_height_px) != (requested_width_px, requested_height_px):
    failures.append(
        f"physical_size: requested {requested_width_px}x{requested_height_px}, "
        f"effective {effective_width_px}x{effective_height_px}"
    )
if effective_density_dpi != requested_density_dpi:
    failures.append(
        f"density: requested {requested_density_dpi}, effective {effective_density_dpi}"
    )
for key, expected_value in expected.items():
    if expected_value != "UNSPECIFIED" and expected_value != actual[key]:
        failures.append(f"{key}: expected {expected_value}, actual {actual[key]}")
print(f"profile={profile}")
print(f"requested_physical_size={requested_width_px}x{requested_height_px}")
print(f"effective_physical_size={effective_width_px}x{effective_height_px}")
print(f"requested_density_dpi={requested_density_dpi}")
print(f"effective_density_dpi={effective_density_dpi}")
print(f"effective_logical_width_dp={width_dp}")
print(f"effective_logical_height_dp={height_dp}")
print(f"font_scale={font_scale}")
print(f"locale={locale}")
print(f"actual_device_class={actual_device}")
print(f"actual_input_mode={actual_input}")
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
''',
)

# The runtime gate must require measured device/input values, not only expectations.
gate = "quality/compatibility-v2/evidence_gate.py"
replace_once(
    gate,
    '''                "expected_device_class=",
                "expected_input_mode=",
''',
    '''                "actual_device_class=",
                "actual_input_mode=",
                "expected_device_class=",
                "expected_input_mode=",
''',
)

# Keep canonical profile vocabulary consistent while preserving old fields.
matrix_path = Path("quality/compatibility-v2/config/device-matrix.json")
import json
matrix = json.loads(matrix_path.read_text(encoding="utf-8"))
for profile in matrix["profiles"]:
    if "device_family" in profile and "form_factor" not in profile:
        family = profile["device_family"]
        profile["form_factor"] = "television" if family == "tv" else family
    if "inputs" in profile and "input" not in profile:
        profile["input"] = "-".join(profile["inputs"])
    profile.pop("device_family", None)
    profile.pop("inputs", None)
matrix_path.write_text(json.dumps(matrix, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

for path, markers in {
    configure: (
        "effective_size=",
        "actual_device_class=",
        "actual_input_mode=",
        "requested_density_dpi=",
    ),
    gate: ("actual_device_class=", "actual_input_mode="),
    str(matrix_path): ('"form_factor": "television"', '"input": "remote-keyboard"'),
}.items():
    text = Path(path).read_text(encoding="utf-8")
    for marker in markers:
        if marker not in text:
            raise SystemExit(f"{path}: missing matrix follow-up marker {marker}")
