#!/usr/bin/env bash
set -Eeuo pipefail

profile="${1:?profile id is required}"
out="${2:-build/compatibility-v2/runtime/$profile}"
matrix_file="quality/compatibility-v2/config/device-matrix.json"
avd_name="hulk_${profile//[^a-zA-Z0-9_]/_}"
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
android_user_home="${ANDROID_USER_HOME:-${HOME}/.android}"
avd_home="${ANDROID_AVD_HOME:-${android_user_home}/avd}"
emulator_pid=""
export ANDROID_AVD_HOME="$avd_home"
mkdir -p "$avd_home"

cleanup() {
  local cleanup_result=PASS
  local attempt
  set +e

  echo "cleanup_started=true" >> "$readiness"

  timeout 15 adb -s "$serial" emu kill >/dev/null 2>&1 || true

  if [[ -n "$emulator_pid" ]] && kill -0 "$emulator_pid" >/dev/null 2>&1; then
    kill -TERM "$emulator_pid" >/dev/null 2>&1 || true

    for attempt in {1..15}; do
      if ! kill -0 "$emulator_pid" >/dev/null 2>&1; then
        break
      fi
      sleep 1
    done

    if kill -0 "$emulator_pid" >/dev/null 2>&1; then
      cleanup_result=FORCED
      kill -KILL "$emulator_pid" >/dev/null 2>&1 || true
    fi

    for attempt in {1..5}; do
      if ! kill -0 "$emulator_pid" >/dev/null 2>&1; then
        break
      fi
      sleep 1
    done

    if kill -0 "$emulator_pid" >/dev/null 2>&1; then
      cleanup_result=FAILED
    fi
  fi

  echo "cleanup_result=$cleanup_result" >> "$readiness"
}
trap cleanup EXIT

record_avd_inventory() {
  {
    echo "requested_avd_name=$avd_name"
    echo "android_user_home=$android_user_home"
    echo "android_avd_home=$avd_home"
    echo "avd_inventory_begin"
    find "$avd_home" -maxdepth 3 -mindepth 1 -printf '%y %p\n' 2>&1 | sort || true
    echo "avd_inventory_end"
    echo "avdmanager_list_begin"
    avdmanager list avd 2>&1 || true
    echo "avdmanager_list_end"
  } >> "$readiness"
}

echo no | avdmanager create avd \
  --force \
  --name "$avd_name" \
  --package "$system_image" \
  --device "$hardware_profile"

record_avd_inventory

descriptor="$avd_home/${avd_name}.ini"
if [[ ! -f "$descriptor" ]]; then
  mapfile -t descriptors < <(
    find "$avd_home" -maxdepth 1 -type f -name '*.ini' -print | sort
  )
  if (( ${#descriptors[@]} == 1 )); then
    descriptor="${descriptors[0]}"
  else
    {
      echo "result=BLOCKED"
      echo "failure_reason=unable to resolve exactly one AVD descriptor after creation"
      echo "descriptor_count=${#descriptors[@]}"
    } >> "$readiness"
    exit 3
  fi
fi

registered_avd_name="$(basename "$descriptor" .ini)"
avd_path="$(sed -n 's/^path=//p' "$descriptor" | tail -n 1)"
avd_path_rel="$(sed -n 's/^path.rel=//p' "$descriptor" | tail -n 1)"

if [[ -n "$avd_path" ]]; then
  if [[ "$avd_path" = /* ]]; then
    avd_dir="$avd_path"
  else
    avd_dir="$android_user_home/$avd_path"
  fi
elif [[ -n "$avd_path_rel" ]]; then
  avd_dir="$android_user_home/$avd_path_rel"
else
  avd_dir="$avd_home/${registered_avd_name}.avd"
fi

config="$avd_dir/config.ini"
if [[ ! -f "$config" ]]; then
  mapfile -t configs < <(
    find "$avd_home" -maxdepth 3 -type f -path '*.avd/config.ini' -print | sort
  )
  if (( ${#configs[@]} == 1 )); then
    config="${configs[0]}"
    avd_dir="$(dirname "$config")"
  else
    {
      echo "resolved_descriptor=$descriptor"
      echo "descriptor_path=$avd_path"
      echo "descriptor_path_rel=$avd_path_rel"
      echo "result=BLOCKED"
      echo "failure_reason=unable to resolve exactly one AVD config after creation"
      echo "config_count=${#configs[@]}"
    } >> "$readiness"
    exit 3
  fi
fi

{
  echo "resolved_descriptor=$descriptor"
  echo "registered_avd_name=$registered_avd_name"
  echo "resolved_avd_dir=$avd_dir"
  echo "resolved_avd_config=$config"
} >> "$readiness"

python3 - "$config" "$width" "$height" "$density" <<'PY_CONFIG'
from pathlib import Path
import sys

path = Path(sys.argv[1])
width, height, density = sys.argv[2:]
replacements = {
    'skin.name': f'{width}x{height}',
    'skin.path': f'{width}x{height}',
    'hw.lcd.width': width,
    'hw.lcd.height': height,
    'hw.lcd.density': density,
    'hw.cpu.ncore': '2',
    'hw.ramSize': '2048M',
    'disk.dataPartition.size': '4096M',
}
lines = path.read_text(encoding='utf-8').splitlines()
output = []
seen = set()
for line in lines:
    key = line.split('=', 1)[0].strip() if '=' in line else ''
    if key in replacements:
        output.append(f'{key}={replacements[key]}')
        seen.add(key)
    else:
        output.append(line)
for key, value in replacements.items():
    if key not in seen:
        output.append(f'{key}={value}')
path.write_text('\n'.join(output) + '\n', encoding='utf-8')
PY_CONFIG

for expected in \
  "skin.name=${width}x${height}" \
  "skin.path=${width}x${height}" \
  "hw.lcd.width=${width}" \
  "hw.lcd.height=${height}" \
  "hw.lcd.density=${density}" \
  'hw.cpu.ncore=2' \
  'hw.ramSize=2048M' \
  'disk.dataPartition.size=4096M'; do
  grep -Fxq "$expected" "$config" || {
    echo "AVD config verification failed: $expected" >&2
    exit 3
  }
done

adb start-server
"$emulator_bin" \
  -port 5554 \
  -avd "$registered_avd_name" \
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

echo "runtime_completed=true" >> "$readiness"
