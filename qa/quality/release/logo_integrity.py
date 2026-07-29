#!/usr/bin/env python3
"""Verify approved brand assets without exposing or rewriting their contents."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path


def load_manifest(path: Path) -> dict[str, str]:
    entries: dict[str, str] = {}
    for line_number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        try:
            digest, relative = line.split(maxsplit=1)
        except ValueError as exc:
            raise ValueError(f"{path}:{line_number}: malformed checksum line") from exc
        relative = relative.lstrip("*")
        if len(digest) != 64 or any(char not in "0123456789abcdefABCDEF" for char in digest):
            raise ValueError(f"{path}:{line_number}: invalid SHA-256")
        if relative in entries:
            raise ValueError(f"{path}:{line_number}: duplicate path {relative}")
        entries[relative] = digest.lower()
    if not entries:
        raise ValueError(f"{path}: empty checksum manifest")
    return entries


def verify(root: Path, manifest: Path) -> list[str]:
    failures: list[str] = []
    for relative, expected in load_manifest(manifest).items():
        target = root / relative
        if not target.is_file():
            failures.append(f"MISSING {relative}")
            continue
        actual = hashlib.sha256(target.read_bytes()).hexdigest()
        if actual != expected:
            failures.append(f"CHANGED {relative}: expected={expected} actual={actual}")
    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument(
        "--manifest",
        type=Path,
        default=Path("qa/quality/release/approved-logo-assets.sha256"),
    )
    args = parser.parse_args()
    failures = verify(args.root, args.manifest)
    if failures:
        print("\n".join(failures))
        return 1
    print(f"PASS: {len(load_manifest(args.manifest))} approved logo assets are unchanged")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
