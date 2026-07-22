#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else "project")


def replace_once(path: str, old: str, new: str) -> None:
    file = ROOT / path
    text = file.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f"Expected block not found: {path}\n{old[:120]}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


# Version metadata.
replace_once(
    "app/build.gradle.kts",
    'versionCode = 13\n        versionName = "0.6.3"',
    'versionCode = 14\n        versionName = "0.6.4"',
)

# Player: keep a PlayerView reference, restore controls from remote keys, fix Back behavior,
# and inset only the native controller so the timeline is not clipped by TV overscan.
replace_once(
    "app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt",
    '    var surfaceFocused by remember { mutableStateOf(false) }\n',
    '    var surfaceFocused by remember { mutableStateOf(false) }\n    var playerView by remember(request) { mutableStateOf<PlayerView?>(null) }\n',
)
replace_once(
    "app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt",
    '''    BackHandler {
        if (browserVisible) browserVisible = false else onBack()
    }''',
    '''    BackHandler {
        when {
            browserVisible -> browserVisible = false
            !controlsVisible -> {
                controlsVisible = true
                playerView?.showController()
            }
            else -> onBack()
        }
    }''',
)
replace_once(
    "app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt",
    '''                if (!controlsVisible) {
                    controlsVisible = true
                    true
                } else {
                    false
                }''',
    '''                if (!request.isLive) {
                    when (keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        AndroidKeyEvent.KEYCODE_DPAD_UP,
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                        AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
                        AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
                        -> {
                            if (!controlsVisible) {
                                controlsVisible = true
                                playerView?.showController()
                                return@onPreviewKeyEvent true
                            }
                            playerView?.showController()
                        }
                    }
                }
                if (!controlsVisible) {
                    controlsVisible = true
                    playerView?.showController()
                    true
                } else {
                    false
                }''',
)
replace_once(
    "app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt",
    '''                    keepScreenOn = true
                    this.player = player
                }''',
    '''                    keepScreenOn = true
                    this.player = player
                    playerView = this
                    post {
                        val density = resources.displayMetrics.density
                        findViewById<View>(androidx.media3.ui.R.id.exo_controller)?.setPadding(
                            (18 * density).toInt(),
                            (8 * density).toInt(),
                            (18 * density).toInt(),
                            (34 * density).toInt(),
                        )
                    }
                }''',
)
replace_once(
    "app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt",
    '''                view.player = player
                view.useController = !request.isLive
                view.resizeMode = resizeModes[resizeModeIndex]''',
    '''                view.player = player
                playerView = view
                view.useController = !request.isLive
                view.resizeMode = resizeModes[resizeModeIndex]''',
)

# Episode cards: show live percentage, transferred size and a mini progress bar.
replace_once(
    "app/src/main/java/sa/hulksa/player/ui/screens/SeriesScreen.kt",
    '''        Spacer(Modifier.height(6.dp))
        FocusButton(
            text = when (download?.status) {
                OfflineStatus.COMPLETED -> "✓ تم التحميل"
                OfflineStatus.QUEUED,
                OfflineStatus.CHECKING,
                OfflineStatus.DOWNLOADING,
                -> "↓ جاري التحميل"
                OfflineStatus.PAUSED,
                OfflineStatus.WAITING_NETWORK,
                OfflineStatus.WAITING_STORAGE,
                -> "⏸ بانتظار الاستكمال"
                OfflineStatus.FAILED -> "↻ إعادة التحميل"
                null -> "↓ تحميل الحلقة"
            },
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth(),
            primary = false,
            compact = true,
            enabled = download == null || download.status == OfflineStatus.FAILED,
        )''',
    '''        Spacer(Modifier.height(6.dp))
        if (download != null && download.status != OfflineStatus.COMPLETED && download.status != OfflineStatus.FAILED) {
            val percent = (download.progress * 100).toInt()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White.copy(alpha = .06f))
                    .padding(horizontal = 9.dp, vertical = 7.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (download.status) {
                            OfflineStatus.PAUSED -> "متوقف مؤقتا"
                            OfflineStatus.WAITING_NETWORK -> "بانتظار الشبكة"
                            OfflineStatus.WAITING_STORAGE -> "بانتظار التخزين"
                            OfflineStatus.CHECKING -> "فحص الحجم والمساحة"
                            OfflineStatus.QUEUED -> "في قائمة الانتظار"
                            else -> "جاري تحميل الحلقة"
                        },
                        color = colors.text,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text("$percent%", color = colors.goldBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = .14f)),
                ) {
                    Box(
                        Modifier.fillMaxWidth(download.progress).height(4.dp)
                            .background(colors.goldBright),
                    )
                }
                if (download.totalBytes > 0L) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${episodeFormatBytes(download.bytesDownloaded)} / ${episodeFormatBytes(download.totalBytes)}",
                        color = colors.textMuted,
                        fontSize = 8.sp,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        FocusButton(
            text = when (download?.status) {
                OfflineStatus.COMPLETED -> "✓ تم التحميل"
                OfflineStatus.QUEUED,
                OfflineStatus.CHECKING,
                OfflineStatus.DOWNLOADING,
                -> "↓ جاري التحميل ${(download.progress * 100).toInt()}%"
                OfflineStatus.PAUSED -> "▶ استكمال التحميل"
                OfflineStatus.WAITING_NETWORK -> "بانتظار عودة الشبكة"
                OfflineStatus.WAITING_STORAGE -> "بانتظار وحدة التخزين"
                OfflineStatus.FAILED -> "↻ إعادة التحميل"
                null -> "↓ تحميل الحلقة"
            },
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth(),
            primary = false,
            compact = true,
            enabled = download == null || download.status == OfflineStatus.FAILED,
        )''',
)
replace_once(
    "app/src/main/java/sa/hulksa/player/ui/screens/SeriesScreen.kt",
    '\n}\n',
    '''
}

private fun episodeFormatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(java.util.Locale.US, "%.1f GB", mb / 1024.0)
    } else {
        String.format(java.util.Locale.US, "%.0f MB", mb)
    }
}
''',
)

# Download cards: remove duplicated episode title and stabilize mixed Arabic/number lines.
replace_once(
    "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt",
    '''            Text(
                item.title,
                color = colors.text,''',
    '''            val cleanDownloadTitle = if (item.seriesTitle != null && item.episodeNumber != null) {
                "الحلقة ${item.episodeNumber}"
            } else {
                item.title
            }
            Text(
                cleanDownloadTitle,
                color = colors.text,''',
)
replace_once(
    "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt",
    'Text(sizeLine, color = colors.textMuted, fontSize = 9.sp, maxLines = 1)',
    'Text("\\u200E$sizeLine", color = colors.textMuted, fontSize = 9.sp, maxLines = 1)',
)
replace_once(
    "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt",
    '"${formatBytes(item.bytesPerSecond)}/ث  •  المتبقي ${formatEta(item.etaSeconds)}",',
    '"\\u200E${formatBytes(item.bytesPerSecond)}/ث  •  المتبقي ${formatEta(item.etaSeconds)}",',
)

print("HULK SA v0.6.4 patch applied")
