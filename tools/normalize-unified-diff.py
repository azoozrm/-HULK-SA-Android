#!/usr/bin/env python3
"""Normalize unified-diff hunk counts without changing patch content."""

from __future__ import annotations

import re
import sys
from pathlib import Path

HUNK = re.compile(r"^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@(.*)$")


def normalize(text: str) -> str:
    lines = text.splitlines(keepends=True)
    output: list[str] = []
    index = 0

    while index < len(lines):
        match = HUNK.match(lines[index].rstrip("\r\n"))
        if not match:
            output.append(lines[index])
            index += 1
            continue

        old_start = int(match.group(1))
        new_start = int(match.group(3))
        suffix = match.group(5)
        body: list[str] = []
        index += 1

        while index < len(lines):
            candidate = lines[index]
            if candidate.startswith("diff --git ") or HUNK.match(candidate.rstrip("\r\n")):
                break
            body.append(candidate)
            index += 1

        old_count = sum(1 for line in body if line.startswith((" ", "-")) and not line.startswith("---"))
        new_count = sum(1 for line in body if line.startswith((" ", "+")) and not line.startswith("+++"))
        newline = "\n" if lines[index - len(body) - 1].endswith("\n") else ""
        output.append(f"@@ -{old_start},{old_count} +{new_start},{new_count} @@{suffix}{newline}")
        output.extend(body)

    return "".join(output)


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: normalize-unified-diff.py PATCH", file=sys.stderr)
        return 2

    path = Path(sys.argv[1])
    original = path.read_text(encoding="utf-8")
    normalized = normalize(original)
    path.write_text(normalized, encoding="utf-8")
    print(f"normalized unified diff: {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
