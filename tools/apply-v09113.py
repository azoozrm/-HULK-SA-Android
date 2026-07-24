#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])


def rw(rel, fn):
    p = root / rel
    text = p.read_text()
    new = fn(text)
    if new == text:
        raise SystemExit(f'No change applied to {rel}')
    p.write_text(new)


rw(
    'app/build.gradle.kts',
    lambda t: t.replace('versionCode = 34', 'versionCode = 35')
               .replace('versionName = "0.9.1.12"', 'versionName = "0.9.1.13"')
)


def player(t):
    # Keep live playback controls hidden after every channel change. OK still reveals them.
    t = t.replace(
        'var controlsVisible by remember(request) { mutableStateOf(true) }',
        'var controlsVisible by remember(request) { mutableStateOf(!request.isLive) }'
    )

    # Remove the customer-facing stream inspector entry and panel.
    t = t.replace(
        'private enum class PlayerPanel { AUDIO, SUBTITLES, SPEED, RESIZE, QUALITY, SERVERS, STREAM_INFO }',
        'private enum class PlayerPanel { AUDIO, SUBTITLES, SPEED, RESIZE, QUALITY, SERVERS }'
    )
    t = t.replace(
        '                    onResize = { activePanel = PlayerPanel.RESIZE },\n                    onStreamInfo = { activePanel = PlayerPanel.STREAM_INFO },\n                    onLock = { controlsLocked = true; controlsVisible = false },',
        '                    onResize = { activePanel = PlayerPanel.RESIZE },\n                    onLock = { controlsLocked = true; controlsVisible = false },'
    )
    t = t.replace(
        '                PlayerPanel.STREAM_INFO -> StreamInfoPanel(\n                    quality = qualityLabel(videoHeight),\n                    audioTracks = audioTracks,\n                    videoTracks = videoTracks,\n                    subtitleTracks = subtitleTracks,\n                    bufferedPercent = bufferedPercent,\n                    onClose = { activePanel = null },\n                    modifier = Modifier.align(Alignment.CenterEnd),\n                )\n',
        ''
    )
    t = t.replace(
        '    onResize: () -> Unit,\n    onStreamInfo: () -> Unit,\n    onLock: () -> Unit,',
        '    onResize: () -> Unit,\n    onLock: () -> Unit,'
    )
    t = t.replace(
        '            item { FocusButton("الصورة: $resizeLabel", onResize, primary = false, compact = true) }\n            item { FocusButton("معلومات البث", onStreamInfo, primary = false, compact = true) }\n            item { FocusButton("قفل التحكم", onLock, primary = false, compact = true) }',
        '            item { FocusButton("الصورة: $resizeLabel", onResize, primary = false, compact = true) }\n            item { FocusButton("قفل التحكم", onLock, primary = false, compact = true) }'
    )

    start = t.find('@Composable\nprivate fun StreamInfoPanel(')
    end_marker = '@Composable\nprivate fun SeekableProgressBar('
    if start == -1:
        raise SystemExit('StreamInfoPanel start not found')
    end = t.find(end_marker, start)
    if end == -1:
        raise SystemExit('SeekableProgressBar marker not found after StreamInfoPanel')
    t = t[:start] + t[end:]

    # Capture live channel arrows at the parent level regardless of which OSD button owns focus.
    # This makes repeated UP/DOWN presses switch channels continuously and keeps the OSD hidden.
    t = t.replace(
        '                if (request.isLive && surfaceFocused) {\n                    when (keyCode) {\n                        AndroidKeyEvent.KEYCODE_DPAD_UP,\n                        AndroidKeyEvent.KEYCODE_CHANNEL_UP,\n                        AndroidKeyEvent.KEYCODE_MEDIA_NEXT,\n                        -> { switchRelative(1); return@onPreviewKeyEvent true }\n                        AndroidKeyEvent.KEYCODE_DPAD_DOWN,\n                        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,\n                        AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,\n                        -> { switchRelative(-1); return@onPreviewKeyEvent true }\n                    }\n                }',
        '                if (request.isLive) {\n                    when (keyCode) {\n                        AndroidKeyEvent.KEYCODE_DPAD_UP,\n                        AndroidKeyEvent.KEYCODE_CHANNEL_UP,\n                        AndroidKeyEvent.KEYCODE_MEDIA_NEXT,\n                        -> {\n                            controlsVisible = false\n                            activePanel = null\n                            switchRelative(1)\n                            return@onPreviewKeyEvent true\n                        }\n                        AndroidKeyEvent.KEYCODE_DPAD_DOWN,\n                        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,\n                        AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,\n                        -> {\n                            controlsVisible = false\n                            activePanel = null\n                            switchRelative(-1)\n                            return@onPreviewKeyEvent true\n                        }\n                    }\n                }'
    )
    return t


rw('app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt', player)
print('Applied v0.9.1.13 continuous live channel navigation and customer OSD cleanup')
