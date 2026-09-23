#!/usr/bin/env bash
# Deterministic test runner for the Android engineering lab diagnostics tools.
# No device, SDK or network is required.

set -Eeuo pipefail
HERE=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
exec python3 -m unittest discover -s "$HERE" -p 'test_*.py' -v
