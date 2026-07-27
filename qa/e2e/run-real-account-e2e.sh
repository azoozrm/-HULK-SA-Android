#!/usr/bin/env bash
set -uo pipefail

APK="$1"
DEVICE="$2"
IS_TV="$3"
OUT="${4:-qa-real-results/$DEVICE}"
mkdir -p "$OUT"

: "${HULK_QA_USERNAME:?HULK_QA_USERNAME secret is required}"
: "${HULK_QA_PASSWORD:?HULK_QA_PASSWORD secret is required}"

die() {
  echo "$1" > "$OUT/harness-error.txt"
  finalize 1
}

sanitize_artifacts() {
  python3 - "$OUT" <<'PY'
from pathlib import Path
import os
import sys
root = Path(sys.argv[1])
secrets = [os.environ.get("HULK_QA_USERNAME", ""), os.environ.get("HULK_QA_PASSWORD", "")]
for path in root.rglob("*"):
    if not path.is_file() or path.suffix.lower() in {".png", ".zip"}:
        continue
    try:
        data = path.read_text(encoding="utf-8", errors="ignore")
        for secret in secrets:
            if secret:
                data = data.replace(secret, "[REDACTED]")
        path.write_text(data, encoding="utf-8")
    except Exception:
        pass
PY
}

finalize() {
  local requested_status="${1:-0}"
  sanitize_artifacts
  set +e
  python3 qa/e2e/analyze-real-e2e.py "$OUT" "$DEVICE"
  local analyzer_status=$?
  sanitize_artifacts
  if [[ $requested_status -ne 0 ]]; then
    exit "$requested_status"
  fi
  exit "$analyzer_status"
}

capture() {
  local name="$1"
  sleep "${2:-2.5}"
  adb exec-out screencap -p > "$OUT/$name.png" 2>/dev/null || true
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$OUT/$name.xml" >/dev/null 2>&1 || true
  adb shell dumpsys window windows > "$OUT/$name.window.txt" 2>&1 || true
  adb shell dumpsys input_method > "$OUT/$name.ime.txt" 2>&1 || true
  adb logcat -d -v threadtime > "$OUT/$name.logcat.txt" 2>&1 || true
}

dump_temp_ui() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml /tmp/hulk-real-window.xml >/dev/null 2>&1 || true
}

find_text_center() {
  local text="$1"
  python3 qa/emulator/node-center.py /tmp/hulk-real-window.xml --text-contains "$text" --index 0 2>/dev/null || true
}

tap_destination() {
  local capture_name="$1"
  shift
  dump_temp_ui
  local center=""
  local label
  for label in "$@"; do
    center=$(find_text_center "$label")
    if [[ -n "$center" ]]; then
      break
    fi
  done
  if [[ -z "$center" ]]; then
    echo "Destination not found: $capture_name" >> "$OUT/navigation-warnings.txt"
    return 1
  fi
  adb shell input tap $center >/dev/null 2>&1 || true
  capture "$capture_name" 5
  if [[ "$IS_TV" == "true" ]]; then
    {
      echo "capture=$capture_name"
      for key in 22 22 20 21 19 23 4; do
        adb shell input keyevent "$key" >/dev/null 2>&1 || true
        sleep 0.2
        adb shell dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp' || true
      done
    } > "$OUT/$capture_name.focus-trace.txt" 2>&1
  fi
  return 0
}

open_first_content() {
  local source_capture="$1"
  local target_capture="$2"
  local xml="$OUT/$source_capture.xml"
  [[ -s "$xml" ]] || return 1
  local center
  center=$(python3 qa/e2e/pick-content-node.py "$xml" --index 0 2>/dev/null || true)
  [[ -n "$center" ]] || return 1
  adb shell input tap $center >/dev/null 2>&1 || true
  capture "$target_capture" 7
  return 0
}

adb wait-for-device || die "ADB device did not become ready"
adb shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
adb shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
adb shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true
adb shell settings put secure show_ime_with_hard_keyboard 1 >/dev/null 2>&1 || true
adb install -r -t "$APK" > "$OUT/install.txt" 2>&1 || die "APK installation failed"

PACKAGE=$(adb shell pm list packages | tr -d '\r' | sed -n 's/package:\(.*hulksa.*\)/\1/p' | head -n1)
[[ -n "$PACKAGE" ]] || PACKAGE="sa.hulksa.player.dev"
adb shell pm clear "$PACKAGE" >/dev/null 2>&1 || true

{
  echo "device=$DEVICE"
  echo "is_tv=$IS_TV"
  echo "package=$PACKAGE"
  adb shell wm size
  adb shell wm density
  adb shell getprop ro.build.version.release
  adb shell getprop ro.product.model
  adb shell getprop ro.build.characteristics
} > "$OUT/device.txt" 2>&1

adb logcat -c >/dev/null 2>&1 || true
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 > "$OUT/launch.txt" 2>&1 || die "Launcher activity could not be started"
sleep 4

dump_temp_ui
USER_CENTER=$(python3 qa/emulator/node-center.py /tmp/hulk-real-window.xml --class-contains EditText --index 0 2>/dev/null || true)
PASS_CENTER=$(python3 qa/emulator/node-center.py /tmp/hulk-real-window.xml --class-contains EditText --index 1 2>/dev/null || true)
[[ -n "$USER_CENTER" ]] || die "Username field was not found"
[[ -n "$PASS_CENTER" ]] || die "Password field was not found"

adb shell input tap $USER_CENTER >/dev/null 2>&1 || true
sleep 0.4
adb shell input text "$HULK_QA_USERNAME" >/dev/null 2>&1 || die "Username input failed"
adb shell input tap $PASS_CENTER >/dev/null 2>&1 || true
sleep 0.4
adb shell input text "$HULK_QA_PASSWORD" >/dev/null 2>&1 || die "Password input failed"
adb shell input keyevent 4 >/dev/null 2>&1 || true
sleep 0.5

dump_temp_ui
LOGIN_CENTER=$(find_text_center "الدخول")
if [[ -z "$LOGIN_CENTER" ]]; then LOGIN_CENTER=$(find_text_center "دخول"); fi
if [[ -n "$LOGIN_CENTER" ]]; then
  adb shell input tap $LOGIN_CENTER >/dev/null 2>&1 || true
else
  adb shell input keyevent 66 >/dev/null 2>&1 || true
fi

LOGIN_OK=false
for _ in $(seq 1 40); do
  sleep 3
  dump_temp_ui
  if python3 - /tmp/hulk-real-window.xml <<'PY'
import sys
import xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    raise SystemExit(1)
texts = []
edit_count = 0
for node in root.iter("node"):
    cls = node.attrib.get("class", "")
    if "EditText" in cls:
        edit_count += 1
    texts.append((node.attrib.get("text", "") or "") + " " + (node.attrib.get("content-desc", "") or ""))
joined = " ".join(texts)
markers = ("الرئيسية", "الأفلام", "افلام", "المسلسلات", "مسلسلات", "البث", "القنوات", "المفضلة")
raise SystemExit(0 if edit_count == 0 and any(x in joined for x in markers) else 1)
PY
  then
    LOGIN_OK=true
    break
  fi
done

if [[ "$LOGIN_OK" != "true" ]]; then
  adb logcat -d -v threadtime > "$OUT/login-failure.logcat.txt" 2>&1 || true
  finalize 1
fi

touch "$OUT/login-success.flag"
adb logcat -c >/dev/null 2>&1 || true
capture "home-real" 4

tap_destination "live-real" "البث المباشر" "البث" "القنوات" "مباشر" || true
if open_first_content "live-real" "live-player-real"; then
  adb shell input keyevent 4 >/dev/null 2>&1 || true
  sleep 2
fi

tap_destination "movies-real" "الأفلام" "افلام" || true
if open_first_content "movies-real" "movie-details-real"; then
  dump_temp_ui
  PLAY_CENTER=$(find_text_center "تشغيل")
  if [[ -z "$PLAY_CENTER" ]]; then PLAY_CENTER=$(find_text_center "شاهد"); fi
  if [[ -n "$PLAY_CENTER" ]]; then
    adb shell input tap $PLAY_CENTER >/dev/null 2>&1 || true
    capture "movie-player-real" 9
    adb shell input keyevent 4 >/dev/null 2>&1 || true
    sleep 2
  fi
fi

tap_destination "series-real" "المسلسلات" "مسلسلات" || true
open_first_content "series-real" "series-details-real" || true
adb shell input keyevent 4 >/dev/null 2>&1 || true
sleep 1

tap_destination "search-real" "البحث" || true
adb shell input keyevent 4 >/dev/null 2>&1 || true
sleep 1

tap_destination "downloads-real" "التنزيلات" "التحميلات" || true
adb shell input keyevent 4 >/dev/null 2>&1 || true
sleep 1

tap_destination "settings-real" "الإعدادات" "الاعدادات" || true

adb logcat -d -v threadtime > "$OUT/final.logcat.txt" 2>&1 || true
finalize 0
