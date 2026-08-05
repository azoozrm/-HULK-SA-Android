#!/usr/bin/env bash
set -Eeuo pipefail

REAL_ADB_BIN="${ADB_BIN:-adb}"
preflight_ran=false

adb() {
  if [[ "${1:-}" == "logcat" && "${2:-}" == "-c" && "$preflight_ran" != true ]]; then
    preflight_ran=true
    ADB_BIN="$REAL_ADB_BIN" bash \
      quality/compatibility-v2/stabilize_foldable_launcher.sh \
      "$profile" "$out" "$package"
  fi
  command "$REAL_ADB_BIN" "$@"
}

source quality/compatibility-v2/run_runtime_profile_impl.sh "$@"
