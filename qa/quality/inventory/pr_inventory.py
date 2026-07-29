#!/usr/bin/env python3
"""Collect a reviewable, paginated GitHub pull-request inventory.

The collector deliberately uses Git for changed-file discovery. This avoids one
GitHub API request per pull request and lets the resulting inventory be rebuilt
without exposing credentials. CI must check out full history before invoking it.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


DEFAULT_REPOSITORY = "azoozrm/-HULK-SA-Android"
OFFICIAL_BRANCH = "phase-3-v0.9.3.0-adaptive-foundation"
HISTORICAL_MAX_PR = 21
EXPLICIT_REPLACEMENTS = {
    28: 29,
    33: 34,
}


@dataclass(frozen=True)
class ChangeClassification:
    kinds: tuple[str, ...]
    contains_lab_changes: bool
    contains_product_changes: bool
    regression_risk: str


def run_git(*args: str, cwd: Path) -> str:
    completed = subprocess.run(
        ["git", *args],
        cwd=cwd,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return completed.stdout.strip()


def fetch_pull_requests(repository: str, token: str | None = None) -> list[dict[str, Any]]:
    """Fetch every PR using explicit page iteration."""
    results: list[dict[str, Any]] = []
    page = 1
    while True:
        url = (
            f"https://api.github.com/repos/{repository}/pulls"
            f"?state=all&sort=created&direction=asc&per_page=100&page={page}"
        )
        headers = {
            "Accept": "application/vnd.github+json",
            "User-Agent": "hulk-sa-quality-lab",
            "X-GitHub-Api-Version": "2022-11-28",
        }
        if token:
            headers["Authorization"] = f"Bearer {token}"
        with urllib.request.urlopen(  # noqa: S310 - fixed GitHub API host.
            urllib.request.Request(url, headers=headers),
            timeout=30,
        ) as response:
            payload = json.load(response)
        if not isinstance(payload, list):
            raise ValueError("GitHub pulls response must be an array")
        results.extend(payload)
        if len(payload) < 100:
            break
        page += 1
    return results


def changed_files(base_sha: str, head_sha: str, repo_root: Path) -> list[str]:
    for sha in (base_sha, head_sha):
        run_git("cat-file", "-e", f"{sha}^{{commit}}", cwd=repo_root)
    output = run_git(
        "diff",
        "--name-only",
        "--no-renames",
        f"{base_sha}...{head_sha}",
        cwd=repo_root,
    )
    return sorted(line for line in output.splitlines() if line)


def classify_paths(paths: Iterable[str]) -> ChangeClassification:
    files = tuple(paths)
    contains_product = any(
        path == "app/build.gradle.kts"
        or path.startswith(("app/src/main/", "app/src/release/"))
        for path in files
    )
    contains_lab = any(
        path.startswith(("qa/compatibility/", "qa/quality/"))
        or path.startswith(("app/src/androidTest/", "app/src/debug/"))
        or path
        in {
            ".github/workflows/compatibility-lab.yml",
            ".github/workflows/quality-pr.yml",
            ".github/workflows/quality-ui.yml",
            ".github/workflows/quality-nightly.yml",
            ".github/workflows/quality-release.yml",
            ".github/workflows/quality-pr-intelligence.yml",
        }
        for path in files
    )
    kinds: list[str] = []
    if contains_product:
        kinds.append("product")
    if contains_lab:
        kinds.append("quality-lab")
    if any(path.startswith(".github/workflows/") for path in files):
        kinds.append("workflow")
    if any(
        path in {"build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew"}
        or path.startswith(("gradle/", "tools/"))
        for path in files
    ):
        kinds.append("build-release")
    if any(path.startswith("docs/") for path in files):
        kinds.append("documentation")
    if not kinds:
        kinds.append("repository")

    if contains_product and contains_lab:
        risk = "high-mixed"
    elif contains_product:
        risk = "high"
    elif contains_lab or "workflow" in kinds or "build-release" in kinds:
        risk = "medium"
    else:
        risk = "low"
    return ChangeClassification(tuple(kinds), contains_lab, contains_product, risk)


def normalize_pull_request(raw: dict[str, Any], repo_root: Path) -> dict[str, Any]:
    number = int(raw["number"])
    base = raw["base"]
    head = raw["head"]
    files = changed_files(str(base["sha"]), str(head["sha"]), repo_root)
    classification = classify_paths(files)
    merged = raw.get("merged_at") is not None
    if merged:
        state = "merged"
    elif raw.get("state") == "open":
        state = "open"
    else:
        state = "closed_without_merge"

    historical = number <= HISTORICAL_MAX_PR
    replacement = EXPLICIT_REPLACEMENTS.get(number)
    replaced = historical or replacement is not None
    if replacement is not None:
        replacement_reason = f"Superseded by PR #{replacement}."
    elif historical:
        replacement_reason = (
            f"Historical pre-canonical line; superseded by {OFFICIAL_BRANCH}."
        )
    else:
        replacement_reason = None

    needs_test = not historical and bool(files) and (
        state == "open" or classification.regression_risk != "low"
    )
    return {
        "number": number,
        "title": str(raw.get("title", "")),
        "url": str(raw.get("html_url", "")),
        "state": state,
        "draft": bool(raw.get("draft", False)),
        "merged": merged,
        "merged_at": raw.get("merged_at"),
        "base": {
            "ref": str(base["ref"]),
            "sha": str(base["sha"]),
        },
        "head": {
            "ref": str(head["ref"]),
            "sha": str(head["sha"]),
        },
        "commit_sha": str(head["sha"]),
        "changed_file_count": len(files),
        "changed_files": files,
        "change_types": list(classification.kinds),
        "contains_lab_changes": classification.contains_lab_changes,
        "contains_product_changes": classification.contains_product_changes,
        "replaced": replaced,
        "replaced_by": replacement,
        "replacement_reason": replacement_reason,
        "regression_risk": classification.regression_risk,
        "needs_testing": needs_test,
        "historical_reference_only": historical,
    }


def build_inventory(
    pulls: Iterable[dict[str, Any]],
    repo_root: Path,
    repository: str = DEFAULT_REPOSITORY,
) -> dict[str, Any]:
    records = [normalize_pull_request(raw, repo_root) for raw in pulls]
    records.sort(key=lambda item: item["number"])
    states = {
        key: sum(record["state"] == key for record in records)
        for key in ("open", "merged", "closed_without_merge")
    }
    return {
        "schema_version": 1,
        "repository": repository,
        "official_branch": OFFICIAL_BRANCH,
        "pull_request_count": len(records),
        "state_counts": states,
        "records": records,
    }


def markdown(inventory: dict[str, Any]) -> str:
    counts = inventory["state_counts"]
    lines = [
        "# Pull Request Inventory",
        "",
        f"- Repository: `{inventory['repository']}`",
        f"- Total: **{inventory['pull_request_count']}**",
        f"- Open: **{counts['open']}**",
        f"- Merged: **{counts['merged']}**",
        f"- Closed without merge: **{counts['closed_without_merge']}**",
        "",
        "| PR | State | Base ← Head | SHA | Files | Types | Lab | Product | Risk | Test | Reference |",
        "|---:|---|---|---|---:|---|:---:|:---:|---|:---:|:---:|",
    ]
    for record in inventory["records"]:
        types = ", ".join(record["change_types"])
        lines.append(
            f"| [#{record['number']}]({record['url']}) {record['title']} "
            f"| {record['state']}{' / draft' if record['draft'] else ''} "
            f"| `{record['base']['ref']}` ← `{record['head']['ref']}` "
            f"| `{record['commit_sha'][:12]}` "
            f"| {record['changed_file_count']} "
            f"| {types} "
            f"| {'yes' if record['contains_lab_changes'] else 'no'} "
            f"| {'yes' if record['contains_product_changes'] else 'no'} "
            f"| {record['regression_risk']} "
            f"| {'yes' if record['needs_testing'] else 'no'} "
            f"| {'historical' if record['historical_reference_only'] else 'active'} |"
        )
    lines += [
        "",
        "## Interpretation",
        "",
        f"- PRs #1–#{HISTORICAL_MAX_PR} are retained as historical references; they target "
        "the pre-canonical `main` line and are not silently merged or closed.",
        "- `replaced` is only asserted for the historical line or a small explicit replacement "
        "map. Absence of that flag does not authorize merging.",
        "- Changed-file lists and machine-readable classifications are in "
        "`qa/quality/pr-inventory.json`.",
        "",
    ]
    return "\n".join(lines)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", default=DEFAULT_REPOSITORY)
    parser.add_argument("--repo-root", type=Path, default=Path.cwd())
    parser.add_argument("--input-json", type=Path)
    parser.add_argument("--json-output", type=Path)
    parser.add_argument("--markdown-output", type=Path)
    parser.add_argument("--check", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.input_json:
        pulls = json.loads(args.input_json.read_text(encoding="utf-8"))
    else:
        pulls = fetch_pull_requests(args.repository, os.environ.get("GITHUB_TOKEN"))
    inventory = build_inventory(pulls, args.repo_root.resolve(), args.repository)
    json_text = json.dumps(inventory, indent=2, ensure_ascii=False) + "\n"
    markdown_text = markdown(inventory) + "\n"

    pairs = (
        (args.json_output, json_text),
        (args.markdown_output, markdown_text),
    )
    if args.check:
        stale = [
            str(path)
            for path, expected in pairs
            if path is None
            or not path.is_file()
            or path.read_text(encoding="utf-8") != expected
        ]
        if stale:
            raise SystemExit(f"stale or missing inventory outputs: {', '.join(stale)}")
        return 0

    for path, content in pairs:
        if path is not None:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
    if args.json_output is None and args.markdown_output is None:
        print(json_text, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
