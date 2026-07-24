from pathlib import Path
import sys

script_path = Path(__file__).with_name('apply-v0914.py')
source = script_path.read_text()

old_download_source = """old_spacer = '''            Spacer(Modifier.weight(1f))
            DownloadProgress(item)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {'''
new_spacer = '''            Spacer(Modifier.height(4.dp))
            DownloadProgress(item)
            Spacer(Modifier.height(5.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {'''
"""
new_download_source = """old_spacer = '''            Spacer(Modifier.weight(1f))
            DownloadProgress(item)
            Spacer(Modifier.height(8.dp))'''
new_spacer = '''            Spacer(Modifier.height(4.dp))
            DownloadProgress(item)
            Spacer(Modifier.height(5.dp))'''
"""
if old_download_source not in source:
    raise SystemExit('v0914 source download anchor missing')
source = source.replace(old_download_source, new_download_source, 1)

source = source.replace(
    '.combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button)',
    '.clickable(onClick = onClick, role = Role.Button)',
    1,
)
old_import_source = """if 'import androidx.compose.foundation.combinedClickable\\n' not in M:
    M = M.replace('import androidx.compose.foundation.clickable\\n', 'import androidx.compose.foundation.clickable\\nimport androidx.compose.foundation.combinedClickable\\n', 1)
"""
new_import_source = """if 'import androidx.compose.ui.input.key.nativeKeyEvent\\n' not in M:
    M = M.replace('import androidx.compose.ui.input.key.key\\n', 'import androidx.compose.ui.input.key.key\\nimport androidx.compose.ui.input.key.nativeKeyEvent\\n', 1)
"""
if old_import_source not in source:
    raise SystemExit('v0914 source import anchor missing')
source = source.replace(old_import_source, new_import_source, 1)

old_center_source = """P = P.replace('.fillMaxHeight(.84f)\\n                .fillMaxWidth(.82f)\\n                .offset(x = 22.dp)',
              '.fillMaxHeight(.80f)\\n                .fillMaxWidth(.76f)\\n                .widthIn(max = 920.dp)', 1)
"""
new_center_source = """P = P.replace('modifier = Modifier.align(Alignment.CenterStart).padding(start = 22.dp, end = 10.dp)', 'modifier = Modifier.align(Alignment.Center)', 1)
P = P.replace('.fillMaxHeight(.84f)\\n            .fillMaxWidth(.82f)',
              '.fillMaxHeight(.80f)\\n            .fillMaxWidth(.76f)\\n            .widthIn(max = 920.dp)', 1)
"""
if old_center_source not in source:
    raise SystemExit('v0914 source center anchor missing')
source = source.replace(old_center_source, new_center_source, 1)

namespace = {'__name__': '__main__', '__file__': str(script_path)}
exec(compile(source, str(script_path), 'exec'), namespace)
