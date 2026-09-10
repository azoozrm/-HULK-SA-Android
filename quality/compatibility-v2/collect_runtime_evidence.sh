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
instrumentation_timeout_seconds=600
collector_timeout_seconds="${COMPAT_V2_COLLECTOR_TIMEOUT_SECONDS:-900}"
collector_kill_after_seconds="${COMPAT_V2_COLLECTOR_KILL_AFTER_SECONDS:-15}"
adb_timeout_seconds="${COMPAT_V2_ADB_TIMEOUT_SECONDS:-30}"
adb_kill_after_seconds="${COMPAT_V2_ADB_KILL_AFTER_SECONDS:-5}"
cleanup_timeout_seconds="${COMPAT_V2_CLEANUP_TIMEOUT_SECONDS:-15}"
execution_file="$out/EVIDENCE-COLLECTION-EXECUTION.txt"
adb_timeout_marker="$out/.EVIDENCE-COLLECTION-ADB-TIMEOUT"
adb_binary="$(command -v adb)"

collector_started_ns="$(python3 -c 'import time; print(time.monotonic_ns())')"
collector_timeout_ms=$((collector_timeout_seconds * 1000))

bounded_cleanup() {
  local target="$1"
  timeout --signal=TERM --kill-after=5s "${cleanup_timeout_seconds}s" \
    "$adb_binary" shell am force-stop "$target" >/dev/null 2>&1
}

if [[ "${COMPAT_V2_COLLECTOR_BOUNDED_CHILD:-false}" != true ]]; then
  rm -f "$adb_timeout_marker"
  {
    echo "schema_version=1"
    echo "owner=collect_runtime_evidence.sh"
    echo "collector_timeout_seconds=$collector_timeout_seconds"
    echo "collector_kill_after_seconds=$collector_kill_after_seconds"
    echo "adb_timeout_seconds=$adb_timeout_seconds"
    echo "adb_kill_after_seconds=$adb_kill_after_seconds"
    echo "cleanup_timeout_seconds=$cleanup_timeout_seconds"
    echo "outer_guard_armed=true"
  } > "$execution_file"

  set +e
  COMPAT_V2_COLLECTOR_BOUNDED_CHILD=true \
    timeout --signal=TERM --kill-after="${collector_kill_after_seconds}s" "${collector_timeout_seconds}s" \
    bash "$0" "$@"
  collector_process_status=$?
  set -e
  collector_finished_ns="$(python3 -c 'import time; print(time.monotonic_ns())')"
  collector_elapsed_ms=$(((collector_finished_ns - collector_started_ns) / 1000000))
  outer_timed_out=false
  if [[ "$collector_process_status" -eq 124 ]] || \
     [[ "$collector_process_status" -eq 137 && "$collector_elapsed_ms" -ge "$collector_timeout_ms" ]]; then
    outer_timed_out=true
  fi

  if [[ "$outer_timed_out" == true ]]; then
    set +e
    bounded_cleanup "$test_package"
    outer_test_cleanup_status=$?
    bounded_cleanup "$package"
    outer_app_cleanup_status=$?
    set -e
    {
      echo "event=outer-collector-timeout"
      echo "stage=collector-owner"
      echo "timeout_seconds=$collector_timeout_seconds"
      echo "elapsed_ms=$collector_elapsed_ms"
      echo "exit_status=$collector_process_status"
      echo "timed_out=true"
      echo "outer_timed_out=true"
      echo "cleanup_test_package_status=$outer_test_cleanup_status"
      echo "cleanup_app_package_status=$outer_app_cleanup_status"
      echo "result=BLOCKED"
      echo "failure_reason=runtime evidence collector exceeded bounded execution timeout"
    } >> "$execution_file"
    exit 3
  fi
  exit "$collector_process_status"
fi

adb_command_stage() {
  local command="$*"
  case "$command" in
    *"uiautomator dump"*) echo "uiautomator-dump" ;;
    pull\ *) echo "adb-pull" ;;
    *"exec-out screencap"*) echo "screencap" ;;
    *"logcat -d"*) echo "logcat" ;;
    *"dumpsys meminfo"*) echo "meminfo" ;;
    *"am force-stop"*) echo "force-stop" ;;
    *"am start"*) echo "activity-start" ;;
    *"dumpsys"*) echo "dumpsys" ;;
    *) echo "adb-command" ;;
  esac
}

adb() {
  if [[ -f "$adb_timeout_marker" ]]; then
    return 0
  fi

  local command_text="adb"
  local argument
  for argument in "$@"; do
    printf -v argument '%q' "$argument"
    command_text+=" $argument"
  done
  local stage
  stage="$(adb_command_stage "$@")"
  local started_ns finished_ns elapsed_ms process_status timed_out
  started_ns="$(python3 -c 'import time; print(time.monotonic_ns())')"
  local had_errexit=false
  if [[ $- == *e* ]]; then
    had_errexit=true
  fi
  set +e
  timeout --signal=TERM --kill-after="${adb_kill_after_seconds}s" "${adb_timeout_seconds}s" \
    "$adb_binary" "$@"
  process_status=$?
  if [[ "$had_errexit" == true ]]; then
    set -e
  fi
  finished_ns="$(python3 -c 'import time; print(time.monotonic_ns())')"
  elapsed_ms=$(((finished_ns - started_ns) / 1000000))
  timed_out=false
  if [[ "$process_status" -eq 124 ]] || \
     [[ "$process_status" -eq 137 && "$elapsed_ms" -ge $((adb_timeout_seconds * 1000)) ]]; then
    timed_out=true
  fi
  {
    echo "event=adb-command"
    echo "stage=$stage"
    echo "command=$command_text"
    echo "timeout_seconds=$adb_timeout_seconds"
    echo "elapsed_ms=$elapsed_ms"
    echo "exit_status=$process_status"
    echo "timed_out=$timed_out"
  } >> "$execution_file"

  if [[ "$timed_out" == true ]]; then
    {
      echo "stage=$stage"
      echo "command=$command_text"
      echo "timeout_seconds=$adb_timeout_seconds"
      echo "elapsed_ms=$elapsed_ms"
      echo "exit_status=$process_status"
    } > "$adb_timeout_marker"
    return 0
  fi
  return "$process_status"
}

required_evidence_adb() {
  if ! adb "$@"; then
    status=1
  fi
  return 0
}

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

instrumentation_timed_out=false
instrumentation_timeout_ms=$((instrumentation_timeout_seconds * 1000))
instrumentation_elapsed_ms=0
test_cleanup_status="not-required"
app_cleanup_status="not-required"
set +e
instrumentation_started_ns="$(python3 -c 'import time; print(time.monotonic_ns())')"
timeout --signal=TERM --kill-after=15s "${instrumentation_timeout_seconds}s" \
  adb shell am instrument -w -r \
  -e class "$test_class" \
  "$test_package/$runner" > "$out/INSTRUMENTATION.txt" 2>&1
instrumentation_status=$?
instrumentation_finished_ns="$(python3 -c 'import time; print(time.monotonic_ns())')"
instrumentation_elapsed_ms=$(((instrumentation_finished_ns - instrumentation_started_ns) / 1000000))
# GNU timeout may return 137 after --kill-after escalates to SIGKILL; only classify it
# as timeout when monotonic execution evidence proves the 600-second boundary was reached.
if [[ "$instrumentation_status" -eq 124 ]] || \
   [[ "$instrumentation_status" -eq 137 && "$instrumentation_elapsed_ms" -ge "$instrumentation_timeout_ms" ]]; then
  instrumentation_timed_out=true
  echo "INSTRUMENTATION_TIMEOUT: exceeded ${instrumentation_timeout_seconds} seconds (process_status=${instrumentation_status}, elapsed_ms=${instrumentation_elapsed_ms})" >> "$out/INSTRUMENTATION.txt"
  timeout 15s adb shell dumpsys activity instrumentation > "$out/INSTRUMENTATION-TIMEOUT-ACTIVITY.txt" 2>&1 || true
  timeout 15s adb shell dumpsys window windows > "$out/INSTRUMENTATION-TIMEOUT-WINDOW.txt" 2>&1 || true
  timeout 15s adb logcat -d -v threadtime > "$out/INSTRUMENTATION-TIMEOUT-LOGCAT.txt" 2>&1 || true
  timeout 15s adb exec-out screencap -p > "$out/INSTRUMENTATION-TIMEOUT.png" 2>/dev/null || true
  timeout 15s adb shell am force-stop "$test_package" >/dev/null 2>&1
  test_cleanup_status=$?
  timeout 15s adb shell am force-stop "$package" >/dev/null 2>&1
  app_cleanup_status=$?
fi
parser_args=(
  quality/compatibility-v2/instrumentation_to_junit.py
  "$out/INSTRUMENTATION.txt"
  "$out/INSTRUMENTATION.xml"
  --process-status "$instrumentation_status"
)
if [[ "$instrumentation_timed_out" == true ]]; then
  parser_args+=(--timed-out --timeout-seconds "$instrumentation_timeout_seconds")
fi
python3 "${parser_args[@]}"
parser_status=$?
set -e
{
  echo "timeout_seconds=$instrumentation_timeout_seconds"
  echo "elapsed_ms=$instrumentation_elapsed_ms"
  echo "timed_out=$instrumentation_timed_out"
  echo "process_status=$instrumentation_status"
  echo "parser_status=$parser_status"
  echo "test_package_cleanup_status=$test_cleanup_status"
  echo "app_package_cleanup_status=$app_cleanup_status"
  if [[ "$instrumentation_timed_out" == true ]]; then
    echo "result=FAIL"
    echo "failure_reason=instrumentation exceeded bounded execution timeout"
  elif [[ "$instrumentation_status" -eq 0 && "$parser_status" -eq 0 ]]; then
    echo "result=PASS"
  else
    echo "result=FAIL"
    echo "failure_reason=instrumentation or parsed test result failed"
  fi
} > "$out/INSTRUMENTATION-EXECUTION.txt"
if [[ "$instrumentation_timed_out" == true ]]; then
  status=124
elif [[ "$instrumentation_status" -ne 0 ]]; then
  status="$instrumentation_status"
elif [[ "$parser_status" -ne 0 ]]; then
  status="$parser_status"
fi

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

adaptive_evidence_required=false
if [[ "$test_class" == *"AdaptiveMainShellComposeTest"* ]]; then
  adaptive_evidence_required=true
  app_evidence_dir="/sdcard/Android/data/$package/files/adaptive-main-shell-evidence"
  : > "$out/ADAPTIVE-EVIDENCE-PULL.txt"
  for evidence_name in     phone-portrait-bottom-navigation.png     phone-portrait-bottom-navigation.xml     phone-short-landscape-bottom-navigation.png     phone-short-landscape-bottom-navigation.xml     tablet-navigation-rail.png     tablet-navigation-rail.xml; do
    set +e
    pull_output="$(adb pull "$app_evidence_dir/$evidence_name" "$out/$evidence_name" 2>&1)"
    pull_status=$?
    set -e
    {
      echo "file=$evidence_name"
      echo "status=$pull_status"
      echo "output=${pull_output//$'\n'/ | }"
    } >> "$out/ADAPTIVE-EVIDENCE-PULL.txt"
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

required_evidence_adb logcat -d -v threadtime > "$out/logcat.txt" 2>&1
required_evidence_adb shell uiautomator dump /sdcard/compatibility-v2-window.xml > /dev/null 2>&1
required_evidence_adb pull /sdcard/compatibility-v2-window.xml "$out/window.xml" > /dev/null 2>&1
required_evidence_adb exec-out screencap -p > "$out/full-window.png"
required_evidence_adb shell dumpsys meminfo "$package" > "$out/MEMINFO.txt" 2>&1

evidence_timeout=false
cleanup_test_package_status="not-required"
cleanup_app_package_status="not-required"
if [[ -f "$adb_timeout_marker" ]]; then
  evidence_timeout=true
  status=3
  set +e
  bounded_cleanup "$test_package"
  cleanup_test_package_status=$?
  bounded_cleanup "$package"
  cleanup_app_package_status=$?
  set -e
  {
    echo "event=evidence-timeout-summary"
    while IFS= read -r line; do
      echo "timeout_$line"
    done < "$adb_timeout_marker"
    echo "cleanup_test_package_status=$cleanup_test_package_status"
    echo "cleanup_app_package_status=$cleanup_app_package_status"
    echo "outer_timed_out=false"
    echo "result=BLOCKED"
    echo "failure_reason=ADB could not produce required runtime evidence within the bounded command timeout"
  } >> "$execution_file"
else
  {
    echo "event=evidence-collection-summary"
    echo "elapsed_ms=$((($(python3 -c 'import time; print(time.monotonic_ns())') - collector_started_ns) / 1000000))"
    echo "evidence_timed_out=false"
    echo "outer_timed_out=false"
    if [[ "$status" -eq 0 ]]; then
      echo "result=PASS"
    else
      echo "result=FAIL"
      echo "failure_reason=runtime collection completed with one or more failed checks"
    fi
  } >> "$execution_file"
fi

if [[ -s "$out/window.xml" ]] && ! grep -Fq "package=\"$package\"" "$out/window.xml"; then
  echo "Window hierarchy does not contain the HULK SA package" >> "$out/FOREGROUND-APP.txt"
  if [[ "$evidence_timeout" != true ]]; then
    status=1
  fi
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
  WINDOW-CLASSIFICATION.txt \
  WINDOW-METRICS.txt \
  INSTRUMENTATION.txt \
  INSTRUMENTATION.xml \
  INSTRUMENTATION-EXECUTION.txt \
  EVIDENCE-COLLECTION-EXECUTION.txt \
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
    if [[ "$evidence_timeout" != true ]]; then
      status=1
    fi
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
      if [[ "$evidence_timeout" != true ]]; then
        status=1
      fi
    fi
  done
fi

if [[ "$adaptive_evidence_required" == true ]]; then
  for adaptive_required in     ADAPTIVE-EVIDENCE-PULL.txt     phone-portrait-bottom-navigation.png     phone-portrait-bottom-navigation.xml     phone-short-landscape-bottom-navigation.png     phone-short-landscape-bottom-navigation.xml     tablet-navigation-rail.png     tablet-navigation-rail.xml; do
    if [[ ! -s "$out/$adaptive_required" ]]; then
      echo "Missing mandatory adaptive runtime evidence: $adaptive_required" >&2
      if [[ "$evidence_timeout" != true ]]; then
        status=1
      fi
    fi
  done
fi

(
  cd "$out"
  find . -maxdepth 1 -type f ! -name SHA256SUMS.txt ! -name '.EVIDENCE-COLLECTION-ADB-TIMEOUT' -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS.txt
  sha256sum -c SHA256SUMS.txt
)

rm -f "$adb_timeout_marker"
exit "$status"