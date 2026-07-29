#!/usr/bin/env python3
"""Classify a Git diff and select the smallest safe quality test set."""

from __future__ import annotations

import argparse
from collections import defaultdict
import json
from pathlib import Path
import subprocess
from typing import Iterable


RULES: tuple[dict[str, object], ...] = (
    {
        "id": "compose_layout",
        "prefixes": ("app/src/main/java/", "app/src/main/res/"),
        "suffixes": (".kt", ".xml", ".webp", ".png", ".svg"),
        "keywords": ("ui/", "screen", "compose", "theme", "drawable", "layout", "values"),
        "tests": (
            "unit",
            "compose-ui",
            "visual",
            "navigation",
            "focus",
            "accessibility",
            "ui-device-smoke",
        ),
    },
    {
        "id": "downloads",
        "keywords": ("download", "workmanager", "offline"),
        "tests": (
            "unit",
            "download-fixtures",
            "download-resume",
            "process-death",
            "storage",
            "network-resilience",
            "downloads-ui",
        ),
    },
    {
        "id": "playback",
        "keywords": ("player", "media3", "exoplayer", "playback", "stream"),
        "tests": (
            "unit",
            "playback-fixtures",
            "player-focus",
            "player-lifecycle",
            "media-smoke",
        ),
    },
    {
        "id": "navigation",
        "keywords": ("navigation", "destination", "deeplink", "backstack", "drawer", "rail"),
        "tests": ("navigation", "focus", "back-stack", "deep-links", "ui-device-smoke"),
    },
    {
        "id": "network",
        "keywords": (
            "api",
            "network",
            "okhttp",
            "repository",
            "xtream",
            "portal",
            "auth",
            "network_security",
        ),
        "tests": ("unit", "network-fixtures", "offline", "configuration-contract"),
    },
    {
        "id": "build_release",
        "keywords": (
            "androidmanifest",
            "build.gradle",
            "settings.gradle",
            "gradle.properties",
            "proguard",
            "network_security",
            "sign",
            "release/",
        ),
        "tests": (
            "clean-build",
            "unit",
            "lint",
            "manifest-audit",
            "release-r8",
            "packaging",
            "abi",
            "bundletool",
            "logo-integrity",
            "configuration-contract",
        ),
    },
    {
        "id": "resources",
        "prefixes": ("app/src/main/res/",),
        "tests": ("visual", "locale", "density", "resource-shrinking", "logo-integrity"),
    },
    {
        "id": "quality_lab",
        "prefixes": ("qa/", "docs/quality/", ".github/workflows/quality-"),
        "tests": ("quality-self-tests", "schema-validation", "report-dry-run"),
    },
)

ALWAYS = ("source-governance", "impact-analysis", "quality-self-tests", "logo-integrity")
FULL_MATRIX = (
    "clean-build",
    "unit",
    "lint",
    "compose-ui",
    "visual",
    "navigation",
    "focus",
    "accessibility",
    "network-fixtures",
    "download-fixtures",
    "playback-fixtures",
    "lifecycle",
    "performance-advisory",
    "packaging",
    "abi",
    "bundletool",
)


def _rule_matches(path: str, rule: dict[str, object]) -> bool:
    lowered = path.lower()
    prefixes = tuple(str(item).lower() for item in rule.get("prefixes", ()))
    suffixes = tuple(str(item).lower() for item in rule.get("suffixes", ()))
    keywords = tuple(str(item).lower() for item in rule.get("keywords", ()))
    prefix_match = not prefixes or lowered.startswith(prefixes)
    suffix_match = not suffixes or lowered.endswith(suffixes)
    keyword_match = not keywords or any(item in lowered for item in keywords)
    return prefix_match and suffix_match and keyword_match


def classify(paths: Iterable[str], force_full: bool = False) -> dict[str, object]:
    files = sorted({path.strip().replace("\\", "/") for path in paths if path.strip()})
    by_category: dict[str, list[str]] = defaultdict(list)
    selected = set(ALWAYS)
    unknown: list[str] = []
    for path in files:
        matched = False
        for rule in RULES:
            if _rule_matches(path, rule):
                matched = True
                by_category[str(rule["id"])].append(path)
                selected.update(str(test) for test in rule["tests"])
        if not matched:
            unknown.append(path)

    wide_change = len(files) > 40 or len(by_category) >= 5
    full_matrix = force_full or bool(unknown) or wide_change
    if full_matrix:
        selected.update(FULL_MATRIX)
    product_files = [
        path
        for path in files
        if path.startswith("app/src/main/") or path == "app/build.gradle.kts"
    ]
    lab_files = [
        path
        for path in files
        if path.startswith(("qa/", "docs/quality/", ".github/workflows/quality-"))
        or "/androidTest/" in path
        or "/test/" in path
    ]
    risk = (
        "high"
        if full_matrix or (product_files and lab_files)
        else "medium"
        if product_files
        else "low"
    )
    return {
        "schema_version": 1,
        "changed_file_count": len(files),
        "changed_files": files,
        "categories": dict(sorted(by_category.items())),
        "unknown_files": unknown,
        "product_files": product_files,
        "lab_files": lab_files,
        "risk": risk,
        "full_matrix": full_matrix,
        "selection_reason": (
            "manual full_matrix"
            if force_full
            else "unclassified or wide change"
            if full_matrix
            else "deterministic path rules"
        ),
        "selected_tests": sorted(selected),
    }


def git_changed_files(root: Path, base: str, head: str) -> list[str]:
    result = subprocess.run(
        ["git", "diff", "--name-only", f"{base}...{head}"],
        cwd=root,
        check=True,
        text=True,
        capture_output=True,
    )
    return result.stdout.splitlines()


def markdown(data: dict[str, object]) -> str:
    tests = "\n".join(f"- `{item}`" for item in data["selected_tests"])
    categories = "\n".join(
        f"- `{name}`: {len(paths)} file(s)"
        for name, paths in data["categories"].items()
    ) or "- none"
    return (
        "## HULK SA PR change impact\n\n"
        f"- Risk: **{str(data['risk']).upper()}**\n"
        f"- Changed files: {data['changed_file_count']}\n"
        f"- Full matrix: `{str(data['full_matrix']).lower()}`\n"
        f"- Selection: {data['selection_reason']}\n\n"
        "### Categories\n\n"
        f"{categories}\n\n"
        "### Selected tests\n\n"
        f"{tests}\n"
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--base")
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--files", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--selected-output", type=Path)
    parser.add_argument("--summary", type=Path)
    parser.add_argument("--full-matrix", action="store_true")
    args = parser.parse_args()
    if args.files:
        paths = args.files.read_text(encoding="utf-8").splitlines()
    elif args.base:
        paths = git_changed_files(args.root, args.base, args.head)
    else:
        parser.error("provide --files or --base")
    data = classify(paths, force_full=args.full_matrix)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if args.selected_output:
        args.selected_output.parent.mkdir(parents=True, exist_ok=True)
        args.selected_output.write_text(
            json.dumps(
                {
                    "schema_version": 1,
                    "full_matrix": data["full_matrix"],
                    "tests": data["selected_tests"],
                },
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )
    if args.summary:
        args.summary.write_text(markdown(data), encoding="utf-8")
    print(f"{data['risk'].upper()}: selected {len(data['selected_tests'])} test group(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

