#!/usr/bin/env python3
from __future__ import annotations

import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCREEN = ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
MANIFEST = ROOT / "qa/canonical/canonical-source.sha256"

FOCUS_CONTRACT = r'''
internal enum class DownloadFocusSlot {
    WIFI,
    SCHEDULE,
    CONCURRENT,
    PRIMARY,
    PRIORITY,
    CANCEL,
}

internal enum class DownloadFocusDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

internal data class DownloadFocusNode(
    val rowIndex: Int,
    val slot: DownloadFocusSlot,
)

internal fun nextDownloadFocusNode(
    current: DownloadFocusNode,
    rowCount: Int,
    direction: DownloadFocusDirection,
): DownloadFocusNode? {
    if (rowCount < 0) return null
    if (current.rowIndex < 0) {
        return when (direction) {
            DownloadFocusDirection.UP -> null
            DownloadFocusDirection.DOWN -> when (current.slot) {
                DownloadFocusSlot.WIFI -> DownloadFocusNode(0, DownloadFocusSlot.PRIMARY)
                DownloadFocusSlot.SCHEDULE -> DownloadFocusNode(0, DownloadFocusSlot.PRIORITY)
                DownloadFocusSlot.CONCURRENT -> DownloadFocusNode(0, DownloadFocusSlot.CANCEL)
                else -> null
            }.takeIf { rowCount > 0 }
            DownloadFocusDirection.LEFT -> when (current.slot) {
                DownloadFocusSlot.WIFI -> DownloadFocusNode(-1, DownloadFocusSlot.SCHEDULE)
                DownloadFocusSlot.SCHEDULE -> DownloadFocusNode(-1, DownloadFocusSlot.CONCURRENT)
                else -> null
            }
            DownloadFocusDirection.RIGHT -> when (current.slot) {
                DownloadFocusSlot.CONCURRENT -> DownloadFocusNode(-1, DownloadFocusSlot.SCHEDULE)
                DownloadFocusSlot.SCHEDULE -> DownloadFocusNode(-1, DownloadFocusSlot.WIFI)
                else -> null
            }
        }
    }

    if (
        current.rowIndex >= rowCount ||
        current.slot !in setOf(
            DownloadFocusSlot.PRIMARY,
            DownloadFocusSlot.PRIORITY,
            DownloadFocusSlot.CANCEL,
        )
    ) {
        return null
    }

    return when (direction) {
        DownloadFocusDirection.LEFT -> when (current.slot) {
            DownloadFocusSlot.PRIMARY -> current.copy(slot = DownloadFocusSlot.PRIORITY)
            DownloadFocusSlot.PRIORITY -> current.copy(slot = DownloadFocusSlot.CANCEL)
            else -> null
        }
        DownloadFocusDirection.RIGHT -> when (current.slot) {
            DownloadFocusSlot.CANCEL -> current.copy(slot = DownloadFocusSlot.PRIORITY)
            DownloadFocusSlot.PRIORITY -> current.copy(slot = DownloadFocusSlot.PRIMARY)
            else -> null
        }
        DownloadFocusDirection.UP -> if (current.rowIndex > 0) {
            current.copy(rowIndex = current.rowIndex - 1)
        } else {
            DownloadFocusNode(
                rowIndex = -1,
                slot = when (current.slot) {
                    DownloadFocusSlot.PRIMARY -> DownloadFocusSlot.WIFI
                    DownloadFocusSlot.PRIORITY -> DownloadFocusSlot.SCHEDULE
                    DownloadFocusSlot.CANCEL -> DownloadFocusSlot.CONCURRENT
                    else -> return null
                },
            )
        }
        DownloadFocusDirection.DOWN -> if (current.rowIndex + 1 < rowCount) {
            current.copy(rowIndex = current.rowIndex + 1)
        } else {
            null
        }
    }
}

private fun Modifier.downloadFocusNavigation(
    isTv: Boolean,
    node: DownloadFocusNode,
    rowCount: Int,
    requesters: Map<DownloadFocusNode, FocusRequester>,
): Modifier {
    if (!isTv) return this
    val requester = requesters[node] ?: return this
    return focusRequester(requester).onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) {
            false
        } else {
            val direction = when (event.key) {
                Key.DirectionUp -> DownloadFocusDirection.UP
                Key.DirectionDown -> DownloadFocusDirection.DOWN
                Key.DirectionLeft -> DownloadFocusDirection.LEFT
                Key.DirectionRight -> DownloadFocusDirection.RIGHT
                else -> null
            }
            val target = direction?.let { nextDownloadFocusNode(node, rowCount, it) }
            if (target == null) {
                false
            } else {
                requesters[target]?.let { targetRequester ->
                    runCatching { targetRequester.requestFocus() }.isSuccess
                } ?: false
            }
        }
    }
}
'''

DOWNLOADS_SCREEN = r'''@Composable
private fun DownloadsScreen(
    downloads: List<OfflineDownload>,
    settings: DownloadSettings,
    isTv: Boolean,
    navigationMemory: NavigationMemoryStore,
    onPlay: (OfflineDownload) -> Unit,
    onDelete: (OfflineDownload) -> Unit,
    onRetry: (OfflineDownload) -> Unit,
    onToggleWifiOnly: () -> Unit,
    onToggleSchedule: () -> Unit,
    onCycleConcurrent: () -> Unit,
    onCyclePriority: (OfflineDownload) -> Unit,
) {
    val completed = downloads.count { it.status == OfflineStatus.COMPLETED }
    val active = downloads.count {
        it.status == OfflineStatus.QUEUED ||
            it.status == OfflineStatus.CHECKING ||
            it.status == OfflineStatus.DOWNLOADING ||
            it.status == OfflineStatus.PAUSED ||
            it.status == OfflineStatus.WAITING_SCHEDULE ||
            it.status == OfflineStatus.WAITING_NETWORK ||
            it.status == OfflineStatus.WAITING_STORAGE
    }
    val storedBytes = downloads
        .filter { it.status == OfflineStatus.COMPLETED }
        .sumOf { it.totalBytes.coerceAtLeast(it.bytesDownloaded).coerceAtLeast(0L) }
    val remembered = navigationMemory.position(MainDestination.DOWNLOADS)
    val rememberedIndex = remembered.itemIndex.coerceIn(0, downloads.lastIndex.coerceAtLeast(0))
    val downloadsState = rememberLazyListState(initialFirstVisibleItemIndex = rememberedIndex)
    val downloadsFocusScope = rememberCoroutineScope()
    var downloadsFocusJob by remember { mutableStateOf<Job?>(null) }
    val context = LocalContext.current
    val availableBytes = remember(downloads) {
        (context.getExternalFilesDir(null) ?: context.filesDir).usableSpace.coerceAtLeast(0L)
    }
    val downloadIds = downloads.map(OfflineDownload::downloadId)
    val downloadFocusRequesters = remember(downloadIds) {
        buildMap {
            put(DownloadFocusNode(-1, DownloadFocusSlot.WIFI), FocusRequester())
            put(DownloadFocusNode(-1, DownloadFocusSlot.SCHEDULE), FocusRequester())
            put(DownloadFocusNode(-1, DownloadFocusSlot.CONCURRENT), FocusRequester())
            downloads.indices.forEach { rowIndex ->
                put(DownloadFocusNode(rowIndex, DownloadFocusSlot.PRIMARY), FocusRequester())
                put(DownloadFocusNode(rowIndex, DownloadFocusSlot.PRIORITY), FocusRequester())
                put(DownloadFocusNode(rowIndex, DownloadFocusSlot.CANCEL), FocusRequester())
            }
        }
    }
    fun focusModifier(node: DownloadFocusNode): Modifier = Modifier.downloadFocusNavigation(
        isTv = isTv,
        node = node,
        rowCount = downloads.size,
        requesters = downloadFocusRequesters,
    )

    LaunchedEffect(isTv, downloadIds, remembered.itemKey, rememberedIndex) {
        if (isTv) {
            delay(180L)
            val initialNode = if (remembered.itemKey.isBlank()) {
                DownloadFocusNode(-1, DownloadFocusSlot.WIFI)
            } else {
                DownloadFocusNode(rememberedIndex, DownloadFocusSlot.PRIMARY)
            }
            runCatching { downloadFocusRequesters[initialNode]?.requestFocus() }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(if (isTv) TV_PAGE_GUTTER else 13.dp),
    ) {
        PageTitle("التنزيلات", "ادارة كاملة للمشاهدة بدون انترنت", downloads.size, Icons.Rounded.Download)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill("مكتمل  $completed")
            if (active > 0) InfoPill("نشط ومجدول  $active")
            if (storedBytes > 0L) InfoPill("المحفوظ  ${formatBytes(storedBytes)}")
            InfoPill("المساحة المتاحة بالجهاز  ${formatBytes(availableBytes)}")
        }
        Spacer(Modifier.height(11.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
        ) {
            item {
                FocusButton(
                    if (settings.wifiOnly) "WiFi فقط  ✓" else "كل الشبكات",
                    onToggleWifiOnly,
                    primary = settings.wifiOnly,
                    compact = true,
                    outlined = !settings.wifiOnly,
                    modifier = focusModifier(DownloadFocusNode(-1, DownloadFocusSlot.WIFI)),
                )
            }
            item {
                FocusButton(
                    if (settings.scheduleMode == DownloadScheduleMode.NIGHT) "الجدولة 02:00" else "الجدولة الان",
                    onToggleSchedule,
                    primary = settings.scheduleMode == DownloadScheduleMode.NIGHT,
                    compact = true,
                    outlined = settings.scheduleMode != DownloadScheduleMode.NIGHT,
                    modifier = focusModifier(DownloadFocusNode(-1, DownloadFocusSlot.SCHEDULE)),
                )
            }
            item {
                FocusButton(
                    "متزامنة  ${settings.concurrentDownloads}",
                    onCycleConcurrent,
                    primary = false,
                    compact = true,
                    outlined = true,
                    modifier = focusModifier(DownloadFocusNode(-1, DownloadFocusSlot.CONCURRENT)),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (downloads.isEmpty()) {
            EmptyState("ستظهر هنا الافلام والحلقات التي تختار تحميلها")
        } else {
            LazyColumn(
                state = downloadsState,
                verticalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
                horizontalAlignment = Alignment.Start,
                contentPadding = PaddingValues(bottom = 28.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(downloads, key = { _, item -> item.downloadId }) { index, item ->
                    DownloadCard(
                        item = item,
                        isTv = isTv,
                        onFocused = {
                            navigationMemory.save(MainDestination.DOWNLOADS, item.downloadId.toString(), index)
                            if (isTv) {
                                downloadsFocusJob?.cancel()
                                downloadsFocusJob = downloadsFocusScope.launch {
                                    delay(100L)
                                    runCatching { downloadsState.scrollToItem(index, scrollOffset = 0) }
                                }
                            }
                        },
                        onPlay = onPlay,
                        onDelete = onDelete,
                        onRetry = onRetry,
                        onCyclePriority = onCyclePriority,
                        primaryActionModifier = focusModifier(DownloadFocusNode(index, DownloadFocusSlot.PRIMARY)),
                        priorityActionModifier = focusModifier(DownloadFocusNode(index, DownloadFocusSlot.PRIORITY)),
                        cancelActionModifier = focusModifier(DownloadFocusNode(index, DownloadFocusSlot.CANCEL)),
                        modifier = Modifier
                            .widthIn(max = if (isTv) 720.dp else 520.dp)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

'''

DOWNLOAD_CARD = r'''@Composable
private fun DownloadCard(
    item: OfflineDownload,
    isTv: Boolean,
    onFocused: () -> Unit,
    onPlay: (OfflineDownload) -> Unit,
    onDelete: (OfflineDownload) -> Unit,
    onRetry: (OfflineDownload) -> Unit,
    onCyclePriority: (OfflineDownload) -> Unit,
    primaryActionModifier: Modifier,
    priorityActionModifier: Modifier,
    cancelActionModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(17.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isTv) 164.dp else 220.dp)
            .clip(shape)
            .background(if (focused) colors.gold.copy(alpha = .10f) else Color(0xFF11120E))
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) colors.goldBright else colors.line.copy(alpha = .45f),
                shape,
            )
            .onFocusChanged { focusState -> focused = focusState.hasFocus }
            .padding(if (isTv) 9.dp else 11.dp),
        verticalArrangement = Arrangement.spacedBy(if (isTv) 6.dp else 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 10.dp else 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1B1C15)),
                contentAlignment = Alignment.Center,
            ) {
                if (!item.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    BrandLogo(Modifier.size(52.dp).graphicsLayer { alpha = .55f })
                }
                if (item.status == OfflineStatus.COMPLETED) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp)
                            .clip(CircleShape)
                            .background(colors.gold)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("جاهز", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        item.seriesTitle ?: if (item.streamKind == "movie") "فيلم" else "حلقة",
                        color = colors.goldBright,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    val cleanDownloadTitle = if (item.seriesTitle != null && item.episodeNumber != null) {
                        "الحلقة ${item.episodeNumber}"
                    } else {
                        item.title
                    }
                    Text(
                        cleanDownloadTitle,
                        color = colors.text,
                        fontSize = if (isTv) 14.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = if (isTv) 17.sp else 15.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        item.season?.let { season ->
                            Text("الموسم $season", color = colors.textMuted, fontSize = 8.sp, maxLines = 1)
                        }
                        Text(
                            "الاولوية  ${priorityLabel(item.priority)}",
                            color = if (item.priority == 1) colors.goldBright else colors.textMuted,
                            fontSize = 8.sp,
                            maxLines = 1,
                        )
                    }
                }
                DownloadProgress(item)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(if (isTv) 36.dp else 40.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            when (item.status) {
                OfflineStatus.COMPLETED -> FocusButton(
                    "تشغيل",
                    { onPlay(item) },
                    compact = true,
                    outlined = true,
                    onFocused = onFocused,
                    modifier = Modifier.weight(1.35f).fillMaxHeight().then(primaryActionModifier),
                )
                OfflineStatus.FAILED -> FocusButton(
                    "اعادة المحاولة",
                    { onRetry(item) },
                    compact = true,
                    outlined = true,
                    onFocused = onFocused,
                    modifier = Modifier.weight(1.35f).fillMaxHeight().then(primaryActionModifier),
                )
                OfflineStatus.PAUSED,
                OfflineStatus.WAITING_SCHEDULE,
                OfflineStatus.WAITING_NETWORK,
                OfflineStatus.WAITING_STORAGE,
                -> FocusButton(
                    "استئناف",
                    { onRetry(item) },
                    compact = true,
                    outlined = true,
                    onFocused = onFocused,
                    modifier = Modifier.weight(1.35f).fillMaxHeight().then(primaryActionModifier),
                )
                OfflineStatus.QUEUED,
                OfflineStatus.CHECKING,
                OfflineStatus.DOWNLOADING,
                -> FocusButton(
                    "ايقاف مؤقت",
                    { onRetry(item) },
                    primary = false,
                    compact = true,
                    outlined = true,
                    onFocused = onFocused,
                    modifier = Modifier.weight(1.35f).fillMaxHeight().then(primaryActionModifier),
                )
            }
            FocusButton(
                priorityShortLabel(item.priority),
                { onCyclePriority(item) },
                primary = item.priority == 1,
                compact = true,
                outlined = true,
                onFocused = onFocused,
                modifier = Modifier.weight(1f).fillMaxHeight().then(priorityActionModifier),
            )
            FocusButton(
                if (item.status == OfflineStatus.COMPLETED) "حذف" else "الغاء",
                { onDelete(item) },
                primary = false,
                compact = true,
                outlined = true,
                onFocused = onFocused,
                modifier = Modifier.weight(1f).fillMaxHeight().then(cancelActionModifier),
            )
        }
    }
}

'''


def replace_between(source: str, start: str, end: str, replacement: str, label: str) -> str:
    start_index = source.find(start)
    if start_index < 0:
        raise SystemExit(f"{label}: start marker not found")
    end_index = source.find(end, start_index + len(start))
    if end_index < 0:
        raise SystemExit(f"{label}: end marker not found")
    return source[:start_index] + replacement + source[end_index:]


def main() -> None:
    source = SCREEN.read_text(encoding="utf-8")
    source = replace_between(
        source,
        "internal enum class DownloadFocusSlot",
        "data class NavigationPosition(",
        FOCUS_CONTRACT + "\n",
        "focus contract",
    )
    source = replace_between(
        source,
        "@Composable\nprivate fun DownloadsScreen(",
        "@Composable\nprivate fun DownloadCard(",
        DOWNLOADS_SCREEN,
        "downloads screen",
    )
    source = replace_between(
        source,
        "@Composable\nprivate fun DownloadCard(",
        "@Composable\nprivate fun DownloadProgress(",
        DOWNLOAD_CARD,
        "download card",
    )
    required = (
        "            .padding(if (isTv) TV_PAGE_GUTTER else 13.dp),",
        "            .height(if (isTv) 164.dp else 220.dp)\n            .clip(shape)",
        "modifier = Modifier.fillMaxSize(),",
        "DownloadFocusNode(index, DownloadFocusSlot.CANCEL)",
        "outlined = true",
    )
    missing = [text for text in required if text not in source]
    if missing:
        raise SystemExit(f"formatted source missing required contracts: {missing}")
    forbidden = [marker for marker in ("qa-tv-", "qaTvPageContent", "qaMarker", "QA_TV_") if marker in source]
    if forbidden:
        raise SystemExit(f"production marker leak: {forbidden}")
    SCREEN.write_text(source, encoding="utf-8")

    entries: dict[str, str] = {}
    for line in MANIFEST.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        digest, path = line.split("  ", 1)
        entries[path] = digest
    relative = SCREEN.relative_to(ROOT).as_posix()
    entries[relative] = hashlib.sha256(SCREEN.read_bytes()).hexdigest()
    MANIFEST.write_text(
        "".join(f"{entries[path]}  {path}\n" for path in sorted(entries)),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
