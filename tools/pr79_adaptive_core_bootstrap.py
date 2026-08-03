#!/usr/bin/env python3
from pathlib import Path

path = Path("tools/pr79_adaptive_core.py")
text = path.read_text(encoding="utf-8")
old = '''replace_once(
    adaptive,
    ''' + "'''        navigationType = HulkNavigationType.TOP_BAR,\n'''" + ''',
    ''' + "'''        navigationType = HulkNavigationType.BOTTOM_BAR,\n'''" + ''',
)
'''
new = '''replace_once(
    adaptive,
    ''' + "'''val LocalAdaptiveUi = staticCompositionLocalOf {\n    AdaptiveUiState(\n        deviceClass = HulkDeviceClass.MOBILE,\n        windowWidthClass = HulkWindowWidthClass.COMPACT,\n        navigationType = HulkNavigationType.TOP_BAR,\n'''" + ''',
    ''' + "'''val LocalAdaptiveUi = staticCompositionLocalOf {\n    AdaptiveUiState(\n        deviceClass = HulkDeviceClass.MOBILE,\n        windowWidthClass = HulkWindowWidthClass.COMPACT,\n        navigationType = HulkNavigationType.BOTTOM_BAR,\n'''" + ''',
)
'''
if text.count(old) != 1:
    raise SystemExit(f"Expected one ambiguous navigation replacement block, found {text.count(old)}")
text = text.replace(old, new, 1)
path.write_text(text, encoding="utf-8")
