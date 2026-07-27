#!/usr/bin/env python3
from pathlib import Path
import json
import sys

root = Path(sys.argv[1])
root.mkdir(parents=True, exist_ok=True)
summaries = []
for p in sorted(root.rglob('summary.json')):
    try:
        summaries.append(json.loads(p.read_text(encoding='utf-8')))
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
]
if not summaries:
    lines += [
        '## Matrix status',
        '',
        'No emulator device summaries were produced. Inspect the QA APK build diagnostics and workflow logs before trusting this run.',
        '',
    ]
lines += [
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
(root / 'EMULATOR-MATRIX-SUMMARY.json').write_text(
    json.dumps(
        {
            'devices': summaries,
            'critical_count': critical,
            'warning_count': warnings,
            'matrix_complete': len(summaries) == 4,
        },
        ensure_ascii=False,
        indent=2,
    ),
    encoding='utf-8',
)
