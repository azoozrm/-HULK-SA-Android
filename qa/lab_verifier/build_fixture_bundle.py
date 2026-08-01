#!/usr/bin/env python3
from __future__ import annotations
import argparse
import json
from pathlib import Path
import re
import struct
from verifier import artifact_checksum


def png_size(data: bytes) -> tuple[int, int]:
    if not data.startswith(b"\x89PNG\r\n\x1a\n") or len(data) < 24:
        raise ValueError("invalid PNG")
    return struct.unpack(">II", data[16:24])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--source-head-sha", required=True)
    parser.add_argument("--base-sha", required=True)
    parser.add_argument("--tested-commit-sha", required=True)
    parser.add_argument("--merge-sha", required=True)
    parser.add_argument("--apk-sha256", required=True)
    parser.add_argument("--width", type=int, required=True)
    parser.add_argument("--height", type=int, required=True)
    parser.add_argument("--density", type=int, required=True)
    args = parser.parse_args()
    token = (args.raw / "launch-token.txt").read_text().strip()
    markers = (args.raw / "markers.log").read_text()
    first = next(line for line in markers.splitlines() if line.strip())
    process_id = first.split()[0]
    width, height = png_size((args.raw / "screenshot.png").read_bytes())
    files = {
        "ui.xml": (args.raw / "post-action.xml").read_text(),
        "focus-events.log": (args.raw / "focus-events.log").read_text(),
        "logcat.txt": (args.raw / "logcat.txt").read_text(),
        "window.txt": (args.raw / "window.txt").read_text(),
        "activity.txt": (args.raw / "activity.txt").read_text(),
        "markers.log": markers,
        "origin.log": (args.raw / "origin.log").read_text(),
        "repository.log": (args.raw / "repository.log").read_text(),
        "screenshot.json": json.dumps({"width": width, "height": height, "density": args.density}, sort_keys=True),
        "PROVENANCE.json": json.dumps({
            "source_head_sha": args.source_head_sha,
            "base_sha": args.base_sha,
            "tested_commit_sha": args.tested_commit_sha,
            "merge_sha": args.merge_sha,
            "lab_apk_sha256": args.apk_sha256,
            "launch_token": token,
            "process_id": process_id,
        }, sort_keys=True),
    }
    bundle = {
        "schema_version": 1,
        "case_id": "minimal-fixture-runtime",
        "expected": {
            "outcome": "PASS",
            "source_head_sha": args.source_head_sha,
            "base_sha": args.base_sha,
            "tested_commit_sha": args.tested_commit_sha,
            "merge_sha": args.merge_sha,
            "lab_apk_sha256": args.apk_sha256,
            "launch_token": token,
            "process_id": process_id,
            "page": "fixture",
            "physical_size": [args.width, args.height],
            "density": args.density,
            "target": "row-1-primary",
            "action": "primary",
        },
        "files": files,
    }
    bundle["artifact_checksum"] = artifact_checksum(files)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(bundle, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
