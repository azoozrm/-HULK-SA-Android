#!/usr/bin/env python3
"""Isolation/evidence layer on top of the qualified Compatibility Lab runner."""

from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import shutil
import sys
from types import ModuleType
from typing import Any
import xml.etree.ElementTree as ET


def load_qualified() -> ModuleType:
    path = Path(__file__).with_name("run-lab-qualified.py")
    spec = importlib.util.spec_from_file_location("hulk_compatibility_run_lab_qualified", path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"unable to load {path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def parse_download_state_xml(raw: str) -> list[dict[str, Any]]:
    """Extract transport state from the debug app's private SharedPreferences XML."""
    if not raw.strip():
        return []
    root = ET.fromstring(raw)
    node = root.find(".//string[@name='downloads']")
    if node is None or not node.text:
        return []
    payload = json.loads(node.text)
    if not isinstance(payload, list):
        return []
    fields = (
        "historyKey",
        "status",
        "bytesDownloaded",
        "totalBytes",
        "retryCount",
        "errorMessage",
    )
    return [
        {field: item.get(field) for field in fields}
        for item in payload
        if isinstance(item, dict)
    ]


def stable_tv_evidence(case_dir: Path) -> tuple[Path, Path] | None:
    """Return the paired stable XML/PNG only when the qualified wait succeeded."""
    status_path = case_dir / "tv-download-layout-wait.json"
    xml_path = case_dir / "tv-download-layout-wait.xml"
    png_path = case_dir / "tv-download-layout-wait.png"
    try:
        status = json.loads(status_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    if status.get("found") is not True or not xml_path.is_file() or not png_path.is_file():
        return None
    return xml_path, png_path


def install_isolation_layer(qualified: ModuleType) -> None:
    original_install = qualified.install_qualified_behavior

    def install(core: ModuleType) -> None:
        original_install(core)
        qualified_set_variant = core.DeviceLab.set_variant
        qualified_start_page = core.DeviceLab.start_page
        qualified_capture_case = core.DeviceLab.capture_case

        def set_variant(self: Any, orientation: str, font_scale: float) -> None:
            qualified_set_variant(self, orientation, font_scale)
            self.adb.shell(["am", "force-stop", core.PACKAGE])
            clear = self.adb.shell(["pm", "clear", core.PACKAGE], timeout=90)
            clear_text = (clear.text + clear.error_text).strip()
            reset_root = self.out / "variant-resets"
            reset_root.mkdir(parents=True, exist_ok=True)
            reset_path = reset_root / f"{orientation}-font-{int(round(font_scale * 100)):03d}.json"
            core.safe_write(
                reset_path,
                json.dumps(
                    {
                        "orientation": orientation,
                        "font_scale": font_scale,
                        "package": core.PACKAGE,
                        "pm_clear_returncode": clear.returncode,
                        "pm_clear_output": clear_text,
                        "fixture_reinitialized": clear.returncode == 0 and "Success" in clear_text,
                    },
                    indent=2,
                )
                + "\n",
            )
            if clear.returncode != 0 or "Success" not in clear_text:
                raise core.LabError(
                    f"failed to reinitialize fixture for {orientation}/{font_scale}: "
                    f"{clear_text[-1000:]}"
                )

        def capture_download_state(self: Any, case_dir: Path) -> None:
            result = self.adb.shell(
                ["run-as", core.PACKAGE, "cat", "shared_prefs/hulk_downloads.xml"],
                timeout=30,
            )
            raw = result.text
            core.safe_write(case_dir / "download-state.xml", raw + result.error_text)
            try:
                items = parse_download_state_xml(raw)
                parse_error = None
            except Exception as exc:
                items = []
                parse_error = str(exc)[-1000:]
            core.safe_write(
                case_dir / "download-state.json",
                json.dumps(
                    {
                        "returncode": result.returncode,
                        "items": items,
                        "positive_item_count": sum(
                            1 for item in items if int(item.get("bytesDownloaded") or 0) > 0
                        ),
                        "parse_error": parse_error,
                    },
                    ensure_ascii=False,
                    indent=2,
                )
                + "\n",
            )

        def start_page(self: Any, page: str, case_dir: Path):
            result = qualified_start_page(self, page, case_dir)
            if page == "downloads":
                self.capture_download_state(case_dir)
                if self.args.is_tv:
                    status_path = case_dir / "tv-download-layout-wait.json"
                    try:
                        status = json.loads(status_path.read_text(encoding="utf-8"))
                    except (OSError, json.JSONDecodeError):
                        status = {}
                    if status.get("found") is True:
                        core.capture_png(self.adb, case_dir / "tv-download-layout-wait.png")
            return result

        def capture_case(self: Any, page: str, orientation: str, font_scale: float) -> None:
            qualified_capture_case(self, page, orientation, font_scale)
            if page != "downloads" or not self.args.is_tv:
                return
            scale_id = f"font-{int(round(font_scale * 100)):03d}"
            case_dir = self.raw / orientation / scale_id / page
            evidence = stable_tv_evidence(case_dir)
            selection = {
                "selected": False,
                "reason": "stable qualified TV evidence was unavailable",
            }
            if evidence is not None:
                xml_path, png_path = evidence
                shutil.copy2(xml_path, case_dir / "ui.xml")
                shutil.copy2(png_path, case_dir / "screenshot.png")
                selection = {
                    "selected": True,
                    "xml": xml_path.name,
                    "screenshot": png_path.name,
                    "reason": "paired evidence satisfied the unchanged two-card gate",
                }
            core.safe_write(
                case_dir / "selected-tv-download-evidence.json",
                json.dumps(selection, indent=2) + "\n",
            )

        core.DeviceLab.set_variant = set_variant
        core.DeviceLab.capture_download_state = capture_download_state
        core.DeviceLab.start_page = start_page
        core.DeviceLab.capture_case = capture_case

    qualified.install_qualified_behavior = install


def main() -> int:
    qualified = load_qualified()
    install_isolation_layer(qualified)
    return int(qualified.main())


if __name__ == "__main__":
    raise SystemExit(main())
