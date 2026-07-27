#!/usr/bin/env python3
from pathlib import Path
import json
import sys

root = Path(sys.argv[1])
summaries = []
for p in sorted(root.rglob('summary.json')):
    try:
        summaries.append(json.loads(p.read_text()))
    except Exception:
        pass
critical = sum(s.get('critical_count', 0) for s in summaries)
warnings = sum(s.get('warning_count', 0) for s in summaries)
lines = [
    '# HULK SA v0.9.3.17 Emulator Matrix Report',
    '',
    f'- Device profiles completed: {len(summaries)}',
    f'- Total scenarios captured: {sum(s.get("scenario_count", 0) for s in summaries)}',
    f'- Critical findings: {critical}',
    f'- Warnings: {warnings}',
    '',
    '| Device | Scenarios | Critical | Warnings | Login IME hidden |',
    '|---|---:|---:|---:|---|',
]
for s in summaries:
    ime = s.get('ime', {})
    ime_ok = bool(ime) and not ime.get('shown_after_login_button', True)
    lines.append(f"| {s.get('device')} | {s.get('scenario_count')} | {s.get('critical_count')} | {s.get('warning_count')} | {'PASS' if ime_ok else 'WARN/FAIL'} |")
for s in summaries:
    if s.get('critical'):
        lines += ['', f"## {s.get('device')} critical findings", ''] + [f'- {x}' for x in s['critical']]
    if s.get('warnings'):
        lines += ['', f"## {s.get('device')} warnings", ''] + [f'- {x}' for x in s['warnings']]
(root / 'EMULATOR-MATRIX-REPORT.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
(root / 'EMULATOR-MATRIX-SUMMARY.json').write_text(json.dumps({'devices': summaries, 'critical_count': critical, 'warning_count': warnings}, ensure_ascii=False, indent=2), encoding='utf-8')
if not summaries:
    sys.exit('No device summaries found')
