#!/usr/bin/env bash
# Temporary Gate 3B emulator qualification probe body. Not a deliverable.
set -euo pipefail

out=build/gate3b-probe
mkdir -p "$out"

adb wait-for-device
if adb root; then
  echo "adb_root=PASS" | tee "$out/device.txt"
else
  echo "adb_root=FAIL" | tee "$out/device.txt"
fi
sleep 5
adb wait-for-device

{
  echo "host_kvm_present=$( [[ -e /dev/kvm ]] && echo yes || echo no )"
  echo "sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "release=$(adb shell getprop ro.build.version.release | tr -d '\r')"
  echo "build_type=$(adb shell getprop ro.build.type | tr -d '\r')"
  echo "debuggable=$(adb shell getprop ro.debuggable | tr -d '\r')"
  echo "product=$(adb shell getprop ro.product.name | tr -d '\r')"
  echo "device=$(adb shell getprop ro.product.device | tr -d '\r')"
  echo "abi=$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
  echo "fingerprint=$(adb shell getprop ro.build.fingerprint | tr -d '\r')"
  echo "shell_id=$(adb shell id | tr -d '\r')"
} | tee -a "$out/device.txt"

if adb shell input keyevent 20; then echo "dpad_down_ok"; else echo "dpad_down_fail"; fi | tee "$out/dpad.txt"
if adb shell input keyevent 22; then echo "dpad_right_ok"; else echo "dpad_right_fail"; fi | tee -a "$out/dpad.txt"

adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk | tee "$out/install-app.txt"
adb install -r macrobenchmark/build/outputs/apk/benchmark/macrobenchmark-benchmark.apk | tee "$out/install-macrobenchmark.txt"

set +e
./gradlew --no-daemon --console=plain --max-workers=2 \
  :macrobenchmark:connectedBenchmarkAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=sa.hulksa.player.macrobenchmark.BaselineProfileGenerator" \
  2>&1 | tee "$out/generation.log"
generation_status=${PIPESTATUS[0]}
set -e
echo "generation_status=$generation_status" | tee "$out/generation-status.txt"

find macrobenchmark/build -type f \( -name 'baseline-prof.txt' -o -name '*.prof' -o -name '*.profm' \) -print 2>/dev/null | tee "$out/profile-files.txt" || true
mkdir -p "$out/profiles"
while IFS= read -r f; do
  cp "$f" "$out/profiles/$(echo "$f" | tr '/' '_')" 2>/dev/null || true
done < "$out/profile-files.txt"

adb logcat -d -t 600 > "$out/logcat-tail.txt" 2>/dev/null || true
