#!/usr/bin/env python3
"""Compare generated UI inventories while ignoring volatile source line metadata."""

from __future__ import annotations

import argparse
from difflib import unified_diff
import json
from pathlib import Path
from typing import Any


VOLATILE_KEYS = frozenset({"line"})


def normalize(value: Any) -> Any:
    """Return a deterministic inventory value without non-semantic metadata."""
    if isinstance(value, dict):
        return {
            key: normalize(item)
            for key, item in sorted(value.items())
            if key not in VOLATILE_KEYS
        }
    if isinstance(value, list):
        return [normalize(item) for item in value]
    return value


def load_normalized(path: Path) -> Any:
    return normalize(json.loads(path.read_text(encoding="utf-8")))


def compare(expected: Path, actual: Path) -> tuple[bool, str]:
    expected_value = load_normalized(expected)
    actual_value = load_normalized(actual)
    if expected_value == actual_value:
        return True, ""

    expected_text = json.dumps(
        expected_value,
        ensure_ascii=False,
        indent=2,
        sort_keys=True,
    ).splitlines(keepends=True)
    actual_text = json.dumps(
        actual_value,
        ensure_ascii=False,
        indent=2,
        sort_keys=True,
    ).splitlines(keepends=True)
    diff = "".join(
        unified_diff(
            expected_text,
            actual_text,
            fromfile=expected.as_posix(),
            tofile=actual.as_posix(),
        )
    )
    return False, diff


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("expected", type=Path)
    parser.add_argument("actual", type=Path)
    args = parser.parse_args()

    matches, diff = compare(args.expected, args.actual)
    if matches:
        print(
            "PASS: inventory semantics match; volatile source line metadata was ignored"
        )
        return 0

    print("FAIL: generated inventory changed semantically")
    print(diff, end="")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
