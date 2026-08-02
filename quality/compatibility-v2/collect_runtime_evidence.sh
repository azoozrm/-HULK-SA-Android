#!/usr/bin/env bash
set -euo pipefail

profile="${1:?profile id is required}"
out="${2:?output directory is required}"
test_class="${3:-sa.hulksa.player.compatibilityv2.CompatibilityV2InstrumentationTest}"
mkdir -p "$out"

package="sa.hulksa.player.dev"
test_package="sa.hulksa.player.dev.test"
runner="androidx.test.runner.AndroidJUnitRunner"
status=0

capture_window_windows() {
  adb shell dumpsys window windows > "$1" 2>&1 || true
}

ime_window_block() {
  local dump="$1"
  awk '
    /^  Window #[0-9]+ Window\{.* InputMethod\}:/ { in_ime=1 }
    in_ime && /^  Window #[0-9]+ Window\{/ && $0 !~ / InputMethod\}:/ { exit }
    in_ime { print }
  ' "$dump"
}

ime_is_actually_visible() {
  local dump="$1"
  local block
  block="$(ime_window_block "$dump")"
  [[ -n "$block" ]] || return 1

  # Insets animation leashes may survive after the IME window is fully hidden.
  # Treat the IME as visible only when the window itself is VISIBLE and has a
  # surface or is reported on-screen/visible by WindowManager.
  printf '%s\n' "$block" | grep -q 'mViewVisibility=0x0' || return 1
  printf '%s\n' "$block" | grep -Eq 'mHasSurface=true|isOnScreen=true|isVisible=true'
}

wait_for_foreground() {
  local attempts="${1:-30}"
  foreground_ready=false
  for _ in $(seq 1 "$attempts"); do
    adb shell dumpsys activity activities > "$out/ACTIVITY-ACTIVITIES.txt" 2>&1 || true
    capture_window_windows "$out/WINDOW-WINDOWS.txt"
    if grep -E 'mResumedActivity|topResumedActivity|ResumedActivity' "$out/ACTIVITY-ACTIVITIES.txt" | grep -q "$package" || \
       grep -E 'mCurrentFocus|mFocusedApp' "$out/WINDOW-WINDOWS.txt" | grep -q "$package"; then
      foreground_ready=true
      return 0
    fi
    sleep 1
  done
  return 1
}

{
  echo "profile=$profile"
  echo "test_class=$test_class"
  echo "serial=${ANDROID_SERIAL:-$(adb get-serialno)}"
  echo "sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "model=$(adb shell getprop ro.product.model | tr -d '\r')"
  echo "device=$(adb shell getprop ro.product.device | tr -d '\r')"
  echo "abi=$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
  echo "system_locale=$(adb shell getprop persist.sys.locale | tr -d '\r')"
  echo "app_locales=$(adb shell cmd locale get-app-locales "$package" --user 0 2>/dev/null | tr -d '\r' || true)"
} > "$out/DEVICE-PROFILE.txt"

{
  adb shell wm size
  adb shell wm density
  adb shell settings get system font_scale
  adb shell dumpsys window displays
  adb shell dumpsys window insets 2>/dev/null || true
} > "$out/WINDOW-METRICS.txt" 2>&1

set +e
adb shell am instrument -w -r \
  -e class "$test_class" \
  "$test_package/$runner" > "$out/INSTRUMENTATION.txt" 2>&1
status=$?
python3 quality/compatibility-v2/instrumentation_to_junit.py \
  "$out/INSTRUMENTATION.txt" "$out/INSTRUMENTATION.xml" --process-status "$status"
parser_status=$?
set -e
if [[ "$parser_status" -ne 0 ]]; then status="$parser_status"; fi

is_tv=false
category="android.intent.category.LAUNCHER"
activity_class="sa.hulksa.player.MainActivity"
if adb shell pm list features 2>/dev/null | tr -d '\r' | grep -q '^feature:android.software.leanback$'; then
  is_tv=true
  category="android.intent.category.LEANBACK_LAUNCHER"
  activity_class="sa.hulksa.player.TvMainActivity"
fi
resolved_activity="${package}/${activity_class}"
component_declared=false
if adb shell dumpsys package "$package" 2>/dev/null | tr -d '\r' | grep -Fq "$activity_class"; then
  component_declared=true
fi

{
  echo "package=$package"
  echo "is_tv=$is_tv"
  echo "category=$category"
  echo "launch_contract=explicit-manifest-component"
  echo "activity_class=$activity_class"
  echo "resolved_activity=$resolved_activity"
  echo "component_declared=$component_declared"
  if [[ "$component_declared" != true ]]; then
    echo "Canonical launcher component is not declared in the installed package"
    exit 1
  fi
  adb shell am force-stop "$package"
  adb shell am start -W -n "$resolved_activity"
} > "$out/FOREGROUND-APP.txt" 2>&1 || status=1

wait_for_foreground 30 || true

ime_initial_active=false
ime_back_sent=false
foreground_relaunch_after_ime=false
capture_window_windows "$out/IME-WINDOW-BEFORE.txt"
if ime_is_actually_visible "$out/IME-WINDOW-BEFORE.txt"; then
  ime_initial_active=true
fi

ime_hidden=false
for _ in $(seq 1 20); do
  capture_window_windows "$out/IME-WINDOW-POLL.txt"
  if ! ime_is_actually_visible "$out/IME-WINDOW-POLL.txt"; then
    ime_hidden=true
    break
  fi
  sleep 0.5
done

if [[ "$ime_hidden" != true ]]; then
  ime_back_sent=true
  adb shell input keyevent KEYCODE_BACK || true
  for _ in $(seq 1 20); do
    capture_window_windows "$out/IME-WINDOW-POLL.txt"
    if ! ime_is_actually_visible "$out/IME-WINDOW-POLL.txt"; then
      ime_hidden=true
      break
    fi
    sleep 0.5
  done
fi

# A BACK used to dismiss a genuinely visible IME must never leave the launcher
# as the captured foreground. Relaunch the canonical component once if the
# platform consumed BACK as activity navigation instead of IME dismissal.
if ! wait_for_foreground 3; then
  foreground_relaunch_after_ime=true
  adb shell am start -W -n "$resolved_activity" >> "$out/FOREGROUND-APP.txt" 2>&1 || status=1
  wait_for_foreground 30 || true
fi

sleep 1
capture_window_windows "$out/WINDOW-WINDOWS.txt"
if ime_is_actually_visible "$out/WINDOW-WINDOWS.txt"; then
  ime_hidden=false
fi
{
  echo "ime_detection=window-visibility-and-surface"
  echo "ime_initial_active=$ime_initial_active"
  echo "ime_back_sent=$ime_back_sent"
  echo "foreground_relaunch_after_ime=$foreground_relaunch_after_ime"
  echo "ime_hidden=$ime_hidden"
  if [[ "$ime_hidden" == true ]]; then
    echo "result=PASS"
  else
    echo "result=FAIL"
    echo "failure_reason=input method window remained actually visible"
  fi
} > "$out/IME-STATE.txt"
if [[ "$ime_hidden" != true ]]; then
  status=1
fi

adb shell dumpsys activity activities > "$out/ACTIVITY-ACTIVITIES.txt" 2>&1 || true
capture_window_windows "$out/WINDOW-WINDOWS.txt"
adb shell dumpsys activity top > "$out/ACTIVITY-TOP.txt" 2>&1 || true
if [[ "$foreground_ready" != true ]]; then
  echo "HULK SA did not become the foreground application" >> "$out/FOREGROUND-APP.txt"
  status=1
fi
if ! grep -q "$package" "$out/ACTIVITY-TOP.txt" && \
   ! grep -q "$package" "$out/ACTIVITY-ACTIVITIES.txt" && \
   ! grep -q "$package" "$out/WINDOW-WINDOWS.txt"; then
  echo "Foreground dumps do not identify the HULK SA package" >> "$out/FOREGROUND-APP.txt"
  status=1
fi

adb logcat -d -v threadtime > "$out/logcat.txt" 2>&1 || true
adb shell uiautomator dump /sdcard/compatibility-v2-window.xml > /dev/null 2>&1 || true
adb pull /sdcard/compatibility-v2-window.xml "$out/window.xml" > /dev/null 2>&1 || true
adb exec-out screencap -p > "$out/full-window.png" || true
adb shell dumpsys meminfo "$package" > "$out/MEMINFO.txt" 2>&1 || true

if [[ -s "$out/window.xml" ]] && ! grep -q "package=\"$package\"" "$out/window.xml"; then
  echo "Window hierarchy does not contain the HULK SA package" >> "$out/FOREGROUND-APP.txt"
  status=1
fi

for required in \
  PROFILE-CONFIG.txt \
  APPLICATION-LOCALE.txt \
  DEVICE-PROFILE.txt \
  WINDOW-METRICS.txt \
  INSTRUMENTATION.txt \
  INSTRUMENTATION.xml \
  FOREGROUND-APP.txt \
  IME-STATE.txt \
  ACTIVITY-TOP.txt \
  ACTIVITY-ACTIVITIES.txt \
  WINDOW-WINDOWS.txt \
  logcat.txt \
  window.xml \
  full-window.png \
  MEMINFO.txt; do
  if [[ ! -s "$out/$required" ]]; then
    echo "Missing mandatory runtime evidence: $required" >&2
    status=1
  fi
done

(
  cd "$out"
  find . -maxdepth 1 -type f ! -name SHA256SUMS.txt -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS.txt
  sha256sum -c SHA256SUMS.txt
)

exit "$status"
