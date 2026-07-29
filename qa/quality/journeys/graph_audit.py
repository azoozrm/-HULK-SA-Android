#!/usr/bin/env python3
"""Validate journey topology and render compact Mermaid evidence."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
from typing import Any


def validate_journeys(data: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    ids: set[str] = set()
    for journey in data.get("journeys", []):
        journey_id = journey.get("id")
        if not journey_id or journey_id in ids:
            errors.append(f"missing or duplicate journey id: {journey_id}")
            continue
        ids.add(journey_id)
        nodes = set(journey.get("nodes", []))
        if journey.get("entry") not in nodes:
            errors.append(f"{journey_id}: entry is not a node")
        if not journey.get("input_modes"):
            errors.append(f"{journey_id}: no input modes")
        for edge in journey.get("edges", []):
            if edge.get("from") not in nodes or edge.get("to") not in nodes:
                errors.append(f"{journey_id}: edge references an unknown node")
            if edge.get("action") not in {
                "click",
                "long-click",
                "back",
                "d-pad",
                "ime",
                "system",
            }:
                errors.append(f"{journey_id}: invalid action {edge.get('action')}")
        if journey.get("status") == "NOT_COVERED" and not journey.get("reason"):
            errors.append(f"{journey_id}: uncovered journey has no reason")
    return errors


def mermaid(journey: dict[str, Any]) -> str:
    def node_id(value: str) -> str:
        return "n_" + re.sub(r"[^a-zA-Z0-9_]", "_", value)

    lines = ["flowchart TD"]
    for node in journey["nodes"]:
        lines.append(f'  {node_id(node)}["{node}"]')
    for edge in journey["edges"]:
        lines.append(
            f"  {node_id(edge['from'])} -->|{edge['action']}| {node_id(edge['to'])}"
        )
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("journeys", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    data = json.loads(args.journeys.read_text(encoding="utf-8"))
    errors = validate_journeys(data)
    if errors:
        print("\n".join(errors))
        return 1
    args.output.mkdir(parents=True, exist_ok=True)
    for journey in data["journeys"]:
        (args.output / f"{journey['id']}.mmd").write_text(
            mermaid(journey),
            encoding="utf-8",
        )
    print(f"PASS: {len(data['journeys'])} journey graph(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

