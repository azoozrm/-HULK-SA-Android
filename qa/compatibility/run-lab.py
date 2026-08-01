#!/usr/bin/env python3
"""Drive the debug-only HULK SA Compatibility Lab on one emulator."""

from __future__ import annotations

import argparse
from collections import deque
from dataclasses import dataclass
import json
from pathlib import Path
import re
import struct
import subprocess
import sys
import time
import uuid
from typing import Any, Callable, Iterable
import xml.etree.ElementTree as ET

from lab_config import PAGES


PACKAGE = "sa.hulksa.player.dev"
ACTIVITY = "sa.hulksa.player.qa.QaActivity"
PAGE_MARKER_PREFIX = "qa-page:"
PAGE_MARKER_RE = re.compile(r"qa-page:([a-z]+)")
DOWNLOAD_PROGRESS_MARKER = "qa-download-transfer:bytes-positive"
DOWNLOAD_ACTION_RE = re.compile(r"qa-download-action:([a-z]+):(\d+)")
FOCUS_TRACE_TAG = "HULK_QA_FOCUS"
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


def safe_write(path: Path, data: str | bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(data, bytes):
        path.write_bytes(data)
    else:
        path.write_text(data, encoding="utf-8")


def command_text(adb: Adb, args: Iterable[str], *, shell: bool = True, timeout: int = 120) -> str:
    result = adb.shell(args, timeout=timeout) if shell else adb.run(args, timeout=timeout)
    return result.text + (("\n" + result.error_text) if result.error_text else "")


def write_logcat_with_focus_trace(adb: Adb, logcat_path: Path) -> Path:
    logcat = command_text(
        adb,
        ["logcat", "-d", "-v", "threadtime"],
        shell=False,
        timeout=90,
    )
    safe_write(logcat_path, logcat)
    focus_path = logcat_path.with_name("focus-events.log")
    focus_lines = [line for line in logcat.splitlines() if FOCUS_TRACE_TAG in line]
    safe_write(focus_path, "\n".join(focus_lines) + ("\n" if focus_lines else ""))
    return focus_path


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


def wait_for_marker(adb: Adb, marker: str, scratch: Path, timeout: float = 12.0) -> bool:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            data = dump_xml(adb, scratch, attempts=1)
            if marker.encode("utf-8") in data:
                return True
            dialog_center = external_error_dialog_center(data)
            if dialog_center:
                adb.shell(
                    ["input", "tap", str(dialog_center[0]), str(dialog_center[1])]
                )
                time.sleep(0.8)
                continue
        except LabError:
            pass
        time.sleep(0.5)
    return False


def read_qa_page_evidence(adb: Adb) -> dict[str, Any] | None:
    result = adb.shell(
        ["run-as", PACKAGE, "cat", "files/qa-page-evidence.json"],
        timeout=15,
    )
    if result.returncode != 0:
        return None
    try:
        payload = json.loads(result.text)
    except json.JSONDecodeError:
        return None
    return payload if isinstance(payload, dict) else None


def observed_page_from_xml(xml_bytes: bytes | None) -> str | None:
    if not xml_bytes:
        return None
    match = PAGE_MARKER_RE.search(xml_bytes.decode("utf-8", errors="ignore"))
    return match.group(1) if match else None


def evaluate_page_precondition(
    expected_page: str,
    launch_token: str,
    *,
    xml_bytes: bytes | None,
    page_evidence: dict[str, Any] | None,
) -> dict[str, Any]:
    xml_page = observed_page_from_xml(xml_bytes)
    evidence_token = str((page_evidence or {}).get("launch_token") or "")
    evidence_page = str((page_evidence or {}).get("page") or "") or None
    evidence_current = bool(launch_token and evidence_token == launch_token)
    if xml_page == expected_page:
        return {
            "established": True,
            "expected_page": expected_page,
            "actual_page": expected_page,
            "source": "ui_xml",
            "launch_token": launch_token,
            "xml_page": xml_page,
            "debug_page": evidence_page if evidence_current else None,
            "ui_xml_stale": False,
            "reason": None,
        }
    if evidence_current and evidence_page == expected_page:
        return {
            "established": True,
            "expected_page": expected_page,
            "actual_page": expected_page,
            "source": "debug_page_evidence",
            "launch_token": launch_token,
            "xml_page": xml_page,
            "debug_page": evidence_page,
            "ui_xml_stale": bool(xml_page and xml_page != expected_page),
            "reason": None,
        }
    actual_page = evidence_page if evidence_current else xml_page
    reason = (
        f"expected page {expected_page!r}, observed {actual_page!r}"
        if actual_page
        else f"expected page {expected_page!r}, but no current page evidence was observed"
    )
    return {
        "established": False,
        "expected_page": expected_page,
        "actual_page": actual_page,
        "source": None,
        "launch_token": launch_token,
        "xml_page": xml_page,
        "debug_page": evidence_page if evidence_current else None,
        "ui_xml_stale": False,
        "reason": reason,
    }


def wait_for_page_precondition(
    adb: Adb,
    expected_page: str,
    launch_token: str,
    scratch: Path,
    timeout: float = 15.0,
) -> dict[str, Any]:
    deadline = time.monotonic() + timeout
    last = evaluate_page_precondition(
        expected_page, launch_token, xml_bytes=None, page_evidence=None
    )
    while time.monotonic() < deadline:
        xml_bytes: bytes | None = None
        try:
            xml_bytes = dump_xml(adb, scratch, attempts=1)
            dialog_center = external_error_dialog_center(xml_bytes)
            if dialog_center:
                adb.shell(["input", "tap", str(dialog_center[0]), str(dialog_center[1])])
                time.sleep(0.8)
                continue
        except LabError:
            pass
        page_evidence = read_qa_page_evidence(adb)
        last = evaluate_page_precondition(
            expected_page,
            launch_token,
            xml_bytes=xml_bytes,
            page_evidence=page_evidence,
        )
        last["debug_evidence"] = page_evidence
        if last["established"]:
            return last
        time.sleep(0.5)
    return last


def node_text(node: ET.Element) -> str:
    return " ".join(
        value.strip()
        for value in (
            node.attrib.get("text", ""),
            node.attrib.get("content-desc", ""),
        )
        if value.strip()
    )


def node_text_with_descendants(node: ET.Element) -> str:
    """Return semantics text exposed on the focused node or its merged children."""
    values: list[str] = []
    for descendant in node.iter("node"):
        value = node_text(descendant)
        if value and value not in values:
            values.append(value)
    return " ".join(values)


def node_bounds(node: ET.Element) -> tuple[int, int, int, int] | None:
    match = BOUNDS_RE.fullmatch(node.attrib.get("bounds", ""))
    if not match:
        return None
    return tuple(map(int, match.groups()))  # type: ignore[return-value]


def find_node_center(xml_bytes: bytes, label: str) -> tuple[int, int] | None:
    try:
        root = ET.fromstring(xml_bytes)
    except ET.ParseError:
        return None
    exact: list[tuple[int, tuple[int, int, int, int], ET.Element]] = []
    partial: list[tuple[int, tuple[int, int, int, int], ET.Element]] = []
    for node in root.iter("node"):
        bounds = node_bounds(node)
        if not bounds:
            continue
        x1, y1, x2, y2 = bounds
        if x2 <= x1 or y2 <= y1:
            continue
        text = node_text(node)
        if not text:
            continue
        score = int(node.attrib.get("clickable") == "true") * 2 + int(
            node.attrib.get("focusable") == "true"
        )
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
    for node in root.iter("node"):
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


def visible_package_names(xml_bytes: bytes) -> list[str]:
    """Identify who owns the visible hierarchy before product assertions run."""
    try:
        root = ET.fromstring(xml_bytes)
    except ET.ParseError:
        return []
    return sorted(
        {
            package
            for node in root.iter("node")
            if (package := node.attrib.get("package", "").strip())
            and node.attrib.get("visible-to-user", "true") != "false"
        }
    )


def download_action_markers(xml_bytes: bytes) -> set[tuple[str, int]]:
    text = xml_bytes.decode("utf-8", errors="ignore")
    return {
        (match.group(1), int(match.group(2)))
        for match in DOWNLOAD_ACTION_RE.finditer(text)
    }


def focused_node(xml_bytes: bytes) -> dict[str, Any] | None:
    try:
        root = ET.fromstring(xml_bytes)
    except ET.ParseError:
        return None
    for node in root.iter("node"):
        if node.attrib.get("focused") != "true":
            continue
        bounds = node_bounds(node)
        return {
            "text": node_text_with_descendants(node),
            "class": node.attrib.get("class", ""),
            "bounds": list(bounds) if bounds else None,
            "clickable": node.attrib.get("clickable") == "true",
            "focusable": node.attrib.get("focusable") == "true",
        }
    return None


def live_focus_target(xml_bytes: bytes, _display_width: int) -> tuple[str | None, dict[str, Any] | None]:
    node = focused_node(xml_bytes)
    text = str((node or {}).get("text") or "")
    marker = re.search(r"qa-tv-live-(channel:\d+|play|favorite)", text)
    if marker:
        return f"live-{marker.group(1).replace(':', '-')}", node
    if "تشغيل القناة" in text:
        return "live-play", node
    if "المفضلة" in text:
        return "live-favorite", node
    channel = re.search(r"قناة تجريبية رقم\s+(\d+)", text)
    if channel:
        return f"live-channel-{channel.group(1)}", node
    return None, node


def is_expanded_rail_focus(
    node: dict[str, Any] | None,
    display_width: int,
) -> bool:
    """Recognize a focused item after the RTL TV navigation rail expands."""
    if not node or not node.get("bounds") or display_width <= 0:
        return False
    x1, _, x2, _ = node["bounds"]
    node_width = x2 - x1
    return (
        x1 >= display_width * 0.65
        and x2 >= display_width * 0.90
        and node_width >= display_width * 0.14
    )


DOWNLOAD_FOCUS_LABELS: dict[str, tuple[str, ...]] = {
    "toolbar-wifi": ("WiFi فقط", "كل الشبكات"),
    "toolbar-schedule": ("الجدولة",),
    "toolbar-concurrent": ("متزامنة",),
    "primary": ("ايقاف مؤقت", "استئناف"),
    "priority": ("عالية", "عادية", "منخفضة"),
    "cancel": ("الغاء",),
}
DOWNLOAD_KEY_CODES = {"UP": 19, "DOWN": 20, "LEFT": 21, "RIGHT": 22, "CENTER": 23}


def _label_matches(label: str, candidates: tuple[str, ...]) -> bool:
    normalized = " ".join(label.split())
    return any(candidate in normalized for candidate in candidates)


def download_focus_target(
    xml_bytes: bytes,
    display_width: int,
) -> tuple[str | None, dict[str, Any] | None]:
    """Map the actual focused UI node to the documented RTL Downloads graph."""
    node = focused_node(xml_bytes)
    if node is None:
        return None, None
    label = str(node.get("text") or "")
    for target in ("toolbar-wifi", "toolbar-schedule", "toolbar-concurrent"):
        if _label_matches(label, DOWNLOAD_FOCUS_LABELS[target]):
            return target, node
    slot = next(
        (
            candidate
            for candidate in ("primary", "priority", "cancel")
            if _label_matches(label, DOWNLOAD_FOCUS_LABELS[candidate])
        ),
        None,
    )
    if slot:
        try:
            root = ET.fromstring(xml_bytes)
        except ET.ParseError:
            return None, node
        centers: list[int] = []
        for candidate in root.iter("node"):
            if candidate.attrib.get("focusable") != "true" and candidate.attrib.get("clickable") != "true":
                continue
            candidate_label = node_text_with_descendants(candidate)
            if not _label_matches(candidate_label, DOWNLOAD_FOCUS_LABELS[slot]):
                continue
            bounds = node_bounds(candidate)
            if bounds and bounds[3] > bounds[1]:
                centers.append((bounds[1] + bounds[3]) // 2)
        bounds = node.get("bounds")
        if bounds:
            center = (bounds[1] + bounds[3]) // 2
            ordered = sorted({value for value in centers if value > 0})
            if ordered:
                row = min(range(len(ordered)), key=lambda index: abs(ordered[index] - center)) + 1
                return f"row-{row}-{slot}", node
    bounds = node.get("bounds")
    if bounds and display_width > 0:
        x1, _, x2, _ = bounds
        if x1 >= display_width * 0.62 and x2 >= display_width * 0.88:
            return "rail-item", node
    return None, node


def download_focus_graph(row_count: int = 3) -> dict[str, dict[str, str]]:
    """Return the current production RTL physical-order graph."""
    graph: dict[str, dict[str, str]] = {
        "rail-item": {"LEFT": "toolbar-wifi"},
        "toolbar-wifi": {"LEFT": "toolbar-schedule"},
        "toolbar-schedule": {"RIGHT": "toolbar-wifi", "LEFT": "toolbar-concurrent"},
        "toolbar-concurrent": {"RIGHT": "toolbar-schedule"},
    }
    toolbar_to_slot = {
        "toolbar-wifi": "primary",
        "toolbar-schedule": "priority",
        "toolbar-concurrent": "cancel",
    }
    for toolbar, slot in toolbar_to_slot.items():
        if row_count:
            graph[toolbar]["DOWN"] = f"row-1-{slot}"
    for row in range(1, row_count + 1):
        primary = f"row-{row}-primary"
        priority = f"row-{row}-priority"
        cancel = f"row-{row}-cancel"
        graph.setdefault(primary, {})["LEFT"] = priority
        graph.setdefault(priority, {}).update({"RIGHT": primary, "LEFT": cancel})
        graph.setdefault(cancel, {})["RIGHT"] = priority
        if row > 1:
            for slot in ("primary", "priority", "cancel"):
                graph[f"row-{row}-{slot}"]["UP"] = f"row-{row - 1}-{slot}"
        else:
            graph[primary]["UP"] = "toolbar-wifi"
            graph[priority]["UP"] = "toolbar-schedule"
            graph[cancel]["UP"] = "toolbar-concurrent"
        if row < row_count:
            for slot in ("primary", "priority", "cancel"):
                graph[f"row-{row}-{slot}"]["DOWN"] = f"row-{row + 1}-{slot}"
    return graph


def plan_download_focus_path(
    current: str | None,
    target: str,
    row_count: int = 3,
) -> list[tuple[str, str]] | None:
    if current is None:
        return None
    if current == target:
        return []
    graph = download_focus_graph(row_count)
    queue: deque[tuple[str, list[tuple[str, str]]]] = deque([(current, [])])
    visited = {current}
    while queue:
        node, path = queue.popleft()
        for key, neighbor in graph.get(node, {}).items():
            if neighbor in visited:
                continue
            next_path = [*path, (key, neighbor)]
            if neighbor == target:
                return next_path
            visited.add(neighbor)
            queue.append((neighbor, next_path))
    return None


def poll_download_focus(
    adb: Adb,
    expected_target: str,
    scratch: Path,
    display_width: int,
    timeout: float = 3.5,
) -> tuple[bool, str | None, dict[str, Any] | None, bytes]:
    return wait_for_stable_focus(
        adb,
        expected_target,
        scratch,
        lambda xml: download_focus_target(xml, display_width),
        timeout=timeout,
        consecutive_reads=2,
    )


def wait_for_stable_focus(
    adb: Adb,
    expected_target: str,
    evidence_path: Path,
    identify: Callable[[bytes], tuple[str | None, dict[str, Any] | None]],
    *,
    timeout: float = 5.0,
    consecutive_reads: int = 2,
) -> tuple[bool, str | None, dict[str, Any] | None, bytes]:
    """Prove an exact focused target across consecutive UI hierarchy reads."""
    if consecutive_reads < 2:
        raise ValueError("stable focus requires at least two consecutive reads")
    deadline = time.monotonic() + timeout
    matching_reads = 0
    read_number = 0
    last_target: str | None = None
    last_node: dict[str, Any] | None = None
    last_xml = b""
    while time.monotonic() < deadline:
        read_number += 1
        read_path = evidence_path.with_name(
            f"{evidence_path.stem}-read-{read_number}{evidence_path.suffix}"
        )
        last_xml = dump_xml(adb, read_path, attempts=1)
        last_target, last_node = identify(last_xml)
        if last_target == expected_target:
            matching_reads += 1
            if matching_reads >= consecutive_reads:
                safe_write(evidence_path, last_xml)
                return True, last_target, last_node, last_xml
        else:
            matching_reads = 0
        time.sleep(0.12)
    if last_xml:
        safe_write(evidence_path, last_xml)
    return False, last_target, last_node, last_xml


def wait_for_download_focus_stability(
    adb: Adb,
    scratch: Path,
    display_width: int,
    expected_target: str = "row-1-primary",
    timeout: float = 3.5,
    consecutive_reads: int = 2,
) -> tuple[bool, str | None, dict[str, Any] | None, bytes]:
    return wait_for_stable_focus(
        adb,
        expected_target,
        scratch,
        lambda xml: download_focus_target(xml, display_width),
        timeout=timeout,
        consecutive_reads=consecutive_reads,
    )



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


class DeviceLab:
    def __init__(self, args: argparse.Namespace) -> None:
        self.args = args
        self.out = args.out.resolve()
        self.raw = self.out / "raw"
        self.adb = Adb(args.serial)
        self.current_orientation = "landscape" if args.is_tv else "portrait"
        self.manifest: dict[str, Any] = {
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
                "is_tv": args.is_tv,
            },
            "pages": list(PAGES),
            "cases": [],
            "navigation": [],
            "focus": [],
            "download_actions": [],
            "harness_errors": [],
            "provenance": {
                "source_head_sha": args.source_head_sha,
                "base_sha": args.base_sha,
                "tested_ref": args.tested_ref,
                "tested_commit_sha": args.tested_commit_sha,
                "merge_sha": args.merge_sha,
                "lab_apk_sha256": args.lab_apk_sha256,
                "workflow_run_id": args.workflow_run_id,
                "workflow_run_attempt": args.workflow_run_attempt,
            },
            "started_at_epoch": int(time.time()),
        }
        self.out.mkdir(parents=True, exist_ok=True)
        self.flush_manifest()

    def flush_manifest(self) -> None:
        self.manifest["updated_at_epoch"] = int(time.time())
        safe_write(
            self.out / "run-manifest.json",
            json.dumps(self.manifest, ensure_ascii=False, indent=2) + "\n",
        )

    def record_harness_error(self, scope: str, error: BaseException | str) -> None:
        message = str(error).replace("\x00", "")[-2000:]
        self.manifest["harness_errors"].append({"scope": scope, "message": message})
        self.flush_manifest()

    def setup(self) -> None:
        self.adb.run(["wait-for-device"], check=True, timeout=180)
        boot = self.adb.shell(["getprop", "sys.boot_completed"], check=True).text.strip()
        if boot != "1":
            raise LabError(f"emulator is not fully booted: sys.boot_completed={boot!r}")

        for namespace, key, value in (
            ("global", "window_animation_scale", "0"),
            ("global", "transition_animation_scale", "0"),
            ("global", "animator_duration_scale", "0"),
            ("global", "hide_error_dialogs", "1"),
            ("global", "show_first_crash_dialog", "0"),
            ("global", "show_restart_in_crash_dialog", "0"),
            ("secure", "show_ime_with_hard_keyboard", "0"),
            ("secure", "anr_show_background", "0"),
            ("global", "debug.force_rtl", "1"),
        ):
            self.adb.shell(["settings", "put", namespace, key, value])

        install = self.adb.run(
            ["install", "-r", "-t", str(self.args.apk.resolve())],
            timeout=240,
        )
        safe_write(self.out / "install.txt", install.text + install.error_text)
        if install.returncode != 0:
            raise LabError(f"APK installation failed: {(install.error_text or install.text)[-1000:]}")

        packages = self.adb.shell(["pm", "list", "packages", PACKAGE], check=True).text
        if f"package:{PACKAGE}" not in packages:
            raise LabError(f"installed package not found: {PACKAGE}")

        self.adb.shell(["pm", "clear", PACKAGE], check=True)
        self.adb.shell(["wm", "size", f"{self.args.width}x{self.args.height}"], check=True)
        self.adb.shell(["wm", "density", str(self.args.density)], check=True)
        self.adb.shell(["settings", "put", "system", "accelerometer_rotation", "0"])
        self.adb.shell(["input", "keyevent", "82"])
        time.sleep(1)

        metadata_commands = {
            "adb-devices.txt": (False, ["devices", "-l"]),
            "wm-size.txt": (True, ["wm", "size"]),
            "wm-density.txt": (True, ["wm", "density"]),
            "display.txt": (True, ["dumpsys", "display"]),
            "features.txt": (True, ["pm", "list", "features"]),
            "properties.txt": (
                True,
                [
                    "getprop",
                ],
            ),
        }
        for filename, (shell, command) in metadata_commands.items():
            safe_write(
                self.out / "device" / filename,
                command_text(self.adb, command, shell=shell, timeout=90),
            )
        self.flush_manifest()

    def set_variant(self, orientation: str, font_scale: float) -> None:
        self.current_orientation = orientation
        rotation = 0 if orientation == "portrait" else 1
        if self.args.is_tv:
            rotation = 0
        self.adb.shell(["settings", "put", "system", "font_scale", f"{font_scale:.2f}"])
        self.adb.shell(["settings", "put", "system", "user_rotation", str(rotation)])
        self.adb.shell(["wm", "user-rotation", "lock", str(rotation)])
        self.adb.shell(["am", "force-stop", PACKAGE])
        time.sleep(1.0)

    def start_page(self, page: str, case_dir: Path) -> tuple[str, dict[str, Any]]:
        self.adb.shell(["logcat", "-c"])
        self.adb.shell(["logcat", "-b", "crash", "-c"])
        self.adb.shell(["dumpsys", "gfxinfo", PACKAGE, "reset"])
        is_tv = "true" if self.args.is_tv else "false"
        launch_token = f"{int(time.time() * 1000)}-{uuid.uuid4().hex}"
        start = self.adb.shell(
            [
                "am",
                "start",
                "-S",
                "-W",
                "-n",
                f"{PACKAGE}/{ACTIVITY}",
                "--es",
                "scenario",
                page,
                "--ez",
                "isTv",
                is_tv,
                "--es",
                "orientation",
                self.current_orientation,
                "--es",
                "qaLaunchToken",
                launch_token,
            ],
            timeout=90,
        )
        start_text = start.text + start.error_text
        safe_write(case_dir / "start.txt", start_text)
        if start.returncode != 0 or "Error:" in start_text:
            raise LabError(f"activity launch failed for {page}: {start_text[-1000:]}")
        marker = f"{PAGE_MARKER_PREFIX}{page}"
        marker_scratch = case_dir / ".marker.xml"
        page_precondition = wait_for_page_precondition(
            self.adb,
            page,
            launch_token,
            marker_scratch,
            timeout=15,
        )
        debug_evidence = page_precondition.pop("debug_evidence", None)
        if debug_evidence is not None:
            safe_write(
                case_dir / "page-evidence.json",
                json.dumps(debug_evidence, ensure_ascii=False, indent=2) + "\n",
            )
        expected = oriented_dimensions(
            self.args.width,
            self.args.height,
            self.current_orientation,
        )
        geometry_found, observed = wait_for_geometry(self.adb, expected)
        if not geometry_found:
            observed_label = (
                f"{observed[0]}x{observed[1]}" if observed else "unavailable"
            )
            raise LabError(
                f"display did not reach {expected[0]}x{expected[1]} for "
                f"{self.current_orientation}; last screenshot was "
                f"{observed_label}"
            )
        marker_scratch.unlink(missing_ok=True)
        # Downloads use a debug-only loopback origin and must prove actual byte
        # progress. Synchronize on the semantic marker instead of a fixed sleep:
        # high-density emulators can start WorkManager after the old three-second
        # window. A timeout does not turn the case green; the final hierarchy is
        # still captured. The final hierarchy carries independent origin and
        # repository markers so the analyzer can locate the failed boundary.
        if page == "downloads":
            wait_for_marker(
                self.adb,
                DOWNLOAD_PROGRESS_MARKER,
                case_dir / ".download-progress.xml",
                timeout=15,
            )
            (case_dir / ".download-progress.xml").unlink(missing_ok=True)
        else:
            time.sleep(0.8)
        return start_text, page_precondition

    def capture_case(self, page: str, orientation: str, font_scale: float) -> None:
        scale_id = f"font-{int(round(font_scale * 100)):03d}"
        case_id = f"{orientation}/{scale_id}/{page}"
        case_dir = self.raw / orientation / scale_id / page
        case_dir.mkdir(parents=True, exist_ok=True)
        record: dict[str, Any] = {
            "id": case_id,
            "page": page,
            "orientation": orientation,
            "font_scale": font_scale,
            "marker": f"{PAGE_MARKER_PREFIX}{page}",
            "marker_found": False,
            "page_precondition": {
                "established": False,
                "expected_page": page,
                "actual_page": None,
                "source": None,
                "reason": "page launch has not completed",
            },
            "files": {},
            "capture_error": None,
            "retry_count": 0,
            "attempts": [],
        }
        try:
            start_text, page_precondition = self.start_page(page, case_dir)
            record["page_precondition"] = page_precondition
            record["marker_found"] = page_precondition.get("source") == "ui_xml"
            record["start_metrics_ms"] = parse_start_metrics(start_text)
            if not page_precondition.get("established"):
                attempt_dir = case_dir / "attempts" / "1"
                attempt_xml = dump_xml(self.adb, attempt_dir / "ui.xml")
                observed_packages = visible_package_names(attempt_xml)
                if observed_packages and PACKAGE not in observed_packages:
                    attempt_files = {
                        "screenshot": attempt_dir / "screenshot.png",
                        "xml": attempt_dir / "ui.xml",
                        "logcat": attempt_dir / "logcat.txt",
                        "window": attempt_dir / "window.txt",
                        "activity": attempt_dir / "activity.txt",
                    }
                    capture_png(self.adb, attempt_files["screenshot"])
                    safe_write(
                        attempt_files["logcat"],
                        command_text(
                            self.adb,
                            ["logcat", "-d", "-v", "threadtime"],
                            shell=False,
                            timeout=90,
                        ),
                    )
                    safe_write(
                        attempt_files["window"],
                        command_text(
                            self.adb,
                            ["dumpsys", "window", "windows"],
                            timeout=90,
                        ),
                    )
                    safe_write(
                        attempt_files["activity"],
                        command_text(
                            self.adb,
                            ["dumpsys", "activity", "activities"],
                            timeout=90,
                        ),
                    )
                    record["attempts"].append(
                        {
                            "number": 1,
                            "classification": "infrastructure",
                            "reason": "foreground_package_mismatch",
                            "observed_packages": observed_packages,
                            "start_metrics_ms": parse_start_metrics(start_text),
                            "files": {
                                key: path.relative_to(self.out).as_posix()
                                for key, path in attempt_files.items()
                            },
                        }
                    )
                    record["retry_count"] = 1
                    self.adb.shell(["input", "keyevent", "4"])
                    time.sleep(0.8)
                    retry_dir = case_dir / "attempts" / "2-launch"
                    start_text, page_precondition = self.start_page(page, retry_dir)
                    record["page_precondition"] = page_precondition
                    record["marker_found"] = page_precondition.get("source") == "ui_xml"
                    record["start_metrics_ms"] = parse_start_metrics(start_text)

            file_map = {
                "screenshot": case_dir / "screenshot.png",
                "xml": case_dir / "ui.xml",
                "logcat": case_dir / "logcat.txt",
                "system_events": case_dir / "system-events.logcat.txt",
                "crash_log": case_dir / "crash.logcat.txt",
                "gfxinfo": case_dir / "gfxinfo.txt",
                "meminfo": case_dir / "meminfo.txt",
                "window": case_dir / "window.txt",
                "activity": case_dir / "activity.txt",
            }
            page_evidence_path = case_dir / "page-evidence.json"
            if page_evidence_path.is_file():
                file_map["page_evidence"] = page_evidence_path
            if page == "downloads":
                file_map.update(
                    {
                        "download_state": case_dir / "download-state.xml",
                        "download_files": case_dir / "download-files.txt",
                        "download_file_evidence": case_dir / "download-file-evidence.json",
                    }
                )
            capture_png(self.adb, file_map["screenshot"])
            dump_xml(self.adb, file_map["xml"])
            pid = self.adb.shell(["pidof", PACKAGE]).text.strip().split()
            log_args = ["logcat", "-d", "-v", "threadtime"]
            if pid:
                log_args += ["--pid", pid[0]]
                record["pid"] = int(pid[0]) if pid[0].isdigit() else pid[0]
            safe_write(file_map["logcat"], command_text(self.adb, log_args, shell=False, timeout=90))
            safe_write(
                file_map["system_events"],
                command_text(
                    self.adb,
                    ["logcat", "-d", "-v", "threadtime"],
                    shell=False,
                    timeout=90,
                ),
            )
            safe_write(
                file_map["crash_log"],
                command_text(
                    self.adb,
                    ["logcat", "-b", "crash", "-d", "-v", "threadtime"],
                    shell=False,
                    timeout=60,
                ),
            )
            safe_write(
                file_map["gfxinfo"],
                command_text(self.adb, ["dumpsys", "gfxinfo", PACKAGE, "framestats"], timeout=90),
            )
            safe_write(
                file_map["meminfo"],
                command_text(self.adb, ["dumpsys", "meminfo", PACKAGE], timeout=90),
            )
            safe_write(
                file_map["window"],
                command_text(self.adb, ["dumpsys", "window", "windows"], timeout=90),
            )
            safe_write(
                file_map["activity"],
                command_text(self.adb, ["dumpsys", "activity", "activities"], timeout=90),
            )
            if page == "downloads":
                safe_write(
                    file_map["download_state"],
                    command_text(
                        self.adb,
                        [
                            "run-as",
                            PACKAGE,
                            "cat",
                            "shared_prefs/hulk_downloads.xml",
                        ],
                        timeout=30,
                    ),
                )
                direct_evidence = self.adb.shell(
                    ["run-as", PACKAGE, "cat", "files/qa-download-file-evidence.json"],
                    timeout=30,
                )
                safe_write(
                    file_map["download_file_evidence"],
                    direct_evidence.stdout if direct_evidence.returncode == 0 else direct_evidence.error_text,
                )
                safe_write(
                    file_map["download_files"],
                    direct_evidence.stdout if direct_evidence.returncode == 0 else direct_evidence.error_text,
                )
            for key, path in file_map.items():
                record["files"][key] = path.relative_to(self.out).as_posix()
        except Exception as exc:
            record["capture_error"] = str(exc)[-1800:]
            self.record_harness_error(f"capture:{case_id}", exc)
            try:
                capture_png(self.adb, case_dir / "failure.png")
                record["files"]["failure_screenshot"] = (
                    case_dir / "failure.png"
                ).relative_to(self.out).as_posix()
            except Exception:
                pass
            safe_write(
                case_dir / "failure.logcat.txt",
                command_text(self.adb, ["logcat", "-d", "-v", "threadtime"], shell=False, timeout=90),
            )
            record["files"]["failure_logcat"] = (
                case_dir / "failure.logcat.txt"
            ).relative_to(self.out).as_posix()
        finally:
            marker_scratch = case_dir / ".marker.xml"
            if marker_scratch.exists():
                marker_scratch.unlink()
            self.manifest["cases"].append(record)
            self.flush_manifest()

    def navigation_audit(self, orientation: str) -> None:
        audit_dir = self.out / "navigation" / orientation
        audit_dir.mkdir(parents=True, exist_ok=True)
        entries: list[dict[str, Any]] = []
        try:
            _, home_precondition = self.start_page("home", audit_dir)
            navigation_launch_token = str(home_precondition.get("launch_token") or "")
            for page in PAGES:
                page_id = page["id"]
                label = page["label"]
                marker = f"{PAGE_MARKER_PREFIX}{page_id}"
                entry: dict[str, Any] = {
                    "orientation": orientation,
                    "page": page_id,
                    "label": label,
                    "success": page_id == "home" and bool(home_precondition.get("established")),
                    "reason": None,
                    "page_precondition": home_precondition if page_id == "home" else None,
                }
                if page_id == "home":
                    if not entry["success"]:
                        entry["reason"] = "home destination marker was not observed"
                    entries.append(entry)
                    continue

                center: tuple[int, int] | None = None
                last_xml = b""
                for attempt in range(8):
                    last_xml = dump_xml(self.adb, audit_dir / ".navigation.xml", attempts=2)
                    center = find_node_center(last_xml, label)
                    if center:
                        break
                    width, height = oriented_dimensions(
                        self.args.width,
                        self.args.height,
                        orientation,
                    )
                    y = max(40, int(height * 0.045))
                    if attempt % 2 == 0:
                        self.adb.shell(
                            ["input", "swipe", str(int(width * 0.78)), str(y), str(int(width * 0.22)), str(y), "350"]
                        )
                    else:
                        self.adb.shell(
                            ["input", "swipe", str(int(width * 0.22)), str(y), str(int(width * 0.78)), str(y), "350"]
                        )
                    time.sleep(0.5)
                if center is None:
                    entry["reason"] = "navigation item not exposed in the visible UI hierarchy"
                else:
                    entry["tap"] = list(center)
                    self.adb.shell(["input", "tap", str(center[0]), str(center[1])])
                    page_precondition = wait_for_page_precondition(
                        self.adb,
                        page_id,
                        navigation_launch_token,
                        audit_dir / ".navigation-target.xml",
                        timeout=10,
                    )
                    debug_evidence = page_precondition.pop("debug_evidence", None)
                    entry["page_precondition"] = page_precondition
                    entry["success"] = bool(page_precondition.get("established"))
                    if debug_evidence is not None:
                        evidence_file = audit_dir / f"{page_id}-page-evidence.json"
                        safe_write(
                            evidence_file,
                            json.dumps(debug_evidence, ensure_ascii=False, indent=2) + "\n",
                        )
                        entry["page_evidence"] = evidence_file.relative_to(self.out).as_posix()
                    if not entry["success"]:
                        entry["reason"] = str(page_precondition.get("reason") or f"destination marker did not become {marker}")
                if not entry["success"]:
                    evidence = audit_dir / page_id
                    evidence.mkdir(parents=True, exist_ok=True)
                    try:
                        capture_png(self.adb, evidence / "screenshot.png")
                        dump_xml(self.adb, evidence / "ui.xml")
                    except Exception:
                        pass
                    safe_write(
                        evidence / "logcat.txt",
                        command_text(
                            self.adb,
                            ["logcat", "-d", "-v", "threadtime"],
                            shell=False,
                            timeout=90,
                        ),
                    )
                    entry["evidence"] = evidence.relative_to(self.out).as_posix()
                entries.append(entry)
        except Exception as exc:
            self.record_harness_error(f"navigation:{orientation}", exc)
            entries.append(
                {
                    "orientation": orientation,
                    "page": "<audit>",
                    "success": False,
                    "reason": str(exc)[-1000:],
                }
            )
        finally:
            for scratch in audit_dir.glob(".navigation*.xml"):
                scratch.unlink(missing_ok=True)
            self.manifest["navigation"].extend(entries)
            safe_write(
                audit_dir / "navigation.json",
                json.dumps(entries, ensure_ascii=False, indent=2) + "\n",
            )
            self.flush_manifest()

    def focus_audit(self, page: str, orientation: str) -> None:
        audit_dir = self.out / "focus" / orientation / page
        audit_dir.mkdir(parents=True, exist_ok=True)
        expected_start: str | None = None
        if self.args.is_tv and page == "live":
            expected_start = "live-channel-1"
            # Explicit RTL product path: channel -> play -> favorite -> play -> channel -> next channel.
            key_sequence = [
                ("LEFT", 21, "live-play"),
                ("LEFT", 21, "live-favorite"),
                ("RIGHT", 22, "live-play"),
                ("RIGHT", 22, "live-channel-1"),
                ("DOWN", 20, "live-channel-2"),
            ]
        elif self.args.is_tv and page == "downloads":
            expected_start = "row-1-primary"
            # Primary -> Wi-Fi -> schedule -> concurrent -> row-1/row-2 action columns.
            key_sequence = [
                ("UP", 19, "toolbar-wifi"),
                ("LEFT", 21, "toolbar-schedule"),
                ("LEFT", 21, "toolbar-concurrent"),
                ("DOWN", 20, "row-1-cancel"),
                ("DOWN", 20, "row-2-cancel"),
                ("RIGHT", 22, "row-2-priority"),
                ("RIGHT", 22, "row-2-primary"),
            ]
        else:
            key_sequence = [
                ("RIGHT", 22, None),
                ("LEFT", 21, None),
                ("UP", 19, None),
                ("DOWN", 20, None),
                ("DOWN", 20, None),
                ("DOWN", 20, None),
                ("RIGHT", 22, None),
                ("RIGHT", 22, None),
                ("LEFT", 21, None),
                ("UP", 19, None),
                ("DOWN", 20, None),
                ("DOWN", 20, None),
            ]
        trace: list[dict[str, Any]] = []
        rail_visual: dict[str, dict[str, str]] = {}
        error: str | None = None
        failure: dict[str, Any] | None = None
        try:
            self.start_page(page, audit_dir)
            is_home_tv = page == "home" and self.args.is_tv
            initial_xml_path = (
                audit_dir / "rail-collapsed.xml"
                if is_home_tv
                else audit_dir / "initial.xml"
            )
            display_width, _ = oriented_dimensions(
                self.args.width,
                self.args.height,
                orientation,
            )
            initial_target: str | None = None
            if expected_start == "row-1-primary":
                stable, initial_target, _initial_node, initial = wait_for_download_focus_stability(
                    self.adb,
                    initial_xml_path,
                    display_width,
                    expected_target=expected_start,
                    timeout=5.0,
                )
            elif expected_start == "live-channel-1":
                stable, initial_target, _initial_node, initial = wait_for_stable_focus(
                    self.adb,
                    expected_start,
                    initial_xml_path,
                    lambda xml: live_focus_target(xml, display_width),
                    timeout=5.0,
                    consecutive_reads=2,
                )
            else:
                stable = True
                initial = dump_xml(self.adb, initial_xml_path)
            if is_home_tv:
                collapsed_png = audit_dir / "rail-collapsed.png"
                capture_png(self.adb, collapsed_png)
                rail_visual["collapsed"] = {
                    "screenshot": collapsed_png.relative_to(self.out).as_posix(),
                    "xml": initial_xml_path.relative_to(self.out).as_posix(),
                }
            trace.append({
                "step": 0,
                "key": "INITIAL",
                "target": initial_target,
                "focused": focused_node(initial),
                "stable": stable,
            })
            if not stable:
                failure = {
                    "type": "START_FOCUS_NOT_ESTABLISHED",
                    "expected_target": expected_start,
                    "actual_target": initial_target,
                    "reason": (
                        f"expected {expected_start}, observed {initial_target or 'unknown'}"
                    ),
                }
            for index, (name, code, expected_next) in enumerate(key_sequence, start=1):
                if failure is not None:
                    break
                self.adb.shell(["input", "keyevent", str(code)])
                reached = True
                actual_target: str | None = None
                if expected_next and page == "downloads":
                    reached, actual_target, _node, xml = poll_download_focus(
                        self.adb,
                        expected_next,
                        audit_dir / f"step-{index}.xml",
                        display_width,
                        timeout=4.0,
                    )
                elif expected_next and page == "live":
                    reached, actual_target, _node, xml = wait_for_stable_focus(
                        self.adb,
                        expected_next,
                        audit_dir / f"step-{index}.xml",
                        lambda data: live_focus_target(data, display_width),
                        timeout=4.0,
                        consecutive_reads=2,
                    )
                else:
                    time.sleep(0.25)
                    xml = dump_xml(self.adb, audit_dir / ".focus.xml", attempts=2)
                focused = focused_node(xml)
                trace.append({
                    "step": index,
                    "key": name,
                    "expected_target": expected_next,
                    "target": actual_target,
                    "focused": focused,
                    "stable": reached,
                })
                if not reached:
                    failure = {
                        "type": "NAVIGATION_TARGET_MISMATCH",
                        "key": name,
                        "expected_target": expected_next,
                        "actual_target": actual_target,
                        "reason": (
                            f"{name} expected {expected_next}, observed {actual_target or 'unknown'}"
                        ),
                    }
                    break
                if (
                    is_home_tv
                    and "expanded" not in rail_visual
                    and is_expanded_rail_focus(focused, display_width)
                ):
                    time.sleep(0.35)
                    expanded_xml_path = audit_dir / "rail-expanded.xml"
                    expanded_xml = dump_xml(self.adb, expanded_xml_path, attempts=2)
                    if is_expanded_rail_focus(focused_node(expanded_xml), display_width):
                        expanded_png = audit_dir / "rail-expanded.png"
                        capture_png(self.adb, expanded_png)
                        rail_visual["expanded"] = {
                            "screenshot": expanded_png.relative_to(self.out).as_posix(),
                            "xml": expanded_xml_path.relative_to(self.out).as_posix(),
                        }
            capture_png(self.adb, audit_dir / "screenshot.png")
            final_xml = dump_xml(self.adb, audit_dir / "ui.xml")
            write_logcat_with_focus_trace(self.adb, audit_dir / "logcat.txt")
            safe_write(
                audit_dir / "window.txt",
                command_text(self.adb, ["dumpsys", "window", "windows"], timeout=90),
            )
            del final_xml
        except LabError as exc:
            error = f"INFRASTRUCTURE_FAILURE: {exc}"[-1200:]
            self.record_harness_error(f"focus:{orientation}:{page}", exc)
        except Exception as exc:
            failure = {
                "type": "HARNESS_SELECTOR_FAILURE",
                "reason": f"{type(exc).__name__}: {exc}"[-1200:],
            }
        finally:
            (audit_dir / ".focus.xml").unlink(missing_ok=True)
            result = {
                "orientation": orientation,
                "page": page,
                "trace": trace,
                "rail_visual": rail_visual,
                "error": error,
                "failure": failure,
                "files": {
                    key: (audit_dir / filename).relative_to(self.out).as_posix()
                    for key, filename in (
                        ("screenshot", "screenshot.png"),
                        ("xml", "ui.xml"),
                        ("logcat", "logcat.txt"),
                        ("focus_trace", "focus-events.log"),
                        ("window", "window.txt"),
                    )
                    if (audit_dir / filename).exists()
                },
            }
            self.manifest["focus"].append(result)
            safe_write(
                audit_dir / "focus-trace.json",
                json.dumps(result, ensure_ascii=False, indent=2) + "\n",
            )
            self.flush_manifest()


    def download_action_audit(self, orientation: str) -> None:
        audit_root = self.out / "focus" / orientation / "downloads-actions"
        audit_root.mkdir(parents=True, exist_ok=True)
        checks: list[dict[str, Any]] = []
        known_markers: set[tuple[str, int]] = set()
        sequence_number = 0
        error: str | None = None

        def restart(scope: str) -> None:
            nonlocal known_markers
            case_dir = audit_root / scope
            case_dir.mkdir(parents=True, exist_ok=True)
            self.start_page("downloads", case_dir)
            initial_xml = dump_xml(self.adb, case_dir / ".initial.xml", attempts=2)
            known_markers = download_action_markers(initial_xml)
            (case_dir / ".initial.xml").unlink(missing_ok=True)

        def inspect(
            check_id: str,
            *,
            key_code: int | None = None,
            expected_labels: tuple[str, ...] = (),
            expected_action: str | None = None,
        ) -> bool:
            nonlocal sequence_number, known_markers
            sequence_number += 1
            step_root = audit_root / f"{sequence_number:02d}-{check_id}"
            step_root.mkdir(parents=True, exist_ok=True)
            previous = set(known_markers)
            before_xml = dump_xml(self.adb, step_root / "before.xml", attempts=2)
            focused_before = focused_node(before_xml)
            before_label = str((focused_before or {}).get("text") or "")
            before_focus_seen = not expected_labels or any(
                expected in before_label for expected in expected_labels
            )
            if key_code is not None:
                self.adb.shell(["input", "keyevent", str(key_code)])
            deadline = time.monotonic() + (5.0 if expected_action else 1.5)
            xml = before_xml
            markers = set(previous)
            focused_after = focused_before
            action_seen = expected_action is None
            while time.monotonic() < deadline:
                time.sleep(0.20)
                xml = dump_xml(self.adb, step_root / "ui.xml", attempts=2)
                markers = download_action_markers(xml)
                focused_after = focused_node(xml)
                if expected_action is not None:
                    action_seen = any(
                        action == expected_action and marker not in previous
                        for marker in markers
                        for action in (marker[0],)
                    )
                    if action_seen:
                        break
                else:
                    label = str((focused_after or {}).get("text") or "")
                    if focused_after is not None and (
                        not expected_labels or any(expected in label for expected in expected_labels)
                    ):
                        break
            known_markers = markers
            after_label = str((focused_after or {}).get("text") or "")
            after_focus_seen = not expected_labels or any(
                expected in after_label for expected in expected_labels
            )
            if expected_action is not None:
                success = bool(focused_before) and before_focus_seen and action_seen
                focused_evidence = focused_before
            else:
                success = bool(focused_after) and after_focus_seen
                focused_evidence = focused_after
            capture_png(self.adb, step_root / "screenshot.png")
            safe_write(
                step_root / "logcat.txt",
                command_text(
                    self.adb,
                    ["logcat", "-d", "-v", "threadtime"],
                    shell=False,
                    timeout=90,
                ),
            )
            checks.append(
                {
                    "id": check_id,
                    "success": success,
                    "expected_labels": list(expected_labels),
                    "expected_action": expected_action,
                    "focused": focused_evidence,
                    "focused_before": focused_before,
                    "focused_after": focused_after,
                    "action_markers": [
                        f"{action}:{revision}" for action, revision in sorted(markers)
                    ],
                    "reason": None if success else (
                        "expected action marker was not emitted"
                        if expected_action is not None and not action_seen
                        else "expected focused control was not reached"
                        if not (before_focus_seen if expected_action is not None else after_focus_seen)
                        else "no focused control was exposed"
                    ),
                    "evidence": {
                        "screenshot": (step_root / "screenshot.png").relative_to(self.out).as_posix(),
                        "before_xml": (step_root / "before.xml").relative_to(self.out).as_posix(),
                        "xml": (step_root / "ui.xml").relative_to(self.out).as_posix(),
                        "logcat": (step_root / "logcat.txt").relative_to(self.out).as_posix(),
                    },
                }
            )
            return success

        try:
            # First prove the complete D-pad graph without mutating download state.
            restart("navigation")
            inspect("top-wifi-initial", expected_labels=("كل الشبكات", "WiFi فقط"))
            inspect("top-schedule-focus", key_code=21, expected_labels=("الجدولة",))
            inspect("top-concurrent-focus", key_code=21, expected_labels=("متزامنة",))
            inspect("row-1-cancel", key_code=20, expected_labels=("الغاء",))
            inspect("row-2-cancel", key_code=20, expected_labels=("الغاء",))
            inspect("row-2-priority", key_code=22, expected_labels=("عالية", "عادية", "منخفضة"))
            inspect("row-2-primary", key_code=22, expected_labels=("ايقاف مؤقت", "استئناف"))
            inspect("row-1-primary", key_code=19, expected_labels=("ايقاف مؤقت", "استئناف"))
            inspect("row-1-priority", key_code=21, expected_labels=("عالية", "عادية", "منخفضة"))

            # Execute each state-changing callback from a fresh deterministic page.
            restart("wifi-action")
            inspect("top-wifi-action-focus", expected_labels=("كل الشبكات", "WiFi فقط"))
            inspect("top-wifi-executes", key_code=23, expected_labels=("كل الشبكات", "WiFi فقط"), expected_action="wifi")

            restart("schedule-action")
            inspect("top-schedule-action-focus", key_code=21, expected_labels=("الجدولة",))
            inspect("top-schedule-executes", key_code=23, expected_labels=("الجدولة",), expected_action="schedule")

            restart("concurrent-action")
            inspect("top-concurrent-action-schedule", key_code=21, expected_labels=("الجدولة",))
            inspect("top-concurrent-action-focus", key_code=21, expected_labels=("متزامنة",))
            inspect("top-concurrent-executes", key_code=23, expected_labels=("متزامنة",), expected_action="concurrent")

            restart("pause-action")
            inspect("pause-row-1-primary", key_code=20, expected_labels=("ايقاف مؤقت", "استئناف"))
            inspect("row-1-pause", key_code=23, expected_labels=("ايقاف مؤقت", "استئناف"), expected_action="pause")
            inspect("row-1-resume", key_code=23, expected_labels=("ايقاف مؤقت", "استئناف"), expected_action="resume")

            restart("row-2-pause-action")
            inspect("row-2-pause-row-1-primary", key_code=20, expected_labels=("ايقاف مؤقت", "استئناف"))
            inspect("row-2-pause-row-2-primary", key_code=20, expected_labels=("ايقاف مؤقت", "استئناف"))
            inspect("row-2-pause", key_code=23, expected_labels=("ايقاف مؤقت", "استئناف"), expected_action="pause")

            restart("priority-action")
            inspect("priority-row-1-primary", key_code=20, expected_labels=("ايقاف مؤقت", "استئناف"))
            inspect("priority-row-1-focus", key_code=21, expected_labels=("عالية", "عادية", "منخفضة"))
            inspect("row-1-priority-executes", key_code=23, expected_labels=("عالية", "عادية", "منخفضة"), expected_action="priority")

            restart("delete-action")
            inspect("cancel-row-1-primary", key_code=20, expected_labels=("ايقاف مؤقت", "استئناف"))
            inspect("cancel-row-1-priority", key_code=21, expected_labels=("عالية", "عادية", "منخفضة"))
            inspect("cancel-row-1-focus", key_code=21, expected_labels=("الغاء",))
            inspect("delete-row-1-executes", key_code=23, expected_labels=("الغاء",), expected_action="delete")
        except Exception as exc:
            error = f"{type(exc).__name__}: {exc}"[-1200:]
            self.record_harness_error(f"download-actions:{orientation}", exc)
        finally:
            result = {
                "orientation": orientation,
                "page": "downloads",
                "success": error is None and all(check.get("success") for check in checks),
                "error": error,
                "checks": checks,
            }
            self.manifest["download_actions"].append(result)
            safe_write(
                audit_root / "download-actions.json",
                json.dumps(result, ensure_ascii=False, indent=2) + "\n",
            )
            self.flush_manifest()

    def run(self) -> None:
        self.setup()
        orientations = [item.strip() for item in self.args.orientations.split(",") if item.strip()]
        font_scales = [float(item.strip()) for item in self.args.font_scales.split(",") if item.strip()]
        for orientation in orientations:
            for font_scale in font_scales:
                self.set_variant(orientation, font_scale)
                for page in PAGES:
                    self.capture_case(page["id"], orientation, font_scale)
            self.set_variant(orientation, 1.0)
            self.navigation_audit(orientation)
            if self.args.is_tv:
                for page in PAGES:
                    self.focus_audit(page["id"], orientation)
                self.download_action_audit(orientation)
        self.manifest["finished_at_epoch"] = int(time.time())
        self.flush_manifest()


def _deterministic_download_action_audit(self: DeviceLab, orientation: str) -> None:
    audit_root = self.out / "focus" / orientation / "downloads-actions"
    audit_root.mkdir(parents=True, exist_ok=True)
    checks: list[dict[str, Any]] = []
    display_width, _ = oriented_dimensions(self.args.width, self.args.height, orientation)
    sequence_number = 0
    audit_error: str | None = None

    def run_check(
        check_id: str,
        target: str,
        action: str | None = None,
        *,
        restart_page: bool = True,
    ) -> bool:
        nonlocal sequence_number
        sequence_number += 1
        step_root = audit_root / f"{sequence_number:02d}-{check_id}"
        step_root.mkdir(parents=True, exist_ok=True)
        key_events: list[dict[str, Any]] = []
        reason: str | None = None
        source = "PRODUCT" if action else "QUALITY_LAB"
        precondition_established = False
        initial_node = None
        initial_target = None
        actual_target = None
        focused_before = None
        focused_after = None
        markers_before: set[tuple[str, int]] = set()
        markers_after: set[tuple[str, int]] = set()
        expected_action = action
        key_press_confirmed = False
        try:
            if restart_page:
                self.start_page("downloads", step_root / "restart")
            stable, initial_target, initial_node, initial_xml = wait_for_download_focus_stability(
                self.adb,
                step_root / "initial.xml",
                display_width,
            )
            markers_before = download_action_markers(initial_xml)
            path = plan_download_focus_path(initial_target, target, row_count=3) if stable else None
            if not stable:
                reason = (
                    "START_FOCUS_NOT_ESTABLISHED: "
                    f"expected a stable supported start target, observed {initial_target or 'unknown'}"
                )
                source = "FIXTURE"
            elif path is None:
                reason = (
                    "HARNESS_SELECTOR_FAILURE: "
                    f"initial focus {initial_target or 'unknown'} cannot reach {target}"
                )
                source = "FIXTURE"
            else:
                current_target = initial_target
                for key_name, expected_next in path:
                    focused_before = focused_node(
                        dump_xml(self.adb, step_root / f"before-{len(key_events) + 1}.xml", attempts=2)
                    )
                    self.adb.shell(["input", "keyevent", str(DOWNLOAD_KEY_CODES[key_name])], check=True)
                    reached, observed, observed_node, _ = poll_download_focus(
                        self.adb,
                        expected_next,
                        step_root / f"after-{len(key_events) + 1}.xml",
                        display_width,
                    )
                    key_events.append(
                        {
                            "key": key_name,
                            "key_code": DOWNLOAD_KEY_CODES[key_name],
                            "focused_before": current_target,
                            "expected_target": expected_next,
                            "actual_target": observed,
                            "success": reached,
                        }
                    )
                    focused_after = observed_node
                    actual_target = observed
                    current_target = observed
                    if not reached:
                        reason = (
                            "NAVIGATION_TARGET_MISMATCH: "
                            f"{key_name} expected {expected_next}, observed {observed or 'unknown'}"
                        )
                        source = "PRODUCT"
                        break
                if reason is None:
                    final_xml = dump_xml(self.adb, step_root / "target.xml", attempts=2)
                    actual_target, focused_before = download_focus_target(final_xml, display_width)
                    markers_before = download_action_markers(final_xml)
                    precondition_established = actual_target == target
                    if not precondition_established:
                        reason = f"START_FOCUS_NOT_ESTABLISHED: expected {target}, observed {actual_target}"
                        source = "FIXTURE"
            if reason is None and action is not None:
                label = str((focused_before or {}).get("text") or "")
                expected_label = {
                    "pause": "ايقاف مؤقت",
                    "resume": "استئناف",
                }.get(action)
                if expected_label and expected_label not in label:
                    label_deadline = time.monotonic() + 5.0
                    while time.monotonic() < label_deadline:
                        label_xml = dump_xml(self.adb, step_root / "pre-action-state.xml", attempts=1)
                        label_target, label_node = download_focus_target(label_xml, display_width)
                        label = str((label_node or {}).get("text") or "")
                        if label_target == target and expected_label in label:
                            focused_before = label_node
                            break
                        time.sleep(0.15)
                if expected_label and expected_label not in label:
                    reason = (
                        "UI_STATE_NOT_UPDATED: "
                        f"{target} expected label {expected_label}, observed {label or 'unknown'}"
                    )
                    source = "PRODUCT"
                else:
                    self.adb.shell(["input", "keyevent", str(DOWNLOAD_KEY_CODES["CENTER"])], check=True)
                    key_press_confirmed = True
            if reason is None and action is not None:
                deadline = time.monotonic() + 5.0
                while time.monotonic() < deadline:
                    after_xml = dump_xml(self.adb, step_root / "ui.xml", attempts=1)
                    markers_after = download_action_markers(after_xml)
                    _, focused_after = download_focus_target(after_xml, display_width)
                    new_markers = markers_after - markers_before
                    if any(marker_action == expected_action for marker_action, _ in new_markers):
                        break
                    if new_markers:
                        break
                    time.sleep(0.15)
                new_markers = markers_after - markers_before
                expected_seen = any(marker_action == expected_action for marker_action, _ in new_markers)
                wrong = sorted(
                    f"{marker_action}:{revision}"
                    for marker_action, revision in new_markers
                    if marker_action != expected_action
                )
                if wrong:
                    reason = (
                        "NAVIGATION_TARGET_MISMATCH: callback marker(s) "
                        f"{', '.join(wrong)} prove a different control received the key instead of {expected_action}"
                    )
                    source = "PRODUCT"
                elif not expected_seen:
                    reason = f"ACTION_CALLBACK_NOT_EXECUTED: {expected_action} revision did not advance"
                    source = "PRODUCT"
                else:
                    post_action_target = "row-1-cancel" if action == "delete" else target
                    focus_stable, observed_focus, focused_after, post_action_xml = wait_for_stable_focus(
                        self.adb,
                        post_action_target,
                        step_root / "post-action-focus.xml",
                        lambda xml: download_focus_target(xml, display_width),
                        timeout=5.0,
                        consecutive_reads=2,
                    )
                    markers_after |= download_action_markers(post_action_xml)
                    if not focus_stable:
                        reason = (
                            "UI_STATE_NOT_UPDATED: post-action focus expected "
                            f"{post_action_target}, observed {observed_focus or 'unknown'}"
                        )
                        source = "PRODUCT"
            elif reason is None:
                markers_after = markers_before
                focused_after = focused_before
        except LabError as exc:
            reason = f"INFRASTRUCTURE_FAILURE: {exc}"[-1200:]
            source = "INFRASTRUCTURE"
        except Exception as exc:
            reason = f"HARNESS_SELECTOR_FAILURE: {type(exc).__name__}: {exc}"[-1200:]
            source = "FIXTURE"

        success = reason is None
        if not (step_root / "ui.xml").exists():
            try:
                dump_xml(self.adb, step_root / "ui.xml", attempts=2)
            except Exception:
                pass
        try:
            capture_png(self.adb, step_root / "screenshot.png")
        except Exception:
            pass
        write_logcat_with_focus_trace(self.adb, step_root / "logcat.txt")
        checks.append(
            {
                "id": check_id,
                "success": success,
                "expected_target": target,
                "actual_target": actual_target,
                "expected_action": expected_action,
                "initial_focused_node": initial_node,
                "initial_target": initial_target,
                "focused_before": focused_before,
                "focused_after": focused_after,
                "key_events": key_events,
                "key_press_confirmed": key_press_confirmed,
                "precondition_established": precondition_established or (action is None and success),
                "precondition_failure": None if success else reason,
                "marker_revision_before": [f"{name}:{revision}" for name, revision in sorted(markers_before)],
                "marker_revision_after": [f"{name}:{revision}" for name, revision in sorted(markers_after)],
                "unexpected_markers": sorted(
                    f"{name}:{revision}"
                    for name, revision in (markers_after - markers_before)
                    if expected_action and name != expected_action
                ),
                "source": source,
                "reason": reason,
                "evidence": {
                    key: path.relative_to(self.out).as_posix()
                    for key, path in {
                        "screenshot": step_root / "screenshot.png",
                        "initial_xml": step_root / "initial.xml",
                        "before_xml": step_root / "target.xml",
                        "xml": step_root / "ui.xml",
                        "logcat": step_root / "logcat.txt",
                        "focus_trace": step_root / "focus-events.log",
                    }.items()
                    if path.is_file()
                },
            }
        )
        return success

    try:
        navigation = [
            ("top-wifi-initial", "toolbar-wifi"),
            ("top-schedule-focus", "toolbar-schedule"),
            ("top-concurrent-focus", "toolbar-concurrent"),
            ("row-1-cancel", "row-1-cancel"),
            ("row-2-cancel", "row-2-cancel"),
            ("row-2-priority", "row-2-priority"),
            ("row-2-primary", "row-2-primary"),
            ("row-1-primary", "row-1-primary"),
            ("row-1-priority", "row-1-priority"),
        ]
        for check_id, target in navigation:
            run_check(check_id, target)
        for check_id, target, action in [
            ("top-wifi-executes", "toolbar-wifi", "wifi"),
            ("top-schedule-executes", "toolbar-schedule", "schedule"),
            ("top-concurrent-executes", "toolbar-concurrent", "concurrent"),
        ]:
            run_check(check_id, target, action)
        run_check("row-1-pause", "row-1-primary", "pause")
        run_check(
            "row-1-resume",
            "row-1-primary",
            "resume",
            restart_page=False,
        )
        run_check("row-2-pause", "row-2-primary", "pause")
        run_check("row-1-priority-executes", "row-1-priority", "priority")
        run_check("delete-row-1-executes", "row-1-cancel", "delete")
    except Exception as exc:
        audit_error = f"{type(exc).__name__}: {exc}"[-1200:]
        self.record_harness_error(f"download-actions:{orientation}", exc)
    result = {
        "orientation": orientation,
        "page": "downloads",
        "success": audit_error is None and all(check.get("success") for check in checks),
        "status": "BLOCKED" if any(check.get("source") in {"FIXTURE", "QUALITY_LAB", "INFRASTRUCTURE"} and not check.get("success") for check in checks) else "FAIL" if any(not check.get("success") for check in checks) else "PASS",
        "error": audit_error,
        "checks": checks,
    }
    self.manifest["download_actions"].append(result)
    safe_write(audit_root / "download-actions.json", json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    self.flush_manifest()


DeviceLab.download_action_audit = _deterministic_download_action_audit



def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--device-id", required=True)
    parser.add_argument("--device-name", required=True)
    parser.add_argument("--family", choices=("phone", "tablet", "tv"), required=True)
    parser.add_argument("--api", type=int, required=True)
    parser.add_argument("--target", required=True)
    parser.add_argument("--arch", required=True)
    parser.add_argument("--profile", required=True)
    parser.add_argument("--width", type=int, required=True)
    parser.add_argument("--height", type=int, required=True)
    parser.add_argument("--density", type=int, required=True)
    parser.add_argument("--orientations", required=True)
    parser.add_argument("--font-scales", required=True)
    parser.add_argument("--is-tv", type=parse_bool, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--serial")
    parser.add_argument("--source-head-sha", required=True)
    parser.add_argument("--base-sha", required=True)
    parser.add_argument("--tested-ref", required=True)
    parser.add_argument("--tested-commit-sha", required=True)
    parser.add_argument("--merge-sha", required=True)
    parser.add_argument("--lab-apk-sha256", required=True)
    parser.add_argument("--workflow-run-id", required=True)
    parser.add_argument("--workflow-run-attempt", required=True)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    lab = DeviceLab(args)
    try:
        lab.run()
    except Exception as exc:
        lab.record_harness_error("device-setup-or-run", exc)
        try:
            safe_write(
                lab.out / "device-failure.logcat.txt",
                command_text(
                    lab.adb,
                    ["logcat", "-d", "-v", "threadtime"],
                    shell=False,
                    timeout=90,
                ),
            )
        except Exception as log_error:
            lab.record_harness_error("device-failure-logcat", log_error)
    try:
        from analyze import analyze_run

        summary = analyze_run(lab.out)
    except Exception as exc:
        lab.record_harness_error("analysis", exc)
        print(f"Compatibility Lab analysis failed: {exc}", file=sys.stderr)
        return 2

    print(
        f"{summary['overall_status']}: {summary['device']['name']} — "
        f"{summary['case_count']} captures, {summary['critical_count']} critical, "
        f"{summary['warning_count']} warnings, "
        f"{summary['infrastructure_error_count']} infrastructure errors"
    )
    if summary["infrastructure_error_count"]:
        return 2
    if summary["critical_count"]:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
