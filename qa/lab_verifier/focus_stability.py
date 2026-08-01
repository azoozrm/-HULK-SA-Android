#!/usr/bin/env python3
"""Evaluate stable focus from an ordered sequence of raw UI hierarchies."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET
from typing import Any, Iterable


def focused_labels(xml_text: str) -> list[str]:
    root = ET.fromstring(xml_text)
    labels: list[str] = []
    for node in root.iter("node"):
        if node.attrib.get("focused") != "true":
            continue
        value = " ".join(
            item.strip()
            for item in (node.attrib.get("text", ""), node.attrib.get("content-desc", ""))
            if item.strip()
        )
        if value:
            labels.append(value)
    return labels


def evaluate_sequence(xml_documents: Iterable[str], expected: str, consecutive: int = 2) -> dict[str, Any]:
    if consecutive < 2:
        raise ValueError("stable focus requires at least two consecutive reads")
    observations: list[str] = []
    for index, xml_text in enumerate(xml_documents, start=1):
        try:
            labels = focused_labels(xml_text)
        except ET.ParseError as exc:
            return {
                "classification": "BLOCKED",
                "code": "FOCUS_XML_PARSE_FAILURE",
                "reason": f"read {index}: {exc}",
                "observations": observations,
                "stable": False,
            }
        if len(labels) != 1:
            return {
                "classification": "BLOCKED",
                "code": "FOCUS_CARDINALITY_INVALID",
                "reason": f"read {index}: expected exactly one focused node, observed {labels!r}",
                "observations": observations,
                "stable": False,
            }
        observations.append(labels[0])
    suffix = 0
    for label in reversed(observations):
        if expected in label:
            suffix += 1
        else:
            break
    return {
        "classification": "PASS" if suffix >= consecutive else "WAIT",
        "code": "FOCUS_STABLE" if suffix >= consecutive else "FOCUS_NOT_STABLE_YET",
        "expected": expected,
        "required_consecutive_reads": consecutive,
        "matching_suffix_reads": suffix,
        "observations": observations,
        "stable": suffix >= consecutive,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("xml", nargs="+", type=Path)
    parser.add_argument("--expected", required=True)
    parser.add_argument("--consecutive", type=int, default=2)
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    try:
        documents = [path.read_text(encoding="utf-8") for path in args.xml]
        report = evaluate_sequence(documents, args.expected, args.consecutive)
    except (OSError, UnicodeError, ValueError) as exc:
        report = {
            "classification": "BLOCKED",
            "code": "FOCUS_EVIDENCE_READ_FAILURE",
            "reason": str(exc),
            "stable": False,
        }
    rendered = json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2) + "\n"
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(rendered, encoding="utf-8")
    print(rendered, end="")
    if report.get("stable") is True:
        return 0
    return 20 if report.get("classification") == "BLOCKED" else 10


if __name__ == "__main__":
    raise SystemExit(main())
