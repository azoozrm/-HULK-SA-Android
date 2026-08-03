#!/usr/bin/env python3
from pathlib import Path

path = Path("app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt")
text = path.read_text(encoding="utf-8")

# HulkApp already applies safeDrawing insets to the complete non-player phone shell.
# A second navigationBarsPadding on the bottom bar would double the bottom inset.
text = text.replace("import androidx.compose.foundation.layout.navigationBarsPadding\n", "")
text = text.replace("            .navigationBarsPadding()\n", "")

anchor = "import sa.hulksa.player.ui.adaptive.HulkNavigationType\n"
replacement = (
    "import sa.hulksa.player.ui.adaptive.HulkNavigationType\n"
    "import sa.hulksa.player.ui.adaptive.HulkOrientation\n"
    "import sa.hulksa.player.ui.adaptive.HulkWindowHeightClass\n"
)
if text.count(anchor) != 1:
    raise SystemExit(f"Expected one adaptive navigation import anchor, found {text.count(anchor)}")
text = text.replace(anchor, replacement, 1)

for required in (
    'testTag("mobile-bottom-navigation")',
    "HulkOrientation.LANDSCAPE",
    "HulkWindowHeightClass.COMPACT",
):
    if required not in text:
        raise SystemExit(f"Missing required bottom-navigation marker: {required}")
if "navigationBarsPadding" in text:
    raise SystemExit("Bottom navigation still applies a duplicate navigation-bar inset")

path.write_text(text, encoding="utf-8")
