#!/usr/bin/env bash
set -euo pipefail

APK="$1"
DEVICE="$2"
IS_TV="$3"
OUT="${4:-qa-results/$DEVICE}"
PACKAGE="sa.hulksa.player.dev"
ACTIVITY="sa.hulksa.player.qa.QaActivity"
mkdir -p "$OUT"

adb wait-for-device
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true
adb shell settings put secure show_ime_with_hard_keyboard 1 || true
adb install -r -t "$APK"
adb shell pm clear "$PACKAGE" >/dev/null || true

{
  echo "# Device"
  echo "name=$DEVICE"
  echo "is_tv=$IS_TV"
  adb shell wm size
  adb shell wm density
  adb shell getprop ro.build.version.release
  adb shell getprop ro.product.model
  adb shell getprop ro.build.characteristics
  adb shell pm list features
} > "$OUT/device.txt" 2>&1

if [[ "$IS_TV" == "true" ]]; then
  adb shell settings put system accelerometer_rotation 0 || true
  adb shell settings put system user_rotation 1 || true
else
  if [[ "$DEVICE" == *tablet* ]]; then
    adb shell settings put system accelerometer_rotation 0 || true
    adb shell settings put system user_rotation 1 || true
  else
    adb shell settings put system accelerometer_rotation 0 || true
    adb shell settings put system user_rotation 0 || true
  fi
fi

SCENARIOS=(
  login home live movies series favorites search downloads settings
  movie series_details
  player_vod player_live
  player_vod_panel_audio player_vod_panel_subtitles player_vod_panel_speed
  player_vod_panel_resize player_vod_panel_quality player_vod_panel_servers
  player_live_panel_audio player_live_panel_resize
  player_next_episode
)

capture() {
  local scenario="$1"
  sleep 1.2
  adb exec-out screencap -p > "$OUT/$scenario.png"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$OUT/$scenario.xml" >/dev/null 2>&1 || true
  adb shell dumpsys window windows > "$OUT/$scenario.window.txt" 2>&1 || true
  adb shell dumpsys input_method > "$OUT/$scenario.ime.txt" 2>&1 || true
  adb logcat -d -v threadtime > "$OUT/$scenario.logcat.txt" 2>&1 || true
}

start_scenario() {
  local scenario="$1"
  adb shell am force-stop "$PACKAGE"
  adb logcat -c || true
  adb shell am start -W -n "$PACKAGE/$ACTIVITY" --es scenario "$scenario" --ez isTv "$IS_TV" > "$OUT/$scenario.start.txt" 2>&1
  capture "$scenario"
}

for scenario in "${SCENARIOS[@]}"; do
  start_scenario "$scenario"

  if [[ "$IS_TV" == "true" && "$scenario" =~ ^(home|live|movies|series|favorites|search|downloads|settings)$ ]]; then
    {
      echo "scenario=$scenario"
      for key in 22 22 20 21 19 23 4; do
        adb shell input keyevent "$key" || true
        sleep 0.15
        adb shell dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp' || true
      done
    } > "$OUT/$scenario.focus-trace.txt" 2>&1
  fi
done

start_scenario login_ime_base
cp "$OUT/login_ime_base.xml" "$OUT/login-ime-working.xml" 2>/dev/null || true
USER_CENTER=$(python3 qa/emulator/node-center.py "$OUT/login-ime-working.xml" --class-contains EditText --index 0 2>/dev/null || true)
PASS_CENTER=$(python3 qa/emulator/node-center.py "$OUT/login-ime-working.xml" --class-contains EditText --index 1 2>/dev/null || true)
if [[ -n "$USER_CENTER" ]]; then
  adb shell input tap $USER_CENTER
  sleep 0.5
  adb shell input text qauser
fi
adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
adb pull /sdcard/window.xml "$OUT/login-ime-password-source.xml" >/dev/null 2>&1 || true
if [[ -z "$PASS_CENTER" ]]; then
  PASS_CENTER=$(python3 qa/emulator/node-center.py "$OUT/login-ime-password-source.xml" --class-contains EditText --index 1 2>/dev/null || true)
fi
if [[ -n "$PASS_CENTER" ]]; then
  adb shell input tap $PASS_CENTER
  sleep 0.4
  adb shell input text qapassword
fi
adb shell dumpsys input_method > "$OUT/login-ime-open.txt" 2>&1 || true
OPENED=false
if grep -Eqi 'mInputShown=true|isInputViewShown\(\)=true|mIsInputViewShown=true' "$OUT/login-ime-open.txt"; then OPENED=true; fi
adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
adb pull /sdcard/window.xml "$OUT/login-ime-button-source.xml" >/dev/null 2>&1 || true
BUTTON_CENTER=$(python3 qa/emulator/node-center.py "$OUT/login-ime-button-source.xml" --text-contains 'الدخول' --index 0 2>/dev/null || true)
if [[ -n "$BUTTON_CENTER" ]]; then
  adb shell input tap $BUTTON_CENTER
else
  adb shell input keyevent 20 || true
  adb shell input keyevent 20 || true
  adb shell input keyevent 20 || true
  adb shell input keyevent 23 || true
fi
sleep 0.7
adb shell dumpsys input_method > "$OUT/login-ime-after-button.txt" 2>&1 || true
SHOWN_AFTER=false
if grep -Eqi 'mInputShown=true|isInputViewShown\(\)=true|mIsInputViewShown=true' "$OUT/login-ime-after-button.txt"; then SHOWN_AFTER=true; fi
cat > "$OUT/login-ime-check.json" <<EOF
{"opened_after_username": $OPENED, "shown_after_login_button": $SHOWN_AFTER}
EOF

python3 qa/emulator/analyze-ui.py "$OUT" "$DEVICE"
