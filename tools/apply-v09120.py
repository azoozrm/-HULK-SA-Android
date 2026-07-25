#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1])

def rw(rel, fn):
    p=root/rel; t=p.read_text(); n=fn(t)
    if n==t: raise SystemExit(f'No change applied to {rel}')
    p.write_text(n)

def gradle(t):
    return t.replace('versionCode = 41','versionCode = 42',1).replace('versionName = "0.9.1.19"','versionName = "0.9.1.20"',1)
rw('app/build.gradle.kts',gradle)

def shell(t):
    t=t.replace('''LaunchedEffect(remembered.rowKey) {
                    if (remembered.rowKey == "hero") { delay(80); runCatching { heroRequester.requestFocus() } }
                }''','''LaunchedEffect(Unit) {
                    if (remembered.rowKey == "hero") { runCatching { heroRequester.requestFocus() } }
                }''',1)
    t=t.replace('''LaunchedEffect(content, remembered.rowKey, remembered.itemKey) {
        if (remembered.rowKey == rowKey && content.isNotEmpty()) {
            rowState.scrollToItem(targetIndex)
            delay(80)
            runCatching { targetRequester.requestFocus() }
        }
    }''','''LaunchedEffect(Unit) {
        if (remembered.rowKey == rowKey && content.isNotEmpty()) {
            rowState.scrollToItem(targetIndex)
            runCatching { targetRequester.requestFocus() }
        }
    }''',1)
    t=t.replace('''LaunchedEffect(entries, remembered.rowKey, remembered.itemKey) {
        if (remembered.rowKey == rowKey && entries.isNotEmpty()) {
            rowState.scrollToItem(targetIndex)
            delay(80)
            runCatching { targetRequester.requestFocus() }
        }
    }''','''LaunchedEffect(Unit) {
        if (remembered.rowKey == rowKey && entries.isNotEmpty()) {
            rowState.scrollToItem(targetIndex)
            runCatching { targetRequester.requestFocus() }
        }
    }''',1)
    t=t.replace('"التحميلات الجارية"','"التنزيلات الجارية"')
    t=t.replace('PageTitle("التحميلات",','PageTitle("التنزيلات",')
    t=t.replace('DestinationEntry(MainDestination.DOWNLOADS, Icons.Rounded.Download, "التحميلات")','DestinationEntry(MainDestination.DOWNLOADS, Icons.Rounded.Download, "التنزيلات")')
    t=t.replace('.height(if (isTv) 292.dp else 268.dp)', '.height(if (isTv) 224.dp else 210.dp)',1)
    t=t.replace('.width(if (isTv) 94.dp else 82.dp)', '.width(if (isTv) 82.dp else 72.dp)',1)
    t=t.replace('modifier = Modifier.fillMaxWidth().height(42.dp).restoreFocus','modifier = Modifier.fillMaxWidth().height(36.dp).restoreFocus')
    t=t.replace('modifier = Modifier.fillMaxWidth().height(38.dp),','modifier = Modifier.fillMaxWidth().height(34.dp),',1)
    t=t.replace('modifier = Modifier.weight(1f).height(38.dp)','modifier = Modifier.weight(1f).height(34.dp)')
    t=t.replace('${formatBytes(item.bytesPerSecond)}/ث','${formatTransferRate(item.bytesPerSecond)}')
    marker='''private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
'''
    helper='''private fun formatTransferRate(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0L) return "0 KB/ث"
    val kb = bytesPerSecond.toDouble() / 1024.0
    return if (kb >= 1024.0) {
        String.format(Locale.US, "%.1f MB/ث", kb / 1024.0)
    } else {
        String.format(Locale.US, "%.0f KB/ث", kb)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
'''
    if marker not in t: raise SystemExit('formatBytes marker missing')
    return t.replace(marker,helper,1)
rw('app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt',shell)
rw('app/src/main/java/sa/hulksa/player/ui/screens/DetailsNavigationDrawer.kt',lambda t:t.replace('MainDestination.DOWNLOADS to "التحميلات"','MainDestination.DOWNLOADS to "التنزيلات"',1))
print('Applied v0.9.1.20 final phase-one polish')
