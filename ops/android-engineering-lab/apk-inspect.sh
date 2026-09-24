#!/usr/bin/env bash
# apk-inspect.sh — deterministic, bounded APK identity evidence.
#
# Usage: apk-inspect.sh <apk-file>
#
# Emits stable key=value lines describing package identity, signer, debuggability,
# profileable declaration, ABI set, packaged baseline profile state and whether the APK
# is a target application APK or a macrobenchmark test APK. It reuses the Android SDK
# tools already present on the lab and never writes inside the repository.

set -Eeuo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=lib-lab.sh
source "$SCRIPT_DIR/lib-lab.sh"

[[ $# -eq 1 ]] || {
  echo 'Usage: apk-inspect.sh <apk-file>' >&2
  exit 2
}

APK=$1
[[ -f "$APK" ]] || {
  echo "STOP: APK not found: $APK" >&2
  exit 3
}

AAPT2=$(lab_aapt2)
APKSIGNER=$(lab_apksigner)
[[ -x "$AAPT2" ]] || { echo "STOP: aapt2 not executable: $AAPT2" >&2; exit 4; }
[[ -x "$APKSIGNER" ]] || { echo "STOP: apksigner not executable: $APKSIGNER" >&2; exit 4; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

BADGING=$TMP/badging.txt
"$AAPT2" dump badging "$APK" >"$BADGING" 2>/dev/null || true
"$AAPT2" dump xmltree --file AndroidManifest.xml "$APK" >"$TMP/manifest.xml" 2>/dev/null || true

package=$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" "$BADGING" | head -n1)
version_code=$(sed -n "s/^package:.*versionCode='\([^']*\)'.*/\1/p" "$BADGING" | head -n1)
version_name=$(sed -n "s/^package:.*versionName='\([^']*\)'.*/\1/p" "$BADGING" | head -n1)
min_sdk=$(sed -n "s/^minSdkVersion:'\([^']*\)'.*/\1/p" "$BADGING" | head -n1)
target_sdk=$(sed -n "s/^targetSdkVersion:'\([^']*\)'.*/\1/p" "$BADGING" | head -n1)
launchable=$(sed -n "s/^launchable-activity: name='\([^']*\)'.*/\1/p" "$BADGING" | head -n1)
leanback=$(sed -n "s/^leanback-launchable-activity: name='\([^']*\)'.*/\1/p" "$BADGING" | head -n1)
abis=$(sed -n "s/^native-code: \(.*\)$/\1/p" "$BADGING" | head -n1 | tr -d "'" | tr ' ' ',')

if grep -q '^application-debuggable$' "$BADGING"; then
  debuggable=yes
else
  debuggable=no
fi

if grep -qE 'E: profileable' "$TMP/manifest.xml"; then
  profileable=yes
else
  profileable=no
fi

instrumentation_target=$(lab_instrumentation_target_package "$TMP/manifest.xml")

if [[ -n "$instrumentation_target" || "$package" == *".macrobenchmark" || "$package" == *".test" ]]; then
  apk_kind=test-apk
elif [[ -n "$package" ]]; then
  apk_kind=target-app
else
  apk_kind=unknown
fi

sha256=$(sha256sum "$APK" | awk '{print $1}')

signer_sha256=$( ("$APKSIGNER" verify --print-certs "$APK" 2>/dev/null || true) \
  | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n1)
signer_label=$("$APKSIGNER" verify --print-certs "$APK" 2>/dev/null | sed -n 's/^Signer #1 certificate DN: //p' | head -n1 || true)
schemes=$( ("$APKSIGNER" verify -v "$APK" 2>/dev/null || true) \
  | sed -n 's/^Verified using \(v[0-9]\).*/\1/p' | paste -sd, -)

zip_listing=$TMP/entries.txt
unzip -Z1 "$APK" >"$zip_listing" 2>/dev/null || true

baseline_prof="absent"
baseline_profm="absent"
if grep -qx 'assets/dexopt/baseline.prof' "$zip_listing"; then
  unzip -p "$APK" assets/dexopt/baseline.prof >"$TMP/baseline.prof" 2>/dev/null || true
  h=$(sha256sum "$TMP/baseline.prof" | awk '{print $1}')
  s=$(wc -c <"$TMP/baseline.prof")
  baseline_prof="present sha256=$h size=$s"
fi
if grep -qx 'assets/dexopt/baseline.profm' "$zip_listing"; then
  unzip -p "$APK" assets/dexopt/baseline.profm >"$TMP/baseline.profm" 2>/dev/null || true
  h=$(sha256sum "$TMP/baseline.profm" | awk '{print $1}')
  s=$(wc -c <"$TMP/baseline.profm")
  baseline_profm="present sha256=$h size=$s"
fi

lab_kv apk_path "$APK"
lab_kv apk_sha256 "$sha256"
lab_kv apk_kind "$apk_kind"
lab_kv package "$package"
lab_kv version_code "$version_code"
lab_kv version_name "$version_name"
lab_kv min_sdk "$min_sdk"
lab_kv target_sdk "$target_sdk"
lab_kv debuggable "$debuggable"
lab_kv profileable_declared "$profileable"
lab_kv abis "$abis"
lab_kv launchable_activity "$launchable"
lab_kv leanback_launchable_activity "$leanback"
lab_kv instrumentation_target_package "$instrumentation_target"
lab_kv signer1_certificate_dn "$signer_label"
lab_kv signer1_sha256 "$signer_sha256"
lab_kv signature_schemes "$schemes"
lab_kv baseline_prof "$baseline_prof"
lab_kv baseline_profm "$baseline_profm"
lab_kv inspected_utc "$(lab_now_utc)"
