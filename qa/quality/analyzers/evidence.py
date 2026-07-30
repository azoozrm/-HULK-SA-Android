from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import struct
from typing import Any
import xml.etree.ElementTree as ET


APP_PACKAGES = {"sa.hulksa.player", "sa.hulksa.player.dev"}
SYSTEM_PACKAGES = {
    "android",
    "com.android.systemui",
    "com.google.android.permissioncontroller",
    "com.android.permissioncontroller",
    "com.google.android.tvlauncher",
}
BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")
CRASH = re.compile(
    r"FATAL EXCEPTION|ANR in\s+sa\.hulksa\.player|am_anr.*sa\.hulksa\.player",
    re.IGNORECASE,
)


class EvidenceError(ValueError):
    pass


def png_geometry(path: Path) -> tuple[int, int]:
    data = path.read_bytes()[:24]
    if len(data) != 24 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise EvidenceError(f"{path}: invalid PNG header")
    width, height = struct.unpack(">II", data[16:24])
    if width <= 0 or height <= 0:
        raise EvidenceError(f"{path}: invalid PNG geometry")
    return width, height


def foreground_package(window_dump: str) -> str | None:
    patterns = (
        r"mCurrentFocus=.*?\s([A-Za-z0-9_.]+)/",
        r"mFocusedApp=.*?\s([A-Za-z0-9_.]+)/",
        r"topResumedActivity=.*?\s([A-Za-z0-9_.]+)/",
    )
    for pattern in patterns:
        match = re.search(pattern, window_dump)
        if match:
            return match.group(1)
    return None


def classify_foreground(window_dump: str) -> str:
    package = foreground_package(window_dump)
    if package in APP_PACKAGES:
        return "app"
    if package in SYSTEM_PACKAGES or package:
        return "non-app"
    return "unknown"


def parse_bounds(raw: str) -> tuple[int, int, int, int] | None:
    match = BOUNDS.fullmatch(raw)
    return tuple(map(int, match.groups())) if match else None  # type: ignore[return-value]


def _area(bounds: tuple[int, int, int, int]) -> int:
    return max(0, bounds[2] - bounds[0]) * max(0, bounds[3] - bounds[1])


def overlap_ratio(
    first: tuple[int, int, int, int],
    second: tuple[int, int, int, int],
) -> float:
    intersection = max(0, min(first[2], second[2]) - max(first[0], second[0])) * max(
        0, min(first[3], second[3]) - max(first[1], second[1])
    )
    minimum = min(_area(first), _area(second))
    return intersection / minimum if minimum else 0.0


def intentional_lazy_partial(
    item: tuple[int, int, int, int],
    viewport: tuple[int, int, int, int],
    *,
    axis: str,
    minimum_visible_ratio: float = 0.15,
) -> bool:
    """Allow an edge teaser only when it intersects a declared lazy-list viewport."""
    if axis not in {"horizontal", "vertical"}:
        raise ValueError("axis must be horizontal or vertical")
    intersection_width = max(0, min(item[2], viewport[2]) - max(item[0], viewport[0]))
    intersection_height = max(0, min(item[3], viewport[3]) - max(item[1], viewport[1]))
    visible = intersection_width * intersection_height
    area = _area(item)
    if not area or visible / area < minimum_visible_ratio:
        return False
    clipped = (
        item[0] < viewport[0] or item[2] > viewport[2]
        if axis == "horizontal"
        else item[1] < viewport[1] or item[3] > viewport[3]
    )
    orthogonal_inside = (
        item[1] >= viewport[1] and item[3] <= viewport[3]
        if axis == "horizontal"
        else item[0] >= viewport[0] and item[2] <= viewport[2]
    )
    return clipped and orthogonal_inside


def xml_interactive_overlaps(xml_path: Path) -> list[dict[str, Any]]:
    root = ET.parse(xml_path).getroot()
    parents = {child: parent for parent in root.iter() for child in parent}
    nodes = [
        node
        for node in root.iter("node")
        if any(node.attrib.get(key) == "true" for key in ("clickable", "focusable", "scrollable"))
        and parse_bounds(node.attrib.get("bounds", ""))
    ]

    def ancestor(first: ET.Element, second: ET.Element) -> bool:
        current = parents.get(second)
        while current is not None:
            if current is first:
                return True
            current = parents.get(current)
        return False

    findings: list[dict[str, Any]] = []
    for index, first in enumerate(nodes):
        first_bounds = parse_bounds(first.attrib["bounds"])
        assert first_bounds
        for second in nodes[index + 1 :]:
            second_bounds = parse_bounds(second.attrib["bounds"])
            assert second_bounds
            if ancestor(first, second) or ancestor(second, first):
                continue
            ratio = overlap_ratio(first_bounds, second_bounds)
            if ratio >= 0.85:
                findings.append(
                    {
                        "first": first.attrib.get("text") or first.attrib.get("content-desc", ""),
                        "second": second.attrib.get("text") or second.attrib.get("content-desc", ""),
                        "ratio": round(ratio, 4),
                    }
                )
    return findings


def launcher_or_system_contamination(xml_path: Path) -> bool:
    root = ET.parse(xml_path).getroot()
    packages = {
        node.attrib.get("package", "")
        for node in root.iter("node")
        if node.attrib.get("package")
    }
    return bool(packages) and not bool(packages & APP_PACKAGES)


def validate_case_evidence(
    case_root: Path,
    *,
    expected_width: int | None = None,
    expected_height: int | None = None,
) -> list[str]:
    errors: list[str] = []
    required = {
        "screenshot.png": "screenshot",
        "hierarchy.xml": "UI hierarchy",
        "logcat.txt": "logcat",
        "window.txt": "window dump",
    }
    for filename, label in required.items():
        if not (case_root / filename).is_file():
            errors.append(f"missing {label}: {filename}")
    screenshot = case_root / "screenshot.png"
    if screenshot.is_file():
        try:
            geometry = png_geometry(screenshot)
            if expected_width and expected_height and geometry != (
                expected_width,
                expected_height,
            ):
                errors.append(
                    f"wrong PNG geometry: {geometry[0]}x{geometry[1]} "
                    f"expected {expected_width}x{expected_height}"
                )
        except EvidenceError as exc:
            errors.append(str(exc))
    hierarchy = case_root / "hierarchy.xml"
    if hierarchy.is_file():
        try:
            ET.parse(hierarchy)
        except ET.ParseError as exc:
            errors.append(f"malformed UI hierarchy: {exc}")
    window = case_root / "window.txt"
    if window.is_file():
        foreground = classify_foreground(window.read_text(encoding="utf-8", errors="replace"))
        if foreground != "app":
            errors.append(f"foreground package is {foreground}, not the application")
    return errors


def has_crash_or_anr(logcat: str) -> bool:
    return bool(CRASH.search(logcat))


def validate_navigation_trace(trace: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    nodes = set(trace.get("nodes", []))
    start = trace.get("start")
    expected = set(trace.get("expected_reachable", []))
    if not start or start not in nodes:
        return ["missing or invalid start node"]
    graph: dict[str, set[str]] = {node: set() for node in nodes}
    for edge in trace.get("edges", []):
        source, target = edge.get("from"), edge.get("to")
        if source not in nodes or target not in nodes:
            errors.append(f"edge references unknown node: {source}->{target}")
        else:
            graph[source].add(target)
    visited: set[str] = set()
    pending = [start]
    while pending:
        node = pending.pop()
        if node in visited:
            continue
        visited.add(node)
        pending.extend(graph[node] - visited)
    missing = sorted(expected - visited)
    if missing:
        errors.append(f"unreachable nodes: {missing}")
    return errors


def validate_focus_trace(trace: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    events = trace.get("events", [])
    seen = {event.get("node") for event in events if event.get("visible") is True}
    expected = set(trace.get("expected_nodes", []))
    if expected - seen:
        errors.append(f"unreachable focus nodes: {sorted(expected - seen)}")
    if any(event.get("node") in (None, "") for event in events):
        errors.append("focus lost")
    recent = [event.get("node") for event in events[-8:]]
    if len(recent) == 8 and len(set(recent)) == 2 and recent[:2] * 4 == recent:
        errors.append("two-node focus loop")
    return errors


def validate_download_evidence(data: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    samples = data.get("samples", [])
    bytes_values = [int(sample.get("bytes", 0)) for sample in samples]
    part_sizes = [int(sample.get("part_size", 0)) for sample in samples]
    if not samples:
        return ["missing download samples"]
    if max(bytes_values, default=0) <= 0:
        errors.append("transferred bytes never became positive")
    if max(part_sizes, default=0) <= 0:
        errors.append("partial file never grew")
    if any(current < previous for previous, current in zip(bytes_values, bytes_values[1:])):
        errors.append("download byte counter regressed")
    if data.get("resumed"):
        requested = int(data.get("resume_requested_offset", -1))
        observed = int(data.get("resume_observed_offset", -2))
        if requested < 0 or requested != observed:
            errors.append("resume offset mismatch")
    final = data.get("final")
    if final:
        expected_size = int(final.get("expected_size", -1))
        actual_size = int(final.get("actual_size", -2))
        if expected_size < 0 or expected_size != actual_size:
            errors.append("final size mismatch")
        expected_sha = final.get("expected_sha256")
        actual_sha = final.get("actual_sha256")
        if not expected_sha or expected_sha != actual_sha:
            errors.append("final checksum mismatch")
    writers = data.get("part_writers", [])
    if len(set(writers)) > 1:
        errors.append("multiple workers wrote the same partial file")
    return errors


def classify_retry(first_attempt: dict[str, Any], retry: dict[str, Any]) -> dict[str, Any]:
    first_kind = first_attempt.get("type")
    allowed = first_kind in {"Infrastructure", "Flaky"}
    return {
        "retry_allowed": allowed,
        "first_attempt_preserved": True,
        "first_status": first_attempt.get("status", "UNKNOWN"),
        "retry_status": retry.get("status", "UNKNOWN"),
        "effective_status": (
            retry.get("status", "UNKNOWN")
            if allowed
            else first_attempt.get("status", "UNKNOWN")
        ),
    }


def file_sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()
