#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1])

def rep(path, old, new, label):
    p=root/path
    s=p.read_text(encoding='utf-8')
    if new in s:
        return
    if old not in s:
        raise SystemExit(f'missing {label}')
    p.write_text(s.replace(old,new,1),encoding='utf-8')

rep('app/build.gradle.kts','versionCode = 49','versionCode = 50','versionCode')
rep('app/build.gradle.kts','versionName = "0.9.3.5"','versionName = "0.9.3.6"','versionName')
main='app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt'
rep(main,'import androidx.compose.runtime.remember\n','import androidx.compose.runtime.remember\nimport androidx.compose.runtime.rememberCoroutineScope\n','rememberCoroutineScope import')
rep(main,'import kotlinx.coroutines.delay\n','import kotlinx.coroutines.delay\nimport kotlinx.coroutines.launch\n','launch import')
rep(main,'.height(if (isTv) 224.dp else 210.dp)','.height(if (isTv) 236.dp else 220.dp)','download card height')
old='''            when (item.status) {
                OfflineStatus.COMPLETED -> FocusButton(
                    "تشغيل",
                    { onPlay(item) },
                    compact = true,
                    modifier = Modifier.fillMaxWidth().height(36.dp).restoreFocus(restoreFocus, actionRequester),
                )
                OfflineStatus.FAILED -> FocusButton(
                    "اعادة المحاولة",
                    { onRetry(item) },
                    compact = true,
                    modifier = Modifier.fillMaxWidth().height(36.dp).restoreFocus(restoreFocus, actionRequester),
                )
                OfflineStatus.PAUSED,
                OfflineStatus.WAITING_SCHEDULE,
                OfflineStatus.WAITING_NETWORK,
                OfflineStatus.WAITING_STORAGE,
                -> FocusButton(
                    "استئناف التحميل",
                    { onRetry(item) },
                    compact = true,
                    modifier = Modifier.fillMaxWidth().height(36.dp).restoreFocus(restoreFocus, actionRequester),
                )
                OfflineStatus.QUEUED,
                OfflineStatus.CHECKING,
                OfflineStatus.DOWNLOADING,
                -> FocusButton(
                    "ايقاف التحميل مؤقتا",
                    { onRetry(item) },
                    primary = false,
                    compact = true,
                    modifier = Modifier.fillMaxWidth().height(36.dp).restoreFocus(restoreFocus, actionRequester),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(34.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (item.status != OfflineStatus.COMPLETED) {
                    FocusButton(
                        priorityShortLabel(item.priority),
                        { onCyclePriority(item) },
                        primary = item.priority == 1,
                        compact = true,
                        modifier = Modifier.weight(1f).height(34.dp),
                    )
                }
                FocusButton(
                    if (item.status == OfflineStatus.COMPLETED) "حذف من الجهاز" else "الغاء التحميل",
                    { onDelete(item) },
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f).height(34.dp),
                )
            }
'''
new='''            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                when (item.status) {
                    OfflineStatus.COMPLETED -> FocusButton(
                        "تشغيل",
                        { onPlay(item) },
                        compact = true,
                        modifier = Modifier.weight(1.35f).fillMaxHeight().restoreFocus(restoreFocus, actionRequester),
                    )
                    OfflineStatus.FAILED -> FocusButton(
                        "اعادة المحاولة",
                        { onRetry(item) },
                        compact = true,
                        modifier = Modifier.weight(1.35f).fillMaxHeight().restoreFocus(restoreFocus, actionRequester),
                    )
                    OfflineStatus.PAUSED,
                    OfflineStatus.WAITING_SCHEDULE,
                    OfflineStatus.WAITING_NETWORK,
                    OfflineStatus.WAITING_STORAGE,
                    -> FocusButton(
                        "استئناف",
                        { onRetry(item) },
                        compact = true,
                        modifier = Modifier.weight(1.35f).fillMaxHeight().restoreFocus(restoreFocus, actionRequester),
                    )
                    OfflineStatus.QUEUED,
                    OfflineStatus.CHECKING,
                    OfflineStatus.DOWNLOADING,
                    -> FocusButton(
                        "ايقاف مؤقت",
                        { onRetry(item) },
                        primary = false,
                        compact = true,
                        modifier = Modifier.weight(1.35f).fillMaxHeight().restoreFocus(restoreFocus, actionRequester),
                    )
                }
                FocusButton(
                    priorityShortLabel(item.priority),
                    { onCyclePriority(item) },
                    primary = item.priority == 1,
                    compact = true,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                FocusButton(
                    if (item.status == OfflineStatus.COMPLETED) "حذف" else "الغاء",
                    { onDelete(item) },
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
'''
rep(main,old,new,'download actions')
rep(main,'''    var moving by remember { mutableStateOf<String?>(null) }
    val ordered = remember(categories, ids) {''','''    var moving by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ordered = remember(categories, ids) {''','category list state')
old_move='''    fun move(id: String, direction: Int) {
        val values = ordered.map { it.id }.toMutableList()
        val from = values.indexOf(id)
        val to = (from + direction).coerceIn(0, values.lastIndex)
        if (from >= 0 && from != to) {
            values.add(to, values.removeAt(from))
            ids = values
            prefs.edit().putString("ids", values.joinToString(",")).apply()
        }
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp),
    ) {'''
new_move='''    fun move(id: String, direction: Int) {
        val values = ordered.map { it.id }.toMutableList()
        val from = values.indexOf(id)
        val to = (from + direction).coerceIn(0, values.lastIndex)
        if (from >= 0 && from != to) {
            values.add(to, values.removeAt(from))
            ids = values
            prefs.edit().putString("ids", values.joinToString(",")).apply()
            scope.launch {
                listState.animateScrollToItem((to + 1).coerceAtLeast(0))
            }
        }
    }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
    ) {'''
rep(main,old_move,new_move,'category auto scroll')
print('Prepared v0.9.3.6 TCL polish')
