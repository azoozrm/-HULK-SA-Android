#!/usr/bin/env python3
from pathlib import Path
import json
import re
import sys
import xml.etree.ElementTree as ET
from PIL import Image, ImageStat

root = Path(sys.argv[1])
device = sys.argv[2]
results = []
critical = []
warnings = []

for png in sorted(root.glob('*.png')):
    scenario = png.stem
    xml_path = root / f'{scenario}.xml'
    log_path = root / f'{scenario}.logcat.txt'
    focus_path = root / f'{scenario}.window.txt'
    img = Image.open(png).convert('RGB')
    width, height = img.size
    stat = ImageStat.Stat(img.resize((64, 64)))
    channel_std = sum(stat.stddev) / 3.0
    mostly_blank = channel_std < 4.0
    if mostly_blank:
        critical.append(f'{scenario}: screenshot appears blank (stddev={channel_std:.2f})')

    node_count = 0
    focus_count = 0
    out_of_bounds = []
    edge_touching_focus = []
    text_nodes = []
    if xml_path.exists() and xml_path.stat().st_size > 20:
        try:
            tree = ET.parse(xml_path)
            for node in tree.getroot().iter('node'):
                node_count += 1
                text = (node.attrib.get('text', '') or node.attrib.get('content-desc', '')).strip()
                if text:
                    text_nodes.append(text)
                m = re.fullmatch(r'\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]', node.attrib.get('bounds', ''))
                if not m:
                    continue
                x1, y1, x2, y2 = map(int, m.groups())
                if x1 < 0 or y1 < 0 or x2 > width or y2 > height:
                    out_of_bounds.append({'text': text, 'bounds': [x1, y1, x2, y2]})
                focused = node.attrib.get('focused') == 'true'
                if focused:
                    focus_count += 1
                    if x1 <= 1 or y1 <= 1 or x2 >= width - 1 or y2 >= height - 1:
                        edge_touching_focus.append({'text': text, 'bounds': [x1, y1, x2, y2]})
        except Exception as exc:
            warnings.append(f'{scenario}: UI XML parse failed: {exc}')
    else:
        warnings.append(f'{scenario}: UI XML missing or empty')

    if out_of_bounds:
        critical.append(f'{scenario}: {len(out_of_bounds)} accessibility bounds exceed {width}x{height}')
    if edge_touching_focus:
        warnings.append(f'{scenario}: focused node touches display edge')
    if scenario != 'player_next_episode' and node_count == 0:
        warnings.append(f'{scenario}: no accessibility nodes found')

    crash = False
    if log_path.exists():
        log = log_path.read_text(errors='ignore')
        crash = 'FATAL EXCEPTION' in log or ('Process: sa.hulksa.player.dev' in log and 'AndroidRuntime' in log)
        if crash:
            critical.append(f'{scenario}: AndroidRuntime crash detected')

    current_focus = ''
    if focus_path.exists():
        for line in focus_path.read_text(errors='ignore').splitlines():
            if 'mCurrentFocus' in line or 'mFocusedApp' in line:
                current_focus += line.strip() + ' '

    results.append({
        'scenario': scenario,
        'width': width,
        'height': height,
        'image_stddev': round(channel_std, 2),
        'blank': mostly_blank,
        'node_count': node_count,
        'focus_count': focus_count,
        'out_of_bounds': out_of_bounds,
        'edge_touching_focus': edge_touching_focus,
        'crash': crash,
        'sample_text': text_nodes[:12],
        'window_focus': current_focus[:400],
    })

ime = {}
ime_file = root / 'login-ime-check.json'
if ime_file.exists():
    ime = json.loads(ime_file.read_text())
    if not ime.get('opened_after_username', False):
        warnings.append('login: emulator IME did not report open after focusing username')
    if ime.get('shown_after_login_button', True):
        critical.append('login: IME still shown after login button focus/click')

summary = {
    'device': device,
    'scenario_count': len(results),
    'critical_count': len(critical),
    'warning_count': len(warnings),
    'critical': critical,
    'warnings': warnings,
    'ime': ime,
    'scenarios': results,
}
(root / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')

lines = [
    f'# HULK SA Emulator QA — {device}',
    '',
    f'- Scenarios: {len(results)}',
    f'- Critical findings: {len(critical)}',
    f'- Warnings: {len(warnings)}',
    '',
]
if ime:
    lines += ['## Login keyboard', '', f"- Opened after username focus: {'PASS' if ime.get('opened_after_username') else 'WARN'}", f"- Hidden after login action: {'PASS' if not ime.get('shown_after_login_button') else 'FAIL'}", '']
lines += ['## Scenario results', '', '| Scenario | Resolution | UI nodes | Focus | Blank | Bounds | Crash |', '|---|---:|---:|---:|---|---|---|']
for r in results:
    lines.append(f"| {r['scenario']} | {r['width']}×{r['height']} | {r['node_count']} | {r['focus_count']} | {'FAIL' if r['blank'] else 'PASS'} | {'FAIL' if r['out_of_bounds'] else 'PASS'} | {'FAIL' if r['crash'] else 'PASS'} |")
if critical:
    lines += ['', '## Critical findings', ''] + [f'- {x}' for x in critical]
if warnings:
    lines += ['', '## Warnings', ''] + [f'- {x}' for x in warnings]
(root / 'REPORT.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')

if critical:
    print('\n'.join(critical))
    sys.exit(1)
