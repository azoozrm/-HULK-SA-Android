#!/usr/bin/env python3
"""Create fail-closed evidence for the Compose/instrumentation quality layer."""

from __future__ import annotations

import argparse
import hashlib
import json
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
        "schema_version": 1,
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
    (output / "REPORT.md").write_text(
        "\n".join(
            [
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
                (
                    "> No instrumentation result was produced; this layer is BLOCKED, "
                    "not PASS."
                    if totals["tests"] == 0
                    else ""
                ),
                "",
            ]
        ),
        encoding="utf-8",
    )
    checksums = []
    for path in sorted(output.iterdir()):
        if path.is_file() and path.name != "SHA256SUMS":
            checksums.append(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}")
    (output / "SHA256SUMS").write_text("\n".join(checksums) + "\n", encoding="utf-8")
    return summary


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
    write_evidence(
        output=args.out,
        head_sha=args.head_sha,
        run_id=args.run_id,
        run_attempt=args.run_attempt,
        step_outcome=args.step_outcome,
        results_root=args.results_root,
        reports_root=args.reports_root,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
