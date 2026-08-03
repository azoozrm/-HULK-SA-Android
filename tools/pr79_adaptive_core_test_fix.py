#!/usr/bin/env python3
from pathlib import Path

path = Path("app/src/test/java/sa/hulksa/player/ui/adaptive/AdaptiveUiClassifierTest.kt")
text = path.read_text(encoding="utf-8")
old = "assertEquals(2, calculateAdaptiveGridColumns(360, 112, 12, maximumColumns = 6))"
new = "assertEquals(3, calculateAdaptiveGridColumns(360, 112, 12, maximumColumns = 6))"
if text.count(old) != 1:
    raise SystemExit(f"Expected one compact-phone grid assertion, found {text.count(old)}")
text = text.replace(old, new, 1)
path.write_text(text, encoding="utf-8")
