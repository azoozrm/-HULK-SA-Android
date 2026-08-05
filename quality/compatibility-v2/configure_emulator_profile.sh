#!/usr/bin/env bash
set -Eeuo pipefail

profile="${1:?profile id is required}"
width="${2:?width px is required}"
height="${3:?height px is required}"
density="${4:?density dpi is required}"
font_scale="${5:?font scale is required}"
rotation="${6:?rotation is required}"
locale="${7:?BCP-47 locale is required}"
out="${8:?evidence path is required}"
cutout_mode="${9:-none}"
navigation_mode="${10:-default}"
expected_device_class="${11:-UNSPECIFIED}"
expected_input_mode="${12:-UNSPECIFIED}"
expected_width_class="${13:-UNSPECIFIED}"
expected_height_class="${14:-UNSPECIFIED}"
expected_orientation="${15:-UNSPECIFIED}"
classification_out="$(dirname "$out")/WINDOW-CLASSIFICATION.txt"
mkdir -p "$(dirname "$out")"
: > "$out"
stage="initializing"

record() {
  printf '%s\n' "$*" >> "$out"
}

capture_device_state() {
  {
    echo "diagnostic_stage=$stage"
    echo "diagnostic_shell_uid=$(adb shell id -u 2>/dev/null | tr -d '\r' || true)"
    echo "diagnostic_locale=$(adb shell getprop persist.sys.locale 2>/dev/null | tr -d '\r' || true)"
    echo "diagnostic_boot_completed=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    echo "diagnostic_wm_size=$(adb shell wm size 2>/dev/null | tr -d '\r' || true)"
    echo "diagnostic_wm_density=$(adb shell wm density 2>/dev/null | tr -d '\r' || true)"
    echo "diagnostic_font_scale=$(adb shell settings get system font_scale 2>/dev/null | tr -d '\r' || true)"
    echo "diagnostic_rotation=$(adb shell settings get system user_rotation 2>/dev/null | tr -d '\r' || true)"
  } >> "$out"
}

on_error() {
  local line="$1"
  local command="$2"
  local status="$3"
  trap - ERR
  record "result=FAIL"
  record "exit_status=$status"
  record "failed_line=$line"
  record "failed_command=$command"
  capture_device_state
  exit "$status"
}
trap 'on_error "$LINENO" "$BASH_COMMAND" "$?"' ERR

record "profile=$profile"
record "requested_locale=$locale"
record "requested_size=${width}x${height}"
record "requested_density=$density"
record "requested_font_scale=$font_scale"
record "requested_rotation=$rotation"
record "requested_cutout_mode=$cutout_mode"
record "requested_navigation_mode=$navigation_mode"
record "expected_device_class=$expected_device_class"
record "expected_input_mode=$expected_input_mode"
record "expected_width_class=$expected_width_class"
record "expected_height_class=$expected_height_class"
record "expected_orientation=$expected_orientation"

wait_for_boot() {
  adb wait-for-device
  timeout 300 bash -c 'until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d "\r")" == "1" ]]; do sleep 2; done'
}

wait_for_services() {
  adb wait-for-device
  timeout 300 bash -c '
    until
      adb shell service check activity 2>/dev/null | tr -d "\r" | grep -q "found" &&
      adb shell service check window 2>/dev/null | tr -d "\r" | grep -q "found" &&
      adb shell service check package 2>/dev/null | tr -d "\r" | grep -q "found" &&
      adb shell service check settings 2>/dev/null | tr -d "\r" | grep -q "found" &&
      adb shell wm size 2>/dev/null | tr -d "\r" | grep -q "size" &&
      adb shell wm density 2>/dev/null | tr -d "\r" | grep -q "density" &&
      adb shell settings get system font_scale >/dev/null 2>&1
    do
      sleep 2
    done
  '
}

effective_size_from_output() {
  local value
  value="$(printf '%s\n' "$1" | sed -n 's/^Override size: //p' | tail -1)"
  if [[ -z "$value" ]]; then
    value="$(printf '%s\n' "$1" | sed -n 's/^Physical size: //p' | tail -1)"
  fi
  printf '%s' "$value"
}

physical_size_from_output() {
  printf '%s\n' "$1" | sed -n 's/^Physical size: //p' | tail -1
}

effective_density_from_output() {
  local value
  value="$(printf '%s\n' "$1" | sed -n 's/^Override density: //p' | tail -1)"
  if [[ -z "$value" ]]; then
    value="$(printf '%s\n' "$1" | sed -n 's/^Physical density: //p' | tail -1)"
  fi
  printf '%s' "$value"
}

stage="initial-boot"
wait_for_boot
wait_for_services
sdk="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
initial_locale="$(adb shell getprop persist.sys.locale | tr -d '\r')"
record "sdk=$sdk"
record "initial_locale=$initial_locale"
record "initial_wm_size=$(adb shell wm size | tr -d '\r')"

stage="request-adb-root"
if root_output="$(adb root 2>&1)"; then
  root_status=0
else
  root_status=$?
fi
record "adb_root_status=$root_status"
record "adb_root_output=${root_output//$'\n'/ | }"
adb wait-for-device
wait_for_services
shell_uid="$(adb shell id -u | tr -d '\r')"
record "shell_uid_after_adb_root=$shell_uid"

actual_locale="$(adb shell getprop persist.sys.locale | tr -d '\r')"
locale_mode="system-existing"
if [[ "$actual_locale" != "$locale" ]]; then
  if [[ "$root_status" -eq 0 && "$shell_uid" == "0" ]]; then
    stage="apply-system-locale"
    locale_mode="system-root"
    record "locale_restart_required=true"
    adb shell "setprop persist.sys.locale '$locale'; stop; sleep 5; start"
    wait_for_services
  elif [[ "$sdk" =~ ^[0-9]+$ && "$sdk" -ge 33 ]]; then
    locale_mode="application-deferred"
    record "locale_restart_required=false"
    record "locale_deferred_reason=system image is non-root; app locale will be applied after APK installation"
  else
    record "locale_mode=unsupported"
    record "result=BLOCKED"
    record "failure_reason=system locale differs and this pre-Android-13 image does not permit root"
    capture_device_state
    exit 3
  fi
else
  record "locale_restart_required=false"
fi
record "locale_mode=$locale_mode"

stage="apply-window-profile"
pre_reset_size_output="$(adb shell wm size | tr -d '\r')"
record "pre_reset_wm_size=${pre_reset_size_output//$'\n'/ | }"
adb shell wm size reset
wait_for_services
reset_size_output="$(adb shell wm size | tr -d '\r')"
physical_size="$(physical_size_from_output "$reset_size_output")"
record "post_reset_wm_size=${reset_size_output//$'\n'/ | }"
record "physical_size_after_reset=$physical_size"
if [[ "$physical_size" == "${width}x${height}" ]]; then
  record "wm_size_mode=physical"
else
  adb shell wm size "${width}x${height}"
  record "wm_size_mode=override"
fi
adb shell wm density reset
adb shell wm density "$density"
adb shell settings put system font_scale "$font_scale"
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation "$rotation"

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
  if enable_output="$(adb shell cmd overlay enable --user 0 "$package_name" 2>&1)"; then
    enable_status=0
  else
    enable_status=$?
  fi
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
actual_locale="$(adb shell getprop persist.sys.locale | tr -d '\r')"
size_output="$(adb shell wm size | tr -d '\r')"
density_output="$(adb shell wm density | tr -d '\r')"
actual_font="$(adb shell settings get system font_scale | tr -d '\r')"
actual_rotation="$(adb shell settings get system user_rotation | tr -d '\r')"
effective_size="$(effective_size_from_output "$size_output")"
effective_density="$(effective_density_from_output "$density_output")"

record "actual_locale=$actual_locale"
record "wm_size=$size_output"
record "wm_density=$density_output"
record "effective_size=$effective_size"
record "effective_density=$effective_density"
record "actual_font_scale=$actual_font"
record "actual_rotation=$actual_rotation"
record "ui_mode=$(adb shell dumpsys uimode | tr -d '\r')"

if [[ "$locale_mode" != "application-deferred" && "$actual_locale" != "$locale" ]]; then
  record "result=BLOCKED"
  record "failure_reason=system locale mismatch after profile configuration"
  capture_device_state
  exit 3
fi
if [[ "$effective_size" != "${width}x${height}" ]]; then
  record "result=BLOCKED"
  record "failure_reason=effective emulator size does not match requested profile"
  capture_device_state
  exit 3
fi
if [[ "$effective_density" != "$density" ]]; then
  record "result=BLOCKED"
  record "failure_reason=effective emulator density does not match requested profile"
  capture_device_state
  exit 3
fi
if [[ "$actual_font" != "$font_scale" ]]; then
  record "result=BLOCKED"
  record "failure_reason=emulator font scale was not applied"
  capture_device_state
  exit 3
fi
if [[ "$actual_rotation" != "$rotation" ]]; then
  record "result=BLOCKED"
  record "failure_reason=emulator rotation was not applied"
  capture_device_state
  exit 3
fi

actual_device_class=MOBILE
actual_input_mode=TOUCH
if adb shell pm list features 2>/dev/null | tr -d '\r' | grep -q '^feature:android.software.leanback$'; then
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

python3 - \
  "$profile" "$width" "$height" "$density" "$font_scale" "$locale" \
  "$expected_device_class" "$expected_input_mode" "$expected_width_class" \
  "$expected_height_class" "$expected_orientation" "$cutout_mode" "$navigation_mode" \
  "$effective_size" "$effective_density" "$actual_device_class" "$actual_input_mode" \
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
if ! grep -q '^result=PASS$' "$classification_out"; then
  record "result=FAIL"
  record "failure_reason=logical window classification mismatch"
  cat "$classification_out" >> "$out"
  exit 1
fi

record "result=PASS"
record "profile_verified=true"
