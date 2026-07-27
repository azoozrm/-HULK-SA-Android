#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])
app = root / "app"

player = app / "src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt"
text = player.read_text(encoding="utf-8")
old_sig = '''    nextEpisodeTitle: String? = null,
    onPlayNextEpisode: (() -> Unit)? = null,
) {'''
new_sig = '''    nextEpisodeTitle: String? = null,
    onPlayNextEpisode: (() -> Unit)? = null,
    qaInitialPanel: String? = null,
    qaShowNextEpisode: Boolean = false,
) {'''
if new_sig not in text:
    if old_sig not in text:
        raise SystemExit("PlayerScreen signature not found")
    text = text.replace(old_sig, new_sig, 1)

old_state = '''    var controlsVisible by remember(request) { mutableStateOf(!request.isLive) }
    var browserVisible by remember(request) { mutableStateOf(false) }
    var activePanel by remember(request) { mutableStateOf<PlayerPanel?>(null) }'''
new_state = '''    var controlsVisible by remember(request, qaInitialPanel) { mutableStateOf(!request.isLive || qaInitialPanel != null) }
    var browserVisible by remember(request) { mutableStateOf(false) }
    var activePanel by remember(request, qaInitialPanel) {
        mutableStateOf(qaInitialPanel?.let { name -> runCatching { PlayerPanel.valueOf(name) }.getOrNull() })
    }'''
if new_state not in text:
    if old_state not in text:
        raise SystemExit("PlayerScreen panel state not found")
    text = text.replace(old_state, new_state, 1)

old_next = '''    var nextCountdown by remember(request) { mutableIntStateOf(-1) }'''
new_next = '''    var nextCountdown by remember(request, qaShowNextEpisode) {
        mutableIntStateOf(if (qaShowNextEpisode) NEXT_EPISODE_SECONDS else -1)
    }'''
if new_next not in text:
    if old_next not in text:
        raise SystemExit("PlayerScreen next countdown state not found")
    text = text.replace(old_next, new_next, 1)
player.write_text(text, encoding="utf-8")

manifest_dir = app / "src/debug"
manifest_dir.mkdir(parents=True, exist_ok=True)
(manifest_dir / "AndroidManifest.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity
            android:name=".qa.QaActivity"
            android:exported="true"
            android:screenOrientation="unspecified"
            android:theme="@style/Theme.HulkSA" />
    </application>
</manifest>
''', encoding="utf-8")

qa_dir = app / "src/debug/java/sa/hulksa/player/qa"
qa_dir.mkdir(parents=True, exist_ok=True)
source = Path(__file__).with_name("QaActivity.kt")
(qa_dir / "QaActivity.kt").write_text(source.read_text(encoding="utf-8"), encoding="utf-8")

print("Prepared debug-only emulator QA harness")
