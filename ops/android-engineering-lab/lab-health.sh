#!/usr/bin/env bash
set -Eeuo pipefail

REPO=/srv/hulk-android/repos/HULK-SA-Android
OFFICIAL=phase-3-v0.9.3.0-adaptive-foundation

echo '=== HULK ANDROID LAB HEALTH ==='
echo "utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "host=$(hostname)"
echo "user=$(whoami)"
echo 'lab_mode=B'

if command -v hulk-disk-gate >/dev/null 2>&1; then
  hulk-disk-gate
fi

printf 'canonical_head='
git -C "$REPO" rev-parse HEAD
printf 'remote_head='
git -C "$REPO" ls-remote origin "refs/heads/$OFFICIAL" | awk '{print $1}'
[[ -z "$(git -C "$REPO" status --porcelain)" ]] && echo canonical_clean=yes || echo canonical_clean=no

if command -v gh >/dev/null 2>&1; then
  printf 'github_user='
  gh api user --jq .login 2>/dev/null || echo unavailable
fi

printf 'git='
git --version
printf 'java='
java -version 2>&1 | head -n 1
printf 'python='
python3 --version
printf 'adb='
adb version | head -n 1
printf 'opencode='
opencode --version | head -n 1

if [[ -e /dev/kvm ]]; then
  echo 'kvm_device=present'
else
  echo 'kvm_device=absent'
fi

free -h
df -h /
echo '=== END HULK ANDROID LAB HEALTH ==='
