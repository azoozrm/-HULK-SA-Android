#!/usr/bin/env python3
"""Apply Compatibility Lab infrastructure and optional product gates."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
from typing import Any


def parse_bool(value: str) -> bool:
    return value.strip().lower() in {"1", "true", "yes", "on"}


def reconcile_final_page_markers(
    data: dict[str, Any],
    summary_path: Path,
) -> list[dict[str, str]]:
    """Reconcile a marker timeout only when the final captured XML proves it.

    The driver waits for a semantic page marker before capture, but on a slow
    emulator that wait can time out immediately before the final hierarchy is
    written. A final hierarchy containing the exact marker is stronger evidence
    than the earlier timeout. This function never guesses from screenshots or
    page titles and never changes findings whose XML is missing or malformed.
    """

    root = summary_path.parent.resolve()
    corrections: list[dict[str, str]] = []
    cases = {
        str(case.get("id")): case
        for case in data.get("cases", [])
        if isinstance(case, dict) and case.get("id")
    }

    for item in data.get("findings", []):
        if not isinstance(item, dict):
            continue
        if item.get("severity") != "critical" or item.get("code") != "page_marker_missing":
            continue

        page = str(item.get("page") or "").strip()
        case_id = str(item.get("case_id") or "").strip()
        evidence = item.get("evidence") if isinstance(item.get("evidence"), dict) else {}
        xml_relative = str(evidence.get("xml") or "").strip()
        if not page or not xml_relative:
            continue

        xml_path = (root / xml_relative).resolve()
        if xml_path != root and root not in xml_path.parents:
            continue
        if not xml_path.is_file():
            continue

        marker = f"qa-page:{page}"
        try:
            marker_present = marker.encode("utf-8") in xml_path.read_bytes()
        except OSError:
            marker_present = False
        if not marker_present:
            continue

        original_message = str(item.get("message") or "")
        item.update(
            {
                "severity": "warning",
                "code": "page_marker_reconciled_from_final_hierarchy",
                "message": (
                    f"{case_id}: the initial marker wait timed out, but the final "
                    f"captured hierarchy contains {marker!r}; reclassified as a "
                    "harness timing advisory."
                ),
                "reclassified_from": {
                    "severity": "critical",
                    "code": "page_marker_missing",
                    "message": original_message,
                },
            }
        )
        case = cases.get(case_id)
        if case is not None:
            case["marker_found"] = True
            if case.get("status") == "FAIL":
                case["status"] = "WARN"

        corrections.append(
            {
                "case_id": case_id,
                "page": page,
                "marker": marker,
                "xml": xml_relative,
            }
        )

    if corrections:
        findings = [item for item in data.get("findings", []) if isinstance(item, dict)]
        data["critical_count"] = sum(
            item.get("severity") == "critical" for item in findings
        )
        data["warning_count"] = sum(
            item.get("severity") == "warning" for item in findings
        )
        infrastructure = int(data.get("infrastructure_error_count", 0))
        if infrastructure:
            data["overall_status"] = "BLOCKED"
        elif data["critical_count"]:
            data["overall_status"] = "FAIL"
        elif data["warning_count"]:
            data["overall_status"] = "WARN"
        else:
            data["overall_status"] = "PASS"
        data["gate_corrections"] = corrections
        summary_path.write_text(
            json.dumps(data, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        (summary_path.parent / "GATE-CORRECTIONS.json").write_text(
            json.dumps(corrections, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )

    return corrections


def _findings(data: dict[str, Any], severity: str) -> list[dict[str, Any]]:
    return [
        item
        for item in data.get("findings", [])
        if isinstance(item, dict) and str(item.get("severity")) == severity
    ]


def _workflow_escape(value: object, *, property_value: bool = False) -> str:
    escaped = str(value).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    if property_value:
        escaped = escaped.replace(":", "%3A").replace(",", "%2C")
    return escaped


def _markdown_escape(value: object) -> str:
    return str(value).replace("|", "\\|").replace("\r", " ").replace("\n", " ")


def emit_finding_diagnostics(
    data: dict[str, Any],
    summary_path: Path,
    severity: str,
) -> int:
    """Print actionable findings and append them to the Actions step summary."""

    findings = _findings(data, severity)
    declared_count = int(
        data.get(
            "critical_count" if severity == "critical" else "infrastructure_error_count",
            0,
        )
    )
    print(f"::group::{severity.upper()} finding diagnostics ({len(findings)} detailed)")
    if declared_count and not findings:
        print(
            "DIAGNOSTIC ERROR: summary declares "
            f"{declared_count} {severity} finding(s), but no detailed finding records exist."
        )

    rows: list[str] = []
    command = "error" if severity in {"critical", "infrastructure"} else "warning"
    for index, item in enumerate(findings, start=1):
        code = str(item.get("code") or "unknown")
        case_id = str(item.get("case_id") or "unknown")
        page = str(item.get("page") or "unknown")
        message = str(item.get("message") or "No diagnostic message supplied.")
        evidence = item.get("evidence") if isinstance(item.get("evidence"), dict) else {}
        evidence_items = [
            f"{name}={path}"
            for name, path in sorted(evidence.items())
            if str(path).strip()
        ]
        evidence_text = "; ".join(evidence_items) or "none"

        print(f"{severity.upper()} {index}/{len(findings)}")
        print(f"  code: {code}")
        print(f"  case: {case_id}")
        print(f"  page: {page}")
        print(f"  message: {message}")
        print(f"  evidence: {evidence_text}")
        title = f"{severity.upper()} {code} · {case_id}"
        print(
            f"::{command} title={_workflow_escape(title, property_value=True)}::"
            f"{_workflow_escape(message)}"
        )
        rows.append(
            "| "
            + " | ".join(
                (
                    _markdown_escape(severity.upper()),
                    f"`{_markdown_escape(code)}`",
                    f"`{_markdown_escape(case_id)}`",
                    _markdown_escape(page),
                    _markdown_escape(message),
                    f"`{_markdown_escape(evidence_text)}`",
                )
            )
            + " |"
        )
    print("::endgroup::")

    step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if step_summary:
        target = Path(step_summary)
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("a", encoding="utf-8") as handle:
            handle.write(f"\n### {severity.title()} compatibility diagnostics\n\n")
            handle.write(f"Source summary: `{summary_path}`\n\n")
            if rows:
                handle.write("| Severity | Code | Case | Page | Message | Evidence |\n")
                handle.write("|---|---|---|---|---|---|\n")
                handle.write("\n".join(rows) + "\n")
            else:
                handle.write(
                    f"No detailed `{severity}` finding records were present; "
                    f"declared count was `{declared_count}`.\n"
                )
    return len(findings)


def evaluate_gate(data: dict[str, Any], summary_path: Path, enforce: bool) -> int:
    infrastructure = int(data.get("infrastructure_error_count", 0))
    critical = int(data.get("critical_count", 0))
    warnings = int(data.get("warning_count", 0))

    if infrastructure:
        print(f"BLOCKED: {infrastructure} Compatibility Lab infrastructure error(s)")
        emit_finding_diagnostics(data, summary_path, "infrastructure")
        if critical:
            emit_finding_diagnostics(data, summary_path, "critical")
        return 2

    if critical:
        emit_finding_diagnostics(data, summary_path, "critical")
        if enforce:
            print(f"FAIL: {critical} critical application compatibility finding(s)")
            return 1
        print(
            "DETECTED: "
            f"{critical} critical product finding(s) preserved as evidence; "
            "product enforcement is disabled only for this lab-only qualification."
        )

    print(
        f"PASS: lab infrastructure completed; critical={critical}, warnings={warnings}, "
        f"enforce_findings={str(enforce).lower()}"
    )
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("summary", type=Path)
    parser.add_argument("--enforce-findings", default="false")
    args = parser.parse_args()
    data = json.loads(args.summary.read_text(encoding="utf-8"))
    corrections = reconcile_final_page_markers(data, args.summary)
    if corrections:
        print(
            "RECONCILED: "
            f"{len(corrections)} page marker timeout(s) were proven present "
            "by final XML evidence"
        )
    return evaluate_gate(data, args.summary, parse_bool(args.enforce_findings))


if __name__ == "__main__":
    raise SystemExit(main())
