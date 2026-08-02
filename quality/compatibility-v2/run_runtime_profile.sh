#!/usr/bin/env bash
set -euo pipefail

profile="${1:?profile id is required}"
width="${2:?width px is required}"
height="${3:?height px is required}"
density="${4:?density dpi is required}"
font_scale="${5:?font scale is required}"
rotation="${6:?rotation is required}"
locale="${7:?BCP-47 locale is required}"
test_class="${8:-sa.hulksa.player.compatibilityv2.CompatibilityV2InstrumentationTest}"
out="${9:-${EVIDENCE_ROOT:-build/compatibility-v2/runtime/$profile}}"
package="sa.hulksa.player.dev"
mkdir -p "$out"

bash quality/compatibility-v2/configure_emulator_profile.sh \
  "$profile" "$width" "$height" "$density" "$font_scale" "$rotation" "$locale" \
  "$out/PROFILE-CONFIG.txt"

adb install -r "${APP_APK:-build/compatibility-v2/binaries/app-debug.apk}"
adb install -r "${TEST_APK:-build/compatibility-v2/binaries/app-debug-androidTest.apk}"

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
