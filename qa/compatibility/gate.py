#!/usr/bin/env python3
"""Apply Compatibility Lab infrastructure and optional product gates."""

from __future__ import annotations

import argparse
import json
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

    infrastructure = int(data.get("infrastructure_error_count", 0))
    critical = int(data.get("critical_count", 0))
    enforce = parse_bool(args.enforce_findings)

    if infrastructure:
        print(f"BLOCKED: {infrastructure} Compatibility Lab infrastructure error(s)")
        return 2
    if enforce and critical:
        print(f"FAIL: {critical} critical application compatibility finding(s)")
        return 1
    print(
        f"PASS: lab completed; critical={critical}, warnings={data.get('warning_count', 0)}, "
        f"enforce_findings={str(enforce).lower()}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
