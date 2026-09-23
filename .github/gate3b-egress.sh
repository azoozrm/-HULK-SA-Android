#!/usr/bin/env bash
# Gate 3B approved egress setup.
#
# Establishes a temporary SOCKS5 tunnel over SSH to the approved Contabo lab host and a
# loopback-only HTTP proxy bridge the Android TV emulator can use via -http-proxy. This is
# not a persistent service: it lives only for the duration of this job, binds only the
# runner loopback, and opens no inbound port on the egress host.
set -euo pipefail

: "${HULK_GATE3B_EGRESS_SSH_KEY:?HULK_GATE3B_EGRESS_SSH_KEY is required}"
: "${HULK_GATE3B_EGRESS_HOST:?HULK_GATE3B_EGRESS_HOST is required}"
: "${HULK_GATE3B_EGRESS_USER:?HULK_GATE3B_EGRESS_USER is required}"
egress_port="${HULK_GATE3B_EGRESS_PORT:-22}"

run_dir="${RUNNER_TEMP:-/tmp}/gate3b-egress"
mkdir -p "$run_dir"

key_file="$run_dir/id_ed25519"
known_hosts="$run_dir/known_hosts"
umask 077

if printf '%s' "$HULK_GATE3B_EGRESS_SSH_KEY" | base64 -d > "$key_file" 2>/dev/null; then
  :
else
  printf '%s\n' "$HULK_GATE3B_EGRESS_SSH_KEY" > "$key_file"
fi
chmod 600 "$key_file"

if [ -n "${HULK_GATE3B_EGRESS_HOST_KEY:-}" ]; then
  printf '%s %s\n' "$HULK_GATE3B_EGRESS_HOST" "$HULK_GATE3B_EGRESS_HOST_KEY" > "$known_hosts"
else
  ssh-keyscan -p "$egress_port" "$HULK_GATE3B_EGRESS_HOST" > "$known_hosts" 2>/dev/null
fi
chmod 600 "$known_hosts"

# SOCKS5 listener bound to the runner loopback only.
ssh -f -N -D 127.0.0.1:1080 \
  -i "$key_file" -p "$egress_port" \
  -o IdentitiesOnly=yes -o BatchMode=yes -o ExitOnForwardFailure=yes \
  -o StrictHostKeyChecking=yes -o UserKnownHostsFile="$known_hosts" \
  -o ServerAliveInterval=30 -o ServerAliveCountMax=3 \
  "$HULK_GATE3B_EGRESS_USER@$HULK_GATE3B_EGRESS_HOST"

# Loopback-only HTTP -> SOCKS5 bridge for the emulator's -http-proxy.
setsid nohup python3 .github/gate3b-http-socks-bridge.py \
  > "$run_dir/bridge.log" 2>&1 &
bridge_pid=$!
echo "$bridge_pid" > "$run_dir/bridge.pid"
sleep 2

# Force IPv4 egress: local DNS resolution to an IPv4 literal for the SOCKS destination.
socks_ip="$(curl -4 -fsS --max-time 25 --socks5 127.0.0.1:1080 https://api.ipify.org || true)"
http_ip="$(curl -4 -fsS --max-time 25 --proxy http://127.0.0.1:8118 https://api.ipify.org || true)"

{
  echo "socks_listener=127.0.0.1:1080"
  echo "http_bridge=127.0.0.1:8118"
  echo "egress_host=${HULK_GATE3B_EGRESS_HOST}"
  echo "forced_family=ipv4"
  echo "socks_egress_ip=${socks_ip}"
  echo "http_egress_ip=${http_ip}"
} | tee "$run_dir/egress.txt"

if [ -z "$socks_ip" ] || [ -z "$http_ip" ]; then
  echo "Gate 3B egress tunnel did not come up" >&2
  tail -40 "$run_dir/bridge.log" >&2 || true
  exit 1
fi

if [ "$socks_ip" != "$http_ip" ]; then
  echo "Gate 3B egress IP mismatch between SOCKS and HTTP paths" >&2
  exit 1
fi

case "$socks_ip" in
  *:*) echo "Expected an IPv4 egress identity but observed: $socks_ip" >&2; exit 1 ;;
esac

echo "Gate 3B egress ready (forced IPv4) via $socks_ip"
