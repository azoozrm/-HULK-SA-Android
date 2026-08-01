#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  run-native-emulator.sh \
    --api API --target TARGET --arch ARCH --profile PROFILE \
    --skin WIDTHxHEIGHT --result-dir PATH \
    [--contract-device-id ID --contract-device-name NAME \
     --physical-width WIDTH --physical-height HEIGHT \
     --logical-width WIDTH --logical-height HEIGHT \
     --density DPI --is-tv true|false] \
    -- COMMAND [ARG...]

Creates a fresh AVD, waits until ADB and core Android services remain stable,
and then runs COMMAND against emulator-5554. This runner is intentionally
used by the Compatibility Lab instead of relying on an action's transient
post-boot ADB commands.
EOF
}

API=""
TARGET=""
ARCH=""
PROFILE=""
SKIN=""
RESULT_DIR=""
CONTRACT_DEVICE_ID=""
CONTRACT_DEVICE_NAME=""
PHYSICAL_WIDTH=""
PHYSICAL_HEIGHT=""
LOGICAL_WIDTH=""
LOGICAL_HEIGHT=""
DENSITY=""
IS_TV=""

while (($#)); do
  case "$1" in
    --api)
      API="${2:-}"
      shift 2
      ;;
    --target)
      TARGET="${2:-}"
      shift 2
      ;;
    --arch)
      ARCH="${2:-}"
      shift 2
      ;;
    --profile)
      PROFILE="${2:-}"
      shift 2
      ;;
    --skin)
      SKIN="${2:-}"
      shift 2
      ;;
    --result-dir)
      RESULT_DIR="${2:-}"
      shift 2
      ;;
    --contract-device-id)
      CONTRACT_DEVICE_ID="${2:-}"
      shift 2
      ;;
    --contract-device-name)
      CONTRACT_DEVICE_NAME="${2:-}"
      shift 2
      ;;
    --physical-width)
      PHYSICAL_WIDTH="${2:-}"
      shift 2
      ;;
    --physical-height)
      PHYSICAL_HEIGHT="${2:-}"
      shift 2
      ;;
    --logical-width)
      LOGICAL_WIDTH="${2:-}"
      shift 2
      ;;
    --logical-height)
      LOGICAL_HEIGHT="${2:-}"
      shift 2
      ;;
    --density)
      DENSITY="${2:-}"
      shift 2
      ;;
    --is-tv)
      IS_TV="${2:-}"
      shift 2
      ;;
    --)
      shift
      break
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ ! "$API" =~ ^[0-9]+$ ]] ||
   [[ ! "$TARGET" =~ ^[a-z0-9_-]+$ ]] ||
   [[ ! "$ARCH" =~ ^[a-z0-9_]+$ ]] ||
   [[ ! "$PROFILE" =~ ^[A-Za-z0-9_.-]+$ ]] ||
   [[ ! "$SKIN" =~ ^[1-9][0-9]*x[1-9][0-9]*$ ]] ||
   [[ -z "$RESULT_DIR" ]] ||
   (($# == 0)); then
  echo "Missing or invalid emulator arguments." >&2
  usage >&2
  exit 2
fi

contract_values=(
  "$CONTRACT_DEVICE_ID" "$CONTRACT_DEVICE_NAME"
  "$PHYSICAL_WIDTH" "$PHYSICAL_HEIGHT"
  "$LOGICAL_WIDTH" "$LOGICAL_HEIGHT" "$DENSITY" "$IS_TV"
)
contract_value_count=0
for value in "${contract_values[@]}"; do
  if [[ -n "$value" ]]; then
    contract_value_count=$((contract_value_count + 1))
  fi
done
if ((contract_value_count != 0 && contract_value_count != ${#contract_values[@]})); then
  echo "The emulator display contract must be supplied in full." >&2
  usage >&2
  exit 2
fi
if ((contract_value_count > 0)); then
  if [[ ! "$CONTRACT_DEVICE_ID" =~ ^[A-Za-z0-9_.-]+$ ]] ||
     [[ ! "$PHYSICAL_WIDTH" =~ ^[1-9][0-9]*$ ]] ||
     [[ ! "$PHYSICAL_HEIGHT" =~ ^[1-9][0-9]*$ ]] ||
     [[ ! "$LOGICAL_WIDTH" =~ ^[1-9][0-9]*$ ]] ||
     [[ ! "$LOGICAL_HEIGHT" =~ ^[1-9][0-9]*$ ]] ||
     [[ ! "$DENSITY" =~ ^[1-9][0-9]*$ ]] ||
     [[ ! "$IS_TV" =~ ^(true|false)$ ]]; then
    echo "Invalid emulator display contract." >&2
    exit 2
  fi
  if [[ "$IS_TV" == "true" ]]; then
    expected_skin="${PHYSICAL_WIDTH}x${PHYSICAL_HEIGHT}"
    expected_viewport="${LOGICAL_WIDTH}x${LOGICAL_HEIGHT}"
    if [[ "$SKIN" != "$expected_skin" ]]; then
      echo "TV contract mismatch: skin $SKIN != physical size $expected_skin." >&2
      exit 2
    fi
    if [[ "$expected_skin" != "$expected_viewport" ]]; then
      echo "TV contract mismatch: physical $expected_skin != logical $expected_viewport." >&2
      exit 2
    fi
  fi
fi

SDKMANAGER="$(command -v sdkmanager)"
AVDMANAGER="$(command -v avdmanager)"
ADB="$(command -v adb)"
EMULATOR="${ANDROID_HOME:?ANDROID_HOME is required}/emulator/emulator"
if [[ ! -x "$EMULATOR" ]]; then
  EMULATOR="${ANDROID_SDK_ROOT:?ANDROID_SDK_ROOT is required}/emulator/emulator"
fi

AVD_NAME="hulk_compat_native"
SERIAL="emulator-5554"
SYSTEM_IMAGE="system-images;android-${API};${TARGET};${ARCH}"
AVD_PARENT="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
ANDROID_AVD_HOME="$(mktemp -d "$AVD_PARENT/hulk-compat-avd-XXXXXX")"
export ANDROID_AVD_HOME
mkdir -p -- "$RESULT_DIR"
EMULATOR_LOG="$RESULT_DIR/emulator-native.log"
EMULATOR_PID=""

capture_boot_evidence() {
  "$ADB" devices -l > "$RESULT_DIR/adb-devices.txt" 2>&1 || true
  "$ADB" -s "$SERIAL" shell getprop > "$RESULT_DIR/getprop.txt" 2>&1 || true
  "$ADB" -s "$SERIAL" shell service list > "$RESULT_DIR/services.txt" 2>&1 || true
  "$ADB" -s "$SERIAL" shell dumpsys activity activities > "$RESULT_DIR/activity.txt" 2>&1 || true
  "$ADB" -s "$SERIAL" shell wm size > "$RESULT_DIR/wm-size-before-lab.txt" 2>&1 || true
  "$ADB" -s "$SERIAL" shell wm density > "$RESULT_DIR/wm-density-before-lab.txt" 2>&1 || true
  cp "$ANDROID_AVD_HOME/$AVD_NAME.avd/config.ini" "$RESULT_DIR/avd-config.ini" 2>/dev/null || true
  tail -n 400 "$EMULATOR_LOG" > "$RESULT_DIR/emulator-native-tail.log" 2>/dev/null || true
}

cleanup() {
  local pid="${EMULATOR_PID:-}"
  set +e
  if [[ -n "$pid" ]]; then
    timeout 20s "$ADB" -s "$SERIAL" emu kill >/dev/null 2>&1
    for _ in $(seq 1 20); do
      if ! kill -0 "$pid" 2>/dev/null; then
        break
      fi
      sleep 1
    done
    kill "$pid" 2>/dev/null
    wait "$pid" 2>/dev/null
  fi
  rm -rf -- "$ANDROID_AVD_HOME"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

echo "Installing emulator image: $SYSTEM_IMAGE"
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" "platform-tools" "emulator" "$SYSTEM_IMAGE"

echo "Creating AVD $AVD_NAME with hardware profile $PROFILE"
echo no | "$AVDMANAGER" create avd \
  --force \
  --name "$AVD_NAME" \
  --package "$SYSTEM_IMAGE" \
  --device "$PROFILE" \
  --path "$ANDROID_AVD_HOME/$AVD_NAME.avd"

if [[ ! -s "$ANDROID_AVD_HOME/$AVD_NAME.ini" ]]; then
  echo "AVD definition was not created in $ANDROID_AVD_HOME." >&2
  find "$ANDROID_AVD_HOME" -maxdepth 2 -type f -print >&2 || true
  exit 2
fi

"$ADB" kill-server >/dev/null 2>&1 || true
"$ADB" start-server >/dev/null

echo "Starting $PROFILE at $SKIN"
"$EMULATOR" \
  -avd "$AVD_NAME" \
  -port 5554 \
  -no-window \
  -gpu swiftshader \
  -noaudio \
  -no-boot-anim \
  -camera-back none \
  -camera-front none \
  -no-snapshot \
  -no-metrics \
  -wipe-data \
  -cores 2 \
  -memory 4096 \
  -partition-size 8192 \
  -skin "$SKIN" \
  >"$EMULATOR_LOG" 2>&1 &
EMULATOR_PID="$!"

deadline=$((SECONDS + 1200))
stable_reads=0
while ((SECONDS < deadline)); do
  if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
    echo "Emulator exited before Android services became ready." >&2
    capture_boot_evidence
    exit 2
  fi

  state="$(timeout 10s "$ADB" -s "$SERIAL" get-state 2>/dev/null || true)"
  boot="$(
    timeout 10s "$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null |
      tr -d '\r' || true
  )"
  package_service="$(
    timeout 10s "$ADB" -s "$SERIAL" shell service check package 2>/dev/null || true
  )"
  activity_service="$(
    timeout 10s "$ADB" -s "$SERIAL" shell service check activity 2>/dev/null || true
  )"
  current_user="$(
    timeout 10s "$ADB" -s "$SERIAL" shell am get-current-user 2>/dev/null |
      tr -d '\r' || true
  )"

  if [[ "$state" == "device" ]] &&
     [[ "$boot" == "1" ]] &&
     [[ "$package_service" == *"found"* ]] &&
     [[ "$activity_service" == *"found"* ]] &&
     [[ "$current_user" =~ ^[0-9]+$ ]]; then
    stable_reads=$((stable_reads + 1))
  else
    stable_reads=0
  fi
  if ((stable_reads >= 6)); then
    break
  fi
  sleep 2
done

if ((stable_reads < 6)); then
  echo "Emulator did not provide stable Android services within 1200 seconds." >&2
  capture_boot_evidence
  exit 2
fi

if ((contract_value_count > 0)); then
  actual_size="$($ADB -s "$SERIAL" shell wm size 2>/dev/null | tr -d '\r' | sed -nE 's/^(Physical size: )?([0-9]+x[0-9]+)$/\2/p' | head -n 1)"
  actual_density="$($ADB -s "$SERIAL" shell wm density 2>/dev/null | tr -d '\r' | sed -nE 's/^(Physical density: )?([0-9]+)$/\2/p' | head -n 1)"
  expected_physical_size="${PHYSICAL_WIDTH}x${PHYSICAL_HEIGHT}"
  contract_status="PASS"
  contract_reason=""
  if [[ "$IS_TV" == "true" && "$actual_size" != "$expected_physical_size" ]]; then
    contract_status="FAIL"
    contract_reason="physical size $actual_size != $expected_physical_size"
  elif [[ "$IS_TV" == "true" && "$actual_density" != "$DENSITY" ]]; then
    contract_status="FAIL"
    contract_reason="physical density $actual_density != $DENSITY"
  fi
  python3 - \
    "$RESULT_DIR/emulator-display-contract.json" \
    "$CONTRACT_DEVICE_ID" "$CONTRACT_DEVICE_NAME" "$PROFILE" "$SKIN" \
    "$PHYSICAL_WIDTH" "$PHYSICAL_HEIGHT" "$LOGICAL_WIDTH" "$LOGICAL_HEIGHT" \
    "$DENSITY" "$IS_TV" "$actual_size" "$actual_density" \
    "$contract_status" "$contract_reason" <<'PY'
import json
import sys
from pathlib import Path

(
    output, device_id, device_name, profile, skin,
    physical_width, physical_height, logical_width, logical_height,
    density, is_tv, actual_size, actual_density, status, reason,
) = sys.argv[1:]
Path(output).write_text(
    json.dumps(
        {
            "schema_version": 1,
            "status": status,
            "reason": reason or None,
            "device_id": device_id,
            "device_name": device_name,
            "hardware_profile": profile,
            "skin": skin,
            "declared_physical_resolution": f"{physical_width}x{physical_height}",
            "declared_logical_resolution": f"{logical_width}x{logical_height}",
            "declared_density": int(density),
            "is_tv": is_tv == "true",
            "observed_physical_resolution": actual_size or None,
            "observed_physical_density": int(actual_density) if actual_density else None,
        },
        indent=2,
        sort_keys=True,
    ) + "\n",
    encoding="utf-8",
)
PY
  if [[ "$contract_status" != "PASS" ]]; then
    echo "Emulator display contract failed for $CONTRACT_DEVICE_ID: $contract_reason" >&2
    capture_boot_evidence
    exit 2
  fi
fi

echo "ADB and Android services remained stable; starting Compatibility Lab."
timeout 20s "$ADB" -s "$SERIAL" shell input keyevent 82 >/dev/null 2>&1 || true
timeout 20s "$ADB" -s "$SERIAL" shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
timeout 20s "$ADB" -s "$SERIAL" shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
timeout 20s "$ADB" -s "$SERIAL" shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true
capture_boot_evidence
export ANDROID_SERIAL="$SERIAL"
"$@"
