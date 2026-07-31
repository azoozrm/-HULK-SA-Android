#!/usr/bin/env python3
"""Build deterministic static-gate evidence from Gradle, lint and security outputs."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET


def build_summary(
    *,
    gradle_outcome: str,
    package_outcome: str,
    lint_path: Path,
    vulnerability_path: Path,
    provenance: dict[str, object] | None = None,
) -> dict[str, object]:
    findings: list[dict[str, object]] = []
    infrastructure_errors = int(
        gradle_outcome != "success" or package_outcome != "success"
    )

    if lint_path.is_file():
        lint_issues = ET.parse(lint_path).getroot().findall("issue")
        advisories = [
            item
            for item in lint_issues
            if item.attrib.get("severity") in {"Warning", "Information"}
        ]
        if advisories:
            findings.append(
                {
                    "severity": "P3",
                    "finding_type": "Needs human review",
                    "code": "android_lint_advisories",
                    "message": (
                        f"Android lint reported {len(advisories)} "
                        "warning/advisory issue(s)."
                    ),
                    "expected": "No unexplained Android lint advisories",
                    "actual": (
                        f"{len(advisories)} issue(s); inspect "
                        f"{lint_path.as_posix()}"
                    ),
                    "evidence": {"lint_xml": lint_path.as_posix()},
                }
            )
    elif gradle_outcome == "success":
        infrastructure_errors += 1
        findings.append(
            {
                "severity": "P1",
                "finding_type": "Infrastructure",
                "code": "missing_lint_report",
                "message": "Gradle succeeded but the expected lint XML is missing.",
                "expected": lint_path.as_posix(),
                "actual": "file absent",
            }
        )

    if vulnerability_path.is_file():
        vulnerability = json.loads(vulnerability_path.read_text(encoding="utf-8"))
        if vulnerability.get("status") != "PASS":
            findings.append(
                {
                    "severity": "P3",
                    "finding_type": "Needs human review",
                    "code": "vulnerability_scan_not_executed",
                    "message": vulnerability.get(
                        "reason", "Vulnerability scan was not executed."
                    ),
                    "expected": "An approved vulnerability scan result",
                    "actual": vulnerability.get("status", "UNKNOWN"),
                    "evidence": {"report": vulnerability_path.as_posix()},
                }
            )
    elif package_outcome == "success":
        infrastructure_errors += 1
        findings.append(
            {
                "severity": "P1",
                "finding_type": "Infrastructure",
                "code": "missing_vulnerability_report",
                "message": (
                    "Package qualification succeeded but its security status "
                    "artifact is missing."
                ),
                "expected": vulnerability_path.as_posix(),
                "actual": "file absent",
            }
        )

    cases = [
        {
            "page": "build",
            "status": "PASS" if gradle_outcome == "success" else "FAIL",
        },
        {
            "page": "package",
            "status": "PASS" if package_outcome == "success" else "FAIL",
        },
        {"page": "vulnerability", "status": "SKIPPED"},
    ]
    summary: dict[str, object] = {
        "device": {"id": "static-build", "api": 36},
        "planned_case_count": len(cases),
        # The reporter's executed count represents emitted case evidence rows.
        # A SKIPPED row is still explicit evidence and must not create a second
        # synthetic matrix gap during aggregation.
        "case_count": len(cases),
        "cases": cases,
        "findings": findings,
        "infrastructure_error_count": infrastructure_errors,
    }
    if provenance is not None:
        summary["provenance"] = dict(provenance)
    return summary


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--gradle-outcome", required=True)
    parser.add_argument("--package-outcome", required=True)
    parser.add_argument("--lint", type=Path, required=True)
    parser.add_argument("--vulnerability", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--source-head-sha", required=True)
    parser.add_argument("--base-sha", required=True)
    parser.add_argument("--tested-ref", required=True)
    parser.add_argument("--tested-commit-sha", required=True)
    parser.add_argument("--merge-sha", required=True)
    parser.add_argument("--lab-apk-sha256", required=True)
    parser.add_argument("--workflow-run-id", required=True)
    parser.add_argument("--workflow-run-attempt", required=True)
    args = parser.parse_args()
    summary = build_summary(
        gradle_outcome=args.gradle_outcome,
        package_outcome=args.package_outcome,
        lint_path=args.lint,
        vulnerability_path=args.vulnerability,
        provenance={
            "source_head_sha": args.source_head_sha,
            "base_sha": args.base_sha,
            "tested_ref": args.tested_ref,
            "tested_commit_sha": args.tested_commit_sha,
            "merge_sha": args.merge_sha,
            "lab_apk_sha256": args.lab_apk_sha256,
            "workflow_run_id": args.workflow_run_id,
            "workflow_run_attempt": args.workflow_run_attempt,
        },
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(
        f"PASS: static summary contains {len(summary['findings'])} finding(s) "
        f"and {summary['infrastructure_error_count']} infrastructure error(s)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
