#!/usr/bin/env python3
"""Analyze one device run and generate JSON, Markdown, HTML and JUnit reports."""

from __future__ import annotations

from collections import defaultdict
from html import escape
import json
from pathlib import Path
import re
import sys
from typing import Any, Iterable
import xml.etree.ElementTree as ET

from PIL import Image, ImageStat


PACKAGE = "sa.hulksa.player.dev"
BOUNDS_RE = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")
CRASH_RE = re.compile(
    r"FATAL EXCEPTION|ANR in\s+sa\.hulksa\.player|am_anr.*sa\.hulksa\.player|"
    r"Process:\s*sa\.hulksa\.player(?:\.dev)?\b[^\n]*(?:Exception|Error)",
    re.IGNORECASE,
)
RENDER_RE = re.compile(
    r"(?:OpenGLRenderer|HWUI|Skia|RenderThread).{0,120}(?:fatal|crash|out of memory)",
    re.IGNORECASE,
)
JANK_RE = re.compile(r"Janky frames:\s*(\d+)\s*\(([\d.]+)%\)")
TOTAL_PSS_RE = re.compile(r"TOTAL PSS:\s*([\d,]+)")


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def parse_bounds(raw: str) -> tuple[int, int, int, int] | None:
    match = BOUNDS_RE.fullmatch(raw)
    if not match:
        return None
    return tuple(map(int, match.groups()))  # type: ignore[return-value]


def relative_files(root: Path, files: dict[str, str]) -> dict[str, str]:
    return {
        key: value
        for key, value in files.items()
        if (root / value).is_file()
    }


def finding(
    severity: str,
    code: str,
    message: str,
    *,
    case_id: str | None = None,
    page: str | None = None,
    evidence: dict[str, str] | None = None,
) -> dict[str, Any]:
    return {
        "severity": severity,
        "code": code,
        "message": message,
        "case_id": case_id,
        "page": page,
        "evidence": evidence or {},
    }


def app_nodes(root: ET.Element) -> Iterable[ET.Element]:
    for node in root.iter("node"):
        package = node.attrib.get("package", "")
        if package and package != PACKAGE:
            continue
        if node.attrib.get("visible-to-user", "true") == "false":
            continue
        yield node


def analyze_xml(
    xml_path: Path,
    width: int,
    height: int,
    density: int,
    font_scale: float,
    is_tv: bool,
) -> dict[str, Any]:
    tree = ET.parse(xml_path)
    nodes = list(app_nodes(tree.getroot()))
    out_of_bounds: list[dict[str, Any]] = []
    zero_sized: list[dict[str, Any]] = []
    edge_text: list[dict[str, Any]] = []
    unsafe_tv_text: list[dict[str, Any]] = []
    undersized_text: list[dict[str, Any]] = []
    interactive: list[tuple[tuple[int, int, int, int], str]] = []
    focused: list[dict[str, Any]] = []
    text_count = 0
    safe_x = max(2, int(width * 0.025))
    safe_y = max(2, int(height * 0.025))
    minimum_text_height = 8.0 * density / 160.0 * font_scale * 0.70

    for node in nodes:
        bounds = parse_bounds(node.attrib.get("bounds", ""))
        if not bounds:
            continue
        x1, y1, x2, y2 = bounds
        raw_text = (node.attrib.get("text", "") or "").strip()
        description = (node.attrib.get("content-desc", "") or "").strip()
        label = raw_text or description
        is_interactive = any(
            node.attrib.get(key) == "true"
            for key in ("clickable", "focusable", "scrollable", "checkable")
        )
        meaningful = bool(label) or is_interactive
        if not meaningful:
            continue
        if x2 <= x1 or y2 <= y1:
            zero_sized.append({"label": label, "bounds": list(bounds)})
            continue
        if x1 < -1 or y1 < -1 or x2 > width + 1 or y2 > height + 1:
            out_of_bounds.append({"label": label, "bounds": list(bounds)})
        if raw_text:
            text_count += 1
            if x1 <= 0 or y1 <= 0 or x2 >= width or y2 >= height:
                edge_text.append({"text": raw_text, "bounds": list(bounds)})
            if is_tv and (
                x1 < safe_x or y1 < safe_y or x2 > width - safe_x or y2 > height - safe_y
            ):
                unsafe_tv_text.append({"text": raw_text, "bounds": list(bounds)})
            if (y2 - y1) < minimum_text_height:
                undersized_text.append(
                    {
                        "text": raw_text,
                        "bounds": list(bounds),
                        "minimum_height_px": round(minimum_text_height, 1),
                    }
                )
        if is_interactive:
            interactive.append((bounds, label))
        if node.attrib.get("focused") == "true":
            focused.append({"label": label, "bounds": list(bounds)})

    overlaps: list[dict[str, Any]] = []
    for index, (first, first_label) in enumerate(interactive):
        ax1, ay1, ax2, ay2 = first
        area_a = (ax2 - ax1) * (ay2 - ay1)
        if area_a <= 0:
            continue
        for second, second_label in interactive[index + 1 :]:
            bx1, by1, bx2, by2 = second
            area_b = (bx2 - bx1) * (by2 - by1)
            if area_b <= 0:
                continue
            contains = (
                (ax1 <= bx1 and ay1 <= by1 and ax2 >= bx2 and ay2 >= by2)
                or (bx1 <= ax1 and by1 <= ay1 and bx2 >= ax2 and by2 >= ay2)
            )
            if contains:
                continue
            intersection = max(0, min(ax2, bx2) - max(ax1, bx1)) * max(
                0, min(ay2, by2) - max(ay1, by1)
            )
            if intersection / min(area_a, area_b) >= 0.85:
                overlaps.append(
                    {
                        "first": first_label,
                        "second": second_label,
                        "first_bounds": list(first),
                        "second_bounds": list(second),
                    }
                )
                if len(overlaps) >= 20:
                    break
        if len(overlaps) >= 20:
            break

    return {
        "node_count": len(nodes),
        "text_count": text_count,
        "interactive_count": len(interactive),
        "focused": focused,
        "out_of_bounds": out_of_bounds,
        "zero_sized": zero_sized,
        "edge_text": edge_text,
        "unsafe_tv_text": unsafe_tv_text,
        "undersized_text": undersized_text,
        "interactive_overlaps": overlaps,
    }


def analyze_image(path: Path) -> dict[str, Any]:
    with Image.open(path) as source:
        image = source.convert("RGB")
        width, height = image.size
        reduced = image.resize((96, 96))
        stat = ImageStat.Stat(reduced)
        stddev = sum(stat.stddev) / 3.0
        grayscale = reduced.convert("L")
        dark_pixels = sum(grayscale.histogram()[:6])
        dark_ratio = dark_pixels / (96 * 96)
        return {
            "width": width,
            "height": height,
            "stddev": round(stddev, 2),
            "dark_ratio": round(dark_ratio, 4),
            "blank": stddev < 4.0 or dark_ratio > 0.995,
        }


def android_error_dialog_title(path: Path) -> str | None:
    root = ET.parse(path).getroot()
    title = ""
    has_error_action = False
    for node in root.iter("node"):
        resource_id = node.attrib.get("resource-id", "")
        if resource_id == "android:id/alertTitle":
            title = node.attrib.get("text", "").strip()
        elif resource_id in {"android:id/aerr_close", "android:id/aerr_wait"}:
            has_error_action = True
    return title if title and has_error_action else None


def analyze_performance(
    root: Path,
    case: dict[str, Any],
) -> dict[str, Any]:
    files = case.get("files", {})
    result: dict[str, Any] = {
        "start_metrics_ms": case.get("start_metrics_ms", {}),
        "janky_frames": None,
        "janky_percent": None,
        "total_pss_kb": None,
    }
    gfx = root / files.get("gfxinfo", "")
    if gfx.is_file():
        match = JANK_RE.search(gfx.read_text(encoding="utf-8", errors="ignore"))
        if match:
            result["janky_frames"] = int(match.group(1))
            result["janky_percent"] = float(match.group(2))
    mem = root / files.get("meminfo", "")
    if mem.is_file():
        match = TOTAL_PSS_RE.search(mem.read_text(encoding="utf-8", errors="ignore"))
        if match:
            result["total_pss_kb"] = int(match.group(1).replace(",", ""))
    return result


def expected_dimensions(device: dict[str, Any], orientation: str) -> tuple[int, int]:
    width = int(device["requested_width"])
    height = int(device["requested_height"])
    if orientation == "landscape" and width < height:
        return height, width
    if orientation == "portrait" and width > height:
        return height, width
    return width, height


def add_case_findings(
    root: Path,
    device: dict[str, Any],
    case: dict[str, Any],
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    case_id = case["id"]
    page = case["page"]
    files = relative_files(root, case.get("files", {}))
    result: dict[str, Any] = {
        "id": case_id,
        "page": page,
        "orientation": case["orientation"],
        "font_scale": case["font_scale"],
        "marker_found": bool(case.get("marker_found")),
        "files": files,
        "image": None,
        "ui": None,
        "performance": analyze_performance(root, case),
        "status": "PASS",
    }
    findings: list[dict[str, Any]] = []
    evidence = {
        key: value
        for key, value in files.items()
        if key in {"screenshot", "xml", "logcat", "crash_log", "system_events"}
    }

    if case.get("capture_error"):
        findings.append(
            finding(
                "infrastructure",
                "capture_error",
                f"{case_id}: {case['capture_error']}",
                case_id=case_id,
                page=page,
                evidence=evidence,
            )
        )
    required_files = {"screenshot", "xml", "logcat"}
    missing = sorted(required_files - files.keys())
    if missing:
        findings.append(
            finding(
                "infrastructure",
                "missing_artifacts",
                f"{case_id}: missing {', '.join(missing)}",
                case_id=case_id,
                page=page,
                evidence=evidence,
            )
        )
        result["status"] = "BLOCKED"
        return result, findings

    try:
        image = analyze_image(root / files["screenshot"])
        result["image"] = image
        expected = expected_dimensions(device, case["orientation"])
        if (image["width"], image["height"]) != expected:
            findings.append(
                finding(
                    "infrastructure",
                    "display_geometry_mismatch",
                    f"{case_id}: captured {image['width']}×{image['height']}, expected "
                    f"{expected[0]}×{expected[1]}",
                    case_id=case_id,
                    page=page,
                    evidence=evidence,
                )
            )
        if image["blank"]:
            findings.append(
                finding(
                    "critical",
                    "blank_render",
                    f"{case_id}: screenshot appears blank "
                    f"(stddev={image['stddev']}, dark={image['dark_ratio']:.1%})",
                    case_id=case_id,
                    page=page,
                    evidence=evidence,
                )
            )
    except Exception as exc:
        findings.append(
            finding(
                "infrastructure",
                "image_decode_error",
                f"{case_id}: screenshot cannot be decoded: {exc}",
                case_id=case_id,
                page=page,
                evidence=evidence,
            )
        )

    try:
        dialog_title = android_error_dialog_title(root / files["xml"])
    except Exception:
        dialog_title = None
    if dialog_title:
        is_app_dialog = "hulk" in dialog_title.casefold()
        findings.append(
            finding(
                "critical" if is_app_dialog else "infrastructure",
                "app_error_dialog" if is_app_dialog else "external_system_error_dialog",
                f"{case_id}: Android displayed {dialog_title!r}",
                case_id=case_id,
                page=page,
                evidence=evidence,
            )
        )
        result["status"] = "FAIL" if is_app_dialog else "BLOCKED"
        return result, findings

    if not case.get("marker_found"):
        findings.append(
            finding(
                "critical",
                "page_marker_missing",
                f"{case_id}: expected authenticated page marker {case.get('marker')!r} was not observed",
                case_id=case_id,
                page=page,
                evidence=evidence,
            )
        )

    image = result.get("image")
    if image:
        try:
            ui = analyze_xml(
                root / files["xml"],
                image["width"],
                image["height"],
                int(device["requested_density"]),
                float(case["font_scale"]),
                bool(device["is_tv"]),
            )
            result["ui"] = ui
            if ui["node_count"] == 0:
                findings.append(
                    finding(
                        "critical",
                        "empty_hierarchy",
                        f"{case_id}: no application nodes were exposed",
                        case_id=case_id,
                        page=page,
                        evidence=evidence,
                    )
                )
            if ui["out_of_bounds"]:
                findings.append(
                    finding(
                        "critical",
                        "out_of_bounds",
                        f"{case_id}: {len(ui['out_of_bounds'])} visible elements exceed the display",
                        case_id=case_id,
                        page=page,
                        evidence=evidence,
                    )
                )
            if ui["zero_sized"]:
                findings.append(
                    finding(
                        "warning",
                        "zero_sized_nodes",
                        f"{case_id}: {len(ui['zero_sized'])} visible/actionable elements have collapsed bounds",
                        case_id=case_id,
                        page=page,
                        evidence=evidence,
                    )
                )
            if ui["interactive_overlaps"]:
                findings.append(
                    finding(
                        "warning",
                        "interactive_overlap",
                        f"{case_id}: {len(ui['interactive_overlaps'])} actionable elements overlap heavily",
                        case_id=case_id,
                        page=page,
                        evidence=evidence,
                    )
                )
            if ui["edge_text"]:
                findings.append(
                    finding(
                        "warning",
                        "text_at_display_edge",
                        f"{case_id}: {len(ui['edge_text'])} text elements touch a physical display edge",
                        case_id=case_id,
                        page=page,
                        evidence=evidence,
                    )
                )
            if ui["unsafe_tv_text"]:
                findings.append(
                    finding(
                        "warning",
                        "tv_safe_area",
                        f"{case_id}: {len(ui['unsafe_tv_text'])} text elements enter the outer 2.5% TV title-safe margin",
                        case_id=case_id,
                        page=page,
                        evidence=evidence,
                    )
                )
            if ui["undersized_text"]:
                findings.append(
                    finding(
                        "warning",
                        "possible_text_clipping",
                        f"{case_id}: {len(ui['undersized_text'])} text bounds are smaller than the conservative font-height threshold",
                        case_id=case_id,
                        page=page,
                        evidence=evidence,
                    )
                )
        except Exception as exc:
            findings.append(
                finding(
                    "infrastructure",
                    "xml_parse_error",
                    f"{case_id}: UI hierarchy cannot be parsed: {exc}",
                    case_id=case_id,
                    page=page,
                    evidence=evidence,
                )
            )

    logs = ""
    for key in ("logcat", "crash_log", "system_events"):
        if key in files:
            logs += (root / files[key]).read_text(encoding="utf-8", errors="ignore") + "\n"
    if CRASH_RE.search(logs):
        findings.append(
            finding(
                "critical",
                "crash_or_anr",
                f"{case_id}: AndroidRuntime crash or ANR signature detected",
                case_id=case_id,
                page=page,
                evidence=evidence,
            )
        )
    if RENDER_RE.search(logs):
        findings.append(
            finding(
                "warning",
                "render_pipeline_error",
                f"{case_id}: render-pipeline fatal/error signature detected",
                case_id=case_id,
                page=page,
                evidence=evidence,
            )
        )

    performance = result["performance"]
    total_time = performance.get("start_metrics_ms", {}).get("TotalTime")
    if total_time is not None and total_time > 5_000:
        findings.append(
            finding(
                "warning",
                "slow_page_start",
                f"{case_id}: activity start took {total_time} ms on the emulator",
                case_id=case_id,
                page=page,
                evidence=evidence,
            )
        )
    jank = performance.get("janky_percent")
    if jank is not None and jank > 35.0:
        findings.append(
            finding(
                "warning",
                "high_emulator_jank",
                f"{case_id}: gfxinfo reports {jank:.1f}% janky frames (emulator advisory)",
                case_id=case_id,
                page=page,
                evidence=evidence,
            )
        )
    pss = performance.get("total_pss_kb")
    if pss is not None and pss > 600_000:
        findings.append(
            finding(
                "warning",
                "high_memory",
                f"{case_id}: total PSS is {pss / 1024:.1f} MiB",
                case_id=case_id,
                page=page,
                evidence=evidence,
            )
        )

    severities = {item["severity"] for item in findings}
    if "infrastructure" in severities:
        result["status"] = "BLOCKED"
    elif "critical" in severities:
        result["status"] = "FAIL"
    elif "warning" in severities:
        result["status"] = "WARN"
    return result, findings


def analyze_navigation(
    root: Path,
    entries: list[dict[str, Any]],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    findings: list[dict[str, Any]] = []
    normalized: list[dict[str, Any]] = []
    for entry in entries:
        item = dict(entry)
        item["status"] = "PASS" if entry.get("success") else "FAIL"
        normalized.append(item)
        if entry.get("success"):
            continue
        evidence: dict[str, str] = {}
        evidence_root = entry.get("evidence")
        if evidence_root:
            for key, filename in (
                ("screenshot", "screenshot.png"),
                ("xml", "ui.xml"),
                ("logcat", "logcat.txt"),
            ):
                path = Path(evidence_root) / filename
                if (root / path).is_file():
                    evidence[key] = path.as_posix()
        severity = "infrastructure" if entry.get("page") == "<audit>" else "critical"
        findings.append(
            finding(
                severity,
                "navigation_failure",
                f"{entry.get('orientation')} / {entry.get('page')}: "
                f"{entry.get('reason') or 'destination did not open'}",
                page=entry.get("page"),
                evidence=evidence,
            )
        )
    return normalized, findings


def focus_signature(node: dict[str, Any]) -> str:
    return json.dumps(
        {
            "text": node.get("text"),
            "class": node.get("class"),
            "bounds": node.get("bounds"),
        },
        ensure_ascii=False,
        sort_keys=True,
    )


def analyze_focus(
    root: Path,
    device: dict[str, Any],
    entries: list[dict[str, Any]],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    normalized: list[dict[str, Any]] = []
    findings: list[dict[str, Any]] = []
    for entry in entries:
        item = dict(entry)
        files = relative_files(root, entry.get("files", {}))
        item["files"] = files
        evidence = {
            key: value
            for key, value in files.items()
            if key in {"screenshot", "xml", "logcat", "window"}
        }
        if entry.get("error"):
            item["status"] = "BLOCKED"
            findings.append(
                finding(
                    "infrastructure",
                    "focus_audit_error",
                    f"{entry.get('orientation')} / {entry.get('page')}: {entry['error']}",
                    page=entry.get("page"),
                    evidence=evidence,
                )
            )
            normalized.append(item)
            continue
        observed = [step["focused"] for step in entry.get("trace", []) if step.get("focused")]
        signatures = {focus_signature(node) for node in observed}
        item["observed_focus_steps"] = len(observed)
        item["unique_focus_targets"] = len(signatures)
        item["status"] = "PASS"
        if not observed:
            item["status"] = "FAIL"
            findings.append(
                finding(
                    "critical",
                    "focus_missing",
                    f"{entry.get('orientation')} / {entry.get('page')}: no focused node after D-pad input",
                    page=entry.get("page"),
                    evidence=evidence,
                )
            )
        elif len(signatures) < 2:
            item["status"] = "FAIL"
            findings.append(
                finding(
                    "critical",
                    "focus_trap",
                    f"{entry.get('orientation')} / {entry.get('page')}: focus never moved to a second target",
                    page=entry.get("page"),
                    evidence=evidence,
                )
            )

        width, height = expected_dimensions(device, entry.get("orientation", "landscape"))
        bad_bounds = []
        edge_bounds = []
        for node in observed:
            bounds = node.get("bounds")
            if not bounds:
                continue
            x1, y1, x2, y2 = bounds
            if x1 < 0 or y1 < 0 or x2 > width or y2 > height:
                bad_bounds.append(bounds)
            if x1 <= 1 or y1 <= 1 or x2 >= width - 1 or y2 >= height - 1:
                edge_bounds.append(bounds)
        if bad_bounds:
            item["status"] = "FAIL"
            findings.append(
                finding(
                    "critical",
                    "focused_node_out_of_bounds",
                    f"{entry.get('orientation')} / {entry.get('page')}: focused node left the display",
                    page=entry.get("page"),
                    evidence=evidence,
                )
            )
        elif edge_bounds:
            if item["status"] == "PASS":
                item["status"] = "WARN"
            findings.append(
                finding(
                    "warning",
                    "focused_node_at_edge",
                    f"{entry.get('orientation')} / {entry.get('page')}: focus touches a display edge",
                    page=entry.get("page"),
                    evidence=evidence,
                )
            )
        normalized.append(item)
    return normalized, findings


def report_markdown(summary: dict[str, Any]) -> str:
    device = summary["device"]
    lines = [
        f"# HULK SA Compatibility Lab — {device['name']}",
        "",
        f"- Overall: **{summary['overall_status']}**",
        f"- Device: `{device['id']}` ({device['family']})",
        f"- Android: API {device['api']} / `{device['target']}` / `{device['arch']}`",
        f"- Requested display: {device['requested_width']}×{device['requested_height']} @ "
        f"{device['requested_density']} dpi",
        f"- Captures: {summary['case_count']} / expected {summary['expected_case_count']}",
        f"- Critical: {summary['critical_count']}",
        f"- Warnings: {summary['warning_count']}",
        f"- Infrastructure errors: {summary['infrastructure_error_count']}",
        "",
        "## Page results",
        "",
        "| Case | Status | Resolution | UI nodes | Start | Jank | Evidence |",
        "|---|---|---:|---:|---:|---:|---|",
    ]
    for case in summary["cases"]:
        image = case.get("image") or {}
        ui = case.get("ui") or {}
        perf = case.get("performance") or {}
        resolution = (
            f"{image.get('width')}×{image.get('height')}"
            if image
            else "missing"
        )
        total = perf.get("start_metrics_ms", {}).get("TotalTime")
        jank = perf.get("janky_percent")
        links = []
        for label, key in (("PNG", "screenshot"), ("XML", "xml"), ("Log", "logcat")):
            if key in case["files"]:
                links.append(f"[{label}]({case['files'][key]})")
        lines.append(
            f"| `{case['id']}` | **{case['status']}** | {resolution} | "
            f"{ui.get('node_count', 0)} | {f'{total} ms' if total is not None else 'n/a'} | "
            f"{f'{jank:.1f}%' if jank is not None else 'n/a'} | {' · '.join(links)} |"
        )

    lines += [
        "",
        "## Navigation",
        "",
        "| Orientation | Page | Status | Detail |",
        "|---|---|---|---|",
    ]
    for entry in summary["navigation"]:
        lines.append(
            f"| {entry.get('orientation')} | {entry.get('page')} | "
            f"**{entry.get('status')}** | {entry.get('reason') or ''} |"
        )
    if not summary["navigation"]:
        lines.append("| — | — | BLOCKED | No navigation audit produced |")

    if device["is_tv"]:
        lines += [
            "",
            "## D-pad focus",
            "",
            "| Page | Status | Observed steps | Unique targets |",
            "|---|---|---:|---:|",
        ]
        for entry in summary["focus"]:
            lines.append(
                f"| {entry.get('page')} | **{entry.get('status')}** | "
                f"{entry.get('observed_focus_steps', 0)} | "
                f"{entry.get('unique_focus_targets', 0)} |"
            )

    lines += ["", "## Findings", ""]
    if not summary["findings"]:
        lines.append("- No findings.")
    for item in summary["findings"]:
        links = []
        for label, key in (("screenshot", "screenshot"), ("XML", "xml"), ("logcat", "logcat")):
            path = item.get("evidence", {}).get(key)
            if path:
                links.append(f"[{label}]({path})")
        suffix = f" — {' · '.join(links)}" if links else ""
        lines.append(
            f"- **{item['severity'].upper()} · `{item['code']}`** — "
            f"{item['message']}{suffix}"
        )
    lines += [
        "",
        "> Performance values come from a software-rendered emulator and are advisory. "
        "Crash, ANR, hierarchy, bounds, navigation and focus failures are deterministic gates.",
        "",
    ]
    return "\n".join(lines)


def report_html(summary: dict[str, Any]) -> str:
    status = summary["overall_status"]
    status_class = status.lower()
    case_rows = []
    for case in summary["cases"]:
        image = case.get("image") or {}
        ui = case.get("ui") or {}
        screenshot = case["files"].get("screenshot")
        thumb = (
            f'<a href="{escape(screenshot)}"><img loading="lazy" src="{escape(screenshot)}" '
            f'alt="{escape(case["id"])}"></a>'
            if screenshot
            else "—"
        )
        evidence = []
        for label, key in (("PNG", "screenshot"), ("XML", "xml"), ("LOG", "logcat")):
            path = case["files"].get(key)
            if path:
                evidence.append(f'<a href="{escape(path)}">{label}</a>')
        case_rows.append(
            "<tr>"
            f'<td><code>{escape(case["id"])}</code></td>'
            f'<td><span class="badge {case["status"].lower()}">{case["status"]}</span></td>'
            f"<td>{image.get('width', '—')}×{image.get('height', '—')}</td>"
            f"<td>{ui.get('node_count', 0)}</td>"
            f"<td>{thumb}</td>"
            f"<td>{' · '.join(evidence)}</td>"
            "</tr>"
        )
    finding_cards = []
    for item in summary["findings"]:
        links = []
        for label, key in (("Screenshot", "screenshot"), ("XML", "xml"), ("Logcat", "logcat")):
            path = item.get("evidence", {}).get(key)
            if path:
                links.append(f'<a href="{escape(path)}">{label}</a>')
        finding_cards.append(
            f'<article class="finding {escape(item["severity"])}">'
            f'<div><span class="badge {escape(item["severity"])}">'
            f'{escape(item["severity"].upper())}</span> '
            f'<code>{escape(item["code"])}</code></div>'
            f'<p>{escape(item["message"])}</p>'
            f'<div class="links">{" · ".join(links)}</div>'
            "</article>"
        )
    device = summary["device"]
    return f"""<!doctype html>
<html lang="en" dir="ltr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>HULK SA Compatibility Lab — {escape(device['name'])}</title>
<style>
:root {{ color-scheme: dark; --bg:#090a07; --panel:#12140f; --line:#303426;
  --text:#f3f0e6; --muted:#aaa99f; --gold:#d9ad45; --pass:#4bc279;
  --warn:#f2b84b; --fail:#ff6767; --blocked:#b28cff; }}
* {{ box-sizing:border-box }} body {{ margin:0; background:var(--bg); color:var(--text);
  font:15px/1.5 system-ui,-apple-system,Segoe UI,sans-serif }}
main {{ width:min(1500px,96vw); margin:28px auto 60px }} h1,h2 {{ line-height:1.2 }}
.hero {{ display:flex; justify-content:space-between; gap:20px; align-items:flex-start;
  padding:24px; border:1px solid var(--line); border-radius:18px; background:var(--panel) }}
.metrics {{ display:grid; grid-template-columns:repeat(4,minmax(110px,1fr)); gap:12px; margin:18px 0 }}
.metric {{ padding:16px; border:1px solid var(--line); border-radius:14px; background:var(--panel) }}
.metric strong {{ display:block; font-size:24px }} .muted {{ color:var(--muted) }}
.badge {{ display:inline-block; padding:3px 8px; border-radius:999px; font-weight:800; font-size:12px }}
.pass {{ color:var(--pass) }} .warn,.warning {{ color:var(--warn) }}
.fail,.critical {{ color:var(--fail) }} .blocked,.infrastructure {{ color:var(--blocked) }}
table {{ width:100%; border-collapse:collapse; background:var(--panel); border-radius:14px; overflow:hidden }}
th,td {{ padding:10px; border-bottom:1px solid var(--line); text-align:left; vertical-align:middle }}
th {{ color:var(--gold); position:sticky; top:0; background:#171a12 }}
td img {{ width:110px; max-height:180px; object-fit:contain; border:1px solid var(--line); border-radius:7px }}
a {{ color:var(--gold) }} code {{ word-break:break-word }}
.finding {{ padding:14px 16px; margin:10px 0; border:1px solid var(--line);
  border-left-width:5px; border-radius:12px; background:var(--panel) }}
.finding.critical {{ border-left-color:var(--fail) }} .finding.warning {{ border-left-color:var(--warn) }}
.finding.infrastructure {{ border-left-color:var(--blocked) }} .links {{ min-height:20px }}
@media(max-width:800px) {{ .metrics {{ grid-template-columns:repeat(2,1fr) }}
  .hero {{ flex-direction:column }} table {{ display:block; overflow:auto }} }}
</style>
</head>
<body><main>
<section class="hero">
  <div><div class="badge {status_class}">{escape(status)}</div>
    <h1>HULK SA Compatibility Lab</h1>
    <div class="muted">{escape(device['name'])} · API {device['api']} ·
      {device['requested_width']}×{device['requested_height']} @ {device['requested_density']} dpi</div>
  </div>
  <div class="muted">Authenticated-shell fixture · RTL · {escape(device['orientations'])}</div>
</section>
<section class="metrics">
  <div class="metric"><span class="muted">Captures</span><strong>{summary['case_count']}</strong></div>
  <div class="metric"><span class="muted">Critical</span><strong>{summary['critical_count']}</strong></div>
  <div class="metric"><span class="muted">Warnings</span><strong>{summary['warning_count']}</strong></div>
  <div class="metric"><span class="muted">Infrastructure</span><strong>{summary['infrastructure_error_count']}</strong></div>
</section>
<h2>Page captures</h2>
<table><thead><tr><th>Case</th><th>Status</th><th>Resolution</th><th>Nodes</th><th>Preview</th><th>Files</th></tr></thead>
<tbody>{''.join(case_rows)}</tbody></table>
<h2>Findings</h2>
{''.join(finding_cards) if finding_cards else '<p>No findings.</p>'}
<p class="muted">Performance values are advisory on software-rendered emulators. Raw PNG, XML,
logcat, gfxinfo, meminfo and window diagnostics are retained for every capture.</p>
</main></body></html>
"""


def junit_xml(summary: dict[str, Any]) -> str:
    tests = len(summary["cases"])
    failures = sum(1 for case in summary["cases"] if case["status"] == "FAIL")
    errors = sum(1 for case in summary["cases"] if case["status"] == "BLOCKED")
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f'<testsuite name="HULK SA Compatibility Lab - {escape(summary["device"]["id"])}" '
        f'tests="{tests}" failures="{failures}" errors="{errors}">',
    ]
    case_findings: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for item in summary["findings"]:
        if item.get("case_id"):
            case_findings[item["case_id"]].append(item)
    for case in summary["cases"]:
        lines.append(
            f'  <testcase classname="{escape(summary["device"]["id"])}" '
            f'name="{escape(case["id"])}">'
        )
        relevant = case_findings.get(case["id"], [])
        critical = [item for item in relevant if item["severity"] == "critical"]
        infra = [item for item in relevant if item["severity"] == "infrastructure"]
        warnings = [item for item in relevant if item["severity"] == "warning"]
        if critical:
            message = "\n".join(item["message"] for item in critical)
            lines.append(f'    <failure message="compatibility failure">{escape(message)}</failure>')
        if infra:
            message = "\n".join(item["message"] for item in infra)
            lines.append(f'    <error message="lab infrastructure failure">{escape(message)}</error>')
        if warnings:
            message = "\n".join(item["message"] for item in warnings)
            lines.append(f"    <system-out>{escape(message)}</system-out>")
        lines.append("  </testcase>")
    lines.append("</testsuite>")
    return "\n".join(lines) + "\n"


def analyze_run(root: Path) -> dict[str, Any]:
    root = root.resolve()
    manifest_path = root / "run-manifest.json"
    if not manifest_path.is_file():
        raise FileNotFoundError(f"run manifest missing: {manifest_path}")
    manifest = load_json(manifest_path)
    device = manifest["device"]
    findings: list[dict[str, Any]] = []
    cases: list[dict[str, Any]] = []

    for item in manifest.get("harness_errors", []):
        findings.append(
            finding(
                "infrastructure",
                "harness_error",
                f"{item.get('scope')}: {item.get('message')}",
            )
        )

    density_file = root / "device/wm-density.txt"
    requested_density = str(device["requested_density"])
    if density_file.is_file():
        density_text = density_file.read_text(encoding="utf-8", errors="ignore")
        if requested_density not in density_text:
            findings.append(
                finding(
                    "infrastructure",
                    "display_density_mismatch",
                    f"wm density did not report requested {requested_density} dpi: "
                    f"{density_text.strip()[:300]}",
                )
            )

    for case in manifest.get("cases", []):
        result, case_findings = add_case_findings(root, device, case)
        cases.append(result)
        findings.extend(case_findings)

    orientations = [item for item in str(device["orientations"]).split(",") if item]
    scales = [item for item in str(device["font_scales"]).split(",") if item]
    expected_case_count = len(manifest.get("pages", [])) * len(orientations) * len(scales)
    if len(cases) != expected_case_count:
        findings.append(
            finding(
                "infrastructure",
                "incomplete_capture_matrix",
                f"captured {len(cases)} cases; expected {expected_case_count}",
            )
        )

    navigation, navigation_findings = analyze_navigation(
        root,
        manifest.get("navigation", []),
    )
    findings.extend(navigation_findings)
    expected_navigation = len(manifest.get("pages", [])) * len(orientations)
    if len(navigation) != expected_navigation:
        findings.append(
            finding(
                "infrastructure",
                "incomplete_navigation_audit",
                f"captured {len(navigation)} navigation checks; expected {expected_navigation}",
            )
        )

    focus, focus_findings = analyze_focus(root, device, manifest.get("focus", []))
    findings.extend(focus_findings)
    expected_focus = len(manifest.get("pages", [])) * len(orientations) if device["is_tv"] else 0
    if len(focus) != expected_focus:
        findings.append(
            finding(
                "infrastructure",
                "incomplete_focus_audit",
                f"captured {len(focus)} focus checks; expected {expected_focus}",
            )
        )

    critical_count = sum(1 for item in findings if item["severity"] == "critical")
    warning_count = sum(1 for item in findings if item["severity"] == "warning")
    infrastructure_count = sum(
        1 for item in findings if item["severity"] == "infrastructure"
    )
    overall = (
        "BLOCKED"
        if infrastructure_count
        else "FAIL"
        if critical_count
        else "WARN"
        if warning_count
        else "PASS"
    )
    summary = {
        "schema_version": 2,
        "device": device,
        "overall_status": overall,
        "expected_case_count": expected_case_count,
        "case_count": len(cases),
        "critical_count": critical_count,
        "warning_count": warning_count,
        "infrastructure_error_count": infrastructure_count,
        "cases": cases,
        "navigation": navigation,
        "focus": focus,
        "findings": findings,
    }

    (root / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    markdown = report_markdown(summary)
    (root / "REPORT.md").write_text(markdown, encoding="utf-8")
    (root / "REPORT.html").write_text(report_html(summary), encoding="utf-8")
    (root / "junit.xml").write_text(junit_xml(summary), encoding="utf-8")
    concise = "\n".join(
        [
            f"## {device['name']} — {overall}",
            "",
            f"- Captures: {len(cases)} / {expected_case_count}",
            f"- Critical: {critical_count}",
            f"- Warnings: {warning_count}",
            f"- Infrastructure errors: {infrastructure_count}",
            "",
        ]
    )
    (root / "GITHUB_STEP_SUMMARY.md").write_text(concise, encoding="utf-8")
    return summary


def main() -> int:
    if len(sys.argv) != 2:
        raise SystemExit("usage: analyze.py <device-result-directory>")
    summary = analyze_run(Path(sys.argv[1]))
    print(json.dumps(
        {
            "status": summary["overall_status"],
            "critical": summary["critical_count"],
            "warnings": summary["warning_count"],
            "infrastructure": summary["infrastructure_error_count"],
        },
        ensure_ascii=False,
    ))
    return 2 if summary["infrastructure_error_count"] else 1 if summary["critical_count"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
