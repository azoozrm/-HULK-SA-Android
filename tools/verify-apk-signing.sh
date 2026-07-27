#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "usage: verify-apk-signing.sh <apk> <expected-cert-sha256> [report]" >&2
  exit 2
fi

APK="$1"
EXPECTED_RAW="$2"
REPORT="${3:-apk-signing-report.txt}"

if [[ ! -f "$APK" ]]; then
  echo "APK not found: $APK" >&2
  exit 2
fi
if ! command -v apksigner >/dev/null 2>&1; then
  echo "apksigner is not available on PATH" >&2
  exit 2
fi

normalize_digest() {
  printf '%s' "$1" | tr '[:lower:]' '[:upper:]' | tr -d ':[:space:]'
}

EXPECTED="$(normalize_digest "$EXPECTED_RAW")"
if [[ ! "$EXPECTED" =~ ^[0-9A-F]{64}$ ]]; then
  echo "Expected certificate SHA-256 must contain exactly 64 hexadecimal characters" >&2
  exit 2
fi

TMP_REPORT="$(mktemp)"
trap 'rm -f "$TMP_REPORT"' EXIT

apksigner verify --verbose --print-certs "$APK" | tee "$TMP_REPORT"

ACTUAL_RAW="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$TMP_REPORT" | head -n1)"
ACTUAL="$(normalize_digest "$ACTUAL_RAW")"

if [[ -z "$ACTUAL" ]]; then
  echo "Unable to read signer certificate SHA-256 from apksigner output" >&2
  exit 1
fi
if [[ "$ACTUAL" != "$EXPECTED" ]]; then
  echo "Signer certificate mismatch" >&2
  echo "Expected: $EXPECTED" >&2
  echo "Actual:   $ACTUAL" >&2
  exit 1
fi

if ! grep -Fq 'Verified using v1 scheme (JAR signing): true' "$TMP_REPORT"; then
  echo "APK is missing required v1 signing for API 23 compatibility" >&2
  exit 1
fi
if ! grep -Fq 'Verified using v2 scheme (APK Signature Scheme v2): true' "$TMP_REPORT"; then
  echo "APK is missing required v2 signing" >&2
  exit 1
fi

{
  echo "APK: $(basename "$APK")"
  echo "Certificate SHA-256: $ACTUAL"
  echo "v1 signing: PASS"
  echo "v2 signing: PASS"
  echo "apksigner verification: PASS"
} > "$REPORT"

cat "$REPORT"
