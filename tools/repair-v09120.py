#!/usr/bin/env python3
"""Narrow build repair for the official HULK SA v0.9.1.20 source.

The recovered source contains two decompiler-generated Compose `$stable` fields.
The Compose compiler generates those fields itself, which creates duplicate JVM
signatures. Each generated field also has a directly associated `@JvmField`
annotation; both the field and its own annotation must be removed together.
"""

from pathlib import Path
import sys


def fail_with_context(message: str, lines: list[str], indexes: list[int]) -> None:
    context_parts: list[str] = []
    for index in indexes:
        start = max(0, index - 3)
        end = min(len(lines), index + 3)
        context_parts.append(
            "\n".join(
                f"{line_index + 1:04d}: {lines[line_index].rstrip()}"
                for line_index in range(start, end)
            )
        )
    details = "\n---\n".join(context_parts) or "No matching context."
    raise SystemExit(f"{message}\n{details}")


def main() -> int:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: repair-v09120.py <project-root>")

    project_root = Path(sys.argv[1]).resolve()
    target = project_root / "app/src/main/java/sa/hulksa/player/HulkViewModel.kt"
    if not target.is_file():
        raise SystemExit(f"Required source file was not found: {target}")

    original = target.read_text(encoding="utf-8")
    lines = original.splitlines(keepends=True)
    stable_indexes = [
        index for index, line in enumerate(lines)
        if "`$stable`" in line
    ]

    # The official v0.9.1.20 source is expected to contain exactly one duplicate
    # in HulkUiState and one in HulkViewModel. Refuse any broader edit.
    if len(stable_indexes) != 2:
        fail_with_context(
            "Expected exactly 2 explicit `$stable` declarations in "
            f"{target}, found {len(stable_indexes)}.",
            lines,
            stable_indexes,
        )

    annotation_indexes: list[int] = []
    for stable_index in stable_indexes:
        cursor = stable_index - 1
        while cursor >= 0 and not lines[cursor].strip():
            cursor -= 1
        if cursor < 0 or "@JvmField" not in lines[cursor]:
            fail_with_context(
                "A `$stable` declaration was not preceded by its expected "
                "@JvmField annotation; refusing an uncertain repair.",
                lines,
                [stable_index],
            )
        annotation_indexes.append(cursor)

    if len(set(annotation_indexes)) != 2:
        fail_with_context(
            "Expected two distinct @JvmField annotations for the two `$stable` fields.",
            lines,
            stable_indexes,
        )

    remove_indexes = set(stable_indexes + annotation_indexes)
    for index in sorted(remove_indexes):
        print(f"Removing generated Compose artifact at line {index + 1}: {lines[index].strip()}")

    repaired = "".join(
        line for index, line in enumerate(lines)
        if index not in remove_indexes
    )

    if repaired.count("{") != original.count("{") or repaired.count("}") != original.count("}"):
        raise SystemExit("Repair unexpectedly changed brace balance; refusing to write.")
    if "`$stable`" in repaired:
        raise SystemExit("A `$stable` declaration remains after the repair; refusing to write.")

    target.write_text(repaired, encoding="utf-8")
    print(f"Repaired {target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
