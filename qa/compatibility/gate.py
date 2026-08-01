#!/usr/bin/env python3
"""Fail-closed Compatibility Lab gate with separate report-only/product semantics."""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
from typing import Any
from qualification_policy import DOWNSTREAM, gate_decision, normalize_summary


def parse_bool(value: str) -> bool:
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _escape(value: object) -> str:
    return str(value).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def emit(data: dict[str, Any], summary_path: Path) -> None:
    primary = [item for item in data.get("findings", []) if item.get("finding_role") != DOWNSTREAM and item.get("severity") in {"critical", "infrastructure"}]
    downstream = [item for item in data.get("findings", []) if item.get("finding_role") == DOWNSTREAM]
    print(f"::group::Primary root diagnostics ({len(primary)})")
    for item in primary:
        classification = str(item.get("classification") or "unknown").upper()
        code = str(item.get("code") or "unknown")
        case_id = str(item.get("case_id") or item.get("page") or "global")
        message = str(item.get("message") or "No diagnostic message supplied.")
        command = "error" if classification != "PRODUCT" else "warning"
        print(f"::{command} title={_escape(classification + ' ' + code + ' · ' + case_id)}::{_escape(message)}")
        print(json.dumps(item, ensure_ascii=False, sort_keys=True))
    print("::endgroup::")
    print(f"DOWNSTREAM_BLOCKED={len(downstream)}")
    step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if step_summary:
        with Path(step_summary).open("a", encoding="utf-8") as handle:
            handle.write("\n### Compatibility qualification decision\n\n")
            handle.write(f"- Source: `{summary_path}`\n")
            handle.write(f"- Overall: `{data['overall_status']}`\n")
            handle.write(f"- Primary roots: `{data['primary_root_cause_count']}`\n")
            handle.write(f"- Downstream blocked: `{data['downstream_count']}`\n")
            handle.write(f"- Product roots: `{data['product_critical_count']}`\n")
            handle.write(f"- Lab roots: `{data['quality_lab_critical_count']}`\n")
            handle.write(f"- Fixture roots: `{data['fixture_critical_count']}`\n")
            handle.write(f"- Infrastructure roots: `{data['infrastructure_invalidity_count']}`\n")
            handle.write(f"- Missing/blocked roots: `{data['blocked_root_count']}`\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("summary", type=Path)
    parser.add_argument("--enforce-findings", default="false")
    args = parser.parse_args()
    raw = json.loads(args.summary.read_text(encoding="utf-8"))
    data = normalize_summary(raw)
    args.summary.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    emit(data, args.summary)
    code, decision = gate_decision(data, parse_bool(args.enforce_findings))
    print(
        f"{decision}: product_roots={data['product_critical_count']}, "
        f"lab_roots={data['quality_lab_critical_count']}, fixture_roots={data['fixture_critical_count']}, "
        f"infrastructure_roots={data['infrastructure_invalidity_count']}, blocked_roots={data['blocked_root_count']}, "
        f"downstream={data['downstream_count']}, enforce_findings={parse_bool(args.enforce_findings)}"
    )
    return code

if __name__ == "__main__":
    raise SystemExit(main())
