#!/usr/bin/env python3
from pathlib import Path

path = Path("tools/pr79_matrix_expansion.py")
text = path.read_text(encoding="utf-8")
start_marker = 'collector = "quality/compatibility-v2/collect_runtime_evidence.sh"\n'
end_marker = 'spec_path = ROOT / "quality/compatibility-v2/config/evidence-spec.json"\n'
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit(f"Unable to locate collector generator block: start={start}, end={end}")
if text.count(start_marker) != 1 or text.count(end_marker) != 1:
    raise SystemExit("Collector generator markers are not unique")

replacement = '''collector = "quality/compatibility-v2/collect_runtime_evidence.sh"
slash = chr(92)
collector_old = (
    "  DEVICE-PROFILE.txt " + slash + "\\n"
    "  WINDOW-METRICS.txt " + slash + "\\n"
)
collector_new = (
    "  DEVICE-PROFILE.txt " + slash + "\\n"
    "  WINDOW-CLASSIFICATION.txt " + slash + "\\n"
    "  WINDOW-METRICS.txt " + slash + "\\n"
)
replace_once(collector, collector_old, collector_new)

'''
text = text[:start] + replacement + text[end:]
path.write_text(text, encoding="utf-8")

updated = path.read_text(encoding="utf-8")
for marker in ("collector_old =", 'slash = chr(92)', '"  WINDOW-CLASSIFICATION.txt " + slash'):
    if marker not in updated:
        raise SystemExit(f"Missing compatibility-fix marker: {marker}")
if "'''  DEVICE-PROFILE.txt" in updated:
    raise SystemExit("Unsafe triple-quoted collector match remains")
