#!/usr/bin/env python3
"""Schema-safe Compatibility Lab analyzer entrypoint."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
from types import ModuleType
from typing import Any


def normalize_device(device: dict[str, Any]) -> dict[str, Any]:
    normalized = dict(device)
    orientations = normalized.get("orientations", "landscape")
    if isinstance(orientations, (list, tuple)):
        normalized["orientations"] = ",".join(
            str(item).strip() for item in orientations if str(item).strip()
        )
    return normalized


def _load_core() -> ModuleType:
    path = Path(__file__).with_name("analyze.py")
    spec = importlib.util.spec_from_file_location("hulk_compatibility_analyze_core", path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"unable to load {path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def main() -> int:
    core = _load_core()
    original = core.analyze_rail_visual

    def analyze_rail_visual(root: Path, device: dict[str, Any], entries: list[dict[str, Any]]):
        return original(root, normalize_device(device), entries)

    core.analyze_rail_visual = analyze_rail_visual
    return int(core.main())


if __name__ == "__main__":
    raise SystemExit(main())
