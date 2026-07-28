#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT="${1:-$REPO_ROOT/project}"

if [[ -e "$OUTPUT" ]]; then
  echo "Refusing to overwrite existing output: $OUTPUT" >&2
  exit 2
fi

WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/hulk-canonical-lab-XXXXXX")"
cleanup() {
  rm -rf -- "$WORK_DIR"
}
trap cleanup EXIT

mkdir -p "$WORK_DIR/project"
cp -a "$REPO_ROOT/app" "$WORK_DIR/project/"
cp -a "$REPO_ROOT/gradle" "$WORK_DIR/project/"
cp -a "$REPO_ROOT/gradlew" "$REPO_ROOT/gradlew.bat" "$WORK_DIR/project/"
cp -a "$REPO_ROOT/build.gradle.kts" "$REPO_ROOT/settings.gradle.kts" "$REPO_ROOT/gradle.properties" "$WORK_DIR/project/"
chmod +x "$WORK_DIR/project/gradlew"

rm -rf "$WORK_DIR/project/app/build" "$WORK_DIR/project/.gradle" "$WORK_DIR/project/build"
python3 "$REPO_ROOT/qa/compatibility/prepare-harness.py" "$WORK_DIR/project"

mkdir -p "$(dirname "$OUTPUT")"
mv "$WORK_DIR/project" "$OUTPUT"
echo "PASS: prepared canonical HULK SA source with debug-only compatibility harness at $OUTPUT"
