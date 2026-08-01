#!/usr/bin/env python3
"""Runtime overrides that replace static Downloads focus assumptions with observed geometry."""
from __future__ import annotations

from collections import deque
import json
from pathlib import Path
import time
from typing import Any
import xml.etree.ElementTree as ET

DIRECTION_CODES = {"UP": 19, "DOWN": 20, "LEFT": 21, "RIGHT": 22, "CENTER": 23}
ACTION_TARGETS = {
    "wifi": "toolbar-wifi",
    "schedule": "toolbar-schedule",
    "concurrent": "toolbar-concurrent",
    "pause": "row-1-primary",
    "resume": "row-1-primary",
    "row-2-pause": "row-2-primary",
    "priority": "row-1-priority",
    "delete": "row-1-cancel",
}


def _text(node: ET.Element) -> str:
    values = [node.attrib.get("text", ""), node.attrib.get("content-desc", "")]
    return " ".join(value.strip() for value in values if value and value.strip())


def _bounds(node: ET.Element) -> tuple[int, int, int, int] | None:
    raw = node.attrib.get("bounds", "")
    try:
        left, right = raw.split("][")
        x1, y1 = (int(value) for value in left.lstrip("[").split(","))
        x2, y2 = (int(value) for value in right.rstrip("]").split(","))
    except (ValueError, TypeError):
        return None
    return (x1, y1, x2, y2) if x2 > x1 and y2 > y1 else None


def _slot(label: str) -> str | None:
    if "WiFi فقط" in label or "كل الشبكات" in label:
        return "toolbar-wifi"
    if "الجدولة" in label:
        return "toolbar-schedule"
    if "متزامنة" in label:
        return "toolbar-concurrent"
    if any(value in label for value in ("ايقاف مؤقت", "استئناف", "تشغيل", "اعادة المحاولة")):
        return "primary"
    if any(value in label for value in ("عالية", "عادية", "منخفضة")):
        return "priority"
    if "الغاء" in label or "حذف" in label:
        return "cancel"
    return None


def extract_layout(xml_bytes: bytes) -> dict[str, tuple[int, int, int, int]]:
    """Extract actual visible focus targets and assign card rows by physical Y."""
    root = ET.fromstring(xml_bytes)
    raw: list[tuple[str, tuple[int, int, int, int]]] = []
    for node in root.iter("node"):
        if node.attrib.get("focusable") != "true" and node.attrib.get("clickable") != "true":
            continue
        slot = _slot(_text(node))
        bounds = _bounds(node)
        if slot and bounds:
            raw.append((slot, bounds))

    result: dict[str, tuple[int, int, int, int]] = {}
    row_centers = sorted({(b[1] + b[3]) // 2 for slot, b in raw if slot in {"primary", "priority", "cancel"}})
    merged: list[int] = []
    for center in row_centers:
        if not merged or abs(center - merged[-1]) > 20:
            merged.append(center)
        else:
            merged[-1] = (merged[-1] + center) // 2
    for slot, bounds in raw:
        target = slot
        if slot in {"primary", "priority", "cancel"}:
            center = (bounds[1] + bounds[3]) // 2
            if not merged:
                continue
            row = min(range(len(merged)), key=lambda index: abs(merged[index] - center)) + 1
            target = f"row-{row}-{slot}"
        previous = result.get(target)
        if previous is None or (bounds[2] - bounds[0]) * (bounds[3] - bounds[1]) > (previous[2] - previous[0]) * (previous[3] - previous[1]):
            result[target] = bounds
    return result


def _center(bounds: tuple[int, int, int, int]) -> tuple[float, float]:
    return ((bounds[0] + bounds[2]) / 2.0, (bounds[1] + bounds[3]) / 2.0)


def build_spatial_graph(layout: dict[str, tuple[int, int, int, int]]) -> dict[str, dict[str, str]]:
    """Build a physical D-pad graph from observed target rectangles, not semantic columns."""
    graph: dict[str, dict[str, str]] = {target: {} for target in layout}
    for source, source_bounds in layout.items():
        sx, sy = _center(source_bounds)
        for direction in ("UP", "DOWN", "LEFT", "RIGHT"):
            candidates: list[tuple[tuple[float, float, float], str]] = []
            for target, target_bounds in layout.items():
                if target == source:
                    continue
                tx, ty = _center(target_bounds)
                dx, dy = tx - sx, ty - sy
                if direction == "UP" and dy >= -1:
                    continue
                if direction == "DOWN" and dy <= 1:
                    continue
                if direction == "LEFT" and dx >= -1:
                    continue
                if direction == "RIGHT" and dx <= 1:
                    continue
                major = abs(dy) if direction in {"UP", "DOWN"} else abs(dx)
                minor = abs(dx) if direction in {"UP", "DOWN"} else abs(dy)
                source_half = (source_bounds[2] - source_bounds[0]) / 2 if direction in {"UP", "DOWN"} else (source_bounds[3] - source_bounds[1]) / 2
                target_half = (target_bounds[2] - target_bounds[0]) / 2 if direction in {"UP", "DOWN"} else (target_bounds[3] - target_bounds[1]) / 2
                in_beam = minor <= source_half + target_half
                candidates.append(((0.0 if in_beam else 1.0, major, minor), target))
            if candidates:
                graph[source][direction] = min(candidates)[1]
    return graph


def shortest_path(graph: dict[str, dict[str, str]], start: str, target: str) -> list[tuple[str, str]] | None:
    if start == target:
        return []
    queue: deque[tuple[str, list[tuple[str, str]]]] = deque([(start, [])])
    visited = {start}
    while queue:
        node, path = queue.popleft()
        for key, neighbor in sorted(graph.get(node, {}).items()):
            if neighbor in visited:
                continue
            next_path = [*path, (key, neighbor)]
            if neighbor == target:
                return next_path
            visited.add(neighbor)
            queue.append((neighbor, next_path))
    return None


def _wait_any_stable(core: Any, lab: Any, path: Path, width: int, timeout: float = 4.0):
    return core.wait_for_supported_download_focus(
        lab.adb,
        path,
        width,
        timeout=timeout,
        consecutive_reads=2,
    )


def install(core: Any) -> None:
    """Install deterministic geometry-derived Downloads action auditing into runtime core."""

    def download_action_audit(self: Any, orientation: str) -> None:
        audit_root = self.out / "focus" / orientation / "downloads-actions"
        audit_root.mkdir(parents=True, exist_ok=True)
        display_width, _ = core.oriented_dimensions(self.args.width, self.args.height, orientation)
        checks: list[dict[str, Any]] = []
        sequence = 0
        audit_error: str | None = None

        scenarios = [
            ("top-wifi-executes", "toolbar-wifi", "wifi"),
            ("top-schedule-executes", "toolbar-schedule", "schedule"),
            ("top-concurrent-executes", "toolbar-concurrent", "concurrent"),
            ("row-1-pause", "row-1-primary", "pause"),
            ("row-1-resume", "row-1-primary", "resume"),
            ("row-2-pause", "row-2-primary", "pause"),
            ("row-1-priority-executes", "row-1-priority", "priority"),
            ("delete-row-1-executes", "row-1-cancel", "delete"),
        ]

        for check_id, target, action in scenarios:
            sequence += 1
            step_root = audit_root / f"{sequence:02d}-{check_id}"
            step_root.mkdir(parents=True, exist_ok=True)
            reason: str | None = None
            source = "PRODUCT"
            precondition = False
            key_confirmed = False
            initial_target = None
            actual_target = None
            focused_before = None
            focused_after = None
            markers_before: set[tuple[str, int]] = set()
            markers_after: set[tuple[str, int]] = set()
            key_events: list[dict[str, Any]] = []
            try:
                self.start_page("downloads", step_root / "restart")
                stable, initial_target, initial_node, initial_xml = _wait_any_stable(core, self, step_root / "initial.xml", display_width)
                if not stable or initial_target is None:
                    reason = f"START_FOCUS_NOT_ESTABLISHED: expected a stable supported target, observed {initial_target or 'unknown'}"
                    source = "FIXTURE"
                else:
                    layout = extract_layout(initial_xml)
                    graph = build_spatial_graph(layout)
                    core.safe_write(step_root / "observed-layout.json", json.dumps({"layout": layout, "graph": graph}, ensure_ascii=False, indent=2) + "\n")
                    path = shortest_path(graph, initial_target, target)
                    if path is None:
                        reason = f"LAB_FOCUS_GRAPH_MODEL_MISMATCH: target {target} is not reachable from {initial_target} in the observed hierarchy"
                        source = "QUALITY_LAB"
                    else:
                        current = initial_target
                        for index, (key, expected_next) in enumerate(path, start=1):
                            self.adb.shell(["input", "keyevent", str(DIRECTION_CODES[key])], check=True)
                            reached, observed, node, _xml = core.wait_for_stable_focus(
                                self.adb,
                                expected_next,
                                step_root / f"path-{index}.xml",
                                lambda data: core.download_focus_target(data, display_width),
                                timeout=4.0,
                                consecutive_reads=2,
                            )
                            key_events.append({
                                "key": key,
                                "key_code": DIRECTION_CODES[key],
                                "focused_before": current,
                                "expected_target": expected_next,
                                "actual_target": observed,
                                "success": reached,
                            })
                            current = observed
                            focused_after = node
                            if not reached:
                                reason = f"LAB_FOCUS_GRAPH_MODEL_MISMATCH: {key} from {key_events[-1]['focused_before']} predicted {expected_next}, observed {observed or 'unknown'}"
                                source = "QUALITY_LAB"
                                break
                        if reason is None:
                            target_xml = core.dump_xml(self.adb, step_root / "target.xml", attempts=2)
                            actual_target, focused_before = core.download_focus_target(target_xml, display_width)
                            markers_before = core.download_action_markers(target_xml)
                            precondition = actual_target == target
                            if not precondition:
                                reason = f"START_FOCUS_NOT_ESTABLISHED: expected {target}, observed {actual_target or 'unknown'}"
                                source = "FIXTURE"
                if reason is None:
                    label = str((focused_before or {}).get("text") or "")
                    expected_label = {"pause": "ايقاف مؤقت", "resume": "استئناف"}.get(action)
                    if expected_label and expected_label not in label:
                        reason = f"UI_STATE_NOT_UPDATED: {target} expected label {expected_label}, observed {label or 'unknown'}"
                    else:
                        self.adb.shell(["input", "keyevent", str(DIRECTION_CODES["CENTER"])], check=True)
                        key_confirmed = True
                if reason is None:
                    deadline = time.monotonic() + 5.0
                    while time.monotonic() < deadline:
                        after_xml = core.dump_xml(self.adb, step_root / "ui.xml", attempts=1)
                        markers_after = core.download_action_markers(after_xml)
                        _, focused_after = core.download_focus_target(after_xml, display_width)
                        if markers_after - markers_before:
                            break
                        time.sleep(0.15)
                    new_markers = markers_after - markers_before
                    wrong = sorted(f"{name}:{revision}" for name, revision in new_markers if name != action)
                    expected_seen = any(name == action for name, _ in new_markers)
                    if wrong:
                        reason = f"NAVIGATION_TARGET_MISMATCH: verified target {target}, but callback marker(s) {', '.join(wrong)} were emitted instead of {action}"
                    elif not expected_seen:
                        reason = f"ACTION_CALLBACK_NOT_EXECUTED: {action} revision did not advance after a verified target and confirmed CENTER key"
            except core.LabError as exc:
                reason = f"INFRASTRUCTURE_FAILURE: {exc}"[-1200:]
                source = "INFRASTRUCTURE"
            except Exception as exc:
                reason = f"HARNESS_SELECTOR_FAILURE: {type(exc).__name__}: {exc}"[-1200:]
                source = "FIXTURE"

            try:
                if not (step_root / "ui.xml").exists():
                    core.dump_xml(self.adb, step_root / "ui.xml", attempts=2)
                core.capture_png(self.adb, step_root / "screenshot.png")
                core.write_logcat_with_focus_trace(self.adb, step_root / "logcat.txt")
            except Exception:
                pass
            checks.append({
                "id": check_id,
                "success": reason is None,
                "expected_target": target,
                "actual_target": actual_target,
                "expected_action": action,
                "initial_target": initial_target,
                "focused_before": focused_before,
                "focused_after": focused_after,
                "key_events": key_events,
                "key_press_confirmed": key_confirmed,
                "precondition_established": precondition,
                "precondition_failure": reason,
                "marker_revision_before": [f"{name}:{revision}" for name, revision in sorted(markers_before)],
                "marker_revision_after": [f"{name}:{revision}" for name, revision in sorted(markers_after)],
                "unexpected_markers": sorted(f"{name}:{revision}" for name, revision in markers_after - markers_before if name != action),
                "source": source,
                "reason": reason,
                "evidence": {
                    key: path.relative_to(self.out).as_posix()
                    for key, path in {
                        "screenshot": step_root / "screenshot.png",
                        "initial_xml": step_root / "initial.xml",
                        "layout": step_root / "observed-layout.json",
                        "before_xml": step_root / "target.xml",
                        "xml": step_root / "ui.xml",
                        "logcat": step_root / "logcat.txt",
                        "focus_trace": step_root / "focus-events.log",
                    }.items()
                    if path.is_file()
                },
            })

        result = {
            "orientation": orientation,
            "page": "downloads",
            "success": audit_error is None and all(check.get("success") for check in checks),
            "status": "BLOCKED" if any(not check.get("success") and check.get("source") in {"FIXTURE", "QUALITY_LAB", "INFRASTRUCTURE"} for check in checks) else "FAIL" if any(not check.get("success") for check in checks) else "PASS",
            "error": audit_error,
            "checks": checks,
        }
        self.manifest["download_actions"].append(result)
        core.safe_write(audit_root / "download-actions.json", json.dumps(result, ensure_ascii=False, indent=2) + "\n")
        self.flush_manifest()

    core.DeviceLab.download_action_audit = download_action_audit
