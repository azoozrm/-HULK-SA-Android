#!/usr/bin/env python3
"""Fail-closed classification of lab-only versus product-affecting PR changes."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import subprocess
from typing import Iterable


LAB_PREFIXES = (
    "qa/",
    "docs/quality/",
    "app/src/androidTest/",
    "tools/",
)
LAB_WORKFLOWS = {
    ".github/workflows/compatibility-lab.yml",
    ".github/workflows/quality-nightly.yml",
    ".github/workflows/quality-pr-intelligence.yml",
    ".github/workflows/quality-pr.yml",
    ".github/workflows/quality-release.yml",
    ".github/workflows/quality-ui.yml",
    ".github/workflows/quality-lab-self-validation.yml",
    ".github/workflows/canonical-build.yml",
    ".github/workflows/signed-release-qualification.yml",
    ".github/workflows/generated-source-snapshot.yml",
}
GRADLE_TEST_TOKENS = (
    "testInstrumentationRunner",
    "testInstrumentationRunnerArguments",
    "testOptions",
    "ANDROIDX_TEST_ORCHESTRATOR",
    "animationsDisabled",
    "unitTests",
    "managedDevices",
    "androidTestImplementation(",
    "androidTestUtil(",
    "testImplementation(",
    'debugImplementation("androidx.compose.ui:ui-test-manifest',
)


def changed_lines_from_patch(patch: str) -> list[str]:
    lines: list[str] = []
    for raw in patch.splitlines():
        if raw.startswith(("+++", "---", "@@", "\\ No newline")):
            continue
        if raw.startswith(("+", "-")):
            lines.append(raw[1:])
    return lines


def is_allowed_gradle_test_line(line: str) -> bool:
    stripped = line.strip()
    if not stripped or stripped in {"{", "}"} or stripped.startswith("//"):
        return True
    return any(token in stripped for token in GRADLE_TEST_TOKENS)


def is_lab_path(path: str) -> bool:
    normalized = path.strip().replace("\\", "/")
    return normalized.startswith(LAB_PREFIXES) or normalized in LAB_WORKFLOWS


def classify_scope(paths: Iterable[str], gradle_patch: str = "") -> dict[str, object]:
    files = sorted({path.strip().replace("\\", "/") for path in paths if path.strip()})
    product_files: list[str] = []
    lab_files: list[str] = []
    for path in files:
        if path == "app/build.gradle.kts":
            continue
        if is_lab_path(path):
            lab_files.append(path)
        else:
            product_files.append(path)

    gradle_lines = changed_lines_from_patch(gradle_patch) if "app/build.gradle.kts" in files else []
    restricted_gradle_lines = [line for line in gradle_lines if not is_allowed_gradle_test_line(line)]
    if "app/build.gradle.kts" in files and not restricted_gradle_lines:
        lab_files.append("app/build.gradle.kts")
    elif "app/build.gradle.kts" in files:
        product_files.append("app/build.gradle.kts")

    lab_only = bool(files) and not product_files and not restricted_gradle_lines
    if not files:
        reason = "No diff evidence was available; product enforcement remains enabled."
    elif product_files:
        reason = "Product-affecting or unclassified files changed."
    elif restricted_gradle_lines:
        reason = "app/build.gradle.kts contains changes outside the test-only allowlist."
    else:
        reason = "All changes are proven Quality Lab, Android test, documentation, or test-only Gradle changes."

    return {
        "schema_version": 1,
        "lab_only": lab_only,
        "enforce_product_findings": not lab_only,
        "reason": reason,
        "changed_files": files,
        "lab_files": sorted(lab_files),
        "product_files": sorted(set(product_files)),
        "restricted_gradle_lines": restricted_gradle_lines,
    }


def git_output(root: Path, args: list[str]) -> str:
    return subprocess.run(
        ["git", *args],
        cwd=root,
        check=True,
        text=True,
        capture_output=True,
    ).stdout


def classify_git_diff(root: Path, base: str, head: str) -> dict[str, object]:
    if not base.strip():
        return classify_scope([])
    files = git_output(root, ["diff", "--name-only", f"{base}...{head}"]).splitlines()
    gradle_patch = ""
    if "app/build.gradle.kts" in files:
        gradle_patch = git_output(
            root,
            ["diff", "--unified=0", f"{base}...{head}", "--", "app/build.gradle.kts"],
        )
    return classify_scope(files, gradle_patch)


def append_step_summary(data: dict[str, object]) -> None:
    destination = os.environ.get("GITHUB_STEP_SUMMARY")
    if not destination:
        return
    with Path(destination).open("a", encoding="utf-8") as handle:
        handle.write("\n## Quality UI scope classification\n\n")
        handle.write(f"- Lab only: `{str(data['lab_only']).lower()}`\n")
        handle.write(f"- Enforce product findings: `{str(data['enforce_product_findings']).lower()}`\n")
        handle.write(f"- Reason: {data['reason']}\n")
        if data["product_files"]:
            handle.write("- Product/unclassified files:\n")
            for path in data["product_files"]:
                handle.write(f"  - `{path}`\n")
        if data["restricted_gradle_lines"]:
            handle.write("- Restricted Gradle changes:\n")
            for line in data["restricted_gradle_lines"]:
                handle.write(f"  - `{line}`\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--base", default="")
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--github-output", type=Path)
    args = parser.parse_args()

    data = classify_git_diff(args.root, args.base, args.head)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if args.github_output:
        with args.github_output.open("a", encoding="utf-8") as handle:
            handle.write(f"lab_only={str(data['lab_only']).lower()}\n")
            handle.write(
                "enforce_product_findings="
                f"{str(data['enforce_product_findings']).lower()}\n"
            )
    append_step_summary(data)
    print(
        "LAB_ONLY" if data["lab_only"] else "PRODUCT_STRICT",
        "-",
        data["reason"],
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
