#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: run-device-job.sh \
  --apk PATH --device-id ID --device-name NAME --family FAMILY \
  --api API --target TARGET --arch ARCH --profile PROFILE --skin WIDTHxHEIGHT \
  --width WIDTH --height HEIGHT --density DENSITY \
  --orientations CSV --font-scales CSV --is-tv BOOL --out PATH
EOF
}

APK=""; DEVICE_ID=""; DEVICE_NAME=""; FAMILY=""; API=""; TARGET=""; ARCH=""
PROFILE=""; SKIN=""; WIDTH=""; HEIGHT=""; DENSITY=""; ORIENTATIONS=""
FONT_SCALES=""; IS_TV=""; OUT=""
while (($#)); do
  case "$1" in
    --apk) APK="${2:-}"; shift 2 ;;
    --device-id) DEVICE_ID="${2:-}"; shift 2 ;;
    --device-name) DEVICE_NAME="${2:-}"; shift 2 ;;
    --family) FAMILY="${2:-}"; shift 2 ;;
    --api) API="${2:-}"; shift 2 ;;
    --target) TARGET="${2:-}"; shift 2 ;;
    --arch) ARCH="${2:-}"; shift 2 ;;
    --profile) PROFILE="${2:-}"; shift 2 ;;
    --skin) SKIN="${2:-}"; shift 2 ;;
    --width) WIDTH="${2:-}"; shift 2 ;;
    --height) HEIGHT="${2:-}"; shift 2 ;;
    --density) DENSITY="${2:-}"; shift 2 ;;
    --orientations) ORIENTATIONS="${2:-}"; shift 2 ;;
    --font-scales) FONT_SCALES="${2:-}"; shift 2 ;;
    --is-tv) IS_TV="${2:-}"; shift 2 ;;
    --out) OUT="${2:-}"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

for required in APK DEVICE_ID DEVICE_NAME FAMILY API TARGET ARCH PROFILE SKIN WIDTH HEIGHT DENSITY ORIENTATIONS FONT_SCALES IS_TV OUT; do
  if [[ -z "${!required}" ]]; then
    echo "Missing required value: $required" >&2
    usage >&2
    exit 2
  fi
done

OUT="$(python3 -c 'import os,sys; print(os.path.abspath(sys.argv[1]))' "$OUT")"
RUNTIME_ROOT="${OUT%/*}/.runtime/$DEVICE_ID"
mkdir -p "$OUT" "$RUNTIME_ROOT"

write_blocked_summary() {
  local message="$1"
  python3 - "$OUT/summary.json" "$DEVICE_ID" "$DEVICE_NAME" "$FAMILY" "$API" "$message" <<'PY'
import json
import pathlib
import sys
path = pathlib.Path(sys.argv[1])
path.parent.mkdir(parents=True, exist_ok=True)
path.write_text(json.dumps({
    "device_id": sys.argv[2],
    "device_name": sys.argv[3],
    "family": sys.argv[4],
    "api": sys.argv[5],
    "critical_finding_count": 0,
    "infrastructure_error_count": 1,
    "status": "infrastructure_blocked",
    "infrastructure_error": sys.argv[6][-2000:],
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY
}

capture_attempt() {
  local attempt="$1"
  local runtime="$RUNTIME_ROOT/$attempt"
  rm -rf "$runtime"
  mkdir -p "$runtime"
  bash qa/compatibility/run-native-emulator.sh \
    --api "$API" \
    --target "$TARGET" \
    --arch "$ARCH" \
    --profile "$PROFILE" \
    --skin "$SKIN" \
    --result-dir "$runtime" \
    -- \
    python3 qa/compatibility/run-lab-qualified.py \
      --apk "$APK" \
      --device-id "$DEVICE_ID" \
      --device-name "$DEVICE_NAME" \
      --family "$FAMILY" \
      --api "$API" \
      --target "$TARGET" \
      --arch "$ARCH" \
      --profile "$PROFILE" \
      --width "$WIDTH" \
      --height "$HEIGHT" \
      --density "$DENSITY" \
      --orientations "$ORIENTATIONS" \
      --font-scales "$FONT_SCALES" \
      --is-tv "$IS_TV" \
      --out "$OUT" \
      --serial emulator-5554
}

analyze_selected() {
  if [[ -s "$OUT/run-manifest.json" ]]; then
    python3 qa/compatibility/analyze-qualified.py "$OUT" || true
  fi
}

needs_retry() {
  if [[ ! -s "$OUT/summary.json" ]]; then
    return 0
  fi
  python3 - "$OUT/summary.json" <<'PY'
import json
import pathlib
import sys
try:
    summary = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
except (OSError, json.JSONDecodeError):
    raise SystemExit(0)
raise SystemExit(0 if int(summary.get("infrastructure_error_count", 0)) > 0 else 1)
PY
}

set +e
capture_attempt primary
primary_rc=$?
set -e
analyze_selected
if [[ ! -s "$OUT/summary.json" ]]; then
  write_blocked_summary "primary native emulator attempt exited with code $primary_rc and produced no analyzable manifest"
fi

if needs_retry; then
  set +e
  capture_attempt retry
  retry_rc=$?
  set -e
  analyze_selected
  if [[ ! -s "$OUT/summary.json" ]]; then
    write_blocked_summary "retry native emulator attempt exited with code $retry_rc and produced no analyzable manifest"
  fi
fi

# Keep native emulator logs with the selected device artifact without allowing
# them to participate in result selection or replace run-manifest/summary data.
mkdir -p "$OUT/emulator-attempts"
for attempt in primary retry; do
  if [[ -d "$RUNTIME_ROOT/$attempt" ]]; then
    rm -rf "$OUT/emulator-attempts/$attempt"
    cp -a "$RUNTIME_ROOT/$attempt" "$OUT/emulator-attempts/$attempt"
  fi
done
