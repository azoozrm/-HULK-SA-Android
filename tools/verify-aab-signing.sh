#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "usage: verify-aab-signing.sh <aab> <expected-cert-sha256> [report]" >&2
  exit 2
fi

AAB="$1"
EXPECTED_RAW="$2"
REPORT="${3:-aab-signing-report.txt}"

if [[ ! -f "$AAB" ]]; then
  echo "AAB not found: $AAB" >&2
  exit 2
fi
if ! command -v jarsigner >/dev/null 2>&1 || ! command -v keytool >/dev/null 2>&1; then
  echo "jarsigner and keytool must be available on PATH" >&2
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

JAR_REPORT="$(mktemp)"
CERT_REPORT="$(mktemp)"
trap 'rm -f "$JAR_REPORT" "$CERT_REPORT"' EXIT

set +e
jarsigner -verify -strict -verbose -certs "$AAB" 2>&1 | tee "$JAR_REPORT"
JARSIGNER_STATUS=${PIPESTATUS[0]}
set -e

keytool -printcert -jarfile "$AAB" | tee "$CERT_REPORT"

ACTUAL_RAW="$(sed -n 's/^[[:space:]]*SHA256: //p' "$CERT_REPORT" | head -n1)"
ACTUAL="$(normalize_digest "$ACTUAL_RAW")"

if [[ -z "$ACTUAL" ]]; then
  echo "Unable to read AAB signer certificate SHA-256" >&2
  exit 1
fi
if [[ "$ACTUAL" != "$EXPECTED" ]]; then
  echo "AAB signer certificate mismatch" >&2
  echo "Expected: $EXPECTED" >&2
  echo "Actual:   $ACTUAL" >&2
  exit 1
fi
if ! grep -Fq 'jar verified.' "$JAR_REPORT"; then
  echo "AAB JAR signature verification did not report success" >&2
  exit 1
fi
if [[ "$JARSIGNER_STATUS" -ne 0 && "$JARSIGNER_STATUS" -ne 4 ]]; then
  echo "AAB jarsigner verification failed with status $JARSIGNER_STATUS" >&2
  exit "$JARSIGNER_STATUS"
fi

{
  echo "AAB: $(basename "$AAB")"
  echo "Certificate SHA-256: $ACTUAL"
  echo "JAR signature verification: PASS"
  if [[ "$JARSIGNER_STATUS" -eq 4 ]]; then
    echo "Strict verification warnings: PRESENT (accepted after successful signature and certificate checks)"
  else
    echo "Strict verification warnings: NONE"
  fi
} > "$REPORT"

cat "$REPORT"
