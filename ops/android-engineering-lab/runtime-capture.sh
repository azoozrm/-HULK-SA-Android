#!/usr/bin/env bash
# runtime-capture.sh — bounded runtime evidence packet for an explicit TEST package.
#
# Usage: runtime-capture.sh <test-package> <outdir> [--launch <component>]
#
# Captures only system-level diagnostics for the explicitly supplied isolated test package:
# package identity, foreground/resumed activity, focused window, compilation state, bounded
# dumpsys activity/window, a bounded logcat tail for the package process, the current UI
# hierarchy when available, and a screenshot. Credentials and account data are never
# intentionally collected and any matching line is filtered out.
#
# Protected production packages (sa.hulksa.player, sa.hulksa.player.dev) are refused.

set -Eeuo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=lib-lab.sh
source "$SCRIPT_DIR/lib-lab.sh"

[[ $# -ge 2 ]] || {
  echo 'Usage: runtime-capture.sh <test-package> <outdir> [--launch <component>]' >&2
  exit 2
}

PKG=$1
OUT=$2
shift 2
COMPONENT=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --launch)
      COMPONENT=${2:-}
      shift 2
      ;;
    *)
      echo "STOP: unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

lab_guard_test_package "$PKG"
SERIAL=$(lab_require_adb_device)
mkdir -p "$OUT"

cap() { timeout 25 adb -s "$SERIAL" shell "$@" 2>&1 || true; }

{
  echo "=== runtime capture utc=$(lab_now_utc) ==="
  lab_kv package "$PKG"
  lab_kv adb_transport "$SERIAL"
} >"$OUT/identity.txt"

{
  echo '--- device identity ---'
  lab_kv ro_product_model "$(cap getprop ro.product.model | tr -d '\r')"
  lab_kv ro_product_device "$(cap getprop ro.product.device | tr -d '\r')"
  lab_kv ro_build_version_sdk "$(cap getprop ro.build.version.sdk | tr -d '\r')"
  lab_kv ro_product_cpu_abi "$(cap getprop ro.product.cpu.abi | tr -d '\r')"
  lab_kv ro_build_fingerprint "$(cap getprop ro.build.fingerprint | tr -d '\r')"
} >>"$OUT/identity.txt"

if [[ -n "$COMPONENT" ]]; then
  {
    echo '--- launch ---'
    cap am start -W -n "$COMPONENT"
  } >"$OUT/launch.txt"
fi

{
  echo '--- package identity ---'
  cap dumpsys package "$PKG" | grep -E 'versionCode|versionName|firstInstallTime|lastUpdateTime|pkgFlags|signatures=\[' | head -n 40
} >"$OUT/package-identity.txt"

{
  echo '--- foreground / resumed activity ---'
  cap dumpsys activity activities | grep -E 'mResumedActivity|mFocusedActivity|topResumedActivity|ResumedActivity' | head -n 20
  echo '--- focused window ---'
  cap dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp' | head -n 20
} >"$OUT/foreground.txt"

{
  echo '--- Dexopt state ---'
  cap dumpsys package "$PKG" | grep -E -A3 'Dexopt state|^[[:space:]]+arm:|status=|reason=|code=' | head -n 60
  echo '--- Compiler stats ---'
  cap dumpsys package "$PKG" | grep -E -A6 'Compiler stats' | head -n 40
} >"$OUT/compilation-state.txt"

cap dumpsys activity activities | head -c 400000 >"$OUT/dumpsys-activity.txt" 2>&1 || true
cap dumpsys window windows | head -c 400000 >"$OUT/dumpsys-window.txt" 2>&1 || true

PID=$(cap pidof "$PKG" | tr -d '\r' | awk '{print $1}')
lab_kv process_pid "${PID:-unknown}" >>"$OUT/identity.txt"
if [[ -n "${PID:-}" ]]; then
  timeout 25 adb -s "$SERIAL" logcat -d --pid="$PID" -t 400 2>&1 | lab_redact >"$OUT/logcat.txt" || true
else
  : >"$OUT/logcat.txt"
fi

REMOTE_UI=/sdcard/hulk_lab_ui.xml
if cap uiautomator dump "$REMOTE_UI" | grep -qi 'dumped'; then
  timeout 25 adb -s "$SERIAL" pull "$REMOTE_UI" "$OUT/ui-hierarchy.xml" >/dev/null 2>&1 || true
  timeout 20 adb -s "$SERIAL" shell rm -f "$REMOTE_UI" || true
fi
[[ -f "$OUT/ui-hierarchy.xml" ]] || echo "ui_dump=unavailable" >"$OUT/ui-hierarchy.unavailable.txt"

if timeout 30 adb -s "$SERIAL" exec-out screencap -p >"$OUT/screenshot.png" 2>/dev/null; then
  : # captured
else
  rm -f "$OUT/screenshot.png"
  echo "screenshot=unavailable" >"$OUT/screenshot.unavailable.txt"
fi

{
  echo '--- captured files ---'
  ls -l "$OUT"
} >"$OUT/capture-index.txt"

echo "RUNTIME_CAPTURE_OK out=$OUT"
