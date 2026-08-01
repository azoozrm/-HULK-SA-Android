#!/usr/bin/env python3
"""Apply and independently verify emulator size/density contracts."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import struct
import subprocess
import time
from typing import Any

PHYSICAL_SIZE_RE = re.compile(r"Physical size:\s*(\d+)x(\d+)")
OVERRIDE_SIZE_RE = re.compile(r"Override size:\s*(\d+)x(\d+)")
PHYSICAL_DENSITY_RE = re.compile(r"Physical density:\s*(\d+)")
OVERRIDE_DENSITY_RE = re.compile(r"Override density:\s*(\d+)")
PNG_HEADER = b"\x89PNG\r\n\x1a\n"


def parse_wm_size(text: str) -> dict[str, list[int] | None]:
    physical = PHYSICAL_SIZE_RE.search(text)
    override = OVERRIDE_SIZE_RE.search(text)
    physical_size = [int(physical.group(1)), int(physical.group(2))] if physical else None
    override_size = [int(override.group(1)), int(override.group(2))] if override else None
    return {
        "physical_size": physical_size,
        "override_size": override_size,
        "effective_size": override_size or physical_size,
    }


def parse_wm_density(text: str) -> dict[str, int | None]:
    physical = PHYSICAL_DENSITY_RE.search(text)
    override = OVERRIDE_DENSITY_RE.search(text)
    physical_density = int(physical.group(1)) if physical else None
    override_density = int(override.group(1)) if override else None
    return {
        "physical_density": physical_density,
        "override_density": override_density,
        "effective_density": override_density or physical_density,
    }


def png_size(data: bytes) -> list[int] | None:
    if len(data) < 24 or not data.startswith(PNG_HEADER):
        return None
    return list(struct.unpack(">II", data[16:24]))


def evaluate_contract(
    wm_size: str,
    wm_density: str,
    screenshot: bytes,
    *,
    width: int,
    height: int,
    density: int,
) -> dict[str, Any]:
    report: dict[str, Any] = {
        "schema_version": 1,
        "expected_size": [width, height],
        "expected_density": density,
        **parse_wm_size(wm_size),
        **parse_wm_density(wm_density),
        "screenshot_size": png_size(screenshot),
    }
    errors: list[str] = []
    if report["physical_size"] != [width, height]:
        errors.append("physical_size")
    if report["effective_size"] != [width, height]:
        errors.append("effective_size")
    if report["effective_density"] != density:
        errors.append("effective_density")
    if report["screenshot_size"] != [width, height]:
        errors.append("screenshot_size")
    report["errors"] = errors
    report["valid"] = not errors
    return report


def adb(*args: str, binary: bool = False) -> str | bytes:
    completed = subprocess.run(
        ["adb", *args],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
        timeout=60,
    )
    if completed.returncode:
        detail = (completed.stderr or completed.stdout).decode("utf-8", errors="replace")
        raise RuntimeError(f"adb {' '.join(args)} failed: {detail[-500:]}")
    return completed.stdout if binary else completed.stdout.decode("utf-8", errors="replace")


def prepare_contract(width: int, height: int, density: int) -> dict[str, Any]:
    """Remove stale AVD overrides before applying the requested effective contract.

    A 4K skin can boot with a stale 1080p override. Setting a size equal to the
    physical display does not reliably clear that override, so reset must happen
    first. Physical-size validity remains independently enforced afterward.
    """
    adb("shell", "wm", "size", "reset")
    adb("shell", "wm", "density", "reset")
    time.sleep(0.5)

    size_after_reset = str(adb("shell", "wm", "size"))
    density_after_reset = str(adb("shell", "wm", "density"))
    parsed_size = parse_wm_size(size_after_reset)
    parsed_density = parse_wm_density(density_after_reset)

    if parsed_size["physical_size"] != [width, height]:
        # Preserve the mismatch as evidence, but set the effective viewport so the
        # report can distinguish physical-skin invalidity from logical-size setup.
        adb("shell", "wm", "size", f"{width}x{height}")
    if parsed_density["effective_density"] != density:
        adb("shell", "wm", "density", str(density))

    return {
        "size_after_reset": parsed_size,
        "density_after_reset": parsed_density,
    }


def apply_contract(width: int, height: int, density: int, timeout: float = 15.0) -> dict[str, Any]:
    reset_evidence = prepare_contract(width, height, density)
    deadline = time.monotonic() + timeout
    last: dict[str, Any] | None = None
    while time.monotonic() < deadline:
        size_text = str(adb("shell", "wm", "size"))
        density_text = str(adb("shell", "wm", "density"))
        screenshot = bytes(adb("exec-out", "screencap", "-p", binary=True))
        last = evaluate_contract(
            size_text,
            density_text,
            screenshot,
            width=width,
            height=height,
            density=density,
        )
        last["reset_evidence"] = reset_evidence
        if last["valid"]:
            return last
        time.sleep(0.5)
    assert last is not None
    return last


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--width", type=int, required=True)
    parser.add_argument("--height", type=int, required=True)
    parser.add_argument("--density", type=int, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    try:
        report = apply_contract(args.width, args.height, args.density)
    except Exception as exc:
        report = {
            "schema_version": 1,
            "expected_size": [args.width, args.height],
            "expected_density": args.density,
            "valid": False,
            "errors": ["adb_contract_application"],
            "reason": str(exc),
        }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(
        json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(report, ensure_ascii=False, sort_keys=True))
    return 0 if report.get("valid") else 2


if __name__ == "__main__":
    raise SystemExit(main())
