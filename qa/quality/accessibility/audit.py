#!/usr/bin/env python3
"""Audit runtime UI hierarchy labels, state semantics and target geometry."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
from typing import Any
import xml.etree.ElementTree as ET

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[3]))

from qa.quality.analyzers.evidence import APP_PACKAGES, parse_bounds


def audit_xml(path: Path, *, density: int, is_tv: bool) -> list[dict[str, Any]]:
    root = ET.parse(path).getroot()
    findings: list[dict[str, Any]] = []
    pixels_per_dp = max(density / 160.0, 0.01)
    minimum_dp = 40.0 if is_tv else 48.0
    for index, node in enumerate(root.iter("node")):
        if node.attrib.get("package") not in APP_PACKAGES:
            continue
        interactive = any(
            node.attrib.get(name) == "true"
            for name in ("clickable", "focusable", "checkable")
        )
        if not interactive:
            continue
        label = (
            node.attrib.get("text", "").strip()
            or node.attrib.get("content-desc", "").strip()
            or node.attrib.get("hint", "").strip()
        )
        bounds = parse_bounds(node.attrib.get("bounds", ""))
        if not label:
            findings.append(
                {
                    "code": "interactive_missing_label",
                    "severity": "P1",
                    "finding_type": "Product",
                    "node_index": index,
                    "message": "Interactive node has no text, content description, or hint.",
                }
            )
        if bounds:
            width_dp = (bounds[2] - bounds[0]) / pixels_per_dp
            height_dp = (bounds[3] - bounds[1]) / pixels_per_dp
            if width_dp < minimum_dp or height_dp < minimum_dp:
                findings.append(
                    {
                        "code": "interactive_target_too_small",
                        "severity": "P2",
                        "finding_type": "Needs human review",
                        "node_index": index,
                        "message": (
                            f"Interactive target is {width_dp:.1f}×{height_dp:.1f}dp; "
                            f"policy minimum is {minimum_dp:.0f}dp."
                        ),
                    }
                )
        if node.attrib.get("checkable") == "true" and "checked" not in node.attrib:
            findings.append(
                {
                    "code": "checkable_missing_state",
                    "severity": "P1",
                    "finding_type": "Product",
                    "node_index": index,
                    "message": "Checkable node has no checked state.",
                }
            )
    return findings


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("hierarchy", type=Path)
    parser.add_argument("--density", type=int, required=True)
    parser.add_argument("--is-tv", action="store_true")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    findings = audit_xml(args.hierarchy, density=args.density, is_tv=args.is_tv)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps({"schema_version": 1, "findings": findings}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    critical = sum(item["severity"] in {"P0", "P1"} for item in findings)
    print(f"{'FAIL' if critical else 'PASS'}: {len(findings)} accessibility finding(s)")
    return 1 if critical else 0


if __name__ == "__main__":
    raise SystemExit(main())
