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

capture_xml() {
  local name="$1"
  local remote=/sdcard/hulk-fixture-window.xml
  local output="$OUT/$name"
  local dump_log="$OUT/${name%.xml}.dump.txt"
  adb shell rm -f "$remote" >/dev/null 2>&1 || true
  set +e
  adb shell uiautomator dump --compressed "$remote" >"$dump_log" 2>&1
  local status=$?
  set -e
  if [[ "$status" -ne 0 ]]; then
    return 1
  fi
  local temporary="$output.tmp"
  if ! adb exec-out cat "$remote" >"$temporary" 2>>"$dump_log"; then
    rm -f "$temporary"
    return 1
  fi
  if ! grep -q '<hierarchy' "$temporary"; then
    rm -f "$temporary"
    return 1
  fi
  mv "$temporary" "$output"
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

wait_for_stable_focus() {
  local expected="$1" prefix="$2" output_one="$3" output_two="$4" guard_dialogs="$5"
  local deadline=$((SECONDS + 15))
  local read_number=0
  local -a evidence=()
  local report="$OUT/${prefix}-stability.json"
  while ((SECONDS < deadline)); do
    read_number=$((read_number + 1))
    local name="${prefix}-probe-${read_number}.xml"
    if capture_xml "$name"; then
      local path="$OUT/$name"
      evidence+=("$path")
      if [[ "$guard_dialogs" == "true" ]]; then
        set +e
        python3 qa/lab_verifier/runtime_guard.py "$path" --out "$OUT/failure-classification.json"
        local guard_status=$?
        set -e
        if [[ "$guard_status" -ne 0 ]]; then
          return "$guard_status"
        fi
      fi
      set +e
      python3 qa/lab_verifier/focus_stability.py \
        "${evidence[@]}" \
        --expected "$expected" \
        --consecutive 2 \
        --out "$report" >/dev/null
      local stability_status=$?
      set -e
      if [[ "$stability_status" -eq 0 ]]; then
        local count=${#evidence[@]}
        cp "${evidence[$((count - 2))]}" "$OUT/$output_one"
        cp "${evidence[$((count - 1))]}" "$OUT/$output_two"
        return 0
      fi
      if [[ "$stability_status" -eq 20 ]]; then
        return 20
      fi
    fi
    sleep 0.25
  done
  python3 - "$report" "$expected" "$read_number" <<'PY'
import json,sys
path,expected,reads=sys.argv[1],sys.argv[2],int(sys.argv[3])
try:
    payload=json.load(open(path, encoding='utf-8'))
except Exception:
    payload={}
payload.update({
    'classification':'BLOCKED',
    'code':'FOCUS_STABILITY_TIMEOUT',
    'expected':expected,
    'reads_attempted':reads,
    'stable':False,
})
json.dump(payload, open(path,'w',encoding='utf-8'), ensure_ascii=False, sort_keys=True, indent=2)
open(path,'a',encoding='utf-8').write('\n')
PY
  return 20
}

set +e
wait_for_stable_focus toolbar-wifi initial initial-1.xml initial-2.xml true
initial_status=$?
set -e
if [[ "$initial_status" -ne 0 ]]; then
  capture_failure_evidence
  exit "$initial_status"
fi

adb shell input keyevent 20
set +e
wait_for_stable_focus row-1-primary target target-1.xml target-2.xml false
target_status=$?
set -e
if [[ "$target_status" -ne 0 ]]; then
  capture_failure_evidence
  exit "$target_status"
fi

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
