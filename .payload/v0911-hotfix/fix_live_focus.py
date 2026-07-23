from pathlib import Path
import sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("project")
path = root / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
text = path.read_text(encoding="utf-8")

old_play = "left = channelRequester; right = favoriteRequester; up = channelRequester; down = channelRequester"
new_play = "left = favoriteRequester; right = channelRequester; up = channelRequester; down = channelRequester"
old_favorite = "left = playRequester; right = channelRequester; up = channelRequester; down = channelRequester"
new_favorite = "left = channelRequester; right = playRequester; up = channelRequester; down = channelRequester"

if old_play in text:
    text = text.replace(old_play, new_play)
elif new_play not in text:
    raise SystemExit("Live play focus mapping not found")

if old_favorite in text:
    text = text.replace(old_favorite, new_favorite)
elif new_favorite not in text:
    raise SystemExit("Live favorite focus mapping not found")

path.write_text(text, encoding="utf-8")
print("RTL live focus mapping fixed")
