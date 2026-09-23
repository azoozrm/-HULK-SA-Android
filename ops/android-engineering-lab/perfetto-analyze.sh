#!/usr/bin/env bash
# perfetto-analyze.sh — bounded Gate 3 benchmark JSON + trace analysis.
#
# Usage: perfetto-analyze.sh <benchmarkData.json> [--traces <dir>] [--target-compilation <file>]
#
# Produces benchmark identity/context, per-metric classification, a trace inventory and a
# separate target-application compilation section. The self-instrumenting test APK's
# `context.compilationMode` is reported under a clearly separated label and is never used
# as the target app state.
#
# A Perfetto trace processor is used only when an existing qualified copy is found. If none
# is available the trace-processor sub-capability is reported as BLOCKED rather than guessed.

set -Eeuo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ANALYZER="$SCRIPT_DIR/benchmark_analysis.py"

[[ $# -ge 1 ]] || {
  echo 'Usage: perfetto-analyze.sh <benchmarkData.json> [--traces <dir>] [--target-compilation <file>]' >&2
  exit 2
}

JSON=$1
shift
TRACES=""
TARGET_COMPILATION=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --traces) TRACES=${2:-}; shift 2 ;;
    --target-compilation) TARGET_COMPILATION=${2:-}; shift 2 ;;
    *) echo "STOP: unknown argument: $1" >&2; exit 2 ;;
  esac
done

[[ -f "$JSON" ]] || { echo "STOP: benchmark JSON not found: $JSON" >&2; exit 3; }

echo '=== BENCHMARK JSON ANALYSIS ==='
python3 "$ANALYZER" "$JSON"

echo
echo '=== TRACE PROCESSOR ==='
TP=""
for candidate in "${TRACE_PROCESSOR:-}" "$(command -v trace_processor_shell 2>/dev/null || true)" \
  "$(command -v trace_processor 2>/dev/null || true)" \
  "${HULK_ANDROID_SDK:-/opt/android-sdk}/perfetto/trace_processor_shell"; do
  if [[ -n "$candidate" && -x "$candidate" ]]; then
    TP=$candidate
    break
  fi
done
if [[ -n "$TP" ]]; then
  echo "trace_processor=available path=$TP"
  "$TP" --version 2>/dev/null | head -n1 || true
else
  echo 'trace_processor=BLOCKED no existing qualified trace processor found (not guessed)'
fi

echo
echo '=== TARGET APPLICATION COMPILATION STATE ==='
if [[ -n "$TARGET_COMPILATION" && -f "$TARGET_COMPILATION" ]]; then
  echo "# source=$TARGET_COMPILATION"
  grep -E 'status=|reason=|code=|Dexopt|Compiler' "$TARGET_COMPILATION" || cat "$TARGET_COMPILATION"
else
  echo 'target_compilation=UNKNOWN no target compilation evidence supplied'
fi

echo
echo '=== TRACE INVENTORY ==='
if [[ -n "$TRACES" && -d "$TRACES" ]]; then
  mapfile -t trace_files < <(find "$TRACES" -maxdepth 1 -type f -name '*.perfetto-trace' | sort)
  echo "trace_count=${#trace_files[@]}"
  for trace in "${trace_files[@]}"; do
    size=$(wc -c <"$trace")
    digest=$(sha256sum "$trace" | awk '{print $1}')
    echo "trace=$(basename "$trace") size=$size sha256=$digest"
  done
  [[ ${#trace_files[@]} -gt 0 ]] || echo 'trace_inventory=EMPTY'
else
  echo 'trace_inventory=UNKNOWN no --traces directory supplied'
fi
