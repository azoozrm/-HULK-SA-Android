from pathlib import Path
import sys

root = Path(sys.argv[1])
main = root / 'app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt'
gradle = root / 'app/build.gradle.kts'

M = main.read_text()
G = gradle.read_text()

G = G.replace('versionCode = 26', 'versionCode = 27')
G = G.replace('versionName = "0.9.1.4"', 'versionName = "0.9.1.5"')

start = M.index('@Composable\nprivate fun DownloadCard(')
end = M.index('\n@Composable\nprivate fun DownloadProgress', start)
block = M[start:end]

old_height = '.height(if (isTv) 232.dp else 222.dp)'
new_height = '.height(if (isTv) 292.dp else 268.dp)'
if old_height not in block:
    raise SystemExit('v0915 download card height anchor missing')
block = block.replace(old_height, new_height, 1)

old_progress = '''            Spacer(Modifier.height(4.dp))
            DownloadProgress(item)
            Spacer(Modifier.height(5.dp))'''
new_progress = '''            Spacer(Modifier.weight(1f))
            DownloadProgress(item)
            Spacer(Modifier.height(4.dp))'''
if old_progress not in block:
    raise SystemExit('v0915 download progress anchor missing')
block = block.replace(old_progress, new_progress, 1)

main_button_anchor = 'modifier = Modifier.fillMaxWidth().restoreFocus('
main_button_count = block.count(main_button_anchor)
if main_button_count != 4:
    raise SystemExit(f'v0915 expected 4 main download buttons, found {main_button_count}')
block = block.replace(
    main_button_anchor,
    'modifier = Modifier.fillMaxWidth().height(42.dp).restoreFocus(',
)

secondary_anchor = 'modifier = Modifier.weight(1f),'
secondary_count = block.count(secondary_anchor)
if secondary_count != 2:
    raise SystemExit(f'v0915 expected 2 secondary download buttons, found {secondary_count}')
block = block.replace(
    secondary_anchor,
    'modifier = Modifier.weight(1f).height(38.dp),',
)

row_anchor = '''            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {'''
row_replacement = '''            Row(
                modifier = Modifier.fillMaxWidth().height(38.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {'''
if row_anchor not in block:
    raise SystemExit('v0915 secondary row anchor missing')
block = block.replace(row_anchor, row_replacement, 1)

M = M[:start] + block + M[end:]

if 'height(if (isTv) 292.dp else 268.dp)' not in M:
    raise SystemExit('v0915 final card height verification failed')
if 'modifier = Modifier.fillMaxWidth().height(38.dp)' not in block:
    raise SystemExit('v0915 final secondary row verification failed')
if 'الغاء التحميل' not in block:
    raise SystemExit('v0915 cancel button missing')
if 'priorityShortLabel(item.priority)' not in block:
    raise SystemExit('v0915 priority button missing')

main.write_text(M)
gradle.write_text(G)
