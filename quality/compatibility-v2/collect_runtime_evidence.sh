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
if adb shell pm list features 2>/dev/null | tr -d '\r' | grep -q '^feature:android.software.leanback$'; then
  is_tv=true
  category="android.intent.category.LEANBACK_LAUNCHER"
fi

resolve_output="$(adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN \
  -c "$category" \
  "$package" 2>&1 | tr -d '\r')"
resolved_activity="$(printf '%s\n' "$resolve_output" | awk '/^[^[:space:]]+\/[^[:space:]]+$/ { component=$0 } END { print component }')"

{
  echo "package=$package"
  echo "is_tv=$is_tv"
  echo "category=$category"
  echo "resolve_output=${resolve_output//$'\n'/ | }"
  echo "resolved_activity=$resolved_activity"
  if [[ -z "$resolved_activity" ]]; then
    echo "Unable to resolve an explicit launcher component"
    exit 1
  fi
  adb shell am force-stop "$package"
  adb shell am start -W -n "$resolved_activity"
} > "$out/FOREGROUND-APP.txt" 2>&1 || status=1

foreground_ready=false
for _ in $(seq 1 30); do
  adb shell dumpsys activity activities > "$out/ACTIVITY-ACTIVITIES.txt" 2>&1 || true
  adb shell dumpsys window windows > "$out/WINDOW-WINDOWS.txt" 2>&1 || true
  if grep -E 'mResumedActivity|topResumedActivity|ResumedActivity' "$out/ACTIVITY-ACTIVITIES.txt" | grep -q "$package" || \
     grep -E 'mCurrentFocus|mFocusedApp' "$out/WINDOW-WINDOWS.txt" | grep -q "$package"; then
    foreground_ready=true
    break
  fi
  sleep 1
done

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
