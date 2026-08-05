#!/usr/bin/env bash
set -Eeuo pipefail

profile="${1:?profile id is required}"
out="${2:-build/compatibility-v2/runtime/$profile}"
matrix_file="quality/compatibility-v2/config/device-matrix.json"
avd_name="hulk-${profile//[^a-zA-Z0-9_-]/-}"
serial="emulator-5554"
readiness="$out/EMULATOR-READINESS.txt"
console_log="$out/EMULATOR-CONSOLE.log"
mkdir -p "$out"
: > "$readiness"
: > "$console_log"

eval "$(
  python3 - "$matrix_file" "$profile" <<'PY'
import json
import shlex
import sys
from pathlib import Path

matrix_file, profile_id = sys.argv[1:]
profiles = json.loads(Path(matrix_file).read_text(encoding='utf-8'))['profiles']
profile = next((item for item in profiles if item['id'] == profile_id), None)
if profile is None:
    raise SystemExit(f'Unknown profile: {profile_id}')

values = {
    'api': profile['api'],
    'target': profile['target'],
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
}
for key, value in values.items():
    print(f"{key}={shlex.quote(str(value))}")
PY
)"

if [[ "$target" != "android-tv" ]]; then
  echo "Stable emulator runner is currently reserved for Android TV profiles." >&2
  exit 2
fi

hardware_profile="tv_1080p"
system_image="system-images;android-${api};${target};x86_64"
emulator_bin="${ANDROID_HOME:?ANDROID_HOME is required}/emulator/emulator"
emulator_pid=""

cleanup() {
  set +e
  adb -s "$serial" emu kill >/dev/null 2>&1
  if [[ -n "$emulator_pid" ]] && kill -0 "$emulator_pid" >/dev/null 2>&1; then
    kill "$emulator_pid" >/dev/null 2>&1
    wait "$emulator_pid" >/dev/null 2>&1
  fi
}
trap cleanup EXIT

echo no | avdmanager create avd \
  --force \
  --name "$avd_name" \
  --package "$system_image" \
  --device "$hardware_profile"

printf 'hw.cpu.ncore=2\nhw.ramSize=2048M\ndisk.dataPartition.size=4096M\n' \
  >> "$HOME/.android/avd/${avd_name}.avd/config.ini"

bash quality/compatibility-v2/prepare_avd_profile.sh \
  "$width" "$height" "$density" "$avd_name"

adb start-server
"$emulator_bin" \
  -port 5554 \
  -avd "$avd_name" \
  -no-window \
  -gpu swiftshader_indirect \
  -noaudio \
  -no-boot-anim \
  -no-metrics \
  -camera-back none \
  -no-snapshot-save \
  -skin "${width}x${height}" \
  > "$console_log" 2>&1 &
emulator_pid=$!

deadline=$((SECONDS + 600))
stable=0
attempt=0

while (( SECONDS < deadline )); do
  attempt=$((attempt + 1))
  state="$(adb -s "$serial" get-state 2>/dev/null || true)"
  boot="$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  activity_service="$(adb -s "$serial" shell service check activity 2>/dev/null | tr -d '\r' || true)"
  package_service="$(adb -s "$serial" shell service check package 2>/dev/null | tr -d '\r' || true)"
  settings_service="$(adb -s "$serial" shell service check settings 2>/dev/null | tr -d '\r' || true)"
  window_service="$(adb -s "$serial" shell service check window 2>/dev/null | tr -d '\r' || true)"
  framework_path="$(adb -s "$serial" shell pm path android 2>/dev/null | tr -d '\r' || true)"
  input_ready=false
  settings_ready=false

  if [[ "$state" == "device" ]] &&
     adb -s "$serial" shell input keyevent 82 >/dev/null 2>&1; then
    input_ready=true
  fi

  if [[ "$state" == "device" ]] &&
     adb -s "$serial" shell settings put global window_animation_scale 0.0 >/dev/null 2>&1 &&
     adb -s "$serial" shell settings put global transition_animation_scale 0.0 >/dev/null 2>&1 &&
     adb -s "$serial" shell settings put global animator_duration_scale 0.0 >/dev/null 2>&1; then
    settings_ready=true
  fi

  {
    echo "attempt=$attempt"
    echo "adb_state=$state"
    echo "boot_completed=$boot"
    echo "activity_service=${activity_service//$'\n'/ | }"
    echo "package_service=${package_service//$'\n'/ | }"
    echo "settings_service=${settings_service//$'\n'/ | }"
    echo "window_service=${window_service//$'\n'/ | }"
    echo "framework_path=${framework_path//$'\n'/ | }"
    echo "input_ready=$input_ready"
    echo "settings_ready=$settings_ready"
  } >> "$readiness"

  if [[ "$state" == "device" ]] &&
     [[ "$boot" == "1" ]] &&
     [[ "$activity_service" == *"found"* ]] &&
     [[ "$package_service" == *"found"* ]] &&
     [[ "$settings_service" == *"found"* ]] &&
     [[ "$window_service" == *"found"* ]] &&
     [[ "$framework_path" == package:* ]] &&
     [[ "$input_ready" == true ]] &&
     [[ "$settings_ready" == true ]]; then
    stable=$((stable + 1))
    echo "stable_readings=$stable" >> "$readiness"
    if (( stable >= 5 )); then
      break
    fi
  else
    stable=0
    echo "stable_readings=0" >> "$readiness"
  fi

  if ! kill -0 "$emulator_pid" >/dev/null 2>&1; then
    {
      echo "result=BLOCKED"
      echo "failure_reason=emulator process exited before stable readiness"
    } >> "$readiness"
    exit 3
  fi
  sleep 2
done

if (( stable < 5 )); then
  {
    echo "result=BLOCKED"
    echo "failure_reason=ADB and Android services did not reach five consecutive stable probes"
    echo "console_tail_begin"
  } >> "$readiness"
  tail -n 120 "$console_log" >> "$readiness" || true
  echo "console_tail_end" >> "$readiness"
  exit 3
fi

set +e
broadcast_idle_output="$(timeout 90 adb -s "$serial" shell am wait-for-broadcast-idle 2>&1)"
broadcast_idle_status=$?
set -e
{
  echo "broadcast_idle_status=$broadcast_idle_status"
  echo "broadcast_idle_output=${broadcast_idle_output//$'\n'/ | }"
  echo "result=PASS"
  echo "stable_readings=$stable"
} >> "$readiness"

bash quality/compatibility-v2/run_runtime_profile.sh \
  "$profile" \
  "$width" \
  "$height" \
  "$density" \
  "$font_scale" \
  "$rotation" \
  "$locale" \
  'sa.hulksa.player.compatibilityv2.CompatibilityV2InstrumentationTest' \
  "$out" \
  "$cutout_mode" \
  "$navigation_mode" \
  "$expected_device_class" \
  "$expected_input_mode" \
  "$expected_width_class" \
  "$expected_height_class" \
  "$expected_orientation"
