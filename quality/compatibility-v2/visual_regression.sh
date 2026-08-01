#!/usr/bin/env bash
set -euo pipefail

actual="${1:?actual full-window PNG is required}"
expected="${2:?approved full-window baseline PNG is required}"
out="${3:?output directory is required}"
mkdir -p "$out"

for file in "$actual" "$expected"; do
  test -s "$file" || { echo "Missing visual input: $file" >&2; exit 2; }
done
command -v identify >/dev/null
command -v compare >/dev/null

actual_size="$(identify -format '%wx%h' "$actual")"
expected_size="$(identify -format '%wx%h' "$expected")"
if [[ "$actual_size" != "$expected_size" ]]; then
  echo "Full-window dimensions differ: actual=$actual_size expected=$expected_size" >&2
  exit 1
fi

cp "$actual" "$out/actual.png"
cp "$expected" "$out/expected.png"
set +e
metric="$(compare -metric AE "$actual" "$expected" "$out/diff.png" 2>&1)"
status=$?
set -e

cat > "$out/VISUAL-METADATA.json" <<JSON
{
  "actual_dimensions": "$actual_size",
  "expected_dimensions": "$expected_size",
  "absolute_error_pixels": "$metric",
  "cropped": false,
  "normalized": false,
  "baseline_updated": false
}
JSON

if [[ "$status" -ne 0 || "$metric" != "0" ]]; then
  echo "Visual regression detected: $metric pixels differ" >&2
  exit 1
fi
