#!/usr/bin/env bash
set -euo pipefail

profile="${1:?profile id is required}"
width="${2:?width px is required}"
height="${3:?height px is required}"
density="${4:?density dpi is required}"
font_scale="${5:?font scale is required}"
rotation="${6:?rotation is required}"
locale="${7:?BCP-47 locale is required}"
out="${8:?evidence path is required}"
mkdir -p "$(dirname "$out")"

wait_for_boot() {
  adb wait-for-device
  timeout 300 bash -c 'until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d "\r")" == "1" ]]; do sleep 2; done'
}

wait_for_services() {
  adb wait-for-device
  timeout 300 bash -c 'until adb shell service check activity 2>/dev/null | tr -d "\r" | grep -q "found"; do sleep 2; done'
  timeout 300 bash -c 'until adb shell service check window 2>/dev/null | tr -d "\r" | grep -q "found"; do sleep 2; done'
  timeout 300 bash -c 'until adb shell service check package 2>/dev/null | tr -d "\r" | grep -q "found"; do sleep 2; done'
  timeout 300 bash -c 'until adb shell service check settings 2>/dev/null | tr -d "\r" | grep -q "found"; do sleep 2; done'
}

wait_for_boot
wait_for_services

actual_locale="$(adb shell getprop persist.sys.locale | tr -d '\r')"
if [[ "$actual_locale" != "$locale" ]]; then
  adb shell setprop persist.sys.locale "$locale"
  adb reboot
  wait_for_boot
  wait_for_services
fi

adb shell wm size "${width}x${height}"
adb shell wm density "$density"
adb shell settings put system font_scale "$font_scale"
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation "$rotation"

wait_for_services
actual_locale="$(adb shell getprop persist.sys.locale | tr -d '\r')"
size_output="$(adb shell wm size | tr -d '\r')"
density_output="$(adb shell wm density | tr -d '\r')"
actual_font="$(adb shell settings get system font_scale | tr -d '\r')"
actual_rotation="$(adb shell settings get system user_rotation | tr -d '\r')"

{
  echo "profile=$profile"
  echo "requested_locale=$locale"
  echo "actual_locale=$actual_locale"
  echo "requested_size=${width}x${height}"
  echo "wm_size=$size_output"
  echo "requested_density=$density"
  echo "wm_density=$density_output"
  echo "requested_font_scale=$font_scale"
  echo "actual_font_scale=$actual_font"
  echo "requested_rotation=$rotation"
  echo "actual_rotation=$actual_rotation"
  echo "ui_mode=$(adb shell dumpsys uimode | tr -d '\r')"
} > "$out"

[[ "$actual_locale" == "$locale" ]] || {
  echo "BLOCKED: emulator locale is '$actual_locale', expected '$locale' after full reboot" >&2
  exit 3
}
[[ "$size_output" == *"${width}x${height}"* ]] || {
  echo "BLOCKED: emulator size override was not applied" >&2
  exit 3
}
[[ "$density_output" == *"$density"* ]] || {
  echo "BLOCKED: emulator density override was not applied" >&2
  exit 3
}
[[ "$actual_font" == "$font_scale" ]] || {
  echo "BLOCKED: emulator font scale was not applied" >&2
  exit 3
}
[[ "$actual_rotation" == "$rotation" ]] || {
  echo "BLOCKED: emulator rotation was not applied" >&2
  exit 3
}
