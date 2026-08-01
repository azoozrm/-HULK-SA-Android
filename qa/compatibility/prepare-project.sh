#!/usr/bin/env bash
set -euo pipefail

LAB_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT="${1:-$LAB_ROOT/project}"
PRODUCT_ROOT="${2:-$LAB_ROOT}"

LAB_ROOT="$(cd "$LAB_ROOT" && pwd)"
PRODUCT_ROOT="$(cd "$PRODUCT_ROOT" && pwd)"

if [[ -e "$OUTPUT" ]]; then
  echo "Refusing to overwrite existing output: $OUTPUT" >&2
  exit 2
fi
for required in app gradle gradlew gradlew.bat build.gradle.kts settings.gradle.kts gradle.properties; do
  if [[ ! -e "$PRODUCT_ROOT/$required" ]]; then
    echo "Product source root is incomplete: missing $PRODUCT_ROOT/$required" >&2
    exit 2
  fi
done
if [[ ! -f "$LAB_ROOT/qa/compatibility/prepare-harness.py" ]]; then
  echo "Lab source root is incomplete: prepare-harness.py is missing" >&2
  exit 2
fi

WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/hulk-canonical-lab-XXXXXX")"
cleanup() {
  rm -rf -- "$WORK_DIR"
}
trap cleanup EXIT

mkdir -p "$WORK_DIR/project"
cp -a "$PRODUCT_ROOT/app" "$WORK_DIR/project/"
cp -a "$PRODUCT_ROOT/gradle" "$WORK_DIR/project/"
cp -a "$PRODUCT_ROOT/gradlew" "$PRODUCT_ROOT/gradlew.bat" "$WORK_DIR/project/"
cp -a "$PRODUCT_ROOT/build.gradle.kts" "$PRODUCT_ROOT/settings.gradle.kts" "$PRODUCT_ROOT/gradle.properties" "$WORK_DIR/project/"
chmod +x "$WORK_DIR/project/gradlew"

rm -rf "$WORK_DIR/project/app/build" "$WORK_DIR/project/.gradle" "$WORK_DIR/project/build"
python3 "$LAB_ROOT/qa/compatibility/prepare-harness.py" "$WORK_DIR/project"

mkdir -p "$(dirname "$OUTPUT")"
mv "$WORK_DIR/project" "$OUTPUT"
echo "PASS: prepared product source from $PRODUCT_ROOT with lab harness from $LAB_ROOT at $OUTPUT"
