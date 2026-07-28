#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT="${1:-$REPO_ROOT/project}"
ARCHIVE="$REPO_ROOT/HULK-SA-v0.9.1.20-PHASE1-FINAL-SOURCE(1).zip"

if [[ -e "$OUTPUT" ]]; then
  echo "Refusing to overwrite existing output: $OUTPUT" >&2
  exit 2
fi
if [[ ! -s "$ARCHIVE" ]]; then
  echo "Official source archive is missing: $ARCHIVE" >&2
  exit 2
fi

WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/hulk-compat-source-XXXXXX")"
cleanup() {
  rm -rf -- "$WORK_DIR"
}
trap cleanup EXIT

mkdir "$WORK_DIR/extracted"
unzip -q "$ARCHIVE" -d "$WORK_DIR/extracted"
SOURCE_ROOT="$(
  find "$WORK_DIR/extracted" -type f \
    \( -name settings.gradle.kts -o -name settings.gradle \) \
    -printf '%h\n' | head -n1
)"
if [[ -z "$SOURCE_ROOT" ]]; then
  echo "No Gradle project found in the official source archive" >&2
  exit 2
fi
cp -a "$SOURCE_ROOT" "$WORK_DIR/project"

python3 "$REPO_ROOT/tools/repair-v09120.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/tools/prepare-v0920-architecture.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/tools/prepare-v0930-adaptive-ui.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/tools/prepare-v0932-mobile-followup.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/tools/prepare-v0933-mobile-memory.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/tools/prepare-v0934-mobile-fixes.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/tools/prepare-v0935-tv-compatibility.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/tools/prepare-v0936-tcl-polish.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/tools/prepare-v0937-tcl-navigation-player.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/tools/prepare-v0938-back-focus-fix.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/tools/prepare-v0939-category-search-polish.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/tools/prepare-v0940-installer-favorites-fix.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/tools/prepare-v0941-rtl-reorder-login-keyboard.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/tools/prepare-v0942-category-position-memory.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/tools/prepare-v0943-category-player-state-fix.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/tools/prepare-v0944-favorites-home-polish.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/tools/prepare-v0945-xiaomi-search-safeareas.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/release/v0.9.3.16/v0946-main.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/release/v0.9.3.16/v0946-login.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/release/v0.9.3.16/v0946-player.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/release/v0.9.3.16/v0946-series.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/release/v0.9.3.17/v0947-navigation-categories.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/release/v0.9.3.17/v0947-player-panels.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/release/v0.9.3.17/v0947-details-favorites-downloads.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/release/v0.9.3.18/v0948-compatibility-responsive-fixes.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/release/v0.9.3.18/v0948-tv-focus-safeareas.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/release/v0.9.3.18/v0948-tv-search-edit-mode.py" "$WORK_DIR/project"
python3 "$REPO_ROOT/qa/compatibility/prepare-harness.py" "$WORK_DIR/project"

mkdir -p "$(dirname "$OUTPUT")"
mv "$WORK_DIR/project" "$OUTPUT"
echo "PASS: prepared reconstructed historical HULK SA source at $OUTPUT"
