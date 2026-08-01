#!/usr/bin/env python3
"""Create a readable BLOCKED result when the emulator action never ran the driver."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from analyze import analyze_run
from lab_config import PAGES


def parse_bool(value: str) -> bool:
    return value.strip().lower() in {"1", "true", "yes", "on"}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--device-id", required=True)
    parser.add_argument("--device-name", required=True)
    parser.add_argument("--family", required=True)
    parser.add_argument("--api", type=int, required=True)
    parser.add_argument("--target", required=True)
    parser.add_argument("--arch", required=True)
    parser.add_argument("--profile", required=True)
    parser.add_argument("--width", type=int, required=True)
    parser.add_argument("--height", type=int, required=True)
    parser.add_argument("--density", type=int, required=True)
    parser.add_argument("--orientations", required=True)
    parser.add_argument("--font-scales", required=True)
    parser.add_argument("--is-tv", required=True)
    parser.add_argument("--reason", required=True)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    manifest = {
        "schema_version": 2,
        "device": {
            "id": args.device_id,
            "name": args.device_name,
            "family": args.family,
            "api": args.api,
            "target": args.target,
            "arch": args.arch,
            "profile": args.profile,
            "hardware_profile": args.profile,
            "physical_width": args.width,
            "physical_height": args.height,
            "requested_width": args.width,
            "requested_height": args.height,
            "logical_width": args.width,
            "logical_height": args.height,
            "viewport": f"{args.width}x{args.height}",
            "requested_density": args.density,
            "result_dir": args.device_id,
            "artifact_name": f"compatibility-{args.device_id}",
            "orientations": args.orientations,
            "font_scales": args.font_scales,
            "is_tv": parse_bool(args.is_tv),
        },
        "pages": list(PAGES),
        "cases": [],
        "navigation": [],
        "focus": [],
        "harness_errors": [
            {
                "scope": "emulator-action",
                "message": args.reason,
            }
        ],
    }
    (args.out / "run-manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    analyze_run(args.out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
