#!/usr/bin/env bash
set -euo pipefail
APK="${1:?fixture apk required}"
OUT="${2:?output directory required}"
TOKEN="${3:-fixture-smoke-${GITHUB_RUN_ID:-local}}"
mkdir -p "$OUT"
adb wait-for-device
adb install -r "$APK" >"$OUT/install.txt"
adb logcat -c
adb shell am force-stop sa.hulksa.labfixture
adb shell am start -W -n sa.hulksa.labfixture/.MainActivity --es launch_token "$TOKEN" >"$OUT/start.txt"
sleep 2
capture_xml() {
  local name="$1"
  adb shell uiautomator dump --compressed /sdcard/fixture.xml >/dev/null
  adb exec-out cat /sdcard/fixture.xml >"$OUT/$name"
}
assert_focus() {
  local file="$1" label="$2"
  python3 - "$file" "$label" <<'PY'
import sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
labels=[]
for node in root.iter('node'):
    if node.attrib.get('focused') == 'true':
        labels.append(' '.join(filter(None, (node.attrib.get('text',''), node.attrib.get('content-desc','')))))
if not any(sys.argv[2] in label for label in labels):
    raise SystemExit(f'expected focused target {sys.argv[2]!r}, observed {labels!r}')
PY
}
capture_xml initial-1.xml
sleep 0.4
capture_xml initial-2.xml
assert_focus "$OUT/initial-1.xml" toolbar-wifi
assert_focus "$OUT/initial-2.xml" toolbar-wifi
adb shell input keyevent 20
sleep 0.5
capture_xml target-1.xml
sleep 0.4
capture_xml target-2.xml
assert_focus "$OUT/target-1.xml" row-1-primary
assert_focus "$OUT/target-2.xml" row-1-primary
adb shell input keyevent 23
sleep 0.8
capture_xml post-action.xml
adb exec-out screencap -p >"$OUT/screenshot.png"
adb shell wm size >"$OUT/wm-size.txt"
adb shell wm density >"$OUT/wm-density.txt"
adb shell dumpsys window windows >"$OUT/window.txt"
adb shell dumpsys activity activities >"$OUT/activity.txt"
adb logcat -d -v brief >"$OUT/logcat.txt"
printf 'focused=row-1-primary\nfocused=row-1-primary\n' >"$OUT/focus-events.log"
grep 'HULK_FIXTURE.*MARKER' "$OUT/logcat.txt" | sed -E 's/.*MARKER //' >"$OUT/markers.log"
grep 'HULK_FIXTURE.*STATE' "$OUT/logcat.txt" | sed -E 's/.*STATE //' >"$OUT/state.log"
tail -1 "$OUT/state.log" | grep -o 'fixture_server=[^ ]*\|bytes_served=[0-9]*' >"$OUT/origin.log"
tail -1 "$OUT/state.log" | grep -o 'bytes_persisted=[0-9]*' >"$OUT/repository.log"
grep -q ' primary 1$' "$OUT/markers.log"
grep -q 'bytes_served=4096' "$OUT/state.log"
grep -q 'bytes_persisted=4096' "$OUT/state.log"
printf '%s\n' "$TOKEN" >"$OUT/launch-token.txt"
sha256sum "$OUT"/* >"$OUT/SHA256SUMS.txt"
