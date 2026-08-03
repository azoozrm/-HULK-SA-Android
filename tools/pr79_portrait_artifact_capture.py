#!/usr/bin/env python3
from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    target = Path(path)
    text = target.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(
            f"{path}: expected one exact match, found {count}: {old[:140]!r}",
        )
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


collector = "quality/compatibility-v2/collect_runtime_evidence.sh"

replace_once(
    collector,
    '''if [[ "$instrumentation_status" -ne 0 ]]; then status="$instrumentation_status"; fi
if [[ "$parser_status" -ne 0 ]]; then status="$parser_status"; fi

adb shell dumpsys package "$package" > "$out/INSTALLED-PACKAGE-COLLECTOR-DUMP.txt" 2>&1 || true
''',
    '''if [[ "$instrumentation_status" -ne 0 ]]; then status="$instrumentation_status"; fi
if [[ "$parser_status" -ne 0 ]]; then status="$parser_status"; fi

portrait_evidence_required=false
if [[ "$test_class" == *"#phonePortraitLoginFieldsAcceptTypingWithoutCrash" ]]; then
  portrait_evidence_required=true
  app_evidence_dir="/sdcard/Android/data/$package/files/compatibility-v2"
  : > "$out/PORTRAIT-EVIDENCE-PULL.txt"
  for evidence_name in \\
    portrait-login-ime-stable.png \\
    portrait-login-ime-stable.xml \\
    portrait-login-ime-actions-reachable.png \\
    portrait-login-ime-actions-reachable.xml; do
    set +e
    pull_output="$(adb pull "$app_evidence_dir/$evidence_name" "$out/$evidence_name" 2>&1)"
    pull_status=$?
    set -e
    {
      echo "file=$evidence_name"
      echo "status=$pull_status"
      echo "output=${pull_output//$'\\n'/ | }"
    } >> "$out/PORTRAIT-EVIDENCE-PULL.txt"
    if [[ "$pull_status" -ne 0 ]]; then
      status=1
    fi
  done
fi

adb shell dumpsys package "$package" > "$out/INSTALLED-PACKAGE-COLLECTOR-DUMP.txt" 2>&1 || true
''',
)

replace_once(
    collector,
    '''done

(
  cd "$out"
''',
    '''done

if [[ "$portrait_evidence_required" == true ]]; then
  for portrait_required in \\
    PORTRAIT-EVIDENCE-PULL.txt \\
    portrait-login-ime-stable.png \\
    portrait-login-ime-stable.xml \\
    portrait-login-ime-actions-reachable.png \\
    portrait-login-ime-actions-reachable.xml; do
    if [[ ! -s "$out/$portrait_required" ]]; then
      echo "Missing mandatory portrait runtime evidence: $portrait_required" >&2
      status=1
    fi
  done
fi

(
  cd "$out"
''',
)

text = Path(collector).read_text(encoding="utf-8")
for marker in (
    "portrait_evidence_required=false",
    "PORTRAIT-EVIDENCE-PULL.txt",
    "portrait-login-ime-actions-reachable.png",
    "Missing mandatory portrait runtime evidence",
):
    if marker not in text:
        raise SystemExit(f"Missing portrait artifact capture marker: {marker}")
