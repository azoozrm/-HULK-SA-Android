from pathlib import Path
import re, sys
r=Path(sys.argv[1])
m=r/'app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt'
p=r/'app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt'
g=r/'app/build.gradle.kts'
M=m.read_text(); P=p.read_text(); G=g.read_text()
G=G.replace('versionCode = 24','versionCode = 25').replace('versionName = "0.9.1.2"','versionName = "0.9.1.3"')
M=M.replace('.height(if (isTv) 236.dp else 222.dp)', '.height(if (isTv) 258.dp else 244.dp)', 1)
old='''            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (item.status) {
                    OfflineStatus.COMPLETED -> FocusButton(
                        "تشغيل",
                        { onPlay(item) },
                        compact = true,
                        modifier = Modifier.weight(1f).restoreFocus(restoreFocus, actionRequester),
                    )
                    OfflineStatus.FAILED -> FocusButton(
                        "اعادة",
                        { onRetry(item) },
                        compact = true,
                        modifier = Modifier.weight(1f).restoreFocus(restoreFocus, actionRequester),
                    )
                    OfflineStatus.PAUSED,
                    OfflineStatus.WAITING_SCHEDULE,
                    OfflineStatus.WAITING_NETWORK,
                    OfflineStatus.WAITING_STORAGE,
                    -> FocusButton(
                        "استئناف",
                        { onRetry(item) },
                        compact = true,
                        modifier = Modifier.weight(1f).restoreFocus(restoreFocus, actionRequester),
                    )
                    OfflineStatus.QUEUED,
                    OfflineStatus.CHECKING,
                    OfflineStatus.DOWNLOADING,
                    -> FocusButton(
                        "ايقاف مؤقت",
                        { onRetry(item) },
                        primary = false,
                        compact = true,
                        modifier = Modifier.weight(1f).restoreFocus(restoreFocus, actionRequester),
                    )
                }
                FocusButton(
                    priorityShortLabel(item.priority),
                    { onCyclePriority(item) },
                    primary = item.priority == 1,
                    compact = true,
                    enabled = item.status != OfflineStatus.COMPLETED,
                )
                FocusButton(
                    if (item.status == OfflineStatus.COMPLETED) "حذف" else "الغاء",
                    { onDelete(item) },
                    primary = false,
                    compact = true,
                )
            }'''
new='''            when (item.status) {
                OfflineStatus.COMPLETED -> FocusButton(
                    "تشغيل",
                    { onPlay(item) },
                    compact = true,
                    modifier = Modifier.fillMaxWidth().restoreFocus(restoreFocus, actionRequester),
                )
                OfflineStatus.FAILED -> FocusButton(
                    "اعادة المحاولة",
                    { onRetry(item) },
                    compact = true,
                    modifier = Modifier.fillMaxWidth().restoreFocus(restoreFocus, actionRequester),
                )
                OfflineStatus.PAUSED,
                OfflineStatus.WAITING_SCHEDULE,
                OfflineStatus.WAITING_NETWORK,
                OfflineStatus.WAITING_STORAGE,
                -> FocusButton(
                    "استئناف التحميل",
                    { onRetry(item) },
                    compact = true,
                    modifier = Modifier.fillMaxWidth().restoreFocus(restoreFocus, actionRequester),
                )
                OfflineStatus.QUEUED,
                OfflineStatus.CHECKING,
                OfflineStatus.DOWNLOADING,
                -> FocusButton(
                    "ايقاف التحميل مؤقتا",
                    { onRetry(item) },
                    primary = false,
                    compact = true,
                    modifier = Modifier.fillMaxWidth().restoreFocus(restoreFocus, actionRequester),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (item.status != OfflineStatus.COMPLETED) {
                    FocusButton(
                        priorityShortLabel(item.priority),
                        { onCyclePriority(item) },
                        primary = item.priority == 1,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                FocusButton(
                    if (item.status == OfflineStatus.COMPLETED) "حذف من الجهاز" else "الغاء التحميل",
                    { onDelete(item) },
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }'''
if old not in M:
    raise SystemExit('download action block missing')
M=M.replace(old,new,1)
P=P.replace('    var lastManualSeekAtMs by remember(request) { mutableLongStateOf(0L) }\n','')
P=P.replace('    var currentPositionMs by remember(request) { mutableLongStateOf(0L) }', '    var currentPositionMs by remember(request) { mutableLongStateOf(0L) }\n    var manualSeekTargetMs by remember(request) { mutableStateOf<Long?>(null) }')
old_seek='''    fun seekBy(deltaMs: Long) {
        if (request.isLive || durationMs <= 0L) return
        val target = ((currentPositionMs.takeIf { it > 0L } ?: player.currentPosition) + deltaMs).coerceIn(0L, durationMs)
        lastManualSeekAtMs = android.os.SystemClock.elapsedRealtime()
        pendingSeekMs = target
        currentPositionMs = target
        player.seekTo(target)
        seekFeedback = if (deltaMs > 0) "+10 ث" else "-10 ث"
        controlsVisible = true
    }

    fun seekToPosition(targetMs: Long) {
        if (request.isLive || durationMs <= 0L) return
        val target = targetMs.coerceIn(0L, durationMs)
        player.seekTo(target)
        currentPositionMs = target
        seekFeedback = "انتقال الى ${formatTime(target)}"
        controlsVisible = true
    }'''
new_seek='''    fun seekBy(deltaMs: Long) {
        if (request.isLive || durationMs <= 0L) return
        val base = manualSeekTargetMs ?: currentPositionMs.takeIf { it > 0L } ?: player.currentPosition.coerceAtLeast(0L)
        val target = (base + deltaMs).coerceIn(0L, durationMs)
        manualSeekTargetMs = target
        currentPositionMs = target
        seekFeedback = if (deltaMs > 0) "+10 ث" else "-10 ث"
        controlsVisible = true
    }

    fun seekToPosition(targetMs: Long) {
        if (request.isLive || durationMs <= 0L) return
        val target = targetMs.coerceIn(0L, durationMs)
        manualSeekTargetMs = target
        currentPositionMs = target
        seekFeedback = "انتقال الى ${formatTime(target)}"
        controlsVisible = true
    }'''
if old_seek not in P:
    raise SystemExit('seek functions missing')
P=P.replace(old_seek,new_seek,1)
old_loop='''    LaunchedEffect(player, request) {
        while (isActive) {
            delay(500L)
            if(android.os.SystemClock.elapsedRealtime()-lastManualSeekAtMs>1400L){currentPositionMs=player.currentPosition.coerceAtLeast(0L);pendingSeekMs=0L}
            durationMs = player.duration.takeIf { it > 0L } ?: 0L
            bufferedPercent = player.bufferedPercentage.coerceIn(0, 100)
        }
    }'''
new_loop='''    LaunchedEffect(manualSeekTargetMs, request) {
        val target = manualSeekTargetMs ?: return@LaunchedEffect
        if (request.isLive) return@LaunchedEffect
        delay(260L)
        if (manualSeekTargetMs != target) return@LaunchedEffect
        player.seekTo(target)
        repeat(80) {
            delay(100L)
            if (manualSeekTargetMs != target) return@LaunchedEffect
            val actual = player.currentPosition.coerceAtLeast(0L)
            if (kotlin.math.abs(actual - target) <= 1_500L) {
                manualSeekTargetMs = null
                currentPositionMs = actual
                return@LaunchedEffect
            }
        }
    }

    LaunchedEffect(player, request) {
        while (isActive) {
            delay(500L)
            if (manualSeekTargetMs == null) {
                currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            }
            durationMs = player.duration.takeIf { it > 0L } ?: 0L
            bufferedPercent = player.bufferedPercentage.coerceIn(0, 100)
        }
    }'''
if old_loop not in P:
    raise SystemExit('position loop missing')
P=P.replace(old_loop,new_loop,1)
P=P.replace('        pendingSeekMs = 0L\n    }', '        pendingSeekMs = 0L\n        manualSeekTargetMs = null\n    }', 1)
P=P.replace('modifier = Modifier.align(Alignment.CenterEnd),\n            )\n        }', 'modifier = Modifier.align(Alignment.CenterStart).padding(start = 22.dp, end = 10.dp),\n            )\n        }', 1)
P=P.replace('.fillMaxHeight(.86f)\n            .fillMaxWidth(.80f)', '.fillMaxHeight(.84f)\n            .fillMaxWidth(.82f)', 1)
P=P.replace('.clip(RoundedCornerShape(28.dp))', '.clip(RoundedCornerShape(24.dp))', 1)
P=P.replace('.border(1.dp, colors.gold.copy(alpha = .42f), RoundedCornerShape(28.dp))', '.border(1.dp, colors.gold.copy(alpha = .46f), RoundedCornerShape(24.dp))', 1)
P=P.replace('.padding(22.dp),\n    ) {\n        Row(verticalAlignment = Alignment.CenterVertically) {\n            BrandBadge(Modifier.size(58.dp))', '.padding(18.dp),\n    ) {\n        Row(verticalAlignment = Alignment.CenterVertically) {\n            BrandBadge(Modifier.size(50.dp))', 1)
P=P.replace('fontSize = 25.sp', 'fontSize = 22.sp', 1)
P=P.replace('fontSize = 10.sp,\n                )\n            }\n            FocusButton("اغلاق"', 'fontSize = 11.sp,\n                )\n            }\n            FocusButton("اغلاق"', 1)
P=P.replace('Spacer(Modifier.height(14.dp))\n        HulkTextField', 'Spacer(Modifier.height(11.dp))\n        HulkTextField', 1)
P=P.replace('Spacer(Modifier.height(14.dp))\n\n        Row(Modifier.fillMaxSize()', 'Spacer(Modifier.height(11.dp))\n\n        Row(Modifier.fillMaxSize()', 1)
P=P.replace('.width(245.dp)', '.width(220.dp)', 1)
P=P.replace('horizontalArrangement = Arrangement.spacedBy(14.dp)', 'horizontalArrangement = Arrangement.spacedBy(12.dp)', 1)
P=P.replace('fontSize = 15.sp', 'fontSize = 16.sp', 1)
P=P.replace('fontSize = 17.sp', 'fontSize = 18.sp', 1)
m.write_text(M); p.write_text(P); g.write_text(G)
