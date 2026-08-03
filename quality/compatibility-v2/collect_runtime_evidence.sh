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
sdk="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"

is_tv=false
category="android.intent.category.LAUNCHER"
activity_class="sa.hulksa.player.MainActivity"
if adb shell pm list features 2>/dev/null | tr -d '\r' | grep -q '^feature:android.software.leanback$'; then
  is_tv=true
  category="android.intent.category.LEANBACK_LAUNCHER"
  activity_class="sa.hulksa.player.TvMainActivity"
fi
resolved_activity="${package}/${activity_class}"

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
  [[ "$block" == *"mViewVisibility=0x0"* ]] || return 1
  [[ "$block" == *"mHasSurface=true"* || "$block" == *"isOnScreen=true"* || "$block" == *"isVisible=true"* ]]
}

wait_for_foreground() {
  local attempts="${1:-30}"
  foreground_ready=false
  for _ in $(seq 1 "$attempts"); do
    adb shell dumpsys activity activities > "$out/ACTIVITY-ACTIVITIES.txt" 2>&1 || true
    capture_window_windows "$out/WINDOW-WINDOWS.txt"
    grep -E 'mResumedActivity|topResumedActivity|ResumedActivity' \
      "$out/ACTIVITY-ACTIVITIES.txt" > "$out/FOREGROUND-ACTIVITY-LINES.txt" 2>/dev/null || true
    grep -E 'mCurrentFocus|mFocusedApp' \
      "$out/WINDOW-WINDOWS.txt" > "$out/FOREGROUND-WINDOW-LINES.txt" 2>/dev/null || true
    if grep -Fq "$package" "$out/FOREGROUND-ACTIVITY-LINES.txt" || \
       grep -Fq "$package" "$out/FOREGROUND-WINDOW-LINES.txt"; then
      foreground_ready=true
      return 0
    fi
    sleep 1
  done
  return 1
}

# API 33+ phones show the POST_NOTIFICATIONS system permission dialog during
# MainActivity.onCreate. The general layout/lifecycle matrix grants this one
# documented precondition so the system overlay does not own the foreground.
# The permission decision policy remains covered by its dedicated unit tests.
permission_required=false
permission_granted=false
permission_grant_status=0
permission_grant_output="not-required"
if [[ "$is_tv" != true && "$sdk" =~ ^[0-9]+$ && "$sdk" -ge 33 ]]; then
  permission_required=true
  set +e
  permission_grant_output="$(adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS 2>&1)"
  permission_grant_status=$?
  set -e
fi
adb shell dumpsys package "$package" > "$out/RUNTIME-PERMISSIONS-DUMP.txt" 2>&1 || true
if [[ "$permission_required" != true ]] || \
   grep -Fq 'android.permission.POST_NOTIFICATIONS: granted=true' "$out/RUNTIME-PERMISSIONS-DUMP.txt"; then
  permission_granted=true
fi
{
  echo "sdk=$sdk"
  echo "is_tv=$is_tv"
  echo "permission=android.permission.POST_NOTIFICATIONS"
  echo "permission_required=$permission_required"
  echo "grant_status=$permission_grant_status"
  echo "grant_output=${permission_grant_output//$'\n'/ | }"
  echo "permission_granted=$permission_granted"
  echo "precondition_scope=general-layout-lifecycle-matrix"
  if [[ "$permission_granted" == true ]]; then
    echo "result=PASS"
  else
    echo "result=BLOCKED"
    echo "failure_reason=notification permission precondition could not be established"
  fi
} > "$out/RUNTIME-PERMISSIONS.txt"
if [[ "$permission_granted" != true ]]; then
  status=1
fi

{
  echo "profile=$profile"
  echo "test_class=$test_class"
  echo "serial=${ANDROID_SERIAL:-$(adb get-serialno)}"
  echo "sdk=$sdk"
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
instrumentation_status=$?
python3 quality/compatibility-v2/instrumentation_to_junit.py \
  "$out/INSTRUMENTATION.txt" "$out/INSTRUMENTATION.xml" --process-status "$instrumentation_status"
parser_status=$?
set -e
if [[ "$instrumentation_status" -ne 0 ]]; then status="$instrumentation_status"; fi
if [[ "$parser_status" -ne 0 ]]; then status="$parser_status"; fi

portrait_evidence_required=false
if [[ "$test_class" == *"#phonePortraitLoginFieldsAcceptTypingWithoutCrash" ]]; then
  portrait_evidence_required=true
  app_evidence_dir="/sdcard/Android/data/$package/files/compatibility-v2"
  : > "$out/PORTRAIT-EVIDENCE-PULL.txt"
  for evidence_name in \
    portrait-login-ime-stable.png \
    portrait-login-ime-stable.xml \
    portrait-login-ime-actions-reachable.png \
    portrait-login-ime-actions-reachable.xml; do
    set +e
    pull_output="$(adb pull "$app_evidence_dir/$evidence_name" "$out/$evidence_name" 2>&1)"
    pull_status=$?
    set -e
    {
      echo "file=$evidence_name"
      echo "status=$pull_status"
      echo "output=${pull_output//$'\n'/ | }"
    } >> "$out/PORTRAIT-EVIDENCE-PULL.txt"
    if [[ "$pull_status" -ne 0 ]]; then
      status=1
    fi
  done
fi

adb shell dumpsys package "$package" > "$out/INSTALLED-PACKAGE-COLLECTOR-DUMP.txt" 2>&1 || true
component_declared=false
if grep -Fq "$activity_class" "$out/INSTALLED-PACKAGE-COLLECTOR-DUMP.txt"; then
  component_declared=true
fi

set +e
{
  echo "package=$package"
  echo "is_tv=$is_tv"
  echo "category=$category"
  echo "launch_contract=explicit-manifest-component"
  echo "activity_class=$activity_class"
  echo "resolved_activity=$resolved_activity"
  echo "component_declared=$component_declared"
  if [[ "$component_declared" == true ]]; then
    adb shell am force-stop "$package"
    adb shell am start -W -n "$resolved_activity"
  else
    echo "Canonical launcher component is not declared in the installed package"
  fi
} > "$out/FOREGROUND-APP.txt" 2>&1
launch_status=$?
set -e
if [[ "$component_declared" != true || "$launch_status" -ne 0 ]]; then
  status=1
fi

wait_for_foreground 30 || true

ime_initial_active=false
ime_back_sent=false
foreground_relaunch_after_ime=false
capture_window_windows "$out/IME-WINDOW-BEFORE.txt"
if ime_is_actually_visible "$out/IME-WINDOW-BEFORE.txt"; then
  ime_initial_active=true
fi

stabilize_ime_hidden() {
  local hidden_streak=0
  local back_budget=2
  for _ in $(seq 1 60); do
    capture_window_windows "$out/IME-WINDOW-POLL.txt"
    if ime_is_actually_visible "$out/IME-WINDOW-POLL.txt"; then
      hidden_streak=0
      if [[ "$back_budget" -gt 0 ]]; then
        ime_back_sent=true
        adb shell input keyevent KEYCODE_BACK || true
        back_budget=$((back_budget - 1))
        sleep 1
      fi
    else
      hidden_streak=$((hidden_streak + 1))
      if [[ "$hidden_streak" -ge 8 ]]; then
        return 0
      fi
    fi
    sleep 0.5
  done
  return 1
}

# A freshly relaunched login activity can request the IME after the first
# foreground frame. Require four seconds of consecutive hidden samples instead
# of accepting one transient hidden window dump.
sleep 2
ime_hidden=false
if stabilize_ime_hidden; then
  ime_hidden=true
fi

if ! wait_for_foreground 3; then
  foreground_relaunch_after_ime=true
  adb shell am start -W -n "$resolved_activity" >> "$out/FOREGROUND-APP.txt" 2>&1 || status=1
  wait_for_foreground 30 || true
  sleep 2
  if stabilize_ime_hidden; then
    ime_hidden=true
  else
    ime_hidden=false
  fi
fi

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
if ! grep -Fq "$package" "$out/ACTIVITY-TOP.txt" && \
   ! grep -Fq "$package" "$out/ACTIVITY-ACTIVITIES.txt" && \
   ! grep -Fq "$package" "$out/WINDOW-WINDOWS.txt"; then
  echo "Foreground dumps do not identify the HULK SA package" >> "$out/FOREGROUND-APP.txt"
  status=1
fi

adb logcat -d -v threadtime > "$out/logcat.txt" 2>&1 || true
adb shell uiautomator dump /sdcard/compatibility-v2-window.xml > /dev/null 2>&1 || true
adb pull /sdcard/compatibility-v2-window.xml "$out/window.xml" > /dev/null 2>&1 || true
adb exec-out screencap -p > "$out/full-window.png" || true
adb shell dumpsys meminfo "$package" > "$out/MEMINFO.txt" 2>&1 || true

if [[ -s "$out/window.xml" ]] && ! grep -Fq "package=\"$package\"" "$out/window.xml"; then
  echo "Window hierarchy does not contain the HULK SA package" >> "$out/FOREGROUND-APP.txt"
  status=1
fi

for required in \
  PROFILE-CONFIG.txt \
  INSTALL-READINESS.txt \
  INSTALLATION.txt \
  PACKAGE-REGISTRATION.txt \
  INSTALLED-PACKAGE-DUMP.txt \
  APPLICATION-LOCALE.txt \
  RUNTIME-PERMISSIONS.txt \
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

if [[ "$portrait_evidence_required" == true ]]; then
  for portrait_required in \
    PORTRAIT-EVIDENCE-PULL.txt \
    portrait-login-ime-stable.png \
    portrait-login-ime-stable.xml \
    portrait-login-ime-actions-reachable.png \
    portrait-login-ime-actions-reachable.xml; do
    if [[ ! -s "$out/$portrait_required" ]]; then
      echo "Missing mandatory portrait runtime evidence: $portrait_required" >&2
      status=1
    fi
  done
fi

(
  cd "$out"
  find . -maxdepth 1 -type f ! -name SHA256SUMS.txt -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS.txt
  sha256sum -c SHA256SUMS.txt
)

exit "$status"
