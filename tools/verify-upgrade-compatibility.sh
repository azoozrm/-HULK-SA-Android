#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "usage: verify-upgrade-compatibility.sh <baseline-apk> <candidate-apk> [report]" >&2
  exit 2
fi

BASELINE="$1"
CANDIDATE="$2"
REPORT="${3:-upgrade-compatibility-report.txt}"

for apk in "$BASELINE" "$CANDIDATE"; do
  if [[ ! -f "$apk" ]]; then
    echo "APK not found: $apk" >&2
    exit 2
  fi
  apksigner verify --print-certs "$apk" >/dev/null
 done

normalize_digest() {
  printf '%s' "$1" | tr '[:lower:]' '[:upper:]' | tr -d ':[:space:]'
}

apk_cert() {
  apksigner verify --print-certs "$1" \
    | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' \
    | head -n1
}

BASELINE_PACKAGE="$(apkanalyzer manifest application-id "$BASELINE")"
CANDIDATE_PACKAGE="$(apkanalyzer manifest application-id "$CANDIDATE")"
BASELINE_VERSION_CODE="$(apkanalyzer manifest version-code "$BASELINE")"
CANDIDATE_VERSION_CODE="$(apkanalyzer manifest version-code "$CANDIDATE")"
BASELINE_VERSION_NAME="$(apkanalyzer manifest version-name "$BASELINE")"
CANDIDATE_VERSION_NAME="$(apkanalyzer manifest version-name "$CANDIDATE")"
BASELINE_CERT="$(normalize_digest "$(apk_cert "$BASELINE")")"
CANDIDATE_CERT="$(normalize_digest "$(apk_cert "$CANDIDATE")")"

if [[ "$BASELINE_PACKAGE" != "$CANDIDATE_PACKAGE" ]]; then
  echo "Application ID mismatch: $BASELINE_PACKAGE != $CANDIDATE_PACKAGE" >&2
  exit 1
fi
if [[ -z "$BASELINE_CERT" || "$BASELINE_CERT" != "$CANDIDATE_CERT" ]]; then
  echo "Signing certificate mismatch between baseline and candidate" >&2
  exit 1
fi
if ! [[ "$BASELINE_VERSION_CODE" =~ ^[0-9]+$ && "$CANDIDATE_VERSION_CODE" =~ ^[0-9]+$ ]]; then
  echo "Unable to parse numeric Android versionCode" >&2
  exit 1
fi
if (( CANDIDATE_VERSION_CODE <= BASELINE_VERSION_CODE )); then
  echo "Candidate versionCode must be greater than baseline: $CANDIDATE_VERSION_CODE <= $BASELINE_VERSION_CODE" >&2
  exit 1
fi

{
  echo "Application ID: $CANDIDATE_PACKAGE"
  echo "Baseline version: $BASELINE_VERSION_NAME ($BASELINE_VERSION_CODE)"
  echo "Candidate version: $CANDIDATE_VERSION_NAME ($CANDIDATE_VERSION_CODE)"
  echo "Certificate SHA-256: $CANDIDATE_CERT"
  echo "Static upgrade compatibility: PASS"
  echo "Device install/upgrade execution: NOT PERFORMED BY THIS SCRIPT"
} > "$REPORT"

cat "$REPORT"
