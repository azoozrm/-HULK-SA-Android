#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -ne 7 ]]; then
  echo "usage: $0 BASELINE_APK CANDIDATE_APK TEST_APK APP_PACKAGE TEST_PACKAGE TEST_RUNNER EVIDENCE_ROOT" >&2
  exit 64
fi

baseline_apk="$1"
candidate_apk="$2"
test_apk="$3"
app_package="$4"
test_package="$5"
test_runner="$6"
evidence_root="$7"
main_activity="sa.hulksa.player.MainActivity"
test_class="sa.hulksa.player.compatibilityv2.CompatibilityV2InstrumentationTest"
sentinel="hulk-sa-install-over-c291-preserved"

mkdir -p "$evidence_root"

collect_exit_evidence() {
  local exit_status="$?"
  set +e
  {
    echo "exit_status=$exit_status"
    if [[ "$exit_status" -eq 0 ]]; then
      echo "result=PASS"
    else
      echo "result=FAIL"
    fi
  } > "$evidence_root/INSTALL-OVER-RESULT.txt"

  if adb get-state >/dev/null 2>&1; then
    adb shell dumpsys package "$app_package" > "$evidence_root/FINAL-PACKAGE.txt" 2>&1 || true
    adb shell dumpsys activity activities > "$evidence_root/FINAL-ACTIVITY.txt" 2>&1 || true
    adb shell dumpsys window windows > "$evidence_root/FINAL-WINDOWS.txt" 2>&1 || true
    adb logcat -d -v threadtime > "$evidence_root/logcat.txt" 2>&1 || true
    adb shell uiautomator dump /sdcard/install-over-window.xml > "$evidence_root/UIAUTOMATOR-DUMP.txt" 2>&1 || true
    adb pull /sdcard/install-over-window.xml "$evidence_root/window.xml" >/dev/null 2>&1 || true
    adb exec-out screencap -p > "$evidence_root/full-window.png" 2>/dev/null || true
  fi

  exit "$exit_status"
}
trap collect_exit_evidence EXIT

for required in "$baseline_apk" "$candidate_apk" "$test_apk"; do
  test -s "$required"
done

adb wait-for-device
stable=0
for attempt in $(seq 1 90); do
  boot_completed="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  if [[ "$boot_completed" == "1" ]] \
    && adb shell cmd package list packages >/dev/null 2>&1 \
    && adb shell cmd activity get-config >/dev/null 2>&1 \
    && adb shell cmd settings get global device_provisioned >/dev/null 2>&1; then
    stable=$((stable + 1))
    if [[ "$stable" -ge 3 ]]; then
      break
    fi
  else
    stable=0
  fi
  sleep 2
done
if [[ "$stable" -lt 3 ]]; then
  echo "Android framework did not become stable" >&2
  exit 70
fi
adb shell cmd activity idle-maintenance >/dev/null 2>&1 || true
adb shell am wait-for-broadcast-idle >/dev/null 2>&1 || true

adb install --no-streaming "$baseline_apk" | tee "$evidence_root/BASELINE-INSTALL.txt"
grep -Fq "Success" "$evidence_root/BASELINE-INSTALL.txt"

baseline_registration_stable=0
for attempt in $(seq 1 45); do
  if adb shell cmd package resolve-activity --brief \
      -a android.intent.action.MAIN \
      -c android.intent.category.LAUNCHER \
      "$app_package" 2>/dev/null | grep -Fq "$main_activity"; then
    baseline_registration_stable=$((baseline_registration_stable + 1))
    if [[ "$baseline_registration_stable" -ge 3 ]]; then
      break
    fi
  else
    baseline_registration_stable=0
  fi
  sleep 2
done
if [[ "$baseline_registration_stable" -lt 3 ]]; then
  echo "Baseline launcher registration did not stabilize" >&2
  exit 71
fi

adb shell pm grant "$app_package" android.permission.POST_NOTIFICATIONS \
  > "$evidence_root/BASELINE-NOTIFICATION-PERMISSION.txt" 2>&1 || true
adb shell am start -W -n "$app_package/$main_activity" | tee "$evidence_root/BASELINE-LAUNCH.txt"
grep -Fq "Status: ok" "$evidence_root/BASELINE-LAUNCH.txt"

app_data_dir="$(adb shell run-as "$app_package" pwd | tr -d '\r')"
if [[ "$app_data_dir" != /data/* || "$app_data_dir" != *"/$app_package" ]]; then
  echo "Unexpected run-as data directory: $app_data_dir" >&2
  exit 73
fi
sentinel_path="$app_data_dir/files/install-over-sentinel.txt"
printf 'app_data_dir=%s\nsentinel_path=%s\n' \
  "$app_data_dir" "$sentinel_path" > "$evidence_root/APP-DATA-PATH.txt"
adb shell run-as "$app_package" mkdir -p "$app_data_dir/files"
printf '%s' "$sentinel" | adb shell run-as "$app_package" tee "$sentinel_path" >/dev/null
baseline_value="$(adb shell run-as "$app_package" cat "$sentinel_path" | tr -d '\r')"
test "$baseline_value" = "$sentinel"
printf 'expected=%s\nactual=%s\nresult=PASS\n' \
  "$sentinel" "$baseline_value" > "$evidence_root/BASELINE-DATA.txt"
adb shell dumpsys package "$app_package" > "$evidence_root/BASELINE-PACKAGE.txt"

adb install --no-streaming -r "$candidate_apk" | tee "$evidence_root/CANDIDATE-UPDATE.txt"
grep -Fq "Success" "$evidence_root/CANDIDATE-UPDATE.txt"

candidate_registration_stable=0
for attempt in $(seq 1 45); do
  if adb shell cmd package resolve-activity --brief \
      -a android.intent.action.MAIN \
      -c android.intent.category.LAUNCHER \
      "$app_package" 2>/dev/null | grep -Fq "$main_activity"; then
    candidate_registration_stable=$((candidate_registration_stable + 1))
    if [[ "$candidate_registration_stable" -ge 3 ]]; then
      break
    fi
  else
    candidate_registration_stable=0
  fi
  sleep 2
done
if [[ "$candidate_registration_stable" -lt 3 ]]; then
  echo "Candidate launcher registration did not stabilize" >&2
  exit 72
fi

preserved="$(adb shell run-as "$app_package" cat "$sentinel_path" | tr -d '\r')"
test "$preserved" = "$sentinel"
printf 'expected=%s\nactual=%s\nresult=PASS\n' \
  "$sentinel" "$preserved" > "$evidence_root/DATA-PRESERVATION.txt"

adb install --no-streaming -r "$test_apk" | tee "$evidence_root/TEST-INSTALL.txt"
grep -Fq "Success" "$evidence_root/TEST-INSTALL.txt"
adb shell pm grant "$app_package" android.permission.POST_NOTIFICATIONS \
  > "$evidence_root/NOTIFICATION-PERMISSION.txt" 2>&1 || true
adb shell am force-stop "$app_package"
adb shell am start -W -n "$app_package/$main_activity" | tee "$evidence_root/CANDIDATE-LAUNCH.txt"
grep -Fq "Status: ok" "$evidence_root/CANDIDATE-LAUNCH.txt"

set +e
adb shell am instrument -w -r \
  -e class "$test_class" \
  "$test_package/$test_runner" | tee "$evidence_root/INSTRUMENTATION-RAW.txt"
instrument_status=${PIPESTATUS[0]}
set -e

python3 candidate/quality/compatibility-v2/instrumentation_to_junit.py \
  "$evidence_root/INSTRUMENTATION-RAW.txt" \
  "$evidence_root/INSTRUMENTATION.xml" \
  --process-status "$instrument_status"

test "$instrument_status" -eq 0
grep -Eq 'tests="7"' "$evidence_root/INSTRUMENTATION.xml"
grep -Eq 'failures="0"' "$evidence_root/INSTRUMENTATION.xml"

final_value="$(adb shell run-as "$app_package" cat "$sentinel_path" | tr -d '\r')"
test "$final_value" = "$sentinel"
printf 'expected=%s\nactual=%s\nresult=PASS\n' \
  "$sentinel" "$final_value" > "$evidence_root/POST-INSTRUMENTATION-DATA.txt"

adb shell dumpsys package "$app_package" > "$evidence_root/CANDIDATE-PACKAGE.txt"
adb shell dumpsys activity activities > "$evidence_root/ACTIVITY-AFTER-UPDATE.txt"

adb shell am force-stop "$app_package"
adb shell am start -W -n "$app_package/$main_activity" | tee "$evidence_root/FINAL-LAUNCH.txt"
grep -Fq "Status: ok" "$evidence_root/FINAL-LAUNCH.txt"
foreground_stable=0
for attempt in $(seq 1 30); do
  adb shell dumpsys activity activities > "$evidence_root/FINAL-FOREGROUND-PROBE.txt"
  if grep -Eq "mResumedActivity:.*${app_package//./\.}/$main_activity|topResumedActivity=.*${app_package//./\.}/$main_activity" \
      "$evidence_root/FINAL-FOREGROUND-PROBE.txt"; then
    foreground_stable=$((foreground_stable + 1))
    if [[ "$foreground_stable" -ge 3 ]]; then
      break
    fi
  else
    foreground_stable=0
  fi
  sleep 1
done
if [[ "$foreground_stable" -lt 3 ]]; then
  echo "Candidate application did not remain foreground after instrumentation" >&2
  exit 74
fi
sleep 2
{
  echo "package=$app_package"
  echo "activity=$main_activity"
  echo "stable_probes=$foreground_stable"
  echo "result=PASS"
} > "$evidence_root/FINAL-FOREGROUND.txt"
