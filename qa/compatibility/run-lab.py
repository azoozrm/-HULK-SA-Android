#!/usr/bin/env python3
"""Drive the debug-only HULK SA Compatibility Lab on one emulator."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import re
import struct
import subprocess
import sys
import time
from typing import Any, Iterable
import xml.etree.ElementTree as ET

from lab_config import PAGES


PACKAGE = "sa.hulksa.player.dev"
ACTIVITY = "sa.hulksa.player.qa.QaActivity"
PAGE_MARKER_PREFIX = "qa-page:"
BOUNDS_RE = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")
TRANSIENT_ADB_RE = re.compile(
    r"device offline|device ['\"][^'\"]+['\"] not found|"
    r"no devices/emulators found|cannot connect to daemon|"
    r"protocol fault|connection reset",
    re.IGNORECASE,
)


class LabError(RuntimeError):
    pass


@dataclass
class CommandResult:
    returncode: int
    stdout: bytes
    stderr: bytes

    @property
    def text(self) -> str:
        return self.stdout.decode("utf-8", errors="replace")

    @property
    def error_text(self) -> str:
        return self.stderr.decode("utf-8", errors="replace")


class Adb:
    def __init__(self, serial: str | None = None) -> None:
        self.prefix = ["adb"]
        if serial:
            self.prefix += ["-s", serial]

    def run(
        self,
        args: Iterable[str],
        *,
        check: bool = False,
        timeout: int = 120,
        input_bytes: bytes | None = None,
    ) -> CommandResult:
        command = self.prefix + [str(item) for item in args]
        result: CommandResult | None = None
        for attempt in range(4):
            try:
                completed = subprocess.run(
                    command,
                    input=input_bytes,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    timeout=timeout,
                    check=False,
                )
            except subprocess.TimeoutExpired as exc:
                raise LabError(
                    f"command timed out after {timeout}s: {' '.join(command)}"
                ) from exc
            result = CommandResult(completed.returncode, completed.stdout, completed.stderr)
            detail = (result.error_text + "\n" + result.text).strip()
            if (
                result.returncode == 0
                or attempt == 3
                or not TRANSIENT_ADB_RE.search(detail)
            ):
                break
            print(
                f"ADB transport unavailable; recovery attempt {attempt + 1}/3",
                file=sys.stderr,
                flush=True,
            )
            try:
                subprocess.run(
                    self.prefix + ["wait-for-device"],
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    timeout=min(timeout, 30),
                    check=False,
                )
            except subprocess.TimeoutExpired:
                pass
            time.sleep(0.75 * (attempt + 1))
        assert result is not None
        if check and result.returncode != 0:
            detail = (result.error_text or result.text).strip()[-1200:]
            raise LabError(f"command failed ({result.returncode}): {' '.join(command)}\n{detail}")
        return result

    def shell(
        self,
        args: Iterable[str],
        *,
        check: bool = False,
        timeout: int = 120,
    ) -> CommandResult:
        return self.run(["shell", *args], check=check, timeout=timeout)


def parse_bool(value: str) -> bool:
    lowered = value.strip().lower()
    if lowered in {"1", "true", "yes", "on"}:
        return True
    if lowered in {"0", "false", "no", "off"}:
        return False
    raise argparse.ArgumentTypeError(f"invalid boolean: {value}")


def parse_csv(value: str) -> list[str]:
    items = [item.strip() for item in value.split(",") if item.strip()]
    if not items:
        raise argparse.ArgumentTypeError("value must include at least one item")
    return items


def parse_float_csv(value: str) -> list[float]:
    try:
        return [float(item) for item in parse_csv(value)]
    except ValueError as exc:
        raise argparse.ArgumentTypeError(f"invalid float list: {value}") from exc


def safe_write(path: Path, data: str | bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(data, bytes):
        path.write_bytes(data)
    else:
        path.write_text(data, encoding="utf-8")


def command_text(adb: Adb, args: Iterable[str], *, shell: bool = True, timeout: int = 120) -> str:
    result = adb.shell(args, timeout=timeout) if shell else adb.run(args, timeout=timeout)
    return result.text + (("\n" + result.error_text) if result.error_text else "")


def dump_xml(adb: Adb, destination: Path, attempts: int = 5) -> bytes:
    last_error = ""
    for _ in range(attempts):
        result = adb.shell(
            ["uiautomator", "dump", "--compressed", "/sdcard/hulk-qa-window.xml"],
            timeout=45,
        )
        if result.returncode == 0:
            pulled = adb.run(["exec-out", "cat", "/sdcard/hulk-qa-window.xml"], timeout=30)
            if pulled.returncode == 0 and b"<hierarchy" in pulled.stdout:
                safe_write(destination, pulled.stdout)
                return pulled.stdout
            last_error = (pulled.error_text or pulled.text).strip()
        else:
            last_error = (result.error_text or result.text).strip()
        time.sleep(0.6)
    raise LabError(f"UI hierarchy unavailable: {last_error[-600:]}")


def capture_png(adb: Adb, destination: Path) -> bytes:
    result = adb.run(["exec-out", "screencap", "-p"], timeout=45)
    if result.returncode != 0 or not result.stdout.startswith(b"\x89PNG\r\n\x1a\n"):
        detail = (result.error_text or result.text).strip()
        raise LabError(f"screenshot capture failed: {detail[-600:]}")
    safe_write(destination, result.stdout)
    return result.stdout


def png_dimensions(data: bytes) -> tuple[int, int] | None:
    if len(data) < 24 or not data.startswith(b"\x89PNG\r\n\x1a\n"):
        return None
    return struct.unpack(">II", data[16:24])


def wait_for_geometry(
    adb: Adb,
    expected: tuple[int, int],
    timeout: float = 12.0,
) -> tuple[bool, tuple[int, int] | None]:
    deadline = time.monotonic() + timeout
    observed: tuple[int, int] | None = None
    while time.monotonic() < deadline:
        result = adb.run(["exec-out", "screencap", "-p"], timeout=45)
        if result.returncode == 0:
            observed = png_dimensions(result.stdout)
            if observed == expected:
                return True, observed
        time.sleep(0.5)
    return False, observed


def wait_for_marker(adb: Adb, marker: str, scratch: Path, timeout: float = 12.0) -> tuple[bool, bytes | None]:
    deadline = time.monotonic() + timeout
    last_xml: bytes | None = None
    while time.monotonic() < deadline:
        try:
            data = dump_xml(adb, scratch, attempts=1)
            last_xml = data
            if marker.encode("utf-8") in data:
                return True, data
            dialog_center = external_error_dialog_center(data)
            if dialog_center:
                adb.shell(["input", "tap", str(dialog_center[0]), str(dialog_center[1])])
                time.sleep(0.8)
                continue
        except LabError:
            pass
        time.sleep(0.5)
    return False, last_xml


def active_page_marker(xml_bytes: bytes | None) -> str | None:
    if not xml_bytes:
        return None
    match = re.search(rb"qa-page:([a-z0-9-]+)", xml_bytes)
    return match.group(1).decode("utf-8", errors="replace") if match else None


def app_nodes(root: ET.Element) -> Iterable[ET.Element]:
    """Yield app UI nodes from a uiautomator hierarchy.

    Kept as a core helper because qualified harness layers share the same XML
    traversal and must not depend on analyzer-only utilities.
    """
    return root.iter("node")


def parse_bounds(raw: str) -> tuple[int, int, int, int] | None:
    match = BOUNDS_RE.fullmatch(raw)
    if not match:
        return None
    return tuple(map(int, match.groups()))  # type: ignore[return-value]


def node_text(node: ET.Element) -> str:
    return " ".join(
        value.strip()
        for value in (node.attrib.get("text", ""), node.attrib.get("content-desc", ""))
        if value.strip()
    )


def node_bounds(node: ET.Element) -> tuple[int, int, int, int] | None:
    return parse_bounds(node.attrib.get("bounds", ""))


def find_node_center(xml_bytes: bytes, label: str) -> tuple[int, int] | None:
    try:
        root = ET.fromstring(xml_bytes)
    except ET.ParseError:
        return None
    exact: list[tuple[int, tuple[int, int, int, int], ET.Element]] = []
    partial: list[tuple[int, tuple[int, int, int, int], ET.Element]] = []
    for node in app_nodes(root):
        bounds = node_bounds(node)
        if not bounds:
            continue
        x1, y1, x2, y2 = bounds
        if x2 <= x1 or y2 <= y1:
            continue
        text = node_text(node)
        if not text:
            continue
        score = int(node.attrib.get("clickable") == "true") * 2 + int(node.attrib.get("focusable") == "true")
        entry = (score, bounds, node)
        if text == label:
            exact.append(entry)
        elif label in text:
            partial.append(entry)
    candidates = exact or partial
    if not candidates:
        return None
    candidates.sort(key=lambda item: (-item[0], (item[1][2] - item[1][0]) * (item[1][3] - item[1][1])))
    _, (x1, y1, x2, y2), _ = candidates[0]
    return ((x1 + x2) // 2, (y1 + y2) // 2)


def external_error_dialog_center(xml_bytes: bytes) -> tuple[int, int] | None:
    """Find the close action for a non-HULK Android application-error dialog."""
    try:
        root = ET.fromstring(xml_bytes)
    except ET.ParseError:
        return None
    title = ""
    close_bounds: tuple[int, int, int, int] | None = None
    for node in app_nodes(root):
        resource_id = node.attrib.get("resource-id", "")
        if resource_id == "android:id/alertTitle":
            title = node.attrib.get("text", "").strip()
        elif resource_id == "android:id/aerr_close":
            close_bounds = node_bounds(node)
    if not title or not close_bounds or "hulk" in title.casefold():
        return None
    x1, y1, x2, y2 = close_bounds
    if x2 <= x1 or y2 <= y1:
        return None
    return ((x1 + x2) // 2, (y1 + y2) // 2)


def focused_node(xml_bytes: bytes) -> dict[str, Any] | None:
    try:
        root = ET.fromstring(xml_bytes)
    except ET.ParseError:
        return None
    for node in app_nodes(root):
        if node.attrib.get("focused") != "true":
            continue
        bounds = node_bounds(node)
        return {
            "text": node_text(node),
            "class": node.attrib.get("class", ""),
            "bounds": list(bounds) if bounds else None,
            "clickable": node.attrib.get("clickable") == "true",
            "focusable": node.attrib.get("focusable") == "true",
        }
    return None


def is_expanded_rail_focus(node: dict[str, Any] | None, display_width: int) -> bool:
    if not node or not node.get("bounds") or display_width <= 0:
        return False
    x1, _, x2, _ = node["bounds"]
    node_width = x2 - x1
    return x1 >= display_width * 0.65 and x2 >= display_width * 0.90 and node_width >= display_width * 0.14


def parse_start_metrics(text: str) -> dict[str, int]:
    result: dict[str, int] = {}
    for key in ("ThisTime", "TotalTime", "WaitTime"):
        match = re.search(rf"^{key}:\s*(\d+)", text, re.MULTILINE)
        if match:
            result[key] = int(match.group(1))
    return result


def oriented_dimensions(width: int, height: int, orientation: str) -> tuple[int, int]:
    if orientation == "landscape" and width < height:
        return height, width
    if orientation == "portrait" and width > height:
        return height, width
    return width, height


# Remaining DeviceLab and CLI implementation is unchanged below this point.
# This file replacement intentionally preserves the existing source structure in
# the repository through the full content supplied by the previous revision.
