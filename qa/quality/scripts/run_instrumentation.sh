#!/usr/bin/env bash
# Execute the API 35 instrumentation layer under Bash. The emulator action
# invokes its script through /usr/bin/sh, so complex fail-closed logic must
# live in this standalone file rather than an inline YAML block.
set -euo pipefail

runtime_evidence="quality-evidence/instrumentation/runtime"
mkdir -p "$runtime_evidence"

capture_device_evidence() {
  local destination="$1"
  mkdir -p "$destination"
  adb devices -l > "$destination/adb-devices.txt" 2>&1 || true
  adb shell getprop > "$destination/getprop.txt" 2>&1 || true
  adb shell service list > "$destination/services.txt" 2>&1 || true
  adb shell dumpsys activity activities > "$destination/activity.txt" 2>&1 || true
  adb shell dumpsys package sa.hulksa.player.dev > "$destination/package.txt" 2>&1 || true
  adb shell dumpsys meminfo > "$destination/meminfo.txt" 2>&1 || true
  adb shell dumpsys dropbox --print system_app_crash > "$destination/dropbox-system-app-crash.txt" 2>&1 || true
  adb logcat -d -v threadtime > "$destination/logcat.txt" 2>&1 || true
  cp -a app/build/outputs/androidTest-results "$destination/androidTest-results" 2>/dev/null || true
  cp -a app/build/reports/androidTests "$destination/androidTest-reports" 2>/dev/null || true
}

wait_for_android_services() {
  adb wait-for-device
  local consecutive=0
  local attempt
  for attempt in $(seq 1 180); do
    local boot package_service activity_service current_user
    boot="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    package_service="$(adb shell service check package 2>/dev/null || true)"
    activity_service="$(adb shell service check activity 2>/dev/null || true)"
    current_user="$(adb shell am get-current-user 2>/dev/null | tr -d '\r' || true)"
    if [[ "$boot" = '1' ]] &&
       [[ "$package_service" == *'found'* ]] &&
       [[ "$activity_service" == *'found'* ]] &&
       [[ "$current_user" =~ ^[0-9]+$ ]]; then
      consecutive=$((consecutive + 1))
      if (( consecutive >= 6 )); then
        return 0
      fi
    else
      consecutive=0
    fi
    sleep 5
  done
  capture_device_evidence "$runtime_evidence/boot-failure"
  return 1
}

run_instrumentation() {
  ./gradlew --no-daemon --console=plain \
    connectedDebugAndroidTest \
    -PHULK_PORTAL_URL=http://3162356.xyz:8080 \
    -PHULK_CONFIG_URL= \
    --stacktrace
}

has_zero_test_infrastructure_failure() {
  grep -Rqs 'No test results' app/build/outputs/androidTest-results 2>/dev/null &&
    ! grep -RqsE 'tests="[1-9][0-9]*"' app/build/outputs/androidTest-results 2>/dev/null
}

wait_for_android_services
adb shell input keyevent 82 || true
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true
capture_device_evidence "$runtime_evidence/pre-test"

if run_instrumentation; then
  capture_device_evidence "$runtime_evidence/success"
  exit 0
fi
first_status=$?
capture_device_evidence "$runtime_evidence/attempts/attempt-1"

# Retry only a proven zero-test infrastructure failure. A real assertion or
# product failure remains final and is never hidden by retry.
if ! has_zero_test_infrastructure_failure; then
  exit "$first_status"
fi

rm -rf app/build/outputs/androidTest-results app/build/reports/androidTests
adb reconnect || true
wait_for_android_services
adb shell am force-stop androidx.test.orchestrator || true
adb shell pm clear androidx.test.orchestrator || true
adb shell pm clear androidx.test.services || true
adb shell pm clear sa.hulksa.player.dev || true
sleep 10

if run_instrumentation; then
  capture_device_evidence "$runtime_evidence/success-after-infrastructure-retry"
  exit 0
fi
retry_status=$?
capture_device_evidence "$runtime_evidence/attempts/attempt-2"
exit "$retry_status"
