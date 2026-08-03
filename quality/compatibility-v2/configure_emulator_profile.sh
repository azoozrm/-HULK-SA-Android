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

stage="initial-boot"
wait_for_boot
wait_for_services
sdk="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
initial_locale="$(adb shell getprop persist.sys.locale | tr -d '\r')"
record "sdk=$sdk"
record "initial_locale=$initial_locale"
record "initial_wm_size=$(adb shell wm size | tr -d '\r')"

stage="request-adb-root"
set +e
root_output="$(adb root 2>&1)"
root_status=$?
set -e
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
adb shell wm size "${width}x${height}"
adb shell wm density "$density"
adb shell settings put system font_scale "$font_scale"
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation "$rotation"
wait_for_services

stage="verify-window-profile"
actual_locale="$(adb shell getprop persist.sys.locale | tr -d '\r')"
size_output="$(adb shell wm size | tr -d '\r')"
density_output="$(adb shell wm density | tr -d '\r')"
actual_font="$(adb shell settings get system font_scale | tr -d '\r')"
actual_rotation="$(adb shell settings get system user_rotation | tr -d '\r')"

record "actual_locale=$actual_locale"
record "wm_size=$size_output"
record "wm_density=$density_output"
record "actual_font_scale=$actual_font"
record "actual_rotation=$actual_rotation"
record "ui_mode=$(adb shell dumpsys uimode | tr -d '\r')"

if [[ "$locale_mode" != "application-deferred" && "$actual_locale" != "$locale" ]]; then
  record "result=BLOCKED"
  record "failure_reason=system locale mismatch after profile configuration"
  capture_device_state
  exit 3
fi
if [[ "$size_output" != *"${width}x${height}"* ]]; then
  record "result=BLOCKED"
  record "failure_reason=emulator size override was not applied"
  capture_device_state
  exit 3
fi
if [[ "$density_output" != *"$density"* ]]; then
  record "result=BLOCKED"
  record "failure_reason=emulator density override was not applied"
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

record "result=PASS"
record "profile_verified=true"
