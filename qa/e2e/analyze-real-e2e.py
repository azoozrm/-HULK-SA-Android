#!/usr/bin/env python3
from pathlib import Path
import json
import re
import sys
import xml.etree.ElementTree as ET
from PIL import Image, ImageStat

root = Path(sys.argv[1])
device = sys.argv[2]
critical = []
warnings = []
scenarios = []
login_success = (root / "login-success.flag").exists()
if not login_success:
    critical.append("Real account login did not reach the authenticated application shell")

for png in sorted(root.glob("*.png")):
    name = png.stem
    xml_path = root / f"{name}.xml"
    log_path = root / f"{name}.logcat.txt"
    image = Image.open(png).convert("RGB")
    width, height = image.size
    stat = ImageStat.Stat(image.resize((64, 64)))
    stddev = sum(stat.stddev) / 3.0
    blank = stddev < 4.0
    if blank:
        critical.append(f"{name}: screenshot appears blank (stddev={stddev:.2f})")

    node_count = 0
    text_nodes = []
    out_of_bounds = []
    if xml_path.exists() and xml_path.stat().st_size > 20:
        try:
            tree = ET.parse(xml_path)
            for node in tree.getroot().iter("node"):
                node_count += 1
                text = ((node.attrib.get("text", "") or "") + " " + (node.attrib.get("content-desc", "") or "")).strip()
                if text:
                    text_nodes.append(text)
                m = re.fullmatch(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", node.attrib.get("bounds", ""))
                if not m:
                    continue
                x1, y1, x2, y2 = map(int, m.groups())
                if x1 < 0 or y1 < 0 or x2 > width or y2 > height:
                    out_of_bounds.append({"text": text, "bounds": [x1, y1, x2, y2]})
        except Exception as exc:
            warnings.append(f"{name}: UI XML parse failed: {exc}")
    else:
        warnings.append(f"{name}: UI XML missing or empty")

    if out_of_bounds:
        critical.append(f"{name}: {len(out_of_bounds)} UI bounds exceed {width}x{height}")
    if node_count == 0:
        warnings.append(f"{name}: no accessibility nodes found")

    crash = False
    playback_error = False
    if log_path.exists():
        log = log_path.read_text(encoding="utf-8", errors="ignore")
        crash = "FATAL EXCEPTION" in log or ("Process: sa.hulksa.player.dev" in log and "AndroidRuntime" in log)
        playback_error = bool(re.search(r"PlaybackException|ExoPlaybackException|ERROR_CODE_(IO|DECODING|PARSING)", log, re.I))
        if crash:
            critical.append(f"{name}: AndroidRuntime crash detected")
        if playback_error:
            warnings.append(f"{name}: playback-related error found in logcat")

    scenarios.append({
        "scenario": name,
        "resolution": f"{width}x{height}",
        "blank": blank,
        "image_stddev": round(stddev, 2),
        "node_count": node_count,
        "out_of_bounds": out_of_bounds,
        "crash": crash,
        "playback_error": playback_error,
        "sample_text": text_nodes[:20],
    })

required = ["home-real"]
for name in required:
    if not (root / f"{name}.png").exists():
        critical.append(f"Missing required capture: {name}")

optional_groups = {
    "live": ["live-real", "live-player-real"],
    "movies": ["movies-real", "movie-details-real", "movie-player-real"],
    "series": ["series-real", "series-details-real"],
    "search": ["search-real"],
    "downloads": ["downloads-real"],
    "settings": ["settings-real"],
}
completed_groups = {}
for group, names in optional_groups.items():
    done = [name for name in names if (root / f"{name}.png").exists()]
    completed_groups[group] = done
    if not done:
        warnings.append(f"No authenticated capture completed for {group}")

summary = {
    "device": device,
    "login_success": login_success,
    "scenario_count": len(scenarios),
    "critical_count": len(critical),
    "warning_count": len(warnings),
    "critical": critical,
    "warnings": warnings,
    "completed_groups": completed_groups,
    "scenarios": scenarios,
}
(root / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")

lines = [
    f"# HULK SA Real Account E2E — {device}",
    "",
    f"- Login: {'PASS' if login_success else 'FAIL'}",
    f"- Captures: {len(scenarios)}",
    f"- Critical findings: {len(critical)}",
    f"- Warnings: {len(warnings)}",
    "",
    "| Scenario | Resolution | Nodes | Blank | Bounds | Crash | Playback log |",
    "|---|---:|---:|---|---|---|---|",
]
for item in scenarios:
    lines.append(
        f"| {item['scenario']} | {item['resolution']} | {item['node_count']} | "
        f"{'FAIL' if item['blank'] else 'PASS'} | {'FAIL' if item['out_of_bounds'] else 'PASS'} | "
        f"{'FAIL' if item['crash'] else 'PASS'} | {'WARN' if item['playback_error'] else 'PASS'} |"
    )
if critical:
    lines += ["", "## Critical findings", ""] + [f"- {x}" for x in critical]
if warnings:
    lines += ["", "## Warnings", ""] + [f"- {x}" for x in warnings]
(root / "REPORT.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

if critical:
    print("\n".join(critical))
    sys.exit(1)
