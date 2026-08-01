#!/usr/bin/env bash
set -euo pipefail

APK="${1:?fixture apk required}"
OUT="${2:?output directory required}"
TOKEN="${3:?launch token required}"
SOURCE_HEAD_SHA="${4:?source head SHA required}"
BASE_SHA="${5:?base SHA required}"
TESTED_COMMIT_SHA="${6:?tested commit SHA required}"
MERGE_SHA="${7:?merge SHA required}"
APK_SHA256="${8:?APK SHA-256 required}"
WIDTH="${9:?width required}"
HEIGHT="${10:?height required}"
DENSITY="${11:?density required}"

mkdir -p "$OUT"
python3 qa/lab_verifier/device_contract.py \
  --width "$WIDTH" \
  --height "$HEIGHT" \
  --density "$DENSITY" \
  --out "$OUT/device-contract.json"

run_attempt() {
  local attempt="$1"
  mkdir -p "$attempt"
  bash qa/lab_fixture_app/run-smoke.sh "$APK" "$attempt" "$TOKEN"
}

FIRST="$OUT/attempt-1"
SECOND="$OUT/attempt-2"
FINAL="$FIRST"
RETRIED=false
SECOND_STATUS=null

set +e
run_attempt "$FIRST"
FIRST_STATUS=$?
set -e

if [[ "$FIRST_STATUS" -ne 0 ]]; then
  if [[ "$FIRST_STATUS" -ne 75 ]]; then
    python3 - "$OUT/retry-evidence.json" "$FIRST_STATUS" <<'PY'
import json, sys
json.dump({
  "attempts": 1,
  "retried": False,
  "first_status": int(sys.argv[2]),
  "retry_reason": None,
  "retry_allowed": False,
  "final_success": False,
}, open(sys.argv[1], "w", encoding="utf-8"), sort_keys=True, indent=2)
open(sys.argv[1], "a", encoding="utf-8").write("\n")
PY
    exit "$FIRST_STATUS"
  fi

  RETRIED=true
  center="$(python3 - "$FIRST/failure-classification.json" <<'PY'
import json, sys
payload=json.load(open(sys.argv[1], encoding='utf-8'))
if payload.get('classification') != 'infrastructure' or payload.get('code') != 'SYSTEM_SERVICE_UNAVAILABLE' or payload.get('retry_allowed') is not True:
    raise SystemExit('retry denied: classification is not a proven transient infrastructure failure')
center=payload.get('dismiss_center')
if not isinstance(center, list) or len(center) != 2:
    raise SystemExit('retry denied: dismiss coordinates missing')
print(f'{int(center[0])} {int(center[1])}')
PY
)"
  read -r dismiss_x dismiss_y <<<"$center"
  adb shell input tap "$dismiss_x" "$dismiss_y"
  sleep 1
  adb shell am force-stop sa.hulksa.labfixture || true
  adb shell service check activity >"$OUT/retry-activity-service.txt" || true
  adb shell service check window >"$OUT/retry-window-service.txt" || true
  adb shell getprop sys.boot_completed >"$OUT/retry-boot-completed.txt" || true
  sleep 2

  set +e
  run_attempt "$SECOND"
  SECOND_STATUS=$?
  set -e
  if [[ "$SECOND_STATUS" -ne 0 ]]; then
    python3 - "$OUT/retry-evidence.json" "$FIRST_STATUS" "$SECOND_STATUS" <<'PY'
import json, sys
json.dump({
  "attempts": 2,
  "retried": True,
  "first_status": int(sys.argv[2]),
  "second_status": int(sys.argv[3]),
  "retry_reason": "SYSTEM_SERVICE_UNAVAILABLE",
  "retry_allowed": True,
  "final_success": False,
}, open(sys.argv[1], "w", encoding="utf-8"), sort_keys=True, indent=2)
open(sys.argv[1], "a", encoding="utf-8").write("\n")
PY
    exit "$SECOND_STATUS"
  fi
  FINAL="$SECOND"
fi

python3 - "$OUT/retry-evidence.json" "$RETRIED" "$FIRST_STATUS" "$SECOND_STATUS" <<'PY'
import json, sys
retried = sys.argv[2].lower() == 'true'
second = None if sys.argv[4] == 'null' else int(sys.argv[4])
json.dump({
  "attempts": 2 if retried else 1,
  "retried": retried,
  "first_status": int(sys.argv[3]),
  "second_status": second,
  "retry_reason": "SYSTEM_SERVICE_UNAVAILABLE" if retried else None,
  "retry_allowed": retried,
  "final_success": True,
  "final_attempt": "attempt-2" if retried else "attempt-1",
}, open(sys.argv[1], "w", encoding="utf-8"), sort_keys=True, indent=2)
open(sys.argv[1], "a", encoding="utf-8").write("\n")
PY

for item in "$FINAL"/*; do
  [[ -e "$item" ]] || continue
  cp -a "$item" "$OUT/"
done

python3 qa/lab_verifier/build_fixture_bundle.py \
  --raw "$OUT" \
  --out "$OUT/bundle.json" \
  --source-head-sha "$SOURCE_HEAD_SHA" \
  --base-sha "$BASE_SHA" \
  --tested-commit-sha "$TESTED_COMMIT_SHA" \
  --merge-sha "$MERGE_SHA" \
  --apk-sha256 "$APK_SHA256" \
  --width "$WIDTH" \
  --height "$HEIGHT" \
  --density "$DENSITY"
python3 qa/lab_verifier/cli.py verify-bundle "$OUT/bundle.json" > "$OUT/verifier-report.json"
python3 qa/lab_verifier/write_sha256_manifest.py "$OUT" --output ARTIFACT-SHA256SUMS.txt
