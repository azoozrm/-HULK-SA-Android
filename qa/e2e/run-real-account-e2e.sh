#!/usr/bin/env bash
set -uo pipefail

APK="$1"
DEVICE="$2"
IS_TV="$3"
OUT="${4:-qa-real-results/$DEVICE}"
mkdir -p "$OUT"

: "${HULK_QA_USERNAME:?HULK_QA_USERNAME secret is required}"
: "${HULK_QA_PASSWORD:?HULK_QA_PASSWORD secret is required}"

sanitize_artifacts() {
  python3 - "$OUT" <<'PY'
from pathlib import Path
import os, sys
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
  local requested="${1:-0}"
  sanitize_artifacts
  set +e
  python3 qa/e2e/analyze-real-e2e.py "$OUT" "$DEVICE"
  local analyzed=$?
  sanitize_artifacts
  [[ $requested -ne 0 ]] && exit "$requested"
  exit "$analyzed"
}

die() {
  echo "$1" > "$OUT/harness-error.txt"
  adb logcat -d -v threadtime > "$OUT/failure.logcat.txt" 2>&1 || true
  finalize 1
}

capture() {
  local name="$1"
  sleep "${2:-3}"
  adb exec-out screencap -p > "$OUT/$name.png" 2>/dev/null || true
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$OUT/$name.xml" >/dev/null 2>&1 || true
  adb shell dumpsys window windows > "$OUT/$name.window.txt" 2>&1 || true
  adb shell dumpsys input_method > "$OUT/$name.ime.txt" 2>&1 || true
  adb logcat -d -v threadtime > "$OUT/$name.logcat.txt" 2>&1 || true
}

dump_ui() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml /tmp/hulk-real-window.xml >/dev/null 2>&1 || true
}

find_text() {
  python3 qa/emulator/node-center.py /tmp/hulk-real-window.xml --text-contains "$1" --index 0 2>/dev/null || true
}

record_network_state() {
  local label="$1"
  {
    echo "===== $label ====="
    date -u +%FT%TZ
    adb shell dumpsys connectivity | grep -E 'NetworkAgentInfo|VALIDATED|INTERNET|CONNECTED' | head -n 40 || true
    adb shell ip route || true
    adb shell getprop net.dns1 || true
    adb shell getprop net.dns2 || true
  } >> "$OUT/network-diagnostics.txt" 2>&1
}

prepare_network() {
  adb shell settings put global airplane_mode_on 0 >/dev/null 2>&1 || true
  adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false >/dev/null 2>&1 || true
  adb shell svc wifi enable >/dev/null 2>&1 || true
  adb shell svc data enable >/dev/null 2>&1 || true
  adb shell settings put global private_dns_mode opportunistic >/dev/null 2>&1 || true
  sleep 4
  record_network_state "network-ready-check"
}

is_logged_in() {
  dump_ui
  python3 - /tmp/hulk-real-window.xml <<'PY'
import sys, xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    raise SystemExit(1)
texts, edits = [], 0
for node in root.iter("node"):
    if "EditText" in node.attrib.get("class", ""):
        edits += 1
    texts.append((node.attrib.get("text", "") or "") + " " + (node.attrib.get("content-desc", "") or ""))
joined = " ".join(texts)
markers = ("الرئيسية", "الأفلام", "افلام", "المسلسلات", "مسلسلات", "البث", "القنوات", "المفضلة")
raise SystemExit(0 if edits == 0 and any(m in joined for m in markers) else 1)
PY
}

login_once() {
  local attempt="$1"
  prepare_network
  adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
  adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 > "$OUT/launch-$attempt.txt" 2>&1 || return 1
  sleep 6
  dump_ui
  local user pass login
  user=$(python3 qa/emulator/node-center.py /tmp/hulk-real-window.xml --class-contains EditText --index 0 2>/dev/null || true)
  pass=$(python3 qa/emulator/node-center.py /tmp/hulk-real-window.xml --class-contains EditText --index 1 2>/dev/null || true)
  [[ -n "$user" && -n "$pass" ]] || { capture "login-form-missing-$attempt" 1; return 1; }
  adb shell input tap $user >/dev/null 2>&1 || true
  adb shell input keyevent 123 >/dev/null 2>&1 || true
  for _ in $(seq 1 64); do adb shell input keyevent 67 >/dev/null 2>&1 || true; done
  adb shell input text "$HULK_QA_USERNAME" >/dev/null 2>&1 || return 1
  adb shell input tap $pass >/dev/null 2>&1 || true
  adb shell input keyevent 123 >/dev/null 2>&1 || true
  for _ in $(seq 1 64); do adb shell input keyevent 67 >/dev/null 2>&1 || true; done
  adb shell input text "$HULK_QA_PASSWORD" >/dev/null 2>&1 || return 1
  adb shell input keyevent 4 >/dev/null 2>&1 || true
  sleep 1
  dump_ui
  login=$(find_text "الدخول")
  [[ -z "$login" ]] && login=$(find_text "دخول")
  if [[ -n "$login" ]]; then adb shell input tap $login >/dev/null 2>&1 || true; else adb shell input keyevent 66 >/dev/null 2>&1 || true; fi
  for _ in $(seq 1 40); do
    sleep 3
    if is_logged_in; then return 0; fi
  done
  record_network_state "login-attempt-$attempt-failed"
  capture "login-failure-attempt-$attempt" 1
  return 1
}

tap_destination() {
  local name="$1"; shift
  dump_ui
  local center="" label
  for label in "$@"; do center=$(find_text "$label"); [[ -n "$center" ]] && break; done
  [[ -n "$center" ]] || { echo "Destination not found: $name" >> "$OUT/navigation-warnings.txt"; return 1; }
  adb shell input tap $center >/dev/null 2>&1 || true
  capture "$name" 5
}

open_first_content() {
  local source="$1" target="$2" center
  [[ -s "$OUT/$source.xml" ]] || return 1
  center=$(python3 qa/e2e/pick-content-node.py "$OUT/$source.xml" --index 0 2>/dev/null || true)
  [[ -n "$center" ]] || return 1
  adb shell input tap $center >/dev/null 2>&1 || true
  capture "$target" 7
}

adb wait-for-device || die "ADB device did not become ready"
adb shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
adb shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
adb shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true
adb shell settings put secure show_ime_with_hard_keyboard 1 >/dev/null 2>&1 || true
prepare_network
adb install -r -t "$APK" > "$OUT/install.txt" 2>&1 || die "APK installation failed"
PACKAGE=$(adb shell pm list packages | tr -d '\r' | sed -n 's/package:\(.*hulksa.*\)/\1/p' | head -n1)
[[ -n "$PACKAGE" ]] || PACKAGE="sa.hulksa.player.dev"
adb shell pm clear "$PACKAGE" >/dev/null 2>&1 || true
{
  echo "device=$DEVICE"; echo "is_tv=$IS_TV"; echo "package=$PACKAGE"
  adb shell wm size; adb shell wm density; adb shell getprop ro.build.version.release
  adb shell getprop ro.product.model; adb shell getprop ro.build.characteristics
} > "$OUT/device.txt" 2>&1

adb logcat -c >/dev/null 2>&1 || true
LOGIN_OK=false
for attempt in 1 2 3; do
  if login_once "$attempt"; then LOGIN_OK=true; break; fi
  adb shell pm clear "$PACKAGE" >/dev/null 2>&1 || true
  sleep 5
done
[[ "$LOGIN_OK" == "true" ]] || die "Real account login failed after three attempts; inspect captured login screens and network diagnostics"
touch "$OUT/login-success.flag"
adb logcat -c >/dev/null 2>&1 || true
capture "home-real" 4

tap_destination "live-real" "البث المباشر" "البث" "القنوات" "مباشر" || true
if open_first_content "live-real" "live-player-real"; then adb shell input keyevent 4 >/dev/null 2>&1 || true; sleep 2; fi

tap_destination "movies-real" "الأفلام" "افلام" || true
if open_first_content "movies-real" "movie-details-real"; then
  dump_ui
  PLAY=$(find_text "تشغيل"); [[ -z "$PLAY" ]] && PLAY=$(find_text "شاهد")
  if [[ -n "$PLAY" ]]; then adb shell input tap $PLAY >/dev/null 2>&1 || true; capture "movie-player-real" 9; adb shell input keyevent 4 >/dev/null 2>&1 || true; fi
fi

tap_destination "series-real" "المسلسلات" "مسلسلات" || true
open_first_content "series-real" "series-details-real" || true
adb shell input keyevent 4 >/dev/null 2>&1 || true

tap_destination "search-real" "البحث" || true
adb shell input keyevent 4 >/dev/null 2>&1 || true

tap_destination "downloads-real" "التنزيلات" "التحميلات" || true
adb shell input keyevent 4 >/dev/null 2>&1 || true

tap_destination "settings-real" "الإعدادات" "الاعدادات" || true
adb logcat -d -v threadtime > "$OUT/final.logcat.txt" 2>&1 || true
finalize 0
