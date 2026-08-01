#!/usr/bin/env bash
set -euo pipefail

profile="${1:?profile id is required}"
out="${2:?output directory is required}"
mkdir -p "$out"

package="sa.hulksa.player.dev"
test_package="sa.hulksa.player.dev.test"
runner="androidx.test.runner.AndroidJUnitRunner"
status=0

{
  echo "profile=$profile"
  echo "serial=${ANDROID_SERIAL:-$(adb get-serialno)}"
  echo "sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "model=$(adb shell getprop ro.product.model | tr -d '\r')"
  echo "device=$(adb shell getprop ro.product.device | tr -d '\r')"
  echo "abi=$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
  echo "locale=$(adb shell getprop persist.sys.locale | tr -d '\r')"
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
  -e class sa.hulksa.player.compatibilityv2.CompatibilityV2InstrumentationTest \
  "$test_package/$runner" > "$out/INSTRUMENTATION.txt" 2>&1
status=$?
set -e

adb logcat -d -v threadtime > "$out/logcat.txt" 2>&1 || true
adb shell uiautomator dump /sdcard/compatibility-v2-window.xml > /dev/null 2>&1 || true
adb pull /sdcard/compatibility-v2-window.xml "$out/window.xml" > /dev/null 2>&1 || true
adb exec-out screencap -p > "$out/full-window.png" || true
adb shell dumpsys activity top > "$out/ACTIVITY-TOP.txt" 2>&1 || true
adb shell dumpsys meminfo "$package" > "$out/MEMINFO.txt" 2>&1 || true

for required in DEVICE-PROFILE.txt WINDOW-METRICS.txt INSTRUMENTATION.txt logcat.txt window.xml full-window.png; do
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
