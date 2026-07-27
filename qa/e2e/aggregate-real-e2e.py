#!/usr/bin/env python3
from pathlib import Path
import json
import sys

root = Path(sys.argv[1])
root.mkdir(parents=True, exist_ok=True)
summaries = []
for path in sorted(root.rglob("summary.json")):
    try:
        summaries.append(json.loads(path.read_text(encoding="utf-8")))
    except Exception:
        pass

critical = sum(item.get("critical_count", 0) for item in summaries)
warnings = sum(item.get("warning_count", 0) for item in summaries)
lines = [
    "# HULK SA v0.9.3.17 Real Account E2E Report",
    "",
    f"- Device profiles completed: {len(summaries)}",
    f"- Successful logins: {sum(1 for x in summaries if x.get('login_success'))}",
    f"- Total captures: {sum(x.get('scenario_count', 0) for x in summaries)}",
    f"- Critical findings: {critical}",
    f"- Warnings: {warnings}",
    "",
    "| Device | Login | Captures | Critical | Warnings |",
    "|---|---|---:|---:|---:|",
]
for item in summaries:
    lines.append(
        f"| {item.get('device')} | {'PASS' if item.get('login_success') else 'FAIL'} | "
        f"{item.get('scenario_count', 0)} | {item.get('critical_count', 0)} | {item.get('warning_count', 0)} |"
    )
for item in summaries:
    if item.get("critical"):
        lines += ["", f"## {item.get('device')} critical findings", ""] + [f"- {x}" for x in item["critical"]]
    if item.get("warnings"):
        lines += ["", f"## {item.get('device')} warnings", ""] + [f"- {x}" for x in item["warnings"]]
if not summaries:
    lines += ["", "No authenticated device summary was produced."]

(root / "REAL-E2E-REPORT.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
(root / "REAL-E2E-SUMMARY.json").write_text(
    json.dumps(
        {
            "devices": summaries,
            "device_count": len(summaries),
            "critical_count": critical,
            "warning_count": warnings,
            "matrix_complete": len(summaries) == 2,
        },
        ensure_ascii=False,
        indent=2,
    ),
    encoding="utf-8",
)
