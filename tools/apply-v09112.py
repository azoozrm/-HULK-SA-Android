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

rw('app/build.gradle.kts', lambda t: t.replace('versionCode = 33', 'versionCode = 34').replace('versionName = "0.9.1.11"', 'versionName = "0.9.1.12"'))

def player(t):
    t = t.replace(
        'private enum class PlayerPanel { AUDIO, SUBTITLES, SPEED, RESIZE, QUALITY, SERVERS }',
        'private enum class PlayerPanel { AUDIO, SUBTITLES, SPEED, RESIZE, QUALITY, SERVERS, STREAM_INFO }'
    )
    t = t.replace(
        '                    onResize = { activePanel = PlayerPanel.RESIZE },\n                    onLock = { controlsLocked = true; controlsVisible = false },',
        '                    onResize = { activePanel = PlayerPanel.RESIZE },\n                    onStreamInfo = { activePanel = PlayerPanel.STREAM_INFO },\n                    onLock = { controlsLocked = true; controlsVisible = false },'
    )
    t = t.replace(
        '                PlayerPanel.SERVERS -> SimpleOptionsPanel(',
        '                PlayerPanel.STREAM_INFO -> StreamInfoPanel(\n                    quality = qualityLabel(videoHeight),\n                    audioTracks = audioTracks,\n                    videoTracks = videoTracks,\n                    subtitleTracks = subtitleTracks,\n                    bufferedPercent = bufferedPercent,\n                    onClose = { activePanel = null },\n                    modifier = Modifier.align(Alignment.CenterEnd),\n                )\n                PlayerPanel.SERVERS -> SimpleOptionsPanel('
    )
    t = t.replace(
        '    onResize: () -> Unit,\n    onLock: () -> Unit,',
        '    onResize: () -> Unit,\n    onStreamInfo: () -> Unit,\n    onLock: () -> Unit,'
    )
    t = t.replace(
        '            item { FocusButton("الصورة: $resizeLabel", onResize, primary = false, compact = true) }\n            item { FocusButton("قفل التحكم", onLock, primary = false, compact = true) }',
        '            item { FocusButton("الصورة: $resizeLabel", onResize, primary = false, compact = true) }\n            item { FocusButton("معلومات البث", onStreamInfo, primary = false, compact = true) }\n            item { FocusButton("قفل التحكم", onLock, primary = false, compact = true) }'
    )
    marker = '@Composable\nprivate fun SeekableProgressBar('
    panel = '''@Composable
private fun StreamInfoPanel(
    quality: String,
    audioTracks: List<PlayerTrackOption>,
    videoTracks: List<PlayerTrackOption>,
    subtitleTracks: List<PlayerTrackOption>,
    bufferedPercent: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedAudio = audioTracks.firstOrNull { it.selected } ?: audioTracks.firstOrNull()
    val selectedVideo = videoTracks.firstOrNull { it.selected } ?: videoTracks.firstOrNull()
    PlayerSidePanel("محلل البث المباشر", onClose, modifier) {
        Text("الجودة الفعلية: $quality", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("الفيديو: ${selectedVideo?.secondary?.ifBlank { selectedVideo.label } ?: "غير مكتشف"}", color = Color.White.copy(alpha = .82f), fontSize = 13.sp)
        Text("الصوت: ${selectedAudio?.secondary?.ifBlank { selectedAudio.label } ?: "لا يوجد مسار صوت مكتشف او انه غير مدعوم"}", color = if (selectedAudio == null) Color(0xFFFFB74D) else Color.White.copy(alpha = .82f), fontSize = 13.sp)
        Text("عدد مسارات الصوت: ${audioTracks.size}", color = Color.White.copy(alpha = .82f), fontSize = 13.sp)
        Text("عدد مسارات الترجمة: ${subtitleTracks.size}", color = Color.White.copy(alpha = .82f), fontSize = 13.sp)
        Text("التخزين المؤقت: $bufferedPercent%", color = Color.White.copy(alpha = .82f), fontSize = 13.sp)
        if (selectedAudio == null) {
            Text("التشخيص: الصورة تعمل لكن المشغل لم يكتشف مسار صوت قابلا للتشغيل. قد يكون المصدر بلا صوت او بترميز غير مدعوم.", color = Color(0xFFFFD180), fontSize = 12.sp)
        } else {
            Text("التشخيص: تم اكتشاف مسار صوت قابل للتشغيل.", color = Color(0xFF8CE99A), fontSize = 12.sp)
        }
    }
}

'''
    if marker not in t:
        raise SystemExit('SeekableProgressBar marker not found')
    t = t.replace(marker, panel + marker)
    return t

rw('app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt', player)
print('Applied v0.9.1.12 live stream inspector upgrade')
