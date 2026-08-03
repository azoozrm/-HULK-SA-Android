#!/usr/bin/env python3
from pathlib import Path

path = Path("tools/pr79_adaptive_core.py")
text = path.read_text(encoding="utf-8")
for unsupported_import in (
    "import androidx.compose.ui.test.assertDoesNotExist\\n",
    "import androidx.compose.ui.test.fetchSemanticsNode\\n",
):
    count = text.count(unsupported_import)
    if count != 1:
        raise SystemExit(
            f"Expected one generated unsupported import, found {count}: {unsupported_import!r}",
        )
    text = text.replace(unsupported_import, "", 1)
path.write_text(text, encoding="utf-8")
