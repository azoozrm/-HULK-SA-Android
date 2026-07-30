#!/usr/bin/env python3
"""Static UI/action inventory with explicit review status for inferred behavior."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
from typing import Any


COMPOSABLE_RE = re.compile(
    r"@Composable\s+(?:internal\s+|private\s+|public\s+)?fun\s+([A-Za-z0-9_]+)\s*\(",
    re.MULTILINE,
)
ACTIVITY_RE = re.compile(r"class\s+([A-Za-z0-9_]+)\s*:\s*(?:ComponentActivity|Activity)\s*\(")
ACTION_PARAM_RE = re.compile(r"\b(on[A-Z][A-Za-z0-9_]*)\s*:\s*\(")
DESTINATION_RE = re.compile(r"enum class MainDestination\s*\{([^}]+)\}", re.DOTALL)

STATE_SCREENS: tuple[tuple[str, str], ...] = (
    ("loading-state", "Loading state"),
    ("empty-state", "Empty state"),
    ("error-state", "Error state"),
    ("permission-prompt", "Permission prompt"),
    ("resume-dialog", "Resume / restart dialog"),
)

ROUTE_TESTS = {
    "home": "compatibility-runtime",
    "live": "compatibility-runtime",
    "movies": "compatibility-runtime",
    "series": "compatibility-runtime",
    "favorites": "compatibility-runtime",
    "search": "compatibility-runtime",
    "downloads": "compatibility-runtime",
    "settings": "compatibility-runtime",
}


def _line_number(source: str, offset: int) -> int:
    return source.count("\n", 0, offset) + 1


def _balanced_signature(source: str, start: int) -> str:
    open_index = source.find("(", start)
    depth = 0
    for index in range(open_index, len(source)):
        char = source[index]
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return source[open_index : index + 1]
    return source[open_index : open_index + 2000]


def discover(root: Path) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    kotlin_files = sorted((root / "app/src/main").rglob("*.kt"))
    screens: list[dict[str, Any]] = []
    actions: list[dict[str, Any]] = []
    destinations: set[str] = set()

    for path in kotlin_files:
        source = path.read_text(encoding="utf-8")
        relative = path.relative_to(root).as_posix()
        for match in DESTINATION_RE.finditer(source):
            destinations.update(
                token
                for token in re.findall(r"\b[A-Z][A-Z0-9_]+\b", match.group(1))
                if token not in {"HTTP"}
            )
        for match in ACTIVITY_RE.finditer(source):
            name = match.group(1)
            screens.append(
                {
                    "id": name.lower(),
                    "name": name,
                    "kind": "activity",
                    "source": relative,
                    "line": _line_number(source, match.start()),
                    "route": None,
                    "states": ["created", "resumed", "recreated"],
                    "test": "instrumentation-smoke" if "/debug/" in relative else "not-covered",
                    "review_required": True,
                }
            )
        for match in COMPOSABLE_RE.finditer(source):
            name = match.group(1)
            lower = name.lower()
            is_screen = (
                name.endswith(("Screen", "Dialog", "Sheet", "Overlay", "Drawer"))
                or name in {"HulkApp", "MainShell", "LiveStage"}
            )
            if is_screen:
                route = next(
                    (destination.lower() for destination in destinations if destination.lower() in lower),
                    None,
                )
                screens.append(
                    {
                        "id": f"composable:{name}",
                        "name": name,
                        "kind": (
                            "dialog"
                            if name.endswith("Dialog")
                            else "overlay"
                            if name.endswith("Overlay")
                            else "drawer"
                            if name.endswith("Drawer")
                            else "composable"
                        ),
                        "source": relative,
                        "line": _line_number(source, match.start()),
                        "route": route,
                        "states": ["loading", "error", "empty", "content"],
                        "test": ROUTE_TESTS.get(route or "", "not-covered"),
                        "review_required": route is None,
                    }
                )
            signature = _balanced_signature(source, match.start())
            for action in sorted(set(ACTION_PARAM_RE.findall(signature))):
                lowered = action.lower()
                interaction = (
                    "long-click"
                    if "long" in lowered
                    else "back"
                    if "back" in lowered or "close" in lowered
                    else "text-input"
                    if "search" in lowered or "query" in lowered
                    else "click"
                )
                actions.append(
                    {
                        "id": f"{name}:{action}",
                        "screen": name,
                        "selector": {"type": "callback", "value": action},
                        "action": interaction,
                        "states": ["enabled", "disabled"],
                        "expected": f"{action} callback produces its named state transition",
                        "input_modes": ["touch", "d-pad", "keyboard"],
                        "test": ROUTE_TESTS.get(next((d.lower() for d in destinations if d.lower() in lower), ""), "not-covered"),
                        "exclusion_reason": (
                            None
                            if name.endswith("Screen")
                            else "Reusable component action requires journey-level binding"
                        ),
                        "review_required": not name.endswith("Screen"),
                        "source": relative,
                        "line": _line_number(source, match.start()),
                    }
                )

    for destination in sorted(destinations):
        route = destination.lower()
        screens.append(
            {
                "id": f"destination:{route}",
                "name": destination,
                "kind": "navigation-destination",
                "source": "app/src/main/java/sa/hulksa/player/HulkViewModel.kt",
                "line": None,
                "route": route,
                "states": ["loading", "error", "empty", "content"],
                "test": ROUTE_TESTS.get(route, "not-covered"),
                "review_required": route not in ROUTE_TESTS,
            }
        )
    for screen_id, label in STATE_SCREENS:
        screens.append(
            {
                "id": f"state:{screen_id}",
                "name": label,
                "kind": "cross-screen-state",
                "source": "static-required-state",
                "line": None,
                "route": None,
                "states": [screen_id],
                "test": "fixture-state",
                "review_required": True,
            }
        )
    screens = sorted({item["id"]: item for item in screens}.values(), key=lambda item: item["id"])
    actions = sorted({item["id"]: item for item in actions}.values(), key=lambda item: item["id"])
    return screens, actions


def coverage_markdown(screens: list[dict[str, Any]], actions: list[dict[str, Any]]) -> str:
    tested_screens = sum(item["test"] != "not-covered" for item in screens)
    tested_actions = sum(item["test"] != "not-covered" for item in actions)
    lines = [
        "# UI and action coverage",
        "",
        "> Generated by `qa/quality/inventory/ui_inventory.py`. Inferred rows remain review-required.",
        "",
        f"- Screens/states discovered: **{len(screens)}**",
        f"- Screens/states with a mapped test layer: **{tested_screens}**",
        f"- Actions discovered: **{len(actions)}**",
        f"- Actions with a mapped test layer: **{tested_actions}**",
        "",
        "## Screens",
        "",
        "| ID | Kind | Route | Test | Review | Source |",
        "|---|---|---|---|---|---|",
    ]
    for item in screens:
        lines.append(
            f"| `{item['id']}` | {item['kind']} | {item['route'] or '—'} | "
            f"{item['test']} | {'yes' if item['review_required'] else 'no'} | "
            f"`{item['source']}` |"
        )
    lines += [
        "",
        "## Action summary",
        "",
        "| Screen | Actions | Covered | Human review |",
        "|---|---:|---:|---:|",
    ]
    grouped: dict[str, list[dict[str, Any]]] = {}
    for action in actions:
        grouped.setdefault(action["screen"], []).append(action)
    for screen, items in sorted(grouped.items()):
        lines.append(
            f"| `{screen}` | {len(items)} | "
            f"{sum(item['test'] != 'not-covered' for item in items)} | "
            f"{sum(item['review_required'] for item in items)} |"
        )
    lines += [
        "",
        "Coordinates are not treated as stable selectors. Runtime journeys prefer Compose "
        "semantics, then test tags, then UIAutomator; coordinates require an explicit geometry check.",
        "",
    ]
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--screens", type=Path, required=True)
    parser.add_argument("--actions", type=Path, required=True)
    parser.add_argument("--documentation", type=Path, required=True)
    args = parser.parse_args()
    screens, actions = discover(args.root)
    for path, value in ((args.screens, screens), (args.actions, actions)):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            json.dumps({"schema_version": 1, path.stem: value}, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    args.documentation.parent.mkdir(parents=True, exist_ok=True)
    args.documentation.write_text(coverage_markdown(screens, actions), encoding="utf-8")
    print(f"PASS: discovered {len(screens)} screens/states and {len(actions)} actions")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
