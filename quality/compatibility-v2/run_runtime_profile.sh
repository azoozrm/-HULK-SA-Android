#!/usr/bin/env bash
set -euo pipefail

profile="${1:?profile id is required}"
width="${2:?width px is required}"
height="${3:?height px is required}"
density="${4:?density dpi is required}"
font_scale="${5:?font scale is required}"
rotation="${6:?rotation is required}"
locale="${7:?BCP-47 locale is required}"
out="build/compatibility-v2/runtime/$profile"

bash quality/compatibility-v2/configure_emulator_profile.sh \
  "$profile" "$width" "$height" "$density" "$font_scale" "$rotation" "$locale" \
  "$out/PROFILE-CONFIG.txt"

adb install -r build/compatibility-v2/binaries/app-debug.apk
adb install -r build/compatibility-v2/binaries/app-debug-androidTest.apk
adb logcat -c
bash quality/compatibility-v2/collect_runtime_evidence.sh "$profile" "$out"
