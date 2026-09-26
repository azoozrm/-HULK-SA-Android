#!/usr/bin/env bash
# physical-preflight.sh — read-only physical-engineering identity preflight.
#
# Usage: physical-preflight.sh --package <pkg> --state-preserving [options]
#
# Establishes and fails closed on: selected ADB serial and device identity,
# expected owner-approved surface, target package, installed
# presence/version/signer, candidate APK package/version/signer/SHA-256,
# supplied source/worktree commit, and the temporary-setting restore contract.
# It never installs, uninstalls, clears, changes settings or launches anything.
#
# Exit codes:
#   0 PASS
#   2 usage or declaration error
#   3 missing input artifact (candidate APK / worktree)
#   4 apk-inspect.sh tooling unavailable
#   5 identity mismatch (FAIL CLOSED)
#  10 device selection failure (no device / ambiguous / explicit absent)
#  12 production package refusal
#
# See physical_preflight.py for the full contract.

set -Eeuo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)

exec python3 "$SCRIPT_DIR/physical_preflight.py" "$@"
