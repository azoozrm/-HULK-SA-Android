#!/usr/bin/env bash
set -Eeuo pipefail

profile="${1:?profile id is required}"
out="${2:?evidence directory is required}"
target_package="${3:-sa.hulksa.player.dev}"
adb_bin="${ADB_BIN:-adb}"
max_probes="${PREFLIGHT_MAX_PROBES:-8}"
probe_sleep="${PREFLIGHT_SLEEP_SECONDS:-1}"
evidence="$out/SYSTEM-DIALOG-PREFLIGHT.txt"
hierarchy="$out/SYSTEM-DIALOG-PREFLIGHT.xml"
remote_hierarchy="/sdcard/hulk-sa-${profile}-system-dialog-preflight.xml"
mkdir -p "$out"
: > "$evidence"

record() {
  printf '%s\n' "$1" >> "$evidence"
}

record "profile=$profile"
record "target_package=$target_package"
record "scope=foldable-pixel-launcher-anr-only"

if [[ "$profile" != "foldable-unfolded-api35" ]]; then
  record "applicable=false"
  record "action=none"
  record "result=PASS"
  exit 0
fi

record "applicable=true"

dump_hierarchy() {
  local dump_status pull_status
  set +e
  "$adb_bin" shell uiautomator dump "$remote_hierarchy" >/dev/null 2>&1
  dump_status=$?
  "$adb_bin" pull "$remote_hierarchy" "$hierarchy" >/dev/null 2>&1
  pull_status=$?
  set -e
  record "dump_status=$dump_status"
  record "pull_status=$pull_status"
  [[ "$dump_status" -eq 0 && "$pull_status" -eq 0 && -s "$hierarchy" ]]
}

launcher_title_present=false
close_button_present=false
target_package_present=false
exact_dialog_present=false

for ((probe = 1; probe <= max_probes; probe++)); do
  record "probe=$probe"
  if ! dump_hierarchy; then
    record "failure_reason=unable to capture foldable system hierarchy before instrumentation"
    record "result=BLOCKED"
    exit 3
  fi

  launcher_title_present=false
  close_button_present=false
  target_package_present=false
  grep -Fq 'Pixel Launcher' "$hierarchy" && launcher_title_present=true
  grep -Fq 'resource-id="android:id/aerr_close"' "$hierarchy" && close_button_present=true
  grep -Fq "package=\"$target_package\"" "$hierarchy" && target_package_present=true
  record "launcher_title_present=$launcher_title_present"
  record "close_button_present=$close_button_present"
  record "target_package_present=$target_package_present"

  if [[ "$launcher_title_present" == true || "$close_button_present" == true ]]; then
    if [[ "$launcher_title_present" != true || "$close_button_present" != true ]]; then
      record "failure_reason=ambiguous system error dialog; refusing broad dismissal"
      record "result=BLOCKED"
      exit 3
    fi
    if [[ "$target_package_present" == true ]]; then
      record "failure_reason=target application is present in the dialog hierarchy; refusing dismissal"
      record "result=BLOCKED"
      exit 3
    fi
    if ! grep -Fq 'package="android"' "$hierarchy"; then
      record "failure_reason=launcher error dialog is not owned by the Android system package"
      record "result=BLOCKED"
      exit 3
    fi
    exact_dialog_present=true
    break
  fi

  if (( probe < max_probes )); then
    sleep "$probe_sleep"
  fi
done

if [[ "$exact_dialog_present" != true ]]; then
  record "launcher_anr_present=false"
  record "action=none"
  record "result=PASS"
  exit 0
fi

coordinates="$(python3 - "$hierarchy" <<'PY'
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

path = Path(sys.argv[1])
root = ET.fromstring(path.read_text(encoding='utf-8'))
for node in root.iter('node'):
    if node.attrib.get('resource-id') != 'android:id/aerr_close':
        continue
    match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
    if match is None:
        raise SystemExit(2)
    left, top, right, bottom = map(int, match.groups())
    if right <= left or bottom <= top:
        raise SystemExit(3)
    print((left + right) // 2, (top + bottom) // 2)
    break
else:
    raise SystemExit(4)
PY
)" || {
  record "failure_reason=unable to resolve exact Pixel Launcher close-button bounds"
  record "result=BLOCKED"
  exit 3
}

read -r tap_x tap_y <<< "$coordinates"
record "action=close-pixel-launcher-anr"
record "tap_x=$tap_x"
record "tap_y=$tap_y"
"$adb_bin" shell input tap "$tap_x" "$tap_y"

cleared=false
for ((probe = 1; probe <= 20; probe++)); do
  sleep "$probe_sleep"
  if ! dump_hierarchy; then
    continue
  fi
  if ! grep -Fq 'Pixel Launcher' "$hierarchy" &&
     ! grep -Fq 'resource-id="android:id/aerr_close"' "$hierarchy"; then
    cleared=true
    break
  fi
done

if [[ "$cleared" != true ]]; then
  record "failure_reason=exact Pixel Launcher ANR remained after scoped close action"
  record "result=BLOCKED"
  exit 3
fi

record "launcher_anr_present=true"
record "launcher_anr_cleared=true"
record "result=PASS"
