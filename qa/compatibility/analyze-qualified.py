#!/usr/bin/env python3
"""Schema-safe Compatibility Lab analyzer entrypoint."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
from types import ModuleType
from typing import Any


def _csv_value(value: Any, default: str) -> str:
    if isinstance(value, (list, tuple)):
        return ",".join(str(item).strip() for item in value if str(item).strip())
    text = str(value).strip() if value is not None else ""
    return text or default


def normalize_device(device: dict[str, Any]) -> dict[str, Any]:
    normalized = dict(device)
    normalized["orientations"] = _csv_value(
        normalized.get("orientations"),
        "landscape",
    )
    normalized["font_scales"] = _csv_value(
        normalized.get("font_scales"),
        "1.0",
    )
    return normalized


def normalize_manifest(payload: Any) -> Any:
    if not isinstance(payload, dict):
        return payload
    normalized = dict(payload)
    device = normalized.get("device")
    if isinstance(device, dict):
        normalized["device"] = normalize_device(device)
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
    original_load_json = core.load_json
    original_rail = core.analyze_rail_visual

    def load_json(path: Path):
        payload = original_load_json(path)
        if Path(path).name == "run-manifest.json":
            return normalize_manifest(payload)
        return payload

    def analyze_rail_visual(root: Path, device: dict[str, Any], entries: list[dict[str, Any]]):
        return original_rail(root, normalize_device(device), entries)

    core.load_json = load_json
    core.analyze_rail_visual = analyze_rail_visual
    return int(core.main())


if __name__ == "__main__":
    raise SystemExit(main())
