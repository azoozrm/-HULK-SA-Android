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
adb shell wm density "$DENSITY"
bash qa/lab_fixture_app/run-smoke.sh "$APK" "$OUT" "$TOKEN"
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
