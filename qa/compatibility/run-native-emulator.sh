#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  run-native-emulator.sh \
    --api API --target TARGET --arch ARCH --profile PROFILE \
    --skin WIDTHxHEIGHT --result-dir PATH -- COMMAND [ARG...]

Creates a fresh AVD, waits until ADB remains stable after Android finishes
booting, and then runs COMMAND against emulator-5554.
EOF
}

API=""
TARGET=""
ARCH=""
PROFILE=""
SKIN=""
RESULT_DIR=""

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

echo "Installing native emulator image: $SYSTEM_IMAGE"
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
  -wipe-data \
  -cores 2 \
  -memory 4096 \
  -partition-size 8192 \
  -skin "$SKIN" \
  >"$EMULATOR_LOG" 2>&1 &
EMULATOR_PID="$!"

deadline=$((SECONDS + 900))
stable_reads=0
while ((SECONDS < deadline)); do
  if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
    echo "Emulator exited before Android finished booting." >&2
    tail -n 200 "$EMULATOR_LOG" >&2 || true
    exit 2
  fi

  state="$(timeout 10s "$ADB" -s "$SERIAL" get-state 2>/dev/null || true)"
  boot="$(
    timeout 10s "$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null |
      tr -d '\r' || true
  )"
  if [[ "$state" == "device" && "$boot" == "1" ]]; then
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
  echo "Emulator did not provide a stable ADB transport within 900 seconds." >&2
  "$ADB" devices -l >&2 || true
  tail -n 200 "$EMULATOR_LOG" >&2 || true
  exit 2
fi

echo "ADB remained stable after boot; starting Compatibility Lab."
timeout 20s "$ADB" -s "$SERIAL" shell input keyevent 82 >/dev/null 2>&1 || true
export ANDROID_SERIAL="$SERIAL"
"$@"
