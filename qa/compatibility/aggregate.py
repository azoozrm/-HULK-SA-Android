#!/usr/bin/env python3
"""Aggregate all device artifacts into a compact compatibility report."""

from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from html import escape
import json
import os
from pathlib import Path
import re
import shutil
from typing import Any

from lab_config import DEVICES


def safe_name(value: str) -> str:
    return re.sub(r"[^a-zA-Z0-9._-]+", "-", value).strip("-") or "evidence"


def copy_evidence(
    source_root: Path,
    report_root: Path,
    device_id: str,
    finding: dict[str, Any],
    index: int,
) -> dict[str, str]:
    copied: dict[str, str] = {}
    case = safe_name(finding.get("case_id") or finding.get("page") or f"finding-{index}")
    destination = report_root / "evidence" / safe_name(device_id) / case
    for key, relative in finding.get("evidence", {}).items():
        source = source_root / relative
        if not source.is_file():
            continue
        suffix = source.suffix or ".txt"
        target = destination / f"{safe_name(key)}{suffix}"
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        copied[key] = target.relative_to(report_root).as_posix()
    return copied


def markdown(summary: dict[str, Any]) -> str:
    lines = [
        "# HULK SA Compatibility Lab",
        "",
        f"- Overall: **{summary['overall_status']}**",
        f"- Devices completed: {summary['device_count']} / {summary['expected_device_count']}",
        f"- Page captures: {summary['case_count']}",
        f"- Raw failed checks: {summary.get('raw_failed_checks_count', summary['critical_count'])}",
        f"- Primary root causes: {summary.get('primary_root_cause_count', 0)}",
        f"- Downstream / blocked assertions: {summary.get('downstream_count', 0)}",
        f"- Product-critical: {summary.get('product_critical_count', 0)}",
        f"- Quality-lab-critical: {summary.get('quality_lab_critical_count', 0)}",
        f"- Fixture-critical: {summary.get('fixture_critical_count', 0)}",
        f"- Critical findings: {summary['critical_count']}",
        f"- Warnings: {summary['warning_count']}",
        f"- Infrastructure errors: {summary['infrastructure_error_count']}",
        "",
        "## Device matrix",
        "",
        "| Device | Class | API | Geometry / density | Captures | Critical | Warnings | Infrastructure | Status |",
        "|---|---|---:|---:|---:|---:|---:|---:|---|",
    ]
    for device in summary["devices"]:
        meta = device["device"]
        lines.append(
            f"| {meta['name']} | {meta['family']} | {meta['api']} | "
            f"{meta['requested_width']}×{meta['requested_height']} @ {meta['requested_density']} | "
            f"{device['case_count']} | {device['critical_count']} | {device['warning_count']} | "
            f"{device['infrastructure_error_count']} | **{device['overall_status']}** |"
        )
    for missing in summary["missing_devices"]:
        lines.append(f"| `{missing}` | — | — | — | 0 | 0 | 0 | 1 | **BLOCKED** |")

    lines += [
        "",
        "## Coverage",
        "",
        "| Page | Captures | PASS | WARN | FAIL | BLOCKED |",
        "|---|---:|---:|---:|---:|---:|",
    ]
    for page, counts in summary["page_coverage"].items():
        lines.append(
            f"| {page} | {counts['total']} | {counts['PASS']} | {counts['WARN']} | "
            f"{counts['FAIL']} | {counts['BLOCKED']} |"
        )

    lines += ["", "## Findings", ""]
    if not summary["findings"]:
        lines.append("- No findings.")
    for item in summary["findings"]:
        links = []
        for label, key in (("screenshot", "screenshot"), ("XML", "xml"), ("logcat", "logcat")):
            path = item.get("evidence", {}).get(key)
            if path:
                links.append(f"[{label}]({path})")
        suffix = f" — {' · '.join(links)}" if links else ""
        lines.append(
            f"- **{item['severity'].upper()} · {item['device_name']} · "
            f"`{item['code']}`** — {item['message']}{suffix}"
        )
    lines += [
        "",
        "## Interpretation",
        "",
        "- `PASS`: capture completed with no deterministic finding.",
        "- `WARN`: inspect layout/performance advisory evidence; the lab itself completed.",
        "- `FAIL`: deterministic crash, ANR, render, bounds, navigation or focus failure.",
        "- `BLOCKED`: emulator or capture infrastructure did not complete; do not treat it as an app result.",
        "",
        "The authenticated shell is supplied by a debug-only deterministic fixture. "
        "Login credentials and the production portal are not exercised by this lab.",
        "",
    ]
    return "\n".join(lines)


def html_report(summary: dict[str, Any]) -> str:
    device_rows = []
    for device in summary["devices"]:
        meta = device["device"]
        device_rows.append(
            "<tr>"
            f"<td>{escape(meta['name'])}</td><td>{escape(meta['family'])}</td>"
            f"<td>{meta['api']}</td>"
            f"<td>{meta['requested_width']}×{meta['requested_height']} @ {meta['requested_density']}</td>"
            f"<td>{device['case_count']}</td><td>{device['critical_count']}</td>"
            f"<td>{device['warning_count']}</td><td>{device['infrastructure_error_count']}</td>"
            f'<td><span class="badge {device["overall_status"].lower()}">'
            f'{device["overall_status"]}</span></td></tr>'
        )
    for missing in summary["missing_devices"]:
        device_rows.append(
            f"<tr><td><code>{escape(missing)}</code></td><td colspan=\"7\">Missing device result</td>"
            '<td><span class="badge blocked">BLOCKED</span></td></tr>'
        )

    findings = []
    for item in summary["findings"]:
        links = []
        screenshot = item.get("evidence", {}).get("screenshot")
        if screenshot:
            links.append(
                f'<a href="{escape(screenshot)}"><img loading="lazy" '
                f'src="{escape(screenshot)}" alt="finding screenshot"></a>'
            )
        text_links = []
        for label, key in (("PNG", "screenshot"), ("XML", "xml"), ("LOG", "logcat")):
            path = item.get("evidence", {}).get(key)
            if path:
                text_links.append(f'<a href="{escape(path)}">{label}</a>')
        findings.append(
            f'<article class="finding {escape(item["severity"])}">'
            f'<div><span class="badge {escape(item["severity"])}">'
            f'{escape(item["severity"].upper())}</span> '
            f'<strong>{escape(item["device_name"])}</strong> '
            f'<code>{escape(item["code"])}</code></div>'
            f'<p>{escape(item["message"])}</p>'
            f'<div class="evidence">{"".join(links)}<span>{" · ".join(text_links)}</span></div>'
            "</article>"
        )
    status = summary["overall_status"]
    return f"""<!doctype html>
<html lang="en" dir="ltr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>HULK SA Compatibility Lab</title>
<style>
:root {{ color-scheme:dark; --bg:#090a07; --panel:#12140f; --line:#303426;
--text:#f3f0e6; --muted:#aaa99f; --gold:#d9ad45; --pass:#4bc279;
--warn:#f2b84b; --fail:#ff6767; --blocked:#b28cff }}
*{{box-sizing:border-box}} body{{margin:0;background:var(--bg);color:var(--text);
font:15px/1.5 system-ui,-apple-system,Segoe UI,sans-serif}}
main{{width:min(1500px,96vw);margin:28px auto 70px}} h1,h2{{line-height:1.2}}
.hero{{padding:26px;border:1px solid var(--line);border-radius:18px;background:var(--panel)}}
.metrics{{display:grid;grid-template-columns:repeat(5,minmax(110px,1fr));gap:12px;margin:18px 0}}
.metric{{padding:16px;border:1px solid var(--line);border-radius:14px;background:var(--panel)}}
.metric strong{{display:block;font-size:25px}} .muted{{color:var(--muted)}}
.badge{{display:inline-block;padding:3px 9px;border-radius:999px;font-weight:800;font-size:12px}}
.pass{{color:var(--pass)}} .warn,.warning{{color:var(--warn)}} .fail,.critical{{color:var(--fail)}}
.blocked,.infrastructure{{color:var(--blocked)}} table{{width:100%;border-collapse:collapse;background:var(--panel)}}
th,td{{padding:10px;border-bottom:1px solid var(--line);text-align:left}} th{{color:var(--gold)}}
a{{color:var(--gold)}} code{{word-break:break-word}}
.finding{{padding:15px;margin:11px 0;border:1px solid var(--line);border-left-width:5px;
border-radius:12px;background:var(--panel)}} .finding.critical{{border-left-color:var(--fail)}}
.finding.warning{{border-left-color:var(--warn)}} .finding.infrastructure{{border-left-color:var(--blocked)}}
.evidence{{display:flex;gap:14px;align-items:center;flex-wrap:wrap}}
.evidence img{{width:160px;max-height:240px;object-fit:contain;border:1px solid var(--line);border-radius:8px}}
@media(max-width:850px){{.metrics{{grid-template-columns:repeat(2,1fr)}}table{{display:block;overflow:auto}}}}
</style></head><body><main>
<section class="hero"><span class="badge {status.lower()}">{escape(status)}</span>
<h1>HULK SA Compatibility Lab</h1>
<p class="muted">Phones · Tablets · Android TV · Portrait · Landscape · RTL · density and font-scale stress</p>
</section>
<section class="metrics">
<div class="metric"><span class="muted">Devices</span><strong>{summary['device_count']}/{summary['expected_device_count']}</strong></div>
<div class="metric"><span class="muted">Captures</span><strong>{summary['case_count']}</strong></div>
<div class="metric"><span class="muted">Critical</span><strong>{summary['critical_count']}</strong></div>
<div class="metric"><span class="muted">Warnings</span><strong>{summary['warning_count']}</strong></div>
<div class="metric"><span class="muted">Infrastructure</span><strong>{summary['infrastructure_error_count']}</strong></div>
</section>
<h2>Device matrix</h2>
<table><thead><tr><th>Device</th><th>Class</th><th>API</th><th>Geometry</th><th>Captures</th>
<th>Critical</th><th>Warnings</th><th>Infrastructure</th><th>Status</th></tr></thead>
<tbody>{''.join(device_rows)}</tbody></table>
<h2>Findings</h2>
{''.join(findings) if findings else '<p>No findings.</p>'}
<p class="muted">Open individual device artifacts for every raw screenshot, UI XML, logcat,
gfxinfo, meminfo and window dump. This aggregate contains evidence for findings.</p>
</main></body></html>"""


def aggregate(input_root: Path, output_root: Path) -> dict[str, Any]:
    input_root = input_root.resolve()
    output_root = output_root.resolve()
    output_root.mkdir(parents=True, exist_ok=True)
    expected_ids = [device["id"] for device in DEVICES]
    loaded: dict[str, tuple[Path, dict[str, Any]]] = {}
    duplicate_ids: list[str] = []
    parse_errors: list[str] = []
    for path in sorted(input_root.rglob("summary.json")):
        if output_root in path.parents:
            continue
        if "attempts" in path.parts:
            continue
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
            device_id = data["device"]["id"]
        except Exception as exc:
            parse_errors.append(f"{path}: {exc}")
            continue
        if device_id in loaded:
            duplicate_ids.append(device_id)
            continue
        loaded[device_id] = (path.parent, data)

    missing = sorted(set(expected_ids) - loaded.keys())
    unexpected = sorted(set(loaded) - set(expected_ids))
    devices = [
        loaded[device_id][1]
        for device_id in expected_ids
        if device_id in loaded
    ]
    all_findings: list[dict[str, Any]] = []
    page_counts: dict[str, Counter[str]] = defaultdict(Counter)
    for device in devices:
        meta = device["device"]
        source_root = loaded[meta["id"]][0]
        for case in device.get("cases", []):
            page_counts[case["page"]][case["status"]] += 1
            page_counts[case["page"]]["total"] += 1
        for index, item in enumerate(device.get("findings", []), start=1):
            enriched = dict(item)
            enriched["device_id"] = meta["id"]
            enriched["device_name"] = meta["name"]
            enriched["evidence"] = copy_evidence(
                source_root,
                output_root,
                meta["id"],
                item,
                index,
            )
            all_findings.append(enriched)

    infrastructure_extra = len(missing) + len(unexpected) + len(duplicate_ids) + len(parse_errors)
    critical = sum(device.get("critical_count", 0) for device in devices)
    warnings = sum(device.get("warning_count", 0) for device in devices)
    infrastructure = (
        sum(device.get("infrastructure_error_count", 0) for device in devices)
        + infrastructure_extra
    )
    overall = (
        "BLOCKED"
        if infrastructure
        else "FAIL"
        if critical
        else "WARN"
        if warnings
        else "PASS"
    )
    page_coverage: dict[str, dict[str, int]] = {}
    for page in (
        "home",
        "live",
        "movies",
        "series",
        "favorites",
        "search",
        "downloads",
        "settings",
    ):
        counts = page_counts[page]
        page_coverage[page] = {
            key: int(counts.get(key, 0))
            for key in ("total", "PASS", "WARN", "FAIL", "BLOCKED")
        }
    summary = {
        "schema_version": 2,
        "overall_status": overall,
        "expected_device_count": len(expected_ids),
        "device_count": len(devices),
        "case_count": sum(device.get("case_count", 0) for device in devices),
        "critical_count": critical,
        "warning_count": warnings,
        "infrastructure_error_count": infrastructure,
        "missing_devices": missing,
        "unexpected_devices": unexpected,
        "duplicate_devices": duplicate_ids,
        "parse_errors": parse_errors,
        "page_coverage": page_coverage,
        "devices": devices,
        "findings": all_findings,
        "primary_root_cause_count": len({str(item.get("root_cause_id") or item.get("code")) for item in all_findings if item.get("finding_role", "primary") == "primary" and item.get("severity") in {"critical", "infrastructure"}}),
        "raw_failed_checks_count": sum(item.get("severity") in {"critical", "infrastructure"} for item in all_findings),
        "downstream_count": sum(item.get("finding_role") in {"downstream", "blocked_assertion"} for item in all_findings),
        "product_critical_count": sum(item.get("severity") == "critical" and item.get("classification", "product") == "product" and item.get("product_strict", True) for item in all_findings),
        "quality_lab_critical_count": sum(item.get("severity") == "critical" and item.get("classification") == "quality_lab" for item in all_findings),
        "fixture_critical_count": sum(item.get("severity") == "critical" and item.get("classification") == "fixture" for item in all_findings),
        "false_positives": sum(bool(item.get("reclassified_from")) or item.get("classification") == "false_positive" for item in all_findings),
    }
    (output_root / "COMPATIBILITY-LAB-SUMMARY.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    report_md = markdown(summary)
    (output_root / "COMPATIBILITY-LAB-REPORT.md").write_text(report_md, encoding="utf-8")
    (output_root / "COMPATIBILITY-LAB-REPORT.html").write_text(
        html_report(summary),
        encoding="utf-8",
    )
    (output_root / "GITHUB_STEP_SUMMARY.md").write_text(report_md, encoding="utf-8")
    files = sorted(
        path.relative_to(output_root).as_posix()
        for path in output_root.rglob("*")
        if path.is_file()
    )
    (output_root / "FILES.txt").write_text("\n".join(files) + "\n", encoding="utf-8")
    return summary


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    summary = aggregate(args.input, args.output)
    print(
        f"{summary['overall_status']}: {summary['device_count']}/"
        f"{summary['expected_device_count']} devices, {summary['case_count']} captures"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
