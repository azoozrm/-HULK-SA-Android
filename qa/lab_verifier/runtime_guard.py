#!/usr/bin/env python3
"""Classify blocking Android system dialogs before fixture assertions.

The guard is intentionally independent from the Compatibility Lab analyzer. It only
consumes raw UIAutomator XML and returns a retry decision for a proven transient
system dialog. Product/fixture dialogs are never retried.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET
from typing import Any

RETRYABLE_EXIT = 75
NON_RETRYABLE_FIXTURE_EXIT = 76
SYSTEM_DIALOG_PATTERNS = (
    "isn't responding",
    "is not responding",
    "keeps stopping",
    "has stopped",
)


def _bounds_center(raw: str) -> tuple[int, int] | None:
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", raw or "")
    if not match:
        return None
    left, top, right, bottom = (int(value) for value in match.groups())
    if right <= left or bottom <= top:
        return None
    return ((left + right) // 2, (top + bottom) // 2)


def classify_blocking_dialog(xml_text: str, fixture_label: str = "HULK Lab Fixture") -> dict[str, Any] | None:
    root = ET.fromstring(xml_text)
    title_node = None
    for node in root.iter("node"):
        if node.attrib.get("resource-id") == "android:id/alertTitle":
            title_node = node
            break
    if title_node is None:
        return None

    title = (title_node.attrib.get("text") or title_node.attrib.get("content-desc") or "").strip()
    lowered = title.lower()
    if not title or not any(pattern in lowered for pattern in SYSTEM_DIALOG_PATTERNS):
        return None

    dismiss_center = None
    dismiss_resource = None
    for resource_id in ("android:id/aerr_close", "android:id/aerr_wait", "android:id/button1"):
        for node in root.iter("node"):
            if node.attrib.get("resource-id") == resource_id:
                center = _bounds_center(node.attrib.get("bounds", ""))
                if center:
                    dismiss_center = list(center)
                    dismiss_resource = resource_id
                    break
        if dismiss_center:
            break

    fixture_owned = fixture_label.lower() in lowered
    if fixture_owned:
        return {
            "classification": "fixture",
            "code": "FIXTURE_APP_UNRESPONSIVE",
            "retry_allowed": False,
            "title": title,
            "dismiss_center": dismiss_center,
            "dismiss_resource": dismiss_resource,
        }
    return {
        "classification": "infrastructure",
        "code": "SYSTEM_SERVICE_UNAVAILABLE",
        "retry_allowed": True,
        "title": title,
        "dismiss_center": dismiss_center,
        "dismiss_resource": dismiss_resource,
    }


def inspect_file(path: Path, output: Path | None = None) -> int:
    try:
        result = classify_blocking_dialog(path.read_text(encoding="utf-8"))
    except (OSError, ET.ParseError) as exc:
        result = {
            "classification": "fixture",
            "code": "UI_EVIDENCE_PARSE_FAILURE",
            "retry_allowed": False,
            "message": str(exc),
            "dismiss_center": None,
        }
        code = NON_RETRYABLE_FIXTURE_EXIT
    else:
        if result is None:
            return 0
        code = RETRYABLE_EXIT if result["retry_allowed"] else NON_RETRYABLE_FIXTURE_EXIT

    if output:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(result, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    else:
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return code


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("xml", type=Path)
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    return inspect_file(args.xml, args.out)


if __name__ == "__main__":
    raise SystemExit(main())
