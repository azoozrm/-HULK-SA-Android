#!/usr/bin/env python3
"""Deterministic TV focus contracts shared by capture and analysis.

The contract intentionally distinguishes repeated catalog content from named controls:
all visible named controls must be reached, while dense content grids must meet a
strong geometry-based coverage threshold. Downloads additionally have a complete
three-column action graph and an executable state-transition audit.
"""

from __future__ import annotations

from dataclasses import dataclass
import re
from typing import Any
import xml.etree.ElementTree as ET


KEY_CODES = {
    "UP": 19,
    "DOWN": 20,
    "LEFT": 21,
    "RIGHT": 22,
    "CENTER": 23,
}
BOUNDS_RE = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")

PAGE_MINIMUM_UNIQUE = {
    "home": 6,
    "live": 7,
    "movies": 7,
    "series": 7,
    "favorites": 3,
    "search": 5,
    "downloads": 9,
    "settings": 7,
}

DOWNLOAD_REQUIRED_LABELS = (
    "كل الشبكات",
    "الجدولة الان",
    "متزامنة  2",
    "ايقاف مؤقت",
    "عادية",
    "الغاء",
)


@dataclass(frozen=True)
class Bounds:
    left: int
    top: int
    right: int
    bottom: int

    @property
    def width(self) -> int:
        return max(0, self.right - self.left)

    @property
    def height(self) -> int:
        return max(0, self.bottom - self.top)

    @property
    def area(self) -> int:
        return self.width * self.height

    @property
    def center(self) -> tuple[float, float]:
        return ((self.left + self.right) / 2.0, (self.top + self.bottom) / 2.0)

    def as_list(self) -> list[int]:
        return [self.left, self.top, self.right, self.bottom]


def parse_bounds(value: str) -> Bounds | None:
    match = BOUNDS_RE.fullmatch(value or "")
    if not match:
        return None
    bounds = Bounds(*map(int, match.groups()))
    return bounds if bounds.area > 0 else None


def node_label(node: ET.Element) -> str:
    return " ".join(
        value.strip()
        for value in (
            node.attrib.get("text", ""),
            node.attrib.get("content-desc", ""),
        )
        if value.strip()
    )


def visible_interactive_targets(
    xml_bytes: bytes,
    *,
    package: str,
    display_width: int,
    display_height: int,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    """Return focus targets and visible clickable nodes that cannot receive focus."""
    root = ET.fromstring(xml_bytes)
    targets: list[dict[str, Any]] = []
    unfocusable: list[dict[str, Any]] = []
    for node in root.iter("node"):
        if node.attrib.get("package", "") != package:
            continue
        if node.attrib.get("visible-to-user", "true") == "false":
            continue
        if node.attrib.get("enabled", "true") == "false":
            continue
        bounds = parse_bounds(node.attrib.get("bounds", ""))
        if bounds is None:
            continue
        if (
            bounds.right <= 0
            or bounds.bottom <= 0
            or bounds.left >= display_width
            or bounds.top >= display_height
        ):
            continue
        record = {
            "label": node_label(node),
            "class": node.attrib.get("class", ""),
            "bounds": bounds.as_list(),
            "clickable": node.attrib.get("clickable") == "true",
            "focusable": node.attrib.get("focusable") == "true",
        }
        if record["focusable"]:
            targets.append(record)
        elif record["clickable"]:
            unfocusable.append(record)
    return targets, unfocusable


def _repeat(name: str, count: int) -> list[tuple[str, int]]:
    return [(name, KEY_CODES[name])] * count


def focus_key_sequence(page: str, *, exhaustive: bool) -> list[tuple[str, int]]:
    """A bounded deterministic sweep; 1080p performs the full application audit."""
    if page == "downloads":
        # The product graph starts on the first card primary action. This sequence
        # reaches every toolbar control and all three actions in three rows.
        return [
            ("UP", 19),
            ("LEFT", 21),
            ("LEFT", 21),
            ("DOWN", 20),
            ("RIGHT", 22),
            ("RIGHT", 22),
            ("DOWN", 20),
            ("LEFT", 21),
            ("LEFT", 21),
            ("DOWN", 20),
            ("RIGHT", 22),
            ("RIGHT", 22),
            ("UP", 19),
            ("UP", 19),
            ("UP", 19),
        ]

    smoke = [
        ("RIGHT", 22),
        ("LEFT", 21),
        ("UP", 19),
        ("DOWN", 20),
        ("DOWN", 20),
        ("LEFT", 21),
        ("RIGHT", 22),
        ("DOWN", 20),
        ("UP", 19),
        ("RIGHT", 22),
        ("DOWN", 20),
        ("UP", 19),
    ]
    if not exhaustive:
        return smoke

    if page == "home":
        content = _repeat("LEFT", 6) + [("DOWN", 20)] + _repeat("RIGHT", 6) + [("DOWN", 20)] + _repeat("LEFT", 6)
    elif page in {"movies", "series", "search"}:
        content = _repeat("LEFT", 5) + [("UP", 19)] + _repeat("LEFT", 6) + _repeat("RIGHT", 6) + [("DOWN", 20)] + _repeat("LEFT", 5) + [("DOWN", 20)] + _repeat("RIGHT", 6)
    elif page == "live":
        content = [("RIGHT", 22), ("DOWN", 20)] + _repeat("LEFT", 6) + _repeat("RIGHT", 6) + _repeat("DOWN", 5) + [("LEFT", 21), ("RIGHT", 22)]
    elif page == "favorites":
        content = _repeat("LEFT", 4) + _repeat("RIGHT", 4) + [("DOWN", 20), ("UP", 19)]
    elif page == "settings":
        content = _repeat("LEFT", 4) + _repeat("RIGHT", 4) + _repeat("DOWN", 3) + _repeat("LEFT", 4) + _repeat("RIGHT", 4) + _repeat("UP", 3)
    else:
        content = smoke

    # Enter the right-hand rail, visit every visible destination, return once.
    rail = _repeat("RIGHT", 7) + _repeat("DOWN", 8) + _repeat("UP", 8) + [("LEFT", 21)]
    return content + rail


def minimum_unique_targets(page: str) -> int:
    return PAGE_MINIMUM_UNIQUE.get(page, 3)


def _overlap_ratio(target: Bounds, observed: Bounds) -> float:
    left = max(target.left, observed.left)
    top = max(target.top, observed.top)
    right = min(target.right, observed.right)
    bottom = min(target.bottom, observed.bottom)
    intersection = max(0, right - left) * max(0, bottom - top)
    if intersection <= 0:
        return 0.0
    return intersection / max(1, min(target.area, observed.area))


def target_reached(target: dict[str, Any], observed_nodes: list[dict[str, Any]]) -> bool:
    target_bounds = Bounds(*target["bounds"])
    target_label = str(target.get("label", "")).strip()
    for observed in observed_nodes:
        bounds_value = observed.get("bounds")
        if not bounds_value:
            continue
        observed_bounds = Bounds(*bounds_value)
        observed_label = str(observed.get("text", "")).strip()
        if target_label and observed_label and target_label == observed_label:
            if _overlap_ratio(target_bounds, observed_bounds) >= 0.20:
                return True
        if _overlap_ratio(target_bounds, observed_bounds) >= 0.55:
            return True
    return False


def focus_coverage(
    targets: list[dict[str, Any]],
    observed_nodes: list[dict[str, Any]],
) -> dict[str, Any]:
    reached = [target for target in targets if target_reached(target, observed_nodes)]
    named = [target for target in targets if str(target.get("label", "")).strip()]
    named_reached = [target for target in named if target_reached(target, observed_nodes)]
    return {
        "target_count": len(targets),
        "reached_count": len(reached),
        "coverage": round(len(reached) / max(1, len(targets)), 5),
        "named_target_count": len(named),
        "named_reached_count": len(named_reached),
        "named_coverage": round(len(named_reached) / max(1, len(named)), 5),
        "unreached": [target for target in targets if target not in reached],
        "unreached_named": [target for target in named if target not in named_reached],
    }
