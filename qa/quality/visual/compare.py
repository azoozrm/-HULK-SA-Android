#!/usr/bin/env python3
"""Perceptual screenshot comparison with explicit dynamic masks."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from PIL import Image, ImageChops, ImageDraw, ImageStat


class BaselineError(ValueError):
    pass


def load_baseline_manifest(path: Path) -> dict[str, Any]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("approved") is not True:
        raise BaselineError("visual baseline is not explicitly approved")
    build_sha = str(data.get("build_sha", ""))
    if len(build_sha) != 40 or any(char not in "0123456789abcdef" for char in build_sha.lower()):
        raise BaselineError("visual baseline has no valid 40-character build SHA")
    if not data.get("reason"):
        raise BaselineError("visual baseline approval has no recorded reason")
    return data


def compare(
    baseline_path: Path,
    current_path: Path,
    output_diff: Path,
    *,
    masks: list[dict[str, int]] | None = None,
    pixel_delta_threshold: int = 18,
    changed_ratio_limit: float = 0.005,
) -> dict[str, Any]:
    baseline = Image.open(baseline_path).convert("RGB")
    current = Image.open(current_path).convert("RGB")
    if baseline.size != current.size:
        return {
            "status": "FAIL",
            "code": "png_geometry_mismatch",
            "baseline_size": list(baseline.size),
            "current_size": list(current.size),
            "changed_ratio": 1.0,
        }
    difference = ImageChops.difference(baseline, current)
    mask_image = Image.new("L", baseline.size, color=255)
    mask_draw = ImageDraw.Draw(mask_image)
    for item in masks or []:
        rectangle = (item["left"], item["top"], item["right"], item["bottom"])
        mask_draw.rectangle(rectangle, fill=0)
    difference = Image.composite(difference, Image.new("RGB", baseline.size), mask_image)
    grayscale = difference.convert("L")
    changed = grayscale.point(lambda value: 255 if value >= pixel_delta_threshold else 0)
    histogram = changed.histogram()
    changed_pixels = histogram[255]
    total_pixels = baseline.width * baseline.height
    changed_ratio = changed_pixels / total_pixels if total_pixels else 1.0
    mean_delta = sum(ImageStat.Stat(difference).mean) / 3.0
    overlay = current.copy()
    red = Image.new("RGB", baseline.size, (255, 40, 40))
    overlay.paste(red, mask=changed.point(lambda value: 150 if value else 0))
    output_diff.parent.mkdir(parents=True, exist_ok=True)
    overlay.save(output_diff)
    return {
        "status": "FAIL" if changed_ratio > changed_ratio_limit else "PASS",
        "code": "visual_regression" if changed_ratio > changed_ratio_limit else "visual_match",
        "baseline_size": list(baseline.size),
        "current_size": list(current.size),
        "pixel_delta_threshold": pixel_delta_threshold,
        "changed_ratio_limit": changed_ratio_limit,
        "changed_pixels": changed_pixels,
        "total_pixels": total_pixels,
        "changed_ratio": round(changed_ratio, 8),
        "mean_channel_delta": round(mean_delta, 4),
        "mask_count": len(masks or []),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("baseline", type=Path)
    parser.add_argument("current", type=Path)
    parser.add_argument("--baseline-manifest", type=Path, required=True)
    parser.add_argument("--masks", type=Path)
    parser.add_argument("--diff", type=Path, required=True)
    parser.add_argument("--result", type=Path, required=True)
    parser.add_argument("--pixel-delta-threshold", type=int, default=18)
    parser.add_argument("--changed-ratio-limit", type=float, default=0.005)
    args = parser.parse_args()
    manifest = load_baseline_manifest(args.baseline_manifest)
    masks = (
        json.loads(args.masks.read_text(encoding="utf-8")).get("masks", [])
        if args.masks
        else []
    )
    result = compare(
        args.baseline,
        args.current,
        args.diff,
        masks=masks,
        pixel_delta_threshold=args.pixel_delta_threshold,
        changed_ratio_limit=args.changed_ratio_limit,
    )
    result["baseline_build_sha"] = manifest["build_sha"]
    args.result.parent.mkdir(parents=True, exist_ok=True)
    args.result.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(f"{result['status']}: changed_ratio={result['changed_ratio']}")
    return 1 if result["status"] == "FAIL" else 0


if __name__ == "__main__":
    raise SystemExit(main())

