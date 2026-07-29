#!/usr/bin/env python3
"""Aggregate evidence into the mandatory Quality Lab artifact contract."""

from __future__ import annotations

import argparse
from html import escape
import json
from pathlib import Path
import shutil
from typing import Any
import xml.etree.ElementTree as ET

from qa.quality.core.models import stable_fingerprint
from qa.quality.analyzers.evidence import file_sha256


REQUIRED_OUTPUTS = (
    "SUMMARY.json",
    "REPORT.md",
    "REPORT.html",
    "junit.xml",
    "run-manifest.json",
    "findings.json",
    "coverage.json",
    "impact.json",
)


def _finding(raw: dict[str, Any], device: str, build_sha: str) -> dict[str, Any]:
    severity = raw.get("severity", "P3").upper()
    severity = {"CRITICAL": "P1", "WARNING": "P2", "INFRASTRUCTURE": "P1"}.get(
        severity, severity
    )
    finding_type = raw.get("finding_type") or (
        "Infrastructure"
        if raw.get("severity") == "infrastructure"
        else "Needs human review"
        if severity in {"P2", "P3"}
        else "Product"
    )
    code = raw.get("code", "unspecified")
    screen = raw.get("page") or raw.get("screen") or "unknown"
    actual = raw.get("actual") or raw.get("message", "finding reported")
    expected = raw.get("expected") or "quality contract satisfied"
    return {
        "fingerprint": raw.get("fingerprint")
        or stable_fingerprint(code, device, screen, expected, actual),
        "code": code,
        "severity": severity if severity in {"P0", "P1", "P2", "P3"} else "P3",
        "finding_type": finding_type,
        "message": raw.get("message", code),
        "expected": expected,
        "actual": actual,
        "device": device,
        "api": raw.get("api"),
        "orientation": raw.get("orientation", ""),
        "density": raw.get("density"),
        "font_scale": raw.get("font_scale"),
        "screen": screen,
        "journey": raw.get("journey") or raw.get("case_id") or screen,
        "build_sha": build_sha,
        "reproduction": raw.get("reproduction", []),
        "root_cause": raw.get("root_cause"),
        "suggested_owner": raw.get("suggested_owner", "quality-triage"),
        "regression_test_id": raw.get("regression_test_id"),
        "evidence": raw.get("evidence", {}),
    }


def _status(findings: list[dict[str, Any]], infrastructure: int, executed: int) -> str:
    if infrastructure:
        return "BLOCKED"
    if any(item["finding_type"] == "Product" and item["severity"] in {"P0", "P1"} for item in findings):
        return "FAIL"
    if executed == 0:
        return "NOT VERIFIED"
    if findings:
        return "PASS WITH WARNINGS"
    return "PASS"


def _markdown(summary: dict[str, Any], findings: list[dict[str, Any]]) -> str:
    lines = [
        "# HULK SA Quality Engineering Lab",
        "",
        f"- Recommendation: **{summary['release_recommendation']}**",
        f"- Build SHA: `{summary['build_sha']}`",
        f"- Planned / executed / skipped: {summary['planned']} / {summary['executed']} / {summary['skipped']}",
        f"- Passed / failed: {summary['passed']} / {summary['failed']}",
        f"- Retries: {summary['retried']}",
        f"- Product P0/P1: {summary['product_critical']}",
        f"- Infrastructure: {summary['infrastructure']}",
        f"- Warnings: {summary['warnings']}",
        "",
        "## Findings",
        "",
    ]
    if not findings:
        lines.append("- No findings.")
    for item in findings:
        lines.append(
            f"- **{item['severity']} · {item['finding_type']} · `{item['code']}`** — "
            f"{item['device']} / {item['screen']}: {item['message']} "
            f"(`{item['fingerprint']}`)"
        )
    lines += [
        "",
        "A missing summary or missing expected device artifact is an Infrastructure BLOCKED result, never PASS.",
        "",
    ]
    return "\n".join(lines)


def _html(summary: dict[str, Any], findings: list[dict[str, Any]]) -> str:
    rows = "".join(
        "<tr>"
        f"<td>{escape(item['severity'])}</td><td>{escape(item['finding_type'])}</td>"
        f"<td><code>{escape(item['code'])}</code></td><td>{escape(item['device'])}</td>"
        f"<td>{escape(item['screen'])}</td><td>{escape(item['message'])}</td>"
        "</tr>"
        for item in findings
    )
    return f"""<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>HULK SA Quality Engineering Lab</title>
<style>body{{font:15px system-ui;background:#090a07;color:#f4f0e4;margin:2rem}}
code{{color:#e2b94f}} table{{border-collapse:collapse;width:100%}}td,th{{padding:.6rem;border-bottom:1px solid #333}}
.status{{font-size:1.4rem;font-weight:800}}</style></head><body>
<h1>HULK SA Quality Engineering Lab</h1>
<p class="status">{escape(summary['release_recommendation'])}</p>
<p>Build <code>{escape(summary['build_sha'])}</code> · executed {summary['executed']} / {summary['planned']}</p>
<table><thead><tr><th>Severity</th><th>Type</th><th>Code</th><th>Device</th><th>Screen</th><th>Message</th></tr></thead>
<tbody>{rows}</tbody></table></body></html>"""


def _junit(summary: dict[str, Any], findings: list[dict[str, Any]]) -> str:
    suite = ET.Element(
        "testsuite",
        name="HULK-SA-Quality-Lab",
        tests=str(max(summary["executed"], 1)),
        failures=str(summary["failed"]),
        errors=str(summary["infrastructure"]),
        skipped=str(summary["skipped"]),
    )
    if not findings:
        ET.SubElement(suite, "testcase", classname="quality", name="evidence-contract")
    for item in findings:
        case = ET.SubElement(
            suite,
            "testcase",
            classname=f"quality.{item['screen']}",
            name=item["fingerprint"],
        )
        if item["finding_type"] == "Infrastructure":
            ET.SubElement(case, "error", message=item["message"]).text = item["actual"]
        elif item["severity"] in {"P0", "P1"} and item["finding_type"] == "Product":
            ET.SubElement(case, "failure", message=item["message"]).text = item["actual"]
        else:
            ET.SubElement(case, "system-out").text = item["message"]
    return ET.tostring(suite, encoding="unicode")


def aggregate(
    input_root: Path,
    output: Path,
    *,
    build_sha: str,
    source_branch: str,
    workflow: str,
    run_id: str,
    expected_devices: list[str],
    impact: dict[str, Any],
) -> dict[str, Any]:
    output.mkdir(parents=True, exist_ok=True)
    summaries: dict[str, dict[str, Any]] = {}
    parse_errors: list[str] = []
    for path in sorted(input_root.rglob("summary.json")):
        if "attempts" in path.parts:
            continue
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
            device = (
                data.get("device", {}).get("id")
                or data.get("device_id")
                or path.parent.name
            )
            if device in summaries:
                parse_errors.append(f"duplicate summary for {device}: {path}")
            else:
                summaries[device] = data
        except Exception as exc:
            parse_errors.append(f"{path}: {exc}")
    missing = sorted(set(expected_devices) - summaries.keys())
    findings: list[dict[str, Any]] = []
    planned = executed = passed = failed = skipped = retried = 0
    infrastructure = len(parse_errors) + len(missing)
    devices: list[dict[str, Any]] = []
    for device, data in sorted(summaries.items()):
        cases = data.get("cases", [])
        planned += int(data.get("planned_case_count", len(cases)))
        executed += int(data.get("case_count", len(cases)))
        passed += sum(case.get("status") == "PASS" for case in cases)
        failed += sum(case.get("status") == "FAIL" for case in cases)
        skipped += sum(case.get("status") in {"SKIPPED", "BLOCKED"} for case in cases)
        retried += int(data.get("retry_count", 0))
        infrastructure += int(data.get("infrastructure_error_count", 0))
        devices.append(
            {
                "id": device,
                "api": data.get("device", {}).get("api") or data.get("api"),
                "status": data.get("overall_status") or data.get("status") or "UNKNOWN",
                "case_count": int(data.get("case_count", len(cases))),
            }
        )
        findings.extend(_finding(item, device, build_sha) for item in data.get("findings", []))
    for device in missing:
        findings.append(
            _finding(
                {
                    "code": "missing_device_artifact",
                    "severity": "P1",
                    "finding_type": "Infrastructure",
                    "message": f"Expected device artifact is missing: {device}",
                    "expected": "summary.json and raw evidence",
                    "actual": "artifact absent",
                },
                device,
                build_sha,
            )
        )
    for error in parse_errors:
        findings.append(
            _finding(
                {
                    "code": "malformed_or_duplicate_summary",
                    "severity": "P1",
                    "finding_type": "Infrastructure",
                    "message": error,
                    "expected": "one parseable summary per device",
                    "actual": error,
                },
                "aggregation",
                build_sha,
            )
        )
    product_critical = sum(
        item["finding_type"] == "Product" and item["severity"] in {"P0", "P1"}
        for item in findings
    )
    warnings = sum(item["severity"] in {"P2", "P3"} for item in findings)
    recommendation = _status(findings, infrastructure, executed)
    summary = {
        "schema_version": 1,
        "release_recommendation": recommendation,
        "build_sha": build_sha,
        "source_branch": source_branch,
        "workflow": workflow,
        "run_id": run_id,
        "planned": planned,
        "executed": executed,
        "passed": passed,
        "failed": failed,
        "skipped": skipped,
        "retried": retried,
        "product_critical": product_critical,
        "infrastructure": infrastructure,
        "warnings": warnings,
        "false_positives": sum(item["finding_type"] == "False positive" for item in findings),
        "devices": devices,
        "missing_devices": missing,
        "parse_errors": parse_errors,
    }
    coverage = {
        "schema_version": 1,
        "planned": planned,
        "executed": executed,
        "devices_expected": expected_devices,
        "devices_observed": sorted(summaries),
    }
    run_manifest = {
        "schema_version": 1,
        "build_sha": build_sha,
        "source_branch": source_branch,
        "workflow": workflow,
        "run_id": run_id,
        "expected_devices": expected_devices,
        "artifact_contract": list(REQUIRED_OUTPUTS) + ["SHA256SUMS"],
    }
    (output / "SUMMARY.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (output / "findings.json").write_text(json.dumps({"schema_version": 1, "findings": findings}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (output / "coverage.json").write_text(json.dumps(coverage, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (output / "run-manifest.json").write_text(json.dumps(run_manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (output / "impact.json").write_text(json.dumps(impact, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report = _markdown(summary, findings)
    (output / "REPORT.md").write_text(report, encoding="utf-8")
    (output / "REPORT.html").write_text(_html(summary, findings), encoding="utf-8")
    (output / "junit.xml").write_text(_junit(summary, findings), encoding="utf-8")
    for required in REQUIRED_OUTPUTS:
        if not (output / required).is_file():
            raise RuntimeError(f"reporter failed to create {required}")
    checksum_lines = [
        f"{file_sha256(path)}  {path.relative_to(output).as_posix()}"
        for path in sorted(output.rglob("*"))
        if path.is_file() and path.name != "SHA256SUMS"
    ]
    (output / "SHA256SUMS").write_text("\n".join(checksum_lines) + "\n", encoding="utf-8")
    return summary


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--build-sha", required=True)
    parser.add_argument("--source-branch", required=True)
    parser.add_argument("--workflow", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--expected-devices", default="")
    parser.add_argument("--impact", type=Path)
    args = parser.parse_args()
    impact = (
        json.loads(args.impact.read_text(encoding="utf-8"))
        if args.impact and args.impact.is_file()
        else {"schema_version": 1, "selected_tests": [], "status": "not-provided"}
    )
    summary = aggregate(
        args.input,
        args.output,
        build_sha=args.build_sha,
        source_branch=args.source_branch,
        workflow=args.workflow,
        run_id=args.run_id,
        expected_devices=[item for item in args.expected_devices.split(",") if item],
        impact=impact,
    )
    print(
        f"{summary['release_recommendation']}: "
        f"{summary['executed']}/{summary['planned']} cases"
    )
    return 2 if summary["release_recommendation"] == "BLOCKED" else 1 if summary["release_recommendation"] == "FAIL" else 0


if __name__ == "__main__":
    raise SystemExit(main())
