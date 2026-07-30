#!/usr/bin/env python3
"""Create fail-closed evidence for the Compose/instrumentation quality layer."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
from typing import Any
import xml.etree.ElementTree as ET


def junit_totals(root: Path | None) -> tuple[dict[str, int], list[str]]:
    totals = {"files": 0, "tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    malformed: list[str] = []
    if root is None or not root.is_dir():
        return totals, malformed

    for path in sorted(root.rglob("*.xml")):
        try:
            document = ET.parse(path).getroot()
        except ET.ParseError:
            malformed.append(path.as_posix())
            continue
        suites = [document] if document.tag == "testsuite" else list(document.findall("./testsuite"))
        if not suites:
            continue
        totals["files"] += 1
        for suite in suites:
            for key in ("tests", "failures", "errors", "skipped"):
                totals[key] += int(suite.attrib.get(key, "0") or 0)
    return totals, malformed


def junit_failure_details(root: Path | None) -> list[dict[str, str]]:
    details: list[dict[str, str]] = []
    if root is None or not root.is_dir():
        return details
    for path in sorted(root.rglob("*.xml")):
        try:
            document = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for case in document.findall(".//testcase"):
            node = case.find("failure")
            kind = "failure"
            if node is None:
                node = case.find("error")
                kind = "error"
            if node is None:
                continue
            trace = (node.text or "").strip()
            message = str(node.attrib.get("message") or "").strip()
            if not message and trace:
                message = trace.splitlines()[0].strip()
            details.append(
                {
                    "kind": kind,
                    "class_name": str(case.attrib.get("classname") or "unknown"),
                    "test_name": str(case.attrib.get("name") or "unknown"),
                    "message": message or "Instrumentation assertion failed.",
                    "trace": trace[:8000],
                    "junit": path.as_posix(),
                }
            )
    return details


def report_files(root: Path | None) -> list[str]:
    if root is None or not root.is_dir():
        return []
    return sorted(path.as_posix() for path in root.rglob("*") if path.is_file())


def classify(
    *,
    step_outcome: str,
    totals: dict[str, int],
    malformed: list[str],
    reports: list[str],
) -> tuple[str, str, int, int]:
    if malformed:
        return "BLOCKED", "test_harness", 0, len(malformed)
    if totals["tests"] == 0:
        return "BLOCKED", "infrastructure", 0, 1
    if totals["failures"] or totals["errors"]:
        return (
            "FAIL",
            "product",
            totals["failures"] + totals["errors"],
            0,
        )
    if step_outcome != "success" or not reports:
        return "BLOCKED", "infrastructure", 0, 1
    return "PASS", "none", 0, 0


def _report_markdown(
    *,
    status: str,
    classification: str,
    head_sha: str,
    run_id: str,
    run_attempt: str,
    step_outcome: str,
    totals: dict[str, int],
    passed: int,
    reports: list[str],
    failed_tests: list[dict[str, str]],
) -> str:
    lines = [
        "# Compose and instrumentation evidence",
        "",
        f"- Status: **{status}**",
        f"- Classification: `{classification}`",
        f"- Build SHA: `{head_sha}`",
        f"- Workflow run: `{run_id}` / attempt `{run_attempt}`",
        f"- Emulator step: `{step_outcome}`",
        f"- Executed: {totals['tests']}",
        f"- Passed: {passed}",
        f"- Failed: {totals['failures'] + totals['errors']}",
        f"- Skipped: {totals['skipped']}",
        f"- JUnit files: {totals['files']}",
        f"- HTML/report files: {len(reports)}",
        "",
    ]
    if failed_tests:
        lines.extend(
            [
                "## Failed tests",
                "",
                "| Class | Test | Kind | Message | JUnit evidence |",
                "|---|---|---|---|---|",
            ]
        )
        for item in failed_tests:
            values = [
                item["class_name"],
                item["test_name"],
                item["kind"],
                item["message"],
                item["junit"],
            ]
            escaped = [value.replace("|", "\\|").replace("\n", " ") for value in values]
            lines.append("| " + " | ".join(escaped) + " |")
        lines.append("")
    if totals["tests"] == 0:
        lines.extend(
            [
                "> No instrumentation result was produced; this layer is BLOCKED, not PASS.",
                "",
            ]
        )
    return "\n".join(lines) + "\n"


def write_evidence(
    *,
    output: Path,
    head_sha: str,
    run_id: str,
    run_attempt: str,
    step_outcome: str,
    results_root: Path | None,
    reports_root: Path | None,
) -> dict[str, Any]:
    output.mkdir(parents=True, exist_ok=True)
    totals, malformed = junit_totals(results_root)
    failed_tests = junit_failure_details(results_root)
    reports = report_files(reports_root)
    status, classification, critical, infrastructure = classify(
        step_outcome=step_outcome,
        totals=totals,
        malformed=malformed,
        reports=reports,
    )
    passed = max(
        0,
        totals["tests"] - totals["failures"] - totals["errors"] - totals["skipped"],
    )
    summary: dict[str, Any] = {
        "schema_version": 2,
        "overall_status": status,
        "classification": classification,
        "build_sha": head_sha,
        "workflow_run_id": run_id,
        "run_attempt": run_attempt,
        "emulator_step_outcome": step_outcome,
        "device": {
            "kind": "emulator",
            "api": 35,
            "target": "google_apis",
            "arch": "x86_64",
            "profile": "pixel_6",
        },
        "planned": totals["tests"] if totals["tests"] else 0,
        "executed": totals["tests"],
        "passed": passed,
        "failed": totals["failures"] + totals["errors"],
        "skipped": totals["skipped"],
        "product_critical": critical,
        "infrastructure": infrastructure,
        "junit_files": totals["files"],
        "report_files": len(reports),
        "malformed_junit": malformed,
        "failed_tests": failed_tests,
    }
    manifest = {
        "schema_version": 1,
        "build_sha": head_sha,
        "workflow_run_id": run_id,
        "run_attempt": run_attempt,
        "layer": "compose-instrumentation",
        "evidence": {
            "results_root": results_root.as_posix() if results_root else None,
            "reports_root": reports_root.as_posix() if reports_root else None,
            "junit_files": totals["files"],
            "report_files": len(reports),
            "failed_test_count": len(failed_tests),
        },
    }
    (output / "SUMMARY.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    (output / "run-manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    report = _report_markdown(
        status=status,
        classification=classification,
        head_sha=head_sha,
        run_id=run_id,
        run_attempt=run_attempt,
        step_outcome=step_outcome,
        totals=totals,
        passed=passed,
        reports=reports,
        failed_tests=failed_tests,
    )
    (output / "REPORT.md").write_text(report, encoding="utf-8")
    checksums = []
    for path in sorted(output.iterdir()):
        if path.is_file() and path.name != "SHA256SUMS":
            checksums.append(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}")
    (output / "SHA256SUMS").write_text("\n".join(checksums) + "\n", encoding="utf-8")
    return summary


def _workflow_escape(value: object, *, property_value: bool = False) -> str:
    escaped = str(value).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    if property_value:
        escaped = escaped.replace(":", "%3A").replace(",", "%2C")
    return escaped


def emit_failure_diagnostics(summary: dict[str, Any], report_path: Path) -> None:
    failed_tests = summary.get("failed_tests", [])
    if not isinstance(failed_tests, list):
        failed_tests = []
    if failed_tests:
        print(f"::group::Instrumentation failure diagnostics ({len(failed_tests)})")
        for index, item in enumerate(failed_tests, start=1):
            if not isinstance(item, dict):
                continue
            class_name = str(item.get("class_name") or "unknown")
            test_name = str(item.get("test_name") or "unknown")
            message = str(item.get("message") or "Instrumentation assertion failed.")
            junit = str(item.get("junit") or "unknown")
            print(f"FAILED TEST {index}/{len(failed_tests)}")
            print(f"  class: {class_name}")
            print(f"  test: {test_name}")
            print(f"  message: {message}")
            print(f"  junit: {junit}")
            title = f"Instrumentation {class_name}.{test_name}"
            print(
                f"::error title={_workflow_escape(title, property_value=True)}::"
                f"{_workflow_escape(message)}"
            )
        print("::endgroup::")
    destination = os.environ.get("GITHUB_STEP_SUMMARY")
    if destination and report_path.is_file():
        with Path(destination).open("a", encoding="utf-8") as handle:
            handle.write("\n" + report_path.read_text(encoding="utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--head-sha", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--run-attempt", required=True)
    parser.add_argument("--step-outcome", required=True)
    parser.add_argument("--results-root", type=Path)
    parser.add_argument("--reports-root", type=Path)
    args = parser.parse_args()
    summary = write_evidence(
        output=args.out,
        head_sha=args.head_sha,
        run_id=args.run_id,
        run_attempt=args.run_attempt,
        step_outcome=args.step_outcome,
        results_root=args.results_root,
        reports_root=args.reports_root,
    )
    emit_failure_diagnostics(summary, args.out / "REPORT.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
