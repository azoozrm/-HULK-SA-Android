#!/usr/bin/env bash
# Shared helpers for the HULK SA Android engineering lab diagnostics tools.
#
# This file is sourced by lab tools; it is not meant to be executed directly.
# It is read-only with respect to the repository and contains no credentials,
# IP addresses, host fingerprints or live state.

set -Eeuo pipefail

lab_sdk_root() {
  printf '%s\n' "${HULK_ANDROID_SDK:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/opt/android-sdk}}}"
}

lab_build_tools_dir() {
  local sdk
  sdk=$(lab_sdk_root)
  local dir
  dir=$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -n 1)
  [[ -n "$dir" ]] || {
    echo "STOP: no Android build-tools found under $sdk/build-tools" >&2
    return 10
  }
  printf '%s\n' "$dir"
}

lab_aapt2() {
  local bt
  bt=$(lab_build_tools_dir)
  printf '%s\n' "$bt/aapt2"
}

lab_apksigner() {
  local bt
  bt=$(lab_build_tools_dir)
  printf '%s\n' "$bt/apksigner"
}

# Extracts android:targetPackage from the E: instrumentation element of an
# `aapt2 dump xmltree` manifest dump, printing the value or nothing. Accepts the
# current resource-ID annotation form :targetPackage(0x<hex>)="<value>" and the
# legacy :targetPackage="<value>" form, and is bounded to the instrumentation
# element so attributes outside it can never be extracted.
lab_instrumentation_target_package() {
  local xmltree=$1
  local block target

  block=$(awk '
    /^[[:space:]]*E: / {
      in_instrumentation = ($0 ~ /^[[:space:]]*E: instrumentation([[:space:]]|$)/)
    }
    in_instrumentation { print }
  ' "$xmltree")

  [[ -n "$block" ]] || return 0

  target=$(printf '%s\n' "$block" | sed -n 's/.*:targetPackage(0x[0-9a-fA-F]*)="\([^"]*\)".*/\1/p' | head -n1)
  if [[ -z "$target" ]]; then
    target=$(printf '%s\n' "$block" | sed -n 's/.*:targetPackage="\([^"]*\)".*/\1/p' | head -n1)
  fi
  printf '%s\n' "$target"
}

lab_adb_serial() {
  if [[ -n "${HULK_ADB_SERIAL:-}" ]]; then
    printf '%s\n' "$HULK_ADB_SERIAL"
    return 0
  fi
  adb devices | awk 'NR>1 && $2=="device" {print $1; exit}'
}

lab_require_adb_device() {
  local serial
  serial=$(lab_adb_serial)
  [[ -n "$serial" ]] || {
    echo "STOP: no adb device in 'device' state" >&2
    return 11
  }
  printf '%s\n' "$serial"
}

lab_adb() {
  local serial
  serial=$(lab_require_adb_device)
  adb -s "$serial" "$@"
}

# Refuse to operate on protected production identities.
lab_guard_test_package() {
  local pkg=$1
  case "$pkg" in
    sa.hulksa.player|sa.hulksa.player.dev)
      echo "STOP: refusing to operate on protected package $pkg" >&2
      return 12
      ;;
  esac
}

# Reads stdin and drops any line that may contain credentials or account data.
lab_redact() {
  grep -viE 'password|passwd|pass_word|token|secret|bearer|authorization|access[_-]?code|iptv|m3u|subscription[_-]?key|credential|username=|user=|login=' || true
}

lab_now_utc() {
  date -u +%Y-%m-%dT%H:%M:%SZ
}

# Deterministic key=value block writer.
lab_kv() {
  printf '%s=%s\n' "$1" "$2"
}
