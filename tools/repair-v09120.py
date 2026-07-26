#!/usr/bin/env python3
"""Narrow build repair for the official HULK SA v0.9.1.20 source.

This script only removes explicit decompiler-generated Compose `$stable` fields.
The Compose compiler generates those fields itself, so keeping both declarations
causes a JVM signature clash during compileDebugKotlin.
"""

from pathlib import Path
import sys


def main() -> int:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: repair-v09120.py <project-root>")

    project_root = Path(sys.argv[1]).resolve()
    target = project_root / "app/src/main/java/sa/hulksa/player/HulkViewModel.kt"
    if not target.is_file():
        raise SystemExit(f"Required source file was not found: {target}")

    original = target.read_text(encoding="utf-8")
    lines = original.splitlines(keepends=True)
    matches = [
        (index, line)
        for index, line in enumerate(lines)
        if "`$stable`" in line
    ]

    # v0.9.1.20 is expected to contain exactly one duplicate declaration in
    # HulkUiState and one in HulkViewModel. Refuse a broader or uncertain edit.
    if len(matches) != 2:
        details = "\n".join(
            f"line {index + 1}: {line.rstrip()}" for index, line in matches
        ) or "none"
        raise SystemExit(
            "Expected exactly 2 explicit `$stable` declarations in "
            f"{target}, found {len(matches)}:\n{details}"
        )

    for index, line in matches:
        print(f"Removing generated stability declaration at line {index + 1}: {line.strip()}")

    repaired_lines = [
        line for index, line in enumerate(lines)
        if all(index != match_index for match_index, _ in matches)
    ]
    repaired = "".join(repaired_lines)

    if repaired.count("{") != original.count("{") or repaired.count("}") != original.count("}"):
        raise SystemExit("Repair unexpectedly changed brace balance; refusing to write.")

    target.write_text(repaired, encoding="utf-8")
    print(f"Repaired {target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
