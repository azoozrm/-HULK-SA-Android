#!/usr/bin/env bash
set -Eeuo pipefail

profile="${1:?profile id is required}"
width="${2:?width px is required}"
height="${3:?height px is required}"
density="${4:?density dpi is required}"
font_scale="${5:?font scale is required}"
rotation="${6:?rotation is required}"
locale="${7:?BCP-47 locale is required}"
test_class="${8:-sa.hulksa.player.compatibilityv2.CompatibilityV2InstrumentationTest}"
out="${9:-${EVIDENCE_ROOT:-build/compatibility-v2/runtime/$profile}}"
cutout_mode="${10:-none}"
navigation_mode="${11:-default}"
expected_device_class="${12:-UNSPECIFIED}"
expected_input_mode="${13:-UNSPECIFIED}"
expected_width_class="${14:-UNSPECIFIED}"
expected_height_class="${15:-UNSPECIFIED}"
expected_orientation="${16:-UNSPECIFIED}"
package="sa.hulksa.player.dev"
mkdir -p "$out"

is_tv=false
launcher_category="android.intent.category.LAUNCHER"
launcher_activity="sa.hulksa.player.MainActivity"
if adb shell pm list features 2>/dev/null | tr -d '\r' | grep -q '^feature:android.software.leanback$'; then
  is_tv=true
  launcher_category="android.intent.category.LEANBACK_LAUNCHER"
  launcher_activity="sa.hulksa.player.TvMainActivity"
fi
launcher_component="${package}/${launcher_activity}"

wait_for_package_installer_ready() {
  local evidence="$out/INSTALL-READINESS.txt"
  local deadline=$((SECONDS + 300))
  local stable=0
  local attempt=0
  : > "$evidence"

  while (( SECONDS < deadline )); do
    attempt=$((attempt + 1))
    local boot activity_service package_service settings_service window_service
    local framework_path package_list data_ready settings_ready

    boot="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    activity_service="$(adb shell service check activity 2>/dev/null | tr -d '\r' || true)"
    package_service="$(adb shell service check package 2>/dev/null | tr -d '\r' || true)"
    settings_service="$(adb shell service check settings 2>/dev/null | tr -d '\r' || true)"
    window_service="$(adb shell service check window 2>/dev/null | tr -d '\r' || true)"
    framework_path="$(adb shell pm path android 2>/dev/null | tr -d '\r' || true)"
    package_list="$(adb shell cmd package list packages --user 0 android 2>/dev/null | tr -d '\r' || true)"
    data_ready=false
    settings_ready=false
    adb shell df /data >/dev/null 2>&1 && data_ready=true
    adb shell settings get system font_scale >/dev/null 2>&1 && settings_ready=true

    {
      echo "attempt=$attempt"
      echo "boot_completed=$boot"
      echo "activity_service=${activity_service//$'\n'/ | }"
      echo "package_service=${package_service//$'\n'/ | }"
      echo "settings_service=${settings_service//$'\n'/ | }"
      echo "window_service=${window_service//$'\n'/ | }"
      echo "framework_path=${framework_path//$'\n'/ | }"
      echo "package_list=${package_list//$'\n'/ | }"
      echo "data_ready=$data_ready"
      echo "settings_ready=$settings_ready"
    } >> "$evidence"

    if [[ "$boot" == "1" ]] &&
       [[ "$activity_service" == *"found"* ]] &&
       [[ "$package_service" == *"found"* ]] &&
       [[ "$settings_service" == *"found"* ]] &&
       [[ "$window_service" == *"found"* ]] &&
       [[ "$framework_path" == package:* ]] &&
       [[ "$package_list" == *"package:android"* ]] &&
       [[ "$data_ready" == true ]] &&
       [[ "$settings_ready" == true ]]; then
      stable=$((stable + 1))
      echo "stable_readings=$stable" >> "$evidence"
      if (( stable >= 3 )); then
        break
      fi
    else
      stable=0
      echo "stable_readings=0" >> "$evidence"
    fi
    sleep 2
  done

  if (( stable < 3 )); then
    {
      echo "result=BLOCKED"
      echo "failure_reason=package installer services did not reach three consecutive stable probes"
    } >> "$evidence"
    return 3
  fi

  set +e
  broadcast_idle_output="$(timeout 90 adb shell am wait-for-broadcast-idle 2>&1)"
  broadcast_idle_status=$?
  set -e
  {
    echo "broadcast_idle_status=$broadcast_idle_status"
    echo "broadcast_idle_output=${broadcast_idle_output//$'\n'/ | }"
    echo "result=PASS"
    echo "installer_ready=true"
  } >> "$evidence"
}

install_apk_non_streaming() {
  local label="$1"
  local apk="$2"
  local evidence="$out/INSTALLATION.txt"
  local output status

  set +e
  output="$(adb install --no-streaming -r "$apk" 2>&1)"
  status=$?
  set -e
  {
    echo "label=$label"
    echo "apk=$apk"
    echo "mode=adb-no-streaming"
    echo "status=$status"
    echo "output=${output//$'\n'/ | }"
  } >> "$evidence"
  if [[ "$status" -ne 0 ]]; then
    echo "result=FAIL" >> "$evidence"
    return "$status"
  fi
}

wait_for_installed_package_registration() {
  local evidence="$out/PACKAGE-REGISTRATION.txt"
  local dump="$out/INSTALLED-PACKAGE-DUMP.txt"
  local deadline=$((SECONDS + 180))
  local stable=0
  local attempt=0
  : > "$evidence"

  while (( SECONDS < deadline )); do
    attempt=$((attempt + 1))
    local package_path user_packages resolve_output dump_has_activity
    package_path="$(adb shell pm path "$package" 2>/dev/null | tr -d '\r' || true)"
    user_packages="$(adb shell pm list packages --user 0 "$package" 2>/dev/null | tr -d '\r' || true)"
    resolve_output="$(adb shell cmd package resolve-activity --brief \
      -a android.intent.action.MAIN \
      -c "$launcher_category" \
      "$package" 2>/dev/null | tr -d '\r' || true)"
    adb shell dumpsys package "$package" > "$dump" 2>&1 || true
    dump_has_activity=false
    grep -Fq "$launcher_activity" "$dump" && dump_has_activity=true

    {
      echo "attempt=$attempt"
      echo "package_path=${package_path//$'\n'/ | }"
      echo "user_packages=${user_packages//$'\n'/ | }"
      echo "launcher_category=$launcher_category"
      echo "launcher_activity=$launcher_activity"
      echo "launcher_component=$launcher_component"
      echo "resolve_output=${resolve_output//$'\n'/ | }"
      echo "dump_has_activity=$dump_has_activity"
    } >> "$evidence"

    if [[ "$package_path" == package:* ]] &&
       [[ "$user_packages" == *"package:$package"* ]] &&
       [[ "$resolve_output" == *"$package"* ]] &&
       [[ "$dump_has_activity" == true ]]; then
      stable=$((stable + 1))
      echo "stable_readings=$stable" >> "$evidence"
      if (( stable >= 3 )); then
        break
      fi
    else
      stable=0
      echo "stable_readings=0" >> "$evidence"
    fi
    sleep 2
  done

  if (( stable < 3 )); then
    {
      echo "result=BLOCKED"
      echo "failure_reason=installed package did not expose its canonical launcher component after installation"
    } >> "$evidence"
    return 3
  fi

  set +e
  broadcast_idle_output="$(timeout 90 adb shell am wait-for-broadcast-idle 2>&1)"
  broadcast_idle_status=$?
  set -e
  {
    echo "broadcast_idle_status=$broadcast_idle_status"
    echo "broadcast_idle_output=${broadcast_idle_output//$'\n'/ | }"
    echo "result=PASS"
    echo "package_registered=true"
  } >> "$evidence"
}

bash quality/compatibility-v2/configure_emulator_profile.sh \
  "$profile" "$width" "$height" "$density" "$font_scale" "$rotation" "$locale" \
  "$out/PROFILE-CONFIG.txt" \
  "$cutout_mode" "$navigation_mode" "$expected_device_class" "$expected_input_mode" \
  "$expected_width_class" "$expected_height_class" "$expected_orientation"

wait_for_package_installer_ready
: > "$out/INSTALLATION.txt"
install_apk_non_streaming application "${APP_APK:-build/compatibility-v2/binaries/app-debug.apk}"
install_apk_non_streaming instrumentation "${TEST_APK:-build/compatibility-v2/binaries/app-debug-androidTest.apk}"
echo "result=PASS" >> "$out/INSTALLATION.txt"
wait_for_installed_package_registration

locale_mode="$(sed -n 's/^locale_mode=//p' "$out/PROFILE-CONFIG.txt" | tail -1)"
actual_system_locale="$(adb shell getprop persist.sys.locale | tr -d '\r')"
{
  echo "requested_locale=$locale"
  echo "locale_mode=$locale_mode"
  echo "actual_system_locale=$actual_system_locale"
} > "$out/APPLICATION-LOCALE.txt"

if [[ "$locale_mode" == "application-deferred" ]]; then
  set +e
  set_output="$(adb shell cmd locale set-app-locales "$package" --user 0 --locales "$locale" 2>&1)"
  set_status=$?
  get_output="$(adb shell cmd locale get-app-locales "$package" --user 0 2>&1)"
  get_status=$?
  set -e
  {
    echo "set_app_locales_status=$set_status"
    echo "set_app_locales_output=${set_output//$'\n'/ | }"
    echo "get_app_locales_status=$get_status"
    echo "actual_app_locales=${get_output//$'\n'/ | }"
  } >> "$out/APPLICATION-LOCALE.txt"
  if [[ "$set_status" -ne 0 || "$get_status" -ne 0 || "$get_output" != *"$locale"* ]]; then
    {
      echo "result=BLOCKED"
      echo "failure_reason=unable to apply and verify requested app locale on non-root image"
    } >> "$out/APPLICATION-LOCALE.txt"
    exit 3
  fi
else
  echo "actual_app_locales=system-inherited" >> "$out/APPLICATION-LOCALE.txt"
  if [[ "$actual_system_locale" != "$locale" ]]; then
    {
      echo "result=BLOCKED"
      echo "failure_reason=verified system locale does not match requested locale"
    } >> "$out/APPLICATION-LOCALE.txt"
    exit 3
  fi
fi

echo "locale_verified=true" >> "$out/APPLICATION-LOCALE.txt"
echo "result=PASS" >> "$out/APPLICATION-LOCALE.txt"
adb logcat -c
bash quality/compatibility-v2/collect_runtime_evidence.sh "$profile" "$out" "$test_class"
