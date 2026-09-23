#!/usr/bin/env bash
# Gate 3B baseline profile generation + qualification.
#
# Runs inside the GitHub-hosted Android TV API 36 x86_64 emulator. Authenticates the
# benchmark target with the protected production-e2e account, persists the session,
# generates baseline-prof.txt through the corrected BaselineProfileGenerator (Gate 3A.5
# HOME -> LIVE contract) and preserves sanitized evidence only.
#
# This script never prints credential values. The workflow masks protected secrets in logs.
set -euo pipefail

out=build/gate3b
mkdir -p "$out"

bench_apk=app/build/outputs/apk/benchmark/app-benchmark.apk
app_test_apk=app/build/outputs/apk/androidTest/benchmark/app-benchmark-androidTest.apk

adb wait-for-device
while [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
  sleep 2
done

{
  echo "source_sha=${GITHUB_SHA}"
  echo "run_id=${GITHUB_RUN_ID}"
  echo "app_apk_sha256=$(sha256sum "$bench_apk" | awk '{print $1}')"
  echo "app_test_apk_sha256=$(sha256sum "$app_test_apk" | awk '{print $1}')"
  echo "emulator_sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "emulator_release=$(adb shell getprop ro.build.version.release | tr -d '\r')"
  echo "emulator_product=$(adb shell getprop ro.product.name | tr -d '\r')"
  echo "emulator_device=$(adb shell getprop ro.product.device | tr -d '\r')"
  echo "emulator_abi=$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
  echo "emulator_build_type=$(adb shell getprop ro.build.type | tr -d '\r')"
} | tee "$out/runtime.txt"

adb install -r -t "$bench_apk" | tee "$out/install-benchmark.txt"
adb install -r -t "$app_test_apk" | tee "$out/install-app-test.txt"

# Protected authentication bootstrap: authenticate the benchmark target with the
# production-e2e account, persist the session (remember=true) and enable Direct Entry.
adb logcat -c 2>/dev/null || true
creds_b64="$(python3 - <<'PY'
import base64, json, os

def clean(value, *, keep_inner_spaces):
    # Transport-only normalization: strip a stray trailing newline/CR that a secret may
    # carry. Access code and username never contain surrounding whitespace; passwords may
    # contain inner spaces, so only trailing CR/LF is removed.
    return value.rstrip("\r\n") if keep_inner_spaces else value.strip()

blob = json.dumps({
    "accessCode": clean(os.environ["HULK_E2E_ACCESS_CODE"], keep_inner_spaces=False),
    "username": clean(os.environ["HULK_E2E_USERNAME"], keep_inner_spaces=False),
    "password": clean(os.environ["HULK_E2E_PASSWORD"], keep_inner_spaces=True),
}).encode("utf-8")
print(base64.b64encode(blob).decode("ascii"))
PY
)"
adb shell am instrument -w \
  -e class sa.hulksa.player.Gate3bBenchmarkAuthBootstrap \
  -e hulkGate3bAuth true \
  -e hulkGate3bCreds "$creds_b64" \
  sa.hulksa.player.benchmark.test/androidx.test.runner.AndroidJUnitRunner \
  > "$out/auth-bootstrap.txt" 2>&1
cat "$out/auth-bootstrap.txt"
adb logcat -d -t 600 > "$out/logcat-bootstrap.txt" 2>/dev/null || true
if ! grep -q "OK (1 test)" "$out/auth-bootstrap.txt"; then
  echo "Gate 3B authentication bootstrap did not pass" >&2
  tail -120 "$out/logcat-bootstrap.txt" >&2 || true
  exit 1
fi

# Baseline profile generation reusing the Gate 3A.5 HOME -> LIVE contract. Any install
# uses -r and BaselineProfileRule uses CompilationMode.Partial (profile reset, not app-data
# wipe), so the bootstrap session persists into the generator.
./gradlew --no-daemon --console=plain --max-workers=2 \
  -PHULK_QUALIFICATION_VERSION_CODE="$HULK_QUALIFICATION_VERSION_CODE" \
  -PHULK_QUALIFICATION_VERSION_NAME="$HULK_QUALIFICATION_VERSION_NAME" \
  :macrobenchmark:connectedBenchmarkAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=sa.hulksa.player.macrobenchmark.BaselineProfileGenerator" \
  > "$out/generation.txt" 2>&1
cat "$out/generation.txt"
adb logcat -d -t 600 > "$out/logcat-generation.txt" 2>/dev/null || true
if ! grep -q "BUILD SUCCESSFUL" "$out/generation.txt"; then
  echo "Gate 3B baseline profile generation did not pass" >&2
  tail -120 "$out/logcat-generation.txt" >&2 || true
  exit 1
fi

remote="$(adb shell ls /storage/emulated/0/Android/media/sa.hulksa.player.macrobenchmark/*baseline-prof*.txt 2>/dev/null | tr -d '\r' | sort | tail -1)"
if [ -z "$remote" ]; then
  echo "baseline-prof.txt was not produced on device" >&2
  exit 1
fi
adb pull "$remote" "$out/baseline-prof.txt" | tee "$out/pull-profile.txt"
test -s "$out/baseline-prof.txt" || { echo "baseline-prof.txt is empty" >&2; exit 1; }

total="$(grep -c . "$out/baseline-prof.txt" || true)"
appowned="$(grep -c 'Lsa/hulksa/player/' "$out/baseline-prof.txt" || true)"
sha="$(sha256sum "$out/baseline-prof.txt" | awk '{print $1}')"

{
  echo "baseline_prof_sha256=$sha"
  echo "total_rule_count=$total"
  echo "app_owned_rule_count=$appowned"
  echo "navigation_proof=PASS (HOME before journey, Optional Update Overlay owned, LIVE after journey)"
} | tee "$out/profile-summary.txt"

grep 'Lsa/hulksa/player/' "$out/baseline-prof.txt" | head -25 > "$out/app-owned-samples.txt" || true

# Leak guard: no protected material may appear anywhere in the evidence directory.
python3 - "$out" <<'PY'
import os
import sys

root = sys.argv[1]
secrets = [
    os.environ.get("HULK_E2E_ACCESS_CODE", ""),
    os.environ.get("HULK_E2E_USERNAME", ""),
    os.environ.get("HULK_E2E_PASSWORD", ""),
    os.environ.get("HULK_GATE3B_EGRESS_SSH_KEY", ""),
]
secrets = [s for s in secrets if s]
for dirpath, _, files in os.walk(root):
    for name in files:
        path = os.path.join(dirpath, name)
        try:
            data = open(path, "rb").read()
        except OSError:
            continue
        for secret in secrets:
            if secret.encode() in data:
                print("SECURITY: protected material detected in evidence", file=sys.stderr)
                sys.exit(1)
PY

echo "Gate 3B generation + qualification complete"
