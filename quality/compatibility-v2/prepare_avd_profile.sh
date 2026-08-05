#!/usr/bin/env bash
set -Eeuo pipefail

width="${1:?width px is required}"
height="${2:?height px is required}"
density="${3:?density dpi is required}"
avd_name="${4:-test}"
avd_home="${ANDROID_AVD_HOME:-${HOME}/.android/avd}"
config="${avd_home}/${avd_name}.avd/config.ini"

if [[ ! -f "$config" ]]; then
  echo "AVD config is missing: $config" >&2
  exit 2
fi

python3 - "$config" "$width" "$height" "$density" <<'PY_CONFIG'
from pathlib import Path
import sys

path = Path(sys.argv[1])
width, height, density = sys.argv[2:]
replacements = {
    'skin.name': f'{width}x{height}',
    'skin.path': f'{width}x{height}',
    'hw.lcd.width': width,
    'hw.lcd.height': height,
    'hw.lcd.density': density,
}
lines = path.read_text(encoding='utf-8').splitlines()
output = []
seen = set()
for line in lines:
    key = line.split('=', 1)[0].strip() if '=' in line else ''
    if key in replacements:
        output.append(f'{key}={replacements[key]}')
        seen.add(key)
    else:
        output.append(line)
for key, value in replacements.items():
    if key not in seen:
        output.append(f'{key}={value}')
path.write_text('\n'.join(output) + '\n', encoding='utf-8')
PY_CONFIG

for expected in \
  "skin.name=${width}x${height}" \
  "skin.path=${width}x${height}" \
  "hw.lcd.width=${width}" \
  "hw.lcd.height=${height}" \
  "hw.lcd.density=${density}"; do
  grep -Fxq "$expected" "$config" || {
    echo "AVD config verification failed: $expected" >&2
    exit 3
  }
done

echo "Prepared ${avd_name}: ${width}x${height} @ ${density}dpi"
