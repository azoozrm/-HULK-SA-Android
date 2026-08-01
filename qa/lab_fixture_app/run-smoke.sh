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
capture_failure_evidence() {
  adb exec-out screencap -p >"$OUT/failure-screenshot.png" || true
  adb shell wm size >"$OUT/failure-wm-size.txt" || true
  adb shell wm density >"$OUT/failure-wm-density.txt" || true
  adb shell dumpsys window windows >"$OUT/failure-window.txt" || true
  adb shell dumpsys activity activities >"$OUT/failure-activity.txt" || true
  adb logcat -d -v brief >"$OUT/failure-logcat.txt" || true
  python3 qa/lab_verifier/write_sha256_manifest.py "$OUT" --output SHA256SUMS.txt || true
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
set +e
python3 qa/lab_verifier/runtime_guard.py "$OUT/initial-2.xml" --out "$OUT/failure-classification.json"
guard_status=$?
set -e
if [[ "$guard_status" -ne 0 ]]; then
  capture_failure_evidence
  exit "$guard_status"
fi
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
python3 - "$OUT/target-1.xml" "$OUT/target-2.xml" "$OUT/focus-events.log" <<'PY'
import sys, xml.etree.ElementTree as ET
output=[]
for filename in sys.argv[1:3]:
    root=ET.parse(filename).getroot()
    labels=[]
    for node in root.iter('node'):
        if node.attrib.get('focused') == 'true':
            label=' '.join(filter(None, (node.attrib.get('text',''), node.attrib.get('content-desc','')))).strip()
            if label:
                labels.append(label)
    if len(labels) != 1:
        raise SystemExit(f'exactly one focused target required in {filename}: {labels!r}')
    output.append(f'focused={labels[0]}')
open(sys.argv[3], 'w', encoding='utf-8').write('\n'.join(output) + '\n')
PY
grep 'HULK_FIXTURE.*MARKER' "$OUT/logcat.txt" | sed -E 's/.*MARKER //' >"$OUT/markers.log"
grep 'HULK_FIXTURE.*STATE' "$OUT/logcat.txt" | sed -E 's/.*STATE //' >"$OUT/state.log"
tail -1 "$OUT/state.log" | grep -o 'fixture_server=[^ ]*\|bytes_served=[0-9]*' >"$OUT/origin.log"
tail -1 "$OUT/state.log" | grep -o 'bytes_persisted=[0-9]*' >"$OUT/repository.log"
grep -q ' primary 1$' "$OUT/markers.log"
grep -q 'bytes_served=4096' "$OUT/state.log"
grep -q 'bytes_persisted=4096' "$OUT/state.log"
printf '%s\n' "$TOKEN" >"$OUT/launch-token.txt"
python3 qa/lab_verifier/write_sha256_manifest.py "$OUT" --output SHA256SUMS.txt
