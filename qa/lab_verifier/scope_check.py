#!/usr/bin/env python3
"""Fail-closed independent validator for Quality Lab pull-request scope."""
from __future__ import annotations

import argparse
import json
from pathlib import Path, PurePosixPath
import sys
from typing import Iterable

ALLOWED_PREFIXES = (
    "qa/lab_verifier/",
    "qa/lab_fixture_app/",
    "qa/compatibility/",
)
ALLOWED_FILES = {
    ".github/workflows/quality-lab-independent-qualification.yml",
    ".github/workflows/quality-lab-base-qualification.yml",
    ".github/workflows/compatibility-lab.yml",
}


def normalize_paths(lines: Iterable[str]) -> list[str]:
    paths: list[str] = []
    for raw in lines:
        value = raw.strip()
        if not value:
            continue
        parsed = PurePosixPath(value)
        if value.startswith("/") or ".." in parsed.parts or "\\" in value:
            raise ValueError(f"unsafe repository path: {value!r}")
        paths.append(value)
    return sorted(set(paths))


def validate_scope(paths: Iterable[str]) -> dict[str, object]:
    normalized = normalize_paths(paths)
    invalid = [
        path
        for path in normalized
        if path not in ALLOWED_FILES
        and not any(path.startswith(prefix) for prefix in ALLOWED_PREFIXES)
    ]
    return {
        "schema_version": 1,
        "changed_file_count": len(normalized),
        "valid": bool(normalized) and not invalid,
        "invalid_paths": invalid,
        "paths": normalized,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("changed_files", type=Path, help="newline-delimited git diff --name-only output")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()

    if not args.changed_files.is_file():
        print(f"BLOCKED: changed-file evidence is missing: {args.changed_files}", file=sys.stderr)
        return 2
    try:
        report = validate_scope(args.changed_files.read_text(encoding="utf-8").splitlines())
    except (OSError, UnicodeError, ValueError) as exc:
        print(f"BLOCKED: cannot validate changed-file evidence: {exc}", file=sys.stderr)
        return 2

    rendered = json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2) + "\n"
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(rendered, encoding="utf-8")
    print(rendered, end="")

    if not report["paths"]:
        print("BLOCKED: the Base/Head comparison produced no changed files", file=sys.stderr)
        return 2
    if report["invalid_paths"]:
        print("FAIL_LAB: non-lab paths are present", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
