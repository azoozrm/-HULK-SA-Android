#!/usr/bin/env python3
"""Single source of truth for the HULK SA compatibility matrix."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any


PAGES: tuple[dict[str, str], ...] = (
    {"id": "home", "label": "الرئيسية"},
    {"id": "live", "label": "البث المباشر"},
    {"id": "movies", "label": "الافلام"},
    {"id": "series", "label": "المسلسلات"},
    {"id": "favorites", "label": "قائمتي"},
    {"id": "search", "label": "البحث"},
    {"id": "downloads", "label": "التنزيلات"},
    {"id": "settings", "label": "الاعدادات"},
)


# Hardware names describe the display geometry being simulated. Touch profiles use
# a stable Pixel hardware definition and override size/density through wm so the
# resulting dp canvas matches the named device. TV profiles use a real Android TV
# x86_64 image; API 36 is the first currently published TV image with x86_64.
_DEVICE_DEFINITIONS: tuple[dict[str, Any], ...] = (
    {
        "id": "pixel-4a-api29",
        "name": "Pixel 4a",
        "family": "phone",
        "api": 29,
        "target": "google_apis",
        "arch": "x86_64",
        "profile": "pixel_6",
        "boot_skin": "1080x2340",
        "width": 1080,
        "height": 2340,
        "density": 440,
        "orientations": "portrait,landscape",
        "font_scales": "1.0,1.30",
        "is_tv": False,
    },
    {
        "id": "pixel-6-api31",
        "name": "Pixel 6",
        "family": "phone",
        "api": 31,
        "target": "google_apis",
        "arch": "x86_64",
        "profile": "pixel_6",
        "boot_skin": "1080x2400",
        "width": 1080,
        "height": 2400,
        "density": 420,
        "orientations": "portrait,landscape",
        "font_scales": "1.0",
        "is_tv": False,
    },
    {
        "id": "pixel-8-pro-api35",
        "name": "Pixel 8 Pro",
        "family": "phone",
        "api": 35,
        "target": "google_apis",
        "arch": "x86_64",
        "profile": "pixel_6",
        "boot_skin": "1344x2992",
        "width": 1344,
        "height": 2992,
        "density": 480,
        "orientations": "portrait,landscape",
        "font_scales": "1.0",
        "is_tv": False,
    },
    {
        "id": "galaxy-s24-ultra-api35",
        "name": "Galaxy S24 Ultra",
        "family": "phone",
        "api": 35,
        "target": "google_apis",
        "arch": "x86_64",
        "profile": "pixel_6",
        "boot_skin": "1440x3120",
        "width": 1440,
        "height": 3120,
        "density": 560,
        "orientations": "portrait,landscape",
        "font_scales": "1.0",
        "is_tv": False,
    },
    {
        "id": "pixel-tablet-api35",
        "name": "Pixel Tablet",
        "family": "tablet",
        "api": 35,
        "target": "google_apis",
        "arch": "x86_64",
        "profile": "pixel_6",
        "boot_skin": "1600x2560",
        "width": 1600,
        "height": 2560,
        "density": 320,
        "orientations": "portrait,landscape",
        "font_scales": "1.0,1.30",
        "is_tv": False,
    },
    {
        "id": "nexus-9-api28",
        "name": "Nexus 9",
        "family": "tablet",
        "api": 28,
        "target": "google_apis",
        "arch": "x86_64",
        "profile": "pixel_6",
        "boot_skin": "1536x2048",
        "width": 1536,
        "height": 2048,
        "density": 320,
        "orientations": "portrait,landscape",
        "font_scales": "1.0",
        "is_tv": False,
    },
    {
        "id": "android-tv-720p-api36",
        "name": "Android TV 720p",
        "family": "tv",
        "api": 36,
        "target": "android-tv",
        "arch": "x86_64",
        "profile": "tv_720p",
        "boot_skin": "1280x720",
        "width": 1280,
        "height": 720,
        "density": 213,
        "orientations": "landscape",
        "font_scales": "1.0",
        "is_tv": True,
    },
    {
        "id": "android-tv-1080p-api36",
        "name": "Android TV 1080p",
        "family": "tv",
        "api": 36,
        "target": "android-tv",
        "arch": "x86_64",
        "profile": "tv_1080p",
        "boot_skin": "1920x1080",
        "width": 1920,
        "height": 1080,
        "density": 320,
        "orientations": "landscape",
        "font_scales": "1.0",
        "is_tv": True,
    },
    {
        "id": "android-tv-4k-api36",
        "name": "Android TV 4K",
        "family": "tv",
        "api": 36,
        "target": "android-tv",
        "arch": "x86_64",
        "profile": "tv_4k",
        "boot_skin": "3840x2160",
        "width": 3840,
        "height": 2160,
        "density": 640,
        "orientations": "landscape",
        "font_scales": "1.0",
        "is_tv": True,
    },
)


def _with_display_contract(device: dict[str, Any]) -> dict[str, Any]:
    physical_width, physical_height = map(int, str(device["boot_skin"]).split("x"))
    device_id = str(device["id"])
    return {
        **device,
        "physical_width": physical_width,
        "physical_height": physical_height,
        "logical_width": int(device["width"]),
        "logical_height": int(device["height"]),
        "viewport": f"{device['width']}x{device['height']}",
        "result_dir": device_id,
        "artifact_name": f"compatibility-{device_id}",
    }


DEVICES: tuple[dict[str, Any], ...] = tuple(
    _with_display_contract(device) for device in _DEVICE_DEFINITIONS
)


TV_DISPLAY_CONTRACTS: dict[str, dict[str, Any]] = {
    "android-tv-720p-api36": {
        "name": "Android TV 720p",
        "profile": "tv_720p",
        "boot_skin": "1280x720",
        "width": 1280,
        "height": 720,
        "density": 213,
    },
    "android-tv-1080p-api36": {
        "name": "Android TV 1080p",
        "profile": "tv_1080p",
        "boot_skin": "1920x1080",
        "width": 1920,
        "height": 1080,
        "density": 320,
    },
    "android-tv-4k-api36": {
        "name": "Android TV 4K",
        "profile": "tv_4k",
        "boot_skin": "3840x2160",
        "width": 3840,
        "height": 2160,
        "density": 640,
    },
}


def validate() -> None:
    required = {
        "id",
        "name",
        "family",
        "api",
        "target",
        "arch",
        "profile",
        "boot_skin",
        "width",
        "height",
        "density",
        "orientations",
        "font_scales",
        "is_tv",
        "physical_width",
        "physical_height",
        "logical_width",
        "logical_height",
        "viewport",
        "result_dir",
        "artifact_name",
    }
    ids: set[str] = set()
    for device in DEVICES:
        missing = required - device.keys()
        if missing:
            raise ValueError(f"{device.get('id', '<unknown>')}: missing {sorted(missing)}")
        if device["id"] in ids:
            raise ValueError(f"duplicate device id: {device['id']}")
        ids.add(device["id"])
        if device["family"] not in {"phone", "tablet", "tv"}:
            raise ValueError(f"{device['id']}: invalid family")
        if int(device["width"]) <= 0 or int(device["height"]) <= 0 or int(device["density"]) <= 0:
            raise ValueError(f"{device['id']}: display metrics must be positive")
        if not re.fullmatch(r"[1-9]\d*x[1-9]\d*", str(device["boot_skin"])):
            raise ValueError(f"{device['id']}: invalid boot skin")
        boot_width, boot_height = map(int, str(device["boot_skin"]).split("x"))
        if (boot_width, boot_height) != (
            int(device["physical_width"]),
            int(device["physical_height"]),
        ):
            raise ValueError(f"{device['id']}: boot skin and physical geometry disagree")
        if (int(device["width"]), int(device["height"])) != (
            int(device["logical_width"]),
            int(device["logical_height"]),
        ):
            raise ValueError(f"{device['id']}: tested and logical geometry disagree")
        if device["viewport"] != f"{device['logical_width']}x{device['logical_height']}":
            raise ValueError(f"{device['id']}: viewport and logical geometry disagree")
        if device["result_dir"] != device["id"]:
            raise ValueError(f"{device['id']}: result directory must equal the device id")
        if device["artifact_name"] != f"compatibility-{device['id']}":
            raise ValueError(f"{device['id']}: artifact name must derive from the device id")
        if boot_width < int(device["width"]) or boot_height < int(device["height"]):
            raise ValueError(
                f"{device['id']}: boot framebuffer cannot be smaller than tested geometry"
            )
        orientations = str(device["orientations"]).split(",")
        if not orientations or any(item not in {"portrait", "landscape"} for item in orientations):
            raise ValueError(f"{device['id']}: invalid orientations")
        if bool(device["is_tv"]) != (device["family"] == "tv"):
            raise ValueError(f"{device['id']}: TV flag and family disagree")
        if device["is_tv"] and (
            device["api"] != 36 or device["target"] != "android-tv" or device["arch"] != "x86_64"
        ):
            raise ValueError(f"{device['id']}: TV profiles must use the published API 36 x86_64 image")
        if device["is_tv"]:
            expected = TV_DISPLAY_CONTRACTS.get(str(device["id"]))
            actual = {key: device[key] for key in ("name", "profile", "boot_skin", "width", "height", "density")}
            if expected != actual:
                raise ValueError(
                    f"{device['id']}: TV display contract mismatch: expected {expected}, got {actual}"
                )
            if (boot_width, boot_height) != (int(device["width"]), int(device["height"])):
                raise ValueError(f"{device['id']}: TV physical and logical resolutions must match")

    page_ids = [page["id"] for page in PAGES]
    if len(page_ids) != len(set(page_ids)):
        raise ValueError("duplicate page id")
    expected_families = {"phone": 4, "tablet": 2, "tv": 3}
    actual_families = {
        family: sum(1 for device in DEVICES if device["family"] == family)
        for family in expected_families
    }
    if actual_families != expected_families:
        raise ValueError(f"unexpected device coverage: {actual_families}")


def matrix_json() -> str:
    validate()
    return json.dumps({"include": list(DEVICES)}, separators=(",", ":"), ensure_ascii=False)


def markdown() -> str:
    validate()
    lines = [
        "| Device | Class | API / image | Geometry | Density | Orientations | Font scales |",
        "|---|---|---|---:|---:|---|---|",
    ]
    for device in DEVICES:
        lines.append(
            f"| {device['name']} | {device['family']} | API {device['api']} / "
            f"{device['target']} {device['arch']} | {device['width']}×{device['height']} | "
            f"{device['density']} dpi | {device['orientations']} | {device['font_scales']} |"
        )
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--matrix-json", action="store_true")
    parser.add_argument("--github-output", type=Path)
    parser.add_argument("--markdown", action="store_true")
    parser.add_argument("--validate", action="store_true")
    args = parser.parse_args()

    validate()
    if args.github_output:
        with args.github_output.open("a", encoding="utf-8") as handle:
            handle.write(f"matrix={matrix_json()}\n")
            handle.write(f"device_count={len(DEVICES)}\n")
        return
    if args.matrix_json:
        print(matrix_json())
    elif args.markdown:
        print(markdown(), end="")
    else:
        print(f"PASS: {len(DEVICES)} devices, {len(PAGES)} pages")


if __name__ == "__main__":
    main()
