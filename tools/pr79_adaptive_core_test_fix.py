#!/usr/bin/env python3
from pathlib import Path

path = Path("app/src/test/java/sa/hulksa/player/ui/adaptive/AdaptiveUiClassifierTest.kt")
text = path.read_text(encoding="utf-8")
old = "assertEquals(2, calculateAdaptiveGridColumns(360, 112, 12, maximumColumns = 6))"
new = "assertEquals(3, calculateAdaptiveGridColumns(360, 112, 12, maximumColumns = 6))"
old_count = text.count(old)
new_count = text.count(new)
if old_count == 1 and new_count == 0:
    text = text.replace(old, new, 1)
elif old_count == 0 and new_count == 1:
    pass
else:
    raise SystemExit(
        f"Unexpected compact-phone grid assertions: old={old_count}, new={new_count}",
    )
path.write_text(text, encoding="utf-8")
