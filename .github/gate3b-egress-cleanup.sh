#!/usr/bin/env bash
# Gate 3B egress teardown. Runs even when the job fails; the runner is ephemeral but this
# keeps the job clean and removes the temporary private key from the runner filesystem.
set -uo pipefail

run_dir="${RUNNER_TEMP:-/tmp}/gate3b-egress"

if [ -f "$run_dir/bridge.pid" ]; then
  kill "$(cat "$run_dir/bridge.pid")" 2>/dev/null || true
fi
pkill -f 'gate3b-http-socks-bridge.py' 2>/dev/null || true
pkill -f 'ssh -f -N -D 127.0.0.1:1080' 2>/dev/null || true

rm -f "$run_dir/id_ed25519" "$run_dir/known_hosts" "$run_dir/bridge.pid"

echo "Gate 3B egress torn down"
