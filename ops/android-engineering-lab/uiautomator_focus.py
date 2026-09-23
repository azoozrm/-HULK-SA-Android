#!/usr/bin/env python3
"""Extract focused accessibility nodes from a UiAutomator window dump.

Read-only helper used by the lab focus probe and its deterministic tests.

Privacy contract: this helper NEVER emits the `text` attribute of any node. Editable fields
(e.g. credential inputs) expose their value through `text`, so only non-sensitive focus
identity is returned: class, content description, resource id, package and bounds. Focus
labelling uses content description only and never falls back to text.
"""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from typing import Any, Dict, List


def focused_nodes(xml_path: str) -> List[Dict[str, Any]]:
    tree = ET.parse(xml_path)
    nodes: List[Dict[str, Any]] = []
    for node in tree.iter("node"):
        if node.get("focused") != "true":
            continue
        nodes.append(
            {
                "class": node.get("class", ""),
                "content_desc": node.get("content-desc", ""),
                "resource_id": node.get("resource-id", ""),
                "package": node.get("package", ""),
                "bounds": node.get("bounds", ""),
            }
        )
    return nodes


def primary_label(nodes: List[Dict[str, Any]]) -> str:
    """Focus label derived from content description only (never editable text)."""
    for node in nodes:
        label = (node.get("content_desc") or "").strip()
        if label:
            return label
    return ""


def main(argv: List[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("xml_path")
    parser.add_argument("--primary-label", action="store_true")
    args = parser.parse_args(argv)
    nodes = focused_nodes(args.xml_path)
    if args.primary_label:
        print(primary_label(nodes))
    else:
        json.dump(nodes, sys.stdout, ensure_ascii=False, indent=2)
        sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
