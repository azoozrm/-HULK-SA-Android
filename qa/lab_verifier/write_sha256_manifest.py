#!/usr/bin/env python3
"""Create and verify a self-contained SHA-256 manifest for one artifact directory."""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import re

LINE_RE = re.compile(r"^(?P<digest>[0-9a-f]{64})  (?P<name>[^/\\]+)$")


def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def write_manifest(root: Path, output_name: str = "SHA256SUMS.txt") -> Path:
    root = root.resolve()
    if not root.is_dir():
        raise ValueError(f"artifact directory is missing: {root}")
    if "/" in output_name or "\\" in output_name or output_name in {"", ".", ".."}:
        raise ValueError(f"invalid manifest name: {output_name!r}")
    target = root / output_name
    files = sorted(
        path
        for path in root.iterdir()
        if path.is_file() and path.name != output_name
    )
    if not files:
        raise ValueError(f"artifact directory contains no files: {root}")
    target.write_text(
        "".join(f"{digest(path)}  {path.name}\n" for path in files),
        encoding="utf-8",
    )
    verify_manifest(root, target)
    return target


def verify_manifest(root: Path, manifest: Path) -> None:
    root = root.resolve()
    manifest = manifest.resolve()
    if manifest.parent != root or not manifest.is_file():
        raise ValueError("manifest must be a file directly inside the artifact directory")
    seen: set[str] = set()
    for number, line in enumerate(manifest.read_text(encoding="utf-8").splitlines(), start=1):
        match = LINE_RE.fullmatch(line)
        if not match:
            raise ValueError(f"malformed checksum line {number}: {line!r}")
        name = match.group("name")
        if name in seen:
            raise ValueError(f"duplicate checksum entry: {name}")
        seen.add(name)
        path = root / name
        if not path.is_file():
            raise ValueError(f"checksummed artifact is missing: {name}")
        observed = digest(path)
        if observed != match.group("digest"):
            raise ValueError(f"checksum mismatch for {name}")
    if not seen:
        raise ValueError("checksum manifest is empty")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    parser.add_argument("--output", default="SHA256SUMS.txt")
    args = parser.parse_args()
    target = write_manifest(args.root, args.output)
    print(f"PASS: verified {target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
