#!/usr/bin/env python3
from pathlib import Path

path = Path("tools/pr79_adaptive_core.py")
text = path.read_text(encoding="utf-8")
targets = {
    "import androidx.compose.ui.test.assertDoesNotExist",
    "import androidx.compose.ui.test.fetchSemanticsNode",
}
counts = {target: 0 for target in targets}
kept: list[str] = []
for line in text.splitlines(keepends=True):
    normalized = line.rstrip("\r\n")
    if normalized in targets:
        counts[normalized] += 1
    else:
        kept.append(line)

unexpected = {target: count for target, count in counts.items() if count != 1}
if unexpected:
    raise SystemExit(f"Unexpected generated Compose import counts: {unexpected}")

path.write_text("".join(kept), encoding="utf-8")
