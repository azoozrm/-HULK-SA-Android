from pathlib import Path
import sys

root = Path(sys.argv[1])
path = root / 'app/src/main/java/sa/hulksa/player/data/ServerDiagnosticsEngine.kt'
text = path.read_text()
old = '        return ServerDiagnosticsReport(\n'
new = '        return@withContext ServerDiagnosticsReport(\n'
if text.count(old) != 1:
    raise SystemExit(f'v0918 coroutine return anchor mismatch: {text.count(old)}')
text = text.replace(old, new, 1)
if 'return@withContext ServerDiagnosticsReport(' not in text:
    raise SystemExit('v0918 coroutine return fix missing')
path.write_text(text)
