#!/usr/bin/env python3
"""Apply Compatibility Lab infrastructure and optional product gates."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def parse_bool(value: str) -> bool:
    return value.strip().lower() in {"1", "true", "yes", "on"}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("summary", type=Path)
    parser.add_argument("--enforce-findings", default="false")
    args = parser.parse_args()
    data = json.loads(args.summary.read_text(encoding="utf-8"))
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
