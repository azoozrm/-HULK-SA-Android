#!/usr/bin/env bash
# focus-dpad-probe.sh — repeatable focus-before / focus-after D-pad evidence.
#
# Usage: focus-dpad-probe.sh <test-package> <outdir> [--component <pkg/Activity>] [--keys DOWN,DOWN,DOWN,UP]
#
# Wakes the device, optionally launches the explicitly supplied TEST package component,
# waits for a stable UI, captures the focused accessibility node(s), sends the requested key
# sequence, and captures the resulting focused node(s).
#
# Privacy contract: raw UiAutomator XML is NEVER persisted. It is pulled to a temporary file,
# parsed for non-sensitive focus identity (class/content-description/resource-id/bounds) only,
# and the temporary file is deleted. Editable-field text values are never written to evidence.
# Protected production packages are refused.

set -Eeuo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=lib-lab.sh
source "$SCRIPT_DIR/lib-lab.sh"
FOCUS_HELPER="$SCRIPT_DIR/uiautomator_focus.py"

[[ $# -ge 2 ]] || {
  echo 'Usage: focus-dpad-probe.sh <test-package> <outdir> [--component <pkg/Activity>] [--keys DOWN,DOWN,DOWN,UP]' >&2
  exit 2
}

PKG=$1
OUT=$2
shift 2
COMPONENT=""
KEYS="DOWN,DOWN,DOWN,UP"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --component) COMPONENT=${2:-}; shift 2 ;;
    --keys) KEYS=${2:-}; shift 2 ;;
    *) echo "STOP: unknown argument: $1" >&2; exit 2 ;;
  esac
done

lab_guard_test_package "$PKG"
SERIAL=$(lab_require_adb_device)
mkdir -p "$OUT"
REMOTE_UI=/sdcard/hulk_lab_focus.xml
LAB_TMP_FILE=""

# Interruption-safe cleanup: remove any local temporary raw-UI file and the on-device raw-UI
# dump even on EXIT/INT/TERM/HUP. Best-effort; never removes intentionally produced evidence.
cleanup_evidence() {
  if [[ -n "${LAB_TMP_FILE:-}" && -f "${LAB_TMP_FILE:-}" ]]; then
    rm -f "$LAB_TMP_FILE" || true
  fi
  if [[ -n "${SERIAL:-}" ]]; then
    timeout 10 adb -s "$SERIAL" shell rm -f "$REMOTE_UI" >/dev/null 2>&1 || true
  fi
}
trap 'cleanup_evidence' EXIT
trap 'cleanup_evidence; exit 130' INT TERM HUP

adb_shell() { timeout 25 adb -s "$SERIAL" shell "$@" 2>&1 || true; }

keycode_for() {
  case "$1" in
    DOWN) echo 20 ;;
    UP) echo 19 ;;
    LEFT) echo 21 ;;
    RIGHT) echo 22 ;;
    CENTER) echo 23 ;;
    BACK) echo 4 ;;
    *) return 1 ;;
  esac
}

# Pulls the device dump into a temporary file, emits only non-sensitive focus identity, and
# deletes the raw XML. Marks "<label>.ok" on success so callers can distinguish "no focused
# node" from "standalone uiautomator unavailable" without retaining raw UI.
dump_focus() {
  local label=$1
  LAB_TMP_FILE=$(mktemp)
  local pulled=false
  adb_shell rm -f "$REMOTE_UI" >/dev/null
  if adb_shell uiautomator dump "$REMOTE_UI" | grep -qi 'dumped'; then
    if timeout 25 adb -s "$SERIAL" pull "$REMOTE_UI" "$LAB_TMP_FILE" >/dev/null 2>&1 && [[ -s "$LAB_TMP_FILE" ]]; then
      pulled=true
    fi
    adb_shell rm -f "$REMOTE_UI" >/dev/null
  fi
  if [[ "$pulled" == true ]]; then
    python3 "$FOCUS_HELPER" "$LAB_TMP_FILE" >"$OUT/focus-$label.json" 2>/dev/null || echo '[]' >"$OUT/focus-$label.json"
    : >"$OUT/focus-$label.ok"
    rm -f "$LAB_TMP_FILE"
    LAB_TMP_FILE=""
    return 0
  fi
  rm -f "$LAB_TMP_FILE"
  LAB_TMP_FILE=""
  echo '[]' >"$OUT/focus-$label.json"
  return 1
}

focus_label() {
  python3 "$FOCUS_HELPER" "$1" --primary-label 2>/dev/null || true
}

adb_shell input keyevent KEYCODE_WAKEUP >/dev/null
adb_shell wm dismiss-keyguard >/dev/null || true

if [[ -n "$COMPONENT" ]]; then
  adb_shell am start -W -n "$COMPONENT" >"$OUT/launch.txt"
fi

# Deterministic settle: poll the focused-app window until the target package owns it.
focused_app=""
for _ in $(seq 1 60); do
  focused_app=$(adb_shell dumpsys window windows | sed -n 's/.*mFocusedApp=.* \([^ ]*\/[^ ]*\).*/\1/p' | head -n1)
  if [[ "$focused_app" == "$PKG/"* ]]; then
    break
  fi
  sleep 0.5
done

adb_shell dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp' | head -n 10 >"$OUT/focused-window-before.txt"

# Deterministic settle: wait (bounded) until a focused node exposes a readable label.
# Standalone uiautomator is unavailable on some TV devices; fail fast in that case.
before_dump_ok=true
if ! dump_focus before; then
  before_dump_ok=false
fi
if [[ "$before_dump_ok" == true && -z "$(focus_label "$OUT/focus-before.json")" ]]; then
  for _ in $(seq 1 6); do
    if dump_focus before && [[ -n "$(focus_label "$OUT/focus-before.json")" ]]; then
      break
    fi
    sleep 0.5
  done
fi
[[ -f "$OUT/focus-before.json" ]] || echo '[]' >"$OUT/focus-before.json"

echo "requested_keys=$KEYS" >"$OUT/key-sequence.txt"
IFS=',' read -r -a key_array <<<"$KEYS"
for key in "${key_array[@]}"; do
  code=$(keycode_for "${key^^}") || { echo "STOP: unsupported key '$key'" >&2; exit 4; }
  adb_shell input keyevent "$code" >/dev/null
  echo "sent=$key keycode=$code" >>"$OUT/key-sequence.txt"
done

adb_shell dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp' | head -n 10 >"$OUT/focused-window-after.txt"
dump_focus after || true

before=$(focus_label "$OUT/focus-before.json")
after=$(focus_label "$OUT/focus-after.json")

if [[ ! -f "$OUT/focus-before.ok" || ! -f "$OUT/focus-after.ok" ]]; then
  focus_evidence="BLOCKED standalone uiautomator dump unavailable; use instrumentation UiDevice harness"
elif [[ -n "$before" && -n "$after" && "$before" != "$after" ]]; then
  focus_evidence="PROVEN"
else
  focus_evidence="NOT_PROVEN"
fi

{
  lab_kv package "$PKG"
  lab_kv component "$COMPONENT"
  lab_kv focused_app_probe "$focused_app"
  lab_kv requested_keys "$KEYS"
  lab_kv focus_before "$before"
  lab_kv focus_after "$after"
  lab_kv focus_evidence "$focus_evidence"
  if [[ "$focus_evidence" == "PROVEN" ]]; then
    lab_kv destination_proven yes
  else
    lab_kv destination_proven no
  fi
  lab_kv captured_utc "$(lab_now_utc)"
} >"$OUT/focus-summary.txt"

cat "$OUT/focus-summary.txt"
if [[ "$focus_evidence" == "PROVEN" ]]; then
  echo FOCUS_PROBE_DESTINATION_PROVEN
elif [[ "$focus_evidence" == "BLOCKED" ]]; then
  echo FOCUS_PROBE_BLOCKED_STANDALONE_UIAUTOMATOR
else
  echo FOCUS_PROBE_DESTINATION_NOT_PROVEN
fi
